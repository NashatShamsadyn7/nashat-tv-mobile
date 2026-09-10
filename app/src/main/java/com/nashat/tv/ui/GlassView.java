package com.nashat.tv.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * A floating chrome layer that blurs whatever is behind it — the bottom nav, a
 * sticky search bar, the player control strip.
 *
 * <h3>Why this class exists at all</h3>
 * Android has no equivalent of iOS's {@code UIVisualEffectView}. The obvious
 * candidate, {@link View#setRenderEffect}, blurs a view's <em>own</em> content,
 * not the content behind it, so pointing it at the nav bar blurs the icons and
 * leaves the backdrop sharp — the exact inverse of what is wanted. The only way
 * to blur a backdrop inside an app window is to draw that backdrop a second
 * time into an offscreen buffer and blur the copy, which is what this does.
 *
 * <h3>What it costs, and the three gates</h3>
 * Re-drawing part of the hierarchy every frame is not free, so the effect is
 * switched off entirely unless all three of these hold:
 *
 * <ul>
 *   <li><b>API 31+</b> — {@link RenderEffect} is the only GPU blur the platform
 *       offers. This app's minSdk is 23, and the pre-31 alternatives (a
 *       RenderScript blur, or a box blur in Java) cost more than the effect is
 *       worth on the hardware that is still on those versions.</li>
 *   <li><b>Not a low-RAM device</b> — {@link ActivityManager#isLowRamDevice()}
 *       is the platform's own signal that a device should be given the cheap
 *       path, and it is the population most likely to be watching video while
 *       the nav bar tries to blur it.</li>
 *   <li><b>A backdrop has been set</b> via {@link #setBackdrop(View)}.</li>
 * </ul>
 *
 * When any gate fails the view draws its background drawable and nothing else.
 * That is why {@code bg_glass_panel} carries a translucent fill of its own
 * rather than relying on the blur: the fallback has to be a finished design,
 * not a degraded one. The panel reads as a slightly veiled layer instead of a
 * blurred one, and no layout changes shape.
 *
 * <h3>Sheets and dialogs do not use this</h3>
 * They get the real thing for free through
 * {@code Window#setBackgroundBlurRadius}, which blurs everything behind the
 * window at the compositor with no redraw at all. See {@code GlassWindow}.
 */
public class GlassView extends FrameLayout {

    /**
     * The backdrop is captured at 1/6 scale before being blurred. Downsampling
     * is itself a blur, so the RenderEffect radius below is divided by the same
     * factor and the visual result is indistinguishable from a full-resolution
     * blur at 1/36th of the fill rate. Going coarser than 6 starts to show as
     * banding where a poster's edge crosses the panel.
     */
    private static final int DOWNSCALE = 6;

    private float blurRadiusPx = 0f;

    @Nullable private View backdrop;
    @Nullable private Bitmap buffer;
    @Nullable private Canvas bufferCanvas;

    /**
     * The blur itself, held as {@link Object} rather than as a {@link Blur}.
     *
     * <p>Not squeamishness about types: {@code Blur} touches {@link RenderNode}
     * (API 29) and {@link RenderEffect} (API 31), and naming it in a field of
     * this class would put those types in GlassView's own verification set.
     * ART verifies a class when it is first loaded, so on the API 23–30 devices
     * this app still supports that would soft-fail GlassView as a whole and
     * drop it to the interpreter — for a view that redraws every frame. Keeping
     * the reference untyped confines the unresolvable types to a class that is
     * only ever loaded on API 31+.
     */
    @Nullable private Object blur;

    /** Guards against the re-entrant draw described in {@link #captureBackdrop}. */
    private boolean capturing;

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        if (isEnabledForDevice() && isShown()) {
            captureBackdrop();
        }
        return true;
    };

    public GlassView(Context context) {
        this(context, null);
    }

    public GlassView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // The panel draws a backdrop copy underneath its children, so it must
        // not be skipped by the "no background, nothing to draw" optimisation.
        setWillNotDraw(false);
        blurRadiusPx = getResources().getDimension(com.nashat.tv.R.dimen.glass_blur_radius);
    }

    /**
     * Names the view whose content should show through blurred — normally the
     * scrolling content the panel floats over, <em>not</em> the whole window.
     *
     * <p>Passing an ancestor of this view would recurse: drawing the ancestor
     * draws this panel, which draws the ancestor again. Pass a sibling, or the
     * content container that this panel is layered on top of.
     */
    public void setBackdrop(@Nullable View backdropRoot) {
        if (backdrop == backdropRoot) return;
        detachListener();
        backdrop = backdropRoot;
        if (backdrop != null && isAttachedToWindow()) {
            backdrop.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        }
        invalidate();
    }

    /** Overrides the default {@code glass_blur_radius} for this panel. */
    public void setBlurRadius(float radiusPx) {
        blurRadiusPx = radiusPx;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (backdrop != null) {
            backdrop.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        detachListener();
        releaseBuffer();
        super.onDetachedFromWindow();
    }

    private void detachListener() {
        if (backdrop != null) {
            backdrop.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // The buffer is sized from the panel, so any resize invalidates it.
        releaseBuffer();
    }

    /**
     * Draws the slice of the backdrop that lies directly behind this panel into
     * the offscreen buffer.
     *
     * <p>The {@link #capturing} flag exists because the backdrop's draw pass can
     * reach this view again on some hierarchies; without it the second entry
     * would recurse into a stack overflow rather than simply skipping.
     */
    private void captureBackdrop() {
        if (capturing || backdrop == null) return;
        if (getWidth() == 0 || getHeight() == 0) return;

        int bw = Math.max(1, getWidth() / DOWNSCALE);
        int bh = Math.max(1, getHeight() / DOWNSCALE);

        if (buffer == null || buffer.getWidth() != bw || buffer.getHeight() != bh) {
            releaseBuffer();
            buffer = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            bufferCanvas = new Canvas(buffer);
        }

        capturing = true;
        try {
            buffer.eraseColor(0);
            bufferCanvas.save();
            bufferCanvas.scale(1f / DOWNSCALE, 1f / DOWNSCALE);
            // Shift the backdrop so the pixels under this panel land at the
            // buffer's origin. Both coordinate pairs are window-relative, so
            // this holds regardless of how deeply either view is nested.
            int[] mine = new int[2];
            int[] theirs = new int[2];
            getLocationInWindow(mine);
            backdrop.getLocationInWindow(theirs);
            bufferCanvas.translate(theirs[0] - mine[0], theirs[1] - mine[1]);
            backdrop.draw(bufferCanvas);
            bufferCanvas.restore();
        } catch (Exception ignored) {
            // A backdrop that cannot be drawn offscreen (a SurfaceView holding
            // video, for one) must not take the frame down with it. Dropping to
            // the flat fill for this frame is the correct failure.
            releaseBuffer();
        } finally {
            capturing = false;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isEnabledForDevice() && buffer != null && !capturing) {
            drawBlurredBuffer(canvas);
        }
        // The background drawable is drawn by the framework BEFORE onDraw, so
        // the translucent fill in bg_glass_panel would sit under the blur and
        // be invisible. Drawing the blur first and the fill on top of it here
        // is what produces the tinted-glass look rather than a bare blur.
        if (getBackground() != null) {
            getBackground().draw(canvas);
        }
        super.onDraw(canvas);
    }

    private void drawBlurredBuffer(Canvas canvas) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        // drawRenderNode is a hardware-canvas call. A software canvas turns up
        // during a draw-to-bitmap (screenshots, another GlassView capturing
        // this one), and would throw rather than degrade.
        if (!canvas.isHardwareAccelerated()) return;

        if (blur == null) {
            blur = new Blur();
        }
        // The buffer is already downsampled by DOWNSCALE, so the effective
        // radius must be scaled to match or the blur is six times too strong.
        ((Blur) blur).draw(canvas, buffer, getWidth(), getHeight(),
                Math.max(1f, blurRadiusPx / DOWNSCALE));
    }

    private void releaseBuffer() {
        if (buffer != null) {
            buffer.recycle();
            buffer = null;
        }
        bufferCanvas = null;
        // The version check is redundant — `blur` is only ever non-null on
        // API 31+, because that is the only path that assigns it — but lint
        // cannot see that through the Object field, and a suppression here
        // would also hide a real mistake if the assignment ever moved.
        if (blur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ((Blur) blur).release();
        }
        blur = null;
    }

    /**
     * Everything that touches an API 31 type, in one class that is never
     * loaded below API 31. See the {@link #blur} field for why this is not
     * simply a couple of {@code @RequiresApi} methods on GlassView.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private static final class Blur {

        private final RenderNode node = new RenderNode("glass");

        void draw(Canvas canvas, Bitmap source, int width, int height, float radius) {
            node.setPosition(0, 0, width, height);
            node.setRenderEffect(
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));

            Canvas recording = node.beginRecording();
            recording.scale((float) width / source.getWidth(),
                    (float) height / source.getHeight());
            recording.drawBitmap(source, 0, 0, null);
            node.endRecording();

            canvas.drawRenderNode(node);
        }

        void release() {
            node.discardDisplayList();
        }
    }

    private boolean isEnabledForDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        if (isInEditMode()) return false;
        ActivityManager am =
                (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        return am == null || !am.isLowRamDevice();
    }
}
