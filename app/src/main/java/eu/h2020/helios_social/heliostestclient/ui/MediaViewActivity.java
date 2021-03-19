package eu.h2020.helios_social.heliostestclient.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.MediaController;
import android.widget.VideoView;

import eu.h2020.helios_social.core.storage.HeliosStorageUtils;
import eu.h2020.helios_social.heliostestclient.R;

/**
 * Activity for viewing media files. Currently only supports showing video files.
 */
public class MediaViewActivity extends AppCompatActivity {
    private static final String TAG = "MediaViewActivity";
    private VideoView mVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setDisplayShowHomeEnabled(true);

        String fileName = this.getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (TextUtils.isEmpty(fileName)) {
            getSupportActionBar().setTitle("Received file name empty.");
            return;
        }

        getSupportActionBar().setTitle(fileName);
        //TODO Update if stored otherwise.
        String mediaFilePath = "file://" + this.getExternalFilesDir(null) + HeliosStorageUtils.FILE_SEPARATOR +
                HeliosStorageUtils.HELIOS_DIR + HeliosStorageUtils.FILE_SEPARATOR +
                fileName;
        //Log.d(TAG, "mediaFilePath:" + mediaFilePath);

        mVideoView = findViewById(R.id.videoView);
        MediaController mediaController = new MediaController(this);
        mVideoView.setMediaController(mediaController);
        mediaController.setMediaPlayer(mVideoView);

        mVideoView.setVideoPath(mediaFilePath);
        mVideoView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
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
