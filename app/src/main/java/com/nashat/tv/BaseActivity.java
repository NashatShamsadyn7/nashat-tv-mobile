package com.nashat.tv;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

public abstract class BaseActivity extends AppCompatActivity {

    public static final String PREFS_SETTINGS = "app_settings";
    public static final String KEY_THEME_MODE = "theme_mode"; // AppCompatDelegate.MODE_NIGHT_*

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme before rendering any views
        applyThemeFromPrefs();
        // Material 3 dynamic color (Android 12+) - follows system wallpaper palette
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
    }

    protected void applyThemeFromPrefs() {
        int mode = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}