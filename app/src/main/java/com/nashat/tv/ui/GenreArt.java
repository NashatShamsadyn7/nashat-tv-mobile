package com.nashat.tv.ui;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.nashat.tv.R;

/**
 * Tile colours for the genre shortcuts: the chart series palette, deepened
 * toward the page colour so white text sits on every tile and a strip of them
 * reads as one set rather than a box of crayons.
 */
public final class GenreArt {

    private static final int[] SERIES = {
            R.color.series_1, R.color.series_4, R.color.series_3, R.color.series_5,
            R.color.series_2, R.color.series_6, R.color.series_7
    };

    @ColorInt
    public static int color(Context context, int index) {
        int base = ContextCompat.getColor(context, SERIES[index % SERIES.length]);
        return ColorUtils.blendARGB(base, ContextCompat.getColor(context, R.color.ink_900), 0.45f);
    }

    private GenreArt() {}
}
