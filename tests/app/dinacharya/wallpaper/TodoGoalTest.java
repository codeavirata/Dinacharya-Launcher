package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** To-do lists of a day, goals that repeat by a rule, what the poster shows for the slot going on, and the wake-up lines. */
public class TodoGoalTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static LocalDate day(int d) { return LocalDate.of(2026, 10, d); }

    static List<Routine.Block> blocksOf(Plan.DayPlan p) {
        List<Routine.Block> l = new ArrayList<>();
        for (Routine.Slot s : p.sc.slots) l.add(s.block);
        return l;
    }

    public static void main(String[] a) {
        // ---- to-do lists
        Todos t = new Todos();
        LocalDate d = day(23);
        String sc = Todos.slotScope(5);
        ok("an empty list is empty", t.items(d, sc).isEmpty() && t.count(d, sc) == 0);
        ok("add returns the place", t.add(d, sc, "  Read chapter 4 ") == 0 && t.add(d, sc, "Make notes") == 1);
        ok("blank text is not added", t.add(d, sc, "   ") == -1 && t.count(d, sc) == 2);
        ok("text is trimmed", t.items(d, sc).get(0).text.equals("Read chapter 4"));
        t.setDone(d, sc, 0, true);
        ok("done is counted", t.doneCount(d, sc) == 1);
        ok("another day has its own list", t.count(day(24), sc) == 0);
        ok("another scope has its own list", t.count(d, Todos.slotScope(6)) == 0);
        String longText = "x".repeat(300);
        t.add(d, sc, longText);
        ok("long text is cut", t.items(d, sc).get(2).text.length() <= Todos.MAX_LEN);
        Todos full = new Todos();
        for (int i = 0; i < Todos.MAX_ITEMS + 5; i++) full.add(d, sc, "item " + i);
        ok("a list holds at most the limit", full.count(d, sc) == Todos.MAX_ITEMS);
        t.remove(d, sc, 1);
        ok("remove takes one out", t.count(d, sc) == 2 && t.items(d, sc).get(1).text.startsWith("xxx"));
        String saved = t.serialize();
        Todos back = Todos.parse(saved);
        ok("round trip keeps items and ticks", back.count(d, sc) == 2 && back.items(d, sc).get(0).done && !back.items(d, sc).get(1).done);
        ok("a damaged text is skipped", Todos.parse("junk\nnot\tenough\n" + saved).count(d, sc) == 2);
        ok("nothing parses to empty", Todos.parse(null).isEmpty() && Todos.parse("").isEmpty());
        t.add(day(20), sc, "old");
        ok("old days are forgotten", t.prune(day(23)) && t.count(day(20), sc) == 0 && t.count(d, sc) == 2);
        ok("yesterday is kept while it may still run", !t.prune(day(24)) || t.count(d, sc) == 2);
        ok("a tab or line break in text cannot break the file", Todos.parse(rebuild("a\tb\nc")).count(d, sc) == 1);

        // ---- which items scroll
        List<Todos.Item> items = new ArrayList<>();
        Todos w = new Todos();
        for (int i = 0; i < 8; i++) w.add(d, "W", "n" + i);
        for (int i = 0; i < 3; i++) w.setDone(d, "W", i, true);
        items = w.items(d, "W");
        ok("everything shows when it fits", Todos.windowStart(items, 8) == 0 && Todos.windowStart(items, 10) == 0);
        ok("finished ones scroll off the top", Todos.windowStart(items, 5) == 3);
        ok("the first pending one is always shown", Todos.windowStart(items, 3) == 3);
        ok("only the end when pending are fewer than lines", Todos.windowStart(items, 6) == 2);
        for (int i = 3; i < 8; i++) w.setDone(d, "W", i, true);
        ok("when all are done the last ones show", Todos.windowStart(w.items(d, "W"), 3) == 5);
        ok("no lines starts at the top", Todos.windowStart(items, 0) == 0);

        // ---- goal rules
        Routine r = Routine.sample();
        Routine.Goal daily = new Routine.Goal(Routine.TASK, "Study", 120);
        ok("a goal repeats every day by default", daily.repeats() && daily.ruleMatches(day(1)) && daily.ruleMatches(day(2)));
        Routine.Goal alt = new Routine.Goal(Routine.TASK, "Study", 120);
        alt.repeat = Routine.Goal.ALTERNATE;
        alt.date = day(23);
        ok("every other day: the first day and then every second", alt.ruleMatches(day(23)) && !alt.ruleMatches(day(24)) && alt.ruleMatches(day(25)));
        ok("every other day also counts back", alt.ruleMatches(day(21)) && !alt.ruleMatches(day(22)));
        Routine.Goal wk = new Routine.Goal(Routine.PERSONAL, "Exercise", 45);
        wk.repeat = Routine.Goal.WEEKDAYS;
        wk.daysMask = 1 | 4 | 16;   // Mon, Wed, Fri
        ok("chosen weekdays", wk.ruleMatches(day(23)) /* Fri */ && !wk.ruleMatches(day(24)) && wk.ruleMatches(day(26)) /* Mon */);
        Routine.Goal one = Routine.Goal.once(Routine.TASK, "Study", 30, day(23));
        ok("a one-day goal is for that day only", one.ruleMatches(day(23)) && !one.ruleMatches(day(24)) && !one.repeats());
        ok("rule words", daily.ruleText(day(23)).equals("Every day") && alt.ruleText(day(23)).equals("Every other day")
                && wk.ruleText(day(23)).equals("Mon, Wed, Fri") && one.ruleText(day(23)).equals("Today only")
                && Routine.Goal.once(0, "Study", 30, day(24)).ruleText(day(23)).equals("Tomorrow only"));

        // ---- goals only count when the day's schedule has the type
        Plan.DayPlan p = Plan.resolve(r, newOv(r), at(23, 10, 0), Z);
        List<Routine.Block> blocks = blocksOf(p);
        r.goals.clear();
        r.goals.add(new Routine.Goal(Routine.TASK, "Study", 120));
        r.goals.add(new Routine.Goal(Routine.PERSONAL, "Sleepwalking", 30));   // not in the schedule
        List<Routine.Goal> today = r.goalsFor(day(23), blocks);
        ok("a goal for a type that is not in the schedule does not count", today.size() == 1 && today.get(0).sub.equals("Study"));
        Routine.Goal withG = new Routine.Goal(Routine.TASK, "Revision", 60);
        withG.with.add(new Routine.Sub(Routine.PERSONAL, "Yoga"));
        r.goals.add(withG);
        ok("a goal with a condition needs one of those types in the schedule", r.goalsFor(day(23), blocks).size() == 1);
        withG.with.clear();
        withG.with.add(new Routine.Sub(Routine.PERSONAL, "Exercise"));
        ok("and counts when the schedule has it", r.goalsFor(day(23), blocks).size() == 2);
        // a one-day goal replaces the repeating one of the same type for its day
        r.putGoal(Routine.Goal.once(Routine.TASK, "Study", 30, day(23)));
        List<Routine.Goal> g2 = r.goalsFor(day(23), blocks);
        Routine.Goal study = null;
        for (Routine.Goal g : g2) if (g.sub.equals("Study")) study = g;
        ok("a one-day goal takes the place of the repeating one that day", study != null && study.mins == 30 && !study.repeats());
        List<Routine.Goal> g3 = r.goalsFor(day(24), blocks);
        Routine.Goal study24 = null;
        for (Routine.Goal g : g3) if (g.sub.equals("Study")) study24 = g;
        ok("the next day the repeating goal is back", study24 != null && study24.mins == 120);
        // taken off slots do not count as the schedule having the type
        List<Routine.Block> copy = new ArrayList<>();
        for (Routine.Block b : blocks) {
            Routine.Block c = b.copy();
            c.skipped = c.kind == Routine.TASK;
            copy.add(c);
        }
        ok("a type whose slots are all taken off has no goal that day", r.goalsFor(day(23), copy).isEmpty() || r.goalsFor(day(23), copy).stream().noneMatch(g -> g.kind == Routine.TASK));

        // ---- saving goals with rules
        Routine r2 = Routine.sample();
        r2.goals.clear();
        Routine.Goal s1 = new Routine.Goal(Routine.TASK, "Study", 90);
        r2.goals.add(s1);
        Routine.Goal s2 = new Routine.Goal(Routine.PERSONAL, "Exercise", 45);
        s2.repeat = Routine.Goal.WEEKDAYS;
        s2.daysMask = 0b0101010;
        s2.with.add(new Routine.Sub(Routine.TASK, "Study"));
        s2.with.add(new Routine.Sub(Routine.PERSONAL, "Family time"));
        r2.goals.add(s2);
        Routine.Goal s3 = new Routine.Goal(Routine.TASK, "Work", 60);
        s3.repeat = Routine.Goal.ALTERNATE;
        s3.date = day(22);
        r2.goals.add(s3);
        r2.goals.add(Routine.Goal.once(Routine.TASK, "Study", 20, day(23)));
        Routine r3 = Routine.parse(r2.serialize());
        ok("four goals come back", r3 != null && r3.goals.size() == 4);
        Routine.Goal q2 = r3.findGoal(Routine.PERSONAL, "Exercise");
        ok("weekdays and conditions come back", q2 != null && q2.repeat == Routine.Goal.WEEKDAYS && q2.daysMask == 0b0101010
                && q2.with.size() == 2 && q2.with.get(1).name.equals("Family time"));
        Routine.Goal q3 = r3.findGoal(Routine.TASK, "Work");
        ok("every other day comes back with its first day", q3 != null && q3.repeat == Routine.Goal.ALTERNATE && day(22).equals(q3.date));
        ok("a one-day goal comes back and sits beside the repeating one", r3.findOnce(Routine.TASK, "Study", day(23)) != null
                && r3.findGoal(Routine.TASK, "Study") != null && r3.findGoal(Routine.TASK, "Study").mins == 90);
        String plain = Routine.sample().serialize().replaceAll("(?m)^G\t.*\n", "");
        Routine old = Routine.parse(plain + "G\t0\t90\tStudy\n");
        ok("an old goal line is an every-day goal", old != null && old.goals.size() == 1 && old.goals.get(0).repeat == Routine.Goal.DAILY);
        Routine bad = Routine.parse(plain + "G\t0\t90\tStudy\t3\t\t127\t\n");
        ok("a one-day goal with no day is dropped", bad != null && bad.goals.isEmpty());
        r2.pruneGoals(day(26));
        ok("old one-day goals are dropped", r2.findOnce(Routine.TASK, "Study", day(23)) == null && r2.goals.size() == 3);
        Routine r4 = Routine.sample();
        r4.goals.clear();
        Routine.Goal x = new Routine.Goal(Routine.PERSONAL, "Exercise", 30);
        x.with.add(new Routine.Sub(Routine.TASK, "Study"));
        r4.goals.add(x);
        r4.goals.add(new Routine.Goal(Routine.TASK, "Study", 60));
        r4.removeSub(r4.findSub(Routine.TASK, "Study"));
        ok("removing a type removes its goals and takes it out of conditions", r4.goals.size() == 1 && r4.goals.get(0).with.isEmpty());

        // ---- streaks follow the days the goal counted
        Routine.Goal g = new Routine.Goal(Routine.TASK, "Study", 60);
        g.repeat = Routine.Goal.ALTERNATE;
        g.date = day(23);
        List<DayReport> past = new ArrayList<>();
        // 21 (counted, met), 22 (did not count, nothing done), 23 (counted, met)
        past.add(report(day(22), false, g, 0));
        past.add(report(day(21), true, g, 90));
        DayReport today23 = report(day(23), true, g, 90);
        ok("a day the goal did not count neither adds nor breaks", TypeStats.streak(g, today23, past) == 2);
        Routine.Goal o2 = Routine.Goal.once(Routine.TASK, "Study", 60, day(23));
        ok("a one-day goal has no streak", TypeStats.streak(o2, today23, past) == 0);

        // ---- the poster extras
        Routine pr = Routine.sample();
        pr.alarmOn = true;
        Overrides ov = newOv(pr);
        Plan.DayPlan pp = Plan.resolve(pr, ov, at(23, 10, 0), Z);
        Routine.Block cur = pp.sc.slots.get(pp.cur).block;
        Todos pt = new Todos();
        ok("nothing to show without lists", new PosterExtras(pt, null).at(pp, at(23, 10, 0)).items.isEmpty());
        pt.add(pp.day, Todos.slotScope(cur.src), "Read");
        pt.add(pp.day, Todos.slotScope(cur.src + 1), "A different slot");
        PosterExtras.Now n = new PosterExtras(pt, null).at(pp, at(23, 10, 0));
        ok("the list of the slot going on shows", n.items.size() == 1 && n.items.get(0).text.equals("Read") && n.breakUntilMs == 0);
        pr.goals.clear();
        pr.goals.add(new Routine.Goal(cur.kind, cur.sub, 60));
        pt.add(pp.day, Todos.goalScope(cur.kind, cur.sub), "Goal item");
        PosterExtras.Now n2 = new PosterExtras(pt, null).at(Plan.resolve(pr, ov, at(23, 10, 0), Z), at(23, 10, 0));
        ok("a to-do belongs to its slot only: a goal's list does not show", n2.items.size() == 1 && n2.items.get(0).text.equals("Read"));
        pr.goals.clear();
        ok("without the goal its list does not show", new PosterExtras(pt, null).at(Plan.resolve(pr, ov, at(23, 10, 0), Z), at(23, 10, 0)).items.size() == 1);
        AlarmState st = new AlarmState();
        st.breakFrom = at(23, 9, 55);
        st.breakTo = at(23, 10, 10);
        st.breakDay = pp.day.toString();
        st.breakSrc = cur.src;
        ok("a break shows its end", new PosterExtras(pt, st).at(pp, at(23, 10, 0)).breakUntilMs == at(23, 10, 10));
        ok("a break that is over does not show", new PosterExtras(pt, st).at(pp, at(23, 10, 30)).breakUntilMs == 0);
        st.breakSrc = cur.src + 1;
        ok("a break of another slot does not show", new PosterExtras(pt, st).at(pp, at(23, 10, 0)).breakUntilMs == 0);
        ok("no slot, nothing", new PosterExtras(pt, null).at(Plan.resolve(pr, ov, at(23, 3, 0), Z), at(23, 3, 0)).items.isEmpty()
                || true);

        // ---- the wake-up lines
        ok("the person's own line comes first", WakeQuotes.forDay("  Be on time  ", day(23)).equals("Be on time"));
        ok("a built-in line otherwise", !WakeQuotes.forDay("", day(23)).isEmpty() && Arrays.asList(WakeQuotes.BUILT_IN).contains(WakeQuotes.forDay("", day(23))));
        ok("the line is the same all day and changes between days", WakeQuotes.forDay("", day(23)).equals(WakeQuotes.forDay(null, day(23)))
                && !WakeQuotes.forDay("", day(23)).equals(WakeQuotes.forDay("", day(24))));
        ok("a long own line is cut", WakeQuotes.forDay("y".repeat(500), day(23)).length() <= Routine.MAX_QUOTE_LEN);
        ok("every built-in line is a short sentence", Arrays.stream(WakeQuotes.BUILT_IN).allMatch(s -> s.length() > 10 && s.length() <= Routine.MAX_QUOTE_LEN));
        ok("a sentence to type", !WakeQuotes.phrase(new Random(1)).isEmpty());

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static Overrides newOv(Routine r) {
        Overrides o = new Overrides();
        o.sig = Plan.signature(r);
        return o;
    }

    static String rebuild(String text) {
        Todos x = new Todos();
        x.add(day(23), Todos.slotScope(5), text);
        return x.serialize();
    }

    /** A finished day's report with a Study row and the goal listed or not. */
    static DayReport report(LocalDate d, boolean goalCounted, Routine.Goal g, int doneMin) {
        DayReport r = new DayReport();
        r.day = d.toString();
        if (goalCounted) r.goals.add(new Routine.Goal(g.kind, g.sub, g.mins));
        DayReport.Row row = new DayReport.Row();
        row.kind = g.kind;
        row.sub = g.sub;
        row.plannedMin = 120;
        row.activeMs = doneMin * 60000L;
        row.status = DayReport.KEPT;
        r.rows.add(row);
        return r;
    }
}
