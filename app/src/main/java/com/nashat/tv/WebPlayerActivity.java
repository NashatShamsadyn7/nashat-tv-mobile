package com.nashat.tv;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

import com.google.android.exoplayer2.C;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nashat.tv.player.EpisodeNav;
import com.nashat.tv.player.PlaybackProgress;
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
    private final Runnable hideGestureHud = this::dismissGestureHud;

    // ─── Viewing comfort ──────────────────────────────────────

    /** Offered speeds. 2× is the ceiling — beyond it dialogue is unusable. */
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    private static final int[] SLEEP_MINUTES = {15, 30, 45, 60, 90};

    /** How often the position is written while playing. */
    private static final long PROGRESS_SAVE_INTERVAL_MS = 5_000;

    /**
     * Seek step for the controller's skip buttons and for a double tap.
     *
     * <p>Asymmetric on purpose, and it is the convention every major player has
     * converged on: going back is almost always "I missed that line", which is
     * a few seconds; going forward is almost always "skip this", which is a
     * scene. A symmetric pair makes one of the two directions the wrong size.
     */
    private static final long SEEK_BACK_MS = 10_000;
    private static final long SEEK_FORWARD_MS = 30_000;

    private PlaybackProgress progressStore;
    private ContinueWatchingManager continueStore;

    private View gestureLayer, gestureHud, gestureLeft, gestureRight;
    private ImageView gestureIcon;
    private TextView gestureValue;
    private ProgressBar gestureBar;
    private ImageButton btnLock, btnSpeed, btnSleep, btnEpisodes;

    private boolean controlsLocked = false;
    private float speed = 1f;
    private AudioManager audio;
    private int maxVolume = 15;

    /** Resume offer is made once per opened title, not on every source switch. */
    private boolean resumeOffered = false;
    private long pendingResumeMs = 0;

    private final Runnable saveProgress = new Runnable() {
        @Override
        public void run() {
            persistProgress();
            handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS);
        }
    };

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
        progressStore = new PlaybackProgress(this);
        continueStore = new ContinueWatchingManager(this);

        bindViews();
        setUpComfortControls();
        setUpWebView();
        setUpCast();
        recordContinueWatching();

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
        persistProgress();
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
        persistProgress();
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
                // ExoPlayer's defaults are 5s back and 15s forward, which are
                // tuned for correcting a missed line of dialogue. On a
                // two-hour film they are also the only way to cover distance
                // when the scrubber is not to hand, and 5s at a time is not a
                // way to cross an hour. 10 back / 30 forward: back is for
                // "what did they say", forward is for skipping a scene.
                .setSeekBackIncrementMs(SEEK_BACK_MS)
                .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
                .build();

        nativePlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    onPlaybackFinished();
                    return;
                }
                if (state != Player.STATE_READY) return;
                pageReady = true;
                enterNativeMode();
                hideOverlay();
                handler.removeCallbacks(rememberSource);
                handler.postDelayed(rememberSource, REMEMBER_DELAY_MS);

                // Speed and resume only exist once ExoPlayer owns playback.
                // The speed control itself is revealed by applyOnDemandControls,
                // on the controller — bottomBar's btnSpeed was being shown on a
                // bar that enterNativeMode had already hidden.
                maybeOfferResume();
                handler.removeCallbacks(saveProgress);
                handler.postDelayed(saveProgress, PROGRESS_SAVE_INTERVAL_MS);
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
        applyOnDemandControls();
    }

    /**
     * Reveals the seek bar.
     *
     * <p>custom_exo_controller ships with its timeline — position, scrubber and
     * duration, grouped as {@code vod_controls} — set to {@code gone}, and its
     * LIVE badge visible. That is the correct default for the file's original
     * caller: PlayerActivity plays broadcast channels, where a scrubber cannot
     * move and a duration cannot be read, and it calls
     * {@code applyLiveOrOnDemandControls()} to swap the two whenever a stream
     * turns out to be on-demand.
     *
     * <p>This Activity reused the same controller and never made that call. So
     * a film that reached native playback showed a LIVE badge and no timeline:
     * every second of it was reachable, the player was seekable, ExoPlayer knew
     * the duration — there was simply no control on screen to say where to go.
     * Jumping to 42 minutes meant tapping a 5-second rewind button.
     *
     * <p>There is no live/on-demand test here as there is in PlayerActivity,
     * because this screen only ever plays films and episodes. A capture that
     * genuinely had no duration would leave ExoPlayer to render an unset
     * timeline, which is a broken source rather than a live one.
     */
    private void applyOnDemandControls() {
        if (nativePlayerView == null) return;

        View liveBadge = nativePlayerView.findViewById(R.id.live_indicator);
        if (liveBadge != null) liveBadge.setVisibility(View.GONE);

        View vodControls = nativePlayerView.findViewById(R.id.vod_controls);
        if (vodControls != null) vodControls.setVisibility(View.VISIBLE);

        View rew = nativePlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_rew);
        if (rew != null) rew.setVisibility(View.VISIBLE);

        View ffwd = nativePlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_ffwd);
        if (ffwd != null) ffwd.setVisibility(View.VISIBLE);

        // The controller has its own speed badge. btnSpeed lives on bottomBar,
        // which enterNativeMode has just hidden — so setting that one visible,
        // as this class did, put the speed control on a hidden bar.
        TextView badge = nativePlayerView.findViewById(R.id.exo_playback_speed);
        if (badge != null) {
            badge.setVisibility(View.VISIBLE);
            badge.setOnClickListener(v -> showSpeedPicker());
        }
        updateSpeedBadge();
    }

    /** Keeps the controller's speed badge in step with {@link #speed}. */
    private void updateSpeedBadge() {
        if (nativePlayerView == null) return;
        TextView badge = nativePlayerView.findViewById(R.id.exo_playback_speed);
        if (badge != null) {
            badge.setText(getString(R.string.player_speed_value, trimZero(speed)));
        }
    }

    private void exitNativeMode() {
        nativeMode = false;
        if (btnSpeed != null) btnSpeed.setVisibility(View.GONE);
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
                    if (!controlsLocked) updateEpisodeButtons();
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
    //  Comfort controls
    // ══════════════════════════════════════════════════════════

    private void setUpComfortControls() {
        gestureLayer = findViewById(R.id.gestureLayer);
        gestureLeft = findViewById(R.id.gestureLeft);
        gestureRight = findViewById(R.id.gestureRight);
        gestureHud = findViewById(R.id.gestureHud);
        gestureIcon = findViewById(R.id.gestureIcon);
        gestureValue = findViewById(R.id.gestureValue);
        gestureBar = findViewById(R.id.gestureBar);
        btnLock = findViewById(R.id.btnLock);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnSleep = findViewById(R.id.btnSleep);
        btnEpisodes = findViewById(R.id.btnEpisodes);

        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            maxVolume = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        }

        btnLock.setOnClickListener(v -> toggleLock());
        btnSpeed.setOnClickListener(v -> showSpeedPicker());
        btnSleep.setOnClickListener(v -> showSleepPicker());
        btnEpisodes.setOnClickListener(v -> showEpisodePicker());
        btnEpisodes.setVisibility(kind == Kind.TV ? View.VISIBLE : View.GONE);

        gestureLeft.setOnTouchListener(new VerticalDragListener(true));
        gestureRight.setOnTouchListener(new VerticalDragListener(false));
    }

    /**
     * Double-tap left to go back, right to go forward.
     *
     * <p>The gesture every video app on the phone already has, and the one
     * users reach for before they look for a button. It matters more here than
     * in most players because the controller auto-hides after four seconds:
     * without it, moving through a film means first tapping to summon the
     * controls and then finding a target inside a bar that is already fading.
     *
     * <p>Only active in native mode. During embed playback the app does not own
     * the video element and cannot seek it at all — a gesture that silently did
     * nothing would be worse than no gesture.
     */
    private final class DoubleTapSeekListener extends GestureDetector.SimpleOnGestureListener {
        private final boolean forward;

        DoubleTapSeekListener(boolean forward) {
            this.forward = forward;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            if (controlsLocked || !nativeMode || nativePlayer == null) return false;

            long delta = forward ? SEEK_FORWARD_MS : -SEEK_BACK_MS;
            long duration = nativePlayer.getDuration();
            long target = nativePlayer.getCurrentPosition() + delta;

            // Clamp rather than let seekTo handle it: seeking past the end of a
            // film ends playback, which is a destructive answer to "skip the
            // last few seconds of this scene".
            if (target < 0) target = 0;
            if (duration != C.TIME_UNSET && target > duration) target = duration;
            nativePlayer.seekTo(target);

            showGestureHud(
                    forward ? R.drawable.ic_fast_forward_24 : R.drawable.ic_fast_rewind_24,
                    getString(forward ? R.string.player_seek_forward : R.string.player_seek_back,
                            Math.abs(delta / 1000)));
            handler.removeCallbacks(hideGestureHud);
            handler.postDelayed(hideGestureHud, 700);
            return true;
        }
    }

    /**
     * Brightness on the left, volume on the right — the platform convention.
     *
     * <p>The listener claims a gesture only once it has moved past a threshold
     * and is more vertical than horizontal. Without that test, the embed's own
     * horizontal scrubbing would be swallowed by this layer, and any stray
     * finger movement during a tap would jolt the brightness.
     */
    private final class VerticalDragListener implements View.OnTouchListener {
        private final boolean brightness;
        private final GestureDetector doubleTap;
        private float startY, startX, startValue;
        private boolean claimed;

        VerticalDragListener(boolean brightness) {
            this.brightness = brightness;
            // Left half seeks back, right half seeks forward — matching the
            // side each half already owns for brightness and volume.
            doubleTap = new GestureDetector(WebPlayerActivity.this,
                    new DoubleTapSeekListener(!brightness));
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (controlsLocked) return false;

            // Offered to the detector first, but its verdict is not returned
            // directly: a double tap arrives as a sequence that also contains
            // the DOWN and MOVE events a drag is built from, so consuming the
            // whole stream here would break the vertical gesture. The detector
            // only ever fires its callback; the drag logic below still sees
            // every event and decides for itself whether it has been claimed.
            if (doubleTap.onTouchEvent(e)) return true;

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = e.getRawY();
                    startX = e.getRawX();
                    claimed = false;
                    startValue = brightness ? currentBrightness() : currentVolume();
                    return false;   // let a tap through until a drag is proven

                case MotionEvent.ACTION_MOVE: {
                    float dy = startY - e.getRawY();
                    float dx = Math.abs(e.getRawX() - startX);
                    if (!claimed) {
                        if (Math.abs(dy) < 24 || Math.abs(dy) < dx) return false;
                        claimed = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    // A full swipe of the view's height is the full range, so
                    // the gesture's sensitivity is the same on any screen.
                    float delta = dy / Math.max(1f, v.getHeight());
                    float next = Math.max(0f, Math.min(1f, startValue + delta));
                    if (brightness) applyBrightness(next);
                    else applyVolume(next);
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (claimed) {
                        handler.removeCallbacks(hideGestureHud);
                        handler.postDelayed(hideGestureHud, 700);
                        return true;
                    }
                    return false;
            }
            return false;
        }
    }

    private float currentBrightness() {
        float b = getWindow().getAttributes().screenBrightness;
        // -1 means "follow the system", which is not a value that can be
        // adjusted from, so the first drag starts from the middle.
        return b < 0 ? 0.5f : b;
    }

    private void applyBrightness(float value) {
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.01f, value);   // never fully dark
        getWindow().setAttributes(lp);
        showGestureHud(R.drawable.ic_brightness_24, Math.round(value * 100));
    }

    private float currentVolume() {
        if (audio == null) return 0.5f;
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) maxVolume;
    }

    private void applyVolume(float value) {
        if (audio == null) return;
        int level = Math.round(value * maxVolume);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0);
        showGestureHud(R.drawable.ic_volume_24, Math.round(level * 100f / maxVolume));
    }

    /** Brightness and volume: a percentage, with the bar showing where it sits. */
    private void showGestureHud(int iconRes, int percent) {
        handler.removeCallbacks(hideGestureHud);
        gestureIcon.setImageResource(iconRes);
        gestureValue.setText(getString(R.string.percent_value, percent));
        gestureBar.setVisibility(View.VISIBLE);
        gestureBar.setProgress(percent);
        gestureHud.setVisibility(View.VISIBLE);
    }

    /**
     * Seeking: a label, and no bar.
     *
     * <p>The bar reads as "how much of the range you are at", which is true of
     * brightness and volume and meaningless for a ±30s jump. Showing it at some
     * arbitrary fill would invite the user to read a position out of it.
     */
    private void showGestureHud(int iconRes, String label) {
        handler.removeCallbacks(hideGestureHud);
        gestureIcon.setImageResource(iconRes);
        gestureValue.setText(label);
        gestureBar.setVisibility(View.GONE);
        gestureHud.setVisibility(View.VISIBLE);
    }

    private void dismissGestureHud() {
        if (gestureHud != null) gestureHud.setVisibility(View.GONE);
    }

    // ─── Lock ─────────────────────────────────────────────────

    /**
     * Hides every control and swallows touches until unlocked.
     *
     * <p>The padlock itself stays live and visible — a lock with no visible way
     * out reads as a crash, and the only escape would be the back button,
     * which leaves the film.
     */
    private void toggleLock() {
        controlsLocked = !controlsLocked;

        int barVisibility = controlsLocked ? View.GONE : View.VISIBLE;
        if (!nativeMode) {
            topBar.setVisibility(barVisibility);
            bottomBar.setVisibility(barVisibility);
        }
        if (nativePlayerView != null) {
            nativePlayerView.setUseController(!controlsLocked);
        }

        btnLock.setImageResource(controlsLocked
                ? R.drawable.ic_lock_24 : R.drawable.ic_lock_open_24);
        btnLock.setContentDescription(getString(
                controlsLocked ? R.string.player_unlock : R.string.player_lock));

        if (controlsLocked) {
            // Re-parent the padlock above the hidden bar so it survives it.
            btnLock.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
            for (int i = 0; i < ((ViewGroup) bottomBar).getChildCount(); i++) {
                View child = ((ViewGroup) bottomBar).getChildAt(i);
                child.setVisibility(child == btnLock ? View.VISIBLE : View.GONE);
            }
            Snackbar.make(bottomBar, R.string.player_locked_hint, Snackbar.LENGTH_SHORT).show();
        } else {
            for (int i = 0; i < ((ViewGroup) bottomBar).getChildCount(); i++) {
                ((ViewGroup) bottomBar).getChildAt(i).setVisibility(View.VISIBLE);
            }
            // Locking makes bottomBar visible so the unlock button stays
            // reachable. Unlocking has to put it back the way the current mode
            // wants it — in native mode that is hidden, because the ExoPlayer
            // controller owns the chrome. Restoring only the CHILDREN, as this
            // did, left a film with two stacked sets of controls after any
            // lock/unlock round trip.
            bottomBar.setVisibility(nativeMode ? View.GONE : View.VISIBLE);
            btnSpeed.setVisibility(View.GONE);   // embed speed is unreachable
            btnEpisodes.setVisibility(kind == Kind.TV ? View.VISIBLE : View.GONE);
            updateEpisodeButtons();
        }
    }

    // ─── Speed ────────────────────────────────────────────────

    /**
     * Speed applies to ExoPlayer only.
     *
     * <p>An embed's player is inside a cross-origin frame, so its playback rate
     * cannot be reached at all. The button is therefore hidden rather than
     * shown and silently doing nothing.
     */
    private void showSpeedPicker() {
        if (nativePlayer == null) return;

        String[] labels = new String[SPEEDS.length];
        int checked = 0;
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = SPEEDS[i] == 1f
                    ? getString(R.string.player_speed_normal)
                    : getString(R.string.player_speed_value, trimZero(SPEEDS[i]));
            if (Math.abs(SPEEDS[i] - speed) < 0.01f) checked = i;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_speed)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    speed = SPEEDS[which];
                    nativePlayer.setPlaybackParameters(
                            new com.google.android.exoplayer2.PlaybackParameters(speed));
                    updateSpeedBadge();
                    d.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** 1.50 → "1.5", 2.00 → "2". Trailing zeros read as false precision. */
    private static String trimZero(float value) {
        String s = String.format(Locale.US, "%.2f", value);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    // ─── Sleep timer ──────────────────────────────────────────

    private void showSleepPicker() {
        String[] labels = new String[SLEEP_MINUTES.length + 1];
        labels[0] = getString(R.string.player_sleep_off);
        for (int i = 0; i < SLEEP_MINUTES.length; i++) {
            labels[i + 1] = getString(R.string.player_sleep_minutes, SLEEP_MINUTES[i]);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_sleep_timer)
                .setItems(labels, (d, which) -> {
                    handler.removeCallbacks(sleepAction);
                    if (which == 0) {
                        Snackbar.make(bottomBar, R.string.player_sleep_cancelled,
                                Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    int minutes = SLEEP_MINUTES[which - 1];
                    handler.postDelayed(sleepAction, minutes * 60_000L);
                    Snackbar.make(bottomBar,
                            getString(R.string.player_sleep_set, minutes),
                            Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    /** Stops playback and leaves, rather than pausing on a lit screen. */
    private final Runnable sleepAction = () -> {
        persistProgress();
        finish();
    };

    // ─── Episodes ─────────────────────────────────────────────

    private void showEpisodePicker() {
        if (kind != Kind.TV) return;

        TmdbClient.season(this, tmdbId, season, LocaleManager.getTmdbLanguage(this),
                new TmdbClient.Callback<com.nashat.tv.tmdb.TmdbModels.Season>() {
                    @Override
                    public void onSuccess(com.nashat.tv.tmdb.TmdbModels.Season data) {
                        if (isFinishing() || isDestroyed() || data.episodes == null) return;
                        BottomSheetDialog dialog = new BottomSheetDialog(WebPlayerActivity.this);
                        View sheet = LayoutInflater.from(WebPlayerActivity.this)
                                .inflate(R.layout.sheet_server_picker, null);
                        ((TextView) sheet.findViewById(R.id.sheetTitle))
                                .setText(R.string.player_episodes);
                        ((TextView) sheet.findViewById(R.id.sheetHint))
                                .setText(getString(R.string.details_season_number, season));

                        LinearLayout list = sheet.findViewById(R.id.serverList);
                        for (com.nashat.tv.tmdb.TmdbModels.Episode ep : data.episodes) {
                            list.addView(buildEpisodeRow(list, ep, dialog));
                        }
                        dialog.setContentView(sheet);
                        dialog.show();
                    }

                    @Override
                    public void onError(String message) {
                        // Prev/next still work; only the full list is unavailable.
                    }
                });
    }

    private View buildEpisodeRow(ViewGroup parent,
                                 com.nashat.tv.tmdb.TmdbModels.Episode ep,
                                 BottomSheetDialog dialog) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_server, parent, false);
        row.findViewById(R.id.btnVoteUp).setVisibility(View.GONE);
        row.findViewById(R.id.btnVoteDown).setVisibility(View.GONE);
        row.findViewById(R.id.statusIcon).setVisibility(View.GONE);

        TextView name = row.findViewById(R.id.serverName);
        TextView status = row.findViewById(R.id.serverStatus);
        View mark = row.findViewById(R.id.activeMark);

        String label = getString(R.string.details_episode_number, ep.episodeNumber);
        name.setText(ep.name == null || ep.name.isEmpty() ? label : label + " · " + ep.name);
        status.setText(ep.airDate == null ? "" : ep.airDate);
        status.setVisibility(ep.airDate == null ? View.GONE : View.VISIBLE);
        mark.setVisibility(ep.episodeNumber == episode ? View.VISIBLE : View.INVISIBLE);

        row.setOnClickListener(v -> {
            dialog.dismiss();
            goToEpisode(new EpisodeNav.Ref(season, ep.episodeNumber));
        });
        return row;
    }

    private void updateEpisodeButtons() {
        if (episodeNav == null) return;
        btnPrevEpisode.setVisibility(episodeNav.previous != null ? View.VISIBLE : View.GONE);
        btnNextEpisode.setVisibility(episodeNav.next != null ? View.VISIBLE : View.GONE);
    }

    // ─── Resume & progress ────────────────────────────────────

    /**
     * Offers to pick up where the viewer stopped.
     *
     * <p>Asked rather than assumed. Automatic resume is wrong often enough —
     * a rewatch, a different person on the same device — that silently
     * skipping twenty minutes in is worse than one dialog.
     */
    private void maybeOfferResume() {
        if (resumeOffered || nativePlayer == null) return;
        resumeOffered = true;

        long saved = progressStore.position(kind, tmdbId, season, episode);
        if (saved <= 0) return;
        pendingResumeMs = saved;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_resume_title)
                .setMessage(getString(R.string.player_resume_body, formatTime(saved)))
                .setPositiveButton(R.string.player_resume, (d, w) -> {
                    if (nativePlayer != null) nativePlayer.seekTo(pendingResumeMs);
                })
                .setNegativeButton(R.string.player_start_over, (d, w) -> {
                    progressStore.clear(kind, tmdbId, season, episode);
                })
                .show();
    }

    private void persistProgress() {
        if (nativePlayer == null) return;
        long pos = nativePlayer.getCurrentPosition();
        long dur = nativePlayer.getDuration();
        if (dur <= 0) return;
        progressStore.save(kind, tmdbId, season, episode, pos, dur);
    }

    private static String formatTime(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
                : String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    /**
     * The episode ended. Roll into the next one if there is one.
     *
     * <p>Only for series, and only when a next episode actually exists — after
     * a film, or a season finale with nothing after it, playback simply stops
     * and the viewer is left on the player rather than being sent somewhere
     * they did not ask to go.
     */
    private void onPlaybackFinished() {
        progressStore.clear(kind, tmdbId, season, episode);
        handler.removeCallbacks(saveProgress);

        if (kind != Kind.TV || episodeNav == null || episodeNav.next == null) return;

        Snackbar.make(bottomBar, R.string.player_autoplay_next, Snackbar.LENGTH_SHORT).show();
        goToEpisode(episodeNav.next);
    }

    /** Records the title so it appears under Continue watching. */
    private void recordContinueWatching() {
        try {
            Movie m = new Movie(titleText, null, kind == Kind.TV ? "series" : "movie",
                    String.valueOf(tmdbId), "", null, 0);
            if (kind == Kind.TV) continueStore.addSeries(m, season, episode);
            else continueStore.addMovie(m);
        } catch (Exception ignored) {
            // A convenience. Failing to record must never stop playback.
        }
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
