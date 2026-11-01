package com.nashat.tv.util;

import android.net.Uri;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Referer table for hotlink-protected live streams.
 *
 * <p>Some broadcasters serve the manifest and every segment only to a request
 * carrying their own site as {@code Referer}. {@code stream.kurdmax.tv} is the
 * strictest case in the current line-up: no Referer, a VLC or ExoPlayer
 * User-Agent, or an {@code Origin} header on its own all come back 403, while
 * {@code https://kurdmax.tv} returns the 1080p manifest.
 *
 * <p>Kept as an exact-host / true-subdomain table rather than a substring
 * match. Forging one broadcaster's Referer onto an unrelated CDN is not
 * harmless: that CDN's own hotlink protection sees a foreign referrer and
 * rejects the request that would otherwise have succeeded.
 *
 * <p>Mirrors {@code src/main/security/streamHeaders.ts} in the desktop client —
 * when a host is added on one platform, add it on the other.
 */
public final class StreamHeaders {

    /** Matches what the desktop client sends, so both apps look alike to a CDN. */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private static final Map<String, String> REFERER_BY_HOST = new LinkedHashMap<>();

    static {
        REFERER_BY_HOST.put("karwan.tv", "https://karwan.tv/");
        REFERER_BY_HOST.put("stream.kurdmax.tv", "https://kurdmax.tv/");
    }

    private StreamHeaders() {
    }

    /**
     * Request headers for {@code url}, or an empty map when the host needs
     * none — passing an empty map to ExoPlayer is a no-op, so callers do not
     * have to branch.
     */
    public static Map<String, String> forUrl(String url) {
        String referer = refererFor(url);
        if (referer == null) return Collections.emptyMap();

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", referer);
        headers.put("Origin", referer.endsWith("/")
                ? referer.substring(0, referer.length() - 1)
                : referer);
        return headers;
    }

    private static String refererFor(String url) {
        if (url == null || url.trim().isEmpty()) return null;

        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
        if (host == null) return null;

        host = host.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : REFERER_BY_HOST.entrySet()) {
            String pattern = entry.getKey();
            if (host.equals(pattern) || host.endsWith("." + pattern)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
