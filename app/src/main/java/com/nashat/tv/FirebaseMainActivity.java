package com.nashat.tv;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FirebaseMainActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private FirebaseChannelAdapter adapter;
    private final List<Channel> channelList = new ArrayList<>();
    private ProgressBar progress;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firebase_main);

        // Make status bar transparent
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        recyclerView = findViewById(R.id.recyclerView);
        
        // Movie & Series style vertical list of horizontal rows
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        
        adapter = new FirebaseChannelAdapter(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        progress = findViewById(R.id.progress);
        emptyView = findViewById(R.id.emptyView);

        bindLoading(true);
        showEmpty(false);

        DatabaseReference db = buildDatabaseReference();
        if (db == null) {
            Log.e("Firebase", "DatabaseReference is null");
            bindLoading(false);
            showEmpty(true);
            return;
        }
        try { db.keepSynced(true); } catch (Throwable ignore) {}

        db.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                channelList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        Channel channel = snap.getValue(Channel.class);
                        if (channel != null) {
                            channelList.add(channel);
                        }
                    }
                }

                // Update adapter with grouped data
                adapter.updateData(channelList);

                bindLoading(false);
                boolean isEmpty = channelList.isEmpty();
                showEmpty(isEmpty);
                recyclerView.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Error: " + error.getMessage());
                bindLoading(false);
                TextView tvEmpty = findViewById(R.id.tvEmptyText);
                if (tvEmpty != null) {
                    tvEmpty.setText(R.string.error_loading);
                }
                showEmpty(true);
                recyclerView.setVisibility(View.INVISIBLE);
            }
        });
    }

    private DatabaseReference buildDatabaseReference() {
        try {
            String rtdbUrl = null;
            try {
                rtdbUrl = getString(R.string.firebase_rtdb_url);
            } catch (Exception ignore) {}
            if (rtdbUrl != null && rtdbUrl.startsWith("http")) {
                Log.d("Firebase", "Using RTDB URL from resources");
                return FirebaseDatabase.getInstance(rtdbUrl).getReference("channels");
            }
            Log.d("Firebase", "Using explicit regional RTDB URL fallback");
            return FirebaseDatabase
                    .getInstance("https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .getReference("channels");
        } catch (Throwable t) {
            Log.e("Firebase", "Failed to build DB reference: " + t.getMessage());
            try {
                return FirebaseDatabase.getInstance().getReference("channels");
            } catch (Throwable t2) {
                Log.e("Firebase", "Default DB instance failed: " + t2.getMessage());
                return null;
            }
        }
    }

    private void bindLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmpty(boolean empty) {
        if (emptyView != null) {
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }
}