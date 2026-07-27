package com.nashat.tv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nashat.tv.util.ImageLoader;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.speech.RecognizerIntent;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity implements ChannelAdapter.Listener {

    private static final String PREFS = "home_prefs";
    private static final String KEY_SORT = "sort_mode";
    private static final String SORT_CURATED = "CURATED";
    private static final String SORT_AZ = "AZ";
    private static final int REQ_VOICE = 1001;
    private static final int REQ_RECORD_AUDIO = 2001;

    private EditText editSearch;
    private MaterialSwitch switchFavorites;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private View fabSettings;
    private MaterialButton btnViewMode;
    private MaterialButton btnVoiceSearch;
    private String activeCategory = "All";

    private ChannelAdapter adapter;
    private FavoritesStore favoritesStore;
    private ChannelRepository repository;

    private final List<Channel> allChannels = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // Firebase listener management to avoid duplicate listeners
    private com.google.firebase.database.ValueEventListener channelsListener;
    private com.google.firebase.database.DatabaseReference channelsRef;

    private String sortMode = SORT_CURATED;
    private boolean isGridView = true;
    private static final List<String> CURATED_ORDER = Arrays.asList(
            "Rûdaw TV", "Kurdistan24 TV", "KurdMax TV Sorani", "KurdMax Show Sorani",
            "Nalia TV", "Nalia TV 2", "KurdSat TV", "KurdSat News TV", "Ava TV",
            "Waar TV", "Kurdistan TV", "Zagros TV", "ZAGROS 24", "Gelî KurdIstan TV",
            "KurdMax TV Kurmanci", "KurdMax Show Kurmanci", "KurdMax Music",
            "Kurd Channel", "Duhok TV", "Spêda TV", "Payam TV", "Kirkuk TV",
            "Euro kurd TV", "Halabja TV", "Ranya TV", "Slemani TV", "Zaxo TV",
            "4 Kurd TV", "Kalapur TV", "Sterk TV", "Med Müzik TV", "Çira TV",
            "Komala TV", "Kurdana TV", "Kurd 7", "IMK TV", "Nishtmani mn NTV",
            "TV KURDI", "BASK TV", "BASK KIDS", "BASK Plus", "Amozhgary TV",
            "Amozhgary academy", "NRT 3 Kids", "NRT 4", "NRT SPORT", "AVA Sport",
            "DUHOK TV SPORT", "Zaxo Sport", "Speda Drama", "Parwarday TV Hawler",
            "Parwarday TV Slemani", "Parwarday TV Duhok", "Kurdmax Pepule",
            "Niga Kids", "NIGA Family", "Niga Movies", "Avar TV", "UTV Duhok"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupToolbar();
        setupRecyclerViews();
        setupListeners();

        loadChannels(false);
        AppUpdater.checkForUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("LastWatched", MODE_PRIVATE);
        String savedTmdbId = prefs.getString("tmdbId", "");
        View layContinueWatching = findViewById(R.id.layContinueWatching);
        if (layContinueWatching != null) {
            if (!savedTmdbId.isEmpty()) {
                layContinueWatching.setVisibility(View.VISIBLE);
                android.widget.TextView txtContinueWatching = findViewById(R.id.txtContinueWatching);
                String title = prefs.getString("title", "Unknown Title");
                txtContinueWatching.setText("Continue: " + title);
                
                boolean isTv = prefs.getBoolean("isTvSeries", false);
                int season = prefs.getInt("season", 1);
                int episode = prefs.getInt("episode", 1);
                
                layContinueWatching.setOnClickListener(v -> {
                    Intent intent = new Intent(this, WebPlayerActivity.class);
                    intent.putExtra("tmdbId", savedTmdbId);
                    intent.putExtra("isTvSeries", isTv);
                    if (isTv) {
                        intent.putExtra("season", season);
                        intent.putExtra("episode", episode);
                    }
                    startActivity(intent);
                });
            } else {
                layContinueWatching.setVisibility(View.GONE);
            }
        }
    }

    private void initializeViews() {
        editSearch = findViewById(R.id.editSearch);
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        switchFavorites = findViewById(R.id.switchFavorites);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        recycler = findViewById(R.id.recyclerChannels);
        fabSettings = findViewById(R.id.fabSettings);
        btnViewMode = findViewById(R.id.btnViewMode);

        favoritesStore = new FavoritesStore(this);
    }

    private void setupToolbar() {
        // No toolbar in new design
    }

    private void setupCategoryChips() {
        View chipAll = findViewById(R.id.chipAll);
        View chipNews = findViewById(R.id.chipNews);
        View chipEntertainment = findViewById(R.id.chipEntertainment);
        View chipSports = findViewById(R.id.chipSports);
        View chipKids = findViewById(R.id.chipKids);
        View chipMusic = findViewById(R.id.chipMusic);

        View.OnClickListener chipListener = v -> {
            // Reset all chips
            chipAll.setBackgroundResource(R.drawable.chip_bg);
            chipNews.setBackgroundResource(R.drawable.chip_bg);
            chipEntertainment.setBackgroundResource(R.drawable.chip_bg);
            chipSports.setBackgroundResource(R.drawable.chip_bg);
            chipKids.setBackgroundResource(R.drawable.chip_bg);
            chipMusic.setBackgroundResource(R.drawable.chip_bg);

            // Activate clicked
            v.setBackgroundResource(R.drawable.chip_bg_active);

            int id = v.getId();
            if (id == R.id.chipAll) activeCategory = "All";
            else if (id == R.id.chipNews) activeCategory = "News";
            else if (id == R.id.chipEntertainment) activeCategory = "Entertainment";
            else if (id == R.id.chipSports) activeCategory = "Sports";
            else if (id == R.id.chipKids) activeCategory = "Kids";
            else if (id == R.id.chipMusic) activeCategory = "Music";
            applyFilters();
        };

        chipAll.setOnClickListener(chipListener);
        chipNews.setOnClickListener(chipListener);
        chipEntertainment.setOnClickListener(chipListener);
        chipSports.setOnClickListener(chipListener);
        chipKids.setOnClickListener(chipListener);
        chipMusic.setOnClickListener(chipListener);
    }

    private void setupRecyclerViews() {
        adapter = new ChannelAdapter(favoritesStore, this);
        updateLayoutManager();
        recycler.setAdapter(adapter);
    }

    private void setupListeners() {
        btnVoiceSearch.setOnClickListener(v -> startVoiceSearch());
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
        });

        switchFavorites.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilters());
        swipeRefresh.setOnRefreshListener(() -> loadChannels(true));

        // Bottom nav - Settings
        fabSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // Bottom nav - Movies
        View navMovies = findViewById(R.id.navMovies);
        if (navMovies != null) {
            navMovies.setOnClickListener(v -> {
                Intent intent = new Intent(this, MoviesActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        // Bottom nav - Series
        View navSeries = findViewById(R.id.navSeries);
        if (navSeries != null) {
            navSeries.setOnClickListener(v -> {
                Intent intent = new Intent(this, SeriesActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        // Bottom nav - Favorites
        View navFavorites = findViewById(R.id.navFavorites);
        if (navFavorites != null) {
            navFavorites.setOnClickListener(v -> {
                switchFavorites.setChecked(!switchFavorites.isChecked());
            });
        }

        btnViewMode.setOnClickListener(v -> toggleViewMode());
        setupCategoryChips();
    }

    private void toggleViewMode() {
        isGridView = !isGridView;
        updateLayoutManager();
        updateViewModeButton();
    }

    private void updateLayoutManager() {
        if (isGridView) {
            recycler.setLayoutManager(new GridLayoutManager(this, computeSpanCount()));
        } else {
            recycler.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private void updateViewModeButton() {
        if (isGridView) {
            btnViewMode.setIcon(getDrawable(R.drawable.ic_list_24));
        } else {
            btnViewMode.setIcon(getDrawable(R.drawable.ic_grid_view_24));
        }
    }

    private int computeSpanCount() {
        int dp = getResources().getConfiguration().screenWidthDp;
        if (dp >= 900) return 4;
        if (dp >= 600) return 3;
        return 2;
    }

    private boolean onMenuItemClick(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_about) {
            startActivity(new android.content.Intent(this, AboutActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return true;
        } else if (id == R.id.action_language) {
            showLanguageDialog();
            return true;
        }
        return false;
    }

    private void loadChannels(boolean force) {
        swipeRefresh.setRefreshing(true);

        // Remove previous listener if any to avoid duplicates
        if (channelsListener != null && channelsRef != null) {
            channelsRef.removeEventListener(channelsListener);
            channelsListener = null;
            channelsRef = null;
        }

        // First, load local Kurdish channels from asset (always available, instant)
        final List<Channel> localChannels = loadLocalAssetChannels();

        // Attach a single realtime listener to the root (channels are at DB root, keys: 0,1,2...)
        channelsRef = com.google.firebase.database.FirebaseDatabase
                .getInstance("https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference();

        channelsListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                allChannels.clear();
                int count = 0;

                // Data is at root as numbered keys (0, 1, 2...) each containing channel objects
                for (com.google.firebase.database.DataSnapshot node : snapshot.getChildren()) {
                    // Try direct channel parsing
                    boolean looksLikeChannel = node.hasChild("name") || node.hasChild("url") || node.hasChild("logo");

                    if (looksLikeChannel) {
                        Channel c = node.getValue(Channel.class);
                        if (c != null) {
                            allChannels.add(c);
                            count++;
                        }
                        continue;
                    }

                    // If not a direct channel, try a second-level grouping (e.g., categories -> items)
                    for (com.google.firebase.database.DataSnapshot sub : node.getChildren()) {
                        if (sub.hasChild("name") || sub.hasChild("url") || sub.hasChild("logo")) {
                            Channel c2 = sub.getValue(Channel.class);
                            if (c2 != null) {
                                allChannels.add(c2);
                                count++;
                            }
                        }
                    }
                }

                // Merge local asset channels, avoiding duplicates
                mergeChannels(localChannels);

                applyFilters();
                swipeRefresh.setRefreshing(false);
                Log.d("FirebaseMain", "Total channels loaded: " + allChannels.size());

                if (allChannels.isEmpty()) {
                    StringBuilder keys = new StringBuilder();
                    int i = 0;
                    for (com.google.firebase.database.DataSnapshot n : snapshot.getChildren()) {
                        if (i++ >= 10) break;
                        if (keys.length() > 0) keys.append(", ");
                        keys.append(n.getKey());
                    }
                    Toast.makeText(MainActivity.this, "No channels found. Top keys: " + keys, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                // If Firebase fails, still show local channels
                allChannels.clear();
                allChannels.addAll(localChannels);
                applyFilters();
                swipeRefresh.setRefreshing(false);
                Log.e("FirebaseMain", "Database error, showing local channels only", error.toException());
            }
        };

        channelsRef.addValueEventListener(channelsListener);
    }

    /**
     * Load channels from local asset M3U file (always available, no internet needed).
     */
    private List<Channel> loadLocalAssetChannels() {
        List<Channel> channels = new ArrayList<>();
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(getAssets().open("kurdish_channels.m3u"), "UTF-8"));
            channels = M3UParser.parseM3U(reader);
            reader.close();
            Log.d("LocalAsset", "Loaded " + channels.size() + " channels from asset");
        } catch (Exception e) {
            Log.e("LocalAsset", "Error loading local channels", e);
        }
        return channels;
    }

    /**
     * Merge new channels into allChannels, skipping any that share a name with existing entries.
     */
    private void mergeChannels(List<Channel> newChannels) {
        Set<String> existingNames = new LinkedHashSet<>();
        for (Channel c : allChannels) {
            existingNames.add(c.getName().toLowerCase(Locale.US).replaceAll("\\s*\\(.*\\)\\s*", "").trim());
        }

        for (Channel nc : newChannels) {
            String cleanName = nc.getName().toLowerCase(Locale.US).replaceAll("\\s*\\(.*\\)\\s*", "").trim();
            if (!existingNames.contains(cleanName)) {
                allChannels.add(nc);
                existingNames.add(cleanName);
            }
        }
    }

    private void applyFilters() {
        String q = editSearch.getText() != null ? editSearch.getText().toString().trim().toLowerCase(Locale.US) : "";
        boolean showFavOnly = switchFavorites.isChecked();

        List<Channel> filtered = new ArrayList<>();
        for (Channel c : allChannels) {
            if (c == null) continue;

            boolean matchesQuery = q.isEmpty()
                    || c.getName().toLowerCase(Locale.US).contains(q)
                    || c.getDescription().toLowerCase(Locale.US).contains(q)
                    || c.getCategory().toLowerCase(Locale.US).contains(q);

            boolean matchesFav = !showFavOnly || favoritesStore.isFavorite(c);

            // Category chip filter
            boolean matchesCategory = "All".equals(activeCategory)
                    || c.getCategory().toLowerCase(Locale.US).contains(activeCategory.toLowerCase(Locale.US))
                    || c.getName().toLowerCase(Locale.US).contains(activeCategory.toLowerCase(Locale.US));

            if (matchesQuery && matchesFav && matchesCategory) {
                filtered.add(c);
            }
        }

        List<Channel> fav = new ArrayList<>();
        List<Channel> other = new ArrayList<>();
        for (Channel c : filtered) {
            if (favoritesStore.isFavorite(c)) fav.add(c); else other.add(c);
        }
        sortList(fav);
        sortList(other);

        List<Channel> finalList = new ArrayList<>(fav.size() + other.size());
        finalList.addAll(fav);
        finalList.addAll(other);

        adapter.submitList(finalList);

        // Update channel count badge
        android.widget.TextView tvCount = findViewById(R.id.tvChannelCount);
        if (tvCount != null) {
            tvCount.setText(String.valueOf(finalList.size()));
        }

        updateRecommendations(q, showFavOnly);
    }

    private void updateRecommendations(String query, boolean showFavOnly) {
        View layRecommendations = findViewById(R.id.layRecommendations);
        RecyclerView recyclerRecommendations = findViewById(R.id.recyclerRecommendations);
        if (layRecommendations == null || recyclerRecommendations == null) return;

        // Hide if searching, filtering favorites, or inside a specific category (keep it main page only)
        if (!query.isEmpty() || showFavOnly || !"All".equals(activeCategory)) {
            layRecommendations.setVisibility(View.GONE);
            return;
        }

        WatchStatsManager stats = new WatchStatsManager(this);
        String topCategory = stats.getTopCategory();
        if ("General".equalsIgnoreCase(topCategory) || topCategory.isEmpty()) {
            layRecommendations.setVisibility(View.GONE);
            return;
        }

        List<Channel> recommended = new ArrayList<>();
        for (Channel c : allChannels) {
            // Suggest unseen channels from their favorite category
            if (c.getCategory().equalsIgnoreCase(topCategory) && !favoritesStore.isFavorite(c)) {
                recommended.add(c);
            }
        }

        if (recommended.isEmpty()) {
            layRecommendations.setVisibility(View.GONE);
            return;
        }

        // Randomize to keep it fresh
        Collections.shuffle(recommended);
        if (recommended.size() > 10) {
            recommended = recommended.subList(0, 10);
        }

        ChannelAdapter recAdapter = new ChannelAdapter(favoritesStore, this, true);
        recAdapter.submitList(recommended);
        recyclerRecommendations.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerRecommendations.setAdapter(recAdapter);
        layRecommendations.setVisibility(View.VISIBLE);
    }

    private void sortList(List<Channel> list) {
        if (SORT_AZ.equals(sortMode)) {
            Collections.sort(list, Comparator.comparing(c -> c.getName().toLowerCase(Locale.US)));
        } else {
            Collections.sort(list, (a, b) -> {
                int ia = indexInCurated(a.getName());
                int ib = indexInCurated(b.getName());
                if (ia == ib) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
                return Integer.compare(ia, ib);
            });
        }
    }

    private int indexInCurated(String name) {
        if (name == null) return Integer.MAX_VALUE / 2;
        int idx = CURATED_ORDER.indexOf(name.trim());
        return idx >= 0 ? idx : Integer.MAX_VALUE / 2;
    }

    @Override
    public void onWatchClicked(Channel channel) {
        if (channel == null || !channel.hasStream()) return;
        android.content.Intent i = new android.content.Intent(this, PlayerActivity.class);
        i.putExtra("name", channel.getName());
        i.putExtra("logo", channel.getLogo());
        i.putExtra("url", channel.getUrl());
        i.putExtra("category", channel.getCategory());
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onFavoriteToggled(Channel channel, boolean isFavorite) {
        applyFilters();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void startVoiceSearch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.hint_search));
        try {
            startActivityForResult(intent, REQ_VOICE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice search not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && editSearch != null) {
                editSearch.setText(results.get(0));
                if (editSearch.getText() != null) {
                    editSearch.setSelection(editSearch.getText().length());
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceSearch();
            } else {
                Toast.makeText(this, "Microphone permission is required for voice search", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLanguageDialog() {
        final CharSequence[] names = new CharSequence[] {
                getString(R.string.lang_ckb),
                getString(R.string.lang_ar),
                getString(R.string.lang_en)
        };
        final String[] codes = new String[] { "ckb", "ar", "en" };

        int checkedItem = 0;
        String cur = LocaleManager.getSavedLocale(this);
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equalsIgnoreCase(cur)) { checkedItem = i; break; }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_language)
                .setSingleChoiceItems(names, checkedItem, (dialog, which) -> {
                    LocaleManager.setLocale(this, codes[which]);
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    static class RecommendedAdapter extends RecyclerView.Adapter<RecommendedAdapter.VH> {

        private final List<Channel> items = new ArrayList<>();
        private final ChannelAdapter.Listener listener;

        RecommendedAdapter(ChannelAdapter.Listener listener) {
            this.listener = listener;
        }

        void submit(List<Channel> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recommended_channel, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Channel item = items.get(pos);
            h.name.setText(item.getName());
            ImageLoader.load(h.logo, item.getLogo());
            h.itemView.setOnClickListener(v -> listener.onWatchClicked(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final android.widget.ImageView logo;
            final android.widget.TextView name;
            VH(@NonNull android.view.View itemView) {
                super(itemView);
                logo = itemView.findViewById(R.id.imgLogo);
                name = itemView.findViewById(R.id.txtName);
            }
        }
    }
}