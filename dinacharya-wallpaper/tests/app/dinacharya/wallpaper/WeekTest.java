package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

public class WeekTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static LocalDate day(int d) { return LocalDate.of(2026, 10, d); }

    static Routine week() {
        Routine r = Routine.sample();
        r.name = "Weekdays";
        Routine we = r.addOther("Weekend", r);
        we.anchor = 450; // 7:30
        we.blocks.get(3).title = "Long study";
        r.dayUse[5] = 1; // Sat
        r.dayUse[6] = 1; // Sun
        return r;
    }

    public static void main(String[] a) {
        // 2026-10-23 is a Friday, 24 Saturday, 25 Sunday, 26 Monday.
        ok("calendar check", day(23).getDayOfWeek() == DayOfWeek.FRIDAY);
        Routine r = week();
        ok("main used on weekdays", r.forDay(day(23)) == r && r.useOn(day(23)) == 0);
        ok("weekend used on Saturday", r.useOn(day(24)) == 1 && r.forDay(day(24)).anchor == 450);
        ok("weekend copy carries the main look and types", r.forDay(day(24)).palette == r.palette && r.forDay(day(24)).subs.size() == r.subs.size());

        Plan.DayPlan fri = Plan.plan(r, new Overrides(), day(23), Z);
        ok("Friday starts 5:30", fri.startMs == at(23, 5, 30));
        ok("Friday ends when Saturday starts (7:30)", fri.endMs == at(24, 7, 30));
        Routine.Block friSleep = fri.sc.slots.get(fri.sc.slots.size() - 1).block;
        ok("Friday night sleep runs 2h longer, as planned sleep", friSleep.mins == 600 && friSleep.extended == 0);
        ok("Friday slots still fill the day exactly", fri.slotEndMs(fri.sc.slots.size() - 1) == fri.endMs);

        Plan.DayPlan sat = Plan.plan(r, new Overrides(), day(24), Z);
        ok("Saturday starts 7:30 with weekend slots", sat.startMs == at(24, 7, 30) && sat.sc.slots.get(3).block.title.equals("Long study"));
        ok("Saturday ends Sunday 7:30", sat.endMs == at(25, 7, 30) && sat.slotEndMs(sat.sc.slots.size() - 1) == sat.endMs);

        Plan.DayPlan sun = Plan.plan(r, new Overrides(), day(25), Z);
        Routine.Block sunSleep = sun.sc.slots.get(sun.sc.slots.size() - 1).block;
        ok("Sunday night is 2h shorter so Monday starts 5:30", sun.endMs == at(26, 5, 30) && sunSleep.mins == 360
                && sun.slotEndMs(sun.sc.slots.size() - 1) == sun.endMs);
        ok("not adjusted (it is the plan, not a change)", !fri.adjusted && !sun.adjusted);

        Plan.DayPlan early = Plan.resolve(r, new Overrides(), at(24, 6, 30), Z);
        ok("Saturday 6:30 is still Friday's night", early.day.equals(day(23)) && early.cur == early.sc.slots.size() - 1);
        Plan.DayPlan late = Plan.resolve(r, new Overrides(), at(24, 7, 45), Z);
        ok("Saturday 7:45 is Saturday, first slot", late.day.equals(day(24)) && late.cur == 0);

        // Alarms follow the routine of each day.
        Routine ra = week();
        ra.alarmOn = true;
        AlarmPlanner.Event e = AlarmPlanner.next(ra, new Overrides(), new AlarmState(), at(24, 1, 0), Z);
        ok("first alarm on Saturday is at 7:30", e != null && e.timeMs == at(24, 7, 30));
        AlarmPlanner.Event e2 = AlarmPlanner.next(ra, new Overrides(), new AlarmState(), at(26, 1, 0), Z);
        ok("first alarm on Monday is at 5:30", e2 != null && e2.timeMs == at(26, 5, 30));

        // Today-only rename on a weekend day names the weekend slot.
        Overrides ov = new Overrides();
        Plan.edit(r, ov, day(24), 3, false, "Mock test");
        Plan.DayPlan satEdited = Plan.plan(r, ov, day(24), Z);
        ok("rename applies to the weekend slot", satEdited.sc.slots.get(3).block.title.equals("Mock test")
                && satEdited.sc.slots.get(3).block.origTitle.equals("Long study"));
        ok("valid overrides survive", Plan.valid(r, ov) == ov);
        r.others.get(0).blocks.get(5).mins += 5;
        ok("changing the weekend routine clears today's changes", Plan.valid(r, ov) != ov);

        // Single-routine signature is unchanged by the new code (older saved changes stay valid).
        Routine single = Routine.sample();
        String sig = Plan.signature(single);
        single.palette = 2;
        single.setGoal(Routine.TASK, "Study", 360);
        ok("look and goals do not change the signature", Plan.signature(single).equals(sig));

        // Saving and loading.
        Routine w = week();
        w.blocks.get(3).note = "Ch. 4\tpolity\nback\\slash";
        w.others.get(0).blocks.get(0).note = "Weekend note";
        w.setGoal(Routine.TASK, "Study", 360);
        w.setGoal(Routine.PERSONAL, "Exercise", 45);
        w.showMark = false;
        Routine back = Routine.parse(w.serialize());
        ok("round trip: names", back != null && back.name.equals("Weekdays") && back.others.size() == 1 && back.others.get(0).name.equals("Weekend"));
        ok("round trip: days", back != null && Arrays.equals(back.dayUse, w.dayUse));
        ok("round trip: weekend start and slots", back.others.get(0).anchor == 450 && back.others.get(0).blocks.size() == w.others.get(0).blocks.size()
                && back.others.get(0).blocks.get(3).title.equals("Long study"));
        ok("round trip: notes with tab, line break and backslash", back.blocks.get(3).note.equals("Ch. 4\tpolity\nback\\slash")
                && back.others.get(0).blocks.get(0).note.equals("Weekend note"));
        ok("round trip: goals", back.goals.size() == 2 && back.findGoal(Routine.TASK, "study").mins == 360);
        ok("round trip: developer mark switch", !back.showMark);
        ok("round trip: same signature", Plan.signature(back).equals(Plan.signature(w)));

        // Removing a routine sends its days back to the main one.
        Routine x = week();
        x.addOther("Exam day", x);
        x.dayUse[2] = 2;
        x.removeOther(1);
        ok("removing: weekend days fall back, later numbers shift down", x.dayUse[5] == 0 && x.dayUse[6] == 0 && x.dayUse[2] == 1
                && x.others.size() == 1 && x.others.get(0).name.equals("Exam day"));

        // An empty routine in a damaged save is dropped.
        String dmg = "DINACHARYA1\nmode=0\nanchor=330\nB\t0\t60\tStudy\t1\nB\t4\t1380\tSleep\t1\ndays=0,0,0,0,0,1,2\nP\tEmpty\t0\t300\nP\tSat\t0\t420\nB\t3\t1440\tFree\t1\n";
        Routine d = Routine.parse(dmg);
        ok("empty routine dropped, days renumbered", d != null && d.others.size() == 1 && d.others.get(0).name.equals("Sat")
                && d.dayUse[5] == 0 && d.dayUse[6] == 1);

        // Old saves still load as one routine.
        Routine old = Routine.parse("DINACHARYA1\nmode=0\nanchor=330\nB\t0\t60\tStudy hard\t1\nB\t4\t480\tSleep\t1\n");
        ok("old save: one routine, no goals, mark on", old != null && !old.hasWeek() && old.goals.isEmpty() && old.showMark);

        // Undo keeps the same object.
        Routine u = week();
        Routine snap = Routine.parse(u.serialize());
        u.blocks.remove(0);
        u.removeOther(1);
        u.replaceWith(snap);
        ok("undo restores routines and days", u.blocks.size() == 19 && u.others.size() == 1 && u.dayUse[5] == 1);

        // Types: removing a type clears it everywhere and drops its goal.
        Routine t = week();
        t.others.get(0).blocks.get(3).sub = "Study";
        t.setGoal(Routine.TASK, "Study", 300);
        t.removeSub(t.findSub(Routine.TASK, "Study"));
        boolean anyStudy = false;
        for (Routine q : t.all()) for (Routine.Block b : q.blocks) if (b.sub.equalsIgnoreCase("Study")) anyStudy = true;
        ok("removing a type clears it in every routine and drops its goal", !anyStudy && t.findGoal(Routine.TASK, "Study") == null);

        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
