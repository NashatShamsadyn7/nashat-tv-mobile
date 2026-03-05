package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;

public class MovieDetailsActivity extends AppCompatActivity {

    private static final String TMDB_API_KEY = "4e44d9029b1270a757cddc766a1bcb63";

    private Movie movie;
    private String trailerYoutubeKey = null;
    private FavoritesMoviesManager favManager;
    private WatchlistManager watchlistManager;
    private ContinueWatchingManager continueWatchingManager;
    private ImageView btnFavorite;
    private ImageView btnWatchlist;
    private boolean isFavorite = false;
    private boolean inWatchlist = false;
    private CastStateListener castStateListener;
    private com.google.android.material.button.MaterialButton btnCastToTv;

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
        setContentView(R.layout.activity_movie_details);

        movie = (Movie) getIntent().getSerializableExtra("movie");
        if (movie == null) {
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
        TextView txtMovieTitle = findViewById(R.id.txtMovieTitle);
        TextView txtYear = findViewById(R.id.txtYear);
        TextView txtRating = findViewById(R.id.txtRating);
        TextView txtOverview = findViewById(R.id.txtOverview);
        MaterialButton btnPlay = findViewById(R.id.btnPlay);
        MaterialButton btnTrailer = findViewById(R.id.btnTrailer);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnWatchlist = findViewById(R.id.btnWatchlist);
        btnCastToTv = findViewById(R.id.btnCastToTv);

        txtMovieTitle.setText(movie.getName());
        txtYear.setText(movie.getYear() != null ? movie.getYear() : "");
        txtRating.setText(String.format(java.util.Locale.US, "%.1f", movie.getVoteAverage()));
        txtOverview.setText(movie.getDescription() != null ? movie.getDescription() : "No Description");

        if (movie.getPoster() != null && !movie.getPoster().isEmpty()) {
            Glide.with(this).load(movie.getPoster()).transform(new CenterCrop()).into(imgBackdrop);
        }

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
            Intent intent = new Intent(MovieDetailsActivity.this, MovieDetailsActivity.class);
            intent.putExtra("movie", selected);
            startActivity(intent);
        });
        recyclerSimilar.setAdapter(similarAdapter);

        // ––– Favorite Button Logic –––
        updateFavoriteUI();
        updateWatchlistUI();

        btnFavorite.setOnClickListener(v -> {
            if (isFavorite) {
                favManager.removeMovie(movie);
                isFavorite = false;
                Toast.makeText(this, "Removed from Favorites 💔", Toast.LENGTH_SHORT).show();
            } else {
                favManager.addMovie(movie);
                isFavorite = true;
                Toast.makeText(this, "Added to Favorites ❤️", Toast.LENGTH_SHORT).show();
            }
            updateFavoriteUI();
        });

        btnWatchlist.setOnClickListener(v -> {
            if (inWatchlist) {
                watchlistManager.removeItem(movie);
                inWatchlist = false;
                Toast.makeText(this, "Removed from Watchlist ➖", Toast.LENGTH_SHORT).show();
            } else {
                watchlistManager.addItem(movie);
                inWatchlist = true;
                Toast.makeText(this, "Added to Watchlist ➕", Toast.LENGTH_SHORT).show();
            }
            updateWatchlistUI();
        });

        // ––– Play Button –––
        btnPlay.setOnClickListener(v -> {
            // Add to Continue Watching
            continueWatchingManager.addMovie(movie);

            Intent intent = new Intent(this, WebPlayerActivity.class);
            intent.putExtra("tmdbId", movie.getUrl()); 
            startActivity(intent);
        });

        // ––– Trailer Button –––
        btnTrailer.setOnClickListener(v -> {
            if (trailerYoutubeKey != null) {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=" + trailerYoutubeKey));
                startActivity(webIntent);
            } else {
                Toast.makeText(this, "Trailer not fully loaded yet, wait a second!", Toast.LENGTH_SHORT).show();
            }
        });

        fetchTrailer();
        fetchCast();
        fetchSimilarMovies();
        fetchMetadata();

        // ––– Cast to TV button –––
        setupCastButton();
    }

    private void setupCastButton() {
        try {
            CastContext castContext = CastContext.getSharedInstance(this);

            // Check initial state
            if (castContext.getCastState() != CastState.NO_DEVICES_AVAILABLE && btnCastToTv != null) {
                btnCastToTv.setVisibility(View.VISIBLE);
            }

            // Listen for state changes
            castStateListener = newState -> {
                if (btnCastToTv != null) {
                    boolean show = newState != CastState.NO_DEVICES_AVAILABLE;
                    btnCastToTv.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            };
            castContext.addCastStateListener(castStateListener);

            if (btnCastToTv != null) {
                btnCastToTv.setOnClickListener(v -> {
                    // Launch player with cast flag
                    Intent intent = new Intent(this, PlayerActivity.class);
                    intent.putExtra("name", movie.getName());
                    intent.putExtra("url", movie.getUrl());
                    intent.putExtra("logo", movie.getPoster());
                    intent.putExtra("category", "Movies");
                    intent.putExtra("cast_immediately", true);
                    startActivity(intent);
                });
            }
        } catch (Exception e) {
            // Cast SDK not available; hide the button silently
            if (btnCastToTv != null) btnCastToTv.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (castStateListener != null) {
                CastContext.getSharedInstance(this).removeCastStateListener(castStateListener);
            }
        } catch (Exception ignored) {}
    }

    private void updateFavoriteUI() {
        isFavorite = favManager.isFavorite(movie);
        if (isFavorite) {
            btnFavorite.setImageResource(R.drawable.ic_favorite_24);
            btnFavorite.setColorFilter(android.graphics.Color.RED);
        } else {
            btnFavorite.setImageResource(R.drawable.ic_favorite_24);
            btnFavorite.setColorFilter(getResources().getColor(R.color.text_secondary, null));
        }
    }

    private void updateWatchlistUI() {
        inWatchlist = watchlistManager.isInWatchlist(movie);
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

    private void fetchTrailer() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String endpoint = "https://api.themoviedb.org/3/movie/" + movie.getUrl() + "/videos?api_key=" + TMDB_API_KEY + "&language=en-US";
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

                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    if ("Trailer".equals(obj.getString("type")) && "YouTube".equals(obj.getString("site"))) {
                        trailerYoutubeKey = obj.getString("key");
                        break;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void fetchCast() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            try {
                URL url = new URL("https://api.themoviedb.org/3/movie/" + movie.getUrl() + "/credits?api_key=" + TMDB_API_KEY + "&language=ar");
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

    private void fetchSimilarMovies() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            try {
                URL url = new URL("https://api.themoviedb.org/3/movie/" + movie.getUrl() + "/similar?api_key=" + TMDB_API_KEY + "&language=ar");
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
                    String title = obj.getString("title");
                    String releaseDate = obj.optString("release_date", "");
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
                URL url = new URL("https://api.themoviedb.org/3/movie/" + movie.getUrl() + "?api_key=" + TMDB_API_KEY + "&append_to_response=release_dates");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                int runtime = jsonObj.optInt("runtime", 0);
                
                String certification = "";
                if (jsonObj.has("release_dates")) {
                    JSONArray results = jsonObj.getJSONObject("release_dates").getJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject rObj = results.getJSONObject(i);
                        if (rObj.optString("iso_3166_1").equals("US")) {
                            JSONArray dates = rObj.getJSONArray("release_dates");
                            if (dates.length() > 0) {
                                certification = dates.getJSONObject(0).optString("certification", "");
                            }
                            break;
                        }
                    }
                }
                
                final String finalRuntime = runtime > 0 ? (runtime / 60) + "h " + (runtime % 60) + "m" : "";
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
