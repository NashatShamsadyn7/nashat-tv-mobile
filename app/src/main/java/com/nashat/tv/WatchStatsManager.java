package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manages watch statistics — tracks total watch time, sessions, categories, etc.
 */
public class WatchStatsManager {
    private static final String PREFS_NAME = "watch_stats";
    private static final String KEY_TOTAL_MINUTES = "total_minutes";
    private static final String KEY_TOTAL_SESSIONS = "total_sessions";
    private static final String KEY_SESSIONS_LOG = "sessions_log";
    private static final String KEY_CATEGORY_MINUTES = "category_minutes";
    private static final int MAX_SESSION_LOG = 200;

    private final SharedPreferences prefs;

    public WatchStatsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Record a watch session.
     * @param channelName Name of the channel/movie/series
     * @param category Category (News, Sports, etc.)
     * @param durationMinutes How many minutes the user watched
     */
    public void recordSession(String channelName, String category, int durationMinutes) {
        if (durationMinutes <= 0) return;

        // Update totals
        long totalMin = prefs.getLong(KEY_TOTAL_MINUTES, 0) + durationMinutes;
        long totalSessions = prefs.getLong(KEY_TOTAL_SESSIONS, 0) + 1;

        // Update category breakdown
        Map<String, Long> catMap = getCategoryMinutes();
        long existing = catMap.containsKey(category) ? catMap.get(category) : 0;
        catMap.put(category, existing + durationMinutes);

        // Add session log entry
        List<SessionEntry> log = getSessionLog();
        SessionEntry entry = new SessionEntry();
        entry.channelName = channelName != null ? channelName : "";
        entry.category = category != null ? category : "General";
        entry.minutes = durationMinutes;
        entry.timestamp = System.currentTimeMillis();
        log.add(0, entry);

        // Trim log
        while (log.size() > MAX_SESSION_LOG) {
            log.remove(log.size() - 1);
        }

        // Save everything
        prefs.edit()
                .putLong(KEY_TOTAL_MINUTES, totalMin)
                .putLong(KEY_TOTAL_SESSIONS, totalSessions)
                .putString(KEY_SESSIONS_LOG, serializeSessionLog(log))
                .putString(KEY_CATEGORY_MINUTES, serializeCatMap(catMap))
                .apply();
    }

    public long getTotalMinutes() {
        return prefs.getLong(KEY_TOTAL_MINUTES, 0);
    }

    public long getTotalSessions() {
        return prefs.getLong(KEY_TOTAL_SESSIONS, 0);
    }

    public String getTotalTimeFormatted() {
        long mins = getTotalMinutes();
        if (mins < 60) return mins + "m";
        long hours = mins / 60;
        long remMins = mins % 60;
        if (hours < 24) return hours + "h " + remMins + "m";
        long days = hours / 24;
        long remHours = hours % 24;
        return days + "d " + remHours + "h";
    }

    /**
     * Get watch minutes for the last 7 days (index 0 = today, 6 = 6 days ago).
     */
    public int[] getWeeklyMinutes() {
        int[] weekly = new int[7];
        List<SessionEntry> log = getSessionLog();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long midnight = cal.getTimeInMillis();
        long day = 1000L * 60 * 60 * 24;
        for (SessionEntry s : log) {
            // Anything after today's midnight is today. Measuring every session
            // from midnight backwards made today's sessions negative, so the
            // current day always read zero.
            long daysDiff = s.timestamp >= midnight ? 0 : (midnight - s.timestamp) / day + 1;
            if (daysDiff >= 0 && daysDiff < 7) {
                weekly[(int) daysDiff] += s.minutes;
            }
        }
        return weekly;
    }

    /**
     * Get the top category name by total minutes watched.
     */
    public String getTopCategory() {
        Map<String, Long> catMap = getCategoryMinutes();
        String top = "—";
        long max = 0;
        for (Map.Entry<String, Long> e : catMap.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                top = e.getKey();
            }
        }
        return top;
    }

    public Map<String, Long> getCategoryMinutes() {
        Map<String, Long> map = new HashMap<>();
        String json = prefs.getString(KEY_CATEGORY_MINUTES, "{}");
        try {
            JSONObject obj = new JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, obj.optLong(k, 0));
            }
        } catch (Exception ignored) {}
        return map;
    }

    public List<SessionEntry> getSessionLog() {
        List<SessionEntry> list = new ArrayList<>();
        String json = prefs.getString(KEY_SESSIONS_LOG, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                SessionEntry e = new SessionEntry();
                e.channelName = o.optString("name", "");
                e.category = o.optString("category", "");
                e.minutes = o.optInt("minutes", 0);
                e.timestamp = o.optLong("timestamp", 0);
                list.add(e);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void resetAll() {
        prefs.edit().clear().apply();
    }

    // ─── Serialization helpers ─────────────────────

    private String serializeSessionLog(List<SessionEntry> log) {
        try {
            JSONArray arr = new JSONArray();
            for (SessionEntry e : log) {
                JSONObject o = new JSONObject();
                o.put("name", e.channelName);
                o.put("category", e.category);
                o.put("minutes", e.minutes);
                o.put("timestamp", e.timestamp);
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String serializeCatMap(Map<String, Long> map) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, Long> e : map.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            return obj.toString();
        } catch (Exception ex) {
            return "{}";
        }
    }

    // ─── Data class ───────────────────────────────

    public static class SessionEntry {
        public String channelName;
        public String category;
        public int minutes;
        public long timestamp;

        public String getFormattedDate() {
            return new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    .format(new Date(timestamp));
        }
    }
}
