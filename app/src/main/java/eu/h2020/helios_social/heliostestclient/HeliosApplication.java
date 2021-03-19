package eu.h2020.helios_social.heliostestclient;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.jakewharton.threetenabp.AndroidThreeTen;

import eu.h2020.helios_social.heliostestclient.service.MessagingService;

/**
 * Base application. Currently used to initialize {@link AndroidThreeTen} library that is used
 * to have support of ZonedDateTime for older devices.
 * <p>
 * Can also be used to initialize application wide singletons and/or enable DEBUG BuildConfig.
 */
public class HeliosApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "HeliosApplication";
    private boolean mIsInBackground = false;
    private LocalBroadcastManager mLocalBroadcastManager;
    private int mCount = 0;
    private Handler mHandler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidThreeTen.init(this);
        Log.d(TAG, "onCreate");

        // Enable if testing.
        /*
        if (BuildConfig.DEBUG) {
            Log.d(TAG,"BuildConfig.DEBUG");
            System.gc();

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectAll()
                    .build());
        }*/
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onTrimMemory(int i) {
        super.onTrimMemory(i);
        Log.d(TAG, "onTrimMemory > " + i);
        // ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN not called when Foreground notification
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
        mCount++;
        Log.d(TAG, "onActivityResumed > foreground :" + mCount);

        if (mIsInBackground) {
            Log.d(TAG, "onActivityResumed > background notify");
            mIsInBackground = false;
            Intent intent = new Intent(MessagingService.FOREGROUND_ACTION);
            intent.putExtra(MessagingService.FOREGROUND_ACTION, false);

            mLocalBroadcastManager.sendBroadcastSync(intent);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Log.d(TAG, "onActivityPaused:" + mCount);
        mCount--;
        mHandler.postDelayed(() -> {
            Log.d(TAG, "onActivityPaused check resumed count:" + mCount);
            if(mCount == 0){
                if(!mIsInBackground) {
                    mIsInBackground = true;
                    Intent intent = new Intent(MessagingService.FOREGROUND_ACTION);
                    intent.putExtra(MessagingService.FOREGROUND_ACTION, true);
                    mLocalBroadcastManager.sendBroadcastSync(intent);
                }
            }
        }, 200);
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.d(TAG, "onActivityDestroyed ");
    }
}
