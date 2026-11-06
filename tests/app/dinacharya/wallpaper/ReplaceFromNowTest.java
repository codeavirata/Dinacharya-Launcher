package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * Build 78: a plan changed in the middle of a slot begins when it was changed. The time before (a delayed start, an alarm nobody answered)
 * stays out of it and keeps showing as such. The sample routine on 21 October 2026, slot "Reading and notes", 09:30 to 11:00 (90 min).
 */
public class ReplaceFromNowTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static void is(String n, Object got, Object want) {
        boolean v = Objects.equals(got, want);
        System.out.println((v ? "PASS " : "FAIL ") + n + (v ? "" : "\n     got:  " + got + "\n     want: " + want));
        if (!v) fails++;
    }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final String DAY = "2026-10-21";
    static final LocalDate D = LocalDate.of(2026, 10, 21);
    static final int SRC = 5;
    static final String TITLE = "Reading & notes";
    static long at(int h, int m) { return ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static long at(int h, int m, int s) { return ZonedDateTime.of(2026, 10, 21, h, m, s, 0, Z).toInstant().toEpochMilli(); }
    static String hm(long ms) { ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(Z); return String.format("%02d:%02d", z.getHour(), z.getMinute()); }

    /** The parts of the day between 09:15 and 11:00 as "09:30-09:50 No response (20)". */
    static String rows(Plan.DayPlan p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.sc.slots.size(); i++) {
            long s = p.slotStartMs(i), e = p.slotEndMs(i);
            if (e <= at(9, 15) || s >= at(11, 0)) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(hm(s)).append('-').append(hm(e)).append(' ').append(p.sc.slots.get(i).block.title);
        }
        return sb.toString();
    }

    /** What happens on the day when the person changes the plan at {@code now} and asks for {@code asked} minutes (0: all that is left). */
    static Plan.DayPlan changed(Routine r, long now, int asked, List<Ev> events) {
        Overrides ov = new Overrides();
        long[] span = ReplaceFromNow.span(Plan.plan(r, ov, D, Z), SRC);
        ReplaceFromNow when = ReplaceFromNow.of(span[0], span[1], now);
        ReplaceFromNow.Placed pl = when.place(asked);
        Plan.editPart(r, ov, D, SRC, "Gym", pl.stored, -1, "", when.lead);
        Plan.DayPlan base = Plan.resolve(r, ov, now + 5 * 60000L, Z);
        return LivePlan.apply(base, events, new AlarmState(), false, now + 5 * 60000L);
    }

    public static void main(String[] args) {
        long s = at(9, 30), e = at(11, 0);

        // ---- the rules on their own
        ReplaceFromNow w = ReplaceFromNow.of(s, e, at(9, 50));
        ok("20 of the 90 minutes had gone by: 70 are left", w.slotMins == 90 && w.lead == 20 && w.room == 70 && w.late());
        ok("the new plan begins now", w.startMs == at(9, 50));
        ok("30 minutes asked: 30 stored, from now to 10:20, the rest goes back", w.stored(30) == 30 && w.toMs(30) == at(10, 20) && w.restBack(30));
        ok("all that is left: stored as 70, to the end of the slot, nothing goes back", w.stored(0) == 70 && w.toMs(0) == e && !w.restBack(0));
        ok("more than is left is cut to what is left", w.stored(100) == 70 && w.minutes(100) == 70);
        ReplaceFromNow early = ReplaceFromNow.of(s, e, at(9, 0));
        ok("before the slot begins nothing is left out", early.lead == 0 && !early.late() && early.startMs == s);
        ok("and the length is stored as it always was (0 for the whole slot)", early.stored(0) == 0 && early.stored(90) == 0 && early.stored(30) == 30);
        ReplaceFromNow quick = ReplaceFromNow.of(s, e, at(9, 30, 20));
        ok("in the first half minute of the slot it is still from the start", quick.lead == 0 && quick.stored(30) == 30);
        ReplaceFromNow rounded = ReplaceFromNow.of(s, e, at(9, 50, 40));
        ok("whole minutes: 20 minutes 40 seconds in is minute 20, so the new plan never begins after the moment it was made",
            rounded.lead == 20 && rounded.startMs == at(9, 50) && rounded.startMs <= at(9, 50, 40));
        ReplaceFromNow last = ReplaceFromNow.of(s, e, at(10, 59, 30));
        ok("in the last minute one minute is left, never none", last.lead == 89 && last.room == 1 && last.stored(30) == 1 && last.stored(0) == 1);
        ReplaceFromNow night = ReplaceFromNow.of(at(21, 30), at(21, 30) + 8 * 3600000L, at(23, 0));
        ok("a slot across midnight counts the same way (sleep 21:30 to 05:30, changed at 23:00)", night.lead == 90 && night.room == 390 && night.stored(0) == 390);
        ok("a slot with no end after its start still has a minute", ReplaceFromNow.of(s, s, s).slotMins == 1);

        // ---- the day as the person sees it: Reading and notes, 09:30 to 11:00, the alarm rang at 09:30
        Routine r = Routine.sample();
        r.alarmOn = true;
        Plan.DayPlan plain = Plan.plan(r, new Overrides(), D, Z);
        long[] span = ReplaceFromNow.span(plain, SRC);
        ok("the slot's planned span is found", span != null && span[0] == s && span[1] == e);

        // 1. the alarm went unanswered, then "Gym for 30 minutes" at 09:50: the missed time keeps its name after the answer
        List<Ev> rang = new ArrayList<>(Arrays.asList(Ev.rang(s, DAY, SRC, TITLE), Ev.ack(at(9, 50), DAY, SRC, TITLE, "R", "Gym")));
        is("the alarm rang at 09:30 and was answered at 09:50: the 20 missed minutes stay out of Gym, which gets its 30",
            rows(changed(r, at(9, 50), 30, rang)),
            "09:15-09:30 Short break | 09:30-09:50 No response | 09:50-10:20 Gym | 10:20-11:00 Reading & notes");

        // 2. nobody answered the alarm and the change is made from Home at 09:55: "No response" until then, Gym from then
        List<Ev> unanswered = new ArrayList<>(Arrays.asList(Ev.rang(s, DAY, SRC, TITLE), Ev.ack(at(9, 55), DAY, SRC, TITLE, "R", "Gym")));
        is("no answer until the change at 09:55: No response until 09:55, Gym 09:55 to 10:25",
            rows(changed(r, at(9, 55), 30, unanswered)),
            "09:15-09:30 Short break | 09:30-09:55 No response | 09:55-10:25 Gym | 10:25-11:00 Reading & notes");

        // 3. the person started the slot on time and worked at it, then changes the rest at 09:50: that time was worked, not rewritten
        List<Ev> worked = new ArrayList<>(Arrays.asList(Ev.rang(s, DAY, SRC, TITLE), Ev.ack(at(9, 31), DAY, SRC, TITLE, "C", ""),
            Ev.ack(at(9, 50), DAY, SRC, TITLE, "R", "Gym")));
        is("started on time and changed at 09:50: the first 20 minutes stay the slot's own",
            rows(changed(r, at(9, 50), 30, worked)),
            "09:15-09:30 Short break | 09:30-09:50 Reading & notes | 09:50-10:20 Gym | 10:20-11:00 Reading & notes");

        // 4. all that is left of the slot
        is("all that is left, after a late answer: Gym takes 09:50 to 11:00 and nothing goes back",
            rows(changed(r, at(9, 50), 0, rang)),
            "09:15-09:30 Short break | 09:30-09:50 No response | 09:50-11:00 Gym");

        // 5. a start that was put off ("Start later"), then the plan is changed at 09:50: the wait ends then, it is not wiped out
        List<Ev> waited = new ArrayList<>(Arrays.asList(Ev.rang(s, DAY, SRC, TITLE), Ev.ack(s, DAY, SRC, TITLE, "C", ""),
            Ev.delay(s, DAY, SRC, TITLE, at(9, 40)), Ev.ack(at(9, 50), DAY, SRC, TITLE, "R", "Gym"), Ev.delay(at(9, 50), DAY, SRC, TITLE, at(9, 50))));
        is("a put-off start ended by the change at 09:50: the wait shows as Delayed start until then",
            rows(changed(r, at(9, 50), 30, waited)),
            "09:15-09:30 Short break | 09:30-09:50 Delayed start | 09:50-10:20 Gym | 10:20-11:00 Reading & notes");

        // 6. no alarm at all (alarms off): nothing says the time before was lost, so it stays the slot's own
        is("no alarm was rung: the minutes before the change stay the slot's own",
            rows(changed(r, at(9, 55), 30, new ArrayList<Ev>(Arrays.asList(Ev.ack(at(9, 55), DAY, SRC, TITLE, "R", "Gym"))))),
            "09:15-09:30 Short break | 09:30-09:55 Reading & notes | 09:55-10:25 Gym | 10:25-11:00 Reading & notes");

        // 7. planned ahead, before the slot begins: as it always was
        Overrides ahead = new Overrides();
        ReplaceFromNow.Placed pa = ReplaceFromNow.of(s, e, at(8, 0)).place(30);
        Plan.editPart(r, ahead, D, SRC, "Gym", pa.stored, -1, "", ReplaceFromNow.of(s, e, at(8, 0)).lead);
        is("changed before the slot begins: Gym is the first 30 minutes, 09:30 to 10:00",
            rows(Plan.plan(r, ahead, D, Z)),
            "09:15-09:30 Short break | 09:30-10:00 Gym | 10:00-11:00 Reading & notes");

        // 8. nothing is lost or made up: the parts of the slot add up to its 90 minutes
        Plan.DayPlan p1 = changed(r, at(9, 50), 30, rang);
        int total = 0, gym = 0, delay = 0, readingAfter = 0;
        for (int i = 0; i < p1.sc.slots.size(); i++) {
            Routine.Block b = p1.sc.slots.get(i).block;
            long a = p1.slotStartMs(i), z = p1.slotEndMs(i);
            if (z <= s || a >= e) continue;
            int m = (int) ((z - a) / 60000L);
            total += m;
            if (b.title.equals("Gym")) gym += m;
            if (b.live == Routine.LIVE_WAIT) delay += m;
            if (b.title.equals(TITLE)) readingAfter += m;
        }
        ok("20 missed + 30 of Gym + 40 of the slot = 90", total == 90 && delay == 20 && gym == 30 && readingAfter == 40);

        // 10. the Home card at the moment of the change and after it: the slot that is going on is Gym, and the part of the saved plan that
        //     goes with it is Gym too (the first part of the slot is the time before and still has the old name; finding the slot by its
        //     number alone showed "Reading & notes" on the card right after "Gym" was chosen)
        for (long[] c : new long[][]{{at(9, 50), at(9, 50)}, {at(9, 50, 40), at(9, 50, 41)}, {at(9, 50), at(10, 5)}}) {
            Overrides hov = new Overrides();
            ReplaceFromNow hw = ReplaceFromNow.of(s, e, c[0]);
            Plan.editPart(r, hov, D, SRC, "Gym", hw.place(30).stored, -1, "", hw.lead);
            Plan.DayPlan hs = Plan.resolve(r, hov, c[1], Z);
            List<Ev> hev = new ArrayList<>(Arrays.asList(Ev.rang(s, DAY, SRC, TITLE), Ev.ack(c[0], DAY, SRC, TITLE, "R", "Gym")));
            Plan.DayPlan hl = LivePlan.apply(hs, hev, new AlarmState(), false, c[1]);
            String when = hm(c[0]) + (c[0] % 60000L != 0 ? "+" : "") + " seen at " + hm(c[1]);
            ok("Home " + when + ": the slot going on is Gym", hl.cur >= 0 && "Gym".equals(hl.sc.slots.get(hl.cur).block.title));
            int oi = SlotPlay.indexOfPart(hs, SRC, hs.tt);
            ok("Home " + when + ": its part of the saved plan is Gym (10:05: the rest, Reading & notes)", oi >= 0
                && hs.sc.slots.get(oi).block.title.equals(c[1] < at(10, 20) ? "Gym" : TITLE));
            ok("Home " + when + ": that part holds the minute", oi >= 0 && hs.tt >= hs.sc.slots.get(oi).start && hs.tt < hs.sc.slots.get(oi).end);
        }
        Overrides hov2 = new Overrides();
        Plan.editPart(r, hov2, D, SRC, "Gym", 30, -1, "", 20);
        Plan.DayPlan hs2 = Plan.resolve(r, hov2, at(10, 30), Z);
        ok("the part of the rest of the slot, 10:20 to 11:00, is found by the minute at 10:30",
            hs2.sc.slots.get(SlotPlay.indexOfPart(hs2, SRC, hs2.tt)).start == 10 * 60 + 20
            && hs2.sc.slots.get(SlotPlay.indexOfPart(hs2, SRC, hs2.tt)).end == 11 * 60);
        ok("the number alone finds the first part, the time before (that was the bug)",
            hs2.sc.slots.get(SlotPlay.indexOfSrc(hs2, SRC)).start == 9 * 60 + 30);
        ok("a minute no part holds (carried on past the end) finds the last part before it",
            hs2.sc.slots.get(SlotPlay.indexOfPart(hs2, SRC, 11 * 60 + 10)).end == 11 * 60);
        ok("a slot number that is not there finds nothing", SlotPlay.indexOfPart(hs2, 99, hs2.tt) == -1);

        // 9. the report counts the same way: the 20 minutes are No response, Gym has its 30 active minutes, the rest of the slot goes on
        long reportNow = at(10, 30);
        Plan.DayPlan rp = Plan.resolve(r, overridesFor(at(9, 50), 30), reportNow, Z);
        DayReport rep = ReportAssembler.assemble(rp, rang, null, true, reportNow, null);
        long noResp = 0, gymActive = 0, otherActive = 0, active = 0;
        for (DayReport.Row row : rep.rows) {
            if (row.endMs <= s || row.startMs >= e) continue;
            noResp += row.noRespMs;
            active += row.activeMs;
            if ("Gym".equals(row.newTitle)) gymActive += row.activeMs; else otherActive += row.activeMs;
        }
        ok("the report: 20 minutes of No response, none of it in Gym", noResp == 20 * 60000L);
        ok("the report: Gym has its 30 minutes", gymActive == 30 * 60000L);
        ok("the report: with 10 minutes of the rest gone by 10:30, 40 minutes of the slot were active", otherActive == 10 * 60000L && active == 40 * 60000L);

        // ---- the saved change keeps its offset
        Overrides ov = overridesFor(at(9, 50), 30);
        Overrides back = Overrides.parse(ov.serialize());
        Overrides.Edit be = back.edits.get(0);
        ok("the offset and length survive saving and reading", be.from == 20 && be.mins == 30 && be.name.equals("Gym") && be.src == SRC);
        ok("a change with no offset is saved as it always was (no extra column)", !Overrides.parse(planAhead().serialize()).edits.isEmpty()
            && Overrides.parse(planAhead().serialize()).edits.get(0).from == 0 && !planAhead().serialize().contains("\t0\t-1\t-1\t\t20"));
        String old = "E\t" + DAY + "\t" + SRC + "\t0\t" + TITLE + "\tGym\t30\t-1\t-1\t";
        Overrides oldOne = Overrides.parse("DINACHARYA-OVERRIDES-1\n".replace("DINACHARYA-OVERRIDES-1", magic()) + old + "\n");
        ok("an edit saved by an older build (no offset) is read with none", oldOne.edits.size() == 1 && oldOne.edits.get(0).from == 0 && oldOne.edits.get(0).mins == 30);

        // ---- the wake-up slot keeps its alarm in the part before the change
        Overrides wk = new Overrides();
        Plan.editPart(r, wk, D, 0, "Stretch", 10, -1, "", 10);
        Plan.DayPlan wp = Plan.plan(r, wk, D, Z);
        int firstWake = -1, wakeCount = 0;
        for (int i = 0; i < wp.sc.slots.size(); i++) {
            Routine.Block b = wp.sc.slots.get(i).block;
            if (b.src != 0) continue;
            if (b.wake) { wakeCount++; if (firstWake < 0) firstWake = i; }
        }
        boolean base0 = plain.sc.slots.get(0).block.wake;
        ok("a changed wake-up slot still has one wake-up alarm, in the part that starts the day", wakeCount == (base0 ? 1 : 0) && (!base0 || firstWake == 0));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static Overrides planAhead() {
        Routine r = Routine.sample();
        Overrides ov = new Overrides();
        Plan.editPart(r, ov, D, SRC, "Gym", 30, -1, "", 0);
        return ov;
    }

    static Overrides overridesFor(long now, int asked) {
        Routine r = Routine.sample();
        Overrides ov = new Overrides();
        ReplaceFromNow when = ReplaceFromNow.of(at(9, 30), at(11, 0), now);
        Plan.editPart(r, ov, D, SRC, "Gym", when.stored(asked), -1, "", when.lead);
        return ov;
    }

    static String magic() {
        String text = new Overrides().serialize();
        return text.split("\n")[0];
    }
}
