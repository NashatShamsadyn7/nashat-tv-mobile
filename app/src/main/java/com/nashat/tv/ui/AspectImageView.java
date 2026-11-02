package com.nashat.tv.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * An image view whose height follows its width at a fixed ratio (4:3 unless
 * set), so a tile keeps its shape in a 3-column phone grid and a 7-column
 * tablet grid alike.
 */
public class AspectImageView extends AppCompatImageView {

    private float ratio = 3f / 4f;

    public AspectImageView(Context context) {
        super(context);
    }

    public AspectImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** Height divided by width. */
    public void setRatio(float ratio) {
        this.ratio = ratio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width * ratio);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
