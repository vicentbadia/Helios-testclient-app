package eu.h2020.helios_social.heliostestclient.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import eu.h2020.helios_social.core.messaging.HeliosIdentityInfo;
import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosEgoTag;
import eu.h2020.helios_social.core.profile.HeliosUserData;
import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.heliostestclient.service.HeliosMessagingServiceHelper;
import eu.h2020.helios_social.heliostestclient.service.MessagingService;


public class PeerTagActivity extends AppCompatActivity {
    private static final String TAG = "Helios-PeerTagActivity";
    private TagListAdapter mTagListAdapter;
    private static String mUserId;
    private static String mUserNetworkId;
    private BroadcastReceiver mTagListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || mTagListAdapter == null) {
                return;
            }

            @SuppressWarnings("unchecked")
            LinkedList<HeliosEgoTag> list = (LinkedList<HeliosEgoTag>) intent
                    .getSerializableExtra(MessagingService.TAG_LIST_UPDATE);

            if (list == null) {
                return;
            }

            mTagListAdapter.updateNetworkMappings();
            mTagListAdapter.submitList(list);
        }
    };

    private static class TagListAdapter extends ListAdapter<HeliosEgoTag, TagListAdapter.TagListViewHolder> {
        protected TagListAdapter() {
            super(DIFF_CALLBACK);
        }

        private void updateNetworkMappings() {

        }

        private static final DiffUtil.ItemCallback<HeliosEgoTag> DIFF_CALLBACK = new DiffUtil.ItemCallback<HeliosEgoTag>() {
            @Override
            public boolean areItemsTheSame(@NonNull HeliosEgoTag oldItem, @NonNull HeliosEgoTag newItem) {
                return oldItem.hashKey().equals(newItem.hashKey());
            }

            @Override
            public boolean areContentsTheSame(@NonNull HeliosEgoTag oldItem, @NonNull HeliosEgoTag newItem) {
                return oldItem.equals(newItem);
            }
        };

        private static final class TagListViewHolder extends RecyclerView.ViewHolder {
            protected TextView tagLabelView;
            protected TextView tagTextView;
            protected TextView tagTextView2;

            public TagListViewHolder(@NonNull View itemView) {
                super(itemView);

                tagLabelView = (TextView) itemView.findViewById(R.id.peer_tag_label);
                tagTextView = (TextView) itemView.findViewById(R.id.peer_tag_text);
                tagTextView2 = (TextView) itemView.findViewById(R.id.peer_tag_text2);
            }

            private void bind(HeliosEgoTag test) {
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openDirectChat(v.getContext(), test);
                    }
                });
            }
        }

        @NonNull
        @Override
        public TagListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_peer_tag, parent, false);

            return new TagListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TagListViewHolder holder, int position) {
            Log.d(TAG, "onBindViewHolder:" + position);

            HeliosEgoTag item = getItem(position);
            holder.tagLabelView.setText(item.getTag());

            // FIXME: Cache and do not query in onBindViewHolder!
            HeliosIdentityInfo userData = HeliosMessagingServiceHelper.getInstance().getUserDataByNetworkId(item.getNetworkId());
            String userText = "UNKNOWN";
            if (userData != null) {
                String username = userData.getNickname();
                userText = username;
            }
            holder.tagTextView.setText(userText + " @" + item.getNetworkId());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String datetimeStr = sdf.format(item.getTimestamp());
            holder.tagTextView2.setText("Last updated: " + datetimeStr);

            holder.bind(item);
        }
    }

    private static void openDirectChat(Context ctx, HeliosEgoTag ego) {
        Log.d(TAG, "openDirectChat:" + ego.getNetworkId());

        if (mUserNetworkId.equals(ego.getNetworkId())) {
            Log.d(TAG, "This is the user, not opening a chat");
            return;
        }

        // If no peers yet, don't try to open
        if (ego.getNetworkId() == "No peers yet.") {
            return;
        }

        Intent i = new Intent(ctx, DirectChatActivity.class);
        i.putExtra(DirectChatActivity.CHAT_NETWORK_ID, ego.getNetworkId());
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peer_tag);
        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setDisplayShowHomeEnabled(true);

        mUserId = HeliosUserData.getInstance().getValue(getString(R.string.setting_user_id));
        mUserNetworkId = HeliosMessagingServiceHelper.getInstance().getUserNetworkIdByUUID(mUserId);

        mTagListAdapter = new TagListAdapter();
        RecyclerView listView = (RecyclerView) findViewById(R.id.tag_peer_list);
        listView.setAdapter(mTagListAdapter);
        listView.setLayoutManager(new LinearLayoutManager(this));

        List<HeliosEgoTag> initialTags = HeliosMessagingServiceHelper.getInstance().getTags();
        if (initialTags != null) {

            if (initialTags.isEmpty()) {
                initialTags.add(new HeliosEgoTag("Sample Ego Id", "No peers yet.", "Example", 0L));
            }

            mTagListAdapter.submitList(initialTags);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mTagListAdapter.notifyDataSetChanged();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mTagListReceiver,
                new IntentFilter(MessagingService.TAG_LIST_UPDATE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mTagListReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}