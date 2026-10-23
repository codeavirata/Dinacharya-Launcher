package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** The four alerts a slot can have, the wake-up slot, timers, and the time a slot loses to breaks and late starts. */
public class AlertModeTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static LocalDate day(int d) { return LocalDate.of(2026, 10, d); }

    static AlarmPlanner.Event eventFor(List<AlarmPlanner.Event> l, int src) {
        for (AlarmPlanner.Event e : l) if (e.src == src && !e.isTimer()) return e;
        return null;
    }

    public static void main(String[] a) {
        Routine r = Routine.sample();
        r.alarmOn = true;
        Overrides ov = new Overrides();
        AlarmState st = new AlarmState();
        long after = at(21, 4, 0);

        // ---- the four alerts
        ok("a new slot rings", new Routine.Block(Routine.TASK, "x", 30).alert == Routine.ALERT_RING && new Routine.Block(Routine.TASK, "x", 30).alarm);
        Routine.Block b = new Routine.Block(Routine.TASK, "x", 30);
        b.setAlert(Routine.ALERT_VIBRATE);
        ok("vibrate counts as a sounding alert", b.alarm && b.sounds());
        b.setAlert(Routine.ALERT_SILENT);
        ok("silent does not sound", !b.alarm && !b.sounds());
        b.setAlert(99);
        ok("a wrong number means ring", b.alert == Routine.ALERT_RING);

        List<AlarmPlanner.Event> all = AlarmPlanner.upcoming(r, ov, st, after, Z);
        AlarmPlanner.Event first = all.get(0);
        ok("by default every slot is listed and rings", first.mode == Routine.ALERT_RING && all.size() > 15);
        int src = first.src;
        r.blocks.get(src).setAlert(Routine.ALERT_VIBRATE);
        ok("a vibrate slot is listed as vibrate", eventFor(AlarmPlanner.upcoming(r, ov, st, after, Z), src).mode == Routine.ALERT_VIBRATE);
        r.blocks.get(src).setAlert(Routine.ALERT_SILENT);
        ok("a silent slot is still listed (it asks without a sound)", eventFor(AlarmPlanner.upcoming(r, ov, st, after, Z), src).mode == Routine.ALERT_SILENT);
        r.askSilent = false;
        ok("with the silent questions off it is left out", eventFor(AlarmPlanner.upcoming(r, ov, st, after, Z), src) == null);
        r.askSilent = true;
        r.blocks.get(src).setAlert(Routine.ALERT_NONE);
        ok("a slot with no alert is left out", eventFor(AlarmPlanner.upcoming(r, ov, st, after, Z), src) == null);
        r.blocks.get(src).setAlert(Routine.ALERT_RING);
        r.alarmOn = false;
        ok("alarms off: no slot events", AlarmPlanner.upcoming(r, ov, st, after, Z).isEmpty());
        r.alarmOn = true;

        // ---- saved text
        Routine back = Routine.parse(r.serialize());
        ok("alerts survive a save", back.blocks.get(0).alert == Routine.ALERT_RING);
        r.blocks.get(2).setAlert(Routine.ALERT_VIBRATE);
        r.blocks.get(3).setAlert(Routine.ALERT_SILENT);
        r.blocks.get(4).setAlert(Routine.ALERT_NONE);
        back = Routine.parse(r.serialize());
        ok("all four alerts round trip", back.blocks.get(2).alert == Routine.ALERT_VIBRATE && back.blocks.get(3).alert == Routine.ALERT_SILENT
            && back.blocks.get(4).alert == Routine.ALERT_NONE && back.blocks.get(1).alert == Routine.ALERT_RING);
        Routine old = Routine.parse("DINACHARYA1\nB\t0\t60\tStudy\t1\nB\t0\t30\tRead\t0\nB\t4\t480\tSleep\t1\n");
        ok("an older save: bell on is ring, bell off is a silent question", old.blocks.get(0).alert == Routine.ALERT_RING && old.blocks.get(1).alert == Routine.ALERT_SILENT);
        r.askSilent = false;
        r.wakeDelayMin = 10;
        r.wakeChallenge = Routine.CHALLENGE_TYPE;
        r.wakeQuote = "  Rise, then shine.  ";
        back = Routine.parse(r.serialize());
        ok("alarm settings round trip", !back.askSilent && back.wakeDelayMin == 10 && back.wakeChallenge == Routine.CHALLENGE_TYPE && back.wakeQuote.equals("Rise, then shine."));
        Routine plain = Routine.parse(Routine.sample().serialize());
        ok("defaults: silent questions on, 5 minute delay, no challenge, no own quote",
            plain.askSilent && plain.wakeDelayMin == 5 && plain.wakeChallenge == Routine.CHALLENGE_NONE && plain.wakeQuote.isEmpty());
        r.askSilent = true;
        r.wakeQuote = "";

        // ---- the wake-up slot
        Routine w = Routine.sample();
        ok("no wake-up slot at first", w.wakeIndex() == -1);
        ok("the first slot can be the wake-up", w.canBeWake(0) && w.setWake(0, true) && w.wakeIndex() == 0);
        ok("a later slot cannot be while an earlier one has an alert", !w.canBeWake(3));
        ok("only one wake-up slot: asking again moves nothing", w.setWake(3, true) == false && w.wakeIndex() == -1);
        w.setWake(0, true);
        for (int i = 0; i < 2; i++) w.blocks.get(i).setAlert(Routine.ALERT_NONE);
        ok("when earlier slots have no alert a later slot can be it", w.canBeWake(2) && w.setWake(2, true) && w.wakeIndex() == 2);
        w.blocks.get(1).setAlert(Routine.ALERT_RING);
        w.normalizeWake();
        ok("an alert added before it takes the mark off", w.wakeIndex() == -1);
        w.setWake(2, false);
        w.blocks.get(0).setAlert(Routine.ALERT_SILENT);
        ok("a slot that makes no sound cannot be the wake-up", !w.canBeWake(0));
        Routine wk = Routine.sample();
        wk.setWake(0, true);
        Routine wb = Routine.parse(wk.serialize());
        ok("the wake-up mark survives a save, and is only one", wb.wakeIndex() == 0 && wb.blocks.get(0).alert == Routine.ALERT_RING);
        wk.alarmOn = true;
        AlarmPlanner.Event we = AlarmPlanner.upcoming(wk, new Overrides(), new AlarmState(), at(21, 4, 0), Z).get(0);
        ok("the planner marks the wake-up event", we.wake && we.firstOfDay && we.kind == AlarmPlanner.SLOT);
        ok("other events are not wake-ups", !AlarmPlanner.upcoming(wk, new Overrides(), new AlarmState(), at(21, 4, 0), Z).get(1).wake);
        Routine.Block s = new Routine.Block(Routine.TASK, "x", 30);
        s.wake = true;
        s.setAlert(Routine.ALERT_NONE);
        ok("changing the alert to something silent drops the mark", !s.wake);

        // ---- timers
        AlarmState t = new AlarmState();
        t.breakFrom = at(21, 10, 0);
        t.breakTo = at(21, 10, 10);
        t.breakDay = "2026-10-21";
        t.breakSrc = 3;
        t.breakTitle = "Morning focus block";
        t.breakSlotEnd = at(21, 11, 0);
        ok("on a break inside the break, not outside", t.onBreak(at(21, 10, 5)) && !t.onBreak(at(21, 9, 59)) && !t.onBreak(at(21, 10, 10)));
        List<AlarmPlanner.Event> ts = AlarmPlanner.upcoming(r, ov, t, at(21, 10, 2), Z);
        AlarmPlanner.Event be = null;
        for (AlarmPlanner.Event e : ts) if (e.kind == AlarmPlanner.BREAK_END) be = e;
        ok("a break end is listed at its time", be != null && be.timeMs == at(21, 10, 10) && be.isTimer() && be.title.equals("Morning focus block"));
        r.alarmOn = false;
        ts = AlarmPlanner.upcoming(r, ov, t, at(21, 10, 2), Z);
        ok("with alarms off a break end is still listed, and only buzzes", ts.size() == 1 && ts.get(0).kind == AlarmPlanner.BREAK_END && ts.get(0).mode == Routine.ALERT_VIBRATE);
        r.alarmOn = true;
        ok("not listed once past", AlarmPlanner.upcoming(r, ov, t, at(21, 10, 11), Z).stream().noneMatch(e -> e.kind == AlarmPlanner.BREAK_END));
        AlarmState g = new AlarmState();
        g.againAt = at(21, 6, 15);
        g.againSrc = 1;
        g.againTitle = "Yoga";
        g.againDay = "2026-10-21";
        ok("a later start is listed", AlarmPlanner.upcoming(r, ov, g, at(21, 6, 0), Z).stream().anyMatch(e -> e.kind == AlarmPlanner.START_AGAIN && e.timeMs == at(21, 6, 15)));
        AlarmPlanner.Event na = AlarmPlanner.nextAlarm(r, ov, t, at(21, 10, 2), Z);
        ok("the next alarm for the home card leaves the timers out", na != null && !na.isTimer());
        AlarmState back2 = AlarmState.parse(t.serialize());
        ok("break and later start survive saving", back2.breakTo == t.breakTo && back2.breakSrc == 3 && back2.breakTitle.equals("Morning focus block") && AlarmState.parse(g.serialize()).againAt == g.againAt);

        // ---- gaps inside a slot (a slot is 10:00 to 11:00, slot 3)
        long s0 = at(21, 10, 0), e0 = at(21, 11, 0);
        List<Ev> evs = new ArrayList<>();
        evs.add(Ev.breakTaken(at(21, 10, 20), at(21, 10, 30), "2026-10-21", 3, "Study"));
        SlotGaps.Result gr = SlotGaps.of(evs, 3, s0, e0, at(21, 12, 0));
        ok("a break takes its minutes from the slot", gr.breakMs == 10 * 60000L && gr.gapMs() == 10 * 60000L && gr.gaps.size() == 1);
        evs.add(Ev.breakTaken(at(21, 10, 20), at(21, 10, 25), "2026-10-21", 3, "Study"));
        gr = SlotGaps.of(evs, 3, s0, e0, at(21, 12, 0));
        ok("a break that ended early (written again) counts the shorter time", gr.breakMs == 5 * 60000L);
        gr = SlotGaps.of(evs, 3, s0, e0, at(21, 10, 22));
        ok("a break still running counts only up to now", gr.breakMs == 2 * 60000L);
        evs.add(Ev.breakTaken(at(21, 10, 50), at(21, 11, 20), "2026-10-21", 3, "Study"));
        gr = SlotGaps.of(evs, 3, s0, e0, at(21, 12, 0));
        ok("a break cannot run past the slot into the next", gr.breakMs == 5 * 60000L + 10 * 60000L);
        ok("another slot's break is not this slot's", SlotGaps.of(evs, 4, s0, e0, at(21, 12, 0)).gapMs() == 0);
        evs.clear();
        evs.add(Ev.delay(at(21, 10, 3), "2026-10-21", 3, "Study", at(21, 10, 13)));
        gr = SlotGaps.of(evs, 3, s0, e0, at(21, 12, 0));
        ok("a put-off start loses the time from the slot's start to when work starts", gr.delayMs == 13 * 60000L);
        evs.add(Ev.breakTaken(at(21, 10, 5), at(21, 10, 20), "2026-10-21", 3, "Study"));
        gr = SlotGaps.of(evs, 3, s0, e0, at(21, 12, 0));
        ok("overlapping gaps count once", gr.gapMs() == 20 * 60000L && gr.gaps.size() == 1);
        evs.clear();
        evs.add(Ev.extend(at(21, 10, 1), "2026-10-21", 3, 2, "Reading", at(21, 10, 15)));
        gr = SlotGaps.of(evs, 3, s0, e0, at(21, 12, 0));
        ok("carrying the slot before on gives that part of this slot away", gr.lentMs == 15 * 60000L && gr.gapMs() == 15 * 60000L);
        gr = SlotGaps.of(evs, 2, at(21, 9, 0), s0, at(21, 12, 0));
        ok("and the slot before gains it", gr.extMs == 15 * 60000L && gr.gapMs() == 0);
        List<long[]> work = SlotGaps.complement(s0, e0, SlotGaps.of(evs, 3, s0, e0, at(21, 12, 0)).gaps);
        ok("the worked part starts after the gap", work.size() == 1 && work.get(0)[0] == at(21, 10, 15) && work.get(0)[1] == e0);

        // ---- the report
        Routine rp = Routine.sample();
        rp.alarmOn = true;
        Plan.DayPlan p = Plan.plan(rp, new Overrides(), day(21), Z);
        int i3 = 3;
        long st3 = p.slotStartMs(i3), en3 = p.slotEndMs(i3);
        List<Ev> events = new ArrayList<>();
        events.add(Ev.breakTaken(st3 + 20 * 60000L, st3 + 30 * 60000L, "2026-10-21", i3, "Morning focus block"));
        events.add(Ev.ack(st3, "2026-10-21", i3, "Morning focus block", "C", ""));
        // phone use: 10 minutes at the start of the slot in one app, 10 minutes during the break in another
        List<long[]> iv = new ArrayList<>();
        iv.add(new long[]{st3, st3 + 10 * 60000L});
        iv.add(new long[]{st3 + 20 * 60000L, st3 + 30 * 60000L});
        Map<String, Long> per = new LinkedHashMap<>();
        per.put("Notes", 10 * 60000L);
        per.put("Chat", 10 * 60000L);
        Map<String, List<long[]>> times = new HashMap<>();
        times.put("Notes", new ArrayList<>(Arrays.asList(new long[]{st3, st3 + 10 * 60000L})));
        times.put("Chat", new ArrayList<>(Arrays.asList(new long[]{st3 + 20 * 60000L, st3 + 30 * 60000L})));
        UsageDetail u = new UsageDetail(p.startMs, p.endMs, iv, per, times);
        DayReport rep = ReportAssembler.assemble(p, events, u, true, p.endMs + 1000L);
        DayReport.Row row = rep.rows.get(i3);
        ok("the report keeps the break", row.breakMs == 10 * 60000L && row.activeMs == en3 - st3 - 10 * 60000L);
        ok("phone use during the break is not counted against the slot", row.phoneMs == 10 * 60000L);
        ok("only the app used while working shows for the slot", row.apps.size() == 1 && row.apps.get(0).name.equals("Notes"));
        ok("time without any app", row.noAppMs() == row.activeMs - 10 * 60000L);
        DayReport re = DayReport.parse(rep.serialize());
        DayReport.Row r2 = re.rows.get(i3);
        ok("the extra slot figures survive saving", r2.breakMs == row.breakMs && r2.apps.size() == 1 && r2.apps.get(0).ms == 10 * 60000L && re.rows.get(0).apps.isEmpty());
        ok("done time is the time worked, so the break is not done", TypeStats.doneMinutes(row) == (int) ((en3 - st3) / 60000L) - 10);
        List<TypeStats.Line> lines = TypeStats.of(rep);
        TypeStats.Line study = TypeStats.find(lines, Routine.TASK, rp.blocks.get(i3).sub);
        ok("a type line adds up the slot's time and apps", study != null && study.apps.containsKey("Notes") && !study.apps.containsKey("Chat") && study.noAppMs() > 0);

        // ---- the rules for breaks and later starts
        long now = at(21, 10, 0), end = at(21, 11, 0);
        ok("a break is the length asked", SlotPlay.breakEnd(now, 10, end) == at(21, 10, 10));
        ok("a break stops at the end of the slot", SlotPlay.breakEnd(at(21, 10, 55), 30, end) == end);
        ok("a later start is the time asked", SlotPlay.startAfter(now, 10, end) == at(21, 10, 10));
        ok("a later start leaves a minute of the slot", SlotPlay.startAfter(now, 90, end) == at(21, 10, 59));
        ok("carrying the slot before stops inside this slot", SlotPlay.continueUntil(now, 10, end) == at(21, 10, 10) && SlotPlay.continueUntil(now, 120, end) == at(21, 10, 59));
        ok("room is what is left of the slot", SlotPlay.roomMinutes(now, end) == 60 && SlotPlay.roomMinutes(end + 5000, end) == 0);
        ok("the minute button moves by five", SlotPlay.stepMinutes(10, true, 60) == 15 && SlotPlay.stepMinutes(10, false, 60) == 5);
        ok("and stays between five and what fits", SlotPlay.stepMinutes(5, false, 60) == 5 && SlotPlay.stepMinutes(60, true, 60) == 60 && SlotPlay.stepMinutes(10, true, 12) == 10);
        Plan.DayPlan dp = Plan.plan(Routine.sample(), new Overrides(), day(21), Z);
        ok("the first slot has nothing before it to carry on", SlotPlay.previousIndex(dp, 0) == -1);
        ok("a slot after a task can carry it on", SlotPlay.previousIndex(dp, 4) == 3);
        Routine sl = Routine.sample();
        Plan.DayPlan sp = Plan.plan(sl, new Overrides(), day(21), Z);
        int last = sp.sc.slots.size() - 1;
        ok("sleep is never carried on", SlotPlay.previousIndex(sp, last + 1) == -1 && sp.sc.slots.get(last).block.kind == Routine.SLEEP);
        Overrides skip = new Overrides();
        Plan.edit(sl, skip, day(21), 3, true, "");
        Plan.DayPlan skipped = Plan.plan(sl, skip, day(21), Z);
        ok("a slot that was taken off is not carried on", SlotPlay.previousIndex(skipped, 4) == -1);
        ok("indexOfSrc finds a slot by its number", SlotPlay.indexOfSrc(dp, 5) == 5 && SlotPlay.indexOfSrc(dp, 999) == -1);
        dp.cur = 3;
        ok("a break can be taken in a running task", SlotPlay.canBreak(dp));
        dp.cur = last;
        Plan.DayPlan sp2 = Plan.plan(sl, new Overrides(), day(21), Z);
        sp2.cur = last;
        ok("but not in sleep, or when nothing is running", !SlotPlay.canBreak(sp2) && !SlotPlay.canBreak(Plan.plan(sl, new Overrides(), day(21), Z)));

        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
