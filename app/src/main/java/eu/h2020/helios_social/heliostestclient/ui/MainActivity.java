package eu.h2020.helios_social.heliostestclient.ui;

import java.util.ArrayList;
import java.util.UUID;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import eu.h2020.helios_social.core.context.ContextListener;
import eu.h2020.helios_social.core.context.Context;
import eu.h2020.helios_social.core.context.ext.ActivityContext;
import eu.h2020.helios_social.core.context.ext.LocationContext;
import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Node;
import eu.h2020.helios_social.core.profile.HeliosProfileManager;
import eu.h2020.helios_social.core.profile.HeliosUserData;
import eu.h2020.helios_social.core.sensor.SensorValueListener;
import eu.h2020.helios_social.core.sensor.ext.ActivitySensor;
import eu.h2020.helios_social.core.sensor.ext.LocationSensor;
import eu.h2020.helios_social.core.messaging.data.HeliosConversation;
import eu.h2020.helios_social.core.messaging.data.HeliosConversationList;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;
import eu.h2020.helios_social.heliostestclient.service.HeliosMessagingServiceHelper;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;
import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.heliostestclient.service.MessagingService;
import eu.h2020.helios_social.heliostestclient.ui.adapters.TopicAdapter;
import eu.h2020.helios_social.core.messaging.HeliosMessage;
import eu.h2020.helios_social.core.messaging.HeliosMessageListener;
import eu.h2020.helios_social.core.messaging.HeliosMessagingException;
import eu.h2020.helios_social.core.messaging.HeliosTopic;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.location.DetectedActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

//UPV - Using Neurobehaviour module listener class to send info about chat communications
import eu.h2020.helios_social.modules.neurobehaviour.NeurobehaviourListener;

//UPV - Using SentimentalAnalysis Class
import eu.h2020.helios_social.modules.neurobehaviour.SentimentalAnalysis;

/**
 * Main activity for the TestClient.
 */
public class
MainActivity extends AppCompatActivity implements HeliosMessageListener, ContextListener, SensorValueListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_ALL = 1;
    private static final int MY_STORAGE_PERMISSION_REQUEST_CODE = 2;
    private static final int MY_LOCATION_PERMISSION_REQUEST_CODE = 3;
    // TODO DEBUG REPLACE CONVERSATION
    public static final String HELIOS_CHAT_TOPIC = "helios";
    private Handler mHandler = new Handler();
    private TopicAdapter mTopicAdapter;
    private String mPreviousTag = null;
    private boolean mLoadingProgressBarHidden;

    private final HeliosMessagingServiceHelper mMessageMgr = HeliosMessagingServiceHelper.getInstance();
    private final HeliosMessageListener listener = this;

    private LocationContext mLocationContext1;
    private LocationContext mLocationContext2;
    private ActivityContext mInVehicleContext;
    private ActivityContext mOnBicycleContext;
    private ActivityContext mOnFootContext;
    private ActivityContext mStillContext;

    private LocationSensor mLocationSensor;
    private ActivitySensor mActivitySensor;

    private ContextualEgoNetwork mEgoNetwork;

    public static ArrayList<Context> mMyContexts;

    // Tracks the status of the location updates request
    private Boolean mRequestingLocationUpdates = false;
    // Tracks the status of the activity updates request
    private Boolean mRequestingActivityUpdates = false;

    //UPV - Listener init
    private NeurobehaviourListener neuroListener;
    private android.content.Context context;

    //UPV - Sentimental analysis instance
    private SentimentalAnalysis sentimentalAnalysis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Init/check user account
        checkUserAccount();

        // TODO: Should create App class to handle this and Singleton(s)
        setupTopicListView();

        // Check context module. Create two example location-based contexts.
        checkContext();
        startOrStopSensorUpdates();

        // Handle permissions
        String[] PERMISSIONS = {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.ACCESS_FINE_LOCATION
        };
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {
            Log.d(TAG, "Permissions OK");
            start();
        }

        //UPV - Setup listener for Neurobehaviour module
        neuroListener = new NeurobehaviourListener();
        context = getApplicationContext();


        neuroListener.startAccel("start_session", context);
        Log.v("accel", "Start accelerometer");

        //UPV - Setting storage vars
        neuroListener.SetCsvReady(false);
        neuroListener.SetCsvImageReady(false);

        //UPV - Sentimental analysis instance
        sentimentalAnalysis = new SentimentalAnalysis();

        //UPV - Neurobehaviour database for analysis results
        neuroListener.DatabaseInstance(context, getApplication());
    }

    private void start() {
        // Bind to service via helper class
        mMessageMgr.setContext(this.getApplicationContext());
        mMessageMgr.bindService(listener);

        // Start messaging service, could be in helper class also.
        Intent startIntent = new Intent(this.getApplicationContext(), MessagingService.class);
        startIntent.setAction(MessagingService.START_ACTION);
        ContextCompat.startForegroundService(this.getApplicationContext(), startIntent);
        //startForegroundService(startIntent);
    }

    private void setupTopicListView() {
        //TODO: Load topics from SQL

        // Create the adapter to convert the array to views
        mTopicAdapter = new TopicAdapter(HeliosConversationList.getInstance().getTopics());
        mTopicAdapter.setOnItemClickListener(topicClickHandler);
        // If topics loaded already, hide progress bar
        if(mTopicAdapter.getItemCount() > 0) {
            findViewById(R.id.progressBarUPDATE).setVisibility(View.INVISIBLE);
            mLoadingProgressBarHidden = true;
        }

        RecyclerView listView = findViewById(R.id.messageListView);
        //listView.setHasFixedSize(false);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(mTopicAdapter);
    }

    private TopicAdapter.OnItemClickListener topicClickHandler = new TopicAdapter.OnItemClickListener() {
        @Override
        public void onClick(HeliosTopicContext htc) {
            Log.d(TAG, "ctx.uuid:" + htc.uuid);

            // If the topic has uuid set, it is a 1-1 chat for now
            if (!TextUtils.isEmpty(htc.uuid)) {
                Intent i = new Intent(MainActivity.this, DirectChatActivity.class);
                i.putExtra(DirectChatActivity.CHAT_UUID, htc.uuid);
                startActivity(i);
            } else {
                String test = htc.topic;
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra(ChatActivity.CHAT_ID, test);
                startActivity(i);
            }
        }

        @Override
        public void onLongClick(HeliosTopicContext htc) {
            Log.d(TAG, "ctx.uuid:" + htc.uuid);

            // Allow deleting chats with uuid (1-1) for now
            if (!TextUtils.isEmpty(htc.uuid)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (TextUtils.isEmpty(htc.uuid)) {
                            mHandler.postDelayed(() -> {
                                try {
                                    mMessageMgr.unsubscribe(new HeliosTopic(htc.topic, htc.topic));
                                } catch (HeliosMessagingException e) {
                                    e.printStackTrace();
                                }
                            }, 1);
                        } else {
                            HeliosConversationList.getInstance().deleteConversationByTopicUUID(htc.uuid);
                        }

                        mTopicAdapter.notifyDataSetChanged();
                    }
                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
                // TODO strings to res
                builder.setMessage("Chat messages will be deleted (excluding externally saved files).")
                        .setTitle("Delete chat: " + htc.topic);
                AlertDialog dialog = builder.create();
                dialog.show();
            }

        }
    };

    private void checkUserAccount() {
        HeliosProfileManager profileMgr = HeliosProfileManager.getInstance();
        android.content.Context appCtx = getApplicationContext();

        // Check ProfileManager key generation
        profileMgr.keyInit(appCtx);
        // Get default preferences userId
        profileMgr.identityInit(this, getString(R.string.setting_user_id));

        String userName = profileMgr.load(appCtx, getString(R.string.setting_username));
        profileMgr.load(appCtx, getString(R.string.setting_fullname));
        profileMgr.load(appCtx, getString(R.string.setting_phone_number));
        profileMgr.load(appCtx, getString(R.string.setting_email_address));
        profileMgr.load(appCtx, getString(R.string.setting_home_address));
        profileMgr.load(appCtx, getString(R.string.setting_work_address));
        profileMgr.load(appCtx, "homelat");
        profileMgr.load(appCtx, "homelong");
        profileMgr.load(appCtx, "worklat");
        profileMgr.load(appCtx, "worklong");
        profileMgr.load(appCtx, getString(R.string.setting_tag));

        // Check that sharing preference value is numerical and if not set it to zero
        String sharing = profileMgr.load(appCtx, getString(R.string.setting_sharing));
        try {
            int val = Integer.parseInt(sharing);
        } catch (NumberFormatException e) {
            profileMgr.store(appCtx, getString(R.string.setting_sharing), "0");
        }

        // Check that sharing preference value is numerical and if not set it to zero
        String location = profileMgr.load(appCtx, getString(R.string.setting_location));
        try {
            int val = Integer.parseInt(location);
        } catch (NumberFormatException e) {
            profileMgr.store(appCtx, getString(R.string.setting_location), "0");
        }

        // Open settings, if username not set
        if (userName.isEmpty()) {
            Toast.makeText(this.getApplicationContext(), "Write a name for your profile.", Toast.LENGTH_LONG).show();
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        }
    }

    private void startOrStopSensorUpdates() {
        // Turn on/off location and Activity sensor updates
        Log.d(TAG, "startOrStopSensorUpdates()");

        // TODO: There should be controls to enable/disable updates more easily?
        try {
            int val = Integer.parseInt(HeliosUserData.getInstance().getValue(getString(R.string.setting_location)));
            if (val == 1) {
                Log.d(TAG, ">startLocationUpdates");
                mLocationSensor.startUpdates();
                mRequestingLocationUpdates = true;
            } else {
                Log.d(TAG, ">stopLocationUpdates");
                mLocationSensor.stopUpdates();
                mRequestingLocationUpdates = false;
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error getting setting location.");
            Log.d(TAG, ">stopLocationUpdates");
            mLocationSensor.stopUpdates();
            mRequestingLocationUpdates = false;
        }
        // Activity sensor updates
        // TODO. Value from settings? Now allowed if location allowed
        if (mRequestingLocationUpdates) {
            if (!mRequestingActivityUpdates) {
                mActivitySensor.startUpdates();
                mRequestingActivityUpdates = true;
            }
        } else {
            if (mRequestingActivityUpdates) {
                mActivitySensor.stopUpdates();
                mRequestingActivityUpdates = false;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        /*
        if (requestCode == PERMISSION_ALL) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "granted");
            } else {
                Log.d(TAG, "not granted");
            }
        }*/
        // Continue starting service even if user has not granted all permissions, then some parts
        // will be disabled.
        // TODO: Notify user why permissions are needed if not granted.
        start();
    }

    public static boolean hasPermissions(android.content.Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
        This method creates two location-based contexts named “At home” and “At work”.
        The location contexts (instances of the class LocationContext) take the coordinates (lat, lon) and
        radius (in meters) as input, which values define the area (circle) where the context is active.
        Further, the example shows how to receive updates to the contexts active value.
        Also, it shows how to associate a context with CEN.
        For LocationContext and LocationSensor class implementations (in context repository):
            @see eu.h2020.helios_social.core.context.ext.LocationContext
            @see eu.h2020.helios_social.core.sensor.ext.LocationSensor
    */
    private void checkContext() {
        Log.d(TAG, "Testing context functionality");
        // Load username from profile data
        HeliosUserData profileData = HeliosUserData.getInstance();
        String userId = profileData.getValue(getString(R.string.setting_user_id));

        // An example of creation of CEN using the CENlibrary.
        // userId is given as the id for the ego node.
        //TODO: CEN tries to save file to system, check proper path
        // CEN may sometimes modify user given egoData (third argument)?
        mEgoNetwork = ContextualEgoNetwork.createOrLoad("", userId, null);
        Node egoNode = mEgoNetwork.getEgo();
        Object nodeData = egoNode.getData();
        if (nodeData != null) {
            Log.i(TAG, "User data related to egoNode modified?");
        }
        String nodeId = egoNode.getId();
        if (nodeId != userId) {
            Log.i(TAG, "User id related to egoNode not found?");
        }

        // Creating a new location-based context named "At work" ...
        // First, get work location coordinates from profile data
        String workLatStr = profileData.getValue("worklat");
        String workLongStr = profileData.getValue("worklong");
        if (workLatStr != null && workLongStr != null) {
            try {
                double workLat = Double.parseDouble(workLatStr);
                double workLong = Double.parseDouble(workLongStr);
                // Create a new context "at work" by instantiating the class LocationContext.
                mLocationContext1 = new LocationContext("At work", workLat, workLong, 1000.0);
                Log.d(TAG, "At work " + workLat + "," + workLong + " r=1000.0");
            } catch (NumberFormatException e) {
                Log.e(TAG, "checkContext Bad number format (workLat, workLong)");
                mLocationContext1 = new LocationContext("At work", 60.1803, 24.8255, 1000.0);
            }
        } else {
            mLocationContext1 = new LocationContext("At work", 60.1803, 24.8255, 1000.0);
        }
        // Register listener to obtain changes in the context active value
        mLocationContext1.registerContextListener(this);

        // Then, similarly than the "At work" context above,
        // create location-based context named "At home" ...
        String homeLatStr = profileData.getValue("homelat");
        String homeLongStr = profileData.getValue("homelong");
        if (homeLatStr != null && homeLongStr != null) {
            try {
                double homeLat = Double.parseDouble(homeLatStr);
                double homeLong = Double.parseDouble(homeLongStr);
                mLocationContext2 = new LocationContext("At home", homeLat, homeLong, 1000.0);
                Log.d(TAG, "At home " + homeLat + "," + homeLong + " r=1000.0");
            } catch (NumberFormatException e) {
                Log.e(TAG, "checkContext Bad number format (homeLat, homeLong)");
                mLocationContext2 = new LocationContext("At home", 60.2803, 24.8255, 1000.0);
            }
        } else {
            mLocationContext2 = new LocationContext("At home", 60.2803, 24.8255, 1000.0);
        }
        // Register listener to obtain changes in the context active value
        mLocationContext2.registerContextListener(this);

        // Init LocationSensor
        mLocationSensor = new LocationSensor(this);
        // Register location listeners for the contexts
        mLocationSensor.registerValueListener(mLocationContext1);
        mLocationSensor.registerValueListener(mLocationContext2);
        // Only for demo UI,  to obtain updates to location coordinates via ValueListener
        mLocationSensor.registerValueListener(this);

        // Associate the created contexts into CEN
        mEgoNetwork.getOrCreateContext(mLocationContext1);
        mEgoNetwork.getOrCreateContext(mLocationContext2);

        // Check if the contexts can be found from the CEN
        ArrayList<eu.h2020.helios_social.core.contextualegonetwork.Context> cenContexts = mEgoNetwork.getContexts();
        for (eu.h2020.helios_social.core.contextualegonetwork.Context c : cenContexts) {
            if (c.getData() == mLocationContext1) {
                Log.i(TAG, "Context: At work");
            } else if (c.getData() == mLocationContext2) {
                Log.i(TAG, "Context: At home");
            }
        }
        Log.d(TAG, "Ego networking test setup done");

        // Still create some example activity contexts ...
        // New activity contexts
        mInVehicleContext = new ActivityContext("In vehicle", DetectedActivity.IN_VEHICLE);
        mOnBicycleContext = new ActivityContext("On bicycle", DetectedActivity.ON_BICYCLE);
        mOnFootContext = new ActivityContext("On foot", DetectedActivity.ON_FOOT);
        mStillContext = new ActivityContext("Still", DetectedActivity.STILL);

        mActivitySensor = new ActivitySensor(this);

        mActivitySensor.registerValueListener(mInVehicleContext);
        mActivitySensor.registerValueListener(mOnBicycleContext);
        mActivitySensor.registerValueListener(mOnFootContext);
        mActivitySensor.registerValueListener(mStillContext);

        // add contexts into a list that is used by MyConstextsActivity to show states of the contexts
        mMyContexts = new ArrayList<Context>();
        mMyContexts.add(mLocationContext1);
        mMyContexts.add(mLocationContext2);
        mMyContexts.add(mInVehicleContext);
        mMyContexts.add(mOnBicycleContext);
        mMyContexts.add(mOnFootContext);
        mMyContexts.add(mStillContext);

        Log.d(TAG, "checkContext end");
    }

    // Send update to listening activities about a new message.
    private void sendUpdate(HeliosTopic topic, HeliosMessage message) {
        Intent intent = new Intent("helios_message");
        if (message != null) {
            intent.putExtra("json", message.getMessage());
        }
        if (topic != null) {
            intent.putExtra("topic", topic.getTopicName());
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() start");

        startOrStopSensorUpdates();

        mHandler.postDelayed(runnable, 1);
        // Start: Check tag updates
        String tagValue = HeliosUserData.getInstance().getValue(getString(R.string.setting_tag));
        Log.d(TAG, "onResume tagValue:" + tagValue);
        Log.d(TAG, "onResume mPreviousTag:" + mPreviousTag);
        mTopicAdapter.setOnItemClickListener(topicClickHandler);
        mMessageMgr.updateHeliosIdentityInfo();

        if (mPreviousTag != tagValue) {
            if (mPreviousTag != null) {
                HeliosMessagingServiceHelper.getInstance().updateTag(tagValue, mPreviousTag);
            }
            Log.d(TAG, "tagValue:" + tagValue);

            mPreviousTag = tagValue;
        }
        // END: check tag updates

        Log.d(TAG, "onResume() end");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        mTopicAdapter.setOnItemClickListener(null);
        //LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");

        // Unbind from service, which continues running in the foreground.
        // User can stop the service and connection from the service notification.
        mMessageMgr.unBindService();
        // stop sensor updates
        if (mRequestingLocationUpdates) {
            mLocationSensor.stopUpdates();
        }
        if (mRequestingActivityUpdates) {
            mActivitySensor.stopUpdates();
        }

        //UPV - Stop accelerometer
        neuroListener.stopAccel();
        Log.v("accel", "Stop accelerometer");

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.join_a_topic:
                showJoinNewTopicDialog();
                return true;

            case R.id.action_settings:
                // User chose the "Settings" item, show the app settings UI...
                //Toast.makeText(this.getApplicationContext(), "Settings..", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
                return true;

            case R.id.action_peer_tag_list:
                Intent j = new Intent(this, PeerTagActivity.class);
                startActivity(j);
                return true;

            case R.id.my_contexts:
                startActivity(new Intent(this, MyContextsActivity.class));
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    private void joinNewTopic(String topic) {
        Log.d(TAG, "joinNewTopic :" + topic);

        if (!TextUtils.isEmpty(topic)) {
            ArrayList<HeliosTopicContext> arrTopics = HeliosConversationList.getInstance().getTopics();
            for (int i = 0; i < arrTopics.size(); i++) {
                HeliosTopicContext tpc = arrTopics.get(i);
                if (tpc.topic.equals(topic)) {
                    Log.d(TAG, "Topic already exists, not joining:" + topic);
                    return;
                }
            }
            createConversation(topic);
        }
    }

    private void createConversation(String topicName) {
        Log.d(TAG, "createConversation with topic :" + topicName);
        HeliosConversation defaultConversation = new HeliosConversation();
        defaultConversation.topic = new HeliosTopicContext(topicName, "-", "-", "-");

        HeliosConversationList.getInstance().addConversation(defaultConversation);

        // Update topic adapter
        mHandler.postDelayed(runnable, 1);
        mHandler.postDelayed(() -> {
            try {
                mMessageMgr.subscribe(new HeliosTopic(topicName, topicName));
            } catch (HeliosMessagingException e) {
                e.printStackTrace();
            }
        }, 1);
    }

    private void showJoinNewTopicDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        EditText text = new EditText(this);
        builder.setView(text);
        builder.setPositiveButton("Join", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                joinNewTopic(text.getText().toString());
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });

        // TODO: Strings to res
        builder.setTitle("Join a topic");
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /*
    // For creating new topics/contexts
    private void setupNewTopicButton() {
        Log.d(TAG,"setupNewTopicButton()");

        Button button = (Button) findViewById(R.id.newTopicButton);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.d(TAG,"NewTopicButton onClick");

                showNewChatDialog();
            }
        });
    }

    private void showNewChatDialog() {
        DialogFragment newFragment = new TestDialogFragment();
        newFragment.show(getSupportFragmentManager(), "newchat");
    }
    */

    /*
     * From TestDialogFragment - new chat dialog
    @Override
    public void onFragmentInteraction(String json) {
        Log.d(TAG, "onFragmentInteraction result: " + json);
    }*/

    @Override
    public void showMessage(HeliosTopic topic, HeliosMessage message) {
        Log.d(TAG, "showMessage(), topic: " + topic);

        // Initialized message. Could be a broadcast or other.
        if (null == topic && null == message) {
            Log.d(TAG, "init showMessage");
            if (!mLoadingProgressBarHidden) {
                mHandler.postDelayed(() -> {
                    findViewById(R.id.progressBarUPDATE).setVisibility(View.INVISIBLE);
                }, 1);

                mLoadingProgressBarHidden = true;
            }
            mHandler.postDelayed(runnable, 1);
            sendUpdate(null, null);
            return;
        }

        Log.d(TAG, "topic: " + topic.getTopicName() + " message:" + message.getMessage());

        // Update internal data
        sendUpdate(topic, message);
        mHandler.postDelayed(runnable, 10);


        //UPV - Extracting user name from message
        String senderName = "";
        String msgType = "";
        try {
            JSONObject json = new JSONObject(message.getMessage());
            senderName = json.getString("senderName");
            msgType = json.getString("messageType");
            Log.v("text", "Sender name: " + senderName);
            Log.v("text", "Type of message: " + msgType);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.v("text", "User name: " + neuroListener.getUserName());

        //UPV - Don't analyze "JOIN" messages
        if (msgType.equals("MESSAGE")) {
            //UPV - sending message to SentimentalAnalysis class to analize
            //public void runThread(Context context, String fileName, HeliosMessageListener messageListener, HeliosTopic topic, HeliosMessage message, String senderName)
            sentimentalAnalysis.runThread(this.getApplicationContext(), message.getMediaFileName(), listener, topic, message, senderName);
            //UPV - sending message to Neurobehaviour module
            neuroListener.sendingMsg(message, this.getApplicationContext());
        }

    }

    private Runnable runnable = new Runnable() {
        public void run() {
            mTopicAdapter.notifyDataSetChanged();
        }
    };

    /**
     * Implements the ContextLister interface contextChanged method.
     * This is called when context is changed. Sending notification to chat
     * channel about context change.
     *
     * @param active
     * @see eu.h2020.helios_social.core.context.ContextListener
     */
    @Override
    public void contextChanged(boolean active) {
        Log.i(TAG, "Context changed " + active);
        String contextMsg;
        if (mLocationContext1.isActive()) {
            Log.e(TAG, "Now at work");
            contextMsg = "Work Context";
        } else if (mLocationContext2.isActive()) {
            Log.e(TAG, "Now at home");
            contextMsg = "Home Context";
        } else {
            Log.e(TAG, "Not in any context");
            contextMsg = "Unknown Context";
        }
        HeliosProfileManager profileMgr = HeliosProfileManager.getInstance();
        String userName = profileMgr.load(this, "username");
        String userId = profileMgr.load(this, getString(R.string.setting_user_id), android.content.Context.MODE_PRIVATE);
        String ts = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now());
        HeliosMessagePart msg = new HeliosMessagePart("Context changed to: " + contextMsg, userName, userId, HELIOS_CHAT_TOPIC, ts);

        try {
            HeliosMessagingServiceHelper.getInstance().publish(new HeliosTopic(HELIOS_CHAT_TOPIC, HELIOS_CHAT_TOPIC), msg);
        } catch (HeliosMessagingException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method implements the SensorValueListener interface receiveValue method, which
     * obtains values from the location sensor.
     *
     * @param location - a Location value
     * @see eu.h2020.helios_social.core.sensor.SensorValueListener
     */
    @Override
    public void receiveValue(Object location) {
        Location loc = (Location) location;
        Log.e(TAG, "Received location: " + loc.toString());
        if (mLocationContext1.isActive()) {
            Log.e(TAG, "At work");
        }
        if (mLocationContext2.isActive()) {
            Log.e(TAG, "At home");
        }
    }
}
