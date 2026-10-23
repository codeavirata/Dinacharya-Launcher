package app.dinacharya.wallpaper;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Small helpers for building screens in code, shared by the activities. */
final class Ui {
    static final int INK = 0xFF10201E;
    static final int MUTED = 0xFF5E7573;
    static final int TEAL = 0xFF0E7C7B;
    static final int TEAL_DARK = 0xFF0A5F5E;
    static final int LINE = 0xFFDCE6E4;
    static final int SURFACE = 0xFFFFFFFF;
    static final int SOFT = 0xFFF2F7F6;
    static final int TONAL = 0xFFE0F0EE;
    static final int AMBER = 0xFFC98A12;
    static final int AMBER_SOFT = 0xFFFDF4E2;
    static final int RED = 0xFFC4553B;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** A background that shows a ripple when pressed. */
    static Drawable pressable(Context c, int fill, int stroke, float radiusDp, int rippleColor) {
        GradientDrawable mask = box(c, 0xFFFFFFFF, 0, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), box(c, fill, stroke, radiusDp), mask);
    }

    static TextView label(Context c, String text, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Primary buttons are filled teal; the others are a soft tint. Both ripple when pressed. */
    static Button button(Context c, String text, boolean primary, View.OnClickListener click) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.WHITE : TEAL_DARK);
        b.setBackground(primary ? pressable(c, TEAL, 0, 24, 0x40FFFFFF) : pressable(c, TONAL, 0, 24, 0x300E7C7B));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        b.setOnClickListener(click);
        return b;
    }

    /** A round icon-sized button, for ＋, ✕, − and the like. */
    static Button iconButton(Context c, String text, int color, View.OnClickListener click) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        b.setBackground(pressable(c, SOFT, 0, 20, 0x300E7C7B));
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40));
        p.leftMargin = dp(c, 6);
        b.setLayoutParams(p);
        b.setOnClickListener(click);
        return b;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static LinearLayout.LayoutParams wide(Context c, float topDp) {
        return lp(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, topDp);
    }

    static LinearLayout.LayoutParams wrap(Context c, float topDp) {
        return lp(c, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, topDp);
    }

    static TextView eyebrow(Context c, String text) {
        TextView t = label(c, text.toUpperCase(java.util.Locale.ROOT), 11, TEAL, true);
        t.setLetterSpacing(0.14f);
        return t;
    }

    /**
     * A white card for one section of a page, with its small heading and title already in it.
     * Returns the card; add the section's content to it.
     */
    static LinearLayout section(Context c, LinearLayout page, String eyebrow, String title) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 18));
        card.setBackground(box(c, SURFACE, 0, 22));
        card.setElevation(dp(c, 1.5f));
        card.setLayoutTransition(new LayoutTransition());
        card.addView(eyebrow(c, eyebrow));
        if (title != null && !title.isEmpty()) card.addView(label(c, title, 20, INK, true), wide(c, 4));
        page.addView(card, wide(c, 16));
        return card;
    }

    /** A heading for a part of the page that is not a card (the routine list). */
    static void heading(Context c, LinearLayout page, String eyebrow, String title) {
        page.addView(eyebrow(c, eyebrow), wrap(c, 26));
        page.addView(label(c, title, 20, INK, true), wide(c, 4));
    }

    /** An on/off switch with its words on the left. */
    static Switch toggle(Context c, String text, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setText(text);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        s.setTextColor(INK);
        s.setChecked(on);
        s.setMinHeight(dp(c, 48));
        s.setMinimumHeight(dp(c, 48));
        s.setOnCheckedChangeListener(l);
        return s;
    }

    /**
     * One line of a checklist: a tick or "!", the name, why it matters, and a button that fixes it.
     * {@code ok} shows a teal tick; otherwise an amber "!" and the button (when {@code fix} is given).
     */
    static LinearLayout statusRow(Context c, String name, String why, boolean ok, String action, final Runnable fix) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 8), 0, dp(c, 8));
        TextView mark = label(c, ok ? "✓" : "!", 16, ok ? Color.WHITE : INK, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(box(c, ok ? TEAL : AMBER_SOFT, ok ? 0 : AMBER, 16));
        row.addView(mark, new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(c, 12), 0, dp(c, 8), 0);
        words.addView(label(c, name, 15, INK, true));
        words.addView(label(c, why, 12, MUTED, false));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!ok && fix != null) {
            TextView btn = label(c, action, 14, TEAL, true);
            btn.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
            btn.setBackground(pressable(c, TONAL, 0, 14, 0x300E7C7B));
            btn.setOnClickListener(v -> fix.run());
            row.addView(btn);
        }
        return row;
    }

    /** A thin divider line. */
    static View rule(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        return v;
    }
}
