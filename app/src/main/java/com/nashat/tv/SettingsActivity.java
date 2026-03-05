package com.nashat.tv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.Map;

public class SettingsActivity extends BaseActivity {

    private static final String PREFS_SETTINGS = "app_settings";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_AUTO_PLAY = "auto_play";
    private static final String KEY_HD_QUALITY = "hd_quality";
    private static final String KEY_NOTIFICATIONS = "notifications";

    private SharedPreferences settings;

    private MaterialSwitch switchDarkMode;
    private MaterialSwitch switchAutoPlay;
    private MaterialSwitch switchHDQuality;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);

        initializeViews();
        setupToolbar();
        loadSettings();
        setupClickListeners();
    }

    private void initializeViews() {
        switchDarkMode = findViewById(R.id.switchDarkMode);
        switchAutoPlay = findViewById(R.id.switchAutoPlay);
        switchHDQuality = findViewById(R.id.switchHDQuality);

        // Appearance section
        findViewById(R.id.theme_option).setOnClickListener(v -> showThemeDialog());
        findViewById(R.id.language_option).setOnClickListener(v -> showLanguageDialog());

        // Watch Stats section
        View statsOption = findViewById(R.id.stats_option);
        if (statsOption != null) {
            statsOption.setOnClickListener(v -> showWatchStatsDialog());
            updateStatsPreview();
        }

        // Playlists section
        View playlistsOption = findViewById(R.id.playlists_option);
        if (playlistsOption != null) {
            playlistsOption.setOnClickListener(v -> showPlaylistsDialog());
            updatePlaylistsPreview();
        }

        // Support section
        findViewById(R.id.about_option).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        findViewById(R.id.contact_option).setOnClickListener(v -> {
            try {
                Intent email = new Intent(Intent.ACTION_SENDTO);
                email.setData(android.net.Uri.parse("mailto:"));
                email.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.contact_email)});
                email.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - Support");
                startActivity(Intent.createChooser(email, getString(R.string.contact_us)));
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_loading, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.rate_option).setOnClickListener(v -> {
            try {
                android.net.Uri uri = android.net.Uri.parse("market://details?id=" + getPackageName());
                Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(goToMarket);
            } catch (Exception e) {
                android.net.Uri uri = android.net.Uri.parse(
                        "https://play.google.com/store/apps/details?id=" + getPackageName());
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });

        // Share App
        findViewById(R.id.share_option).setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    getString(R.string.share_message) +
                    "\nhttps://play.google.com/store/apps/details?id=" + getPackageName());
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)));
        });

        // Clear Cache
        findViewById(R.id.clear_cache_option).setOnClickListener(v -> showCacheDialog());

        // Update current language display
        updateLanguageDisplay();

        // Show version
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView tvVersion = findViewById(R.id.tvVersionInfo);
            if (tvVersion != null) {
                tvVersion.setText("v" + versionName);
            }
        } catch (Exception ignored) {}
    }

    private void updateLanguageDisplay() {
        TextView tvLang = findViewById(R.id.tvCurrentLang);
        if (tvLang != null) {
            String code = LocaleManager.getSavedLocale(this);
            switch (code) {
                case "ckb": tvLang.setText(getString(R.string.lang_ckb)); break;
                case "ar": tvLang.setText(getString(R.string.lang_ar)); break;
                default: tvLang.setText(getString(R.string.lang_en)); break;
            }
        }
    }

    private void updateStatsPreview() {
        TextView tvStatsPreview = findViewById(R.id.tvStatsPreview);
        if (tvStatsPreview != null) {
            WatchStatsManager stats = new WatchStatsManager(this);
            String totalTime = stats.getTotalTimeFormatted();
            long sessions = stats.getTotalSessions();
            tvStatsPreview.setText(totalTime + " · " + sessions + " " + getString(R.string.stats_sessions));
        }
    }

    private void updatePlaylistsPreview() {
        TextView tvPlaylistsPreview = findViewById(R.id.tvPlaylistsPreview);
        if (tvPlaylistsPreview != null) {
            PlaylistManager pm = new PlaylistManager(this);
            int count = pm.getPlaylists().size();
            tvPlaylistsPreview.setText(count + " " + getString(R.string.playlists_count));
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setElevation(0f);
        }
    }

    private void loadSettings() {
        int mode = settings.getInt(BaseActivity.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES);
        switchDarkMode.setChecked(mode == AppCompatDelegate.MODE_NIGHT_YES);
        switchAutoPlay.setChecked(settings.getBoolean(KEY_AUTO_PLAY, true));
        switchHDQuality.setChecked(settings.getBoolean(KEY_HD_QUALITY, true));
    }

    private void setupClickListeners() {
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return; // only run if triggered by user
            int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);
            settings.edit()
                    .putBoolean(KEY_DARK_MODE, isChecked)
                    .putInt(BaseActivity.KEY_THEME_MODE, mode)
                    .apply();
            Toast.makeText(this, getString(isChecked ? R.string.dark_mode : R.string.light_mode),
                    Toast.LENGTH_SHORT).show();
            // Restart completely to apply new Theme Style
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply();
            Toast.makeText(this,
                    getString(R.string.auto_play) + " " + (isChecked ? "✓" : "✗"),
                    Toast.LENGTH_SHORT).show();
        });

        switchHDQuality.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean(KEY_HD_QUALITY, isChecked).apply();
            Toast.makeText(this,
                    getString(R.string.prefer_hd_quality) + " " + (isChecked ? "✓" : "✗"),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showThemeDialog() {
        String[] themes = {
                "🌙 " + getString(R.string.dark_mode),
                "☀️ " + getString(R.string.light_mode)
        };

        int mode = settings.getInt(BaseActivity.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES);
        int checkedItem = (mode == AppCompatDelegate.MODE_NIGHT_YES) ? 0 : 1;

        new AlertDialog.Builder(this)
                .setTitle(R.string.theme)
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    int newMode = (which == 0) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;

                    AppCompatDelegate.setDefaultNightMode(newMode);
                    settings.edit()
                            .putInt(BaseActivity.KEY_THEME_MODE, newMode)
                            .putBoolean(KEY_DARK_MODE, newMode == AppCompatDelegate.MODE_NIGHT_YES)
                            .apply();
                    
                    switchDarkMode.setChecked(newMode == AppCompatDelegate.MODE_NIGHT_YES);
                    dialog.dismiss();
                    
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    // ─── Watch Statistics Dialog ────────────────────────

    private void showWatchStatsDialog() {
        WatchStatsManager stats = new WatchStatsManager(this);
        
        StringBuilder sb = new StringBuilder();
        
        // Header stats
        sb.append("⏱ ").append(getString(R.string.stats_total_time)).append(": ")
          .append(stats.getTotalTimeFormatted()).append("\n");
        sb.append("📺 ").append(getString(R.string.stats_sessions)).append(": ")
          .append(stats.getTotalSessions()).append("\n");
        sb.append("🏆 ").append(getString(R.string.stats_top_category)).append(": ")
          .append(stats.getTopCategory()).append("\n\n");

        // Weekly breakdown
        sb.append("📊 ").append(getString(R.string.stats_this_week)).append(":\n");
        String[] dayNames = {"Today", "Yesterday", "2d ago", "3d ago", "4d ago", "5d ago", "6d ago"};
        int[] weekly = stats.getWeeklyMinutes();
        for (int i = 0; i < 7; i++) {
            if (weekly[i] > 0) {
                sb.append("  ").append(dayNames[i]).append(": ");
                if (weekly[i] >= 60) {
                    sb.append(weekly[i] / 60).append("h ").append(weekly[i] % 60).append("m");
                } else {
                    sb.append(weekly[i]).append("m");
                }
                sb.append("\n");
            }
        }

        // Category breakdown
        Map<String, Long> catMap = stats.getCategoryMinutes();
        if (!catMap.isEmpty()) {
            sb.append("\n📂 ").append(getString(R.string.stats_by_category)).append(":\n");
            for (Map.Entry<String, Long> entry : catMap.entrySet()) {
                long mins = entry.getValue();
                String timeStr = mins >= 60 ? (mins / 60) + "h " + (mins % 60) + "m" : mins + "m";
                sb.append("  ").append(entry.getKey()).append(": ").append(timeStr).append("\n");
            }
        }

        // Recent sessions
        List<WatchStatsManager.SessionEntry> recent = stats.getSessionLog();
        if (!recent.isEmpty()) {
            sb.append("\n🕐 ").append(getString(R.string.stats_recent)).append(":\n");
            int count = Math.min(10, recent.size());
            for (int i = 0; i < count; i++) {
                WatchStatsManager.SessionEntry session = recent.get(i);
                sb.append("  • ").append(session.channelName)
                  .append(" (").append(session.minutes).append("m) — ")
                  .append(session.getFormattedDate()).append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("📈 " + getString(R.string.stats_title))
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.stats_reset, (dialog, which) -> {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.stats_reset)
                            .setMessage(R.string.stats_reset_confirm)
                            .setPositiveButton(R.string.clear_cache_btn, (d, w) -> {
                                stats.resetAll();
                                updateStatsPreview();
                                Toast.makeText(this, R.string.stats_reset_done, Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .show();
    }

    // ─── Playlists Dialog ──────────────────────────────

    private void showPlaylistsDialog() {
        PlaylistManager pm = new PlaylistManager(this);
        List<PlaylistManager.Playlist> playlists = pm.getPlaylists();

        if (playlists.isEmpty()) {
            // No playlists yet, offer to create one
            showCreatePlaylistDialog();
            return;
        }

        String[] names = new String[playlists.size() + 1];
        names[0] = "➕ " + getString(R.string.playlist_create_new);
        for (int i = 0; i < playlists.size(); i++) {
            PlaylistManager.Playlist p = playlists.get(i);
            names[i + 1] = p.emoji + " " + p.name + " (" + p.getItemCount() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("🗂 " + getString(R.string.playlists_title))
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        showCreatePlaylistDialog();
                    } else {
                        PlaylistManager.Playlist selected = playlists.get(which - 1);
                        showPlaylistDetailDialog(selected);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCreatePlaylistDialog() {
        String[] emojis = {"🎬", "📺", "🎵", "⭐", "❤️", "🔥", "🌙", "🎮", "📖", "🏆"};
        final String[] selectedEmoji = {"🎬"};

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.playlist_name_hint);
        input.setPadding(48, 32, 48, 16);

        new AlertDialog.Builder(this)
                .setTitle(R.string.playlist_create_new)
                .setView(input)
                .setPositiveButton(R.string.playlist_create, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        PlaylistManager pm = new PlaylistManager(this);
                        pm.createPlaylist(name, "", selectedEmoji[0]);
                        updatePlaylistsPreview();
                        Toast.makeText(this, R.string.playlist_created, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPlaylistDetailDialog(PlaylistManager.Playlist playlist) {
        StringBuilder sb = new StringBuilder();
        sb.append(playlist.emoji).append(" ").append(playlist.name).append("\n\n");

        if (playlist.items.isEmpty()) {
            sb.append(getString(R.string.playlist_empty));
        } else {
            for (int i = 0; i < playlist.items.size(); i++) {
                PlaylistManager.PlaylistItem item = playlist.items.get(i);
                sb.append(i + 1).append(". ").append(item.title);
                if (item.subtitle != null && !item.subtitle.isEmpty()) {
                    sb.append(" — ").append(item.subtitle);
                }
                sb.append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(playlist.name)
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.playlist_rename, (dialog, which) -> {
                    android.widget.EditText input = new android.widget.EditText(this);
                    input.setText(playlist.name);
                    input.setPadding(48, 32, 48, 16);

                    new AlertDialog.Builder(this)
                            .setTitle(R.string.playlist_rename)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok, (d, w) -> {
                                String newName = input.getText().toString().trim();
                                if (!newName.isEmpty()) {
                                    new PlaylistManager(this).renamePlaylist(playlist.id, newName);
                                    updatePlaylistsPreview();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(R.string.playlist_delete, (dialog, which) -> {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.playlist_delete)
                            .setMessage(R.string.playlist_delete_confirm)
                            .setPositiveButton(R.string.clear_cache_btn, (d, w) -> {
                                new PlaylistManager(this).deletePlaylist(playlist.id);
                                updatePlaylistsPreview();
                                Toast.makeText(this, R.string.playlist_deleted, Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .show();
    }

    // ─── Existing dialogs ──────────────────────────────

    private void showLanguageDialog() {
        String[] languages = {
            getString(R.string.lang_ckb),
            getString(R.string.lang_ar),
            getString(R.string.lang_en)
        };
        String[] codes = {"ckb", "ar", "en"};

        int checkedItem = 0;
        String currentLang = LocaleManager.getSavedLocale(this);
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equalsIgnoreCase(currentLang)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_language)
                .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                    LocaleManager.setLocale(this, codes[which]);
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> recreate())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCacheDialog() {
        long cacheSize = getDirSize(getCacheDir());
        String sizeStr = formatSize(cacheSize);

        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_cache)
                .setMessage(getString(R.string.clear_cache_confirm, sizeStr))
                .setPositiveButton(R.string.clear_cache_btn, (dialog, which) -> {
                    clearAppCache();
                    Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void clearAppCache() {
        try {
            new Thread(() -> {
                com.bumptech.glide.Glide.get(this).clearDiskCache();
                runOnUiThread(() -> com.bumptech.glide.Glide.get(this).clearMemory());
            }).start();
            if (getCacheDir() != null) deleteDir(getCacheDir());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private long getDirSize(java.io.File dir) {
        long size = 0;
        if (dir != null && dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    size += f.isDirectory() ? getDirSize(f) : f.length();
                }
            }
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private boolean deleteDir(java.io.File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    if (!deleteDir(new java.io.File(dir, child))) return false;
                }
            }
            return dir.delete();
        }
        return dir != null && dir.isFile() && dir.delete();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    public static boolean isDarkModeEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean(KEY_DARK_MODE, false);
    }
    public static boolean isAutoPlayEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean(KEY_AUTO_PLAY, true);
    }
    public static boolean isHDQualityEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean(KEY_HD_QUALITY, true);
    }
}