package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * What "it will take more time" costs, in plain words: every minute you stay up before bed is a minute less of sleep before
 * the alarm, which stays where it is. Uses no Android classes.
 */
public final class SnoozeCost {
    private SnoozeCost() {
    }

    /** Minutes already snoozed in the schedule day {@code day}. */
    public static int snoozedBefore(List<Ev> events, String day) {
        int sum = 0;
        if (events == null || day == null) return 0;
        for (Ev e : events) {
            if (!Ev.SNOOZE.equals(e.type) || !day.equals(e.day)) continue;
            long m = e.aLong(0);
            if (m > 0 && m < 1000) sum += (int) m;
        }
        return sum;
    }

    /** Minutes of sleep left between {@code atMs} and the alarm at {@code wakeMs} (never below 0). */
    public static int sleepLeft(long atMs, long wakeMs) {
        return (int) Math.max(0, (wakeMs - atMs) / 60000L);
    }

    /**
     * The lines to show under the snooze choice.
     *
     * @param snoozeMin     the length being considered
     * @param sleepNowMin   sleep you would get if you went to sleep right now
     * @param plannedMin    the sleep your plan asks for (0 if unknown)
     * @param snoozedBefore minutes already snoozed today
     */
    public static List<String> lines(int snoozeMin, int sleepNowMin, int plannedMin, int snoozedBefore) {
        List<String> out = new ArrayList<>();
        int m = Math.max(0, snoozeMin);
        int now = Math.max(0, sleepNowMin);
        int after = Math.max(0, now - m);
        if (m == 0) return out;
        if (m >= now) {
            out.add("That is past your alarm: there would be no sleep left.");
        } else {
            out.add("This costs you " + Routine.formatDuration(m) + " of sleep.");
            out.add("Sleep now: " + Routine.formatDuration(now) + ". After " + Routine.formatDuration(m) + ": "
                    + Routine.formatDuration(after) + ".");
        }
        if (plannedMin > 0 && after < plannedMin && m < now) {
            out.add("That is " + Routine.formatDuration(plannedMin - after) + " short of the "
                    + Routine.formatDuration(plannedMin) + " you planned.");
        }
        if (after > 0 && after < 360) out.add("Under 6 hours of sleep makes tomorrow harder.");
        if (snoozedBefore > 0) {
            out.add("Already snoozed " + Routine.formatDuration(snoozedBefore) + " today. With this: "
                    + Routine.formatDuration(snoozedBefore + m) + ".");
        }
        return out;
    }
}
