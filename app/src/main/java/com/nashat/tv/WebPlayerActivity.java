package com.nashat.tv;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.nashat.tv.player.EpisodeNav;
import com.nashat.tv.player.ServerHealth;
import com.nashat.tv.player.ServerPrefs;
import com.nashat.tv.player.StreamServers;
import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.tmdb.TmdbModels.Video;
import com.nashat.tv.tmdb.TmdbModels.Videos;
import com.nashat.tv.util.AdBlocker;
import com.nashat.tv.util.HttpClient;
import com.nashat.tv.util.StreamSniffer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Plays one film or episode, ported from the desktop client's
 * {@code components/player/MoviePlayerModal.tsx}.
 *
 * <h3>How a source is chosen</h3>
 *
 * <p>Every source is probed in parallel the moment the screen opens. The one
 * that gets loaded is, in order of preference: the source that last worked for
 * <em>this exact title</em> if it is healthy right now, otherwise the
 * highest-ranked healthy source. Ranking is health, then reliability, then the
 * user's own votes, then speed — speed last, because a source that answers
 * instantly with an ad shell is the fastest thing in the list.
 *
 * <p>A source that has not loaded within {@link #LOAD_TIMEOUT_MS} is marked
 * failed for this session and the next candidate is tried. When all of them
 * fail, the trailer is offered rather than an apology.
 *
 * <h3>Two playback surfaces</h3>
 *
 * <p>The embed plays in a WebView, as on the desktop. In parallel
 * {@link StreamSniffer} watches the page's traffic, and if it catches the
 * manifest the film moves to ExoPlayer — no ads, no embedded-browser checks,
 * real controls. If that fails the WebView is still there.
 */
public class WebPlayerActivity extends AppCompatActivity {

    private static final String TAG = "WebPlayerActivity";

    public static final String EXTRA_TMDB_ID = "tmdb_id";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SEASON = "season";
    public static final String EXTRA_EPISODE = "episode";

    /**
     * The exact identity the desktop client sends.
     *
     * <p>Not WebView's own string with {@code "; wv"} removed, which is what
     * this used to do. The remainder still read {@code (Linux; Android …) …
     * Mobile Safari}, and several sources fingerprint an in-app browser from
     * the Android+Mobile pair rather than from {@code wv} — answering with a
     * full-page "In-App Browser Detected" instead of a video.
     */
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    /**
     * How long a source gets to load before it is given up on.
     *
     * <p>Matches the desktop's 12s. Generous on purpose: these embeds are slow
     * on a cold hit, and cutting away from one that was about to work costs the
     * user another full load somewhere else.
     */
    private static final long LOAD_TIMEOUT_MS = 12_000;

    /**
     * How long a source must keep playing before it is remembered as the one
     * that works for this title.
     *
     * <p>Without the delay, a source the user immediately abandons would be
     * saved as their preference and served first next time.
     */
    private static final long REMEMBER_DELAY_MS = 8_000;

    /**
     * Hard ceiling on the loading overlay.
     *
     * <p>Some embeds hand off to a nested cross-origin frame that the injected
     * script cannot see into, so the "ready" callback never arrives even while
     * the film plays perfectly well behind the overlay. Holding an opaque
     * spinner over working video is the worst of the available outcomes.
     */
    private static final long OVERLAY_CEILING_MS = 7_000;

    // ─── Media identity ───────────────────────────────────────

    private int tmdbId;
    private Kind kind = Kind.MOVIE;
    private String titleText = "";
    private int season = 1;
    private int episode = 1;

    // ─── Source state ─────────────────────────────────────────

    private ServerHealth health;
    private ServerPrefs prefs;
    private List<ServerHealth.Entry> entries;

    @Nullable private String activeServerId;
    private final Set<String> failedServers = new HashSet<>();
    private boolean autoPicked = false;
    private boolean pageReady = false;

    private EpisodeNav.Nav episodeNav;

    // ─── Views ────────────────────────────────────────────────

    private WebView webView;
    private PlayerView nativePlayerView;
    private View topBar, bottomBar, loadingOverlay;
    private TextView txtTitle, txtEpisodeLabel, loadingTitle, loadingSubtitle;
    private ProgressBar loadingSpinner;
    private MaterialButton btnServer, btnWatchTrailer, btnPickServer;
    private ImageButton btnPrevEpisode, btnNextEpisode;
    private MediaRouteButton castButton;
    private CastStateListener castStateListener;

    // ─── Native handoff ───────────────────────────────────────

    private final StreamSniffer sniffer = new StreamSniffer();
    private ExoPlayer nativePlayer;
    private boolean nativeMode = false;
    private boolean nativeFailedHere = false;
    private volatile String committedUrl = "";

    // ─── Timers ───────────────────────────────────────────────

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadTimeout = this::onLoadTimedOut;
    private final Runnable rememberSource = this::commitRememberedSource;
    private final Runnable overlayCeiling = this::hideOverlay;

    // ══════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setContentView(R.layout.activity_web_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        tmdbId = getIntent().getIntExtra(EXTRA_TMDB_ID, 0);
        kind = Kind.fromPath(getIntent().getStringExtra(EXTRA_KIND));
        titleText = getIntent().getStringExtra(EXTRA_TITLE);
        if (titleText == null) titleText = "";
        season = Math.max(1, getIntent().getIntExtra(EXTRA_SEASON, 1));
        episode = Math.max(1, getIntent().getIntExtra(EXTRA_EPISODE, 1));

        prefs = new ServerPrefs(this);
        health = new ServerHealth(HttpClient.get(this));

        bindViews();
        setUpWebView();
        setUpCast();

        if (tmdbId <= 0) {
            showFailure(getString(R.string.player_unavailable_title),
                    getString(R.string.player_unavailable_body));
            return;
        }

        showLoading(getString(R.string.player_finding_source),
                getString(R.string.player_finding_source_body));
        startProbe();
        if (kind == Kind.TV) resolveEpisodeNav();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
        // Keep playing in PiP; that is what PiP is for. Anywhere else, audio
        // continuing over another app is a bug.
        if (nativePlayer != null && !isInPipCompat()) nativePlayer.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        sniffer.disarm();
        releaseNativePlayer();
        if (health != null) health.shutdown();
        try {
            if (castStateListener != null) {
                CastContext.getSharedInstance(this).removeCastStateListener(castStateListener);
            }
        } catch (Exception ignored) {
        }
        if (webView != null) {
            // Detach before destroying: a WebView torn down while still in the
            // hierarchy can leak its rendering surface.
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    // ══════════════════════════════════════════════════════════
    //  Views
    // ══════════════════════════════════════════════════════════

    private void bindViews() {
        webView = findViewById(R.id.webView);
        nativePlayerView = findViewById(R.id.nativePlayerView);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingTitle = findViewById(R.id.loadingTitle);
        loadingSubtitle = findViewById(R.id.loadingSubtitle);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        txtTitle = findViewById(R.id.txtTitle);
        txtEpisodeLabel = findViewById(R.id.txtEpisodeLabel);
        btnServer = findViewById(R.id.btnServer);
        btnWatchTrailer = findViewById(R.id.btnWatchTrailer);
        btnPickServer = findViewById(R.id.btnPickServer);
        btnPrevEpisode = findViewById(R.id.btnPrevEpisode);
        btnNextEpisode = findViewById(R.id.btnNextEpisode);
        castButton = findViewById(R.id.webCastButton);

        txtTitle.setText(titleText);
        updateEpisodeLabel();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPip).setOnClickListener(v -> enterPip());
        findViewById(R.id.btnReload).setOnClickListener(v -> reloadCurrentSource());

        btnServer.setOnClickListener(v -> showSourcePicker());
        btnPickServer.setOnClickListener(v -> showSourcePicker());
        btnWatchTrailer.setOnClickListener(v -> playTrailer());

        btnPrevEpisode.setOnClickListener(v -> {
            if (episodeNav != null && episodeNav.previous != null) goToEpisode(episodeNav.previous);
        });
        btnNextEpisode.setOnClickListener(v -> {
            if (episodeNav != null && episodeNav.next != null) goToEpisode(episodeNav.next);
        });

        updateSourceLabel();
    }

    private void updateEpisodeLabel() {
        if (kind != Kind.TV) {
            txtEpisodeLabel.setVisibility(View.GONE);
            return;
        }
        txtEpisodeLabel.setVisibility(View.VISIBLE);
        txtEpisodeLabel.setText(getString(R.string.player_season_episode, season, episode));
    }

    private void updateSourceLabel() {
        int index = activeServerId == null ? -1 : StreamServers.indexOf(activeServerId);
        btnServer.setText(index < 0
                ? getString(R.string.player_choose_source)
                : StreamServers.at(index).label);
    }

    // ══════════════════════════════════════════════════════════
    //  Source selection
    // ══════════════════════════════════════════════════════════

    private void startProbe() {
        health.probe(kind, tmdbId, season, episode, DESKTOP_USER_AGENT,
                new ServerHealth.Listener() {
                    @Override
                    public void onHealthChanged(List<ServerHealth.Entry> updated) {
                        entries = updated;
                        // Start as soon as *something* is known to work rather
                        // than waiting for the whole sweep. The first healthy
                        // reply is usually seconds ahead of the slowest.
                        if (!autoPicked) autoPick();
                    }

                    @Override
                    public void onSweepComplete(List<ServerHealth.Entry> updated) {
                        entries = updated;
                        if (!autoPicked) autoPick();
                    }
                });
    }

    /**
     * Picks a source: the one remembered for this title if it is healthy,
     * otherwise the best-ranked healthy one.
     */
    private void autoPick() {
        if (entries == null || activeServerId != null) return;

        String remembered = prefs.lastUsed(contentKey());
        if (remembered != null) {
            for (ServerHealth.Entry e : entries) {
                if (e.id().equals(remembered) && e.status == ServerHealth.Status.OK) {
                    autoPicked = true;
                    loadSource(e.id());
                    return;
                }
            }
        }

        ServerHealth.Entry best = bestAvailable();
        if (best == null) {
            // Every probe that has resolved so far has failed. Only give up
            // once nothing is still checking.
            if (!anyStillChecking()) offerTrailerFallback();
            return;
        }
        autoPicked = true;
        loadSource(best.id());
    }

    @Nullable
    private ServerHealth.Entry bestAvailable() {
        if (entries == null) return null;
        for (ServerHealth.Entry e : ServerHealth.sorted(entries, prefs::scoreOf)) {
            if (e.status == ServerHealth.Status.OK && !failedServers.contains(e.id())) {
                return e;
            }
        }
        return null;
    }

    private boolean anyStillChecking() {
        if (entries == null) return true;
        for (ServerHealth.Entry e : entries) {
            if (e.status == ServerHealth.Status.CHECKING) return true;
        }
        return false;
    }

    private String contentKey() {
        return ServerPrefs.contentKey(kind, tmdbId, season, episode);
    }

    private void loadSource(String serverId) {
        int index = StreamServers.indexOf(serverId);
        if (index < 0) return;

        activeServerId = serverId;
        pageReady = false;
        nativeFailedHere = false;
        updateSourceLabel();

        showLoading(getString(R.string.player_loading_title),
                getString(R.string.player_loading_body,
                        StreamServers.at(index).label));

        handler.removeCallbacks(loadTimeout);
        handler.removeCallbacks(rememberSource);
        handler.removeCallbacks(overlayCeiling);
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);

        releaseNativePlayer();
        exitNativeMode();
        sniffer.arm(this::onStreamCaptured);

        String url = StreamServers.at(index).url(kind, tmdbId, season, episode);
        if (webView != null) webView.loadUrl(url);
    }

    private void reloadCurrentSource() {
        if (activeServerId == null) {
            startProbe();
            return;
        }
        // A manual reload is the user disagreeing with the automatic verdict,
        // so the source gets its failure record cleared.
        failedServers.remove(activeServerId);
        loadSource(activeServerId);
    }

    /** A source that never loaded. Mark it and move on. */
    private void onLoadTimedOut() {
        if (pageReady || activeServerId == null) return;

        Log.i(TAG, "Source timed out: " + activeServerId);
        failedServers.add(activeServerId);

        ServerHealth.Entry next = bestAvailable();
        if (next == null) {
            if (anyStillChecking()) {
                // Something may yet come back healthy — wait for it rather than
                // declaring the title unavailable.
                handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
                return;
            }
            offerTrailerFallback();
            return;
        }
        showLoading(getString(R.string.player_trying_next_title),
                getString(R.string.player_trying_next_body));
        activeServerId = null;
        loadSource(next.id());
    }

    /** Called once a source has played long enough to be worth remembering. */
    private void commitRememberedSource() {
        if (activeServerId != null && pageReady) {
            prefs.rememberLastUsed(contentKey(), activeServerId);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Source picker
    // ══════════════════════════════════════════════════════════

    private void showSourcePicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this)
                .inflate(R.layout.sheet_server_picker, null);
        LinearLayout list = sheet.findViewById(R.id.serverList);

        List<ServerHealth.Entry> ordered = entries == null
                ? java.util.Collections.emptyList()
                : ServerHealth.sorted(entries, prefs::scoreOf);

        for (ServerHealth.Entry entry : ordered) {
            list.addView(buildServerRow(list, entry, dialog));
        }

        dialog.setContentView(sheet);
        dialog.show();
    }

    private View buildServerRow(ViewGroup parent, ServerHealth.Entry entry,
                                BottomSheetDialog dialog) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_server, parent, false);

        ImageView statusIcon = row.findViewById(R.id.statusIcon);
        TextView name = row.findViewById(R.id.serverName);
        TextView status = row.findViewById(R.id.serverStatus);
        View activeMark = row.findViewById(R.id.activeMark);
        ImageButton voteUp = row.findViewById(R.id.btnVoteUp);
        ImageButton voteDown = row.findViewById(R.id.btnVoteDown);

        name.setText(entry.label());
        status.setText(describe(entry));

        boolean dead = entry.status == ServerHealth.Status.FAIL
                || failedServers.contains(entry.id());

        statusIcon.setImageResource(dead ? R.drawable.ic_close_24
                : entry.status == ServerHealth.Status.OK ? R.drawable.ic_check
                : R.drawable.ic_info_24);
        statusIcon.setColorFilter(ContextCompat.getColor(this,
                dead ? R.color.server_fail
                        : entry.status == ServerHealth.Status.OK ? R.color.server_ok
                        : R.color.server_checking));

        // Dead sources are dimmed, not removed. Health checks are a prediction,
        // and a user who knows a particular source works for a particular title
        // must still be able to reach it.
        row.setAlpha(dead ? 0.55f : 1f);

        activeMark.setVisibility(entry.id().equals(activeServerId)
                ? View.VISIBLE : View.INVISIBLE);

        applyVoteTint(voteUp, prefs.vote(entry.id()) == ServerPrefs.VOTE_UP);
        applyVoteTint(voteDown, prefs.vote(entry.id()) == ServerPrefs.VOTE_DOWN);

        voteUp.setOnClickListener(v -> {
            prefs.setVote(entry.id(), ServerPrefs.VOTE_UP);
            applyVoteTint(voteUp, prefs.vote(entry.id()) == ServerPrefs.VOTE_UP);
            applyVoteTint(voteDown, false);
        });
        voteDown.setOnClickListener(v -> {
            prefs.setVote(entry.id(), ServerPrefs.VOTE_DOWN);
            applyVoteTint(voteDown, prefs.vote(entry.id()) == ServerPrefs.VOTE_DOWN);
            applyVoteTint(voteUp, false);
        });

        row.setOnClickListener(v -> {
            dialog.dismiss();
            failedServers.remove(entry.id());
            autoPicked = true;
            loadSource(entry.id());
        });
        return row;
    }

    private void applyVoteTint(ImageButton button, boolean on) {
        button.setColorFilter(ContextCompat.getColor(this,
                on ? R.color.accent_text : R.color.content_tertiary));
    }

    /** One line describing a source's state: speed when healthy, why not when not. */
    private String describe(ServerHealth.Entry entry) {
        if (failedServers.contains(entry.id())) {
            return getString(R.string.player_source_did_not_load);
        }
        switch (entry.status) {
            case OK:
                String speed = getString(R.string.player_source_latency, entry.latencyMs);
                return entry.reliable() ? speed
                        : speed + "  ·  " + getString(R.string.player_source_unreliable);
            case CHECKING:
                return getString(R.string.player_source_checking);
            default:
                return getString(R.string.player_source_unavailable);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  WebView
    // ══════════════════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(DESKTOP_USER_AGENT);

        // No second window, ever. This is the pop-under's primary route, and
        // refusing it here means onCreateWindow is never even reached.
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        // Deny the page access to local files, content providers and
        // cross-origin XHR. A streaming embed needs none of these, and
        // disabling them removes file:// exfiltration if a source is
        // compromised.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new Bridge(), "NativeBridge");
        webView.setWebViewClient(new EmbedClient());
        webView.setWebChromeClient(new EmbedChromeClient());
    }

    /**
     * JS → native callbacks.
     *
     * <p><b>Threading:</b> every method here runs on WebView's private
     * JavaBridge thread, never on the UI thread. That is why the trust check
     * reads {@link #committedUrl} instead of calling {@code webView.getUrl()}:
     * WebView's public API is UI-thread-only, and called from here it does not
     * throw — it silently returns null. That one line previously made every
     * callback fail its own trust check, so the ready signal was discarded
     * every single time and the overlay never lifted.
     */
    private final class Bridge {

        private boolean trusted() {
            String url = committedUrl;
            return url != null && !url.isEmpty();
        }

        @JavascriptInterface
        public void onPlayerReady() {
            if (!trusted()) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                pageReady = true;
                handler.removeCallbacks(loadTimeout);
                hideOverlay();
                handler.postDelayed(rememberSource, REMEMBER_DELAY_MS);
            });
        }

        @JavascriptInterface
        public void onPlayerDead(String reason) {
            if (!trusted()) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || pageReady) return;
                Log.i(TAG, "Source reported dead: " + reason);
                onLoadTimedOut();
            });
        }
    }

    private final class EmbedClient extends WebViewClient {

        /**
         * The gate that keeps the app in the foreground.
         *
         * <p>Anything that is not http(s) is refused outright — {@code intent:},
         * {@code market:}, {@code whatsapp:} and friends exist here only to
         * throw the viewer into another app. Sub-frames are allowed to go
         * wherever they like, because an embed genuinely does chain through
         * unpredictable CDN hosts inside nested frames; the main frame may only
         * move between known source hosts.
         */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (!AdBlocker.isSchemeAllowed(url)) {
                Log.i(TAG, "Blocked non-web navigation: " + url);
                return true;
            }
            if (!request.isForMainFrame()) return false;
            if (AdBlocker.isAdRequest(url)) return true;

            String host = AdBlocker.hostOf(url);
            if (host != null && isSourceHost(host)) return false;

            // A top-level navigation to anywhere else is a redirect, whether or
            // not it carries a gesture.
            Log.i(TAG, "Blocked top-level redirect: " + url);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Playback traffic is never touched. First, and generously: an
            // embed streams from hostnames that cannot be known in advance.
            if (AdBlocker.isPlaybackRequest(url)) {
                offerToSniffer(request, url);
                return super.shouldInterceptRequest(view, request);
            }
            if (AdBlocker.isAdRequest(url)) {
                return AdBlocker.blockedResponse();
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            if (url != null) committedUrl = url;
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url != null) committedUrl = url;

            injectGuards(view);

            // NOTE: loadTimeout is deliberately NOT cancelled here. A page can
            // finish loading and still show nothing — that is exactly what a
            // dead source looks like — so the watchdog stays armed until the
            // page reports a real player, and onPlayerReady cancels it.
            handler.removeCallbacks(overlayCeiling);
            handler.postDelayed(overlayCeiling, OVERLAY_CEILING_MS);

            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    android.webkit.WebResourceError error) {
            if (!request.isForMainFrame()) return;
            runOnUiThread(WebPlayerActivity.this::onLoadTimedOut);
        }
    }

    /** Last line of defence on windows: refuse to create one. */
    private final class EmbedChromeClient extends WebChromeClient {
        private View customView;
        private CustomViewCallback customViewCallback;

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      android.os.Message resultMsg) {
            Log.i(TAG, "Blocked popup window");
            return false;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            ((FrameLayout) getWindow().getDecorView()).addView(view,
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            if (webView != null) webView.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            ((FrameLayout) getWindow().getDecorView()).removeView(customView);
            customView = null;
            if (webView != null && !nativeMode) webView.setVisibility(View.VISIBLE);
            if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        }
    }

    private boolean isSourceHost(String host) {
        for (String allowed : StreamServers.hosts()) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) return true;
        }
        return false;
    }

    private void offerToSniffer(WebResourceRequest request, String url) {
        if (!sniffer.isArmed()) return;
        String host = AdBlocker.hostOf(url);
        if (host != null && AdBlocker.isAdHost(host)) return;
        sniffer.offer(url, request.getRequestHeaders(), committedUrl);
    }

    /**
     * Injects the ad guard and the player detector.
     *
     * <p>The detector reports back on the bridge: a page holding a real
     * {@code <video>} with a source is what lifts the overlay, and a page whose
     * body reads like a block notice is what triggers moving on.
     */
    private void injectGuards(WebView view) {
        view.evaluateJavascript(AdBlocker.script(), null);
        view.evaluateJavascript(detectorScript(), null);
    }

    private String detectorScript() {
        return "(function(){\n"
                + "if(window.__nashatDetect)return;window.__nashatDetect=true;\n"
                + "var DEAD=['in-app browser','unsupported browser','embedded browser',\n"
                + "  'open in external browser','browser not supported','video not found',\n"
                + "  'no sources found','media not available','this video is unavailable'];\n"
                + "function bodyLooksDead(){\n"
                + "  try{var t=(document.body.innerText||'').toLowerCase();\n"
                + "    if(t.length>4000)return false;\n"
                + "    for(var i=0;i<DEAD.length;i++)if(t.indexOf(DEAD[i])>=0)return true;\n"
                + "  }catch(e){}\n"
                + "  return false;\n"
                + "}\n"
                + "function hasPlayer(){\n"
                + "  try{\n"
                + "    var v=document.getElementsByTagName('video');\n"
                + "    for(var i=0;i<v.length;i++){\n"
                + "      if(v[i].src||v[i].currentSrc||v[i].readyState>0)return true;\n"
                + "    }\n"
                // A nested cross-origin frame cannot be inspected, so its mere
                // presence counts: it is where the player almost always lives.
                + "    if(document.querySelectorAll('iframe').length>0)return true;\n"
                + "  }catch(e){}\n"
                + "  return false;\n"
                + "}\n"
                + "var tries=0;\n"
                + "var timer=setInterval(function(){\n"
                + "  tries++;\n"
                + "  if(bodyLooksDead()){clearInterval(timer);\n"
                + "    try{NativeBridge.onPlayerDead('blocked');}catch(e){}return;}\n"
                + "  if(hasPlayer()){clearInterval(timer);\n"
                + "    try{NativeBridge.onPlayerReady();}catch(e){}return;}\n"
                + "  if(tries>40)clearInterval(timer);\n"
                + "},250);\n"
                + "})();";
    }

    // ══════════════════════════════════════════════════════════
    //  Native handoff
    // ══════════════════════════════════════════════════════════

    /**
     * The embed revealed its stream — take it over.
     *
     * <p>The WebView is navigated away <em>before</em> ExoPlayer is built, not
     * after it succeeds. Two players holding the same stream would both decode
     * and both emit sound for as long as buffering takes, which is audible.
     * Nothing is risked by being early: the URL is already in hand, and
     * {@link #fallBackToEmbed} reloads the source if ExoPlayer cannot use it.
     */
    private void onStreamCaptured(StreamSniffer.Capture capture) {
        if (isFinishing() || isDestroyed() || nativeMode || nativeFailedHere) return;

        Log.i(TAG, "Moving playback to ExoPlayer: " + capture.url);
        showLoading(getString(R.string.player_loading_title),
                getString(R.string.player_starting_playback));

        if (webView != null) webView.loadUrl("about:blank");
        handler.removeCallbacks(loadTimeout);

        startNativePlayback(capture);
    }

    private void startNativePlayback(StreamSniffer.Capture capture) {
        if (nativePlayerView == null) {
            fallBackToEmbed();
            return;
        }
        releaseNativePlayer();

        // Arrive as the page did. A CDN that served the manifest to the embed
        // will refuse the identical URL to a client with no Referer/Origin —
        // by far the most common reason a lifted stream 403s while the page
        // plays it fine.
        Map<String, String> headers = new HashMap<>();
        if (capture.referer != null && !capture.referer.isEmpty()) {
            headers.put("Referer", capture.referer);
        }
        if (capture.origin != null && !capture.origin.isEmpty()) {
            headers.put("Origin", capture.origin);
        }

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(DESKTOP_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setDefaultRequestProperties(headers);

        nativePlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();

        nativePlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state != Player.STATE_READY) return;
                pageReady = true;
                enterNativeMode();
                hideOverlay();
                handler.removeCallbacks(rememberSource);
                handler.postDelayed(rememberSource, REMEMBER_DELAY_MS);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.w(TAG, "Native playback failed, returning to the embed", error);
                fallBackToEmbed();
            }
        });

        nativePlayerView.setPlayer(nativePlayer);
        nativePlayer.setMediaItem(MediaItem.fromUri(Uri.parse(capture.url)));
        nativePlayer.prepare();
        nativePlayer.play();

        wireNativeControls();
    }

    /**
     * Gives the shared controller's buttons an owner.
     *
     * <p>custom_exo_controller is written for the channel player, so its back,
     * title and settings controls do nothing here until bound. Settings maps to
     * the source picker, which is the equivalent action for a film.
     */
    private void wireNativeControls() {
        if (nativePlayerView == null) return;

        View back = nativePlayerView.findViewById(R.id.exo_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View pip = nativePlayerView.findViewById(R.id.exo_pip);
        if (pip != null) pip.setOnClickListener(v -> enterPip());

        View settings = nativePlayerView.findViewById(R.id.exo_settings);
        if (settings != null) settings.setOnClickListener(v -> showSourcePicker());

        TextView name = nativePlayerView.findViewById(R.id.exo_channel_name);
        if (name != null) name.setText(titleText);
    }

    private void enterNativeMode() {
        if (nativeMode) return;
        nativeMode = true;
        if (webView != null) webView.setVisibility(View.GONE);
        if (nativePlayerView != null) nativePlayerView.setVisibility(View.VISIBLE);
        // The controller carries its own bars; leaving these up would stack two
        // sets of controls on top of each other.
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
    }

    private void exitNativeMode() {
        nativeMode = false;
        if (nativePlayerView != null) {
            nativePlayerView.setPlayer(null);
            nativePlayerView.setVisibility(View.GONE);
        }
        if (webView != null) webView.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
    }

    /**
     * Native playback did not work out — put the film back in the WebView.
     *
     * <p>{@link #nativeFailedHere} is set first so the reload cannot be
     * captured again and loop forever between the two surfaces.
     */
    private void fallBackToEmbed() {
        if (isFinishing() || isDestroyed() || activeServerId == null) return;

        nativeFailedHere = true;
        sniffer.disarm();
        releaseNativePlayer();
        exitNativeMode();

        pageReady = false;
        showLoading(getString(R.string.player_loading_title),
                getString(R.string.player_starting_playback));
        handler.removeCallbacks(loadTimeout);
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);

        int index = StreamServers.indexOf(activeServerId);
        if (index >= 0 && webView != null) {
            webView.loadUrl(StreamServers.at(index).url(kind, tmdbId, season, episode));
        }
    }

    private void releaseNativePlayer() {
        if (nativePlayer != null) {
            nativePlayer.release();
            nativePlayer = null;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Episodes
    // ══════════════════════════════════════════════════════════

    private void resolveEpisodeNav() {
        EpisodeNav.resolve(this, tmdbId, season, episode,
                LocaleManager.getTmdbLanguage(this), nav -> {
                    if (isFinishing() || isDestroyed()) return;
                    episodeNav = nav;
                    btnPrevEpisode.setVisibility(nav.previous != null ? View.VISIBLE : View.GONE);
                    btnNextEpisode.setVisibility(nav.next != null ? View.VISIBLE : View.GONE);
                });
    }

    /**
     * Moves to another episode in place.
     *
     * <p>Everything source-related resets: health is re-probed for the new
     * episode, because sources routinely carry one episode and not the next,
     * and this episode's own remembered source is consulted rather than the
     * previous one's.
     */
    private void goToEpisode(EpisodeNav.Ref ref) {
        season = ref.season;
        episode = ref.episode;
        updateEpisodeLabel();

        activeServerId = null;
        autoPicked = false;
        pageReady = false;
        failedServers.clear();
        handler.removeCallbacksAndMessages(null);

        releaseNativePlayer();
        exitNativeMode();
        if (webView != null) webView.loadUrl("about:blank");

        showLoading(getString(R.string.player_finding_source),
                getString(R.string.player_finding_source_body));
        startProbe();
        resolveEpisodeNav();
    }

    // ══════════════════════════════════════════════════════════
    //  Failure & trailer fallback
    // ══════════════════════════════════════════════════════════

    /**
     * Every source failed. Offer the trailer.
     *
     * <p>Opt-in rather than automatic, matching the desktop: switching a viewer
     * to a trailer without asking, when they may be about to pick a source by
     * hand, replaces one disappointment with another.
     */
    private void offerTrailerFallback() {
        showFailure(getString(R.string.player_unavailable_title),
                getString(R.string.player_unavailable_body));
        btnPickServer.setVisibility(View.VISIBLE);

        TmdbClient.videos(this, kind, tmdbId, LocaleManager.getTmdbLanguage(this),
                new TmdbClient.Callback<Videos>() {
                    @Override
                    public void onSuccess(Videos data) {
                        if (isFinishing() || isDestroyed()) return;
                        if (pickTrailerKey(data) != null) {
                            btnWatchTrailer.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // No trailer offered. The source picker is still there.
                    }
                });
    }

    private void playTrailer() {
        TmdbClient.videos(this, kind, tmdbId, LocaleManager.getTmdbLanguage(this),
                new TmdbClient.Callback<Videos>() {
                    @Override
                    public void onSuccess(Videos data) {
                        String key = pickTrailerKey(data);
                        if (key == null) return;
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/watch?v=" + key));
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);
                        }
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
    }

    @Nullable
    private String pickTrailerKey(Videos videos) {
        if (videos == null || videos.results == null) return null;
        Video anyTrailer = null, any = null;
        for (Video v : videos.results) {
            if (v == null || v.key == null || !"YouTube".equals(v.site)) continue;
            if (any == null) any = v;
            if ("Trailer".equals(v.type)) {
                if (v.official) return v.key;
                if (anyTrailer == null) anyTrailer = v;
            }
        }
        if (anyTrailer != null) return anyTrailer.key;
        return any == null ? null : any.key;
    }

    // ══════════════════════════════════════════════════════════
    //  Overlay
    // ══════════════════════════════════════════════════════════

    private void showLoading(String title, String subtitle) {
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingSpinner.setVisibility(View.VISIBLE);
        loadingTitle.setText(title);
        loadingSubtitle.setText(subtitle);
        btnWatchTrailer.setVisibility(View.GONE);
        btnPickServer.setVisibility(View.GONE);
    }

    private void showFailure(String title, String subtitle) {
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingSpinner.setVisibility(View.GONE);
        loadingTitle.setText(title);
        loadingSubtitle.setText(subtitle);
    }

    private void hideOverlay() {
        loadingOverlay.setVisibility(View.GONE);
    }

    // ══════════════════════════════════════════════════════════
    //  Window
    // ══════════════════════════════════════════════════════════

    private void setUpCast() {
        try {
            CastContext castContext = CastContext.getSharedInstance(this);
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), castButton);
            castStateListener = newState -> castButton.setVisibility(
                    newState != CastState.NO_DEVICES_AVAILABLE ? View.VISIBLE : View.GONE);
            castContext.addCastStateListener(castStateListener);
        } catch (Exception e) {
            castButton.setVisibility(View.GONE);
        }
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        View surface = nativeMode && nativePlayerView != null ? nativePlayerView : webView;
        int w = surface != null && surface.getWidth() > 0 ? surface.getWidth() : 16;
        int h = surface != null && surface.getHeight() > 0 ? surface.getHeight() : 9;
        PictureInPictureParams.Builder builder =
                new PictureInPictureParams.Builder().setAspectRatio(new Rational(w, h));
        enterPictureInPictureMode(builder.build());
    }

    private boolean isInPipCompat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration config) {
        super.onPictureInPictureModeChanged(inPip, config);
        if (nativeMode) {
            if (nativePlayerView != null) nativePlayerView.setUseController(!inPip);
            return;
        }
        topBar.setVisibility(inPip ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(inPip ? View.GONE : View.VISIBLE);
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
}
