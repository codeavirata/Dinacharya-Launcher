package app.dinacharya.wallpaper;

/**
 * Notices when Dinacharya WAS the phone's home app and is not any more (an update that resets the choice, a reinstall,
 * or another launcher picked), so the dashboard can offer a one-tap way back. The phone remembers whether Dinacharya was
 * home the last time it was seen ({@code wasHome}) and, after "Not now", when to ask again. Uses no Android classes.
 */
public final class HomeWatch {
    /** How long "Not now" stays quiet. */
    public static final long SNOOZE_MS = 24L * 3600000L;

    private HomeWatch() {
    }

    /**
     * True when the offer should be shown: it was the home app before, it is not now, and "Not now" has run out.
     * A person who never used it as their home app is never asked.
     */
    public static boolean shouldAsk(boolean wasHome, boolean homeNow, long snoozeUntilMs, long nowMs) {
        return wasHome && !homeNow && nowMs >= snoozeUntilMs;
    }

    /** The value to keep for {@code wasHome} after looking: it becomes true whenever Dinacharya is home, otherwise stays. */
    public static boolean remember(boolean wasHome, boolean homeNow) {
        return wasHome || homeNow;
    }

    /** When to ask again after "Not now" at {@code nowMs}. */
    public static long snoozeFrom(long nowMs) {
        return nowMs + SNOOZE_MS;
    }
}
