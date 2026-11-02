package com.nashat.tv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nashat.tv.R;

/**
 * The "on air" mark: a solid dot with a ring that swells and fades every
 * 1.6 seconds. Respects the system animator scale, so it sits still when the
 * user has turned animations off.
 */
public class PulseDot extends View {

    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;

    public PulseDot(Context context) {
        this(context, null);
    }

    public PulseDot(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int color = ContextCompat.getColor(context, R.color.status_live_fill);
        dot.setColor(color);
        ring.setColor(color);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float max = Math.min(cx, cy);
        ring.setAlpha((int) (110 * (1f - phase)));
        canvas.drawCircle(cx, cy, max * (0.45f + 0.55f * phase), ring);
        canvas.drawCircle(cx, cy, max * 0.45f, dot);
    }
}
