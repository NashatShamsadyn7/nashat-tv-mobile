package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages search history persistence using SharedPreferences.
 * Stores up to 15 recent search queries.
 */
public class SearchHistoryManager {
    private static final String PREFS_NAME = "search_history";
    private static final String KEY_MOVIES = "movies_history";
    private static final String KEY_SERIES = "series_history";
    private static final int MAX_HISTORY = 15;

    private final SharedPreferences prefs;

    public SearchHistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addMovieSearch(String query) {
        addSearch(KEY_MOVIES, query);
    }

    public void addSeriesSearch(String query) {
        addSearch(KEY_SERIES, query);
    }

    public List<String> getMovieHistory() {
        return getHistory(KEY_MOVIES);
    }

    public List<String> getSeriesHistory() {
        return getHistory(KEY_SERIES);
    }

    public void clearMovieHistory() {
        prefs.edit().remove(KEY_MOVIES).apply();
    }

    public void clearSeriesHistory() {
        prefs.edit().remove(KEY_SERIES).apply();
    }

    private void addSearch(String key, String query) {
        if (query == null || query.trim().isEmpty()) return;
        query = query.trim();
        List<String> history = getHistory(key);
        // Remove if already exists (move to top)
        history.remove(query);
        // Add to top
        history.add(0, query);
        // Trim to max size
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        saveHistory(key, history);
    }

    private List<String> getHistory(String key) {
        List<String> list = new ArrayList<>();
        String jsonStr = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void saveHistory(String key, List<String> list) {
        try {
            JSONArray arr = new JSONArray();
            for (String s : list) {
                arr.put(s);
            }
            prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
