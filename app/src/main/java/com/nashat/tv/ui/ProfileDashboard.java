package com.nashat.tv.ui;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nashat.tv.ContinueWatchingManager;
import com.nashat.tv.FavoritesMoviesManager;
import com.nashat.tv.R;
import com.nashat.tv.WatchStatsManager;
import com.nashat.tv.WatchlistManager;
import com.nashat.tv.ui.chart.SegmentBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fills the dashboard at the top of the profile tab: the week card, watch
 * time split by category, and library counts.
 */
public final class ProfileDashboard {

    public static void bind(Activity activity) {
        View week = activity.findViewById(R.id.profileWeek);
        if (week == null) return;
        WeekCard.bind(week);
        bindSplit(activity);

        setCount(activity, R.id.libFavorites, new FavoritesMoviesManager(activity).getMovies().size());
        setCount(activity, R.id.libWatchlist, new WatchlistManager(activity).getItems().size());
        setCount(activity, R.id.libContinue, new ContinueWatchingManager(activity).getItems().size());
    }

    private static void bindSplit(Activity activity) {
        Map<String, Long> minutes = new WatchStatsManager(activity).getCategoryMinutes();
        View card = activity.findViewById(R.id.splitCard);
        List<SegmentBar.Segment> segments = new ArrayList<>();
        for (Map.Entry<String, Long> e : minutes.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            segments.add(new SegmentBar.Segment(e.getKey(), e.getValue(),
                    CategoryColors.forCategory(activity, e.getKey())));
        }
        if (segments.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }
        Collections.sort(segments, (a, b) -> Float.compare(b.value, a.value));
        card.setVisibility(View.VISIBLE);
        ((SegmentBar) activity.findViewById(R.id.splitBar)).setSegments(segments);
        LinearLayout legend = activity.findViewById(R.id.splitLegend);
        SegmentBar.fillLegend(legend, segments, value -> WeekCard.formatLong(activity, Math.round(value)));
    }

    private static void setCount(Activity activity, int id, int count) {
        ((TextView) activity.findViewById(id)).setText(String.format(Locale.getDefault(), "%d", count));
    }

    private ProfileDashboard() {}
}
