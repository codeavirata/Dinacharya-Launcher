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
            for (Routine.Goal g : m.goals) streaks.put(TypeStats.goalKey(g), TypeStats.streak(g, t, p));
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
        goalsBox.addView(goalsCard(a, lines, streaks, "Daily goals", addGoalButton()), Ui.wide(a, 16));
        LinearLayout gc = (LinearLayout) goalsBox.getChildAt(0);
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
            if (l.doneMin <= 0 || !l.slot.isEmpty()) continue;   // a slot's goal is part of its type, not another type
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
            if (r.activeMs <= 0 && r.breakMs <= 0 && r.delayMs <= 0 && r.noRespMs <= 0) continue;
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
            if (r.noRespMs > 0) more.addView(detailLine("No response", dur(r.noRespMs)));
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

    /** A card listing goals with a clock-style ring each; used on Home and here. */
    static LinearLayout goalsCard(MainActivity a, List<TypeStats.Line> lines, Map<String, Integer> streaks, String title) {
        return goalsCard(a, lines, streaks, title, null);
    }

    /** The same, with a small view (the plus that adds a goal) at the right end of the title line. */
    static LinearLayout goalsCard(MainActivity a, List<TypeStats.Line> lines, Map<String, Integer> streaks, String title, View trailing) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14));
        card.setBackground(Ui.box(a, Ui.SURFACE, 0, 22));
        card.setElevation(a.dp(1.5f));
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.eyebrow(a, title), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) head.addView(trailing, new LinearLayout.LayoutParams(a.dp(34), a.dp(34)));
        card.addView(head);
        TypeStats.Summary sm = TypeStats.summary(lines);
        if (sm.goals > 0) {
            LinearLayout top = new LinearLayout(a);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(new GoalRing(a, 52, sm.pct / 100f, Ui.TEAL, sm.pct + "%", sm.met == sm.goals), new LinearLayout.LayoutParams(a.dp(52), a.dp(52)));
            LinearLayout words = new LinearLayout(a);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(a.dp(14), 0, 0, 0);
            words.addView(Ui.label(a, sm.met + " of " + sm.goals + " goals", 16, Ui.INK, true));
            words.addView(Ui.label(a, sm.minLeft > 0 ? Routine.formatDuration(sm.minLeft) + " to go" : "All goals done", 13, Ui.MUTED, false),
                    Ui.wide(a, 1));
            top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(top, Ui.wide(a, 8));
        }
        boolean any = false;
        for (TypeStats.Line l : lines) {
            if (!l.hasGoal()) continue;
            any = true;
            Integer st = streaks == null ? null : streaks.get(TypeStats.lineKey(l));
            addBar(a, card, l, st);
        }
        if (!any) {
            card.addView(a.note("Set a daily target for a slot of your routine, for example 2 hours for Morning study, with its own to-do list, and see it fill up through the day."),
                    Ui.wide(a, 6));
        }
        return card;
    }

    /** One line: name, bar, "3h of 6h", and a streak when given. Tapping it edits the goal. */
    static void addBar(final MainActivity a, LinearLayout parent, final TypeStats.Line l, Integer streak) {
        int col = SubColors.of(a.routine(), l.kind, l.slot.isEmpty() ? l.sub : a.routine().slotSub(l.kind, l.slot));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, a.dp(6), 0, a.dp(6));
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final TextView arrow = Ui.label(a, "▾", 14, Ui.TEAL, true);
        arrow.setPadding(a.dp(8), 0, 0, 0);
        // The detail lines are one tap away, so a list of goals stays short.
        final LinearLayout details = new LinearLayout(a);
        details.setOrientation(LinearLayout.VERTICAL);
        if (l.hasGoal()) {
            // a goal is one slim line: a ring, the name, and "1h 20 of 2h"
            top.addView(new GoalRing(a, 40, l.pct() / 100f, col, l.pct() + "", l.met()), new LinearLayout.LayoutParams(a.dp(40), a.dp(40)));
            LinearLayout words = new LinearLayout(a);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(a.dp(12), 0, 0, 0);
            words.addView(Ui.label(a, l.label() + (streak != null && streak > 0 ? "  🔥 " + streak : ""), 15, Ui.INK, true));
            words.addView(Ui.label(a, Routine.formatDuration(l.doneMin) + " of " + Routine.formatDuration(l.goalMin), 12,
                    l.met() ? Ui.TEAL : Ui.MUTED, l.met()), Ui.wide(a, 1));
            top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(arrow);
            box.addView(top);
        } else {
            top.addView(Ui.label(a, l.label(), 15, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(Ui.label(a, Routine.formatDuration(l.doneMin) + " of " + Routine.formatDuration(l.plannedMin) + " planned", 13, Ui.MUTED, false));
            top.addView(arrow);
            box.addView(top);
            FrameLayout bar = new FrameLayout(a);
            bar.setBackground(Ui.box(a, Ui.SOFT, 0, 5));
            final View fill = new View(a);
            fill.setBackground(Ui.box(a, col, 0, 5));
            bar.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            final float frac = l.pct() / 100f;
            bar.post(() -> {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = Math.round(bar.getWidth() * frac);
                fill.setLayoutParams(lp);
            });
            box.addView(bar, Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, a.dp(10), 6));
        }
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

    /** The one small plus of the goals card. It asks when the goal is for, then opens the editor. */
    private View addGoalButton() {
        Button plus = Ui.iconButton(a, "＋", Ui.TEAL_DARK, v -> chooseGoalDay());
        plus.setContentDescription(Lang.tr("New goal"));
        return plus;
    }

    /** A short sheet at the bottom of the screen: every day, today only, or tomorrow only. */
    private void chooseGoalDay() {
        final android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(a.dp(20), a.dp(14), a.dp(20), a.dp(18));
        sheet.setBackground(Ui.box(a, Ui.SURFACE, 0, 22));
        View grip = new View(a);
        grip.setBackground(Ui.box(a, Ui.LINE, 0, 3));
        sheet.addView(grip, Ui.lp(a, a.dp(36), a.dp(4), 0));
        ((LinearLayout.LayoutParams) grip.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(Ui.label(a, "New goal", 17, Ui.INK, true), Ui.wide(a, 12));
        sheet.addView(Ui.label(a, "When is it for?", 13, Ui.MUTED, false), Ui.wide(a, 2));
        String[] names = {"Every day", "Today only", "Tomorrow only"};
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            Button b = Ui.button(a, names[i], false, x -> {
                d.dismiss();
                if (which == 0) editGoal(null, false);
                else if (which == 1) editGoal(null, true);
                else editGoal(null, false, true);
            });
            sheet.addView(b, Ui.wide(a, 8));
        }
        d.setContentView(sheet);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    /** The day the app calls today (a day that began yesterday and is still running counts as today). */
    static LocalDate todayOf(MainActivity a) {
        Routine m = a.routine();
        return Plan.resolve(m, Store.overrides(a, m), System.currentTimeMillis(), Store.zone()).day;
    }

    /** The goal behind a line of today: the one-day goal of today if there is one, else the repeating one. */
    private static Routine.Goal goalOf(MainActivity a, TypeStats.Line l) {
        Routine m = a.routine();
        if (!l.slot.isEmpty()) {
            Routine.Goal s = m.findSlotOnce(l.kind, l.slot, todayOf(a));
            return s != null ? s : m.findSlotGoal(l.kind, l.slot);
        }
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
            dot.setBackground(Ui.box(a, SubColors.of(m, g.kind, g.bound() ? m.slotSub(g.kind, g.slot) : g.sub), 0, 5));
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(dot, new LinearLayout.LayoutParams(a.dp(10), a.dp(10)));
            TextView name = Ui.label(a, g.bound() ? g.slot : g.sub.isEmpty() || Routine.isOther(g.sub) ? "Other " + Routine.KIND_NAMES[g.kind].toLowerCase(Lang.locale()) : g.sub,
                    15, Ui.INK, true);
            name.setPadding(a.dp(8), 0, 0, 0);
            top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(Ui.label(a, Routine.formatDuration(g.mins), 14, Ui.TEAL_DARK, true));
            row.addView(top);
            row.addView(Ui.label(a, g.ruleText(today) + (now ? "   ·   counts today" : "   ·   not today")
                    + (g.todos.isEmpty() ? "" : g.todos.size() == 1 ? "   ·   1 to-do" : "   ·   " + g.todos.size() + " to-dos"), 12, Ui.MUTED, false), Ui.wide(a, 2));
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
    private static final int[] MODE_REPEAT = {Routine.Goal.ONCE, Routine.Goal.DAILY, Routine.Goal.ALTERNATE, Routine.Goal.WEEKDAYS, Routine.Goal.ONCE};
    /** The place of "Tomorrow only" in the list of ways to repeat (the first, "Today only", is 0). */
    private static final int TOMORROW = 4;

    /**
     * Adds or changes a goal. A goal for today is only for what is still to come in today's schedule. Every other way of repeating
     * is for the activities of the schedules it will meet. Either way the goal only counts on days whose schedule has the activity.
     */
    void editGoal(final Routine.Goal g, final boolean todayOnly) {
        editGoal(g, todayOnly, false);
    }

    /** Same, and with {@code tomorrow} a new goal starts as "Tomorrow only": it is for a slot of tomorrow's schedule. */
    void editGoal(final Routine.Goal g, final boolean todayOnly, final boolean tomorrow) {
        if (g == null && !a.requirePro(DayPass.GOALS, "Setting a goal", () -> editGoal(null, todayOnly, tomorrow))) return;
        final Routine m = a.routine();
        final long now = System.currentTimeMillis();
        final Plan.DayPlan plan = Plan.resolve(m, Store.overrides(a, m), now, Store.zone());
        final LocalDate today = plan.day;

        final int[] mode = {g == null ? (tomorrow ? TOMORROW : todayOnly ? 0 : 1) : indexOfGoal(g, today)};
        final int[] mask = {g != null && g.repeat == Routine.Goal.WEEKDAYS ? g.daysMask : 31};
        final List<Routine.Sub> with = new ArrayList<>();
        if (g != null) with.addAll(g.with);
        final GoalSlots.Pick[] chosen = {g == null ? null : GoalSlots.Pick.of(g)};
        final List<GoalSlots.Pick> cands = new ArrayList<>();
        final List<String> todos = new ArrayList<>();
        if (g != null) todos.addAll(g.todos);

        final Spinner when = new Spinner(a);
        ArrayAdapter<String> wad = Lang.adapter(a, android.R.layout.simple_spinner_item,
                new String[]{"Today only", "Every day", "Every other day", "On chosen days", "Tomorrow only"});
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
                    cands.add(GoalSlots.Pick.of(g));   // changing a goal keeps its slot
                } else {
                    cands.addAll(mode[0] == TOMORROW ? tomorrowPicks(m, today) : slotPicks(m, plan, mode[0], mask[0]));
                }
                List<String> labels = new ArrayList<>();
                int sel = 0;
                for (int i = 0; i < cands.size(); i++) {
                    GoalSlots.Pick c = cands.get(i);
                    if (c.is(chosen[0])) sel = i;
                    labels.add(c.label());
                }
                ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, labels);
                ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                type.setAdapter(ad);
                if (!cands.isEmpty()) {
                    type.setSelection(sel);
                    chosen[0] = cands.get(sel);
                }
                type.setEnabled(g == null);
                // conditions that no longer fit the type are dropped
                with.removeIf(w -> chosen[0] != null && chosen[0].legacy && w.kind == chosen[0].kind && w.name.equalsIgnoreCase(chosen[0].name));
                withBtn.setText(with.isEmpty() ? "Any day with this slot" : "Only on days with " + names(with));
                withBtn.setVisibility(mode[0] == 0 || mode[0] == TOMORROW ? View.GONE : View.VISIBLE);
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
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        withBtn.setOnClickListener(v -> pickWith(m, plan, mode[0], mask[0],
                chosen[0] != null && chosen[0].legacy ? new Routine.Sub(chosen[0].kind, chosen[0].name) : null, with, withBtn));
        fillTypes.run();
        if (g == null && cands.isEmpty() && mode[0] == TOMORROW) {
            a.toastText("Tomorrow's schedule has no slot to set a goal for. Add a slot in the Routine tab first.");
            return;
        }
        if (g == null && cands.isEmpty()) {
            a.toastText(todayOnly ? "Nothing is left in today's schedule to set a goal for. Pick Every day instead."
                    : "Add a slot to your routine first (Routine tab)");
            if (todayOnly) {
                mode[0] = 1;
                when.setSelection(1);
                fillTypes.run();
            }
            if (cands.isEmpty()) return;
        }

        final EditText nameField = new EditText(a);
        nameField.setSingleLine(true);
        nameField.setHint(Lang.tr("Goal name (optional)"));
        nameField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Routine.Goal.MAX_NAME)});
        if (g != null) nameField.setText(g.name);
        final EditText hours = number(g == null ? "2" : String.valueOf(g.mins / 60));
        final EditText mins = number(g == null ? "0" : String.valueOf(g.mins % 60));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        box.addView(Ui.label(a, "How often", 12, Ui.MUTED, true));
        box.addView(when, Ui.wide(a, 0));
        box.addView(daysRow, Ui.wide(a, 6));
        box.addView(Ui.label(a, "Slot", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        box.addView(type, Ui.wide(a, 0));
        box.addView(withBtn, Ui.wide(a, 4));
        box.addView(Ui.label(a, "Name", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        box.addView(nameField, Ui.wide(a, 0));
        box.addView(Ui.label(a, "At least", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        LinearLayout hm = new LinearLayout(a);
        hm.setOrientation(LinearLayout.HORIZONTAL);
        hm.setGravity(Gravity.CENTER_VERTICAL);
        hm.addView(hours, new LinearLayout.LayoutParams(a.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        hm.addView(Ui.label(a, " hours   ", 15, Ui.INK, false));
        hm.addView(mins, new LinearLayout.LayoutParams(a.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
        hm.addView(Ui.label(a, " minutes", 15, Ui.INK, false));
        box.addView(hm, Ui.wide(a, 0));

        // the goal's own to-do list, one dropdown
        final EditText todoField = new EditText(a);
        box.addView(todoDrop(todos, todoField), Ui.wide(a, 14));
        box.addView(a.note("Without a to-do list, the goal is a to-do of its slot: tick it, or it ticks itself when the time is reached. "
                + "With one, the goal ticks only when every to-do is done. The same list shows in the slot's to-dos."), Ui.wide(a, 8));
        box.addView(a.note("A goal counts on days whose schedule has the slot. Kept slots count towards it. "
                + "Days in a row you reach it make a streak."), Ui.wide(a, 6));
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        AlertDialog.Builder b = new DBuilder(a)
                .setTitle(g == null ? (mode[0] == 0 ? "New goal for today" : mode[0] == TOMORROW ? "New goal for tomorrow" : "New goal") : "Goal for " + g.label())
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
                    GoalSlots.Pick pick = chosen[0];
                    Routine.Goal ng = pick.legacy ? new Routine.Goal(pick.kind, pick.name, mm) : Routine.Goal.forSlot(pick.kind, pick.name, mm);
                    ng.repeat = MODE_REPEAT[mode[0]];
                    if (ng.repeat == Routine.Goal.ONCE) ng.date = mode[0] == TOMORROW ? today.plusDays(1) : today;
                    else if (ng.repeat == Routine.Goal.ALTERNATE) ng.date = g != null && g.repeat == Routine.Goal.ALTERNATE && g.date != null ? g.date : today;
                    ng.name = Routine.cleanGoalName(nameField.getText().toString());
                    if (ng.repeat == Routine.Goal.WEEKDAYS) ng.daysMask = mask[0] & 0x7F;
                    if (ng.repeat != Routine.Goal.ONCE) ng.with.addAll(with);
                    for (String t : todos) ng.addTodo(t);
                    ng.addTodo(todoField.getText().toString());   // what was typed and not yet added
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

    /**
     * The to-do list of a goal inside the editor, as one dropdown: closed it says how many to-dos there are; open it lists them, each
     * with a remove mark, and a field to add another.
     */
    private LinearLayout todoDrop(final List<String> todos, final EditText field) {
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.box(a, Ui.SOFT, 0, 12));
        root.setPadding(a.dp(12), a.dp(4), a.dp(12), a.dp(4));
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(a.dp(44));
        final TextView title = Ui.label(a, "", 15, Ui.INK, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView arrow = Ui.label(a, "▾", 14, Ui.TEAL, true);
        head.addView(arrow);
        root.addView(head);
        final LinearLayout open = new LinearLayout(a);
        open.setOrientation(LinearLayout.VERTICAL);
        open.setVisibility(View.GONE);
        root.addView(open);
        final LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        open.addView(rows);

        field.setHint(Lang.tr("Add a to-do"));
        field.setSingleLine(true);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        final Button add = Ui.button(a, "Add", false, null);
        LinearLayout addRow = new LinearLayout(a);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = a.dp(8);
        addRow.addView(add, ap);
        open.addView(addRow, Ui.wide(a, 4));

        final Runnable[] paint = new Runnable[1];
        paint[0] = () -> {
            rows.removeAllViews();
            for (int i = 0; i < todos.size(); i++) {
                final int at = i;
                LinearLayout line = new LinearLayout(a);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView t = Ui.label(a, todos.get(i), 14, Ui.INK, false);
                line.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView x = Ui.label(a, "✕", 15, Ui.MUTED, true);
                x.setGravity(Gravity.CENTER);
                x.setContentDescription(Lang.tr("Remove"));
                x.setOnClickListener(v -> {
                    if (at < todos.size()) todos.remove(at);
                    paint[0].run();
                });
                line.addView(x, new LinearLayout.LayoutParams(a.dp(44), a.dp(40)));
                rows.addView(line);
            }
            title.setText(todos.isEmpty() ? "To-do list (optional)" : todos.size() == 1 ? "To-do list  ·  1 to-do" : "To-do list  ·  " + todos.size() + " to-dos");
            boolean full = todos.size() >= Routine.Goal.MAX_TODOS;
            field.setEnabled(!full);
            add.setEnabled(!full);
            if (full) field.setHint(Lang.tr("The list is full"));
        };
        final Runnable addIt = () -> {
            String text = Routine.cleanTodo(field.getText().toString());
            if (text.isEmpty()) return;
            for (String x : todos) {
                if (x.equalsIgnoreCase(text)) {
                    a.toastText("That is on the list already");
                    return;
                }
            }
            if (todos.size() >= Routine.Goal.MAX_TODOS) return;
            todos.add(text);
            field.setText("");
            paint[0].run();
        };
        add.setOnClickListener(v -> addIt.run());
        field.setOnEditorActionListener((v, actionId, ev) -> {
            addIt.run();
            return true;
        });
        head.setOnClickListener(v -> {
            boolean show = open.getVisibility() != View.VISIBLE;
            open.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.animate().rotation(show ? 180f : 0f).setDuration(160).start();
        });
        paint[0].run();
        return root;
    }

    /** The slots a goal can be set for: for today only, those still to come in today's schedule (the one going on counts). */
    static List<GoalSlots.Pick> slotPicks(Routine m, Plan.DayPlan plan, int mode, int mask) {
        List<Routine.Block> blocks = new ArrayList<>();
        if (mode == 0) {
            for (Routine.Slot s : plan.sc.slots) {
                if (s.block.skipped || s.block.kind == Routine.SLEEP || s.end <= plan.tt) continue;
                blocks.add(s.block);
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
            for (Routine r : rs) blocks.addAll(r.blocks);
        }
        return GoalSlots.of(blocks);
    }

    /** The slots a goal for tomorrow can be set for: those of tomorrow's schedule (not sleep, not taken off). */
    static List<GoalSlots.Pick> tomorrowPicks(Routine m, LocalDate today) {
        List<Routine.Block> blocks = new ArrayList<>();
        for (Routine.Block b : m.forDay(today.plusDays(1)).blocks) if (b.kind != Routine.SLEEP && !b.skipped) blocks.add(b);
        return GoalSlots.of(blocks);
    }

    /** Which entry of the list of ways to repeat a goal is: a one-day goal for tomorrow is "Tomorrow only". */
    private static int indexOfGoal(Routine.Goal g, LocalDate today) {
        if (g.repeat == Routine.Goal.ONCE && g.date != null && g.date.equals(today.plusDays(1))) return TOMORROW;
        return indexOfRepeat(g.repeat);
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
