package app.dinacharya.wallpaper;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** How your days are going: today so far, the last week, and every saved day. */
public class PerformanceActivity extends LangActivity {
    private static final int TAB_TODAY = 0, TAB_WEEK = 1, TAB_DAYS = 2;

    private final Button[] tabs = new Button[3];
    private LinearLayout body;
    private int tab = TAB_TODAY;
    private int generation;
    private DayReport todayReport;
    private AdSlot adSlot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = Ui.label(this, "Performance", 22, Ui.INK, true);
        title.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 4));
        root.addView(title);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 8));
        String[] names = {"Today", Pro.has(this, DayPass.REPORT_WEEK) ? "Week" : "Week · Pro", Pro.has(this, DayPass.REPORT_DAYS) ? "Days" : "Days · Pro"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = Ui.button(this, names[i], false, v -> select(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = Ui.dp(this, 4);
            lp.rightMargin = Ui.dp(this, 4);
            bar.addView(b, lp);
            tabs[i] = b;
        }
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        adSlot = new AdSlot(this, R.string.admob_banner_performance_id, () -> ProPrompt.open(this));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 8));
        root.addView(adSlot, ap);
        Insets.pad(root);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (body != null) select(tab);
        if (adSlot != null) adSlot.refresh();
    }

    @Override
    protected void onDestroy() {
        if (adSlot != null) adSlot.release();
        super.onDestroy();
    }

    private void select(int which) {
        tab = which;
        final int gen = ++generation;
        tabs[TAB_WEEK].setText(Pro.has(this, DayPass.REPORT_WEEK) ? "Week" : "Week · Pro");
        tabs[TAB_DAYS].setText(Pro.has(this, DayPass.REPORT_DAYS) ? "Days" : "Days · Pro");
        for (int i = 0; i < 3; i++) {
            boolean on = i == which;
            tabs[i].setTextColor(on ? Ui.ON_FILL : Ui.INK);
            tabs[i].setBackground(on ? Ui.box(this, Ui.TEAL, 0, 14) : Ui.box(this, Ui.SURFACE, Ui.LINE, 12));
        }
        body.removeAllViews();
        TextView wait = Ui.label(this, "Reading your day…", 14, Ui.MUTED, false);
        wait.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), 0);
        body.addView(wait);
        final boolean h24 = Store.load(this).fmt24;
        // The saved days and the week view are two items: a video for one does not open the other.
        final boolean daysOpen = Pro.has(this, DayPass.REPORT_DAYS);
        if (which == TAB_WEEK && !Limits.weekAllowed(Pro.has(this, DayPass.REPORT_WEEK))) {
            showLocked(DayPass.REPORT_WEEK, "The weekly view is part of Pro",
                    "The week chart, your average score and the tips that come from it. Today's report stays free.");
            return;
        }
        if (which == TAB_DAYS && !daysOpen) {
            showLocked(DayPass.REPORT_DAYS, "Saved days are part of Pro",
                    "Open the report of any past day, with its slot analysis and a PDF to save or share. Today's report stays free.");
            return;
        }
        new Thread(() -> {
            try {
                if (which == TAB_TODAY) {
                    final DayReport r = DayCloser.today(this);
                    runOnUiThread(() -> {
                        if (gen != generation) return;
                        todayReport = r;
                        showToday(r, h24);
                    });
                } else if (which == TAB_WEEK) {
                    HistoryStore h = Store.history(this);
                    List<DayReport> recent = h.recent(14);
                    final WeekStats w = WeekStats.of(recent, 7);
                    final List<String> tips = Suggestions.generate(null, recent, Store.load(this));
                    runOnUiThread(() -> {
                        if (gen != generation) return;
                        showWeek(w, tips, h24);
                    });
                } else {
                    final List<DayReport> all = Store.history(this).recent(60);
                    runOnUiThread(() -> {
                        if (gen != generation) return;
                        showDays(all, daysOpen);
                    });
                }
            } catch (RuntimeException e) {
                final String m = "Could not read the day: " + e.getMessage();
                runOnUiThread(() -> {
                    if (gen != generation) return;
                    body.removeAllViews();
                    TextView t = Ui.label(this, m, 14, Ui.RED, false);
                    t.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), 0);
                    body.addView(t);
                });
            }
        }, "perf-load").start();
    }

    private void showToday(final DayReport r, boolean h24) {
        body.removeAllViews();
        if (!r.phoneKnown) {
            Button grant = Ui.button(this, "Allow usage access to see phone time", false,
                    v -> UsageAccess.ask(this));
            body.addView(grant, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4));
            ((LinearLayout.LayoutParams) grant.getLayoutParams()).setMargins(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 8));
        }
        // the top of the report, then the slot analysis as dropdowns, then the table of slots and the suggestions
        ReportView head = new ReportView(this);
        head.showDay(r, h24, ReportRenderer.HEAD);
        body.addView(head, ReportView.lp(this));
        addSlotCards(r, h24);
        ReportView tail = new ReportView(this);
        tail.showDay(r, h24, ReportRenderer.TAIL);
        body.addView(tail, ReportView.lp(this));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 24));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a.rightMargin = Ui.dp(this, 8);
        row.addView(Ui.button(this, "Save PDF", true, v -> PdfActions.save(this, r)), a);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(Ui.button(this, "Share PDF", false, v -> PdfActions.share(this, r)), b);
        body.addView(row);
    }

    /** The slot-by-slot analysis of a day, as a card between the two halves of the report. */
    private void addSlotCards(DayReport r, boolean h24) {
        try {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 8));
            body.addView(Limits.analysisAllowed(Pro.has(this, DayPass.ANALYSIS)) ? SlotCards.build(this, r, h24)
                    : SlotCards.locked(this, () -> select(tab)), lp);
        } catch (RuntimeException e) {
            CrashLog.note(this, "slot analysis", e);
        }
    }

    private void showWeek(WeekStats w, List<String> tips, boolean h24) {
        body.removeAllViews();
        if (w.days.isEmpty()) {
            body.addView(empty("No finished days yet. After your first full day, a report is saved here and the week chart starts."));
            return;
        }
        ReportView rv = new ReportView(this);
        rv.showWeek(w, tips, h24);
        body.addView(rv, ReportView.lp(this));
    }

    /** A tab that is part of Pro: what it holds, and the button that opens the Pro screen. */
    private void showLocked(int item, String title, String what) {
        body.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.box(this, Ui.SURFACE, Ui.LINE, 14));
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        card.addView(Ui.label(this, title, 18, Ui.INK, true));
        TextView t = Ui.label(this, what, 14, Ui.MUTED, false);
        t.setLineSpacing(0, 1.15f);
        card.addView(t, Ui.wide(this, 8));
        card.addView(Ui.button(this, "See Pro", true, v -> ProPrompt.open(this)), Ui.wide(this, 14));
        ProPrompt.addOffer(this, card, 8, () -> select(tab));
        LinearLayout.LayoutParams lp = Ui.wide(this, 8);
        lp.leftMargin = Ui.dp(this, 14);
        lp.rightMargin = Ui.dp(this, 14);
        body.addView(card, lp);
    }

    private void showDays(List<DayReport> everyDay, boolean pro) {
        body.removeAllViews();
        if (everyDay.isEmpty()) {
            body.addView(empty("No saved days yet. A report is saved on this phone each time a schedule day ends."));
            return;
        }
        final int open = Limits.reportDays(pro, everyDay.size());
        final List<DayReport> all = everyDay.subList(0, open);
        AccountStore acc = new AccountStore(this);
        boolean cloud = acc.signedIn();
        long backedUp = acc.lastOk();
        for (final DayReport r : all) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.box(this, Ui.SURFACE, Ui.LINE, 14));
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(Ui.label(this, prettyDay(r.day), 16, Ui.INK, true));
            String sub = (r.phoneKnown ? "Phone " + hm(r.phoneMs) : "Phone not measured")
                    + (cloud ? (backedUp >= r.endMs ? "  ·  backed up" : "  ·  not backed up yet") : "");
            left.addView(Ui.label(this, sub, 12, Ui.MUTED, false));
            card.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView score = Ui.label(this, r.score >= 0 ? String.valueOf(r.score) : "–", 24, scoreColor(r.score), true);
            card.addView(score);
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, ReportActivity.class);
                i.putExtra(ReportActivity.EXTRA_DAY, r.day);
                startActivity(i);
            });
            LinearLayout.LayoutParams lp = Ui.wide(this, 8);
            lp.leftMargin = Ui.dp(this, 14);
            lp.rightMargin = Ui.dp(this, 14);
            body.addView(card, lp);
        }
        View pad = new View(this);
        body.addView(pad, new LinearLayout.LayoutParams(1, Ui.dp(this, 24)));
    }

    private TextView empty(String msg) {
        TextView t = Ui.label(this, msg, 15, Ui.MUTED, false);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), 0);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private static int scoreColor(int s) {
        if (s < 0) return Ui.MUTED;
        if (s >= 70) return Ui.TEAL_DARK;
        if (s >= 45) return Ui.AMBER;
        return Ui.RED;
    }

    private static String hm(long ms) {
        long m = Math.round(ms / 60000.0);
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    static String prettyDay(String iso) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(iso);
            return d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Lang.locale())
                    + ", " + d.getDayOfMonth() + " " + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Lang.locale());
        } catch (RuntimeException e) {
            return iso;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PdfActions.REQ_SAVE && resultCode == RESULT_OK && data != null && todayReport != null) {
            Uri u = data.getData();
            PdfActions.saved(this, todayReport, u);
        }
    }
}
