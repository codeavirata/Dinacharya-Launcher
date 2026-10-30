package app.dinacharya.wallpaper;

/** What the free version keeps and what Pro adds for the reports. Uses no Android classes. */
public final class Limits {
    /** Saved days a free user can open: none. Today's report is the only one that is free. */
    public static final int FREE_REPORT_DAYS = 0;

    private Limits() {
    }

    /** How many of the {@code available} saved days this person can open. */
    public static int reportDays(boolean pro, int available) {
        return Math.max(0, pro ? available : Math.min(FREE_REPORT_DAYS, available));
    }

    /** The week view and its suggestions are part of Pro. */
    public static boolean weekAllowed(boolean pro) {
        return pro;
    }

    /** The deeper analysis (a day's slot by slot analysis, the habit cards, and phone use in slots) is part of Pro. */
    public static boolean analysisAllowed(boolean pro) {
        return pro;
    }
}
