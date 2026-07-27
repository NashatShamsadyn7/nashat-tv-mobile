package com.nashat.tv.util;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Ad and redirect protection for the embed WebView.
 *
 * <h3>The threat is not banners</h3>
 *
 * <p>Free streaming embeds do not make their money from display advertising.
 * They make it from <b>pop-unders and forced redirects</b> — the first tap
 * anywhere on the page navigates the whole frame to an affiliate, or fires an
 * {@code intent://} URL that throws the user out into Chrome or the Play Store.
 * On a phone that is not an annoyance, it is a loss of place: the app goes to
 * the background, the film stops, and coming back means starting over.
 *
 * <p>So the defence is layered, and the layers are ordered by how early they
 * can stop the attack:
 *
 * <ol>
 *   <li><b>Scheme</b> — anything that is not http(s) never leaves the WebView.
 *       This is what stops the app being ejected, and it is absolute: an embed
 *       player has no legitimate reason to open Play Store, WhatsApp, a dialer
 *       or a deep link into another app.</li>
 *   <li><b>Navigation</b> — the main frame may only ever sit on an allowlisted
 *       embed host. A top-level navigation anywhere else is a redirect, not a
 *       feature.</li>
 *   <li><b>Network</b> — requests to known ad, tracker and pop-under networks
 *       are answered with an empty body, so the script that would have opened
 *       the pop-under never runs.</li>
 *   <li><b>Script</b> — what still gets through is disarmed inside the page:
 *       {@code window.open} is stubbed, {@code target="_blank"} is stripped,
 *       navigation traps are removed, and full-screen overlay click-catchers
 *       are deleted from the DOM.</li>
 * </ol>
 *
 * <p>Every layer is independent. Any one of them being bypassed by a source
 * that changes its tactics still leaves the other three standing.
 */
public final class AdBlocker {

    /**
     * URL schemes the WebView is allowed to navigate to.
     *
     * <p>Everything else — {@code intent:}, {@code market:}, {@code tel:},
     * {@code sms:}, {@code whatsapp:}, {@code fb:}, custom app links — is
     * refused outright. These are the exact mechanism by which an embed page
     * ejects the user into another app.
     */
    private static final Set<String> ALLOWED_SCHEMES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("http", "https", "about", "data")));

    /**
     * Ad, tracker and pop-under networks, matched as a substring of the host.
     *
     * <p>The pop-under names near the top matter far more here than the
     * familiar analytics ones: propellerads, popads, adsterra, hilltopads and
     * exoclick are what these particular sites actually monetise with, and they
     * are what produce the "it threw me out of the app" behaviour.
     */
    private static final String[] AD_HOSTS = {
            // Pop-under / redirect networks — the ones that cause the ejection.
            "propellerads", "propeller-ads", "popads", "popcash", "popunder",
            "adsterra", "hilltopads", "exoclick", "exosrv", "juicyads",
            "trafficjunky", "trafficfactory", "clickadu", "adcash", "admaven",
            "ad-maven", "bidvertiser", "mgid", "mediafeeds", "revcontent",
            "taboola", "outbrain", "zeropark", "voluum", "clickaine",
            "onclickalgo", "onclicka", "adnium", "adspyglass", "adsco",
            "monetizer", "clksite", "clickstrack", "trackwilltrk", "toroadvertising",
            "richads", "rich-ads", "adprovider", "adplusplus", "poweradnetwork",

            // Mainstream ad exchanges.
            "doubleclick", "googlesyndication", "googleadservices", "googletagservices",
            "googletagmanager", "google-analytics", "adservice.google",
            "amazon-adsystem", "adnxs", "rubiconproject", "casalemedia",
            "openx", "pubmatic", "criteo", "smartadserver", "adform",
            "33across", "sharethrough", "indexexchange", "districtm",
            "sovrn", "gumgum", "triplelift", "yieldmo", "inmobi",
            "smaato", "applovin", "unityads", "vungle", "chartboost",
            "startapp", "tapjoy", "ironsrc", "adcolony", "mopub",

            // Analytics and fingerprinting.
            "scorecardresearch", "quantserve", "quantcast", "hotjar",
            "mixpanel", "segment.io", "amplitude.com", "fullstory",
            "mouseflow", "crazyegg", "clicktale", "yandex.ru/metrika",
            "matomo", "statcounter", "histats", "clicky.com",

            // Video ad servers.
            "spotxchange", "spotx.tv", "springserve", "beachfront",
            "adsmoloco", "aniview", "connatix", "primis", "vidoomy",

            // Generic ad infrastructure.
            "adservice", "adserver", "adsystem", "advertising", "adtech",
            "adroll", "admob", "moatads", "serving-sys", "flashtalking",
            "bluekai", "krxd.net", "demdex", "everesttech", "adsrvr"
    };

    /**
     * Path and query fragments that mark an ad request regardless of host.
     *
     * <p>Necessary because many of these sites proxy their ad calls through
     * their own domain, which is by definition allowlisted.
     */
    private static final String[] AD_PATHS = {
            "/ads/", "/ad/", "/adserve", "/adframe", "/adhandler", "/adcall",
            "/popunder", "/popup", "/pop.js", "/pop.php", "/popd",
            "/banner", "/banners/", "/sponsor", "/promoted",
            "/vast", "/vmap", "/vpaid", "/preroll", "/midroll", "/postroll",
            "/prebid", "/openrtb", "/rtb/", "/bid?", "/bidder",
            "/track?", "/tracker", "/pixel", "/beacon", "/telemetry",
            "/analytics", "/collect?", "/impression", "/click?redirect",
            "utm_source=", "aff_id=", "affiliate_id="
    };

    /** File names that are almost always an ad or tracking script. */
    private static final String[] AD_FILES = {
            "ads.js", "ad.js", "adsbygoogle.js", "advertisement.js",
            "popunder.js", "popup.js", "pop.min.js", "aclib.js",
            "gtag.js", "analytics.js", "gtm.js", "fbevents.js",
            "prebid.js", "apstag.js", "ima3.js"
    };

    /** An empty 200, so the page's own error handling does not take a different bad path. */
    public static WebResourceResponse blockedResponse() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    // ─── Layer 1: scheme ──────────────────────────────────────

    /**
     * True when a URL may be loaded at all.
     *
     * <p>The single most important check in this class. Returning false here is
     * what keeps the app in the foreground.
     */
    public static boolean isSchemeAllowed(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String scheme = schemeOf(url);
        return scheme != null && ALLOWED_SCHEMES.contains(scheme);
    }

    @Nullable
    private static String schemeOf(String url) {
        int cut = url.indexOf(':');
        if (cut <= 0) return null;
        return url.substring(0, cut).toLowerCase(Locale.US);
    }

    // ─── Layer 3: network ─────────────────────────────────────

    /** True when a request should be answered with nothing. */
    public static boolean isAdRequest(@Nullable String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.US);

        String host = hostOf(lower);
        if (host != null && isAdHost(host)) return true;

        for (String path : AD_PATHS) {
            if (lower.contains(path)) return true;
        }
        String file = fileNameOf(lower);
        if (file != null) {
            for (String adFile : AD_FILES) {
                if (file.equals(adFile)) return true;
            }
        }
        return false;
    }

    /**
     * True when a host belongs to an ad network.
     *
     * <p>Named networks are matched as substrings. The generic "ad" family is
     * matched <em>per host label</em> instead, because a substring test for
     * "ad" alone hits {@code upload-cdn}, {@code download}, {@code header} and
     * {@code broadcast} — and blocking those kills real playback.
     */
    public static boolean isAdHost(String host) {
        for (String adHost : AD_HOSTS) {
            if (host.contains(adHost)) return true;
        }
        for (String label : host.split("\\.")) {
            if (label.equals("ad") || label.equals("ads") || label.equals("adx")
                    || label.equals("adv") || label.equals("adserver")
                    || label.equals("adservice") || label.equals("banner")
                    || label.equals("popup") || label.equals("track")
                    || label.equals("tracker") || label.equals("analytics")
                    || label.startsWith("ad-") || label.startsWith("ads-")
                    || label.startsWith("adserv")) {
                return true;
            }
        }
        return false;
    }

    /**
     * True for anything the video pipeline needs.
     *
     * <p>Checked before the ad rules so a false positive can never break
     * playback. Query strings are stripped first: signed segment URLs nearly
     * always carry one, and {@code endsWith(".m3u8")} misses
     * {@code index.m3u8?token=…}, which is the common case.
     */
    public static boolean isPlaybackRequest(@Nullable String url) {
        if (url == null) return false;
        String path = url.toLowerCase(Locale.US);
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        cut = path.indexOf('#');
        if (cut >= 0) path = path.substring(0, cut);

        if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".ts")
                || path.endsWith(".m4s") || path.endsWith(".mp4") || path.endsWith(".m4v")
                || path.endsWith(".aac") || path.endsWith(".m4a") || path.endsWith(".webm")
                || path.endsWith(".key") || path.endsWith(".vtt") || path.endsWith(".srt")) {
            return true;
        }
        return path.contains("/hls/") || path.contains("/dash/")
                || path.contains("/manifest") || path.contains("/playlist")
                || path.contains("/segment") || path.contains("/chunk");
    }

    // ─── Helpers ──────────────────────────────────────────────

    /** Host of a URL, lower-cased, without {@code www.}. */
    @Nullable
    public static String hostOf(@Nullable String url) {
        if (url == null) return null;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.US);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String fileNameOf(String url) {
        int cut = url.indexOf('?');
        String path = cut >= 0 ? url.substring(0, cut) : url;
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) return null;
        return path.substring(slash + 1);
    }

    // ─── Layer 4: in-page script ──────────────────────────────

    /**
     * The script injected into every embed page.
     *
     * <p>This runs inside the page's own context, which is the only place some
     * of these behaviours can be reached at all — a native layer cannot see a
     * {@code window.open} call or an overlay {@code <div>}.
     *
     * <p>It is deliberately conservative about what it deletes. Removing
     * anything that merely <em>looks</em> like an ad risks removing the player,
     * so element removal is limited to full-viewport click-catchers: elements
     * covering essentially the whole screen, at a high stacking order, that
     * contain no video. That shape is a click-jacker and nothing else.
     */
    public static String script() {
        return "(function(){\n"
                + "if(window.__nashatGuard)return;window.__nashatGuard=true;\n"
                + "var W=window;\n"

                // ── window.open: the pop-under primitive. Stubbed rather than
                //    deleted, because a page that finds it missing sometimes
                //    falls back to assigning location, which is worse.
                + "try{W.open=function(){return null};}catch(e){}\n"
                + "try{Object.defineProperty(W,'open',{value:function(){return null},writable:false,configurable:false});}catch(e){}\n"

                // ── Top-level navigation from inside a frame. The embed itself
                //    never needs it; only a redirect does.
                + "try{var _assign=W.location.assign.bind(W.location);\n"
                + "W.location.assign=function(u){if(/^https?:/i.test(u))_assign(u)};\n"
                + "W.location.replace=function(){};}catch(e){}\n"
                + "try{if(W.top!==W.self){Object.defineProperty(W,'top',{get:function(){return W.self}});}}catch(e){}\n"

                // ── Exit traps. A beforeunload handler is used to hold the user
                //    on the page while a pop-under lands behind it.
                + "try{W.onbeforeunload=null;W.addEventListener('beforeunload',function(e){e.stopImmediatePropagation()},true);}catch(e){}\n"

                // ── Permission prompts nobody asked for.
                + "try{if(W.Notification)W.Notification.requestPermission=function(){return Promise.resolve('denied')};}catch(e){}\n"

                // ── target=_blank and javascript: links, re-applied on mutation
                //    because these pages rewrite their anchors continuously.
                + "function clean(){\n"
                + "  var a=document.getElementsByTagName('a');\n"
                + "  for(var i=0;i<a.length;i++){\n"
                + "    if(a[i].target==='_blank')a[i].removeAttribute('target');\n"
                + "    var h=a[i].getAttribute('href')||'';\n"
                + "    if(/^(javascript|intent|market|whatsapp|tg|fb|tel|sms):/i.test(h))a[i].removeAttribute('href');\n"
                + "  }\n"
                // Full-viewport click-catchers: big, floating, high z-index, no
                // video inside. Anything holding a <video> or an <iframe> is
                // left alone — that is the player.
                + "  var all=document.querySelectorAll('div,a,ins,section');\n"
                + "  for(var j=0;j<all.length;j++){\n"
                + "    var el=all[j];\n"
                + "    try{\n"
                + "      var s=W.getComputedStyle(el);\n"
                + "      if(s.position!=='fixed'&&s.position!=='absolute')continue;\n"
                + "      var z=parseInt(s.zIndex||'0',10);if(isNaN(z)||z<100)continue;\n"
                + "      var r=el.getBoundingClientRect();\n"
                + "      if(r.width<W.innerWidth*0.85||r.height<W.innerHeight*0.85)continue;\n"
                + "      if(el.querySelector('video,iframe,canvas'))continue;\n"
                + "      el.remove();\n"
                + "    }catch(e){}\n"
                + "  }\n"
                + "}\n"
                + "try{clean();new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}\n"
                + "try{setInterval(clean,1500);}catch(e){}\n"

                // ── First-tap hijack. These pages bind a one-shot listener at
                //    the document level that redirects on the first touch
                //    anywhere. Swallowing untrusted synthetic clicks stops it
                //    without affecting the player's own controls, which receive
                //    genuine user events.
                + "try{['click','mousedown','touchstart','pointerdown'].forEach(function(t){\n"
                + "  W.addEventListener(t,function(e){if(!e.isTrusted)e.stopImmediatePropagation();},true);\n"
                + "});}catch(e){}\n"

                // ── Hide leftover ad furniture rather than removing it, so a
                //    mis-identified node cannot break the page's layout logic.
                + "try{var st=document.createElement('style');\n"
                + "st.textContent='[id*=\"banner\"],[class*=\"banner\"],[id*=\"popup\"],[class*=\"popup\"],"
                + "[id^=\"ad_\"],[class^=\"ad_\"],[id$=\"_ad\"],[class$=\"_ad\"],ins.adsbygoogle,"
                + "[class*=\"advert\"],[id*=\"advert\"]{display:none!important;visibility:hidden!important}';\n"
                + "(document.head||document.documentElement).appendChild(st);}catch(e){}\n"
                + "})();";
    }

    private AdBlocker() {}
}
