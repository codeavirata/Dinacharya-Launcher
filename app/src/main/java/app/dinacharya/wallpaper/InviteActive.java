package app.dinacharya.wallpaper;

/**
 * Whether a friend who used an invite code has really used the app, which is what releases the inviter's reward. The friend's phone keeps
 * a short record: the day the code was used, the days the app was opened since (counted once each) and whether the server was told.
 * Two different days of use are needed. Uses no Android classes.
 */
public final class InviteActive {
    /** Days of use that count as "really used". */
    public static final int DAYS_NEEDED = 2;

    private InviteActive() {
    }

    /** The record just after a code was used on {@code today} (an epoch day). That day counts as the first day of use. */
    public static String redeemed(long today) {
        return today + "," + today + ",1,0";
    }

    private static long[] read(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split(",");
        if (p.length != 4) return null;
        try {
            return new long[]{Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]), Long.parseLong(p[3])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The app was opened on {@code today}. A record that is empty or unreadable stays as it is. */
    public static String used(String state, long today) {
        long[] r = read(state);
        if (r == null || r[3] == 1 || today <= r[1]) return state == null ? "" : state;   // not redeemed, already told, or the same day (or a clock that went back)
        long days = Math.min(r[2] + 1, 1000);
        return r[0] + "," + today + "," + days + ",0";
    }

    /** True when the server should be told now: a code was used, the app was used on enough days, and it has not been told yet. */
    public static boolean due(String state) {
        long[] r = read(state);
        return r != null && r[3] == 0 && r[2] >= DAYS_NEEDED;
    }

    /** The record after the server was told. */
    public static String told(String state) {
        long[] r = read(state);
        return r == null ? (state == null ? "" : state) : r[0] + "," + r[1] + "," + r[2] + ",1";
    }
}
