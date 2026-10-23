package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Insights tab: today's score and where the time went (a ring chart by type), goals with streaks, every slot so far with the apps
 * used in it, phone use by hour and by app, time per activity type and habit notes.
 */
final class InsightsPage extends Page {
    private LinearLayout todayBox;
    private LinearLayout goalsBox;
    private LinearLayout slotsBox;
    private LinearLayout phoneBox;
    private LinearLayout typesBox;
    private int generation;

    private AdSlot adSlot;

    InsightsPage(MainActivity host) {
        super(host);
    }

    @Override
    void destroy() {
        if (adSlot != null) adSlot.release();
    }

    @Override
    void build(LinearLayout body) {
        title("Insights", "How your days are going");
        todayBox = new LinearLayout(a);
        todayBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(todayBox, Ui.wide(a, 0));
        goalsBox = new LinearLayout(a);
        goalsBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(goalsBox, Ui.wide(a, 0));
        slotsBox = new LinearLayout(a);
        slotsBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(slotsBox, Ui.wide(a, 0));
        phoneBox = new LinearLayout(a);
        phoneBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(phoneBox, Ui.wide(a, 0));
        typesBox = new LinearLayout(a);
        typesBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(typesBox, Ui.wide(a, 0));
        a.extras.buildHabits(body);
        adSlot = new AdSlot(a, a::openPro);
        body.addView(adSlot, Ui.wide(a, 16));
        LinearLayout rep = Ui.section(a, body, "Reports", "Every day, saved");
        rep.addView(a.note("When a day ends its report is saved on this phone: score, phone use by hour and app, time per type, "
                + "sleep and every slot. Open any day to save or share it as a PDF."), Ui.wide(a, 4));
        rep.addView(Ui.button(a, "Performance and reports", true, v -> {
            try {
                a.startActivity(new Intent(a, PerformanceActivity.class));
            } catch (RuntimeException e) {
                a.toastText("Could not open Performance");
            }
        }), Ui.wide(a, 12));
        a.addFooter(body);
    }

    @Override
    void refresh() {
        if (!built()) return;
        final int gen = ++generation;
        if (adSlot != null) adSlot.refresh();
        final Context app = a.getApplicationContext();
        final Routine m = a.routine();
        if (m.pruneGoals(LocalDate.now(Store.zone()))) a.persist();   // one-day goals of days gone by
        new Thread(() -> {
            DayReport today = null;
            List<DayReport> past = new ArrayList<>();
            try {
                today = DayCloser.today(app);
                past = Store.history(app).recent(60);
            } catch (RuntimeException e) {
                CrashLog.note(app, "insights", e);
            }
            final DayReport t = today;
            final List<DayReport> p = past;
            final Map<String, Integer> streaks = new HashMap<>();
            for (Routine.Goal g : m.goals) streaks.put(TypeStats.key(g.kind, g.sub), TypeStats.streak(g, t, p));
            a.runOnUiThread(() -> {
                if (a.isFinishing() || gen != generation) return;
                show(t, streaks);
            });
        }, "insights").start();
        a.extras.refreshHabits();
    }

    private void show(DayReport today, Map<String, Integer> streaks) {
        todayBox.removeAllViews();
        goalsBox.removeAllViews();
        slotsBox.removeAllViews();
        phoneBox.removeAllViews();
        typesBox.removeAllViews();
        if (today == null) {
            todayBox.addView(a.note("Could not work out today yet."), Ui.wide(a, 12));
            return;
        }
        final Routine m = a.routine();
        List<TypeStats.Line> lines = TypeStats.of(today);

        // Today so far: the score, and where the time went.
        LinearLayout card = Ui.section(a, todayBox, "Today so far", null);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView big = Ui.label(a, today.score >= 0 ? String.valueOf(today.score) : "–", 44, scoreColor(today.score), true);
        row.addView(big, new LinearLayout.LayoutParams(a.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout parts = new LinearLayout(a);
        parts.setOrientation(LinearLayout.VERTICAL);
        parts.addView(Ui.label(a, Analyzer.grade(today.score), 16, Ui.INK, true));
        parts.addView(Ui.label(a, part("Plan kept", today.planKept) + "   " + part("Focus", today.focus) + "   " + part("Sleep", today.sleep),
                13, Ui.MUTED, false), Ui.wide(a, 4));
        row.addView(parts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row, Ui.wide(a, 8));
        if (today.phoneKnown) {
            card.addView(a.note("Phone so far: " + Routine.formatDuration((int) (today.phoneMs / 60000L))), Ui.wide(a, 6));
        }
        addTimeRing(card, m, lines);

        // Goals.
        goalsBox.addView(goalsCard(a, lines, streaks, "Daily goals"), Ui.wide(a, 16));
        LinearLayout gc = (LinearLayout) goalsBox.getChildAt(0);
        LinearLayout adds = new LinearLayout(a);
        adds.setOrientation(LinearLayout.HORIZONTAL);
        Button addGoal = Ui.button(a, "＋  Add a goal", false, v -> editGoal(null, false));
        Button addToday = Ui.button(a, "＋  Goal for today", false, v -> editGoal(null, true));
        adds.addView(addGoal, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = a.dp(8);
        adds.addView(addToday, tp);
        gc.addView(adds, Ui.wide(a, 12));
        addGoalList(gc, m);

        // Every slot so far, with the apps used in it.
        addSlots(today, m);

        // Phone use.
        addPhone(today, m);

        // Other types.
        List<TypeStats.Line> other = new ArrayList<>();
        for (TypeStats.Line l : lines) if (!l.hasGoal()) other.add(l);
        if (!other.isEmpty()) {
            LinearLayout tc = Expander.card(a, typesBox, "Time by type today", null,
                    other.size() == 1 ? "1 type" : other.size() + " types", "ins_types", false).body;
            for (TypeStats.Line l : other) addBar(a, tc, l, null);
            tc.addView(a.note("Kept slots, and slots without an alarm, count as done."), Ui.wide(a, 8));
        }
    }

    // ------------------------------------------------------------------ today: charts and app use

    static String dur(long ms) {
        if (ms < 60000L) return ms <= 0 ? "0 min" : "Under a minute";
        return Routine.formatDuration((int) Math.round(ms / 60000.0));
    }

    private static int minuteOf(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    /** A ring chart of the time counted so far by type, each type in its own colour, with a legend. */
    private void addTimeRing(LinearLayout card, Routine m, List<TypeStats.Line> lines) {
        List<Charts.Slice> slices = new ArrayList<>();
        long total = 0;
        for (TypeStats.Line l : lines) {
            if (l.doneMin <= 0) continue;
            slices.add(new Charts.Slice(l.label(), l.doneMin * 60000L, SubColors.of(m, l.kind, l.sub)));
            total += l.doneMin;
        }
        card.addView(Ui.eyebrow(a, "Time by type so far"), Ui.wrap(a, 18));
        if (slices.isEmpty()) {
            card.addView(a.note("Nothing is counted yet. Time counts as the slots of your plan pass."), Ui.wide(a, 6));
            return;
        }
        LinearLayout both = new LinearLayout(a);
        both.setOrientation(LinearLayout.HORIZONTAL);
        both.setGravity(Gravity.CENTER_VERTICAL);
        Charts.Ring ring = new Charts.Ring(a, slices, Routine.formatDuration((int) total), "counted so far");
        both.addView(ring, new LinearLayout.LayoutParams(a.dp(150), a.dp(150)));
        LinearLayout legend = new LinearLayout(a);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setPadding(a.dp(14), 0, 0, 0);
        int shown = 0;
        for (Charts.Slice s : slices) {
            if (shown++ >= 7) break;
            LinearLayout item = new LinearLayout(a);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(a);
            dot.setBackground(Ui.box(a, s.color, 0, 5));
            item.addView(dot, new LinearLayout.LayoutParams(a.dp(10), a.dp(10)));
            TextView name = Ui.label(a, s.label, 13, Ui.INK, false);
            name.setSingleLine(true);
            name.setPadding(a.dp(8), 0, 0, 0);
            item.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            item.addView(Ui.label(a, Routine.formatDuration((int) (s.value / 60000L)), 13, Ui.MUTED, true));
            legend.addView(item, Ui.wide(a, 4));
        }
        if (slices.size() > 7) legend.addView(Ui.label(a, "and " + (slices.size() - 7) + " more", 12, Ui.MUTED, false), Ui.wide(a, 4));
        both.addView(legend, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(both, Ui.wide(a, 8));
    }

    /** One line for each slot that has started: its time, how much of it had an app in use, and (tap) each app, breaks and put-off time. */
    private void addSlots(DayReport today, final Routine m) {
        List<DayReport.Row> started = new ArrayList<>();
        for (DayReport.Row r : today.rows) {
            if (r.kind == Routine.SLEEP || r.status == DayReport.SKIPPED || r.status == DayReport.UPCOMING) continue;
            if (r.activeMs <= 0 && r.breakMs <= 0 && r.delayMs <= 0) continue;
            started.add(r);
        }
        if (started.isEmpty()) return;
        long worked = 0;
        for (DayReport.Row r : started) worked += r.workedMs();
        LinearLayout card = Expander.card(a, slotsBox, "Slots so far", "Time and apps in each slot",
                started.size() == 1 ? "1 slot  ·  " + dur(worked) : started.size() + " slots  ·  " + dur(worked), "ins_slots", false).body;
        for (final DayReport.Row r : started) {
            final LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(a.dp(2), a.dp(8), a.dp(2), a.dp(8));
            LinearLayout top = new LinearLayout(a);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(a);
            dot.setBackground(Ui.box(a, SubColors.of(m, r.kind, r.sub), 0, 5));
            top.addView(dot, new LinearLayout.LayoutParams(a.dp(10), a.dp(10)));
            String title = r.newTitle.isEmpty() ? r.title : r.newTitle;
            TextView name = Ui.label(a, title.isEmpty() ? Routine.KIND_NAMES[r.kind] : title, 15, Ui.INK, true);
            name.setPadding(a.dp(8), 0, 0, 0);
            name.setSingleLine(true);
            top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(Ui.label(a, dur(r.workedMs()), 14, Ui.TEAL_DARK, true));
            box.addView(top);
            String when = Routine.formatTime(minuteOf(r.startMs), m.fmt24) + " – " + Routine.formatTime(minuteOf(r.endMs), m.fmt24);
            String type = r.sub.isEmpty() || Routine.isOther(r.sub) ? Routine.KIND_NAMES[r.kind] : r.sub;
            box.addView(Ui.label(a, type + "  ·  " + when, 12, Ui.MUTED, false), Ui.wide(a, 2));
            if (r.workedMs() > 0) {
                box.addView(Ui.label(a, "On apps  " + dur(r.phoneMs) + "   ·   Without apps  " + dur(r.noAppMs()), 13, Ui.INK, false), Ui.wide(a, 4));
            }
            final LinearLayout more = new LinearLayout(a);
            more.setOrientation(LinearLayout.VERTICAL);
            more.setVisibility(View.GONE);
            more.setPadding(a.dp(18), a.dp(4), 0, 0);
            for (DayReport.App ap : r.apps) {
                if (ap.ms <= 0) continue;
                more.addView(detailLine(ap.name, dur(ap.ms)));
            }
            if (r.apps.isEmpty() && r.workedMs() > 0) more.addView(a.note("No app was used in this slot."));
            if (r.breakMs > 0) more.addView(detailLine("Break", dur(r.breakMs)));
            if (r.delayMs > 0) more.addView(detailLine("Start put off", dur(r.delayMs)));
            if (r.lentMs > 0) more.addView(detailLine("Given to the slot before", dur(r.lentMs)));
            if (r.extMs > 0) more.addView(detailLine("Carried on past its end", dur(r.extMs)));
            box.addView(more);
            box.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 10, 0x200E7C7B));
            box.setContentDescription("Tap to show or hide the apps used in this slot.");
            box.setOnClickListener(v -> more.setVisibility(more.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
            card.addView(box, Ui.wide(a, 2));
        }
        card.addView(a.note("Tap a slot to see each app, breaks and time the start was put off."), Ui.wide(a, 6));
    }

    private LinearLayout detailLine(String name, String value) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.addView(Ui.label(a, name, 13, Ui.MUTED, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(Ui.label(a, value, 13, Ui.INK, true));
        l.setPadding(0, a.dp(2), 0, a.dp(2));
        return l;
    }

    /** Phone use through the day by hour, and the apps used most. */
    private void addPhone(DayReport today, Routine m) {
        if (!today.phoneKnown) return;
        LinearLayout card = Expander.card(a, phoneBox, "Phone today", Routine.formatDuration((int) (today.phoneMs / 60000L)),
                "Phone use by hour, and the apps used most", "ins_phone", false).body;
        String[] marks = new String[24];
        for (int i = 0; i < 24; i += 6) marks[i] = Routine.formatTime(minuteOf(today.startMs + i * 3600000L), m.fmt24);
        card.addView(new Charts.HourBars(a, today.hourMs, marks, Ui.TEAL), Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, a.dp(110), 10));
        card.addView(a.note("Each bar is one hour of the day, starting when it began."), Ui.wide(a, 4));
        if (today.apps.isEmpty()) return;
        card.addView(Ui.eyebrow(a, "Apps used most"), Ui.wrap(a, 14));
        long max = 1;
        for (DayReport.App ap : today.apps) max = Math.max(max, ap.ms);
        int n = 0;
        for (DayReport.App ap : today.apps) {
            if (ap.ms <= 0) continue;
            if (n++ >= 6) break;
            LinearLayout line = new LinearLayout(a);
            line.setOrientation(LinearLayout.VERTICAL);
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            TextView nm = Ui.label(a, ap.name, 14, Ui.INK, false);
            nm.setSingleLine(true);
            head.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.label(a, dur(ap.ms), 13, Ui.MUTED, true));
            line.addView(head);
            final FrameLayout bar = new FrameLayout(a);
            bar.setBackground(Ui.box(a, Ui.SOFT, 0, 4));
            final View fill = new View(a);
            fill.setBackground(Ui.box(a, SubColors.of(m, Routine.TASK, ap.name), 0, 4));
            bar.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            final float frac = ap.ms / (float) max;
            bar.post(() -> {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = Math.max(a.dp(4), Math.round(bar.getWidth() * frac));
                fill.setLayoutParams(lp);
            });
            line.addView(bar, Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, a.dp(7), 4));
            card.addView(line, Ui.wide(a, 8));
        }
    }

    private static String part(String name, int v) {
        return name + " " + (v >= 0 ? v : "–");
    }

    private static int scoreColor(int s) {
        return s < 0 ? Ui.MUTED : s >= 70 ? Ui.TEAL : (s >= 50 ? Ui.AMBER : Ui.RED);
    }

    /** A card listing goals with progress bars; used on Home and here. */
    static LinearLayout goalsCard(MainActivity a, List<TypeStats.Line> lines, Map<String, Integer> streaks, String title) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.dp(18), a.dp(16), a.dp(18), a.dp(18));
        card.setBackground(Ui.box(a, Ui.SURFACE, 0, 22));
        card.setElevation(a.dp(1.5f));
        card.addView(Ui.eyebrow(a, title));
        boolean any = false;
        for (TypeStats.Line l : lines) {
            if (!l.hasGoal()) continue;
            any = true;
            Integer st = streaks == null ? null : streaks.get(TypeStats.key(l.kind, l.sub));
            addBar(a, card, l, st);
        }
        if (!any) {
            card.addView(a.note("Set a daily target for a type, for example 6 hours of Study, and see it fill up through the day."),
                    Ui.wide(a, 6));
        }
        return card;
    }

    /** One line: name, bar, "3h of 6h", and a streak when given. Tapping it edits the goal. */
    static void addBar(final MainActivity a, LinearLayout parent, final TypeStats.Line l, Integer streak) {
        int col = SubColors.of(a.routine(), l.kind, l.sub);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, a.dp(6), 0, a.dp(6));
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.label(a, l.label() + (streak != null && streak > 0 ? "  🔥 " + streak : ""), 15, Ui.INK, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String v = l.hasGoal() ? Routine.formatDuration(l.doneMin) + " of " + Routine.formatDuration(l.goalMin)
                : Routine.formatDuration(l.doneMin) + " of " + Routine.formatDuration(l.plannedMin) + " planned";
        TextView val = Ui.label(a, l.met() ? v + "  ✓" : v, 13, l.met() ? Ui.TEAL : Ui.MUTED, l.met());
        top.addView(val);
        final TextView arrow = Ui.label(a, "▾", 14, Ui.TEAL, true);
        arrow.setPadding(a.dp(8), 0, 0, 0);
        top.addView(arrow);
        box.addView(top);
        // The detail lines are one tap away, so a list of goals stays short.
        final LinearLayout details = new LinearLayout(a);
        details.setOrientation(LinearLayout.VERTICAL);
        FrameLayout bar = new FrameLayout(a);
        bar.setBackground(Ui.box(a, Ui.SOFT, 0, 5));
        final View fill = new View(a);
        fill.setBackground(Ui.box(a, l.met() ? Ui.TEAL : col, 0, 5));
        bar.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        final float frac = l.pct() / 100f;
        bar.post(() -> {
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            lp.width = Math.round(bar.getWidth() * frac);
            fill.setLayoutParams(lp);
        });
        box.addView(bar, Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, a.dp(10), 6));
        if (l.hasGoal()) {
            String sub = l.plannedMin < l.goalMin
                    ? "Your plan has " + Routine.formatDuration(l.plannedMin) + " of it today."
                    : "Planned today: " + Routine.formatDuration(l.plannedMin) + ".";
            if (streak != null && streak > 0) sub = "🔥 " + streak + "-day streak.  " + sub;
            details.addView(Ui.label(a, sub, 12, Ui.MUTED, false), Ui.wide(a, 4));
        }
        // The apps used while slots of this type were going on, and the time with no app in use.
        if (l.workedMs > 0) {
            StringBuilder u = new StringBuilder();
            List<Object[]> apps = TypeStats.topApps(l);
            int n = 0;
            for (Object[] ap : apps) {
                if (n++ >= 3) break;
                u.append(u.length() == 0 ? "" : ", ").append((String) ap[0]).append(' ').append(dur((Long) ap[1]));
            }
            if (apps.size() > 3) u.append(" and ").append(apps.size() - 3).append(" more");
            String text = (u.length() == 0 ? "No app used" : "Apps: " + u) + "   ·   Without apps  " + dur(l.noAppMs());
            details.addView(Ui.label(a, text, 12, Ui.INK, false), Ui.wide(a, 3));
        }
        if (l.hasGoal()) {
            LinearLayout acts = new LinearLayout(a);
            acts.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            acts.addView(Ui.button(a, "Change the goal", false, x -> {
                Page p = a.page(MainActivity.TAB_INSIGHTS);
                if (p instanceof InsightsPage) ((InsightsPage) p).editGoal(goalOf(a, l), false);
            }), ep);
            details.addView(acts, Ui.wide(a, 8));
        }
        if (details.getChildCount() == 0) {
            arrow.setVisibility(View.GONE);
        } else {
            details.setVisibility(View.GONE);
            box.addView(details);
            box.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 10, 0x200E7C7B));
            box.setContentDescription(l.label() + ", " + Lang.tr("collapsed"));
            box.setOnClickListener(x -> {
                boolean open = details.getVisibility() != View.VISIBLE;
                details.setVisibility(open ? View.VISIBLE : View.GONE);
                arrow.animate().rotation(open ? 180f : 0f).setDuration(160).start();
                box.setContentDescription(l.label() + ", " + Lang.tr(open ? "expanded" : "collapsed"));
            });
        }
        parent.addView(box, Ui.wide(a, 6));
    }

    /** The day the app calls today (a day that began yesterday and is still running counts as today). */
    static LocalDate todayOf(MainActivity a) {
        Routine m = a.routine();
        return Plan.resolve(m, Store.overrides(a, m), System.currentTimeMillis(), Store.zone()).day;
    }

    /** The goal behind a line of today: the one-day goal of today if there is one, else the repeating one. */
    private static Routine.Goal goalOf(MainActivity a, TypeStats.Line l) {
        Routine m = a.routine();
        Routine.Goal g = m.findOnce(l.kind, l.sub, todayOf(a));
        return g != null ? g : m.findGoal(l.kind, l.sub);
    }

    /** Every goal, with how it repeats and whether it counts today. A tap changes it. */
    private void addGoalList(LinearLayout card, Routine m) {
        if (m.goals.isEmpty()) return;
        LocalDate today = todayOf(a);
        List<Routine.Block> blocks = new ArrayList<>();
        Plan.DayPlan p = Plan.resolve(m, Store.overrides(a, m), System.currentTimeMillis(), Store.zone());
        for (Routine.Slot sl : p.sc.slots) blocks.add(sl.block);
        List<Routine.Goal> counting = m.forDay(today).goalsFor(today, blocks);
        Expander.Part listPart = Expander.more(a, card, "All your goals", "ins_goal_list", false);
        listPart.setSummary(m.goals.size() == 1 ? "1 goal, with how it repeats" : m.goals.size() + " goals, with how they repeat");
        LinearLayout listBox = listPart.body;
        for (final Routine.Goal g : m.goals) {
            boolean now = counting.contains(g);
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(a.dp(2), a.dp(8), a.dp(2), a.dp(8));
            LinearLayout top = new LinearLayout(a);
            top.setOrientation(LinearLayout.HORIZONTAL);
            View dot = new View(a);
            dot.setBackground(Ui.box(a, SubColors.of(m, g.kind, g.sub), 0, 5));
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(dot, new LinearLayout.LayoutParams(a.dp(10), a.dp(10)));
            TextView name = Ui.label(a, g.sub.isEmpty() || Routine.isOther(g.sub) ? "Other " + Routine.KIND_NAMES[g.kind].toLowerCase(Lang.locale()) : g.sub,
                    15, Ui.INK, true);
            name.setPadding(a.dp(8), 0, 0, 0);
            top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(Ui.label(a, Routine.formatDuration(g.mins), 14, Ui.TEAL_DARK, true));
            row.addView(top);
            row.addView(Ui.label(a, g.ruleText(today) + (now ? "   ·   counts today" : "   ·   not today"), 12, Ui.MUTED, false), Ui.wide(a, 2));
            row.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 10, 0x200E7C7B));
            row.setOnClickListener(x -> editGoal(g, false));
            listBox.addView(row, Ui.wide(a, 2));
        }
    }

    /** Adds or changes a goal ({@code g} null to add). */
    void editGoal(final Routine.Goal g) {
        editGoal(g, false);
    }

    /** Which ways a goal can repeat, in the order of the first list of the editor. */
    private static final int[] MODE_REPEAT = {Routine.Goal.ONCE, Routine.Goal.DAILY, Routine.Goal.ALTERNATE, Routine.Goal.WEEKDAYS};

    /**
     * Adds or changes a goal. A goal for today is only for what is still to come in today's schedule. Every other way of repeating
     * is for the activities of the schedules it will meet. Either way the goal only counts on days whose schedule has the activity.
     */
    void editGoal(final Routine.Goal g, final boolean todayOnly) {
        if (g == null && !a.requirePro(DayPass.GOALS, "Daily goals", () -> editGoal(null, todayOnly))) return;
        final Routine m = a.routine();
        final long now = System.currentTimeMillis();
        final Plan.DayPlan plan = Plan.resolve(m, Store.overrides(a, m), now, Store.zone());
        final LocalDate today = plan.day;

        final int[] mode = {g == null ? (todayOnly ? 0 : 1) : indexOfRepeat(g.repeat)};
        final int[] mask = {g != null && g.repeat == Routine.Goal.WEEKDAYS ? g.daysMask : 31};
        final List<Routine.Sub> with = new ArrayList<>();
        if (g != null) with.addAll(g.with);
        final Routine.Sub[] chosen = {g == null ? null : new Routine.Sub(g.kind, g.sub)};
        final List<Routine.Sub> cands = new ArrayList<>();

        final Spinner when = new Spinner(a);
        ArrayAdapter<String> wad = Lang.adapter(a, android.R.layout.simple_spinner_item,
                new String[]{"Today only", "Every day", "Every other day", "On chosen days"});
        wad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        when.setAdapter(wad);
        when.setSelection(mode[0]);

        final LinearLayout daysRow = new LinearLayout(a);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] dayChips = new TextView[7];
        for (int d = 0; d < 7; d++) {
            final int day = d;
            TextView t = Ui.label(a, Routine.DAY_SHORT[d].substring(0, 2), 13, Ui.MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                mask[0] ^= 1 << day;
                paintDays(dayChips, mask[0]);
                ((Runnable) v.getTag()).run();
            });
            dayChips[d] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, a.dp(38), 1f);
            if (d > 0) lp.leftMargin = a.dp(4);
            daysRow.addView(t, lp);
        }

        final Spinner type = new Spinner(a);
        final Button withBtn = Ui.button(a, "", false, null);

        final Runnable fillTypes = new Runnable() {
            @Override
            public void run() {
                cands.clear();
                if (g != null) {
                    cands.add(new Routine.Sub(g.kind, g.sub));   // changing a goal keeps its type
                } else {
                    cands.addAll(candidates(m, plan, mode[0], mask[0]));
                }
                List<String> labels = new ArrayList<>();
                int sel = 0;
                for (int i = 0; i < cands.size(); i++) {
                    Routine.Sub c = cands.get(i);
                    if (chosen[0] != null && chosen[0].kind == c.kind && chosen[0].name.equalsIgnoreCase(c.name)) sel = i;
                    labels.add(c.name + "  (" + Routine.KIND_NAMES[c.kind] + ")");
                }
                ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, labels);
                ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                type.setAdapter(ad);
                if (!cands.isEmpty()) {
                    type.setSelection(sel);
                    chosen[0] = cands.get(sel);
                }
                type.setEnabled(g == null);
                // conditions that no longer fit the type or the days are dropped
                with.removeIf(w -> chosen[0] != null && w.kind == chosen[0].kind && w.name.equalsIgnoreCase(chosen[0].name));
                withBtn.setText(with.isEmpty() ? "Any day with this activity" : "Only on days with " + names(with));
                withBtn.setVisibility(mode[0] == 0 ? View.GONE : View.VISIBLE);
                daysRow.setVisibility(mode[0] == 3 ? View.VISIBLE : View.GONE);
            }
        };
        for (TextView t : dayChips) t.setTag(fillTypes);
        paintDays(dayChips, mask[0]);

        when.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == mode[0] && !cands.isEmpty()) return;
                mode[0] = position;
                fillTypes.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < cands.size() && g == null) {
                    chosen[0] = cands.get(position);
                    with.removeIf(w -> w.kind == chosen[0].kind && w.name.equalsIgnoreCase(chosen[0].name));
                    withBtn.setText(with.isEmpty() ? "Any day with this activity" : "Only on days with " + names(with));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        withBtn.setOnClickListener(v -> pickWith(m, plan, mode[0], mask[0], chosen[0], with, withBtn));
        fillTypes.run();
        if (g == null && cands.isEmpty()) {
            a.toastText(todayOnly ? "Nothing is left in today's schedule to set a goal for. Pick Every day instead."
                    : "Add a slot with an activity type first (Routine tab)");
            if (todayOnly) {
                mode[0] = 1;
                when.setSelection(1);
                fillTypes.run();
            }
            if (cands.isEmpty()) return;
        }

        final EditText hours = number(g == null ? "2" : String.valueOf(g.mins / 60));
        final EditText mins = number(g == null ? "0" : String.valueOf(g.mins % 60));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        box.addView(Ui.label(a, "How often", 12, Ui.MUTED, true));
        box.addView(when, Ui.wide(a, 0));
        box.addView(daysRow, Ui.wide(a, 6));
        box.addView(Ui.label(a, "Activity", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        box.addView(type, Ui.wide(a, 0));
        box.addView(withBtn, Ui.wide(a, 4));
        box.addView(Ui.label(a, "At least", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        LinearLayout hm = new LinearLayout(a);
        hm.setOrientation(LinearLayout.HORIZONTAL);
        hm.setGravity(Gravity.CENTER_VERTICAL);
        hm.addView(hours, new LinearLayout.LayoutParams(a.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        hm.addView(Ui.label(a, " hours   ", 15, Ui.INK, false));
        hm.addView(mins, new LinearLayout.LayoutParams(a.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        hm.addView(Ui.label(a, " minutes", 15, Ui.INK, false));
        box.addView(hm, Ui.wide(a, 0));
        box.addView(a.note("A goal counts on days whose schedule has the activity. Kept slots of the activity count towards it. "
                + "Days in a row you reach it make a streak."), Ui.wide(a, 8));
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        AlertDialog.Builder b = new DBuilder(a)
                .setTitle(g == null ? (mode[0] == 0 ? "New goal for today" : "New goal") : "Goal for " + g.label())
                .setView(sv)
                .setPositiveButton("Save", (d, w) -> {
                    int total = parse(hours) * 60 + parse(mins);
                    if (total < 5) {
                        a.toastText("A goal needs at least 5 minutes");
                        return;
                    }
                    if (chosen[0] == null) return;
                    if (mode[0] == 3 && (mask[0] & 0x7F) == 0) {
                        a.toastText("Pick at least one day");
                        return;
                    }
                    String snap = a.snapshot();
                    int mm = Math.min(1440, total);
                    Routine.Goal ng = new Routine.Goal(chosen[0].kind, chosen[0].name, mm);
                    ng.repeat = MODE_REPEAT[mode[0]];
                    if (ng.repeat == Routine.Goal.ONCE) ng.date = today;
                    else if (ng.repeat == Routine.Goal.ALTERNATE) ng.date = g != null && g.repeat == Routine.Goal.ALTERNATE && g.date != null ? g.date : today;
                    if (ng.repeat == Routine.Goal.WEEKDAYS) ng.daysMask = mask[0] & 0x7F;
                    if (ng.repeat != Routine.Goal.ONCE) ng.with.addAll(with);
                    if (g != null) m.goals.remove(g);
                    m.putGoal(ng);
                    a.persist();
                    a.routineChanged();
                    a.undoable("Goal set: " + ng.label() + ", " + Routine.formatDuration(mm) + "  ·  " + ng.ruleText(today), snap);
                })
                .setNegativeButton("Cancel", null);
        if (g != null) {
            b.setNeutralButton("Remove", (d, w) -> {
                String snap = a.snapshot();
                m.goals.remove(g);
                a.persist();
                a.routineChanged();
                a.undoable("Goal removed.", snap);
            });
        }
        b.show();
    }

    private static int indexOfRepeat(int repeat) {
        for (int i = 0; i < MODE_REPEAT.length; i++) if (MODE_REPEAT[i] == repeat) return i;
        return 1;
    }

    private void paintDays(TextView[] chips, int mask) {
        for (int d = 0; d < 7; d++) {
            boolean on = ((mask >> d) & 1) != 0;
            chips[d].setTextColor(on ? 0xFFFFFFFF : Ui.MUTED);
            chips[d].setBackground(on ? Ui.box(a, Ui.TEAL, 0, 20) : Ui.pressable(a, Ui.SOFT, Ui.LINE, 20, 0x300E7C7B));
        }
    }

    private static String names(List<Routine.Sub> subs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < subs.size(); i++) sb.append(i == 0 ? "" : ", ").append(subs.get(i).name);
        return sb.toString();
    }

    /**
     * The activities a goal can be for. For today only: what is still to come in today's schedule (the slot going on counts). Otherwise:
     * the activities of the routines the goal will meet (every routine, or those the chosen weekdays use). Sleep has no goals.
     */
    static List<Routine.Sub> candidates(Routine m, Plan.DayPlan plan, int mode, int mask) {
        Set<String> present = new LinkedHashSet<>();
        if (mode == 0) {
            for (Routine.Slot s : plan.sc.slots) {
                if (s.block.skipped || s.block.kind == Routine.SLEEP || s.end <= plan.tt) continue;
                present.add(s.block.kind + ":" + s.block.sub.toLowerCase(java.util.Locale.ROOT));
            }
        } else {
            List<Routine> rs = new ArrayList<>();
            if (mode == 3) {
                for (int d = 0; d < 7; d++) {
                    if (((mask >> d) & 1) == 0) continue;
                    Routine r = m.routineAt(m.dayUse[d]);
                    if (!rs.contains(r)) rs.add(r);
                }
            } else {
                rs.addAll(m.all());
            }
            for (Routine r : rs) {
                for (Routine.Block b : r.blocks) {
                    if (b.kind == Routine.SLEEP) continue;
                    present.add(b.kind + ":" + b.sub.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        List<Routine.Sub> out = new ArrayList<>();
        for (int k = 0; k < Routine.SLEEP; k++) {
            for (Routine.Sub s : m.subsOf(k)) {
                if (present.contains(k + ":" + s.name.toLowerCase(java.util.Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }

    /** Which other activities the day's schedule must also have for a repeating goal to count. Any one of them is enough. */
    private void pickWith(Routine m, Plan.DayPlan plan, int mode, int mask, Routine.Sub own, final List<Routine.Sub> with, final Button btn) {
        final List<Routine.Sub> pool = new ArrayList<>();
        for (Routine.Sub s : candidates(m, plan, mode, mask)) {
            if (own != null && s.kind == own.kind && s.name.equalsIgnoreCase(own.name)) continue;
            pool.add(s);
        }
        if (pool.isEmpty()) {
            a.toastText("There are no other activities to choose from");
            return;
        }
        final String[] labels = new String[pool.size()];
        final boolean[] on = new boolean[pool.size()];
        for (int i = 0; i < labels.length; i++) {
            Routine.Sub s = pool.get(i);
            labels[i] = s.name + "  (" + Routine.KIND_NAMES[s.kind] + ")";
            for (Routine.Sub w : with) if (w.kind == s.kind && w.name.equalsIgnoreCase(s.name)) on[i] = true;
        }
        new DBuilder(a)
                .setTitle("Count it only on days with…")
                .setMultiChoiceItems(labels, on, (d, which, checked) -> on[which] = checked)
                .setPositiveButton("OK", (d, w) -> {
                    with.clear();
                    for (int i = 0; i < on.length; i++) if (on[i]) with.add(pool.get(i));
                    btn.setText(with.isEmpty() ? "Any day with this activity" : "Only on days with " + names(with));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText number(String v) {
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setText(v);
        e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.CENTER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        return e;
    }

    private static int parse(EditText e) {
        try {
            return Math.max(0, Integer.parseInt(e.getText().toString().trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
