package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** "No response": an alarm that goes off and is not answered. The slot counts from the answer, and the time before is marked. */
public class NoResponseTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final long MIN = 60000L;
    static long at(int h, int m) { return ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, Z).toInstant().toEpochMilli(); }

    static Routine.Block cur(Plan.DayPlan p) { return p.sc.slots.get(p.cur).block; }

    public static void main(String[] args) {
        Routine r = Routine.sample();
        Plan.DayPlan base = Plan.resolve(r, new Overrides(), at(8, 30), Z);
        int x = base.cur + 1;
        while (x < base.sc.slots.size() && (base.sc.slots.get(x).block.kind == Routine.SLEEP || base.sc.slots.get(x).block.src < 0 || base.sc.slots.get(x).block.mins < 60)) x++;
        Routine.Block bx = base.sc.slots.get(x).block;
        String day = base.day.toString();
        long planned = base.slotStartMs(x);
        long end = base.slotEndMs(x);
        AlarmState st = new AlarmState();

        List<Ev> rang = new ArrayList<>();
        rang.add(Ev.rang(planned + 2000, day, bx.src, bx.title));

        // answered in time: nothing
        List<Ev> quick = new ArrayList<>(rang);
        quick.add(Ev.ack(planned + 2 * MIN, day, bx.src, bx.title, "C", ""));
        ok("answered within the grace time: no response", NoResponse.until(quick, bx.src, planned + 5 * MIN) == 0);
        ok("and the slot is not changed", LivePlan.apply(base, quick, st, false, planned + 5 * MIN) == base || cur(LivePlan.apply(base, quick, st, false, planned + 5 * MIN)).live != Routine.LIVE_WAIT);

        // not answered yet
        ok("not answered, still within the grace time: nothing yet", NoResponse.until(rang, bx.src, planned + 2 * MIN) == 0);
        ok("not answered after the grace time: pending", NoResponse.until(rang, bx.src, planned + 5 * MIN) == NoResponse.PENDING);
        Plan.DayPlan waiting = LivePlan.apply(base, rang, st, false, planned + 5 * MIN);
        ok("the slot waits, called No response", waiting.cur >= 0 && cur(waiting).live == Routine.LIVE_WAIT && LivePlan.NORESP_TITLE.equals(cur(waiting).title));
        SlotGaps.Result g0 = SlotGaps.of(rang, bx.src, planned, end, planned + 5 * MIN);
        ok("the five minutes are counted as no response, none as active time", g0.noRespMs == 5 * MIN && g0.delayMs == 0 && g0.gapMs() == 5 * MIN);

        // answered late
        List<Ev> late = new ArrayList<>(rang);
        late.add(Ev.ack(planned + 10 * MIN, day, bx.src, bx.title, "C", ""));
        ok("answered after the grace time: the stretch ends at the answer", NoResponse.until(late, bx.src, planned + 30 * MIN) == planned + 10 * MIN);
        SlotGaps.Result g1 = SlotGaps.of(late, bx.src, planned, end, planned + 30 * MIN);
        ok("ten minutes of no response, then the slot counts", g1.noRespMs == 10 * MIN && g1.delayMs == 0);
        Plan.DayPlan after = LivePlan.apply(base, late, st, false, planned + 30 * MIN);
        ok("after the answer the slot runs", after.cur >= 0 && cur(after).live != Routine.LIVE_WAIT && cur(after).src == bx.src);

        // answered late and the start put off
        List<Ev> later = new ArrayList<>(late);
        later.add(Ev.delay(planned + 10 * MIN, day, bx.src, bx.title, planned + 25 * MIN));
        SlotGaps.Result g2 = SlotGaps.of(later, bx.src, planned, end, planned + 40 * MIN);
        ok("no response and the put-off start do not count twice", g2.noRespMs == 10 * MIN && g2.delayMs == 15 * MIN && g2.gapMs() == 25 * MIN);

        // never answered
        SlotGaps.Result g3 = SlotGaps.of(rang, bx.src, planned, end, end + 5 * MIN);
        ok("never answered: the whole slot is no response", g3.noRespMs >= end - planned - MIN && g3.gapMs() >= end - planned - MIN);

        // began before its alarm
        List<Ev> early = new ArrayList<>();
        early.add(Ev.ack(planned - 5 * MIN, day, bx.src, bx.title, "C", ""));
        early.add(Ev.rang(planned + 2000, day, bx.src, bx.title));
        ok("started before its alarm: no response", NoResponse.until(early, bx.src, planned + 20 * MIN) == 0);

        // another slot, no alarm
        ok("a slot whose alarm did not go off has none", NoResponse.until(rang, bx.src + 1, planned + 20 * MIN) == 0);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
