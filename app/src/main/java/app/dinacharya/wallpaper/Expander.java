package app.dinacharya.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Parts of a screen that open and close, so a page shows what matters first and keeps the details one tap away.
 *
 * Every part has a small header (what it is, a short line of what is inside while it is closed, and an arrow) that is at least 48 dp
 * tall and says "expanded" or "collapsed" to screen readers. A part with a {@code key} remembers whether it was left open, and starts as
 * {@code openByDefault} the first time. Parts without a key are not remembered (for use inside dialogs).
 */
final class Expander {
    private static final String FILE = "dinacharya_ui";

    private Expander() {
    }

    interface Toggled {
        void toggled(boolean open);
    }

    /** One open-and-close part: add the header and the body to a layout, put the content in the body. */
    static final class Part {
        final LinearLayout root;
        final LinearLayout body;
        private final Context c;
        private final String key;
        private final TextView arrow;
        private final TextView summaryView;
        private final LinearLayout headerRow;
        private Toggled listener;
        private final String title;
        private String summary;
        private boolean open;

        Part(Context c, CharSequence eyebrow, CharSequence title, CharSequence summary, String key, boolean openByDefault,
             int eyebrowColor, int titleColor, int mutedColor, float titleSp) {
            this.c = c;
            this.key = key;
            this.title = (title == null || title.length() == 0 ? String.valueOf(eyebrow) : String.valueOf(title));
            this.summary = summary == null ? "" : summary.toString();
            this.open = key == null ? openByDefault : Expander.isOpen(c, key, openByDefault);

            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            Ui.animateChangesLater(root);   // the opening and closing glides; the first filling of the card does not

            LinearLayout row = new LinearLayout(c);
            headerRow = row;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(c, 48));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(Ui.pressable(c, 0x00000000, 0, 12, Ui.RIPPLE));

            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            if (eyebrow != null && eyebrow.length() > 0) {
                TextView e = Ui.label(c, String.valueOf(eyebrow).toUpperCase(java.util.Locale.ROOT), 11, eyebrowColor, true);
                e.setLetterSpacing(0.14f);
                words.addView(e);
            }
            if (title != null && title.length() > 0) {
                words.addView(Ui.label(c, title.toString(), titleSp, titleColor, true), Ui.wide(c, 3));
            }
            summaryView = Ui.label(c, this.summary, 13, mutedColor, false);
            summaryView.setSingleLine(false);
            summaryView.setMaxLines(2);
            words.addView(summaryView, Ui.wide(c, 2));
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            arrow = Ui.label(c, "▾", 18, eyebrowColor, true);
            arrow.setGravity(Gravity.CENTER);
            arrow.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 2), 0);
            row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(c, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(row, Ui.wide(c, 0));

            body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            root.addView(body, Ui.wide(c, 0));

            row.setOnClickListener(v -> {
                setOpen(!this.open, true);
                if (listener != null) listener.toggled(this.open);
            });
            apply(false);
        }

        /** Told when the person taps the header (not when the program opens or closes it). */
        void onToggled(Toggled l) {
            listener = l;
        }

        /** Draws the header as a rounded tonal button, for a choice on a screen that asks one thing at a time. */
        Part asButton() {
            headerRow.setBackground(Ui.pressable(c, Ui.TONAL, 0, 24, Ui.RIPPLE));
            headerRow.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 8), 0);
            return this;
        }

        boolean isOpen() {
            return open;
        }

        /** The short line shown under the title while the part is closed. */
        void setSummary(String s) {
            summary = s == null ? "" : s;
            summaryView.setText(summary);
            apply(false);
        }

        void setOpen(boolean on, boolean remember) {
            open = on;
            if (remember && key != null) save(c, key, on);
            apply(true);
        }

        private void apply(boolean animate) {
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            summaryView.setVisibility(!open && !summary.isEmpty() ? View.VISIBLE : View.GONE);
            float rot = open ? 180f : 0f;
            if (animate) arrow.animate().rotation(rot).setDuration(160).start();
            else arrow.setRotation(rot);
            View row = root.getChildAt(0);
            if (row != null) row.setContentDescription(Lang.tr(title) + ", " + Lang.tr(open ? "expanded" : "collapsed"));
        }
    }

    /** A white card for one section, like {@link Ui#section}, whose heading opens and closes it. Returns the part; fill its body. */
    static Part card(Context c, LinearLayout page, String eyebrow, String title, String summary, String key, boolean openByDefault) {
        Part p = new Part(c, eyebrow, title, summary, key, openByDefault, Ui.TEAL_DARK, Ui.INK, Ui.MUTED, 20);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 12));
        card.setBackground(Ui.box(c, Ui.SURFACE, 0, 22));
        card.addView(p.root, Ui.wide(c, 0));
        page.addView(card, Ui.wide(c, 16));
        return p;
    }

    /**
     * A quiet "more" row inside a card or dialog: a line of text with an arrow, and under it the details. Returns the part; fill its body
     * (the body has no padding of its own, so give its children the usual top margins).
     */
    static Part more(Context c, LinearLayout parent, String label, String key, boolean openByDefault) {
        Part p = new Part(c, null, label, null, key, openByDefault, Ui.TEAL_DARK, Ui.TEAL_DARK, Ui.MUTED, 14);
        parent.addView(p.root, Ui.wide(c, 6));
        return p;
    }

    /** A choice drawn as a tonal button that opens its details under it (not remembered). Returns the part; fill its body. */
    static Part choice(Context c, LinearLayout parent, String label) {
        Part p = new Part(c, null, label, null, null, false, Ui.TEAL_DARK, Ui.TEAL_DARK, Ui.MUTED, 15).asButton();
        parent.addView(p.root, Ui.wide(c, 12));
        return p;
    }

    /** The same on a dark (teal) card: white text. */
    static Part moreOnDark(Context c, LinearLayout parent, String label, String key, boolean openByDefault) {
        Part p = new Part(c, null, label, null, key, openByDefault, 0xE6FFFFFF, 0xFFFFFFFF, 0xCCFFFFFF, 14);
        parent.addView(p.root, Ui.wide(c, 10));
        return p;
    }

    // ------------------------------------------------------------------ what was left open

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean isOpen(Context c, String key, boolean def) {
        try {
            return prefs(c).getBoolean("open_" + key, def);
        } catch (RuntimeException e) {
            return def;
        }
    }

    static void save(Context c, String key, boolean open) {
        try {
            prefs(c).edit().putBoolean("open_" + key, open).apply();
        } catch (RuntimeException ignored) {
            // the part still opens and closes; it just starts as its default next time
        }
    }

    /** Forgets what was left open (every part starts as its default again). */
    static void reset(Context c) {
        try {
            prefs(c).edit().clear().apply();
        } catch (RuntimeException ignored) {
            // nothing to forget
        }
    }
}
