package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.google.android.material.imageview.ShapeableImageView;

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

public class ActorDetailsActivity extends BaseActivity {

    private String actorId;
    private String actorName;
    private static final String TMDB_API_KEY = "4e44d9029b1270a757cddc766a1bcb63";

    private ShapeableImageView imgActorPoster;
    private TextView txtActorNameFull;
    private TextView txtBirthInfo;
    private TextView txtPlaceOfBirth;
    private TextView txtBiography;
    
    private RecyclerView recyclerKnownFor;
    private ProgressBar progressKnownFor;
    private HorizontalMovieAdapter knownForAdapter;
    private List<Movie> knownForList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actor_details);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        actorId = getIntent().getStringExtra("actor_id");
        actorName = getIntent().getStringExtra("actor_name");

        imgActorPoster = findViewById(R.id.imgActorPoster);
        txtActorNameFull = findViewById(R.id.txtActorNameFull);
        txtBirthInfo = findViewById(R.id.txtBirthInfo);
        txtPlaceOfBirth = findViewById(R.id.txtPlaceOfBirth);
        txtBiography = findViewById(R.id.txtBiography);
        
        recyclerKnownFor = findViewById(R.id.recyclerKnownFor);
        progressKnownFor = findViewById(R.id.progressKnownFor);
        
        txtActorNameFull.setText(actorName != null ? actorName : "Unknown");

        recyclerKnownFor.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        knownForAdapter = new HorizontalMovieAdapter(knownForList, movie -> {
            // Movie.url carries the TMDB id — that is how this app's stores have
            // always identified a title, and DetailsActivity re-fetches from it.
            int id;
            try {
                id = Integer.parseInt(movie.getUrl());
            } catch (NumberFormatException e) {
                return;
            }
            boolean isSeries = movie.getCategory() != null
                    && movie.getCategory().toLowerCase(java.util.Locale.US).contains("series");
            Intent intent = new Intent(this, DetailsActivity.class);
            intent.putExtra(DetailsActivity.EXTRA_TMDB_ID, id);
            intent.putExtra(DetailsActivity.EXTRA_KIND, isSeries ? "tv" : "movie");
            startActivity(intent);
        });
        recyclerKnownFor.setAdapter(knownForAdapter);

        fetchActorDetails();
        fetchKnownFor();
    }

    private void fetchActorDetails() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        
        String langUrl = "?api_key=" + TMDB_API_KEY + "&language=ar";

        executor.execute(() -> {
            try {
                URL url = new URL("https://api.themoviedb.org/3/person/" + actorId + langUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject obj = new JSONObject(response.toString());

                String bio = obj.optString("biography", "No biography available.");
                String bday = obj.optString("birthday", "");
                String place = obj.optString("place_of_birth", "");
                String profilePath = obj.optString("profile_path", "");

                handler.post(() -> {
                    if (bio.isEmpty()) txtBiography.setText("لا توجد نبذة شخصية.");
                    else txtBiography.setText(bio);
                    
                    if (!bday.isEmpty()) txtBirthInfo.setText("تاريخ الميلاد: " + bday);
                    else txtBirthInfo.setVisibility(View.GONE);
                    
                    if (!place.isEmpty()) txtPlaceOfBirth.setText(place);
                    else txtPlaceOfBirth.setVisibility(View.GONE);

                    if (!profilePath.isEmpty() && !profilePath.equals("null")) {
                        Glide.with(this)
                                .load("https://image.tmdb.org/t/p/w500" + profilePath)
                                .transform(new CenterCrop())
                                .into(imgActorPoster);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void fetchKnownFor() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        
        String langUrl = "?api_key=" + TMDB_API_KEY + "&language=ar";

        executor.execute(() -> {
            try {
                URL url = new URL("https://api.themoviedb.org/3/person/" + actorId + "/combined_credits" + langUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject jsonObj = new JSONObject(response.toString());
                JSONArray results = jsonObj.getJSONArray("cast");
                List<Movie> works = new ArrayList<>();

                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String id = String.valueOf(obj.getInt("id"));
                    
                    String mediaType = obj.optString("media_type", "movie");
                    boolean isTv = mediaType.equals("tv");
                    
                    String title = isTv ? obj.optString("name", obj.optString("original_name")) 
                                        : obj.optString("title", obj.optString("original_title"));
                                        
                    String dateField = isTv ? "first_air_date" : "release_date";
                    String date = obj.optString(dateField, "");
                    String year = date.length() >= 4 ? date.substring(0, 4) : "";
                    
                    String posterPath = obj.optString("poster_path", "");
                    String posterFull = "https://image.tmdb.org/t/p/w500" + posterPath;
                    double voteAvg = obj.optDouble("vote_average", 0.0);
                    String vote = String.format("%.1f", voteAvg);

                    String category = isTv ? "TV Series" : "Movie";

                    // Note: constructor args are: name, poster, category, url/id, description, year, voteAverage
                    Movie m = new Movie(title, posterFull, category, id, obj.optString("overview", ""), year, voteAvg);
                    works.add(m);
                }

                // Sort by popularity or vote_count could be done here, we'll just take the top 20
                works.sort((m1, m2) -> {
                    // TMDB usually sorts combined_credits roughly by popularity, but let's take a simple approach
                    return Double.compare(m2.getVoteAverage(), m1.getVoteAverage());
                });

                handler.post(() -> {
                    knownForList.addAll(works.subList(0, Math.min(20, works.size())));
                    knownForAdapter.notifyDataSetChanged();
                    progressKnownFor.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> progressKnownFor.setVisibility(View.GONE));
            }
        });
    }
}
