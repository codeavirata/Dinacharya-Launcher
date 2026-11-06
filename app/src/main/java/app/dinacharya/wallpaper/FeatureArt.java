package app.dinacharya.wallpaper;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * A moving picture from {@link FeatureScenes}. It plays its scene in a loop while it is on screen, and stops by itself when it is not:
 * each frame asks for the next one, and a view that is scrolled away or whose screen is closed is not drawn, so nothing keeps running.
 * It shows one still picture instead when the phone has animations turned off. It keeps the shape of the scenes (330 by 170) and clips
 * itself to rounded corners.
 */
final class FeatureArt extends View {
    /** The moment of a scene that tells its story best, shown when animations are off. */
    private static final float STILL = 0.72f;
    private static final long FRAME_MS = 24L;
    /** A view not drawn for this long starts its scene again from the beginning when it comes back. */
    private static final long IDLE_MS = 500L;

    private int scene;
    private long startedAt;
    private long lastDraw;
    private final float radius;

    FeatureArt(Context c, int scene) {
        super(c);
        this.scene = scene;
        radius = Ui.dp(c, 22);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
    }

    void setScene(int scene) {
        if (this.scene == scene) return;
        this.scene = scene;
        startedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private static boolean animationsOn() {
        try {
            return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        } catch (RuntimeException e) {
            return true;
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, Math.round(w * 170f / 330f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        long now = SystemClock.uptimeMillis();
        boolean moving = animationsOn();
        float t = STILL;
        if (moving) {
            if (now - lastDraw > IDLE_MS) startedAt = now;
            t = ((now - startedAt) % FeatureScenes.LOOP_MS) / (float) FeatureScenes.LOOP_MS;
        }
        lastDraw = now;
        try {
            FeatureScenes.draw(canvas, scene, t, w, h);
        } catch (RuntimeException e) {
            CrashLog.note(getContext(), "feature scene", e);
            return;
        }
        if (moving) postInvalidateDelayed(FRAME_MS);
    }
}
