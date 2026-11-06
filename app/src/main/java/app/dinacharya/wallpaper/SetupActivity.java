package app.dinacharya.wallpaper;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The first-start setup, and "Features" in Settings. It starts by asking what the person wants Dinacharya to do: each feature is a card with
 * a small moving picture of it. Nothing is asked of the phone until a feature is chosen, and then only what that feature needs, one thing at a
 * time, each on a page that shows what it is for and says what happens without it. Every page can be skipped, and a feature that was left
 * unfinished can be finished later from Home or Settings.
 */
public class SetupActivity extends LangActivity {
    static final String PREF_SETUP_DONE = "setup_done";
    static final String EXTRA_REVIEW = "review";
    /** Opens straight on the steps of one feature (a number from {@link FeaturePlan}), without the list of features. */
    static final String EXTRA_FEATURE = "feature";

    private static final int PICK = 0, STEP = 1, DONE = 2;

    @Override
    protected int look() {
        return LOOK_OWN;
    }

    private FrameLayout content;
    private LinearLayout foot;
    private int mode = PICK;
    private boolean review;
    private int direct = -1;
    private boolean busy;

    private final boolean[] picked = new boolean[FeaturePlan.FEATURES];
    private final View[] cards = new View[FeaturePlan.FEATURES];
    private final CheckView[] checks = new CheckView[FeaturePlan.FEATURES];

    /** The things still to ask, in order, and who asked for each. */
    private final List<Integer> queue = new ArrayList<>();
    private final Map<Integer, Integer> owner = new HashMap<>();
    private int at;
    /** The step whose Android page was opened, so coming back can look at whether it was allowed. */
    private int launched = -1;
    private boolean tried;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        review = getIntent().getBooleanExtra(EXTRA_REVIEW, false);
        direct = getIntent().getIntExtra(EXTRA_FEATURE, -1);
        if (direct >= FeaturePlan.FEATURES) direct = -1;
        Ui.screen(this, true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(dp(20), dp(8), dp(20), dp(14));
        root.addView(foot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Insets.pad(root);
        setContentView(root);

        if (direct >= 0) {
            picked[direct] = true;
            begin();
        } else {
            showPicker();
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.mode(true);
        checkReturn();
    }

    /** Back from one of Android's pages: carry on if the step was allowed, otherwise say so and let the person try again or skip. */
    private void checkReturn() {
        if (mode != STEP || launched < 0 || busy) return;
        int step = launched;
        if (Permissions.isOk(this, step)) {
            launched = -1;
            tried = false;
            advance();
        } else {
            tried = true;
            showStep(false);
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == Permissions.REQ_WALL) {
            WallpaperSetup.noteResult(this, result == RESULT_OK);
            // The wallpaper starts a moment after Android's preview closes: look again once it has.
            content.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (WallpaperSetup.lockOnly(this)) {
                    Lang.toast(this, "That set the lock screen only. Tap Set again and choose the home screen too.",
                            android.widget.Toast.LENGTH_LONG).show();
                }
                checkReturn();
            }, 1500);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (mode == STEP && direct < 0) {
            showPicker();
            return;
        }
        finishSetup();
    }

    // ------------------------------------------------------------------ pieces

    private LinearLayout body() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(20), dp(26), dp(20), dp(16));
        return b;
    }

    private void setPage(LinearLayout body) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.removeAllViews();
        content.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView words(String s, float sp, int color) {
        TextView t = Ui.label(this, s, sp, color, false);
        t.setLineSpacing(0, 1.14f);
        return t;
    }

    /** The big pill at the bottom, in the colour of what is being asked. */
    private Button cta(String text, int accent, View.OnClickListener click) {
        Button b = Ui.button(this, text, true, click);
        if (accent != 0) {
            b.setBackground(Ui.pressable(this, accent, 0, 26, 0x40000000));
            b.setTextColor(0xFF1B1405);
        }
        b.setMinHeight(dp(52));
        b.setMinimumHeight(dp(52));
        return b;
    }

    private TextView link(String text, int color, View.OnClickListener click) {
        TextView t = Ui.label(this, text, 15, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(12), dp(8), dp(12));
        t.setOnClickListener(click);
        return t;
    }

    private void setFoot(View... views) {
        foot.removeAllViews();
        for (View v : views) {
            foot.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /** Fades the old page out and the new one in, with a short slide. */
    private void swap(final boolean forward, final Runnable build) {
        if (busy) return;
        busy = true;
        final float shift = dp(26) * (forward ? 1 : -1);
        content.animate().alpha(0f).translationX(-shift).setDuration(110).withEndAction(() -> {
            build.run();
            content.setTranslationX(shift);
            content.animate().alpha(1f).translationX(0f).setDuration(170).withEndAction(() -> busy = false).start();
        }).start();
    }

    // ------------------------------------------------------------------ which features

    private void showPicker() {
        mode = PICK;
        launched = -1;
        tried = false;
        LinearLayout body = body();
        body.addView(Ui.eyebrow(this, review ? "Features" : "Set up"));
        body.addView(Ui.display(this, review ? "Add or fix a feature" : "What would you like Dinacharya to do?", 30, Ui.INK), Ui.wide(this, 6));
        body.addView(words(review
                ? "Turn on a feature and Dinacharya asks only for what that feature needs."
                : "Choose only what you want. Dinacharya asks your phone for a permission only when a feature you choose needs it, and tells you why first.",
                15, Ui.MUTED), Ui.wide(this, 10));
        for (int f = 0; f < FeaturePlan.FEATURES; f++) body.addView(featureCard(f), Ui.wide(this, 14));
        setPage(body);
        updatePickerFoot();
    }

    private View featureCard(final int f) {
        final boolean ready = Features.ready(this, f);
        final int accent = Features.accent(f);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(2), dp(2), dp(2), dp(2));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(24));
            }
        });

        FrameLayout top = new FrameLayout(this);
        top.addView(new FeatureArt(this, Features.scene(f)), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String badge = ready ? "On" : (f == FeaturePlan.F_ALARMS ? "Start here" : null);
        if (badge != null) {
            TextView chip = Ui.label(this, ready ? "✓  " + Lang.tr(badge) : badge, 11.5f, accent, true);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setBackground(Ui.box(this, 0xCC0B0C0F, 0, 12));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            lp.setMargins(dp(10), dp(10), 0, 0);
            top.addView(chip, lp);
        }
        card.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(14), dp(12), dp(12), dp(14));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.label(this, Features.title(f), 17, Ui.INK, true));
        TextView tag = words(Features.tagline(f), 13.5f, Ui.MUTED);
        text.addView(tag, Ui.wide(this, 3));
        String asks = Features.asks(f);
        if (!asks.isEmpty() && !ready) {
            TextView ask = Ui.label(this, Lang.tr("Asks for") + ": " + asks, 11.5f, accent, false);
            ask.setAlpha(0.9f);
            text.addView(ask, Ui.wide(this, 7));
        }
        info.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!ready) {
            CheckView check = new CheckView(this, accent);
            checks[f] = check;
            info.addView(check, Ui.lp(this, dp(30), dp(30), 0));
        }
        card.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cards[f] = card;
        paintCard(f);
        if (!ready) {
            card.setOnClickListener(v -> toggle(f));
        }
        return card;
    }

    private void paintCard(int f) {
        View card = cards[f];
        if (card == null) return;
        int accent = Features.accent(f);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        if (picked[f]) {
            g.setColor((accent & 0x00FFFFFF) | 0x26000000);
            g.setStroke(dp(2), accent);
        } else {
            g.setColor(0x14FFFFFF);
            g.setStroke(dp(1), 0x2BFFFFFF);
        }
        card.setBackground(g);
        if (checks[f] != null) checks[f].set(picked[f], true);
    }

    private void toggle(int f) {
        picked[f] = !picked[f];
        paintCard(f);
        updatePickerFoot();
    }

    private int pickedCount() {
        int n = 0;
        for (boolean p : picked) if (p) n++;
        return n;
    }

    private void updatePickerFoot() {
        int n = pickedCount();
        String label = n == 0 ? (review ? "Close" : "Skip for now") : n == 1 ? "Set up 1 feature" : "Set up " + n + " features";
        Button main = cta(label, 0, v -> {
            if (pickedCount() == 0) finishSetup();
            else begin();
        });
        TextView note = Ui.label(this, "You can add or change any feature later in Settings.", 12.5f, Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(10), 0, 0);
        setFoot(main, note);
    }

    // ------------------------------------------------------------------ the steps

    /** The features are chosen: remember them and start on what they need. */
    private void begin() {
        List<Integer> chosen = new ArrayList<>();
        for (int f = 0; f < FeaturePlan.FEATURES; f++) if (picked[f]) chosen.add(f);
        int mask = 0;
        owner.clear();
        queue.clear();
        for (int f : chosen) {
            mask |= FeaturePlan.bit(f);
            for (int s : Features.needs(f)) {
                if (!owner.containsKey(s)) owner.put(s, f);
            }
        }
        Features.choose(this, mask);
        final int sdk = Build.VERSION.SDK_INT;
        int[] arr = new int[chosen.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = chosen.get(i);
        for (int s : FeaturePlan.queue(arr, sdk, AlarmHealth.knownPhoneMaker())) {
            if (FeaturePlan.automatic(s)) {
                Permissions.request(this, s);   // only switches something on inside the app: no page, nothing asked of the phone
            } else if (!Permissions.isOk(this, s)) {
                queue.add(s);
            }
        }
        at = 0;
        launched = -1;
        tried = false;
        if (queue.isEmpty()) {
            mode = DONE;
            if (direct >= 0) showDone();
            else swap(true, this::showDone);
        } else if (direct >= 0) {
            showStep(true);
        } else {
            swap(true, () -> showStep(true));
        }
    }

    private void advance() {
        at++;
        launched = -1;
        tried = false;
        if (at >= queue.size()) swap(true, this::showDone);
        else swap(true, () -> showStep(true));
    }

    private void showStep(boolean fresh) {
        mode = STEP;
        final int step = queue.get(at);
        final int f = owner.containsKey(step) ? owner.get(step) : 0;
        final int accent = Features.stepAccent(step);
        LinearLayout body = body();

        // where we are: one small bar for each thing still to ask
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < queue.size(); i++) {
            View bar = new View(this);
            bar.setBackground(Ui.box(this, i <= at ? accent : 0x26FFFFFF, 0, 2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(4), 1f);
            if (i > 0) lp.leftMargin = dp(5);
            bars.addView(bar, lp);
        }
        body.addView(bars, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView eye = Ui.eyebrow(this, Features.title(f));
        eye.setTextColor(accent);
        head.addView(eye, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.label(this, (at + 1) + " / " + queue.size(), 12, Ui.MUTED, false));
        body.addView(head, Ui.wide(this, 16));

        body.addView(new FeatureArt(this, Features.stepScene(step)), Ui.wide(this, 12));
        body.addView(Ui.display(this, Features.stepTitle(step), 27, Ui.INK), Ui.wide(this, 18));
        body.addView(words(Features.stepWhy(step), 15.5f, Ui.MUTED), Ui.wide(this, 8));
        if (tried) {
            TextView hint = words("Not allowed yet. You can try again, or skip this for now.", 13.5f, Ui.AMBER);
            hint.setPadding(dp(12), dp(9), dp(12), dp(9));
            hint.setBackground(Ui.box(this, Ui.AMBER_SOFT, 0, 14));
            body.addView(hint, Ui.wide(this, 14));
        }
        final String extra = Features.stepExtra(this, step);
        if (extra != null) {
            TextView more = Ui.label(this, extra, 13.5f, accent, true);
            more.setPadding(0, dp(10), 0, dp(4));
            more.setOnClickListener(v -> Permissions.requestExtra(this, step));
            body.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        setPage(body);

        Button main = cta(tried ? "Try again" : Features.stepAction(step), accent, v -> {
            launched = step;
            Permissions.request(this, step);
            // asked for something that needs no Android page (it is already on): carry on
            if (Permissions.isOk(this, step) && step != Permissions.WALLPAPER) checkReturn();
        });
        TextView skip = link(FeaturePlan.optional(step) ? "Skip this one" : "Not now", Ui.MUTED, v -> {
            launched = -1;
            advanceSkipping();
        });
        setFoot(main, skip);
    }

    private void advanceSkipping() {
        tried = false;
        advance();
    }

    // ------------------------------------------------------------------ all set

    private void showDone() {
        mode = DONE;
        LinearLayout body = body();
        int n = pickedCount();
        body.addView(Ui.eyebrow(this, n == 0 ? "Setup" : "All set"));
        body.addView(new FeatureArt(this, FeatureScenes.DONE), Ui.wide(this, 14));
        body.addView(Ui.display(this, n == 0 ? "Nothing to set up yet" : "Your features are ready", 28, Ui.INK), Ui.wide(this, 18));
        body.addView(words(n == 0
                ? "That is fine. Add any feature later from Settings, and Dinacharya asks only for what it needs then."
                : "Here is what you chose. Anything that still needs a setting can be finished now or later.", 15.5f, Ui.MUTED), Ui.wide(this, 8));
        if (n > 0) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(6), dp(16), dp(6));
            card.setBackground(Ui.box(this, Ui.SURFACE, 0, 22));
            boolean first = true;
            for (int f = 0; f < FeaturePlan.FEATURES; f++) {
                if (!picked[f]) continue;
                final int feature = f;
                boolean ready = Features.ready(this, f);
                if (!first) card.addView(Ui.rule(this), Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, dp(1), 2));
                first = false;
                card.addView(Ui.statusRow(this, Features.title(f), ready ? "Ready" : "Needs one more setting", ready, "Finish", () -> {
                    for (int g = 0; g < picked.length; g++) picked[g] = g == feature;
                    begin();
                }));
            }
            body.addView(card, Ui.wide(this, 16));
        }
        setPage(body);
        setFoot(cta(review || direct >= 0 ? "Done" : "Continue", 0, v -> finishSetup()));
    }

    private void finishSetup() {
        Store.prefs(this).edit().putBoolean(PREF_SETUP_DONE, true).apply();
        if (!review && direct < 0) {
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

    // ------------------------------------------------------------------ the tick

    /** The round mark at the end of a feature card: an empty ring, or a filled circle with a tick that pops in. */
    private static final class CheckView extends View {
        private final int accent;
        private boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CheckView(android.content.Context c, int accent) {
            super(c);
            this.accent = accent;
        }

        void set(boolean on, boolean animate) {
            boolean changed = this.on != on;
            this.on = on;
            invalidate();
            if (changed && animate) {
                animate().cancel();
                setScaleX(0.8f);
                setScaleY(0.8f);
                animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f - 2f;
            float cx = w / 2f, cy = h / 2f;
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(accent);
                c.drawCircle(cx, cy, r, p);
                path.reset();
                path.moveTo(cx - r * 0.42f, cy + r * 0.02f);
                path.lineTo(cx - r * 0.1f, cy + r * 0.34f);
                path.lineTo(cx + r * 0.46f, cy - r * 0.3f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.22f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(0xFF1B1405);
                c.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
                p.setColor(0x66FFFFFF);
                c.drawCircle(cx, cy, r, p);
            }
        }
    }
}
