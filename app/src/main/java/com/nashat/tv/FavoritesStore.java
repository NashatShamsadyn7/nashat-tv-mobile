package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FavoritesStore {

    private static final String PREFS = "favorites_prefs";
    private static final String KEY_FAVORITES = "favorites_set";

    private final SharedPreferences prefs;

    public FavoritesStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Set<String> getAll() {
        Set<String> set = prefs.getStringSet(KEY_FAVORITES, null);
        if (set == null) return Collections.emptySet();
        // Return a copy to avoid ConcurrentModification
        return new HashSet<>(set);
    }

    public boolean isFavorite(Channel channel) {
        String key = keyOf(channel);
        if (TextUtils.isEmpty(key)) return false;
        return getAll().contains(key);
    }

    public void toggle(Channel channel) {
        String key = keyOf(channel);
        if (TextUtils.isEmpty(key)) return;
        Set<String> set = new HashSet<>(getAll());
        if (set.contains(key)) {
            set.remove(key);
        } else {
            set.add(key);
        }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply();
    }

    private String keyOf(Channel c) {
        if (c == null) return null;
        String url = c.getUrl();
        if (!TextUtils.isEmpty(url)) return url.trim();
        return c.getName();
    }
}