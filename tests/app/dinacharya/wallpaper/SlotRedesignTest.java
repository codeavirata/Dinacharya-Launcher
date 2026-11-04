package app.dinacharya.wallpaper;

import java.time.*;
import java.util.List;

/** Adjust time, Finish choices and a new activity: the day stays 24 hours, every limit holds. */
public class SlotRedesignTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    static int total(Plan.DayPlan p) { int s = 0; for (Routine.Slot sl : p.sc.slots) s += sl.block.mins; return s; }
    static long at(int minuteOfDay) { return DAY.atStartOfDay(Z).plusMinutes(minuteOfDay).toInstant().toEpochMilli(); }
    static int mins(Plan.DayPlan p, int i) { return p.sc.slots.get(i).end - p.sc.slots.get(i).start; }

    public static void main(String[] a) {
        Routine r = Routine.sample();
        r.mode = Routine.MODE_WAKE;
        r.anchor = 330;
        Plan.DayPlan base = Plan.plan(r, new Overrides(), DAY, Z);
        int n = base.sc.slots.size();

        // ---- the limits of each slot
        Plan.DayPlan p = Plan.resolve(r, new Overrides(), at(480), Z);   // 8:00, in "Morning focus block" (index 3, 435-555)
        ok("running slot found", p.cur == 3);
        SlotAdjust.Choices c = SlotAdjust.of(p, at(480));
        ok("choices exist", c != null && c.index == 3);
        ok("later slots are the other side", c.others.size() == n - 4 && c.others.get(0).index == 4);
        SlotAdjust.Option practice = null, tea = null, brk = null, sleep = null, lunch = null;
        for (SlotAdjust.Option o : c.others) {
            if (o.src == 7) practice = o;
            if (o.src == 6) tea = o;
            if (o.src == 4) brk = o;
            if (o.src == 18) sleep = o;
            if (o.src == 8) lunch = o;
        }
        ok("a task can give down to 30 min", practice.canGive == 60);
        ok("another slot can give down to 10 min", lunch.canGive == 50);
        ok("a 10 minute slot gives nothing", tea.canGive == 0);
        ok("a 15 minute rest gives only 5", brk.canGive == 5);
        ok("sleep can give down to 4 hours", sleep.canGive == 240);
        ok("sleep can take up to 12 hours", sleep.canTake == 240);
        ok("a task can take any amount", practice.canTake > 600);
        ok("growing is capped by the giver", c.maxGrow(practice) == 60 && c.maxGrow(tea) == 0);
        ok("shrinking is capped by the running slot's own floor and time left", c.ownCanShrink == 70 && c.maxShrink(practice) == 70);
        ok("sleep is capped by the running slot here", c.maxShrink(sleep) == 70);
        Plan.DayPlan late = Plan.resolve(r, new Overrides(), at(545), Z);   // 9:05, 10 min left
        SlotAdjust.Choices cl = SlotAdjust.of(late, at(545));
        ok("a few minutes ahead must stay", cl.ownCanShrink == 5);
        ok("nothing to adjust in the last slot", SlotAdjust.of(Plan.resolve(r, new Overrides(), at(330 + 1440 - 100), Z), at(330 + 1440 - 100)) == null);

        // ---- grow the running slot, taking from a later one
        Overrides ov = new Overrides();
        ok("move recorded", Plan.moveMinutes(r, ov, p, 3, practice.index, 30));
        Plan.DayPlan q = Plan.plan(r, ov, DAY, Z);
        ok("slot grows", mins(q, 3) == 150 && mins(q, practice.index) == 60);
        ok("day length kept", total(q) == total(base) && q.sc.slots.size() == n);
        ok("slots between shift later", q.sc.slots.get(4).start == base.sc.slots.get(4).start + 30 && q.sc.slots.get(6).start == base.sc.slots.get(6).start + 30);
        ok("giver keeps its end", q.sc.slots.get(practice.index).end == base.sc.slots.get(practice.index).end);
        ok("slots after the giver unmoved", q.sc.slots.get(8).start == base.sc.slots.get(8).start);
        ok("running slot starts where it did", q.sc.slots.get(3).start == base.sc.slots.get(3).start);
        ok("marked as adjusted", q.adjusted);

        // a second move between the same two adds up; the opposite one cancels
        Plan.DayPlan p2 = Plan.resolve(r, ov, at(480), Z);
        Plan.moveMinutes(r, ov, p2, 3, practice.index, 15);
        q = Plan.plan(r, ov, DAY, Z);
        ok("moves add up", mins(q, 3) == 165 && mins(q, practice.index) == 45 && ov.steps.size() == 1);
        p2 = Plan.resolve(r, ov, at(480), Z);
        Plan.moveMinutes(r, ov, p2, 3, practice.index, -165 + 120);
        q = Plan.plan(r, ov, DAY, Z);
        ok("moving back restores", mins(q, 3) == 120 && mins(q, practice.index) == 90 && ov.steps.isEmpty());

        // ---- shrink the running slot, giving to a later one (including sleep)
        ov = new Overrides();
        Plan.moveMinutes(r, ov, p, 3, sleep.index, -60);
        q = Plan.plan(r, ov, DAY, Z);
        ok("shrinks and sleep receives", mins(q, 3) == 60 && mins(q, sleep.index) == 540 && total(q) == total(base));
        ok("everything between moves earlier", q.sc.slots.get(4).start == base.sc.slots.get(4).start - 60 && q.sc.slots.get(17).start == base.sc.slots.get(17).start - 60);
        ok("sleep ends when it did", q.sc.slots.get(sleep.index).end == base.sc.slots.get(sleep.index).end);

        // ---- the saved text and a stale step
        Overrides back = Overrides.parse(ov.serialize());
        ok("step survives save", back.steps.size() == 1 && back.steps.get(0).type == Overrides.MOVE && back.steps.get(0).mins == -60 && back.steps.get(0).to == 18);
        Plan.DayPlan qb = Plan.plan(r, back, DAY, Z);
        ok("restored plan equals", mins(qb, 3) == 60 && mins(qb, sleep.index) == 540);
        Routine r2 = Routine.sample();
        r2.mode = Routine.MODE_WAKE;
        r2.anchor = 330;
        r2.blocks.get(18).title = "Night sleep";
        Plan.DayPlan stale = Plan.plan(r2, back, DAY, Z);
        ok("a step for a slot that was renamed is left out", mins(stale, 3) == 120);
        Overrides cleared = back.copy();
        cleared.clearEdits(DAY);
        ok("clearing the day clears its steps", cleared.steps.isEmpty());
        Overrides pr = back.copy();
        pr.prune(DAY.plusDays(10));
        ok("old steps are pruned", pr.steps.isEmpty());
        ok("a step counts as a change today", back.editCount(DAY) == 1 && !back.isEmpty());

        // ---- a slot split for today: the second part is the one that is adjusted
        ov = new Overrides();
        Plan.editPart(r, ov, DAY, 3, "Maths", 30);   // 435-465 Maths, 465-555 rest of the focus block
        Plan.DayPlan sp = Plan.resolve(r, ov, at(500), Z);
        ok("split slot: second part running", sp.cur == 4 && Plan.partOf(sp, 4) == 1 && Plan.partOf(sp, 3) == 0);
        SlotAdjust.Choices cs = SlotAdjust.of(sp, at(500));
        SlotAdjust.Option pr7 = null;
        for (SlotAdjust.Option o : cs.others) if (o.src == 7) pr7 = o;
        Plan.moveMinutes(r, ov, sp, 4, pr7.index, 20);
        Plan.DayPlan sq = Plan.plan(r, ov, DAY, Z);
        ok("only the second part grew", mins(sq, 3) == 30 && mins(sq, 4) == 110 && total(sq) == total(base));

        // ---- the Finish choices
        Plan.DayPlan f1 = Plan.resolve(r, new Overrides(), at(670 + 20), Z);   // 11:10, Practice problems with 70 left; next is lunch
        EarlyFinish.Offer o1 = EarlyFinish.of(f1, at(670 + 20));
        FinishChoices fc = FinishChoices.of(o1);
        ok("next is a meal and >30 left: all three", fc.rest && fc.startNext && fc.newActivity && !fc.confirmOnly());
        Plan.DayPlan f2 = Plan.resolve(r, new Overrides(), at(570 + 20), Z);   // Reading & notes with 70 left; next is Tea break (a rest)
        FinishChoices fr = FinishChoices.of(EarlyFinish.of(f2, at(570 + 20)));
        ok("next is a rest and >30 left: rest and start next only", fr.rest && fr.startNext && !fr.newActivity && !fr.confirmOnly());
        Plan.DayPlan f3 = Plan.resolve(r, new Overrides(), at(670 + 60), Z);   // 30 left exactly
        FinishChoices f30 = FinishChoices.of(EarlyFinish.of(f3, at(670 + 60)));
        ok("30 minutes left: only a confirm", f30.confirmOnly() && f30.startNext && !f30.rest && !f30.newActivity);
        Plan.DayPlan f4 = Plan.resolve(r, new Overrides(), at(670 + 75), Z);   // 15 left
        FinishChoices f15 = FinishChoices.of(EarlyFinish.of(f4, at(670 + 75)));
        ok("under 30 left: only a confirm", f15.confirmOnly());
        Plan.DayPlan f5 = Plan.resolve(r, new Overrides(), at(670 + 31), Z);   // 59 left
        ok("31 left of the slot is more than 30", FinishChoices.of(EarlyFinish.of(f5, at(670 + 31))).newActivity);
        Plan.DayPlan f6 = Plan.resolve(r, new Overrides(), at(1270), Z);   // "Wind down" 1260-1290, next is Sleep
        EarlyFinish.Offer o6 = EarlyFinish.of(f6, at(1270));
        ok("before sleep: its own single question", o6 != null && o6.kind == EarlyFinish.SLEEP && FinishChoices.of(o6).confirmOnly());

        // ---- limits of the new activity (Practice problems, 20 min gone, 70 left, lunch 60 min next)
        ok("from the leftover time", FinishChoices.maxFromLeft(o1) == 70);
        ok("from lunch when all of the leftover is used", FinishChoices.maxFromNext(o1, 70) == 50);
        ok("less taken from lunch when part of the leftover is unused", FinishChoices.maxFromNext(o1, 30) == 90);
        ok("longest", FinishChoices.maxLength(o1) == 120);
        ok("a task needs 30, anything else 10", FinishChoices.minLength(Routine.TASK) == 30 && FinishChoices.minLength(Routine.REST) == 10 && FinishChoices.minLength(Routine.PERSONAL) == 10);

        // ---- a new activity that begins now
        ov = new Overrides();
        Plan.DayPlan sv = Plan.resolve(r, ov, at(690), Z);
        int keep = 20;   // the slot began at 670
        ok("recorded", Plan.startActivity(r, ov, sv, sv.cur, sv.cur + 1, keep, "Mock test", Routine.TASK, "Practice", 40));
        Plan.DayPlan nq = Plan.plan(r, ov, DAY, Z);
        ok("one slot more", nq.sc.slots.size() == n + 1);
        ok("the finished slot ends now", mins(nq, 7) == 20 && nq.sc.slots.get(7).end == 690);
        ok("the new activity begins now", nq.sc.slots.get(8).block.title.equals("Mock test") && nq.sc.slots.get(8).start == 690 && mins(nq, 8) == 40);
        ok("it has its type and sub type", nq.sc.slots.get(8).block.kind == Routine.TASK && nq.sc.slots.get(8).block.sub.equals("Practice"));
        ok("lunch starts after it and keeps its end", nq.sc.slots.get(9).block.src == 8 && nq.sc.slots.get(9).start == 730 && nq.sc.slots.get(9).end == base.sc.slots.get(8).end);
        ok("lunch got the time that was left over", mins(nq, 9) == 90);
        ok("day length kept", total(nq) == total(base));
        ok("later slots unmoved", nq.sc.slots.get(10).start == base.sc.slots.get(9).start);
        Plan.DayPlan nr = Plan.plan(r, Overrides.parse(ov.serialize()), DAY, Z);
        ok("new activity survives save", nr.sc.slots.size() == n + 1 && nr.sc.slots.get(8).block.title.equals("Mock test") && mins(nr, 9) == 90);

        // an activity longer than the leftover time takes the rest from the next slot
        ov = new Overrides();
        Plan.startActivity(r, ov, sv, sv.cur, sv.cur + 1, keep, "Long session", Routine.TASK, "", 100);
        nq = Plan.plan(r, ov, DAY, Z);
        ok("longer than the leftover: next slot shortened", mins(nq, 8) == 100 && mins(nq, 9) == 30 && nq.sc.slots.get(9).end == base.sc.slots.get(8).end && total(nq) == total(base));
        // never longer than the next slot can give
        ov = new Overrides();
        Plan.startActivity(r, ov, sv, sv.cur, sv.cur + 1, keep, "Too long", Routine.TASK, "", 500);
        nq = Plan.plan(r, ov, DAY, Z);
        ok("a huge activity cannot erase the next slot", mins(nq, 9) >= 1 && total(nq) == total(base));

        // a time move after a new activity still finds its slots
        Plan.DayPlan after = Plan.resolve(r, ov = fresh(r), at(700), Z);
        ok("new activity running", after.sc.slots.get(after.cur).block.title.equals("Mock test"));
        SlotAdjust.Choices ca = SlotAdjust.of(after, at(700));
        ok("it can be adjusted like any slot", ca != null && ca.index == 8);
        Plan.moveMinutes(r, ov, after, 8, 9, 10);
        Plan.DayPlan aq = Plan.plan(r, ov, DAY, Z);
        ok("grown by taking from lunch", mins(aq, 8) == 50 && mins(aq, 9) == 80 && total(aq) == total(base));

        if (fails > 0) System.exit(1);
        System.out.println("ALL PASS");
    }

    static Overrides fresh(Routine r) {
        Overrides ov = new Overrides();
        Plan.DayPlan sv = Plan.resolve(r, ov, at(690), Z);
        Plan.startActivity(r, ov, sv, sv.cur, sv.cur + 1, 20, "Mock test", Routine.TASK, "Practice", 40);
        return ov;
    }
}
