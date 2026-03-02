package com.nashat.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads channels from a remote JSON (GitHub raw or any free host) and caches offline in SharedPreferences.
 */
public class ChannelRepository {

    private static final String PREFS = "channels_prefs";
    private static final String KEY_CACHE_JSON = "cache_json";
    private static final String KEY_LAST_UPDATED = "last_updated";

    private final Context appContext;
    private final Gson gson = new Gson();
    private final SharedPreferences prefs;

    public ChannelRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static class Result {
        public final List<Channel> channels;
        public final boolean fromNetwork;
        public final long lastUpdatedEpochMs;

        public Result(List<Channel> channels, boolean fromNetwork, long lastUpdatedEpochMs) {
            this.channels = channels;
            this.fromNetwork = fromNetwork;
            this.lastUpdatedEpochMs = lastUpdatedEpochMs;
        }
    }

    public Result loadChannels(String jsonUrl) {
        // Try network when URL is provided and network is available
        List<Channel> channels = null;
        boolean fromNetwork = false;
        long now = System.currentTimeMillis();

        if (!TextUtils.isEmpty(jsonUrl) && hasNetwork()) {
            try {
                String json = fetch(jsonUrl);
                channels = parse(json);
                if (channels != null && !channels.isEmpty()) {
                    cacheJson(json, now);
                    fromNetwork = true;
                }
            } catch (IOException ignored) {
                // will fallback to cache
            }
        }

        if (channels == null) {
            String cached = prefs.getString(KEY_CACHE_JSON, null);
            if (!TextUtils.isEmpty(cached)) {
                channels = parse(cached);
                long ts = prefs.getLong(KEY_LAST_UPDATED, 0L);
                return new Result(channels != null ? channels : Collections.emptyList(), false, ts);
            }
        }

        // If still no channels, load from bundled assets as a last resort.
        if (channels == null || channels.isEmpty()) {
            try {
                String json = readFromAssets("channels.json");
                channels = parse(json);
            } catch (IOException ignored) {
                // Should not happen if asset exists, but handle it.
                channels = new ArrayList<>();
            }
        }

        return new Result(channels, fromNetwork, fromNetwork ? now : 0L);
    }

    private void cacheJson(String json, long ts) {
        prefs.edit().putString(KEY_CACHE_JSON, json).putLong(KEY_LAST_UPDATED, ts).apply();
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc == null) return false;
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private String fetch(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");
            conn.connect();
            int code = conn.getResponseCode();
            is = (code >= 200 && code < 300) ? new BufferedInputStream(conn.getInputStream())
                    : new BufferedInputStream(conn.getErrorStream());

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " " + conn.getResponseMessage());
            }
            return sb.toString();
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private List<Channel> parse(String json) {
        try {
            Type type = new TypeToken<List<Channel>>(){}.getType();
            List<Channel> list = gson.fromJson(json, type);
            if (list == null) return Collections.emptyList();
            // sanitize nulls
            List<Channel> out = new ArrayList<>();
            for (Channel c : list) {
                if (c != null && !TextUtils.isEmpty(c.getName())) {
                    out.add(c);
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static String formatLastUpdated(Context ctx, long epochMs) {
        if (epochMs <= 0L) return "";
        java.text.DateFormat df = android.text.format.DateFormat.getMediumDateFormat(ctx);
        java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(ctx);
        return df.format(new java.util.Date(epochMs)) + " " + tf.format(new java.util.Date(epochMs));
    }

    private String readFromAssets(String filename) throws IOException {
        InputStream is = appContext.getAssets().open(filename);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}