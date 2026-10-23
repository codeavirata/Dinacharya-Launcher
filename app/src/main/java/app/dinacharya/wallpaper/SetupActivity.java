package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shown the first time the app starts (after the welcome, before the tour): every permission and setting Dinacharya needs, in one list with
 * a tick for each, and one button that walks through the ones still missing. Nothing is forced: each step can be skipped,
 * and the whole list can be opened again later from Settings.
 */
public class SetupActivity extends LangActivity {
    static final String PREF_SETUP_DONE = "setup_done";
    static final String EXTRA_REVIEW = "review";

    private LinearLayout rows;
    private TextView summary;
    private Button main;
    private TextView skip;
    /** Steps already asked for in this visit, so the guide moves on instead of asking again. */
    private final Set<Integer> tried = new HashSet<>();
    private int launched = -1;
    private boolean guided;
    private boolean review;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        review = getIntent().getBooleanExtra(EXTRA_REVIEW, false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFEDF2F1);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(28), dp(20), dp(12));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(Ui.eyebrow(this, review ? "Phone settings" : "Welcome"));
        TextView title = Ui.label(this, review ? "Check your phone settings" : "Let’s get your phone ready", 26, Ui.INK, true);
        body.addView(title, Ui.wide(this, 4));
        TextView sub = Ui.label(this, "A few settings so the alarms ring on time and the home screen works. "
                + "Each one opens Android’s own screen and takes a moment. You can skip any of them.", 15, Ui.MUTED, false);
        sub.setLineSpacing(0, 1.12f);
        body.addView(sub, Ui.wide(this, 10));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setBackground(Ui.box(this, Ui.SURFACE, 0, 22));
        card.setElevation(dp(1.5f));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        card.addView(rows);
        body.addView(card, Ui.wide(this, 18));

        summary = Ui.label(this, "", 14, Ui.MUTED, false);
        summary.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(summary, Ui.wide(this, 14));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(dp(20), dp(8), dp(20), dp(16));
        main = Ui.button(this, "Continue", true, v -> onMain());
        foot.addView(main, Ui.wide(this, 0));
        skip = Ui.label(this, review ? "Close" : "Skip for now", 15, Ui.TEAL, true);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(dp(8), dp(12), dp(8), dp(12));
        skip.setOnClickListener(v -> finishSetup());
        foot.addView(skip, Ui.wide(this, 4));
        root.addView(foot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Insets.pad(root);
        setContentView(root);
        Insets.lightBars(this);
        refresh();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        // Back from Android's screen with the step done: go straight on to the next one.
        if (guided && launched >= 0 && Permissions.isOk(this, launched)) {
            launched = -1;
            next();
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == Permissions.REQ_WALL) {
            WallpaperSetup.noteResult(this, result == RESULT_OK);
            // The wallpaper starts a moment after Android's preview closes: look again once it has.
            rows.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                refresh();
                if (WallpaperSetup.lockOnly(this)) {
                    Lang.toast(this, "That set the lock screen only. Tap Set again and choose the home screen too.",
                            android.widget.Toast.LENGTH_LONG).show();
                }
            }, 1500);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        // onResume follows and carries on.
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        finishSetup();
    }

    private void refresh() {
        if (rows == null) return;
        rows.removeAllViews();
        List<Permissions.Step> steps = Permissions.steps(this);
        int missing = 0;
        for (int i = 0; i < steps.size(); i++) {
            final Permissions.Step s = steps.get(i);
            if (!s.ok) missing++;
            if (i > 0) rows.addView(Ui.rule(this), Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, dp(1), 2));
            rows.addView(Ui.statusRow(this, s.title, s.why, s.ok, s.action, () -> {
                tried.add(s.id);
                launched = s.id;
                guided = false;
                Permissions.request(this, s.id);
                if (s.id == Permissions.ALARMS_ON) refresh();
            }));
            if (s.extra != null && !s.ok) {
                TextView more = Ui.label(this, s.extra, 13, Ui.TEAL, true);
                more.setPadding(dp(46), dp(2), dp(8), dp(10));
                more.setOnClickListener(v -> Permissions.requestExtra(this, s.id));
                rows.addView(more);
            }
        }
        if (missing == 0) {
            summary.setText("Everything is set.");
            main.setText(review ? "Close" : "Finish");
        } else {
            summary.setText(missing == 1 ? "1 step left." : missing + " steps left.");
            main.setText(guided || !tried.isEmpty() ? "Next step" : "Set up everything");
        }
    }

    private void onMain() {
        if (Permissions.missing(this) == 0) {
            finishSetup();
            return;
        }
        guided = true;
        next();
    }

    /** Asks for the first missing step not yet asked for; when none is left, finishes. */
    private void next() {
        for (Permissions.Step s : Permissions.steps(this)) {
            if (s.ok || tried.contains(s.id)) continue;
            tried.add(s.id);
            launched = s.id;
            Permissions.request(this, s.id);
            if (s.id == Permissions.ALARMS_ON) {
                refresh();
                if (Permissions.isOk(this, s.id)) {
                    launched = -1;
                    next();
                }
            }
            return;
        }
        guided = false;
        launched = -1;
        refresh();
        finishSetup();
    }

    private void finishSetup() {
        Store.prefs(this).edit().putBoolean(PREF_SETUP_DONE, true).apply();
        if (!review) {
            // First time: on to the tour, which ends in the dashboard. (If the tour was already seen, straight to the dashboard.)
            try {
                if (!Store.prefs(this).getBoolean(LauncherActivity.PREF_TOUR_DONE, false)) {
                    startActivity(new Intent(this, TourActivity.class).putExtra(TourActivity.EXTRA_FIRST_RUN, true));
                } else {
                    startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "ready"));
                }
            } catch (RuntimeException e) {
                CrashLog.note(this, "setup to tour", e);
            }
        }
        finish();
    }
}
