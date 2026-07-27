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

import com.google.android.material.button.MaterialButton;
import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.tmdb.TmdbModels.Title;
import com.nashat.tv.ui.BottomNav;
import com.nashat.tv.ui.PosterAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * The films and series catalogue, ported from the desktop client's
 * {@code pages/Movies.tsx} and {@code pages/Series.tsx}.
 *
 * <p>Those two pages are the same screen with different endpoints, so this is
 * one Activity with two thin subclasses. The previous Android build had two
 * 900-line Activities that had drifted apart in layout, in loading behaviour
 * and in what they did when a request failed.
 *
 * <p>Search replaces the rows rather than appending to them, matching the
 * desktop behaviour: showing results above a still-populated catalogue leaves
 * no clear answer to "am I looking at my search or at the app".
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
     * "avengers" — and they do not come back in order, so the results shown
     * would be whichever request happened to finish last rather than the one
     * matching what is on screen.
     */
    private static final long SEARCH_DEBOUNCE_MS = 350;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    /**
     * Monotonic token for search requests. A result is only rendered if it
     * still owns the newest token, so a slow early request cannot land after a
     * newer one and overwrite fresher results.
     */
    private int searchGeneration = 0;

    private EditText searchInput;
    private ImageButton searchClear;
    private ProgressBar searchSpinner;
    private View rowsScroll;
    private LinearLayout rowsContainer;
    private RecyclerView resultsGrid;
    private View emptyState;
    private TextView emptyTitle, emptyBody;
    private MaterialButton emptyAction;

    private PosterAdapter resultsAdapter;
    private final List<PosterAdapter> rowAdapters = new ArrayList<>();

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

        searchInput = findViewById(R.id.searchInput);
        searchClear = findViewById(R.id.searchClear);
        searchSpinner = findViewById(R.id.searchSpinner);
        rowsScroll = findViewById(R.id.rowsScroll);
        rowsContainer = findViewById(R.id.rowsContainer);
        resultsGrid = findViewById(R.id.resultsGrid);
        emptyState = findViewById(R.id.emptyState);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyBody = findViewById(R.id.emptyBody);
        emptyAction = findViewById(R.id.emptyAction);

        ((TextView) findViewById(R.id.screenTitle)).setText(screenTitleRes());
        searchInput.setHint(searchHintRes());

        setUpResultsGrid();
        setUpSearch();
        buildRows();

        BottomNav.bind(this, navTab());
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * Back leaves search rather than leaving the screen.
     *
     * <p>Without this, a user who searched, looked, and pressed Back would be
     * thrown out of the catalogue entirely — and would have to navigate back in
     * and re-find where they were.
     */
    @Override
    public void onBackPressed() {
        if (!TextUtils.isEmpty(searchInput.getText())) {
            searchInput.setText("");
            return;
        }
        super.onBackPressed();
    }

    // ─── Curated rows ─────────────────────────────────────────

    private void buildRows() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Row row : rows()) {
            View view = inflater.inflate(R.layout.view_catalog_row, rowsContainer, false);
            ((TextView) view.findViewById(R.id.rowTitle)).setText(row.titleRes);

            RecyclerView list = view.findViewById(R.id.rowList);
            View loading = view.findViewById(R.id.rowLoading);

            list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
            // Every row is the same size regardless of its contents, which lets
            // RecyclerView skip a re-measure of the whole row on each bind.
            list.setHasFixedSize(true);
            // One shared pool across all rows: the posters are identical, so a
            // view scrolled off one row can be reused by another instead of
            // being inflated again.
            list.setRecycledViewPool(sharedPool);

            PosterAdapter adapter = new PosterAdapter(this::openDetails);
            list.setAdapter(adapter);
            rowAdapters.add(adapter);

            list.setVisibility(View.GONE);
            rowsContainer.addView(view);

            row.loader.load(new TmdbClient.Callback<List<Title>>() {
                @Override
                public void onSuccess(List<Title> data) {
                    if (isFinishing() || isDestroyed()) return;
                    List<Title> clean = TmdbClient.withArtwork(data);
                    loading.setVisibility(View.GONE);
                    if (clean.isEmpty()) {
                        // An empty row is noise. Drop the heading with it.
                        view.setVisibility(View.GONE);
                        return;
                    }
                    adapter.submit(clean);
                    list.setVisibility(View.VISIBLE);
                }

                @Override
                public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    // One failed row must not take the screen down with it.
                    view.setVisibility(View.GONE);
                    maybeShowTotalFailure();
                }
            });
        }
    }

    private final RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();

    /** Only surface an error when nothing at all loaded. */
    private void maybeShowTotalFailure() {
        for (int i = 0; i < rowsContainer.getChildCount(); i++) {
            if (rowsContainer.getChildAt(i).getVisibility() == View.VISIBLE) return;
        }
        for (PosterAdapter a : rowAdapters) {
            if (!a.isEmpty()) return;
        }
        showEmpty(R.string.catalog_offline_title, R.string.catalog_offline_body, true);
    }

    // ─── Search ───────────────────────────────────────────────

    private void setUpResultsGrid() {
        int columns = getResources().getInteger(R.integer.poster_grid_columns);
        resultsGrid.setLayoutManager(new GridLayoutManager(this, columns));
        resultsGrid.setHasFixedSize(true);
        resultsAdapter = new PosterAdapter(this::openDetails);
        resultsGrid.setAdapter(resultsAdapter);
    }

    private void setUpSearch() {
        searchClear.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                runSearch(searchInput.getText().toString());
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);

                if (query.isEmpty()) {
                    // Invalidate anything in flight so a late result cannot
                    // render over the catalogue the user just returned to.
                    searchGeneration++;
                    searchSpinner.setVisibility(View.GONE);
                    showRows();
                    return;
                }

                pendingSearch = () -> runSearch(query);
                handler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }
        });
    }

    private void runSearch(String rawQuery) {
        String query = rawQuery.trim();
        if (query.isEmpty()) {
            showRows();
            return;
        }

        final int generation = ++searchGeneration;
        searchSpinner.setVisibility(View.VISIBLE);

        TmdbClient.search(this, kind(), query, language(),
                new TmdbClient.Callback<List<Title>>() {
                    @Override
                    public void onSuccess(List<Title> data) {
                        if (isFinishing() || isDestroyed()) return;
                        if (generation != searchGeneration) return;
                        searchSpinner.setVisibility(View.GONE);

                        List<Title> clean = TmdbClient.withArtwork(data);
                        if (clean.isEmpty()) {
                            resultsAdapter.submit(clean);
                            showEmpty(R.string.search_no_results_title,
                                    R.string.search_no_results_body, false);
                            return;
                        }
                        resultsAdapter.submit(clean);
                        showResults();
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        if (generation != searchGeneration) return;
                        searchSpinner.setVisibility(View.GONE);
                        showEmpty(R.string.search_failed_title, R.string.search_failed_body, true);
                    }
                });
    }

    // ─── View states ──────────────────────────────────────────

    private void showRows() {
        rowsScroll.setVisibility(View.VISIBLE);
        resultsGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showResults() {
        rowsScroll.setVisibility(View.GONE);
        resultsGrid.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmpty(@StringRes int title, @StringRes int body, boolean retryable) {
        rowsScroll.setVisibility(View.GONE);
        resultsGrid.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(title);
        emptyBody.setText(body);
        emptyAction.setVisibility(retryable ? View.VISIBLE : View.GONE);
        emptyAction.setOnClickListener(retryable ? v -> retry() : null);
    }

    private void retry() {
        String query = searchInput.getText().toString().trim();
        if (!query.isEmpty()) {
            runSearch(query);
            return;
        }
        rowsContainer.removeAllViews();
        rowAdapters.clear();
        showRows();
        buildRows();
    }

    // ─── Navigation ───────────────────────────────────────────

    private void openDetails(@NonNull Title title) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_TMDB_ID, title.id);
        // The row's own kind is authoritative: a film row returns entries with
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
