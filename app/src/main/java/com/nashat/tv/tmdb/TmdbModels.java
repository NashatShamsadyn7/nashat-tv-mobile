package com.nashat.tv.tmdb;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/**
 * TMDB response shapes, mirroring the desktop client's {@code shared/tmdb.ts}.
 *
 * <p>Films and series are deliberately modelled by one {@link Title} rather
 * than two classes. TMDB itself is inconsistent here — a film carries
 * {@code title}/{@code release_date} and a series carries {@code name}/
 * {@code first_air_date} — and every screen that shows both (search, "you may
 * also like", an actor's filmography) otherwise has to branch on the type
 * before it can read so much as a name. One class with {@link Title#displayTitle()}
 * puts that branch in a single place.
 */
public final class TmdbModels {

    /** Which TMDB endpoint family a title belongs to. Serialises as the path segment. */
    public enum Kind {
        MOVIE("movie"),
        TV("tv");

        public final String path;

        Kind(String path) {
            this.path = path;
        }

        public static Kind fromPath(@Nullable String value) {
            return "tv".equals(value) ? TV : MOVIE;
        }
    }

    /** A film or a series, as returned by a list, search or recommendation endpoint. */
    public static class Title implements Serializable {
        public int id;

        @SerializedName("title")
        public String title;
        @SerializedName("name")
        public String name;

        @SerializedName("release_date")
        public String releaseDate;
        @SerializedName("first_air_date")
        public String firstAirDate;

        @SerializedName("poster_path")
        public String posterPath;
        @SerializedName("backdrop_path")
        public String backdropPath;

        public String overview;

        @SerializedName("vote_average")
        public double voteAverage;

        /**
         * Present only on the mixed-media endpoints (trending/all, search/multi,
         * an actor's combined credits). On {@code /movie/*} and {@code /tv/*} it
         * is absent, which is why {@link #kind()} falls back to shape.
         */
        @SerializedName("media_type")
        public String mediaType;

        /**
         * A ready-made poster URL, used when a Title is built from a locally
         * saved record rather than from TMDB.
         *
         * <p>Favourites and the watchlist store the full image URL, not the
         * {@code /abc.jpg} path TMDB returns, so there is nothing to prefix.
         * Marked transient because it never appears in a TMDB payload.
         */
        public transient String posterUrlOverride;

        public String displayTitle() {
            if (title != null && !title.isEmpty()) return title;
            if (name != null && !name.isEmpty()) return name;
            return "";
        }

        public String releasedOn() {
            if (releaseDate != null && !releaseDate.isEmpty()) return releaseDate;
            return firstAirDate;
        }

        @Nullable
        public String year() {
            return Tmdb.year(releasedOn());
        }

        /**
         * Whether this is a film or a series.
         *
         * <p>{@code media_type} is trusted when TMDB sends it. When it does not,
         * the presence of {@code name}/{@code first_air_date} is the tell: only
         * series carry those fields.
         */
        public Kind kind() {
            if (mediaType != null) {
                if ("tv".equals(mediaType)) return Kind.TV;
                if ("movie".equals(mediaType)) return Kind.MOVIE;
            }
            boolean looksLikeSeries = (name != null && !name.isEmpty())
                    || (firstAirDate != null && !firstAirDate.isEmpty());
            return looksLikeSeries ? Kind.TV : Kind.MOVIE;
        }
    }

    /** A page of titles. */
    public static class TitlePage {
        public int page;
        public List<Title> results;
        @SerializedName("total_pages")
        public int totalPages;
        @SerializedName("total_results")
        public int totalResults;
    }

    public static class Genre {
        public int id;
        public String name;
    }

    /** Response shape of {@code /genre/{kind}/list}. */
    public static class GenreList {
        public List<Genre> genres;
    }

    public static class CastMember {
        public int id;
        public String name;
        public String character;
        @SerializedName("profile_path")
        public String profilePath;
    }

    public static class Credits {
        public List<CastMember> cast;
    }

    public static class Video {
        public String key;
        public String site;
        public String type;
        public String name;
        public boolean official;
    }

    public static class Videos {
        public List<Video> results;
    }

    /** A season stub as listed on a series' detail payload. */
    public static class SeasonStub {
        public int id;
        public String name;
        @SerializedName("season_number")
        public int seasonNumber;
        @SerializedName("episode_count")
        public int episodeCount;
        @SerializedName("poster_path")
        public String posterPath;
    }

    public static class Episode {
        public int id;
        public String name;
        public String overview;
        @SerializedName("episode_number")
        public int episodeNumber;
        @SerializedName("season_number")
        public int seasonNumber;
        @SerializedName("air_date")
        public String airDate;
        @SerializedName("still_path")
        public String stillPath;
        @SerializedName("vote_average")
        public double voteAverage;
    }

    public static class Season {
        public int id;
        public String name;
        public String overview;
        @SerializedName("season_number")
        public int seasonNumber;
        public List<Episode> episodes;
    }

    /**
     * Full details for one title.
     *
     * <p>Extends {@link Title} so the fields both share are declared once, and
     * so a details payload can be handed to anything that renders a card.
     */
    public static class Details extends Title {
        public List<Genre> genres;
        public String status;
        public String tagline;

        /** Films only. Minutes. */
        public Integer runtime;

        /** Series only. */
        @SerializedName("number_of_seasons")
        public int numberOfSeasons;
        @SerializedName("number_of_episodes")
        public int numberOfEpisodes;
        @SerializedName("episode_run_time")
        public List<Integer> episodeRunTime;
        public List<SeasonStub> seasons;

        /** Appended via {@code append_to_response}, so one request covers the screen. */
        public Credits credits;
        public Videos videos;
        public TitlePage recommendations;
        public TitlePage similar;
    }

    private TmdbModels() {}
}
