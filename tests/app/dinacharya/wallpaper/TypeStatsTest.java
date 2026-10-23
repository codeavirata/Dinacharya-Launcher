package app.dinacharya.wallpaper;

import java.util.*;

public class TypeStatsTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static DayReport.Row row(int kind, String sub, int mins, int status, int passedMin) {
        DayReport.Row r = new DayReport.Row();
        r.kind = kind; r.sub = sub; r.plannedMin = mins; r.status = status; r.activeMs = passedMin * 60000L; r.title = sub + " slot";
        return r;
    }

    static DayReport day(String d, int studyKept, boolean partial) {
        DayReport r = new DayReport();
        r.day = d; r.partial = partial;
        r.goals.add(new Routine.Goal(Routine.TASK, "Study", 240));
        r.rows.add(row(Routine.TASK, "Study", studyKept, DayReport.KEPT, studyKept));
        r.rows.add(row(Routine.TASK, "Study", 60, DayReport.SKIPPED, 60));
        return r;
    }

    public static void main(String[] a) {
        DayReport r = new DayReport();
        r.day = "2026-10-19";
        r.goals.add(new Routine.Goal(Routine.TASK, "Study", 360));
        r.goals.add(new Routine.Goal(Routine.PERSONAL, "Exercise", 30));
        r.rows.add(row(Routine.TASK, "Study", 120, DayReport.KEPT, 120));
        r.rows.add(row(Routine.TASK, "study", 90, DayReport.CHANGED, 90));       // renamed: not done
        r.rows.add(row(Routine.TASK, "Study", 90, DayReport.UNTRACKED, 90));     // no alarm: trusted
        r.rows.add(row(Routine.TASK, "Study", 60, DayReport.KEPT, 25));          // running now: 25 min so far
        r.rows.add(row(Routine.TASK, "Study", 60, DayReport.SKIPPED, 60));       // taken off: not planned any more
        r.rows.add(row(Routine.TASK, "Revision", 45, DayReport.UNANSWERED, 45)); // no answer: not done
        r.rows.add(row(Routine.TASK, "", 30, DayReport.KEPT, 30));               // no type: not listed
        r.rows.add(row(Routine.SLEEP, "", 480, DayReport.UNTRACKED, 480));
        List<TypeStats.Line> lines = TypeStats.of(r);
        ok("goals first, in order, then other types", lines.size() == 3 && lines.get(0).label().equals("Study")
                && lines.get(1).label().equals("Exercise") && lines.get(2).label().equals("Revision"));
        TypeStats.Line st = lines.get(0);
        ok("study planned excludes taken-off slots (120+90+90+60)", st.plannedMin == 360);
        ok("study done = kept + no-alarm + passed part of running slot (120+90+25)", st.doneMin == 235);
        ok("goal not met yet, 65%", !st.met() && st.pct() == 65);
        TypeStats.Line ex = lines.get(1);
        ok("goal with no slots today shows 0 of goal", ex.plannedMin == 0 && ex.doneMin == 0 && ex.goalMin == 30);
        ok("unanswered revision does not count", lines.get(2).doneMin == 0 && lines.get(2).plannedMin == 45 && !lines.get(2).hasGoal());
        ok("case of the type name does not matter", TypeStats.find(lines, Routine.TASK, "STUDY") == st);

        // report round trip keeps types and goals
        r.score = 50;
        DayReport back = DayReport.parse(r.serialize());
        ok("round trip: row types and goals", back != null && back.rows.get(0).sub.equals("Study") && back.goals.size() == 2
                && back.goals.get(1).sub.equals("Exercise") && back.goals.get(1).mins == 30);
        ok("round trip: same totals", TypeStats.of(back).get(0).doneMin == 235);
        DayReport oldStyle = DayReport.parse("DINAREPORT1\nday=2026-10-01\nR\t0\t0\t1\t2\t60\t0\t60\tOld\t\n");
        ok("old report without types still loads", oldStyle != null && oldStyle.rows.get(0).sub.isEmpty() && oldStyle.goals.isEmpty());

        // streaks
        Routine.Goal g = new Routine.Goal(Routine.TASK, "Study", 240);
        List<DayReport> past = new ArrayList<>();
        past.add(day("2026-10-18", 240, false));
        past.add(day("2026-10-17", 300, false));
        DayReport noStudy = new DayReport(); noStudy.day = "2026-10-16"; noStudy.goals.add(g);
        past.add(noStudy);                       // a day with no study slots: skipped
        past.add(day("2026-10-15", 250, false));
        past.add(day("2026-10-14", 100, false)); // missed: streak stops here
        past.add(day("2026-10-13", 300, false));
        ok("streak counts back to the first missed day", TypeStats.streak(g, null, past) == 3);
        ok("today not met yet does not break it", TypeStats.streak(g, day("2026-10-19", 60, true), past) == 3);
        ok("today met adds one", TypeStats.streak(g, day("2026-10-19", 240, true), past) == 4);
        List<DayReport> broken = new ArrayList<>(); broken.add(day("2026-10-18", 10, false)); broken.addAll(past);
        ok("yesterday missed: 0", TypeStats.streak(g, null, broken) == 0);

        // week averages
        List<TypeStats.Line> avg = TypeStats.average(Arrays.asList(day("a", 240, false), day("b", 120, false)));
        ok("average per day", avg.size() == 1 && avg.get(0).doneMin == 180 && avg.get(0).goalMin == 240);

        // assembler copies type and goals from the plan
        Routine base = Routine.sample();
        base.setGoal(Routine.TASK, "Study", 300);
        java.time.ZoneId z = java.time.ZoneId.of("Asia/Kolkata");
        Plan.DayPlan p = Plan.plan(base, new Overrides(), java.time.LocalDate.of(2026, 10, 19), z);
        DayReport rep = ReportAssembler.assemble(p, new ArrayList<Ev>(), null, false, p.endMs + 1);
        TypeStats.Line study = TypeStats.find(TypeStats.of(rep), Routine.TASK, "Study");
        ok("assembled: goal present and study slots counted (alarms off, so trusted)", study != null && study.goalMin == 300
                && study.plannedMin == 210 && study.doneMin == 210);

        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
