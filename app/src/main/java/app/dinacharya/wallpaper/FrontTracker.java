package app.dinacharya.wallpaper;

/**
 * Works out which app is in front of the screen from the phone's usage events (an app coming to the front, going behind,
 * the screen going off). Feed it the events in time order; ask {@link #front()} for the package. Uses no Android classes.
 *
 * A screen of an app is told apart by its class name, because an app moving from one of its screens to another sends
 * "behind" for the old one after "front" for the new one, and that must not look like the app leaving.
 */
public final class FrontTracker {
    /** The usage event types that matter (the numbers Android uses, written out so this class needs no Android). */
    public static final int RESUMED = 1, PAUSED = 2, SCREEN_OFF = 16, STOPPED = 23, SHUTDOWN = 26;

    private String pkg = "";
    private String cls = "";
    private long lastTs;

    /** One event. {@code pkg} and {@code cls} may be null for the events that name none. */
    public void feed(int type, String pkg, String cls, long ts) {
        if (ts > lastTs) lastTs = ts;
        String p = pkg == null ? "" : pkg;
        String c = cls == null ? "" : cls;
        switch (type) {
            case RESUMED:
                if (!p.isEmpty()) {
                    this.pkg = p;
                    this.cls = c;
                }
                break;
            case PAUSED:
            case STOPPED:
                if (p.equals(this.pkg) && c.equals(this.cls)) {
                    this.pkg = "";
                    this.cls = "";
                }
                break;
            case SCREEN_OFF:
            case SHUTDOWN:
                this.pkg = "";
                this.cls = "";
                break;
            default:
                break;
        }
    }

    /** The package in front, or an empty string when none is known (the screen is off, or nothing has been seen). */
    public String front() {
        return pkg;
    }

    /** The time of the latest event fed in (0 if none). */
    public long lastTs() {
        return lastTs;
    }

    public void reset() {
        pkg = "";
        cls = "";
        lastTs = 0;
    }
}
