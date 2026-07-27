package com.nashat.tv;

import com.nashat.tv.tmdb.TmdbClient;
import com.nashat.tv.tmdb.TmdbModels.Kind;
import com.nashat.tv.ui.BottomNav;

import java.util.Arrays;
import java.util.List;

/**
 * The series catalogue. Same screen as {@link MoviesActivity} with the TV
 * endpoints, mirroring the desktop client's {@code pages/Series.tsx}.
 */
public class SeriesActivity extends CatalogActivity {

    @Override
    protected Kind kind() {
        return Kind.TV;
    }

    @Override
    protected int screenTitleRes() {
        return R.string.nav_series;
    }

    @Override
    protected int searchHintRes() {
        return R.string.series_search_hint;
    }

    @Override
    protected BottomNav.Tab navTab() {
        return BottomNav.Tab.SERIES;
    }

    @Override
    protected List<Row> rows() {
        return Arrays.asList(
                new Row(R.string.row_trending,
                        cb -> TmdbClient.trending(this, Kind.TV, language(), cb)),
                new Row(R.string.row_airing_today,
                        cb -> TmdbClient.airingToday(this, language(), cb)),
                new Row(R.string.row_popular,
                        cb -> TmdbClient.popular(this, Kind.TV, language(), cb)),
                new Row(R.string.row_top_rated,
                        cb -> TmdbClient.topRated(this, Kind.TV, language(), cb))
        );
    }
}
