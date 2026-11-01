package com.nashat.tv;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.material.snackbar.Snackbar;

import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.ui.PlayerView;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

public class PlayerActivity extends BaseActivity {

    private static final String TAG = "PlayerActivity";

    /**
     * Swaps the bottom bar between its live and on-demand forms.
     *
     * <p>A live channel has no duration, so ExoPlayer reports its position
     * against an unset timeline. The shared controller used to show the seek
     * bar regardless, which rendered readings like "01:00 / 00:31" on a
     * broadcast and offered a scrubber that could not move — and a playback
     * speed control, which means nothing on a live feed.
     *
     * <p>Called on STATE_READY because that is the first point at which the
     * timeline is populated and {@code isCurrentMediaItemLive()} is reliable.
     */
    private void applyLiveOrOnDemandControls() {
        if (player == null || playerView == null) return;

        boolean live = player.isCurrentMediaItemLive();

        View liveBadge = playerView.findViewById(R.id.live_indicator);
        View vodControls = playerView.findViewById(R.id.vod_controls);

        if (liveBadge != null) liveBadge.setVisibility(live ? View.VISIBLE : View.GONE);
        if (vodControls != null) vodControls.setVisibility(live ? View.GONE : View.VISIBLE);
        if (playbackSpeedView != null) {
            playbackSpeedView.setVisibility(live ? View.GONE : View.VISIBLE);
        }

        // Skip buttons are only meaningful when there is somewhere to skip to.
        View rew = playerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_rew);
        View ffwd = playerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_ffwd);
        if (rew != null) rew.setVisibility(live ? View.GONE : View.VISIBLE);
        if (ffwd != null) ffwd.setVisibility(live ? View.GONE : View.VISIBLE);
    }

    private PlayerView playerView;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private AudioManager audioManager;
    private Handler handler;

    /**
     * Kept as a field so the Referer can be set for whichever URL is about to
     * play. The headers must be in place before {@code prepare()}, because that
     * is when the factory hands out the data source that fetches the manifest.
     */
    private DefaultHttpDataSource.Factory httpDataSourceFactory;

    private String channelName;
    private String channelUrl;
    private String channelLogo;
    private String channelCategory;
    private long playbackStartTime = 0;
    
    private boolean isFullscreen = false;
    private float[] playbackSpeeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private int currentSpeedIndex = 2; // 1.0x default

    // UI Controls
    private TextView channelNameView;
    private TextView playbackSpeedView;
    private ImageButton backButton;
    private ImageButton fullscreenButton;
    private ImageButton settingsButton;
    private ImageButton audioTrackButton;
    private ImageButton subtitleButton;
    private ImageButton volumeButton;
    private ImageButton pipButton;

    // Chromecast
    private CastManager castManager;
    private MediaRouteButton castButton;
    private boolean isCastingActive = false;

    private static final int REQ_VOICE_CMD = 3001;
    private static final int REQ_RECORD_AUDIO_CMD = 3002;

    // Gesture and overlay UI
    private View gestureAreaLeft, gestureAreaRight;
    private View overlayBrightness, overlayVolume, overlaySeek, overlayHints;
    private ProgressBar brightnessProgress, volumeProgress;
    private TextView brightnessPercent, volumePercent, seekSeconds;
    private ImageView seekDirectionIcon;

    // Gesture state
    private float touchStartX, touchStartY;
    private float startBrightness = -1f;
    private int startVolume = -1;
    private boolean isSeeking = false;
    private long seekAccumulationMs = 0;

    private GestureDetector leftGestureDetector, rightGestureDetector;

    private final Runnable hideOverlaysRunnable = new Runnable() {
        @Override public void run() {
            if (overlayBrightness != null) overlayBrightness.setVisibility(View.GONE);
            if (overlayVolume != null) overlayVolume.setVisibility(View.GONE);
            if (overlaySeek != null) overlaySeek.setVisibility(View.GONE);
            if (overlayHints != null) overlayHints.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);

        channelName = getIntent().getStringExtra("name");
        channelUrl = getIntent().getStringExtra("url");
        channelLogo = getIntent().getStringExtra("logo");
        channelCategory = getIntent().getStringExtra("category");
        if (channelCategory == null || channelCategory.isEmpty()) channelCategory = "General";

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        // Start fully immersive (hide status/wifi/battery/clock)
        isFullscreen = true;
        enterImmersiveMode();

        initializeCustomControls();
        setupPlayerSettings();
        initializeCast();
    }

    private void initializeCustomControls() {
        // Find custom control views
        channelNameView = playerView.findViewById(R.id.exo_channel_name);
        playbackSpeedView = playerView.findViewById(R.id.exo_playback_speed);
        backButton = playerView.findViewById(R.id.exo_back);
        fullscreenButton = playerView.findViewById(R.id.exo_fullscreen);
        settingsButton = playerView.findViewById(R.id.exo_settings);
        pipButton = playerView.findViewById(R.id.exo_pip);
        castButton = playerView.findViewById(R.id.exo_cast);
        audioTrackButton = playerView.findViewById(R.id.exo_audio_track);
        subtitleButton = playerView.findViewById(R.id.exo_subtitle);
        volumeButton = playerView.findViewById(R.id.exo_volume);

        // Set channel name
        if (channelNameView != null && channelName != null) {
            channelNameView.setText(channelName);
        }

        // Set up click listeners
        if (fullscreenButton != null) {
            fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        }
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (isFullscreen) {
                    exitFullscreen();
                } else {
                    finish();
                }
            });
        }

        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> showSettingsDialog());
            settingsButton.setOnLongClickListener(v -> {
                startVoiceControl();
                return true;
            });
        }

        if (playbackSpeedView != null) {
            playbackSpeedView.setOnClickListener(v -> showSpeedDialog());
            updateSpeedDisplay();
        }

        if (audioTrackButton != null) {
            audioTrackButton.setOnClickListener(v -> showAudioTrackDialog());
        }

        if (subtitleButton != null) {
            subtitleButton.setOnClickListener(v -> showSubtitleDialog());
        }

        if (volumeButton != null) {
            volumeButton.setOnClickListener(v -> showVolumeDialog());
        }

        if (pipButton != null) {
            pipButton.setOnClickListener(v -> enterPipMode());
        }

        // Bind gesture areas and overlays
        gestureAreaLeft = playerView.findViewById(R.id.gesture_area_left);
        gestureAreaRight = playerView.findViewById(R.id.gesture_area_right);

        overlayBrightness = playerView.findViewById(R.id.overlay_brightness);
        brightnessProgress = playerView.findViewById(R.id.brightness_progress);
        brightnessPercent = playerView.findViewById(R.id.brightness_percent);

        overlayVolume = playerView.findViewById(R.id.overlay_volume);
        volumeProgress = playerView.findViewById(R.id.volume_progress);
        volumePercent = playerView.findViewById(R.id.volume_percent);

        overlaySeek = playerView.findViewById(R.id.overlay_seek);
        seekDirectionIcon = playerView.findViewById(R.id.seek_direction_icon);
        seekSeconds = playerView.findViewById(R.id.seek_seconds);

        overlayHints = playerView.findViewById(R.id.overlay_hints);

        setupGestureControls();
    }

    // ===================== Chromecast Integration =====================

    private void initializeCast() {
        castManager = new CastManager(this);
        castManager.initialize(new CastManager.CastCallback() {
            @Override
            public void onCastSessionStarted(CastPlayer castPlayer) {
                isCastingActive = true;
                // Pause local player and let Cast take over
                long currentPos = 0;
                if (player != null) {
                    currentPos = player.getCurrentPosition();
                    player.pause();
                }
                // Set the CastPlayer on the PlayerView for controls
                playerView.setPlayer(castPlayer);
                Toast.makeText(PlayerActivity.this,
                        getString(R.string.cast_connecting), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCastSessionEnded() {
                isCastingActive = false;
                // Switch back to local ExoPlayer
                if (player != null) {
                    playerView.setPlayer(player);
                    player.play();
                }
                Toast.makeText(PlayerActivity.this,
                        getString(R.string.cast_disconnected), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCastStateChanged(int newState) {
                // Show/hide cast button based on device availability
                updateCastButtonVisibility(newState);
            }
        });

        // Set up the MediaRouteButton
        if (castButton != null) {
            castManager.setupCastButton(castButton);
        }

        // Set media info so CastManager knows what to cast
        castManager.setCurrentMedia(channelName, channelUrl, channelLogo);

        // If launched with cast_immediately, open device picker after a short delay
        boolean castImmediately = getIntent().getBooleanExtra("cast_immediately", false);
        if (castImmediately && castButton != null) {
            handler.postDelayed(() -> {
                if (castManager != null && castManager.areCastDevicesAvailable()) {
                    castButton.performClick();
                }
            }, 1500); // Allow time for Cast SDK to discover devices
        }
    }

    private void updateCastButtonVisibility(int castState) {
        if (castButton == null) return;
        boolean showButton = castState != CastState.NO_DEVICES_AVAILABLE;
        castButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
    }

    private void setupPlayerSettings() {
        // Keep screen on during playback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int w = playerView != null ? playerView.getWidth() : 0;
            int h = playerView != null ? playerView.getHeight() : 0;
            Rational aspect = (w > 0 && h > 0) ? new Rational(w, h) : new Rational(16, 9);
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspect);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Seamlessly Auto-enter PiP mode on swipe up / home
                builder.setAutoEnterEnabled(true);
            }
            enterPictureInPictureMode(builder.build());
        } else {
            Toast.makeText(this, "Picture-in-Picture not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player != null && player.isPlaying()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && SettingsActivity.isBackgroundPlayEnabled(this)
                && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                enterPipMode();
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (playerView != null) {
            View top = playerView.findViewById(R.id.exo_top_bar);
            View bottom = playerView.findViewById(R.id.exo_bottom_bar);
            if (top != null) top.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
            if (bottom != null) bottom.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        }
    }

    // Voice control for playback (long-press settings to trigger)
    private void startVoiceControl() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO_CMD);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.player_settings));
        try {
            startActivityForResult(intent, REQ_VOICE_CMD);
        } catch (Exception e) {
            Toast.makeText(this, "Voice control not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE_CMD && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String cmd = results.get(0);
                handleVoiceCommand(cmd);
            }
        } else if (requestCode == REQ_PICK_SUBTITLE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignore) {
            }
            applySubtitleUri(uri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO_CMD) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceControl();
            } else {
                Toast.makeText(this, "Microphone permission is required for voice control", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleVoiceCommand(String original) {
        if (original == null) return;
        String cmd = original.toLowerCase(java.util.Locale.US).trim();

        try {
            if (player != null) {
                if (cmd.contains("pause") || cmd.contains("stop")) {
                    player.pause();
                    Toast.makeText(this, "Paused", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cmd.contains("play") || cmd.contains("resume")) {
                    player.play();
                    Toast.makeText(this, "Playing", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cmd.contains("mute")) {
                    player.setVolume(0f);
                    Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cmd.contains("unmute") || cmd.contains("volume up")) {
                    player.setVolume(1f);
                    Toast.makeText(this, "Unmuted", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cmd.contains("faster")) {
                    if (currentSpeedIndex < playbackSpeeds.length - 1) currentSpeedIndex++;
                    player.setPlaybackParameters(new PlaybackParameters(playbackSpeeds[currentSpeedIndex]));
                    updateSpeedDisplay();
                    Toast.makeText(this, "Speed " + playbackSpeeds[currentSpeedIndex] + "x", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (cmd.contains("slower")) {
                    if (currentSpeedIndex > 0) currentSpeedIndex--;
                    player.setPlaybackParameters(new PlaybackParameters(playbackSpeeds[currentSpeedIndex]));
                    updateSpeedDisplay();
                    Toast.makeText(this, "Speed " + playbackSpeeds[currentSpeedIndex] + "x", Toast.LENGTH_SHORT).show();
                    return;
                }
                java.util.regex.Matcher mSpeed = java.util.regex.Pattern.compile("speed\\s*(\\d+(?:\\.\\d+)?)").matcher(cmd);
                if (mSpeed.find()) {
                    float sp = Float.parseFloat(mSpeed.group(1));
                    int nearest = 0;
                    float diff = Float.MAX_VALUE;
                    for (int i = 0; i < playbackSpeeds.length; i++) {
                        float d = Math.abs(playbackSpeeds[i] - sp);
                        if (d < diff) { diff = d; nearest = i; }
                    }
                    currentSpeedIndex = nearest;
                    player.setPlaybackParameters(new PlaybackParameters(playbackSpeeds[currentSpeedIndex]));
                    updateSpeedDisplay();
                    Toast.makeText(this, "Speed " + playbackSpeeds[currentSpeedIndex] + "x", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (cmd.contains("subtitle")) {
                showSubtitleDialog();
                return;
            }
            if (cmd.contains("audio")) {
                showAudioTrackDialog();
                return;
            }

            if (cmd.contains("quality") || cmd.matches(".*\\b(720|480|360|240)\\b.*") || cmd.contains("auto")) {
                if (trackSelector != null) {
                    DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.getParameters().buildUpon();
                    if (cmd.contains("auto")) {
                        parametersBuilder.clearVideoSizeConstraints();
                        Toast.makeText(this, "Quality: Auto", Toast.LENGTH_SHORT).show();
                    } else if (cmd.contains("720")) {
                        parametersBuilder.setMaxVideoSize(1280, 720);
                        Toast.makeText(this, "Quality: 720p", Toast.LENGTH_SHORT).show();
                    } else if (cmd.contains("480")) {
                        parametersBuilder.setMaxVideoSize(854, 480);
                        Toast.makeText(this, "Quality: 480p", Toast.LENGTH_SHORT).show();
                    } else if (cmd.contains("360")) {
                        parametersBuilder.setMaxVideoSize(640, 360);
                        Toast.makeText(this, "Quality: 360p", Toast.LENGTH_SHORT).show();
                    } else if (cmd.contains("240")) {
                        parametersBuilder.setMaxVideoSize(426, 240);
                        Toast.makeText(this, "Quality: 240p", Toast.LENGTH_SHORT).show();
                    }
                    trackSelector.setParameters(parametersBuilder);
                }
                return;
            }

            if (cmd.contains("fullscreen")) {
                if (cmd.contains("exit")) {
                    exitFullscreen();
                    Toast.makeText(this, "Exit Fullscreen", Toast.LENGTH_SHORT).show();
                } else {
                    enterFullscreen();
                    Toast.makeText(this, "Fullscreen", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (cmd.contains("pip") || cmd.contains("picture") || cmd.contains("mini")) {
                enterPipMode();
                return;
            }

            if (cmd.contains("cast") || cmd.contains("chromecast") || cmd.contains("tv")) {
                if (castManager != null && castManager.areCastDevicesAvailable()) {
                    Toast.makeText(this, getString(R.string.cast_to_tv), Toast.LENGTH_SHORT).show();
                    // Trigger the cast button (opens device chooser)
                    if (castButton != null) castButton.performClick();
                } else {
                    Toast.makeText(this, getString(R.string.cast_not_available), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            Toast.makeText(this, "Unknown command: " + original, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Command error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (SettingsActivity.isBackgroundPlayEnabled(this)) {
            // Keep player alive — audio continues
            return;
        }
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (SettingsActivity.isBackgroundPlayEnabled(this)) {
            return;
        }
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    private void initializePlayer() {
        if (player != null) return;

        // Apply user settings: HD quality and preferred audio language
        boolean hdEnabled = SettingsActivity.isHDQualityEnabled(this);

        trackSelector = new DefaultTrackSelector(this);
        DefaultTrackSelector.Parameters.Builder params = trackSelector.buildUponParameters()
                .setPreferredAudioLanguage("ku"); // Kurdish preference

        // If HD is disabled, limit to SD to save data and improve stability
        boolean dataSaver = SettingsActivity.isDataSaverEnabled(this);
        if (!hdEnabled || dataSaver) {
            params.setMaxVideoSizeSd();
        }
        if (dataSaver) {
            params.setMaxVideoBitrate(800_000);
        }
        trackSelector.setParameters(params.build());

        int bufIdx = SettingsActivity.getBufferSize(this);
        int minBufMs = (bufIdx == 0) ? 8000 : (bufIdx == 2 ? 30000 : 15000);
        int maxBufMs = (bufIdx == 0) ? 20000 : (bufIdx == 2 ? 60000 : 30000);
        int playbackBufMs = (bufIdx == 0) ? 1500 : (bufIdx == 2 ? 5000 : 2500);
        int rebufMs = (bufIdx == 0) ? 3000 : (bufIdx == 2 ? 8000 : 5000);

        com.google.android.exoplayer2.DefaultLoadControl loadControl =
                new com.google.android.exoplayer2.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(minBufMs, maxBufMs, playbackBufMs, rebufMs)
                        .build();

        // Channels such as KurdMax are served only to a request carrying the
        // broadcaster's own Referer; ExoPlayer's default data source sends
        // none and gets a 403 instead of a manifest. See StreamHeaders.
        httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(com.nashat.tv.util.StreamHeaders.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);

        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpDataSourceFactory))
                .build();

        playerView.setPlayer(player);

        // Add player listener for better error handling
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    // Track when playback actually starts
                    if (playbackStartTime == 0) {
                        playbackStartTime = System.currentTimeMillis();
                    }
                    applyLiveOrOnDemandControls();
                }
            }

            @Override
            public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                // The old handler surfaced error.getMessage() directly — raw
                // ExoPlayer text such as "Source error" or an HTTP status, in
                // English, telling the user nothing they could act on. The
                // cause is mapped to advice instead, and the detail goes to
                // Crashlytics where it is actually useful.
                Log.w(TAG, "Playback failed", error);
                int message;
                switch (error.errorCode) {
                    case com.google.android.exoplayer2.PlaybackException
                            .ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                    case com.google.android.exoplayer2.PlaybackException
                            .ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                        message = R.string.player_error_network;
                        break;
                    case com.google.android.exoplayer2.PlaybackException
                            .ERROR_CODE_IO_BAD_HTTP_STATUS:
                    case com.google.android.exoplayer2.PlaybackException
                            .ERROR_CODE_IO_FILE_NOT_FOUND:
                        message = R.string.player_error_source;
                        break;
                    default:
                        message = R.string.player_error_generic;
                }
                Snackbar.make(playerView, message, Snackbar.LENGTH_LONG).show();
            }
        });

        if (channelUrl != null && !channelUrl.trim().isEmpty()) {
            if (channelUrl.contains("karwan.tv")) {
                Toast.makeText(this, "Resolving channel stream...", Toast.LENGTH_SHORT).show();
                new com.nashat.tv.util.KarwanScraper().scrapeStreamUrl(this, channelUrl, new com.nashat.tv.util.KarwanScraper.ScrapeCallback() {
                    @Override
                    public void onStreamFound(String streamUrl) {
                        Log.d("PlayerActivity", "Resolved stream URL: " + streamUrl);
                        applyStreamHeaders(streamUrl);
                        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));
                        player.setMediaItem(mediaItem);
                        player.prepare();
                        if (SettingsActivity.isAutoPlayEnabled(PlayerActivity.this)) {
                            player.play();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("PlayerActivity", "Failed to resolve stream: " + error);
                        Toast.makeText(PlayerActivity.this, "Failed to resolve stream", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                applyStreamHeaders(channelUrl);
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(channelUrl));
                player.setMediaItem(mediaItem);
                player.prepare();
                // Respect Auto-Play setting
                if (SettingsActivity.isAutoPlayEnabled(PlayerActivity.this)) {
                    player.play();
                }
            }
        }
    }

    /**
     * Puts the Referer the given host insists on onto every request the player
     * is about to make. A host that needs none clears the properties, so a
     * previous channel's Referer is never carried over to an unrelated CDN.
     */
    private void applyStreamHeaders(String url) {
        if (httpDataSourceFactory == null) return;
        httpDataSourceFactory.setDefaultRequestProperties(
                com.nashat.tv.util.StreamHeaders.forUrl(url));
    }

    private void releasePlayer() {
        if (player != null) {
            // Record watch session stats before releasing
            if (playbackStartTime > 0) {
                long watchedMs = System.currentTimeMillis() - playbackStartTime;
                int watchedMinutes = (int) (watchedMs / 60000);
                if (watchedMinutes >= 1) { // Only record sessions >= 1 minute
                    try {
                        WatchStatsManager stats = new WatchStatsManager(this);
                        stats.recordSession(
                                channelName != null ? channelName : "Unknown",
                                channelCategory != null ? channelCategory : "General",
                                watchedMinutes
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                playbackStartTime = 0;
            }
            try {
                player.setPlayWhenReady(false);
                player.stop();
                player.clearMediaItems();
            } catch (Exception ignore) {
            }
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        if (trackSelector != null) {
            trackSelector = null;
        }
    }

    private void toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        isFullscreen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);

        if (fullscreenButton != null) {
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit_24);
        }
    }

    private void exitFullscreen() {
        isFullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

        if (fullscreenButton != null) {
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_24);
        }
    }

    private void showSpeedDialog() {
        String[] speedLabels = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
        
        new AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setSingleChoiceItems(speedLabels, currentSpeedIndex, (dialog, which) -> {
                    currentSpeedIndex = which;
                    float speed = playbackSpeeds[which];
                    if (player != null) {
                        player.setPlaybackParameters(new PlaybackParameters(speed));
                    }
                    updateSpeedDisplay();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSpeedDisplay() {
        if (playbackSpeedView != null) {
            playbackSpeedView.setText(playbackSpeeds[currentSpeedIndex] + "x");
        }
    }

    private void showSettingsDialog() {
        String[] options = {"Video Quality", "Audio Track", "Subtitles", "Playback Speed"};
        
        new AlertDialog.Builder(this)
                .setTitle("Player Settings")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showVideoQualityDialog();
                            break;
                        case 1:
                            showAudioTrackDialog();
                            break;
                        case 2:
                            showSubtitleDialog();
                            break;
                        case 3:
                            showSpeedDialog();
                            break;
                    }
                })
                .show();
    }

    private void showVideoQualityDialog() {
        if (trackSelector == null) return;
        
        String[] qualities = {"Auto", "720p", "480p", "360p", "240p"};
        
        new AlertDialog.Builder(this)
                .setTitle("Video Quality")
                .setItems(qualities, (dialog, which) -> {
                    MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
                    if (mappedTrackInfo != null) {
                        DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.getParameters().buildUpon();
                        
                        switch (which) {
                            case 0: // Auto
                                parametersBuilder.clearVideoSizeConstraints();
                                break;
                            case 1: // 720p
                                parametersBuilder.setMaxVideoSize(1280, 720);
                                break;
                            case 2: // 480p
                                parametersBuilder.setMaxVideoSize(854, 480);
                                break;
                            case 3: // 360p
                                parametersBuilder.setMaxVideoSize(640, 360);
                                break;
                            case 4: // 240p
                                parametersBuilder.setMaxVideoSize(426, 240);
                                break;
                        }
                        
                        trackSelector.setParameters(parametersBuilder);
                        Toast.makeText(this, "Quality set to " + qualities[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showAudioTrackDialog() {
        if (trackSelector == null) return;
        
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_AUDIO) {
                    // Show audio track selection dialog
                    Toast.makeText(this, "Audio track selection available", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        } else {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSubtitleDialog() {
        String[] options = {"📁 Load subtitle file (.srt / .vtt)", "❌ Disable subtitles"};
        new AlertDialog.Builder(this)
                .setTitle("Subtitles")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                                new String[]{"application/x-subrip", "text/vtt", "text/plain", "*/*"});
                        try {
                            startActivityForResult(intent, REQ_PICK_SUBTITLE);
                        } catch (Exception e) {
                            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        applySubtitleUri(null);
                        Toast.makeText(this, "Subtitles disabled", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private static final int REQ_PICK_SUBTITLE = 9931;

    private void applySubtitleUri(@Nullable Uri subtitleUri) {
        if (player == null || channelUrl == null) return;
        long savedPosition = player.getCurrentPosition();

        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(channelUrl));
        if (subtitleUri != null) {
            String mime = MimeTypes.APPLICATION_SUBRIP;
            String ext = subtitleUri.toString().toLowerCase(java.util.Locale.US);
            if (ext.endsWith(".vtt")) mime = MimeTypes.TEXT_VTT;
            else if (ext.endsWith(".ssa") || ext.endsWith(".ass")) mime = MimeTypes.TEXT_SSA;

            MediaItem.SubtitleConfiguration sub = new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(mime)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build();
            builder.setSubtitleConfigurations(java.util.Collections.singletonList(sub));
            Toast.makeText(this, "Subtitle loaded", Toast.LENGTH_SHORT).show();
        }

        player.setMediaItem(builder.build());
        player.prepare();
        player.seekTo(savedPosition);
        player.play();
    }

    private void showVolumeDialog() {
        if (audioManager == null) return;
        
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        
        new AlertDialog.Builder(this)
                .setTitle("Volume: " + (currentVolume * 100 / maxVolume) + "%")
                .setMessage("Use device volume buttons to adjust")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterImmersiveMode();
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
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

    // ===================== Gesture Controls & Overlays =====================

    private void setupGestureControls() {
        if (handler == null) handler = new Handler(Looper.getMainLooper());

        // Double-tap detectors for quick seek
        leftGestureDetector = new GestureDetector(this, createDoubleTapDetector(true));
        rightGestureDetector = new GestureDetector(this, createDoubleTapDetector(false));

        if (gestureAreaLeft != null) {
            gestureAreaLeft.setOnTouchListener((v, event) -> {
                leftGestureDetector.onTouchEvent(event);
                return handleTouch(true, v, event);
            });
        }
        if (gestureAreaRight != null) {
            gestureAreaRight.setOnTouchListener((v, event) -> {
                rightGestureDetector.onTouchEvent(event);
                return handleTouch(false, v, event);
            });
        }

        // Optionally show one-time hints (kept hidden by default)
        if (overlayHints != null) overlayHints.setVisibility(View.GONE);
    }

    private GestureDetector.SimpleOnGestureListener createDoubleTapDetector(final boolean isLeftArea) {
        return new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Seek +/-10s on double tap
                performSeekBy(isLeftArea ? -10_000 : 10_000);
                return true;
            }
        };
    }

    private boolean handleTouch(boolean isLeftArea, View v, MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                touchStartX = event.getX();
                touchStartY = event.getY();
                isSeeking = false;
                seekAccumulationMs = 0;

                if (isLeftArea) {
                    startBrightness = getCurrentWindowBrightness();
                } else if (audioManager != null) {
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }
                handler.removeCallbacks(hideOverlaysRunnable);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;

                // Decide if this is a seek (horizontal) or adjustment (vertical)
                if (!isSeeking && Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 24f) {
                    isSeeking = true;
                }

                if (isSeeking) {
                    // Map full width swipe to +/- 60s
                    int width = Math.max(1, v.getWidth());
                    long deltaMs = (long) (dx / width * 60_000L);
                    seekAccumulationMs = deltaMs;
                    showSeekOverlay(deltaMs);
                } else {
                    // Vertical adjustments
                    int height = Math.max(1, v.getHeight());
                    float fraction = -(dy / height); // up = positive
                    if (isLeftArea) {
                        adjustBrightnessByFraction(fraction);
                    } else {
                        adjustVolumeByFraction(fraction);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isSeeking) {
                    commitSeek();
                }
                // Hide overlays after short delay
                handler.removeCallbacks(hideOverlaysRunnable);
                handler.postDelayed(hideOverlaysRunnable, 800);
                return true;
            }
        }
        return false;
    }

    private void commitSeek() {
        if (player == null) return;
        try {
            if (!player.isCurrentMediaItemSeekable()) return;
        } catch (Throwable t) {
            // Some sources might not report seekable; be safe
        }
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        long target = pos + seekAccumulationMs;
        if (dur > 0) {
            target = Math.max(0, Math.min(target, dur));
        } else {
            target = Math.max(0, target);
        }
        player.seekTo(target);
    }

    private void performSeekBy(long deltaMs) {
        if (player == null) return;
        try {
            if (!player.isCurrentMediaItemSeekable()) return;
        } catch (Throwable t) {}
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        long target = pos + deltaMs;
        if (dur > 0) target = Math.max(0, Math.min(target, dur));
        else target = Math.max(0, target);
        player.seekTo(target);
        showSeekOverlay(deltaMs);
        handler.removeCallbacks(hideOverlaysRunnable);
        handler.postDelayed(hideOverlaysRunnable, 800);
    }

    private void showSeekOverlay(long deltaMs) {
        if (overlaySeek == null || seekSeconds == null || seekDirectionIcon == null) return;
        overlaySeek.setVisibility(View.VISIBLE);
        boolean forward = deltaMs >= 0;
        int seconds = (int) Math.abs(deltaMs / 1000);
        seekSeconds.setText((forward ? "+" : "-") + seconds + "s");
        seekDirectionIcon.setImageResource(forward ? R.drawable.ic_fast_forward_24 : R.drawable.ic_fast_rewind_24);
    }

    private void adjustBrightnessByFraction(float fractionDelta) {
        float base = startBrightness;
        if (base < 0f) base = 0.5f; // default if not set
        float target = clamp(base + fractionDelta, 0.05f, 1.0f);
        setWindowBrightness(target);

        if (overlayBrightness != null) overlayBrightness.setVisibility(View.VISIBLE);
        if (brightnessProgress != null) brightnessProgress.setProgress((int) (target * 100));
        if (brightnessPercent != null) brightnessPercent.setText((int) (target * 100) + "%");

        handler.removeCallbacks(hideOverlaysRunnable);
        handler.postDelayed(hideOverlaysRunnable, 800);
    }

    private void adjustVolumeByFraction(float fractionDelta) {
        if (audioManager == null) return;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int base = (startVolume >= 0) ? startVolume : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int delta = Math.round(fractionDelta * max);
        int target = clamp(base + delta, 0, max);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);

        if (overlayVolume != null) overlayVolume.setVisibility(View.VISIBLE);
        if (volumeProgress != null) volumeProgress.setProgress((int) (target * 100f / max));
        if (volumePercent != null) volumePercent.setText((int) (target * 100f / max) + "%");

        handler.removeCallbacks(hideOverlaysRunnable);
        handler.postDelayed(hideOverlaysRunnable, 800);
    }

    private float getCurrentWindowBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        return lp.screenBrightness; // -1 if system default
    }

    private void setWindowBrightness(float value01) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = clamp(value01, 0.05f, 1.0f);
        getWindow().setAttributes(lp);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullscreen) {
            enterImmersiveMode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        if (castManager != null) {
            castManager.release();
            castManager = null;
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
