package com.nashat.tv.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nashat.tv.R;

/**
 * Ten short segments, filled up to a score out of ten (the last one partly).
 * Reads like a signal meter, so 7.4 and 6.1 are told apart without reading.
 */
public class StepBar extends View {

    private static final int STEPS = 10;

    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private double score;

    public StepBar(Context context) {
        this(context, null);
    }

    public StepBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        off.setColor(ContextCompat.getColor(context, R.color.chart_track));
    }

    public void setScore(double score) {
        this.score = Math.max(0, Math.min(10, score));
        on.setColor(RatingRing.colorFor(getContext(), this.score));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float gap = getResources().getDisplayMetrics().density * 3;
        float w = (getWidth() - gap * (STEPS - 1)) / STEPS;
        float h = getHeight();
        float r = h / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < STEPS; i++) {
            int slot = rtl ? STEPS - 1 - i : i;
            float x = slot * (w + gap);
            rect.set(x, 0, x + w, h);
            canvas.drawRoundRect(rect, r, r, off);
            double part = Math.max(0, Math.min(1, score - i));
            if (part > 0) {
                float pw = (float) (w * part);
                if (rtl) rect.set(x + w - pw, 0, x + w, h);
                else rect.set(x, 0, x + pw, h);
                canvas.drawRoundRect(rect, r, r, on);
            }
        }
    }
}
