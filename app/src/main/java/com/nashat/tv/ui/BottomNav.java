package com.nashat.tv.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.nashat.tv.LiveTvActivity;
import com.nashat.tv.MoviesActivity;
import com.nashat.tv.R;
import com.nashat.tv.SeriesActivity;
import com.nashat.tv.SettingsActivity;

/**
 * Builds and binds {@code view_bottom_nav.xml}.
 *
 * <p>Exists so the destination list and the active-state logic live in one
 * place. Each screen used to hard-code its own colours into its own copy of the
 * bar, and the copies had drifted: two highlighted the active tab, one
 * highlighted nothing, and the labels were three different sizes.
 *
 * <p>The bar itself is now built here rather than spelled out in XML. Every
 * fact about a destination — its icon, its label, where it goes — is one row of
 * {@link #TABS}, so adding a fifth is one line rather than an edit to a layout,
 * two new ids and three methods.
 */
public final class BottomNav {

    public enum Tab {
        LIVE, MOVIES, SERIES, SETTINGS
    }

    /** One row per destination, in the order they appear in the bar. */
    private static final Destination[] TABS = {
            new Destination(Tab.LIVE, R.drawable.ic_tv_series, R.string.nav_live, LiveTvActivity.class),
            new Destination(Tab.MOVIES, R.drawable.ic_movie_24, R.string.nav_movies, MoviesActivity.class),
            new Destination(Tab.SERIES, R.drawable.ic_tv_series, R.string.nav_series, SeriesActivity.class),
            new Destination(Tab.SETTINGS, R.drawable.ic_settings_24, R.string.nav_settings, SettingsActivity.class),
    };

    /**
     * Fills the bar inside {@code activity} and marks {@code active}.
     *
     * <p>Safe to call more than once on the same screen: the container is
     * cleared first, so a re-bind after a configuration change rebuilds rather
     * than appending a second set of tabs.
     *
     * <p>Tapping the active tab does nothing. Reloading the screen the user is
     * already looking at would only lose their scroll position.
     */
    public static void bind(Activity activity, Tab active) {
        LinearLayout container = activity.findViewById(R.id.bottomNavItems);
        if (container == null) return;

        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);

        for (Destination destination : TABS) {
            View item = inflater.inflate(R.layout.item_bottom_nav, container, false);
            bindItem(activity, item, destination, destination.tab == active);
            container.addView(item);
        }

        bindBackdrop(activity);
    }

    /**
     * Points the glass panel at the content it floats over, so the blur has
     * something to blur.
     *
     * <p>The backdrop must be a SIBLING of the bar, never an ancestor: drawing
     * an ancestor draws the bar, which would draw the ancestor again. Screens
     * that follow the standard structure give their content container the id
     * {@code contentBehindNav} and get this for free; screens that do not
     * simply keep the flat translucent fill, which is a finished look in its
     * own right.
     */
    private static void bindBackdrop(Activity activity) {
        View bar = activity.findViewById(R.id.bottomNav);
        if (!(bar instanceof GlassView)) return;
        ((GlassView) bar).setBackdrop(activity.findViewById(R.id.contentBehindNav));
    }

    private static void bindItem(Activity activity, View item,
                                 Destination destination, boolean isActive) {
        ImageView icon = item.findViewById(R.id.navItemIcon);
        TextView label = item.findViewById(R.id.navItemLabel);

        icon.setImageResource(destination.icon);
        label.setText(destination.label);
        item.setContentDescription(activity.getString(destination.label));

        // The active tab is a solid lime block, so its content has to flip to
        // the on-accent ink — content_secondary on lime is 1.9:1 and would be
        // unreadable exactly where the user looks first.
        int colour = ContextCompat.getColor(activity,
                isActive ? R.color.on_accent_fill : R.color.content_secondary);
        icon.setColorFilter(colour);
        label.setTextColor(colour);

        item.setBackgroundResource(R.drawable.bg_nav_item);
        item.setSelected(isActive);

        if (isActive) {
            item.setOnClickListener(null);
            return;
        }
        item.setOnClickListener(v -> {
            Intent intent = new Intent(activity, destination.target);
            // REORDER_TO_FRONT keeps each tab's Activity alive, so switching
            // tabs and coming back returns to the same scroll position instead
            // of rebuilding the screen from scratch.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        });
    }

    private static final class Destination {
        final Tab tab;
        @DrawableRes final int icon;
        @StringRes final int label;
        @Nullable final Class<?> target;

        Destination(Tab tab, @DrawableRes int icon, @StringRes int label, @Nullable Class<?> target) {
            this.tab = tab;
            this.icon = icon;
            this.label = label;
            this.target = target;
        }
    }

    private BottomNav() {}
}
