package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * "Finish early": the slot going on is done before its time. Asks what comes next: start the next slot now, take a break first, start it
 * after a while, or take the rest of the day off. The next slot keeps its planned end, so it gets the time that was left.
 */
final class EarlyDialog {
    private EarlyDialog() {
    }

    private static String clock(long ms, boolean h24) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(ms).atZone(Store.zone());
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
    }

    static void show(final MainActivity a, final EarlyFinish.Offer o, final Runnable changed) {
        if (o.kind == EarlyFinish.SLEEP) {
            showSleep(a, o, changed);
            return;
        }
        if (o.kind == EarlyFinish.WAKE) {
            showWake(a, o, changed);
            return;
        }
        final boolean h24 = a.routine().fmt24;
        final boolean canRest = o.canWait() && o.maxRestMin() >= SlotPlay.STEP;
        final String cur = o.titleCur.isEmpty() ? "this slot" : o.titleCur;
        final String next = o.titleNext.isEmpty() ? "the next slot" : o.titleNext;
        ScrollView sv = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), a.dp(4));
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView msg = Ui.label(a, "“" + cur + "” was to end at " + clock(o.plannedEndMs, h24) + ". Finish it now, and “" + next
                + "” starts early. It still ends at " + clock(o.nextEndMs, h24) + ", so it gets the time that was left.", 14, Ui.MUTED, false);
        msg.setLineSpacing(a.dp(2), 1f);
        box.addView(msg, Ui.wide(a, 0));
        final AlertDialog[] dlg = new AlertDialog[1];
        box.addView(Ui.button(a, "Start “" + next + "” now", true, v -> {
            dlg[0].dismiss();
            run(a, o, SlotActions.EARLY_NOW, 0, changed);
        }), Ui.wide(a, 14));
        if (canRest) {
            box.addView(Ui.button(a, "Take a break first", false, v -> {
                dlg[0].dismiss();
                askMinutes(a, o, SlotActions.EARLY_BREAK, changed);
            }), Ui.wide(a, 8));
            box.addView(Ui.button(a, "Start “" + next + "” after a while", false, v -> {
                dlg[0].dismiss();
                askMinutes(a, o, SlotActions.EARLY_AFTER, changed);
            }), Ui.wide(a, 8));
        }
        box.addView(Ui.button(a, "Take the rest of today off", false, v -> {
            dlg[0].dismiss();
            DayOffDialog.confirm(a, changed, changed);
        }), Ui.wide(a, 8));
        dlg[0] = new DBuilder(a).setTitle(o.curIsSleep ? "Wake up early?" : "Finish “" + cur + "” early?").setView(sv).setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    /** "Early sleep": the slot before sleep ends here. The bedtime question opens, and sleep begins when it is answered. */
    private static void showSleep(final MainActivity a, final EarlyFinish.Offer o, final Runnable changed) {
        final boolean h24 = a.routine().fmt24;
        final String cur = o.titleCur.isEmpty() ? "this slot" : o.titleCur;
        ScrollView sv = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), a.dp(4));
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView msg = Ui.label(a, "Sleep was to begin at " + clock(o.plannedEndMs, h24) + ". If you go to sleep now, “" + cur
                + "” ends here and your wake-up alarm stays as planned.", 14, Ui.MUTED, false);
        msg.setLineSpacing(a.dp(2), 1f);
        box.addView(msg, Ui.wide(a, 0));
        final AlertDialog[] dlg = new AlertDialog[1];
        box.addView(Ui.button(a, "I am going to sleep", true, v -> {
            dlg[0].dismiss();
            try {
                a.startActivity(AlarmActivity.earlySleepIntent(a, o.day, o.srcNext, o.titleNext, o.plannedEndMs, o.nextEndMs));
            } catch (RuntimeException ex) {
                a.toastText("That cannot be done now");
            }
            if (changed != null) changed.run();
        }), Ui.wide(a, 14));
        dlg[0] = new DBuilder(a).setTitle("Go to sleep early?").setView(sv).setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    /** "Early wake": the person is up before the alarm, and the next day starts now. */
    private static void showWake(final MainActivity a, final EarlyFinish.Offer o, final Runnable changed) {
        final boolean h24 = a.routine().fmt24;
        final String next = o.titleNext.isEmpty() ? "the first slot" : o.titleNext;
        ScrollView sv = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), a.dp(4));
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView msg = Ui.label(a, "Your day was to start at " + clock(o.plannedEndMs, h24) + ". Start it now, and “" + next
                + "” begins early. It still ends at " + clock(o.nextEndMs, h24) + ", so it gets the time that was left.", 14, Ui.MUTED, false);
        msg.setLineSpacing(a.dp(2), 1f);
        box.addView(msg, Ui.wide(a, 0));
        final AlertDialog[] dlg = new AlertDialog[1];
        box.addView(Ui.button(a, "Start my day now", true, v -> {
            dlg[0].dismiss();
            if (SlotActions.earlyWake(a) < 0) a.toastText("That cannot be done now");
            else a.toastText("Your day has started.");
            a.previewChanged();
            if (changed != null) changed.run();
        }), Ui.wide(a, 14));
        dlg[0] = new DBuilder(a).setTitle("Wake up early?").setView(sv).setNegativeButton("Cancel", null).create();
        dlg[0].show();
    }

    /** How long: − and + in steps of five minutes, never so long that it reaches the time the next slot was to begin. */
    private static void askMinutes(final MainActivity a, final EarlyFinish.Offer o, final int how, final Runnable changed) {
        final boolean h24 = a.routine().fmt24;
        final boolean rest = how == SlotActions.EARLY_BREAK;
        final String next = o.titleNext.isEmpty() ? "the next slot" : o.titleNext;
        final MinuteStepper[] st = new MinuteStepper[1];
        final TextView caption = Ui.label(a, "", 13, Ui.MUTED, false);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        final Runnable draw = () -> {
            long at = Math.min(System.currentTimeMillis() + st[0].minutes() * 60000L, o.plannedEndMs - 60000L);
            caption.setText(rest ? "Back at " + clock(at, h24) + ", and “" + next + "” goes on." : "“" + next + "” starts at " + clock(at, h24) + ".");
        };
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        st[0] = new MinuteStepper(a, 10, o.maxRestMin(), m -> draw.run());
        box.addView(st[0].view, Ui.wide(a, 4));
        box.addView(caption, Ui.wide(a, 6));
        draw.run();
        new DBuilder(a)
                .setTitle(rest ? "Take a break" : "Start later")
                .setView(box)
                .setPositiveButton(rest ? "Start the break" : "Start “" + next + "” later", (d, w) -> run(a, o, how, st[0].minutes(), changed))
                .setNegativeButton("Back", (d, w) -> show(a, o, changed))
                .show();
    }

    private static void run(MainActivity a, EarlyFinish.Offer o, int how, int minutes, Runnable changed) {
        long begin = SlotActions.finishEarly(a, how, minutes);
        if (begin < 0) {
            a.toastText("That cannot be done now");
        } else {
            boolean h24 = a.routine().fmt24;
            String next = o.titleNext.isEmpty() ? "The next slot" : o.titleNext;
            if (how == SlotActions.EARLY_NOW) a.toastText("“" + next + "” has started");
            else if (how == SlotActions.EARLY_BREAK) a.toastText("Enjoy your break. “" + next + "” goes on at " + clock(begin, h24));
            else a.toastText("“" + next + "” starts at " + clock(begin, h24));
        }
        a.previewChanged();
        if (changed != null) changed.run();
    }
}
