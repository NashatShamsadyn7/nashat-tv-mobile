package com.nashat.tv;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the Live TV line-up.
 *
 * <p>The list is assembled from three sources, best first:
 * <ol>
 *   <li>{@code assets/channels.json} — bundled with the APK, every stream in it
 *       was probed before release, so the grid is populated instantly and works
 *       with no network round-trip and no Firebase read at all.</li>
 *   <li>{@code /live_tv_channels} in Realtime Database — the maintained list,
 *       so channels can be added or repointed without shipping an update.</li>
 *   <li>{@code /channels} — the legacy node.</li>
 * </ol>
 *
 * <p>Remote entries are merged on top of the bundled ones, and anything that is
 * not a direct, playable stream is dropped. That last filter matters: the
 * legacy {@code /channels} node holds ~350 entries whose {@code streamUrl} is a
 * <em>karwan.tv web page</em>, not a stream. Those used to be resolved by
 * scraping the page for its {@code .m3u8}, but karwan.tv now sits behind a
 * Cloudflare challenge that returns 403 to every non-browser request, so each
 * of those channels failed at play time. They are filtered out here rather than
 * shown as entries that cannot play.
 */
public class LiveTvChannelRepository {

    private static final String TAG = "LiveTvRepository";
    private static final String DEFAULT_RTDB_URL = "https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app";
    /** Maintained list — direct stream URLs. */
    public static final String PRIMARY_PATH = "live_tv_channels";
    /** Legacy node, kept so any still-playable entry in it is not lost. */
    public static final String LEGACY_PATH = "channels";
    private static final String BUNDLED_ASSET = "channels.json";

    public interface LoadCallback {
        void onLoaded(List<Channel> channels, String sourcePath);
        void onError(String message);
    }

    private final Context context;

    public LiveTvChannelRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public void loadChannels(@NonNull LoadCallback callback) {
        load(callback, true);
    }

    public void loadChannelsOnce(@NonNull LoadCallback callback) {
        load(callback, false);
    }

    private void load(@NonNull LoadCallback callback, boolean continuous) {
        List<Channel> bundled = loadBundled();

        DatabaseReference primary = buildReference(PRIMARY_PATH);
        if (primary == null) {
            // No database at all — the bundled list is still a complete app.
            if (bundled.isEmpty()) {
                callback.onError("Database unavailable");
            } else {
                callback.onLoaded(bundled, BUNDLED_ASSET);
            }
            return;
        }

        loadFromReference(primary, PRIMARY_PATH, bundled, callback, continuous);
    }

    public void updateChannel(@NonNull String channelId, @NonNull Channel channel) {
        DatabaseReference reference = buildReference(PRIMARY_PATH);
        if (reference == null || channelId.trim().isEmpty()) {
            return;
        }
        reference.child(channelId).setValue(channel);
    }

    /** Reads the verified line-up shipped inside the APK. */
    private List<Channel> loadBundled() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(BUNDLED_ASSET), StandardCharsets.UTF_8))) {
            Type type = new TypeToken<List<Channel>>() {}.getType();
            List<Channel> parsed = new Gson().fromJson(reader, type);
            if (parsed == null) {
                return new ArrayList<>();
            }
            List<Channel> playable = new ArrayList<>(parsed.size());
            for (Channel channel : parsed) {
                if (channel != null && isPlayable(channel)) {
                    playable.add(channel);
                }
            }
            return playable;
        } catch (Exception e) {
            Log.e(TAG, "Bundled channel list unavailable", e);
            return new ArrayList<>();
        }
    }

    /**
     * True when the channel points at something a media player can open.
     * Anything else — most importantly a karwan.tv page URL — cannot be played
     * and is not worth showing in the grid.
     */
    private static boolean isPlayable(@NonNull Channel channel) {
        String url = channel.getUrl();
        if (url == null) {
            return false;
        }
        url = url.trim().toLowerCase(Locale.US);
        if (!url.startsWith("http")) {
            return false;
        }
        if (url.contains("karwan.tv")) {
            return false;
        }
        return url.contains(".m3u8") || url.contains(".mpd") || url.contains(".mp4")
                || url.contains(".ts") || url.contains("/playlist") || url.contains("/manifest")
                || url.contains("/hls") || url.contains("/live") || url.contains("/index.m3u");
    }

    /**
     * True when a remote channel belongs in this app's Kurdish-only line-up.
     *
     * <p>The bundled list is Kurdish by construction. The database nodes are
     * not — {@code /live_tv_channels} also carries beIN Sports and assorted
     * international channels — so anything merged in from there has to declare
     * itself Kurdish, either by category or by language tag.
     */
    private static boolean isKurdish(@NonNull Channel channel) {
        String category = channel.getCategory().toLowerCase(Locale.US);
        if (category.startsWith("kurdish")) {
            return true;
        }
        String language = channel.getLanguage().toLowerCase(Locale.US);
        return language.equals("ku") || language.equals("kur")
                || language.equals("ckb") || language.equals("kmr");
    }

    private static String mergeKey(@NonNull Channel channel) {
        String name = channel.getName().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
        return name.isEmpty() ? channel.getUrl().toLowerCase(Locale.US) : name;
    }

    @Nullable
    private DatabaseReference buildReference(String path) {
        try {
            String rtdbUrl = context.getString(R.string.firebase_rtdb_url);
            if (rtdbUrl != null && rtdbUrl.startsWith("http")) {
                return FirebaseDatabase.getInstance(rtdbUrl).getReference(path);
            }
        } catch (Throwable ignore) {
        }

        try {
            return FirebaseDatabase.getInstance(DEFAULT_RTDB_URL).getReference(path);
        } catch (Throwable t) {
            Log.e(TAG, "Explicit DB init failed", t);
            try {
                return FirebaseDatabase.getInstance().getReference(path);
            } catch (Throwable inner) {
                Log.e(TAG, "Default DB init failed", inner);
                return null;
            }
        }
    }

    private void loadFromReference(@NonNull DatabaseReference reference,
                                   @NonNull String path,
                                   @NonNull List<Channel> bundled,
                                   @NonNull LoadCallback callback,
                                   boolean continuous) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Channel> remote = parse(snapshot);

                if (PRIMARY_PATH.equals(path)) {
                    // Also sweep the legacy node for anything still playable,
                    // then deliver whatever the combination produced.
                    DatabaseReference legacy = buildReference(LEGACY_PATH);
                    if (legacy != null) {
                        List<Channel> merged = merge(bundled, remote);
                        loadFromReference(legacy, LEGACY_PATH, merged, callback, continuous);
                        return;
                    }
                }

                deliver(merge(bundled, remote), path, callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // A denied or failed read is not fatal — the bundled list plus
                // whatever already merged is still a working line-up.
                Log.w(TAG, "Read of /" + path + " failed: " + error.getMessage());
                if (PRIMARY_PATH.equals(path)) {
                    DatabaseReference legacy = buildReference(LEGACY_PATH);
                    if (legacy != null) {
                        loadFromReference(legacy, LEGACY_PATH, bundled, callback, continuous);
                        return;
                    }
                }
                deliver(bundled, path, callback);
            }
        };

        if (continuous) {
            reference.addValueEventListener(listener);
        } else {
            reference.addListenerForSingleValueEvent(listener);
        }
    }

    private void deliver(@NonNull List<Channel> channels,
                         @NonNull String path,
                         @NonNull LoadCallback callback) {
        if (channels.isEmpty()) {
            callback.onError(context.getString(R.string.error_loading));
        } else {
            callback.onLoaded(channels, path);
        }
    }

    private List<Channel> parse(@NonNull DataSnapshot snapshot) {
        List<Channel> channels = new ArrayList<>();
        if (!snapshot.exists()) {
            return channels;
        }
        for (DataSnapshot child : snapshot.getChildren()) {
            Channel channel;
            try {
                channel = child.getValue(Channel.class);
            } catch (Exception e) {
                continue;
            }
            if (channel == null) {
                continue;
            }
            if (channel.getId().isEmpty()) {
                channel.setId(child.getKey());
            }
            try {
                String key = child.getKey();
                if (key != null) {
                    channel.setDbIndex(Integer.parseInt(key));
                }
            } catch (NumberFormatException e) {
                channel.setDbIndex(-1);
            }
            if (!channel.getName().trim().isEmpty() && isPlayable(channel) && isKurdish(channel)) {
                channels.add(channel);
            }
        }
        return channels;
    }

    /** Base list wins on conflict; remote-only channels are appended. */
    private List<Channel> merge(@NonNull List<Channel> base, @NonNull List<Channel> extra) {
        Map<String, Channel> byKey = new LinkedHashMap<>();
        for (Channel channel : base) {
            byKey.put(mergeKey(channel), channel);
        }
        for (Channel channel : extra) {
            // Not Map.putIfAbsent — that is API 24 and this app ships to API 23.
            String key = mergeKey(channel);
            if (!byKey.containsKey(key)) {
                byKey.put(key, channel);
            }
        }
        return new ArrayList<>(byKey.values());
    }
}
