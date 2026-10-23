package app.dinacharya.wallpaper;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Dinacharya Pro: what is free, what Pro adds, the price Google Play shows, and the buy and restore buttons. Every
 * "See Pro" button in the app opens this one screen, on top of whatever the person was doing, so Back returns to it.
 */
public class ProActivity extends LangActivity {
    /** Feature, what is free, what Pro adds. */
    private static final String[][] TABLE = {
            {"Home screen, alarms, reports of today, backup", "Everything", "Everything"},
            {"Performance", "Today and the last " + Limits.FREE_REPORT_DAYS + " days", "Every saved day, the weekly view and tips"},
            {"Pause before an app", "Up to " + FocusRules.FREE_APPS + " apps, a short wait", "Any number of apps, strict pauses, daily limits, "
                    + "and playful pauses (a sum, a sentence, breathing, say why)"},
            {"Lock apps for a time", "Up to " + LockRules.FREE_GROUP_APPS + " apps", "Groups of apps, any number of locks, and strict locks"},
            {"Routines", "One routine", "Weekday, weekend and exam-day routines"},
            {"Goals and type totals", "Not included", "Daily goals with streaks"},
            {"Colour looks of the home screen", "The first look", "All the looks"},
            {"Ads", "Small ads in the dashboard", "No ads"},
    };

    private final Pro.Listener listener = this::render;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.SOFT);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Insets.pad(sv);
        setContentView(sv);
        Insets.lightBars(this);
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pro.get(this).addListener(listener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pro.get(this).start();   // refreshes the price and what this account owns
        render();
    }

    @Override
    protected void onStop() {
        Pro.get(this).removeListener(listener);
        super.onStop();
    }

    private void render() {
        if (body == null) return;
        try {
            body.removeAllViews();
            final Pro p = Pro.get(this);
            boolean owned = p.owned();
            body.addView(Ui.eyebrow(this, "Dinacharya"));
            body.addView(Ui.label(this, "Dinacharya Pro", 28, Ui.INK, true), Ui.wide(this, 4));
            body.addView(Ui.label(this, owned ? "You have Pro. Thank you!" : "One purchase, no subscription. Paid once through Google Play.",
                    15, owned ? Ui.TEAL : Ui.MUTED, owned), Ui.wide(this, 6));

            if (!owned) {
                String price = p.price();
                body.addView(Ui.button(this, price.isEmpty() ? "Get Pro" : "Get Pro for " + price, true, v -> p.buy(this)), Ui.wide(this, 16));
                if (price.isEmpty()) {
                    TextView note = Ui.label(this, "The price comes from Google Play. It shows here once Play answers, and Pro can only be "
                            + "bought in a copy of the app that was installed from Google Play.", 12, Ui.MUTED, false);
                    note.setLineSpacing(Ui.dp(this, 2), 1f);
                    body.addView(note, Ui.wide(this, 6));
                }
            } else if (p.debugPro()) {
                body.addView(Ui.label(this, "Switched on for testing on this phone.", 14, Ui.TEAL, true), Ui.wide(this, 10));
            }

            if (!owned && VideoPass.offered(this)) {
                LinearLayout trial = Ui.section(this, body, "Try it first", "Watch short videos, use Pro features for a while");
                String running = VideoPass.line(this);
                if (!running.isEmpty()) trial.addView(small(running, Ui.TEAL), Ui.wide(this, 8));
                trial.addView(small("Pick a feature, then choose how many short videos to watch: 1 video opens just that feature for a day, "
                        + "2 videos for 3 days, 3 videos the whole group it is in (all the pauses, say). Each feature has its own video "
                        + "and its own time, so opening one never opens another. Nothing is charged. When the time is up, what you set up "
                        + "with it goes back to the free version (Pro keeps it for good).", Ui.MUTED), Ui.wide(this, 8));
                int lastArea = -1;
                final int open = p.open();
                for (int f = DayPass.FIRST; f <= DayPass.LAST; f++) {
                    final int feature = f;
                    int area = DayPass.area(f);
                    if (area != lastArea) {
                        trial.addView(Ui.label(this, DayPass.areaName(area), 13, Ui.MUTED, true), Ui.wide(this, 12));
                        lastArea = area;
                    }
                    if (DayPass.has(open, f)) {
                        trial.addView(Ui.label(this, DayPass.title(f) + "\n" + ProPrompt.runningLine(this, f), 14, Ui.TEAL, false), Ui.wide(this, 8));
                        ProPrompt.addExtendButton(this, trial, f, 6, this::render);
                    } else {
                        trial.addView(Ui.button(this, DayPass.title(f), false,
                                v -> ProPrompt.choose(this, feature, "Open it free", DayPass.title(feature) + " is part of Pro.", 0, this::render)),
                                Ui.wide(this, 6));
                    }
                }
                VideoPass.warm(this);
            }

            LinearLayout card = Ui.section(this, body, "What is free, and what Pro adds", null);
            for (String[] row : TABLE) {
                card.addView(Ui.label(this, row[0], 15, Ui.INK, true), Ui.wide(this, 14));
                card.addView(small("Free:  " + row[1], Ui.MUTED), Ui.wide(this, 2));
                card.addView(small("Pro:  " + row[2], Ui.TEAL), Ui.wide(this, 1));
            }

            Button restore = Ui.button(this, "Restore my purchase", false, v -> p.restore());
            body.addView(restore, Ui.wide(this, 18));
            body.addView(small("Tied to your Google Play account, so it comes back on a new phone. Refunds follow Google Play's rules. "
                    + "If Pro ever ends (a refund, say), nothing you made is taken away: a routine, a lock or a pause that goes past the "
                    + "free limit stays saved and starts working again with Pro. A video pass is different: when its time is up, "
                    + "what you set up with it goes back to the free version.", Ui.MUTED), Ui.wide(this, 10));

            if (p.debuggable()) {
                LinearLayout dev = Ui.section(this, body, "For testing", "Test build only");
                dev.addView(Ui.toggle(this, "Pretend I have Pro", p.debugPro(), (b, on) -> p.setDebugPro(on)), Ui.wide(this, 6));
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "pro screen", e);
        }
    }

    private TextView small(String text, int color) {
        TextView t = Ui.label(this, text, 13, color, false);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        return t;
    }
}
