package com.nashat.tv;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private VideoView videoView;
    private TextView tvLoadingStatus;
    private boolean hasNavigated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide status bar and make full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        videoView = findViewById(R.id.videoViewSplash);
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus);

        // Clear stale local caches once to force loading fresh popularity sorted lists
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        if (!prefs.getBoolean("v2_sorting_cached_v3", false)) {
            LiveTvChannelCache.clear(this);
            prefs.edit().putBoolean("v2_sorting_cached_v3", true).apply();
        }

        preloadChannels();

        // Path to your raw video file
        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.splash_video;
        Uri uri = Uri.parse(videoPath);
        videoView.setVideoURI(uri);

        // Transition to MainActivity when video finishes
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                navigateToMain();
            }
        });

        // In case of an error (e.g., video not supported), fallback gracefully
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                navigateToMain();
                return true; // Return true to indicate we handled the error
            }
        });

        // Start playing the video
        videoView.start();
    }

    private void preloadChannels() {
        LiveTvChannelRepository repository = new LiveTvChannelRepository(this);
        repository.loadChannelsOnce(new LiveTvChannelRepository.LoadCallback() {
            @Override
            public void onLoaded(java.util.List<Channel> channels, String sourcePath) {
                LiveTvChannelCache.save(SplashActivity.this, channels);
                if (tvLoadingStatus != null) {
                    tvLoadingStatus.setText(R.string.splash_ready_text);
                }
            }

            @Override
            public void onError(String message) {
                if (tvLoadingStatus != null) {
                    tvLoadingStatus.setText(R.string.splash_offline_text);
                }
            }
        });
    }

    private void navigateToMain() {
        if (hasNavigated) {
            return;
        }
        hasNavigated = true;
        Intent intent = new Intent(SplashActivity.this, LiveTvActivity.class);
        startActivity(intent);
        finish(); // Close SplashActivity so user can't navigate back to it
    }
}
