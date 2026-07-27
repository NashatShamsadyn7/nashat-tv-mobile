package com.nashat.tv.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Single shared OkHttp client with:
 *   - 50 MB disk cache (so re-visiting screens is instant)
 *   - Connection pooling (5 idle conns kept warm)
 *   - 8s connect / 12s read timeouts (fail fast on weak net, retry)
 *   - Automatic retry on network failure
 *   - Force-cache fallback when offline
 */
public final class HttpClient {
    private static final String TAG = "HttpClient";
    private static OkHttpClient INSTANCE;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback2 {
        void onSuccess(String body);
        void onError(String message);
    }

    public static synchronized OkHttpClient get(Context ctx) {
        if (INSTANCE == null) {
            Cache cache = new Cache(ctx.getApplicationContext().getCacheDir(), 50L * 1024 * 1024);
            INSTANCE = new OkHttpClient.Builder()
                    .cache(cache)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return INSTANCE;
    }

    /** Async GET with 5-minute cache and main-thread callback. */
    public static void getJson(Context ctx, String url, Callback2 cb) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .cacheControl(new CacheControl.Builder()
                        .maxAge(5, TimeUnit.MINUTES)
                        .maxStale(7, TimeUnit.DAYS)
                        .build());

        // TMDB calls carry the v4 read token, matching how the desktop client
        // authenticates. The v3 key already on the query string keeps working
        // on its own, so an empty token is not an error.
        if (url.contains("api.themoviedb.org") && !AppConstants.TMDB_V4_TOKEN.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + AppConstants.TMDB_V4_TOKEN);
            builder.addHeader("Accept", "application/json");
        }

        Request req = builder.build();
        get(ctx).newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                MAIN.post(() -> cb.onError(e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        MAIN.post(() -> cb.onError("HTTP " + r.code()));
                        return;
                    }
                    String body = r.body().string();
                    MAIN.post(() -> cb.onSuccess(body));
                } catch (Exception e) {
                    MAIN.post(() -> cb.onError(e.getMessage()));
                }
            }
        });
    }

    private HttpClient() {}
}
