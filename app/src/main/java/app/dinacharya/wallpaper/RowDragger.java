package app.dinacharya.wallpaper;

import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Drags a row of the routine list up or down. The row follows the finger, the rows it passes slide
 * out of the way, and the list scrolls by itself near the top and bottom of the screen. The maths
 * is in {@link DragReorder}.
 */
final class RowDragger {
    interface Listener {
        /** The row was dropped at a new place. */
        void onMoved(int from, int to);
    }

    private final LinearLayout list;
    private final ScrollView scroll;
    private final Listener listener;
    private final float gap;
    private final float zone;
    private final int maxStep;

    private int dragged = -1;
    private int target = -1;
    private float startRawY;
    private int startScroll;
    private float lastRawY;
    private float step;
    private float[] heights;
    private float firstTop;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (dragged < 0) return;
            int[] loc = new int[2];
            scroll.getLocationOnScreen(loc);
            int by = DragReorder.autoScroll(lastRawY, loc[1], loc[1] + scroll.getHeight(), zone, maxStep);
            if (by != 0) {
                scroll.scrollBy(0, by);
                follow();
            }
            list.postDelayed(this, 16);
        }
    };

    RowDragger(LinearLayout list, ScrollView scroll, float gapPx, float zonePx, int maxStepPx, Listener l) {
        this.list = list;
        this.scroll = scroll;
        this.gap = gapPx;
        this.zone = zonePx;
        this.maxStep = maxStepPx;
        this.listener = l;
    }

    /** Call from the touch listener of a row's handle. */
    boolean onHandleTouch(int index, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(index, e.getRawY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragged >= 0) {
                    lastRawY = e.getRawY();
                    follow();
                }
                return true;
            case MotionEvent.ACTION_UP:
                finish(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                finish(false);
                return true;
            default:
                return true;
        }
    }

    private void begin(int index, float rawY) {
        int n = list.getChildCount();
        if (index < 0 || index >= n) return;
        list.requestDisallowInterceptTouchEvent(true);
        dragged = index;
        target = index;
        startRawY = rawY;
        lastRawY = rawY;
        startScroll = scroll.getScrollY();
        heights = new float[n];
        for (int i = 0; i < n; i++) heights[i] = list.getChildAt(i).getHeight();
        firstTop = list.getChildAt(0).getTop();
        step = heights[index] + gap;
        View row = list.getChildAt(index);
        row.setTranslationZ(gap * 2);
        row.setAlpha(0.95f);
        list.postDelayed(tick, 16);
    }

    private void follow() {
        if (dragged < 0) return;
        float dy = (lastRawY - startRawY) + (scroll.getScrollY() - startScroll);
        View row = list.getChildAt(dragged);
        if (row == null || heights == null || list.getChildCount() != heights.length) {
            finish(false);
            return;
        }
        row.setTranslationY(dy);
        float top = row.getTop() + dy - firstTop;
        int t = DragReorder.targetIndex(heights, gap, dragged, top);
        if (t != target) {
            target = t;
            for (int i = 0; i < heights.length; i++) {
                if (i == dragged) continue;
                View other = list.getChildAt(i);
                if (other == null) continue;
                other.animate().translationY(DragReorder.shift(i, dragged, target, step)).setDuration(120).start();
            }
        }
    }

    private void finish(boolean drop) {
        if (dragged < 0) return;
        list.removeCallbacks(tick);
        int from = dragged;
        int to = target;
        dragged = -1;
        target = -1;
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            v.animate().cancel();
            v.setTranslationY(0);
            v.setTranslationZ(0);
            v.setAlpha(1f);
        }
        // Rebuild after this touch has finished; changing the list inside its own touch event is unsafe.
        if (drop && to >= 0 && to != from && listener != null) list.post(() -> listener.onMoved(from, to));
    }
}
