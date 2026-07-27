package com.nashat.tv.util;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches the traffic of an embed page and reports the first real video
 * manifest it requests, so playback can be moved out of the WebView and into
 * the app's own ExoPlayer.
 *
 * <h3>Why interception rather than scraping</h3>
 *
 * <p>The obvious approach is to fetch the embed page and pull the stream URL
 * out of its HTML, the way {@link KarwanScraper} does for karwan.tv. That
 * works there because karwan.tv publishes the URL in plain text. It does not
 * work for the movie sources: they build the manifest URL at runtime from an
 * obfuscated, frequently-rotated bundle, precisely so that it cannot be
 * lifted out of the markup. Any parser written against one of them is a
 * parser that breaks the week they reroll it.
 *
 * <p>So this class does not try to understand the page at all. It lets the
 * page run its own code, deobfuscate its own URL, and issue its own request —
 * and reads the URL off that request. Whatever the site does to hide the
 * address, it must eventually ask the network for it, and every such request
 * passes through {@code WebViewClient.shouldInterceptRequest}. There is
 * nothing to keep up with.
 *
 * <h3>What counts as a capture</h3>
 *
 * <p>Only manifests — {@code .m3u8} and {@code .mpd}. A media segment is not
 * useful on its own (it is a few seconds of video with no index), and the
 * player fetches its own segments once it has the manifest.
 *
 * <h3>Threading</h3>
 *
 * <p>{@link #offer} is called on WebView's network thread; the listener is
 * always invoked on the main thread. Exactly one capture is ever reported per
 * {@link #arm} — the flag is claimed with a compare-and-set so that two
 * requests racing in from different threads cannot both fire it.
 */
public final class StreamSniffer {

    private static final String TAG = "StreamSniffer";

    /** A playable stream lifted from the page, with the headers it was asked for under. */
    public static final class Capture {
        public final String url;
        /** Sent as {@code Referer}. Media CDNs commonly 403 without the embed's own referer. */
        public final String referer;
        /** Sent as {@code Origin}, for CDNs that check it instead of (or as well as) the referer. */
        public final String origin;

        Capture(String url, String referer, String origin) {
            this.url = url;
            this.referer = referer;
            this.origin = origin;
        }

        @Override
        public String toString() {
            return "Capture{" + url + " referer=" + referer + "}";
        }
    }

    public interface Listener {
        void onStreamCaptured(Capture capture);
    }

    /**
     * Substrings that mark a manifest as advertising rather than content.
     *
     * <p>Ad breaks are served as their own short HLS manifests, and they load
     * <em>before</em> the feature does. Without this filter the sniffer would
     * reliably capture the pre-roll — the one manifest guaranteed to be first —
     * and the user would get a fifteen-second advert playing natively while
     * the film they asked for never started.
     */
    private static final String[] AD_MARKERS = {
            "/ads/", "/ad/", "advert", "preroll", "pre-roll", "midroll", "mid-roll",
            "postroll", "post-roll", "/vast", "/vmap", "doubleclick",
            "googlesyndication", "imasdk", "adsystem", "/promo/", "bumper"
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean claimed = new AtomicBoolean(false);

    private volatile boolean armed = false;
    private volatile Listener listener;

    /**
     * Begin watching. Any previous capture is forgotten, so this is also how a
     * server switch or an episode change resets the sniffer.
     */
    public void arm(Listener l) {
        listener = l;
        claimed.set(false);
        armed = true;
    }

    /**
     * Stop watching. Called once a capture has been acted on, and whenever the
     * app deliberately stays in WebView mode — after a native attempt has
     * already failed, for instance, so a retry cannot loop back into it.
     */
    public void disarm() {
        armed = false;
    }

    public boolean isArmed() {
        return armed;
    }

    /**
     * Offer one outgoing request to the sniffer.
     *
     * @param url      the request URL <em>with its original casing intact</em>.
     *                 Signed CDN URLs carry base64 tokens in the query string
     *                 and a lower-cased copy of one is a 403.
     * @param headers  the request headers, as given by
     *                 {@code WebResourceRequest.getRequestHeaders()}
     * @param pageUrl  the URL the WebView has committed to, used as the referer
     *                 when the request itself does not carry one
     */
    public void offer(String url, Map<String, String> headers, String pageUrl) {
        if (!armed || url == null) return;
        if (!isManifest(url) || looksLikeAd(url)) return;

        // From here on this is a real capture, so claim it before doing any
        // more work. Two manifests arriving together must not both report.
        if (!claimed.compareAndSet(false, true)) return;
        armed = false;

        String referer = header(headers, "Referer");
        if (TextUtils.isEmpty(referer)) referer = pageUrl;
        String origin = header(headers, "Origin");
        if (TextUtils.isEmpty(origin)) origin = originOf(referer);

        final Capture capture = new Capture(url, referer, origin);
        Log.i(TAG, "Captured " + capture);

        final Listener l = listener;
        if (l != null) {
            main.post(() -> l.onStreamCaptured(capture));
        }
    }

    // ─── Classification ───────────────────────────────────────

    /**
     * True for an HLS or DASH manifest.
     *
     * <p>The query string is cut away first: signed manifest URLs are far more
     * often {@code master.m3u8?token=…} than a bare {@code .m3u8}, and an
     * {@code endsWith} test on the whole URL misses every one of them.
     */
    private static boolean isManifest(String url) {
        String path = url.toLowerCase(Locale.US);
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        cut = path.indexOf('#');
        if (cut >= 0) path = path.substring(0, cut);
        return path.endsWith(".m3u8") || path.endsWith(".mpd");
    }

    private static boolean looksLikeAd(String url) {
        String lower = url.toLowerCase(Locale.US);
        for (String marker : AD_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    // ─── Header helpers ───────────────────────────────────────

    /** Case-insensitive header lookup — HTTP header names are not case-sensitive. */
    private static String header(Map<String, String> headers, String name) {
        if (headers == null) return null;
        String direct = headers.get(name);
        if (direct != null) return direct;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** {@code https://host:port} of a URL, which is what an Origin header is. */
    private static String originOf(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            if (scheme == null || authority == null) return "";
            return scheme + "://" + authority;
        } catch (Exception e) {
            return "";
        }
    }
}
