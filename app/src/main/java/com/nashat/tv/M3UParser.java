package com.nashat.tv;

import android.os.AsyncTask;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for M3U/M3U8 playlist files.
 * Extracts channels with name, url, logo, and category from #EXTINF lines.
 */
public class M3UParser {
    private static final String TAG = "M3UParser";

    public interface M3UCallback {
        void onSuccess(List<Channel> channels);
        void onError(String error);
    }

    /**
     * Parse an M3U playlist from a URL asynchronously.
     */
    public static void parseFromUrl(String url, M3UCallback callback) {
        new AsyncTask<String, Void, List<Channel>>() {
            String errorMsg = null;

            @Override
            protected List<Channel> doInBackground(String... urls) {
                try {
                    URL m3uUrl = new URL(urls[0]);
                    HttpURLConnection conn = (HttpURLConnection) m3uUrl.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(20000); // 20 seconds
                    conn.setReadTimeout(30000); // 30 seconds

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));

                    List<Channel> channels = parseM3U(reader);
                    reader.close();
                    conn.disconnect();
                    return channels;
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing M3U", e);
                    errorMsg = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(List<Channel> channels) {
                if (channels != null) {
                    callback.onSuccess(channels);
                } else {
                    callback.onError(errorMsg != null ? errorMsg : "Unknown error");
                }
            }
        }.execute(url);
    }

    /**
     * Parse M3U content from a BufferedReader.
     */
    public static List<Channel> parseM3U(BufferedReader reader) throws IOException {
        List<Channel> channels = new ArrayList<>();
        String line;
        String currentName = null;
        String currentLogo = null;
        String currentCategory = null;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.startsWith("#EXTINF")) {
                // Parse #EXTINF:-1 tvg-name="..." tvg-logo="..." group-title="...",Channel Name
                currentName = extractDisplayName(line);
                currentLogo = extractAttribute(line, "tvg-logo");
                currentCategory = extractAttribute(line, "group-title");

                // Fallback: use tvg-name if display name is empty
                if (currentName == null || currentName.isEmpty()) {
                    currentName = extractAttribute(line, "tvg-name");
                }
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                // This is the URL line
                if (currentName != null && !currentName.isEmpty()) {
                    // Clean up specific formatting from index.m3u like "KURD ?"
                    if (currentName.startsWith("KURD ?")) {
                        currentName = currentName.substring(6).trim();
                    }

                    if (currentLogo == null || currentLogo.isEmpty()) {
                        currentLogo = com.nashat.tv.util.ChannelLogoMapper.getLogoForChannel(currentName);
                    }

                    Channel ch = new Channel(
                            currentName,
                            currentLogo != null ? currentLogo : "",
                            currentCategory != null ? currentCategory : "Imported",
                            line,
                            ""
                    );
                    channels.add(ch);
                }
                currentName = null;
                currentLogo = null;
                currentCategory = null;
            }
        }

        Log.d(TAG, "Parsed " + channels.size() + " channels from M3U");
        return channels;
    }

    /**
     * Extract the display name from #EXTINF line (text after last comma).
     */
    private static String extractDisplayName(String line) {
        int lastComma = line.lastIndexOf(',');
        if (lastComma >= 0 && lastComma < line.length() - 1) {
            return line.substring(lastComma + 1).trim();
        }
        return null;
    }

    /**
     * Extract an attribute value from #EXTINF line.
     * e.g., tvg-logo="http://example.com/logo.png"
     */
    private static String extractAttribute(String line, String attr) {
        String search = attr + "=\"";
        int start = line.indexOf(search);
        if (start < 0) return null;

        start += search.length();
        int end = line.indexOf("\"", start);
        if (end < 0) return null;

        String value = line.substring(start, end).trim();
        return value.isEmpty() ? null : value;
    }
}
