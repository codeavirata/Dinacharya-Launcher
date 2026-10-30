package app.dinacharya.wallpaper;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * What the app's icon opens. The very first time it is a proper welcome (the picture of the home screen, one sentence, a
 * "Get started" button) that leads to the setup, then the tour, then the dashboard. After that the icon goes straight to
 * the dashboard. The home screen itself has no icon: it is what Android shows when you press Home, and it never shows up
 * in the list of recent apps, so Dinacharya appears there once (the dashboard).
 */
public class WelcomeActivity extends LangActivity {
    private static final int BG = 0xFF10201E;
    private static final int WHITE = 0xFFF1F3F2;
    private static final int SOFT = 0xFFB8C7C5;
    private static final int TEAL = 0xFF63D2CC;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        int step = FirstRun.next(Store.prefs(this).getBoolean(SetupActivity.PREF_SETUP_DONE, false),
                Store.prefs(this).getBoolean(LauncherActivity.PREF_TOUR_DONE, false));
        try {
            if (step == FirstRun.DASHBOARD) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
            if (step == FirstRun.TOUR) {
                startActivity(new Intent(this, TourActivity.class).putExtra(TourActivity.EXTRA_FIRST_RUN, true));
                finish();
                return;
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "welcome hand-off", e);
            finish();
            return;
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        Insets.bars(this, false);
        build();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        return t;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(28), dp(24), dp(8));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView eyebrow = text("DINACHARYA", 13, TEAL, true);
        eyebrow.setLetterSpacing(0.14f);
        col.addView(eyebrow);

        FrameLayout stage = new FrameLayout(this);
        int stageH = Math.min(dp(300), (int) (getResources().getDisplayMetrics().heightPixels * 0.38f));
        TourArt art = new TourArt(this);
        art.setPage(TourArt.HOME);
        stage.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, stageH);
        sp.topMargin = dp(12);
        col.addView(stage, sp);

        TextView title = text("Welcome to Dinacharya Launcher", 28, WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(20);
        col.addView(title, tp);

        TextView body = text("A calm home screen made from your daily routine: your whole day, live, with nothing else on it. "
                + "Alarms that ask, a pause or a lock for the apps that pull you away, and a report on how each day went.", 16, SOFT, false);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(10);
        col.addView(body, bp);

        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(dp(24), dp(8), dp(24), dp(4));
        foot.addView(Ui.button(this, "Get started", true, v -> start()),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView note = text("It takes about a minute. Every step can be skipped.", 13, SOFT, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(10);
        foot.addView(note, np);
        // the language can be chosen before anything else, so the welcome and the setup read in it
        TextView langLabel = text("Language", 12, SOFT, false);
        langLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = dp(8);
        foot.addView(langLabel, ll);
        TextView langLink = text(LanguageDialog.summary(this), 14, TEAL, true);
        langLink.setGravity(Gravity.CENTER);
        langLink.setPadding(dp(8), dp(6), dp(8), dp(8));
        langLink.setOnClickListener(v -> LanguageDialog.show(this, android.R.style.Theme_DeviceDefault_Dialog_Alert));
        foot.addView(langLink, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        BrandMark mark = new BrandMark(this);
        mark.setColors(TEAL, SOFT);
        foot.addView(mark, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(foot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Insets.pad(root);
        setContentView(root);
    }

    /** On to the setup. This screen is finished with, so Back from the setup does not return to it. */
    private void start() {
        try {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
        } catch (RuntimeException e) {
            CrashLog.note(this, "welcome start", e);
        }
    }
}
