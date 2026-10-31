package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** The goals strip of the wallpaper: progress is the plan so far, at most four goals, the nearest to done first. */
public class GoalGlanceTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int h, int m) { return ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, Z).toInstant().toEpochMilli(); }

    public static void main(String[] args) {
        Routine r = Routine.sample();
        Plan.DayPlan early = Plan.resolve(r, new Overrides(), at(8, 30), Z);
        Routine.Block b = null;
        for (Routine.Slot s : early.sc.slots) if (s.block.kind != Routine.SLEEP && s.block.sub != null && !s.block.sub.isEmpty() && (s.end - s.start) >= 60) { b = s.block; break; }
        ok("the sample has a typed slot of an hour or more", b != null);
        if (b == null) { System.out.println(fails + " FAILS"); System.exit(1); }
        ok("no goals, nothing to show", GoalGlance.of(r, early).isEmpty());

        r.goals.add(new Routine.Goal(b.kind, b.sub, 60));
        Plan.DayPlan day = Plan.resolve(r, new Overrides(), at(8, 30), Z);
        List<GoalGlance> g = GoalGlance.of(r, day);
        ok("a goal of a type in today's plan shows", g.size() == 1);
        float f = g.isEmpty() ? -1 : g.get(0).frac;
        ok("progress is between nothing and the target", f >= 0f && f <= 1f);

        Plan.DayPlan late = Plan.resolve(r, new Overrides(), at(23, 0), Z);
        List<GoalGlance> done = GoalGlance.of(r, late);
        ok("later in the day the goal is further along", !done.isEmpty() && done.get(0).frac >= f);

        for (int i = 0; i < 6; i++) r.goals.add(new Routine.Goal(b.kind, "Extra" + i, 30 + i));
        ok("at most four goals are shown", GoalGlance.of(r, day).size() <= GoalGlance.MAX);

        Routine skippedAll = Routine.sample();
        ok("a routine with no goals shows none", GoalGlance.of(skippedAll, day).isEmpty());
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
