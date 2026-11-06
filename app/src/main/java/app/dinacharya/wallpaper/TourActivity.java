package app.dinacharya.wallpaper;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;


/**
 * A short tour of what the app does: eleven cards with a picture each, then a card that checks what is still missing from
 * the setup, then the last screen, which shows Free and Pro side by side (see {@link CompareActivity}). It comes after the welcome
 * and the setup the first time, and can be replayed from the settings. When it is the first time, finishing it opens the dashboard
 * with a "you're all set" message.
 */
public class TourActivity extends LangActivity {

    @Override
    protected int look() {
        return LOOK_OWN;
    }

    /** Set when the tour is the last step of the first-run welcome, so that it ends in the dashboard. */
    static final String EXTRA_FIRST_RUN = "first_run";
    private static final int WHITE = 0xFFF1F3F2;
    private static final int SOFT = 0xFFB8C7C5;
    private static final int TEAL = 0xFFFFC66B;
    private static final int SETUP = TourArt.PAGES;
    private static final int TOTAL = TourArt.PAGES + 1;

    private static final String[] TITLES = {
            "Your day, on your home screen",
            "Swipe up for every app",
            "Swipe and press",
            "A routine that fits you",
            "Alarms that ask",
            "Change the plan, just for today",
            "Breaks, to-dos and goals",
            "A pause before distractions",
            "Lock apps for a while",
            "See how your days go",
            "Back up to your own Drive",
            "Make it yours",
            "Free, and Pro if you want more",
            "Your day is the wallpaper",
            "Last check",
    };
    private static final String[] BODIES = {
            "No icons and no clutter. Your whole home screen is your day: the time, the date and every slot, with the one you are in highlighted.",
            "Every app, as a plain list over a soft blur of your day. Type to search. Slide along the A to Z strip to jump to a letter. Press and hold an app to add a pause, hide it, or open its info. Dinacharya Launcher is in the list too: tap it for the dashboard.",
            "Tap your day to open the dashboard. Swipe down for notifications. Swipe left for the camera and right for the phone: you can change both to any app. Press and hold anywhere for settings.",
            "Set your wake-up time and shape the day into slots: Study, Rest, Meals and more. The day always adds up to 24 hours: lengthen, add or delete a slot and the minutes come from, or go back to, Sleep, which stays between 4 and 12 hours. Wake-up and Sleep are always there, and a slot is at least 5 minutes. Keep one routine for weekdays and others for weekends, exam days, or just tomorrow. Open the dashboard to edit them.",
            "Each slot can ring when it starts and ask one question: carry on, or change the plan? At bedtime it asks when you went to sleep, and if you need more time it tells you what that costs in sleep. The question comes up by itself, and again when you wake the phone or come back to the home screen. The start-of-day alarm can ask for a sum, a short pause, a shake or a sentence before it stops, and shows a motivating quote. Finished early? Tap Finish on Home to take a rest, start the next slot or begin something new. Need more or less time? Tap Adjust time. Library mode keeps every alarm silent, with only vibration.",
            "When a slot starts, tap Change plan. Replace the slot with something else, keep doing the previous slot, or swap it with another slot, by name or by place and time. Change only a few minutes, or the whole slot. The rest of the slot goes back to your saved plan, and your saved routine is never changed.",
            "Take a break with − and +, or until the slot ends, and end it early when you are ready. Give a slot its own to-do list: tasks can repeat, and they show on your home screen while the slot runs. Set a daily goal for a slot and keep a streak.",
            "Pick the apps that pull you away. During focus time they wait a few seconds before they open, and you can always open them anyway. Free: one app. Pro: any number, strict pauses, daily limits, and playful pauses: a quick sum, a sentence to type, slow breaths, or saying why you are opening it.",
            "Shut the apps that eat your time for hours you choose: every day or on some days, or just for the next hour. A locked app is greyed in the list, and the lock screen shows when it opens again. You can break a lock by waiting half a minute and typing a sentence. Free: one lock with one app. Pro: groups of apps, as many locks as you like, and strict locks that cannot be broken. A group can also lock only while an activity type such as Study is going on, only when both the hours and the activity hold, or in library mode. Set it up in the home settings. The strong lock makes every lock hold wherever an app is opened from.",
            "A score for today, phone time by hour and by app, and plain suggestions, with a PDF to save or share. Today's report is free. Pro adds every saved day, the weekly view, the slot-by-slot analysis and habit cards, and goals: set one for a slot, give it its own to-do list, and watch the streak.",
            "Sign in with Google to back up to a private space in your own Drive, and restore on a new phone. The home settings, your routines and your reports are all in it.",
            "Choose Light, Night or Auto for your day poster, and a colour look: Teal is free and Pro adds the rest. The app speaks English, Hindi, Marathi, Gujarati, Bengali, Tamil and Telugu. Library mode and quiet rules keep your phone silent in the slots you choose. Settings > Feature guide explains every feature and how to set it up.",
            "",
            "The Dinacharya Launcher live wallpaper shows the time, the date and every slot of your day, with the one you are in highlighted, and keeps itself up to date. It comes with the home screen: Dinacharya Launcher asks Android to put it in place and you confirm once, so it also shows on your lock screen and in your recent apps.",
            "A feature you chose that still needs a setting is listed here. You can finish it now or come back from Settings.",
    };

    private int page;
    private boolean busy;
    private TourArt art;
    private TextView stepView, titleView, bodyView, skip, back;
    private android.widget.Button next;
    private LinearLayout dots, setupCard, setupRows;
    private LinearLayout content;
    private GestureDetector swipes;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        Ui.screen(this, true);   // night glass
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        if (b != null) page = Math.max(0, Math.min(TOTAL - 1, b.getInt("page", 0)));
        Pro.get(this).start();   // so the price is known by the time the Pro page is reached
        build();
        bind();
        swipes = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                float min = ViewConfiguration.get(TourActivity.this).getScaledMinimumFlingVelocity() * 3f;
                if (Math.abs(dx) < Ui.dp(TourActivity.this, 70) || Math.abs(dx) < Math.abs(dy) * 1.5f || Math.abs(vx) < min) return false;
                if (dx < 0) step(1);
                else step(-1);
                return true;
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == SETUP) fillSetup();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (page > 0) step(-1);
        else finishTour();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            if (swipes != null) swipes.onTouchEvent(ev);
        } catch (RuntimeException e) {
            CrashLog.note(this, "tour swipe", e);
        }
        return super.dispatchTouchEvent(ev);
    }

    // ------------------------------------------------------------------ layout

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
        root.setPadding(dp(24), dp(12), dp(24), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        stepView = text("", 13, SOFT, false);
        top.addView(stepView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        skip = text("Skip", 15, TEAL, true);
        skip.setPadding(dp(14), dp(12), dp(4), dp(12));
        skip.setOnClickListener(v -> finishTour());
        top.addView(skip);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        FrameLayout stage = new FrameLayout(this);
        int stageH = Math.min(dp(300), (int) (getResources().getDisplayMetrics().heightPixels * 0.36f));
        art = new TourArt(this);
        stage.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupCard = new LinearLayout(this);
        setupCard.setOrientation(LinearLayout.VERTICAL);
        setupCard.setPadding(dp(16), dp(10), dp(16), dp(10));
        setupCard.setBackground(Ui.box(this, Ui.SURFACE, 0, 22));
        setupRows = new LinearLayout(this);
        setupRows.setOrientation(LinearLayout.VERTICAL);
        setupCard.addView(setupRows);
        FrameLayout.LayoutParams scp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scp.gravity = Gravity.CENTER_VERTICAL;
        stage.addView(setupCard, scp);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, stageH);
        sp.topMargin = dp(8);
        content.addView(stage, sp);

        titleView = text("", 26, WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(22);
        content.addView(titleView, tp);
        bodyView = text("", 16, SOFT, false);
        bodyView.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(10);
        content.addView(bodyView, bp);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < TOTAL; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(7), dp(7));
            dp.leftMargin = dp(3);
            dp.rightMargin = dp(3);
            dots.addView(dot, dp);
        }
        root.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        back = text("Back", 16, WHITE, true);
        back.setPadding(dp(8), dp(14), dp(20), dp(14));
        back.setOnClickListener(v -> step(-1));
        nav.addView(back);
        View gap = new View(this);
        nav.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        next = Ui.button(this, "Next", true, v -> {
            if (page == SETUP) openFinal();
            else step(1);
        });
        next.setMinWidth(dp(120));
        nav.addView(next, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(10);
        root.addView(nav, np);

        Insets.pad(root);
        setContentView(root);
    }

    /** Shows the current page's words, picture or checklist, dots and buttons. */
    private void bind() {
        boolean setup = page == SETUP;
        stepView.setText((page + 1) + " of " + TOTAL);
        titleView.setText(TITLES[page]);
        bodyView.setText(page == TourArt.PRO ? proBody() : BODIES[page]);
        art.setVisibility(setup ? View.GONE : View.VISIBLE);
        setupCard.setVisibility(setup ? View.VISIBLE : View.GONE);
        if (!setup) art.setPage(page);
        else fillSetup();
        skip.setVisibility(setup ? View.INVISIBLE : View.VISIBLE);
        back.setVisibility(page == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText("Next");
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            dot.setBackground(Ui.box(this, i == page ? TEAL : 0x44FFFFFF, 0, 4));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = dp(i == page ? 18 : 7);
            dot.setLayoutParams(lp);
        }
    }

    /** The Pro page's words, with the price Google Play shows once it has answered. */
    private String proBody() {
        String life = Pro.get(this).price();
        String month = Pro.get(this).monthlyPrice();
        String pay = life.isEmpty() || month.isEmpty() ? "Pro is a monthly plan or one payment for life, through Google Play. "
                : "Pro is " + month + " a month, or " + life + " once for life, through Google Play. ";
        return "Free: the home screen, alarms, to-dos, today's report, one paused app and one locked app. "
                + pay
                + "It adds goals with their own to-dos, every saved day and the deeper analysis, more pauses and locks, more routines, "
                + "every colour look, and no ads."
                + (VideoPass.offered(this) ? " Or watch " + VideoProgress.NEED + " short videos and everything in Pro is open for a day. "
                + "What you set up with it goes back to the free version when the day is up. The last screen shows it all side by side." : "");
    }

    /** Moves to the next ({@code dir} 1) or previous (-1) page with a short slide and fade. */
    private void step(final int dir) {
        final int to = page + dir;
        if (busy || to < 0 || to >= TOTAL) return;
        busy = true;
        final float shift = dp(28) * dir;
        content.animate().alpha(0f).translationX(-shift).setDuration(110).withEndAction(() -> {
            page = to;
            bind();
            content.setTranslationX(shift);
            content.animate().alpha(1f).translationX(0f).setDuration(150).withEndAction(() -> busy = false).start();
        }).start();
    }

    /** After the last check: the screen that shows Free and Pro side by side. It carries on to the dashboard the first time. */
    private void openFinal() {
        Store.prefs(this).edit().putBoolean(LauncherActivity.PREF_TOUR_DONE, true).apply();
        try {
            startActivity(new Intent(this, CompareActivity.class)
                    .putExtra(CompareActivity.EXTRA_FIRST_RUN, getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false)));
            finish();
        } catch (RuntimeException e) {
            CrashLog.note(this, "tour last screen", e);
            finishTour();
        }
    }

    private void finishTour() {
        Store.prefs(this).edit().putBoolean(LauncherActivity.PREF_TOUR_DONE, true).apply();
        if (getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false)) {
            try {
                startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "ready"));
            } catch (RuntimeException e) {
                CrashLog.note(this, "tour to dashboard", e);
            }
        }
        finish();
    }

    // ------------------------------------------------------------------ the setup card

    private void fillSetup() {
        if (setupRows == null) return;
        setupRows.removeAllViews();
        boolean any = false;
        for (final int f : Features.unfinished(this)) {
            if (any) setupRows.addView(Ui.rule(this), Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, dp(1), 2));
            any = true;
            setupRows.addView(Ui.statusRow(this, Features.title(f), Features.lacking(this, f), false, "Finish", () ->
                    startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.EXTRA_REVIEW, true).putExtra(SetupActivity.EXTRA_FEATURE, f))));
        }
        if (!any) {
            setupRows.addView(Ui.statusRow(this, "Everything you chose is set", "Add more features any time from Settings.", true, "", null));
        }
    }
}
