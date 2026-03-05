package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

public class SeriesDetailsActivity extends AppCompatActivity {

    private static final String TMDB_API_KEY = "4e44d9029b1270a757cddc766a1bcb63";

    private Movie series;
    private RecyclerView recyclerSeasons;
    private RecyclerView recyclerEpisodes;
    private ProgressBar progressEpisodes;
    private TextView txtEpisodesTitle;
    
    private List<Integer> seasonsList = new ArrayList<>();
    private SeasonsAdapter seasonsAdapter;
    
    private List<Episode> episodesList = new ArrayList<>();
    private EpisodesAdapter episodesAdapter;
    private int activeSeason = -1;

    private boolean isFavorite = false;
    private boolean inWatchlist = false;
    private FavoritesMoviesManager favManager;
    private WatchlistManager watchlistManager;
    private ImageView btnFavorite;
    private ImageView btnWatchlist;

    // Continue Watching
    private ContinueWatchingManager continueWatchingManager;

    // New UI Elements for Cast & Similar
    private View layoutCast, layoutSimilar;
    private androidx.recyclerview.widget.RecyclerView recyclerCast, recyclerSimilar;
    private java.util.List<Actor> castList = new java.util.ArrayList<>();
    private java.util.List<Movie> similarList = new java.util.ArrayList<>();
    private ActorAdapter castAdapter;
    private HorizontalMovieAdapter similarAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series_details);

        series = (Movie) getIntent().getSerializableExtra("series");
        if (series == null) {
            finish();
            return;
        }

        favManager = new FavoritesMoviesManager(this);
        watchlistManager = new WatchlistManager(this);
        continueWatchingManager = new ContinueWatchingManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        ImageView imgBackdrop = findViewById(R.id.imgBackdrop);
        TextView txtSeriesTitle = findViewById(R.id.txtSeriesTitle);
        TextView txtYear = findViewById(R.id.txtYear);
        TextView txtRating = findViewById(R.id.txtRating);
        TextView txtOverview = findViewById(R.id.txtOverview);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnWatchlist = findViewById(R.id.btnWatchlist);

        txtSeriesTitle.setText(series.getName());
        txtYear.setText(series.getYear() != null ? series.getYear() : "");
        txtRating.setText(String.format(java.util.Locale.US, "%.1f", series.getVoteAverage()));
        txtOverview.setText(series.getDescription() != null ? series.getDescription() : "No Description");

        if (series.getPoster() != null && !series.getPoster().isEmpty()) {
            Glide.with(this).load(series.getPoster()).transform(new CenterCrop()).into(imgBackdrop);
        }

        recyclerSeasons = findViewById(R.id.recyclerSeasons);
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes);
        progressEpisodes = findViewById(R.id.progressEpisodes);
        txtEpisodesTitle = findViewById(R.id.txtEpisodesTitle);

        recyclerSeasons.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        seasonsAdapter = new SeasonsAdapter(seasonsList, this::onSeasonClicked);
        recyclerSeasons.setAdapter(seasonsAdapter);

        recyclerEpisodes.setLayoutManager(new LinearLayoutManager(this));
        episodesAdapter = new EpisodesAdapter(episodesList, this::onEpisodeClicked);
        recyclerEpisodes.setAdapter(episodesAdapter);

        // Initialize new views
        layoutCast = findViewById(R.id.layoutCast);
        layoutSimilar = findViewById(R.id.layoutSimilar);
        recyclerCast = findViewById(R.id.recyclerCast);
        recyclerSimilar = findViewById(R.id.recyclerSimilar);

        recyclerCast.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        castAdapter = new ActorAdapter(castList, actor -> openActorDetails(actor));
        recyclerCast.setAdapter(castAdapter);

        recyclerSimilar.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        similarAdapter = new HorizontalMovieAdapter(similarList, selected -> {
            Intent intent = new Intent(SeriesDetailsActivity.this, SeriesDetailsActivity.class);
            intent.putExtra("series", selected);
            startActivity(intent);
        });
        recyclerSimilar.setAdapter(similarAdapter);

        // ––– Favorite and Watchlist Logic –––
        updateFavoriteUI();
        updateWatchlistUI();

        btnFavorite.setOnClickListener(v -> {
            if (isFavorite) {
                favManager.removeMovie(series);
                isFavorite = false;
                Toast.makeText(this, "Removed from Favorites 💔", Toast.LENGTH_SHORT).show();
            } else {
                favManager.addMovie(series);
                isFavorite = true;
                Toast.makeText(this, "Added to Favorites ❤️", Toast.LENGTH_SHORT).show();
            }
            updateFavoriteUI();
        });

        btnWatchlist.setOnClickListener(v -> {
            if (inWatchlist) {
                watchlistManager.removeItem(series);
                inWatchlist = false;
                Toast.makeText(this, "Removed from Watchlist ➖", Toast.LENGTH_SHORT).show();
            } else {
                watchlistManager.addItem(series);
                inWatchlist = true;
                Toast.makeText(this, "Added to Watchlist ➕", Toast.LENGTH_SHORT).show();
            }
            updateWatchlistUI();
        });

        fetchSeasons();
        fetchCast();
        fetchSimilarSeries();
        fetchMetadata();
    }

    private void updateFavoriteUI() {
        isFavorite = favManager.isFavorite(series);
        if (isFavorite) {
            btnFavorite.setImageResource(R.drawable.ic_favorite_24);
            btnFavorite.setColorFilter(android.graphics.Color.RED);
        } else {
            btnFavorite.setImageResource(R.drawable.ic_favorite_24);
            btnFavorite.setColorFilter(getResources().getColor(R.color.text_secondary, null));
        }
    }

    private void updateWatchlistUI() {
        inWatchlist = watchlistManager.isInWatchlist(series);
        if (inWatchlist) {
            btnWatchlist.setImageResource(R.drawable.ic_playlist_add_check);
            btnWatchlist.setColorFilter(getResources().getColor(R.color.accent_success, null));
        } else {
            btnWatchlist.setImageResource(R.drawable.ic_playlist_add);
            btnWatchlist.setColorFilter(getResources().getColor(R.color.text_secondary, null));
        }
    }

    private void openActorDetails(Actor actor) {
        Intent intent = new Intent(this, ActorDetailsActivity.class);
        intent.putExtra("actor_id", actor.getId());
        intent.putExtra("actor_name", actor.getName());
        startActivity(intent);
    }

    private void fetchSeasons() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String endpoint = "https://api.themoviedb.org/3/tv/" + series.getUrl() + "?api_key=" + TMDB_API_KEY + "&language=en-US";
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                JSONArray arr = jsonObj.getJSONArray("seasons");

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int seasonNum = obj.getInt("season_number");
                    if (seasonNum > 0) { // skip specials usually 0
                        seasonsList.add(seasonNum);
                    }
                }

                handler.post(() -> {
                    seasonsAdapter.notifyDataSetChanged();
                    if (!seasonsList.isEmpty()) {
                        // auto select first found season
                        onSeasonClicked(seasonsList.get(0));
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void onSeasonClicked(int seasonNumber) {
        activeSeason = seasonNumber;
        seasonsAdapter.notifyDataSetChanged();
        fetchEpisodes(seasonNumber);
    }

    private void fetchEpisodes(int seasonNumber) {
        txtEpisodesTitle.setVisibility(View.VISIBLE);
        txtEpisodesTitle.setText("Episodes - Season " + seasonNumber);
        progressEpisodes.setVisibility(View.VISIBLE);
        episodesList.clear();
        episodesAdapter.notifyDataSetChanged();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String endpoint = "https://api.themoviedb.org/3/tv/" + series.getUrl() + "/season/" + seasonNumber + "?api_key=" + TMDB_API_KEY + "&language=en-US";
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                JSONArray results = jsonObj.getJSONArray("episodes");
                
                List<Episode> fetched = new ArrayList<>();
                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String id = String.valueOf(obj.getInt("id"));
                    String name = obj.getString("name");
                    String overview = obj.optString("overview", "No overview available.");
                    int epNum = obj.getInt("episode_number");
                    String stillPath = obj.optString("still_path", "");
                    String imageFull = stillPath.isEmpty() ? "" : "https://image.tmdb.org/t/p/w300" + stillPath;

                    Episode ep = new Episode(id, epNum + ". " + name, overview, imageFull, seasonNumber, epNum);
                    fetched.add(ep);
                }

                handler.post(() -> {
                    episodesList.addAll(fetched);
                    episodesAdapter.notifyDataSetChanged();
                    progressEpisodes.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                handler.post(() -> {
                    progressEpisodes.setVisibility(View.GONE);
                    Toast.makeText(SeriesDetailsActivity.this, "Failed to load episodes.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void onEpisodeClicked(Episode episode) {
        String tmdbId = series.getUrl();
        int s = episode.getSeasonNumber();
        int e = episode.getEpisodeNumber();

        // Add to Continue Watching (updates 'last watched' for home screen)
        continueWatchingManager.addSeries(series, s, e);
        // Mark this specific episode as watched
        continueWatchingManager.markEpisodeWatched(tmdbId, s, e);
        // Refresh the list to show the checkmark immediately
        episodesAdapter.notifyDataSetChanged();

        Intent intent = new Intent(this, WebPlayerActivity.class);
        intent.putExtra("tmdbId", tmdbId);
        intent.putExtra("isTvSeries", true);
        intent.putExtra("season", s);
        intent.putExtra("episode", e);
        startActivity(intent);
    }

    class SeasonsAdapter extends RecyclerView.Adapter<SeasonsAdapter.ViewHolder> {
        private List<Integer> list;
        private OnSeasonClickListener listener;

        public SeasonsAdapter(List<Integer> list, OnSeasonClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_season, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int season = list.get(position);
            holder.txtSeasonName.setText("Season " + season);
            
            if (activeSeason == season) {
                holder.txtSeasonName.setBackgroundResource(R.drawable.chip_bg_active);
                holder.txtSeasonName.setTextColor(getResources().getColor(R.color.white, null));
            } else {
                holder.txtSeasonName.setBackgroundResource(R.drawable.chip_bg);
                holder.txtSeasonName.setTextColor(getResources().getColor(R.color.text_primary, null));
            }

            holder.itemView.setOnClickListener(v -> listener.onClick(season));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtSeasonName;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtSeasonName = itemView.findViewById(R.id.txtSeasonName);
            }
        }
    }

    class EpisodesAdapter extends RecyclerView.Adapter<EpisodesAdapter.ViewHolder> {
        private List<Episode> list;
        private OnEpisodeClickListener listener;

        public EpisodesAdapter(List<Episode> list, OnEpisodeClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Episode ep = list.get(position);
            holder.txtName.setText(ep.getName());
            holder.txtOverview.setText(ep.getOverview());

            if (!ep.getImage().isEmpty() && !ep.getImage().endsWith("null")) {
                Glide.with(holder.itemView.getContext())
                        .load(ep.getImage())
                        .transform(new CenterCrop(), new RoundedCorners(12))
                        .into(holder.imgEpisodeImage);
            } else {
                holder.imgEpisodeImage.setImageResource(R.mipmap.ic_launcher);
            }

            boolean isWatched = continueWatchingManager.isEpisodeWatched(series.getUrl(), ep.getSeasonNumber(), ep.getEpisodeNumber());
            holder.imgWatched.setVisibility(isWatched ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> listener.onClick(ep));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgEpisodeImage;
            ImageView imgWatched;
            TextView txtName;
            TextView txtOverview;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgEpisodeImage = itemView.findViewById(R.id.imgEpisodeImage);
                imgWatched = itemView.findViewById(R.id.imgWatched);
                txtName = itemView.findViewById(R.id.txtEpisodeName);
                txtOverview = itemView.findViewById(R.id.txtEpisodeOverview);
            }
        }
    }

    interface OnSeasonClickListener { void onClick(int seasonNumber); }
    interface OnEpisodeClickListener { void onClick(Episode episode); }

    private void fetchCast() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            try {
                URL url = new URL("https://api.themoviedb.org/3/tv/" + series.getUrl() + "/credits?api_key=" + TMDB_API_KEY + "&language=ar");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                JSONArray results = jsonObj.getJSONArray("cast");
                for (int i = 0; i < Math.min(10, results.length()); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String name = obj.getString("name");
                    String character = obj.optString("character", "");
                    String profilePath = obj.optString("profile_path", "");
                    String fullProfilePath = profilePath.isEmpty() || profilePath.equals("null") ? "" : "https://image.tmdb.org/t/p/w185" + profilePath;
                    castList.add(new Actor(String.valueOf(obj.getInt("id")), name, character, fullProfilePath));
                }
                handler.post(() -> {
                    if (!castList.isEmpty()) {
                        layoutCast.setVisibility(View.VISIBLE);
                        castAdapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void fetchSimilarSeries() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            try {
                URL url = new URL("https://api.themoviedb.org/3/tv/" + series.getUrl() + "/similar?api_key=" + TMDB_API_KEY + "&language=ar");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                JSONArray results = jsonObj.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String id = String.valueOf(obj.getInt("id"));
                    // TV shows use "name" instead of "title" and "first_air_date" instead of "release_date"
                    String title = obj.has("name") ? obj.getString("name") : obj.optString("title", "Unknown");
                    String releaseDate = obj.optString("first_air_date", "");
                    String year = releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : "";
                    String posterPath = obj.optString("poster_path", "");
                    String posterFull = "https://image.tmdb.org/t/p/w500" + posterPath;
                    double voteAvg = obj.optDouble("vote_average", 0.0);
                    similarList.add(new Movie(title, posterFull, "⭐️ " + voteAvg, id, obj.optString("overview", ""), year, voteAvg));
                }
                handler.post(() -> {
                    if (!similarList.isEmpty()) {
                        layoutSimilar.setVisibility(View.VISIBLE);
                        similarAdapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void fetchMetadata() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            try {
                URL url = new URL("https://api.themoviedb.org/3/tv/" + series.getUrl() + "?api_key=" + TMDB_API_KEY + "&append_to_response=content_ratings");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                JSONArray runtimes = jsonObj.optJSONArray("episode_run_time");
                int runtime = 0;
                if (runtimes != null && runtimes.length() > 0) {
                    runtime = runtimes.getInt(0);
                }
                
                String certification = "";
                if (jsonObj.has("content_ratings")) {
                    JSONArray results = jsonObj.getJSONObject("content_ratings").getJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject rObj = results.getJSONObject(i);
                        if (rObj.optString("iso_3166_1").equals("US")) {
                            certification = rObj.optString("rating", "");
                            break;
                        }
                    }
                }
                
                final String finalRuntime = runtime > 0 ? (runtime / 60 > 0 ? (runtime / 60) + "h " : "") + (runtime % 60) + "m" : "";
                final String finalCert = certification.isEmpty() ? "NR" : certification;

                handler.post(() -> {
                    TextView txtRuntime = findViewById(R.id.txtRuntime);
                    TextView txtAgeRating = findViewById(R.id.txtAgeRating);
                    if (txtRuntime != null && !finalRuntime.isEmpty()) txtRuntime.setText(finalRuntime);
                    if (txtAgeRating != null) txtAgeRating.setText(finalCert);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
