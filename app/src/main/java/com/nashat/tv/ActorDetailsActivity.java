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
                java.util.Map<Movie, Integer> votes = new java.util.HashMap<>();

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
                    votes.put(m, obj.optInt("vote_count", 0));
                }

                // Sort by popularity or vote_count could be done here, we'll just take the top 20
                // Most-voted first: that is what an actor is known for. Sorting
                // by score put obscure titles with a single 10/10 vote on top.
                works.sort((m1, m2) -> Integer.compare(votes.get(m2), votes.get(m1)));
                final List<Movie> all = new ArrayList<>(works);
                final java.util.Map<Movie, Integer> allVotes = votes;

                handler.post(() -> {
                    knownForList.addAll(works.subList(0, Math.min(20, works.size())));
                    knownForAdapter.notifyDataSetChanged();
                    progressKnownFor.setVisibility(View.GONE);
                    bindCareer(all, allVotes);
                });

            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> progressKnownFor.setVisibility(View.GONE));
            }
        });
    }

    /**
     * Career at a glance from the credit list: how many, how well received
     * (average of titles with at least 20 votes, so festival shorts do not
     * skew it), the span of years, and credits per decade.
     */
    private void bindCareer(List<Movie> works, java.util.Map<Movie, Integer> votes) {
        if (works.isEmpty()) return;
        int first = Integer.MAX_VALUE, last = 0;
        double scoreSum = 0;
        int scored = 0;
        java.util.TreeMap<Integer, Integer> decades = new java.util.TreeMap<>();
        for (Movie m : works) {
            String y = m.getYear();
            if (y != null && y.length() == 4) {
                try {
                    int year = Integer.parseInt(y);
                    first = Math.min(first, year);
                    last = Math.max(last, year);
                    int decade = year / 10 * 10;
                    Integer n = decades.get(decade);
                    decades.put(decade, n == null ? 1 : n + 1);
                } catch (NumberFormatException ignored) {
                }
            }
            Integer v = votes.get(m);
            if (v != null && v >= 20 && m.getVoteAverage() > 0) {
                scoreSum += m.getVoteAverage();
                scored++;
            }
        }
        findViewById(R.id.careerCard).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.careerCredits)).setText(String.valueOf(works.size()));
        ((TextView) findViewById(R.id.careerScore)).setText(scored == 0 ? "–"
                : String.format(java.util.Locale.getDefault(), "%.1f", scoreSum / scored));
        ((TextView) findViewById(R.id.careerYears)).setText(first == Integer.MAX_VALUE ? "–"
                : (first == last ? String.valueOf(first) : first + "–" + last));

        com.nashat.tv.ui.chart.BarChart chart = findViewById(R.id.careerDecades);
        if (decades.size() < 2) {
            chart.setVisibility(View.GONE);
            return;
        }
        float[] values = new float[decades.size()];
        String[] labels = new String[decades.size()];
        String[] tops = new String[decades.size()];
        int i = 0, peak = 0;
        for (java.util.Map.Entry<Integer, Integer> e : decades.entrySet()) {
            values[i] = e.getValue();
            labels[i] = (e.getKey() % 100 < 10 ? "0" : "") + (e.getKey() % 100) + "s";
            tops[i] = String.valueOf(e.getValue());
            if (values[i] > values[peak]) peak = i;
            i++;
        }
        chart.setData(values, labels, tops, peak);
    }
}
