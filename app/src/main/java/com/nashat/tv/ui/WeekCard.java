package com.nashat.tv.ui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.nashat.tv.R;
import com.nashat.tv.WatchStatsManager;
import com.nashat.tv.ui.chart.BarChart;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/** Fills {@code view_week_card} from {@link WatchStatsManager}. */
public final class WeekCard {

    public static void bind(View card) {
        Context ctx = card.getContext();
        WatchStatsManager stats = new WatchStatsManager(ctx);

        // WatchStatsManager counts backwards from today; the chart reads
        // left-to-right oldest to newest, so today lands at the end.
        int[] byDaysAgo = stats.getWeeklyMinutes();
        float[] values = new float[7];
        String[] labels = new String[7];
        String[] shortDays = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();
        Calendar day = Calendar.getInstance();
        int week = 0;
        for (int i = 6; i >= 0; i--) {
            int daysAgo = 6 - i;
            values[i] = byDaysAgo[daysAgo];
            week += byDaysAgo[daysAgo];
            Calendar c = (Calendar) day.clone();
            c.add(Calendar.DAY_OF_YEAR, -daysAgo);
            String name = shortDays[c.get(Calendar.DAY_OF_WEEK)];
            labels[i] = name.length() > 3 ? name.substring(0, 3) : name;
        }

        String[] valueLabels = new String[7];
        for (int i = 0; i < 7; i++) {
            valueLabels[i] = values[i] > 0 ? formatShort(ctx, (int) values[i]) : "";
        }
        ((BarChart) card.findViewById(R.id.weekBars)).setData(values, labels, valueLabels, 6);

        ((TextView) card.findViewById(R.id.weekTotal)).setText(formatLong(ctx, week));
        card.findViewById(R.id.weekEmpty).setVisibility(week == 0 ? View.VISIBLE : View.GONE);

        ((TextView) card.findViewById(R.id.weekSessions)).setText(
                String.format(Locale.getDefault(), "%d", stats.getTotalSessions()));
        ((TextView) card.findViewById(R.id.weekAverage)).setText(formatShort(ctx, Math.round(week / 7f)));
        String top = stats.getTopCategory();
        ((TextView) card.findViewById(R.id.weekTop)).setText(
                top == null || top.trim().isEmpty() ? "–" : top);
    }

    public static String formatLong(Context ctx, int minutes) {
        if (minutes < 60) return ctx.getString(R.string.stats_minutes, minutes);
        return ctx.getString(R.string.stats_hours, minutes / 60, minutes % 60);
    }

    private static String formatShort(Context ctx, int minutes) {
        if (minutes < 60) return ctx.getString(R.string.stats_minutes, minutes);
        return String.format(Locale.getDefault(), "%.1fh", minutes / 60f);
    }

    private WeekCard() {}
}
