package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** The Exam Specific section: shown until the person has a schedule of their own, then gone for good; Hide only hides it for now before that. */
public class FirstDayTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    public static void main(String[] args) {
        Routine r = Routine.sample();
        ok("the sample day is not a schedule of the person's own", !FirstDay.customized(r));
        ok("a routine read back from its text is still the sample", !FirstDay.customized(Routine.parse(r.serialize())));

        Routine e = Routine.parse(r.serialize());
        e.anchor = 400;
        ok("only another start time: not customized", !FirstDay.customized(e));
        e.mode = Routine.MODE_TASK;
        ok("only another way to start the day: not customized", !FirstDay.customized(e));

        e = Routine.parse(r.serialize());
        e.blocks.get(2).title = "My own name";
        ok("a renamed slot: customized", FirstDay.customized(e));
        e = Routine.parse(r.serialize());
        e.blocks.get(2).mins += 5;
        ok("a longer slot: customized", FirstDay.customized(e));
        e = Routine.parse(r.serialize());
        e.blocks.remove(2);
        ok("a deleted slot: customized", FirstDay.customized(e));
        e = Routine.parse(r.serialize());
        e.blocks.add(new Routine.Block(Routine.TASK, "Extra", 10));
        ok("an added slot: customized", FirstDay.customized(e));
        e = Routine.parse(r.serialize());
        e.blocks.get(2).setAlert(e.blocks.get(2).alert == Routine.ALERT_NONE ? Routine.ALERT_RING : Routine.ALERT_NONE);
        ok("a changed alert: customized", FirstDay.customized(e));
        e = Routine.parse(r.serialize());
        e.others.add(new Routine());
        ok("another routine: customized", FirstDay.customized(e));
        e = Routine.parse(r.serialize());
        e.addTemp(LocalDate.of(2026, 10, 20), "One day", e);
        ok("a routine for one date: customized", FirstDay.customized(e));
        for (int i = 0; i < RoutineTemplates.count(); i++) {
            Routine t = Routine.parse(r.serialize());
            t.copyScheduleFrom(RoutineTemplates.build(i));
            ok("taking the template " + RoutineTemplates.name(i) + " is customized", FirstDay.customized(t));
        }

        ok("Hide before a schedule of one's own: only for now", FirstDay.hide(r) == FirstDay.HIDE_FOR_NOW);
        e = Routine.parse(r.serialize());
        e.blocks.get(2).title = "My own name";
        ok("Hide after a schedule of one's own: for good", FirstDay.hide(e) == FirstDay.HIDE_FOR_GOOD);

        ok("shown to a new person", FirstDay.shown(r, false, false));
        ok("not shown after Hide this session", !FirstDay.shown(r, false, true));
        ok("shown again next session (the session flag is gone)", FirstDay.shown(r, false, false));
        ok("not shown once a schedule of one's own is made", !FirstDay.shown(e, false, false));
        ok("not shown after it was remembered, even when the routine is the sample again", !FirstDay.shown(r, true, false));
        ok("done: remembered once customized", FirstDay.done(e, false) && !FirstDay.done(r, false) && FirstDay.done(r, true));

        // the bar
        List<int[]> s = FirstDay.shape(r);
        int sum = 0;
        for (int[] x : s) sum += x[1];
        ok("the bar covers the whole day", sum == 1440);
        boolean merged = true;
        for (int i = 1; i < s.size(); i++) if (s.get(i)[0] == s.get(i - 1)[0]) merged = false;
        ok("slots of the same kind next to each other are one run", merged);
        Routine two = new Routine();
        two.blocks.clear();
        two.blocks.add(new Routine.Block(Routine.TASK, "A", 30));
        two.blocks.add(new Routine.Block(Routine.TASK, "B", 45));
        two.blocks.add(new Routine.Block(Routine.REST, "C", 15));
        two.blocks.add(new Routine.Block(Routine.TASK, "D", 60));
        List<int[]> q = FirstDay.shape(two);
        ok("two tasks then a rest then a task: three runs 75, 15, 60", q.size() == 3 && q.get(0)[1] == 75 && q.get(1)[0] == Routine.REST && q.get(1)[1] == 15 && q.get(2)[1] == 60);
        Routine none = new Routine();
        none.blocks.clear();
        ok("an empty routine has an empty bar", FirstDay.shape(none).isEmpty());

        // templates
        ok("four exam templates, the rest are for every day", RoutineTemplates.EXAMS == 4 && RoutineTemplates.isExam(0) && RoutineTemplates.isExam(3)
                && !RoutineTemplates.isExam(4) && !RoutineTemplates.isExam(5) && !RoutineTemplates.isExam(-1) && !RoutineTemplates.isExam(6));
        ok("the exam templates are UPSC, SSC / Banking, Board exams, JEE / NEET",
                RoutineTemplates.name(0).startsWith("UPSC") && RoutineTemplates.name(1).startsWith("SSC") && RoutineTemplates.name(2).startsWith("Board") && RoutineTemplates.name(3).startsWith("JEE"));
        ok("wake times match what the templates build", RoutineTemplates.wake(0) == 300 && RoutineTemplates.wake(1) == 330 && RoutineTemplates.build(2).anchor == RoutineTemplates.wake(2));
        for (int i = 0; i < RoutineTemplates.count(); i++) {
            int t = 0;
            for (int[] x : FirstDay.shape(RoutineTemplates.build(i))) t += x[1];
            ok("template " + RoutineTemplates.name(i) + " fills the day in its bar", t == 1440);
        }

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
