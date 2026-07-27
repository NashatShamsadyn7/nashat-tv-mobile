package com.nashat.tv.player;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Details;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.tmdb.TmdbModels.Season;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the previous and next episode of a series, across season boundaries.
 * A port of the desktop client's {@code features/player/useEpisodeNav.ts}.
 *
 * <p>TMDB has no "next episode" endpoint. Deciding whether E+1 exists — or
 * whether the next episode is actually S+1 E1 — requires knowing how many
 * episodes the current season holds, which only the season payload carries.
 * This class fetches that and caches it.
 *
 * <p>Season 0 (TMDB's bucket for specials and behind-the-scenes material) is
 * never navigated into: it is not part of the running order, and rolling a
 * viewer from the season finale into a blooper reel is not "next episode".
 */
public final class EpisodeNav {

    /** One point in a series. */
    public static final class Ref {
        public final int season;
        public final int episode;

        public Ref(int season, int episode) {
            this.season = season;
            this.episode = episode;
        }
    }

    public static final class Nav {
        @Nullable public final Ref previous;
        @Nullable public final Ref next;

        Nav(@Nullable Ref previous, @Nullable Ref next) {
            this.previous = previous;
            this.next = next;
        }
    }

    public interface Callback {
        @MainThread
        void onResolved(Nav nav);
    }

    /**
     * Episode counts, keyed {@code tmdbId:season}, and total season counts.
     * Series structure does not change while the app is open, so these persist
     * for the process — which is what keeps binge-watching from re-fetching the
     * same season payload on every single episode change.
     */
    private static final Map<String, Integer> SEASON_SIZES = new HashMap<>();
    private static final Map<Integer, Integer> SEASON_COUNTS = new HashMap<>();

    /**
     * Works out the neighbours of one episode and reports them on the main
     * thread.
     *
     * <p>A lookup failure is never surfaced as an error. It resolves to "no
     * navigation offered" instead: the user came here to watch an episode, and
     * failing to describe its neighbours is no reason to interrupt that.
     */
    public static void resolve(Context ctx, int tvId, int season, int episode,
                               String language, Callback cb) {
        if (tvId <= 0 || season <= 0 || episode <= 0) {
            cb.onResolved(new Nav(null, null));
            return;
        }

        episodeCount(ctx, tvId, season, language, currentSize -> {
            if (currentSize == null) {
                cb.onResolved(new Nav(null, null));
                return;
            }
            seasonCount(ctx, tvId, language, totalSeasons -> {
                int seasons = totalSeasons == null ? 1 : totalSeasons;
                resolvePrevious(ctx, tvId, season, episode, language, previous ->
                        resolveNext(ctx, tvId, season, episode, currentSize, seasons, language,
                                next -> cb.onResolved(new Nav(previous, next))));
            });
        });
    }

    private interface IntSink {
        void accept(@Nullable Integer value);
    }

    private interface RefSink {
        void accept(@Nullable Ref ref);
    }

    private static void resolveNext(Context ctx, int tvId, int season, int episode,
                                    int currentSize, int totalSeasons, String language,
                                    RefSink sink) {
        if (episode < currentSize) {
            sink.accept(new Ref(season, episode + 1));
            return;
        }
        if (season >= totalSeasons) {
            sink.accept(null);
            return;
        }
        // End of the season — roll into the next one, but only if it actually
        // has episodes. TMDB lists announced-but-unaired seasons with zero.
        episodeCount(ctx, tvId, season + 1, language, size ->
                sink.accept(size != null && size > 0 ? new Ref(season + 1, 1) : null));
    }

    private static void resolvePrevious(Context ctx, int tvId, int season, int episode,
                                        String language, RefSink sink) {
        if (episode > 1) {
            sink.accept(new Ref(season, episode - 1));
            return;
        }
        if (season <= 1) {
            sink.accept(null);
            return;
        }
        // First episode of a season steps back to the *last* episode of the
        // previous one, which means its length has to be known first.
        episodeCount(ctx, tvId, season - 1, language, size ->
                sink.accept(size != null && size > 0 ? new Ref(season - 1, size) : null));
    }

    private static void episodeCount(Context ctx, int tvId, int season, String language,
                                     IntSink sink) {
        String key = tvId + ":" + season;
        Integer cached = SEASON_SIZES.get(key);
        if (cached != null) {
            sink.accept(cached);
            return;
        }
        TmdbClient.season(ctx, tvId, season, language, new TmdbClient.Callback<Season>() {
            @Override
            public void onSuccess(Season data) {
                int count = data.episodes == null ? 0 : data.episodes.size();
                SEASON_SIZES.put(key, count);
                sink.accept(count);
            }

            @Override
            public void onError(String message) {
                sink.accept(null);
            }
        });
    }

    private static void seasonCount(Context ctx, int tvId, String language, IntSink sink) {
        Integer cached = SEASON_COUNTS.get(tvId);
        if (cached != null) {
            sink.accept(cached);
            return;
        }
        TmdbClient.details(ctx, Kind.TV, tvId, language, new TmdbClient.Callback<Details>() {
            @Override
            public void onSuccess(Details data) {
                int count = Math.max(1, data.numberOfSeasons);
                SEASON_COUNTS.put(tvId, count);
                sink.accept(count);
            }

            @Override
            public void onError(String message) {
                sink.accept(null);
            }
        });
    }

    private EpisodeNav() {}
}
