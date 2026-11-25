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
    // Every screen is frosted glass over a ground, in one of two app colours: Graphite & Amber (dark, the default) or White & Black
    // (light), or the one that follows the phone's own dark mode (System default). All three are free. The colours below are not
    // constants: they are set by mode() before a screen is built, so the whole app reads them the same way. "Night" in this file means
    // the dark one.

    /** The app colours: what is stored is one of these. */
    static final int COLOURS_GRAPHITE = 0;
    static final int COLOURS_PAPER = 1;
    /** Follows the phone: Graphite & Amber while the phone is in its dark mode, White & Black otherwise. */
    static final int COLOURS_SYSTEM = 2;

    /** True when the screens are in their dark colours (Graphite & Amber). */
    static boolean night = true;

    /** Text, and the quiet text under it. */
    static int INK = 0xF2FFFFFF;
    static int MUTED = 0xA8FFFFFF;
    /** The accent as a fill (selected chips, switches, marks): amber on graphite, black on white. {@link #ON_FILL} goes on it. */
    static int TEAL = 0xFFFFB84D;
    /** The accent as text or an icon on the glass. */
    static int TEAL_DARK = 0xFFFFC66B;
    /** The words and marks that sit on an accent fill (a selected chip, the Pro card, a tick). */
    static int ON_FILL = 0xFF1F1608;
    /** A hairline between rows. */
    static int LINE = 0x24FFFFFF;
    /** The glass of a card, a quieter glass for tiles and chips, and a tinted glass for what is selected or pressed. */
    static int SURFACE = 0x1AFFFFFF;
    static int SOFT = 0x1FFFFFFF;
    static int TONAL = 0x29FFFFFF;
    /** The bright edge of a piece of glass. */
    static int EDGE = 0x2BFFFFFF;
    /** The bars at the top and bottom, which are the most solid glass. */
    static int BAR = 0xB3101014;
    /** The main button: a white pill with dark words on graphite, a black pill with white words on white. */
    static int BTN = 0xFFFFFFFF;
    static int BTN_INK = 0xFF17171B;
    static int AMBER = 0xFFFFC66B;
    static int AMBER_SOFT = 0x33FFC66B;
    static int RED = 0xFFFF8F78;
    /** What a pressed piece of glass ripples with. */
    static int RIPPLE = 0x30FFFFFF;

    /** Sets the colours for dark (Graphite & Amber) or light (White & Black). Call it before building any view. */
    static void mode(boolean isNight) {
        night = isNight;
        if (isNight) {
            INK = 0xF2FFFFFF; MUTED = 0xA8FFFFFF; TEAL = 0xFFFFB84D; TEAL_DARK = 0xFFFFC66B; ON_FILL = 0xFF1F1608;
            LINE = 0x24FFFFFF; SURFACE = 0x1AFFFFFF; SOFT = 0x1FFFFFFF; TONAL = 0x29FFFFFF; EDGE = 0x2BFFFFFF; BAR = 0xB3101014;
            BTN = 0xFFFFFFFF; BTN_INK = 0xFF17171B;
            AMBER = 0xFFFFC66B; AMBER_SOFT = 0x33FFC66B; RED = 0xFFFF8F78; RIPPLE = 0x30FFFFFF;
        } else {
            INK = 0xFF111111; MUTED = 0xFF666666; TEAL = 0xFF111111; TEAL_DARK = 0xFF111111; ON_FILL = 0xFFFFFFFF;
            LINE = 0x1F000000; SURFACE = 0xD9FFFFFF; SOFT = 0x99FFFFFF; TONAL = 0x14000000; EDGE = 0x1A000000; BAR = 0xE6FFFFFF;
            BTN = 0xFF111111; BTN_INK = 0xFFFFFFFF;
            AMBER = 0xFFA86F00; AMBER_SOFT = 0x38E0A020; RED = 0xFFC4402B; RIPPLE = 0x22000000;
        }
        Glass.paint(isNight);
    }

    /** Sets the colours the routine's look asks for (its theme: day, night, or by the hour). */
    static void mode(Routine r) {
        mode(Glass.night(r));
    }

    /**
     * Whether the screens are in their dark colours: yes unless the person chose White & Black, or System default while the phone is in
     * its light mode. The routine's Light / Night / Auto look dresses the wallpaper only.
     */
    static boolean nightNow(Context c) {
        systemNight = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return colours(c) != COLOURS_PAPER;
    }

    private static final String KEY_COLOURS = "app_colours";

    /** What the person picked in Settings: Graphite & Amber, White & Black or System default. */
    static int chosenColours(Context c) {
        try {
            return pick(Store.prefs(c).getInt(KEY_COLOURS, COLOURS_GRAPHITE));
        } catch (RuntimeException e) {
            return COLOURS_GRAPHITE;
        }
    }

    private static int pick(int v) {
        return v == COLOURS_PAPER || v == COLOURS_SYSTEM ? v : COLOURS_GRAPHITE;
    }

    /** Saves the pick (the screens must then be restyled). */
    static void chooseColours(Context c, int which) {
        Store.prefs(c).edit().putInt(KEY_COLOURS, pick(which)).apply();
    }

    /** The colours in use now: the pick, with System default turned into the dark or the light one by the phone's own mode. */
    static int colours(Context c) {
        int p = chosenColours(c);
        if (p != COLOURS_SYSTEM) return p;
        return phoneNight(c) ? COLOURS_GRAPHITE : COLOURS_PAPER;
    }

    /** Whether the phone itself is in its dark mode right now. */
    static boolean phoneNight(Context c) {
        try {
            return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (RuntimeException e) {
            return true;
        }
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

    /** The ground of a whole screen: graphite (or soft paper) with a warm glow in one corner and a quiet one in the other. */
    static Drawable ground(Context c, boolean isNight) {
        int[] base = isNight ? new int[]{0xFF2E2D33, 0xFF1B1B20, 0xFF0C0C0F} : new int[]{0xFFF8F8F6, 0xFFEDEDEA, 0xFFDEDEDA};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, base);
        float w = c.getResources().getDisplayMetrics().widthPixels;
        GradientDrawable one = glow(isNight ? 0x24FFB84D : 0xCCFFFFFF, w * 0.95f, 1f, 0f);
        GradientDrawable two = glow(isNight ? 0x14FFFFFF : 0x14000000, w * 0.9f, 0f, 1f);
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

    /** A vivid pane for what should stand out (the Pro card): amber on graphite, black on white. {@link #ON_FILL} words go on it. */
    static GradientDrawable accent(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                night ? new int[]{0xFFFFC66B, 0xFFF29A3A} : new int[]{0xFF2B2B2E, 0xFF050505});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), night ? 0x59FFFFFF : 0x33000000);
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

    /** Primary buttons are the main pill (white on graphite, black on white); the others are glass. Both ripple when pressed. */
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
        TextView t = label(c, text.toUpperCase(java.util.Locale.ROOT), 11, TEAL_DARK, true);
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
        animateChangesLater(card);
        card.addView(eyebrow(c, eyebrow));
        if (title != null && !title.isEmpty()) card.addView(label(c, title, 20, INK, true), wide(c, 4));
        page.addView(card, wide(c, 16));
        return card;
    }

    /**
     * Lets the changes made to {@code box} later (a part shown or hidden) glide, but not the first filling of it: a card that is being
     * built, or built again, has all its parts added at once, and gliding each of them in (with every other one moving over) is what
     * makes a page stutter when it is switched to.
     */
    static void animateChangesLater(final ViewGroup box) {
        box.post(() -> box.setLayoutTransition(new LayoutTransition()));
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

    /** Draws a switch as glass: an accent track with a white thumb when it is on, a quiet track with a soft thumb when it is off. */
    static void glassSwitch(Switch s) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, night ? 0xFFD2D2D6 : 0xFFFFFFFF}));
        s.setTrackTintList(new ColorStateList(states, new int[]{TEAL, night ? 0x55FFFFFF : 0x33000000}));
    }

    /**
     * One line of a checklist: a tick or "!", the name, why it matters, and a button that fixes it.
     * {@code ok} shows an accent tick; otherwise an amber "!" and the button (when {@code fix} is given).
     */
    static LinearLayout statusRow(Context c, String name, String why, boolean ok, String action, final Runnable fix) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 8), 0, dp(c, 8));
        TextView mark = label(c, ok ? "✓" : "!", 16, ok ? ON_FILL : INK, true);
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
            btn.setBackground(pressable(c, TONAL, 0, 14, RIPPLE));
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
