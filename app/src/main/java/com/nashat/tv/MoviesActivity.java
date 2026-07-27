package com.nashat.tv;

import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.ui.BottomNav;

import java.util.Arrays;
import java.util.List;

/**
 * The films catalogue. Row selection mirrors the desktop client's
 * {@code pages/Movies.tsx}, with "In cinemas" added — on a phone that is the
 * row people open the app for, and it costs one request.
 */
public class MoviesActivity extends CatalogActivity {

    @Override
    protected Kind kind() {
        return Kind.MOVIE;
    }

    @Override
    protected int screenTitleRes() {
        return R.string.nav_movies;
    }

    @Override
    protected int searchHintRes() {
        return R.string.movies_search_hint;
    }

    @Override
    protected BottomNav.Tab navTab() {
        return BottomNav.Tab.MOVIES;
    }

    @Override
    protected List<Row> rows() {
        return Arrays.asList(
                new Row(R.string.row_trending,
                        cb -> TmdbClient.trending(this, Kind.MOVIE, language(), cb)),
                new Row(R.string.row_in_cinemas,
                        cb -> TmdbClient.nowPlaying(this, language(), cb)),
                new Row(R.string.row_popular,
                        cb -> TmdbClient.popular(this, Kind.MOVIE, language(), cb)),
                new Row(R.string.row_top_rated,
                        cb -> TmdbClient.topRated(this, Kind.MOVIE, language(), cb))
        );
    }
}
