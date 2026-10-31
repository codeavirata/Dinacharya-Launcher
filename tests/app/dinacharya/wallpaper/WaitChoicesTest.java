package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** While a slot's start is put off: "Start now" ends the wait, "Continue the slot before" moves it to that moment, "Other plan" cancels it. */
public class WaitChoicesTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final long MIN = 60000L;
    static long at(int h, int m) { return ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, Z).toInstant().toEpochMilli(); }

    static Routine.Block curBlock(Plan.DayPlan p) { return p.sc.slots.get(p.cur).block; }

    public static void main(String[] args) {
        Routine r = Routine.sample();
        Plan.DayPlan base = Plan.resolve(r, new Overrides(), at(8, 30), Z);
        int x = base.cur + 1;
        // find a slot x whose slot before it can be carried on
        while (x < base.sc.slots.size() && SlotPlay.previousIndex(base, x) < 0) x++;
        Routine.Block bx = base.sc.slots.get(x).block;
        Routine.Block bp = base.sc.slots.get(SlotPlay.previousIndex(base, x)).block;
        String day = base.day.toString();
        long planned = base.slotStartMs(x);
        long now = planned + 3 * MIN;
        AlarmState st = new AlarmState();

        List<Ev> ev = new ArrayList<>();
        ev.add(Ev.ack(planned, day, bx.src, bx.title, "C", ""));
        ev.add(Ev.delay(planned, day, bx.src, bx.title, planned + 10 * MIN));
        Plan.DayPlan waiting = LivePlan.apply(Plan.resolve(r, new Overrides(), now, Z), ev, st, false, now);
        ok("with a late start the slot is waiting", curBlock(waiting).live == Routine.LIVE_WAIT);
        ok("the wait ends where the delay said", waiting.slotEndMs(waiting.cur) == planned + 10 * MIN);

        // start now: the wait stops at once
        List<Ev> started = new ArrayList<>(ev);
        started.add(Ev.ack(now, day, bx.src, bx.title, "C", ""));
        started.add(Ev.delay(now, day, bx.src, bx.title, now));
        long later = now + MIN;
        Plan.DayPlan go = LivePlan.apply(Plan.resolve(r, new Overrides(), later, Z), started, st, false, later);
        ok("after Start now the slot is not waiting", curBlock(go).live != Routine.LIVE_WAIT && curBlock(go).src == bx.src);
        SlotGaps.Result g = SlotGaps.of(started, bx.src, planned, base.slotEndMs(x), later);
        ok("only the time really waited counts as delay", g.delayMs == 3 * MIN);

        // continue the slot before until 4 minutes from now: the wait moves to that moment
        long until = now + 4 * MIN;
        List<Ev> carried = new ArrayList<>(ev);
        carried.add(Ev.ack(now, day, bx.src, bx.title, "C", ""));
        carried.add(Ev.extend(now, day, bx.src, bp.src, bp.title, until));
        carried.add(Ev.delay(now, day, bx.src, bx.title, until));
        Plan.DayPlan during = LivePlan.apply(Plan.resolve(r, new Overrides(), later, Z), carried, st, false, later);
        ok("the slot before goes on", curBlock(during).src == bp.src);
        ok("it goes on until the chosen moment", during.slotEndMs(during.cur) == until);
        long after = until + MIN;
        Plan.DayPlan then = LivePlan.apply(Plan.resolve(r, new Overrides(), after, Z), carried, st, false, after);
        ok("then the slot starts, not a wait", curBlock(then).src == bx.src && curBlock(then).live != Routine.LIVE_WAIT);

        // other plan: the wait is cancelled and the slot has its new name for today
        Overrides ov = new Overrides();
        Plan.edit(r, ov, base.day, bx.src, false, "Something else");
        List<Ev> other = new ArrayList<>(ev);
        other.add(Ev.ack(now, day, bx.src, bx.title, "R", "Something else"));
        other.add(Ev.delay(now, day, bx.src, bx.title, planned));
        Plan.DayPlan swapped = LivePlan.apply(Plan.resolve(r, ov, later, Z), other, st, false, later);
        ok("after Other plan the slot is not waiting", curBlock(swapped).live != Routine.LIVE_WAIT);
        ok("it has the new name for today", curBlock(swapped).title.equals("Something else"));
        ok("its type is the same", curBlock(swapped).kind == bx.kind);
        ok("no wait is counted", SlotGaps.of(other, bx.src, planned, base.slotEndMs(x), later).delayMs == 0);
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
