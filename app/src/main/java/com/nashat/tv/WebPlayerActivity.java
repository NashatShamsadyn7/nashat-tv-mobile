package com.nashat.tv;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
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

    private WebView webView;
    private ProgressBar progressBar;

    // Controls
    private View topBar, bottomBar;
    private ImageButton btnBack, btnPip, btnFullscreen, btnReload, btnLockControls, btnServerSwitch;
    private TextView txtTitle, txtServerName;
    private MediaRouteButton castButton;

    // State
    private boolean isFullscreen = true;
    private boolean controlsLocked = false;
    private boolean controlsVisible = true;
    private Handler handler;
    private CastStateListener castStateListener;

    // ─── Multi-Server System ──────────────────────────────────
    private static final String[][] SERVERS = {
        // { "Display Name", "Movie URL pattern", "TV URL pattern" }
        // %s = tmdbId, %d/%d = season/episode
        {"VidSrc ICU",   "https://vidsrc.icu/embed/movie/%s",             "https://vidsrc.icu/embed/tv/%s/%d/%d"},
        {"VidSrc PRO",   "https://vidsrc.pro/embed/movie/%s",             "https://vidsrc.pro/embed/tv/%s/%d/%d"},
        {"AutoEmbed",    "https://player.autoembed.cc/embed/movie/%s",     "https://player.autoembed.cc/embed/tv/%s/%d/%d"},
        {"2Embed",       "https://www.2embed.cc/embed/%s",                 "https://www.2embed.cc/embedtv/%s&s=%d&e=%d"},
        {"VidSrc TO",    "https://vidsrc.to/embed/movie/%s",               "https://vidsrc.to/embed/tv/%s/%d/%d"},
        {"Embed SU",     "https://embed.su/embed/movie/%s",                "https://embed.su/embed/tv/%s/%d/%d"},
        {"VidLink PRO",  "https://vidlink.pro/movie/%s",                   "https://vidlink.pro/tv/%s/%d/%d"},
        {"MovieAPI",     "https://moviesapi.club/movie/%s",                "https://moviesapi.club/tv/%s/%d/%d"},
        {"NontonGo",     "https://www.nontongo.win/embed/movie/%s",        "https://www.nontongo.win/embed/tv/%s/%d/%d"},
        {"VidSrc NL",    "https://player.vidsrc.nl/embed/movie/%s",        "https://player.vidsrc.nl/embed/tv/%s/%d/%d"},
    };

    private int currentServerIndex = 0;
    private String tmdbId = "";
    private boolean isTvSeries = false;
    private int season = 1;
    private int episode = 1;

    private final Runnable hideControlsRunnable = () -> setControlsVisibility(false);

    // Massive AdBlocker Hosts List
    private static final String[] AD_HOSTS = {
            "ads.", "popcash", "popunder", "propeller", "adx.", "onclick",
            "syndication", "doubleclick", "adult", "betting", "casino",
            "ad-", "tracking", "analytics", "pixel", "beacon", "exoclick",
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

        // ─── Get movie/tv info from Intent ───────────────────
        tmdbId = getIntent().getStringExtra("tmdbId");
        if (tmdbId == null) tmdbId = "";

        isTvSeries = getIntent().getBooleanExtra("isTvSeries", false);
        season = getIntent().getIntExtra("season", 1);
        episode = getIntent().getIntExtra("episode", 1);
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

        // ─── Setup controls ───────────────────────────────────
        setupControls();
        setupCast();
        setupWebView(url);

        // Auto-hide controls after 4s
        scheduleHideControls();

        // Tap anywhere on webView to toggle controls
        webView.setOnClickListener(v -> toggleControls());
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
    }

    private void switchToNextServer() {
        currentServerIndex = (currentServerIndex + 1) % SERVERS.length;
        String newUrl = buildServerUrl(currentServerIndex);
        updateServerLabel();

        if (webView != null) {
            progressBar.setVisibility(View.VISIBLE);
            webView.loadUrl(newUrl);
        }

        Toast.makeText(this, "🔄 Switched to: " + SERVERS[currentServerIndex][0], Toast.LENGTH_SHORT).show();
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
            btnServerSwitch.setOnClickListener(v -> switchToNextServer());
            btnServerSwitch.setOnLongClickListener(v -> {
                // Long press shows a dialog with all servers
                showServerSelector();
                return true;
            });
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

    private void showServerSelector() {
        String[] serverNames = new String[SERVERS.length];
        for (int i = 0; i < SERVERS.length; i++) {
            serverNames[i] = (i == currentServerIndex ? "✅ " : "   ") + SERVERS[i][0];
        }

        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("🎬 Select Streaming Server")
                .setItems(serverNames, (dialog, which) -> {
                    currentServerIndex = which;
                    String newUrl = buildServerUrl(currentServerIndex);
                    updateServerLabel();
                    if (webView != null) {
                        progressBar.setVisibility(View.VISIBLE);
                        webView.loadUrl(newUrl);
                    }
                    Toast.makeText(this, "▶ Now using: " + SERVERS[currentServerIndex][0], Toast.LENGTH_SHORT).show();
                })
                .show();
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

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Spoof user-agent to look like standard Chrome Mobile (bypass Cloudflare/Bot protection)
        String currentUserAgent = settings.getUserAgentString();
        String safeUserAgent = currentUserAgent.replace("; wv", "");
        settings.setUserAgentString(safeUserAgent);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();
                // Allow known streaming server domains
                if (isAllowedDomain(reqUrl)) {
                    return false;
                }
                return true; // Block popup
            }

            @Override
            public void onPageFinished(WebView view, String url1) {
                progressBar.setVisibility(View.GONE);
                // Inject ad cleaner + popup blocker
                view.evaluateJavascript("javascript:(function() { " +
                        "window.open = function() { return null; }; " +
                        "var clearAds = function() {" +
                        "  var links = document.getElementsByTagName('a');" +
                        "  for (var i=0; i<links.length; i++) { links[i].target = '_self'; }" +
                        "  var ads = document.querySelectorAll('iframe:not([src*=\"vidsrc\"]):not([src*=\"embed\"]):not([src*=\"vidlink\"]):not([src*=\"autoembed\"]):not([src*=\"2embed\"]):not([src*=\"moviesapi\"]):not([src*=\"nontongo\"]), div[id*=\"ad\"], div[class*=\"ad\"], div[id*=\"banner\"], div[class*=\"banner\"]');" +
                        "  for (var i=0; i<ads.length; i++) { ads[i].remove(); }" +
                        "}; clearAds();" +
                        "var observer = new MutationObserver(function() { clearAds(); });" +
                        "observer.observe(document.body, {childList: true, subtree: true});" +
                        "})()", null);
                super.onPageFinished(view, url1);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();

                // Whitelist streaming essentials
                if (isAllowedDomain(reqUrl) ||
                        reqUrl.endsWith(".m3u8") || reqUrl.endsWith(".mp4") ||
                        reqUrl.contains("cdnjs") || reqUrl.contains("jsdelivr") ||
                        reqUrl.contains(".css") || reqUrl.contains("fonts.")) {
                    return super.shouldInterceptRequest(view, request);
                }

                for (String host : AD_HOSTS) {
                    if (reqUrl.contains(host)) {
                        return new WebResourceResponse("text/plain", "utf-8",
                                new java.io.ByteArrayInputStream("".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            private View customView;
            private CustomViewCallback customViewCallback;

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

        // Load the first server
        webView.loadUrl(url);
    }

    /** Check if a URL belongs to a whitelisted streaming domain */
    private boolean isAllowedDomain(String url) {
        return url.contains("vidsrc") || url.contains("embed.su") || url.contains("vidlink") ||
                url.contains("autoembed") || url.contains("2embed") || url.contains("moviesapi") ||
                url.contains("nontongo") || url.contains("youtube.com") || url.contains("googlevideo.com") ||
                url.contains("player.") || url.contains("stream") || url.contains("rabbitstream") ||
                url.contains("rapid-cloud") || url.contains("dokicloud") || url.contains("megacloud");
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
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, 4000);
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
