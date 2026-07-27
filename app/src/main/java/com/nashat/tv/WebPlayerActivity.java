package com.nashat.tv;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.PictureInPictureParams;
import android.util.Rational;

import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;

public class WebPlayerActivity extends AppCompatActivity {

    private static final String TAG = "WebPlayerActivity";

    private WebView webView;
    private ProgressBar progressBar;

    // Controls
    private View topBar, bottomBar, episodeControls;
    private ImageButton btnBack, btnPip, btnFullscreen, btnReload, btnLockControls, btnServerSwitch;
    private ImageButton btnPrevEpisode, btnNextEpisode;
    private TextView txtTitle, txtServerName, txtEpisodeLabel;
    private MediaRouteButton castButton;

    // State
    private boolean isFullscreen = true;
    private boolean controlsLocked = false;
    private boolean controlsVisible = true;
    private Handler handler;
    private CastStateListener castStateListener;
    private View loadingOverlay;
    private TextView loadingTitle, loadingSubtitle;

    // ─── Smart Server Engine ──────────────────────────────────
    // Ordered best→worst. Known-good first; silent fallback if any fails.
    //
    // Every host below was probed and confirmed to still serve a real player.
    // The previous list had gone stale — vidsrc.xyz, vidsrc.cc, vidsrc.icu,
    // autoembed.cc, embed.su, primewire.tf, dbgo.fun and moviesapi.club no
    // longer resolve at all, and vidsrc.net now redirects to a parked lander,
    // so the player spent its whole timeout budget on dead hosts before it
    // ever reached one that works. Keep this list probed before shipping.
    private static final String[][] SERVERS = {
        // Order is evidence-based: each of the first five was loaded with an
        // Android user-agent and confirmed to reach a real <video> element.
        {"VidFast",        "https://vidfast.pro/movie/%s",                 "https://vidfast.pro/tv/%s/%d/%d"},
        {"VidLink",        "https://vidlink.pro/movie/%s",                 "https://vidlink.pro/tv/%s/%d/%d"},
        {"111Movies",      "https://111movies.com/movie/%s",               "https://111movies.com/tv/%s/%d/%d"},
        {"StreamVault",    "https://streamvaultsrc.click/embed/movie/%s",  "https://streamvaultsrc.click/embed/tv/%s/%d/%d"},
        {"GoDrive",        "https://databasegdriveplayer.co/player.php?type=movie&tmdb=%s", "https://databasegdriveplayer.co/player.php?type=series&tmdb=%s&season=%d&episode=%d"},
        {"Videasy",        "https://player.videasy.net/movie/%s",          "https://player.videasy.net/tv/%s/%d/%d"},
        {"VidSrc",         "https://vidsrc.me/embed/movie?tmdb=%s",        "https://vidsrc.me/embed/tv?tmdb=%s&season=%d&episode=%d"},
        {"VidSrc SU",      "https://vidsrc.su/embed/movie/%s",             "https://vidsrc.su/embed/tv/%s/%d/%d"},
        {"2Embed Stream",  "https://www.2embed.stream/embed/movie/%s",     "https://www.2embed.stream/embed/tv/%s/%d/%d"},
        {"VidAPI",         "https://vidapi.xyz/embed/movie/%s",            "https://vidapi.xyz/embed/tv/%s/%d/%d"},
        {"2Embed",         "https://www.2embed.cc/embed/%s",               "https://www.2embed.cc/embedtv/%s&s=%d&e=%d"},
        {"MultiEmbed",     "https://multiembed.mov/?video_id=%s&tmdb=1",   "https://multiembed.mov/?video_id=%s&tmdb=1&s=%d&e=%d"},
        {"VidSrc (legacy)","https://vidsrc.to/embed/movie/%s",             "https://vidsrc.to/embed/tv/%s/%d/%d"},
    };

    // ── Server health ─────────────────────────────────────────────────────
    //
    // Two parallel arrays, both indexed by SERVERS:
    //   serverHealth  true = answered, false = dead, null = not probed yet
    //   serverLatency round-trip in ms; LATENCY_DEAD for anything unusable
    //
    // Latency is what drives ordering. The picker used to list servers in
    // hard-coded array order and always START on index 0, so if the first
    // entry was slow or down the user sat on "One moment" for the full
    // timeout before anything else was tried. Ranking by measured round-trip
    // means the fastest reachable server is chosen first, every time.
    private final Boolean[] serverHealth = new Boolean[SERVERS.length];
    private final long[] serverLatency = new long[SERVERS.length];
    static final long LATENCY_DEAD = Long.MAX_VALUE;

    private final java.util.concurrent.ExecutorService probeExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(6);
    /** Set once the probe sweep has finished, so it only re-ranks once. */
    private final java.util.concurrent.atomic.AtomicInteger probesOutstanding =
            new java.util.concurrent.atomic.AtomicInteger();

    private int autoSwitchAttempts = 0;
    private boolean pageLoadedSuccessfully = false;

    // Embed players resolve a source, then a CDN, then start buffering, so
    // this cannot be aggressive — cutting them off mid-handshake makes a
    // working server look dead.
    //
    // It is nonetheless down from 16s to 9s, because it is no longer the only
    // thing that can move us off a bad server: the health probe now switches
    // as soon as it *knows* the current one is unreachable, usually within
    // three seconds, instead of letting this timer run its full course.
    private static final long LOAD_TIMEOUT_MS = 9000;

    private final Runnable loadTimeoutRunnable = () -> {
        if (!pageLoadedSuccessfully && autoSwitchAttempts < SERVERS.length - 1) {
            autoSwitchAttempts++;
            switchToNextServer();
        }
    };

    /** Host of the page the WebView actually committed to, redirects included. */
    private volatile String committedHost = "";

    private int currentServerIndex = 0;
    /** Servers already attempted for this title, so we never retry one twice. */
    private final java.util.Set<Integer> triedServers = new java.util.HashSet<>();
    private String tmdbId = "";
    private boolean isTvSeries = false;
    private int season = 1;
    private int episode = 1;

    private final Runnable hideControlsRunnable = () -> setControlsVisibility(false);

    // Ad and tracker hosts.
    //
    // The generic fragments that used to live here — "ads.", "adx." and "ad-" —
    // were removed: matched as a substring they also hit "downloads.", "upload-"
    // and "head-", which are ordinary parts of a CDN URL, so real playback
    // requests were being answered with an empty body. Generic ad labels are
    // handled by isAdHost() below, which inspects host labels instead.
    private static final String[] AD_HOSTS = {
            "popcash", "popunder", "propeller", "onclick",
            "syndication", "doubleclick", "adult", "betting", "casino",
            "tracking", "analytics", "pixel", "beacon", "exoclick",
            "adsterra", "popads", "juicyads", "adcash", "admagik", "traffichaus",
            "realsrv", "exosrv", "chaturbate", "bongacams", "jerkmate", "trafficfactory",
            "taboola", "outbrain", "scorecardresearch", "quantserve", "adform",
            "revcontent", "teads", "amazon-adsystem", "criteo", "pubmatic",
            "smartadserver", "rubiconproject", "casalemedia", "openx"
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setContentView(R.layout.activity_web_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        handler = new Handler(Looper.getMainLooper());

        // Immersive mode
        enterImmersiveMode();

        // ─── Find views ───────────────────────────────────────
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        btnBack = findViewById(R.id.btnBack);
        btnPip = findViewById(R.id.btnPip);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        btnReload = findViewById(R.id.btnReload);
        btnLockControls = findViewById(R.id.btnLockControls);
        btnServerSwitch = findViewById(R.id.btnServerSwitch);
        txtTitle = findViewById(R.id.txtTitle);
        txtServerName = findViewById(R.id.txtServerName);
        castButton = findViewById(R.id.webCastButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingTitle = findViewById(R.id.loadingTitle);
        loadingSubtitle = findViewById(R.id.loadingSubtitle);

        // Persistent floating server button — always visible, never auto-hides
        View floatingServerBtn = findViewById(R.id.floatingServerBtn);
        TextView floatingServerLabel = findViewById(R.id.floatingServerLabel);
        if (floatingServerBtn != null) {
            floatingServerBtn.setOnClickListener(v -> showServerSelector());
            floatingServerBtn.setOnLongClickListener(v -> { switchToNextServer(); return true; });
        }
        if (floatingServerLabel != null) {
            floatingServerLabel.setText(SERVERS[currentServerIndex][0]);
        }

        // Show server switch — user can pick from working servers
        if (btnServerSwitch != null) btnServerSwitch.setVisibility(View.VISIBLE);
        if (txtServerName != null) txtServerName.setVisibility(View.VISIBLE);
        episodeControls = findViewById(R.id.episodeControls);
        btnPrevEpisode = findViewById(R.id.btnPrevEpisode);
        btnNextEpisode = findViewById(R.id.btnNextEpisode);
        txtEpisodeLabel = findViewById(R.id.txtEpisodeLabel);

        // ─── Get movie/tv info from Intent ───────────────────
        // SECURITY: sanitize all extras — they're concatenated into URLs sent
        // to third-party embed servers. Reject anything that isn't a numeric
        // TMDB id or an IMDB id (tt#######).
        String rawTmdbId = getIntent().getStringExtra("tmdbId");
        tmdbId = sanitizeMediaId(rawTmdbId);
        if (tmdbId.isEmpty()) {
            Toast.makeText(this, R.string.loading_unavailable, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isTvSeries = getIntent().getBooleanExtra("isTvSeries", false);
        season = clampInt(getIntent().getIntExtra("season", 1), 1, 99);
        episode = clampInt(getIntent().getIntExtra("episode", 1), 1, 999);
        String movieTitle = getIntent().getStringExtra("title");

        // Build URL with first server
        String url = buildServerUrl(currentServerIndex);

        // Set title if provided
        if (txtTitle != null) {
            if (movieTitle != null && !movieTitle.isEmpty()) {
                txtTitle.setText(movieTitle);
            } else {
                txtTitle.setVisibility(View.GONE);
            }
        }

        // Update server label
        updateServerLabel();

        // Show episode controls only for series
        if (isTvSeries && episodeControls != null) {
            episodeControls.setVisibility(View.VISIBLE);
            updateEpisodeLabel();
        }

        // ─── Setup controls ───────────────────────────────────
        setupControls();
        setupCast();
        setupWebView(url);

        // Auto-hide controls after a longer delay (8s)
        scheduleHideControls();

        // Reliable tap detection on WebView via GestureDetector
        android.view.GestureDetector tapDetector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(android.view.MotionEvent e) {
                        toggleControls();
                        return false; // let the WebView process the tap normally too
                    }
                });
        webView.setOnTouchListener((v, event) -> {
            tapDetector.onTouchEvent(event);
            return false;
        });
    }

    // ─── Multi-Server URL Builder ─────────────────────────────
    private String buildServerUrl(int serverIndex) {
        if (serverIndex < 0 || serverIndex >= SERVERS.length) serverIndex = 0;
        if (isTvSeries) {
            return String.format(SERVERS[serverIndex][2], tmdbId, season, episode);
        } else {
            return String.format(SERVERS[serverIndex][1], tmdbId);
        }
    }

    private void updateServerLabel() {
        if (txtServerName != null) {
            txtServerName.setText(SERVERS[currentServerIndex][0]);
        }
        TextView floatingServerLabel = findViewById(R.id.floatingServerLabel);
        if (floatingServerLabel != null) {
            floatingServerLabel.setText(SERVERS[currentServerIndex][0]);
        }
    }

    private void changeEpisode(int delta) {
        if (!isTvSeries) return;
        int newEp = episode + delta;
        if (newEp < 1) {
            if (season > 1) {
                season--;
                newEp = 20;
            } else {
                Toast.makeText(this, "First episode", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        episode = newEp;
        updateEpisodeLabel();

        String url = buildServerUrl(currentServerIndex);
        if (webView != null) {
            progressBar.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        }
        Toast.makeText(this, (delta > 0 ? "▶ Next: " : "⏮ Prev: ")
                + "S" + season + " E" + episode, Toast.LENGTH_SHORT).show();
    }

    private void updateEpisodeLabel() {
        if (txtEpisodeLabel != null) {
            txtEpisodeLabel.setText("S" + season + " · E" + episode);
        }
    }

    private void showLoadingOverlay(String title, String subtitle) {
        if (loadingOverlay == null) return;
        if (loadingTitle != null && title != null) loadingTitle.setText(title);
        if (loadingSubtitle != null && subtitle != null) loadingSubtitle.setText(subtitle);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.bringToFront();
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void simulateCenterTap(WebView view) {
        if (view == null || view.getWidth() == 0) return;
        float x = view.getWidth() / 2f;
        float y = view.getHeight() / 2f;
        long downTime = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(
                downTime, downTime, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(
                downTime, downTime + 50, android.view.MotionEvent.ACTION_UP, x, y, 0);
        view.dispatchTouchEvent(down);
        view.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void injectKillerScript(WebView view) {
        String js = "javascript:(function(){"
                + "window.open=function(){return null;};"
                + "window.alert=function(){};"
                + "window.confirm=function(){return true;};"
                + "Object.defineProperty(document,'visibilityState',{value:'visible',writable:false});"
                // Force all links to open in same window — do NOT touch onclick handlers
                + "var fixLinks=function(){document.querySelectorAll('a[target=\"_blank\"]').forEach(function(a){a.target='_self';});};"
                + "fixLinks();"
                // Aggressive ad + overlay killer
                + "var killStuff=function(){"
                + "  var sel='iframe:not([src*=\"vidsrc\"]):not([src*=\"embed\"]):not([src*=\"vidlink\"]):not([src*=\"autoembed\"]):not([src*=\"2embed\"]):not([src*=\"moviesapi\"]):not([src*=\"nontongo\"]):not([src*=\"multiembed\"]):not([src*=\"player\"]):not([src*=\"stream\"]):not([src*=\"vid\"]),'"
                + "    +'div[id*=\"ad\"],div[class*=\"ad\"],div[id*=\"banner\"],div[class*=\"banner\"],'"
                + "    +'div[class*=\"popup\"],div[id*=\"popup\"],div[class*=\"overlay\"],div[id*=\"overlay\"],'"
                + "    +'div[class*=\"modal\"],div[id*=\"modal\"],a[href*=\"http\"][target=\"_blank\"]';"
                + "  document.querySelectorAll(sel).forEach(function(el){"
                + "    var r=el.getBoundingClientRect();"
                + "    if(r.width>window.innerWidth*0.5&&r.height>window.innerHeight*0.5&&!el.querySelector('video')){return;}"
                + "    el.remove();"
                + "  });"
                // Auto-play any video element
                + "  document.querySelectorAll('video').forEach(function(v){"
                + "    v.autoplay=true; v.removeAttribute('disabled');"
                + "    var p=v.play(); if(p&&p.catch)p.catch(function(){});"
                + "  });"
                // Click play buttons inside the player (NOT in ads)
                + "  var playSelectors=['.jw-icon-display','.plyr__control--overlaid','.vjs-big-play-button',"
                + "    'button[aria-label=\"Play\"]','button[title=\"Play\"]','.play-button','#play-button'];"
                + "  playSelectors.forEach(function(s){"
                + "    document.querySelectorAll(s).forEach(function(b){if(b.offsetParent!==null){try{b.click();}catch(e){}}});"
                + "  });"
                + "};"
                + "killStuff();"
                + "var obs=new MutationObserver(killStuff);"
                + "obs.observe(document.body,{childList:true,subtree:true});"
                + "setInterval(killStuff,800);"
                // White-screen / dead-embed detector
                + "var checks=0;"
                + "var detector=setInterval(function(){"
                + "  checks++;"
                + "  var html=(document.body&&document.body.innerText)?document.body.innerText.toLowerCase():'';"
                + "  var hasVideo=document.querySelector('video')||document.querySelector('iframe[src*=\"http\"]')||document.querySelector('.jw-video')||document.querySelector('.plyr');"
                + "  var dead=['not found','no source','source not available','404','no stream','unavailable','error loading','cannot find','failed to load','no embed'];"
                + "  var isDead=false;"
                + "  for(var k=0;k<dead.length;k++){if(html.indexOf(dead[k])!==-1){isDead=true;break;}}"
                + "  if(hasVideo&&!isDead){"
                + "    if(window.NativeBridge)window.NativeBridge.onPlayerReady();"
                + "    clearInterval(detector);"
                + "  }else if(isDead||checks>=6){"
                + "    if(window.NativeBridge)window.NativeBridge.onPlayerDead(isDead?'dead-content':'no-video-element');"
                + "    clearInterval(detector);"
                + "  }"
                + "},1000);"
                + "})()";
        view.evaluateJavascript(js, null);
    }

    private void switchToNextServer() {
        // Skip known-dead servers; pick the next working/unknown one
        currentServerIndex = findNextWorkingServer(currentServerIndex);
        // Mark current one dead so we don't loop back
        if (serverHealth[currentServerIndex] == null) {
            serverHealth[currentServerIndex] = true; // optimistic
        }
        String newUrl = buildServerUrl(currentServerIndex);
        updateServerLabel();
        pageLoadedSuccessfully = false;

        if (webView != null) {
            webView.loadUrl(newUrl);
        }

        handler.removeCallbacks(loadTimeoutRunnable);
        handler.postDelayed(loadTimeoutRunnable, LOAD_TIMEOUT_MS);
    }


    private void setupControls() {
        btnBack.setOnClickListener(v -> finish());

        btnFullscreen.setOnClickListener(v -> toggleFullscreen());

        btnPip.setOnClickListener(v -> enterPipMode());

        btnReload.setOnClickListener(v -> {
            if (webView != null) {
                webView.reload();
                progressBar.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Reloading…", Toast.LENGTH_SHORT).show();
            }
        });

        // ─── Server Switch Button ─────────────────────────────
        if (btnServerSwitch != null) {
            // Single tap → open selector dialog (only working servers selectable)
            btnServerSwitch.setOnClickListener(v -> showServerSelector());
            // Long press → quick switch to next working server
            btnServerSwitch.setOnLongClickListener(v -> {
                switchToNextServer();
                return true;
            });
        }

        if (btnPrevEpisode != null) {
            btnPrevEpisode.setOnClickListener(v -> changeEpisode(-1));
        }
        if (btnNextEpisode != null) {
            btnNextEpisode.setOnClickListener(v -> changeEpisode(+1));
        }

        btnLockControls.setOnClickListener(v -> {
            controlsLocked = !controlsLocked;
            if (controlsLocked) {
                setControlsVisibility(false);
                Toast.makeText(this, "Controls locked 🔒", Toast.LENGTH_SHORT).show();
            } else {
                setControlsVisibility(true);
                Toast.makeText(this, "Controls unlocked 🔓", Toast.LENGTH_SHORT).show();
                scheduleHideControls();
            }
        });
    }

    /**
     * Server picker, ranked fastest-first with dead entries struck through.
     *
     * <p>The old dialog listed servers in hard-coded array order and encoded
     * their state as an emoji prefix — ✅ 🟢 ⏳ ❌ — which gave no sense of
     * how much better one working server was than another, and pushed the
     * name sideways by two characters on every row. It also used the raw
     * platform dialog theme, so it appeared in system colours rather than the
     * app's, and its one error path was an untranslated Arabic toast.
     *
     * <p>Now each row carries the measured round-trip, the list is sorted by
     * it, unreachable servers are rendered with a strike-through and greyed
     * out, and the row currently playing is marked. Sorting by latency makes
     * the best choice the top choice — no reading required.
     */
    private void showServerSelector() {
        // Rank: reachable by ascending latency, then unprobed, then dead.
        Integer[] order = new Integer[SERVERS.length];
        for (int i = 0; i < SERVERS.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            long la = rankOf(a), lb = rankOf(b);
            return Long.compare(la, lb);
        });

        CharSequence[] rows = new CharSequence[SERVERS.length];
        final boolean[] selectable = new boolean[SERVERS.length];

        for (int pos = 0; pos < order.length; pos++) {
            int idx = order[pos];
            Boolean health = serverHealth[idx];
            boolean dead = Boolean.FALSE.equals(health);
            boolean current = idx == currentServerIndex;
            selectable[pos] = !dead;

            String name = SERVERS[idx][0];
            String detail;
            if (dead) {
                detail = getString(R.string.server_unavailable);
            } else if (health == null) {
                detail = getString(R.string.server_checking);
            } else {
                detail = getString(R.string.server_latency_ms, serverLatency[idx]);
            }
            if (current) detail = detail + " · " + getString(R.string.server_playing);

            SpannableString row = new SpannableString(name + "\n" + detail);

            // Detail line: smaller and quieter than the server name.
            int detailStart = name.length() + 1;
            row.setSpan(new RelativeSizeSpan(0.8f), detailStart, row.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            row.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(this,
                                    dead ? R.color.content_disabled : R.color.content_secondary)),
                    detailStart, row.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (dead) {
                // Struck through across the whole row, so an unusable server is
                // identifiable at a glance without reading its status text.
                row.setSpan(new StrikethroughSpan(), 0, row.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                row.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(this, R.color.content_disabled)),
                        0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (current) {
                row.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(this, R.color.accent_text)),
                        0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            rows[pos] = row;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.server_choose)
                .setItems(rows, (dialog, which) -> {
                    if (!selectable[which]) {
                        Snackbar.make(webView, R.string.server_unavailable_message,
                                Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    currentServerIndex = order[which];
                    triedServers.add(currentServerIndex);
                    autoSwitchAttempts = 0;
                    loadCurrentServer();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Sort key: latency for reachable servers, then unprobed, then dead. */
    private long rankOf(int idx) {
        Boolean h = serverHealth[idx];
        if (Boolean.TRUE.equals(h)) return serverLatency[idx];
        if (h == null) return LATENCY_DEAD - 1;   // unprobed sits above dead
        return LATENCY_DEAD;
    }

    private void setupCast() {
        try {
            CastContext castContext = CastContext.getSharedInstance(this);
            CastButtonFactory.setUpMediaRouteButton(this, castButton);

            if (castContext.getCastState() != CastState.NO_DEVICES_AVAILABLE) {
                castButton.setVisibility(View.VISIBLE);
            }

            castStateListener = newState -> {
                if (castButton != null) {
                    castButton.setVisibility(
                            newState != CastState.NO_DEVICES_AVAILABLE ? View.VISIBLE : View.GONE);
                }
            };
            castContext.addCastStateListener(castStateListener);
        } catch (Exception e) {
            if (castButton != null) castButton.setVisibility(View.GONE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(String url) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        // SECURITY: deny WebView access to local files / content providers /
        // cross-origin XHR. Streaming embeds never need these, and disabling
        // them blocks file:// exfiltration if a remote page is compromised.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // JS bridge: page reports back whether the embed actually has a video.
        // SECURITY: Every callback verifies the WebView's current URL is on our
        // whitelist before reacting — prevents a hijacked iframe / redirected
        // page from driving the native side.
        webView.addJavascriptInterface(new Object() {
            private boolean isCallerTrusted() {
                String currentUrl = webView != null ? webView.getUrl() : null;
                if (currentUrl == null) {
                    return false;
                }
                String host = hostOf(currentUrl.toLowerCase());
                if (host == null) {
                    return false;
                }
                // Trust the page we actually committed to. Embed servers
                // redirect to their own player domains, and requiring the final
                // host to be on the static whitelist meant onPlayerReady() was
                // discarded on a working server — the load timer then treated
                // it as dead and moved on, so the player cycled through every
                // server and never settled. The script that calls in here is
                // only injected into the page we navigated to.
                return host.equals(committedHost) || isAllowedDomain(currentUrl.toLowerCase());
            }

            @android.webkit.JavascriptInterface
            public void onPlayerReady() {
                if (!isCallerTrusted()) return;
                runOnUiThread(() -> {
                    pageLoadedSuccessfully = true;
                    autoSwitchAttempts = 0;
                    hideLoadingOverlay();
                });
            }

            @android.webkit.JavascriptInterface
            public void onPlayerDead(String reason) {
                if (!isCallerTrusted()) return;
                runOnUiThread(() -> {
                    if (!pageLoadedSuccessfully && autoSwitchAttempts < SERVERS.length - 1) {
                        autoSwitchAttempts++;
                        showLoadingOverlay(getString(R.string.loading_trying_another_server),
                                getString(R.string.loading_preparing_movie));
                        switchToNextServer();
                    } else if (autoSwitchAttempts >= SERVERS.length - 1) {
                        showLoadingOverlay(getString(R.string.loading_unavailable),
                                getString(R.string.loading_try_another));
                    }
                });
            }
        }, "NativeBridge");

        // Spoof user-agent to look like standard Chrome Mobile (bypass Cloudflare/Bot protection)
        String currentUserAgent = settings.getUserAgentString();
        String safeUserAgent = currentUserAgent.replace("; wv", "");
        settings.setUserAgentString(safeUserAgent);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();
                if (isAllowedDomain(reqUrl)) {
                    return false;
                }
                // Embed players chain through their own CDNs inside nested
                // iframes, and those hosts are neither predictable nor listed,
                // so sub-frames are always allowed.
                if (!request.isForMainFrame()) {
                    return false;
                }
                // A main-frame navigation with no user gesture is the player
                // redirecting itself — blocking those left a blank page on
                // every server that hands off to its own player domain. What
                // this guard is actually for is the tap-anywhere ad redirect,
                // which does carry a gesture.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !request.hasGesture()) {
                    return false;
                }
                return true; // Block popup
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (!request.isForMainFrame()) return; // ignore sub-resource errors
                // Hide WebView's ugly error page by loading blank
                runOnUiThread(() -> view.loadUrl("about:blank"));
                if (autoSwitchAttempts < SERVERS.length - 1) {
                    autoSwitchAttempts++;
                    runOnUiThread(() -> {
                        showLoadingOverlay(getString(R.string.loading_trying_another_server),
                                getString(R.string.loading_preparing_movie));
                        switchToNextServer();
                    });
                } else {
                    runOnUiThread(() -> showLoadingOverlay(
                            getString(R.string.loading_unavailable),
                            getString(R.string.loading_try_another)));
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse != null
                        && errorResponse.getStatusCode() >= 400) {
                    onReceivedError(view, request, null);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url1, android.graphics.Bitmap favicon) {
                String host = hostOf(url1 == null ? "" : url1.toLowerCase());
                if (host != null) {
                    committedHost = host;
                }
                super.onPageStarted(view, url1, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url1) {
                String host = hostOf(url1 == null ? "" : url1.toLowerCase());
                if (host != null) {
                    committedHost = host;
                }
                progressBar.setVisibility(View.GONE);
                handler.removeCallbacks(loadTimeoutRunnable);
                injectKillerScript(view);
                // Synthesize a real user-gesture tap on the WebView center to unlock autoplay
                handler.postDelayed(() -> simulateCenterTap(view), 1500);
                handler.postDelayed(() -> simulateCenterTap(view), 3500);
                super.onPageFinished(view, url1);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();

                // Playback traffic is never blocked. This check has to come
                // first and has to be generous: an embed player streams from
                // CDN hostnames that are not knowable in advance, and its
                // segments carry query strings, so an endsWith(".m3u8") test
                // misses "index.m3u8?token=…" — which is the common case.
                if (isPlaybackRequest(reqUrl) || isAllowedDomain(reqUrl)) {
                    return super.shouldInterceptRequest(view, request);
                }

                // Ad hosts are matched against the host, not the whole URL.
                // Matching the URL meant a path segment could trigger a block:
                // "ad-" alone hits every "upload-", "download-" and "head-" in
                // a path, which silently killed real playback requests.
                String host = hostOf(reqUrl);
                if (host != null && isAdHost(host)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new java.io.ByteArrayInputStream("".getBytes()));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            private View customView;
            private CustomViewCallback customViewCallback;

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                android.util.Log.d("WebPlayerJS",
                        cm.message() + " @ " + cm.sourceId() + ":" + cm.lineNumber());
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                ((FrameLayout) getWindow().getDecorView()).addView(customView,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                ((FrameLayout) getWindow().getDecorView()).removeView(customView);
                customView = null;
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            }
        });

        // Load the top-ranked server immediately; fallback runs silently on failure
        pageLoadedSuccessfully = false;
        handler.removeCallbacks(loadTimeoutRunnable);
        handler.postDelayed(loadTimeoutRunnable, LOAD_TIMEOUT_MS);
        webView.loadUrl(url);
        // Background: probe all servers so the picker shows only live ones
        probeAllServers();
    }

    /** Ping each server's homepage in parallel. Marks serverHealth[i]=true if reachable. */
    /**
     * Probes every server in parallel, recording how long each one takes to
     * answer for THIS title.
     *
     * <p>Two behaviours were added on top of the original fire-and-forget
     * sweep, and between them they are what stops the endless "One moment":
     *
     * <ol>
     *   <li>If the probe determines the server we are currently sitting on is
     *       unreachable, it switches immediately rather than letting the
     *       9-second load timer expire. A dead host usually fails its probe in
     *       well under three seconds.</li>
     *   <li>Once the whole sweep finishes, if nothing has loaded yet we jump
     *       straight to the fastest server that answered, instead of walking
     *       the list in array order one 9-second timeout at a time.</li>
     * </ol>
     */
    private void probeAllServers() {
        probesOutstanding.set(SERVERS.length);
        for (int i = 0; i < SERVERS.length; i++) {
            final int idx = i;
            final String probeUrl = buildServerUrl(idx);
            probeExecutor.execute(() -> {
                long ms = measureLatency(probeUrl);
                serverLatency[idx] = ms;
                serverHealth[idx] = ms != LATENCY_DEAD;

                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    // The server we are waiting on just came back dead —
                    // do not sit through the rest of the load timeout.
                    if (idx == currentServerIndex
                            && Boolean.FALSE.equals(serverHealth[idx])
                            && !pageLoadedSuccessfully) {
                        Log.d(TAG, "Current server " + SERVERS[idx][0]
                                + " failed its probe; switching now");
                        handler.removeCallbacks(loadTimeoutRunnable);
                        switchToNextServer();
                        return;
                    }

                    if (probesOutstanding.decrementAndGet() == 0) {
                        onProbeSweepComplete();
                    }
                });
            });
        }
    }

    /**
     * Called once every server has reported. If playback still has not
     * started, move to the fastest one that answered.
     */
    private void onProbeSweepComplete() {
        if (pageLoadedSuccessfully) return;

        int fastest = fastestWorkingServer();
        if (fastest < 0 || fastest == currentServerIndex) return;

        Log.d(TAG, "Probe sweep done; fastest is " + SERVERS[fastest][0]
                + " at " + serverLatency[fastest] + "ms");
        currentServerIndex = fastest;
        loadCurrentServer();
    }

    /** Index of the reachable server with the lowest round-trip, or -1. */
    private int fastestWorkingServer() {
        int best = -1;
        long bestMs = LATENCY_DEAD;
        for (int i = 0; i < SERVERS.length; i++) {
            if (!Boolean.TRUE.equals(serverHealth[i])) continue;
            if (serverLatency[i] < bestMs) {
                bestMs = serverLatency[i];
                best = i;
            }
        }
        return best;
    }

    /**
     * Round-trip to a server in milliseconds, or {@link #LATENCY_DEAD}.
     *
     * <p>The previous version accepted any status below 500, which counted a
     * 404 as healthy — so a server that simply does not carry this particular
     * title was ranked as working, chosen, and then showed a blank player.
     * Only 2xx and 3xx count now.
     */
    private long measureLatency(String url) {
        java.net.HttpURLConnection conn = null;
        long start = android.os.SystemClock.elapsedRealtime();
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) return LATENCY_DEAD;
            return android.os.SystemClock.elapsedRealtime() - start;
        } catch (Exception e) {
            return LATENCY_DEAD;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Loads whatever currentServerIndex points at, and arms the load timer. */
    private void loadCurrentServer() {
        String url = buildServerUrl(currentServerIndex);
        updateServerLabel();
        showLoadingOverlay(getString(R.string.loading_please_wait),
                getString(R.string.loading_preparing_movie));
        pageLoadedSuccessfully = false;
        handler.removeCallbacks(loadTimeoutRunnable);
        handler.postDelayed(loadTimeoutRunnable, LOAD_TIMEOUT_MS);
        if (webView != null) webView.loadUrl(url);
    }

    /** Find the next server marked alive (or unknown). Skips dead ones. */
    /**
     * The next server to try after {@code startFrom} has failed.
     *
     * <p>This used to walk the array in order, so a failure on entry 3 always
     * moved to entry 4 regardless of whether entry 4 had already been measured
     * as the slowest host in the list — or had already failed its probe. Each
     * wrong guess then cost a full load timeout.
     *
     * <p>Preference order now: fastest measured server that is still untried,
     * then anything not yet probed, then give up and step forward.
     */
    private int findNextWorkingServer(int startFrom) {
        int best = -1;
        long bestMs = LATENCY_DEAD;
        for (int i = 0; i < SERVERS.length; i++) {
            if (i == startFrom || triedServers.contains(i)) continue;
            if (!Boolean.TRUE.equals(serverHealth[i])) continue;
            if (serverLatency[i] < bestMs) {
                bestMs = serverLatency[i];
                best = i;
            }
        }
        if (best >= 0) return best;

        // Nothing measured is left: fall back to anything unprobed.
        for (int step = 1; step <= SERVERS.length; step++) {
            int idx = (startFrom + step) % SERVERS.length;
            if (triedServers.contains(idx)) continue;
            if (serverHealth[idx] == null) return idx;
        }
        return (startFrom + 1) % SERVERS.length;
    }

    /** Accept only numeric TMDB ids or IMDB ids (tt#######). Returns "" if invalid. */
    private static String sanitizeMediaId(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty() || t.length() > 16) return "";
        if (t.matches("\\d+")) return t;
        if (t.matches("tt\\d{6,10}")) return t;
        return "";
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * Hosts the player may navigate to.
     *
     * <p>Derived from {@link #SERVERS} so a server added to that list can never
     * be silently blocked here — which is exactly what the previous hard-coded
     * list did. It also replaces substring tests like {@code url.contains("stream")}
     * and {@code url.contains("player.")}, which matched a large share of the ad
     * and popunder domains this check exists to keep out.
     */
    private static final java.util.Set<String> ALLOWED_HOSTS = buildAllowedHosts();

    private static java.util.Set<String> buildAllowedHosts() {
        java.util.Set<String> hosts = new java.util.HashSet<>();
        for (String[] server : SERVERS) {
            for (int i = 1; i < server.length; i++) {
                String host = hostOf(server[i]);
                if (host != null) {
                    hosts.add(host);
                }
            }
        }
        // Players hand playback off to these; they never appear in SERVERS.
        java.util.Collections.addAll(hosts,
                // Redirect targets observed when each server is opened with an
                // Android user-agent. They are the host the player actually
                // ends up on, so leaving them out blocked playback outright.
                "vidfast.vc", "videasy.to", "111movies.net", "vidsrc.net", "vidsrc.xyz",
                // Hosts players hand playback off to.
                "youtube.com", "youtube-nocookie.com", "googlevideo.com", "ytimg.com",
                "rabbitstream.net", "rapid-cloud.co", "dokicloud.one",
                "megacloud.tv", "megacloud.club", "vidplay.site", "filemoon.sx");
        return hosts;
    }

    /** Host of a URL (or of a printf template), lower-cased, without any "www." */
    @androidx.annotation.Nullable
    private static String hostOf(String url) {
        try {
            String host = android.net.Uri.parse(url).getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(java.util.Locale.US);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True when the host is an ad or tracker host.
     *
     * <p>Named networks are matched as substrings; the generic "ad" family is
     * matched per host label, so {@code ads.example.com} is blocked while
     * {@code downloads.example.com} and {@code upload-cdn.example.com} — both
     * of which a substring test would have caught — are not.
     */
    private static boolean isAdHost(String host) {
        for (String adHost : AD_HOSTS) {
            if (host.contains(adHost)) {
                return true;
            }
        }
        for (String label : host.split("\\.")) {
            if (label.equals("ad") || label.equals("ads") || label.equals("adx")
                    || label.equals("adserver") || label.equals("adservice")
                    || label.startsWith("ad-") || label.startsWith("ads-")) {
                return true;
            }
        }
        return false;
    }

    /**
     * True for anything the video pipeline needs: manifests, media segments,
     * decryption keys, subtitles, and the scripts and styles a player is built
     * from. Query strings are stripped before the extension is examined,
     * because signed segment URLs almost always carry one.
     */
    private static boolean isPlaybackRequest(String url) {
        int cut = url.indexOf('?');
        String path = cut >= 0 ? url.substring(0, cut) : url;
        cut = path.indexOf('#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".ts")
                || path.endsWith(".m4s") || path.endsWith(".mp4") || path.endsWith(".m4v")
                || path.endsWith(".aac") || path.endsWith(".m4a") || path.endsWith(".webm")
                || path.endsWith(".key") || path.endsWith(".vtt") || path.endsWith(".srt")
                || path.endsWith(".js") || path.endsWith(".css")) {
            return true;
        }
        // HLS/DASH endpoints are frequently extension-less behind a CDN route.
        return path.contains("/hls/") || path.contains("/dash/")
                || path.contains("/manifest") || path.contains("/playlist")
                || path.contains("/segment") || path.contains("/chunk");
    }

    /** True when the URL is on a whitelisted streaming domain or a subdomain of one. */
    private boolean isAllowedDomain(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        for (String allowed : ALLOWED_HOSTS) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    // ─── Controls visibility ──────────────────────────────────

    private void toggleControls() {
        if (controlsLocked) return;
        setControlsVisibility(!controlsVisible);
        if (controlsVisible) {
            scheduleHideControls();
        }
    }

    private void setControlsVisibility(boolean visible) {
        controlsVisible = visible;
        float alpha = visible ? 1f : 0f;

        if (topBar != null) {
            topBar.animate().alpha(alpha).setDuration(250).withStartAction(() -> {
                if (visible) topBar.setVisibility(View.VISIBLE);
            }).withEndAction(() -> {
                if (!visible) topBar.setVisibility(View.GONE);
            }).start();
        }
        if (bottomBar != null) {
            bottomBar.animate().alpha(alpha).setDuration(250).withStartAction(() -> {
                if (visible) bottomBar.setVisibility(View.VISIBLE);
            }).withEndAction(() -> {
                if (!visible) bottomBar.setVisibility(View.GONE);
            }).start();
        }
        View floatingServerBtn = findViewById(R.id.floatingServerBtn);
        if (floatingServerBtn != null) {
            floatingServerBtn.animate().alpha(alpha).setDuration(250).withStartAction(() -> {
                if (visible) floatingServerBtn.setVisibility(View.VISIBLE);
            }).withEndAction(() -> {
                if (!visible) floatingServerBtn.setVisibility(View.GONE);
            }).start();
        }
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, 8000);
    }

    // ─── Fullscreen ───────────────────────────────────────────

    private void toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        isFullscreen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        enterImmersiveMode();
        if (btnFullscreen != null) {
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit_24);
        }
    }

    private void exitFullscreen() {
        isFullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (btnFullscreen != null) {
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_24);
        }
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // ─── PiP ──────────────────────────────────────────────────

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int w = webView != null && webView.getWidth() > 0 ? webView.getWidth() : 16;
            int h = webView != null && webView.getHeight() > 0 ? webView.getHeight() : 9;
            Rational aspect = new Rational(w, h);
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspect);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true);
            }
            enterPictureInPictureMode(builder.build());
        } else {
            Toast.makeText(this, "PiP not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterPipMode();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPip, newConfig);
        if (topBar != null) topBar.setVisibility(isInPip ? View.GONE : View.VISIBLE);
        if (bottomBar != null) bottomBar.setVisibility(isInPip ? View.GONE : View.VISIBLE);
    }

    // ─── Lifecycle ────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (probeExecutor != null) probeExecutor.shutdownNow();
        try {
            if (castStateListener != null) {
                CastContext.getSharedInstance(this).removeCastStateListener(castStateListener);
            }
        } catch (Exception ignored) {}
        if (webView != null) webView.destroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullscreen) {
            enterImmersiveMode();
        }
    }
}
