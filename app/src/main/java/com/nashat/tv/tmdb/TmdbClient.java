package com.nashat.tv.tmdb;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.nashat.tv.tmdb.TmdbModels.Details;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.tmdb.TmdbModels.Season;
import com.nashat.tv.tmdb.TmdbModels.Title;
import com.nashat.tv.tmdb.TmdbModels.TitlePage;
import com.nashat.tv.tmdb.TmdbModels.Videos;
import com.nashat.tv.util.AppConstants;
import com.nashat.tv.util.HttpClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TMDB endpoints used by the catalogue and details screens, mirroring the
 * desktop client's {@code services/tmdb.ts}.
 *
 * <p>Every method is asynchronous and calls back on the main thread, because
 * {@link HttpClient} guarantees that. Callers are Activities, so each one is
 * responsible for checking it is still alive before touching views.
 */
public final class TmdbClient {

    private static final String TAG = "TmdbClient";
    private static final Gson GSON = new Gson();

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    // ─── Catalogue rows ───────────────────────────────────────

    public static void trending(Context ctx, Kind kind, String language, Callback<List<Title>> cb) {
        page(ctx, url("/trending/" + kind.path + "/week", language), cb);
    }

    public static void popular(Context ctx, Kind kind, String language, Callback<List<Title>> cb) {
        page(ctx, url("/" + kind.path + "/popular", language), cb);
    }

    public static void topRated(Context ctx, Kind kind, String language, Callback<List<Title>> cb) {
        page(ctx, url("/" + kind.path + "/top_rated", language), cb);
    }

    public static void nowPlaying(Context ctx, String language, Callback<List<Title>> cb) {
        page(ctx, url("/movie/now_playing", language), cb);
    }

    public static void airingToday(Context ctx, String language, Callback<List<Title>> cb) {
        page(ctx, url("/tv/airing_today", language), cb);
    }

    public static void search(Context ctx, Kind kind, String query, String language,
                              Callback<List<Title>> cb) {
        String encoded = Uri.encode(query);
        page(ctx, url("/search/" + kind.path, language) + "&query=" + encoded
                + "&include_adult=false", cb);
    }

    // ─── Discover (filtering & sorting) ───────────────────────

    /** How a filtered list is ordered. */
    public enum Sort {
        /** TMDB's own popularity score. The sensible default. */
        POPULAR("popularity.desc"),
        /** Highest rated. See the vote-count floor in {@link #discover}. */
        RATING("vote_average.desc"),
        /** Newest first. Resolved per kind, since the date field differs. */
        NEWEST("release_date.desc"),
        /** Biggest box office. Films only; TMDB has no revenue for series. */
        REVENUE("revenue.desc"),
        /** A–Z. */
        TITLE("title.asc");

        final String value;

        Sort(String value) {
            this.value = value;
        }

        String forKind(Kind kind) {
            if (kind == Kind.MOVIE) return value;
            // TMDB names these fields differently on the TV endpoint, and an
            // unknown sort_by is rejected outright rather than ignored.
            switch (this) {
                case NEWEST: return "first_air_date.desc";
                case TITLE: return "name.asc";
                case REVENUE: return "popularity.desc"; // no revenue on TV
                default: return value;
            }
        }
    }

    /**
     * Minimum votes required before a title may appear in a rating-sorted list.
     *
     * <p>Without this, "top rated" is worthless: TMDB will happily return a
     * short film with a single 10/10 vote ahead of every film the user has
     * heard of. TMDB's own Top Rated list applies a similar floor, which is
     * why the curated row looks sane and a naive discover query does not.
     */
    private static final int RATING_VOTE_FLOOR = 300;

    /**
     * A filtered, sorted page.
     *
     * @param minRating 0 disables the filter
     * @param yearFrom  0 disables the lower bound
     * @param yearTo    0 disables the upper bound
     * @param genreId   0 disables the genre filter
     * @param page      1-based
     */
    public static void discover(Context ctx, Kind kind, Sort sort, float minRating,
                                int yearFrom, int yearTo, int genreId, int page,
                                String language, Callback<List<Title>> cb) {
        StringBuilder u = new StringBuilder(url("/discover/" + kind.path, language))
                .append("&sort_by=").append(sort.forKind(kind))
                .append("&page=").append(Math.max(1, page))
                .append("&include_adult=false");

        if (minRating > 0) {
            u.append("&vote_average.gte=").append(minRating);
        }
        // The floor is applied whenever rating is the sort key OR a rating
        // filter is set — both are cases where a one-vote title would surface.
        if (sort == Sort.RATING || minRating > 0) {
            u.append("&vote_count.gte=").append(RATING_VOTE_FLOOR);
        }

        String dateField = kind == Kind.MOVIE ? "primary_release_date" : "first_air_date";
        if (yearFrom > 0) {
            u.append('&').append(dateField).append(".gte=").append(yearFrom).append("-01-01");
        }
        if (yearTo > 0) {
            u.append('&').append(dateField).append(".lte=").append(yearTo).append("-12-31");
        }
        if (genreId > 0) {
            u.append("&with_genres=").append(genreId);
        }
        page(ctx, u.toString(), cb);
    }

    /** The genre list for one kind, used to build the filter chips. */
    public static void genres(Context ctx, Kind kind, String language,
                              Callback<List<TmdbModels.Genre>> cb) {
        json(ctx, url("/genre/" + kind.path + "/list", language), TmdbModels.GenreList.class,
                new Callback<TmdbModels.GenreList>() {
                    @Override
                    public void onSuccess(TmdbModels.GenreList data) {
                        cb.onSuccess(data.genres != null ? data.genres : Collections.emptyList());
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    /** A search page, for paginated result grids. */
    public static void searchPage(Context ctx, Kind kind, String query, int page,
                                  String language, Callback<List<Title>> cb) {
        page(ctx, url("/search/" + kind.path, language)
                + "&query=" + Uri.encode(query)
                + "&page=" + Math.max(1, page)
                + "&include_adult=false", cb);
    }

    // ─── Details ──────────────────────────────────────────────

    /**
     * Full details in a single request.
     *
     * <p>{@code append_to_response} folds credits, videos and recommendations
     * into the same response. Four separate calls would each pay their own
     * round-trip, and the screen could not render until the slowest returned —
     * so this is both fewer requests and a faster first paint.
     */
    public static void details(Context ctx, Kind kind, int tmdbId, String language,
                               Callback<Details> cb) {
        String u = url("/" + kind.path + "/" + tmdbId, language)
                + "&append_to_response=credits,videos,recommendations";
        json(ctx, u, Details.class, cb);
    }

    public static void season(Context ctx, int tvId, int seasonNumber, String language,
                              Callback<Season> cb) {
        json(ctx, url("/tv/" + tvId + "/season/" + seasonNumber, language), Season.class, cb);
    }

    public static void videos(Context ctx, Kind kind, int tmdbId, String language,
                              Callback<Videos> cb) {
        json(ctx, url("/" + kind.path + "/" + tmdbId + "/videos", language), Videos.class, cb);
    }

    // ─── Request plumbing ─────────────────────────────────────

    /**
     * Builds a request URL.
     *
     * <p>The v3 key stays on the query string even though {@link HttpClient}
     * also attaches the v4 bearer token: TMDB accepts either, and keeping both
     * means a missing token is not an outage.
     */
    private static String url(String path, @Nullable String language) {
        StringBuilder sb = new StringBuilder(AppConstants.TMDB_BASE_URL)
                .append(path)
                .append("?api_key=").append(AppConstants.TMDB_API_KEY);
        if (language != null && !language.isEmpty()) {
            sb.append("&language=").append(language);
        }
        return sb.toString();
    }

    private static void page(Context ctx, String url, Callback<List<Title>> cb) {
        json(ctx, url, TitlePage.class, new Callback<TitlePage>() {
            @Override
            public void onSuccess(TitlePage data) {
                cb.onSuccess(data.results != null ? data.results : Collections.emptyList());
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    private static <T> void json(Context ctx, String url, Class<T> type, Callback<T> cb) {
        HttpClient.getJson(ctx, url, new HttpClient.Callback2() {
            @Override
            public void onSuccess(String body) {
                try {
                    T parsed = GSON.fromJson(body, type);
                    if (parsed == null) {
                        cb.onError("Empty response");
                        return;
                    }
                    cb.onSuccess(parsed);
                } catch (JsonSyntaxException e) {
                    Log.w(TAG, "Malformed response from " + strip(url), e);
                    cb.onError(e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Request failed: " + strip(url) + " — " + message);
                cb.onError(message);
            }
        });
    }

    /** Drops the API key before a URL reaches a log. */
    private static String strip(String url) {
        int cut = url.indexOf("?api_key=");
        return cut > 0 ? url.substring(0, cut) : url;
    }

    /**
     * Removes titles with no poster.
     *
     * <p>TMDB carries a long tail of entries with no artwork at all. In a
     * poster row they render as grey rectangles that cannot be told apart, so
     * a row of twenty results can look broken while being perfectly correct.
     */
    public static List<Title> withArtwork(@Nullable List<Title> titles) {
        List<Title> out = new ArrayList<>();
        if (titles == null) return out;
        for (Title t : titles) {
            if (t != null && t.posterPath != null && !t.posterPath.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private TmdbClient() {}
}
