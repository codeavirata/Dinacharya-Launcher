package app.dinacharya.wallpaper;

/**
 * The rules for noticing that Android stopped the app and with it the alarms: when an alarm that was set never reached the app, when
 * the alarm Android holds has gone, and what a way of closing the app means for the alarms. Uses no Android classes, so it can be tested
 * on a computer. {@link AlarmGuard} applies it on the phone.
 */
public final class AlarmWatch {
    /** How late an alarm may be before it counts as one that never came. */
    public static final long GRACE_MS = 90000L;
    /** A missed alarm older than this is remembered but not announced: it would only be noise. */
    public static final long ANNOUNCE_MS = 24L * 3600000L;

    // The codes Android gives for how a process ended (ApplicationExitInfo.REASON_*), copied so this class needs no Android.
    public static final int EXIT_UNKNOWN = 0, EXIT_SELF = 1, EXIT_SIGNALED = 2, EXIT_LOW_MEMORY = 3, EXIT_CRASH = 4,
            EXIT_CRASH_NATIVE = 5, EXIT_ANR = 6, EXIT_INIT_FAILURE = 7, EXIT_PERMISSION_CHANGE = 8, EXIT_EXCESSIVE = 9,
            EXIT_USER_REQUESTED = 10, EXIT_USER_STOPPED = 11, EXIT_DEPENDENCY_DIED = 12, EXIT_OTHER = 13, EXIT_FREEZER = 14,
            EXIT_PACKAGE_STATE_CHANGE = 15, EXIT_PACKAGE_UPDATED = 16;

    private AlarmWatch() {
    }

    /**
     * True when the alarm that was set for {@code armedFor} never reached the app: its time is well past and nothing newer than it was
     * handed over. {@code firedDue} is the time of the last alarm that did arrive.
     */
    public static boolean missed(long armedFor, long firedDue, long nowMs) {
        return armedFor > 0 && armedFor < nowMs - GRACE_MS && firedDue < armedFor;
    }

    /** True when a missed alarm is recent enough to be worth a notification. */
    public static boolean worthAnnouncing(long missedFor, long nowMs) {
        return missedFor > 0 && nowMs - missedFor <= ANNOUNCE_MS;
    }

    /**
     * True when an alarm was set and is still in the future, yet Android no longer holds it: the thing that carried it is gone, which
     * is what closing the app from App info does (the app is stopped and every alarm and pending action of it is dropped).
     */
    public static boolean lost(long armedFor, boolean heldByAndroid, long nowMs) {
        return armedFor > nowMs && !heldByAndroid;
    }

    /** True when this way of ending the app takes the app's alarms away with it. A crash or running out of memory does not. */
    public static boolean clearsAlarms(int reason) {
        return reason == EXIT_USER_REQUESTED || reason == EXIT_USER_STOPPED || reason == EXIT_PERMISSION_CHANGE
                || reason == EXIT_PACKAGE_STATE_CHANGE || reason == EXIT_PACKAGE_UPDATED;
    }

    /**
     * True when this way of ending the app is worth telling the person about: one that takes the alarms away, or a crash. The app
     * leaving by itself, or being closed in the background to free memory, happens all day and is not.
     */
    public static boolean worthTelling(int reason) {
        return reason == EXIT_USER_REQUESTED || reason == EXIT_USER_STOPPED || reason == EXIT_PERMISSION_CHANGE
                || reason == EXIT_CRASH || reason == EXIT_CRASH_NATIVE || reason == EXIT_ANR
                || reason == EXIT_INIT_FAILURE || reason == EXIT_EXCESSIVE;
    }

    /** What happened to the app, in plain words ("it was closed from App info"). */
    public static String what(int reason) {
        switch (reason) {
            case EXIT_USER_REQUESTED: return "It was closed from App info (Force stop).";
            case EXIT_USER_STOPPED: return "Android stopped it when the user was switched.";
            case EXIT_CRASH:
            case EXIT_CRASH_NATIVE: return "It crashed.";
            case EXIT_ANR: return "It stopped answering and Android closed it.";
            case EXIT_LOW_MEMORY: return "Android closed it to free memory.";
            case EXIT_PERMISSION_CHANGE: return "Android closed it because a permission was changed.";
            case EXIT_PACKAGE_UPDATED: return "It was closed to be updated.";
            case EXIT_PACKAGE_STATE_CHANGE: return "Android closed it because it was switched off or on.";
            case EXIT_EXCESSIVE: return "Android closed it for using too much of the phone.";
            case EXIT_INIT_FAILURE: return "It could not start.";
            case EXIT_DEPENDENCY_DIED: return "Android closed it because something it needs stopped.";
            case EXIT_SIGNALED: return "Android ended it.";
            default: return "It was closed.";
        }
    }

    /** What that means for the alarms and the wallpaper, in plain words. */
    public static String consequence(int reason) {
        if (clearsAlarms(reason)) {
            return "Android clears every alarm of an app when it is closed this way, so the alarms and the live wallpaper were gone until you came back. They are in place again now.";
        }
        return "Android keeps the alarms of an app that stops this way. They have been checked and are in place.";
    }
}
