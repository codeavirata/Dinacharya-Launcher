package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** The Routine tab may not change a slot that today has already lived; Change plan is for that. "From tomorrow" keeps today exactly as it was. */
public class RoutineLockTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    static Routine base() {
        Routine r = Routine.sample();
        r.mode = Routine.MODE_WAKE;
        r.anchor = 330; // 5:30: slot 3 (Morning focus block) runs 7:15 to 9:15
        return r;
    }

    static Routine copy(Routine r) { return Routine.parse(r.serialize()); }

    static long at(int h, int m) { return DAY.atStartOfDay(Z).plusMinutes(h * 60L + m).toInstant().toEpochMilli(); }

    static boolean refused(Routine saved, Routine edited, Set<Integer> lived, boolean strict) {
        return RoutineLock.violation(saved, edited, DAY, lived, strict) != null;
    }

    static int total(Plan.DayPlan p) { int s = 0; for (Routine.Slot sl : p.sc.slots) s += sl.block.mins; return s; }

    static boolean samePlan(Plan.DayPlan a, Plan.DayPlan b) {
        if (a.sc.slots.size() != b.sc.slots.size() || a.startMs != b.startMs) return false;
        for (int i = 0; i < a.sc.slots.size(); i++) {
            Routine.Slot x = a.sc.slots.get(i), y = b.sc.slots.get(i);
            if (x.start != y.start || x.end != y.end || x.block.src != y.block.src || !x.block.title.equals(y.block.title)
                    || x.block.kind != y.block.kind || x.block.skipped != y.block.skipped) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        Routine r = base();
        Plan.DayPlan p = Plan.resolve(r, new Overrides(), at(8, 0), Z);
        ok("8:00 is inside the morning focus block", p.day.equals(DAY) && p.cur == 3);

        // ---- which slots count as lived: every slot that has started, whatever the history says
        Set<Integer> lived = RoutineLock.lived(p);
        ok("every slot that has started is lived, with no alarm and no history at all", lived.equals(new TreeSet<>(Arrays.asList(0, 1, 2, 3))));
        ok("a slot that has not started yet is not lived", !lived.contains(4) && !lived.contains(18));
        Plan.DayPlan atStart = Plan.resolve(r, new Overrides(), at(5, 30), Z);
        ok("at the very start of the day the first slot is already lived", RoutineLock.lived(atStart).equals(new TreeSet<>(Arrays.asList(0))));
        ok("a few minutes later the slot after it is not lived yet", !RoutineLock.lived(Plan.resolve(r, new Overrides(), at(6, 4), Z)).contains(2));
        Plan.DayPlan later = Plan.resolve(r, new Overrides(), at(9, 20), Z);
        ok("later in the day more slots are lived", RoutineLock.lived(later).containsAll(Arrays.asList(0, 1, 2, 3, 4)) && !RoutineLock.lived(later).contains(5));

        // ---- the day after the first setup
        long setup = at(14, 0);
        ok("no record of the setup: no grace", !RoutineLock.inGrace(0, at(15, 0)));
        ok("an hour after the setup: grace", RoutineLock.inGrace(setup, at(15, 0)));
        ok("23 hours after the setup: grace", RoutineLock.inGrace(setup, setup + 23L * 3600000));
        ok("a day after the setup: no grace", !RoutineLock.inGrace(setup, setup + RoutineLock.GRACE_MS));
        ok("the clock put back before the setup gives no grace", !RoutineLock.inGrace(setup, setup - 3600000));

        // ---- what may and may not change
        Set<Integer> L = new TreeSet<>(Arrays.asList(0, 1, 2, 3));
        ok("an unchanged routine is allowed", !refused(r, copy(r), L, false));
        Routine e = copy(r); e.blocks.get(3).title = "Netflix";
        ok("renaming the running slot is refused", refused(r, e, L, false));
        ok("the refusal names the slot", "Morning focus block".equals(RoutineLock.violation(r, e, DAY, L, false)));
        e = copy(r); e.blocks.remove(3);
        ok("deleting the running slot is refused", refused(r, e, L, false));
        e = copy(r); e.blocks.get(3).mins = 90;
        ok("shortening the running slot is refused", refused(r, e, L, false));
        e = copy(r); e.blocks.get(3).mins = 150;
        ok("lengthening the running slot is refused", refused(r, e, L, false));
        e = copy(r); e.blocks.get(1).mins = 20;
        ok("shortening a slot that is over is refused", refused(r, e, L, false));
        e = copy(r); e.blocks.get(2).kind = Routine.REST;
        ok("changing the type of a lived slot is refused", refused(r, e, L, false));
        e = copy(r); e.blocks.get(2).sub = "Other";
        ok("changing the activity of a lived slot is refused", refused(r, e, L, false));
        e = copy(r); e.blocks.get(3).alert = Routine.ALERT_SILENT;
        ok("making a lived slot silent is refused", refused(r, e, L, false));
        e = copy(r); e.anchor = 360;
        ok("moving the start of the day is refused", refused(r, e, L, false));
        e = copy(r); e.blocks.add(0, new Routine.Block(Routine.REST, "Extra", 10));
        ok("adding a slot before a lived one is refused", refused(r, e, L, false));
        e = copy(r); Routine.Block mv = e.blocks.remove(3); e.blocks.add(5, mv);
        ok("moving a lived slot is refused", refused(r, e, L, false));
        e = RoutineTemplates.build(0);
        ok("replacing the day with a template is refused", refused(r, e, L, false));

        e = copy(r); e.blocks.get(1).mins = 60;
        ok("moving a lived slot in time, by changing an unlived slot before it, is refused", refused(r, e, new TreeSet<>(Arrays.asList(3)), false));
        e = copy(r); e.blocks.get(8).title = "Late lunch";
        ok("renaming a later slot is allowed", !refused(r, e, L, false));
        e = copy(r); e.blocks.get(5).mins = 100; e.blocks.get(9).mins = 10;
        ok("making a later slot longer or shorter is allowed", !refused(r, e, L, false));
        e = copy(r); e.blocks.remove(8);
        ok("deleting a later slot is allowed", !refused(r, e, L, false));
        e = copy(r); e.blocks.add(e.blocks.size() - 1, new Routine.Block(Routine.TASK, "Read", 30));
        ok("adding a slot at the end is allowed", !refused(r, e, L, false));
        e = copy(r); e.blocks.get(3).note = "Chapter 4";
        ok("a note on a lived slot is allowed (it changes nothing about the day)", !refused(r, e, L, false));
        ok("with nothing lived anything is allowed", !refused(r, RoutineTemplates.build(2), new TreeSet<>(), false));
        e = copy(r); e.anchor = 400;
        ok("with nothing lived the start may move", !refused(r, e, new TreeSet<>(), false));

        // ---- strict switch
        e = copy(r); e.blocks.get(8).title = "Late lunch";
        ok("strict: any change to the running day is refused", refused(r, e, L, true));
        ok("strict: it is refused even when nothing was lived", refused(r, e, new TreeSet<>(), true));
        ok("strict: an unchanged day is allowed", !refused(r, copy(r), L, true));
        e = copy(r); e.blocks.get(8).note = "less oil";
        ok("strict: a note is allowed", !refused(r, e, L, true));
        ok("strict: the refusal has no slot name", "".equals(RoutineLock.violation(r, withTitle(r, 8, "x"), DAY, L, true)));

        // ---- other routines and one-day routines
        Routine w = copy(r);
        Routine other = w.addOther("Weekend", w);
        e = copy(w); e.others.get(0).blocks.get(2).title = "Brunch";
        ok("editing a routine that today does not use is allowed", !refused(w, e, L, false));
        Routine t = copy(r);
        t.addTemp(DAY, "Today", r);
        e = copy(t); e.blocks.get(3).title = "Netflix";
        ok("editing the main routine while today follows a one-day routine leaves today alone", !refused(t, e, L, false));
        e = copy(t); e.tempFor(DAY).blocks.get(3).title = "Netflix";
        ok("editing today's own one-day routine over a lived slot is refused", refused(t, e, L, false));

        // ---- from tomorrow: today stays exactly as it was
        Overrides ov = new Overrides();
        Plan.editPart(r, ov, DAY, 8, "Salad", 30);
        Plan.swapPlaces(r, ov, DAY, 10, 12);
        ov.setShift(DAY.plusDays(1), Overrides.WHOLE, 20);
        Plan.DayPlan before = Plan.plan(r, ov, DAY, Z);
        Routine ed = copy(r);
        ed.blocks.get(3).title = "Netflix";
        ed.blocks.get(8).mins = 45; ed.blocks.get(9).mins = 35;
        Routine saved = RoutineLock.pin(r, ed, DAY);
        Overrides carried = RoutineLock.carry(ov, saved, DAY);
        Plan.DayPlan after = Plan.plan(saved, carried, DAY, Z);
        ok("from tomorrow: today's plan, with its Change plan edits, is exactly as it was", samePlan(before, after));
        ok("from tomorrow: today is still 24 hours", total(after) == total(before));
        ok("from tomorrow: the running slot still has its old name", after.sc.slots.get(3).block.title.equals("Morning focus block"));
        Plan.DayPlan tomorrow = Plan.plan(saved, carried, DAY.plusDays(1), Z);
        ok("from tomorrow: tomorrow follows the edited routine", tomorrow.sc.slots.get(3).block.title.equals("Netflix") && tomorrow.sc.slots.get(8).block.mins == 45);
        ok("from tomorrow: the one-day routine is named for today", saved.tempFor(DAY) != null && saved.tempFor(DAY).name.endsWith("(today)"));
        ok("from tomorrow: the saved day-of-routine is the edited one", saved.blocks.get(3).title.equals("Netflix"));
        ok("from tomorrow: today's slot changes are kept", carried.edits.size() == 2 || carried.editCount(DAY) >= 2);
        ok("from tomorrow: the overrides belong to the new routine", carried.sig.equals(Plan.signature(saved)));
        ok("from tomorrow: the new routine and its one-day routine survive saving", Routine.parse(saved.serialize()).tempFor(DAY) != null);
        Plan.DayPlan reread = Plan.plan(Routine.parse(saved.serialize()), Overrides.parse(carried.serialize()), DAY, Z);
        ok("from tomorrow: still the same after being saved and read back", samePlan(before, reread));
        Overrides yest = new Overrides();
        Plan.editPart(r, yest, DAY.minusDays(1), 4, "Chat", 5);
        ok("from tomorrow: slot changes of other days are dropped (their numbers mean the old list)", RoutineLock.carry(yest, saved, DAY).editCount(DAY.minusDays(1)) == 0);
        ok("from tomorrow: a later start of tomorrow is kept", carried.shiftFor(DAY.plusDays(1)) != null);

        // ---- a routine edit that leaves today alone keeps today's Change plan edits
        Routine e2 = copy(w); e2.others.get(0).blocks.get(2).title = "Brunch";
        Overrides ov2 = new Overrides();
        Plan.editPart(w, ov2, DAY, 8, "Salad", 30);
        Overrides kept = RoutineLock.carry(ov2, e2, DAY);
        ok("an edit that leaves today alone keeps today's edits", samePlan(Plan.plan(w, ov2, DAY, Z), Plan.plan(e2, kept, DAY, Z)));

        // ---- after midnight the schedule day is still yesterday's
        long late = DAY.plusDays(1).atStartOfDay(Z).plusMinutes(30).toInstant().toEpochMilli();   // 00:30, the day that began on DAY runs until 5:30
        Plan.DayPlan night = Plan.resolve(r, new Overrides(), late, Z);
        ok("at 00:30 the schedule day is still the one that began yesterday, in its sleep slot", night.day.equals(DAY) && night.cur == 18);
        Set<Integer> nl = RoutineLock.lived(night);
        ok("at 00:30 every slot of the day that began yesterday has started", nl.size() == 19);
        e = copy(r); e.blocks.get(18).mins = 420; e.blocks.get(17).mins = 30 + 60;
        ok("shortening tonight's sleep from the Routine tab is refused", refused(r, e, nl, false));
        e = copy(r); e.blocks.get(12).title = "Revise";
        ok("at night an edit of any slot of the day that is ending is refused", refused(r, e, nl, false));
        Routine none = copy(r); none.alarmOn = false;
        ok("it makes no difference whether alarms are on or off", refused(none, e, nl, false));

        // ---- slots that Change plan moved: lived follows the slot, not the place
        Overrides sw = new Overrides();
        Plan.swapPlaces(r, sw, DAY, 10, 12);
        Plan.DayPlan swapped = Plan.resolve(r, sw, at(15, 0), Z);
        ok("after a swap, Revision runs at 15:00", swapped.cur >= 0 && swapped.sc.slots.get(swapped.cur).block.src == 12);
        Set<Integer> sl = RoutineLock.lived(swapped);
        ok("after a swap the slots that have started are the lived ones", sl.contains(12) && !sl.contains(10) && !sl.contains(11));
        e = copy(r); e.blocks.get(12).title = "Netflix";
        ok("after a swap, renaming the slot that is running is refused", refused(r, e, sl, false));
        e = copy(r); e.blocks.get(10).title = "Calls";
        ok("after a swap, the slot that moved later can still be edited", !refused(r, e, sl, false));

        if (fails > 0) System.exit(1);
        System.out.println("ALL PASS");
    }

    static Routine withTitle(Routine r, int i, String t) { Routine c = copy(r); c.blocks.get(i).title = t; return c; }
}
