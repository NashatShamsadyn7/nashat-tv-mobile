package com.nashat.tv.player;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.nashat.tv.tmdb.TmdbModels.Kind;

import java.util.Locale;

/**
 * Remembers which embed server worked for which title, plus the user's own
 * up/down votes on each server. A port of the desktop client's
 * {@code features/player/useServerPrefs.ts}.
 *
 * <h3>Deviation from the desktop client</h3>
 *
 * <p>The desktop version keeps these under {@code users/{uid}/serverPrefs} in
 * Firebase, so they follow the account. This one uses {@link SharedPreferences}
 * instead, deliberately: on the desktop the user is signed in before they can
 * reach the player, whereas here the catalogue is browsable without an account.
 * Tying "which server plays this film" to sign-in would mean the single feature
 * that most reduces waiting simply would not work for anyone browsing
 * anonymously — which is most people, most of the time.
 */
public final class ServerPrefs {

    private static final String FILE = "server_prefs";
    private static final String KEY_LAST_USED = "last:";
    private static final String KEY_VOTE = "vote:";

    public static final int VOTE_NONE = 0;
    public static final int VOTE_UP = 1;
    public static final int VOTE_DOWN = -1;

    private final SharedPreferences prefs;

    public ServerPrefs(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * Identity of one playable thing. A series is keyed per episode, not per
     * series: sources routinely carry one episode and not the next, so
     * remembering at series level would send the user to a server that has
     * nothing for the episode they actually opened.
     */
    public static String contentKey(Kind kind, int tmdbId, int season, int episode) {
        if (kind == Kind.MOVIE) {
            return "movie-" + tmdbId;
        }
        return String.format(Locale.US, "tv-%d-s%d-e%d", tmdbId,
                Math.max(1, season), Math.max(1, episode));
    }

    // ─── Last working server ──────────────────────────────────

    @Nullable
    public String lastUsed(String contentKey) {
        return prefs.getString(KEY_LAST_USED + contentKey, null);
    }

    public void rememberLastUsed(String contentKey, String serverId) {
        prefs.edit().putString(KEY_LAST_USED + contentKey, serverId).apply();
    }

    public void forgetLastUsed(String contentKey) {
        prefs.edit().remove(KEY_LAST_USED + contentKey).apply();
    }

    // ─── Votes ────────────────────────────────────────────────

    /** {@link #VOTE_UP}, {@link #VOTE_DOWN} or {@link #VOTE_NONE}. */
    public int vote(String serverId) {
        return prefs.getInt(KEY_VOTE + serverId, VOTE_NONE);
    }

    /**
     * Applies a vote. Voting the same way twice clears it, so the control is a
     * toggle rather than a one-way door.
     */
    public void setVote(String serverId, int vote) {
        int next = (vote(serverId) == vote) ? VOTE_NONE : vote;
        if (next == VOTE_NONE) {
            prefs.edit().remove(KEY_VOTE + serverId).apply();
        } else {
            prefs.edit().putInt(KEY_VOTE + serverId, next).apply();
        }
    }

    /**
     * Ranking bias for a server. Fed to
     * {@link ServerHealth#sorted(java.util.List, ServerHealth.ScoreLookup)} so a
     * server the user has upvoted drifts toward auto-pick and a downvoted one
     * drifts away, without either ever overriding health or reliability.
     */
    public int scoreOf(String serverId) {
        return vote(serverId);
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
