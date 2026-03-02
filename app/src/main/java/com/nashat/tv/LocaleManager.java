package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public class LocaleManager {

    private static final String PREFS = "locale_prefs";
    private static final String KEY_LOCALE = "locale_code";

    public static Context applyLocale(Context base) {
        String code = getSavedLocale(base);
        if (code == null || code.isEmpty()) {
            // Default Sorani (Central Kurdish)
            code = "ckb";
        }
        return updateResources(base, code);
    }

    public static void setLocale(Context ctx, String code) {
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LOCALE, code).apply();
    }

    public static String getSavedLocale(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_LOCALE, "ckb");
    }

    private static Context updateResources(Context context, String language) {
        // Normalize to BCP-47 tag where possible (improves script/region & RTL handling)
        String tag = language;
        if ("ckb".equalsIgnoreCase(language)) {
            // Central Kurdish (Sorani) in Arabic script for Iraq
            tag = (Build.VERSION.SDK_INT >= 21) ? "ckb-Arab-IQ" : "ckb";
        } else if ("ar".equalsIgnoreCase(language)) {
            tag = "ar";
        } else if ("en".equalsIgnoreCase(language)) {
            tag = "en";
        }
    
        Locale locale;
        if (Build.VERSION.SDK_INT >= 21) {
            locale = Locale.forLanguageTag(tag);
        } else {
            locale = new Locale(language);
        }
    
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            config.setLayoutDirection(locale);
            return context.createConfigurationContext(config);
        } else {
            // Deprecated path for older devices
            config.locale = locale;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLayoutDirection(locale);
            }
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            return context;
        }
    }
}