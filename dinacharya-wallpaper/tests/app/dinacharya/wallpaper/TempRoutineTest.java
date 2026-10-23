package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** Activity types with "Other", their colours, the time-by-type breakdown, and routines for one date. */
public class TempRoutineTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static LocalDate day(int d) { return LocalDate.of(2026, 10, d); }

    public static void main(String[] a) {
        // ---- types
        Routine r = new Routine();
        ok("new routine has Meal, Personal and Sleep types", r.findSub(Routine.MEAL, "Meal") != null
            && r.findSub(Routine.PERSONAL, "Personal") != null && r.findSub(Routine.SLEEP, "Sleep") != null);
        ok("Study, Exercise and Family time are still there", r.findSub(Routine.TASK, "Study") != null
            && r.findSub(Routine.PERSONAL, "Exercise") != null && r.findSub(Routine.PERSONAL, "Family time") != null);
        boolean everyOther = true;
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) everyOther &= r.findSub(k, Routine.OTHER) != null;
        ok("every kind has Other", everyOther);
        ok("Other is last in its kind", r.subsOf(Routine.TASK).get(r.subsOf(Routine.TASK).size() - 1).name.equals(Routine.OTHER));
        ok("a new block starts as Other", new Routine.Block(Routine.TASK, "x", 30).sub.equals(Routine.OTHER));
        r.removeSub(r.findSub(Routine.TASK, Routine.OTHER));
        ok("Other cannot be removed", r.findSub(Routine.TASK, Routine.OTHER) != null);

        Routine s = Routine.sample();
        s.blocks.get(0).sub = "";
        s.syncSubs();
        ok("an empty type becomes Other on sync", s.blocks.get(0).sub.equals(Routine.OTHER));
        String legacy = "DINACHARYA1\nmode=0\nanchor=330\nsubs=1\nU\t0\tStudy\nB\t0\t60\tRead\t1\t\nB\t2\t30\tLunch\t1\tMeal\nB\t4\t480\tSleep\t1\n";
        Routine lg = Routine.parse(legacy);
        ok("saved before 2.9: blank types become Other", lg != null && lg.blocks.get(0).sub.equals(Routine.OTHER));
        ok("saved before 2.9: the new types are added once", lg.findSub(Routine.MEAL, "Meal") != null && lg.findSub(Routine.SLEEP, "Sleep") != null && lg.findSub(Routine.PERSONAL, "Personal") != null);
        lg.removeSub(lg.findSub(Routine.MEAL, "Meal"));
        Routine lg2 = Routine.parse(lg.serialize());
        ok("a deleted default type does not come back", lg2.findSub(Routine.MEAL, "Meal") == null && lg2.blocks.get(1).sub.equals(Routine.OTHER));
        ok("saved text says the list is complete", lg.serialize().contains("subs=2"));

        // ---- colours
        Routine c = Routine.sample();
        Set<Integer> seen = new HashSet<>();
        boolean distinct = true;
        for (Routine.Sub u : c.subs) {
            if (Routine.isOther(u.name)) continue;
            distinct &= seen.add(SubColors.of(c, u.kind, u.name));
        }
        ok("every named type has its own colour", distinct);
        Set<Integer> others = new HashSet<>();
        for (int k = 0; k < 5; k++) others.add(SubColors.of(c, k, Routine.OTHER));
        ok("the five Other types differ from each other", others.size() == 5);
        ok("no named type shares an Other colour", Collections.disjoint(seen, others));
        Routine many = new Routine();
        for (int i = 0; i < 40; i++) many.addSub(Routine.TASK, "T" + i);
        Set<Integer> manyCols = new HashSet<>();
        for (Routine.Sub u : many.subs) if (!Routine.isOther(u.name)) manyCols.add(SubColors.of(many, u.kind, u.name));
        ok("colours stay distinct past the palette", manyCols.size() == many.subs.size() - 5);
        int before = SubColors.of(c, Routine.TASK, "Study");
        c.addSub(Routine.TASK, "Maths");
        ok("a colour does not change when a type is added", SubColors.of(c, Routine.TASK, "Study") == before);

        // ---- time by type
        Routine t = Routine.sample();
        int[] byKind = Allotment.byKind(t);
        int sum = 0;
        for (int v : byKind) sum += v;
        ok("kinds add up to the routine", sum == t.totalMinutes() && byKind[Routine.SLEEP] == 480);
        int subSum = 0;
        for (Allotment.Item it : Allotment.bySub(t)) subSum += it.mins;
        ok("types add up to the routine", subSum == t.totalMinutes());
        boolean studyFirst = false;
        for (Allotment.Item it : Allotment.bySub(t)) if (it.kind == Routine.TASK) { studyFirst = it.sub.equals("Study"); break; }
        ok("the longest type of a kind comes first", studyFirst);

        // ---- one-date routines
        Routine m = Routine.sample();
        ok("no temporary routine at first", !m.hasTemps() && m.tempFor(day(24)) == null && m.dayKey(day(24)) == 0);
        Routine tmp = m.addTemp(day(24), "Tomorrow", m);
        tmp.anchor = 420;
        tmp.blocks.get(3).title = "Mock test";
        ok("a temporary routine is found for its date only", m.tempFor(day(24)) == tmp && m.tempFor(day(23)) == null);
        ok("its date follows it", m.forDay(day(24)).anchor == 420 && m.forDay(day(24)).blocks.get(3).title.equals("Mock test"));
        ok("other dates follow the usual routine", m.forDay(day(23)) == m && m.forDay(day(25)) == m);
        ok("the saved copy is independent of the original", !m.blocks.get(3).title.equals("Mock test"));
        ok("it carries the look, types and goals", m.forDay(day(24)).palette == m.palette && m.forDay(day(24)).subs.size() == m.subs.size());
        ok("dayKey tells it apart", m.dayKey(day(24)) >= 1000 && m.dayKey(day(24)) != m.dayKey(day(23)));
        Routine again = m.addTemp(day(24), "Again", m);
        ok("one temporary routine per date", m.temps.size() == 1 && m.tempFor(day(24)) == again);
        again.anchor = 420;
        again.blocks.get(3).title = "Mock test";

        Routine back = Routine.parse(m.serialize());
        ok("round trip keeps the temporary routine", back != null && back.temps.size() == 1 && back.temps.get(0).tempDate.equals(day(24))
            && back.temps.get(0).anchor == 420 && back.temps.get(0).blocks.get(3).title.equals("Mock test")
            && back.temps.get(0).blocks.size() == m.blocks.size());
        ok("the main routine is unchanged by it", back.blocks.size() == m.blocks.size() && back.blocks.get(3).title.equals(m.blocks.get(3).title));
        ok("an older reader would read past the slots (they use their own line type)", !m.serialize().contains("\nB\t0\t120\tMock test"));
        ok("temporary slots use TB lines", m.serialize().contains("\nTB\t"));

        Routine snapCopy = new Routine();
        snapCopy.replaceWith(m);
        ok("replaceWith (undo) keeps temporary routines", snapCopy.temps.size() == 1);

        // weekday routines still work next to it
        Routine w = Routine.sample();
        Routine we = w.addOther("Weekend", w);
        we.anchor = 450;
        w.dayUse[5] = 1;
        w.addTemp(day(24), "Exam", w).anchor = 360;   // 24 Oct is a Saturday
        ok("a temporary routine beats the weekday routine", w.forDay(day(24)).anchor == 360 && w.forDay(day(24)).name.equals("Exam"));
        ok("the other Saturday still follows the weekend routine", w.forDay(day(31)).anchor == 450);
        Routine wb = Routine.parse(w.serialize());
        ok("both survive a round trip", wb.others.size() == 1 && wb.temps.size() == 1 && wb.forDay(day(31)).anchor == 450 && wb.forDay(day(24)).anchor == 360);

        // pruning
        Routine p = Routine.sample();
        p.addTemp(day(20), "Old", p);
        p.addTemp(day(22), "Yesterday", p);
        p.addTemp(day(25), "Later", p);
        boolean pruned = p.pruneTemps(day(23));
        ok("old routines for a date are forgotten after a day", pruned && p.temps.size() == 2 && p.tempFor(day(20)) == null && p.tempFor(day(22)) != null);
        ok("temporary routines stay sorted by date", p.temps.get(0).tempDate.isBefore(p.temps.get(1).tempDate));
        p.moveTemp(p.tempFor(day(25)), day(22));
        ok("moving onto a taken date replaces it", p.temps.size() == 1 && p.temps.get(0).name.equals("Later") && p.temps.get(0).tempDate.equals(day(22)));

        // the plan
        Routine q = Routine.sample();
        Routine qt = q.addTemp(day(24), "Late start", q);
        qt.anchor = 8 * 60;
        Plan.DayPlan fri = Plan.plan(q, new Overrides(), day(23), Z);
        Plan.DayPlan sat = Plan.plan(q, new Overrides(), day(24), Z);
        ok("the day before a temporary routine ends when it starts", fri.endMs == sat.startMs);
        ok("the temporary day starts at its own time", sat.sc.start == 8 * 60);
        ok("a plan without it is unchanged", Plan.plan(q, new Overrides(), day(26), Z).sc.start == q.compute().start);
        String sig = Plan.signature(q);
        q.addTemp(day(27), "Other", q);
        ok("adding a temporary routine does not drop today's changes", Plan.signature(q).equals(sig));

        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
