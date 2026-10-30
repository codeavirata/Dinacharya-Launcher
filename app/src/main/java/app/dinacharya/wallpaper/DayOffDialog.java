package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Intent;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Taking the rest of the day off, and the question that follows it: plan the day now, later, or leave it. Shared by the Home screen, the
 * "finish early" question and the start-of-day alarm.
 */
final class DayOffDialog {
    private DayOffDialog() {
    }

    /**
     * "Take the rest of today off?" Then, when it was done, "Plan your day now, later or leave it?".
     *
     * @param taken    runs as soon as the day is off (a screen behind can redraw; an alarm that is ringing can be silenced)
     * @param answered runs after the plan question was answered
     */
    static void confirm(final Activity a, final Runnable taken, final Runnable answered) {
        new DBuilder(a)
                .setTitle("Take the rest of today off?")
                .setMessage("No alarm will ring for the rest of today. The slot going on ends now, and the slots still to come count as "
                        + "taken off. Tomorrow starts as usual.")
                .setPositiveButton("Take it off", (d, w) -> {
                    if (!SlotActions.takeDayOff(a, 0L)) {
                        Lang.toast(a, "The day could not be taken off", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (taken != null) taken.run();
                    askPlan(a, answered);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** "Plan your day?" with the three answers. {@code done} runs after the person has answered (and the planner, if wanted, was opened). */
    static void askPlan(final Activity a, final Runnable done) {
        new DBuilder(a)
                .setTitle("Plan your day?")
                .setMessage("Your day is off, so no alarm will ring until you plan it again. Plan it now, later, or leave it?")
                .setPositiveButton("Plan now", (d, w) -> {
                    openPlanner(a);
                    if (done != null) done.run();
                })
                .setNeutralButton("Later", (d, w) -> askLater(a, done))
                .setNegativeButton("Leave it", (d, w) -> {
                    Lang.toast(a, "Enjoy your day off. Your alarms are back tomorrow.", android.widget.Toast.LENGTH_LONG).show();
                    if (done != null) done.run();
                })
                .setCancelable(false)
                .show();
    }

    /** When to ask again. */
    static void askLater(final Activity a, final Runnable done) {
        final long now = System.currentTimeMillis();
        final boolean h24 = Store.load(a).fmt24;
        final List<String> labels = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        int[] hours = {1, 2, 4};
        for (int h : hours) {
            labels.add(h == 1 ? "In 1 hour" : "In " + h + " hours");
            times.add(now + h * 3600000L);
        }
        ZonedDateTime evening = Instant.ofEpochMilli(now).atZone(Store.zone()).toLocalDate().atTime(18, 0).atZone(Store.zone());
        if (evening.toInstant().toEpochMilli() - now >= 3600000L * 3) {
            labels.add("This evening, " + Routine.formatTime(18 * 60, h24));
            times.add(evening.toInstant().toEpochMilli());
        }
        new DBuilder(a)
                .setTitle("Ask me again")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    long at = times.get(which);
                    SlotActions.planLater(a, at);
                    ZonedDateTime z = Instant.ofEpochMilli(at).atZone(Store.zone());
                    Lang.toast(a, "I will ask again at " + Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24), android.widget.Toast.LENGTH_LONG).show();
                    if (done != null) done.run();
                })
                .setNegativeButton("Cancel", (d, w) -> askPlan(a, done))
                .setCancelable(false)
                .show();
    }

    /** Opens the Routine page so the day can be planned. */
    static void openPlanner(Activity a) {
        if (a instanceof MainActivity) {
            ((MainActivity) a).openPlan();
            return;
        }
        Intent i = new Intent(a, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "plan")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        a.startActivity(i);
    }
}
