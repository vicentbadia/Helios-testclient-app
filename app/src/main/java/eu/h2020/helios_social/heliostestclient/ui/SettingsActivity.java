package eu.h2020.helios_social.heliostestclient.ui;

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;


import eu.h2020.helios_social.heliostestclient.ui.fragments.MySettingsFragment;

/**
 * Activity for loading settings - provided in a corresponding fragment {@link MySettingsFragment}.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get a support ActionBar corresponding to this toolbar
        // Enable the Up button
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setDisplayShowHomeEnabled(true);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new MySettingsFragment())
                .commit();
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