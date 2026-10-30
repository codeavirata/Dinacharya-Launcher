package app.dinacharya.wallpaper;

/**
 * Tells a sideways swipe over the pages (which changes the tab) from a scroll, a tap or a drag that belongs to something on the page.
 * Feed it the touch points; it says when the gesture is clearly sideways (so the page below should be cancelled) and, when the finger
 * is lifted, which tab to go to. Uses no Android classes.
 */
public final class TabSwipe {
    /** No tab change. */
    public static final int STAY = -1;

    private final float slop;
    private final float far;
    private final float flick;
    private float x0, y0;
    private long t0;
    private boolean live;

    /**
     * @param slop  the touch slop (pixels): the finger must be well past it before the gesture counts as sideways
     * @param far   how far (pixels) a normal swipe must travel
     * @param flick how far (pixels) a quick flick must travel
     */
    public TabSwipe(float slop, float far, float flick) {
        this.slop = slop;
        this.far = far;
        this.flick = flick;
    }

    /**
     * A finger touched down.
     *
     * @param eligible false when the finger landed on something that uses sideways moves itself (a slider, a row that swipes to
     *                 delete, a strip that scrolls sideways) or took the finger at once (a drag handle): then this gesture is left alone
     */
    public void down(float x, float y, long ms, boolean eligible) {
        x0 = x;
        y0 = y;
        t0 = ms;
        live = eligible;
    }

    /** A second finger landed, or the system took the gesture: this is no swipe. */
    public void cancel() {
        live = false;
    }

    public boolean live() {
        return live;
    }

    /** True once the finger has gone clearly sideways: take the gesture away from the page below (it gets a cancel, so no tap follows). */
    public boolean takes(float x, float y) {
        if (!live) return false;
        float ax = Math.abs(x - x0);
        float ay = Math.abs(y - y0);
        return ax > 2 * slop && ax > 2 * ay;
    }

    /**
     * The finger was lifted. Returns the tab to show: the next one for a swipe to the left, the previous one for a swipe to the right,
     * or {@link #STAY} when it was not a clear, quick enough swipe or there is no tab on that side (no wrap-around).
     */
    public int release(float x, float y, long ms, int tab, int tabs) {
        return release(x, y, ms, tab, tabs, 0);
    }

    /**
     * The same, for a page that followed the finger: a drag that took it a third of the way across counts however slowly it was done
     * ({@code width} is the page width in pixels; 0 turns that off).
     */
    public int release(float x, float y, long ms, int tab, int tabs, float width) {
        boolean was = live;
        live = false;
        if (!was || tab < 0 || tab >= tabs) return STAY;
        float dx = x - x0;
        float ax = Math.abs(dx);
        float ay = Math.abs(y - y0);
        long took = ms - t0;
        boolean longEnough = ax >= far && took <= 900;
        boolean quickEnough = ax >= flick && took <= 250;
        boolean draggedFar = width > 0 && ax >= width / 3f;
        if (!(longEnough || quickEnough || draggedFar) || ax < 2 * ay) return STAY;
        int next = dx < 0 ? tab + 1 : tab - 1;
        return next < 0 || next >= tabs ? STAY : next;
    }
}
