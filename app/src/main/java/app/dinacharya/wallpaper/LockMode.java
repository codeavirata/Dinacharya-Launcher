package app.dinacharya.wallpaper;

/**
 * The way a group of apps is locked, as the group editor offers it: by time, by activity, or by both together. The saved group keeps the
 * same few values as always (the days and hours, the activity types, and the "both" flag); this class only says which way those values
 * mean, and which values to store for the way the person chose. Uses no Android classes.
 */
public final class LockMode {
    /** Locked at the hours and days set, whatever the slot going on is. */
    public static final int TIME = 0;
    /** Locked while a slot of the chosen activity types is going on, at any hour. */
    public static final int ACTIVITY = 1;
    /** Locked only when a slot of the chosen types is going on inside the hours and days set. */
    public static final int BOTH = 2;
    /**
     * Locked in the hours set, and also whenever a slot of the chosen types is going on. Groups made before the editor had a choice can be
     * this way, so the editor shows it only for them.
     */
    public static final int EITHER = 3;

    private LockMode() {
    }

    /** Which way a saved group locks: {@code days} is its days mask, {@code types} how many activity types it has, {@code both} its flag. */
    public static int of(int days, int types, boolean both) {
        if (types <= 0) return TIME;
        if (days == 0) return ACTIVITY;
        return both ? BOTH : EITHER;
    }

    /** The days to store: an activity lock has no days. */
    public static int daysToSave(int mode, int days) {
        return mode == ACTIVITY ? 0 : days;
    }

    /** True when the activity types are stored (a lock by time keeps none). */
    public static boolean keepsTypes(int mode) {
        return mode != TIME;
    }

    /** The value of the "both" flag to store. */
    public static boolean bothToSave(int mode) {
        return mode == BOTH;
    }

    public static final int OK = 0, NEED_TYPES = 1, NEED_DAYS = 2;

    /** What is still missing before a group locked this way can be saved: activity types, or days. */
    public static int missing(int mode, int days, int types) {
        if (mode != TIME && types <= 0) return NEED_TYPES;
        if (mode == BOTH && days == 0) return NEED_DAYS;
        return OK;
    }

    /** The name of the way, as the chip says it. */
    public static String name(int mode) {
        switch (mode) {
            case ACTIVITY:
                return "Activity";
            case BOTH:
                return "Time + activity";
            case EITHER:
                return "Time or activity";
            default:
                return "Time";
        }
    }

    /** One line saying what the way means. */
    public static String about(int mode) {
        switch (mode) {
            case ACTIVITY:
                return "Locked whenever a slot of the types you pick is going on, at any hour.";
            case BOTH:
                return "Locked only when a slot of the types you pick is going on inside the hours you set.";
            case EITHER:
                return "Locked in the hours you set, and also whenever a slot of the types you pick is going on.";
            default:
                return "Locked at the hours you set, whatever you are doing.";
        }
    }
}
