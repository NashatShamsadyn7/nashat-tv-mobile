package com.nashat.tv;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nashat.tv.tmdb.Tmdb;
import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Details;
import com.nashat.tv.tmdb.TmdbModels.Episode;
import com.nashat.tv.tmdb.TmdbModels.Genre;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.tmdb.TmdbModels.Season;
import com.nashat.tv.tmdb.TmdbModels.Title;
import com.nashat.tv.tmdb.TmdbModels.Video;
import com.nashat.tv.ui.CastAdapter;
import com.nashat.tv.ui.EpisodeAdapter;
import com.nashat.tv.ui.PosterAdapter;
import com.nashat.tv.ui.chart.BarChart;
import com.nashat.tv.ui.chart.MeterBar;
import com.nashat.tv.ui.chart.RatingRing;
import com.nashat.tv.ui.chart.StepBar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Details for one film or series, ported from the desktop client's
 * {@code pages/Details.tsx}.
 *
 * <p>One Activity covers both, replacing {@code MovieDetailsActivity} and
 * {@code SeriesDetailsActivity} — two screens that shared roughly eighty per
 * cent of their code and had drifted apart in the other twenty.
 */
public class DetailsActivity extends BaseActivity {

    public static final String EXTRA_TMDB_ID = "tmdb_id";
    public static final String EXTRA_KIND = "kind";

    private int tmdbId;
    private Kind kind;
    private Details details;

    private int selectedSeason = 1;

    private View scroll, errorState, episodesSection, castSection, recommendationsSection;
    private ProgressBar loading, episodesLoading;
    private ImageView backdrop, poster;
    private TextView title, meta, overview, tagline, kindBadge, scoreVotes;
    private LinearLayout genres, statCharts;
    private GridLayout statGrid;
    private RatingRing scoreRing;
    private StepBar scoreSteps;
    private MaterialButton btnPlay, btnTrailer, btnFavorite, btnWatchlist, btnSeason;

    private EpisodeAdapter episodeAdapter;
    private CastAdapter castAdapter;
    private PosterAdapter recommendationsAdapter;

    private FavoritesMoviesManager favorites;
    private WatchlistManager watchlist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        tmdbId = getIntent().getIntExtra(EXTRA_TMDB_ID, 0);
        kind = Kind.fromPath(getIntent().getStringExtra(EXTRA_KIND));

        favorites = new FavoritesMoviesManager(this);
        watchlist = new WatchlistManager(this);

        bindViews();

        if (tmdbId <= 0) {
            showError();
            return;
        }
        load();
    }

    private void bindViews() {
        scroll = findViewById(R.id.detailsScroll);
        loading = findViewById(R.id.detailsLoading);
        errorState = findViewById(R.id.detailsError);
        backdrop = findViewById(R.id.backdrop);
        poster = findViewById(R.id.poster);
        title = findViewById(R.id.title);
        meta = findViewById(R.id.meta);
        genres = findViewById(R.id.genres);
        overview = findViewById(R.id.overview);
        tagline = findViewById(R.id.tagline);
        kindBadge = findViewById(R.id.kindBadge);
        scoreRing = findViewById(R.id.scoreRing);
        scoreVotes = findViewById(R.id.scoreVotes);
        scoreSteps = findViewById(R.id.scoreSteps);
        statGrid = findViewById(R.id.statGrid);
        statCharts = findViewById(R.id.statCharts);
        ((TextView) findViewById(R.id.castTitle).findViewById(R.id.sectionTitle)).setText(R.string.details_cast);
        ((TextView) findViewById(R.id.recsTitle).findViewById(R.id.sectionTitle)).setText(R.string.details_also_like);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        statGrid.setColumnCount(dm.widthPixels / dm.density >= 600 ? 6 : 3);
        btnPlay = findViewById(R.id.btnPlay);
        btnTrailer = findViewById(R.id.btnTrailer);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnWatchlist = findViewById(R.id.btnWatchlist);
        btnSeason = findViewById(R.id.btnSeason);
        episodesSection = findViewById(R.id.episodesSection);
        episodesLoading = findViewById(R.id.episodesLoading);
        castSection = findViewById(R.id.castSection);
        recommendationsSection = findViewById(R.id.recommendationsSection);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRetry).setOnClickListener(v -> load());

        RecyclerView episodes = findViewById(R.id.episodesList);
        episodes.setLayoutManager(new LinearLayoutManager(this));
        // The list lives inside a NestedScrollView and is never itself
        // scrolled, so it must lay out at full height or only its first item
        // would be reachable.
        episodes.setNestedScrollingEnabled(false);
        episodeAdapter = new EpisodeAdapter(this::playEpisode);
        episodes.setAdapter(episodeAdapter);

        RecyclerView cast = findViewById(R.id.castList);
        cast.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        castAdapter = new CastAdapter(this::openActor);
        cast.setAdapter(castAdapter);

        RecyclerView recs = findViewById(R.id.recommendationsList);
        recs.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        recommendationsAdapter = new PosterAdapter(this::openRelated);
        recs.setAdapter(recommendationsAdapter);
    }

    // ─── Loading ──────────────────────────────────────────────

    private void load() {
        loading.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
        scroll.setVisibility(View.GONE);

        TmdbClient.details(this, kind, tmdbId, LocaleManager.getTmdbLanguage(this),
                new TmdbClient.Callback<Details>() {
                    @Override
                    public void onSuccess(Details data) {
                        if (isFinishing() || isDestroyed()) return;
                        details = data;
                        render();
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        showError();
                    }
                });
    }

    private void showError() {
        loading.setVisibility(View.GONE);
        scroll.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
    }

    // ─── Rendering ────────────────────────────────────────────

    private void render() {
        loading.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);

        title.setText(details.displayTitle());
        meta.setText(buildMeta());
        overview.setText(details.overview == null || details.overview.isEmpty()
                ? getString(R.string.details_no_overview) : details.overview);

        kindBadge.setText(kind == Kind.TV ? R.string.nav_series : R.string.nav_movies);
        if (details.tagline != null && !details.tagline.trim().isEmpty()) {
            tagline.setText(details.tagline.trim());
            tagline.setVisibility(View.VISIBLE);
        } else {
            tagline.setVisibility(View.GONE);
        }
        bindGenrePills();
        bindNumbers();

        Glide.with(this).load(Tmdb.backdropLarge(details.backdropPath))
                .placeholder(R.color.surface_sunken).centerCrop().into(backdrop);
        Glide.with(this).load(Tmdb.posterLarge(details.posterPath))
                .placeholder(R.color.surface_sunken).centerCrop().into(poster);

        btnPlay.setOnClickListener(v -> playMain());
        bindTrailer();
        bindLibraryButtons();
        bindCast();
        bindRecommendations();

        if (kind == Kind.TV && details.numberOfSeasons > 0) {
            bindEpisodes();
        }
    }

    /** Rating · year · runtime or season count, joined with a middle dot. */
    private String buildMeta() {
        List<String> parts = new ArrayList<>();

        if (details.voteAverage > 0) {
            parts.add(String.format(Locale.getDefault(), "★ %.1f", details.voteAverage));
        }
        String year = details.year();
        if (year != null) parts.add(year);

        if (kind == Kind.MOVIE && details.runtime != null && details.runtime > 0) {
            parts.add(formatRuntime(details.runtime));
        } else if (kind == Kind.TV && details.numberOfSeasons > 0) {
            parts.add(getResources().getQuantityString(R.plurals.details_seasons,
                    details.numberOfSeasons, details.numberOfSeasons));
        }
        return android.text.TextUtils.join("  ·  ", parts);
    }

    private String formatRuntime(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) return getString(R.string.details_runtime_minutes, rest);
        if (rest == 0) return getString(R.string.details_runtime_hours, hours);
        return getString(R.string.details_runtime_full, hours, rest);
    }

    private void bindGenrePills() {
        genres.removeAllViews();
        if (details.genres == null) return;
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        for (Genre g : details.genres) {
            if (g == null || g.name == null) continue;
            TextView pill = (TextView) getLayoutInflater().inflate(R.layout.item_pill, genres, false);
            pill.setText(g.name);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) pill.getLayoutParams();
            lp.setMarginEnd(gap);
            genres.addView(pill, lp);
        }
    }

    // ─── By the numbers ───────────────────────────────────────

    /**
     * The stats card: score ring and step bar, a grid of key figures, and
     * one chart chosen by what the title has — runtime for a film, money
     * when TMDB knows both budget and box office, episodes per season for a
     * series.
     */
    private void bindNumbers() {
        scoreRing.setScore(details.voteAverage);
        scoreSteps.setScore(details.voteAverage);
        scoreVotes.setText(details.voteCount > 0
                ? getString(R.string.details_votes_count, compact(details.voteCount))
                : getString(R.string.details_no_votes));

        statGrid.removeAllViews();
        String year = details.year();
        if (year != null) addStat(year, getString(R.string.details_year));
        if (kind == Kind.MOVIE) {
            if (details.runtime != null && details.runtime > 0) {
                addStat(formatRuntime(details.runtime), getString(R.string.details_runtime));
            }
        } else {
            if (details.numberOfSeasons > 0) {
                addStat(String.valueOf(details.numberOfSeasons), getString(R.string.details_seasons_label));
            }
            if (details.numberOfEpisodes > 0) {
                addStat(String.valueOf(details.numberOfEpisodes), getString(R.string.details_episodes_label));
            }
        }
        if (details.voteCount > 0) addStat(compact(details.voteCount), getString(R.string.details_votes));
        if (details.originalLanguage != null && !details.originalLanguage.isEmpty()) {
            String lang = new Locale(details.originalLanguage).getDisplayLanguage();
            addStat(lang.isEmpty() ? details.originalLanguage.toUpperCase(Locale.US) : lang,
                    getString(R.string.details_language));
        }
        if (details.popularity > 0) {
            addStat(String.valueOf(Math.round(details.popularity)), getString(R.string.details_popularity));
        }
        if (details.status != null && !details.status.isEmpty() && statGrid.getChildCount() < 6) {
            addStat(details.status, getString(R.string.details_status));
        }

        statCharts.removeAllViews();
        if (kind == Kind.MOVIE) {
            if (details.runtime != null && details.runtime > 0) {
                addMeter(getString(R.string.details_runtime) + " · " + getString(R.string.details_of_3h),
                        formatRuntime(details.runtime), details.runtime / 180f, 0);
            }
            if (details.budget > 0 && details.revenue > 0) {
                long max = Math.max(details.budget, details.revenue);
                addMeter(getString(R.string.details_budget), money(details.budget),
                        (float) details.budget / max, ContextCompat.getColor(this, R.color.chart_bar));
                addMeter(getString(R.string.details_revenue), money(details.revenue),
                        (float) details.revenue / max, ContextCompat.getColor(this, R.color.score_high));
                addNote(getString(R.string.details_return, String.format(Locale.getDefault(),
                        "%.1f", (double) details.revenue / details.budget)));
            }
        } else {
            addSeasonChart();
        }
    }

    private void addStat(String value, String label) {
        View tile = getLayoutInflater().inflate(R.layout.item_stat_tile, statGrid, false);
        ((TextView) tile.findViewById(R.id.statValue)).setText(value);
        ((TextView) tile.findViewById(R.id.statLabel)).setText(label);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        statGrid.addView(tile, lp);
    }

    private void addMeter(String label, String value, float fraction, int color) {
        View meter = getLayoutInflater().inflate(R.layout.item_meter, statCharts, false);
        ((TextView) meter.findViewById(R.id.meterLabel)).setText(label);
        ((TextView) meter.findViewById(R.id.meterValue)).setText(value);
        MeterBar bar = meter.findViewById(R.id.meterBar);
        bar.setFraction(fraction);
        if (color != 0) bar.setColor(color);
        statCharts.addView(meter);
    }

    private void addNote(String text) {
        TextView note = new TextView(this);
        note.setTextAppearance(R.style.TextAppearance_Nashat_Label_Medium);
        note.setTextColor(ContextCompat.getColor(this, R.color.content_secondary));
        note.setPadding(0, Math.round(12 * getResources().getDisplayMetrics().density), 0, 0);
        note.setText(text);
        statCharts.addView(note);
    }

    private void addSeasonChart() {
        if (details.seasons == null) return;
        List<Float> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (var s : details.seasons) {
            if (s == null || s.seasonNumber < 1 || s.episodeCount <= 0) continue;
            values.add((float) s.episodeCount);
            labels.add("S" + s.seasonNumber);
        }
        if (values.size() < 2) return;
        // Past a dozen seasons the bars get too thin to read; keep the latest.
        int from = Math.max(0, values.size() - 12);
        float[] v = new float[values.size() - from];
        String[] l = new String[v.length];
        String[] top = new String[v.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = values.get(from + i);
            l[i] = labels.get(from + i);
            top[i] = String.valueOf(Math.round(v[i]));
        }
        addNote(getString(R.string.details_episodes_per_season));

        BarChart chart = new BarChart(this);
        chart.setData(v, l, top, -1);
        statCharts.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(130 * getResources().getDisplayMetrics().density)));
    }

    /** 1234 → "1.2K", 2_500_000 → "2.5M". */
    private static String compact(long n) {
        if (n >= 1_000_000) return String.format(Locale.getDefault(), "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.getDefault(), "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private static String money(long dollars) {
        if (dollars >= 1_000_000_000L) return String.format(Locale.US, "$%.2fB", dollars / 1e9);
        if (dollars >= 1_000_000L) return String.format(Locale.US, "$%.0fM", dollars / 1e6);
        return String.format(Locale.US, "$%,d", dollars);
    }

    // ─── Trailer ──────────────────────────────────────────────

    private void bindTrailer() {
        Video trailer = pickTrailer();
        if (trailer == null) {
            btnTrailer.setVisibility(View.GONE);
            return;
        }
        btnTrailer.setVisibility(View.VISIBLE);
        btnTrailer.setOnClickListener(v -> openTrailer(trailer.key));
    }

    /**
     * The best available trailer: an official one, then any trailer, then a
     * teaser, then whatever YouTube video exists. Mirrors the desktop
     * {@code pickTrailer}.
     */
    @Nullable
    private Video pickTrailer() {
        if (details.videos == null || details.videos.results == null) return null;
        Video anyTrailer = null, teaser = null, any = null;
        for (Video v : details.videos.results) {
            if (v == null || v.key == null || !"YouTube".equals(v.site)) continue;
            if (any == null) any = v;
            if ("Trailer".equals(v.type)) {
                if (v.official) return v;
                if (anyTrailer == null) anyTrailer = v;
            } else if ("Teaser".equals(v.type) && teaser == null) {
                teaser = v;
            }
        }
        if (anyTrailer != null) return anyTrailer;
        if (teaser != null) return teaser;
        return any;
    }

    /**
     * Hands the trailer to whatever app the user has for YouTube.
     *
     * <p>Not played in the app's own WebView deliberately: YouTube's embed
     * refuses to play in many WebView configurations, and the result would be
     * an apparently broken button. The installed app plays it every time.
     */
    private void openTrailer(String key) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=" + key));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    // ─── Library ──────────────────────────────────────────────

    private void bindLibraryButtons() {
        refreshLibraryState();

        btnFavorite.setOnClickListener(v -> {
            Movie m = asMovie();
            if (favorites.isFavorite(m)) favorites.removeMovie(m);
            else favorites.addMovie(m);
            refreshLibraryState();
        });

        btnWatchlist.setOnClickListener(v -> {
            Movie m = asMovie();
            if (watchlist.isInWatchlist(m)) watchlist.removeItem(m);
            else watchlist.addItem(m);
            refreshLibraryState();
        });
    }

    /**
     * Reflects both toggles in one place.
     *
     * <p>State is carried by tint rather than by a different icon: swapping the
     * glyph moves the button's optical weight and makes the row jump. Colour
     * changes nothing about the layout.
     */
    private void refreshLibraryState() {
        Movie m = asMovie();

        boolean fav = favorites.isFavorite(m);
        btnFavorite.setIconTint(ContextCompat.getColorStateList(this,
                fav ? R.color.status_live_fill : R.color.content_primary));
        btnFavorite.setContentDescription(getString(
                fav ? R.string.action_remove_favorite : R.string.action_add_favorite));

        boolean listed = watchlist.isInWatchlist(m);
        btnWatchlist.setIcon(ContextCompat.getDrawable(this,
                listed ? R.drawable.ic_playlist_add_check : R.drawable.ic_playlist_add));
        btnWatchlist.setIconTint(ContextCompat.getColorStateList(this,
                listed ? R.color.accent_text : R.color.content_primary));
        btnWatchlist.setContentDescription(getString(
                listed ? R.string.action_remove_watchlist : R.string.action_add_watchlist));
    }

    /**
     * Adapts the TMDB payload to the {@link Movie} the library managers store.
     *
     * <p>{@code Movie.url} carries the TMDB id — that is how the existing
     * favourites, watchlist and continue-watching stores identify an entry, so
     * it is kept rather than migrated. Changing it would orphan everything
     * users have already saved.
     */
    private Movie asMovie() {
        return new Movie(
                details == null ? "" : details.displayTitle(),
                details == null ? null : Tmdb.poster(details.posterPath),
                kind == Kind.TV ? "series" : "movie",
                String.valueOf(tmdbId),
                details == null ? "" : details.overview,
                details == null ? null : details.year(),
                details == null ? 0 : details.voteAverage);
    }

    // ─── Cast & recommendations ───────────────────────────────

    private void bindCast() {
        if (details.credits == null || details.credits.cast == null
                || details.credits.cast.isEmpty()) {
            castSection.setVisibility(View.GONE);
            return;
        }
        castSection.setVisibility(View.VISIBLE);
        castAdapter.submit(details.credits.cast);
    }

    private void bindRecommendations() {
        List<Title> recs = details.recommendations == null
                ? null : details.recommendations.results;
        List<Title> clean = TmdbClient.withArtwork(recs);
        if (clean.isEmpty()) {
            recommendationsSection.setVisibility(View.GONE);
            return;
        }
        recommendationsSection.setVisibility(View.VISIBLE);
        recommendationsAdapter.submit(clean);
    }

    // ─── Episodes ─────────────────────────────────────────────

    private void bindEpisodes() {
        episodesSection.setVisibility(View.VISIBLE);
        selectedSeason = firstRealSeason();
        updateSeasonButton();

        btnSeason.setOnClickListener(v -> showSeasonPicker());
        loadSeason(selectedSeason);
    }

    /**
     * The first season worth opening on.
     *
     * <p>TMDB files specials as season 0. Landing a viewer on a bucket of
     * behind-the-scenes clips when they opened a series to watch it is wrong,
     * so season 0 is never the default — though it stays pickable.
     */
    private int firstRealSeason() {
        if (details.seasons == null) return 1;
        for (var s : details.seasons) {
            if (s != null && s.seasonNumber >= 1 && s.episodeCount > 0) return s.seasonNumber;
        }
        return 1;
    }

    private void updateSeasonButton() {
        btnSeason.setText(getString(R.string.details_season_number, selectedSeason));
    }

    private void showSeasonPicker() {
        if (details.seasons == null || details.seasons.isEmpty()) return;

        List<Integer> numbers = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (var s : details.seasons) {
            if (s == null || s.episodeCount <= 0) continue;
            numbers.add(s.seasonNumber);
            labels.add(s.seasonNumber == 0
                    ? getString(R.string.details_specials)
                    : getString(R.string.details_season_number, s.seasonNumber));
        }
        if (numbers.isEmpty()) return;

        int checked = numbers.indexOf(selectedSeason);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.details_choose_season)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    selectedSeason = numbers.get(which);
                    updateSeasonButton();
                    loadSeason(selectedSeason);
                    d.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.show();
    }

    private void loadSeason(int seasonNumber) {
        episodesLoading.setVisibility(View.VISIBLE);
        episodeAdapter.submit(null);

        TmdbClient.season(this, tmdbId, seasonNumber, LocaleManager.getTmdbLanguage(this),
                new TmdbClient.Callback<Season>() {
                    @Override
                    public void onSuccess(Season data) {
                        if (isFinishing() || isDestroyed()) return;
                        // A season change while this was in flight means the
                        // result is for a season nobody is looking at.
                        if (data.seasonNumber != selectedSeason) return;
                        episodesLoading.setVisibility(View.GONE);
                        episodeAdapter.submit(data.episodes);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        episodesLoading.setVisibility(View.GONE);
                    }
                });
    }

    // ─── Playback ─────────────────────────────────────────────

    private void playMain() {
        if (kind == Kind.MOVIE) {
            openPlayer(1, 1);
        } else {
            openPlayer(selectedSeason, 1);
        }
    }

    private void playEpisode(Episode episode) {
        openPlayer(episode.seasonNumber > 0 ? episode.seasonNumber : selectedSeason,
                episode.episodeNumber);
    }

    private void openPlayer(int season, int episode) {
        Intent intent = new Intent(this, WebPlayerActivity.class);
        intent.putExtra(WebPlayerActivity.EXTRA_TMDB_ID, tmdbId);
        intent.putExtra(WebPlayerActivity.EXTRA_KIND, kind.path);
        intent.putExtra(WebPlayerActivity.EXTRA_TITLE, details.displayTitle());
        intent.putExtra(WebPlayerActivity.EXTRA_SEASON, season);
        intent.putExtra(WebPlayerActivity.EXTRA_EPISODE, episode);
        startActivity(intent);

        recordContinueWatching(season, episode);
    }

    private void recordContinueWatching(int season, int episode) {
        try {
            ContinueWatchingManager manager = new ContinueWatchingManager(this);
            if (kind == Kind.TV) {
                manager.addSeries(asMovie(), season, episode);
            } else {
                manager.addMovie(asMovie());
            }
        } catch (Exception ignored) {
            // Continue Watching is a convenience. Failing to record must never
            // stop the film from opening.
        }
    }

    // ─── Navigation out ───────────────────────────────────────

    private void openActor(com.nashat.tv.tmdb.TmdbModels.CastMember member) {
        Intent intent = new Intent(this, ActorDetailsActivity.class);
        intent.putExtra("actor_id", member.id);
        intent.putExtra("actor_name", member.name);
        startActivity(intent);
    }

    private void openRelated(Title related) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(EXTRA_TMDB_ID, related.id);
        // A recommendation for a film can be a series and vice versa, so the
        // item's own kind is used here rather than this screen's.
        intent.putExtra(EXTRA_KIND, related.kind().path);
        startActivity(intent);
    }
}
