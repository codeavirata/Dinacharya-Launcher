package app.dinacharya.wallpaper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Back to work" and "Start" on the Home card must show at once. The card draws the day in whole minutes, and a break (or a wait) that
 * the person ended in the second half of a minute used to be drawn up to the next minute, so for up to half a minute the card still said
 * "On a break" and offered "Back to work" again.
 */
public class BreakEndTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final String DAY = "2026-10-21";
    static long at(int h, int m, int s) { return ZonedDateTime.of(2026, 10, 21, h, m, s, 0, Z).toInstant().toEpochMilli(); }

    static Routine routine() { Routine r = Routine.sample(); r.alarmOn = true; return r; }
    static Plan.DayPlan live(Routine r, List<Ev> ev, long now) {
        return LivePlan.apply(Plan.resolve(r, new Overrides(), now, Z), ev, new AlarmState(), false, now);
    }
    static boolean onBreak(Plan.DayPlan p) { return p.cur >= 0 && p.sc.slots.get(p.cur).block.live == Routine.LIVE_BREAK; }
    static boolean onWait(Plan.DayPlan p) { return p.cur >= 0 && p.sc.slots.get(p.cur).block.live == Routine.LIVE_WAIT; }
    static int breakRows(Plan.DayPlan p) {
        int n = 0;
        for (Routine.Slot s : p.sc.slots) if (s.block.live == Routine.LIVE_BREAK) n++;
        return n;
    }
    static String hm(Plan.DayPlan p, int i, boolean end) {
        ZonedDateTime z = Instant.ofEpochMilli(end ? p.slotEndMs(i) : p.slotStartMs(i)).atZone(Z);
        return String.format("%02d:%02d", z.getHour(), z.getMinute());
    }
    static int total(Plan.DayPlan p) { int t = 0; for (Routine.Slot s : p.sc.slots) t += s.block.mins; return t; }

    public static void main(String[] args) {
        Routine r = routine();
        int baseTotal = total(Plan.resolve(r, new Overrides(), at(8, 0, 0), Z));
        // "Morning focus block" is slot 3, 07:15 to 09:15

        // a break of 10 minutes is going on: the card says so
        List<Ev> running = Arrays.asList(Ev.breakTaken(at(8, 0, 10), at(8, 10, 10), DAY, 3, "Morning focus block"));
        ok("a running break shows as a break", onBreak(live(r, running, at(8, 4, 0))));

        // the person ends it at 08:03:40, in the second half of a minute
        List<Ev> ended = new ArrayList<>(running);
        ended.add(Ev.breakTaken(at(8, 0, 10), at(8, 3, 40), DAY, 3, "Morning focus block"));
        Plan.DayPlan p5 = live(r, ended, at(8, 3, 45));
        ok("five seconds after Back to work (second half of a minute) the card is no longer on a break", !onBreak(p5));
        ok("the slot goes on right away", p5.cur >= 0 && p5.sc.slots.get(p5.cur).block.src == 3);
        ok("the day still adds up", total(p5) == baseTotal);
        Plan.DayPlan p30 = live(r, ended, at(8, 4, 5));
        ok("a little later it is the same", !onBreak(p30) && p30.sc.slots.get(p30.cur).block.src == 3);
        ok("the break stays in the day, ending at 08:03 or 08:04", breakRows(p30) == 1);

        // ended in the first half of a minute
        List<Ev> first = new ArrayList<>(running);
        first.add(Ev.breakTaken(at(8, 0, 10), at(8, 3, 10), DAY, 3, "Morning focus block"));
        ok("ended in the first half of a minute: not on a break", !onBreak(live(r, first, at(8, 3, 15))));

        // a very short break, ended within the minute it began in
        List<Ev> blink = new ArrayList<>();
        blink.add(Ev.breakTaken(at(8, 0, 10), at(8, 10, 10), DAY, 3, "Morning focus block"));
        blink.add(Ev.breakTaken(at(8, 0, 10), at(8, 0, 40), DAY, 3, "Morning focus block"));
        Plan.DayPlan pb = live(r, blink, at(8, 0, 45));
        ok("a break ended 30 seconds after it began leaves no break on the card", !onBreak(pb) && pb.cur >= 0 && pb.sc.slots.get(pb.cur).block.src == 3);
        ok("and the day is unchanged in length", total(pb) == baseTotal);

        // an old break that ended minutes ago is untouched
        Plan.DayPlan old = live(r, ended, at(8, 30, 0));
        int bi = -1;
        for (int i = 0; i < old.sc.slots.size(); i++) if (old.sc.slots.get(i).block.live == Routine.LIVE_BREAK) bi = i;
        ok("a break that ended long ago is drawn as before (08:00 to 08:04)", bi >= 0 && hm(old, bi, false).equals("08:00") && hm(old, bi, true).equals("08:04"));

        // a break that is still going on is untouched, even in the second half of a minute
        ok("a break still going on at 08:03:45 shows as a break", onBreak(live(r, running, at(8, 3, 45))));
        ok("it ends at 08:10", hm(live(r, running, at(8, 3, 45)), live(r, running, at(8, 3, 45)).cur, true).equals("08:10"));

        // a start that was put off and then begun with "Start" (a wait that ends now)
        List<Ev> waitEv = new ArrayList<>();
        waitEv.add(Ev.delay(at(7, 15, 20), DAY, 3, "Morning focus block", at(7, 25, 20)));
        ok("a start put off shows as a wait", onWait(live(r, waitEv, at(7, 20, 0))));
        waitEv.add(Ev.delay(at(7, 20, 40), DAY, 3, "Morning focus block", at(7, 20, 40)));
        Plan.DayPlan pw = live(r, waitEv, at(7, 20, 45));
        ok("pressing Start in the second half of a minute ends the wait on the card at once", !onWait(pw) && pw.cur >= 0 && pw.sc.slots.get(pw.cur).block.src == 3);
        ok("the day still adds up after a wait ended early", total(pw) == baseTotal);
        Plan.DayPlan pw2 = live(r, waitEv, at(7, 30, 0));
        ok("later the wait that ended at 07:21 is drawn as before", pw2.cur >= 0 && pw2.sc.slots.get(pw2.cur).block.src == 3 && !onWait(pw2));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
