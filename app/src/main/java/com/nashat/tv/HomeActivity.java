package com.nashat.tv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.nashat.tv.ContinueWatchingManager.WatchItem;
import com.nashat.tv.player.PlaybackProgress;
import com.nashat.tv.tmdb.Tmdb;
import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Genre;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.tmdb.TmdbModels.Title;
import com.nashat.tv.ui.BottomNav;
import com.nashat.tv.ui.ContinueWatchingAdapter;
import com.nashat.tv.ui.PosterAdapter;
import com.nashat.tv.ui.RankAdapter;
import com.nashat.tv.ui.WeekCard;
import com.nashat.tv.ui.chart.RatingRing;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The launcher screen: one page that leads into films, series and live TV.
 *
 * <p>Every section is filled independently and hides itself when it has
 * nothing, so a slow or failed request costs one section, never the page.
 */
public class HomeActivity extends BaseActivity {

    private static final int HERO_COUNT = 6;
    private static final long HERO_AUTO_ADVANCE_MS = 6000;
    private static final int LIVE_STRIP_COUNT = 16;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, String> genreNames = new HashMap<>();
    private final List<Title> heroItems = new ArrayList<>();

    private RecyclerView heroPager;
    private LinearLayout heroDots, rowsContainer;
    private HeroAdapter heroAdapter;
    private LinearLayoutManager heroLayout;
    private View weekCard;

    private WatchlistManager watchlist;
    private ContinueWatchingManager continueManager;

    private final Runnable advanceHero = new Runnable() {
        @Override
        public void run() {
            if (heroItems.size() > 1) {
                int next = (currentHero() + 1) % heroItems.size();
                heroPager.smoothScrollToPosition(next);
            }
            handler.postDelayed(this, HERO_AUTO_ADVANCE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        watchlist = new WatchlistManager(this);
        continueManager = new ContinueWatchingManager(this);

        heroPager = findViewById(R.id.heroPager);
        heroDots = findViewById(R.id.heroDots);
        rowsContainer = findViewById(R.id.rowsContainer);
        weekCard = findViewById(R.id.weekCard);

        ((TextView) findViewById(R.id.greeting)).setText(greeting());
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            Intent intent = new Intent(this, MoviesActivity.class);
            intent.putExtra(CatalogActivity.EXTRA_FOCUS_SEARCH, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        findViewById(R.id.liveSeeAll).setOnClickListener(v -> openTab(LiveTvActivity.class));

        setUpHero();
        loadGenres();
        loadHero();
        loadLiveStrip();
        buildRows();

        BottomNav.bind(this, BottomNav.Tab.HOME);
        getWindow().getDecorView().postDelayed(() -> LiveTvValidationWorker.schedule(this), 4000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshContinueRow();
        WeekCard.bind(weekCard);
        handler.postDelayed(advanceHero, HERO_AUTO_ADVANCE_MS);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(advanceHero);
        super.onPause();
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.home_greeting_morning);
        if (hour < 18) return getString(R.string.home_greeting_afternoon);
        return getString(R.string.home_greeting_evening);
    }

    private String language() {
        return LocaleManager.getTmdbLanguage(this);
    }

    // ══════════════════════════════════════════════════════════
    //  Hero
    // ══════════════════════════════════════════════════════════

    private void setUpHero() {
        heroLayout = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        heroPager.setLayoutManager(heroLayout);
        new PagerSnapHelper().attachToRecyclerView(heroPager);
        heroAdapter = new HeroAdapter();
        heroPager.setAdapter(heroAdapter);
        heroPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) updateDots();
                if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // The viewer took over; restart the timer from now.
                    handler.removeCallbacks(advanceHero);
                    handler.postDelayed(advanceHero, HERO_AUTO_ADVANCE_MS);
                }
            }
        });
    }

    private int currentHero() {
        int first = heroLayout.findFirstCompletelyVisibleItemPosition();
        return first == RecyclerView.NO_POSITION ? heroLayout.findFirstVisibleItemPosition() : first;
    }

    private void loadHero() {
        final List<Title> films = new ArrayList<>();
        final List<Title> series = new ArrayList<>();
        final int[] pending = {2};

        TmdbClient.trending(this, Kind.MOVIE, language(), new TmdbClient.Callback<List<Title>>() {
            @Override public void onSuccess(List<Title> data) {
                for (Title t : TmdbClient.withArtwork(data)) { t.mediaType = "movie"; films.add(t); }
                if (--pending[0] == 0) showHero(films, series);
            }
            @Override public void onError(String message) {
                if (--pending[0] == 0) showHero(films, series);
            }
        });
        TmdbClient.trending(this, Kind.TV, language(), new TmdbClient.Callback<List<Title>>() {
            @Override public void onSuccess(List<Title> data) {
                for (Title t : TmdbClient.withArtwork(data)) { t.mediaType = "tv"; series.add(t); }
                if (--pending[0] == 0) showHero(films, series);
            }
            @Override public void onError(String message) {
                if (--pending[0] == 0) showHero(films, series);
            }
        });
    }

    /** Interleaves films and series so the hero shows both sides of the app. */
    private void showHero(List<Title> films, List<Title> series) {
        if (isFinishing() || isDestroyed()) return;
        heroItems.clear();
        for (int i = 0; heroItems.size() < HERO_COUNT && (i < films.size() || i < series.size()); i++) {
            if (i < films.size()) heroItems.add(films.get(i));
            if (i < series.size() && heroItems.size() < HERO_COUNT) heroItems.add(series.get(i));
        }
        findViewById(R.id.heroSection).setVisibility(heroItems.isEmpty() ? View.GONE : View.VISIBLE);
        heroAdapter.notifyDataSetChanged();
        buildDots();
    }

    private void buildDots() {
        heroDots.removeAllViews();
        int gap = Math.round(3 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < heroItems.size(); i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_hero_dot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(gap, 0, gap, 0);
            heroDots.addView(dot, lp);
        }
        updateDots();
    }

    private void updateDots() {
        int current = Math.max(0, currentHero());
        for (int i = 0; i < heroDots.getChildCount(); i++) {
            heroDots.getChildAt(i).setSelected(i == current);
        }
    }

    /** Hero size for this screen: tall poster art on phones, wide backdrop on tablets. */
    private int[] heroSize() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        int gutter = getResources().getDimensionPixelSize(R.dimen.screen_gutter);
        int width = m.widthPixels - 2 * gutter;
        boolean wide = m.widthPixels / m.density >= 600;
        int height = wide
                ? Math.round(width * 0.48f)
                : Math.min(Math.round(width * 1.32f), Math.round(m.heightPixels * 0.62f));
        return new int[]{width, height, wide ? 1 : 0};
    }

    private String heroMeta(Title t) {
        List<String> parts = new ArrayList<>();
        String year = t.year();
        if (year != null) parts.add(year);
        parts.add(getString(t.kind() == Kind.TV ? R.string.nav_series : R.string.nav_movies));
        if (t.genreIds != null) {
            for (Integer id : t.genreIds) {
                String name = genreNames.get(id);
                if (name != null) parts.add(name);
                if (parts.size() >= 4) break;
            }
        }
        return android.text.TextUtils.join("  ·  ", parts);
    }

    private class HeroAdapter extends RecyclerView.Adapter<HeroAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hero, parent, false);
            int[] size = heroSize();
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.width = size[0] - lp.getMarginStart() - lp.getMarginEnd();
            lp.height = size[1];
            v.setLayoutParams(lp);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Title t = heroItems.get(position);
            boolean wide = heroSize()[2] == 1;
            String art = wide || t.posterPath == null
                    ? Tmdb.backdropLarge(t.backdropPath) : Tmdb.posterLarge(t.posterPath);
            Glide.with(h.image).load(art).placeholder(R.color.surface_sunken).centerCrop().into(h.image);

            int rank = 1 + countBefore(position, t.kind());
            h.eyebrow.setText(getString(t.kind() == Kind.TV
                    ? R.string.home_hero_series : R.string.home_hero_film, rank));
            h.title.setText(t.displayTitle());
            h.ring.setFilled(true);
            h.ring.setScore(t.voteAverage);
            h.meta.setText(heroMeta(t));

            h.info.setOnClickListener(v -> openDetails(t));
            h.itemView.setOnClickListener(v -> openDetails(t));
            bindListButton(h.list, t);
        }

        private int countBefore(int position, Kind kind) {
            int n = 0;
            for (int i = 0; i < position; i++) if (heroItems.get(i).kind() == kind) n++;
            return n;
        }

        @Override
        public int getItemCount() {
            return heroItems.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView eyebrow, title, meta, info, list;
            final RatingRing ring;

            Holder(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.heroImage);
                eyebrow = v.findViewById(R.id.heroEyebrow);
                title = v.findViewById(R.id.heroTitle);
                meta = v.findViewById(R.id.heroMeta);
                info = v.findViewById(R.id.heroInfo);
                list = v.findViewById(R.id.heroList);
                ring = v.findViewById(R.id.heroRing);
            }
        }
    }

    private void bindListButton(TextView button, Title t) {
        Movie m = asMovie(t);
        boolean listed = watchlist.isInWatchlist(m);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                listed ? R.drawable.ic_playlist_add_check : R.drawable.ic_playlist_add, 0, 0, 0);
        button.setOnClickListener(v -> {
            if (watchlist.isInWatchlist(m)) watchlist.removeItem(m);
            else {
                watchlist.addItem(m);
                Toast.makeText(this, R.string.action_add_watchlist, Toast.LENGTH_SHORT).show();
            }
            bindListButton(button, t);
        });
    }

    private Movie asMovie(Title t) {
        return new Movie(t.displayTitle(), Tmdb.poster(t.posterPath),
                t.kind() == Kind.TV ? "series" : "movie", String.valueOf(t.id),
                t.overview, t.year(), t.voteAverage);
    }

    private void loadGenres() {
        for (Kind kind : Kind.values()) {
            TmdbClient.genres(this, kind, language(), new TmdbClient.Callback<List<Genre>>() {
                @Override public void onSuccess(List<Genre> data) {
                    for (Genre g : data) if (g != null && g.name != null) genreNames.put(g.id, g.name);
                    if (!isFinishing() && !isDestroyed()) heroAdapter.notifyDataSetChanged();
                }
                @Override public void onError(String message) { }
            });
        }
    }

    // ══════════════════════════════════════════════════════════
    //  On air now
    // ══════════════════════════════════════════════════════════

    private void loadLiveStrip() {
        List<Channel> cached = LiveTvChannelCache.get(this);
        if (!cached.isEmpty()) {
            showLiveStrip(cached);
            return;
        }
        new LiveTvChannelRepository(this).loadChannelsOnce(new LiveTvChannelRepository.LoadCallback() {
            @Override
            public void onLoaded(List<Channel> channels, String sourcePath) {
                LiveTvChannelCache.save(HomeActivity.this, channels);
                showLiveStrip(channels);
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void showLiveStrip(List<Channel> source) {
        if (isFinishing() || isDestroyed()) return;
        List<Channel> playable = new ArrayList<>();
        for (Channel c : source) if (c != null && c.hasStream()) playable.add(c);
        if (playable.isEmpty()) return;

        // Same order as the Live TV tab: the curated database order first.
        Collections.sort(playable, (a, b) -> {
            int ia = a.getDbIndex(), ib = b.getDbIndex();
            if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
            return ia >= 0 ? -1 : ib >= 0 ? 1 : 0;
        });

        findViewById(R.id.liveSection).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.liveCount)).setText(
                getString(R.string.home_channels_count, playable.size()));

        RecyclerView list = findViewById(R.id.liveList);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        LiveTvAdapter adapter = new LiveTvAdapter(this, true);
        list.setAdapter(adapter);
        adapter.submitList(playable.subList(0, Math.min(LIVE_STRIP_COUNT, playable.size())));
    }

    // ══════════════════════════════════════════════════════════
    //  Rows
    // ══════════════════════════════════════════════════════════

    private void buildRows() {
        rowsContainer.removeAllViews();
        addRankRow(R.string.home_top10_films, Kind.MOVIE);
        addPosterRow(R.string.home_in_cinemas, cb -> TmdbClient.nowPlaying(this, language(), cb), Kind.MOVIE);
        addRankRow(R.string.home_top10_series, Kind.TV);
        addPosterRow(R.string.home_airing_today, cb -> TmdbClient.airingToday(this, language(), cb), Kind.TV);
    }

    private interface Loader {
        void load(TmdbClient.Callback<List<Title>> cb);
    }

    private View inflateRow(@StringRes int title) {
        View row = LayoutInflater.from(this).inflate(R.layout.view_catalog_row, rowsContainer, false);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        return row;
    }

    private void addRankRow(@StringRes int title, Kind kind) {
        View row = inflateRow(title);
        RecyclerView list = row.findViewById(R.id.rowList);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        RankAdapter adapter = new RankAdapter(t -> {
            t.mediaType = kind.path;
            openDetails(t);
        });
        list.setAdapter(adapter);
        bindSeeAll(row, kind);
        rowsContainer.addView(row);

        TmdbClient.trending(this, kind, language(), rowCallback(row, data -> adapter.submit(data)));
    }

    private void addPosterRow(@StringRes int title, Loader loader, Kind kind) {
        View row = inflateRow(title);
        RecyclerView list = row.findViewById(R.id.rowList);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        PosterAdapter adapter = new PosterAdapter(t -> {
            t.mediaType = kind.path;
            openDetails(t);
        });
        list.setAdapter(adapter);
        rowsContainer.addView(row);
        loader.load(rowCallback(row, adapter::submit));
    }

    private void bindSeeAll(View row, Kind kind) {
        TextView action = row.findViewById(R.id.rowAction);
        action.setText(R.string.action_see_all);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> openTab(kind == Kind.TV ? SeriesActivity.class : MoviesActivity.class));
    }

    private interface Sink {
        void accept(List<Title> titles);
    }

    private TmdbClient.Callback<List<Title>> rowCallback(View row, Sink sink) {
        View loading = row.findViewById(R.id.rowLoading);
        View list = row.findViewById(R.id.rowList);
        list.setVisibility(View.GONE);
        return new TmdbClient.Callback<List<Title>>() {
            @Override
            public void onSuccess(List<Title> data) {
                if (isFinishing() || isDestroyed()) return;
                List<Title> clean = TmdbClient.withArtwork(data);
                loading.setVisibility(View.GONE);
                if (clean.isEmpty()) {
                    row.setVisibility(View.GONE);
                    return;
                }
                sink.accept(clean);
                list.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                row.setVisibility(View.GONE);
            }
        };
    }

    /** Continue watching, films and series together, newest first. */
    private void refreshContinueRow() {
        View existing = rowsContainer.findViewWithTag("continue");
        if (existing != null) rowsContainer.removeView(existing);

        List<WatchItem> items = new ArrayList<>();
        for (WatchItem i : continueManager.getItems()) {
            if (i == null || i.tmdbId == null) continue;
            try {
                Integer.parseInt(i.tmdbId);
                items.add(i);
            } catch (NumberFormatException ignored) {
                // Live TV entries are keyed by URL and cannot open details.
            }
        }
        if (items.isEmpty()) return;

        View row = inflateRow(R.string.row_continue);
        row.setTag("continue");
        row.findViewById(R.id.rowLoading).setVisibility(View.GONE);
        RecyclerView list = row.findViewById(R.id.rowList);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        ContinueWatchingAdapter adapter = new ContinueWatchingAdapter(new PlaybackProgress(this),
                new ContinueWatchingAdapter.Listener() {
                    @Override
                    public void onResume(WatchItem item) {
                        Intent intent = new Intent(HomeActivity.this, DetailsActivity.class);
                        intent.putExtra(DetailsActivity.EXTRA_TMDB_ID, Integer.parseInt(item.tmdbId));
                        intent.putExtra(DetailsActivity.EXTRA_KIND, item.isSeries ? "tv" : "movie");
                        startActivity(intent);
                    }

                    @Override
                    public void onRemove(WatchItem item) {
                        continueManager.removeItem(item.tmdbId);
                        refreshContinueRow();
                    }
                });
        adapter.submit(items);
        list.setAdapter(adapter);
        rowsContainer.addView(row, 0);
    }

    // ══════════════════════════════════════════════════════════
    //  Navigation
    // ══════════════════════════════════════════════════════════

    private void openDetails(Title t) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_TMDB_ID, t.id);
        intent.putExtra(DetailsActivity.EXTRA_KIND, t.kind().path);
        startActivity(intent);
    }

    private void openTab(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
}
