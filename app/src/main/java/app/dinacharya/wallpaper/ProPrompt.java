package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The "this is part of Pro" message shared by the home screen, its settings and the dashboard. Each time it names the
 * item (a {@link DayPass} item: one single thing Pro adds), so that, besides "See Pro", it can offer to open it for a while in
 * return for watching short videos: 1 video opens just that item for a day, 2 videos for 3 days, 3 videos the whole group it
 * belongs to (all the pauses, say) for a day. The person picks; nothing plays until they do.
 */
final class ProPrompt {
    private ProPrompt() {
    }

    /** Opens the Pro screen on top of the current one. */
    static void open(Activity a) {
        try {
            a.startActivity(new Intent(a, ProActivity.class));
        } catch (RuntimeException e) {
            CrashLog.note(a, "pro screen", e);
        }
    }

    /**
     * Says that {@code what} is part of Pro, with "See Pro", "Not now" and, when videos are on, the ways to watch videos for it.
     * {@code unlocked} (may be null) runs after the videos have opened {@code item}, so the caller can carry on or redraw.
     */
    static void show(final Activity a, final int item, String what, int dialogTheme, final Runnable unlocked) {
        try {
            String message = what + " is part of Pro, a one-time purchase that also removes the ads.";
            if (DayPass.valid(item) && VideoPass.offered(a)) {
                // Something else was just opened with a video and this needs one more thing: say so, so it is clear why this appears.
                int just = Pro.get(a).recentlyOpened();
                if (just != 0 && !DayPass.has(just, item)) {
                    String opened = DayPass.maskName(just);
                    message = Character.toUpperCase(opened.charAt(0)) + opened.substring(1) + " is open now. To finish, you also need: " + what
                            + ". It has its own video, or it is part of Pro, a one-time purchase that also removes the ads.";
                }
                choose(a, item, "Part of Pro", message, dialogTheme, unlocked);
                return;
            }
            new DBuilder(a, dialogTheme)
                    .setTitle("Part of Pro")
                    .setMessage(message)
                    .setPositiveButton("See Pro", (d, w) -> open(a))
                    .setNegativeButton("Not now", null)
                    .show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "pro prompt", e);
        }
    }

    /**
     * The choice of how many videos to watch for {@code item}: 1 (the item, a day), 2 (the item, 3 days) or 3 (the whole group,
     * a day), with "See Pro" and "Not now" beside them. The third is only there where the item has a group.
     */
    static void choose(final Activity a, final int item, String title, String intro, int dialogTheme, final Runnable unlocked) {
        try {
            final AlertDialog.Builder b = new DBuilder(a, dialogTheme);
            final Context cx = b.getContext();
            final AlertDialog[] dlg = new AlertDialog[1];
            LinearLayout box = new LinearLayout(cx);
            box.setOrientation(LinearLayout.VERTICAL);
            int side = Ui.dp(cx, 22);
            box.setPadding(side, Ui.dp(cx, 6), side, 0);
            TextView msg = new TextView(cx);
            msg.setText(intro + "\n\nOr watch short videos to use it free. You choose how many:");
            msg.setTextSize(15);
            msg.setLineSpacing(Ui.dp(cx, 2), 1f);
            box.addView(msg);
            for (int tier = DayPass.TIER_DAY; tier <= DayPass.TIER_GROUP; tier++) {
                if (!DayPass.tierOffered(tier, item)) continue;
                final int t = tier;
                Button o = new Button(cx);
                o.setAllCaps(false);
                o.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                o.setText(DayPass.tierLabel(tier, item));
                o.setOnClickListener(v -> {
                    if (dlg[0] != null) dlg[0].dismiss();
                    VideoPass.watch(a, item, t, unlocked);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(cx, 8);
                box.addView(o, lp);
            }
            TextView note = new TextView(cx);
            String back = PassReset.needsReset(item) ? " When the time is up, what you set up with it goes back to the free version." : "";
            note.setText("Only the videos you watch to the end count, and stopping early still keeps what you earned. "
                    + "Each thing you open has its own video and its own time." + back);
            note.setTextSize(12);
            note.setAlpha(0.75f);
            note.setLineSpacing(Ui.dp(cx, 2), 1f);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            np.topMargin = Ui.dp(cx, 12);
            box.addView(note, np);
            ScrollView scroll = new ScrollView(cx);
            scroll.addView(box);
            b.setTitle(title).setView(scroll)
                    .setPositiveButton("See Pro", (d, w) -> open(a))
                    .setNegativeButton("Not now", null);
            dlg[0] = b.create();
            dlg[0].show();
            VideoPass.warm(a);   // the first video is fetched while the person reads
        } catch (RuntimeException e) {
            CrashLog.note(a, "pro choice", e);
        }
    }

    /**
     * Adds a "Watch videos: open it free" button under a locked view, when videos are on. Nothing is added
     * when they are not (no ads in this build, or Pro owned).
     */
    static void addVideoButton(final Activity a, LinearLayout parent, final int item, float topDp, final Runnable unlocked) {
        if (!DayPass.valid(item) || !VideoPass.offered(a)) return;
        parent.addView(Ui.button(a, "Watch videos: open " + DayPass.name(item) + " free", false,
                v -> choose(a, item, "Open it free", DayPass.title(item) + " is part of Pro.", 0, unlocked)), Ui.wide(a, topDp));
        VideoPass.warm(a);
    }

    /**
     * Under a card of Pro things: for each of {@code items}, while a pass for it runs, says that it is open and until when (with, for a
     * day-long pass, a button to keep it for three days with one more video); otherwise, when videos are on, a "Watch videos" button
     * that opens only that item (or, when three videos are watched, its group). Every item has its own pass, so one being open never
     * hides or opens another. Nothing is added when videos are off or Pro is owned.
     */
    static void addTryList(final Activity a, LinearLayout parent, int[] items, float topDp, final Runnable unlocked) {
        try {
            if (!VideoPass.offered(a)) return;
            Pro p = Pro.get(a);
            int open = p.open();
            for (int it : items) {
                if (DayPass.has(open, it)) {
                    parent.addView(Ui.label(a, runningLine(a, it), 13, Ui.MUTED, false), Ui.wide(a, topDp));
                    addExtendButton(a, parent, it, 6, unlocked);
                } else {
                    addVideoButton(a, parent, it, topDp, unlocked);
                }
                topDp = 6;
            }
        } catch (RuntimeException e) {
            CrashLog.note(a, "try list", e);
        }
    }

    /** "Strict pauses: open until tomorrow 4:30 PM." for the pass that opens {@code item}. */
    static String runningLine(Context c, int item) {
        long until = Pro.get(c).passUntil(item);
        if (until <= 0) return "";
        return DayPass.shortName(item) + ": open until "
                + LockRules.untilText(until, System.currentTimeMillis(), Store.zone(), android.text.format.DateFormat.is24HourFormat(c)) + ".";
    }

    /** "Keep it for 3 days: watch 1 more video", only while the pass on {@code item} is a day long and for that item alone. */
    static void addExtendButton(final Activity a, LinearLayout parent, final int item, float topDp, final Runnable unlocked) {
        try {
            if (!VideoPass.offered(a) || !Pro.get(a).canExtendPass(item)) return;
            parent.addView(Ui.button(a, "Keep " + DayPass.name(item) + " for 3 days: watch 1 more video", false,
                    v -> VideoPass.extend(a, item, unlocked)), Ui.wide(a, topDp));
            VideoPass.warm(a);
        } catch (RuntimeException e) {
            CrashLog.note(a, "extend button", e);
        }
    }
}
