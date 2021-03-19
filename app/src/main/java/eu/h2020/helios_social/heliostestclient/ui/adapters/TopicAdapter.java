package eu.h2020.helios_social.heliostestclient.ui.adapters;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.core.messaging.data.HeliosTopicContext;

import java.util.ArrayList;

/**
 * TopicAdapter for {@link HeliosTopicContext} to be shown in a list view.
 */
public class TopicAdapter extends RecyclerView.Adapter<TopicAdapter.TopicViewHolder> {
    private static final String TAG = "TopicAdapter";
    private ArrayList<HeliosTopicContext> mTopics;
    private TopicAdapter.OnItemClickListener mOnClickListener;

    // Constructor
    public TopicAdapter(ArrayList<HeliosTopicContext> heliosTopics) {
        mTopics = heliosTopics;
    }

    public void setOnItemClickListener(TopicAdapter.OnItemClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    protected class TopicViewHolder extends RecyclerView.ViewHolder {
        public View view;

        public TopicViewHolder(View v) {
            super(v);

            itemView.setOnClickListener(v1 -> {
                Log.d(TAG, "Element " + getAdapterPosition() + " clicked.");
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    HeliosTopicContext topic = mTopics.get(pos);
                    if (mOnClickListener != null) {
                        mOnClickListener.onClick(topic);
                    }
                }
            });
            itemView.setOnLongClickListener(v12 -> {
                Log.d(TAG, "Element " + getAdapterPosition() + " long clicked.");
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    HeliosTopicContext topic = mTopics.get(pos);
                    if (mOnClickListener != null) {
                        mOnClickListener.onLongClick(topic);
                    }
                }
                return true;
            });
            view = v;
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public TopicAdapter.TopicViewHolder onCreateViewHolder(ViewGroup parent,
                                                           int viewType) {
        // create a new view
        View v = (View) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_main_chat, parent, false);

        TopicAdapter.TopicViewHolder vh = new TopicAdapter.TopicViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(TopicAdapter.TopicViewHolder holder, int position) {
        HeliosTopicContext topic = mTopics.get(position);
        holder.view.setTag(position);

        // Lookup view for data population
        TextView tvParticipants = (TextView) holder.view.findViewById(R.id.participantTextView);
        TextView tvTopic = (TextView) holder.view.findViewById(R.id.topicTextView);
        TextView tvTime = (TextView) holder.view.findViewById(R.id.timeTextView);

        // Populate the data into the template view using the data object
        tvParticipants.setText(topic.participants);

        // Add type of topic
        if (!TextUtils.isEmpty(topic.uuid)) {
            tvTopic.setText("DirectMessage: " + topic.topic);
        } else {
            tvTopic.setText("Topic: " + topic.topic);
        }

        tvTime.setText(topic.ts);
        Log.d(TAG, "last holder at " + position);
    }

    // Return the size of the dataset
    @Override
    public int getItemCount() {
        return mTopics.size();
    }

    public interface OnItemClickListener {
        /**
         * Called when a view has been clicked.
         *
         * @param htc The msg that was clicked
         */
        void onClick(HeliosTopicContext htc);

        /**
         * Called when a view has been clicked.
         *
         * @param htc The msg that was clicked
         */
        void onLongClick(HeliosTopicContext htc);
    }
}
