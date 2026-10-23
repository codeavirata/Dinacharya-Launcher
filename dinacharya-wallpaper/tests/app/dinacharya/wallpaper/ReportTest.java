package app.dinacharya.wallpaper;

import java.io.File;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

public class ReportTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static String hm(long ms) { return Plan.clock(ms, Z, true); }
    static final long MIN = 60000L;

    static UsageDetail usage(long[][] iv, Map<String, Long> apps) {
        List<long[]> l = new ArrayList<>(Arrays.asList(iv));
        return new UsageDetail(0, Long.MAX_VALUE / 4, l, apps);
    }

    public static void main(String[] a) throws Exception {
        Routine base = Routine.sample();
        base.alarmOn = true;
        Overrides ov = new Overrides(); ov.sig = Plan.signature(base);
        LocalDate d19 = LocalDate.of(2026, 10, 19);

        // ---------- alarm planner
        AlarmState st = new AlarmState();
        List<AlarmPlanner.Event> up = AlarmPlanner.upcoming(base, ov, st, at(19, 10, 43), Z);
        ok("next alarm after 10:43 is Tea break at 11:00", hm(up.get(0).timeMs).equals("11:00") && up.get(0).title.equals("Tea break") && up.get(0).kind == AlarmPlanner.SLOT);
        ok("alarms are in order", isSorted(up));
        AlarmPlanner.Event bed = null; for (AlarmPlanner.Event e : up) if (e.kind == AlarmPlanner.BEDTIME) { bed = e; break; }
        ok("bedtime event at 21:30 carries planned bed", bed != null && hm(bed.timeMs).equals("21:30") && bed.plannedBedMs == bed.timeMs && bed.day.equals("2026-10-19"));
        AlarmPlanner.Event wake = null; for (AlarmPlanner.Event e : up) if (e.firstOfDay && e.timeMs > at(20, 0, 0)) { wake = e; break; }
        ok("next wake alarm 05:30 on the 20th", wake != null && hm(wake.timeMs).equals("05:30") && wake.day.equals("2026-10-20"));
        Routine off = Routine.sample();
        ok("alarms off: nothing", AlarmPlanner.upcoming(off, ov, st, at(19, 10, 43), Z).isEmpty());
        Routine blockOff = Routine.sample(); blockOff.alarmOn = true; blockOff.blocks.get(6).setAlert(Routine.ALERT_NONE); // Tea break
        ok("a slot set to no alert is skipped", AlarmPlanner.next(blockOff, ov, st, at(19, 10, 43), Z).title.equals("Practice problems"));
        Overrides sk = ov.copy(); Plan.edit(base, sk, d19, 6, true, "");
        ok("a slot taken off does not ring", AlarmPlanner.next(base, sk, st, at(19, 10, 43), Z).title.equals("Practice problems"));
        Overrides rn = ov.copy(); Plan.edit(base, rn, d19, 6, false, "Chai with friends");
        ok("renamed slot rings with its new name", AlarmPlanner.next(base, rn, st, at(19, 10, 43), Z).title.equals("Chai with friends"));
        st.reaskAt = at(19, 22, 15); st.reaskBedMs = at(19, 21, 30); st.reaskDay = "2026-10-19"; st.reaskSrc = 18;
        List<AlarmPlanner.Event> up2 = AlarmPlanner.upcoming(base, ov, st, at(19, 21, 31), Z);
        ok("re-ask is the next alarm", up2.get(0).kind == AlarmPlanner.REASK && hm(up2.get(0).timeMs).equals("22:15") && up2.get(0).plannedBedMs == at(19, 21, 30));
        ok("alarm already fired is not repeated", AlarmPlanner.next(base, ov, new AlarmState(), at(19, 11, 0), Z).title.equals("Practice problems"));
        Overrides w2 = ov.copy();
        List<Plan.WakeOption> wo = Plan.wakeOptions(base, ov, d19, at(19, 23, 30), Z, true);
        Plan.commit(base, w2, d19, wo.get(1));
        AlarmPlanner.Event wk2 = null;
        for (AlarmPlanner.Event e : AlarmPlanner.upcoming(base, w2, new AlarmState(), at(19, 23, 31), Z)) { wk2 = e; break; }
        ok("after a shifted choice the next alarm is the new wake time", hm(wk2.timeMs).equals("07:30") && wk2.firstOfDay && wk2.day.equals("2026-10-20"));
        Overrides w1 = ov.copy(); Plan.commit(base, w1, d19, wo.get(0));
        ok("normal choice keeps 05:30", hm(AlarmPlanner.next(base, w1, new AlarmState(), at(19, 23, 31), Z).timeMs).equals("05:30"));
        AlarmState rs = AlarmState.parse(st.serialize());
        ok("alarm state round trip", rs.reaskAt == st.reaskAt && rs.reaskSrc == 18 && rs.reaskDay.equals("2026-10-19"));

        // ---------- a whole day
        List<Ev> evs = new ArrayList<>();
        evs.add(Ev.alarms(at(18, 20, 0), true));
        String[] titles = new String[base.blocks.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = base.blocks.get(i).title;
        long[] ackAt = new long[]{at(19, 5, 31), at(19, 6, 1), at(19, 6, 46), at(19, 7, 16), at(19, 9, 16), at(19, 9, 31), at(19, 11, 1), at(19, 11, 11), at(19, 12, 41)};
        String[] act = {"C", "C", "C", "C", "C", "R", "S", "C", "C"};
        for (int i = 0; i < act.length; i++) evs.add(Ev.ack(ackAt[i], "2026-10-19", i, titles[i], act[i], act[i].equals("R") ? "Mock test" : ""));
        evs.add(Ev.snooze(at(19, 21, 31), "2026-10-19", 30));
        evs.add(Ev.snooze(at(19, 22, 2), "2026-10-19", 30));
        evs.add(Ev.sleep(at(19, 23, 30), "2026-10-19", at(19, 21, 30), 0, 0));
        Overrides day = ov.copy();
        Plan.edit(base, day, d19, 5, false, "Mock test");
        Plan.edit(base, day, d19, 6, true, "");
        Plan.DayPlan p = Plan.plan(base, day, d19, Z);
        Map<String, Long> apps = new LinkedHashMap<>();
        apps.put("YouTube", 45 * MIN); apps.put("WhatsApp", 25 * MIN); apps.put("Chrome", 20 * MIN);
        UsageDetail u = usage(new long[][]{{at(19, 7, 30), at(19, 8, 0)}, {at(19, 11, 0), at(19, 11, 10)}, {at(19, 15, 0), at(19, 15, 40)}, {at(19, 21, 40), at(19, 22, 10)}, {at(19, 22, 30), at(19, 22, 45)}}, apps);
        long closeNow = at(20, 5, 31);
        DayReport r = ReportAssembler.assemble(p, evs, u, true, closeNow);
        ok("closed day is not partial", !r.partial && r.day.equals("2026-10-19"));
        ok("status kept", r.rows.get(0).status == DayReport.KEPT && r.rows.get(4).status == DayReport.KEPT);
        ok("status changed with new name", r.rows.get(5).status == DayReport.CHANGED && r.rows.get(5).newTitle.equals("Mock test") && r.rows.get(5).title.equals("Reading & notes"));
        ok("status taken off", r.rows.get(6).status == DayReport.SKIPPED);
        ok("no answer after the last ack", r.rows.get(10).status == DayReport.UNANSWERED);
        ok("sleep row is not tracked", r.rows.get(18).status == DayReport.UNTRACKED);
        ok("phone in Morning focus 30 min", r.rows.get(3).phoneMs == 30 * MIN);
        ok("phone in Project work 40 min", r.rows.get(10).phoneMs == 40 * MIN);
        ok("phone total 30+10+40+30+15 = 125", r.phoneKnown && r.phoneMs == 125 * MIN);
        ok("hour bucket 2 (07:30-08:30) is 30 min", r.hourMs[2] == 30 * MIN);
        long hsum = 0; for (long h : r.hourMs) hsum += h;
        ok("hour buckets add up to total", hsum == r.phoneMs);
        ok("top apps in order", r.apps.size() == 3 && r.apps.get(0).name.equals("YouTube"));
        ok("bed planned 21:30, actual 23:30", hm(r.plannedBedMs).equals("21:30") && hm(r.actualBedMs).equals("23:30") && r.plannedSleepMin == 480);
        ok("2 snoozes, 60 minutes", r.snoozes == 2 && r.snoozeMinutes == 60);
        ok("phone before bed (22:30-23:30 window) = 15 min", r.beforeBedMs == 15 * MIN);
        // expected score
        double pts = 0; int n = 0;
        for (DayReport.Row row : r.rows) { if (row.kind == Routine.SLEEP) continue; n++; if (row.status == DayReport.KEPT) pts += 1; if (row.status == DayReport.CHANGED) pts += .5; }
        ok("plan kept = " + Math.round(100 * pts / n), r.planKept == Math.round(100 * pts / n));
        long taskMs = 0, taskPhone = 0;
        for (DayReport.Row row : r.rows) if (row.kind == Routine.TASK && row.status != DayReport.SKIPPED) { taskMs += row.activeMs; taskPhone += row.phoneMs; }
        int focusExp = (int) Math.round(100 - 100 * ((double) taskPhone / taskMs) / 0.35);
        ok("focus = " + focusExp, r.focus == focusExp);
        // slept 23:30 -> 05:30 = 360 of 480; 120 late => bed 0; dur 75
        ok("sleep score 38", r.sleep == 38);
        int scoreExp = (int) Math.round((40.0 * r.planKept + 35.0 * r.focus + 25.0 * r.sleep) / 100);
        ok("score weighted 40/35/25 = " + scoreExp, r.score == scoreExp);
        ok("schedule snapshot is a routine", Routine.parse(r.schedule) != null && Routine.parse(r.schedule).blocks.size() == 19);
        DayReport back = DayReport.parse(r.serialize());
        ok("report round trip", back != null && back.serialize().equals(r.serialize()));

        // no phone data
        DayReport np = ReportAssembler.assemble(p, evs, null, true, closeNow);
        ok("no usage: phone unknown, focus not scored, score still there", !np.phoneKnown && np.focus == -1 && np.score >= 0 && np.apps.isEmpty());
        // alarms were off
        DayReport offDay = ReportAssembler.assemble(Plan.plan(base, ov, d19, Z), new ArrayList<Ev>(), u, false, closeNow);
        ok("alarms off all day: slots not tracked, plan kept unknown", offDay.planKept == -1 && offDay.rows.get(3).status == DayReport.UNTRACKED && !offDay.alarmsOn);
        List<Ev> toggled = new ArrayList<>(); toggled.add(Ev.alarms(at(19, 12, 0), true));
        DayReport mid = ReportAssembler.assemble(Plan.plan(base, ov, d19, Z), toggled, u, true, closeNow);
        ok("alarms switched on at noon: morning not tracked, afternoon is", mid.rows.get(3).status == DayReport.UNTRACKED && mid.rows.get(10).status == DayReport.UNANSWERED);
        ok("alarmsOnAt before/after toggles", !ReportAssembler.alarmsOnAt(toggled, true, at(19, 9, 0)) && ReportAssembler.alarmsOnAt(toggled, true, at(19, 15, 0)));

        // partial day at 10:43
        DayReport pt = ReportAssembler.assemble(p, evs, u, true, at(19, 10, 43));
        ok("partial flag", pt.partial);
        ok("future slot is upcoming", pt.rows.get(12).status == DayReport.UPCOMING && pt.rows.get(12).activeMs == 0);
        ok("running slot partly elapsed", pt.rows.get(5).activeMs == 73 * MIN);
        ok("no sleep part yet", pt.sleep == -1 && pt.score >= 0);

        // shifted day: sleep planned excludes the extension
        Overrides sh = ov.copy(); Plan.commit(base, sh, d19, wo.get(1));
        DayReport tonight = ReportAssembler.assemble(Plan.plan(base, sh, d19, Z), evs, u, true, at(20, 7, 31));
        ok("extended sleep: planned sleep still 480, ends 07:30", tonight.plannedSleepMin == 480 && hm(tonight.endMs).equals("07:30"));
        ok("extended sleep: full 8h gives sleep score = (0+100)/2 = 50", tonight.sleep == 50);

        // ---------- history store
        File tmp = Files.createTempDirectory("dina").toFile();
        HistoryStore hs = new HistoryStore(tmp);
        for (Ev e : evs) hs.append(e);
        List<Ev> back2 = hs.events();
        ok("events saved and read back", back2.size() == evs.size() && back2.get(1).title.equals("Wake up & freshen up"));
        hs.saveReport(r);
        ok("report saved", hs.hasReport("2026-10-19") && hs.loadReport("2026-10-19").score == r.score);
        ok("report listed", hs.reportDays().equals(Arrays.asList("2026-10-19")));
        hs.append(new Ev(1000L, Ev.ACK, "2020-01-01", 0, "C", "", "old"));
        hs.compact(at(20, 0, 0));
        ok("old events are compacted away", hs.events().size() == evs.size());
        ok("unreadable lines are ignored", Ev.parse("garbage") == null && Ev.parse("1\tack\td\tx\ta\tb\tc") == null);
        ok("favourite snooze is 30", Suggestions.favouriteSnooze(evs) == 30);
        ok("no snoozes: 15", Suggestions.favouriteSnooze(new ArrayList<Ev>()) == 15);

        // ---------- suggestions
        List<DayReport> past = new ArrayList<>();
        for (int i = 1; i <= 7; i++) past.add(fakeDay(i));
        List<String> tips = Suggestions.generate(r, past, base);
        System.out.println("  tips: " + tips);
        boolean hasSkip = false, hasLate = false, hasFocus = false;
        for (String t : tips) { if (t.contains("Evening walk")) hasSkip = true; if (t.contains("late on average")) hasLate = true; if (t.contains("Project work")) hasFocus = true; }
        ok("tip: repeated skip", hasSkip);
        ok("tip: late nights", hasLate);
        ok("tip: phone during a task block", hasFocus);
        ok("at most 5 tips", tips.size() <= 5);
        ok("setup tips when alarms off", Suggestions.generate(offDay, new ArrayList<DayReport>(), base).get(0).contains("alarms"));
        ok("empty history gives a gentle message", Suggestions.generate(null, new ArrayList<DayReport>(), base).get(0).startsWith("Not enough days"));
        Routine gap = Routine.sample(); gap.blocks.remove(gap.blocks.size() - 2); gap.blocks.remove(gap.blocks.size() - 2);
        boolean unplanned = false; for (String t : Suggestions.generate(null, new ArrayList<DayReport>(), gap)) if (t.contains("not planned")) unplanned = true;
        ok("tip: unplanned time", unplanned);

        // ---------- week stats
        WeekStats ws = WeekStats.of(past, 7);
        ok("week has 7 days oldest first", ws.days.size() == 7 && ws.days.get(0).day.compareTo(ws.days.get(6).day) < 0);
        ok("week averages", ws.avgScore >= 0 && ws.avgPhoneMin > 0 && ws.avgLateMin == 120);
        ok("week worst slot is Evening walk", ws.worstSlot.equals("Evening walk"));
        System.out.println("  week: avg " + ws.avgScore + " phone " + ws.avgPhoneMin + " late " + ws.avgLateMin + " best " + ws.bestSlot + " " + ws.bestPct + " worst " + ws.worstSlot + " " + ws.worstPct + " good " + ws.goodDays);
        ok("grades", Analyzer.grade(90).equals("Excellent") && Analyzer.grade(72).equals("Good") && Analyzer.grade(-1).equals("Not enough data"));

        System.out.println(fails == 0 ? "ALL PASS" : ("FAILS: " + fails));
    }

    static boolean isSorted(List<AlarmPlanner.Event> l) { for (int i = 1; i < l.size(); i++) if (l.get(i).timeMs < l.get(i - 1).timeMs) return false; return true; }

    /** A synthetic closed day: Evening walk taken off, 2h-late bedtime, 40 min of phone in Project work. */
    static DayReport fakeDay(int back) {
        Routine base = Routine.sample(); base.alarmOn = true;
        LocalDate d = LocalDate.of(2026, 10, 19).minusDays(back);
        Plan.DayPlan p = Plan.plan(base, new Overrides(), d, Z);
        List<Ev> evs = new ArrayList<>();
        evs.add(Ev.alarms(p.startMs - 3600000L * 30, true));
        for (int i = 0; i < 18; i++) {
            String act = (i == 13) ? "S" : "C";
            evs.add(Ev.ack(p.slotStartMs(i) + 60000L, d.toString(), i, base.blocks.get(i).title, act, ""));
        }
        long bedP = p.slotStartMs(18);
        evs.add(Ev.sleep(bedP + 120 * MIN, d.toString(), bedP, 0, 0));
        long ps = p.slotStartMs(10);
        UsageDetail u = new UsageDetail(0, Long.MAX_VALUE / 4, new ArrayList<>(Arrays.asList(new long[][]{{ps + 10 * MIN, ps + 50 * MIN}, {p.slotStartMs(4), p.slotStartMs(4) + 15 * MIN}})), new LinkedHashMap<String, Long>());
        return ReportAssembler.assemble(p, evs, u, true, p.endMs + MIN);
    }
}
