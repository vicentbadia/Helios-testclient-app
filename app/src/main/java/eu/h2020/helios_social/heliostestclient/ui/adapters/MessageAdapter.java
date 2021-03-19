package eu.h2020.helios_social.heliostestclient.ui.adapters;

import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import eu.h2020.helios_social.core.messaging.MessagingConstants;
import eu.h2020.helios_social.core.storage.HeliosStorageUtils;
import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.core.messaging.data.HeliosMessagePart;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ChatViewHolder> {
    private static final String TAG = "MessageAdapter";
    private String mUserId;
    private ArrayList<HeliosMessagePart> mMessages;
    private int mLastItemPosition = -1;
    private OnItemClickListener mOnClickListener;

    // Constructor
    public MessageAdapter(String userId, ArrayList<HeliosMessagePart> dataSet) {
        mUserId = userId;
        mMessages = dataSet;
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    protected class ChatViewHolder extends RecyclerView.ViewHolder {
        public View view;
        public ChatViewHolder(View v) {
            super(v);

            v.setOnClickListener(v1 -> {
                Log.d(TAG, "Element " + getAdapterPosition() + " clicked.");
                int pos = getAdapterPosition();
                if(pos != RecyclerView.NO_POSITION) {
                    HeliosMessagePart msg = mMessages.get(getAdapterPosition());
                    if(mOnClickListener != null){
                        mOnClickListener.onClick(msg);
                    }
                }
            });
            view = v;
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ChatViewHolder onCreateViewHolder(ViewGroup parent,
                                             int viewType) {
        // create a new view
        View v = (View) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);

        ChatViewHolder vh = new ChatViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        HeliosMessagePart msg = mMessages.get(position);
        holder.view.setTag(position);

        // Lookup view for data population
        TextView tvName = (TextView) holder.view.findViewById(R.id.nameTextView);
        TextView tvMsg = (TextView) holder.view.findViewById(R.id.messageTextView);

        // Populate the data into the template view using the data object
        tvName.setText(msg.senderName + " (" + msg.getLocaleTs() + ")");
        tvMsg.setText(msg.msg);

        if (msg.senderUUID.equals(mUserId)) {
            tvName.setTextColor(Color.GREEN);
            tvMsg.setTextColor(Color.GREEN);
        } else {
            tvName.setTextColor(Color.BLACK);
            tvMsg.setTextColor(Color.BLACK);
        }

        // Show image/video icon, if one is present
        ImageView imageView = holder.view.findViewById(R.id.photoImageView);

        if (!TextUtils.isEmpty(msg.mediaFileName)) {
            // TODO update if storage changed
            String mediaFilePath = "";

            // This file was received from someone
            if (msg.mediaFileName.startsWith(MessagingConstants.HELIOS_RECEIVED_FILENAME_START)) {
                mediaFilePath = "file://" + holder.view.getContext().getExternalFilesDir(null) + HeliosStorageUtils.FILE_SEPARATOR +
                        HeliosStorageUtils.HELIOS_DIR + HeliosStorageUtils.FILE_SEPARATOR +
                        msg.mediaFileName;
            } else {
                // This is something that the user sent
                mediaFilePath = "file://" + holder.view.getContext().getFilesDir() + HeliosStorageUtils.FILE_SEPARATOR +
                        HeliosStorageUtils.HELIOS_DIR + HeliosStorageUtils.FILE_SEPARATOR +
                        msg.mediaFileName;
            }

            Picasso.get().load(mediaFilePath).resize(800, 0)
                    .centerCrop()
                    .placeholder(R.drawable.helios_launcher_foreground)
                    .error(R.drawable.helios_launcher_foreground)
                    .into(imageView);
        } else {
            imageView.setImageDrawable(null);
        }

        // Show a symbol if message was sent and received
        ImageView msgReceivedView = (ImageView) holder.view.findViewById(R.id.messageReceived);
        if (msg.msgReceived && msg.senderUUID.equals(mUserId)) {
            msgReceivedView.setVisibility(View.VISIBLE);
        } else {
            msgReceivedView.setVisibility(View.INVISIBLE);
        }

        // Update last position bound
        if (position > mLastItemPosition) {
            mLastItemPosition = position;
        }
        //Log.d(TAG, "last holder at " + position);
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    public void addItem(HeliosMessagePart msg) {
        Log.d(TAG, "addItem " + msg.msg);
        mMessages.add(msg);
    }

    public int getLastVisibleItemPosition(){
        return mLastItemPosition;
    }

    public void setOnItemClickListener(OnItemClickListener onClickListener){
        mOnClickListener = onClickListener;
    }

    public interface OnItemClickListener {
        /**
         * Called when a view has been clicked.
         *
         * @param msg The msg that was clicked
         */
        void onClick(HeliosMessagePart msg);
    }
}
