package com.nashat.tv.player;

import android.content.Context;
import android.content.SharedPreferences;

import com.nashat.tv.tmdb.TmdbModels.Kind;

/**
 * Where the viewer stopped, so playback can resume there.
 *
 * <p>Only ExoPlayer playback can be recorded. When a title plays inside the
 * embed the position lives in a cross-origin frame that the app cannot read at
 * all — so "resume" is offered exactly when it can be honoured, rather than
 * shown everywhere and silently failing on half of it.
 */
public final class PlaybackProgress {

    private static final String FILE = "playback_progress";

    /**
     * Below this, a saved position is discarded.
     *
     * <p>Opening a title and leaving after ten seconds is not "watching" it,
     * and resuming there would be indistinguishable from starting over while
     * still costing the user a seek.
     */
    private static final long MIN_RESUME_MS = 30_000;

    /**
     * How close to the end counts as finished.
     *
     * <p>Resuming into the closing credits is the one resume behaviour people
     * actively dislike, so the last two minutes reset to the start instead.
     */
    private static final long END_MARGIN_MS = 120_000;

    private final SharedPreferences prefs;

    public PlaybackProgress(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static String key(Kind kind, int tmdbId, int season, int episode) {
        return ServerPrefs.contentKey(kind, tmdbId, season, episode);
    }

    /** Records a position. A near-finished title is cleared instead of stored. */
    public void save(Kind kind, int tmdbId, int season, int episode,
                     long positionMs, long durationMs) {
        String k = key(kind, tmdbId, season, episode);

        boolean tooEarly = positionMs < MIN_RESUME_MS;
        boolean finished = durationMs > 0 && positionMs > durationMs - END_MARGIN_MS;
        if (tooEarly || finished) {
            prefs.edit().remove(k).remove(k + ".dur").apply();
            return;
        }
        prefs.edit().putLong(k, positionMs).putLong(k + ".dur", durationMs).apply();
    }

    /** Saved position in ms, or 0 when there is nothing to resume. */
    public long position(Kind kind, int tmdbId, int season, int episode) {
        return prefs.getLong(key(kind, tmdbId, season, episode), 0L);
    }

    public long duration(Kind kind, int tmdbId, int season, int episode) {
        return prefs.getLong(key(kind, tmdbId, season, episode) + ".dur", 0L);
    }

    /** 0–1 through the title, for the bar on a Continue Watching card. */
    public float fraction(Kind kind, int tmdbId, int season, int episode) {
        long pos = position(kind, tmdbId, season, episode);
        long dur = duration(kind, tmdbId, season, episode);
        if (pos <= 0 || dur <= 0) return 0f;
        return Math.min(1f, (float) pos / (float) dur);
    }

    public void clear(Kind kind, int tmdbId, int season, int episode) {
        String k = key(kind, tmdbId, season, episode);
        prefs.edit().remove(k).remove(k + ".dur").apply();
    }

    /** Drops every stored position. Used by "clear watch history" in Settings. */
    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
