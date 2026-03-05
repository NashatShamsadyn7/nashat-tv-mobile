package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WatchlistManager {
    private static final String PREFS_NAME = "watchlist_prefs";
    private static final String KEY_ITEMS = "watchlist_items";
    private final SharedPreferences prefs;

    public WatchlistManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addItem(Movie movie) {
        List<Movie> list = getItems();
        for (Movie m : list) {
            if (m.getUrl().equals(movie.getUrl())) return; // Already exists
        }
        list.add(0, movie); // add to top
        saveItems(list);
    }

    public void removeItem(Movie movie) {
        List<Movie> list = getItems();
        for (int i=0; i<list.size(); i++) {
            if (list.get(i).getUrl().equals(movie.getUrl())) {
                list.remove(i);
                break;
            }
        }
        saveItems(list);
    }

    public boolean isInWatchlist(Movie movie) {
        List<Movie> list = getItems();
        for (Movie m : list) {
            if (m.getUrl().equals(movie.getUrl())) return true;
        }
        return false;
    }

    public List<Movie> getItems() {
        List<Movie> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i=0; i<arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Movie m = new Movie(
                    obj.optString("name"),
                    obj.optString("poster"),
                    obj.optString("category"),
                    obj.optString("url"),
                    obj.optString("description"),
                    obj.optString("year"),
                    obj.optDouble("voteAverage", 0.0)
                );
                list.add(m);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void saveItems(List<Movie> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Movie m : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", m.getName());
                obj.put("poster", m.getPoster());
                obj.put("category", m.getCategory());
                obj.put("url", m.getUrl());
                obj.put("description", m.getDescription());
                obj.put("year", m.getYear());
                obj.put("voteAverage", m.getVoteAverage());
                arr.put(obj);
            }
            prefs.edit().putString(KEY_ITEMS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
