package app.dinacharya.wallpaper;

import java.time.*;

/** Replace part of a slot, or swap two slots, for one day: the day stays 24 hours and the rest returns to the saved plan. */
public class ChangePlanTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");

    static int total(Plan.DayPlan p) { int s = 0; for (Routine.Slot sl : p.sc.slots) s += sl.block.mins; return s; }

    public static void main(String[] a) {
        Routine r = Routine.sample();
        r.mode = Routine.MODE_WAKE;
        r.anchor = 330;
        LocalDate day = LocalDate.of(2026, 10, 20);
        Plan.DayPlan base = Plan.plan(r, new Overrides(), day, Z);
        int n = base.sc.slots.size();
        int src = 2;
        int len = r.blocks.get(src).mins;
        Overrides ov = new Overrides();
        Plan.editPart(r, ov, day, src, "Revision", 20);
        Plan.DayPlan p = Plan.plan(r, ov, day, Z);
        ok("part adds one slot", p.sc.slots.size() == n + 1);
        ok("day length kept", total(p) == total(base));
        ok("first part renamed", p.sc.slots.get(src).block.title.equals("Revision") && p.sc.slots.get(src).block.mins == 20);
        ok("rest is original", p.sc.slots.get(src + 1).block.title.equals(r.blocks.get(src).title) && p.sc.slots.get(src + 1).block.mins == len - 20);
        ok("later slots same start", p.sc.slots.get(src + 2).start == base.sc.slots.get(src + 1).start);

        ov = new Overrides();
        Plan.swapPlaces(r, ov, day, 1, 3);
        p = Plan.plan(r, ov, day, Z);
        ok("swap keeps count and length", p.sc.slots.size() == n && total(p) == total(base));
        ok("swap trades places", p.sc.slots.get(1).block.src == 3 && p.sc.slots.get(3).block.src == 1);

        Overrides back = Overrides.parse(ov.serialize());
        ok("swap survives save", back.edits.size() == 1 && back.edits.get(0).with == 3);
        Overrides o2 = new Overrides();
        Plan.editPart(r, o2, day, src, "Revision", 20);
        ok("part survives save", Overrides.parse(o2.serialize()).edits.get(0).mins == 20);
        Overrides o3 = new Overrides();
        Plan.editPart(r, o3, day, src, "Mock test", 0, Routine.TASK, "Practice");
        Plan.DayPlan p3 = Plan.plan(r, o3, day, Z);
        ok("whole slot takes type and sub type", p3.sc.slots.get(src).block.title.equals("Mock test") && p3.sc.slots.get(src).block.sub.equals("Practice") && p3.sc.slots.size() == n);
        Overrides o4 = Overrides.parse(o3.serialize());
        ok("type survives save", o4.edits.get(0).kind == Routine.TASK && o4.edits.get(0).sub.equals("Practice"));
        if (fails > 0) System.exit(1);
        System.out.println("ALL PASS");
    }
}
