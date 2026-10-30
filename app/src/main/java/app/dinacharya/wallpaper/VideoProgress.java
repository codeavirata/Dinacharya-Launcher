package app.dinacharya.wallpaper;

/**
 * How far along the three videos are. Each video watched to the end counts at once, so stopping after one or two keeps them; they count only
 * for the day they were watched (the next morning the count starts again). The third video opens everything in Pro for a day (see
 * {@link DayPass}), and the count starts again. Uses no Android classes.
 */
public final class VideoProgress {
    /** How many videos earn the pass. */
    public static final int NEED = DayPass.VIDEOS;

    private VideoProgress() {
    }

    /** The videos counted towards the next pass: those kept for {@code today}, none for any other day. */
    public static int counted(String savedDay, int savedCount, String today) {
        if (savedDay == null || today == null || !savedDay.equals(today)) return 0;
        return Math.max(0, Math.min(NEED - 1, savedCount));
    }

    /** How many more videos the next pass needs. */
    public static int remaining(int counted) {
        return Math.max(1, NEED - Math.max(0, counted));
    }

    /** What the count is after one more video was watched to the end; {@link #NEED} means the pass is earned. */
    public static int after(int counted) {
        return Math.min(NEED, Math.max(0, counted) + 1);
    }

    /** True when that count earns the pass. */
    public static boolean earned(int counted) {
        return counted >= NEED;
    }

    /** What the button says: "Watch 3 videos" at first, then how many are left. */
    public static String buttonText(int counted) {
        int left = remaining(counted);
        return left == NEED ? "Watch " + NEED + " videos: everything in Pro free for a day"
                : "Watch " + left + (left == 1 ? " more video" : " more videos") + ": everything in Pro free for a day";
    }

    /** "1 of 3 videos watched today", or empty when none has been. */
    public static String progressText(int counted) {
        int c = Math.max(0, Math.min(NEED - 1, counted));
        return c == 0 ? "" : c + " of " + NEED + " videos watched today";
    }
}
