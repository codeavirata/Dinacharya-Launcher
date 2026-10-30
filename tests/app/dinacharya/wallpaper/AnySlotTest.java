package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * Build 27: any slot can be finished early (during a break, after being carried on, with a minute left), goals can have a name of their
 * own and a goal can be set for tomorrow. On the sample routine on 21 October 2026.
 */
public class AnySlotTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final String DAY = "2026-10-21";
    static final LocalDate D = LocalDate.of(2026, 10, 21);
    static long at(int d, int h, int m, int s) { return ZonedDateTime.of(2026, 10, d, h, m, s, 0, Z).toInstant().toEpochMilli(); }
    static long at(int h, int m) { return at(21, h, m, 0); }
    static String hm(Plan.DayPlan p, int i, boolean end) {
        ZonedDateTime z = Instant.ofEpochMilli(end ? p.slotEndMs(i) : p.slotStartMs(i)).atZone(Z);
        return String.format("%02d:%02d", z.getHour(), z.getMinute());
    }
    static int find(Plan.DayPlan p, int src, int live) {
        for (int i = 0; i < p.sc.slots.size(); i++) { Routine.Block b = p.sc.slots.get(i).block; if (b.src == src && b.live == live) return i; }
        return -1;
    }

    public static void main(String[] args) {
        Routine r = Routine.sample();
        r.alarmOn = true;
        Plan.DayPlan base = Plan.resolve(r, new Overrides(), at(8, 30), Z);
        int cur = base.cur;
        int srcCur = base.sc.slots.get(cur).block.src;
        int srcNext = base.sc.slots.get(cur + 1).block.src;
        String titleCur = base.sc.slots.get(cur).block.title;
        long end = base.slotEndMs(cur);

        // ---- on a break: the slot can still be finished, and the break goes with it
        List<Ev> brk = new ArrayList<>(Arrays.asList(Ev.breakTaken(at(8, 20), at(8, 40), DAY, srcCur, titleCur)));
        Plan.DayPlan onBreak = LivePlan.apply(base, brk, new AlarmState(), false, at(8, 30));
        ok("the part going on is the break", onBreak.sc.slots.get(onBreak.cur).block.live == Routine.LIVE_BREAK);
        EarlyFinish.Offer ob = EarlyFinish.of(onBreak, at(8, 30));
        ok("during a break Finish early is offered for the slot the break sits in", ob != null && ob.kind == EarlyFinish.FINISH && ob.srcCur == srcCur
            && ob.srcNext == srcNext && ob.plannedEndMs == end && !ob.carried);
        brk.add(Ev.early(at(8, 30), DAY, srcNext, srcCur, titleCur, at(8, 30)));
        Plan.DayPlan afterBreak = LivePlan.apply(base, brk, new AlarmState(), false, at(21, 8, 30, 5));
        int ni = find(afterBreak, srcNext, Routine.LIVE_EARLY);
        ok("the next slot begins now and no break is left running", ni >= 0 && hm(afterBreak, ni, false).equals("08:30") && afterBreak.cur == ni);
        boolean anyBreakOpen = false;
        for (Routine.Slot sl : afterBreak.sc.slots) if (sl.block.live == Routine.LIVE_BREAK && sl.end > sl.start && sl.block.slotSrc() == srcCur
            && afterBreak.slotEndMs(afterBreak.sc.slots.indexOf(sl)) > at(8, 30)) anyBreakOpen = true;
        ok("the break was cut at the moment of finishing", !anyBreakOpen);

        // ---- a slot that was carried on past its time
        long carryUntil = end + 20 * 60000L;
        List<Ev> carry = new ArrayList<>(Arrays.asList(Ev.ack(end, DAY, srcNext, "x", "C", ""),
            Ev.extend(end, DAY, srcNext, srcCur, titleCur, carryUntil)));
        Plan.DayPlan carried = LivePlan.apply(Plan.resolve(r, new Overrides(), end + 5 * 60000L, Z), carry, new AlarmState(), false, end + 5 * 60000L);
        EarlyFinish.Offer oc = EarlyFinish.of(carried, end + 5 * 60000L);
        ok("a slot that was carried on can be finished: the next begins now", oc != null && oc.kind == EarlyFinish.FINISH && oc.carried && oc.srcCur == srcCur
            && oc.srcNext == srcNext && !oc.canWait() && oc.plannedEndMs == carried.slotEndMs(carried.cur) && oc.plannedEndMs > end);
        carry.add(Ev.extend(end + 5 * 60000L, DAY, srcNext, srcCur, titleCur, end + 5 * 60000L));
        Plan.DayPlan cut = LivePlan.apply(Plan.resolve(r, new Overrides(), end + 6 * 60000L, Z), carry, new AlarmState(), false, end + 6 * 60000L);
        int ci = find(cut, srcNext, Routine.LIVE_LATE);
        ok("finishing the carried slot starts the next one at that moment", ci >= 0 && cut.slotStartMs(ci) == end + 5 * 60000L && cut.cur == ci);

        // ---- a minute left is enough
        ok("a minute left: Finish early is offered", EarlyFinish.of(base, end - 90 * 1000L) != null);
        ok("seconds left: it is not", EarlyFinish.of(base, end - 20 * 1000L) == null);
        EarlyFinish.Offer one = EarlyFinish.of(Plan.resolve(r, new Overrides(), end - 90 * 1000L, Z), end - 90 * 1000L);
        ok("with one minute only Start now is offered, no break or wait", one != null && one.roomMin == 1 && !one.canWait());
        ok("a slot that was taken off is still not finishable", EarlyFinish.of(skippedNow(r), at(8, 30)) == null);

        // ---- goals have names of their own
        Routine g = Routine.sample();
        Routine.Goal plain = Routine.Goal.forSlot(Routine.PERSONAL, "Yoga & stretching", 30);
        ok("a goal without a name shows its slot", plain.name.isEmpty() && plain.label().equals("Yoga & stretching"));
        Routine.Goal named = Routine.Goal.forSlot(Routine.PERSONAL, "Yoga & stretching", 30);
        named.name = Routine.cleanGoalName("  Morning stretch  ");
        ok("a named goal shows its name", named.label().equals("Morning stretch") && named.bound() && named.slot.equals("Yoga & stretching"));
        ok("the name does not change what the goal counts or replaces", named.ident().equals(plain.ident()) && named.key().equals(plain.key()));
        g.goals.clear();
        g.putGoal(named);
        Routine back = Routine.parse(g.serialize());
        ok("the name survives saving", back.goals.size() == 1 && back.goals.get(0).label().equals("Morning stretch") && back.goals.get(0).slot.equals("Yoga & stretching"));
        Routine.Goal withTodo = Routine.Goal.forSlot(Routine.PERSONAL, "Breakfast", 20);
        withTodo.name = "Eat well";
        withTodo.addTodo("Fruit");
        g.goals.clear();
        g.putGoal(withTodo);
        Routine back2 = Routine.parse(g.serialize());
        ok("a name and a to-do list live together", back2.goals.get(0).label().equals("Eat well") && back2.goals.get(0).todos.size() == 1);
        Routine old = Routine.parse(Routine.sample().serialize().replace("\nN\t", "\nN\t") + "G\t" + Routine.PERSONAL + "\t30\t\t1\t\t127\t\tYoga & stretching\tFruit\n");
        ok("an older goal line without a name still reads", old.goals.size() >= 1 && old.goals.get(old.goals.size() - 1).name.isEmpty());
        ok("the name is cut to a sensible length and cleaned", Routine.cleanGoalName("a\tb\nc").equals("a b c") && Routine.cleanGoalName("x".repeat(100)).length() == Routine.Goal.MAX_NAME);
        Routine.Goal cp = named.copy();
        ok("a copy keeps the name", cp.label().equals("Morning stretch"));

        // ---- a goal for tomorrow
        Routine.Goal tomorrow = Routine.Goal.onceForSlot(Routine.PERSONAL, "Yoga & stretching", 30, D.plusDays(1));
        ok("a goal for tomorrow counts tomorrow only", tomorrow.ruleMatches(D.plusDays(1)) && !tomorrow.ruleMatches(D) && !tomorrow.ruleMatches(D.plusDays(2)));
        ok("it is called Tomorrow only in lists", tomorrow.ruleText(D).equals("Tomorrow only"));
        g.goals.clear();
        g.putGoal(tomorrow);
        ok("it is not dropped today", !g.pruneGoals(D) && g.goals.size() == 1);
        Routine back3 = Routine.parse(g.serialize());
        ok("it survives saving", back3.goals.size() == 1 && D.plusDays(1).equals(back3.goals.get(0).date));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
    }

    static Plan.DayPlan skippedNow(Routine r) {
        Overrides ov = new Overrides();
        Plan.DayPlan p = Plan.resolve(r, ov, at(8, 30), Z);
        Plan.edit(r, ov, D, p.sc.slots.get(p.cur).block.src, true, "");
        return Plan.resolve(r, ov, at(8, 30), Z);
    }
}
