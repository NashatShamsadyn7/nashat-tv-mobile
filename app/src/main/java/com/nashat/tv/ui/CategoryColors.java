package com.nashat.tv.ui;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.nashat.tv.R;

import java.util.Locale;

/**
 * One colour per channel category, shared by the Live TV chart, its legend
 * and the dot on every channel tile — so a colour learned in the chart means
 * the same thing in the grid.
 */
public final class CategoryColors {

    private static final int[] SERIES = {
            R.color.series_1, R.color.series_2, R.color.series_3, R.color.series_4,
            R.color.series_5, R.color.series_6, R.color.series_7, R.color.series_8
    };

    @ColorInt
    public static int forCategory(Context context, String category) {
        String c = category == null ? "" : category.toLowerCase(Locale.US);
        int index;
        if (c.contains("news")) index = 0;
        else if (c.contains("music")) index = 1;
        else if (c.contains("kid") || c.contains("cartoon")) index = 2;
        else if (c.contains("sport")) index = 3;
        else if (c.contains("religi")) index = 4;
        else if (c.contains("movie") || c.contains("series") || c.contains("drama") || c.contains("entertain")) index = 5;
        else if (c.contains("doc")) index = 6;
        else index = 7;
        return ContextCompat.getColor(context, SERIES[index]);
    }

    private CategoryColors() {}
}
