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

import java.util.Locale;

/**
 * A TMDB score (0–10) drawn as a ring with the number in the middle.
 *
 * <p>The arc colour carries the verdict so it reads before the number does:
 * green from 7, amber from 5, crimson below. The ring is the only rating
 * treatment in the app — posters, details and the home hero all use it, so a
 * score looks the same wherever the viewer meets it.
 */
public class RatingRing extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private double score;
    private boolean filled;

    public RatingRing(Context context) {
        this(context, null);
    }

    public RatingRing(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(ContextCompat.getColor(context, R.color.chart_track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        fill.setColor(ContextCompat.getColor(context, R.color.rating_ring_fill));
        number.setTextAlign(Paint.Align.CENTER);
        number.setColor(ContextCompat.getColor(context, R.color.content_primary));
        Typeface bold = ResourcesCompat.getFont(context, R.font.cairo_bold);
        number.setTypeface(bold != null ? bold : Typeface.DEFAULT_BOLD);
    }

    /** Score out of ten. Zero or less hides the arc and shows a dash. */
    public void setScore(double score) {
        this.score = Math.max(0, Math.min(10, score));
        setContentDescription(score > 0
                ? String.format(Locale.US, "%.1f / 10", this.score) : null);
        invalidate();
    }

    /** Solid dark disc behind the ring, for when it sits on artwork. */
    public void setFilled(boolean filled) {
        this.filled = filled;
        invalidate();
    }

    public static int colorFor(Context context, double score) {
        int res = score >= 7 ? R.color.score_high : score >= 5 ? R.color.score_mid : R.color.score_low;
        return ContextCompat.getColor(context, res);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float stroke = Math.max(2f, size * 0.09f);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = size / 2f - stroke / 2f;

        if (filled) canvas.drawCircle(cx, cy, size / 2f, fill);

        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        bounds.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawArc(bounds, 0, 360, false, track);

        if (score > 0) {
            arc.setColor(colorFor(getContext(), score));
            canvas.drawArc(bounds, -90, (float) (360 * score / 10.0), false, arc);
        }

        number.setTextSize(size * 0.34f);
        String label = score > 0 ? String.format(Locale.US, "%.1f", score) : "–";
        float baseline = cy - (number.descent() + number.ascent()) / 2f;
        canvas.drawText(label, cx, baseline, number);
    }
}
