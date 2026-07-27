package com.nashat.tv;

import android.util.Log;

import androidx.annotation.NonNull;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LiveTvChannelValidator {

    private static final String TAG = "LiveTvValidator";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;

    public Channel validate(@NonNull Channel source) {
        Channel channel = copyOf(source);
        if (!channel.hasStream()) {
            channel.setAlive(false);
            channel.setPing(0);
            channel.setLoadSpeed(0);
            channel.setLastChecked(nowIso());
            return channel;
        }

        HttpURLConnection connection = null;
        long startedAt = System.currentTimeMillis();
        int responseCode = -1;

        try {
            URL url = new URL(channel.getUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "NashatTV/1.0");
            connection.setRequestProperty("Accept", "*/*");
            connection.connect();

            responseCode = connection.getResponseCode();
            int elapsed = (int) Math.max(1L, System.currentTimeMillis() - startedAt);
            channel.setPing(elapsed);
            channel.setLoadSpeed(elapsed);

            boolean success = responseCode >= 200 && responseCode < 400;
            channel.setAlive(success);
        } catch (Exception error) {
            Log.w(TAG, "Validation failed for " + channel.getName() + ": " + error.getMessage());
            channel.setAlive(false);
            channel.setPing((int) Math.max(1L, System.currentTimeMillis() - startedAt));
            channel.setLoadSpeed(CONNECT_TIMEOUT_MS + 1);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (responseCode < 0) {
            channel.setAlive(false);
        }
        channel.setLastChecked(nowIso());
        return channel;
    }

    private Channel copyOf(Channel source) {
        Channel channel = new Channel();
        channel.setId(source.getId());
        channel.setName(source.getName());
        channel.setLogoUrl(source.getLogo());
        channel.setCategory(source.getCategory());
        channel.setStreamUrl(source.getUrl());
        channel.setDescription(source.getDescription());
        channel.setCountry(source.getCountry());
        channel.setLanguage(source.getLanguage());
        channel.setPing(source.getPing());
        channel.setLoadSpeed(source.getLoadSpeed());
        channel.setAlive(source.isAlive());
        channel.setLastChecked(source.getLastChecked());
        channel.setSource(source.getSource());
        return channel;
    }

    private String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
    }
}
