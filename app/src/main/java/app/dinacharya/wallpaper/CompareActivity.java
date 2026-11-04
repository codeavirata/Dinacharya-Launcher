package app.dinacharya.wallpaper;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The last screen of the tour, also opened from the Pro screen: everything the app does on one page, Free and Pro side by side. The two
 * columns are named at the top; the features are in groups that open one at a time, each showing what Free gives and what Pro gives in
 * two columns. At the bottom: three short videos for a day of Pro, and the way to Pro itself. The words come from {@link PlanTable}.
 */
public class CompareActivity extends LangActivity {
    /** Set when this is the last step of the first-run tour, so that closing it opens the dashboard. */
    static final String EXTRA_FIRST_RUN = TourActivity.EXTRA_FIRST_RUN;

    private LinearLayout body;
    private int openGroup = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        if (b != null) openGroup = b.getInt("open", -1);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Insets.pad(sv);
        setContentView(sv);
        Pro.get(this).start();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("open", openGroup);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();   // Pro may have been bought, or a day of Pro may have started, on the screen in between
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        close();
    }

    private void close() {
        if (getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false)) {
            try {
                startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, "ready"));
            } catch (RuntimeException e) {
                CrashLog.note(this, "compare to dashboard", e);
            }
        }
        finish();
    }

    private void render() {
        if (body == null) return;
        try {
            body.removeAllViews();
            final Pro p = Pro.get(this);
            final boolean owned = p.owned();
            body.addView(Ui.eyebrow(this, "Dinacharya"));
            body.addView(Ui.label(this, "Free and Pro", 28, Ui.INK, true), Ui.wide(this, 4));
            TextView sub = Ui.label(this, "Everything the app does, side by side. Open a group to see what each one gives.", 15, Ui.MUTED, false);
            sub.setLineSpacing(Ui.dp(this, 2), 1f);
            body.addView(sub, Ui.wide(this, 6));

            // the two columns, named
            LinearLayout heads = new LinearLayout(this);
            heads.setOrientation(LinearLayout.HORIZONTAL);
            heads.addView(head("Free", "Your whole day, and today's report", false), column());
            heads.addView(head("Pro", owned ? "You have Pro. Thank you!" : "Everything, and no ads", true), columnRight());
            body.addView(heads, Ui.wide(this, 18));

            // the groups, one open at a time
            final Accordion acc = new Accordion();
            List<PlanTable.Group> groups = PlanTable.groups();
            for (PlanTable.Group g : groups) {
                Expander.Part part = Expander.card(this, body, "", g.name, g.summary, null, false);
                acc.add(part);
                fillGroup(part.body, g);
            }
            acc.onChanged(() -> openGroup = acc.open());
            acc.show(openGroup);

            // the way to Pro
            LinearLayout card = Ui.section(this, body, owned ? "Pro" : "Try it, or keep it", owned ? "You have Pro" : "Try Pro free for a day");
            if (owned) {
                card.addView(small("Everything in this list is open for you, and there are no ads."), Ui.wide(this, 6));
            } else {
                card.addView(small("Watch " + VideoProgress.NEED + " short videos and everything in Pro is open for 24 hours. Nothing is charged. "
                        + "When the day is up, what you set up with it goes back to the free version."), Ui.wide(this, 6));
                ProPrompt.addOffer(this, card, 12, this::render);
                card.addView(Ui.button(this, "See Pro plans", true, v -> ProPrompt.open(this)), Ui.wide(this, 10));
            }
            body.addView(Ui.button(this, getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false) ? "Done" : "Close", false, v -> close()),
                    Ui.wide(this, 16));
        } catch (RuntimeException e) {
            CrashLog.note(this, "free and pro screen", e);
        }
    }

    private LinearLayout.LayoutParams column() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.rightMargin = Ui.dp(this, 5);
        return lp;
    }

    private LinearLayout.LayoutParams columnRight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = Ui.dp(this, 5);
        return lp;
    }

    /** The heading of one column: its name large, and one line under it. Pro is the filled one. */
    private LinearLayout head(String name, String line, boolean pro) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        c.setBackground(pro ? Ui.accent(this, 22) : Ui.box(this, Ui.SURFACE, Ui.LINE, 22));
        c.addView(Ui.label(this, name, 24, pro ? 0xFFFFFFFF : Ui.INK, true));
        TextView t = Ui.label(this, line, 13, pro ? 0xE6FFFFFF : Ui.MUTED, false);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        c.addView(t, Ui.wide(this, 4));
        return c;
    }

    /** The rows of one group: the feature, and under it what Free gives next to what Pro gives. */
    private void fillGroup(LinearLayout into, PlanTable.Group g) {
        for (int i = 0; i < g.rows.size(); i++) {
            PlanTable.Row r = g.rows.get(i);
            TextView what = Ui.label(this, r.what, 14, Ui.INK, true);
            what.setLineSpacing(Ui.dp(this, 2), 1f);
            into.addView(what, Ui.wide(this, i == 0 ? 4 : 14));
            LinearLayout cells = new LinearLayout(this);
            cells.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            l.rightMargin = Ui.dp(this, 4);
            LinearLayout.LayoutParams rt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            rt.leftMargin = Ui.dp(this, 4);
            cells.addView(cell(r.free, false, r.inFree()), l);
            cells.addView(cell(r.pro, true, true), rt);
            into.addView(cells, Ui.wide(this, 6));
        }
    }

    /** One cell: a tick and the words, on a soft ground (tonal for Pro). A thing the plan does not have is muted, with no tick. */
    private TextView cell(String text, boolean pro, boolean has) {
        TextView t = Ui.label(this, has ? "✓  " + Lang.tr(text) : text, 13, has ? (pro ? Ui.TEAL_DARK : Ui.INK) : Ui.MUTED, has);
        if (has) Lang.keep(t);   // the tick and the words are put together here, already in the language
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(Ui.dp(this, 40));
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        t.setBackground(Ui.box(this, pro ? Ui.TONAL : Ui.SOFT, 0, 12));
        return t;
    }

    private TextView small(String s) {
        TextView t = Ui.label(this, s, 14, Ui.MUTED, false);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        return t;
    }
}
