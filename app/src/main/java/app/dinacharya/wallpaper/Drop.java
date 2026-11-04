package app.dinacharya.wallpaper;

import android.animation.LayoutTransition;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One dropdown card: a coloured stripe, a small line above, a title, a short line of what is inside while it is closed, a small badge at
 * the right (a percentage, say) and an arrow. Tapping the header opens or closes it; the card's outline turns teal while it is open. At
 * least 48 dp tall, and says "expanded" or "collapsed" to screen readers. Use with {@link Accordion} so that only one is open.
 */
final class Drop implements Accordion.Foldable {
    final LinearLayout root;
    final LinearLayout body;
    private final Context c;
    private final LinearLayout header;
    private final TextView arrow;
    private final TextView summaryView;
    private final String title;
    private final String badgeWords;
    private Expander.Toggled listener;
    private boolean open;

    /**
     * @param stripe     the colour of the stripe at the left
     * @param above      a small line above the title (may be empty)
     * @param summary    the line under the title while the card is closed
     * @param badge      the badge text ("82%"), or empty for none
     * @param badgeNote  the small word under the badge ("focus")
     * @param badgeInk   the badge colours
     * @param badgeWords what the badge means, for screen readers ("82% focus")
     */
    Drop(Context c, int stripe, String above, String title, String summary, String badge, String badgeNote, int badgeInk, int badgeFill,
         String badgeWords) {
        this.c = c;
        this.title = title == null ? "" : title;
        this.badgeWords = badgeWords == null ? "" : badgeWords;

        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutTransition(new LayoutTransition());

        header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Ui.dp(c, 64));
        header.setClickable(true);
        header.setFocusable(true);
        header.setPadding(0, Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));

        View bar = new View(c);
        bar.setBackground(Ui.box(c, stripe, 0, 3));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(c, 5), ViewGroup.LayoutParams.MATCH_PARENT);
        bp.leftMargin = Ui.dp(c, 12);
        bp.rightMargin = Ui.dp(c, 12);
        header.addView(bar, bp);

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        if (above != null && !above.isEmpty()) words.addView(Ui.label(c, above, 12, Ui.MUTED, false));
        TextView t = Ui.label(c, this.title, 16, Ui.INK, true);
        t.setSingleLine(false);
        t.setMaxLines(2);
        words.addView(t, Ui.wide(c, above == null || above.isEmpty() ? 0 : 1));
        summaryView = Ui.label(c, summary == null ? "" : summary, 13, Ui.MUTED, false);
        summaryView.setSingleLine(false);
        summaryView.setMaxLines(2);
        words.addView(summaryView, Ui.wide(c, 3));
        header.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (badge != null && !badge.isEmpty()) {
            LinearLayout chip = new LinearLayout(c);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
            chip.setBackground(Ui.box(c, badgeFill, 0, 12));
            TextView v = Ui.label(c, badge, 16, badgeInk, true);
            v.setGravity(Gravity.CENTER);
            chip.addView(v);
            if (badgeNote != null && !badgeNote.isEmpty()) {
                TextView n = Ui.label(c, badgeNote, 10, badgeInk, false);
                n.setGravity(Gravity.CENTER);
                chip.addView(n);
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.leftMargin = Ui.dp(c, 8);
            chip.setMinimumWidth(Ui.dp(c, 56));
            header.addView(chip, cp);
        }

        arrow = Ui.label(c, "▾", 18, Ui.TEAL_DARK, true);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(c, 36), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 16), Ui.dp(c, 2), Ui.dp(c, 16), Ui.dp(c, 16));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header.setOnClickListener(v -> {
            setOpen(!open, true);
            if (listener != null) listener.toggled(open);
        });
        apply(false);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void setOpen(boolean on, boolean remember) {
        boolean animate = on != open;
        open = on;
        apply(animate);
    }

    @Override
    public void onToggled(Expander.Toggled l) {
        listener = l;
    }

    private void apply(boolean animate) {
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        summaryView.setVisibility(!open && summaryView.getText().length() > 0 ? View.VISIBLE : View.GONE);
        float rot = open ? 180f : 0f;
        if (animate) arrow.animate().rotation(rot).setDuration(160).start();
        else arrow.setRotation(rot);
        root.setBackground(Ui.box(c, Ui.SURFACE, open ? Ui.TEAL : Ui.LINE, 16));
        header.setBackground(Ui.pressable(c, 0x00000000, 0, 16, Ui.RIPPLE));
        String words = Lang.tr(title) + (badgeWords.isEmpty() ? "" : ", " + Lang.tr(badgeWords));
        header.setContentDescription(words + ", " + Lang.tr(open ? "expanded" : "collapsed"));
    }
}
