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
    // ------------------------------------------------------------------ the glass look
    // Every screen is frosted glass over a gradient ground, in a night version (the default) or a day version. The colours below
    // are not constants: they are set by mode() before a screen is built, so the whole app reads them the same way.

    /** True when the screens are in their night colours. */
    static boolean night = true;

    /** Text, and the quiet text under it. */
    static int INK = 0xF2FFFFFF;
    static int MUTED = 0xA8FFFFFF;
    /** The brand teal as a fill (selected chips, switches, marks), always with white on it. */
    static int TEAL = 0xFF17938F;
    /** The same teal as text or an icon on the glass: mint at night, deep teal by day. */
    static int TEAL_DARK = 0xFF7FE0C3;
    /** A hairline between rows. */
    static int LINE = 0x24FFFFFF;
    /** The glass of a card, a quieter glass for tiles and chips, and a tinted glass for what is selected or pressed. */
    static int SURFACE = 0x1AFFFFFF;
    static int SOFT = 0x1FFFFFFF;
    static int TONAL = 0x29FFFFFF;
    /** The bright edge of a piece of glass. */
    static int EDGE = 0x2BFFFFFF;
    /** The bars at the top and bottom, which are the most solid glass. */
    static int BAR = 0xB3081424;
    /** The main button: a white pill at night with dark words, a teal pill by day with white words. */
    static int BTN = 0xFFFFFFFF;
    static int BTN_INK = 0xFF0B1A2A;
    static int AMBER = 0xFFFFC66B;
    static int AMBER_SOFT = 0x33FFC66B;
    static int RED = 0xFFFF8F78;
    /** What a pressed piece of glass ripples with. */
    static int RIPPLE = 0x30FFFFFF;

    /** Sets the colours for night or day. Call it before building any view. */
    static void mode(boolean isNight) {
        night = isNight;
        if (isNight) {
            INK = 0xF2FFFFFF; MUTED = 0xA8FFFFFF; TEAL = 0xFF17938F; TEAL_DARK = 0xFF7FE0C3;
            LINE = 0x24FFFFFF; SURFACE = 0x1AFFFFFF; SOFT = 0x1FFFFFFF; TONAL = 0x29FFFFFF; EDGE = 0x2BFFFFFF; BAR = 0xB3081424;
            BTN = 0xFFFFFFFF; BTN_INK = 0xFF0B1A2A;
            AMBER = 0xFFFFC66B; AMBER_SOFT = 0x33FFC66B; RED = 0xFFFF8F78; RIPPLE = 0x30FFFFFF;
        } else {
            INK = 0xFF10201E; MUTED = 0xFF4A6462; TEAL = 0xFF0E7C7B; TEAL_DARK = 0xFF0A5F5E;
            LINE = 0x2610201E; SURFACE = 0x8CFFFFFF; SOFT = 0x66FFFFFF; TONAL = 0x290E7C7B; EDGE = 0xB3FFFFFF; BAR = 0xA6FFFFFF;
            BTN = 0xFF0E7C7B; BTN_INK = 0xFFFFFFFF;
            AMBER = 0xFFA86F00; AMBER_SOFT = 0x38E0A020; RED = 0xFFC4553B; RIPPLE = 0x300E7C7B;
        }
    }

    /** Sets the colours the routine's look asks for (its theme: day, night, or by the hour). */
    static void mode(Routine r) {
        mode(Glass.night(r));
    }

    /**
     * Whether the screens are in night colours: always. The app is dark glass; the routine's Light / Night / Auto look dresses the
     * wallpaper only. (The day colours stay in {@link #mode} for the home screen's own light theme.)
     */
    static boolean nightNow(Context c) {
        systemNight = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return true;
    }

    private static boolean systemNight = true;

    /** Whether the phone itself is in its dark mode (what the routine's Auto look follows, as the wallpaper does). */
    static boolean systemNight() {
        return systemNight;
    }

    /**
     * Picks the theme of a screen (day or night glass). Call it before {@code super.onCreate}; it also sets the colours, and returns
     * whether it is night.
     */
    static boolean theme(android.app.Activity a) {
        boolean n = nightNow(a);
        a.setTheme(n ? R.style.AppNight : R.style.AppDay);
        mode(n);
        return n;
    }

    /**
     * Dresses a screen: the colours, the gradient ground behind everything, and see-through system bars with icons that read on it.
     * Call it first in {@code onCreate} (and again in {@code onResume}, since another screen may have set the other mode).
     */
    static void screen(android.app.Activity a, boolean isNight) {
        mode(isNight);
        try {
            android.view.Window w = a.getWindow();
            w.setBackgroundDrawable(ground(a, isNight));
            w.setStatusBarColor(0);
            w.setNavigationBarColor(0);
            Insets.bars(a, !isNight);
        } catch (RuntimeException ignored) {
            // the screen keeps the theme's own background
        }
    }

    static void screen(android.app.Activity a, Routine r) {
        screen(a, Glass.night(r));
    }

    /** The ground of a whole screen: a slow diagonal from teal into navy (or into soft blue-grey by day) with two soft glows. */
    static Drawable ground(Context c, boolean isNight) {
        int[] base = isNight ? new int[]{0xFF1B5E66, 0xFF16384F, 0xFF0D1B30} : new int[]{0xFFBFD9DD, 0xFFA8C4D9, 0xFF8FB0CE};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, base);
        float w = c.getResources().getDisplayMetrics().widthPixels;
        GradientDrawable one = glow(isNight ? 0x38A8F0D6 : 0xB3FFFFFF, w * 0.95f, 1f, 0f);
        GradientDrawable two = glow(isNight ? 0x334678DC : 0x4D5A8CC8, w * 0.9f, 0f, 1f);
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{g, one, two});
    }

    private static GradientDrawable glow(int color, float radiusPx, float cx, float cy) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color, color & 0x00FFFFFF});
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(radiusPx);
        return g;
    }

    /** The style of the dialogs for the mode: frosted glass, dark at night and light by day. */
    static int dialogTheme() {
        return night ? R.style.GlassDialogNight : R.style.GlassDialogDay;
    }

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        if (stroke == 0 && radiusDp >= 10 && (fill == SURFACE || fill == SOFT)) stroke = EDGE;   // a piece of glass has a bright edge
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** A vivid pane of glass, teal into blue, for what should stand out (the Pro card). White words go on it. */
    static GradientDrawable accent(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                night ? new int[]{0xE01E9E96, 0xE02B6DB5} : new int[]{0xF20E7C7B, 0xF22C6EA6});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), 0x59FFFFFF);
        return g;
    }

    /** A big, light title. */
    static TextView display(Context c, String text, float sp, int color) {
        TextView t = label(c, text, sp, color, false);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return t;
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

    /** Primary buttons are the main pill (white at night, teal by day); the others are glass. Both ripple when pressed. */
    static Button button(Context c, String text, boolean primary, View.OnClickListener click) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? BTN_INK : INK);
        b.setBackground(primary ? pressable(c, BTN, 0, 24, 0x40808080) : pressable(c, TONAL, EDGE, 24, RIPPLE));
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
        b.setBackground(pressable(c, SOFT, EDGE, 20, RIPPLE));
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
        card.setBackground(box(c, SURFACE, 0, 24));
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
        glassSwitch(s);
        return s;
    }

    /** Draws a switch as glass: a teal track with a white thumb when it is on, a quiet track with a soft thumb when it is off. */
    static void glassSwitch(Switch s) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, night ? 0xFFC9D6D8 : 0xFF6F8584}));
        s.setTrackTintList(new ColorStateList(states, new int[]{TEAL, night ? 0x55FFFFFF : 0x40000000}));
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
