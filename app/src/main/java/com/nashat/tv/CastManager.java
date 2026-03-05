package com.nashat.tv;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.common.images.WebImage;

/**
 * Manages Google Cast (Chromecast) integration for the player.
 * Handles session lifecycle, local↔remote player switching, and media routing.
 */
public class CastManager implements SessionAvailabilityListener {

    private static final String TAG = "CastManager";

    public interface CastCallback {
        /** Called when cast session starts — switch from local to cast player. */
        void onCastSessionStarted(CastPlayer castPlayer);

        /** Called when cast session ends — switch back to local player. */
        void onCastSessionEnded();

        /** Called when cast state changes (devices available / not available). */
        void onCastStateChanged(int newState);
    }

    private final Context context;
    private CastContext castContext;
    private CastPlayer castPlayer;
    private CastCallback callback;
    private CastStateListener castStateListener;

    // Current media metadata for casting
    private String currentTitle;
    private String currentUrl;
    private String currentLogoUrl;
    private String currentContentType;

    public CastManager(@NonNull Context context) {
        this.context = context;
    }

    /**
     * Initializes the Cast context and player.
     * Call this in Activity.onCreate() or onStart().
     */
    public void initialize(@Nullable CastCallback callback) {
        this.callback = callback;
        try {
            castContext = CastContext.getSharedInstance(context);

            castPlayer = new CastPlayer(castContext);
            castPlayer.setSessionAvailabilityListener(this);

            // Listen for cast state changes (devices available, connecting, etc.)
            castStateListener = newState -> {
                Log.d(TAG, "Cast state changed: " + castStateToString(newState));
                if (callback != null) {
                    callback.onCastStateChanged(newState);
                }
            };
            castContext.addCastStateListener(castStateListener);

        } catch (Exception e) {
            Log.w(TAG, "Cast SDK not available: " + e.getMessage());
            // Cast not available on this device (missing Google Play Services)
        }
    }

    /**
     * Sets up a MediaRouteButton to show the Cast icon.
     */
    public void setupCastButton(@NonNull MediaRouteButton castButton) {
        try {
            CastButtonFactory.setUpMediaRouteButton(context, castButton);
        } catch (Exception e) {
            Log.w(TAG, "Could not set up Cast button: " + e.getMessage());
        }
    }

    /**
     * Updates the current media info. Call before starting playback.
     */
    public void setCurrentMedia(String title, String url, String logoUrl) {
        this.currentTitle = title;
        this.currentUrl = url;
        this.currentLogoUrl = logoUrl;
        this.currentContentType = guessContentType(url);
    }

    /**
     * Returns true if currently casting.
     */
    public boolean isCasting() {
        if (castContext == null) return false;
        CastSession session = castContext.getSessionManager().getCurrentCastSession();
        return session != null && session.isConnected();
    }

    /**
     * Sends the current media to the Chromecast device.
     */
    public void loadMediaOnCast() {
        if (castPlayer == null || currentUrl == null) return;

        try {
            // Build metadata
            MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
            metadata.putString(MediaMetadata.KEY_TITLE, currentTitle != null ? currentTitle : "Nashat TV");
            if (currentLogoUrl != null && !currentLogoUrl.isEmpty()) {
                metadata.addImage(new WebImage(Uri.parse(currentLogoUrl)));
            }

            // Build MediaInfo
            MediaInfo.Builder mediaInfoBuilder = new MediaInfo.Builder(currentUrl)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(metadata);

            if (currentContentType != null) {
                mediaInfoBuilder.setContentType(currentContentType);
            }

            MediaInfo mediaInfo = mediaInfoBuilder.build();

            // Load on Cast device
            MediaItem castItem = MediaItem.fromUri(Uri.parse(currentUrl));
            castPlayer.setMediaItem(castItem);
            castPlayer.prepare();
            castPlayer.play();

            Log.d(TAG, "Loaded media on Cast: " + currentTitle);
        } catch (Exception e) {
            Log.e(TAG, "Error loading media on Cast", e);
            Toast.makeText(context, "Error casting: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get the CastPlayer instance for direct control.
     */
    @Nullable
    public CastPlayer getCastPlayer() {
        return castPlayer;
    }

    /**
     * Returns whether Cast devices are available on the network.
     */
    public boolean areCastDevicesAvailable() {
        return castContext != null && castContext.getCastState() != CastState.NO_DEVICES_AVAILABLE;
    }

    /**
     * Clean up resources. Call in Activity.onDestroy().
     */
    public void release() {
        if (castPlayer != null) {
            castPlayer.setSessionAvailabilityListener(null);
            castPlayer.release();
            castPlayer = null;
        }
        if (castContext != null && castStateListener != null) {
            castContext.removeCastStateListener(castStateListener);
        }
    }

    // ─── SessionAvailabilityListener ───────────────────────────────

    @Override
    public void onCastSessionAvailable() {
        Log.d(TAG, "Cast session available — switching to remote playback");
        if (callback != null && castPlayer != null) {
            loadMediaOnCast();
            callback.onCastSessionStarted(castPlayer);
        }
    }

    @Override
    public void onCastSessionUnavailable() {
        Log.d(TAG, "Cast session unavailable — switching to local playback");
        if (callback != null) {
            callback.onCastSessionEnded();
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private String guessContentType(String url) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (lower.contains(".m3u8") || lower.contains("m3u8")) {
            return "application/x-mpegURL";
        } else if (lower.contains(".mpd")) {
            return "application/dash+xml";
        } else if (lower.contains(".mp4")) {
            return "video/mp4";
        } else if (lower.contains(".mp3")) {
            return "audio/mpeg";
        }
        // Default for HLS streams (most IPTV)
        return "application/x-mpegURL";
    }

    private String castStateToString(int state) {
        switch (state) {
            case CastState.NO_DEVICES_AVAILABLE:
                return "NO_DEVICES_AVAILABLE";
            case CastState.NOT_CONNECTED:
                return "NOT_CONNECTED";
            case CastState.CONNECTING:
                return "CONNECTING";
            case CastState.CONNECTED:
                return "CONNECTED";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }
}
