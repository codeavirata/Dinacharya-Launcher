package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Random;

/**
 * The screen in front of an app whose group is locked: the name of the group, when the lock ends and how long is left.
 * Go back is always there. A lock you started yourself can be broken by waiting half a minute and typing a sentence;
 * a strict lock (Pro) cannot be broken at all. When the lock runs out while the screen is showing, the app opens.
 */
final class LockScreen {
    interface Exit {
        void goBack();

        /** The lock was broken or it ran out: open the app. */
        void open();
    }

    /** Seconds to wait before the sentence to break a lock appears. */
    static final int BREAK_WAIT_SEC = 30;

    private final Activity a;
    private final LauncherTheme th;
    private final AppEntry app;
    private final LockRules.Hit hit;
    private final Exit exit;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private final FrameLayout root;
    private final LinearLayout col;
    private final TextView left;
    private final TextView breakBtn;
    private final FrameLayout stage;
    private final TextView goBack;
    private EditText input;
    private boolean released;
    private boolean breaking;
    private boolean phraseShown;
    private boolean ended;
    private long breakStart;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (released) return;
            update();
            h.postDelayed(this, 250);
        }
    };

    LockScreen(Activity activity, LauncherTheme theme, AppEntry app, LockRules.Hit hit, boolean h24, Exit exit) {
        this.a = activity;
        this.th = theme;
        this.app = app;
        this.hit = hit;
        this.exit = exit;

        root = new FrameLayout(a);
        root.setBackgroundColor(th.scrim(0xF2));
        root.setClickable(true);

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(32), dp(24), dp(32), dp(24));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Insets.pad(sv);

        TextView name = text(app.shown(), 30, th.text, true);
        name.setGravity(Gravity.CENTER);
        col.addView(name, wide(0));

        String group = hit.group.name.isEmpty() ? "a locked group" : hit.group.name;
        TextView kind = text(hit.strict ? "Strictly locked  ·  " + group : "Locked  ·  " + group, 15, th.accent, false);
        kind.setGravity(Gravity.CENTER);
        col.addView(kind, wide(12));

        long now = System.currentTimeMillis();
        boolean why = !hit.during.isEmpty() || hit.library;
        if (!hit.during.isEmpty()) {
            TextView w = text("While you are on: " + hit.during, 14, th.muted, false);
            w.setGravity(Gravity.CENTER);
            col.addView(w, wide(8));
        } else if (hit.library) {
            TextView w = text("Library mode is on", 14, th.muted, false);
            w.setGravity(Gravity.CENTER);
            col.addView(w, wide(8));
        }
        TextView until = text(hit.untilOpen ? "Until library mode is switched off" : "Until " + LockRules.untilText(hit.untilMs, now, Store.zone(), h24),
                hit.untilOpen ? 19 : 22, th.text, true);
        until.setGravity(Gravity.CENTER);
        col.addView(until, wide(why ? 14 : 22));

        left = text("", 17, th.muted, false);
        left.setGravity(Gravity.CENTER);
        col.addView(left, wide(6));

        goBack = pill("Go back", true);
        goBack.setOnClickListener(x -> exit.goBack());
        col.addView(goBack, wide(30));

        breakBtn = pill("Break the lock", false);
        stage = new FrameLayout(a);
        if (hit.strict) {
            TextView note = text("This lock is strict. There is no way past it until it ends.", 14, th.muted, false);
            note.setGravity(Gravity.CENTER);
            note.setLineSpacing(0, 1.15f);
            col.addView(note, wide(18));
        } else {
            breakBtn.setOnClickListener(x -> beginBreak());
            col.addView(breakBtn, wide(12));
            col.addView(stage, wide(16));
        }
        update();
    }

    View view() {
        return root;
    }

    /** Call once the view is on screen: starts the clock and the entrance. */
    void start() {
        root.setAlpha(0f);
        col.setScaleX(0.94f);
        col.setScaleY(0.94f);
        root.animate().alpha(1f).setDuration(220).start();
        col.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        h.postDelayed(tick, 250);
    }

    /** Stops the clock and closes the keyboard. */
    void release() {
        released = true;
        h.removeCallbacksAndMessages(null);
        try {
            InputMethodManager m = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (m != null) m.hideSoftInputFromWindow(root.getWindowToken(), 0);
        } catch (RuntimeException ignored) {
            // the keyboard closes with the screen anyway
        }
    }

    // ------------------------------------------------------------------ the clock

    /** Runs four times a second: the time left, the end of the lock, and the half-minute wait. */
    private void update() {
        long now = System.currentTimeMillis();
        long ms = hit.untilMs - now;
        if (ms <= 0) {
            if (!ended) showEnded();
            return;
        }
        left.setText(hit.untilOpen ? "" : LockRules.left(hit.untilMs, now) + " left");
        if (breaking && !phraseShown) {
            if (root.getWindowVisibility() != View.VISIBLE) {
                breakStart = SystemClock.uptimeMillis();   // left the screen: the wait starts again
                return;
            }
            long el = SystemClock.uptimeMillis() - breakStart;
            long sec = BREAK_WAIT_SEC - el / 1000L;
            if (sec <= 0) showPhrase();
            else if (waitText != null) waitText.setText(String.valueOf(sec));
        }
    }

    private void showEnded() {
        ended = true;
        left.setText("The lock has ended.");
        stage.removeAllViews();
        breakBtn.setVisibility(View.GONE);
        goBack.setText("Open " + app.shown());
        goBack.setOnClickListener(x -> exit.open());
    }

    // ------------------------------------------------------------------ breaking a lock

    private TextView waitText;

    private void beginBreak() {
        if (breaking || ended) return;
        breaking = true;
        breakStart = SystemClock.uptimeMillis();
        breakBtn.setVisibility(View.GONE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView ask = text("A lock is a promise to yourself. Wait a moment before you break it.", 14, th.muted, false);
        ask.setGravity(Gravity.CENTER);
        ask.setLineSpacing(0, 1.15f);
        box.addView(ask, wide(0));
        waitText = text(String.valueOf(BREAK_WAIT_SEC), 64, th.accent, true);
        waitText.setGravity(Gravity.CENTER);
        box.addView(waitText, wide(8));
        stage.removeAllViews();
        stage.addView(box);
    }

    private void showPhrase() {
        phraseShown = true;
        final String phrase = PauseChallenge.phrase(rnd);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView ask = text("Type this to break the lock", 14, th.muted, false);
        ask.setGravity(Gravity.CENTER);
        box.addView(ask, wide(0));
        TextView p = text("“" + phrase + "”", 22, th.text, true);
        p.setGravity(Gravity.CENTER);
        p.setLineSpacing(0, 1.1f);
        box.addView(p, wide(10));
        input = field();
        final EditText typed = input;
        box.addView(typed, wide(14));
        final TextView note = text(" ", 14, th.accent, false);
        note.setGravity(Gravity.CENTER);
        box.addView(note, wide(8));
        final Runnable check = () -> {
            if (PauseChallenge.phraseMatches(typed.getText().toString(), phrase)) {
                exit.open();
            } else {
                note.setText("Not the same yet. Check each word.");
                shake(typed);
            }
        };
        typed.setOnEditorActionListener((v, actionId, ev) -> {
            check.run();
            return true;
        });
        TextView go = pill("Break the lock", true);
        go.setOnClickListener(x -> check.run());
        box.addView(go, wide(10));
        stage.removeAllViews();
        stage.addView(box);
        typed.requestFocus();
        h.postDelayed(() -> {
            if (released) return;
            try {
                InputMethodManager m = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (m != null) m.showSoftInput(typed, 0);
            } catch (RuntimeException ignored) {
                // the keyboard opens when the field is tapped
            }
        }, 300);
    }

    // ------------------------------------------------------------------ small helpers

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    private LinearLayout.LayoutParams wide(float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private TextView text(String s, float sp, int color, boolean light) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(light ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView pill(String label, boolean filled) {
        TextView b = text(label, 17, filled ? (th.dark ? 0xFF001B1A : 0xFFFFFFFF) : th.text, false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setMinHeight(dp(54));
        b.setPadding(dp(24), dp(14), dp(24), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(30));
        if (filled) bg.setColor(th.accent);
        else {
            bg.setColor(th.dark ? 0x1FFFFFFF : 0x14000000);
            bg.setStroke(dp(1), th.dark ? 0x40FFFFFF : 0x33000000);
        }
        b.setBackground(bg);
        return b;
    }

    private EditText field() {
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setSingleLine(true);
        e.setHint("Type it here");
        e.setHintTextColor(th.muted);
        e.setTextColor(th.text);
        e.setGravity(Gravity.CENTER);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(th.dark ? 0x1FFFFFFF : 0x14000000);
        bg.setStroke(dp(1), th.dark ? 0x33FFFFFF : 0x26000000);
        e.setBackground(bg);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        return e;
    }

    private void shake(final View v) {
        v.animate().translationX(dp(12)).setDuration(60).withEndAction(() ->
                v.animate().translationX(-dp(10)).setDuration(80).withEndAction(() ->
                        v.animate().translationX(0f).setDuration(70).start()).start()).start();
    }
}
