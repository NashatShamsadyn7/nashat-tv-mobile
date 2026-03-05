package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FavoritesMoviesManager {
    private static final String PREFS_NAME = "movies_favorites";
    private static final String KEY_MOVIES = "fav_list";
    private SharedPreferences prefs;

    public FavoritesMoviesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addMovie(Movie movie) {
        List<Movie> list = getMovies();
        for (Movie m : list) {
            if (m.getUrl().equals(movie.getUrl())) return; // Already exists
        }
        list.add(0, movie); // add to top
        saveMovies(list);
    }

    public void removeMovie(Movie movie) {
        List<Movie> list = getMovies();
        for (int i=0; i<list.size(); i++) {
            if (list.get(i).getUrl().equals(movie.getUrl())) {
                list.remove(i);
                break;
            }
        }
        saveMovies(list);
    }

    public boolean isFavorite(Movie movie) {
        List<Movie> list = getMovies();
        for (Movie m : list) {
            if (m.getUrl().equals(movie.getUrl())) return true;
        }
        return false;
    }

    public List<Movie> getMovies() {
        List<Movie> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_MOVIES, "[]");
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

    private void saveMovies(List<Movie> list) {
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
            prefs.edit().putString(KEY_MOVIES, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
