package com.nashat.tv.player;

import com.nashat.tv.tmdb.TmdbModels.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Embed-server registry for films and series. A direct port of the desktop
 * client's {@code features/player/servers.ts}, kept in the same order.
 *
 * <p><b>Order is preference.</b> The player tries the first working entry and
 * advances from there.
 *
 * <p><b>Reliability outranks speed.</b> A "reliable" server is one whose embed
 * actually plays inside a third-party frame. An unreliable one answers its
 * health probe quickly and then refuses to embed — vidsrc.to now serves a
 * couple of kilobytes of pop-under script where its player used to be. Ranking
 * purely on latency would put exactly that server first, because answering
 * with nothing is fast. Unreliable entries stay manually selectable but can
 * never win auto-pick while anything reliable works.
 */
public final class StreamServers {

    /** One embed source. */
    public static final class Server {
        public final String id;
        public final String label;
        public final boolean reliable;
        private final String movieTemplate;
        private final String tvTemplate;

        Server(String id, String label, boolean reliable, String movieTemplate, String tvTemplate) {
            this.id = id;
            this.label = label;
            this.reliable = reliable;
            this.movieTemplate = movieTemplate;
            this.tvTemplate = tvTemplate;
        }

        /**
         * The embed URL for one title.
         *
         * @param season  1-based; ignored for films
         * @param episode 1-based; ignored for films
         */
        public String url(Kind kind, int tmdbId, int season, int episode) {
            if (kind == Kind.MOVIE) {
                return String.format(Locale.US, movieTemplate, tmdbId);
            }
            return String.format(Locale.US, tvTemplate, tmdbId,
                    Math.max(1, season), Math.max(1, episode));
        }

        /** Host of this server, for allowlisting and ad-block decisions. */
        public String host() {
            String u = movieTemplate;
            int start = u.indexOf("://");
            if (start < 0) return "";
            start += 3;
            int end = u.indexOf('/', start);
            String host = end < 0 ? u.substring(start) : u.substring(start, end);
            return host.startsWith("www.") ? host.substring(4) : host;
        }
    }

    private static final List<Server> SERVERS = Collections.unmodifiableList(build());

    private static List<Server> build() {
        List<Server> s = new ArrayList<>();

        s.add(new Server("vidlink", "VidLink", true,
                "https://vidlink.pro/movie/%d",
                "https://vidlink.pro/tv/%d/%d/%d"));

        // VidFast — largest, most complete embed payload of everything probed.
        s.add(new Server("vidfast", "VidFast", true,
                "https://vidfast.pro/movie/%d",
                "https://vidfast.pro/tv/%d/%d/%d"));

        s.add(new Server("111movies", "111Movies", true,
                "https://111movies.com/movie/%d",
                "https://111movies.com/tv/%d/%d/%d"));

        s.add(new Server("videasy", "Videasy", true,
                "https://player.videasy.net/movie/%d",
                "https://player.videasy.net/tv/%d/%d/%d"));

        // vidsrc.me — the surviving VidSrc host. vidsrc.xyz no longer resolves.
        s.add(new Server("vidsrc-me", "VidSrc", true,
                "https://vidsrc.me/embed/movie?tmdb=%d",
                "https://vidsrc.me/embed/tv?tmdb=%d&season=%d&episode=%d"));

        // Newer 2Embed, a distinct host from 2embed.cc. Clean frame, HD.
        s.add(new Server("2embed-stream", "2Embed Stream", true,
                "https://www.2embed.stream/embed/movie/%d",
                "https://www.2embed.stream/embed/tv/%d/%d/%d"));

        // StreamVault — markets sandbox/CSP-friendly embeds with multi-source fallback.
        s.add(new Server("streamvault", "StreamVault", true,
                "https://streamvaultsrc.click/embed/movie/%d",
                "https://streamvaultsrc.click/embed/tv/%d/%d/%d"));

        s.add(new Server("vidapi", "VidAPI", true,
                "https://vidapi.xyz/embed/movie/%d",
                "https://vidapi.xyz/embed/tv/%d/%d/%d"));

        // GoDrivePlayer — VidSrc alternative, mostly 1080p, keyed by TMDB id.
        s.add(new Server("godrive", "GoDrive", true,
                "https://databasegdriveplayer.co/player.php?type=movie&tmdb=%d",
                "https://databasegdriveplayer.co/player.php?type=series&tmdb=%d&season=%d&episode=%d"));

        s.add(new Server("2embed", "2Embed", true,
                "https://www.2embed.cc/embed/%d",
                "https://www.2embed.cc/embedtv/%d&s=%d&e=%d"));

        s.add(new Server("multiembed", "MultiEmbed", true,
                "https://multiembed.mov/?video_id=%d&tmdb=1",
                "https://multiembed.mov/?video_id=%d&tmdb=1&s=%d&e=%d"));

        // ── Unreliable fallback. Pings fine, player frequently refuses to embed.
        //    Kept so it can still be picked by hand; auto-pick reaches it only
        //    when nothing reliable works.
        s.add(new Server("vidsrc", "VidSrc (legacy)", false,
                "https://vidsrc.to/embed/movie/%d",
                "https://vidsrc.to/embed/tv/%d/%d/%d"));

        return s;
    }

    public static List<Server> all() {
        return SERVERS;
    }

    public static int count() {
        return SERVERS.size();
    }

    public static Server at(int index) {
        return SERVERS.get(index);
    }

    public static int indexOf(String id) {
        for (int i = 0; i < SERVERS.size(); i++) {
            if (SERVERS.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    /** Every server host, for the WebView allowlist. */
    public static List<String> hosts() {
        List<String> out = new ArrayList<>();
        for (Server s : SERVERS) {
            String h = s.host();
            if (!h.isEmpty()) out.add(h);
        }
        return out;
    }

    private StreamServers() {}
}
