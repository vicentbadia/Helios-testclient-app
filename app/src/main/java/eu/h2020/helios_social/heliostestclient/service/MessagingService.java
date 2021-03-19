package eu.h2020.helios_social.heliostestclient.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import eu.h2020.helios_social.core.messaging.HeliosConnect;
import eu.h2020.helios_social.core.messaging.HeliosConnectionInfo;
import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo;
import eu.h2020.helios_social.core.messaging.HeliosMessage;
import eu.h2020.helios_social.core.messaging.HeliosMessageListener;
import eu.h2020.helios_social.core.messaging.HeliosMessaging;
import eu.h2020.helios_social.core.messaging.HeliosMessagingException;
import eu.h2020.helios_social.core.messaging.HeliosTopic;
import eu.h2020.helios_social.core.messaging.HeliosTopicMatch;
import eu.h2020.helios_social.core.messaging.MessagingConstants;
import eu.h2020.helios_social.core.messaging.ReliableHeliosMessagingNodejsLibp2pImpl;
import eu.h2020.helios_social.core.messaging.data.StorageHelperClass;
import eu.h2020.helios_social.core.messaging.db.HeliosMessageStore;
import eu.h2020.helios_social.core.messaging.sync.HeartbeatDataException;
import eu.h2020.helios_social.core.messaging.sync.HeartbeatManager;
import eu.h2020.helios_social.core.messaging.sync.SyncManager;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosEgoTag;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosMessageLibp2pPubSub;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosMessagingNodejsLibp2p;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosMessagingReceiver;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosNetworkAddress;
import eu.h2020.helios_social.core.profile.HeliosProfileManager;
import eu.h2020.helios_social.core.storage.DownloadReadyListener;
import eu.h2020.helios_social.core.storage.HeliosStorageManager;
import eu.h2020.helios_social.core.storage.OperationReadyListener;
import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;
import eu.h2020.helios_social.core.messaging.data.JsonMessageConverter;
import eu.h2020.helios_social.heliostestclient.ui.MainActivity;
import kotlin.Unit;

/**
 * Service that provides background messaging using {@link HeliosMessagingNodejsLibp2p}.
 * <p>
 * For convenience, currently implements the {@link HeliosMessaging} interface.
 * <p>
 * Takes care of loading and storing all the messages exchanged by users and notifying of new
 * messages received while the application is not running (thus this service is running as a
 * foreground Service that the user can close from the visible notification).
 */
public class MessagingService extends Service implements HeliosMessaging, HeliosConnect {
    private static final String TAG = "MessagingService";
    private static final String JSON_FILE_NAME = "JSON_MSG_STORE";
    public static final String HELIOS_DEFAULT_CHAT_TOPIC = "helios";
    public static final String HELIOS_DEFAULT_BUG_CHAT_TOPIC = "BUG CHAT";
    public static final String START_ACTION = "start_action";
    public static final String STOP_ACTION = "stop_action";
    public static final String FOREGROUND_ACTION = "fg_action";
    public static final String CHANNEL_ID = "HELIOS_messaging";
    public static final String TAG_LIST_UPDATE = "helios_tag_list_update";

    private static final String EGO_MAPPING_SHARED_PREF_NAME = "helios-identity-ego-network-id-mappings";
    private static final String EGO_MAPPING_SHARED_PREF_KEY = "json";
    public static final String GROUP_HELIOS = "eu.h2020.helios_social.HELIOS_MESSAGE_GROUP";
    public static final int ONGOING_NOTIFICATION_ID = 1;
    private int testNotificationId = 0;
    private boolean mInitialized = false;
    private boolean mLoadingMessages = false;
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private boolean mConfigurationChange = false;
    private HeliosMessageListener mListener = null;
    private HeliosReceiver mHeliosReceiver;
    private ReliableHeliosMessagingNodejsLibp2pImpl mHeliosMessagingNodejs = ReliableHeliosMessagingNodejsLibp2pImpl.getInstance();
    private boolean mConnected = false;
    private HashMap<String, HeliosMessageListener> mSubscribers = new HashMap<>();
    private HashMap<String, HeliosMessagingReceiver> mDirectMessageReceivers = new HashMap<>();
    private Hashtable<String, HeliosIdentityInfo> mUserNetworkMap = new Hashtable<>();
    private Hashtable<String, Hashtable<String, HeliosEgoTag>> mHeartbeatUsers = new Hashtable<>();
    private StorageHelperClass mStorageHelper;
    private boolean mFilterHeartbeatMsg = true;
    private boolean mFilterJoinMsg = false;
    private HeliosMessageStore mChatMessageStore;
    private HandlerThread mJsonHandlerThread = new HandlerThread("JsonHandlerThread");
    private Handler mJsonHandler;
    // Heartbeat related
    private HeartbeatManager mHeartbeatManager = HeartbeatManager.getInstance();
    private HeliosIdentityInfo mHeliosIdentityInfo = null;
    private boolean mShouldNotify = false;

    private BroadcastReceiver mBackgroundListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mShouldNotify = intent.getBooleanExtra(MessagingService.FOREGROUND_ACTION, false);
            Log.d(TAG, "HeliosApp receive BG info should notify: " + mShouldNotify);
        }
    };

    private HeliosMessagingReceiver mDirectHeliosMessagingReceiver = new HeliosMessagingReceiver() {
        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull FileDescriptor fd) {
            Log.d(TAG, "MessagingService.direct receiveMessage FileDescriptor()");

            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            try (FileInputStream fileInputStream = new FileInputStream(fd)) {
                int byteRead;
                while ((byteRead = fileInputStream.read()) != -1) {
                    ba.write(byteRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            receiveMessage(address, protocolId, ba.toByteArray());
        }

        private void handleSyncProto(HeliosNetworkAddress address, byte[] data) {
            Log.d(TAG, "Received resend sync from " + address.getNetworkId());
            try {
                String json = new String(data, StandardCharsets.UTF_8);
                Log.d(TAG, "Received resend sync: " + json);
                HeliosMessagePart msg = JsonMessageConverter.getInstance().readHeliosMessagePart(json);
                HeliosTopic topic = new HeliosTopic(msg.to, "");
                HeliosMessage tempMsg = new HeliosMessage(json);

                // Anyways, if we have seen this user, forward it.
                mHeliosReceiver.showMessage(topic, tempMsg);
            } catch (RuntimeException e) {
                Log.e(TAG, "Error handling resend sync proto: " + e.getMessage());
            }
        }

        // Handle synced DM message status, save to HeliosConversationList singleton.
        private void handleSyncAckDM(HeliosNetworkAddress address, String protocolId, String jsonMsg) {
            // Keeping syncMgr with only DB interactions, update singleton on this side.
            HeliosMessagePart msg = JsonMessageConverter.getInstance().readHeliosMessagePart(jsonMsg);
            Log.d(TAG, "handleSyncedDMMessage networkId:" + address.getNetworkId() + " msg uuid:" + msg.to);
            // Check if we actually have conversation with this user
            // TODO: this assumes senderUUID and senderNetworkId are correct and not spoofed.
            HeliosConversation conversation = HeliosConversationList.getInstance().getConversationByTopicUUID(msg.to);
            if (conversation == null) {
                Log.e(TAG, "handleSyncedDMMessage could not find conversation with  uuid:" + msg.to);
                return;
            }
            // Update message to singleton
            conversation.setMessageReceivedValue(msg.getUuid(), true);

            // Notify if receivers
            if (mDirectMessageReceivers.containsKey(MessagingConstants.HELIOS_DIRECT_CHAT_PROTO)) {
                mDirectMessageReceivers.get(MessagingConstants.HELIOS_DIRECT_CHAT_PROTO).receiveMessage(address, protocolId, protocolId.getBytes());
            }
        }

        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull byte[] data) {
            Log.d(TAG, "MessagingService.direct receiveMessage()");
            Log.d(TAG, "address:" + address);
            Log.d(TAG, "protocolId:" + protocolId);
            //Log.d(TAG, "data:" + new String(data, StandardCharsets.UTF_8));

            // TODO a proper way for internal cache of networkIds
            String networkId = address.getNetworkId();
            HeliosIdentityInfo user = getUserDataByNetworkId(networkId);

            // TODO: How to handle if we don't "know" the user?
            if (user != null) {
                String message = null;
                String fileName = null;

                if (MessagingConstants.HELIOS_CHAT_SYNC_PROTO.equals(protocolId)) {
                    handleSyncProto(address, data);
                    return;
                }
                if (MessagingConstants.HELIOS_SYNC_DM_ACK_PROTO.equals(protocolId)) {
                    handleSyncAckDM(address, protocolId, new String(data, StandardCharsets.UTF_8));
                    return;
                }

                // FIXME: Why are we checking this here? Create handlers for different protocols..
                if (MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO.equals(protocolId)) {
                    // 1) Handle HELIOS_DIRECT_CHAT_FILE_PROTO
                    // ---------------------------------------
                    Log.d(TAG, "direct receiveMessage HELIOS_DIRECT_CHAT_FILE_PROTO:");
                    // TODO FIX BELOW, a proper way for file sending/receiving
                    int zPos = 0;
                    for (; zPos < 300 && zPos < data.length; zPos++) {
                        if (data[zPos] == 0) {
                            break;
                        }
                    }
                    String directFileName = new String(data, 0, zPos, StandardCharsets.UTF_8);
                    Log.d(TAG, "new directFileName:" + directFileName);
                    // <---------------


                    fileName = mStorageHelper.generateFileNameByExtension(directFileName);
                    Log.d(TAG, "new fileName:" + fileName);
                    if (!TextUtils.isEmpty(fileName)) {
                        // Write file to the storage
                        //TODO: Refactor this to ContentProvider or other way to deliver the data
                        // to the listener. To some level it could be raw bytes as well from the delivery.getBody().
                        boolean res = mStorageHelper.saveFileExternal(Arrays.copyOfRange(data, zPos + 1, data.length), fileName);
                        if (res) {
                            Log.e(TAG, " file saved");
                            message = "Shared>";
                        } else {
                            Log.e(TAG, "Error saving file.");
                            fileName = null;
                        }
                    } else {
                        Log.e(TAG, "Error saving file, fileExt is empty or null.");
                    }

                } else if (MessagingConstants.HELIOS_DIRECT_CHAT_PROTO.equals(protocolId)) {
                    // 2) Handle HELIOS_DIRECT_CHAT_PROTO
                    // ---------------------------------------
                    Log.d(TAG, "direct receiveMessage HELIOS_DIRECT_CHAT_PROTO:");
                    // Create message form raw data - Could send the JSON also.
                    message = new String(data, StandardCharsets.UTF_8);
                } else {
                    // 3) Handle some other PROTO, not yet.
                    // ---------------------------------------
                    Log.e(TAG, "No handler for protocol, discarding:" + protocolId + ", address:" + address.getNetworkId());
                }

                // If we have message set, it was handled.
                if (!TextUtils.isEmpty(message)) {
                    String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
                    String name = user.getNickname();
                    String uuid = user.getUserUUID();
                    Log.d(TAG, "name:" + name);
                    Log.d(TAG, "uuid:" + uuid);
                    // Save DM messages with sender uuid as the 'to'.
                    HeliosMessagePart receivedMsg = new HeliosMessagePart(message, name, uuid, uuid, ts);
                    HeliosTopic topic = new HeliosTopic(name, "");
                    // Mark message as received
                    receivedMsg.msgReceived = true;
                    HeliosMessage tempMsg;
                    tempMsg = new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(receivedMsg), fileName);

                    // Anyways, if we have seen this user, forward it.
                    mHeliosReceiver.showMessage(topic, tempMsg);
                }
            } else {
                // We don't have the networkId -- UUID mapping.
                // We have not seen the UUID of this user/stored it.
                Log.e(TAG, "No user info/UUID from direct message available, discarding. :" + address.getNetworkId());
            }

            // Check the internal receivers, though already stored above if known
            if (mDirectMessageReceivers.containsKey(protocolId)) {
                // Don't forward files now
                if (MessagingConstants.HELIOS_DIRECT_CHAT_PROTO.equals(protocolId)) {
                    mDirectMessageReceivers.get(protocolId).receiveMessage(address, protocolId, data);
                }
            } else {
                Log.d(TAG, "No internal receiver for protocolId: " + protocolId);
            }
        }
    };

    private HeliosMessagingReceiver mDirectHeliosSyncReceiver = new HeliosMessagingReceiver() {
        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress heliosNetworkAddress, @NotNull String s, @NotNull byte[] bytes) {

        }

        @Override
        public void receiveMessage(@NotNull HeliosNetworkAddress address, @NotNull String protocolId, @NotNull FileDescriptor fd) {
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            try (FileInputStream fileInputStream = new FileInputStream(fd)) {
                int byteRead;
                while ((byteRead = fileInputStream.read()) != -1) {
                    ba.write(byteRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            receiveMessage(address, protocolId, ba.toByteArray());
        }
    };


    public MessagingService() {
    }


    @Override
    public void onCreate() {
        super.onCreate();
        // The service is being created
        Log.d(TAG, "MessagingService.onCreate()");

        // TODO create a helper class for notifications
        // Create the NotificationChannel, but only on API 26+ (8/Oreo +) because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //CharSequence name = getString(R.string.channel_name);
            //String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "HELIOS_Message_Channel", importance);
            channel.setDescription("HELIOS Channel description");
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Log.d(TAG, "Identity info");
        mHeliosIdentityInfo = HeliosProfileManager.getInstance().getIdentityInfo(this.getApplicationContext());
        Log.d(TAG, "startForeground");
        startForeground(ONGOING_NOTIFICATION_ID, getNotification());

        mStorageHelper = new StorageHelperClass(this.getApplicationContext());
        mChatMessageStore = new HeliosMessageStore(this.getApplicationContext());

        mHeliosMessagingNodejs.setContext(this.getApplicationContext());
        mHeliosMessagingNodejs.setFilterJoinMsg(false);

        //HandlerThread handlerThread = new HandlerThread(TAG);
        //handlerThread.start();
        //mServiceHandler = new Handler(handlerThread.getLooper());

        // TODO: loadJson is using this thread. This is tempoary solution and should be removed
        mJsonHandlerThread.start();
        mJsonHandler = new Handler(mJsonHandlerThread.getLooper());

        // HEARTBEAT
        // TODO: Is it possible to move this to private constructor?
        mHeartbeatManager.init();

        mHeliosReceiver = new HeliosReceiver();
        loadJson();
        loadUserNetworkMappings();

        mInitialized = true;
        LocalBroadcastManager.getInstance(this).registerReceiver(mBackgroundListener, new IntentFilter(MessagingService.FOREGROUND_ACTION));
    }


    private void saveJson() {
        // Don't save if we are still loading previous messages.
        if (mLoadingMessages) {
            return;
        }

        try {
            // Should block/copy this instead
            String list = JsonMessageConverter.getInstance().convertConversationListToJson(HeliosConversationList.getInstance().getConversations());

            long res = HeliosStorageManager.getInstance().uploadSync(JSON_FILE_NAME, list.getBytes(), new OperationReadyListener() {
                @Override
                public void operationReady(Long aLong) {
                    Log.d(TAG, "upload result " + String.valueOf(aLong));
                }
            });

            Log.d(TAG, "saveJson res " + res);
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "error saving json " + e.toString());
        }
    }

    private void loadJson() {
        Log.d(TAG, "loadJson()");
        // Since our service is in the main app process for now, just use the cache singleton to store messages.

        mLoadingMessages = true;
        HeliosStorageManager.getInstance().download(JSON_FILE_NAME, new DownloadReadyListener() {
            @Override
            public void downloadReady(String s, ByteArrayOutputStream byteArrayOutputStream) {
                //Log.d(TAG, "downloadReady result " + s + " " + String.valueOf(byteArrayOutputStream));
                Log.d(TAG, "downloadReady " + s);

                // Load current file, just log errors.
                ArrayList<HeliosConversation> arr = null;
                try {
                    arr = JsonMessageConverter.getInstance().readConversationList(String.valueOf(byteArrayOutputStream));
                } catch (JsonParseException e) {
                    Log.e(TAG, "JsonParseException while reading JSON file: " + e.toString());
                }

                ArrayList<HeliosTopicContext> msgStoreTopics = mChatMessageStore.loadDirectMessageTopics();

                // Load or create new structure.
                if (null != arr && !arr.isEmpty()) {
                    Log.d(TAG, "Loaded Conversations, size: " + arr.size());
                    HeliosConversationList.getInstance().replaceConversations(arr);

                    //TODO: DEBUG REMOVE: If no default topic, create it
                    // This is mainly for updating from previous app versions
                    ArrayList<HeliosTopicContext> arrTopics = HeliosConversationList.getInstance().getTopics();
                    boolean defaultTopicExists = false;
                    boolean defaultBugTopicExists = false;
                    for (int i = 0; i < arrTopics.size(); i++) {
                        HeliosTopicContext topic = arrTopics.get(i);
                        if (HELIOS_DEFAULT_CHAT_TOPIC.equals(topic.topic)) {
                            defaultTopicExists = true;
                        }
                        if (HELIOS_DEFAULT_BUG_CHAT_TOPIC.equals(topic.topic)) {
                            defaultBugTopicExists = true;
                        }

                        // Check consistency with mChatMessageStore messages
                        if (!TextUtils.isEmpty(topic.uuid)) {
                            for (int a = msgStoreTopics.size() - 1; a >= 0; a--) {
                                if (topic.uuid.equals((msgStoreTopics.get(a).uuid))) {
                                    msgStoreTopics.remove(a);
                                }
                            }
                        }
                    }
                    // Creating default topics, if not existing
                    if (!defaultTopicExists) {
                        createDefaultConversation(HELIOS_DEFAULT_CHAT_TOPIC);
                    }
                    if (!defaultBugTopicExists) {
                        createDefaultConversation(HELIOS_DEFAULT_BUG_CHAT_TOPIC);
                    }
                } else {
                    Log.d(TAG, "Loaded Conversations empty. Creating default.");
                    createDefaultConversation(HELIOS_DEFAULT_CHAT_TOPIC);
                    createDefaultConversation(HELIOS_DEFAULT_BUG_CHAT_TOPIC);
                }

                // If DMs available that were not loaded from json
                for (HeliosTopicContext bTopic : msgStoreTopics) {
                    Log.d(TAG, "##restoring DM chat topic:" + bTopic.topic);
                    // Load topic info
                    ArrayList<HeliosMessagePart> oldMessages = mChatMessageStore.loadMessages(bTopic.uuid);
                    String lastMsg = "";
                    String participants = "";
                    String ts = "";
                    if (!oldMessages.isEmpty()) {
                        HeliosMessagePart latestMsg = oldMessages.get(oldMessages.size() - 1);
                        lastMsg = latestMsg.msg;
                        participants = latestMsg.senderName + ": " + latestMsg.msg;
                        ts = latestMsg.getLocaleTs();
                    }

                    HeliosConversation loadedConversation = new HeliosConversation();
                    loadedConversation.topic = new HeliosTopicContext(bTopic.topic, lastMsg, participants, ts);
                    loadedConversation.topic.uuid = bTopic.uuid;
                    HeliosConversationList.getInstance().addConversation(loadedConversation);
                }

                mLoadingMessages = false;

                // If for some reason listener is not attached yet
                if (mListener != null) {
                    showMessageToListener(null, null);

                    if (mListener instanceof MessagingServiceStatusListener) {
                        ((MessagingServiceStatusListener) mListener).onDataLoaded();
                    }
                } else {
                    Log.e(TAG, "loadJson -- mListener is null.");
                    mJsonHandler.postDelayed(() -> {
                        Log.d(TAG, "loadJson -- handler.");
                        if (mListener != null) {
                            Log.e(TAG, "loadJson -- handler send onDataLoaded.");
                            showMessageToListener(null, null);

                            if (mListener instanceof MessagingServiceStatusListener) {
                                ((MessagingServiceStatusListener) mListener).onDataLoaded();
                            }
                        }
                    }, 100);
                }
            }
        });
    }

    private void createDefaultConversation(String topic) {
        HeliosConversation defaultConversation = new HeliosConversation();
        defaultConversation.topic = new HeliosTopicContext(topic, "-", "-", "-");

        HeliosConversationList.getInstance().addConversation(defaultConversation);
    }

    /*
    public void requestStartService() {
        Log.d(TAG, "requestStartService");
        startService(new Intent(getApplicationContext(), MessagingService.class).setAction(MessagingService.START_ACTION));
        //Intent startIntent = new Intent(MainActivity.this, MessagingService.class);
        //startIntent.setAction(MessagingService.START_ACTION);
    }*/

    // Show notification of a message received.
    private void showNotification(String title, String message) {
        Log.d(TAG, "showNotification title:" + title + " message:" + message);

        Context ctx = this.getApplicationContext();
        Intent notificationIntent = new Intent(ctx, MainActivity.class);
        // Open the app to the state it was in if opened.
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(ctx, 0, notificationIntent, 0);

        // On Android 7 N (API 24) and higher the system groups notifications. Let's not make
        // a separate group for normal notifications now.
        // Build the notification and add the action.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // Issue the notification.
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);
        notificationManager.notify(++testNotificationId, builder.build());
    }

    private Notification getServiceNotification() {
        Context ctx = this.getApplicationContext();
        Intent notificationIntent = new Intent(ctx, MainActivity.class);
        // Open the app to the state it was in if opened.
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(ctx, 0, notificationIntent, 0);

        Intent stopIntent = new Intent(ctx, MessagingService.class);
        stopIntent.setAction(MessagingService.STOP_ACTION);
        PendingIntent pendingIntentStop = PendingIntent.getService(ctx, 0,
                stopIntent, 0);

        // Group this notification to GROUP_HELIOS -- in order to show Service notification
        // separately from other notifications.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setColor(Color.GREEN)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("HELIOS is connected and running in the background.")
                .setContentText("Tap below to disconnect.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(GROUP_HELIOS)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_delete, "Disconnect/Stop Service",
                        pendingIntentStop);

        return builder.build();
    }

    private Notification getNotification() {
        Context ctx = this.getApplicationContext();
        Intent notificationIntent = new Intent(ctx, MainActivity.class);
        // Open the app to the state it was in if opened.
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(ctx, 0, notificationIntent, 0);

        // Group this notification to GROUP_HELIOS -- in order to show Service notification
        // separately from other notifications.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setColor(Color.GREEN)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("HELIOS")
                .setContentText("Application running.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(GROUP_HELIOS)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        return builder.build();
    }

    /**
     * Start/stop the service using intent.
     * Starts the service if receives: {@link MessagingService#START_ACTION}.
     * Stops the service and closes connections if receives: {@link MessagingService#STOP_ACTION}.
     *
     * @param intent  Intent to deliver message.
     * @param flags   flags
     * @param startId startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        // If system has restarted this because it was destroyed, intent will be null.
        if (intent == null) {
            return START_STICKY;
        }

        // The service is starting, due to a call to bindService()
        if (intent.getAction().equals(MessagingService.START_ACTION)) {
            Log.d(TAG, "MessagingService.START_ACTION");
            Log.d(TAG, "startForeground");
            startForeground(ONGOING_NOTIFICATION_ID, getNotification());
            return START_STICKY;
        } else if (intent.getAction().equals(MessagingService.STOP_ACTION)) {
            Log.d(TAG, "MessagingService.STOP_ACTION");

            sendMessageToDefaultTopic(HeliosMessagePart.MessagePartType.LEAVE, "is no longer online.");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            stopForeground(true);
            stopSelf();

            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mConfigurationChange = true;
        Log.d(TAG, "onConfigurationChanged, new orientation:" + newConfig.toString());
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        mConfigurationChange = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind()");
        setAppNotification();

        mConfigurationChange = false;
        super.onRebind(intent);
    }

    private void setAppNotification() {
        Context ctx = this.getApplicationContext();
        NotificationManagerCompat mgr =
                NotificationManagerCompat.from(ctx);
        mgr.notify(ONGOING_NOTIFICATION_ID, getNotification());
    }

    private void setServiceNotification() {
        Context ctx = this.getApplicationContext();
        NotificationManagerCompat mgr =
                NotificationManagerCompat.from(ctx);
        mgr.notify(ONGOING_NOTIFICATION_ID, getServiceNotification());
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        // Client has exited and no configuration change.
        if (!mConfigurationChange) {
            mListener = null;
            setServiceNotification();
        }

        // To receive onRebind()
        return true;
    }

    /**
     * Set listener for delivering messages.
     *
     * @param list {@link HeliosMessageListener}
     */
    public void setHeliosMessageListener(HeliosMessageListener list) {
        Log.d(TAG, "setHeliosMessageListener: " + list);
        this.mListener = list;

        // When listener changes, update it to all current subscribers.
        for (String topic : mSubscribers.keySet()) {
            mSubscribers.replace(topic, null);
        }
    }

    private boolean storeHeliosMessage(HeliosTopic heliosTopic, HeliosMessagePart msg, boolean notify, boolean isDirectMessage, HeliosNetworkAddress senderAddress) {
        boolean stored = false;
        // Update message to singleton
        ArrayList<HeliosConversation> conversationList = HeliosConversationList.getInstance().getConversations();
        for (int i = 0; i < conversationList.size(); i++) {
            HeliosConversation conversation = conversationList.get(i);
            if (isDirectMessage) {
                // If uuid, it is direct chat
                if (!TextUtils.isEmpty(conversation.topic.uuid)) {
                    Log.d(TAG, "topic.uuid  " + conversation.topic.uuid);
                    Log.d(TAG, "msg.senderUUID  " + msg.senderUUID);
                    if (conversation.topic.uuid.equals(msg.senderUUID)) {
                        Log.d(TAG, "update message to topic " + conversation.topic.topic);
                        Log.d(TAG, "update message to uuid " + conversation.topic.uuid);
                        stored = conversation.addMessage(msg);
                        if (stored)
                            mChatMessageStore.addMessage(msg);
                        break;
                    }
                }
            } else {
                if (conversation.topic.topic.equals(heliosTopic.getTopicName())) {
                    Log.d(TAG, "update message to topic " + heliosTopic.getTopicName());
                    if ((msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT) && (senderAddress != null)) {
                        try {
                            List<HeliosMessagePart> hasMissing = mHeartbeatManager.collectMissingMessages(msg, conversation);
                            if (!hasMissing.isEmpty()) {
                                for (HeliosMessagePart missingMsg : hasMissing) {
                                    Log.i(TAG, "Sender " + msg.senderName + " is missing " + missingMsg.msg);
                                }
                                // Trigger a sync message to heartbeat sender
                                SyncManager syncMgr = SyncManager.getInstance();
                                syncMgr.syncMessages(hasMissing, senderAddress);
                            }
                        } catch (HeartbeatDataException e) {
                            Log.d(TAG, "Heartbeat message without payload");
                        }
                    }

                    // Check if we need to sync direct messages to this user
                    //TODO: Now only with HEARTBEAT or JOIN, should check when user is actually online.
                    Log.d(TAG, "update msg.messageType:" + msg.messageType);
                    //Log.d(TAG, "msg.senderUUID:" + msg.senderUUID);
                    //Log.d(TAG, "mHeliosIdentityInfo.getUserUUID:" + mHeliosIdentityInfo.getUserUUID());
                    if (msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT || msg.messageType == HeliosMessagePart.MessagePartType.JOIN) {
                        SyncManager syncMgr = SyncManager.getInstance();
                        syncMgr.syncDirectMessages(getApplicationContext(), msg.senderUUID,
                                msg.senderNetworkId, mChatMessageStore, mDirectMessageReceivers);
                    }

                    if (mFilterHeartbeatMsg && msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT) {
                        break;
                    }
                    if (mFilterJoinMsg && msg.messageType == HeliosMessagePart.MessagePartType.JOIN) {
                        break;
                    }
                    stored = conversation.addMessage(msg);
                    if (stored)
                        mChatMessageStore.addMessage(msg);
                    break;
                }
            }
        }

        if (!stored) {
            Log.d(TAG, "addMessage done, duplicate >");
            // FIXME: separate view sync from storage, display shoudl hook into the conversation, now will display duplicates on resend
            //return false;
        }

        Log.d(TAG, "addMessage done >");
        // If this message is filtered, don't update topic info
        if (mFilterHeartbeatMsg && msg.messageType == HeliosMessagePart.MessagePartType.HEARTBEAT) {
            Log.d(TAG, "addMessage HEARTBEAT, not updating topic");
            return false;
        }
        if (mFilterJoinMsg && msg.messageType == HeliosMessagePart.MessagePartType.JOIN) {
            Log.d(TAG, "addMessage JOIN, not updating topic");
            return false;
        }

        boolean topicFound = false;
        // Update topic to singleton
        ArrayList<HeliosTopicContext> arrTopics = HeliosConversationList.getInstance().getTopics();
        for (int i = 0; i < arrTopics.size(); i++) {
            HeliosTopicContext topicContext = arrTopics.get(i);
            if (isDirectMessage) {
                // Only 1-1 has uuid set.
                if (!TextUtils.isEmpty(topicContext.uuid)) {
                    if (topicContext.uuid.equals(msg.senderUUID)) {
                        Log.d(TAG, "update topic desc to topic.uuid  " + topicContext.uuid);
                        topicFound = true;
                        topicContext.lastMsg = msg.msg;
                        // Update also user's name if changed..
                        HeliosIdentityInfo userInfo = getUserDataByNetworkId(msg.senderUUID);
                        if (userInfo != null) {
                            msg.senderName = userInfo.getNickname();
                        }

                        topicContext.topic = msg.senderName;
                        topicContext.participants = msg.senderName + ":" + msg.msg;
                        topicContext.ts = msg.getLocaleTs();

                        break;
                    }
                }
            } else {
                if (topicContext.topic.equals(heliosTopic.getTopicName())) {
                    Log.d(TAG, "update topic desc to topic name " + topicContext.topic);
                    topicFound = true;
                    topicContext.lastMsg = msg.msg;
                    topicContext.participants = msg.senderName + ":" + msg.msg;
                    topicContext.ts = msg.getLocaleTs();
                    break;
                }
            }
        }
        Log.d(TAG, "update topic done >");

        if (!topicFound) {
            Log.d(TAG, "## store helios message: could not find/update topic for: " + heliosTopic.getTopicName() + " from:" + msg.senderName);
        }

        // TODO: Fix better handling for direct messages, first message.
        // If this is a new 1-1 chat started by someone, we create a new topic for it and save the uuid
        // of the user to the topic.
        if (!topicFound && isDirectMessage) {
            Log.d(TAG, "topic NOT FOUND, adding topic " + heliosTopic.getTopicName());
            HeliosConversation newConversation = new HeliosConversation();
            newConversation.topic = new HeliosTopicContext(heliosTopic.getTopicName(), msg.msg, msg.to, msg.getLocaleTs());
            newConversation.topic.uuid = msg.senderUUID;
            newConversation.topic.lastMsg = msg.msg;
            newConversation.topic.participants = msg.senderName + ":" + msg.msg;
            newConversation.topic.ts = msg.getLocaleTs();
            stored = newConversation.addMessage(msg);
            HeliosConversationList.getInstance().addConversation(newConversation);
        }

        Log.d(TAG, "should notify: " + mShouldNotify);
        if (stored && notify && mShouldNotify) {
            // don't notify own messages
            if (!mHeliosIdentityInfo.getUserUUID().equals(msg.senderUUID)) {
                // Showing notification even if we have a listener.
                showNotification(heliosTopic.getTopicName(), msg.senderName + ": " + msg.msg);
            }
        }

        return stored;
    }

    /**
     * Receive all subscribes from general pub-sub
     */
    private class HeliosReceiver implements HeliosMessageListener {

        @Override
        public void showMessage(HeliosTopic heliosTopic, HeliosMessage heliosMessage) {
            Log.d(TAG, "HeliosReceiver showMessage() topic:" + heliosTopic.getTopicName());

            boolean isDirectMessage = true;
            // Convert message part from JSON
            HeliosMessagePart msg = null;
            HeliosNetworkAddress networkAddress = null;
            try {
                msg = JsonMessageConverter.getInstance().readHeliosMessagePart(heliosMessage.getMessage());

                // In case there was a media file, update its name accordingly to local saved file.
                if (null != heliosMessage.getMediaFileName()) {
                    msg.mediaFileName = heliosMessage.getMediaFileName();
                }

                if (msg.messageType == HeliosMessagePart.MessagePartType.PUBSUB_SYNC_RESEND) {
                    isDirectMessage = false;

                    // FIXME: allows anyone to impersonate anyone else
                    // FIXME: this duplicates UserNetworkMap handling below
                    // Update seen users.
                    if (msg.senderNetworkId == null) {
                        // A resend without the original sender id, handle without update.
                    } else if (mUserNetworkMap.containsKey(msg.senderNetworkId)) {
                        Log.d(TAG, "we already had networkId, updating:" + msg.senderNetworkId);
                        HeliosIdentityInfo temp = new HeliosIdentityInfo(msg.senderName, msg.senderUUID);
                        mUserNetworkMap.replace(msg.senderNetworkId, temp);
                    } else {
                        Log.d(TAG, "did not have networkId, creating:" + msg.senderNetworkId);
                        HeliosIdentityInfo temp = new HeliosIdentityInfo(msg.senderName, msg.senderUUID);
                        mUserNetworkMap.put(msg.senderNetworkId, temp);
                    }

                    msg.messageType = msg.originalType;
                    // RESEND only for group / pubsub messages
                } else if (heliosMessage instanceof HeliosMessageLibp2pPubSub) {
                    // TODO fix better handling for direct/sub messages.
                    // TODO fix handling users not seen before -- privacy.
                    isDirectMessage = false;

                    HeliosMessageLibp2pPubSub msgPubSub = (HeliosMessageLibp2pPubSub) heliosMessage;
                    networkAddress = msgPubSub.getNetworkAddress();
                    String networkId = msgPubSub.getNetworkAddress().getNetworkId();
                    Log.d(TAG, "getNetworkAddress:" + msgPubSub.getNetworkAddress());
                    Log.d(TAG, "getNetworkId:" + networkId);
                    Log.d(TAG, "getMessage:" + msgPubSub.getMessage());
                    msg.senderNetworkId = networkId;

                    // Update seen users.
                    if (mUserNetworkMap.containsKey(networkId)) {
                        Log.d(TAG, "we already had networkId, updating:" + networkId);
                        HeliosIdentityInfo temp = new HeliosIdentityInfo(msg.senderName, msg.senderUUID);
                        mUserNetworkMap.replace(networkId, temp);
                    } else {
                        Log.d(TAG, "did not have networkId, creating:" + networkId);
                        HeliosIdentityInfo temp = new HeliosIdentityInfo(msg.senderName, msg.senderUUID);
                        mUserNetworkMap.put(networkId, temp);
                    }
                    //TODO REMOVE
                    Log.d(TAG, "senderName:" + msg.senderName);
                    Log.d(TAG, "msg:" + msg.msg);
                    Log.d(TAG, "senderUUID:" + msg.senderUUID);

                    // Update last message seen from this user in publish-subscribe
                    // disregarding PUBSUB_SYNC_RESEND messages received.
                    if (!mHeartbeatUsers.containsKey(heliosTopic.getTopicName())) {
                        mHeartbeatUsers.put(heliosTopic.getTopicName(), new Hashtable<>());
                    }
                    mHeartbeatUsers.get(heliosTopic.getTopicName()).put(msg.senderUUID, new HeliosEgoTag(msg.senderUUID, msg.senderNetworkId, msg.senderName, System.currentTimeMillis()));
                }

            } catch (JsonParseException e) {
                // TODO: notify error
                Log.e(TAG, "JsonParseException while reading heliosMessage: " + e.toString());
                return;
            }

            // We received a message, store it internally.
            // Don't notify every message
            boolean stored = storeHeliosMessage(heliosTopic, msg, (null != mListener), isDirectMessage, networkAddress);

            //TODO: modify logic here, since we should not get duplicates at all to this layer as ReliableLibp2p already stores.
            if (!stored) {
                stored = true;
            }

            // Pass the info to listeners if stored
            // Now just uses the main listeners.
            if (stored) {
                if (null != mListener) {
                    // Update mediaFileName reference if needed.
                    if (null != heliosMessage.getMediaFileName()) {
                        HeliosMessage newMsg = new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(msg), heliosMessage.getMediaFileName());
                        showMessageToListener(heliosTopic, newMsg);
                    } else {
                        showMessageToListener(heliosTopic, heliosMessage);
                    }
                } else {
                    // Don't show HEARTBEAT in notifications
                    if (msg.messageType != HeliosMessagePart.MessagePartType.HEARTBEAT) {
                        // Don't notify own messages
                        if (!mHeliosIdentityInfo.getUserUUID().equals(msg.senderUUID)) {
                            // Show notification every time, could only show when no listeners?
                            showNotification(heliosTopic.getTopicName(), msg.senderName + ": " + msg.msg);
                        }
                    }
                }
            }

            if (isDirectMessage) {
                //TODO direct message handling
                // If direct message, update still, since processing is not taking care of it now.
                showMessageToListener(null, null);
            }
        }
    }

    private void showMessageToListener(HeliosTopic topic, HeliosMessage message) {
        try {
            if (null != mListener) {
                mListener.showMessage(topic, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "showMessageToListener error:" + e.toString());
        }
    }

    public List<HeliosEgoTag> getTopicOnlineUsers(String topic) {
        // Check that topic is not empty or null
        if (TextUtils.isEmpty(topic)) {
            return new ArrayList<>();
        }

        if (mHeartbeatUsers.containsKey(topic)) {
            return new ArrayList<>(mHeartbeatUsers.get(topic).values());
        }

        return new ArrayList<>();
    }

    // Stores mappings to shared preferences.
    private void storeUserNetworkMappings() {
        Log.d(TAG, "storeUserNetworkMappings()");
        Gson gson = new Gson();
        String jsonStr = gson.toJson(mUserNetworkMap);
        Log.d(TAG, "storeUserNetworkMappings() jsonStr:" + jsonStr);

        SharedPreferences sharedPrefs = this.getApplicationContext().getSharedPreferences(EGO_MAPPING_SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(EGO_MAPPING_SHARED_PREF_KEY, jsonStr);
        editor.commit();
        Log.d(TAG, "storeUserNetworkMappings() OK");
    }

    // Loads mappings from shared preferences.
    private void loadUserNetworkMappings() {

        Log.d(TAG, "loadUserNetworkMappings()");
        SharedPreferences sharedPrefs = this.getApplicationContext().getSharedPreferences(EGO_MAPPING_SHARED_PREF_NAME, Context.MODE_PRIVATE);
        String jsonStr = sharedPrefs.getString(EGO_MAPPING_SHARED_PREF_KEY, null);

        if (jsonStr != null) {
            Log.d(TAG, "loadUserNetworkMappings() jsonStr:" + jsonStr);

            Gson gson = new Gson();
            java.lang.reflect.Type type = new TypeToken<Hashtable<String, HeliosIdentityInfo>>() {
            }.getType();
            // TODO: Can contain null values as identity
            Hashtable<String, HeliosIdentityInfo> newHashtable = gson.fromJson(jsonStr, type);

            mUserNetworkMap = newHashtable;

            Log.d(TAG, "Original mappings:");
            Log.d(TAG, "map size:" + mUserNetworkMap.size());
            Enumeration<String> enumeration = mUserNetworkMap.keys();
            while (enumeration.hasMoreElements()) {
                String key = enumeration.nextElement();
                HeliosIdentityInfo temp = mUserNetworkMap.get(key);
                Log.d(TAG, "networkId:" + key);
                Log.d(TAG, "getUserUUID:" + temp.getUserUUID());
                Log.d(TAG, "getNickname:" + temp.getNickname());
            }
            Log.d(TAG, "loadUserNetworkMappings() OK");

        } else {
            Log.d(TAG, "loadUserNetworkMappings() was empty");
        }
    }

    protected String getUserNetworkIdByUUID(String userUUID) {
        Log.d(TAG, "getUserNetworkIdByUUID:" + userUUID);
        if (TextUtils.isEmpty(userUUID)) {
            return null;
        }

        Enumeration<String> enumeration = mUserNetworkMap.keys();
        while (enumeration.hasMoreElements()) {
            String key = enumeration.nextElement();
            HeliosIdentityInfo temp = mUserNetworkMap.get(key);
            if (userUUID.equals(temp.getUserUUID())) {
                return key;
            }
        }
        return null;
    }

    /**
     * Return a copy of HeliosIdentityInfo by networkId.
     *
     * @param networkId networkId to search
     * @return HeliosIdentityInfo
     */
    protected HeliosIdentityInfo getUserDataByNetworkId(String networkId) {
        Log.d(TAG, "getUserDataByNetworkId:" + networkId);
        if (TextUtils.isEmpty(networkId)) {
            return null;
        }

        if (mUserNetworkMap.containsKey(networkId)) {
            Log.d(TAG, "contains" + networkId);
            HeliosIdentityInfo temp = mUserNetworkMap.get(networkId);
            return new HeliosIdentityInfo(temp.getNickname(), temp.getUserUUID());
            //return mUserNetworkMap.get(networkId);
        }

        return null;
    }

    protected void updateHeliosIdentityInfo(HeliosIdentityInfo heliosIdentityInfo) {
        Log.d(TAG, "updateHeliosIdentity");

        String newNick = heliosIdentityInfo.getNickname();
        if (TextUtils.isEmpty(newNick)) {
            newNick = heliosIdentityInfo.getUserUUID();
        }

        // If no identity set, set it and return
        if (mHeliosIdentityInfo == null) {
            Log.d(TAG, "updateHeliosIdentity setting initial nickname:" + newNick);
            mHeliosIdentityInfo = new HeliosIdentityInfo(newNick, heliosIdentityInfo.getUserUUID());
            return;
        }

        // Check for changes and inform
        String previousNick = mHeliosIdentityInfo.getNickname();
        if (!newNick.equals(previousNick)) {
            Log.d(TAG, "updateHeliosIdentity updating to new nickname:" + newNick);
            mHeliosIdentityInfo = new HeliosIdentityInfo(newNick, heliosIdentityInfo.getUserUUID());
            sendMessageToDefaultTopic(HeliosMessagePart.MessagePartType.JOIN, "Has updated nickname (was " + previousNick + ")");
        }
    }

    @Override
    public void connect(HeliosConnectionInfo heliosConnectionInfo, HeliosIdentityInfo heliosIdentityInfo) throws HeliosMessagingException {
        Log.d(TAG, "connect()");
        updateHeliosIdentityInfo(heliosIdentityInfo);

        if (mConnected) {
            Log.d(TAG, "connect() already connected. Disconnect first if connection info changed.");
            return;
        }

        try {
            mHeliosMessagingNodejs.connect(heliosConnectionInfo, heliosIdentityInfo);
            mConnected = true;
            //mHeartbeatManager.start(this, this, mHeliosIdentityInfo);
        } catch (RuntimeException e) {
            // FIXME: should catch HeliosMessagingException
            Log.e(TAG, "Helios Messaging Exception");
            mConnected = false;
        }
    }

    private void sendMessageToDefaultTopic(HeliosMessagePart.MessagePartType msgType, String msg) {
        HeliosMessagePart message = createHeliosMessagePart(HELIOS_DEFAULT_CHAT_TOPIC, msg, msgType);
        if (message == null) {
            Log.e(TAG, "Identity info is missing - message is not published");
            return;
        }
        try {
            publish(new HeliosTopic(message.to, message.to), new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(message)));
            Log.d(TAG, "sendMessageToDefaultTopic sent to:" + message.to);
        } catch (HeliosMessagingException e) {
            Log.e(TAG, "sendMessageToDefaultTopic error sending to:" + message.to);
            e.printStackTrace();
        }
    }

    private HeliosMessagePart createHeliosMessagePart(String topicName, String message, HeliosMessagePart.MessagePartType messageType) {
        String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
        if (mHeliosIdentityInfo == null) {
            Log.e(TAG, "Identity info is null - message part is not created");
            return null;
        }
        HeliosMessagePart msg = new HeliosMessagePart(message, mHeliosIdentityInfo.getNickname(), mHeliosIdentityInfo.getUserUUID(), topicName, ts, messageType);
        return msg;
    }

    @Override
    public void disconnect(HeliosConnectionInfo heliosConnectionInfo, HeliosIdentityInfo heliosIdentityInfo) throws HeliosMessagingException {
        Log.d(TAG, "disconnect()");
        mJsonHandlerThread.quit();
        mHeartbeatManager.stop();
        mHeliosMessagingNodejs.disconnect(heliosConnectionInfo, heliosIdentityInfo);
        mHeliosMessagingNodejs.stop();
        mConnected = false;
    }

    @Override
    public void publish(HeliosTopic heliosTopic, HeliosMessage heliosMessage) throws HeliosMessagingException {
        Log.d(TAG, "publish() topic " + heliosTopic.getTopicName());
        if (!mConnected) {
            Log.e(TAG, "publish() not available, not connected.");
            return;
        }
        mHeliosMessagingNodejs.publish(heliosTopic, heliosMessage);
    }

    @Override
    public void subscribe(HeliosTopic heliosTopic, HeliosMessageListener heliosMessageListener) throws HeliosMessagingException {
        Log.d(TAG, "subscribe()");
        if (!mConnected) {
            Log.e(TAG, "subscribe() not available, not connected.");
            return;
        }

        // Now just uses subscribers to handle if topic is subscribed.
        if (mSubscribers.containsKey(heliosTopic.getTopicName())) {
            Log.d(TAG, "there was a subscribe for " + heliosTopic.getTopicName());
            mSubscribers.replace(heliosTopic.getTopicName(), heliosMessageListener);
        } else {
            Log.d(TAG, "there was no subscribe for " + heliosTopic.getTopicName());
            try {
                mHeliosMessagingNodejs.subscribe(heliosTopic, mHeliosReceiver);
                // Add to map of subscribes
                mSubscribers.put(heliosTopic.getTopicName(), heliosMessageListener);

                // Send arrived message when subscribing the first time.
                if (HELIOS_DEFAULT_CHAT_TOPIC.equals(heliosTopic.getTopicName())) {
                    mHeartbeatManager.sendDelayed(
                            this,
                            HELIOS_DEFAULT_CHAT_TOPIC,
                            "is now online.",
                            mHeliosIdentityInfo,
                            HeliosMessagePart.MessagePartType.JOIN,
                            3700);
                }
            } catch (RuntimeException e) {
                // FIXME: should catch HeliosMessagingException
                Log.e(TAG, "Helios Messaging Exception:" + e.toString());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void unsubscribe(HeliosTopic heliosTopic) throws HeliosMessagingException {
        Log.d(TAG, "unsubscribe()");
        mSubscribers.remove(heliosTopic.getTopicName());
        mHeliosMessagingNodejs.unsubscribe(heliosTopic);
    }

    @Override
    public void unsubscribeListener(HeliosMessageListener heliosMessageListener) throws HeliosMessagingException {
        Log.d(TAG, "unsubscribeListener()");
    }

    @Override
    public HeliosTopic[] search(HeliosTopicMatch heliosTopicMatch) throws HeliosMessagingException {
        Log.d(TAG, "search()");
        return new HeliosTopic[0];
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved()");
    }

    /**
     * Binder class, service runs in the same process as the clients now.
     */
    public class LocalBinder extends Binder {
        MessagingService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MessagingService.this;
        }
    }

    /**
     * If service is being destroyed, close connections.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() start");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBackgroundListener);

        // Save data to storage
        saveJson();

        storeUserNetworkMappings();
        mChatMessageStore.closeDatabase();
        //mServiceHandler.removeCallbacksAndMessages(null);

        // Close connections
        try {
            disconnect(null, null);
        } catch (HeliosMessagingException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "onDestroy() end");

        // Force close nodejs side for now after service destroyed
        System.exit(0);
    }

    /**
     * Get list of tags and related peers - called by any interested activity
     *
     * @return List<HeliosEgoTag> a list of peers announcing the subscribed tags
     */
    public List<HeliosEgoTag> getTags() {
        if (mHeliosMessagingNodejs == null) {
            return new LinkedList<>();
        }

        return mHeliosMessagingNodejs.getTags();
    }

    public void announceTag(String tag) {

        Log.d(TAG, "announceTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.announceTag(tag);
    }

    public void unannounceTag(String tag) {
        Log.d(TAG, "unannounceTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.unannounceTag(tag);
    }

    protected void observeTag(String tag) {
        Log.d(TAG, "observeTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.observeTag(tag);
    }

    protected void unobserveTag(String tag) {
        Log.d(TAG, "unobserveTag:" + tag);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mHeliosMessagingNodejs.unobserveTag(tag);
    }

    /**
     * Resolve egoId into a HeliosNetworkAddress.
     *
     * @param egoId to resolve
     * @return HeliosNetworkAddress
     */
    protected HeliosNetworkAddress resolve(String egoId) {
        if (mHeliosMessagingNodejs == null) {
            return null;
        }
        Log.d(TAG, "resolve egoId:" + egoId);
        HeliosNetworkAddress res = mHeliosMessagingNodejs.getDirectMessaging().resolve(egoId);
        return res;
    }

    protected void addDirectReceiverInternal(String proto) {
        Log.d(TAG, "addDirectReceiverInternal:" + proto);
        if (mHeliosMessagingNodejs == null) {
            return;
        }
        mHeliosMessagingNodejs.getDirectMessaging().addReceiver(proto, mDirectHeliosMessagingReceiver);
    }

    protected void removeDirectReceiverInternal(String proto) {
        Log.d(TAG, "removeDirectReceiverInternal:" + proto);
        if (mHeliosMessagingNodejs == null) {
            return;
        }
        mHeliosMessagingNodejs.getDirectMessaging().removeReceiver(proto);
        Log.d(TAG, "proto:" + proto);
    }

    // Methods for direct messaging between peers.
    // Note: Receiver has to be added before sending (and the other end has to have receiver as well).
    public void addDirectReceiver(String proto, HeliosMessagingReceiver receiver) {
        Log.d(TAG, "addDirectReceiver:" + proto);
        if (mHeliosMessagingNodejs == null) {
            return;
        }

        mDirectMessageReceivers.put(proto, receiver);

        //mHeliosMessagingNodejs.getDirectMessaging().addReceiver(proto, receiver);
    }

    public void removeDirectReceiver(String proto) {
        Log.d(TAG, "removeDirectReceiver:" + proto);
        if (mHeliosMessagingNodejs == null) {
            return;
        }
        mDirectMessageReceivers.remove(proto);

        // mHeliosMessagingNodejs.getDirectMessaging().removeReceiver(proto);
    }

    // Send direct message to other address.
    public void sendDirect(HeliosNetworkAddress addr, String proto, byte[] data) {
        Log.d(TAG, "sendDirect:" + "Pid:" + android.os.Process.myPid() + ", Tid:" + android.os.Process.myTid());
        Log.d(TAG, "sendDirect");
        if (mHeliosMessagingNodejs == null) {
            return;
        }
        Log.d(TAG, "addr:" + addr);
        Log.d(TAG, "proto:" + proto);

        mHeliosMessagingNodejs.getDirectMessaging().sendTo(addr, proto, data);
    }

    public Future<Unit> sendDirectFuture(HeliosNetworkAddress addr, String proto, byte[] data) {
        Log.d(TAG, "sendDirectFuture");
        if (mHeliosMessagingNodejs == null) {
            return null;
        }
        Log.d(TAG, "addr:" + addr);
        Log.d(TAG, "proto:" + proto);

        return mHeliosMessagingNodejs.getDirectMessaging().sendToFuture(addr, proto, data);
    }

    public boolean isConnected() {
        return mConnected;
    }


    public interface MessagingServiceStatusListener {

        /**
         * Emitted when MessagingService has finished loading its internal data structures.
         */
        void onDataLoaded();
    }
}
