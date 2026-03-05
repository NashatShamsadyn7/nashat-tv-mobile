package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class ExploreActivity extends BaseActivity {

    private RecyclerView recyclerCategories;
    private ProgressBar progressBar;

    // Preset M3U playlists that are lightweight, fast, and globally working (no geoblocks)
    private static final String[][] CATEGORIES = {
            {"🌍 Free-TV Global", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", "📺"},
            {"☀️ Kurdish Channels", "https://iptv-org.github.io/iptv/languages/kur.m3u", "☀️"},
            {"🌙 Arabic Channels", "https://iptv-org.github.io/iptv/languages/ara.m3u", "🌙"},
            {"🇮🇶 Iraq Channels", "https://iptv-org.github.io/iptv/countries/iq.m3u", "🇮🇶"},
            {"🇬🇧 English Channels", "https://iptv-org.github.io/iptv/languages/eng.m3u", "🇬🇧"},
            {"📰 Global News", "https://iptv-org.github.io/iptv/categories/news.m3u", "📰"},
            {"⚽ Global Sports", "https://iptv-org.github.io/iptv/categories/sports.m3u", "⚽"},
            {"👶 Kids & Cartoons", "https://iptv-org.github.io/iptv/categories/kids.m3u", "👶"}
    };

    // Public domain and geoblock-free specific categories
    private static final String[][] MOVIES = {
            {"🎬 Movies Channel", "https://iptv-org.github.io/iptv/categories/movies.m3u", "🎬"},
            {"🎥 VOD Public Free Movies", "https://raw.githubusercontent.com/BiteYourConsole/IPTV-Free-Links/master/Vod.m3u", "🎞️"},
            {"🎭 Entertainment", "https://iptv-org.github.io/iptv/categories/entertainment.m3u", "🎭"},
            {" 😂 Comedy ", "https://iptv-org.github.io/iptv/categories/comedy.m3u", "😂"},
            {"🎞️ Animation", "https://iptv-org.github.io/iptv/categories/animation.m3u", "🎞️"},
            {"📚 Documentaries", "https://iptv-org.github.io/iptv/categories/documentary.m3u", "📚"},
            {"👨‍👩‍👧 Family", "https://iptv-org.github.io/iptv/categories/family.m3u", "👨‍👩‍👧"},
            {"🎵 Music & Audio", "https://iptv-org.github.io/iptv/categories/music.m3u", "🎵"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        progressBar = findViewById(R.id.progressBar);
        recyclerCategories = findViewById(R.id.recyclerCategories);
        recyclerCategories.setLayoutManager(new LinearLayoutManager(this));

        // Build category list
        List<CategoryItem> items = new ArrayList<>();

        // ★ Featured Kurdish Entertainment (local asset, always works)
        items.add(new CategoryItem("⭐ Kurdish Channels (Verified & Working)", null, true));
        items.add(new CategoryItem("☀️ All Kurdish TV (31 Channels)", "asset://kurdish_channels.m3u", false));

        // Live TV by language/country
        items.add(new CategoryItem("📡 Live TV by Region", null, true));
        for (String[] cat : CATEGORIES) {
            items.add(new CategoryItem(cat[0], cat[1], false));
        }

        // Movies & Entertainment categories
        items.add(new CategoryItem("🎬 Movies & Entertainment", null, true));
        for (String[] movie : MOVIES) {
            items.add(new CategoryItem(movie[0], movie[1], false));
        }

        CategoryAdapter adapter = new CategoryAdapter(items, item -> {
            if (item.url.startsWith("asset://")) {
                loadAssetPlaylist(item.name, item.url.replace("asset://", ""));
            } else {
                loadM3UPlaylist(item.name, item.url);
            }
        });
        recyclerCategories.setAdapter(adapter);

        // Custom URL button
        MaterialButton btnCustomUrl = findViewById(R.id.btnCustomUrl);
        btnCustomUrl.setOnClickListener(v -> showCustomUrlDialog());
    }

    private void loadAssetPlaylist(String name, String assetFile) {
        progressBar.setVisibility(View.VISIBLE);
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(getAssets().open(assetFile), "UTF-8"));
            List<Channel> channels = M3UParser.parseM3U(reader);
            reader.close();
            progressBar.setVisibility(View.GONE);

            if (channels.isEmpty()) {
                Toast.makeText(this, "No channels found", Toast.LENGTH_SHORT).show();
            } else {
                showChannelList(name, channels);
            }
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error loading: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadM3UPlaylist(String name, String url) {
        progressBar.setVisibility(View.VISIBLE);

        M3UParser.parseFromUrl(url, new M3UParser.M3UCallback() {
            @Override
            public void onSuccess(List<Channel> channels) {
                progressBar.setVisibility(View.GONE);
                if (channels.isEmpty()) {
                    Toast.makeText(ExploreActivity.this,
                            "No channels found in playlist", Toast.LENGTH_SHORT).show();
                    return;
                }
                showChannelList(name, channels);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ExploreActivity.this,
                        "Error loading playlist. Service might be down.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showChannelList(String title, List<Channel> channels) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_m3u_channels, null);
        android.widget.TextView txtTitle = dialogView.findViewById(R.id.txtDialogTitle);
        EditText editSearch = dialogView.findViewById(R.id.editSearchChannel);
        RecyclerView recycler = dialogView.findViewById(R.id.recyclerDialogChannels);

        txtTitle.setText(title + " (" + channels.size() + " channels)");
        recycler.setLayoutManager(new LinearLayoutManager(this));
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setNegativeButton("Close", null)
                .show();
                
        // Add a transparent background to not clip rounded corners entirely
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        DialogAdapter adapter = new DialogAdapter(channels, channel -> {
            dialog.dismiss();
            playChannel(channel);
        });
        recycler.setAdapter(adapter);

        editSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                adapter.filter(s.toString());
            }
        });
    }

    private void playChannel(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("name", channel.getName());
        intent.putExtra("url", channel.getUrl());
        intent.putExtra("logo", channel.getLogo());
        intent.putExtra("category", channel.getCategory());
        startActivity(intent);
    }

    private void showCustomUrlDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_custom_url, null);
        EditText editUrl = dialogView.findViewById(R.id.editCustomUrl);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Import M3U Playlist")
                .setView(dialogView)
                .setPositiveButton("Import", (dialog, which) -> {
                    String url = editUrl.getText().toString().trim();
                    if (!url.isEmpty()) {
                        loadM3UPlaylist("Custom Playlist", url);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- Inner classes ----

    static class CategoryItem {
        String name;
        String url;
        boolean isHeader;

        CategoryItem(String name, String url, boolean isHeader) {
            this.name = name;
            this.url = url;
            this.isHeader = isHeader;
        }
    }

    static class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final List<CategoryItem> items;
        private final OnCategoryClick listener;

        interface OnCategoryClick {
            void onClick(CategoryItem item);
        }

        CategoryAdapter(List<CategoryItem> items, OnCategoryClick listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).isHeader ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View v = android.view.LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_explore_header, parent, false);
                return new HeaderVH(v);
            } else {
                View v = android.view.LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_explore_category, parent, false);
                return new ItemVH(v);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            CategoryItem item = items.get(position);
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).title.setText(item.name);
            } else if (holder instanceof ItemVH) {
                ((ItemVH) holder).title.setText(item.name);
                holder.itemView.setOnClickListener(v -> listener.onClick(item));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            android.widget.TextView title;
            HeaderVH(View v) {
                super(v);
                title = v.findViewById(R.id.txtHeader);
            }
        }

        static class ItemVH extends RecyclerView.ViewHolder {
            android.widget.TextView title;
            ItemVH(View v) {
                super(v);
                title = v.findViewById(R.id.txtCategory);
            }
        }
    }

    static class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ChannelVH> {
        private final List<Channel> allChannels;
        private final List<Channel> filtered;
        private final OnChannelClick listener;

        interface OnChannelClick {
            void onClick(Channel channel);
        }

        DialogAdapter(List<Channel> channels, OnChannelClick listener) {
            this.allChannels = new ArrayList<>(channels);
            this.filtered = new ArrayList<>(channels);
            this.listener = listener;
        }

        void filter(String query) {
            filtered.clear();
            if (query == null || query.isEmpty()) {
                filtered.addAll(allChannels);
            } else {
                String q = query.toLowerCase();
                for (Channel ch : allChannels) {
                    if (ch.getName().toLowerCase().contains(q) || ch.getCategory().toLowerCase().contains(q)) {
                        filtered.add(ch);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public ChannelVH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dialog_channel, parent, false);
            return new ChannelVH(v);
        }

        @Override
        public void onBindViewHolder(ChannelVH holder, int position) {
            Channel ch = filtered.get(position);
            holder.txtName.setText(ch.getName());
            
            String cat = ch.getCategory();
            if (cat.isEmpty()) {
                holder.txtCategory.setVisibility(View.GONE);
            } else {
                holder.txtCategory.setVisibility(View.VISIBLE);
                holder.txtCategory.setText(cat);
            }
            
            holder.itemView.setOnClickListener(v -> listener.onClick(ch));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        static class ChannelVH extends RecyclerView.ViewHolder {
            android.widget.TextView txtName;
            android.widget.TextView txtCategory;
            ChannelVH(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtChannelName);
                txtCategory = v.findViewById(R.id.txtChannelCategory);
            }
        }
    }
}
