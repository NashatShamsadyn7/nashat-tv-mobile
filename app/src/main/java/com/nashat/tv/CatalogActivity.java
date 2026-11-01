package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.nashat.tv.ContinueWatchingManager.WatchItem;
import com.nashat.tv.catalog.CatalogFilter;
import com.nashat.tv.player.PlaybackProgress;
import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Genre;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.tmdb.TmdbModels.Title;
import com.nashat.tv.ui.BottomNav;
import com.nashat.tv.ui.ContinueWatchingAdapter;
import com.nashat.tv.ui.PosterAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The films and series catalogue, ported from the desktop client's
 * {@code pages/Movies.tsx} and {@code pages/Series.tsx} and extended.
 *
 * <p>Films and series are the same screen with different endpoints, so this is
 * one Activity with two thin subclasses.
 *
 * <h3>Three display modes, never two at once</h3>
 *
 * <ul>
 *   <li><b>Rows</b> — Continue watching, the user's own lists, then curated
 *       rows. The default.</li>
 *   <li><b>Grid</b> — search results or a filtered list, paginated.</li>
 *   <li><b>Empty</b> — nothing found, or nothing loaded.</li>
 * </ul>
 *
 * <p>They replace each other rather than stacking. Showing results above a
 * still-populated catalogue leaves no clear answer to "am I looking at my
 * search or at the app".
 */
public abstract class CatalogActivity extends BaseActivity {

    /** One curated row: a heading and the request that fills it. */
    protected static final class Row {
        final int titleRes;
        final Loader loader;

        protected Row(@StringRes int titleRes, Loader loader) {
            this.titleRes = titleRes;
            this.loader = loader;
        }
    }

    protected interface Loader {
        void load(TmdbClient.Callback<List<Title>> cb);
    }

    /**
     * How long typing must pause before a search is issued.
     *
     * <p>Firing per keystroke would send one request per letter — eight for
     * "avengers" — and they do not return in order, so what ended up on screen
     * would be whichever finished last rather than the one matching the query.
     */
    private static final long SEARCH_DEBOUNCE_MS = 350;

    /** Rows from the end of the grid at which the next page starts loading. */
    private static final int PAGE_PREFETCH_DISTANCE = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    /**
     * Monotonic token for grid requests. A result renders only if it still owns
     * the newest token, so a slow earlier request cannot land after a newer one
     * and overwrite fresher results.
     */
    private int gridGeneration = 0;

    // Views
    private EditText searchInput;
    private ImageButton searchClear;
    private ProgressBar searchSpinner, pageLoading;
    private SwipeRefreshLayout rowsRefresh;
    private View rowsScroll;
    private LinearLayout rowsContainer, historyPanel;
    private ChipGroup historyChips;
    private RecyclerView resultsGrid;
    private View emptyState;
    private TextView emptyTitle, emptyBody;
    private MaterialButton emptyAction, btnFilter;

    // Adapters
    private PosterAdapter resultsAdapter;
    private ContinueWatchingAdapter continueAdapter;
    private final RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();

    // State
    private CatalogFilter filter;
    private final List<Genre> genres = new ArrayList<>();
    private ContinueWatchingManager continueManager;
    private FavoritesMoviesManager favorites;
    private WatchlistManager watchlist;
    private SearchHistoryManager history;
    private PlaybackProgress progress;

    /** Paging state for whichever query currently fills the grid. */
    private int gridPage = 1;
    private boolean gridLoading = false;
    private boolean gridExhausted = false;
    private String gridQuery = null;   // null means the grid is showing a filter result

    // ─── Subclass contract ────────────────────────────────────

    protected abstract Kind kind();

    @StringRes
    protected abstract int screenTitleRes();

    @StringRes
    protected abstract int searchHintRes();

    protected abstract BottomNav.Tab navTab();

    protected abstract List<Row> rows();

    protected String language() {
        return LocaleManager.getTmdbLanguage(this);
    }

    // ─── Lifecycle ────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        continueManager = new ContinueWatchingManager(this);
        favorites = new FavoritesMoviesManager(this);
        watchlist = new WatchlistManager(this);
        history = new SearchHistoryManager(this);
        progress = new PlaybackProgress(this);
        filter = CatalogFilter.load(this, kind());

        bindViews();
        setUpResultsGrid();
        setUpSearch();
        setUpFilter();
        loadGenres();
        rebuild();

        BottomNav.bind(this, navTab());
    }

    /**
     * Rebuilds the personal rows on every return.
     *
     * <p>Continue watching, favourites and the watchlist can all have changed
     * while the user was on another screen — most obviously by watching
     * something — and a stale row here is the one the user will notice.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (rowsScroll.getVisibility() == View.VISIBLE) {
            refreshPersonalRows();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Back unwinds one layer of state at a time rather than leaving outright. */
    @Override
    public void onBackPressed() {
        if (!TextUtils.isEmpty(searchInput.getText())) {
            searchInput.setText("");
            return;
        }
        if (!filter.isDefault()) {
            filter.reset();
            filter.save(this, kind());
            updateFilterButton();
            rebuild();
            return;
        }
        super.onBackPressed();
    }

    private void bindViews() {
        searchInput = findViewById(R.id.searchInput);
        searchClear = findViewById(R.id.searchClear);
        searchSpinner = findViewById(R.id.searchSpinner);
        pageLoading = findViewById(R.id.pageLoading);
        rowsRefresh = findViewById(R.id.rowsRefresh);
        rowsScroll = findViewById(R.id.rowsScroll);
        rowsContainer = findViewById(R.id.rowsContainer);
        historyPanel = findViewById(R.id.historyPanel);
        historyChips = findViewById(R.id.historyChips);
        resultsGrid = findViewById(R.id.resultsGrid);
        emptyState = findViewById(R.id.emptyState);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyBody = findViewById(R.id.emptyBody);
        emptyAction = findViewById(R.id.emptyAction);
        btnFilter = findViewById(R.id.btnFilter);

        ((TextView) findViewById(R.id.screenTitle)).setText(screenTitleRes());
        searchInput.setHint(searchHintRes());

        rowsRefresh.setColorSchemeResources(R.color.accent_text);
        rowsRefresh.setOnRefreshListener(this::rebuild);

        findViewById(R.id.btnClearHistory).setOnClickListener(v -> {
            if (kind() == Kind.MOVIE) history.clearMovieHistory();
            else history.clearSeriesHistory();
            renderHistory();
        });
    }

    // ══════════════════════════════════════════════════════════
    //  Mode: rows
    // ══════════════════════════════════════════════════════════

    /** Rebuilds the whole rows view: personal rows first, then curated ones. */
    private void rebuild() {
        rowsContainer.removeAllViews();
        showRows();
        refreshPersonalRows();
        buildCuratedRows();
    }

    /**
     * Rebuilds only the rows sourced from the device.
     *
     * <p>They are inserted at the top in a fixed order and removed by tag, so
     * refreshing them cannot disturb the curated rows already loaded below.
     */
    private void refreshPersonalRows() {
        removeTagged("personal");

        int at = 0;
        View cont = buildContinueRow();
        if (cont != null) rowsContainer.addView(cont, at++);

        View fav = buildLibraryRow(R.string.row_favorites, toTitles(favorites.getMovies()));
        if (fav != null) rowsContainer.addView(fav, at++);

        View list = buildLibraryRow(R.string.row_watchlist, toTitles(watchlist.getItems()));
        if (list != null) rowsContainer.addView(list, at);
    }

    private void removeTagged(String tag) {
        for (int i = rowsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = rowsContainer.getChildAt(i);
            if (tag.equals(child.getTag())) rowsContainer.removeViewAt(i);
        }
    }

    /** Continue watching, or null when there is nothing to continue. */
    private View buildContinueRow() {
        List<WatchItem> items = filterByKind(continueManager.getItems());
        if (items.isEmpty()) return null;

        View view = LayoutInflater.from(this)
                .inflate(R.layout.view_catalog_row, rowsContainer, false);
        view.setTag("personal");
        ((TextView) view.findViewById(R.id.rowTitle)).setText(R.string.row_continue);
        view.findViewById(R.id.rowLoading).setVisibility(View.GONE);

        RecyclerView list = view.findViewById(R.id.rowList);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));

        continueAdapter = new ContinueWatchingAdapter(progress,
                new ContinueWatchingAdapter.Listener() {
                    @Override
                    public void onResume(WatchItem item) {
                        resumeWatching(item);
                    }

                    @Override
                    public void onRemove(WatchItem item) {
                        removeFromContinue(item);
                    }
                });
        continueAdapter.submit(items);
        list.setAdapter(continueAdapter);
        list.setVisibility(View.VISIBLE);
        return view;
    }

    /**
     * This screen's half of Continue watching.
     *
     * <p>The store is shared with Live TV and with the other catalogue, so
     * without this the films screen would offer to resume a series.
     */
    private List<WatchItem> filterByKind(List<WatchItem> all) {
        List<WatchItem> out = new ArrayList<>();
        boolean wantSeries = kind() == Kind.TV;
        for (WatchItem i : all) {
            if (i == null || i.tmdbId == null) continue;
            if (i.isSeries != wantSeries) continue;
            // Live TV entries share the store but are keyed by URL, not by a
            // TMDB id, so they cannot be reopened from here.
            try {
                Integer.parseInt(i.tmdbId);
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(i);
        }
        return out;
    }

    private void resumeWatching(WatchItem item) {
        Intent intent = new Intent(this, WebPlayerActivity.class);
        intent.putExtra(WebPlayerActivity.EXTRA_TMDB_ID, Integer.parseInt(item.tmdbId));
        intent.putExtra(WebPlayerActivity.EXTRA_KIND, item.isSeries ? "tv" : "movie");
        intent.putExtra(WebPlayerActivity.EXTRA_TITLE, item.name);
        intent.putExtra(WebPlayerActivity.EXTRA_SEASON, Math.max(1, item.season));
        intent.putExtra(WebPlayerActivity.EXTRA_EPISODE, Math.max(1, item.episode));
        startActivity(intent);
    }

    private void removeFromContinue(WatchItem item) {
        int position = continueAdapter == null ? -1 : indexOf(item);
        continueManager.removeItem(item.tmdbId);
        try {
            progress.clear(item.isSeries ? Kind.TV : Kind.MOVIE,
                    Integer.parseInt(item.tmdbId), item.season, item.episode);
        } catch (NumberFormatException ignored) {
        }

        if (position >= 0) continueAdapter.removeAt(position);
        // Taking the last card out leaves an empty heading behind, so the whole
        // row goes with it.
        if (continueAdapter == null || continueAdapter.isEmpty()) {
            refreshPersonalRows();
        }
    }

    private int indexOf(WatchItem item) {
        List<WatchItem> current = filterByKind(continueManager.getItems());
        for (int i = 0; i < current.size(); i++) {
            WatchItem c = current.get(i);
            if (c.tmdbId.equals(item.tmdbId) && c.season == item.season
                    && c.episode == item.episode) {
                return i;
            }
        }
        return -1;
    }

    /** A row built from saved {@link Movie} entries, or null when empty. */
    private View buildLibraryRow(@StringRes int titleRes, List<Title> titles) {
        if (titles.isEmpty()) return null;

        View view = LayoutInflater.from(this)
                .inflate(R.layout.view_catalog_row, rowsContainer, false);
        view.setTag("personal");
        ((TextView) view.findViewById(R.id.rowTitle)).setText(titleRes);
        view.findViewById(R.id.rowLoading).setVisibility(View.GONE);

        RecyclerView list = view.findViewById(R.id.rowList);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        list.setRecycledViewPool(sharedPool);

        PosterAdapter adapter = new PosterAdapter(this::openDetails);
        adapter.submit(titles);
        list.setAdapter(adapter);
        list.setVisibility(View.VISIBLE);
        return view;
    }

    /**
     * Adapts saved {@link Movie} records to the TMDB shape the poster adapter
     * renders. {@code Movie.url} carries the TMDB id — that is how this app's
     * stores have always identified a title.
     */
    private List<Title> toTitles(List<Movie> saved) {
        List<Title> out = new ArrayList<>();
        boolean wantSeries = kind() == Kind.TV;
        for (Movie m : saved) {
            if (m == null || m.getUrl() == null) continue;
            boolean isSeries = m.getCategory() != null
                    && m.getCategory().toLowerCase(Locale.US).contains("series");
            if (isSeries != wantSeries) continue;

            Title t = new Title();
            try {
                t.id = Integer.parseInt(m.getUrl());
            } catch (NumberFormatException e) {
                continue;
            }
            t.title = m.getName();
            t.voteAverage = m.getVoteAverage();
            t.mediaType = isSeries ? "tv" : "movie";
            t.releaseDate = m.getYear();
            // Already a full URL, not a TMDB path fragment, so it bypasses the
            // image-base prefix rather than being mangled by it.
            t.posterUrlOverride = m.getPoster();
            out.add(t);
        }
        return out;
    }

    // rowList scrolls horizontally and its width is match_parent, so the
    // wrap_content height lint objects to is not in the scrolling direction.
    // The check matches the call against every RecyclerView layout in the
    // module rather than the one it touches; it fails lintVitalRelease, which
    // is the only reason the suppression is here.
    @android.annotation.SuppressLint("InvalidSetHasFixedSize")
    private void buildCuratedRows() {
        // A filtered catalogue is not a catalogue any more — it is a query, so
        // it renders as a grid instead of as rows.
        if (!filter.isDefault()) {
            runFilterQuery(true);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Row row : rows()) {
            View view = inflater.inflate(R.layout.view_catalog_row, rowsContainer, false);
            ((TextView) view.findViewById(R.id.rowTitle)).setText(row.titleRes);

            RecyclerView list = view.findViewById(R.id.rowList);
            View loading = view.findViewById(R.id.rowLoading);

            list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
            list.setHasFixedSize(true);
            // One pool across every row: the cards are identical, so a view
            // scrolled off one row is reused by another instead of re-inflated.
            list.setRecycledViewPool(sharedPool);

            PosterAdapter adapter = new PosterAdapter(this::openDetails);
            list.setAdapter(adapter);
            list.setVisibility(View.GONE);
            rowsContainer.addView(view);

            row.loader.load(new TmdbClient.Callback<List<Title>>() {
                @Override
                public void onSuccess(List<Title> data) {
                    if (isFinishing() || isDestroyed()) return;
                    rowsRefresh.setRefreshing(false);
                    List<Title> clean = TmdbClient.withArtwork(data);
                    loading.setVisibility(View.GONE);
                    if (clean.isEmpty()) {
                        view.setVisibility(View.GONE);   // an empty row is noise
                        return;
                    }
                    adapter.submit(clean);
                    list.setVisibility(View.VISIBLE);
                }

                @Override
                public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    rowsRefresh.setRefreshing(false);
                    view.setVisibility(View.GONE);   // one dead row, not a dead screen
                    maybeShowTotalFailure();
                }
            });
        }
    }

    /** Only surface an error when nothing at all loaded. */
    private void maybeShowTotalFailure() {
        for (int i = 0; i < rowsContainer.getChildCount(); i++) {
            if (rowsContainer.getChildAt(i).getVisibility() == View.VISIBLE) return;
        }
        showEmpty(R.string.catalog_offline_title, R.string.catalog_offline_body, true);
    }

    // ══════════════════════════════════════════════════════════
    //  Mode: grid (search + filter), paginated
    // ══════════════════════════════════════════════════════════

    // resultsGrid is match_parent in both axes, so nothing about it is
    // wrap_content — same imprecise lint check as on buildCuratedRows().
    @android.annotation.SuppressLint("InvalidSetHasFixedSize")
    private void setUpResultsGrid() {
        int columns = getResources().getInteger(R.integer.poster_grid_columns);
        GridLayoutManager manager = new GridLayoutManager(this, columns);
        resultsGrid.setLayoutManager(manager);
        resultsGrid.setHasFixedSize(true);
        resultsAdapter = new PosterAdapter(this::openDetails);
        resultsGrid.setAdapter(resultsAdapter);

        resultsGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || gridLoading || gridExhausted) return;
                int lastVisible = manager.findLastVisibleItemPosition();
                if (lastVisible >= manager.getItemCount() - PAGE_PREFETCH_DISTANCE) {
                    loadNextPage();
                }
            }
        });
    }

    private void loadNextPage() {
        gridPage++;
        if (gridQuery != null) runSearch(gridQuery, false);
        else runFilterQuery(false);
    }

    private void runFilterQuery(boolean reset) {
        if (reset) {
            gridPage = 1;
            gridExhausted = false;
            gridQuery = null;
            resultsAdapter.submit(new ArrayList<>());
        }
        final int generation = reset ? ++gridGeneration : gridGeneration;
        gridLoading = true;
        pageLoading.setVisibility(gridPage > 1 ? View.VISIBLE : View.GONE);
        if (gridPage == 1) showGrid();

        TmdbClient.discover(this, kind(), filter.sort, filter.minRating,
                filter.yearFrom, filter.yearTo, filter.genreId, gridPage, language(),
                new TmdbClient.Callback<List<Title>>() {
                    @Override
                    public void onSuccess(List<Title> data) {
                        if (isFinishing() || isDestroyed() || generation != gridGeneration) return;
                        onPageLoaded(TmdbClient.withArtwork(data),
                                R.string.filter_no_results_title,
                                R.string.filter_no_results_body);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed() || generation != gridGeneration) return;
                        onPageFailed();
                    }
                });
    }

    private void runSearch(String rawQuery, boolean reset) {
        String query = rawQuery.trim();
        if (query.isEmpty()) {
            rebuild();
            return;
        }
        if (reset) {
            gridPage = 1;
            gridExhausted = false;
            gridQuery = query;
            resultsAdapter.submit(new ArrayList<>());
        }
        final int generation = reset ? ++gridGeneration : gridGeneration;
        gridLoading = true;
        if (gridPage == 1) searchSpinner.setVisibility(View.VISIBLE);
        else pageLoading.setVisibility(View.VISIBLE);

        TmdbClient.searchPage(this, kind(), query, gridPage, language(),
                new TmdbClient.Callback<List<Title>>() {
                    @Override
                    public void onSuccess(List<Title> data) {
                        if (isFinishing() || isDestroyed() || generation != gridGeneration) return;
                        searchSpinner.setVisibility(View.GONE);
                        // Only remember a query that actually found something —
                        // otherwise the history fills with typos.
                        if (gridPage == 1 && !data.isEmpty()) remember(query);
                        onPageLoaded(TmdbClient.withArtwork(data),
                                R.string.search_no_results_title,
                                R.string.search_no_results_body);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed() || generation != gridGeneration) return;
                        searchSpinner.setVisibility(View.GONE);
                        onPageFailed();
                    }
                });
    }

    private void onPageLoaded(List<Title> clean, @StringRes int emptyTitleRes,
                              @StringRes int emptyBodyRes) {
        gridLoading = false;
        pageLoading.setVisibility(View.GONE);

        if (clean.isEmpty()) {
            gridExhausted = true;
            if (gridPage == 1) showEmpty(emptyTitleRes, emptyBodyRes, false);
            return;
        }
        resultsAdapter.append(clean);
        showGrid();
    }

    private void onPageFailed() {
        gridLoading = false;
        pageLoading.setVisibility(View.GONE);
        // A failed *later* page is not worth an error screen — the results
        // already on screen are still good. Only page one is fatal.
        if (gridPage == 1) {
            showEmpty(R.string.search_failed_title, R.string.search_failed_body, true);
        } else {
            gridPage--;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Search & history
    // ══════════════════════════════════════════════════════════

    private void setUpSearch() {
        searchClear.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                runSearch(searchInput.getText().toString(), true);
                return true;
            }
            return false;
        });

        searchInput.setOnFocusChangeListener((v, hasFocus) -> updateHistoryVisibility());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                updateHistoryVisibility();

                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);

                if (query.isEmpty()) {
                    // Invalidate anything in flight so a late result cannot
                    // render over the catalogue the user just returned to.
                    gridGeneration++;
                    searchSpinner.setVisibility(View.GONE);
                    rebuild();
                    return;
                }
                pendingSearch = () -> runSearch(query, true);
                handler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }
        });

        renderHistory();
    }

    private void remember(String query) {
        if (kind() == Kind.MOVIE) history.addMovieSearch(query);
        else history.addSeriesSearch(query);
        renderHistory();
    }

    private List<String> historyEntries() {
        return kind() == Kind.MOVIE ? history.getMovieHistory() : history.getSeriesHistory();
    }

    private void renderHistory() {
        historyChips.removeAllViews();
        for (String query : historyEntries()) {
            Chip chip = new Chip(this);
            chip.setText(query);
            chip.setCloseIconVisible(true);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                searchInput.setText(query);
                searchInput.setSelection(query.length());
                hideKeyboard();
                searchInput.clearFocus();
                runSearch(query, true);
            });
            chip.setOnCloseIconClickListener(v -> {
                if (kind() == Kind.MOVIE) history.removeMovieSearch(query);
                else history.removeSeriesSearch(query);
                renderHistory();
            });
            historyChips.addView(chip);
        }
        updateHistoryVisibility();
    }

    /** Recent searches belong on screen only while the field is focused and empty. */
    private void updateHistoryVisibility() {
        boolean show = searchInput.hasFocus()
                && TextUtils.isEmpty(searchInput.getText())
                && historyChips.getChildCount() > 0;
        historyPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ══════════════════════════════════════════════════════════
    //  Filter
    // ══════════════════════════════════════════════════════════

    private void setUpFilter() {
        btnFilter.setOnClickListener(v -> showFilterSheet());
        updateFilterButton();
    }

    private void updateFilterButton() {
        int active = filter.activeCount();
        btnFilter.setText(active == 0
                ? getString(R.string.filter_title)
                : getString(R.string.filter_title_count, active));
    }

    private void loadGenres() {
        TmdbClient.genres(this, kind(), language(), new TmdbClient.Callback<List<Genre>>() {
            @Override
            public void onSuccess(List<Genre> data) {
                genres.clear();
                genres.addAll(data);
            }

            @Override
            public void onError(String message) {
                // The sheet simply shows no genre chips; every other filter works.
            }
        });
    }

    private void showFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_catalog_filter, null);

        // Edited on a copy, so dismissing without applying changes nothing.
        final CatalogFilter draft = filter.copy();

        ChipGroup sortGroup = sheet.findViewById(R.id.sortGroup);
        buildSortChips(sortGroup, draft);

        Slider rating = sheet.findViewById(R.id.ratingSlider);
        TextView ratingValue = sheet.findViewById(R.id.ratingValue);
        rating.setValue(draft.minRating);
        ratingValue.setText(formatRating(draft.minRating));
        rating.addOnChangeListener((s, value, fromUser) -> {
            draft.minRating = value;
            ratingValue.setText(formatRating(value));
        });

        RangeSlider years = sheet.findViewById(R.id.yearSlider);
        TextView yearValue = sheet.findViewById(R.id.yearValue);
        int maxYear = CatalogFilter.maxYear();
        years.setValueFrom(CatalogFilter.MIN_YEAR);
        years.setValueTo(maxYear);
        float from = draft.yearFrom > 0 ? draft.yearFrom : CatalogFilter.MIN_YEAR;
        float to = draft.yearTo > 0 ? draft.yearTo : maxYear;
        years.setValues(from, to);
        yearValue.setText(formatYears((int) from, (int) to));
        years.addOnChangeListener((s, value, fromUser) -> {
            List<Float> v = s.getValues();
            int lo = Math.round(v.get(0));
            int hi = Math.round(v.get(1));
            // The full span means "no year filter", not "1950 to next year" —
            // otherwise every query would carry a date range it did not need.
            draft.yearFrom = (lo <= CatalogFilter.MIN_YEAR) ? 0 : lo;
            draft.yearTo = (hi >= maxYear) ? 0 : hi;
            yearValue.setText(formatYears(lo, hi));
        });

        ChipGroup genreGroup = sheet.findViewById(R.id.genreGroup);
        buildGenreChips(genreGroup, draft);

        sheet.findViewById(R.id.btnResetFilter).setOnClickListener(v -> {
            draft.reset();
            rating.setValue(0f);
            years.setValues((float) CatalogFilter.MIN_YEAR, (float) maxYear);
            genreGroup.clearCheck();
            buildSortChips(sortGroup, draft);
        });

        sheet.findViewById(R.id.btnApplyFilter).setOnClickListener(v -> {
            filter = draft;
            filter.save(this, kind());
            updateFilterButton();
            dialog.dismiss();
            searchInput.setText("");
            rebuild();
        });

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void buildSortChips(ChipGroup group, CatalogFilter draft) {
        group.removeAllViews();
        addSortChip(group, draft, TmdbClient.Sort.POPULAR, R.string.sort_popular);
        addSortChip(group, draft, TmdbClient.Sort.RATING, R.string.sort_rating);
        addSortChip(group, draft, TmdbClient.Sort.NEWEST, R.string.sort_newest);
        if (kind() == Kind.MOVIE) {
            addSortChip(group, draft, TmdbClient.Sort.REVENUE, R.string.sort_revenue);
        }
        addSortChip(group, draft, TmdbClient.Sort.TITLE, R.string.sort_title);
    }

    private void addSortChip(ChipGroup group, CatalogFilter draft,
                             TmdbClient.Sort sort, @StringRes int labelRes) {
        Chip chip = new Chip(this);
        chip.setText(labelRes);
        chip.setCheckable(true);
        chip.setChecked(draft.sort == sort);
        chip.setOnClickListener(v -> draft.sort = sort);
        group.addView(chip);
    }

    private void buildGenreChips(ChipGroup group, CatalogFilter draft) {
        group.removeAllViews();
        for (Genre g : genres) {
            if (g == null || g.name == null) continue;
            Chip chip = new Chip(this);
            chip.setText(g.name);
            chip.setCheckable(true);
            chip.setChecked(draft.genreId == g.id);
            chip.setOnCheckedChangeListener((v, checked) -> {
                draft.genreId = checked ? g.id : 0;
                draft.genreName = checked ? g.name : "";
            });
            group.addView(chip);
        }
    }

    private String formatRating(float value) {
        return value <= 0f
                ? getString(R.string.filter_any)
                : getString(R.string.filter_rating_value, value);
    }

    private String formatYears(int from, int to) {
        if (from <= CatalogFilter.MIN_YEAR && to >= CatalogFilter.maxYear()) {
            return getString(R.string.filter_any);
        }
        return getString(R.string.filter_year_range, from, to);
    }

    // ══════════════════════════════════════════════════════════
    //  View states
    // ══════════════════════════════════════════════════════════

    private void showRows() {
        rowsRefresh.setVisibility(View.VISIBLE);
        resultsGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        pageLoading.setVisibility(View.GONE);
    }

    private void showGrid() {
        rowsRefresh.setVisibility(View.GONE);
        resultsGrid.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmpty(@StringRes int title, @StringRes int body, boolean retryable) {
        rowsRefresh.setVisibility(View.GONE);
        resultsGrid.setVisibility(View.GONE);
        pageLoading.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(title);
        emptyBody.setText(body);
        emptyAction.setVisibility(retryable ? View.VISIBLE : View.GONE);
        emptyAction.setOnClickListener(retryable ? v -> retry() : null);
    }

    private void retry() {
        String query = searchInput.getText().toString().trim();
        if (!query.isEmpty()) runSearch(query, true);
        else rebuild();
    }

    // ══════════════════════════════════════════════════════════
    //  Navigation
    // ══════════════════════════════════════════════════════════

    private void openDetails(@NonNull Title title) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_TMDB_ID, title.id);
        // This screen's kind is authoritative: a film row returns entries with
        // no media_type, and Title.kind() would have to guess from shape.
        intent.putExtra(DetailsActivity.EXTRA_KIND, kind().path);
        startActivity(intent);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && searchInput != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }
}
