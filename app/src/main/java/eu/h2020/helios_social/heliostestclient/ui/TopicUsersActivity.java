package eu.h2020.helios_social.heliostestclient.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.h2020.helios_social.core.messaging_nodejslibp2p.HeliosEgoTag;
import eu.h2020.helios_social.core.profile.HeliosUserData;
import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.heliostestclient.service.HeliosMessagingServiceHelper;

public class TopicUsersActivity extends AppCompatActivity {
    private Handler mHeartbeat;
    private int mHeartbeatInterval = 10 * 1000; // 10 seconds
    private String mChatId;
    private static final String TAG = "Helios-TopicUsersActivity";
    private TagListAdapter mTagListAdapter;
    private static String mUserId;

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

            String userText = item.getTag();
            holder.tagTextView.setText(userText + " (" + item.getEgoId() + ") @" + item.getNetworkId());

            long seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - item.getTimestamp());
            holder.tagTextView2.setText("Last seen: " + seconds + " seconds ago");

            holder.bind(item);
        }
    }

    private static void openDirectChat(Context ctx, HeliosEgoTag ego) {
        Log.d(TAG, "openDirectChat networkId:" + ego.getNetworkId());
        Log.d(TAG, "openDirectChat ego id:" + ego.getEgoId());
        // If no peers yet, don't try to open
        if (ego.getEgoId() == "Sample Ego Id") {
            return;
        }

        if (mUserId.equals(ego.getEgoId())) {
            Log.d(TAG, "This is the user, not opening a chat");
            return;
        }
        Intent i = new Intent(ctx, DirectChatActivity.class);
        i.putExtra(DirectChatActivity.CHAT_UUID, ego.getEgoId());
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
        Log.d(TAG, "mUserId:" + mUserId);

        mTagListAdapter = new TagListAdapter();

        RecyclerView listView = (RecyclerView) findViewById(R.id.tag_peer_list);
        listView.setAdapter(mTagListAdapter);
        listView.setLayoutManager(new LinearLayoutManager(this));

        mChatId = this.getIntent().getStringExtra(ChatActivity.CHAT_ID);
        getSupportActionBar().setTitle(mChatId + ": users online recently");

        mHeartbeat = new Handler();
        mHeartbeat.postDelayed(mRunnableUpdate, 1);
    }

    private Runnable mRunnableUpdate =
            new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "mRunnableUpdate()");
                    List<HeliosEgoTag> initialTags = HeliosMessagingServiceHelper.getInstance().getTopicOnlineUsers(mChatId);
                    if (initialTags != null) {

                        if (initialTags.isEmpty()) {
                            initialTags.add(new HeliosEgoTag("Sample Ego Id", "No peers yet.", "UNKNOWN", 0L));
                        }

                        mTagListAdapter.submitList(initialTags);
                    }

                    mHeartbeat.postDelayed(this, mHeartbeatInterval);
                }
            };

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        mTagListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");

        mHeartbeat.removeCallbacks(mRunnableUpdate);
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