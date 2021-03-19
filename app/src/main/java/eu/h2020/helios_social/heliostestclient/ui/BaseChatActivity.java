package eu.h2020.helios_social.heliostestclient.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import eu.h2020.helios_social.heliostestclient.R;
import eu.h2020.helios_social.heliostestclient.ui.adapters.MessageAdapter;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING;
import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;

public abstract class BaseChatActivity extends AppCompatActivity {
    public static final String TAG = "BaseChatActivity";
    protected MessageAdapter mMessageAdapter;
    protected RecyclerView mListView;
    protected RecyclerView.LayoutManager layoutManager;

    // Handle scrolling state
    protected boolean mHasUserScrolledList = false;
    protected int mLastVisiblePosition = 0;
    private ImageButton mScrollToBottomBtn;
    private TextView mScrollBtnTw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Enable the Up button
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setDisplayShowHomeEnabled(true);

        mListView = findViewById(R.id.messageListView);
        //mListView.setHasFixedSize(false);

        // use a linear layout manager
        layoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(layoutManager);

        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                //Log.d(TAG, "onScrollStateChanged " + newState);

                if (newState == SCROLL_STATE_IDLE) {
                    Log.d(TAG, "Scroll ended, adapter count " + (mMessageAdapter.getItemCount() - 1) + ", last loaded: " + mMessageAdapter.getLastVisibleItemPosition());

                    // Scroll stops and last item is visible
                    if (!mListView.canScrollVertically(1)) {
                        mHasUserScrolledList = false;
                        mScrollToBottomBtn.setVisibility(View.INVISIBLE);
                        mScrollToBottomBtn.setBackgroundColor(Color.LTGRAY);
                    } else {
                        // Scrolled stops and user has scrolled
                        mHasUserScrolledList = true;
                        mScrollToBottomBtn.setVisibility(View.VISIBLE);
                    }

                    // Update last position to calculate unseen messages
                    int currentLastPosition = mMessageAdapter.getLastVisibleItemPosition();
                    if (currentLastPosition >= mLastVisiblePosition) {
                        mLastVisiblePosition = currentLastPosition;
                    }
                    checkUnseenMessages();
                } else if (newState == SCROLL_STATE_DRAGGING) {
                    mHasUserScrolledList = true; // User started scrolling
                    // if first scroll, store the initial last item
                    if (mLastVisiblePosition == 0) {
                        mLastVisiblePosition = mMessageAdapter.getItemCount() - 1;
                    }
                } else {
                    mHasUserScrolledList = true;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                //Log.d(TAG, "onScrolled " + dx);
                //Log.d(TAG, "onScrolled " + dy);
            }
        });

        setupScrollButtons();
    }

    private void setupScrollButtons() {
        mScrollBtnTw = findViewById(R.id.textViewScrollBtn);
        mScrollToBottomBtn = findViewById(R.id.btnScrollToBottom);

        mScrollToBottomBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mListView.scrollToPosition(mMessageAdapter.getItemCount() - 1);
                mHasUserScrolledList = false;
                mLastVisiblePosition = mMessageAdapter.getItemCount() - 1;
                // hide
                hideScrollToBottom();
            }
        });
    }

    protected void checkUnseenMessages() {
        int lastItemPositionInAdapter = (mMessageAdapter.getItemCount() - 1);
        //Log.d(TAG, "mLastVisiblePosition " + mLastVisiblePosition);
        //Log.d(TAG, "lastItemPositionInAdapter " + lastItemPositionInAdapter);

        // If user has scrolled, check whether there's new messages unseen by scroll information
        if (mLastVisiblePosition < lastItemPositionInAdapter) {
            mScrollToBottomBtn.setBackgroundColor(Color.GREEN);
            mScrollToBottomBtn.setVisibility(View.VISIBLE);
            int unRead = lastItemPositionInAdapter - mLastVisiblePosition;
            mScrollBtnTw.setText(String.valueOf(unRead));
        } else {
            mScrollToBottomBtn.setBackgroundColor(Color.LTGRAY);
            mScrollBtnTw.setText("");
        }
    }

    protected void hideScrollToBottom() {
        // hide
        mScrollToBottomBtn.setBackgroundColor(Color.LTGRAY);
        mScrollToBottomBtn.setVisibility(View.INVISIBLE);
        mScrollBtnTw.setText("");
    }
}
