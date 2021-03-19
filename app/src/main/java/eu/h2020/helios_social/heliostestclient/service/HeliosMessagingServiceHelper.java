package eu.h2020.helios_social.heliostestclient.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

import eu.h2020.helios_social.core.messaging.HeliosConnectionInfo;
import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo;
import eu.h2020.helios_social.core.messaging.HeliosMessage;
import eu.h2020.helios_social.core.messaging.HeliosMessageListener;
import eu.h2020.helios_social.core.messaging.HeliosMessagingException;
import eu.h2020.helios_social.core.messaging.HeliosTopic;
import eu.h2020.helios_social.core.messaging.MessagingConstants;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosEgoTag;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosMessagingReceiver;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosNetworkAddress;
import eu.h2020.helios_social.core.profile.HeliosUserData;
import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;
import eu.h2020.helios_social.core.messaging.data.JsonMessageConverter;
import kotlin.Unit;

/**
 * Helper class for the TestClient application to communicate with the MessagingService background
 * service (also foreground service).
 * <p>
 * Provides easy methods to bind and unbind from the service and communicate it without exposing the
 * binder interface to the activity.
 * <p>
 * Used to connect/disconnect, publish/subscribe messages.
 */
public class HeliosMessagingServiceHelper {
    private static final String TAG = "HeliosMessagingServiceHelper";
    private static final HeliosMessagingServiceHelper ourInstance = new HeliosMessagingServiceHelper();

    // Related to service
    private MessagingService mService;
    private boolean mBound = false;
    private Context mContext = null;
    private HeliosReceiver mHeliosReceiver;
    private HeliosMessageListener mActivityReceiver;
    private Hashtable<String, HeliosMessageListener> mSubscribers = new Hashtable<>();
    private boolean mSubscribedSavedTopics = false;

    public static HeliosMessagingServiceHelper getInstance() {
        return ourInstance;
    }

    private HeliosMessagingServiceHelper() {
        Log.d(TAG, "HeliosMessagingServiceHelper()");
        mHeliosReceiver = new HeliosReceiver();
    }

    /**
     * Set context to be used when binding/unbinding service.
     *
     * @param ctx {@link Context}
     */
    public void setContext(Context ctx) {
        Log.d(TAG, "setContext");
        mContext = ctx;
    }

    /**
     * Bind our service {@link MessagingService} and provide a listener which is used to notify
     * messages received from the service (HELIOS messages).
     *
     * @param listener {@link HeliosMessageListener}.
     */
    public void bindService(HeliosMessageListener listener) {
        Log.d(TAG, "bindService, bound:" + mBound);
        mActivityReceiver = listener;
        // Bind to LocalService
        Intent intent = new Intent(mContext, MessagingService.class);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbind the service. Should be called when closing the activity. Note, this does not
     * close the actual service, it will continue running in the background.
     */
    public void unBindService() {
        Log.d(TAG, "unBindService");
        if (mBound) {
            if (mService != null) {
                mService.setHeliosMessageListener(null);
            }

            mContext.unbindService(mConnection);
            mContext = null;
            mBound = false;
            mService = null;
            Log.d(TAG, "mBound = FALSE");
        }
    }

    private class HeliosReceiver implements HeliosMessageListener, MessagingService.MessagingServiceStatusListener {
        // Service call for any message coming from the core.
        @Override
        public void showMessage(HeliosTopic heliosTopic, HeliosMessage heliosMessage) {
            Log.d(TAG, "showMessage from Service:");

            // Empty message is the initialize message.
            if (null == heliosTopic) {
                Enumeration<String> a = mSubscribers.keys();
                while (a.hasMoreElements()) {
                    mSubscribers.get(a.nextElement()).showMessage(null, null);
                }

                return;
            }

            Log.d(TAG, "showMessage heliosTopic:" + heliosTopic.getTopicName() + ", message:" + heliosMessage.getMessage());

            // Handle normal messages for any subscriber for this topic.
            if (mSubscribers.containsKey(heliosTopic.getTopicName())) {
                mSubscribers.get(heliosTopic.getTopicName()).showMessage(heliosTopic, heliosMessage);
            } else {
                Log.d(TAG, "No subscriber for topic: " + heliosTopic.getTopicName());
            }
        }

        @Override
        public void onDataLoaded() {
            Log.d(TAG, "##onDataLoaded");
            doHeliosConnect();

            if (!mSubscribedSavedTopics) {
                Log.d(TAG, "subscribe saved topics");
                subscribeFollowedTopics();
                mSubscribedSavedTopics = true;
            }
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MessagingService.LocalBinder binder = (MessagingService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d(TAG, "onServiceConnected");

            // Start the service in order to continue running after we unbind.
            // mService.requestStartService();

            mService.setHeliosMessageListener(mHeliosReceiver);
            // start connection only when onDataLoaded() has been called.
            //doHeliosConnect();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mService = null;
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private void subscribeFollowedTopics() {
        if (mBound) {
            //TODO topics should be loaded already when this is called
            ArrayList<HeliosTopicContext> arrTopics = HeliosConversationList.getInstance().getTopics();
            for (int i = 0; i < arrTopics.size(); i++) {
                HeliosTopicContext tpc = arrTopics.get(i);
                Log.d(TAG, "subscribe topics() tpc.topic:" + tpc.topic + " tpc.uuid:" + tpc.uuid);
                // Only 1-1 messages have uuid set, so check there is none
                if (TextUtils.isEmpty(tpc.uuid)) {
                    // Create a new subscribe, duplicates are not created
                    HeliosTopic newTopic = new HeliosTopic(tpc.topic, tpc.topic);
                    try {
                        subscribe(newTopic);
                    } catch (HeliosMessagingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void doHeliosConnect() {
        Log.d(TAG, "doHeliosConnect()");
        if (mBound) {
            try {
                Log.d(TAG, "doHeliosConnect() is bound");
                mService.connect(new HeliosConnectionInfo(), new HeliosIdentityInfo(HeliosUserData.getInstance().getValue(mContext.getString(R.string.setting_username)),
                        HeliosUserData.getInstance().getValue(mContext.getString(R.string.setting_user_id))));
                if (!mService.isConnected()) {
                    throw new RuntimeException("HeliosService not connected.");
                }
                Log.d(TAG, "HeliosService connected.");

                HeliosTopic topic = new HeliosTopic(MessagingService.HELIOS_DEFAULT_CHAT_TOPIC, MessagingService.HELIOS_DEFAULT_CHAT_TOPIC);
                for (int i = 0; i < 200; i++) {
                    try {
                        mService.subscribe(topic, mHeliosReceiver);
                        mSubscribers.put(topic.getTopicName(), mActivityReceiver);
                        break;
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Error subscribing to topic: " + topic.getTopicName());

                        if (i >= 100) {
                            throw e;
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // ignore sleep interrupt
                    }
                }

                // Create additional BUG CHAT topic
                HeliosTopic bugTopic = new HeliosTopic(MessagingService.HELIOS_DEFAULT_BUG_CHAT_TOPIC, MessagingService.HELIOS_DEFAULT_BUG_CHAT_TOPIC);
                mService.subscribe(bugTopic, mHeliosReceiver);
                mSubscribers.put(bugTopic.getTopicName(), mActivityReceiver);

                // Init message
                Enumeration<String> a = mSubscribers.keys();
                while (a.hasMoreElements()) {
                    mSubscribers.get(a.nextElement()).showMessage(null, null);
                }

                // Add internal receiver for chat protocol
                addDirectReceiverInternal();

                // FIXME: These parts are not in the interface fully
                // Announce/Observe defaults
                mService.announceTag("helios");
                mService.observeTag("helios");

                // Announce/observe user selections
                String tagValue = HeliosUserData.getInstance().getValue("tag_setting");
                String tagToAnnounce = getTagName(tagValue);
                Log.d(TAG, "tagValue:" + tagValue);
                if (tagToAnnounce != null) {
                    mService.announceTag(tagToAnnounce);
                    mService.observeTag(tagToAnnounce);
                }
            } catch (HeliosMessagingException e) {
                Log.e(TAG, "Helios Messaging Exception:" + e.toString());
            }
        } else {
            // Should not happen.
            Log.e(TAG, "doHeliosConnect() not bound, cannot connect");
        }
    }

    public void updateHeliosIdentityInfo() {
        Log.d(TAG, "updateHeliosIdentity");
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }
        mService.updateHeliosIdentityInfo(new HeliosIdentityInfo(HeliosUserData.getInstance().getValue(mContext.getString(R.string.setting_username)),
                HeliosUserData.getInstance().getValue(mContext.getString(R.string.setting_user_id))));
    }

    public void subscribe(HeliosTopic heliosTopic) throws HeliosMessagingException {
        Log.d(TAG, "subscribe heliosTopic:" + heliosTopic.getTopicName());
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        if (mBound) {
            if (!mSubscribers.containsKey(heliosTopic.getTopicName())) {
                mSubscribers.put(heliosTopic.getTopicName(), mActivityReceiver);
                mService.subscribe(heliosTopic, mHeliosReceiver);
            } else {
                Log.d(TAG, "Topic already had a subscribe: " + heliosTopic.getTopicName());
            }
        }
    }

    public void unsubscribe(HeliosTopic heliosTopic) throws HeliosMessagingException {
        Log.d(TAG, "unsubscribe heliosTopic:" + heliosTopic.getTopicName());
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        if (mBound) {
            if (mSubscribers.containsKey(heliosTopic.getTopicName())) {
                mSubscribers.remove(heliosTopic.getTopicName());
                mService.unsubscribe(heliosTopic);
            } else {
                Log.d(TAG, "Topic did not have a subscribe: " + heliosTopic.getTopicName());
            }
        }
    }

    // TODO: fix with GUI and move
    private String getTagName(String tag) {
        String tagReturn = null;
        try {
            int val = Integer.parseInt(tag);
            if (val == 1) {
                tagReturn = "dogs";
            } else if (val == 2) {
                tagReturn = "cats";
            }
        } catch (NumberFormatException e) {
        }

        return tagReturn;
    }

    public List<HeliosEgoTag> getTopicOnlineUsers(String topic) {
        if (mService == null) {
            return new ArrayList<>();
        }

        return mService.getTopicOnlineUsers(topic);
    }

    /**
     * Get user's network id by UUID from the seen users.
     *
     * @param userUUID UUID to search.
     * @return networkId or null
     */
    public String getUserNetworkIdByUUID(String userUUID) {
        Log.d(TAG, "getUserNetworkIdByUUID userUUID:" + userUUID);
        if (userUUID == null) {
            return null;
        }
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return null;
        }

        String temp = mService.getUserNetworkIdByUUID(userUUID);

        Log.d(TAG, "got" + temp);
        return temp;
    }

    /**
     * Get user's data snapshot by networkid from the seen users.
     *
     * @param networkId networkId
     * @return HeliosIdentityInfo containing user data.
     */
    public HeliosIdentityInfo getUserDataByNetworkId(String networkId) {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return null;
        }

        if (networkId == null) {
            return null;
        }
        Log.d(TAG, "getUserDataByNetworkId:" + networkId);
        HeliosIdentityInfo temp = mService.getUserDataByNetworkId(networkId);

        Log.d(TAG, "got" + temp);
        return temp;
    }

    /**
     * Update tag announcements.
     *
     * @param newTag New tag to add listen/announce
     * @param oldTag Old tag to remove listen/announce
     */
    public void updateTag(String newTag, String oldTag) {
        Log.d(TAG, "updateTag: new" + newTag + " old:" + oldTag);
        String tagNew = getTagName(newTag);
        String tagOld = getTagName(oldTag);

        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        if (tagOld != null) {
            mService.unobserveTag(tagOld);
            mService.unannounceTag(tagOld);
        }
        if (tagNew != null) {
            mService.announceTag(tagNew);
            mService.observeTag(tagNew);
        }
    }

    /**
     * Publish a message - called by the Activity.
     *
     * @param heliosTopic   {@link HeliosTopic}
     * @param heliosMessage {@link HeliosMessagePart}
     * @throws HeliosMessagingException publishing message failed.
     */
    public void publish(HeliosTopic heliosTopic, HeliosMessagePart heliosMessage) throws HeliosMessagingException {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        Log.d(TAG, "publish heliosTopic:" + heliosTopic.getTopicName() + ", heliosMessage: " + heliosMessage.msg);

        if (mBound) {
            String mediaFileName = heliosMessage.mediaFileName;
            heliosMessage.mediaFileName = null;
            mService.publish(heliosTopic, new HeliosMessage(JsonMessageConverter.getInstance().convertToJson(heliosMessage), mediaFileName));
        }
    }

    /**
     * Get list of tags and related peers - called by any interested activity
     *
     * @return List<HeliosEgoTag> a list of peers announcing the subscribed tags
     */
    public List<HeliosEgoTag> getTags() {
        if (mService == null) {
            return new LinkedList<>();
        }

        return mService.getTags();
    }

    public void announceTag(String tag) {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        mService.announceTag(tag);
    }

    public void observeTag(String tag) {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        mService.observeTag(tag);
    }

    public void unobserveTag(String tag) {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        mService.unobserveTag(tag);
    }

    /**
     * Resolve egoId into a HeliosNetworkAddress.
     *
     * @param egoId to resolve
     * @return HeliosNetworkAddress
     */
    public HeliosNetworkAddress resolve(String egoId) {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return null;
        }

        return mService.resolve(egoId);
    }

    /**
     * Add receivers for currently supported protocols.
     */
    private void addDirectReceiverInternal() {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }
        Log.d(TAG, "adding receivers");
        mService.addDirectReceiverInternal(MessagingConstants.HELIOS_DIRECT_CHAT_PROTO);
        mService.addDirectReceiverInternal(MessagingConstants.HELIOS_DIRECT_CHAT_FILE_PROTO);
        mService.addDirectReceiverInternal(MessagingConstants.HELIOS_CHAT_SYNC_PROTO);

    }

    /**
     * Add a receiver for a specific protocol.
     *
     * @param proto    protocol to receive from service
     * @param receiver HeliosMessagingReceiver
     */
    public void addDirectReceiver(String proto, HeliosMessagingReceiver receiver) {
        Log.d(TAG, "addDirectReceiver:" + proto);
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }

        Log.d(TAG, "adding receiver");
        mService.addDirectReceiver(proto, receiver);
        Log.d(TAG, "added receiver proto:" + proto);
        //mService.setHeliosMessageListener(mHeliosReceiver);
    }

    /**
     * Remove a receiver for a specific protocol.
     *
     * @param proto protocol to remove from service receivers.
     */
    public void removeDirectReceiver(String proto) {
        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }
        Log.d(TAG, "removing receiver");
        mService.removeDirectReceiver(proto);
        Log.d(TAG, "proto:" + proto);

        //mService.setHeliosMessageListener(mHeliosReceiver);
    }

    public void sendDirect(HeliosNetworkAddress addr, String proto, byte[] data) {
        Log.d(TAG, "sendDirect:proto" + proto + ", Pid: " + Process.myPid() + ", Tid:" + Process.myTid());

        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return;
        }
        mService.sendDirect(addr, proto, data);
    }

    public Future<Unit> sendDirectFuture(HeliosNetworkAddress addr, String proto, byte[] data) {
        Log.d(TAG, "sendDirectFuture:proto" + proto + ", Pid: " + Process.myPid() + ", Tid:" + Process.myTid());

        if (mService == null) {
            Log.d(TAG, "Service connection is null.");
            return null;
        }

        return mService.sendDirectFuture(addr, proto, data);
    }
}
