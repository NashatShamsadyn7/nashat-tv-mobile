package com.nashat.tv;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LiveTvChannelCache {

    private static final long MAX_AGE_MS = 60 * 60 * 1000L; // 1 hour cache lifetime
    private static final String CACHE_FILE_NAME = "live_tv_channels_cache.json";
    private static final String PREFS_NAME = "live_tv_cache_prefs";
    private static final String KEY_LAST_UPDATED = "last_updated_at";

    private static List<Channel> channels = new ArrayList<>();
    private static long lastUpdatedAt;

    private LiveTvChannelCache() {
    }

    public static synchronized void clear(Context context) {
        channels.clear();
        lastUpdatedAt = 0L;
        if (context != null) {
            try {
                File cacheFile = new File(context.getCacheDir(), CACHE_FILE_NAME);
                if (cacheFile.exists()) {
                    cacheFile.delete();
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_LAST_UPDATED)
                        .apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static synchronized void save(Context context, List<Channel> items) {
        channels = new ArrayList<>(items);
        lastUpdatedAt = System.currentTimeMillis();
        persistToFile(context, channels);
    }

    public static synchronized List<Channel> get(Context context) {
        if (channels.isEmpty()) {
            loadFromFile(context);
        }
        return new ArrayList<>(channels);
    }

    public static synchronized boolean hasFreshData(Context context) {
        if (channels.isEmpty()) {
            loadFromFile(context);
        }
        return !channels.isEmpty() && (System.currentTimeMillis() - lastUpdatedAt) < MAX_AGE_MS;
    }

    public static synchronized List<Channel> getFreshOrEmpty(Context context) {
        if (channels.isEmpty()) {
            loadFromFile(context);
        }
        // Return cached channels immediately if available (even if older than MAX_AGE, to avoid blocking UI)
        if (!channels.isEmpty()) {
            return new ArrayList<>(channels);
        }
        return Collections.emptyList();
    }

    public static synchronized void mergeValidationResults(Context context, List<Channel> validated) {
        if (validated == null || validated.isEmpty()) {
            return;
        }
        if (channels.isEmpty()) {
            loadFromFile(context);
        }
        if (channels.isEmpty()) {
            return;
        }
        for (Channel result : validated) {
            if (result == null) continue;
            String id = result.getId();
            if (id == null || id.trim().isEmpty()) continue;
            for (int i = 0; i < channels.size(); i++) {
                Channel existing = channels.get(i);
                if (existing != null && id.equals(existing.getId())) {
                    existing.setAlive(result.isAlive());
                    existing.setPing(result.getPing());
                    existing.setLoadSpeed(result.getLoadSpeed());
                    existing.setLastChecked(result.getLastChecked());
                    break;
                }
            }
        }
        persistToFile(context, channels);
    }

    private static void persistToFile(Context context, List<Channel> items) {
        if (context == null) return;
        try {
            File cacheFile = new File(context.getCacheDir(), CACHE_FILE_NAME);
            try (FileWriter writer = new FileWriter(cacheFile)) {
                new Gson().toJson(items, writer);
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_UPDATED, lastUpdatedAt)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadFromFile(Context context) {
        if (context == null) return;
        try {
            File cacheFile = new File(context.getCacheDir(), CACHE_FILE_NAME);
            if (cacheFile.exists()) {
                Type type = new TypeToken<List<Channel>>(){}.getType();
                try (FileReader reader = new FileReader(cacheFile)) {
                    List<Channel> loaded = new Gson().fromJson(reader, type);
                    if (loaded != null) {
                        channels = loaded;
                    }
                }
                lastUpdatedAt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getLong(KEY_LAST_UPDATED, 0L);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
