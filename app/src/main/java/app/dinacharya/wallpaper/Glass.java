package app.dinacharya.wallpaper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * The look of the slot screens: frosted glass over graphite (or white), big quiet type, pill buttons, round steppers and bottom
 * sheets, all drawn here (no system dialogs, buttons, switches or lists). Two answers are the main ones and are filled; the others are
 * glass; the rest are plain words. Colours and shapes live in this one file so every slot screen looks the same. The colours follow
 * the app colours (Graphite & Amber or White & Black): {@link Ui#mode} calls {@link #paint}.
 */
final class Glass {
    /** The dark ink that goes on a filled (white) pill; on White & Black the pill is black and this is white. */
    static int NAVY = 0xFF17171B;
    static int DEEP = 0xFF222227;
    /** Text and the filled pill: white on graphite, black on white. */
    static int WHITE = 0xFFFFFFFF;
    static int SOFT = 0xE6FFFFFF;
    static int MUTE = 0xB3FFFFFF;
    static int FAINT = 0x73FFFFFF;
    static int FILL = 0x1FFFFFFF;
    static int FILL_HI = 0x33FFFFFF;
    static int EDGE = 0x33FFFFFF;
    /** The accent: a switch that is on, a change worth noticing. */
    static int GREEN = 0xFFFFC66B;
    static int AMBER = 0xFFFFC66B;
    /** What a pressed pill ripples with, and what a pressed main pill ripples with. */
    static int RIPPLE = 0x33FFFFFF;
    static int RIPPLE_MAIN = 0x33000000;
    /** The edge of the answer that is picked, the dimmed screen behind a sheet, the sheet itself, its grip, and a text selection. */
    static int PICKED_EDGE = 0xCCFFFFFF;
    static int SCRIM = 0xB3050508;
    static int PANEL_TOP = 0xFA2A2A31;
    static int PANEL_BOT = 0xFA131317;
    static int GRIP = 0x4DFFFFFF;
    static int HIGHLIGHT = 0x55FFC66B;

    /** Sets the colours for the dark (Graphite & Amber) or the light (White & Black) look. {@link Ui#mode} calls it. */
    static void paint(boolean dark) {
        if (dark) {
            NAVY = 0xFF17171B; DEEP = 0xFF222227; WHITE = 0xFFFFFFFF; SOFT = 0xE6FFFFFF; MUTE = 0xB3FFFFFF; FAINT = 0x73FFFFFF;
            FILL = 0x1FFFFFFF; FILL_HI = 0x33FFFFFF; EDGE = 0x33FFFFFF; GREEN = 0xFFFFC66B; AMBER = 0xFFFFC66B;
            RIPPLE = 0x33FFFFFF; RIPPLE_MAIN = 0x33000000; PICKED_EDGE = 0xCCFFFFFF; SCRIM = 0xB3050508;
            PANEL_TOP = 0xFA2A2A31; PANEL_BOT = 0xFA131317; GRIP = 0x4DFFFFFF; HIGHLIGHT = 0x55FFC66B;
        } else {
            NAVY = 0xFFFFFFFF; DEEP = 0xFFF1F1EF; WHITE = 0xFF111111; SOFT = 0xE6111111; MUTE = 0xA6111111; FAINT = 0x66111111;
            FILL = 0x0F000000; FILL_HI = 0x1F000000; EDGE = 0x1F000000; GREEN = 0xFF111111; AMBER = 0xFFA86F00;
            RIPPLE = 0x22000000; RIPPLE_MAIN = 0x33FFFFFF; PICKED_EDGE = 0xCC111111; SCRIM = 0x66000000;
            PANEL_TOP = 0xFAFFFFFF; PANEL_BOT = 0xFAF1F1EF; GRIP = 0x33000000; HIGHLIGHT = 0x33000000;
        }
    }

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private Glass() {
    }

    static int dp(Context c, float v) {
        return Ui.dp(c, v);
    }

    // ------------------------------------------------------------------ type

    static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (medium) t.setTypeface(MEDIUM);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Big light numbers and titles. */
    static TextView display(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color, false);
        t.setTypeface(LIGHT);
        return t;
    }

    static TextView eyebrow(Context c, String s, int color) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.ROOT), 11, color, true);
        t.setLetterSpacing(0.16f);
        return t;
    }

    // ------------------------------------------------------------------ shapes

    static GradientDrawable shape(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    /** A background that ripples when pressed. */
    static RippleDrawable press(Context c, int fill, int stroke, float radiusDp, int ripple) {
        return new RippleDrawable(ColorStateList.valueOf(ripple), shape(c, fill, stroke, radiusDp), shape(c, 0xFFFFFFFF, 0, radiusDp));
    }

    /** A deep ground for a card: graphite (by night), or near black (the card that stands out on White & Black). */
    static GradientDrawable ground(Context c, boolean night, float radiusDp) {
        int[] colors = night ? new int[]{0xFF34343B, 0xFF1D1D22, 0xFF0D0D10} : new int[]{0xFF232326, 0xFF141416, 0xFF0A0A0B};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /**
     * The big card at the top of Home: on graphite a brighter pane of glass over the ground, on white a black card that stands out.
     * White words sit on both (the card is built with the dark colours, see {@link #paint}).
     */
    static GradientDrawable hero(Context c, boolean night, float radiusDp) {
        if (!night) return ground(c, false, radiusDp);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x38FFFFFF, 0x14FFFFFF});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), 0x40FFFFFF);
        return g;
    }

    /** True when the screens should look like night: the routine's theme says so, or it is automatic and the phone is in dark mode. */
    static boolean night(Routine r) {
        if (r == null) return true;
        if (r.theme == Routine.THEME_NIGHT) return true;
        if (r.theme == Routine.THEME_LIGHT) return false;
        return Ui.systemNight();
    }

    // ------------------------------------------------------------------ buttons

    private static TextView pill(Context c, String s, int textColor, int fill, int stroke, int ripple, float heightDp, float sp, View.OnClickListener click) {
        TextView t = text(c, s, sp, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, heightDp));
        t.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        t.setBackground(press(c, fill, stroke, heightDp / 2f, ripple));
        t.setClickable(true);
        t.setFocusable(true);
        t.setContentDescription(s);
        t.setOnClickListener(click);
        return t;
    }

    /** A main answer: filled white with dark words. */
    static TextView main(Context c, String s, View.OnClickListener click) {
        return pill(c, s, NAVY, WHITE, 0, RIPPLE_MAIN, 54, 16, click);
    }

    /** The other main answer, and the secondary ones that matter: glass with white words. */
    static TextView glass(Context c, String s, View.OnClickListener click) {
        return pill(c, s, WHITE, FILL, EDGE, RIPPLE, 54, 16, click);
    }

    /** A secondary answer: a smaller glass pill. */
    static TextView chip(Context c, String s, View.OnClickListener click) {
        return pill(c, s, SOFT, FILL, EDGE, RIPPLE, 38, 13.5f, click);
    }

    /** The quietest answer: plain words (Cancel, Back). */
    static TextView quiet(Context c, String s, View.OnClickListener click) {
        TextView t = text(c, s, 15, MUTE, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 44));
        t.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        t.setBackground(press(c, 0, 0, 22, RIPPLE));
        t.setClickable(true);
        t.setFocusable(true);
        t.setContentDescription(s);
        t.setOnClickListener(click);
        return t;
    }

    /** A button that cannot be pressed yet: the same shape, dimmed. */
    static void enable(TextView t, boolean on) {
        t.setEnabled(on);
        t.setAlpha(on ? 1f : 0.38f);
    }

    static LinearLayout.LayoutParams gap(Context c, int w, int h, float top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, top);
        return p;
    }

    static LinearLayout.LayoutParams wide(Context c, float top) {
        return gap(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top);
    }

    /** Buttons side by side, sharing the width equally. */
    static LinearLayout row(Context c, float gapDp, View... views) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) p.leftMargin = dp(c, gapDp);
            r.addView(views[i], p);
        }
        return r;
    }

    /** Chips that wrap onto another line when they do not fit. */
    static final class Flow extends ViewGroup {
        private final int gapPx;

        Flow(Context c, float gapDp) {
            super(c);
            gapPx = dp(c, gapDp);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int rowH = 0;
            int widest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                v.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = v.getMeasuredWidth();
                if (x > 0 && x + w > maxW) {
                    x = 0;
                    y += rowH + gapPx;
                    rowH = 0;
                }
                x += w + gapPx;
                rowH = Math.max(rowH, v.getMeasuredHeight());
                widest = Math.max(widest, x - gapPx);
            }
            setMeasuredDimension(resolveSize(widest + getPaddingLeft() + getPaddingRight(), wSpec),
                    y + rowH + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int y = 0;
            int rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                int w = v.getMeasuredWidth();
                if (x > 0 && x + w > maxW) {
                    x = 0;
                    y += rowH + gapPx;
                    rowH = 0;
                }
                v.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + v.getMeasuredHeight());
                x += w + gapPx;
                rowH = Math.max(rowH, v.getMeasuredHeight());
            }
        }
    }

    // ------------------------------------------------------------------ stepper

    /**
     * A round − and + with the number between them. Hold a button to run on. Never below {@code min}, never above {@code max}; moves by
     * {@code step}. The number can be shown in other words (a clock time).
     */
    static final class Stepper {
        interface Change {
            void changed(int value);
        }

        interface Words {
            String of(int value);
        }

        final LinearLayout view;
        private final TextView number;
        private final TextView unit;
        private final TextView minus;
        private final TextView plus;
        private final int min;
        private final int step;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private int max;
        private int value;
        private boolean active = true;
        private Change change;
        private Words words;

        Stepper(Context c, int initial, int min, int max, int step, String unitText, Change change) {
            this.min = min;
            this.max = Math.max(min, max);
            this.step = Math.max(1, step);
            this.change = change;
            this.value = fit(initial);
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
            view.setBackground(shape(c, FILL, EDGE, 40));
            minus = round(c, "−", false);
            plus = round(c, "+", true);
            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setGravity(Gravity.CENTER);
            number = display(c, "", 34, WHITE);
            number.setGravity(Gravity.CENTER);
            unit = text(c, unitText, 12, MUTE, true);
            unit.setGravity(Gravity.CENTER);
            mid.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mid.addView(unit, gap(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
            view.addView(minus, new LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)));
            view.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            view.addView(plus, new LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)));
            draw();
        }

        private int fit(int v) {
            int s = Math.round((v - min) / (float) step) * step + min;
            return Math.max(min, Math.min(max - ((max - min) % step), s));
        }

        private TextView round(Context c, String sign, final boolean up) {
            final TextView t = text(c, sign, 26, WHITE, false);
            t.setGravity(Gravity.CENTER);
            t.setBackground(press(c, FILL_HI, 0, 28, RIPPLE));
            t.setClickable(true);
            t.setContentDescription(Lang.tr(up ? "More" : "Less"));
            final Runnable[] run = new Runnable[1];
            run[0] = () -> {
                if (!move(up)) return;
                handler.postDelayed(run[0], 110);
            };
            t.setOnClickListener(v -> move(up));   // for a screen reader; a touch is handled below
            t.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (move(up)) handler.postDelayed(run[0], 420);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        handler.removeCallbacks(run[0]);
                        return true;
                    default:
                        return true;
                }
            });
            return t;
        }

        int value() {
            return value;
        }

        /** Changes the top of the range (when something else changed what is possible); the number is brought inside it. */
        void setMax(int newMax) {
            max = Math.max(min, newMax);
            value = fit(value);
            draw();
        }

        void setValue(int v) {
            value = fit(v);
            draw();
        }

        Stepper onChange(Change c) {
            change = c;
            return this;
        }

        Stepper words(Words w) {
            words = w;
            draw();
            return this;
        }

        void setUnit(String s) {
            unit.setText(s);
        }

        void setActive(boolean on) {
            active = on;
            draw();
        }

        private boolean move(boolean up) {
            if (!active) return false;
            int next = fit(value + (up ? step : -step));
            if (up && next <= value) return false;
            if (!up && next >= value) return false;
            value = next;
            draw();
            if (change != null) change.changed(value);
            return true;
        }

        private void draw() {
            number.setText(words == null ? String.valueOf(value) : words.of(value));
            view.setAlpha(active ? 1f : 0.4f);
            minus.setAlpha(active && value > min ? 1f : 0.3f);
            plus.setAlpha(active && fit(value + step) > value ? 1f : 0.3f);
        }
    }

    // ------------------------------------------------------------------ switch

    /** A glass on and off switch with its words, in one row. */
    static LinearLayout switchRow(Context c, String label, boolean on, final java.util.function.Consumer<Boolean> change) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 52));
        TextView t = text(c, label, 15, SOFT, true);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final FrameLayout track = new FrameLayout(c);
        final View knob = new View(c);
        final boolean[] state = {on};
        final int w = dp(c, 52);
        final int h = dp(c, 30);
        final int pad = dp(c, 3);
        track.addView(knob, new FrameLayout.LayoutParams(h - 2 * pad, h - 2 * pad, Gravity.CENTER_VERTICAL));
        final Runnable paint = () -> {
            track.setBackground(shape(c, state[0] ? GREEN : FILL_HI, 0, 15));
            knob.setBackground(shape(c, state[0] ? NAVY : 0xFFFFFFFF, 0, 15));
            knob.animate().translationX(state[0] ? w - h + pad : pad).setDuration(140).start();
        };
        track.setClickable(true);
        track.setFocusable(true);
        track.setContentDescription(label);
        track.setOnClickListener(v -> {
            state[0] = !state[0];
            paint.run();
            if (change != null) change.accept(state[0]);
        });
        row.addView(track, new LinearLayout.LayoutParams(w, h));
        row.setOnClickListener(v -> track.performClick());
        paint.run();
        knob.setTranslationX(state[0] ? w - h + pad : pad);
        return row;
    }

    // ------------------------------------------------------------------ choices

    /** One thing to pick among several: a glass row with a name, a few words under it and, when picked, a bright edge. */
    static final class Choices {
        interface Pick {
            void picked(int index);
        }

        final LinearLayout view;
        private final List<LinearLayout> rows = new ArrayList<>();
        private final List<TextView> marks = new ArrayList<>();
        private final Context c;
        private int selected = -1;
        private Pick pick;

        Choices(Context c, Pick pick) {
            this.c = c;
            this.pick = pick;
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.VERTICAL);
        }

        void add(String name, String detail, String side) {
            final int index = rows.size();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
            row.setClickable(true);
            row.setFocusable(true);
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(text(c, name, 16, WHITE, true));
            if (detail != null && !detail.isEmpty()) words.addView(text(c, detail, 12.5f, MUTE, false), gap(c, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 3));
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView mark = text(c, side == null ? "" : side, 12.5f, MUTE, true);
            mark.setGravity(Gravity.END);
            row.addView(mark, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(v -> select(index, true));
            rows.add(row);
            marks.add(mark);
            view.addView(row, wide(c, rows.size() == 1 ? 0 : 8));
            paint(index);
        }

        int selected() {
            return selected;
        }

        void select(int index, boolean notify) {
            if (index < 0 || index >= rows.size()) return;
            int old = selected;
            selected = index;
            if (old >= 0) paint(old);
            paint(index);
            if (notify && pick != null) pick.picked(index);
        }

        /** Changes the small words at the right of a row. */
        void side(int index, String s) {
            if (index >= 0 && index < marks.size()) marks.get(index).setText(s);
        }

        private void paint(int i) {
            boolean on = i == selected;
            rows.get(i).setBackground(press(c, on ? FILL_HI : FILL, on ? PICKED_EDGE : EDGE, 18, RIPPLE));
        }
    }

    /** Two to four choices in one glass strip; the picked one is filled white. */
    static final class Segments {
        interface Pick {
            void picked(int index);
        }

        final LinearLayout view;
        private final List<TextView> items = new ArrayList<>();
        private final Context c;
        private int selected = -1;
        private final Pick pick;

        Segments(Context c, Pick pick, String... names) {
            this.c = c;
            this.pick = pick;
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
            view.setBackground(shape(c, FILL, EDGE, 26));
            for (int i = 0; i < names.length; i++) {
                final int k = i;
                TextView t = text(c, names[i], 14.5f, SOFT, true);
                t.setGravity(Gravity.CENTER);
                t.setMinHeight(dp(c, 42));
                t.setClickable(true);
                t.setFocusable(true);
                t.setContentDescription(names[i]);
                t.setOnClickListener(v -> select(k, true));
                items.add(t);
                view.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
        }

        int selected() {
            return selected;
        }

        void select(int index, boolean notify) {
            if (index < 0 || index >= items.size()) return;
            selected = index;
            for (int i = 0; i < items.size(); i++) {
                TextView t = items.get(i);
                boolean on = i == index;
                t.setBackground(press(c, on ? WHITE : 0, 0, 22, RIPPLE));
                t.setTextColor(on ? NAVY : SOFT);
            }
            if (notify && pick != null) pick.picked(index);
        }

        void enable(int index, boolean on) {
            if (index >= 0 && index < items.size()) {
                items.get(index).setEnabled(on);
                items.get(index).setAlpha(on ? 1f : 0.35f);
            }
        }
    }


    // ------------------------------------------------------------------ pick one of a few words, type and name

    /** A wrapping row of chips of which one is picked (white), for types and sub types. */
    static final class Pick {
        interface Picked {
            void picked(int index);
        }

        final Flow view;
        private final Context c;
        private final List<TextView> chips = new ArrayList<>();
        private int selected = -1;
        private Picked picked;

        Pick(Context c) {
            this.c = c;
            view = new Flow(c, 8);
        }

        void set(List<String> names, int select, Picked picked) {
            this.picked = picked;
            view.removeAllViews();
            chips.clear();
            for (int i = 0; i < names.size(); i++) {
                final int k = i;
                TextView t = text(c, names.get(i), 14, SOFT, true);
                t.setGravity(Gravity.CENTER);
                t.setMinHeight(dp(c, 38));
                t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
                t.setSingleLine(true);
                t.setClickable(true);
                t.setFocusable(true);
                t.setContentDescription(names.get(i));
                t.setOnClickListener(v -> {
                    select(k);
                    if (this.picked != null) this.picked.picked(k);
                });
                chips.add(t);
                view.addView(t);
            }
            select(Math.max(0, Math.min(select, names.size() - 1)));
        }

        void select(int i) {
            selected = i;
            for (int k = 0; k < chips.size(); k++) {
                boolean on = k == i;
                TextView t = chips.get(k);
                t.setTextColor(on ? NAVY : SOFT);
                t.setBackground(press(c, on ? WHITE : FILL, on ? 0 : EDGE, 19, RIPPLE));
            }
        }

        int selected() {
            return selected;
        }
    }

    /** The type and sub type of an activity: it starts on the ones given. */
    static final class TypePick {
        final LinearLayout view;
        private final Pick kinds;
        private final Pick subs;
        private final Routine routine;
        private List<Routine.Sub> subList = new ArrayList<>();
        private int kind;

        TypePick(Context c, Routine routine, int curKind, String curSub) {
            this.routine = routine;
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.VERTICAL);
            kinds = new Pick(c);
            subs = new Pick(c);
            List<String> names = new ArrayList<>();
            for (int k = 0; k < Routine.SLEEP; k++) names.add(Routine.KIND_NAMES[k]);
            kind = curKind >= 0 && curKind < Routine.SLEEP ? curKind : Routine.TASK;
            view.addView(eyebrow(c, "Type", FAINT));
            view.addView(kinds.view, wide(c, 8));
            view.addView(eyebrow(c, "Sub type", FAINT), wide(c, 16));
            view.addView(subs.view, wide(c, 8));
            kinds.set(names, kind, i -> {
                kind = i;
                fillSubs("");
                if (changed != null) changed.run();
            });
            fillSubs(curSub);
            subs.picked = i -> {
                if (changed != null) changed.run();
            };
        }

        private Runnable changed;

        /** Runs whenever the type or the sub type is changed. */
        TypePick onChange(Runnable r) {
            changed = r;
            return this;
        }

        private void fillSubs(String want) {
            subList = routine.subsOf(kind);
            List<String> names = new ArrayList<>();
            int at = subList.size() - 1;
            for (int i = 0; i < subList.size(); i++) {
                names.add(subList.get(i).name);
                if (want != null && subList.get(i).name.equalsIgnoreCase(want)) at = i;
            }
            if (names.isEmpty()) names.add(Routine.OTHER);
            final Pick.Picked keep = subs.picked;
            subs.set(names, Math.max(0, at), null);
            subs.picked = keep;
        }

        int kind() {
            return kind;
        }

        String sub() {
            int i = subs.selected();
            return i >= 0 && i < subList.size() ? subList.get(i).name : Routine.OTHER;
        }
    }

    /** A text box that fits the glass: one line, big, no underline. */
    static android.widget.EditText field(Context c, String hint, String value) {
        android.widget.EditText e = new android.widget.EditText(c);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSelectAllOnFocus(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setTextColor(WHITE);
        e.setHintTextColor(FAINT);
        e.setTypeface(MEDIUM);
        e.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        e.setBackground(shape(c, FILL, EDGE, 18));
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setHighlightColor(HIGHLIGHT);
        return e;
    }

    /** Small words that explain a number or a result. */
    static TextView note(Context c, String s) {
        TextView t = text(c, s, 13.5f, MUTE, false);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    /** A glass box around some content (a note, a result). */
    static LinearLayout box(Context c) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        b.setBackground(shape(c, FILL, EDGE, 18));
        return b;
    }

    // ------------------------------------------------------------------ sheet

    /** A scroll view that stops growing at a height. */
    private static final class Capped extends ScrollView {
        private final int maxPx;

        Capped(Context c, int maxPx) {
            super(c);
            this.maxPx = maxPx;
            setOverScrollMode(OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(false);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST));
        }
    }

    private static final WeakHashMap<Activity, List<Sheet>> OPEN = new WeakHashMap<>();

    /**
     * A sheet that rises from the bottom over a dimmed screen: a title, a line under it and the content. {@link #step} swaps the content
     * for the next question without closing the sheet. Back closes the top sheet of an activity ({@link #back(Activity)}).
     */
    static final class Sheet {
        final Activity act;
        final LinearLayout body;
        private final FrameLayout overlay;
        private final View scrim;
        private final LinearLayout panel;
        private final LinearLayout content;
        private final TextView title;
        private final TextView sub;
        private boolean shown;
        private boolean closing;
        private boolean cancelable = true;
        private Runnable onClose;

        Sheet(Activity act, String titleText, String subText) {
            this.act = act;
            overlay = new FrameLayout(act);
            overlay.setClickable(true);
            scrim = new View(act);
            scrim.setBackgroundColor(SCRIM);
            scrim.setAlpha(0f);
            scrim.setOnClickListener(v -> {
                if (cancelable) dismiss();
            });
            overlay.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            panel = new LinearLayout(act);
            panel.setOrientation(LinearLayout.VERTICAL);
            float r = dp(act, 30);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{PANEL_TOP, PANEL_BOT});
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            bg.setStroke(Math.max(1, dp(act, 1)), EDGE);
            panel.setBackground(bg);
            panel.setClickable(true);

            View grab = new View(act);
            grab.setBackground(shape(act, GRIP, 0, 2));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(act, 38), dp(act, 4));
            gp.gravity = Gravity.CENTER_HORIZONTAL;
            gp.topMargin = dp(act, 10);
            panel.addView(grab, gp);
            watchDrag(grab);

            int maxH = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.84f);
            Capped scroll = new Capped(act, maxH);
            content = new LinearLayout(act);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(act, 22), dp(act, 14), dp(act, 22), dp(act, 22));
            title = display(act, titleText, 27, WHITE);
            sub = text(act, subText == null ? "" : subText, 14, MUTE, false);
            sub.setLineSpacing(dp(act, 2), 1f);
            content.addView(title);
            content.addView(sub, wide(act, 8));
            sub.setVisibility(subText == null || subText.isEmpty() ? View.GONE : View.VISIBLE);
            body = new LinearLayout(act);
            body.setOrientation(LinearLayout.VERTICAL);
            content.addView(body, wide(act, 20));
            scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            overlay.addView(panel, pp);
            Insets.watch(overlay, (l, t, rr, b) -> panel.setPadding(l, 0, rr, b));
        }

        Sheet cancelable(boolean yes) {
            cancelable = yes;
            return this;
        }

        Sheet onClose(Runnable r) {
            onClose = r;
            return this;
        }

        /** Swaps the heading and the content for the next question. */
        void step(String titleText, String subText, View newBody) {
            title.setText(titleText);
            sub.setText(subText == null ? "" : subText);
            sub.setVisibility(subText == null || subText.isEmpty() ? View.GONE : View.VISIBLE);
            body.removeAllViews();
            body.addView(newBody, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Lang.watch(panel);
            Lang.refresh(panel);
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(160).start();
        }

        void show() {
            if (shown) return;
            shown = true;
            ViewGroup root = act.findViewById(android.R.id.content);
            if (root == null) return;
            List<Sheet> list = OPEN.get(act);
            if (list == null) {
                list = new ArrayList<>();
                OPEN.put(act, list);
            }
            list.add(this);
            root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Lang.watch(panel);
            panel.setVisibility(View.INVISIBLE);
            overlay.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    overlay.getViewTreeObserver().removeOnPreDrawListener(this);
                    panel.setTranslationY(panel.getHeight() + dp(act, 40));
                    panel.setVisibility(View.VISIBLE);
                    scrim.animate().alpha(1f).setDuration(200).start();
                    panel.animate().translationY(0f).setDuration(280).setInterpolator(new DecelerateInterpolator(2f)).start();
                    return true;
                }
            });
        }

        boolean isCancelable() {
            return cancelable;
        }

        void dismiss() {
            if (closing || !shown) return;
            closing = true;
            scrim.animate().alpha(0f).setDuration(160).start();
            panel.animate().translationY(panel.getHeight() + dp(act, 40)).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    remove();
                }
            }).start();
        }

        private void remove() {
            try {
                ViewGroup p = (ViewGroup) overlay.getParent();
                if (p != null) p.removeView(overlay);
            } catch (RuntimeException ignored) {
                // the screen is already gone
            }
            List<Sheet> list = OPEN.get(act);
            if (list != null) list.remove(this);
            if (onClose != null) onClose.run();
        }

        /** Pulling the grip down closes the sheet. */
        private void watchDrag(View grip) {
            final float[] y0 = new float[1];
            View target = panel;
            target.setOnTouchListener(null);
            grip.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        y0[0] = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        panel.setTranslationY(Math.max(0f, e.getRawY() - y0[0]));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (cancelable && panel.getTranslationY() > dp(act, 90)) dismiss();
                        else panel.animate().translationY(0f).setDuration(160).start();
                        return true;
                    default:
                        return true;
                }
            });
            grip.setPadding(0, dp(act, 14), 0, dp(act, 14));
        }
    }

    /** Closes the sheet on top of an activity (the Back key); false when there is none or it must be answered. */
    static boolean back(Activity a) {
        List<Sheet> list = OPEN.get(a);
        if (list == null || list.isEmpty()) return false;
        Sheet top = list.get(list.size() - 1);
        if (top.isCancelable()) top.dismiss();
        return true;
    }

    /** True while a sheet is open over the activity. */
    static boolean isOpen(Activity a) {
        List<Sheet> list = OPEN.get(a);
        return list != null && !list.isEmpty();
    }

    /** A thin line of progress, filled to {@code frac}. */
    static View bar(Context c, final float frac, int trackColor, int fillColor) {
        final FrameLayout bar = new FrameLayout(c);
        bar.setBackground(shape(c, trackColor, 0, 4));
        final View fill = new View(c);
        fill.setBackground(shape(c, fillColor, 0, 4));
        bar.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        bar.post(() -> {
            // the fill gets its width once and grows by scaling, so the screen is not laid out again on every frame of the growth
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            lp.width = Math.round(bar.getWidth() * Math.max(0.02f, Math.min(1f, frac)));
            fill.setLayoutParams(lp);
            fill.setPivotX(0f);
            fill.setScaleX(0f);
            fill.animate().scaleX(1f).setDuration(500).start();
        });
        return bar;
    }
}
