package com.nashat.tv;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.nashat.tv.ui.BottomNav;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiveTvActivity extends BaseActivity {

    private static final String TAG = "LiveTvActivity";

    private static final int GRID_COLUMNS = 4;
    private static final int PAGE_SIZE = 32;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> filteredChannels = new ArrayList<>();
    private final List<Channel> visibleChannels = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();

    private LiveTvChannelRepository repository;
    private LiveTvAdapter adapter;
    private GridLayoutManager gridLayoutManager;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private RecyclerView recyclerChannels;
    private ProgressBar progress;
    private LinearLayout emptyView;
    private LinearLayout skeletonView;
    private LinearLayout categoryContainer;
    private TextView tvEmptyText;
    private TextView tvScreenSubtitle;
    private TextView tvChannelCount;
    private EditText editSearch;
    private SwipeRefreshLayout swipeRefresh;

    private String selectedCategory = "All";
    private String searchQuery = "";
    private String userCountry = "";
    private String userLanguage = "";
    private boolean isLoadingPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_tv);

        repository = new LiveTvChannelRepository(this);

        // Clear stale local caches once to force loading fresh popularity sorted lists
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        if (!prefs.getBoolean("v2_sorting_cached_v3", false)) {
            LiveTvChannelCache.clear(this);
            prefs.edit().putBoolean("v2_sorting_cached_v3", true).apply();
        }

        detectUserProfile();
        initViews();
        setupSearch();
        setupBottomNavigation();
        loadChannels();
        getWindow().getDecorView().postDelayed(() -> LiveTvValidationWorker.schedule(this), 4000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacksAndMessages(null);
        backgroundExecutor.shutdownNow();
    }

    private void detectUserProfile() {
        Locale locale = Locale.getDefault();
        userCountry = locale.getCountry() != null ? locale.getCountry().toUpperCase(Locale.US) : "";
        userLanguage = locale.getLanguage() != null ? locale.getLanguage().toLowerCase(Locale.US) : "";

        try {
            TelephonyManager manager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (manager != null) {
                String simCountry = manager.getSimCountryIso();
                if (simCountry != null && !simCountry.trim().isEmpty()) {
                    userCountry = simCountry.toUpperCase(Locale.US);
                }
            }
        } catch (Exception ignore) {
        }
    }

    private void initViews() {
        recyclerChannels = findViewById(R.id.recyclerChannels);
        progress = findViewById(R.id.progress);
        emptyView = findViewById(R.id.emptyView);
        skeletonView = findViewById(R.id.skeletonView);
        categoryContainer = findViewById(R.id.categoryContainer);
        tvEmptyText = findViewById(R.id.tvEmptyText);
        tvScreenSubtitle = findViewById(R.id.tvScreenSubtitle);
        tvChannelCount = findViewById(R.id.tvChannelCount);
        editSearch = findViewById(R.id.editSearch);

        gridLayoutManager = new GridLayoutManager(this, GRID_COLUMNS);
        recyclerChannels.setLayoutManager(gridLayoutManager);
        recyclerChannels.setHasFixedSize(true);
        adapter = new LiveTvAdapter(this);
        recyclerChannels.setAdapter(adapter);
        recyclerChannels.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || isLoadingPage) {
                    return;
                }
                int visible = gridLayoutManager.getChildCount();
                int total = gridLayoutManager.getItemCount();
                int firstVisible = gridLayoutManager.findFirstVisibleItemPosition();
                if (visible + firstVisible >= total - 8) {
                    appendNextPage();
                }
            }
        });

        swipeRefresh = findViewById(R.id.swipeRefresh);
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent_primary));
            swipeRefresh.setOnRefreshListener(() -> forceRefreshChannels());
        }

        View btnRetry = findViewById(R.id.btnRetry);
        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> loadChannels());
        }
    }

    private void setupSearch() {
        Runnable searchRunnable = () -> {
            searchQuery = editSearch.getText().toString().trim().toLowerCase(Locale.US);
            applyFilters(true);
        };

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 250);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
    }

    /**
     * The launcher screen was the last one still carrying a hand-written copy
     * of the navigation bar — 120 lines of layout and the three click handlers
     * that used to live here, with its own ids ({@code fabSettings} for what is
     * a nav item, not a FAB) and its own idea of which colours mark the active
     * tab. It now includes the shared bar like every other screen.
     */
    private void setupBottomNavigation() {
        BottomNav.bind(this, BottomNav.Tab.LIVE);
    }

    private void loadChannels() {
        final List<Channel> cachedChannels = LiveTvChannelCache.getFreshOrEmpty(this);
        if (!cachedChannels.isEmpty()) {
            processChannelsAsync(cachedChannels, LiveTvChannelRepository.PRIMARY_PATH);
        }

        if (!isNetworkAvailable()) {
            if (cachedChannels.isEmpty()) {
                showLoading(false);
                showEmpty(getString(R.string.live_tv_no_connection));
            }
            return;
        }

        if (cachedChannels.isEmpty()) {
            showLoading(true);
        }
        repository.loadChannelsOnce(new LiveTvChannelRepository.LoadCallback() {
            @Override
            public void onLoaded(List<Channel> channels, String sourcePath) {
                LiveTvChannelCache.save(LiveTvActivity.this, channels);
                processChannelsAsync(channels, sourcePath);
            }

            @Override
            public void onError(String message) {
                if (cachedChannels.isEmpty()) {
                    showLoading(false);
                    showEmpty(message == null || message.trim().isEmpty()
                            ? getString(R.string.error_loading)
                            : message);
                }
            }
        });
    }

    private void forceRefreshChannels() {
        if (!isNetworkAvailable()) {
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
            android.widget.Toast.makeText(this, R.string.live_tv_no_connection, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }

        repository.loadChannelsOnce(new LiveTvChannelRepository.LoadCallback() {
            @Override
            public void onLoaded(List<Channel> channels, String sourcePath) {
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
                LiveTvChannelCache.save(LiveTvActivity.this, channels);
                processChannelsAsync(channels, sourcePath);
                android.widget.Toast.makeText(LiveTvActivity.this, R.string.toast_refresh_success, android.widget.Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }
                android.widget.Toast.makeText(LiveTvActivity.this,
                        message == null || message.trim().isEmpty() ? getString(R.string.error_loading) : message,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processChannelsAsync(List<Channel> source, String sourcePath) {
        final List<Channel> snapshot = new ArrayList<>(source);
        backgroundExecutor.execute(() -> {
            final List<Channel> sorted = cleanAndSortChannels(snapshot);
            final List<String> cats = buildCategories(sorted);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allChannels.clear();
                allChannels.addAll(sorted);
                categories.clear();
                categories.addAll(cats);
                if (!categories.contains(selectedCategory)) {
                    selectedCategory = "All";
                }
                rebuildCategoryChips();
                updateSubtitle(sourcePath);
                applyFilters(true);
                showLoading(false);
            });
        });
    }

    private List<Channel> cleanAndSortChannels(List<Channel> source) {
        List<Channel> result = new ArrayList<>();
        Set<String> seenUrls = new java.util.HashSet<>();
        Set<String> seenNames = new java.util.HashSet<>();
        for (Channel channel : source) {
            if (channel == null || !channel.hasStream()) {
                continue;
            }
            channel.setCategory(normalizeCategory(channel));
            if (channel.getId().isEmpty()) {
                channel.setId(createChannelId(channel));
            }
            String urlKey = normalizeUrlKey(channel.getUrl());
            String nameKey = normalizeTextKey(channel.getName());
            if (seenUrls.contains(urlKey) || seenNames.contains(nameKey)) {
                continue;
            }
            seenUrls.add(urlKey);
            seenNames.add(nameKey);
            result.add(channel);
        }

        Collections.sort(result, (c1, c2) -> {
            int idx1 = c1.getDbIndex();
            int idx2 = c2.getDbIndex();

            // Primary: Database Index Order
            if (idx1 >= 0 && idx2 >= 0) {
                return Integer.compare(idx1, idx2);
            }
            if (idx1 >= 0) return -1;
            if (idx2 >= 0) return 1;

            // Secondary: Kurdish priority
            boolean isKurd1 = isKurdishChannel(c1);
            boolean isKurd2 = isKurdishChannel(c2);
            if (isKurd1 && !isKurd2) return -1;
            if (!isKurd1 && isKurd2) return 1;

            if (isKurd1 && isKurd2) {
                // Tertiary: Kurdish popularity rank fallback when dbIndex is not available
                int rank1 = getKurdishPopularityRank(c1);
                int rank2 = getKurdishPopularityRank(c2);
                if (rank1 != rank2) {
                    return Integer.compare(rank1, rank2);
                }
            }

            // Quaternary: Name alphabetical
            return c1.getName().compareToIgnoreCase(c2.getName());
        });

        return result;
    }


    private Channel pickBetterChannel(Channel first, Channel second) {
        if (first == null) return second;
        if (second == null) return first;

        int firstScore = qualityScore(first);
        int secondScore = qualityScore(second);
        return secondScore > firstScore ? second : first;
    }

    private int qualityScore(Channel channel) {
        int score = 0;
        if (channel.isAlive()) score += 3000;
        if (channel.hasLogo()) score += 300;
        if (safeMetric(channel.getPing()) < 10000) score += 1000 - Math.min(1000, safeMetric(channel.getPing()));
        if (safeMetric(channel.getLoadSpeed()) < 10000) score += 1500 - Math.min(1500, safeMetric(channel.getLoadSpeed()));
        if (!channel.getCountry().isEmpty() && userCountry.equalsIgnoreCase(channel.getCountry())) score += 700;
        return score;
    }

    // Famous Kurdish channel name fragments — used to boost ranking regardless of category
    private static final String[] KURDISH_NAME_HINTS = {
            "nrt", "rudaw", "kurdmax", "kurdsat", "kurdistan", "kurd ", "kurdy",
            "waar", "payam", "speda", "zagros", "korek", "channel 8", "newroz",
            "mixkurdy", "k24", "khoshk", "tishk", "kurdish"
    };

    private static final String[] KURDISH_POPULAR_ORDER = {
            "rûdaw", "rudaw",
            "kurdistan24", "k24",
            "kurdmax tv sorani", "kurdmax tv",
            "kurdmax show sorani", "kurdmax show",
            "nalia tv", "nrt",
            "nalia tv 2", "nrt 2",
            "kurdsat tv", "kurdsat",
            "kurdsat news",
            "ava tv",
            "waar tv", "waar",
            "kurdistan tv",
            "zagros tv", "zagros",
            "gelî kurdistan", "geli kurdistan",
            "kurdmax tv kurmanci",
            "kurdmax show kurmanci",
            "kurdmax music",
            "kurd channel",
            "duhok tv",
            "spêda tv", "speda",
            "payam tv", "payam",
            "kirkuk tv",
            "euro kurd",
            "halabja tv",
            "ranya tv",
            "slemani tv",
            "zaxo tv",
            "4 kurd",
            "sterk tv", "sterk"
    };

    private int getKurdishPopularityRank(Channel channel) {
        String name = channel.getName() == null ? "" : channel.getName().toLowerCase(Locale.US);
        String id = channel.getId() == null ? "" : channel.getId().toLowerCase(Locale.US);

        for (int i = 0; i < KURDISH_POPULAR_ORDER.length; i++) {
            String popular = KURDISH_POPULAR_ORDER[i];
            if (name.contains(popular) || id.contains(popular)) {
                return i;
            }
        }
        return KURDISH_POPULAR_ORDER.length;
    }

    private boolean isKurdishChannel(Channel channel) {
        String name = channel.getName() == null ? "" : channel.getName().toLowerCase(Locale.US);
        for (String hint : KURDISH_NAME_HINTS) {
            if (name.contains(hint)) return true;
        }
        return false;
    }

    private int channelRank(Channel channel) {
        int score = qualityScore(channel);
        String category = channel.getCategory();
        String categoryLower = category.toLowerCase(Locale.US);
        String channelCountry = channel.getCountry();
        String language = channel.getLanguage();
        boolean isKurdish = "كوردية".equals(category)
                || "kurdish".equals(categoryLower)
                || "ku".equalsIgnoreCase(language)
                || "kur".equalsIgnoreCase(language)
                || isKurdishChannel(channel);

        if ("IQ".equalsIgnoreCase(userCountry)) {
            // Kurdish channels get highest priority for Iraqi users
            if (isKurdish) {
                score += 3000;
            } else if ("IQ".equalsIgnoreCase(channelCountry)) {
                score += 1800;
            }
        } else if (!userCountry.isEmpty() && userCountry.equalsIgnoreCase(channelCountry)) {
            score += 1800;
        }

        if (userLanguage.equalsIgnoreCase(language)) {
            score += 400;
        }
        return score;
    }

    private List<String> buildCategories(List<Channel> channels) {
        Set<String> result = new LinkedHashSet<>();
        result.add("All");
        for (Channel channel : channels) {
            result.add(channel.getCategory());
        }
        return new ArrayList<>(result);
    }

    private void rebuildCategoryChips() {
        categoryContainer.removeAllViews();
        for (String category : categories) {
            boolean selected = category.equals(selectedCategory);

            // The single-argument constructor resolves ?attr/chipStyle from the
            // theme, which Theme.Nashat points at Widget.Nashat.Chip — so the
            // checked/unchecked colours, the 48dp touch target and the pill
            // radius all come from the design system rather than being set here.
            Chip chip = new Chip(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.space_200));
            chip.setLayoutParams(params);

            chip.setText(categoryLabel(category));
            chip.setCheckable(true);
            chip.setChecked(selected);

            chip.setOnClickListener(v -> {
                selectedCategory = category;
                rebuildCategoryChips();
                applyFilters(true);
            });
            categoryContainer.addView(chip);
        }
    }

    /**
     * Turns a raw category value from the channel feed into something a
     * Kurdish or Arabic speaker can read.
     *
     * <p>Two problems are fixed here. The chips used to render the feed's
     * value verbatim, so a fully translated interface displayed "Kurdish
     * News", "Kurdish Music", "Kurdish Kids" in English. And every value is
     * prefixed with the word "Kurdish" — inside an app whose entire catalogue
     * is Kurdish, that prefix is on every chip and distinguishes nothing,
     * while pushing the word that actually differs off the end of the chip.
     *
     * <p>Each chip also used to carry an emoji glued to the front of its
     * label. Those are gone: an emoji cannot take the chip's checked-state
     * tint, so on a filled chip it stayed full-colour and fought the fill.
     *
     * <p>Anything unrecognised falls through with only the prefix removed,
     * so a category added to the feed later still shows a sensible label
     * rather than disappearing.
     */
    private String categoryLabel(String category) {
        if (category == null) return getString(R.string.category_all);

        String c = category.toLowerCase(Locale.US).trim();
        if (c.equals("all")) return getString(R.string.category_all);
        if (c.contains("news")) return getString(R.string.category_news);
        if (c.contains("sport")) return getString(R.string.category_sports);
        if (c.contains("kid") || c.contains("cartoon")) return getString(R.string.category_kids);
        if (c.contains("music")) return getString(R.string.category_music);
        if (c.contains("religi")) return getString(R.string.category_religion);
        if (c.contains("doc")) return getString(R.string.category_documentary);
        if (c.contains("movie")) return getString(R.string.category_movies);
        if (c.contains("series") || c.contains("drama")) return getString(R.string.category_series);
        if (c.contains("entertain")) return getString(R.string.category_entertainment);

        // Unmapped: strip the redundant language prefix and show the rest.
        String stripped = category.replaceAll("(?i)^kurdish\\s+", "").trim();
        return stripped.isEmpty() ? category : stripped;
    }

    private void applyFilters(boolean resetPaging) {
        filteredChannels.clear();
        for (Channel channel : allChannels) {
            if (!matchesCategory(channel) || !matchesSearch(channel)) {
                continue;
            }
            filteredChannels.add(channel);
        }

        if (resetPaging) {
            visibleChannels.clear();
            appendNextPage();
        } else {
            adapter.submitList(new ArrayList<>(visibleChannels));
        }

        tvChannelCount.setText(getString(R.string.live_tv_count, filteredChannels.size()));
        if (filteredChannels.isEmpty()) {
            showEmpty(getString(R.string.no_channels));
        } else {
            showContent();
        }
    }

    private void appendNextPage() {
        if (visibleChannels.size() >= filteredChannels.size()) {
            return;
        }

        isLoadingPage = true;
        int end = Math.min(filteredChannels.size(), visibleChannels.size() + PAGE_SIZE);
        for (int index = visibleChannels.size(); index < end; index++) {
            visibleChannels.add(filteredChannels.get(index));
        }
        adapter.submitList(new ArrayList<>(visibleChannels));
        isLoadingPage = false;
    }

    private boolean matchesCategory(Channel channel) {
        return "All".equalsIgnoreCase(selectedCategory)
                || selectedCategory.equalsIgnoreCase(channel.getCategory());
    }

    private boolean matchesSearch(Channel channel) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        return channel.getName().toLowerCase(Locale.US).contains(searchQuery)
                || channel.getCategory().toLowerCase(Locale.US).contains(searchQuery)
                || channel.getCountry().toLowerCase(Locale.US).contains(searchQuery)
                || channel.getLanguage().toLowerCase(Locale.US).contains(searchQuery);
    }
    private String normalizeCategory(Channel channel) {
        String category = channel.getCategory();
        if (category != null && !category.trim().isEmpty()) {
            return titleCase(category);
        }

        String raw = (channel.getCategory() + " " + channel.getDescription() + " "
                + channel.getLanguage() + " " + channel.getCountry() + " " + channel.getName())
                .toLowerCase(Locale.US);

        if (raw.contains("kurd") || raw.contains("sorani") || raw.contains("kurmanji") || "ku".equalsIgnoreCase(channel.getLanguage())) {
            return "Kurdish";
        }
        if (("IQ".equalsIgnoreCase(channel.getCountry()) && "ar".equalsIgnoreCase(channel.getLanguage()))
                || raw.contains("iraq") || raw.contains("arabic")) {
            return "Iraqi Arabic";
        }
        if (raw.contains("news")) return "News";
        if (raw.contains("sport") || raw.contains("football") || raw.contains("soccer")) return "Sports";
        if (raw.contains("movie") || raw.contains("cinema") || raw.contains("entertainment") || raw.contains("series")) {
            return "Entertainment & Movies";
        }
        if (raw.contains("kids") || raw.contains("cartoon")) return "Kids";
        return channel.getCategory().trim().isEmpty() ? "International" : titleCase(channel.getCategory());
    }

    private String normalizeUrlKey(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.US);
    }

    private String normalizeTextKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.US);
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private int safeMetric(int value) {
        return value > 0 ? value : 10000;
    }

    /**
     * The subtitle used to read "Loading from Firebase live catalog" or
     * "Loading from legacy live_tv_channels feed" — the name of a backend the
     * user never chose and cannot act on, in English only, on the app's own
     * launcher screen.
     *
     * <p>Which feed answered is a diagnostic detail, so it goes to the log.
     * What the user actually gains from this line is knowing why the ordering
     * looks the way it does, which is the region the list is sorted for.
     */
    private void updateSubtitle(String sourcePath) {
        Log.d(TAG, "Channel list served from: " + sourcePath);
        tvScreenSubtitle.setText(
                getString(R.string.live_tv_country_focus, displayCountry(userCountry)));
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(View.GONE);
        if (skeletonView != null) {
            skeletonView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (loading) {
            emptyView.setVisibility(View.GONE);
            recyclerChannels.setVisibility(View.INVISIBLE);
        }
    }

    private void showEmpty(String message) {
        tvEmptyText.setText(message);
        emptyView.setVisibility(View.VISIBLE);
        recyclerChannels.setVisibility(View.GONE);
    }

    private void showContent() {
        emptyView.setVisibility(View.GONE);
        recyclerChannels.setVisibility(View.VISIBLE);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String createChannelId(Channel channel) {
        String base = channel.getName() + "-" + channel.getCountry() + "-" + channel.getCategory();
        return base.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private String displayCountry(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return getString(R.string.live_tv_global);
        }
        Locale locale = new Locale("", countryCode);
        String display = locale.getDisplayCountry();
        return display == null || display.trim().isEmpty() ? getString(R.string.live_tv_global) : display;
    }

    private String titleCase(String value) {
        String[] parts = value.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.US));
            }
        }
        return builder.length() == 0 ? value : builder.toString();
    }
}
