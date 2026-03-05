package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SeriesActivity extends BaseActivity {

    private RecyclerView recyclerSeries;
    private ProgressBar progressBar;
    private List<Movie> seriesList = new ArrayList<>();
    private List<Movie> filteredSeriesList = new ArrayList<>();
    private SeriesAdapter adapter;
    private int currentPage = 1;
    private boolean isLoading = false;
    private String currentQuery = "";
    private String currentGenreId = "";

    private static final String TMDB_API_KEY = "4e44d9029b1270a757cddc766a1bcb63";

    // Advanced filters
    private float minRatingFilter = 0.0f;
    private float minYearFilter = 1990f;
    private float maxYearFilter = 2026f;

    // Search History
    private SearchHistoryManager searchHistoryManager;
    private boolean isSearchHistoryVisible = false;
    private RecyclerView recyclerSearchHistory;
    private MoviesActivity.SearchHistoryAdapter searchHistoryAdapter;

    // Continue Watching
    private ContinueWatchingManager continueWatchingManager;
    private RecyclerView recyclerContinueWatching;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series);

        searchHistoryManager = new SearchHistoryManager(this);
        continueWatchingManager = new ContinueWatchingManager(this);

        progressBar = findViewById(R.id.progressBar);
        recyclerSeries = findViewById(R.id.recyclerSeries);
        
        // 5 columns layout
        GridLayoutManager layoutManager = new GridLayoutManager(this, 5);
        recyclerSeries.setLayoutManager(layoutManager);

        android.widget.EditText editSearch = findViewById(R.id.editSearch);
        android.content.SharedPreferences searchPrefs = getSharedPreferences("SearchState", MODE_PRIVATE);
        currentQuery = searchPrefs.getString("series_query", "");
        editSearch.setText(currentQuery);
        editSearch.setSelection(currentQuery.length());
        
        // Debounced search logic
        android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable searchRunnable = () -> {
            String newQuery = editSearch.getText().toString().trim();
            if (!newQuery.equals(currentQuery)) {
                currentQuery = newQuery;
                searchPrefs.edit().putString("series_query", currentQuery).apply();
                
                // Save to search history
                if (!currentQuery.isEmpty() && currentQuery.length() >= 2) {
                    searchHistoryManager.addSeriesSearch(currentQuery);
                }
                
                currentPage = 1;
                seriesList.clear();
                filteredSeriesList.clear();
                adapter.notifyDataSetChanged();
                
                if (!currentQuery.isEmpty()) {
                    currentGenreId = "";
                }
                // Hide search history and continue watching when typing
                hideSearchHistory();
                if (headerAdapter != null) headerAdapter.notifyDataSetChanged();
                
                fetchTMDBSeries(currentPage);
            }
        };

        editSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 600);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                
                String q = editSearch.getText().toString().trim();
                if (!q.isEmpty()) {
                    searchHistoryManager.addSeriesSearch(q);
                }
                hideSearchHistory();
                return true;
            }
            return false;
        });

        // Search icon
        ImageView btnSearchIcon = findViewById(R.id.btnSearchIcon);
        btnSearchIcon.setOnClickListener(v -> {
            if (isSearchHistoryVisible) {
                hideSearchHistory();
            } else {
                showSearchHistory();
            }
        });

        adapter = new SeriesAdapter(filteredSeriesList, this::openSeriesDetails);
        
        headerAdapter = new HeaderAdapter();
        headerAdapter.setItems(continueWatchingManager.getItems());

        ConcatAdapter sortedAdapter = new ConcatAdapter(headerAdapter, adapter);
        recyclerSeries.setAdapter(sortedAdapter);

        // Header takes full 5 columns, normal series takes 1
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0 && headerAdapter.getItemCount() > 0) {
                    return 5;
                }
                return 1;
            }
        });

        // Filter Button Setup
        ImageView btnFilterIcon = findViewById(R.id.btnFilterIcon);
        if (btnFilterIcon != null) {
            btnFilterIcon.setOnClickListener(v -> showAdvancedFilterDialog());
        }

        // Infinite Scroll
        recyclerSeries.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && !isLoading) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        currentPage++;
                        fetchTMDBSeries(currentPage);
                    }
                }
            }
        });

        // Setup Search History
        setupSearchHistory();

        fetchTMDBSeries(currentPage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (headerAdapter != null) {
            headerAdapter.setItems(continueWatchingManager.getItems());
        }
    }

    private void setupSearchHistory() {
        recyclerSearchHistory = findViewById(R.id.recyclerSearchHistory);
        recyclerSearchHistory.setLayoutManager(new LinearLayoutManager(this));

        TextView btnClearHistory = findViewById(R.id.btnClearHistory);
        btnClearHistory.setOnClickListener(v -> {
            searchHistoryManager.clearSeriesHistory();
            hideSearchHistory();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void showSearchHistory() {
        List<String> history = searchHistoryManager.getSeriesHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "No search history yet", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.EditText editSearch = findViewById(R.id.editSearch);

        searchHistoryAdapter = new MoviesActivity.SearchHistoryAdapter(history, new MoviesActivity.SearchHistoryAdapter.OnHistoryClickListener() {
            @Override
            public void onItemClick(String query) {
                editSearch.setText(query);
                editSearch.setSelection(query.length());
                hideSearchHistory();
            }

            @Override
            public void onRemoveClick(String query, int position) {
                List<String> updated = searchHistoryManager.getSeriesHistory();
                updated.remove(query);
                searchHistoryManager.clearSeriesHistory();
                for (int i = updated.size() - 1; i >= 0; i--) {
                    searchHistoryManager.addSeriesSearch(updated.get(i));
                }
                searchHistoryAdapter.removeItem(position);
                if (searchHistoryAdapter.getItemCount() == 0) {
                    hideSearchHistory();
                }
            }
        });
        recyclerSearchHistory.setAdapter(searchHistoryAdapter);

        findViewById(R.id.searchHistoryPanel).setVisibility(View.VISIBLE);
        recyclerSeries.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        isSearchHistoryVisible = true;
    }

    private void hideSearchHistory() {
        findViewById(R.id.searchHistoryPanel).setVisibility(View.GONE);
        recyclerSeries.setVisibility(View.VISIBLE);
        isSearchHistoryVisible = false;
        if (headerAdapter != null) {
            headerAdapter.notifyDataSetChanged();
        }
    }

    private HeaderAdapter headerAdapter;

    class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder> {
        private List<ContinueWatchingManager.WatchItem> items = new ArrayList<>();
        private ContinueWatchingSeriesAdapter horizontalAdapter;

        public void setItems(List<ContinueWatchingManager.WatchItem> allItems) {
            List<ContinueWatchingManager.WatchItem> seriesItems = new ArrayList<>();
            for (ContinueWatchingManager.WatchItem item : allItems) {
                if (item.isSeries) seriesItems.add(item);
            }
            this.items = seriesItems;
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
            horizontalAdapter = new ContinueWatchingSeriesAdapter(items, item -> {
                Movie movie = item.toMovie();
                openSeriesDetails(movie);
            });
            holder.recycler.setAdapter(horizontalAdapter);
        }

        @Override
        public int getItemCount() {
            // Hide header if no items, or if user is searching/filtering
            if (items.isEmpty() || !currentQuery.isEmpty() || !currentGenreId.isEmpty() || minRatingFilter > 0 || minYearFilter > 1990f || maxYearFilter < 2026f) {
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
        String[] genres = {"All", "Action|10759", "Animation|16", "Comedy|35", "Crime|80", "Drama|18", "Family|10762", "Mystery|9648", "Sci-Fi|10765"};
        
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
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    chip.setChipBackgroundColorResource(R.color.accent_primary);
                    chip.setTextColor(getResources().getColor(R.color.white, null));
                } else {
                    chip.setChipBackgroundColorResource(R.color.chip_bg);
                    chip.setTextColor(getResources().getColor(R.color.text_primary, null));
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
        seriesList.clear();
        filteredSeriesList.clear();
        adapter.notifyDataSetChanged();
        
        hideSearchHistory();
        if (headerAdapter != null) headerAdapter.notifyDataSetChanged();
        
        fetchTMDBSeries(currentPage);
    }

    private void applyRatingFilter() {
        filteredSeriesList.clear();
        for (Movie m : seriesList) {
            boolean passRating = m.getVoteAverage() >= minRatingFilter;
            boolean passYear = true;
            if (m.getYear() != null && !m.getYear().isEmpty()) {
                try {
                    int y = Integer.parseInt(m.getYear());
                    passYear = (y >= minYearFilter && y <= maxYearFilter);
                } catch (Exception e) {}
            }
            if (passRating && passYear) {
                filteredSeriesList.add(m);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void fetchTMDBSeries(int page) {
        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);

        String endpoint;
        String langUrl = "&language=ar";
        String baseQuery = "";
        
        if (!currentQuery.isEmpty()) {
            endpoint = "https://api.themoviedb.org/3/search/tv?api_key=" + TMDB_API_KEY + langUrl + "&query=" + android.net.Uri.encode(currentQuery) + "&page=" + page;
        } else {
            baseQuery = "https://api.themoviedb.org/3/discover/tv?api_key=" + TMDB_API_KEY + langUrl + "&page=" + page;
            if (!currentGenreId.isEmpty()) {
                baseQuery += "&with_genres=" + currentGenreId;
            }
            if (minYearFilter > 1990f || maxYearFilter < 2026f) {
                baseQuery += "&first_air_date.gte=" + (int)minYearFilter + "-01-01" +
                             "&first_air_date.lte=" + (int)maxYearFilter + "-12-31";
            }
            if (minRatingFilter > 0) {
                baseQuery += "&vote_average.gte=" + minRatingFilter;
            }
            endpoint = baseQuery;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                JSONArray results = jsonObj.getJSONArray("results");
                List<Movie> newSeries = new ArrayList<>();

                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String id = String.valueOf(obj.getInt("id"));
                    String title = obj.optString("name", obj.optString("original_name"));
                    String firstAirDate = obj.optString("first_air_date", "");
                    String year = firstAirDate.length() >= 4 ? firstAirDate.substring(0, 4) : "";
                    String posterPath = obj.optString("poster_path", "");
                    String posterFull = "https://image.tmdb.org/t/p/w500" + posterPath;
                    double voteAvg = obj.optDouble("vote_average", 0.0);
                    String vote = String.valueOf(voteAvg);

                    Movie m = new Movie(title, posterFull, "⭐️ " + vote, id, obj.optString("overview", ""), year, voteAvg);
                    newSeries.add(m);
                }

                handler.post(() -> {
                    seriesList.addAll(newSeries);
                    applyRatingFilter();
                    progressBar.setVisibility(View.GONE);
                    isLoading = false;
                });

            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isLoading = false;
                    Toast.makeText(SeriesActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void openSeriesDetails(Movie series) {
        Intent intent = new Intent(this, SeriesDetailsActivity.class);
        intent.putExtra("series", series);
        startActivity(intent);
    }

    // Continue Watching Adapter for Series
    class ContinueWatchingSeriesAdapter extends RecyclerView.Adapter<ContinueWatchingSeriesAdapter.CWVH> {
        private List<ContinueWatchingManager.WatchItem> items;
        private java.util.function.Consumer<ContinueWatchingManager.WatchItem> listener;

        ContinueWatchingSeriesAdapter(List<ContinueWatchingManager.WatchItem> items, java.util.function.Consumer<ContinueWatchingManager.WatchItem> listener) {
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

    // Series Adapter
    class SeriesAdapter extends RecyclerView.Adapter<SeriesAdapter.SeriesViewHolder> {

        private List<Movie> list;
        private OnSeriesClickListener listener;

        public SeriesAdapter(List<Movie> list, OnSeriesClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public SeriesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie, parent, false);
            return new SeriesViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SeriesViewHolder holder, int position) {
            Movie movie = list.get(position);
            holder.txtName.setText(movie.getName());
            holder.txtCategory.setText(movie.getCategory() != null ? movie.getCategory() : "TV Series");
            
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

            holder.itemView.setOnClickListener(v -> listener.onSeriesClick(movie));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class SeriesViewHolder extends RecyclerView.ViewHolder {
            ImageView imgPoster;
            TextView txtName;
            TextView txtCategory;
            TextView txtYear;

            public SeriesViewHolder(@NonNull View itemView) {
                super(itemView);
                imgPoster = itemView.findViewById(R.id.imgPoster);
                txtName = itemView.findViewById(R.id.txtMovieName);
                txtCategory = itemView.findViewById(R.id.txtMovieCategory);
                txtYear = itemView.findViewById(R.id.txtYear);
            }
        }
    }

    interface OnSeriesClickListener {
        void onSeriesClick(Movie series);
    }
}
