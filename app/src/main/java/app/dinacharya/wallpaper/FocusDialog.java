package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

/** Edits the pause that happens before one app opens from the home screen. */
final class FocusDialog {
    private static final int[] LIMITS = {0, 15, 30, 45, 60, 90, 120, 180};

    private FocusDialog() {
    }

    private static String limitText(int min) {
        if (min == 0) return "No daily limit";
        if (min < 60) return min + " min a day";
        return (min % 60 == 0 ? (min / 60) + " h" : (min / 60) + " h " + (min % 60) + " min") + " a day";
    }

    /** {@code openNow}: what is open now ({@link Pro#open}); each part of the dialog follows its own item. */
    static void show(final Activity a, final int theme, final LauncherConfig cfg, final AppEntry app, final int openNow,
                     final Runnable changed) {
        final AlertDialog.Builder b = new DBuilder(a, theme);
        final Context c = b.getContext();
        final LauncherConfig.Rule now = cfg.ruleFor(app.key);
        // What is open can change while this dialog is up (a video pass), so it is read from here. Each part has its own item:
        // a video for one of them opens that one and leaves the others as they were.
        final int[] open = {openNow};
        final Runnable[] onUnlocked = new Runnable[1];

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 22);
        box.setPadding(pad, Ui.dp(c, 6), pad, 0);

        TextView intro = new TextView(c);
        intro.setText("A pause gives you a moment to decide. It never locks you out: Go back is always there, and so is a way in after a longer wait.");
        intro.setTextSize(14);
        intro.setAlpha(0.8f);
        box.addView(intro, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final RadioGroup modes = new RadioGroup(c);
        String[] names = {"No pause", "Short pause (" + cfg.pauseSec + " seconds)", "Strict pause (" + cfg.strictSec + " seconds)"};
        final RadioButton[] radios = new RadioButton[names.length];
        for (int i = 0; i < names.length; i++) {
            RadioButton r = new RadioButton(c);
            r.setId(100 + i);
            r.setText(names[i] + (i == 2 && !DayPass.has(open[0], DayPass.PAUSE_STRICT) ? "  • Pro" : ""));
            r.setMinHeight(Ui.dp(c, 44));
            modes.addView(r);
            radios[i] = r;
        }
        // A new pause starts as a short one: "No pause" selected would make Save remove the rule and nothing would happen.
        int mode = now == null ? LauncherConfig.RULE_NUDGE : now.mode;
        if (!DayPass.has(open[0], DayPass.PAUSE_STRICT) && mode == LauncherConfig.RULE_STRICT) mode = LauncherConfig.RULE_NUDGE;
        modes.check(100 + mode);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Ui.dp(c, 10);
        box.addView(modes, mp);

        TextView howLabel = new TextView(c);
        howLabel.setText("How the pause goes");
        howLabel.setTextSize(14);
        howLabel.setAlpha(0.8f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(c, 12);
        box.addView(howLabel, hl);

        final Spinner how = new GlassSpinner(c);
        how.setAdapter(Lang.adapter(c, android.R.layout.simple_spinner_dropdown_item, howTexts(DayPass.has(open[0], DayPass.PAUSE_PLAYFUL))));
        how.setSelection(now != null && DayPass.has(open[0], DayPass.PAUSE_PLAYFUL) && PauseChallenge.valid(now.challenge) ? now.challenge : PauseChallenge.WAIT);
        box.addView(how, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        how.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!DayPass.has(open[0], DayPass.PAUSE_PLAYFUL) && pos > 0) {
                    how.setSelection(0);
                    ProPrompt.show(a, DayPass.PAUSE_PLAYFUL, "Playful pauses (a quick sum, a sentence, breathing, saying why)", theme,
                            () -> onUnlocked[0].run());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        TextView limitLabel = new TextView(c);
        limitLabel.setText("Daily limit" + (DayPass.has(open[0], DayPass.PAUSE_LIMIT) ? "" : "  • Pro"));
        limitLabel.setTextSize(14);
        limitLabel.setAlpha(0.8f);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = Ui.dp(c, 12);
        box.addView(limitLabel, ll);

        final Spinner limit = new GlassSpinner(c);
        String[] ltxt = new String[LIMITS.length];
        int sel = 0;
        for (int i = 0; i < LIMITS.length; i++) {
            ltxt[i] = limitText(LIMITS[i]);
            if (now != null && now.limitMin == LIMITS[i]) sel = i;
        }
        if (now != null && now.limitMin > 0 && sel == 0) {
            // a limit from an older save that is not in the list: keep it by showing the nearest step
            int best = 1;
            for (int i = 1; i < LIMITS.length; i++) {
                if (Math.abs(LIMITS[i] - now.limitMin) < Math.abs(LIMITS[best] - now.limitMin)) best = i;
            }
            sel = best;
        }
        limit.setAdapter(Lang.adapter(c, android.R.layout.simple_spinner_dropdown_item, ltxt));
        limit.setSelection(sel);
        final int initialLimit = sel;
        box.addView(limit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        limit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!DayPass.has(open[0], DayPass.PAUSE_LIMIT) && pos > 0) {
                    limit.setSelection(0);
                    // A limit left from a pass that has ended is only shown as "none" (no message); a new choice gets the message.
                    if (pos != initialLimit) ProPrompt.show(a, DayPass.PAUSE_LIMIT, "A daily limit", theme, () -> onUnlocked[0].run());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });

        final CheckBox always = new CheckBox(c);
        always.setText("Pause at any time of day (a strict pause still only applies in focus times)");
        always.setChecked(now == null || now.always);
        always.setMinHeight(Ui.dp(c, 44));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(c, 8);
        box.addView(always, cl);

        TextView foot = new TextView(c);
        int used = cfg.rules.size();
        final int usedRules = used;
        foot.setText(footText(c, open[0], cfg, usedRules));
        foot.setTextSize(12);
        foot.setAlpha(0.65f);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.topMargin = Ui.dp(c, 6);
        box.addView(foot, fl);

        b.setTitle(app.shown()).setView(box);
        b.setPositiveButton("Save", null);
        b.setNegativeButton("Cancel", null);
        if (now != null) b.setNeutralButton("Remove", (d, w) -> {
            cfg.removeRule(app.key);
            LauncherStore.save(a, cfg);
            if (changed != null) changed.run();
        });
        final AlertDialog d = b.create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int m = modes.getCheckedRadioButtonId() - 100;
            int lim = LIMITS[Math.max(0, limit.getSelectedItemPosition())];
            if (!DayPass.has(open[0], DayPass.PAUSE_STRICT) && m == LauncherConfig.RULE_STRICT) {
                ProPrompt.show(a, DayPass.PAUSE_STRICT, "A strict pause", theme, () -> {
                    onUnlocked[0].run();
                    v.performClick();   // carry on with Save: if it needs one more thing, that is asked for next
                });
                return;
            }
            if (!DayPass.has(open[0], DayPass.PAUSE_APPS) && now == null && cfg.rules.size() >= FocusRules.FREE_APPS
                    && (m != LauncherConfig.RULE_NONE || lim > 0)) {
                ProPrompt.show(a, DayPass.PAUSE_APPS, "Pause rules for more than " + DayPass.appCount(FocusRules.FREE_APPS), theme, () -> {
                    onUnlocked[0].run();
                    v.performClick();
                });
                return;
            }
            if (!DayPass.has(open[0], DayPass.PAUSE_LIMIT)) lim = 0;
            int ch = DayPass.has(open[0], DayPass.PAUSE_PLAYFUL) ? Math.max(0, how.getSelectedItemPosition()) : PauseChallenge.WAIT;
            cfg.setRule(app.key, m, lim, always.isChecked(), ch);
            LauncherStore.save(a, cfg);
            d.dismiss();
            Lang.toast(a, m == LauncherConfig.RULE_NONE && lim == 0
                    ? "No pause for " + app.shown()
                    : "Pause set for " + app.shown() + ". It starts the next time you open it from the home screen.",
                    android.widget.Toast.LENGTH_LONG).show();
            if (lim > 0 && !ScreenTime.hasAccess(a)) UsageAccess.ask(a);
            else if (m != LauncherConfig.RULE_NONE || lim > 0) StrongMode.offer(a);   // so the pause also holds from Recents and App info
            if (changed != null) changed.run();
        }));
        d.show();
        // After a video has opened one item, the words in this dialog follow (only that item's part changes).
        onUnlocked[0] = () -> {
            open[0] = Pro.open(a);
            radios[2].setText("Strict pause (" + cfg.strictSec + " seconds)" + (DayPass.has(open[0], DayPass.PAUSE_STRICT) ? "" : "  • Pro"));
            int keep = how.getSelectedItemPosition();
            how.setAdapter(Lang.adapter(c, android.R.layout.simple_spinner_dropdown_item, howTexts(DayPass.has(open[0], DayPass.PAUSE_PLAYFUL))));
            how.setSelection(Math.max(0, keep));
            limitLabel.setText("Daily limit" + (DayPass.has(open[0], DayPass.PAUSE_LIMIT) ? "" : "  • Pro"));
            foot.setText(footText(c, open[0], cfg, usedRules));
        };
    }

    private static String[] howTexts(boolean open) {
        String[] htxt = new String[PauseChallenge.NAMES.length];
        for (int i = 0; i < htxt.length; i++) {
            htxt[i] = PauseChallenge.NAMES[i] + (i > 0 && !open ? "  \u2022 Pro" : "") + "  \u2013  " + PauseChallenge.BLURBS[i].toLowerCase(java.util.Locale.ROOT);
        }
        return htxt;
    }

    private static String footText(Context c, int open, LauncherConfig cfg, int used) {
        StringBuilder sb = new StringBuilder();
        if (DayPass.has(open, DayPass.PAUSE_APPS)) sb.append("Pause rules for as many apps as you like. ");
        else sb.append("Free: a pause rule for ").append(DayPass.appCount(FocusRules.FREE_APPS)).append(" (").append(used).append(" used). Pro has no limit. ");
        if (DayPass.has(open, DayPass.PAUSE_STRICT)) sb.append("Strict pauses apply in focus times: ").append(focusNames(cfg)).append(". ");
        sb.append(StrongMode.holdsLine(c, false));
        return sb.toString();
    }

    private static String focusNames(LauncherConfig cfg) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) {
            if (!cfg.isFocusKind(k)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Routine.KIND_NAMES[k]);
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }
}
