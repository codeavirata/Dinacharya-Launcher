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
     * A small "Day off" chip for the Home and Routine tabs (not a full-size button). It asks "Take the rest of today off?" and goes on as
     * the other ways to a day off do. {@code changed} runs once the day is off (and after the plan question).
     */
    static android.widget.TextView chip(final Activity a, final Runnable changed) {
        android.widget.TextView t = Ui.label(a, "Day off", 14, Ui.TEAL_DARK, true);
        t.setGravity(android.view.Gravity.CENTER);
        t.setMinHeight(Ui.dp(a, 34));
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6));
        t.setBackground(Ui.pressable(a, Ui.SURFACE, Ui.TEAL, 17, 0x300E7C7B));
        t.setContentDescription(Lang.tr("Take the rest of today off"));
        t.setOnClickListener(v -> {
            boolean off = false;
            try {
                off = DayOff.pending(Store.load(a), Store.alarmState(a), System.currentTimeMillis());
            } catch (RuntimeException ignored) {
                // treat it as not off: the question below is harmless
            }
            if (off) Lang.toast(a, "Today is already a day off", android.widget.Toast.LENGTH_SHORT).show();
            else confirm(a, changed, changed);
        });
        return t;
    }

    /**
     * "Take the rest of today off?" Then, when it was done, "Plan your day now, later or leave it?".
     *
     * @param taken    runs as soon as the day is off (a screen behind can redraw; an alarm that is ringing can be silenced)
     * @param answered runs after the plan question was answered
     */
    static void confirm(final Activity a, final Runnable taken, final Runnable answered) {
        final Glass.Sheet sheet = new Glass.Sheet(a, "Take the rest of today off?",
                "No alarm will ring for the rest of today. The slot going on ends now, and the slots still to come count as taken off. "
                        + "Tomorrow starts as usual.");
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.addView(Glass.main(a, "Take it off", v -> {
            sheet.dismiss();
            if (!SlotActions.takeDayOff(a, 0L)) {
                Lang.toast(a, "The day could not be taken off", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (taken != null) taken.run();
            askPlan(a, answered);
        }));
        box.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 6));
        sheet.body.addView(box);
        sheet.show();
    }

    /** "Plan your day?" with the three answers. {@code done} runs after the person has answered (and the planner, if wanted, was opened). */
    static void askPlan(final Activity a, final Runnable done) {
        final Glass.Sheet sheet = new Glass.Sheet(a, "Plan your day?",
                "Your day is off, so no alarm will ring until you plan it again. Plan it now, later, or leave it?").cancelable(false);
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.addView(Glass.main(a, "Plan now", v -> {
            sheet.dismiss();
            openPlanner(a);
            if (done != null) done.run();
        }));
        box.addView(Glass.glass(a, "Later", v -> {
            sheet.dismiss();
            askLater(a, done);
        }), Glass.wide(a, 10));
        box.addView(Glass.quiet(a, "Leave it", v -> {
            sheet.dismiss();
            Lang.toast(a, "Enjoy your day off. Your alarms are back tomorrow.", android.widget.Toast.LENGTH_LONG).show();
            if (done != null) done.run();
        }), Glass.wide(a, 6));
        sheet.body.addView(box);
        sheet.show();
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
        final Glass.Sheet sheet = new Glass.Sheet(a, "Ask me again", null).cancelable(false);
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (int i = 0; i < labels.size(); i++) {
            final int which = i;
            box.addView(Glass.glass(a, labels.get(i), v -> {
                sheet.dismiss();
                long at = times.get(which);
                SlotActions.planLater(a, at);
                ZonedDateTime z = Instant.ofEpochMilli(at).atZone(Store.zone());
                Lang.toast(a, "I will ask again at " + Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24), android.widget.Toast.LENGTH_LONG).show();
                if (done != null) done.run();
            }), Glass.wide(a, i == 0 ? 0 : 10));
        }
        box.addView(Glass.quiet(a, "Back", v -> {
            sheet.dismiss();
            askPlan(a, done);
        }), Glass.wide(a, 6));
        sheet.body.addView(box);
        sheet.show();
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
