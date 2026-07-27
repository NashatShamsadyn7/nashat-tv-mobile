package com.nashat.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public abstract class BaseActivity extends AppCompatActivity {

    public static final String PREFS_SETTINGS = "app_settings";
    public static final String KEY_THEME_MODE = "theme_mode";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeFromPrefs();
        super.onCreate(savedInstanceState);
    }

    protected void applyThemeFromPrefs() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES);
        AppCompatDelegate.setDefaultNightMode(mode);
        setTheme(R.style.Theme_NashatTV);
    }

    public void navigateToTab(Class<?> targetClass) {
        if (this.getClass() == targetClass) {
            return;
        }
        Intent intent = new Intent(this, targetClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (this instanceof LiveTvActivity) {
            super.onBackPressed();
        } else if (this instanceof MoviesActivity || this instanceof SeriesActivity) {
            navigateToTab(LiveTvActivity.class);
        } else {
            super.onBackPressed();
        }
    }
}