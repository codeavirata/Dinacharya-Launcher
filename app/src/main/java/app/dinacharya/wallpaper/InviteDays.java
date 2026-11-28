package app.dinacharya.wallpaper;

/**
 * The free Pro days an invite gives: the rules for adding days to the end date kept on the phone. New days start from the later of
 * now and the date already kept (so days add up and never overlap), and the end date is never more than {@link #MAX_DAYS} ahead
 * (a record changed by hand does not give Pro for good). Uses no Android classes.
 */
public final class InviteDays {
    public static final long DAY_MS = 24L * 3600000L;
    /** The most that can be ahead at once: 46 days from inviting plus 7 from using a code, with room to spare. */
    public static final int MAX_DAYS = 60;

    private InviteDays() {
    }

    /** The new end date after adding {@code days} to {@code until} (0 when none yet) at {@code now}. A bad number of days adds nothing. */
    public static long add(long until, int days, long now) {
        long cap = now + MAX_DAYS * DAY_MS;
        long base = Math.max(now, Math.min(until, cap));
        if (days <= 0 || days > MAX_DAYS) return until > now ? Math.min(until, cap) : 0;
        return Math.min(base + days * DAY_MS, cap);
    }

    /** True while the free days are running. A date more than {@link #MAX_DAYS} ahead is not trusted. */
    public static boolean active(long until, long now) {
        return until > now && until - now <= MAX_DAYS * DAY_MS;
    }

    /** Whole days left, rounded up (1 on the last day), or 0 when nothing is running. */
    public static int left(long until, long now) {
        if (!active(until, now)) return 0;
        return (int) ((until - now + DAY_MS - 1) / DAY_MS);
    }
}
