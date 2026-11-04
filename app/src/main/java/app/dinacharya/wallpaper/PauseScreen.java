package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
 * The screen that waits in front of an app you gave a pause: a countdown, or (in Pro) a quick sum, a sentence to type,
 * a few slow breaths, or a question about why. It is friction, not a lock: Go back is always there, and the way past
 * appears when a longer wait is over.
 */
final class PauseScreen {
    interface Exit {
        void goBack();

        /** The wait is over, the challenge is done, or the way past it was taken: open the app. */
        void open();
    }

    private final Activity a;
    private final LauncherTheme th;
    private final AppEntry app;
    private final FocusRules.Decision dec;
    private final Exit exit;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private final FrameLayout root;
    private final LinearLayout col;
    private final FrameLayout stage;
    private TextView countdown;
    private TextView escape;
    private EditText input;
    private Runnable breathFrame;
    private long breathStart;
    private boolean released;
    private int left;
    private final boolean challenge;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (released) return;
            left--;
            refresh();
            if (left > 0) h.postDelayed(this, 1000);
        }
    };

    PauseScreen(Activity activity, LauncherTheme theme, AppEntry app, String message, FocusRules.Decision dec, Exit exit) {
        this.a = activity;
        this.th = theme;
        this.app = app;
        this.dec = dec;
        this.exit = exit;
        this.challenge = dec.challenge != PauseChallenge.WAIT;

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

        TextView msg = text(message, 17, th.muted, false);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0, 1.15f);
        col.addView(msg, wide(14));

        stage = new FrameLayout(a);
        col.addView(stage, wide(22));

        TextView back = pill("Go back", true);
        back.setOnClickListener(x -> exit.goBack());
        LinearLayout.LayoutParams bp = wide(24);
        col.addView(back, bp);

        escape = pill("Open anyway", false);
        escape.setOnClickListener(x -> {
            if (left > 0) return;
            exit.open();
        });
        col.addView(escape, wide(12));

        switch (dec.challenge) {
            case PauseChallenge.MATH:
                left = PauseChallenge.escapeSec(dec.heavy());
                stage.addView(mathStage());
                break;
            case PauseChallenge.TYPE:
                left = PauseChallenge.escapeSec(dec.heavy());
                stage.addView(typeStage());
                break;
            case PauseChallenge.BREATHE:
                left = PauseChallenge.escapeSec(dec.heavy());
                stage.addView(breatheStage());
                break;
            case PauseChallenge.WHY:
                left = PauseChallenge.escapeSec(dec.heavy());
                stage.addView(whyStage());
                break;
            default:
                left = Math.max(1, dec.waitSec);
                stage.addView(waitStage());
                break;
        }
        refresh();
    }

    View view() {
        return root;
    }

    /** Call once the view is on screen: starts the countdown, the breathing and the entrance. */
    void start() {
        root.setAlpha(0f);
        col.setScaleX(0.94f);
        col.setScaleY(0.94f);
        root.animate().alpha(1f).setDuration(220).start();
        col.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        h.postDelayed(tick, 1000);
        if (breathFrame != null) {
            breathStart = SystemClock.uptimeMillis();
            h.post(breathFrame);
        }
        if (input != null) {
            input.requestFocus();
            h.postDelayed(() -> {
                if (released) return;
                try {
                    InputMethodManager m = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (m != null) m.showSoftInput(input, 0);
                } catch (RuntimeException ignored) {
                    // the keyboard opens when the field is tapped
                }
            }, 350);
        }
    }

    /** Stops the timers and the breathing and closes the keyboard. */
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
        TextView b = text(label, 17, filled ? (th.dark ? 0xFF17171B : 0xFFFFFFFF) : th.text, false);
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

    private EditText field(int inputType, String hint) {
        EditText e = new EditText(a);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setHintTextColor(th.muted);
        e.setTextColor(th.text);
        e.setGravity(Gravity.CENTER);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(th.dark ? 0x1FFFFFFF : 0x14000000);
        bg.setStroke(dp(1), th.dark ? 0x33FFFFFF : 0x26000000);
        e.setBackground(bg);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        return e;
    }

    /** A little left-right shake for a wrong answer. */
    private void shake(final View v) {
        v.animate().translationX(dp(12)).setDuration(60).withEndAction(() ->
                v.animate().translationX(-dp(10)).setDuration(80).withEndAction(() ->
                        v.animate().translationX(0f).setDuration(70).start()).start()).start();
    }

    private void refresh() {
        if (escape == null) return;
        if (!challenge) {
            if (countdown != null) countdown.setText(left > 0 ? String.valueOf(left) : "✓");
            escape.setText(left > 0 ? "Open anyway in " + left : "Open anyway");
        } else {
            escape.setText(left > 0 ? "Skip the challenge in " + left : "Skip the challenge");
        }
        escape.setAlpha(left > 0 ? 0.45f : 1f);
    }

    // ------------------------------------------------------------------ the plain wait

    private View waitStage() {
        countdown = text(String.valueOf(left), 72, th.accent, true);
        countdown.setGravity(Gravity.CENTER);
        countdown.setPadding(0, dp(4), 0, dp(4));
        return countdown;
    }

    // ------------------------------------------------------------------ a quick sum

    private View mathStage() {
        final int rounds = PauseChallenge.rounds(PauseChallenge.MATH, dec.heavy());
        final int[] done = {0};
        final PauseChallenge.Sum[] cur = {PauseChallenge.sum(rnd, 0)};
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        final TextView step = text(rounds > 1 ? "Sum 1 of " + rounds : "Solve it to open", 14, th.muted, false);
        step.setGravity(Gravity.CENTER);
        box.addView(step, wide(0));
        final TextView q = text(cur[0].question + " = ?", 46, th.text, true);
        q.setGravity(Gravity.CENTER);
        box.addView(q, wide(10));
        input = field(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, "Your answer");
        final EditText answer = input;
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(14);
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(answer, ip);
        final TextView note = text(" ", 14, th.accent, false);
        note.setGravity(Gravity.CENTER);
        box.addView(note, wide(8));

        final Runnable check = () -> {
            if (PauseChallenge.answerMatches(answer.getText().toString(), cur[0].answer)) {
                done[0]++;
                if (done[0] >= rounds) {
                    exit.open();
                    return;
                }
                note.setText("Right! One more.");
                cur[0] = PauseChallenge.sum(rnd, done[0]);
                q.setText(cur[0].question + " = ?");
                step.setText("Sum " + (done[0] + 1) + " of " + rounds);
            } else {
                note.setText("Not quite. Try again.");
                shake(answer);
            }
            answer.setText("");
        };
        answer.setOnEditorActionListener((v, actionId, ev) -> {
            check.run();
            return true;
        });
        TextView go = pill("Check", true);
        go.setOnClickListener(x -> check.run());
        box.addView(go, wide(10));
        return box;
    }

    // ------------------------------------------------------------------ a sentence to type

    private View typeStage() {
        final String phrase = PauseChallenge.phrase(rnd);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView ask = text("Type this to open it", 14, th.muted, false);
        ask.setGravity(Gravity.CENTER);
        box.addView(ask, wide(0));
        TextView p = text("“" + phrase + "”", 22, th.text, true);
        p.setGravity(Gravity.CENTER);
        p.setLineSpacing(0, 1.1f);
        box.addView(p, wide(10));
        input = field(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, "Type it here");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
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
        TextView go = pill("Check", true);
        go.setOnClickListener(x -> check.run());
        box.addView(go, wide(10));
        return box;
    }

    // ------------------------------------------------------------------ slow breaths

    /** A circle that grows while you breathe in and shrinks while you breathe out. */
    private final class BreathView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        float level;

        BreathView(Context c) {
            super(c);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor((th.accent & 0x00FFFFFF) | 0x38000000);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(3));
            ring.setColor(th.accent);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float max = Math.min(getWidth(), getHeight()) / 2f - dp(4);
            float r = max * (0.40f + 0.60f * level);
            c.drawCircle(cx, cy, r, fill);
            c.drawCircle(cx, cy, r, ring);
            c.drawCircle(cx, cy, r * 0.30f, fill);
        }
    }

    private View breatheStage() {
        final int cycles = PauseChallenge.rounds(PauseChallenge.BREATHE, dec.heavy());
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView phase = text("Breathe in", 24, th.text, true);
        phase.setGravity(Gravity.CENTER);
        box.addView(phase, wide(0));
        final BreathView bv = new BreathView(a);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(190), dp(190));
        bp.topMargin = dp(10);
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(bv, bp);
        final TextView count = text("Breath 1 of " + cycles, 14, th.muted, false);
        count.setGravity(Gravity.CENTER);
        box.addView(count, wide(8));

        // Driven by the clock, not by an animator, so turning animations off in the phone's settings cannot skip the breaths.
        final long half = 4000L;
        final long total = cycles * 2L * half;
        breathFrame = new Runnable() {
            @Override
            public void run() {
                if (released) return;
                if (root.getWindowVisibility() != View.VISIBLE) {
                    breathStart = SystemClock.uptimeMillis();   // left the screen: the breaths start again
                    h.postDelayed(this, 250);
                    return;
                }
                long el = SystemClock.uptimeMillis() - breathStart;
                if (el >= total) {
                    exit.open();
                    return;
                }
                int seg = (int) (el / half);
                float f = (el % half) / (float) half;
                float eased = (1f - (float) Math.cos(Math.PI * f)) / 2f;
                bv.level = seg % 2 == 0 ? eased : 1f - eased;
                bv.invalidate();
                // in the language of the app, so that what is on screen is compared with what would be put there
                String p = Tr.t(seg % 2 == 0 ? "Breathe in" : "Breathe out");
                if (!p.contentEquals(phase.getText())) phase.setText(p);
                String c = Tr.t("Breath " + (seg / 2 + 1) + " of " + cycles);
                if (!c.contentEquals(count.getText())) count.setText(c);
                h.postDelayed(this, 16);
            }
        };
        return box;
    }

    // ------------------------------------------------------------------ say why

    private View whyStage() {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView ask = text("Why are you opening it?", 22, th.text, true);
        ask.setGravity(Gravity.CENTER);
        box.addView(ask, wide(0));
        for (int i = 0; i < PauseChallenge.REASONS.length; i++) {
            final int reason = i;
            TextView option = pill(PauseChallenge.REASONS[i], i == 0);
            option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            option.setOnClickListener(x -> {
                if (PauseChallenge.reasonIsNeed(reason)) {
                    exit.open();
                } else {
                    reflect(box, reason);
                }
            });
            box.addView(option, wide(i == 0 ? 14 : 10));
        }
        return box;
    }

    /** For a reason that is not a real need: a kind sentence and a short wait before the app can open. */
    private void reflect(LinearLayout box, int reason) {
        box.removeAllViews();
        TextView tip = text(PauseChallenge.REASON_TIPS[reason], 18, th.text, false);
        tip.setGravity(Gravity.CENTER);
        tip.setLineSpacing(0, 1.15f);
        box.addView(tip, wide(0));
        final TextView go = pill("Open in " + PauseChallenge.REASON_WAIT_SEC, false);
        go.setAlpha(0.45f);
        box.addView(go, wide(20));
        final int[] wait = {PauseChallenge.REASON_WAIT_SEC};
        final Runnable[] t = new Runnable[1];
        t[0] = () -> {
            if (released) return;
            wait[0]--;
            if (wait[0] > 0) {
                go.setText("Open in " + wait[0]);
                h.postDelayed(t[0], 1000);
            } else {
                go.setText("Open it");
                go.setAlpha(1f);
                go.setOnClickListener(x -> exit.open());
            }
        };
        h.postDelayed(t[0], 1000);
    }
}
