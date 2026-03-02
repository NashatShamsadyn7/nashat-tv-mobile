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

    private MaterialToolbar toolbar;
    private EditText editSearch;
    private MaterialSwitch switchFavorites;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private RecyclerView recyclerRecommended;
    private MaterialButton fabSettings;
    private MaterialButton btnViewMode;
    private MaterialButton btnSeeAllRecommended;
    private MaterialButton btnVoiceSearch;

    private ChannelAdapter adapter;
    private RecommendedAdapter recommendedAdapter;
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
            "Rudaw TV", "Kurdistan 24", "Kurdsat News", "Kurdsat", "Kurdistan TV",
            "NRT TV", "Waar TV", "TRT Kurdi", "KurdMax Show", "KurdMax Music",
            "Rojava TV", "Ronahi TV", "Sterk TV", "Channel 8 Kurdish", "Mixkurdy",
            "Zarok TV", "Payam TV", "4 Kurd TV"
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
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        editSearch = findViewById(R.id.editSearch);
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        switchFavorites = findViewById(R.id.switchFavorites);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        recycler = findViewById(R.id.recyclerChannels);
        recyclerRecommended = findViewById(R.id.recyclerRecommended);
        fabSettings = findViewById(R.id.fabSettings);
        btnViewMode = findViewById(R.id.btnViewMode);
        btnSeeAllRecommended = findViewById(R.id.btnSeeAllRecommended);

        favoritesStore = new FavoritesStore(this);
        // Firebase is used as the sole data source; JSON repository disabled
        // repository = new ChannelRepository(this);
    }

    private void setupToolbar() {
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
    }

    private void setupCategoryChips() {
        // Category chips removed - no longer needed
    }

    private void setupRecyclerViews() {
        adapter = new ChannelAdapter(favoritesStore, this);
        updateLayoutManager();
        recycler.setAdapter(adapter);

        recommendedAdapter = new RecommendedAdapter(this);
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerRecommended.setLayoutManager(lm);
        recyclerRecommended.setAdapter(recommendedAdapter);
        
        findViewById(R.id.recommendedSection).setVisibility(View.VISIBLE);
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
        fabSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnViewMode.setOnClickListener(v -> toggleViewMode());
        btnSeeAllRecommended.setOnClickListener(v -> {
            switchFavorites.setChecked(true);
        });
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
            btnViewMode.setText(R.string.list_view);
            btnViewMode.setIcon(getDrawable(R.drawable.ic_list_24));
        } else {
            btnViewMode.setText(R.string.grid_view);
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

        // Attach a single realtime listener to the /channels node
        channelsRef = com.google.firebase.database.FirebaseDatabase
                .getInstance("https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("channels");

        channelsListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                allChannels.clear();
                int count = 0;

                // Decide source node: /channels if present, else use root
                com.google.firebase.database.DataSnapshot source = snapshot;
                if (snapshot.hasChild("channels")) {
                    source = snapshot.child("channels");
                    Log.d("FirebaseMain", "Using /channels node");
                } else {
                    Log.d("FirebaseMain", "Using DB root as source");
                }

                // Collect channels from either flat list or grouped by category
                for (com.google.firebase.database.DataSnapshot node : source.getChildren()) {
                    boolean looksLikeChannel = node.hasChild("name") || node.hasChild("url") || node.hasChild("logo");

                    if (looksLikeChannel) {
                        Channel c = node.getValue(Channel.class);
                        if (c != null) {
                            allChannels.add(c);
                            count++;
                            Log.d("FirebaseMain", "Loaded channel: " + c.getName());
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
                                Log.d("FirebaseMain", "Loaded channel (grouped): " + c2.getName());
                            }
                        }
                    }
                }

                applyFilters();
                swipeRefresh.setRefreshing(false);
                Log.d("FirebaseMain", "Total channels loaded: " + count);

                if (count == 0) {
                    // Help diagnose by printing top-level keys
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
                swipeRefresh.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Firebase error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("FirebaseMain", "Database error", error.toException());
            }
        };

        channelsRef.addValueEventListener(channelsListener);
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

            if (matchesQuery && matchesFav) {
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

        buildRecommended();
        updateRecommendedVisibility();
    }

    private void updateRecommendedVisibility() {
        View recommendedSection = findViewById(R.id.recommendedSection);
        if (recommendedSection != null) {
            String query = editSearch.getText() != null ? editSearch.getText().toString().trim() : "";
            boolean shouldShow = !switchFavorites.isChecked() &&
                               (query.isEmpty() || query.length() < 2);
            
            recommendedSection.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
        }
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

    private void buildRecommended() {
        Set<Channel> set = new LinkedHashSet<>();

        for (Channel c : allChannels) {
            if (favoritesStore.isFavorite(c)) set.add(c);
            if (set.size() >= 10) break;
        }

        if (set.size() < 10) {
            for (String name : CURATED_ORDER) {
                Channel match = findByName(allChannels, name);
                if (match != null) {
                    set.add(match);
                    if (set.size() >= 12) break;
                }
            }
        }

        recommendedAdapter.submit(new ArrayList<>(set));
    }

    private Channel findByName(List<Channel> list, String name) {
        if (name == null) return null;
        for (Channel c : list) {
            if (c != null && name.equalsIgnoreCase(c.getName())) return c;
        }
        return null;
    }

    @Override
    public void onWatchClicked(Channel channel) {
        if (channel == null || !channel.hasStream()) return;
        android.content.Intent i = new android.content.Intent(this, PlayerActivity.class);
        i.putExtra("name", channel.getName());
        i.putExtra("logo", channel.getLogo());
        i.putExtra("url", channel.getUrl());
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