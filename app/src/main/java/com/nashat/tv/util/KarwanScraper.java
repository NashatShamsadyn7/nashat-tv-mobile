package com.nashat.tv.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class KarwanScraper {

    public interface ScrapeCallback {
        void onStreamFound(String streamUrl);
        void onError(String error);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean hasFinished = false;

    static {
        // Trust all certs (equivalent to SSL check verification bypass in python)
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            Log.e("KarwanScraper", "Failed to bypass SSL check", e);
        }
    }

    public void scrapeStreamUrl(Context context, String channelUrl, ScrapeCallback callback) {
        hasFinished = false;
        
        executor.submit(() -> {
            try {
                String streamUrl = scrapeHttpRecursive(channelUrl, 0, 5);
                if (streamUrl != null && !streamUrl.isEmpty()) {
                    postSuccess(callback, streamUrl);
                } else {
                    postError(callback, "Stream URL not found in pages");
                }
            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    private String scrapeHttpRecursive(String targetUrl, int hop, int maxHops) throws Exception {
        if (hop >= maxHops) {
            throw new Exception("Too many iframe hops");
        }

        Log.d("KarwanScraper", "HTTP Request [" + hop + "]: " + targetUrl);

        if (targetUrl.contains("/embed.html")) {
            String streamUrl = targetUrl.replace("/embed.html", "/index.m3u8");
            Log.d("KarwanScraper", "Direct convert in URL check: " + streamUrl);
            return streamUrl;
        }

        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");
        conn.setRequestProperty("Referer", "https://karwan.tv/");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setInstanceFollowRedirects(true);

        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new Exception("HTTP response code: " + status + " for URL: " + targetUrl);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine).append("\n");
        }
        in.close();
        conn.disconnect();

        String html = content.toString();

        // 1. Search for any iframes
        List<String> validIframes = new ArrayList<>();
        Pattern iframePattern = Pattern.compile("<iframe[^>]*src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = iframePattern.matcher(html);
        while (matcher.find()) {
            String iframeSrc = matcher.group(1);
            if (iframeSrc != null) {
                if (iframeSrc.contains("google") || iframeSrc.contains("facebook") || iframeSrc.contains("ads")) {
                    continue;
                }
                if (iframeSrc.contains("karwan") || iframeSrc.contains("embed") || iframeSrc.contains("live") || iframeSrc.startsWith("/") || iframeSrc.startsWith(".")) {
                    validIframes.add(iframeSrc);
                }
            }
        }

        if (!validIframes.isEmpty()) {
            String nextIframe = validIframes.get(0);
            if (nextIframe.startsWith("//")) {
                nextIframe = "https:" + nextIframe;
            } else if (!nextIframe.startsWith("http")) {
                String baseUrl = "https://karwan.tv";
                nextIframe = baseUrl + (nextIframe.startsWith("/") ? "" : "/") + nextIframe;
            }
            return scrapeHttpRecursive(nextIframe, hop + 1, maxHops);
        }

        // 2. Search for player configs (dash: "..." or hls: "...")
        Pattern dashPattern = Pattern.compile("dash\\s*:\\s*['\"](.*?)['\"]");
        Matcher dashMatcher = dashPattern.matcher(html);
        if (dashMatcher.find()) {
            String streamUrl = dashMatcher.group(1);
            Log.d("KarwanScraper", "Found Dash URL in config: " + streamUrl);
            return streamUrl;
        }

        Pattern hlsPattern = Pattern.compile("hls\\s*:\\s*['\"](.*?)['\"]");
        Matcher hlsMatcher = hlsPattern.matcher(html);
        if (hlsMatcher.find()) {
            String streamUrl = hlsMatcher.group(1);
            Log.d("KarwanScraper", "Found HLS URL in config: " + streamUrl);
            return streamUrl;
        }

        // 3. Last fallback check of redirect URL
        String finalUrl = conn.getURL().toString();
        if (finalUrl.contains("/embed.html")) {
            String streamUrl = finalUrl.replace("/embed.html", "/index.m3u8");
            Log.d("KarwanScraper", "Direct convert in redirect URL fallback check: " + streamUrl);
            return streamUrl;
        }

        return null;
    }

    private void postSuccess(ScrapeCallback callback, String streamUrl) {
        mainHandler.post(() -> {
            if (!hasFinished) {
                hasFinished = true;
                callback.onStreamFound(streamUrl);
            }
        });
    }

    private void postError(ScrapeCallback callback, String error) {
        mainHandler.post(() -> {
            if (!hasFinished) {
                hasFinished = true;
                callback.onError(error);
            }
        });
    }
}
