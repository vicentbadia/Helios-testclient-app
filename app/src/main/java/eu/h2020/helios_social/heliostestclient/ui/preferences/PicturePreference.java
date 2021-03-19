/**
 * Based on https://gist.github.com/solar/1002049
 */

package eu.h2020.helios_social.heliostestclient.ui.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.io.File;

import eu.h2020.helios_social.heliostestclient.R;

/**
 * Preference implementation to show user avatar.
 */
public class PicturePreference extends Preference {
    private static final String TAG = "PicturePreference";
    private Drawable icon = null;
    private ImageView imageView = null;

    public PicturePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Log.d(TAG, "Build PicturePreference");
        setLayoutResource(R.layout.picture_preference);

        String pathname = Environment.getExternalStorageDirectory().getAbsolutePath()+"/avatar.img";
        File file = new File(pathname);
        if (file.exists()) {
            Log.d(TAG, "Avatar file found");
            icon = Drawable.createFromPath(pathname);
        } else {
            Log.d(TAG, "No avatar file found");
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PicturePreference, defStyle, 0);
            icon = ta.getDrawable(R.styleable.PicturePreference_icon);
        }
    }

    public PicturePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public void onBindViewHolder(PreferenceViewHolder holder) {
        imageView = (ImageView) holder.findViewById(R.id.profile_icon);
        Log.d(TAG, "Fetching ImageView");
        imageView.setImageDrawable(icon);
    }

    public void setIcon(Drawable icon) {
        Log.d(TAG, "Calling setIcon");
        if (icon == null)
            return;
        if (this.icon == null || !icon.equals(this.icon)) {
            Log.d(TAG, "Assign new picture");
            this.icon = icon;
            if (imageView != null) {
                imageView.setImageDrawable(icon);
                notifyChanged();
            } else {
                Log.d(TAG, "Image is not changed because imageView was null");
            }
        }
    }


}
