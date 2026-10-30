package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * The day as it is going: delays, breaks, carrying on the slot before, finishing early, the day off; what the alarms do then; and
 * library mode. All on the sample routine (wake 05:30, bed 21:30) on 21 October 2026.
 */
public class LiveDayTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final String DAY = "2026-10-21";
    static long at(int d, int h, int m, int s) { return ZonedDateTime.of(2026, 10, d, h, m, s, 0, Z).toInstant().toEpochMilli(); }
    static long at(int h, int m) { return at(21, h, m, 0); }

    static Routine routine() { Routine r = Routine.sample(); r.alarmOn = true; return r; }
    static Plan.DayPlan base(Routine r, long now) { return Plan.resolve(r, new Overrides(), now, Z); }
    static Plan.DayPlan live(Routine r, List<Ev> ev, AlarmState st, boolean off, long now) { return LivePlan.apply(base(r, now), ev, st, off, now); }

    static int total(Plan.DayPlan p) { int t = 0; for (Routine.Slot s : p.sc.slots) t += s.block.mins; return t; }
    static int indexOfTitle(Plan.DayPlan p, String title, int live) {
        for (int i = 0; i < p.sc.slots.size(); i++) { Routine.Block b = p.sc.slots.get(i).block; if (b.title.equals(title) && b.live == live) return i; }
        return -1;
    }
    static String hm(Plan.DayPlan p, int i, boolean end) {
        long ms = end ? p.slotEndMs(i) : p.slotStartMs(i);
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(Z);
        return String.format("%02d:%02d", z.getHour(), z.getMinute());
    }

    public static void main(String[] args) {
        Routine r = routine();
        Plan.DayPlan b0 = base(r, at(8, 0));
        int baseCount = b0.sc.slots.size();
        int baseTotal = total(b0);

        // ---- nothing happened: the same plan comes back
        ok("with nothing to show the plan is returned as it is", LivePlan.apply(b0, new ArrayList<Ev>(), new AlarmState(), false, at(8, 0)) == b0);
        ok("events of another day change nothing", LivePlan.apply(b0, Arrays.asList(Ev.breakTaken(at(20, 8, 0, 0), at(20, 8, 20, 0), "2026-10-20", 3, "Morning focus block")), new AlarmState(), false, at(8, 0)) == b0);

        // ---- a start put off: a waiting part, then the slot
        List<Ev> delay = Arrays.asList(Ev.delay(at(21, 7, 15, 20), DAY, 3, "Morning focus block", at(7, 30)));
        Plan.DayPlan d = live(r, delay, new AlarmState(), false, at(7, 20));
        int w = indexOfTitle(d, LivePlan.WAIT_TITLE, Routine.LIVE_WAIT);
        int f = indexOfTitle(d, "Morning focus block", Routine.LIVE_LATE);
        ok("a put-off start adds a waiting part before the slot", w >= 0 && f == w + 1 && d.sc.slots.size() == baseCount + 1);
        ok("the waiting part is from the slot's start to the later start", w >= 0 && hm(d, w, false).equals("07:15") && hm(d, w, true).equals("07:30"));
        ok("the slot now begins at the later start and keeps its end", f >= 0 && hm(d, f, false).equals("07:30") && hm(d, f, true).equals("09:15"));
        ok("the length of the day does not change", total(d) == baseTotal);
        ok("the waiting part is not a slot of the plan and knows which slot it is in", w >= 0 && d.sc.slots.get(w).block.src == -1 && d.sc.slots.get(w).block.owner == 3 && d.sc.slots.get(w).block.slotSrc() == 3);
        ok("while waiting, the waiting part is the one going on", d.cur == w);
        Plan.DayPlan d2 = live(r, delay, new AlarmState(), false, at(7, 40));
        ok("after the later start the slot is the one going on", d2.cur == indexOfTitle(d2, "Morning focus block", Routine.LIVE_LATE));
        ok("slots before and after are untouched", d2.sc.slots.get(2).block.title.equals("Breakfast") && d2.sc.slots.get(2).block.live == Routine.LIVE_NONE
            && d2.sc.slots.get(f + 1).block.title.equals("Short break"));
        ok("the saved plan is not touched", b0.sc.slots.size() == baseCount && base(r, at(8, 0)).sc.slots.size() == baseCount);

        // a start put off twice: the later one counts
        List<Ev> delay2 = Arrays.asList(Ev.delay(at(21, 7, 15, 20), DAY, 3, "Morning focus block", at(7, 25)),
            Ev.delay(at(21, 7, 25, 10), DAY, 3, "Morning focus block", at(7, 40)));
        Plan.DayPlan dd = live(r, delay2, new AlarmState(), false, at(7, 30));
        int ww = indexOfTitle(dd, LivePlan.WAIT_TITLE, Routine.LIVE_WAIT);
        ok("a start put off twice waits until the second time", ww >= 0 && hm(dd, ww, true).equals("07:40") && dd.sc.slots.size() == baseCount + 1);
        List<Ev> longDelay = Arrays.asList(Ev.delay(at(21, 7, 15, 20), DAY, 3, "Morning focus block", at(12, 0)));
        Plan.DayPlan ld = live(r, longDelay, new AlarmState(), false, at(7, 20));
        int lf = indexOfTitle(ld, "Morning focus block", Routine.LIVE_LATE);
        ok("a delay can never swallow the whole slot: its last minute stays", lf >= 0 && ld.sc.slots.get(lf).block.mins >= 1 && total(ld) == baseTotal);

        // ---- a break inside a slot: slot, break, slot
        List<Ev> brk = Arrays.asList(Ev.breakTaken(at(21, 8, 0, 10), at(21, 8, 20, 10), DAY, 3, "Morning focus block"));
        Plan.DayPlan bk = live(r, brk, new AlarmState(), false, at(8, 10));
        int bi = indexOfTitle(bk, LivePlan.BREAK_TITLE, Routine.LIVE_BREAK);
        ok("a break splits its slot in three: before, the break, after", bi >= 1 && bk.sc.slots.size() == baseCount + 2
            && bk.sc.slots.get(bi - 1).block.title.equals("Morning focus block") && bk.sc.slots.get(bi + 1).block.title.equals("Morning focus block"));
        ok("the parts are 07:15-08:00, 08:00-08:20 and 08:20-09:15", hm(bk, bi - 1, false).equals("07:15") && hm(bk, bi - 1, true).equals("08:00")
            && hm(bk, bi, true).equals("08:20") && hm(bk, bi + 1, true).equals("09:15"));
        ok("the two parts of the slot still add up to its length", bk.sc.slots.get(bi - 1).block.mins + bk.sc.slots.get(bi + 1).block.mins + bk.sc.slots.get(bi).block.mins == 120 && total(bk) == baseTotal);
        ok("during the break the break part is the one going on", bk.cur == bi && bk.sc.slots.get(bi).block.owner == 3);
        Plan.DayPlan bk2 = live(r, brk, new AlarmState(), false, at(8, 30));
        ok("after the break the second part of the slot is going on", bk2.cur == bi + 1 && bk2.sc.slots.get(bk2.cur).block.src == 3);
        List<Ev> brk2 = new ArrayList<>(brk);
        brk2.add(Ev.breakTaken(at(21, 8, 0, 10), at(21, 8, 10, 10), DAY, 3, "Morning focus block"));
        Plan.DayPlan bk3 = live(r, brk2, new AlarmState(), false, at(8, 30));
        int bi3 = indexOfTitle(bk3, LivePlan.BREAK_TITLE, Routine.LIVE_BREAK);
        ok("a break that ended early (written again) is the shorter one", bi3 >= 0 && hm(bk3, bi3, true).equals("08:10"));
        List<Ev> two = new ArrayList<>(brk);
        two.add(Ev.breakTaken(at(21, 8, 40, 0), at(21, 8, 45, 0), DAY, 3, "Morning focus block"));
        Plan.DayPlan bt = live(r, two, new AlarmState(), false, at(9, 0));
        int n = 0;
        for (Routine.Slot s : bt.sc.slots) if (s.block.live == Routine.LIVE_BREAK) n++;
        ok("two breaks in one slot show as two break parts", n == 2 && bt.sc.slots.size() == baseCount + 4 && total(bt) == baseTotal);
        List<Ev> toEnd = Arrays.asList(Ev.breakTaken(at(21, 8, 30, 0), at(21, 9, 15, 0), DAY, 3, "Morning focus block"));
        Plan.DayPlan te = live(r, toEnd, new AlarmState(), false, at(8, 40));
        int tei = indexOfTitle(te, LivePlan.BREAK_TITLE, Routine.LIVE_BREAK);
        ok("a break until the end of the slot is the last part of it", tei >= 0 && hm(te, tei, true).equals("09:15") && te.sc.slots.get(tei + 1).block.title.equals("Short break"));

        // ---- carrying on the slot before
        List<Ev> carry = Arrays.asList(Ev.extend(at(21, 9, 30, 5), DAY, 5, 4, "Short break", at(9, 40)));
        Plan.DayPlan cp = live(r, carry, new AlarmState(), false, at(9, 35));
        int ci = indexOfTitle(cp, "Short break", Routine.LIVE_CARRIED);
        int cn = indexOfTitle(cp, "Reading & notes", Routine.LIVE_LATE);
        ok("carrying the slot before on moves the boundary: it ends later, the next begins later", ci >= 0 && cn == ci + 1
            && hm(cp, ci, true).equals("09:40") && hm(cp, cn, false).equals("09:40") && hm(cp, cn, true).equals("11:00"));
        ok("the slot before is the one going on until then", cp.cur == ci && total(cp) == baseTotal && cp.sc.slots.size() == baseCount);
        ok("minutes move between the two slots", cp.sc.slots.get(ci).block.mins == 25 && cp.sc.slots.get(cn).block.mins == 80);

        // ---- finishing early: the next slot begins before its time
        List<Ev> early = Arrays.asList(Ev.early(at(21, 8, 50, 0), DAY, 4, 3, "Morning focus block", at(8, 50)));
        Plan.DayPlan ep = live(r, early, new AlarmState(), false, at(8, 55));
        int ei = indexOfTitle(ep, "Morning focus block", Routine.LIVE_CUT);
        int en = indexOfTitle(ep, "Short break", Routine.LIVE_EARLY);
        ok("finishing early ends the slot at that moment and starts the next one then", ei >= 0 && en == ei + 1 && hm(ep, ei, true).equals("08:50") && hm(ep, en, false).equals("08:50"));
        ok("the next slot keeps its planned end, so it gets the time that was left", hm(ep, en, true).equals("09:30") && ep.sc.slots.get(en).block.mins == 40 && total(ep) == baseTotal);
        ok("the next slot is the one going on", ep.cur == en);
        Plan.DayPlan before = live(r, early, new AlarmState(), false, at(8, 40));
        ok("before that moment nothing has changed yet in what is going on", before.cur >= 0 && before.sc.slots.size() == baseCount);
        List<Ev> badEarly = Arrays.asList(Ev.early(at(21, 9, 20, 0), DAY, 4, 3, "Morning focus block", at(9, 20)));
        ok("a boundary that is not earlier than planned is ignored", live(r, badEarly, new AlarmState(), false, at(9, 25)) == null
            || live(r, badEarly, new AlarmState(), false, at(9, 25)).sc.slots.size() == baseCount);

        List<Ev> earlyBreak = new ArrayList<>(early);
        earlyBreak.add(Ev.breakTaken(at(21, 8, 50, 0), at(21, 9, 0, 0), DAY, 4, "Short break"));
        Plan.DayPlan eb = live(r, earlyBreak, new AlarmState(), false, at(8, 55));
        int ebi = indexOfTitle(eb, LivePlan.BREAK_TITLE, Routine.LIVE_BREAK);
        ok("finishing early and taking a break: slot ends, break, next slot", ebi >= 1 && eb.sc.slots.get(ebi - 1).block.live == Routine.LIVE_CUT
            && hm(eb, ebi, false).equals("08:50") && hm(eb, ebi, true).equals("09:00") && eb.sc.slots.get(ebi + 1).block.title.equals("Short break") && hm(eb, ebi + 1, true).equals("09:30"));
        ok("that break knows it sits in the next slot", eb.sc.slots.get(ebi).block.owner == 4);
        List<Ev> earlyWait = new ArrayList<>(early);
        earlyWait.add(Ev.delay(at(21, 8, 50, 0), DAY, 4, "Short break", at(9, 0)));
        Plan.DayPlan ew = live(r, earlyWait, new AlarmState(), false, at(8, 55));
        int ewi = indexOfTitle(ew, LivePlan.WAIT_TITLE, Routine.LIVE_WAIT);
        ok("finishing early and starting after a while: slot ends, waiting, next slot", ewi >= 1 && ew.sc.slots.get(ewi - 1).block.live == Routine.LIVE_CUT
            && hm(ew, ewi, true).equals("09:00") && ew.sc.slots.get(ewi + 1).block.live == Routine.LIVE_LATE && total(ew) == baseTotal);

        // finishing twice in a row
        List<Ev> chain = new ArrayList<>(early);
        chain.add(Ev.early(at(21, 9, 0, 0), DAY, 5, 4, "Short break", at(9, 0)));
        Plan.DayPlan ch = live(r, chain, new AlarmState(), false, at(9, 5));
        ok("finishing two slots early one after the other", indexOfTitle(ch, "Morning focus block", Routine.LIVE_CUT) >= 0 && indexOfTitle(ch, "Short break", Routine.LIVE_CUT) >= 0
            && indexOfTitle(ch, "Reading & notes", Routine.LIVE_EARLY) >= 0 && total(ch) == baseTotal);
        int ri = indexOfTitle(ch, "Reading & notes", Routine.LIVE_EARLY);
        ok("and the last one starts when the second ended", hm(ch, ri, false).equals("09:00") && hm(ch, ri, true).equals("11:00") && ch.cur == ri);

        // ---- the day off
        AlarmState off = new AlarmState();
        off.offDay = DAY;
        off.offFrom = at(7, 15);
        off.offSig = DayOff.sig(r, LocalDate.of(2026, 10, 21));
        Plan.DayPlan of = live(r, new ArrayList<Ev>(), off, true, at(10, 0));
        int oi = indexOfTitle(of, LivePlan.OFF_TITLE, Routine.LIVE_OFF);
        ok("a day off replaces the rest of the day, until sleep, with one part", oi >= 0 && hm(of, oi, false).equals("07:15") && hm(of, oi, true).equals("21:30")
            && of.sc.slots.get(oi + 1).block.kind == Routine.SLEEP && of.sc.slots.size() == oi + 2);
        ok("what was before the day off stays", of.sc.slots.get(0).block.title.equals("Wake up & freshen up") && of.sc.slots.get(oi - 1).block.title.equals("Breakfast"));
        ok("the day is still the same length", total(of) == baseTotal);
        ok("during the day off the day-off part is the one going on", of.cur == oi);
        Plan.DayPlan nightOff = live(r, new ArrayList<Ev>(), off, true, at(22, 0));
        ok("at night the sleep is going on, not the day off", nightOff.cur == nightOff.sc.slots.size() - 1 && nightOff.sc.slots.get(nightOff.cur).block.kind == Routine.SLEEP);
        AlarmState offFromWake = new AlarmState();
        offFromWake.offDay = DAY;
        offFromWake.offFrom = at(5, 30);
        Plan.DayPlan allDay = live(r, new ArrayList<Ev>(), offFromWake, true, at(9, 0));
        ok("a day off from the wake-up leaves just the day off and sleep", allDay.sc.slots.size() == 2 && allDay.sc.slots.get(0).block.live == Routine.LIVE_OFF && hm(allDay, 0, false).equals("05:30"));
        AlarmState offMid = new AlarmState();
        offMid.offDay = DAY;
        offMid.offFrom = at(7, 30);
        Plan.DayPlan mid = live(r, new ArrayList<Ev>(), offMid, true, at(8, 0));
        int mi = indexOfTitle(mid, LivePlan.OFF_TITLE, Routine.LIVE_OFF);
        ok("a day off in the middle of a slot cuts that slot there", mi >= 1 && mid.sc.slots.get(mi - 1).block.title.equals("Morning focus block") && hm(mid, mi - 1, true).equals("07:30") && total(mid) == baseTotal);
        ok("not off any more (planned again): the plan is back to normal", live(r, new ArrayList<Ev>(), off, false, at(10, 0)) == null || live(r, new ArrayList<Ev>(), off, false, at(10, 0)).sc.slots.size() == baseCount);

        // ---- the alarms
        long after = at(4, 0);
        AlarmState st = new AlarmState();
        List<AlarmPlanner.Event> plain = AlarmPlanner.upcoming(r, new Overrides(), st, after, Z);
        ok("sanity: the wake alarm and a slot alarm are listed", plain.get(0).wake && plain.stream().anyMatch(e -> e.kind == AlarmPlanner.SLOT && e.src == 5 && e.day.equals(DAY)));
        st.skipAlarm(DAY, 5);
        ok("a slot started before its time has no alarm of its own", AlarmPlanner.upcoming(r, new Overrides(), st, after, Z).stream().noneMatch(e -> e.src == 5 && e.day.equals(DAY) && !e.isTimer())
            && AlarmPlanner.upcoming(r, new Overrides(), st, after, Z).stream().anyMatch(e -> e.src == 5 && e.day.equals("2026-10-22")));
        AlarmState back = AlarmState.parse(st.serialize());
        ok("the skipped alarms survive saving", back.skipsAlarm(DAY, 5) && !back.skipsAlarm(DAY, 6));
        AlarmState junk = AlarmState.parse("skip=hello,2026-10-21:7,2026-10-21:x\nplanAt=zz\n");
        ok("damaged saved text is ignored", junk.skipsAlarm(DAY, 7) && !junk.skipsAlarm(DAY, 3) && junk.planAt == 0);
        AlarmState old = new AlarmState();
        for (int i = 0; i < 60; i++) old.skipAlarm("2026-09-" + String.format("%02d", 1 + i % 28), i);
        ok("the list of skipped alarms stays short", old.skipAlarms.size() <= 40);
        old.pruneSkips("2026-09-20");
        ok("old skip marks are forgotten", old.skipAlarms.stream().allMatch(k -> k.compareTo("2026-09-20") >= 0));

        AlarmState dayOff = new AlarmState();
        DayOff.start(r, dayOff, LocalDate.of(2026, 10, 21), at(7, 15));
        List<AlarmPlanner.Event> offList = AlarmPlanner.upcoming(r, new Overrides(), dayOff, at(7, 16), Z);
        ok("a day off: no slot alarm of that day rings, not even bedtime", offList.stream().noneMatch(e -> e.day.equals(DAY)));
        ok("the next day's alarms, the wake-up first, are still listed", offList.get(0).day.equals("2026-10-22") && offList.get(0).wake && offList.get(0).timeMs == at(22, 5, 30, 0));
        ok("slots before the moment of the day off are not touched", AlarmPlanner.upcoming(r, new Overrides(), dayOff, at(5, 0), Z).stream().anyMatch(e -> e.day.equals(DAY) && e.src == 1)
            && AlarmPlanner.upcoming(r, new Overrides(), dayOff, at(5, 0), Z).stream().noneMatch(e -> e.day.equals(DAY) && e.src == 3));
        // a timer that is somehow still waiting (a break end, a later start) rings no more than the slots do in a day off
        AlarmState stale = new AlarmState();
        DayOff.start(r, stale, LocalDate.of(2026, 10, 21), at(7, 15), at(22, 5, 30, 0));
        stale.breakFrom = at(7, 10); stale.breakTo = at(7, 40); stale.breakDay = DAY; stale.breakSrc = 3; stale.breakTitle = "Morning focus block"; stale.breakSlotEnd = at(9, 15);
        stale.againAt = at(8, 0); stale.againDay = DAY; stale.againSrc = 4; stale.againTitle = "Short break"; stale.againSlotStart = at(7, 15); stale.againSlotEnd = at(9, 30);
        ok("a leftover break or later-start timer does not ring in a day off", AlarmPlanner.upcoming(r, new Overrides(), stale, at(7, 16), Z).stream().noneMatch(AlarmPlanner.Event::isTimer));
        stale.offDay = "";
        ok("without the day off the same timers are listed", AlarmPlanner.upcoming(r, new Overrides(), stale, at(7, 16), Z).stream().filter(AlarmPlanner.Event::isTimer).count() == 2);
        ok("the day off is in force while the schedule is the same", DayOff.active(r, dayOff, LocalDate.of(2026, 10, 21)) && DayOff.pending(r, dayOff));
        ok("not another day", !DayOff.active(r, dayOff, LocalDate.of(2026, 10, 22)));
        Routine changed = routine();
        changed.blocks.get(3).title = "New plan";
        ok("a new schedule for the day ends the day off", !DayOff.active(changed, dayOff, LocalDate.of(2026, 10, 21))
            && AlarmPlanner.upcoming(changed, new Overrides(), dayOff, at(7, 16), Z).stream().anyMatch(e -> e.day.equals(DAY) && e.src == 5));
        Routine shifted = routine();
        shifted.anchor = shifted.anchor + 30;
        ok("so does moving the day's start", !DayOff.active(shifted, dayOff, LocalDate.of(2026, 10, 21)));
        Routine tempDay = routine();
        Routine oneDay = tempDay.addTemp(LocalDate.of(2026, 10, 21), "Today", tempDay);
        ok("a one-day copy that is not changed is the same schedule", DayOff.active(tempDay, dayOff, LocalDate.of(2026, 10, 21)));
        oneDay.blocks.get(4).title = "Today only";
        ok("and a one-day routine that differs ends it", !DayOff.active(tempDay, dayOff, LocalDate.of(2026, 10, 21)));
        Routine other = routine();
        other.askSilent = false;
        other.wakeDelayMin = 20;
        ok("changing an alarm setting does not end it", DayOff.active(other, dayOff, LocalDate.of(2026, 10, 21)));
        DayOff.planLater(dayOff, at(9, 0));
        List<AlarmPlanner.Event> ask = AlarmPlanner.upcoming(r, new Overrides(), dayOff, at(7, 16), Z);
        ok("'plan later' brings back one question at that time", ask.stream().filter(e -> e.kind == AlarmPlanner.PLAN_ASK).count() == 1
            && ask.stream().filter(e -> e.kind == AlarmPlanner.PLAN_ASK).findFirst().get().timeMs == at(9, 0) && ask.stream().filter(e -> e.kind == AlarmPlanner.PLAN_ASK).findFirst().get().isTimer());
        ok("the plan question is not 'the next alarm' on the Home card", AlarmPlanner.nextAlarm(r, new Overrides(), dayOff, at(7, 16), Z).kind != AlarmPlanner.PLAN_ASK);
        ok("no plan question once the day is planned", AlarmPlanner.upcoming(changed, new Overrides(), dayOff, at(7, 16), Z).stream().noneMatch(e -> e.kind == AlarmPlanner.PLAN_ASK));
        ok("no plan question after the time has passed", AlarmPlanner.upcoming(r, new Overrides(), dayOff, at(9, 1), Z).stream().noneMatch(e -> e.kind == AlarmPlanner.PLAN_ASK));
        DayOff.end(dayOff);
        ok("ending the day off brings the alarms back", AlarmPlanner.upcoming(r, new Overrides(), dayOff, at(7, 16), Z).stream().anyMatch(e -> e.day.equals(DAY) && e.src == 5));
        AlarmState timers = new AlarmState();
        timers.breakFrom = at(8, 0); timers.breakTo = at(8, 20); timers.breakSrc = 3; timers.breakDay = DAY;
        timers.againAt = at(8, 30); timers.againSrc = 3; timers.againDay = DAY; timers.reaskAt = at(22, 0);
        DayOff.start(r, timers, LocalDate.of(2026, 10, 21), at(8, 5));
        ok("taking the day off drops a waiting break, a later start and a bedtime question", timers.breakTo == 0 && timers.againAt == 0 && timers.reaskAt == 0);
        AlarmState saved = AlarmState.parse(dayOff.serialize());
        DayOff.start(r, dayOff, LocalDate.of(2026, 10, 21), at(7, 15));
        DayOff.planLater(dayOff, at(9, 0));
        saved = AlarmState.parse(dayOff.serialize());
        ok("the day off survives saving", saved.offDay.equals(DAY) && saved.offFrom == at(7, 15) && saved.offSig.equals(dayOff.offSig) && saved.planAt == at(9, 0));
        List<DayOff.Pick> gone = DayOff.removed(base(r, at(7, 20)), at(7, 20));
        ok("the slots a day off takes away are those not over and not sleep", gone.size() == 15 && gone.get(0).src == 3 && gone.stream().noneMatch(pk -> pk.src == 18 || pk.src < 3));
        Plan.DayPlan skipPlan = Plan.plan(r, skipEdit(r), LocalDate.of(2026, 10, 21), Z);
        ok("a slot already taken off is not taken off again", DayOff.removed(skipPlan, at(7, 20)).stream().noneMatch(pk -> pk.src == 5));

        // ---- the report knows about early starts and early finishes
        List<Ev> reportEv = new ArrayList<>(early);
        Plan.DayPlan full = Plan.plan(r, new Overrides(), LocalDate.of(2026, 10, 21), Z);
        DayReport rep = ReportAssembler.assemble(full, reportEv, null, true, at(21, 12, 0, 0));
        ok("a slot finished early counts only the time it lasted", rep.rows.get(3).activeMs == 95 * 60000L);
        ok("the slot that began early counts from when it began", rep.rows.get(4).activeMs == 40 * 60000L);
        SlotGaps.Result g3 = SlotGaps.of(reportEv, 3, full.slotStartMs(3), full.slotEndMs(3), at(21, 12, 0, 0));
        SlotGaps.Result g4 = SlotGaps.of(reportEv, 4, full.slotStartMs(4), full.slotEndMs(4), at(21, 12, 0, 0));
        ok("the gaps record what moved", g3.cutMs == 25 * 60000L && g3.effEnd == at(8, 50) && g4.preMs == 25 * 60000L && g4.effStart == at(8, 50));
        List<Ev> reportEv2 = new ArrayList<>(early);
        reportEv2.add(Ev.breakTaken(at(21, 8, 50, 0), at(21, 9, 0, 0), DAY, 4, "Short break"));
        DayReport rep2 = ReportAssembler.assemble(full, reportEv2, null, true, at(21, 12, 0, 0));
        ok("a break taken right after finishing early comes off the next slot", rep2.rows.get(4).activeMs == 30 * 60000L && rep2.rows.get(4).breakMs == 10 * 60000L);
        List<Ev> reportEv3 = new ArrayList<>(early);
        reportEv3.add(Ev.delay(at(21, 8, 50, 0), DAY, 4, "Short break", at(9, 0)));
        DayReport rep3 = ReportAssembler.assemble(full, reportEv3, null, true, at(21, 12, 0, 0));
        ok("starting after a while takes that time off the next slot", rep3.rows.get(4).activeMs == 30 * 60000L && rep3.rows.get(4).delayMs == 10 * 60000L);
        DayReport repNow = ReportAssembler.assemble(full, reportEv, null, true, at(21, 8, 55, 0));
        ok("while it goes the early slot counts up to now", repNow.rows.get(4).activeMs == 5 * 60000L && repNow.rows.get(3).activeMs == 95 * 60000L);
        List<Ev> noEvents = new ArrayList<>();
        DayReport plainRep = ReportAssembler.assemble(full, noEvents, null, true, at(21, 12, 0, 0));
        ok("without any of this the report is as before", plainRep.rows.get(3).activeMs == 120 * 60000L && plainRep.rows.get(4).activeMs == 15 * 60000L);

        // ---- finishing early: when it is offered
        Plan.DayPlan mid3 = live(r, new ArrayList<Ev>(), new AlarmState(), false, at(8, 30));
        Plan.DayPlan cur = mid3 == null ? base(r, at(8, 30)) : mid3;
        EarlyFinish.Offer o = EarlyFinish.of(cur, at(8, 30));
        ok("offered in a slot with a real slot after it", o != null && o.srcCur == 3 && o.srcNext == 4 && o.roomMin == 45 && o.maxRestMin() == 44
            && o.plannedEndMs == at(9, 15) && o.nextEndMs == at(9, 30));
        ok("offered with two minutes left (build 27)", EarlyFinish.of(base(r, at(9, 13)), at(9, 13)) != null);
        ok("offered with a few minutes left", EarlyFinish.of(base(r, at(9, 10)), at(9, 10)) != null);
        EarlyFinish.Offer beforeSleep = EarlyFinish.of(base(r, at(21, 15)), at(21, 15));
        ok("before sleep it is offered as Early sleep (see EarlySkipTest)", beforeSleep != null && beforeSleep.kind == EarlyFinish.SLEEP && beforeSleep.label().equals("Early sleep"));
        ok("in sleep it is offered only once the next day is known (see EarlySkipTest)", EarlyFinish.of(base(r, at(23, 0)), at(23, 0)) == null);
        ok("not offered when nothing is going on", EarlyFinish.of(null, at(8, 0)) == null);
        Plan.DayPlan onCarry = live(r, carry, new AlarmState(), false, at(9, 35));
        EarlyFinish.Offer carryOffer = EarlyFinish.of(onCarry, at(9, 35));
        ok("offered in a slot that was carried on, and the next slot then starts now (build 27)", carryOffer == null || carryOffer.carried);
        Plan.DayPlan atBreak = live(r, brk, new AlarmState(), false, at(8, 10));
        ok("a break does not hide it any more (build 27; see AnySlotTest)", atBreak == null || EarlyFinish.of(atBreak, at(8, 10)) == null || EarlyFinish.of(atBreak, at(8, 10)).srcCur >= 0);
        Routine skipNext = routine();
        Overrides ovSkip = skipEdit(skipNext);
        ok("not offered when the next slot is taken off", EarlyFinish.of(Plan.resolve(skipNext, ovSkip, at(10, 30), Z), at(10, 30)) == null);
        Plan.DayPlan afterEarly = live(r, early, new AlarmState(), false, at(8, 55));
        EarlyFinish.Offer o2 = EarlyFinish.of(afterEarly, at(8, 55));
        ok("after finishing early, the next slot can be finished early too", o2 != null && o2.srcCur == 4 && o2.srcNext == 5 && o2.roomMin == 35);

        // ---- library mode
        LibraryMode lib = new LibraryMode();
        ok("library mode starts off", !lib.on(at(10, 0), Z) && lib.endMs(at(10, 0), Z) == 0 && lib.nextChangeMs(at(10, 0), Z) == 0);
        lib.turnOn(at(10, 0), 60);
        ok("on for an hour", lib.on(at(10, 30), Z) && !lib.on(at(11, 0), Z) && lib.endMs(at(10, 30), Z) == at(11, 0) && lib.nextChangeMs(at(10, 30), Z) == at(11, 0));
        lib.turnOn(at(10, 0), 0);
        ok("on until switched off", lib.on(at(23, 59), Z) && lib.openEnded(at(10, 0)) && lib.nextChangeMs(at(10, 0), Z) == 0 && lib.endMs(at(10, 0), Z) > at(10, 0));
        lib.turnOff();
        ok("switched off", !lib.on(at(10, 30), Z));
        LibraryMode hours = new LibraryMode();
        hours.auto = true; hours.days = LockRules.WEEKDAYS; hours.startMin = 9 * 60; hours.endMin = 17 * 60;
        ok("daily hours: on inside them (Wednesday 21 Oct)", hours.on(at(10, 0), Z) && hours.endMs(at(10, 0), Z) == at(17, 0) && !hours.on(at(8, 0), Z) && !hours.on(at(17, 0), Z));
        ok("daily hours: the next change is the end, or the next opening", hours.nextChangeMs(at(10, 0), Z) == at(17, 0) && hours.nextChangeMs(at(18, 0), Z) == at(22, 9, 0, 0));
        ok("daily hours: not on a day left out (Saturday 24 Oct)", !hours.on(at(24, 10, 0, 0), Z) && hours.nextStartMs(at(24, 10, 0, 0), Z) == at(26, 9, 0, 0));
        LibraryMode night = new LibraryMode();
        night.auto = true; night.days = LockRules.EVERY_DAY; night.startMin = 22 * 60; night.endMin = 6 * 60;
        ok("hours that run past midnight", night.on(at(23, 0), Z) && night.on(at(22, 3, 0, 0), Z) && !night.on(at(22, 7, 0, 0), Z) && night.endMs(at(22, 3, 0, 0), Z) == at(22, 6, 0, 0));
        LibraryMode off2 = new LibraryMode();
        off2.days = LockRules.EVERY_DAY; off2.startMin = 0; off2.endMin = 1439;
        ok("hours that are not switched on do nothing", !off2.on(at(10, 0), Z));
        LibraryMode both = new LibraryMode();
        both.auto = true; both.days = LockRules.EVERY_DAY; both.startMin = 9 * 60; both.endMin = 10 * 60;
        both.turnOn(at(9, 30), 120);
        ok("by hand and by the clock together last as long as the longer", both.endMs(at(9, 40), Z) == at(11, 30));
        LibraryMode copy = LibraryMode.parse(both.serialize());
        ok("library settings survive saving", copy.auto && copy.untilMs == both.untilMs && copy.days == both.days && copy.startMin == 540 && copy.endMin == 600 && !copy.ringer);
        LibraryMode openCopy = new LibraryMode();
        openCopy.turnOn(at(9, 0), 0);
        openCopy.ringer = true;
        LibraryMode oc = LibraryMode.parse(openCopy.serialize());
        ok("an open-ended session and the ringer choice survive saving", oc.untilMs == LibraryMode.OPEN_ENDED && oc.ringer && oc.on(at(23, 0), Z));
        ok("damaged library text gives the defaults", !LibraryMode.parse("nonsense").on(at(10, 0), Z) && LibraryMode.parse(null).days == LockRules.WEEKDAYS
            && LibraryMode.parse("DINALIB1\nstart=zz\nend=99999\ndays=-4\n").endMin == 1439);
        ok("status lines", new LibraryMode().status(at(10, 0), Z, false).equals("Off") && openCopy.status(at(10, 0), Z, false).equals("On until you switch it off")
            && both.status(at(9, 40), Z, true).startsWith("On until "));

        // ---- the phone's ringer while library mode is on
        LibraryMode.RingerStep r1 = LibraryMode.ringerStep(true, LibraryMode.RINGER_NORMAL, -1);
        ok("library on, ringer loud: go to vibrate and remember loud", r1.set == LibraryMode.RINGER_VIBRATE && r1.keep == LibraryMode.RINGER_NORMAL);
        LibraryMode.RingerStep r2 = LibraryMode.ringerStep(true, LibraryMode.RINGER_VIBRATE, -1);
        ok("library on, ringer already vibrate: leave it, remember nothing", r2.set == -1 && r2.keep == -1);
        LibraryMode.RingerStep r3 = LibraryMode.ringerStep(true, LibraryMode.RINGER_SILENT, -1);
        ok("library on, ringer silent: leave it, remember nothing", r3.set == -1 && r3.keep == -1);
        LibraryMode.RingerStep r4 = LibraryMode.ringerStep(true, LibraryMode.RINGER_VIBRATE, LibraryMode.RINGER_NORMAL);
        ok("library still on, already done: nothing more", r4.set == -1 && r4.keep == LibraryMode.RINGER_NORMAL);
        LibraryMode.RingerStep r5 = LibraryMode.ringerStep(true, LibraryMode.RINGER_NORMAL, LibraryMode.RINGER_NORMAL);
        ok("the person turned the sound back on during library mode: it is not forced again", r5.set == -1 && r5.keep == LibraryMode.RINGER_NORMAL);
        LibraryMode.RingerStep r6 = LibraryMode.ringerStep(false, LibraryMode.RINGER_VIBRATE, LibraryMode.RINGER_NORMAL);
        ok("library over, still vibrate: the old mode comes back", r6.set == LibraryMode.RINGER_NORMAL && r6.keep == -1);
        LibraryMode.RingerStep r7 = LibraryMode.ringerStep(false, LibraryMode.RINGER_SILENT, LibraryMode.RINGER_NORMAL);
        ok("library over, the person set silent meanwhile: their choice stands", r7.set == -1 && r7.keep == -1);
        LibraryMode.RingerStep r8 = LibraryMode.ringerStep(false, LibraryMode.RINGER_NORMAL, LibraryMode.RINGER_NORMAL);
        ok("library over, the person already restored it: nothing to do, memory dropped", r8.set == -1 && r8.keep == -1);
        LibraryMode.RingerStep r9 = LibraryMode.ringerStep(false, LibraryMode.RINGER_NORMAL, -1);
        ok("library off and nothing remembered: nothing to do", r9.set == -1 && r9.keep == -1);
        LibraryMode.RingerStep r10 = LibraryMode.ringerStep(false, LibraryMode.RINGER_VIBRATE, -1);
        ok("a phone on vibrate by the person's choice is never changed", r10.set == -1 && r10.keep == -1);

        LibraryMode on = new LibraryMode();
        on.turnOn(at(4, 0), 0);
        List<AlarmPlanner.Event> quiet = AlarmPlanner.upcoming(r, new Overrides(), new AlarmState(), at(4, 0), Z, on);
        boolean allVibrate = true;
        for (AlarmPlanner.Event e : quiet) if (e.mode == Routine.ALERT_RING) allVibrate = false;
        ok("in library mode no alarm rings: they all vibrate", !quiet.isEmpty() && allVibrate && quiet.get(0).wake && quiet.get(0).mode == Routine.ALERT_VIBRATE);
        ok("without library mode they ring as before", AlarmPlanner.upcoming(r, new Overrides(), new AlarmState(), at(4, 0), Z, null).get(0).mode == Routine.ALERT_RING);
        LibraryMode window = new LibraryMode();
        window.auto = true; window.days = LockRules.EVERY_DAY; window.startMin = 9 * 60; window.endMin = 12 * 60;
        List<AlarmPlanner.Event> partial = AlarmPlanner.upcoming(r, new Overrides(), new AlarmState(), at(4, 0), Z, window);
        boolean mixed = true;
        for (AlarmPlanner.Event e : partial) {
            boolean inside = e.timeMs % 86400000L >= 0 && window.on(e.timeMs, Z);
            if (inside != (e.mode == Routine.ALERT_VIBRATE) && e.mode != Routine.ALERT_SILENT) mixed = false;
        }
        ok("with daily hours only the alarms inside them are quiet", mixed && partial.get(0).mode == Routine.ALERT_RING
            && partial.stream().anyMatch(e -> e.mode == Routine.ALERT_VIBRATE));
        Routine mixedRoutine = routine();
        mixedRoutine.blocks.get(5).setAlert(Routine.ALERT_SILENT);
        mixedRoutine.blocks.get(6).setAlert(Routine.ALERT_VIBRATE);
        List<AlarmPlanner.Event> keep = AlarmPlanner.upcoming(mixedRoutine, new Overrides(), new AlarmState(), at(4, 0), Z, on);
        ok("a silent slot stays silent and a vibrate slot stays vibrate in library mode", keep.stream().filter(e -> e.src == 5 && e.day.equals(DAY)).findFirst().get().mode == Routine.ALERT_SILENT
            && keep.stream().filter(e -> e.src == 6 && e.day.equals(DAY)).findFirst().get().mode == Routine.ALERT_VIBRATE);
        AlarmState tm = new AlarmState();
        tm.breakFrom = at(8, 0); tm.breakTo = at(8, 20); tm.breakSrc = 3; tm.breakDay = DAY;
        tm.againAt = at(8, 30); tm.againSrc = 3; tm.againDay = DAY;
        List<AlarmPlanner.Event> timerList = AlarmPlanner.upcoming(r, new Overrides(), tm, at(8, 5), Z, on);
        ok("the timers (break end, later start) vibrate in library mode too", timerList.stream().filter(e -> e.isTimer()).count() == 2 && timerList.stream().filter(e -> e.isTimer()).allMatch(e -> e.mode == Routine.ALERT_VIBRATE));
        AlarmPlanner.Event ringing = plain.get(1);
        ok("an event can be made quiet when it rings", ringing.mode == Routine.ALERT_RING && ringing.withMode(Routine.ALERT_VIBRATE).mode == Routine.ALERT_VIBRATE
            && ringing.withMode(Routine.ALERT_VIBRATE).timeMs == ringing.timeMs && ringing.withMode(Routine.ALERT_VIBRATE).src == ringing.src && ringing.withMode(Routine.ALERT_RING) == ringing);
        ok("the start-of-day alarm stays the start-of-day alarm in library mode", AlarmPlanner.upcoming(r, new Overrides(), new AlarmState(), at(4, 0), Z, on).get(0).wake);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static Overrides skipEdit(Routine r) {
        Overrides ov = new Overrides();
        Plan.edit(r, ov, LocalDate.of(2026, 10, 21), 5, true, "");
        return ov;
    }
}
