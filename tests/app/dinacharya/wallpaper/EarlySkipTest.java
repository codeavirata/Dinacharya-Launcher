package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * Skipping a slot when it begins, finishing early in every slot (Finish early, Early sleep before sleep, Early wake for sleep), and what
 * each leaves behind: the day as it is going, the alarms, the report and the next day. All on the sample routine (wake 05:30, bed 21:30)
 * on 21 October 2026.
 */
public class EarlySkipTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final String DAY = "2026-10-21";
    static final LocalDate D = LocalDate.of(2026, 10, 21);
    static long at(int d, int h, int m, int s) { return ZonedDateTime.of(2026, 10, d, h, m, s, 0, Z).toInstant().toEpochMilli(); }
    static long at(int h, int m) { return at(21, h, m, 0); }

    static Routine routine() { Routine r = Routine.sample(); r.alarmOn = true; return r; }
    static int total(Plan.DayPlan p) { int t = 0; for (Routine.Slot s : p.sc.slots) t += s.block.mins; return t; }
    static String hm(Plan.DayPlan p, int i, boolean end) {
        long ms = end ? p.slotEndMs(i) : p.slotStartMs(i);
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(Z);
        return String.format("%02d:%02d", z.getHour(), z.getMinute());
    }
    static int indexOfSrc(Plan.DayPlan p, int src, int live) {
        for (int i = 0; i < p.sc.slots.size(); i++) { Routine.Block b = p.sc.slots.get(i).block; if (b.src == src && b.live == live) return i; }
        return -1;
    }

    /** What the app does when a slot is skipped at its start: marks it taken off for the day and starts the next real one now. */
    static List<Ev> skipped(Routine r, Overrides ov, int src, long now) {
        Plan.edit(r, ov, D, src, true, "");
        Plan.DayPlan p = Plan.plan(r, ov, D, Z);
        int i = SlotPlay.indexOfSrc(p, src);
        int n = i + 1;
        while (n < p.sc.slots.size() && (p.sc.slots.get(n).block.skipped || p.sc.slots.get(n).block.src < 0)) n++;
        List<Ev> ev = new ArrayList<>();
        ev.add(Ev.ack(now, DAY, src, p.sc.slots.get(i).block.title, "S", ""));
        ev.add(Ev.skip(now, DAY, src, p.sc.slots.get(i).block.title, p.sc.slots.get(n).block.src, now));
        ev.add(Ev.ack(now, DAY, p.sc.slots.get(n).block.src, p.sc.slots.get(n).block.title, "C", ""));
        return ev;
    }

    public static void main(String[] args) {
        Routine r = routine();

        // ---- the day that starts early: Overrides
        Overrides ov0 = new Overrides();
        ok("no early start to begin with", ov0.earlyFor(D) == 0 && ov0.isEmpty());
        ov0.setEarly(D.plusDays(1), 90);
        ok("an early start is a change", !ov0.isEmpty() && ov0.earlyFor(D.plusDays(1)) == 90 && ov0.earlyFor(D) == 0);
        Overrides cp = Overrides.parse(ov0.serialize());
        ok("it survives saving", cp.earlyFor(D.plusDays(1)) == 90 && !cp.isEmpty());
        ok("a copy keeps it", ov0.copy().earlyFor(D.plusDays(1)) == 90);
        ov0.setEarly(D.plusDays(1), 0);
        ok("0 takes it back", ov0.earlyFor(D.plusDays(1)) == 0 && ov0.isEmpty());
        ov0.setEarly(D, 5000);
        ok("never more than twelve hours", ov0.earlyFor(D) == 720);
        Overrides old = Overrides.parse("DINAOV1\nsig=abc\nS\t2026-10-22\t1\t30\nE\t2026-10-21\t5\t1\tReading\t\n");
        ok("saves from before still read", old.shiftFor(LocalDate.of(2026, 10, 22)) != null && old.edits.size() == 1 && old.wakes.isEmpty());
        Overrides pr = new Overrides();
        pr.setEarly(LocalDate.of(2026, 10, 1), 30);
        pr.setEarly(D, 30);
        pr.prune(D);
        ok("old early starts are forgotten", pr.earlyFor(LocalDate.of(2026, 10, 1)) == 0 && pr.earlyFor(D) == 30);
        Overrides dmg = Overrides.parse("DINAOV1\nW\tnot-a-date\t5\nW\t2026-10-22\tx\nW\t2026-10-23\t45\n");
        ok("damaged lines are skipped", dmg.wakes.size() == 1 && dmg.earlyFor(LocalDate.of(2026, 10, 23)) == 45);

        // ---- the plan of a day that starts early
        Overrides ovE = new Overrides();
        ovE.sig = Plan.signature(r);
        ovE.setEarly(D.plusDays(1), 90);
        Plan.DayPlan tomorrow = Plan.plan(r, ovE, D.plusDays(1), Z);
        Plan.DayPlan tomorrow0 = Plan.plan(r, new Overrides(), D.plusDays(1), Z);
        ok("the day starts 90 minutes early", hm(tomorrow, 0, false).equals("04:00") && tomorrow.startMs == tomorrow0.startMs - 90 * 60000L);
        ok("its first slot keeps its end and gets the time", hm(tomorrow, 0, true).equals("06:00") && tomorrow.sc.slots.get(0).block.mins == 120);
        ok("every other slot stays where it was", hm(tomorrow, 1, false).equals("06:00") && hm(tomorrow, 3, false).equals("07:15")
            && hm(tomorrow, tomorrow.sc.slots.size() - 1, false).equals(hm(tomorrow0, tomorrow0.sc.slots.size() - 1, false)));
        ok("the day is as long as before, plus the early part", total(tomorrow) == total(tomorrow0) + 90);
        Plan.DayPlan tonight = Plan.plan(r, ovE, D, Z);
        Plan.DayPlan tonight0 = Plan.plan(r, new Overrides(), D, Z);
        int sl = tonight.lastSleepIndex();
        ok("the night before ends when the day starts", tonight.endMs == tomorrow.startMs && hm(tonight, sl, true).equals("04:00"));
        ok("the night is shorter by that much", tonight.sc.slots.get(sl).block.mins == tonight0.sc.slots.get(sl).block.mins - 90);
        ok("the sleep that was planned stays what it was", tonight.sc.slots.get(sl).block.mins - tonight.sc.slots.get(sl).block.extended == tonight0.sc.slots.get(sl).block.mins);
        ok("the day before the night is not touched", hm(tonight, 0, false).equals("05:30") && hm(tonight, sl, false).equals("21:30"));
        Plan.DayPlan at430 = Plan.resolve(r, ovE, at(22, 4, 30, 0), Z);
        ok("at 04:30 it is already the new day, in its first slot", at430.day.equals(D.plusDays(1)) && at430.cur == 0);
        Plan.DayPlan at330 = Plan.resolve(r, ovE, at(22, 3, 30, 0), Z);
        ok("before the early start it is still the night", at330.day.equals(D) && at330.cur == at330.lastSleepIndex());
        AlarmState st0 = new AlarmState();
        AlarmPlanner.Event nextAlarm = AlarmPlanner.next(r, ovE, st0, at(22, 4, 30, 0), Z);
        ok("the wake-up alarm of the planned time does not ring after waking early; the next slot's does", nextAlarm != null && nextAlarm.timeMs == at(22, 6, 0, 0) && nextAlarm.src == 1);
        ok("without waking early the alarm is at 05:30", AlarmPlanner.next(r, new Overrides(), st0, at(22, 3, 0, 0), Z).timeMs == at(22, 5, 30, 0));
        // a later start already chosen: waking early starts that day sooner than its shifted start
        Overrides ovLate = new Overrides();
        ovLate.sig = Plan.signature(r);
        ovLate.setShift(D.plusDays(1), Overrides.WHOLE, 60);
        Plan.DayPlan lateDay = Plan.plan(r, ovLate, D.plusDays(1), Z);
        ok("a later start moves the morning an hour", hm(lateDay, 0, false).equals("06:30"));
        ovLate.setEarly(D.plusDays(1), 150);
        Plan.DayPlan lateEarly = Plan.plan(r, ovLate, D.plusDays(1), Z);
        ok("waking early after choosing a later start begins the day at the time waked", hm(lateEarly, 0, false).equals("04:00") && lateEarly.sc.slots.get(1).start == lateDay.sc.slots.get(1).start);
        Plan.DayPlan lateNight = Plan.plan(r, ovLate, D, Z);
        ok("the night ends when the day begins", lateNight.endMs == lateEarly.startMs && lateNight.slotEndMs(lateNight.lastSleepIndex()) == lateEarly.startMs);
        // the bedtime question works the same with or without it
        List<Plan.WakeOption> wo = Plan.wakeOptions(r, ovE, D, at(23, 0), Z, true);
        ok("the bedtime choices are worked out from the normal wake-up", wo.get(0).wakeMs == at(22, 5, 30, 0));
        List<Plan.WakeOption> early = Plan.wakeOptions(r, new Overrides(), D, at(21, 10), Z, true);
        ok("going to sleep early gives the one normal choice and more sleep than planned", early.size() == 1 && early.get(0).wakeMs == at(22, 5, 30, 0)
            && early.get(0).sleepMin == 500 && early.get(0).plannedMin == 480);

        // ---- Early finish is offered in every slot
        Plan.DayPlan p830 = Plan.resolve(r, new Overrides(), at(8, 30), Z);
        EarlyFinish.Offer o = EarlyFinish.of(p830, at(8, 30));
        ok("an ordinary slot: Finish early", o != null && o.kind == EarlyFinish.FINISH && !o.curIsSleep && o.label().equals("Finish early") && o.srcNext == 4 && o.canWait());
        Plan.DayPlan p2110 = Plan.resolve(r, new Overrides(), at(21, 10), Z);
        EarlyFinish.Offer es = EarlyFinish.of(p2110, at(21, 10));
        ok("the slot before sleep: Early sleep", es != null && es.kind == EarlyFinish.SLEEP && es.label().equals("Early sleep") && es.srcNext == 18
            && es.plannedEndMs == at(21, 30) && es.roomMin == 20 && !es.canWait());
        ok("a minute before sleep it can still be done, a few seconds before it cannot", EarlyFinish.of(Plan.resolve(r, new Overrides(), at(21, 28), Z), at(21, 28)) != null
            && EarlyFinish.of(Plan.resolve(r, new Overrides(), at(21, 21, 29, 30), Z), at(21, 21, 29, 30)) == null);
        Plan.DayPlan night = Plan.resolve(r, new Overrides(), at(22, 2, 0, 0), Z);
        Plan.DayPlan nextDay = Plan.plan(r, new Overrides(), D.plusDays(1), Z);
        ok("sleep is the slot going on at 02:00", night.cur == night.lastSleepIndex());
        EarlyFinish.Offer ew = EarlyFinish.of(night, nextDay, at(22, 2, 0, 0));
        ok("sleep: Early wake, the next day starts now", ew != null && ew.kind == EarlyFinish.WAKE && ew.curIsSleep && ew.label().equals("Early wake")
            && ew.srcNext == 0 && ew.titleNext.equals("Wake up & freshen up") && ew.plannedEndMs == at(22, 5, 30, 0) && ew.nextEndMs == at(22, 6, 0, 0)
            && ew.roomMin == 210 && !ew.canWait());
        ok("sleep without knowing the next day offers nothing", EarlyFinish.of(night, at(22, 2, 0, 0)) == null);
        ok("no early wake with a few minutes to the alarm", EarlyFinish.of(Plan.resolve(r, new Overrides(), at(22, 5, 28, 0), Z), nextDay, at(22, 5, 28, 0)) == null);
        ok("early wake at 21:45 (sleep has begun)", EarlyFinish.of(Plan.resolve(r, new Overrides(), at(21, 45), Z), nextDay, at(21, 45)) != null);
        ok("minutes earlier are rounded up, so the new day has begun", EarlyFinish.minutesEarlier(at(22, 5, 30, 0), at(22, 4, 0, 37)) == 90
            && EarlyFinish.minutesEarlier(at(22, 5, 30, 0), at(22, 4, 0, 0)) == 90 && EarlyFinish.minutesEarlier(at(22, 5, 30, 0), at(22, 6, 0, 0)) == 0);
        ok("the minute count is the same one the plan uses", Plan.resolve(r, withEarly(r, EarlyFinish.minutesEarlier(at(22, 5, 30, 0), at(22, 4, 0, 37))), at(22, 4, 0, 37), Z).day.equals(D.plusDays(1)));
        ok("a made-up early wake count is clamped", EarlyFinish.minutesEarlier(at(22, 5, 30, 0), at(21, 12, 0, 0)) == Plan.MAX_SHIFT);

        // ---- a slot taken off after the next: Finish early passes over it
        Routine r2 = routine();
        Overrides skipTea = new Overrides();
        Plan.edit(r2, skipTea, D, 6, true, "");
        Plan.DayPlan pRead = Plan.resolve(r2, skipTea, at(10, 30), Z);
        EarlyFinish.Offer over = EarlyFinish.of(pRead, at(10, 30));
        ok("the next slot is taken off: the one after it is the one that begins", over != null && over.srcCur == 5 && over.srcNext == 7 && over.plannedEndMs == at(11, 0)
            && over.nextEndMs == at(12, 40));
        List<Ev> evOver = Arrays.asList(Ev.early(at(10, 30), DAY, 7, 5, "Reading & notes", at(10, 30)));
        Plan.DayPlan liveOver = LivePlan.apply(pRead, evOver, new AlarmState(), false, at(10, 31));
        int ri = indexOfSrc(liveOver, 7, Routine.LIVE_EARLY);
        ok("the slot that follows the taken-off one begins now", ri >= 0 && hm(liveOver, ri, false).equals("10:30") && hm(liveOver, ri, true).equals("12:40") && liveOver.cur == ri);
        ok("the day keeps its length", total(liveOver) == total(pRead));
        int readIdx = indexOfSrc(liveOver, 5, Routine.LIVE_CUT);
        ok("the slot finished ends now and the taken-off one has no time", readIdx >= 0 && hm(liveOver, readIdx, true).equals("10:30") && indexOfSrc(liveOver, 6, Routine.LIVE_NONE) < 0);

        // ---- Early sleep in the day as it goes
        List<Ev> evSleep = Arrays.asList(Ev.early(at(21, 10), DAY, 18, 17, "Wind down", at(21, 10)));
        Plan.DayPlan liveSleep = LivePlan.apply(p2110, evSleep, new AlarmState(), false, at(21, 12));
        int sIdx = liveSleep.lastSleepIndex();
        ok("sleep begins when the person went to sleep", hm(liveSleep, sIdx, false).equals("21:10") && hm(liveSleep, sIdx, true).equals("05:30") && liveSleep.cur == sIdx);
        ok("the slot before sleep ends then", hm(liveSleep, sIdx - 1, true).equals("21:10") && liveSleep.sc.slots.get(sIdx - 1).block.live == Routine.LIVE_CUT);
        ok("the day keeps its length", total(liveSleep) == total(p2110));
        ok("an alarm for a slot that began early is not wanted", AlarmPlanner.begunEarly(evSleep, DAY, 18, at(22, 30)));
        ok("but one for another slot, another day, or a slot that began on time is", !AlarmPlanner.begunEarly(evSleep, DAY, 17, at(22, 30))
            && !AlarmPlanner.begunEarly(evSleep, "2000-01-01", 18, at(22, 30)) && !AlarmPlanner.begunEarly(evSleep, DAY, 18, at(21, 10)));
        ok("a slot that follows a skipped one also began early", AlarmPlanner.begunEarly(Arrays.asList(Ev.skip(at(21, 0), DAY, 17, "Wind down", 18, at(21, 0))), DAY, 18, at(22, 30)));
        ok("sleep can be finished early (early wake) in the same day", EarlyFinish.of(liveSleep, nextDay, at(21, 12)) != null
            && EarlyFinish.of(liveSleep, nextDay, at(21, 12)).kind == EarlyFinish.WAKE);

        // ---- Skip at the start of a slot
        Routine r3 = routine();
        Overrides ovS = new Overrides();
        List<Ev> evSkip = skipped(r3, ovS, 3, at(7, 17));
        Plan.DayPlan saved = Plan.plan(r3, ovS, D, Z);
        Plan.DayPlan lv = LivePlan.apply(Plan.resolve(r3, ovS, at(7, 20), Z), evSkip, new AlarmState(), false, at(7, 20));
        int nx = indexOfSrc(lv, 4, Routine.LIVE_EARLY);
        ok("skipping a slot starts the next one at once", nx >= 0 && hm(lv, nx, false).equals("07:17") && hm(lv, nx, true).equals("09:30") && lv.cur == nx);
        int sk = -1;
        for (int i = 0; i < lv.sc.slots.size(); i++) if (lv.sc.slots.get(i).block.src == 3) sk = i;
        ok("the skipped slot is kept, taken off, up to that moment", sk >= 0 && lv.sc.slots.get(sk).block.skipped && hm(lv, sk, false).equals("07:15") && hm(lv, sk, true).equals("07:17"));
        ok("the rest of the day stays on the clock", hm(lv, nx + 1, false).equals("09:30") && total(lv) == total(saved));
        ok("the slot before is untouched", lv.sc.slots.get(2).block.title.equals("Breakfast") && hm(lv, 2, true).equals("07:15"));
        Plan.DayPlan quick = LivePlan.apply(Plan.resolve(r3, ovS, at(21, 7, 15, 20), Z), skipped(r3, new Overrides(), 3, at(21, 7, 15, 20)), new AlarmState(), false, at(21, 7, 16, 0));
        ok("skipped within the first minute: the skipped slot has no time left to show", indexOfSrc(quick, 3, Routine.LIVE_NONE) < 0 && indexOfSrc(quick, 4, Routine.LIVE_EARLY) >= 0);
        // the event alone does nothing when the slot is not taken off (the day was changed back)
        Plan.DayPlan plain = Plan.resolve(r3, new Overrides(), at(7, 20), Z);
        ok("the skip is not applied when the slot is no longer taken off", LivePlan.apply(plain, evSkip, new AlarmState(), false, at(7, 20)) == plain);
        // a slot taken off after the one that began by a skip: the skip no longer holds, and the day stays whole
        Routine r4 = routine();
        Overrides ov4 = new Overrides();
        List<Ev> ev4 = new ArrayList<>(skipped(r4, ov4, 3, at(7, 17)));
        Plan.edit(r4, ov4, D, 4, true, "");
        Plan.DayPlan lv4 = LivePlan.apply(Plan.resolve(r4, ov4, at(9, 35), Z), ev4, new AlarmState(), false, at(9, 35));
        ok("when the slot that began by a skip is taken off too, the day stays whole", total(lv4) == total(saved) && lv4.cur >= 0 && lv4.sc.slots.get(lv4.cur).block.src == 5
            && lv4.sc.slots.get(lv4.cur).block.skipped == false);
        // skipping when the next slot is already taken off: the one after it begins
        Routine r5 = routine();
        Overrides ov5 = new Overrides();
        Plan.edit(r5, ov5, D, 4, true, "");
        List<Ev> ev5 = skipped(r5, ov5, 3, at(7, 17));
        Plan.DayPlan lv5 = LivePlan.apply(Plan.resolve(r5, ov5, at(7, 20), Z), ev5, new AlarmState(), false, at(7, 20));
        int n5 = indexOfSrc(lv5, 5, Routine.LIVE_EARLY);
        ok("the next real slot begins when the one after the skipped is off too", n5 >= 0 && hm(lv5, n5, false).equals("07:17") && lv5.cur == n5 && total(lv5) == total(saved));
        // skipping the slot before sleep: sleep begins now
        Routine r6 = routine();
        Overrides ov6 = new Overrides();
        List<Ev> ev6 = skipped(r6, ov6, 17, at(21, 1));
        Plan.DayPlan lv6 = LivePlan.apply(Plan.resolve(r6, ov6, at(21, 5), Z), ev6, new AlarmState(), false, at(21, 5));
        ok("skipping the slot before sleep begins sleep", lv6.cur == lv6.lastSleepIndex() && hm(lv6, lv6.lastSleepIndex(), false).equals("21:01") && total(lv6) == total(saved));
        // after a skip the new slot can be finished early like any other
        EarlyFinish.Offer after = EarlyFinish.of(lv, at(7, 20));
        ok("the slot that began by a skip can be finished early", after != null && after.srcCur == 4 && after.srcNext == 5 && after.label().equals("Finish early"));
        // skipping the first slot of the day
        Routine r7 = routine();
        Overrides ov7 = new Overrides();
        List<Ev> ev7 = skipped(r7, ov7, 0, at(5, 31));
        Plan.DayPlan lv7 = LivePlan.apply(Plan.resolve(r7, ov7, at(5, 40), Z), ev7, new AlarmState(), false, at(5, 40));
        ok("skipping the first slot of the day starts the second", indexOfSrc(lv7, 1, Routine.LIVE_EARLY) >= 0 && lv7.cur == indexOfSrc(lv7, 1, Routine.LIVE_EARLY)
            && hm(lv7, lv7.cur, false).equals("05:31"));

        // ---- the alarms around a skip
        AlarmState stSkip = new AlarmState();
        stSkip.bindSkips(Plan.scheduleSignature(r3));
        stSkip.skipAlarm(DAY, 4);
        AlarmPlanner.Event ne = AlarmPlanner.next(r3, ovS, stSkip, at(7, 17), Z);
        ok("the next slot's own alarm at the planned time stays quiet, the one after rings", ne != null && ne.src == 5 && ne.timeMs == at(9, 30));
        AlarmPlanner.Event noSkip = AlarmPlanner.next(r3, ovS, new AlarmState(), at(7, 17), Z);
        ok("without the mark it would ring at 09:15", noSkip != null && noSkip.src == 4 && noSkip.timeMs == at(9, 15));
        ok("the skipped slot rings no more", AlarmPlanner.next(r3, ovS, new AlarmState(), at(7, 10), Z).src != 3);

        // ---- the report
        DayReport rep = ReportAssembler.assemble(saved, evSkip, null, true, at(12, 0));
        ok("the skipped slot shows as skipped", rep.rows.get(3).status == DayReport.SKIPPED);
        ok("the slot that began by the skip counts from then", rep.rows.get(4).activeMs == (9 * 60 + 30 - (7 * 60 + 17)) * 60000L && rep.rows.get(4).status == DayReport.KEPT);
        ok("the slots around are as before", rep.rows.get(5).activeMs == 90 * 60000L);
        SlotGaps.Result g = SlotGaps.of(evSkip, 4, saved.slotStartMs(4), saved.slotEndMs(4), at(12, 0));
        ok("the gaps say the next slot began early", g.effStart == at(7, 17) && g.preMs == (9 * 60 + 15 - (7 * 60 + 17)) * 60000L);

        // ---- early wake in the report: the sleep ends when the day began
        Overrides ovW = withEarly(r, 90);
        Plan.DayPlan nightRep = Plan.plan(r, ovW, D, Z);
        List<Ev> sleepEv = Arrays.asList(Ev.sleep(at(21, 40), DAY, at(21, 30), 0, 0), Ev.woke(at(22, 4, 0, 5), "2026-10-22", 0, "Wake up & freshen up", at(22, 5, 30, 0), 90));
        DayReport rn = ReportAssembler.assemble(nightRep, sleepEv, null, true, at(22, 8, 0, 0));
        ok("the night report keeps the planned sleep and shows the real bedtime", rn.plannedSleepMin == 480 && rn.actualBedMs == at(21, 40));
        ok("the woke record is plain history", Ev.parse(sleepEv.get(1).line()) != null && Ev.parse(sleepEv.get(1).line()).type.equals(Ev.WOKE) && Ev.parse(sleepEv.get(1).line()).b.equals("90"));
        Ev sk1 = Ev.parse(evSkip.get(1).line());
        ok("the skip record survives saving", sk1 != null && sk1.type.equals(Ev.SKIP) && sk1.src == 3 && sk1.b.equals("4") && sk1.aLong(0) == at(7, 17));

        // ---- every slot, one after the other: finishing early keeps the day whole
        int bad = 0;
        Plan.DayPlan whole = Plan.plan(r, new Overrides(), D, Z);
        int count = whole.sc.slots.size();
        for (int i = 0; i < count - 1; i++) {
            long now = whole.slotStartMs(i) + 4 * 60000L;
            if (whole.slotEndMs(i) - now < 6 * 60000L) continue;
            Plan.DayPlan base = Plan.resolve(r, new Overrides(), now, Z);
            EarlyFinish.Offer of = EarlyFinish.of(base, nextDay, now);
            if (of == null) { bad++; System.out.println("  no offer in slot " + i); continue; }
            List<Ev> e = Arrays.asList(Ev.early(now, DAY, of.srcNext, of.srcCur, of.titleCur, now));
            Plan.DayPlan lv9 = LivePlan.apply(base, e, new AlarmState(), false, now + 60000L);
            boolean sane = lv9 != base && total(lv9) == total(base) && lv9.cur >= 0 && lv9.sc.slots.get(lv9.cur).block.src == of.srcNext;
            if (!sane) { bad++; System.out.println("  slot " + i + " not sane"); }
            if (i == count - 2 && of.kind != EarlyFinish.SLEEP) { bad++; System.out.println("  last before sleep is not Early sleep"); }
            if (i < count - 2 && of.kind != EarlyFinish.FINISH) { bad++; System.out.println("  slot " + i + " not Finish early"); }
        }
        ok("every slot can be finished early and the next begins at once, the day keeps its length", bad == 0);

        // ---- every minute of a day: nothing throws, an offer always has room
        int weird = 0;
        for (long t = at(5, 0); t < at(22, 5, 30, 0); t += 7 * 60000L) {
            Plan.DayPlan b = Plan.resolve(r, new Overrides(), t, Z);
            Plan.DayPlan nd = Plan.plan(r, new Overrides(), b.day.plusDays(1), Z);
            EarlyFinish.Offer x = EarlyFinish.of(b, nd, t);
            if (x != null && (x.roomMin < EarlyFinish.MIN_ROOM || x.plannedEndMs <= t)) weird++;
        }
        ok("an offer always has room", weird == 0);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static Overrides withEarly(Routine r, int mins) {
        Overrides ov = new Overrides();
        ov.sig = Plan.signature(r);
        ov.setEarly(D.plusDays(1), mins);
        return ov;
    }
}
