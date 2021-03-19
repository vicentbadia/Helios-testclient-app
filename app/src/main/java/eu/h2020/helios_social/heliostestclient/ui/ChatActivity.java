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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import eu.h2020.helios_social.core.messaging.MessagingConstants;
import eu.h2020.helios_social.core.messaging.db.HeliosMessageStore;
import eu.h2020.helios_social.core.storage.HeliosStorageUtils;
import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.heliostestclient.service.HeliosMessagingServiceHelper;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.HeliosMessagingException;
import eu.h2020.helios_social.core.messaging.HeliosTopic;
import eu.h2020.helios_social.core.profile.HeliosUserData;
import eu.h2020.helios_social.heliostestclient.ui.adapters.MessageAdapter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Activity for enabling chat functionality. Presents the messages in the chat in a list view.
 * <p>
 * Enables user to send messages.
 */
public class ChatActivity extends BaseChatActivity {
    public static final String CHAT_ID = ChatActivity.class.getCanonicalName() + "CHATID";
    private static final String TAG = "ChatActivity";
    private static final int PICK_MEDIA_FILE = 3113;

    private Button mSendBtn;
    private EditText mMessageEditText;
    private ImageButton mMediaPickerBtn;
    private String mChatId;
    private String mUserName;
    private String mUserId;
    private HeliosMessageStore mChatMessageStore;
    private int mExpirationTimeAsDays = 7; // Messages older than 7 days are removed from SQLite

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUserName = HeliosUserData.getInstance().getValue(getString(R.string.setting_username));
        mUserId = HeliosUserData.getInstance().getValue(getString(R.string.setting_user_id));
        if (TextUtils.isEmpty(mUserName)) {
            mUserName = mUserId;
        }

        mChatId = this.getIntent().getStringExtra(CHAT_ID);

        // Create connection to a database and remove all expired entries
        mChatMessageStore = new HeliosMessageStore(this.getApplicationContext());
        mChatMessageStore.deleteExpiredEntries(ZonedDateTime.now().minusDays(mExpirationTimeAsDays).toInstant().toEpochMilli());

        if (!TextUtils.isEmpty(mChatId)) {
            Log.d(TAG, "Add stuff to chat " + mChatId);
            ArrayList<HeliosMessagePart> oldMessages = mChatMessageStore.loadMessages(mChatId);
            Log.d(TAG, "Old message list size is  " + oldMessages.size());
            HeliosConversation conversation = HeliosConversationList.getInstance().getConversation(mChatId);

            if (conversation != null) {
                Log.d(TAG, "messages size is  " + conversation.messages.size());
                conversation.joinMessages(oldMessages, HeliosConversation.JoinLocation.PREPEND);
                Log.d(TAG, "Old messages added");
                Log.d(TAG, "messages size is now  " + conversation.messages.size());

                getSupportActionBar().setTitle(mChatId);
                mListView = (RecyclerView) findViewById(R.id.messageListView);

                mMessageAdapter = new MessageAdapter(mUserId, conversation.messages);
                mMessageAdapter.setOnItemClickListener(mOnClickListener);
                mListView.setAdapter(mMessageAdapter);
                mListView.scrollToPosition(mMessageAdapter.getItemCount() - 1);

                setupPubButtons();
            } else {
                mMessageAdapter = new MessageAdapter(mUserId, new ArrayList<>());
                mMessageAdapter.setOnItemClickListener(mOnClickListener);
                mListView.setAdapter(mMessageAdapter);

                AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ChatActivity.this.finish();
                    }
                });

                builder.setMessage("Could not load group chat data for (" + mChatId + "). Please report this problem and try to open the chat again.")
                        .setTitle("Error opening chat.");
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        }

        // Send join message
        //HeliosMessagePart joinMsg = createHeliosMessagePart("has arrived.", HeliosMessagePart.MessagePartType.JOIN);
        //sendMessage(joinMsg);
    }

    private MessageAdapter.OnItemClickListener mOnClickListener = new MessageAdapter.OnItemClickListener() {
        @Override
        public void onClick(HeliosMessagePart msg) {
            Log.d(TAG, "onItemClick ");

            if (!TextUtils.isEmpty(msg.mediaFileName)) {
                viewMediaFile(msg.mediaFileName);
            }

            openPrivateChat(msg);
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String topic = intent.getStringExtra("topic");
                if (!TextUtils.isEmpty(topic) && mChatId.equals(topic)) {
                    mMessageAdapter.notifyDataSetChanged();

                    // If user has not scrolled, smooth scroll to last
                    if (!mHasUserScrolledList) {
                        mListView.scrollToPosition(mMessageAdapter.getItemCount() - 1);
                        hideScrollToBottom();
                    } else {
                        checkUnseenMessages();
                    }
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
        // Update unseen messages, if user has scrolled and not initial item visible
        if (mLastVisiblePosition != 0 && mHasUserScrolledList) {
            checkUnseenMessages();
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter("helios_message"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
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
        super.onDestroy();
        mChatMessageStore.closeDatabase();
    }

    private void setupPubButtons() {
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendBtn = (Button) findViewById(R.id.sendButton);
        mMediaPickerBtn = (ImageButton) findViewById(R.id.mediaPickerButton);
        // FIXME Disabled media sending in group chat for now
        mMediaPickerBtn.setVisibility(View.INVISIBLE);
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
                Log.d(TAG, "send onclick " + msg);
                sendMessage(createHeliosMessagePart(msg));
                mMessageEditText.setText("");
            }
        });
    }

    private HeliosMessagePart createHeliosMessagePart(String message) {
        return createHeliosMessagePart(message, HeliosMessagePart.MessagePartType.MESSAGE);
    }

    private HeliosMessagePart createHeliosMessagePart(String message, HeliosMessagePart.MessagePartType messageType) {
        String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
        HeliosMessagePart msg = new HeliosMessagePart(message, mUserName, mUserId, mChatId, ts, messageType);
        return msg;
    }


    private void openPrivateChat(HeliosMessagePart msg) {
        // If this is the user's message
        if (mUserId.equals(msg.senderUUID)) {
            return;
        }

        String networkId = HeliosMessagingServiceHelper.getInstance().getUserNetworkIdByUUID(msg.senderUUID);
        Log.d(TAG, "networkId is:" + networkId + " for user:" + msg.senderName + " " + msg.senderUUID);

        // If we have information for this user, open a direct chat
        if (networkId == null) {
            Toast.makeText(this.getApplicationContext(), "Could not determine peer id.", Toast.LENGTH_LONG).show();
        } else {
            Intent i = new Intent(ChatActivity.this, DirectChatActivity.class);
            i.putExtra(DirectChatActivity.CHAT_NETWORK_ID, networkId);
            startActivity(i);
        }
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

    private void sendMessage(HeliosMessagePart msg) {
        /*
        Intent intent = new Intent("helios_message_user");
        intent.putExtra("key", msg.msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);*/

        try {
            HeliosMessagingServiceHelper.getInstance().publish(new HeliosTopic(mChatId, mChatId), msg);
        } catch (HeliosMessagingException e) {
            e.printStackTrace();
        }
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

            try {
                /*
                //TODO: Compress the content to be sent and remove any attributes
                //-- compress images, e.g. for JPEG
                Bitmap bit = BitmapFactory.decodeStream(inputStream);
                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                bit.compress(Bitmap.CompressFormat.JPEG, 100, bao);
                byte[] bytes = bao.toByteArray();
                */

                // For now, store using set paths instead of using ContentProviders or sending bytes
                // to Messaging module. To be refactored.
                // Now to internal storage, could use app-internal with this.getApplicationContext().getFilesDir()
                // or this.getApplicationContext.().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                String timeString = ZonedDateTime.now().format(DateTimeFormatter.ofPattern(HeliosStorageUtils.HELIOS_DATETIME_PATTERN));
                String fileName = "helios-" + timeString + fileExt;

                boolean saveFileRes;
                try (InputStream inputStream = getContentResolver().openInputStream(dataUri)) {
                    Log.d(TAG, "Input stream opened");
                    //TODO Update if stored otherwise.
                    saveFileRes = HeliosStorageUtils.saveFile(inputStream, this.getApplicationContext().getFilesDir(), fileName);
                }

                if (saveFileRes) {
                    HeliosMessagePart message = createHeliosMessagePart(messageMediaTypeDesc);
                    // Name of the file would not matter actually, receiver renames files on receive.
                    message.mediaFileName = fileName;
                    sendMessage(message);
                    Log.d(TAG, "Media file sent.");
                } else {
                    Log.e(TAG, "Error saving media file.");
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Media file is not available (file not found)");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, "Media file is not available (read error)");
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

            case R.id.show_users_online:
                Intent i = new Intent(this, TopicUsersActivity.class);
                i.putExtra(ChatActivity.CHAT_ID, mChatId);
                startActivity(i);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

}
