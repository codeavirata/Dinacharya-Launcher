package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * A circle that grows while you breathe in and shrinks while you breathe out, for a set number of breaths. It is driven by the clock, not
 * by an animator, so turning animations off in the phone's settings cannot skip the breaths. If the screen is left, the breaths start again.
 */
final class BreatheView extends View {
    interface Listener {
        /** Every frame: breathing in or out, and the number of the breath (from 1). */
        void phase(boolean in, int breath, int total);

        /** All the breaths are done. */
        void done();
    }

    /** Seconds to breathe in, and to breathe out. */
    static final long HALF_MS = 4000L;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;
    private int breaths;
    private long begin;
    private boolean running;
    private Listener listener;
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (getWindowVisibility() != View.VISIBLE) {
                begin = SystemClock.uptimeMillis();   // the screen was left: start the breaths again
                postDelayed(this, 250);
                return;
            }
            long el = SystemClock.uptimeMillis() - begin;
            long total = breaths * 2L * HALF_MS;
            if (el >= total) {
                running = false;
                level = 0.4f;
                invalidate();
                if (listener != null) listener.done();
                return;
            }
            int seg = (int) (el / HALF_MS);
            float f = (el % HALF_MS) / (float) HALF_MS;
            float eased = (1f - (float) Math.cos(Math.PI * f)) / 2f;
            level = seg % 2 == 0 ? eased : 1f - eased;
            invalidate();
            if (listener != null) listener.phase(seg % 2 == 0, seg / 2 + 1, breaths);
            postDelayed(this, 16);
        }
    };

    BreatheView(Context c) {
        super(c);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor((Ui.TEAL & 0x00FFFFFF) | 0x38000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 3));
        ring.setColor(Ui.TEAL);
    }

    /** Starts {@code count} breaths. */
    void start(int count, Listener l) {
        listener = l;
        breaths = Math.max(1, count);
        begin = SystemClock.uptimeMillis();
        running = true;
        removeCallbacks(frame);
        post(frame);
    }

    void stop() {
        running = false;
        removeCallbacks(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float max = Math.min(getWidth(), getHeight()) / 2f - Ui.dp(getContext(), 4);
        float r = max * (0.40f + 0.60f * level);
        c.drawCircle(cx, cy, r, fill);
        c.drawCircle(cx, cy, r, ring);
        c.drawCircle(cx, cy, r * 0.30f, fill);
    }
}
