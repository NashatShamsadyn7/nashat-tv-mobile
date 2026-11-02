package com.nashat.tv.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nashat.tv.R;

/** A rounded track filled to a fraction (0–1), in the accent unless told otherwise. */
public class MeterBar extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;

    public MeterBar(Context context) {
        this(context, null);
    }

    public MeterBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        track.setColor(ContextCompat.getColor(context, R.color.chart_track));
        fill.setColor(ContextCompat.getColor(context, R.color.accent_fill));
    }

    public void setFraction(float fraction) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public void setColor(@ColorInt int color) {
        fill.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float w = getWidth();
        float r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);
        if (fraction <= 0) return;
        float fw = Math.max(h, w * fraction);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (rtl) rect.set(w - fw, 0, w, h);
        else rect.set(0, 0, fw, h);
        canvas.drawRoundRect(rect, r, r, fill);
    }
}
