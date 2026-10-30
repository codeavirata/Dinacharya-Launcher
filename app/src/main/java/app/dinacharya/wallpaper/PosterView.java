package app.dinacharya.wallpaper;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * The day poster as a view. In the setup screen it is a small rounded preview; as the home screen's background
 * ({@link #setFullScreen}) it fills the screen. It redraws every minute, but only while its window is showing.
 */
final class PosterView extends View {
    private final PosterRenderer renderer = new PosterRenderer();
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            refreshScreen();
            scheduleNext();
        }
    };

    private final Runnable pageTick = new Runnable() {
        @Override
        public void run() {
            if (getWindowVisibility() == VISIBLE && getVisibility() == VISIBLE) invalidate();
        }
    };

    /** The routine being edited on screen; when set, the preview draws it instead of the saved one. */
    private Routine source;
    private boolean failedOnce;
    private boolean full;

    PosterView(Context context) {
        super(context);
    }

    /** Fills the whole view, square-cornered: the poster is the home screen's background. */
    void setFullScreen(boolean on) {
        full = on;
        invalidate();
    }

    /** Whether the poster is drawn in its night colours right now (so the status bar icons can match). */
    boolean isNight() {
        try {
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return PosterRenderer.isNight(routine(), night == Configuration.UI_MODE_NIGHT_YES);
        } catch (RuntimeException e) {
            return false;
        }
    }

    void setSource(Routine r) {
        source = r;
        invalidate();
    }

    private Routine routine() {
        return source != null ? source : Store.load(getContext());
    }

    /** Reads today's screen time in the background, then redraws the preview. */
    void refreshScreen() {
        if (routine().showScreen) {
            ScreenTime.refresh(getContext(), () -> invalidate());
        }
        SlotUseCache.refresh(getContext(), () -> invalidate());   // phone use in the slots, if the person turned it on
    }

    private void scheduleNext() {
        removeCallbacks(minuteTick);
        long now = System.currentTimeMillis();
        postDelayed(minuteTick, 60000L - (now % 60000L) + 150L);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE && getVisibility() == VISIBLE) scheduleNext();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        syncTicking();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (changed == this) syncTicking();
    }

    /** Redraws every minute only while the poster can actually be seen. */
    private void syncTicking() {
        if (getWindowVisibility() == VISIBLE && getVisibility() == VISIBLE) {
            invalidate();
            refreshScreen();
            scheduleNext();
        } else {
            removeCallbacks(minuteTick);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(minuteTick);
        removeCallbacks(pageTick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        if (!full) {
            float r = w * 0.07f;
            bounds.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(bounds, r, r, Path.Direction.CW);
            canvas.clipPath(clip);
        }
        try {
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            Routine routine = routine();
            long now = System.currentTimeMillis();
            renderer.drawLive(canvas, w, h, routine, Store.overrides(getContext(), routine), now, Store.zone(),
                    night == Configuration.UI_MODE_NIGHT_YES, routine.showScreen ? ScreenTime.last : null,
                    Store.posterExtras(getContext(), routine));
            // A to-do list longer than its room moves on to its next page every few seconds (only drawn while the view is showing).
            if (renderer.scrolling()) {
                removeCallbacks(pageTick);
                postDelayed(pageTick, Todos.PAGE_MS - (now % Todos.PAGE_MS) + 40L);
            }
        } catch (RuntimeException e) {
            // A drawing problem must never close the setup screen. Note it once and show a plain card.
            if (!failedOnce) CrashLog.note(getContext(), "preview", e);
            failedOnce = true;
            canvas.drawColor(0xFFF3F7F6);
        }
        canvas.restore();
    }
}
