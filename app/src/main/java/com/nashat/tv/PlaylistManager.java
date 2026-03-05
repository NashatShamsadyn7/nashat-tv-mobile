package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages user-created custom playlists for channels, movies, and series.
 */
public class PlaylistManager {
    private static final String PREFS_NAME = "user_playlists";
    private static final String KEY_PLAYLISTS = "playlists_data";
    private static final int MAX_PLAYLISTS = 50;

    private final SharedPreferences prefs;

    public PlaylistManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Create a new playlist.
     * @return the new Playlist object (empty initial items)
     */
    public Playlist createPlaylist(String name, String description, String emoji) {
        List<Playlist> all = getPlaylists();
        if (all.size() >= MAX_PLAYLISTS) return null;

        Playlist p = new Playlist();
        p.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        p.name = name != null ? name : "My Playlist";
        p.description = description != null ? description : "";
        p.emoji = emoji != null ? emoji : "🎬";
        p.createdAt = System.currentTimeMillis();
        p.items = new ArrayList<>();

        all.add(0, p);
        savePlaylists(all);
        return p;
    }

    /**
     * Add an item to a playlist.
     */
    public boolean addItem(String playlistId, PlaylistItem item) {
        List<Playlist> all = getPlaylists();
        for (Playlist p : all) {
            if (p.id.equals(playlistId)) {
                // Check for duplicates
                for (PlaylistItem existing : p.items) {
                    if (existing.contentId != null && existing.contentId.equals(item.contentId)) {
                        return false; // Already exists
                    }
                }
                p.items.add(item);
                savePlaylists(all);
                return true;
            }
        }
        return false;
    }

    /**
     * Remove an item from a playlist.
     */
    public void removeItem(String playlistId, String contentId) {
        List<Playlist> all = getPlaylists();
        for (Playlist p : all) {
            if (p.id.equals(playlistId)) {
                for (int i = p.items.size() - 1; i >= 0; i--) {
                    if (p.items.get(i).contentId.equals(contentId)) {
                        p.items.remove(i);
                    }
                }
                savePlaylists(all);
                return;
            }
        }
    }

    /**
     * Delete an entire playlist.
     */
    public void deletePlaylist(String playlistId) {
        List<Playlist> all = getPlaylists();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(playlistId)) {
                all.remove(i);
            }
        }
        savePlaylists(all);
    }

    /**
     * Rename a playlist.
     */
    public void renamePlaylist(String playlistId, String newName) {
        List<Playlist> all = getPlaylists();
        for (Playlist p : all) {
            if (p.id.equals(playlistId)) {
                p.name = newName;
                savePlaylists(all);
                return;
            }
        }
    }

    /**
     * Get a specific playlist by ID.
     */
    public Playlist getPlaylist(String playlistId) {
        for (Playlist p : getPlaylists()) {
            if (p.id.equals(playlistId)) return p;
        }
        return null;
    }

    /**
     * Get all playlists.
     */
    public List<Playlist> getPlaylists() {
        List<Playlist> list = new ArrayList<>();
        String json = prefs.getString(KEY_PLAYLISTS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Playlist p = new Playlist();
                p.id = obj.optString("id", "");
                p.name = obj.optString("name", "");
                p.description = obj.optString("description", "");
                p.emoji = obj.optString("emoji", "🎬");
                p.createdAt = obj.optLong("createdAt", 0);

                p.items = new ArrayList<>();
                JSONArray itemsArr = obj.optJSONArray("items");
                if (itemsArr != null) {
                    for (int j = 0; j < itemsArr.length(); j++) {
                        JSONObject iObj = itemsArr.getJSONObject(j);
                        PlaylistItem item = new PlaylistItem();
                        item.contentId = iObj.optString("contentId", "");
                        item.title = iObj.optString("title", "");
                        item.subtitle = iObj.optString("subtitle", "");
                        item.imageUrl = iObj.optString("imageUrl", "");
                        item.streamUrl = iObj.optString("streamUrl", "");
                        item.type = iObj.optString("type", "channel");
                        item.addedAt = iObj.optLong("addedAt", 0);
                        p.items.add(item);
                    }
                }
                list.add(p);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void savePlaylists(List<Playlist> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Playlist p : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.id);
                obj.put("name", p.name);
                obj.put("description", p.description);
                obj.put("emoji", p.emoji);
                obj.put("createdAt", p.createdAt);

                JSONArray itemsArr = new JSONArray();
                for (PlaylistItem item : p.items) {
                    JSONObject iObj = new JSONObject();
                    iObj.put("contentId", item.contentId);
                    iObj.put("title", item.title);
                    iObj.put("subtitle", item.subtitle);
                    iObj.put("imageUrl", item.imageUrl);
                    iObj.put("streamUrl", item.streamUrl);
                    iObj.put("type", item.type);
                    iObj.put("addedAt", item.addedAt);
                    itemsArr.put(iObj);
                }
                obj.put("items", itemsArr);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ─── Data classes ───────────────────────────────

    public static class Playlist {
        public String id;
        public String name;
        public String description;
        public String emoji;
        public long createdAt;
        public List<PlaylistItem> items;

        public int getItemCount() {
            return items != null ? items.size() : 0;
        }
    }

    public static class PlaylistItem {
        public String contentId;
        public String title;
        public String subtitle;
        public String imageUrl;
        public String streamUrl;
        public String type; // "channel", "movie", "series"
        public long addedAt;

        public static PlaylistItem fromChannel(Channel channel) {
            PlaylistItem item = new PlaylistItem();
            item.contentId = channel.getName();
            item.title = channel.getName();
            item.subtitle = channel.getCategory();
            item.imageUrl = channel.getLogo();
            item.streamUrl = channel.getUrl();
            item.type = "channel";
            item.addedAt = System.currentTimeMillis();
            return item;
        }

        public static PlaylistItem fromMovie(Movie movie) {
            PlaylistItem item = new PlaylistItem();
            item.contentId = movie.getUrl();
            item.title = movie.getName();
            item.subtitle = movie.getCategory();
            item.imageUrl = movie.getPoster();
            item.streamUrl = movie.getUrl();
            item.type = "movie";
            item.addedAt = System.currentTimeMillis();
            return item;
        }
    }
}
