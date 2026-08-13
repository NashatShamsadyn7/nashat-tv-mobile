package com.nashat.tv.catalog;

import android.content.Context;
import android.content.SharedPreferences;

import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Kind;

import java.util.Calendar;

/**
 * The state of the catalogue's filter sheet: sort order, minimum rating, year
 * range and genre.
 *
 * <p>Persisted per kind. Films and series are browsed with different
 * intentions — someone filtering films to 2024 and 8+ does not want that
 * carried into their series browsing — so the two keep separate settings.
 */
public final class CatalogFilter {

    private static final String FILE = "catalog_filter";

    /**
     * Oldest year offered by the year picker.
     *
     * <p>Cinema before this exists, but TMDB's coverage of it is thin enough
     * that the earlier decades return almost nothing and make the filter feel
     * broken rather than precise.
     */
    public static final int MIN_YEAR = 1950;

    public TmdbClient.Sort sort = TmdbClient.Sort.POPULAR;
    /** 0 means no rating filter. */
    public float minRating = 0f;
    /** 0 means unbounded. */
    public int yearFrom = 0;
    public int yearTo = 0;
    /** 0 means all genres. */
    public int genreId = 0;
    public String genreName = "";

    /** The newest year worth offering — next year, since TMDB lists upcoming titles. */
    public static int maxYear() {
        return Calendar.getInstance().get(Calendar.YEAR) + 1;
    }

    /**
     * True when nothing is filtered and the order is the default.
     *
     * <p>Drives whether the catalogue shows its curated rows or a filtered
     * grid, and whether the filter button is badged.
     */
    public boolean isDefault() {
        return sort == TmdbClient.Sort.POPULAR
                && minRating == 0f
                && yearFrom == 0
                && yearTo == 0
                && genreId == 0;
    }

    /** How many criteria are active, for the count on the filter button. */
    public int activeCount() {
        int n = 0;
        if (sort != TmdbClient.Sort.POPULAR) n++;
        if (minRating > 0f) n++;
        if (yearFrom > 0 || yearTo > 0) n++;
        if (genreId > 0) n++;
        return n;
    }

    public void reset() {
        sort = TmdbClient.Sort.POPULAR;
        minRating = 0f;
        yearFrom = 0;
        yearTo = 0;
        genreId = 0;
        genreName = "";
    }

    public CatalogFilter copy() {
        CatalogFilter c = new CatalogFilter();
        c.sort = sort;
        c.minRating = minRating;
        c.yearFrom = yearFrom;
        c.yearTo = yearTo;
        c.genreId = genreId;
        c.genreName = genreName;
        return c;
    }

    // ─── Persistence ──────────────────────────────────────────

    public static CatalogFilter load(Context ctx, Kind kind) {
        SharedPreferences p = prefs(ctx);
        String k = kind.path;
        CatalogFilter f = new CatalogFilter();
        try {
            f.sort = TmdbClient.Sort.valueOf(
                    p.getString(k + ".sort", TmdbClient.Sort.POPULAR.name()));
        } catch (IllegalArgumentException e) {
            // A stored value from an older build that no longer names a sort.
            f.sort = TmdbClient.Sort.POPULAR;
        }
        f.minRating = p.getFloat(k + ".minRating", 0f);
        f.yearFrom = p.getInt(k + ".yearFrom", 0);
        f.yearTo = p.getInt(k + ".yearTo", 0);
        f.genreId = p.getInt(k + ".genreId", 0);
        f.genreName = p.getString(k + ".genreName", "");
        return f;
    }

    public void save(Context ctx, Kind kind) {
        String k = kind.path;
        prefs(ctx).edit()
                .putString(k + ".sort", sort.name())
                .putFloat(k + ".minRating", minRating)
                .putInt(k + ".yearFrom", yearFrom)
                .putInt(k + ".yearTo", yearTo)
                .putInt(k + ".genreId", genreId)
                .putString(k + ".genreName", genreName)
                .apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
