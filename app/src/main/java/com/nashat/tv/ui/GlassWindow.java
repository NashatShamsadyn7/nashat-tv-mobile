package com.nashat.tv.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nashat.tv.R;

/**
 * Applies the platform's own backdrop blur to a dialog or bottom-sheet window.
 *
 * <p>Unlike {@link GlassView}, this costs nothing: the blur happens in the
 * system compositor, on content the app never redraws. Any window that has one
 * — a sheet, an alert, the source picker — should use this rather than the
 * view-level path, which exists only for chrome that lives inside the activity
 * window and therefore has no window of its own to blur behind.
 *
 * <p>Two conditions have to hold, and the second is the one that surprises
 * people: cross-window blur is a <em>runtime</em> capability, not just an API
 * level. It is off on devices whose GPU cannot afford it, and the system turns
 * it off dynamically in battery-saver mode and when the developer option
 * "disable window blurs" is set. So {@link WindowManager#isCrossWindowBlurEnabled()}
 * has to be consulted at the moment the window is shown, and the sheet has to
 * look finished when the answer is no — which is why the fallback swaps in an
 * opaque fill instead of leaving a 92%-transparent panel floating over sharp
 * content.
 */
public final class GlassWindow {

    private GlassWindow() {
    }

    /** Convenience for the common case of a {@link Dialog} or BottomSheetDialog. */
    public static void apply(@Nullable Dialog dialog) {
        if (dialog != null) {
            apply(dialog.getWindow(), dialog.getContext());
        }
    }

    /**
     * Blurs whatever is behind {@code window}, falling back to an opaque
     * surface where the platform will not do it.
     *
     * @param decorTarget the view carrying the glass background — normally the
     *                    sheet's own container. Passed so the fallback can
     *                    replace a translucent fill with a solid one; may be
     *                    null, in which case the window is simply left alone.
     */
    public static void apply(@Nullable Window window, @NonNull Context context,
                             @Nullable View decorTarget) {
        if (window == null) return;

        // The SDK check is spelled out rather than left inside
        // isBlurAvailable, because lint resolves API guards syntactically and
        // will not follow one into a helper method.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isBlurAvailable(context)) {
            // The blur is drawn behind the window's own background, so that
            // background must let it through. An opaque sheet would blur
            // pixels nobody can see.
            window.setBackgroundBlurRadius(
                    context.getResources().getDimensionPixelSize(R.dimen.glass_blur_radius_sheet));
            // FLAG_DIM_BEHIND and a blur fight each other: the dim is a flat
            // black wash that flattens exactly the depth the blur creates.
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        } else if (decorTarget != null) {
            decorTarget.setBackgroundResource(R.drawable.bg_sheet_opaque);
        }
    }

    public static void apply(@Nullable Window window, @NonNull Context context) {
        apply(window, context, null);
    }

    /**
     * True only when the device will actually honour a blur right now. Callers
     * that need to lay out differently (rather than just restyle) should ask
     * this directly.
     */
    public static boolean isBlurAvailable(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return wm != null && wm.isCrossWindowBlurEnabled();
    }
}
