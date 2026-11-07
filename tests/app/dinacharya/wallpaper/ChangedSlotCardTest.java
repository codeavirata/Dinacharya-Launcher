package app.dinacharya.wallpaper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A slot whose plan was changed in the middle ("Change plan" on the running slot) is several parts with one number: the time before, the
 * new plan, and what is left. Break and Finish on the Home card went to the first part (the time before, long over), so after a change of
 * plan neither showed on the card: the break never appeared and a finished slot went on running while the app already counted the next
 * one as begun. They go to the part that is going on now.
 * On the sample routine, 21 October 2026: slot 3 "Morning focus block" is 07:15 to 09:15, slot 4 "Short break" follows at 09:15.
 */
public class ChangedSlotCardTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final String DAY = "2026-10-21";
    static final LocalDate D = LocalDate.of(2026, 10, 21);
    static long at(int h, int m, int s) { return ZonedDateTime.of(2026, 10, 21, h, m, s, 0, Z).toInstant().toEpochMilli(); }

    static Routine routine() { Routine r = Routine.sample(); r.alarmOn = true; return r; }
    static int total(Plan.DayPlan p) { int t = 0; for (Routine.Slot s : p.sc.slots) t += s.block.mins; return t; }
    static String hm(Plan.DayPlan p, int i, boolean end) {
        ZonedDateTime z = Instant.ofEpochMilli(end ? p.slotEndMs(i) : p.slotStartMs(i)).atZone(Z);
        return String.format("%02d:%02d", z.getHour(), z.getMinute());
    }
    static boolean onBreak(Plan.DayPlan p) { return p.cur >= 0 && p.sc.slots.get(p.cur).block.live == Routine.LIVE_BREAK; }
    static int curSrc(Plan.DayPlan p) { return p.cur < 0 ? -9 : p.sc.slots.get(p.cur).block.src; }
    static String curTitle(Plan.DayPlan p) { return p.cur < 0 ? "" : p.sc.slots.get(p.cur).block.title; }

    /** The plan changed at 08:00 (45 minutes into the slot): "Reading" for the rest of the slot. */
    static Overrides changed(Routine r, int leadMin) {
        Overrides ov = new Overrides();
        Plan.editPart(r, ov, D, 3, "Reading", 0, -1, "", leadMin);
        return ov;
    }
    static Plan.DayPlan live(Routine r, Overrides ov, List<Ev> ev, long now) {
        return LivePlan.apply(Plan.resolve(r, ov, now, Z), ev, new AlarmState(), false, now);
    }

    public static void main(String[] args) {
        Routine r = routine();
        Overrides ov = changed(r, 45);
        Plan.DayPlan base = Plan.resolve(r, ov, at(8, 10, 0), Z);
        int baseTotal = total(base);
        int parts = 0;
        for (Routine.Slot s : base.sc.slots) if (s.block.src == 3) parts++;
        ok("the changed slot is two parts, 07:15 to 08:00 and 08:00 to 09:15", parts == 2);

        // ---- a break in the new plan (second part)
        for (int sec : new int[]{10, 40}) {
            long from = at(8, 20, sec);
            List<Ev> brk = Arrays.asList(Ev.breakTaken(from, from + 600000L, DAY, 3, "Morning focus block"));
            Plan.DayPlan p = live(r, ov, brk, from + 5000L);
            ok("a break begun at 08:20:" + sec + " in the changed plan shows on the card", onBreak(p));
            ok("  the day adds up (" + sec + ")", total(p) == baseTotal);
            ok("  the plan before and after it is still 'Reading' (" + sec + ")", p.cur > 0 && p.sc.slots.get(p.cur - 1).block.title.equals("Reading")
                    && p.sc.slots.get(p.cur + 1).block.title.equals("Reading"));
        }
        // back to work
        long f = at(8, 20, 10);
        List<Ev> ended = new ArrayList<>(Arrays.asList(Ev.breakTaken(f, f + 600000L, DAY, 3, "Morning focus block")));
        ended.add(Ev.breakTaken(f, at(8, 23, 40), DAY, 3, "Morning focus block"));
        Plan.DayPlan pe = live(r, ov, ended, at(8, 23, 45));
        ok("after Back to work in the changed plan the card is on 'Reading' again", !onBreak(pe) && curTitle(pe).equals("Reading") && curSrc(pe) == 3);
        // a break in the first part (before the change) is still shown, when looked at later
        long g = at(7, 30, 10);
        List<Ev> early = Arrays.asList(Ev.breakTaken(g, g + 300000L, DAY, 3, "Morning focus block"));
        Plan.DayPlan p1 = live(r, ov, early, g + 5000L);
        ok("a break in the time before the change still shows", onBreak(p1));

        // ---- Finish early in the new plan: the next slot begins at once
        for (int sec : new int[]{10, 40}) {
            long t = at(8, 30, sec);
            List<Ev> fin = Arrays.asList(Ev.early(t, DAY, 4, 3, "Reading", t));
            Plan.DayPlan p = live(r, ov, fin, t + 5000L);
            ok("finished at 08:30:" + sec + ": the next slot is the one going on", curSrc(p) == 4);
            ok("  the day adds up (" + sec + ")", total(p) == baseTotal);
            int last = -1;
            for (int i = 0; i < p.sc.slots.size(); i++) if (p.sc.slots.get(i).block.src == 3) last = i;
            ok("  the new plan ends at 08:30 and the next begins then (" + sec + ")", last >= 0 && hm(p, last, true).equals("08:30") && hm(p, p.cur, false).equals("08:30"));
            ok("  the time before the change is untouched (" + sec + ")", hm(p, last - 1, false).equals("07:15") && hm(p, last - 1, true).equals("08:00"));
        }
        // finish while on a break in the new plan
        long bs = at(8, 20, 0);
        List<Ev> onb = new ArrayList<>(Arrays.asList(Ev.breakTaken(bs, bs + 1200000L, DAY, 3, "Morning focus block")));
        long ft = at(8, 30, 40);
        onb.add(Ev.early(ft, DAY, 4, 3, "Reading", ft));
        Plan.DayPlan pb = live(r, ov, onb, ft + 5000L);
        ok("finishing during a break in the new plan: the next slot begins, no break is left running", curSrc(pb) == 4 && !onBreak(pb));

        // ---- the offer on the Home card and what it does agree: finish what is going on, the card then shows the next slot
        long now = at(8, 30, 5);
        Plan.DayPlan b2 = Plan.resolve(r, ov, now, Z);
        EarlyFinish.Offer of = EarlyFinish.of(b2, now);
        ok("the finish offer is for the slot that is going on", of != null && of.srcCur == 3 && of.srcNext == 4);
        if (of != null) {
            List<Ev> e = Arrays.asList(Ev.early(now, DAY, of.srcNext, of.srcCur, of.titleCur, now));
            Plan.DayPlan p = live(r, ov, e, now + 5000L);
            ok("doing what it offers moves the card to the next slot", curSrc(p) == of.srcNext);
        }

        // ---- carried on past its end: the last part is the one that goes on
        long end = at(9, 15, 0);
        List<Ev> carry = new ArrayList<>(Arrays.asList(Ev.ack(end, DAY, 4, "x", "C", ""), Ev.extend(end, DAY, 4, 3, "Reading", end + 20 * 60000L)));
        Plan.DayPlan pc = live(r, ov, carry, end + 5 * 60000L);
        ok("a changed slot carried on past its end goes on (the next slot begins later)", curSrc(pc) == 3);

        // ---- slots that were never split behave as before
        Overrides none = new Overrides();
        long t0 = at(8, 30, 40);
        Plan.DayPlan q = live(r, none, Arrays.asList(Ev.early(t0, DAY, 4, 3, "Morning focus block", t0)), t0 + 5000L);
        ok("a slot that was never changed is finished as before", curSrc(q) == 4);
        long b0 = at(8, 20, 40);
        ok("and its break shows as before", onBreak(live(r, none, Arrays.asList(Ev.breakTaken(b0, b0 + 600000L, DAY, 3, "Morning focus block")), b0 + 5000L)));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
