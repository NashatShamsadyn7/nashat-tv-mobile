package com.nashat.tv;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends BaseActivity {

    private static final String PREFS_SETTINGS = "app_settings";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_AUTO_PLAY = "auto_play";
    private static final String KEY_HD_QUALITY = "hd_quality";
    private static final String KEY_NOTIFICATIONS = "notifications";

    private SharedPreferences settings;
    
    // UI Components
    private MaterialSwitch switchDarkMode;
    private MaterialSwitch switchAutoPlay;
    private MaterialSwitch switchHDQuality;
    private MaterialSwitch switchNotifications;
    private MaterialCardView cardTheme;
    private MaterialCardView cardLanguage;
    private MaterialCardView cardAbout;
    private MaterialCardView cardCache;

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
        
        findViewById(R.id.theme_option).setOnClickListener(v -> showThemeDialog());
        findViewById(R.id.about_option).setOnClickListener(v -> startActivity(new android.content.Intent(this, AboutActivity.class)));
        findViewById(R.id.contact_option).setOnClickListener(v -> {
            try {
                android.content.Intent email = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
                email.setData(android.net.Uri.parse("mailto:"));
                email.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{getString(R.string.contact_email)});
                email.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - Support");
                startActivity(android.content.Intent.createChooser(email, getString(R.string.contact_us)));
            } catch (Exception e) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.rate_option).setOnClickListener(v -> {
            try {
                android.net.Uri uri = android.net.Uri.parse("market://details?id=" + getPackageName());
                android.content.Intent goToMarket = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri);
                goToMarket.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY |
                                    android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                                    android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(goToMarket);
            } catch (Exception e) {
                // Fallback to web Play Store
                android.net.Uri uri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName());
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri));
            }
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }
        // Reduce elevation for a softer, modern look
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            toolbar.setElevation(0f);
        }
    }

    private void loadSettings() {
        // Theme mode
        int mode = settings.getInt(BaseActivity.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        boolean darkChecked = (mode == AppCompatDelegate.MODE_NIGHT_YES);
        switchDarkMode.setChecked(darkChecked);

        // Other toggles
        switchAutoPlay.setChecked(settings.getBoolean(KEY_AUTO_PLAY, true));
        switchHDQuality.setChecked(settings.getBoolean(KEY_HD_QUALITY, true));
    }

    private void setupClickListeners() {
        // Dark Mode Switch (quick toggle)
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);
            settings.edit()
                    .putBoolean(KEY_DARK_MODE, isChecked) // legacy toggle, kept for compatibility
                    .putInt(BaseActivity.KEY_THEME_MODE, mode)
                    .apply();
            Toast.makeText(this, getString(isChecked ? R.string.dark_mode : R.string.light_mode), Toast.LENGTH_SHORT).show();
            recreate();
        });

        // Auto Play Switch
        switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply();
            Toast.makeText(this, "Auto play " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        // HD Quality Switch
        switchHDQuality.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean(KEY_HD_QUALITY, isChecked).apply();
            Toast.makeText(this, "HD quality " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        // Notifications switch not present in layout; removed to avoid NPE

    }

    private void showThemeDialog() {
        String[] themes = {
                getString(R.string.light_mode),
                getString(R.string.dark_mode),
                getString(R.string.system_default)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.theme)
                .setItems(themes, (dialog, which) -> {
                    int mode;
                    if (which == 0) {
                        mode = AppCompatDelegate.MODE_NIGHT_NO;
                    } else if (which == 1) {
                        mode = AppCompatDelegate.MODE_NIGHT_YES;
                    } else {
                        mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    }
                    AppCompatDelegate.setDefaultNightMode(mode);
                    settings.edit()
                            .putInt(BaseActivity.KEY_THEME_MODE, mode)
                            .putBoolean(KEY_DARK_MODE, mode == AppCompatDelegate.MODE_NIGHT_YES) // keep legacy key consistent
                            .apply();
                    // update quick toggle to reflect selected theme
                    switchDarkMode.setChecked(mode == AppCompatDelegate.MODE_NIGHT_YES);
                    recreate();
                })
                .show();
    }

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
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCacheDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Cache")
                .setMessage("This will clear all cached data including channel logos and temporary files. Continue?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    // Implement cache clearing logic
                    clearAppCache();
                    Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAppCache() {
        try {
            // Clear Glide cache
            new Thread(() -> {
                com.bumptech.glide.Glide.get(this).clearDiskCache();
                runOnUiThread(() -> {
                    com.bumptech.glide.Glide.get(this).clearMemory();
                });
            }).start();

            // Clear app cache directory
            if (getCacheDir() != null) {
                deleteDir(getCacheDir());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean deleteDir(java.io.File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new java.io.File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Static methods to get settings from other activities
    public static boolean isDarkModeEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false);
    }

    public static boolean isAutoPlayEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                .getBoolean(KEY_AUTO_PLAY, true);
    }

    public static boolean isHDQualityEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                .getBoolean(KEY_HD_QUALITY, true);
    }

    public static boolean areNotificationsEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATIONS, true);
    }
}