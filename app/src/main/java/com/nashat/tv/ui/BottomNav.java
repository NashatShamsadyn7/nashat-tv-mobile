package com.nashat.tv.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.nashat.tv.LiveTvActivity;
import com.nashat.tv.MoviesActivity;
import com.nashat.tv.R;
import com.nashat.tv.SeriesActivity;
import com.nashat.tv.SettingsActivity;

/**
 * Wires up {@code view_bottom_nav.xml} and marks the current tab.
 *
 * <p>Exists so the active-state logic lives in one place. Each screen used to
 * hard-code its own colours into its own copy of the bar, and the copies had
 * drifted: two of them highlighted the active tab, one highlighted nothing,
 * and the labels were three different sizes.
 */
public final class BottomNav {

    public enum Tab {
        LIVE, MOVIES, SERIES, SETTINGS
    }

    /**
     * Binds the bar inside {@code activity} and marks {@code active}.
     *
     * <p>Tapping the active tab does nothing — {@code navigateToTab} already
     * short-circuits a self-navigation, and reloading the screen the user is
     * looking at would only lose their scroll position.
     */
    public static void bind(Activity activity, Tab active) {
        apply(activity, R.id.navLiveIcon, R.id.navLiveLabel, active == Tab.LIVE);
        apply(activity, R.id.navMoviesIcon, R.id.navMoviesLabel, active == Tab.MOVIES);
        apply(activity, R.id.navSeriesIcon, R.id.navSeriesLabel, active == Tab.SERIES);
        apply(activity, R.id.navSettingsIcon, R.id.navSettingsLabel, active == Tab.SETTINGS);

        route(activity, R.id.navLive, LiveTvActivity.class, active == Tab.LIVE);
        route(activity, R.id.navMovies, MoviesActivity.class, active == Tab.MOVIES);
        route(activity, R.id.navSeries, SeriesActivity.class, active == Tab.SERIES);
        route(activity, R.id.navSettings, SettingsActivity.class, active == Tab.SETTINGS);
    }

    private static void apply(Activity activity, int iconId, int labelId, boolean isActive) {
        ImageView icon = activity.findViewById(iconId);
        TextView label = activity.findViewById(labelId);
        int colour = ContextCompat.getColor(activity,
                isActive ? R.color.accent_text : R.color.content_secondary);
        if (icon != null) icon.setColorFilter(colour);
        if (label != null) label.setTextColor(colour);
    }

    private static void route(Activity activity, int viewId, Class<?> target, boolean isActive) {
        View item = activity.findViewById(viewId);
        if (item == null) return;
        item.setSelected(isActive);
        if (isActive) {
            item.setOnClickListener(null);
            return;
        }
        item.setOnClickListener(v -> {
            Intent intent = new Intent(activity, target);
            // REORDER_TO_FRONT keeps each tab's Activity alive, so switching
            // tabs and coming back returns to the same scroll position instead
            // of rebuilding the screen from scratch.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        });
    }

    private BottomNav() {}
}
