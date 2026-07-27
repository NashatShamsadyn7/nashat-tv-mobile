package com.nashat.tv.util;

import com.nashat.tv.BuildConfig;

public final class AppConstants {
    public static final String TMDB_API_KEY = BuildConfig.TMDB_API_KEY;
    /** TMDB v4 read token — the same credential the desktop client uses. */
    public static final String TMDB_V4_TOKEN = BuildConfig.TMDB_V4_TOKEN;
    public static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";
    public static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
    public static final String TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780";

    public static final String DEFAULT_LANGUAGE = "ar";

    private AppConstants() {}
}
