package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.nashat.tv.util.AppConstants;
import com.nashat.tv.util.HttpClient;

public class MoviesActivity extends BaseActivity {

    private RecyclerView recyclerMovies;
    private ProgressBar progressBar;
    private List<Movie> movieList = new ArrayList<>();
    private List<Movie> filteredMovieList = new ArrayList<>();
    private MoviesAdapter adapter;
    private int currentPage = 1;
    private boolean isLoading = false;
    private String currentQuery = "";
    private String currentGenreId = "";

    private static final String TMDB_API_KEY = AppConstants.TMDB_API_KEY;

    private FavoritesMoviesManager favManager;
    private boolean showFavoritesOnly = false;

    // Advanced Filters
    private float minRatingFilter = 0.0f;
    private float minYearFilter = 1990f;
    private float maxYearFilter = 2026f;

    // Search History
    private SearchHistoryManager searchHistoryManager;
    private boolean isSearchHistoryVisible = false;
    private RecyclerView recyclerSearchHistory;
    private SearchHistoryAdapter searchHistoryAdapter;

    // Continue Watching
    private ContinueWatchingManager continueWatchingManager;
    private RecyclerView recyclerContinueWatching;

    // Hero banner
    private View heroBannerContainer;
    private ImageView ivHeroBackdrop;
    private TextView tvHeroTitle;
    private TextView tvHeroMeta;
    private LinearLayout heroIndicators;
    private final Handler heroHandler = new Handler(Looper.getMainLooper());
    private final List<Movie> heroMovies = new ArrayList<>();
    private int heroIndex = 0;
    private final Runnable heroRotator = new Runnable() {
        @Override
        public void run() {
            if (heroMovies.isEmpty()) return;
            heroIndex = (heroIndex + 1) % heroMovies.size();
            bindHeroAt(heroIndex);
            heroHandler.postDelayed(this, 5000);
        }
    };

    // Home Categories
    private View layoutHomeCategories;
    private RecyclerView recyclerNowPlaying, recyclerTrending, recyclerTopRated, recyclerPopular, recyclerAnime;
    private List<Movie> listNowPlaying = new ArrayList<>(), listTrending = new ArrayList<>(),
            listTopRated = new ArrayList<>(), listPopular = new ArrayList<>(), listAnime = new ArrayList<>();
    private HorizontalMovieAdapter adapterNowPlaying, adapterTrending, adapterTopRated, adapterPopular, adapterAnime;
    private boolean homeFetched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movies);
        
        favManager = new FavoritesMoviesManager(this);
        searchHistoryManager = new SearchHistoryManager(this);
        continueWatchingManager = new ContinueWatchingManager(this);

        progressBar = findViewById(R.id.progressBar);
        recyclerMovies = findViewById(R.id.recyclerMovies);
        layoutHomeCategories = findViewById(R.id.layoutHomeCategories);
        recyclerNowPlaying = findViewById(R.id.recyclerNowPlaying);
        recyclerTrending = findViewById(R.id.recyclerTrending);
        recyclerTopRated = findViewById(R.id.recyclerTopRated);
        recyclerPopular = findViewById(R.id.recyclerPopular);
        recyclerAnime = findViewById(R.id.recyclerAnime);

        heroBannerContainer = findViewById(R.id.heroBannerContainer);
        ivHeroBackdrop = findViewById(R.id.ivHeroBackdrop);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroMeta = findViewById(R.id.tvHeroMeta);
        heroIndicators = findViewById(R.id.heroIndicators);
        if (heroBannerContainer != null) {
            heroBannerContainer.setOnClickListener(v -> {
                if (!heroMovies.isEmpty() && heroIndex < heroMovies.size()) {
                    openMovieDetails(heroMovies.get(heroIndex));
                }
            });
        }

        // Setup horizontal adapters
        HorizontalMovieAdapter.OnMovieClickListener homeClickListener = this::openMovieDetails;
        
        adapterNowPlaying = new HorizontalMovieAdapter(listNowPlaying, homeClickListener);
        recyclerNowPlaying.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerNowPlaying.setAdapter(adapterNowPlaying);

        adapterTrending = new HorizontalMovieAdapter(listTrending, homeClickListener);
        recyclerTrending.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerTrending.setAdapter(adapterTrending);

        adapterTopRated = new HorizontalMovieAdapter(listTopRated, homeClickListener);
        recyclerTopRated.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerTopRated.setAdapter(adapterTopRated);

        adapterPopular = new HorizontalMovieAdapter(listPopular, homeClickListener);
        recyclerPopular.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerPopular.setAdapter(adapterPopular);

        adapterAnime = new HorizontalMovieAdapter(listAnime, homeClickListener);
        recyclerAnime.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerAnime.setAdapter(adapterAnime);

        // 5 columns layout for grid
        GridLayoutManager layoutManager = new GridLayoutManager(this, 5);
        recyclerMovies.setLayoutManager(layoutManager);

        android.widget.EditText editSearch = findViewById(R.id.editSearch);
        android.content.SharedPreferences searchPrefs = getSharedPreferences("SearchState", MODE_PRIVATE);
        currentQuery = searchPrefs.getString("movies_query", "");
        editSearch.setText(currentQuery);
        // Put cursor at the end
        editSearch.setSelection(currentQuery.length());
        
        // Debounced search logic (Search as you type)
        android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable searchRunnable = () -> {
            String newQuery = editSearch.getText().toString().trim();
            if (!newQuery.equals(currentQuery)) {
                currentQuery = newQuery;
                searchPrefs.edit().putString("movies_query", currentQuery).apply();
                
                // Save to search history when user types a meaningful query
                if (!currentQuery.isEmpty() && currentQuery.length() >= 2) {
                    searchHistoryManager.addMovieSearch(currentQuery);
                }
                
                currentPage = 1;
                movieList.clear();
                filteredMovieList.clear();
                adapter.notifyDataSetChanged();
                
                // If user typed something, disable genre selection to prioritize search
                if (!currentQuery.isEmpty()) {
                    currentGenreId = "";
                }
                
                checkViewMode();
                
                // Hide search history and continue watching when typing
                hideSearchHistory();
                if (headerAdapter != null) headerAdapter.notifyDataSetChanged();
                
                if (recyclerMovies.getVisibility() == View.VISIBLE) {
                    fetchTMDBMovies(currentPage);
                }
            }
        };

        editSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Remove previous pending search requests
                searchHandler.removeCallbacks(searchRunnable);
                // Wait 600ms after user stops typing before making the API call
                searchHandler.postDelayed(searchRunnable, 600);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Still allow direct search action from keyboard (to hide it)
        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                
                // Save search when user explicitly presses search
                String q = editSearch.getText().toString().trim();
                if (!q.isEmpty()) {
                    searchHistoryManager.addMovieSearch(q);
                }
                hideSearchHistory();
                return true;
            }
            return false;
        });

        // Search icon - shows search history when pressed
        ImageView btnSearchIcon = findViewById(R.id.btnSearchIcon);
        btnSearchIcon.setOnClickListener(v -> {
            if (isSearchHistoryVisible) {
                hideSearchHistory();
            } else {
                showSearchHistory();
            }
        });

        ImageView btnFavToggle = findViewById(R.id.btnFavToggle);
        if (btnFavToggle != null) {
            btnFavToggle.setOnClickListener(v -> {
                showFavoritesOnly = !showFavoritesOnly;
                if (showFavoritesOnly) {
                    btnFavToggle.setColorFilter(android.graphics.Color.RED);
                    movieList.clear();
                    movieList.addAll(favManager.getMovies());
                    applyRatingFilter();
                } else {
                    btnFavToggle.setColorFilter(null); // Default tint
                    currentPage = 1;
                    movieList.clear();
                    filteredMovieList.clear();
                    checkViewMode();
                    if (recyclerMovies.getVisibility() == View.VISIBLE) {
                        fetchTMDBMovies(currentPage);
                    }
                }
                checkViewMode();
            });
        }

        ImageView btnFilterIcon = findViewById(R.id.btnFilterIcon);
        if (btnFilterIcon != null) {
            btnFilterIcon.setOnClickListener(v -> showAdvancedFilterDialog());
        }

        adapter = new MoviesAdapter(filteredMovieList, this::openMovieDetails);
        
        headerAdapter = new HeaderAdapter();
        headerAdapter.setItems(continueWatchingManager.getItems());

        ConcatAdapter sortedAdapter = new ConcatAdapter(headerAdapter, adapter);
        recyclerMovies.setAdapter(sortedAdapter);

        // Header takes full 5 columns, normal movie takes 1
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0 && headerAdapter.getItemCount() > 0) {
                    return 5;
                }
                return 1;
            }
        });

        // Header takes full 5 columns, normal movie takes 1
        recyclerMovies.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && !isLoading) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        currentPage++;
                        fetchTMDBMovies(currentPage);
                    }
                }
            }
        });

        // Setup Search History RecyclerView
        setupSearchHistory();

        // ═══ Wire Bottom Navigation ═══
        View navLiveTv = findViewById(R.id.navLiveTv);
        if (navLiveTv != null) {
            navLiveTv.setOnClickListener(v -> navigateToTab(LiveTvActivity.class));
        }
        View navSeries = findViewById(R.id.navSeries);
        if (navSeries != null) {
            navSeries.setOnClickListener(v -> navigateToTab(SeriesActivity.class));
        }
        View navSettings = findViewById(R.id.fabSettings);
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> navigateToTab(SettingsActivity.class));
        }

        // Load initial data
        checkViewMode();
        if (recyclerMovies.getVisibility() == View.VISIBLE) {
            fetchTMDBMovies(currentPage);
        }
    }

    private void checkViewMode() {
        boolean isDefault = currentQuery.isEmpty() && currentGenreId.isEmpty() && minRatingFilter == 0.0f && minYearFilter <= 1990f && maxYearFilter >= 2026f && !showFavoritesOnly;
        if (isDefault) {
            layoutHomeCategories.setVisibility(View.VISIBLE);
            recyclerMovies.setVisibility(View.GONE);
            if (!homeFetched) {
                fetchHomeCategories();
            }
        } else {
            layoutHomeCategories.setVisibility(View.GONE);
            recyclerMovies.setVisibility(View.VISIBLE);
        }
    }

    private void fetchCategoryList(String endpoint, List<Movie> targetList, HorizontalMovieAdapter adapter) {
        HttpClient.getJson(this, endpoint, new HttpClient.Callback2() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray results = new JSONObject(body).getJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject obj = results.getJSONObject(i);
                        String id = String.valueOf(obj.getInt("id"));
                        String title = obj.optString("title", obj.optString("name", ""));
                        String releaseDate = obj.optString("release_date", "");
                        String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
                        String posterPath = obj.optString("poster_path", "");
                        String posterFull = AppConstants.TMDB_IMAGE_BASE + posterPath;
                        double voteAvg = obj.optDouble("vote_average", 0.0);
                        targetList.add(new Movie(title, posterFull, "⭐️ " + voteAvg, id,
                                obj.optString("overview", ""), year, voteAvg));
                    }
                    adapter.notifyDataSetChanged();
                } catch (Exception ignored) {}
            }
            @Override public void onError(String message) { /* silent */ }
        });
    }

    private void fetchHomeCategories() {
        homeFetched = true;
        String lang = "&language=ar";
        fetchCategoryList("https://api.themoviedb.org/3/movie/now_playing?api_key=" + TMDB_API_KEY + lang, listNowPlaying, adapterNowPlaying);
        fetchCategoryList("https://api.themoviedb.org/3/trending/movie/week?api_key=" + TMDB_API_KEY + lang, listTrending, adapterTrending);
        fetchCategoryList("https://api.themoviedb.org/3/movie/top_rated?api_key=" + TMDB_API_KEY + lang, listTopRated, adapterTopRated);
        // Matches the desktop client, which shows trending / popular / top rated.
        fetchCategoryList("https://api.themoviedb.org/3/movie/popular?api_key=" + TMDB_API_KEY + lang, listPopular, adapterPopular);
        fetchCategoryList("https://api.themoviedb.org/3/discover/movie?api_key=" + TMDB_API_KEY + "&with_genres=16" + lang, listAnime, adapterAnime);

        // Posted on heroHandler, NOT on a fresh anonymous Handler.
        //
        // This line used to read `new Handler(Looper.getMainLooper())`, which
        // created an untracked Handler that onDestroy() had no reference to and
        // therefore could not cancel. Leaving this screen within 800ms of the
        // category fetch completing left the callback queued against a dead
        // Activity; it then reached Glide.with(this) and took the process down
        // with "You cannot start a load for a destroyed activity".
        //
        // heroHandler is cleared in both onPause() and onDestroy(), so routing
        // the callback through it makes the timer die with the screen.
        heroHandler.postDelayed(this::populateHero, 800);
    }

    private void populateHero() {
        if (heroBannerContainer == null || isFinishing() || isDestroyed()) return;
        heroMovies.clear();
        int limit = Math.min(5, listTrending.size());
        for (int i = 0; i < limit; i++) heroMovies.add(listTrending.get(i));
        if (heroMovies.isEmpty()) {
            heroBannerContainer.setVisibility(View.GONE);
            return;
        }
        heroBannerContainer.setVisibility(View.VISIBLE);
        buildHeroIndicators();
        heroIndex = 0;
        bindHeroAt(0);
        heroHandler.removeCallbacks(heroRotator);
        heroHandler.postDelayed(heroRotator, 5000);
    }

    private void buildHeroIndicators() {
        if (heroIndicators == null) return;
        heroIndicators.removeAllViews();
        int dp = (int) (getResources().getDisplayMetrics().density);
        for (int i = 0; i < heroMovies.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(7 * dp, 7 * dp);
            lp.setMarginEnd(5 * dp);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_hero_dot);
            dot.setAlpha(i == heroIndex ? 1f : 0.4f);
            heroIndicators.addView(dot);
        }
    }

    private void bindHeroAt(int idx) {
        if (idx < 0 || idx >= heroMovies.size() || ivHeroBackdrop == null) return;
        // Defence in depth behind the heroHandler fix: Glide throws rather than
        // no-opping when handed a dead Activity, and this method is reachable
        // from a timer, so it verifies the host is still alive before loading.
        if (isFinishing() || isDestroyed()) return;

        Movie m = heroMovies.get(idx);
        tvHeroTitle.setText(m.getName() != null ? m.getName() : "");
        StringBuilder meta = new StringBuilder();
        if (m.getYear() != null && !m.getYear().isEmpty()) meta.append(m.getYear());
        if (m.getVoteAverage() > 0) {
            if (meta.length() > 0) meta.append("  ·  ");
            // The rating is rendered with a star ICON beside this text view, so
            // the emoji star that used to be concatenated here is redundant —
            // and it printed at the font's own colour, ignoring the theme.
            meta.append(String.format(java.util.Locale.US, "%.1f", m.getVoteAverage()));
        }
        tvHeroMeta.setText(meta.toString());
        Glide.with(this)
                .load(m.getPoster())
                .centerCrop()
                .into(ivHeroBackdrop);
        if (heroIndicators != null) {
            for (int i = 0; i < heroIndicators.getChildCount(); i++) {
                heroIndicators.getChildAt(i).setAlpha(i == idx ? 1f : 0.4f);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        heroHandler.removeCallbacks(heroRotator);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        heroHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh continue watching when returning from player
        if (headerAdapter != null) {
            headerAdapter.setItems(continueWatchingManager.getItems());
        }
    }

    private void setupSearchHistory() {
        recyclerSearchHistory = findViewById(R.id.recyclerSearchHistory);
        recyclerSearchHistory.setLayoutManager(new LinearLayoutManager(this));

        TextView btnClearHistory = findViewById(R.id.btnClearHistory);
        btnClearHistory.setOnClickListener(v -> {
            searchHistoryManager.clearMovieHistory();
            hideSearchHistory();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void showSearchHistory() {
        List<String> history = searchHistoryManager.getMovieHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "No search history yet", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.widget.EditText editSearch = findViewById(R.id.editSearch);
        
        searchHistoryAdapter = new SearchHistoryAdapter(history, new SearchHistoryAdapter.OnHistoryClickListener() {
            @Override
            public void onItemClick(String query) {
                editSearch.setText(query);
                editSearch.setSelection(query.length());
                hideSearchHistory();
            }

            @Override
            public void onRemoveClick(String query, int position) {
                // Remove from stored history
                List<String> updated = searchHistoryManager.getMovieHistory();
                updated.remove(query);
                // Reconstruct - clear and re-add
                searchHistoryManager.clearMovieHistory();
                for (int i = updated.size() - 1; i >= 0; i--) {
                    searchHistoryManager.addMovieSearch(updated.get(i));
                }
                searchHistoryAdapter.removeItem(position);
                if (searchHistoryAdapter.getItemCount() == 0) {
                    hideSearchHistory();
                }
            }
        });
        recyclerSearchHistory.setAdapter(searchHistoryAdapter);

        findViewById(R.id.searchHistoryPanel).setVisibility(View.VISIBLE);
        recyclerMovies.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        isSearchHistoryVisible = true;
    }

    private void hideSearchHistory() {
        findViewById(R.id.searchHistoryPanel).setVisibility(View.GONE);
        recyclerMovies.setVisibility(View.VISIBLE);
        isSearchHistoryVisible = false;
        if (headerAdapter != null) {
            headerAdapter.notifyDataSetChanged();
        }
    }

    private HeaderAdapter headerAdapter;

    class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder> {
        private List<ContinueWatchingManager.WatchItem> items = new ArrayList<>();
        private ContinueWatchingAdapter horizontalAdapter;

        public void setItems(List<ContinueWatchingManager.WatchItem> newItems) {
            this.items = newItems;
            if (horizontalAdapter != null) {
                horizontalAdapter.notifyDataSetChanged();
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_continue_watching_header, parent, false);
            return new HeaderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
            holder.recycler.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            horizontalAdapter = new ContinueWatchingAdapter(items, item -> {
                Movie movie = item.toMovie();
                if (item.isSeries) {
                    Intent intent = new Intent(MoviesActivity.this, SeriesDetailsActivity.class);
                    intent.putExtra("series", movie);
                    startActivity(intent);
                } else {
                    openMovieDetails(movie);
                }
            });
            holder.recycler.setAdapter(horizontalAdapter);
        }

        @Override
        public int getItemCount() {
            // Hide header if no items, or if user is searching/filtering
            if (items.isEmpty() || !currentQuery.isEmpty() || !currentGenreId.isEmpty() || showFavoritesOnly || minRatingFilter > 0 || minYearFilter > 1990f || maxYearFilter < 2026f) {
                return 0;
            }
            return 1;
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            RecyclerView recycler;
            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                recycler = itemView.findViewById(R.id.recyclerContinueWatching);
            }
        }
    }

    private void showAdvancedFilterDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_advanced_filter, null);
        dialog.setContentView(view);
        
        dialog.findViewById(R.id.btnCloseFilter).setOnClickListener(v -> dialog.dismiss());
        
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupGenres);
        String[] genres = {"All", "Action|28", "Horror|27", "Comedy|35", "Anime|16", "Sci-Fi|878", "Drama|18"};
        
        for (String g : genres) {
            String[] parts = g.split("\\|");
            String name = parts[0];
            String id = parts.length > 1 ? parts[1] : "";
            
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setTag(id);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.chip_bg);
            chip.setTextColor(getResources().getColor(R.color.text_primary, null));
            if (currentGenreId.equals(id)) {
                chip.setChecked(true);
                chip.setChipBackgroundColorResource(R.color.accent_primary);
                chip.setTextColor(getResources().getColor(R.color.white, null));
            }
            
            // Ensure color updates when user clicks dynamically
            chip.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        chip.setChipBackgroundColorResource(R.color.accent_primary);
                        chip.setTextColor(getResources().getColor(R.color.white, null));
                    } else {
                        chip.setChipBackgroundColorResource(R.color.chip_bg);
                        chip.setTextColor(getResources().getColor(R.color.text_primary, null));
                    }
                }
            });
            chipGroup.addView(chip);
        }
        
        Slider sliderRating = view.findViewById(R.id.sliderRating);
        TextView txtRatingLabel = view.findViewById(R.id.txtRatingLabel);
        sliderRating.setValue(minRatingFilter);
        txtRatingLabel.setText(minRatingFilter > 0 ? "⭐ " + minRatingFilter + "+" : "Any (0.0+)");
        
        sliderRating.addOnChangeListener((slider, value, fromUser) -> {
            txtRatingLabel.setText(value > 0 ? "⭐ " + value + "+" : "Any (0.0+)");
            if (fromUser) {
                minRatingFilter = value;
                applyRatingFilter();
            }
        });
        
        RangeSlider sliderYear = view.findViewById(R.id.sliderYear);
        TextView txtYearLabel = view.findViewById(R.id.txtYearLabel);
        sliderYear.setValues(minYearFilter, maxYearFilter);
        txtYearLabel.setText((int)minYearFilter + " - " + (int)maxYearFilter);
        
        sliderYear.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> vals = slider.getValues();
            txtYearLabel.setText(vals.get(0).intValue() + " - " + vals.get(1).intValue());
            if (fromUser) {
                minYearFilter = vals.get(0);
                maxYearFilter = vals.get(1);
                applyRatingFilter();
            }
        });
        
        view.findViewById(R.id.btnResetFilter).setOnClickListener(v -> {
            currentGenreId = "";
            minRatingFilter = 0.0f;
            minYearFilter = 1990f;
            maxYearFilter = 2026f;
            dialog.dismiss();
            applyFiltersFromDialog();
        });
        
        view.findViewById(R.id.btnApplyFilters).setOnClickListener(v -> {
            int checkedChipId = chipGroup.getCheckedChipId();
            if (checkedChipId != View.NO_ID) {
                Chip c = chipGroup.findViewById(checkedChipId);
                if (c != null) {
                    currentGenreId = (String) c.getTag();
                }
            } else {
                currentGenreId = "";
            }
            
            minRatingFilter = sliderRating.getValue();
            List<Float> vals = sliderYear.getValues();
            minYearFilter = vals.get(0);
            maxYearFilter = vals.get(1);
            
            dialog.dismiss();
            applyFiltersFromDialog();
        });
        
        dialog.show();
    }

    private void applyFiltersFromDialog() {
        currentQuery = ""; // Clear explicit search
        android.widget.EditText editSearch = findViewById(R.id.editSearch);
        if (editSearch != null) editSearch.setText("");
        
        currentPage = 1;
        movieList.clear();
        filteredMovieList.clear();
        adapter.notifyDataSetChanged();
        
        showFavoritesOnly = false;
        ImageView btnFavToggle = findViewById(R.id.btnFavToggle);
        if (btnFavToggle != null) btnFavToggle.setColorFilter(null);
        
        hideSearchHistory();
        
        checkViewMode();
        if (recyclerMovies.getVisibility() == View.VISIBLE) {
            fetchTMDBMovies(currentPage);
        }
    }

    private void applyRatingFilter() {
        filteredMovieList.clear();
        for (Movie m : movieList) {
            boolean passRating = m.getVoteAverage() >= minRatingFilter;
            boolean passYear = true;
            if (m.getYear() != null && !m.getYear().isEmpty()) {
                try {
                    int y = Integer.parseInt(m.getYear());
                    passYear = (y >= minYearFilter && y <= maxYearFilter);
                } catch (Exception e) {}
            }
            if (passRating && passYear) {
                filteredMovieList.add(m);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void fetchTMDBMovies(int page) {
        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);

        String endpoint;
        String langUrl = "&language=ar"; // Enforce arabic when viewing grids too
        String baseQuery = "";
        
        if (!currentQuery.isEmpty()) {
            endpoint = "https://api.themoviedb.org/3/search/movie?api_key=" + TMDB_API_KEY + langUrl + "&query=" + android.net.Uri.encode(currentQuery) + "&page=" + page;
        } else {
            baseQuery = "https://api.themoviedb.org/3/discover/movie?api_key=" + TMDB_API_KEY + langUrl + "&page=" + page;
            if (!currentGenreId.isEmpty()) {
                baseQuery += "&with_genres=" + currentGenreId;
            }
            if (minYearFilter > 1990f || maxYearFilter < 2026f) {
                baseQuery += "&primary_release_date.gte=" + (int)minYearFilter + "-01-01" +
                             "&primary_release_date.lte=" + (int)maxYearFilter + "-12-31";
            }
            // Add rating check filter natively to DB optionally, or sort by it
            if (minRatingFilter > 0) {
                baseQuery += "&vote_average.gte=" + minRatingFilter;
            }
            endpoint = baseQuery;
        }

        HttpClient.getJson(this, endpoint, new HttpClient.Callback2() {
            @Override
            public void onSuccess(String body) {
                try {
                    JSONArray results = new JSONObject(body).getJSONArray("results");
                    List<Movie> newMovies = new ArrayList<>();
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject obj = results.getJSONObject(i);
                        String id = String.valueOf(obj.getInt("id"));
                        String title = obj.optString("title", obj.optString("name", ""));
                        String releaseDate = obj.optString("release_date", "");
                        String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
                        String posterPath = obj.optString("poster_path", "");
                        String posterFull = AppConstants.TMDB_IMAGE_BASE + posterPath;
                        double voteAvg = obj.optDouble("vote_average", 0.0);
                        newMovies.add(new Movie(title, posterFull, "⭐️ " + voteAvg, id,
                                obj.optString("overview", ""), year, voteAvg));
                    }
                    movieList.addAll(newMovies);
                    applyRatingFilter();
                } catch (Exception ignored) {}
                progressBar.setVisibility(View.GONE);
                isLoading = false;
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                isLoading = false;
            }
        });
    }

    /**
     * Directly open movie details - NO dialog.
     * Favorite toggle is now inside MovieDetailsActivity.
     */
    private void openMovieDetails(Movie movie) {
        Intent intent = new Intent(this, MovieDetailsActivity.class);
        intent.putExtra("movie", movie);
        startActivity(intent);
    }

    // ––––––– Continue Watching Adapter –––––––
    class ContinueWatchingAdapter extends RecyclerView.Adapter<ContinueWatchingAdapter.CWVH> {
        private List<ContinueWatchingManager.WatchItem> items;
        private java.util.function.Consumer<ContinueWatchingManager.WatchItem> listener;

        ContinueWatchingAdapter(List<ContinueWatchingManager.WatchItem> items, java.util.function.Consumer<ContinueWatchingManager.WatchItem> listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull @Override
        public CWVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_continue_watching, parent, false);
            return new CWVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CWVH holder, int position) {
            ContinueWatchingManager.WatchItem item = items.get(position);
            holder.txtName.setText(item.name);
            holder.txtSubtitle.setText(item.getSubtitle());

            if (item.poster != null && !item.poster.isEmpty() && !item.poster.endsWith("null")) {
                Glide.with(holder.itemView.getContext())
                    .load(item.poster)
                    .transform(new CenterCrop(), new RoundedCorners(12))
                    .into(holder.imgPoster);
            }

            holder.itemView.setOnClickListener(v -> listener.accept(item));
        }

        @Override public int getItemCount() { return items.size(); }

        class CWVH extends RecyclerView.ViewHolder {
            ImageView imgPoster;
            TextView txtName, txtSubtitle;
            CWVH(@NonNull View itemView) {
                super(itemView);
                imgPoster = itemView.findViewById(R.id.imgPoster);
                txtName = itemView.findViewById(R.id.txtName);
                txtSubtitle = itemView.findViewById(R.id.txtSubtitle);
            }
        }
    }

    // ––––––– Search History Adapter –––––––
    static class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.SHVH> {
        private List<String> history;
        private OnHistoryClickListener listener;

        interface OnHistoryClickListener {
            void onItemClick(String query);
            void onRemoveClick(String query, int position);
        }

        SearchHistoryAdapter(List<String> history, OnHistoryClickListener listener) {
            this.history = new ArrayList<>(history);
            this.listener = listener;
        }

        void removeItem(int position) {
            if (position >= 0 && position < history.size()) {
                history.remove(position);
                notifyItemRemoved(position);
            }
        }

        @NonNull @Override
        public SHVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_history, parent, false);
            return new SHVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SHVH holder, int position) {
            String query = history.get(position);
            holder.txtQuery.setText(query);
            holder.itemView.setOnClickListener(v -> listener.onItemClick(query));
            holder.btnRemove.setOnClickListener(v -> listener.onRemoveClick(query, holder.getAdapterPosition()));
        }

        @Override public int getItemCount() { return history.size(); }

        static class SHVH extends RecyclerView.ViewHolder {
            TextView txtQuery;
            ImageView btnRemove;
            SHVH(@NonNull View itemView) {
                super(itemView);
                txtQuery = itemView.findViewById(R.id.txtSearchQuery);
                btnRemove = itemView.findViewById(R.id.btnRemove);
            }
        }
    }

    // ––––––– Movies Adapter –––––––
    class MoviesAdapter extends RecyclerView.Adapter<MoviesAdapter.MovieViewHolder> {

        private List<Movie> movies;
        private OnMovieClickListener listener;

        public MoviesAdapter(List<Movie> movies, OnMovieClickListener listener) {
            this.movies = movies;
            this.listener = listener;
        }

        @NonNull
        @Override
        public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie, parent, false);
            return new MovieViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
            Movie movie = movies.get(position);
            holder.txtName.setText(movie.getName());
            holder.txtCategory.setText(movie.getCategory() != null ? movie.getCategory() : "Free VOD");
            
            if (movie.getYear() != null && !movie.getYear().isEmpty()) {
                holder.txtYear.setText(movie.getYear());
                holder.txtYear.setVisibility(View.VISIBLE);
            } else {
                holder.txtYear.setVisibility(View.GONE);
            }

            if (movie.getPoster() != null && !movie.getPoster().isEmpty() && !movie.getPoster().endsWith("null")) {
                Glide.with(holder.itemView.getContext())
                        .load(movie.getPoster())
                        .transform(new CenterCrop(), new RoundedCorners(16))
                        .into(holder.imgPoster);
            } else {
                holder.imgPoster.setImageResource(R.mipmap.ic_launcher);
            }

            // Click directly opens details - no dialog
            holder.itemView.setOnClickListener(v -> listener.onMovieClick(movie));
        }

        @Override
        public int getItemCount() {
            return movies.size();
        }

        class MovieViewHolder extends RecyclerView.ViewHolder {
            ImageView imgPoster;
            TextView txtName;
            TextView txtCategory;
            TextView txtYear;

            public MovieViewHolder(@NonNull View itemView) {
                super(itemView);
                imgPoster = itemView.findViewById(R.id.imgPoster);
                txtName = itemView.findViewById(R.id.txtMovieName);
                txtCategory = itemView.findViewById(R.id.txtMovieCategory);
                txtYear = itemView.findViewById(R.id.txtYear);
            }
        }
    }

    interface OnMovieClickListener {
        void onMovieClick(Movie movie);
    }
}
