package com.nashat.tv.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.nashat.tv.tmdb.TmdbModels.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Probes every embed server in parallel and reports which ones are actually
 * serving a player. A port of the desktop client's
 * {@code features/player/useServerHealth.ts}.
 *
 * <h3>Why the probe reads the body</h3>
 *
 * <p>The desktop version originally probed with a {@code no-cors} fetch, which
 * was worse than useless: such a request resolves for <em>any</em> response, so
 * a 404, a 403 and an expired domain parked on a "this domain may be for sale"
 * page all reported healthy. letsembed.cc showed a green tick for months while
 * serving nothing.
 *
 * <p>Android has no CORS restriction, so this probe does what the desktop main
 * process does: it reads the status code <em>and</em> looks at the body. A
 * server is healthy only if it answers 2xx/3xx with a page big enough to be a
 * player and without the fingerprints of a parking page or an ad shell.
 */
public final class ServerHealth {

    private static final String TAG = "ServerHealth";

    /** Below this the response cannot be a working player, whatever its status code. */
    private static final int MIN_PLAYER_BYTES = 900;

    /** How much of the body to read. Enough to classify; never the whole page. */
    private static final int BODY_SAMPLE_BYTES = 6144;

    private static final int PROBE_TIMEOUT_MS = 7000;

    public enum Status {
        CHECKING,
        OK,
        FAIL
    }

    /** Live state of one server for one title. */
    public static final class Entry {
        public final StreamServers.Server server;
        public final String url;
        public Status status = Status.CHECKING;
        public long latencyMs = Long.MAX_VALUE;
        /** Why the probe failed, for the picker. Null while healthy. */
        @Nullable
        public String reason;

        Entry(StreamServers.Server server, String url) {
            this.server = server;
            this.url = url;
        }

        public String id() {
            return server.id;
        }

        public String label() {
            return server.label;
        }

        public boolean reliable() {
            return server.reliable;
        }
    }

    public interface Listener {
        /** Called on the main thread after each probe resolves. */
        @MainThread
        void onHealthChanged(List<Entry> entries);

        /** Called on the main thread once every probe has resolved. */
        @MainThread
        void onSweepComplete(List<Entry> entries);
    }

    /**
     * Phrases that mark a response as a parking page, a block page or an ad
     * shell rather than a player. Matched against a lower-cased body sample.
     */
    private static final String[] DEAD_BODY_MARKERS = {
            "domain may be for sale", "domain is for sale", "buy this domain",
            "parked", "godaddy", "sedoparking", "namecheap parking",
            "in-app browser", "unsupported browser", "embedded browser",
            "open in external browser", "browser not supported",
            "404 not found", "page not found", "access denied",
            "cloudflare", "checking your browser", "captcha",
            "video not found", "no sources found", "media not available"
    };

    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    private final List<Entry> entries = new ArrayList<>();
    private final AtomicInteger outstanding = new AtomicInteger();
    private volatile int generation = 0;

    public ServerHealth(OkHttpClient base) {
        // Its own timeouts: a probe that hangs must not hold the sweep open for
        // as long as a real content request is allowed to take.
        this.client = base.newBuilder()
                .connectTimeout(PROBE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(PROBE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .callTimeout(PROBE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Start a fresh sweep for one title. Any sweep already running is abandoned:
     * its results carry a stale generation and are dropped as they land, so
     * switching episode quickly cannot let an old title's health overwrite the
     * new one's.
     */
    @MainThread
    public void probe(Kind kind, int tmdbId, int season, int episode,
                      String userAgent, Listener listener) {
        final int gen = ++generation;

        entries.clear();
        for (StreamServers.Server s : StreamServers.all()) {
            entries.add(new Entry(s, s.url(kind, tmdbId, season, episode)));
        }
        listener.onHealthChanged(entries());
        outstanding.set(entries.size());

        for (Entry entry : entries) {
            pool.execute(() -> {
                long started = System.currentTimeMillis();
                Probe result = run(entry.url, userAgent);
                long elapsed = System.currentTimeMillis() - started;

                main.post(() -> {
                    if (gen != generation) return;
                    entry.status = result.ok ? Status.OK : Status.FAIL;
                    entry.latencyMs = result.ok ? elapsed : Long.MAX_VALUE;
                    entry.reason = result.reason;
                    listener.onHealthChanged(entries());
                    if (outstanding.decrementAndGet() == 0) {
                        listener.onSweepComplete(entries());
                    }
                });
            });
        }
    }

    public void shutdown() {
        generation++;
        pool.shutdownNow();
    }

    // ─── The probe itself ─────────────────────────────────────

    private static final class Probe {
        final boolean ok;
        @Nullable final String reason;

        Probe(boolean ok, @Nullable String reason) {
            this.ok = ok;
            this.reason = reason;
        }
    }

    private Probe run(String url, String userAgent) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code >= 400) {
                return new Probe(false, "HTTP " + code);
            }
            if (response.body() == null) {
                return new Probe(false, "empty");
            }

            byte[] buffer = new byte[BODY_SAMPLE_BYTES];
            int read = readUpTo(response.body().byteStream(), buffer);
            if (read < MIN_PLAYER_BYTES) {
                // Small but non-empty is the signature of an ad shell: a couple
                // of kilobytes of pop-under script where a player should be.
                return new Probe(false, "stub");
            }

            String sample = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase(java.util.Locale.US);
            for (String marker : DEAD_BODY_MARKERS) {
                if (sample.contains(marker)) {
                    return new Probe(false, marker);
                }
            }
            return new Probe(true, null);
        } catch (Exception e) {
            Log.d(TAG, "Probe failed for " + url + ": " + e.getMessage());
            return new Probe(false, "offline");
        }
    }

    private static int readUpTo(java.io.InputStream in, byte[] buffer) throws java.io.IOException {
        int total = 0;
        while (total < buffer.length) {
            int n = in.read(buffer, total, buffer.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    // ─── Ranking ──────────────────────────────────────────────

    /**
     * Working before checking before failed; reliable before unreliable; the
     * user's own preference before neutral; then fastest first.
     *
     * <p>Latency is the <em>last</em> key, not the first. It has to be: an
     * unreliable server that answers instantly with an ad shell is the fastest
     * thing in the list and the one result the user must never be sent to.
     *
     * @param scoreOf net up/down score for a server id, higher is better
     */
    public static List<Entry> sorted(List<Entry> input, ScoreLookup scoreOf) {
        List<Entry> copy = new ArrayList<>(input);
        Collections.sort(copy, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int byStatus = rank(a.status) - rank(b.status);
                if (byStatus != 0) return byStatus;

                if (a.reliable() != b.reliable()) return a.reliable() ? -1 : 1;

                int scoreDelta = scoreOf.of(b.id()) - scoreOf.of(a.id());
                if (scoreDelta != 0) return scoreDelta;

                return Long.compare(a.latencyMs, b.latencyMs);
            }
        });
        return copy;
    }

    public interface ScoreLookup {
        int of(String serverId);
    }

    private static int rank(Status s) {
        switch (s) {
            case OK: return 0;
            case CHECKING: return 1;
            default: return 2;
        }
    }
}
