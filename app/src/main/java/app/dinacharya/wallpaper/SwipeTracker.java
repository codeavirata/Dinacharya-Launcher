package app.dinacharya.wallpaper;

/**
 * Tells a sideways swipe on a row from a scroll or a tap. Feed it the touch points; it decides once
 * the finger has moved past the touch slop. Uses no Android classes.
 */
public final class SwipeTracker {
    public static final int NONE = 0, DELETE = -1, ADD = 1;

    private static final int UNDECIDED = 0, SWIPING = 1, VERTICAL = 2;

    private final float slop;
    private final float commitFraction;
    private float x0, y0, dx;
    private int state = UNDECIDED;

    /**
     * @param slop           how far the finger must move before the direction is decided (pixels)
     * @param commitFraction how far across the row (0..1) the row must be pulled to count
     */
    public SwipeTracker(float slop, float commitFraction) {
        this.slop = slop;
        this.commitFraction = commitFraction;
    }

    public void down(float x, float y) {
        x0 = x;
        y0 = y;
        dx = 0;
        state = UNDECIDED;
    }

    /** Returns true while the gesture is a sideways swipe. */
    public boolean move(float x, float y) {
        float mx = x - x0;
        float my = y - y0;
        if (state == UNDECIDED) {
            float ax = Math.abs(mx);
            float ay = Math.abs(my);
            if (ax > slop || ay > slop) state = (ax > 1.5f * ay) ? SWIPING : VERTICAL;
        }
        if (state == SWIPING) dx = mx;
        return state == SWIPING;
    }

    public boolean swiping() {
        return state == SWIPING;
    }

    /** How far to shift the row now, limited to +-maxAbs. */
    public float offset(float maxAbs) {
        return Math.max(-maxAbs, Math.min(maxAbs, dx));
    }

    /** DELETE for a left swipe, ADD for a right swipe, NONE if it was not pulled far enough. */
    public int release(float rowWidth) {
        int out = NONE;
        if (state == SWIPING) {
            if (dx <= -commitFraction * rowWidth) out = DELETE;
            else if (dx >= commitFraction * rowWidth) out = ADD;
        }
        state = UNDECIDED;
        dx = 0;
        return out;
    }

    /** How strongly the action is "armed" (0 to 1) so the row can show it: negative for delete, positive for add. */
    public float armed(float rowWidth) {
        if (state != SWIPING || rowWidth <= 0) return 0;
        return Math.max(-1f, Math.min(1f, dx / (commitFraction * rowWidth)));
    }
}
