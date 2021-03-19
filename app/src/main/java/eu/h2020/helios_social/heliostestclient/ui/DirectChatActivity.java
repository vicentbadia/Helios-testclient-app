package eu.h2020.helios_social.heliostestclient.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jetbrains.annotations.NotNull;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo;
import eu.h2020.helios_social.core.messaging.HeliosMessage;
import eu.h2020.helios_social.core.messaging.HeliosTopic;
import eu.h2020.helios_social.core.messaging.MessagingConstants;
import eu.h2020.helios_social.core.messaging.db.HeliosMessageStore;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosMessagingReceiver;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosNetworkAddress;
import eu.h2020.helios_social.core.profile.HeliosUserData;
import eu.h2020.helios_social.core.storage.HeliosStorageUtils;
import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;
import eu.h2020.helios_social.heliostestclient.service.HeliosMessagingServiceHelper;
import eu.h2020.helios_social.heliostestclient.ui.adapters.MessageAdapter;
import kotlin.Unit;

import static eu.h2020.helios_social.core.messaging.MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO;
import static eu.h2020.helios_social.core.messaging.MessagingConstants.HELIOS_DIRECT_CHAT_PROTO;

/**
 * Activity for enabling direct chat functionality. Presents the messages in the chat in a list view.
 * <p>
 * Enables user to send messages and media files.
 */
public class DirectChatActivity extends BaseChatActivity {
    public static final String CHAT_NETWORK_ID = DirectChatActivity.class.getCanonicalName() + "CHATNETWORKID";
    public static final String CHAT_UUID = DirectChatActivity.class.getCanonicalName() + "CHATUUID";
    private static final String TAG = "DirectChatActivity";

    private static final long TIMEOUT_SEND_CHAT_FILE_PROTO = 40 * 1000L;
    private static final long TIMEOUT_SEND_CHAT_PROTO = 10 * 1000L;
    private static final int PICK_MEDIA_FILE = 3113;

    private Handler mHandler = new Handler();
    private Button mSendBtn;
    private EditText mMessageEditText;
    private ImageButton mMediaPickerBtn;
    private String mUserName;
    private String mUserId;

    private String mReceiverNetworkId;
    private String mReceiverName;
    private String mReceiverUUID;
    private ArrayList<HeliosMessagePart> mChatArray = null;
    private byte[] mCacheMediaFileData;
    private String mLastReceivedFromNetworkAddress;
    private HeliosNetworkAddress mHeliosNetworkAddress;
    private HeliosMessageStore mChatMessageStore;
    private MessageAdapter.OnItemClickListener mOnClickListener = new MessageAdapter.OnItemClickListener() {
        @Override
        public void onClick(HeliosMessagePart msg) {
            Log.d(TAG, "onItemClick ");
            handleItemClick(msg);
        }
    };

    private void handleItemClick(HeliosMessagePart msg) {
        Log.d(TAG, "handleItemClick");

        if (!TextUtils.isEmpty(msg.mediaFileName)) {
            viewMediaFile(msg.mediaFileName);
        }

        // Allow re-sending plain messages
        if (mUserId.equals(msg.senderUUID)) {
            if (!msg.msgReceived) {
                AlertDialog.Builder builder = new AlertDialog.Builder(DirectChatActivity.this);
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Disable further actions until re-send finishes
                        mSendBtn.setEnabled(false);
                        mMediaPickerBtn.setEnabled(false);
                        mMessageEditText.setEnabled(false);

                        Toast.makeText(DirectChatActivity.this, "Trying to send the message again... Please wait.", Toast.LENGTH_LONG).show();

                        HeliosNetworkAddress addr = new HeliosNetworkAddress();
                        addr.setNetworkId(mReceiverNetworkId);
                        sendMessageTo(addr, msg);
                    }
                });

                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

                // TODO: Strings to res
                builder.setMessage("Send again?")
                        .setTitle("This message has not been delivered yet.");
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(DirectChatActivity.this);
            builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                }
            });

            // TODO: Strings to res
            builder.setMessage(mLastReceivedFromNetworkAddress)
                    .setTitle("Last Message received from network address:");
            AlertDialog dialog = builder.create();
            dialog.show();

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUserName = HeliosUserData.getInstance().getValue(getString(R.string.setting_username));
        mUserId = HeliosUserData.getInstance().getValue(getString(R.string.setting_user_id));
        if (TextUtils.isEmpty(mUserName)) {
            mUserName = mUserId;
        }

        mReceiverNetworkId = this.getIntent().getStringExtra(CHAT_NETWORK_ID);

        // SQLite chat message store
        mChatMessageStore = new HeliosMessageStore(this.getApplicationContext());

        // Check whether we have user's UUID or networkId
        // If we have CHAT_NETWORK_ID, it means direct chat was opened from:
        // 1) ChatActivity
        // 2) PeerTagActivity
        // In this case, we can either know or not know the user's ego-id (UUID).
        // -> would be simpler if unknown user (no UUID) is not supported.
        if (mReceiverNetworkId == null) {
            Log.d(TAG, "CHAT_NETWORK_ID is null:");
            // If we don't have CHAT_NETWORK_ID, means that we were opened from MainActivity, i.e.,
            // we should have the ego-id of the user in CHAT_UUID.. and can proceed as such.
            mReceiverUUID = this.getIntent().getStringExtra(CHAT_UUID);

            if (mReceiverUUID == null) {
                Log.d(TAG, "No CHAT_NETWORK_ID or CHAT_UUID in intent. Exiting.");
                Toast.makeText(this.getApplicationContext(), "No info for chat provided.", Toast.LENGTH_LONG).show();
                this.finish();
                return;
            } else {
                getSupportActionBar().setTitle(mReceiverUUID);
                String userNetworkId = HeliosMessagingServiceHelper.getInstance().getUserNetworkIdByUUID(mReceiverUUID);
                if (userNetworkId != null) {
                    mReceiverNetworkId = userNetworkId;
                } else {
                    Log.d(TAG, "not known user");
                    showUserNetworkIdNotKnownDialog();
                }
            }
        }

        Log.d(TAG, "getUserDataByNetworkId: " + mReceiverNetworkId);
        HeliosIdentityInfo userInfo = HeliosMessagingServiceHelper.getInstance().getUserDataByNetworkId(mReceiverNetworkId);

        // If we  were opened from 'PeerTagActivity' we might only know the networkId and not the egoId
        if (userInfo == null) {
            Toast.makeText(this.getApplicationContext(), "Could not determine user data by networkId.", Toast.LENGTH_LONG).show();
            // Label the chat with the networkId or UUID
            if (TextUtils.isEmpty(mReceiverNetworkId)) {
                getSupportActionBar().setTitle(mReceiverUUID);
            } else {
                getSupportActionBar().setTitle(mReceiverNetworkId);
                // TODO: Is this our networkID?
            }
        } else {
            Log.d(TAG, "userInfo: " + userInfo);
            mReceiverName = userInfo.getNickname();
            mReceiverUUID = userInfo.getUserUUID();
            Log.d(TAG, "senderName:" + mReceiverName);
            Log.d(TAG, "senderUUID:" + mReceiverUUID);
            // Label the chat with the  user's set name
            getSupportActionBar().setTitle(mReceiverName);
        }

        // Create the adapter to convert the array to views
        // If we don't know the user, create temporary chat
        if (mReceiverUUID == null) {
            mChatArray = new ArrayList<HeliosMessagePart>();
            Log.d(TAG, "DirectChat: creating new temporary chat for user NetworkId:" + mReceiverNetworkId);
            String message = "User not known. Messages will be temporary until we know user's HELIOS ID. Temporary messages will not be saved. FILE receive/send is disabled. You can introduce you in the helios topic, then others now your ID.";
            String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
            HeliosMessagePart receivedMsg = new HeliosMessagePart(message, "HELIOS platform", "-", "-", ts);
            // At this point, we only have networkID available.
            mReceiverName = mReceiverNetworkId;
            mReceiverUUID = mReceiverNetworkId;
            mChatArray.add(receivedMsg);
            mMessageAdapter = new MessageAdapter(mUserId, mChatArray);
        } else {
            HeliosConversation conversation = HeliosConversationList.getInstance().getConversationByTopicUUID(mReceiverUUID);

            if (conversation != null) {
                // We already have a previous chat with this user stored.
                Log.d(TAG, "DirectChat: Loaded previous messages from:" + mReceiverName + ", uuid:" + mReceiverUUID);

                // load from sql, HELIOS_DIRECT_CHAT_PROTO and HELIOS_DIRECT_CHAT_FILE_PROTO should use UUID to load here.
                ArrayList<HeliosMessagePart> oldMessages = mChatMessageStore.loadMessages(mReceiverUUID);

                conversation.joinMessages(oldMessages, HeliosConversation.JoinLocation.PREPEND);
                mMessageAdapter = new MessageAdapter(mUserId, conversation.messages);
            } else {
                if (!mUserId.equals(mReceiverUUID)) {
                    // No previous direct chat, but we have the user's information. Create/store a new chat.
                    Log.d(TAG, "DirectChat: creating new chat for known user:" + mReceiverName);
                    if (TextUtils.isEmpty(mReceiverName)) {
                        mReceiverName = mReceiverUUID;
                    }
                    // Creating new chat with known user
                    // TODO: -- modifies storage!
                    HeliosTopic topic = new HeliosTopic(mReceiverName, "");
                    HeliosConversation newConversation = new HeliosConversation();
                    newConversation.topic = new HeliosTopicContext(topic.getTopicName(), "", mReceiverName, "");
                    newConversation.topic.uuid = mReceiverUUID;
                    ArrayList<HeliosMessagePart> oldMessages = mChatMessageStore.loadMessages(mReceiverUUID);
                    newConversation.joinMessages(oldMessages, HeliosConversation.JoinLocation.PREPEND);
                    HeliosConversationList.getInstance().addConversation(newConversation);
                    mMessageAdapter = new MessageAdapter(mUserId, HeliosConversationList.getInstance().getConversationByTopicUUID(mReceiverUUID).messages);
                } else {
                    Log.d(TAG, "DirectChat: not creating chat for myself.");
                    mChatArray = new ArrayList<HeliosMessagePart>();
                    String message = "This is you. Chat to someone else?";
                    String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
                    HeliosMessagePart receivedMsg = new HeliosMessagePart(message, "HELIOS platform", "-", "-", ts);
                    mChatArray.add(receivedMsg);
                    mMessageAdapter = new MessageAdapter(mUserId, mChatArray);
                }
            }
        }

        // Attach the adapter to a ListView
        mListView.setAdapter(mMessageAdapter);
        mMessageAdapter.setOnItemClickListener(mOnClickListener);
        mListView.scrollToPosition(mMessageAdapter.getItemCount() - 1);

        setupPubButtons();

        // Listen to updates to receive address of the other user with every message.
        listen(HELIOS_DIRECT_CHAT_PROTO);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                mMessageAdapter.notifyDataSetChanged();
                // If user has not scrolled, scroll to last
                if (!mHasUserScrolledList) {
                    mHandler.postDelayed(() -> {
                        mListView.scrollToPosition(mMessageAdapter.getItemCount() - 1);
                        hideScrollToBottom();
                    }, 1);
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        mMessageAdapter.notifyDataSetChanged();
        mMessageAdapter.setOnItemClickListener(mOnClickListener);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter("helios_message"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        mMessageAdapter.setOnItemClickListener(null);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");

        Log.d(TAG, "removing receiver.");
        HeliosMessagingServiceHelper.getInstance().removeDirectReceiver(HELIOS_DIRECT_CHAT_PROTO);
        mChatMessageStore.closeDatabase();
        super.onDestroy();

    }

    private void setupPubButtons() {
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendBtn = (Button) findViewById(R.id.sendButton);
        mMediaPickerBtn = (ImageButton) findViewById(R.id.mediaPickerButton);

        // Disable sending if we don't know receiver UUID
        if (mReceiverUUID == null) {
            mMediaPickerBtn.setEnabled(false);
        }

        mMessageEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //Log.d(TAG, "onTextChanged " + s.toString());
                if (!TextUtils.isEmpty(s.toString())) {
                    Log.d(TAG, "disableSendButton");
                    mSendBtn.setEnabled(true);
                } else {
                    Log.d(TAG, "enableSendButton");
                    mSendBtn.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                String msg = mMessageEditText.getText().toString();
                Log.d(TAG, "onclick " + msg);
                sendMessage(createHeliosMessagePart(msg));
                mMessageEditText.setText("");

                // Disable further sends until we finish sending this.
                mMessageEditText.setEnabled(false);
                mSendBtn.setEnabled(false);
                mMediaPickerBtn.setEnabled(false);
            }
        });

        mMediaPickerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.d(TAG, "mMediaPickerBtn onclick()");
                chooseMediaFileToSend();
            }
        });
    }

    private HeliosMessagePart createHeliosMessagePart(String message) {
        String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
        HeliosMessagePart msg = new HeliosMessagePart(message, mUserName, mUserId, mReceiverUUID, ts);
        return msg;
    }

    private void viewMediaFile(String mediaFileName) {
        Log.d(TAG, "viewMediaFile: " + mediaFileName);

        if (mediaFileName.endsWith(".mp4")) {
            Intent i = new Intent(this, MediaViewActivity.class);
            // Only send media file name, not full path.
            i.putExtra(Intent.EXTRA_TEXT, mediaFileName);
            startActivity(i);
        } else {
            Log.d(TAG, "Full screen view of other media files not available yet.");
        }
    }

    private HeliosMessagingReceiver mHeliosMessagingReceiver = new HeliosMessagingReceiver() {
        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull FileDescriptor fd) {
            // Implemented in MessagingService.
        }

        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull byte[] data) {
            if (mReceiverNetworkId == null) {
                updateUserInfo();
            }
            Log.d(TAG, "mReceiverNetworkId:" + mReceiverNetworkId);
            Log.d(TAG, "receiveMessage()");
            Log.d(TAG, "address:" + address);
            Log.d(TAG, "address:" + address.getNetworkAddress());
            Log.d(TAG, "protocolId:" + protocolId);

            if (HELIOS_DIRECT_CHAT_PROTO.equals(protocolId)) {
                // This is only useful if we don't have a chat with the user, otherwise we have the data already.
                if (address.getNetworkId().equals(mReceiverNetworkId)) {
                    // Store information about where we received message.
                    mHeliosNetworkAddress = address;
                    mLastReceivedFromNetworkAddress = "NetworkId: " + address.getNetworkId();
                    mLastReceivedFromNetworkAddress += "\n\nNetworkAddress: " + address.getNetworkAddress();
                    mLastReceivedFromNetworkAddress += "\n\nPublicKey: " + address.getPublicKey();

                    Log.d(TAG, "mChatArray:" + mChatArray);
                    if (mChatArray != null) {
                        String message = new String(data, StandardCharsets.UTF_8);
                        String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());

                        HeliosMessagePart receivedMsg = new HeliosMessagePart(message, mReceiverName, mReceiverUUID, mUserId, ts);
                        // Update message to temporary cache
                        mChatArray.add(receivedMsg);
                        mHandler.postDelayed(runnable, 1);

                        // Check if we know the user now?
                        HeliosIdentityInfo userInfo = HeliosMessagingServiceHelper.getInstance().getUserDataByNetworkId(mReceiverNetworkId);
                        if (userInfo != null) {
                            mReceiverName = userInfo.getNickname();
                            // It seems we have the UUID for this user now, should update to normal chat. Notify user.
                            mHandler.postDelayed(mRunnableShowUserKnownDialog, 1);
                            return;
                        }
                    } else {
                        Log.d(TAG, "Not using temporary chats.");
                    }

                    // If user has not scrolled, scroll to last
                    if (!mHasUserScrolledList) {
                        mHandler.postDelayed(() -> {
                            mListView.scrollToPosition(mMessageAdapter.getItemCount() - 1);
                            hideScrollToBottom();
                        }, 1);
                    } else {
                        // This receiver is not called from the main thread
                        mHandler.postDelayed(() -> checkUnseenMessages(), 1);
                    }
                } else {
                    Log.e(TAG, "Error: received from does not match.");
                }
            }

            mHandler.postDelayed(runnable, 1);
        }
    };

    private Runnable mRunnableShowUserKnownDialog = new Runnable() {
        public void run() {
            showUserKnownDialog();
        }
    };

    private boolean updateUserInfo() {
        boolean res = false;
        String userNetworkId = HeliosMessagingServiceHelper.getInstance().getUserNetworkIdByUUID(mReceiverUUID);
        if (userNetworkId != null) {
            mReceiverNetworkId = userNetworkId;
            Log.d(TAG, "updateUserInfo: updated new networkId for user: " + mReceiverNetworkId);

            // Update also name if available
            HeliosIdentityInfo userInfo = HeliosMessagingServiceHelper.getInstance().getUserDataByNetworkId(mReceiverNetworkId);
            if (userInfo != null) {
                Log.d(TAG, "updateUserInfo userInfo: " + userInfo);
                mReceiverName = userInfo.getNickname();

                mHandler.postDelayed(() -> {
                    // Label the chat with the  user's set name
                    getSupportActionBar().setTitle(mReceiverName);
                }, 1);
            }
            res = true;
        }

        return res;
    }

    private void showUserNetworkIdNotKnownDialog() {
        // TODO: Strings to res
        AlertDialog.Builder builder = new AlertDialog.Builder(DirectChatActivity.this);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });

        builder.setMessage("Messages cannot be delivered before we receive this user's networkId. Messages sent will be synced once we have the networkId.")
                .setTitle("Could not determine user networkId.");
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showUserKnownDialog() {
        // TODO: Strings to res
        AlertDialog.Builder builder = new AlertDialog.Builder(DirectChatActivity.this);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Toast.makeText(DirectChatActivity.this, "User's nickname is:" + mReceiverName, Toast.LENGTH_LONG).show();
            }
        });

        builder.setMessage("We now have user's HELIOS ID. Please exit from the temporary chat and re-join the chat to persist messages. This user's nickname is: " + mReceiverName)
                .setTitle("HELIOS ID received.");
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private Runnable mNetworkIdNotKnown = new Runnable() {
        public void run() {
            // TODO: Strings to res
            Toast.makeText(DirectChatActivity.this, "Cannot deliver message before we receive current networkId for user. Messages will be sent after that.", Toast.LENGTH_LONG).show();
        }
    };

    private Runnable mMessageSendFailed = new Runnable() {
        public void run() {
            mSendBtn.setEnabled(true);
            mMediaPickerBtn.setEnabled(true);

            mMessageEditText.setEnabled(true);
            // TODO: Strings to res
            Toast.makeText(DirectChatActivity.this, "Sending message failed. Message will be synced automatically. Try again directly by tapping the message. ", Toast.LENGTH_LONG).show();
        }
    };

    private Runnable mMessageSendOk = new Runnable() {
        public void run() {
            mMessageEditText.setText("");
            mSendBtn.setEnabled(true);
            mMediaPickerBtn.setEnabled(true);
            mMessageEditText.setEnabled(true);
            mMessageAdapter.notifyDataSetChanged();
        }
    };

    private Runnable runnable = new Runnable() {
        public void run() {
            mMessageAdapter.notifyDataSetChanged();
        }
    };

    // Add a receiver for messages.
    private void listen(String proto) {
        Log.d(TAG, "Listening for direct connections.");
        HeliosMessagingServiceHelper.getInstance().addDirectReceiver(proto, mHeliosMessagingReceiver);
    }

    /**
     * Used to send a message when user pushes send or selects a file to send.
     *
     * @param msg HeliosMessagePart
     */
    private void sendMessage(HeliosMessagePart msg) {
        // For now we use the networkId to send
        HeliosNetworkAddress addr = new HeliosNetworkAddress();
        addr.setNetworkId(mReceiverNetworkId);
        Log.d(TAG, "sendMessage to addr:" + addr.getNetworkId());
        Log.d(TAG, "Message mediaFileName: " + msg.mediaFileName);

        /*
        if(mHeliosNetworkAddress != null){
            Log.d(TAG, "sendMessage: previous address received for this user was" + mHeliosNetworkAddress);
        }*/

        try {
            // take file into internal cache if available
            if (msg.mediaFileName != null && msg.mediaFileData != null) {
                mCacheMediaFileData = msg.mediaFileData;
                msg.mediaFileData = null;

                // Save the file to internal storage before sending the first time.
                boolean saveFileRes = HeliosStorageUtils.saveFile(mCacheMediaFileData, getApplicationContext().getFilesDir(), msg.mediaFileName);
                Log.d(TAG, "Save file to internal storage succeeded: " + saveFileRes);
            }

            // Show the message without send succeeded
            mMessageAdapter.addItem(msg);

            mMessageAdapter.notifyDataSetChanged();
            mListView.scrollToPosition(mMessageAdapter.getItemCount() - 1);
            hideScrollToBottom();

            // Send the message
            sendMessageTo(addr, msg);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "getDirectMessaging().sendTo error occurred.");
            Toast.makeText(this.getApplicationContext(), "Could not send direct message to peer.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Actually send the message to the other user. This is called when:
     * 1) Sending the message the first time
     * 2) Re-sending message.
     *
     * @param addr HeliosNetworkAddress
     * @param msg  HeliosMessagePart
     */
    private void sendMessageTo(HeliosNetworkAddress addr, HeliosMessagePart msg) {
        // If we don't have networkId, check if we have it now
        if (mReceiverNetworkId == null) {
            Log.d(TAG, "sendMessageTo cannot deliver messages before we receive networkId.");

            boolean userUpdated = updateUserInfo();
            if (userUpdated) {
                addr.setNetworkId(mReceiverNetworkId);
            } else {
                Log.d(TAG, "sendMessageTo: not known networkId yet.");
                mHandler.postDelayed(mNetworkIdNotKnown, 1500);
            }
        }

        // TODO: proper way to handle this action
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "sendDirect:" + " Pid:" + Process.myPid() + ", Tid:" + Process.myTid());
                try {
                    HeliosMessagingServiceHelper service = HeliosMessagingServiceHelper.getInstance();
                    Log.d(TAG, "msg.mediaFileName :" + msg.mediaFileName);
                    //Log.d(TAG, "msg.mediaFileData :" + msg.mediaFileData);

                    // If this is a file that needs to be sent again??
                    // Try to restore file from internal storage
                    if (msg.mediaFileName != null && mCacheMediaFileData == null) {
                        Log.d(TAG, "Loading file from internal storage to re-send");
                        mCacheMediaFileData = HeliosStorageUtils.getFileBytes(getApplicationContext().getFilesDir(), msg.mediaFileName);
                    }

                    if (msg.mediaFileName != null && mCacheMediaFileData != null) {
                        // Sending a file. HELIOS_DIRECT_CHAT_FILE_PROTO
                        Log.d(TAG, "Sending a file >");
                        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                            // TODO FIX a proper way for file sending
                            outputStream.write(msg.mediaFileName.getBytes(StandardCharsets.UTF_8));
                            outputStream.write(0);
                            outputStream.write(mCacheMediaFileData);

                            Log.d(TAG, "sendDirectFuture HELIOS_DIRECT_CHAT_FILE_PROTO start");
                            msg.protocol = HELIOS_DIRECT_CHAT_FILE_PROTO;
                            Future<Unit> res = service.sendDirectFuture(addr, HELIOS_DIRECT_CHAT_FILE_PROTO, outputStream.toByteArray());
                            res.get(TIMEOUT_SEND_CHAT_FILE_PROTO, TimeUnit.MILLISECONDS);
                            Log.d(TAG, "sendDirectFuture HELIOS_DIRECT_CHAT_FILE_PROTO end");
                        }
                    } else {
                        // Sending a normal message HELIOS_DIRECT_CHAT_PROTO
                        Log.d(TAG, "Sending plain message >");
                        Log.d(TAG, "sendDirectFuture HELIOS_DIRECT_CHAT_PROTO start");

                        msg.protocol = HELIOS_DIRECT_CHAT_PROTO;
                        Future<Unit> res = service.sendDirectFuture(addr, HELIOS_DIRECT_CHAT_PROTO, msg.msg.getBytes());
                        res.get(TIMEOUT_SEND_CHAT_PROTO, TimeUnit.MILLISECONDS);
                        Log.d(TAG, "sendDirectFuture HELIOS_DIRECT_CHAT_PROTO end");
                    }

                    // This is the instance from the adapter/singleton, we can directly update the flag.
                    msg.msgReceived = true;
                    msg.mediaFileData = null;
                    mCacheMediaFileData = null;

                    // Note: We are saving msg to db, other protocols save msg to db in the messaging impl.
                    // Try to store the message to SQLite
                    mChatMessageStore.addMessage(msg);

                    // Inform OK
                    mHandler.postDelayed(mMessageSendOk, 1);
                } catch (Exception e) {
                    msg.mediaFileData = null;

                    // Note: We are saving msg to db, other protocols save msg to db in the messaging impl.
                    // Try to store the message to SQLite
                    mChatMessageStore.addMessage(msg);

                    e.printStackTrace();
                    Log.e(TAG, "sendMessage sendDirectFuture error occurred.");

                    // Remove cache
                    mCacheMediaFileData = null;
                    // Inform failure
                    mHandler.postDelayed(mMessageSendFailed, 1);
                    return;
                }
            }
        }).start();
    }

    private void chooseMediaFileToSend() {
        Intent intent = new Intent();
        intent.setType("*/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        String[] mimeTypes = {"image/jpeg", "image/png", "video/mp4"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        Log.d(TAG, "sending pick intent");

        startActivityForResult(Intent.createChooser(intent, "Select Picture/Video"), PICK_MEDIA_FILE);
    }

    private long getFileSize(Uri dataUri) {
        // Check file size with Cursor
        long fileSize = -1;
        Cursor c =
                getContentResolver().query(dataUri, null, null, null, null);
        try {
            int sizeIndex = c.getColumnIndex(OpenableColumns.SIZE);
            c.moveToFirst();
            fileSize = c.getLong(sizeIndex);
        } catch (NullPointerException e) {
            Log.e(TAG, "Media data is not available (read error)");
            e.printStackTrace();
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return fileSize;
    }

    private String getFileExtByDataType(String dataType) {
        //TODO refactor check
        String fileExt = "";
        if (TextUtils.isEmpty(dataType)) {
            Log.e(TAG, "dataType empty or null.");
            return fileExt;
        }

        switch (dataType) {
            case "video/mp4":
                fileExt = ".mp4";
                break;
            case "image/jpeg":
                fileExt = ".jpg";
                break;
            case "image/png":
                fileExt = ".png";
                break;
            default:
                Log.e(TAG, "Unknown MimeType/file extension.");
        }

        return fileExt;
    }

    private String getDescriptionByFileExt(String fileExt) {
        //TODO refactor check
        String description = "";
        if (TextUtils.isEmpty(fileExt)) {
            Log.e(TAG, "fileExt empty or null.");
            return description;
        }

        switch (fileExt) {
            case ".mp4":
                description = "Video>";
                break;
            case ".jpg":
            case ".png":
                description = "Image>";
                break;
            default:
                Log.e(TAG, "Unknown file extension.");
        }

        return description;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA_FILE && resultCode == Activity.RESULT_OK) {
            Uri dataUri = data.getData();
            if (data == null || dataUri == null) {
                Log.e(TAG, "Media data is not available (intent was null)");
                return;
            }

            long fileSize = getFileSize(dataUri);
            Log.d(TAG, "fileSize:" + fileSize);
            if (fileSize > MessagingConstants.MAX_UPLOAD_SIZE_BYTES) {
                Toast.makeText(this.getApplicationContext(), "File size is too large.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "File size is too large: " + fileSize + " MAX:" + MessagingConstants.MAX_UPLOAD_SIZE_BYTES);
                return;
            } else if (fileSize <= 0) {
                Toast.makeText(this.getApplicationContext(), "File size is zero or undetermined.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "File size returned is <=0.");
                return;
            }

            String fileExt = getFileExtByDataType(getContentResolver().getType(dataUri));
            Log.d(TAG, "fileExt:" + fileExt);
            if (TextUtils.isEmpty(fileExt)) {
                Log.e(TAG, "Could not determine MimeType/file extension.");
                Toast.makeText(this.getApplicationContext(), "Could not determine MimeType/file extension.", Toast.LENGTH_SHORT).show();
                return;
            }
            String messageMediaTypeDesc = getDescriptionByFileExt(fileExt);

            try (
                    DataInputStream inputStream = new DataInputStream(getContentResolver().openInputStream(dataUri))
            ) {
                Log.d(TAG, "Input stream opened");
                HeliosMessagePart message = createHeliosMessagePart(messageMediaTypeDesc);
                String timeString = ZonedDateTime.now().format(DateTimeFormatter.ofPattern(HeliosStorageUtils.HELIOS_DATETIME_PATTERN));
                String fileNameNew = "helios-" + timeString + fileExt;
                message.mediaFileName = fileNameNew;
                message.mediaFileData = new byte[Math.toIntExact(fileSize)];

                // Read the _entire_ file into a blob
                inputStream.readFully(message.mediaFileData);
                sendMessage(message);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Media file is not available (file not found): " + e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, "Media file is not available (read error): " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                this.finish();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

}
