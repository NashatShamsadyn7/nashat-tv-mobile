package com.nashat.tv.ui.chart;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nashat.tv.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One horizontal bar split into proportional coloured segments — "how the
 * whole divides". Live TV uses it for channels per category, the profile page
 * for watch time per category.
 *
 * <p>The legend is a separate view group filled by {@link #fillLegend}, so a
 * screen can put it under the bar on a phone and beside it on a tablet.
 */
public class SegmentBar extends View {

    public static final class Segment {
        public final String label;
        public final float value;
        @ColorInt public final int color;

        public Segment(String label, float value, @ColorInt int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private List<Segment> segments = new ArrayList<>();

    public SegmentBar(Context context) {
        this(context, null);
    }

    public SegmentBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSegments(List<Segment> segments) {
        this.segments = segments;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float total = 0;
        for (Segment s : segments) total += Math.max(0, s.value);
        float h = getHeight();
        float w = getWidth();
        float r = h / 2f;
        float gap = getResources().getDisplayMetrics().density * 2;

        clip.reset();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        if (total <= 0) {
            paint.setColor(ContextCompat.getColor(getContext(), R.color.chart_track));
            canvas.drawRect(rect, paint);
            canvas.restore();
            return;
        }

        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float x = 0;
        for (Segment s : segments) {
            if (s.value <= 0) continue;
            float segW = w * (s.value / total);
            float start = rtl ? w - x - segW : x;
            paint.setColor(s.color);
            canvas.drawRect(start, 0, start + Math.max(0, segW - gap), h, paint);
            x += segW;
        }
        canvas.restore();
    }

    /**
     * Fills {@code legend} with one row per segment: swatch, label, value and
     * share of the total.
     */
    public static void fillLegend(ViewGroup legend, List<Segment> segments, ValueFormatter format) {
        legend.removeAllViews();
        float total = 0;
        for (Segment s : segments) total += Math.max(0, s.value);
        LayoutInflater inflater = LayoutInflater.from(legend.getContext());
        for (Segment s : segments) {
            if (s.value <= 0) continue;
            View row = inflater.inflate(R.layout.item_chart_legend, legend, false);
            row.findViewById(R.id.legendSwatch).setBackgroundTintList(ColorStateList.valueOf(s.color));
            ((TextView) row.findViewById(R.id.legendLabel)).setText(s.label);
            ((TextView) row.findViewById(R.id.legendValue)).setText(format.format(s.value));
            int pct = total <= 0 ? 0 : Math.round(100f * s.value / total);
            ((TextView) row.findViewById(R.id.legendShare)).setText(
                    String.format(Locale.getDefault(), "%d%%", pct));
            if (legend instanceof GridLayout) {
                // Equal-width columns; the gap goes between them, not at the edges.
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED),
                        GridLayout.spec(GridLayout.UNDEFINED, 1f));
                lp.width = 0;
                int columns = ((GridLayout) legend).getColumnCount();
                int gap = Math.round(legend.getResources().getDisplayMetrics().density * 16);
                if (legend.getChildCount() % columns != columns - 1) lp.setMarginEnd(gap);
                legend.addView(row, lp);
            } else {
                legend.addView(row);
            }
        }
    }

    public interface ValueFormatter {
        String format(float value);
    }
}
