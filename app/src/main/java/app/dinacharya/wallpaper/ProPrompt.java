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
 * The "this is part of Pro" message shared by the home screen, its settings and the dashboard. Besides "See Pro", it offers the one
 * way to use Pro without paying: watch three short videos and everything in Pro is open for a day (see {@link VideoPass}). The person
 * picks; nothing plays until they do.
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
     * Says that {@code what} is part of Pro, with "See Pro", "Not now" and, when videos are on, the way to watch videos for a day of
     * Pro. {@code item} is the thing asked for (it decides nothing here: the videos open everything). {@code unlocked} (may be null)
     * runs after the videos have opened it, so the caller can carry on or redraw.
     */
    static void show(final Activity a, final int item, String what, int dialogTheme, final Runnable unlocked) {
        try {
            String message = what + " is part of Pro, which also removes the ads.";
            if (DayPass.valid(item) && VideoPass.offered(a) && Pro.get(a).canStartPass()) {
                choose(a, "Part of Pro", message, dialogTheme, unlocked);
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

    /** The offer: three videos for a day of everything in Pro, with "See Pro" and "Not now" beside it. */
    static void choose(final Activity a, String title, String intro, int dialogTheme, final Runnable unlocked) {
        try {
            final AlertDialog.Builder b = new DBuilder(a, dialogTheme);
            final Context cx = b.getContext();
            final AlertDialog[] dlg = new AlertDialog[1];
            LinearLayout box = new LinearLayout(cx);
            box.setOrientation(LinearLayout.VERTICAL);
            int side = Ui.dp(cx, 22);
            box.setPadding(side, Ui.dp(cx, 6), side, 0);
            TextView msg = new TextView(cx);
            msg.setText(intro + "\n\nOr watch " + VideoProgress.NEED + " short videos and use everything in Pro free for a day.");
            msg.setTextSize(15);
            msg.setLineSpacing(Ui.dp(cx, 2), 1f);
            box.addView(msg);
            int counted = VideoPass.counted(cx);
            if (counted > 0) {
                TextView got = new TextView(cx);
                got.setText(VideoProgress.progressText(counted));
                got.setTextSize(13);
                got.setAlpha(0.75f);
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                gp.topMargin = Ui.dp(cx, 8);
                box.addView(got, gp);
            }
            Button o = new Button(cx);
            o.setAllCaps(false);
            o.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            o.setText(VideoProgress.buttonText(counted));
            o.setOnClickListener(v -> {
                if (dlg[0] != null) dlg[0].dismiss();
                VideoPass.watch(a, unlocked);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(cx, 10);
            box.addView(o, lp);
            TextView note = new TextView(cx);
            note.setText("Only the videos you watch to the end count, and the ones you watched today stay counted if you stop. "
                    + "When the day is up, everything closes again and what you set up with Pro goes back to the free version.");
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
     * Under a locked view or a card of Pro things: while the pass runs, says that everything is open and until when; otherwise, when
     * videos are on, a button to watch the videos. Nothing is added when videos are off or Pro is owned.
     */
    static void addOffer(final Activity a, LinearLayout parent, float topDp, final Runnable unlocked) {
        try {
            if (!VideoPass.offered(a)) return;
            Pro p = Pro.get(a);
            if (p.owned()) return;
            if (!p.canStartPass()) {
                String running = runningLine(a);
                if (!running.isEmpty()) parent.addView(Ui.label(a, running, 13, Ui.MUTED, false), Ui.wide(a, topDp));
                return;
            }
            int counted = VideoPass.counted(a);
            if (counted > 0) parent.addView(Ui.label(a, VideoProgress.progressText(counted), 13, Ui.MUTED, false), Ui.wide(a, topDp));
            parent.addView(Ui.button(a, VideoProgress.buttonText(counted), false, v -> VideoPass.watch(a, unlocked)),
                    Ui.wide(a, counted > 0 ? 6 : topDp));
            VideoPass.warm(a);
        } catch (RuntimeException e) {
            CrashLog.note(a, "video offer", e);
        }
    }

    /** "Everything in Pro: open until tomorrow 4:30 PM." while the pass runs, otherwise empty. */
    static String runningLine(Context c) {
        long until = Pro.get(c).passUntil();
        if (until <= 0) return "";
        return "Everything in Pro: open until "
                + LockRules.untilText(until, System.currentTimeMillis(), Store.zone(), android.text.format.DateFormat.is24HourFormat(c)) + ".";
    }
}
