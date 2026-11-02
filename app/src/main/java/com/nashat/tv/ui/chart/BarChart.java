package com.nashat.tv.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.nashat.tv.R;

/**
 * Vertical bars with a label under each and the value over the highlighted
 * one. Used for minutes watched per weekday and episodes per season.
 *
 * <p>Bars are scaled to the largest value, not to a fixed ceiling, so a quiet
 * week still shows its shape instead of a row of slivers. Empty bars keep a
 * short stub so the axis never looks broken.
 */
public class BarChart extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final int colorBar;
    private final int colorHighlight;

    private float[] values = new float[0];
    private String[] labels = new String[0];
    private String[] valueLabels;
    private int highlight = -1;

    public BarChart(Context context) {
        this(context, null);
    }

    public BarChart(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        colorBar = ContextCompat.getColor(context, R.color.chart_bar);
        colorHighlight = ContextCompat.getColor(context, R.color.accent_fill);
        Typeface regular = ResourcesCompat.getFont(context, R.font.cairo_medium);
        Typeface bold = ResourcesCompat.getFont(context, R.font.cairo_bold);
        float sp = getResources().getDisplayMetrics().scaledDensity;
        label.setTypeface(regular);
        label.setTextSize(11 * sp);
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(ContextCompat.getColor(context, R.color.content_tertiary));
        value.setTypeface(bold);
        value.setTextSize(12 * sp);
        value.setTextAlign(Paint.Align.CENTER);
        value.setColor(ContextCompat.getColor(context, R.color.content_primary));
    }

    /**
     * @param valueLabels text drawn above each bar, or null to label only the
     *                    highlighted one with its raw value
     * @param highlight   index drawn in the accent colour, or -1 for none
     */
    public void setData(float[] values, String[] labels, @Nullable String[] valueLabels, int highlight) {
        this.values = values;
        this.labels = labels;
        this.valueLabels = valueLabels;
        this.highlight = highlight;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;

        float density = getResources().getDisplayMetrics().density;
        float labelH = label.getTextSize() * 1.6f;
        float valueH = value.getTextSize() * 1.6f;
        float top = getPaddingTop() + valueH;
        float bottom = getHeight() - getPaddingBottom() - labelH;
        float left = getPaddingLeft();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float slot = width / n;
        float barW = Math.min(slot * 0.56f, 28 * density);
        float radius = Math.min(barW / 2f, 6 * density);

        float max = 0;
        for (float v : values) max = Math.max(max, v);

        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < n; i++) {
            int slotIndex = rtl ? n - 1 - i : i;
            float cx = left + slot * slotIndex + slot / 2f;
            float h = max <= 0 ? 0 : (bottom - top) * (values[i] / max);
            h = Math.max(h, 3 * density);

            bar.setColor(i == highlight ? colorHighlight : colorBar);
            rect.set(cx - barW / 2f, bottom - h, cx + barW / 2f, bottom);
            canvas.drawRoundRect(rect, radius, radius, bar);

            if (i < labels.length) {
                canvas.drawText(labels[i], cx, getHeight() - getPaddingBottom() - label.descent(), label);
            }
            String text = valueLabels != null && i < valueLabels.length ? valueLabels[i]
                    : (i == highlight && values[i] > 0 ? String.valueOf(Math.round(values[i])) : null);
            if (text != null && !text.isEmpty()) {
                canvas.drawText(text, cx, bottom - h - value.descent() - 2 * density, value);
            }
        }
    }
}
