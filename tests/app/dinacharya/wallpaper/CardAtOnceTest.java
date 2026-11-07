package app.dinacharya.wallpaper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Home card must show what a button did at once. The card draws the day in whole minutes, and what the person did in the second half
 * of a minute (start a break, finish a slot, carry one on, take the day off) used to be drawn from the next minute, so for up to half a
 * minute after the tap the card still showed the old slot. (Ending a break or a wait is in BreakEndTest.) Every case here is at 40
 * seconds past the minute, which is the case that went wrong; the same at 10 seconds is checked too, since that always worked.
 */
public class CardAtOnceTest {
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

    public static void main(String[] args) {
        Routine r = routine();
        Plan.DayPlan whole = Plan.plan(r, new Overrides(), D, Z);
        int baseTotal = total(whole);

        // ---- a break begun at 08:00:40 (slot 3, "Morning focus block", 07:15 to 09:15)
        for (int sec : new int[]{10, 40}) {
            long from = at(8, 0, sec);
            List<Ev> brk = Arrays.asList(Ev.breakTaken(from, from + 600000L, DAY, 3, "Morning focus block"));
            Plan.DayPlan p = LivePlan.apply(Plan.resolve(r, new Overrides(), from + 5000L, Z), brk, new AlarmState(), false, from + 5000L);
            ok("a break begun at 08:00:" + sec + " shows as a break five seconds later", onBreak(p));
            ok("  and the day adds up (" + sec + ")", total(p) == baseTotal);
            Plan.DayPlan later = LivePlan.apply(Plan.resolve(r, new Overrides(), from + 90000L, Z), brk, new AlarmState(), false, from + 90000L);
            ok("  and a minute and a half later (" + sec + ")", onBreak(later) && total(later) == baseTotal);
        }
        // the break is not drawn as ending earlier than it was set: it still ends at about 08:10
        long b40 = at(8, 0, 40);
        List<Ev> brk40 = Arrays.asList(Ev.breakTaken(b40, b40 + 600000L, DAY, 3, "Morning focus block"));
        Plan.DayPlan pb = LivePlan.apply(Plan.resolve(r, new Overrides(), b40 + 5000L, Z), brk40, new AlarmState(), false, b40 + 5000L);
        ok("the break begun at 08:00:40 ends at 08:10 or 08:11 on the card", hm(pb, pb.cur, true).equals("08:10") || hm(pb, pb.cur, true).equals("08:11"));
        ok("the slot goes on after the break and ends at its time", hm(pb, pb.cur + 1, true).equals("09:15"));
        // an old break is drawn as before
        Plan.DayPlan old = LivePlan.apply(Plan.resolve(r, new Overrides(), at(9, 0, 0), Z), brk40, new AlarmState(), false, at(9, 0, 0));
        int bi = -1;
        for (int i = 0; i < old.sc.slots.size(); i++) if (old.sc.slots.get(i).block.live == Routine.LIVE_BREAK) bi = i;
        ok("a break from an hour ago is drawn from 08:01 to 08:11", bi >= 0 && hm(old, bi, false).equals("08:01") && hm(old, bi, true).equals("08:11"));

        // ---- Finish early, in every slot, at 40 seconds past the minute
        int bad = 0;
        int count = whole.sc.slots.size();
        for (int i = 0; i < count - 1; i++) {
            for (int sec : new int[]{10, 40}) {
                long now = whole.slotStartMs(i) + 4 * 60000L + sec * 1000L;
                if (whole.slotEndMs(i) - now < 6 * 60000L) continue;
                Plan.DayPlan base = Plan.resolve(r, new Overrides(), now, Z);
                Plan.DayPlan nd = Plan.plan(r, new Overrides(), base.day.plusDays(1), Z);
                EarlyFinish.Offer of = EarlyFinish.of(base, nd, now);
                if (of == null) { bad++; System.out.println("  no offer in slot " + i); continue; }
                List<Ev> e = Arrays.asList(Ev.early(now, DAY, of.srcNext, of.srcCur, of.titleCur, now));
                Plan.DayPlan lv = LivePlan.apply(base, e, new AlarmState(), false, now + 5000L);
                boolean sane = lv != base && total(lv) == total(base) && lv.cur >= 0 && lv.sc.slots.get(lv.cur).block.src == of.srcNext;
                if (!sane) { bad++; System.out.println("  slot " + i + " at :" + sec + " the card did not move to the next slot at once"); }
            }
        }
        ok("every slot, finished early at :10 or :40, shows the next one beginning five seconds later", bad == 0);

        // ---- Finish early while on a break
        long bs = at(8, 20, 0);
        List<Ev> onb = new ArrayList<>(Arrays.asList(Ev.breakTaken(bs, bs + 20 * 60000L, DAY, 3, "Morning focus block")));
        Plan.DayPlan baseB = Plan.resolve(r, new Overrides(), at(8, 30, 40), Z);
        EarlyFinish.Offer ob = EarlyFinish.of(LivePlan.apply(baseB, onb, new AlarmState(), false, at(8, 30, 40)), at(8, 30, 40));
        if (ob != null) {
            onb.add(Ev.early(at(8, 30, 40), DAY, ob.srcNext, ob.srcCur, ob.titleCur, at(8, 30, 40)));
            Plan.DayPlan af = LivePlan.apply(baseB, onb, new AlarmState(), false, at(8, 30, 45));
            ok("finishing during a break at 08:30:40 starts the next slot at once", af.cur >= 0 && af.sc.slots.get(af.cur).block.src == ob.srcNext && !onBreak(af));
        } else {
            ok("an offer exists during a break", false);
        }

        // ---- the day taken off from 08:00:40
        AlarmState off = new AlarmState();
        off.offDay = DAY;
        off.offFrom = at(8, 0, 40);
        Plan.DayPlan dp = LivePlan.apply(Plan.resolve(r, new Overrides(), at(8, 0, 45), Z), new ArrayList<Ev>(), off, true, at(8, 0, 45));
        ok("a day off from 08:00:40 shows as the day off five seconds later", dp.cur >= 0 && dp.sc.slots.get(dp.cur).block.live == Routine.LIVE_OFF);
        AlarmState off10 = new AlarmState();
        off10.offDay = DAY;
        off10.offFrom = at(8, 0, 10);
        Plan.DayPlan dp10 = LivePlan.apply(Plan.resolve(r, new Overrides(), at(8, 0, 15), Z), new ArrayList<Ev>(), off10, true, at(8, 0, 15));
        ok("and from 08:00:10 as before", dp10.cur >= 0 && dp10.sc.slots.get(dp10.cur).block.live == Routine.LIVE_OFF);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
