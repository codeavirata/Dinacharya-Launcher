package app.dinacharya.wallpaper;

/** Maths for dragging a row up or down a list. Uses no Android classes. */
public final class DragReorder {
    private DragReorder() {
    }

    /**
     * Where the dragged row would land. Rows are judged by where they sit now, with the dragged row
     * still in its place. Dragging down, the row passes a neighbour once its bottom edge goes past the
     * neighbour's middle; dragging up, once its top edge goes above it.
     *
     * @param heights    row heights in the current order
     * @param gap        space between rows
     * @param dragged    index of the row being dragged
     * @param draggedTop the dragged row's top edge now, measured from the top of the first row
     * @return the index the row should be dropped at, from 0 to heights.length - 1
     */
    public static int targetIndex(float[] heights, float gap, int dragged, float draggedTop) {
        int n = heights.length;
        float[] mid = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            mid[i] = y + heights[i] / 2f;
            y += heights[i] + gap;
        }
        float bottom = draggedTop + heights[dragged];
        int target = dragged;
        for (int j = dragged + 1; j < n; j++) {
            if (mid[j] < bottom) target = j;
            else break;
        }
        for (int j = dragged - 1; j >= 0; j--) {
            if (mid[j] > draggedTop) target = j;
            else break;
        }
        return target;
    }

    /**
     * How far another row moves out of the way while a row is dragged from {@code dragged} to {@code target}.
     *
     * @param step the dragged row's height plus the gap
     */
    public static float shift(int row, int dragged, int target, float step) {
        if (row == dragged) return 0;
        if (target > dragged && row > dragged && row <= target) return -step;
        if (target < dragged && row >= target && row < dragged) return step;
        return 0;
    }

    /** How many pixels to scroll while the finger is near the top or bottom of the visible list (negative = up). */
    public static int autoScroll(float fingerY, float viewTop, float viewBottom, float zone, int maxStep) {
        if (fingerY < viewTop + zone) {
            float f = Math.min(1f, (viewTop + zone - fingerY) / zone);
            return -Math.max(1, Math.round(f * maxStep));
        }
        if (fingerY > viewBottom - zone) {
            float f = Math.min(1f, (fingerY - (viewBottom - zone)) / zone);
            return Math.max(1, Math.round(f * maxStep));
        }
        return 0;
    }
}
