package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** A timer started before the routine was changed must not ring for the old routine next to the new one. */
public class StaleTimerTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }

    static Routine base() {
        Routine r = Routine.sample();
        r.alarmOn = true;
        r.mode = Routine.MODE_WAKE;
        r.anchor = 330;
        return r;
    }

    static Overrides ovFor(Routine r) { Overrides o = new Overrides(); o.sig = Plan.signature(r); return o; }

    static boolean hasTimer(Routine r, AlarmState st, long after) {
        for (AlarmPlanner.Event e : AlarmPlanner.upcoming(r, ovFor(r), st, after, Z)) if (e.isTimer() || e.kind == AlarmPlanner.REASK) return true;
        return false;
    }

    public static void main(String[] a) {
        LocalDate day = LocalDate.of(2026, 10, 20);
        // a slot in the middle of the day of the saved routine
        Routine r1 = base();
        Plan.DayPlan p = Plan.plan(r1, ovFor(r1), day, Z);
        int idx = Math.min(2, p.sc.slots.size() - 2);
        Routine.Block b = p.sc.slots.get(idx).block;
        long start = p.slotStartMs(idx), end = p.slotEndMs(idx);
        long after = start - 3600000L;

        // 1. "start later" asked on that slot, routine untouched: the timer stays
        AlarmState st = new AlarmState();
        st.againAt = start + 600000L; st.againDay = day.toString(); st.againSrc = b.src; st.againTitle = b.title;
        st.againSlotStart = start; st.againSlotEnd = end;
        ok("untouched routine keeps the timer", !StaleTimers.drop(r1, ovFor(r1), st, Z) && st.againAt > 0);

        // 2. the slot is renamed in the saved routine: the old timer goes
        Routine r2 = Routine.parse(r1.serialize());
        r2.blocks.get(b.src).title = "Something else";
        ok("renamed slot: the old timer goes", StaleTimers.drop(r2, ovFor(r2), st, Z) && st.againAt == 0);

        // 3. the slot gets a different length (ends later): the old timer goes
        st = new AlarmState();
        st.againAt = start + 600000L; st.againDay = day.toString(); st.againSrc = b.src; st.againTitle = b.title;
        st.againSlotStart = start; st.againSlotEnd = end;
        Routine r3 = Routine.parse(r1.serialize());
        r3.blocks.get(b.src).mins += 30;
        ok("longer slot: the old timer goes", StaleTimers.drop(r3, ovFor(r3), st, Z) && st.againAt == 0);

        // 4. the slot is taken out of the routine: the old timer goes, and nothing of the old routine is left to ring
        st = new AlarmState();
        st.againAt = start + 600000L; st.againDay = day.toString(); st.againSrc = b.src; st.againTitle = b.title;
        st.againSlotStart = start; st.againSlotEnd = end;
        st.breakFrom = start; st.breakTo = start + 900000L; st.breakDay = day.toString(); st.breakSrc = b.src; st.breakTitle = b.title; st.breakSlotEnd = end;
        Routine r4 = Routine.parse(r1.serialize());
        r4.blocks.remove(b.src);
        boolean leftBefore = hasTimer(r4, st, after);
        ok("old routine's timers would still ring before the fix is applied", leftBefore);
        ok("removed slot: drop reports a change", StaleTimers.drop(r4, ovFor(r4), st, Z));
        ok("removed slot: no timer is left", !hasTimer(r4, st, after));

        // 5. a break on the slot of a routine for one day (temp), which is then deleted
        st = new AlarmState();
        st.breakFrom = start; st.breakTo = start + 900000L; st.breakDay = day.toString(); st.breakSrc = b.src; st.breakTitle = b.title; st.breakSlotEnd = end;
        ok("same routine: the break stays", !StaleTimers.drop(r1, ovFor(r1), st, Z) && st.breakTo > 0);

        // 6. bedtime question asked again, then the bedtime slot is renamed
        int sleepIdx = -1;
        for (int i = 0; i < p.sc.slots.size(); i++) if (p.sc.slots.get(i).block.kind == Routine.SLEEP) sleepIdx = i;
        if (sleepIdx >= 0) {
            Routine.Block s = p.sc.slots.get(sleepIdx).block;
            st = new AlarmState();
            st.reaskAt = p.slotStartMs(sleepIdx) + 900000L; st.reaskDay = day.toString(); st.reaskSrc = s.src; st.reaskTitle = s.title; st.reaskBedMs = p.slotStartMs(sleepIdx);
            ok("bedtime question again: stays while the routine is the same", !StaleTimers.drop(r1, ovFor(r1), st, Z) && st.reaskAt > 0);
            Routine r6 = Routine.parse(r1.serialize());
            r6.blocks.remove(s.src);
            ok("bedtime slot removed: the question goes", StaleTimers.drop(r6, ovFor(r6), st, Z) && st.reaskAt == 0);
        }
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
