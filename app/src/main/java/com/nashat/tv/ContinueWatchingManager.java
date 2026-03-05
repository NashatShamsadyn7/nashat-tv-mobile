package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages "Continue Watching" feature.
 * Stores movies/series that the user has started watching with timestamps.
 */
public class ContinueWatchingManager {
    private static final String PREFS_NAME = "continue_watching";
    private static final String KEY_LIST = "watching_list";
    private static final int MAX_ITEMS = 20;

    private final SharedPreferences prefs;

    public ContinueWatchingManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Add a movie to continue watching list.
     * @param movie The movie being watched
     * @param isSeries Whether this is a TV series
     * @param season Season number (for series)
     * @param episode Episode number (for series)
     */
    public void addItem(Movie movie, boolean isSeries, int season, int episode) {
        if (movie == null) return;
        List<WatchItem> list = getItems();
        
        // Remove existing entry for same movie/series
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).tmdbId.equals(movie.getUrl())) {
                list.remove(i);
            }
        }
        
        // Add to top
        WatchItem item = new WatchItem();
        item.name = movie.getName();
        item.poster = movie.getPoster();
        item.category = movie.getCategory();
        item.tmdbId = movie.getUrl();
        item.description = movie.getDescription();
        item.year = movie.getYear();
        item.isSeries = isSeries;
        item.season = season;
        item.episode = episode;
        item.timestamp = System.currentTimeMillis();
        
        list.add(0, item);
        
        // Trim
        while (list.size() > MAX_ITEMS) {
            list.remove(list.size() - 1);
        }
        
        saveItems(list);
    }

    public void addMovie(Movie movie) {
        addItem(movie, false, 0, 0);
    }

    public void addSeries(Movie series, int season, int episode) {
        addItem(series, true, season, episode);
    }

    public List<WatchItem> getItems() {
        List<WatchItem> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                WatchItem item = new WatchItem();
                item.name = obj.optString("name", "");
                item.poster = obj.optString("poster", "");
                item.category = obj.optString("category", "");
                item.tmdbId = obj.optString("tmdbId", "");
                item.description = obj.optString("description", "");
                item.year = obj.optString("year", "");
                item.isSeries = obj.optBoolean("isSeries", false);
                item.season = obj.optInt("season", 0);
                item.episode = obj.optInt("episode", 0);
                item.timestamp = obj.optLong("timestamp", 0);
                list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void removeItem(String tmdbId) {
        List<WatchItem> list = getItems();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).tmdbId.equals(tmdbId)) {
                list.remove(i);
            }
        }
        saveItems(list);
    }

    private void saveItems(List<WatchItem> list) {
        try {
            JSONArray arr = new JSONArray();
            for (WatchItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", item.name);
                obj.put("poster", item.poster);
                obj.put("category", item.category);
                obj.put("tmdbId", item.tmdbId);
                obj.put("description", item.description);
                obj.put("year", item.year);
                obj.put("isSeries", item.isSeries);
                obj.put("season", item.season);
                obj.put("episode", item.episode);
                obj.put("timestamp", item.timestamp);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void markEpisodeWatched(String tmdbId, int season, int episode) {
        String key = "watched_" + tmdbId + "_" + season + "_" + episode;
        prefs.edit().putBoolean(key, true).apply();
    }

    public boolean isEpisodeWatched(String tmdbId, int season, int episode) {
        String key = "watched_" + tmdbId + "_" + season + "_" + episode;
        return prefs.getBoolean(key, false);
    }

    /**
     * Data class for a continue watching item.
     */
    public static class WatchItem {
        public String name;
        public String poster;
        public String category;
        public String tmdbId;
        public String description;
        public String year;
        public boolean isSeries;
        public int season;
        public int episode;
        public long timestamp;

        public Movie toMovie() {
            return new Movie(name, poster, category, tmdbId, description, year);
        }

        public String getSubtitle() {
            if (isSeries && season > 0) {
                return "S" + season + " E" + episode;
            }
            return "Continue watching";
        }
    }
}
