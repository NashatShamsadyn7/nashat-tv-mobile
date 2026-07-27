package com.nashat.tv.tmdb;

import androidx.annotation.Nullable;

/**
 * TMDB image URL construction, mirroring the desktop client's {@code shared/tmdb.ts}.
 *
 * <p>Sizes are chosen per role rather than fetched at "original" everywhere.
 * A poster grid on a phone showing thirty {@code original} images pulls tens of
 * megabytes for pictures rendered 120dp wide; {@code w342} is visually
 * identical at that size and roughly a twentieth of the bytes.
 */
public final class Tmdb {

    public static final String IMAGE_BASE = "https://image.tmdb.org/t/p/";

    /** Poster inside a grid or a horizontal row. */
    public static final String POSTER = "w342";
    /** Poster on the details hero, where it is rendered large. */
    public static final String POSTER_LARGE = "w500";
    /** Backdrop behind a row card. */
    public static final String BACKDROP = "w780";
    /** Backdrop filling the details hero. */
    public static final String BACKDROP_LARGE = "w1280";
    /** Round cast portrait. */
    public static final String PROFILE = "w185";
    /** Episode thumbnail. */
    public static final String STILL = "w300";

    @Nullable
    public static String image(@Nullable String path, String size) {
        if (path == null || path.isEmpty()) return null;
        return IMAGE_BASE + size + path;
    }

    @Nullable
    public static String poster(@Nullable String path) {
        return image(path, POSTER);
    }

    @Nullable
    public static String posterLarge(@Nullable String path) {
        return image(path, POSTER_LARGE);
    }

    @Nullable
    public static String backdrop(@Nullable String path) {
        return image(path, BACKDROP);
    }

    @Nullable
    public static String backdropLarge(@Nullable String path) {
        return image(path, BACKDROP_LARGE);
    }

    @Nullable
    public static String profile(@Nullable String path) {
        return image(path, PROFILE);
    }

    @Nullable
    public static String still(@Nullable String path) {
        return image(path, STILL);
    }

    /** The four-digit year of a {@code YYYY-MM-DD} TMDB date, or null. */
    @Nullable
    public static String year(@Nullable String date) {
        if (date == null || date.length() < 4) return null;
        return date.substring(0, 4);
    }

    private Tmdb() {}
}
