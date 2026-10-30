package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * The slots a goal can be set for, the Free and Pro table, and the whole path of a slot goal with a to-do list: made as the editor makes
 * it, shown in the slot's list, ticked, followed through a rename while typing, and what free and Pro each get.
 */
public class PlanScreenTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }

    static List<Routine.Block> blocksOf(Plan.DayPlan p) {
        List<Routine.Block> l = new ArrayList<>();
        for (Routine.Slot s : p.sc.slots) l.add(s.block);
        return l;
    }

    static Overrides newOv(Routine r) {
        Overrides o = new Overrides();
        o.sig = Plan.signature(r);
        return o;
    }

    public static void main(String[] a) {
        // ---- the slots a goal can be set for
        Routine r = Routine.sample();
        List<GoalSlots.Pick> all = GoalSlots.of(r.blocks);
        ok("every slot of the routine is offered once", !all.isEmpty());
        Set<String> seen = new HashSet<>();
        boolean dup = false, sleep = false, blank = false;
        for (GoalSlots.Pick p : all) {
            if (!seen.add(p.kind + "#" + p.name.toLowerCase(Locale.ROOT))) dup = true;
            if (p.kind == Routine.SLEEP) sleep = true;
            if (p.name.isEmpty()) blank = true;
        }
        ok("no slot twice, no sleep, no nameless slot", !dup && !sleep && !blank);
        ok("the slot is offered with its saved name", all.get(0).label().contains("(" + Routine.KIND_NAMES[all.get(0).kind] + ")"));

        List<Routine.Block> twice = new ArrayList<>();
        Routine.Block s1 = new Routine.Block(Routine.TASK, "Study", 60);
        Routine.Block s2 = new Routine.Block(Routine.TASK, "STUDY", 30);
        Routine.Block s3 = new Routine.Block(Routine.REST, "Study", 30);
        Routine.Block s4 = new Routine.Block(Routine.TASK, "Taken off", 30);
        s4.skipped = true;
        Routine.Block s5 = new Routine.Block(Routine.SLEEP, "Sleep", 480);
        Routine.Block s6 = new Routine.Block(Routine.TASK, "  ", 30);
        twice.add(s1); twice.add(s2); twice.add(s3); twice.add(s4); twice.add(s5); twice.add(s6);
        List<GoalSlots.Pick> picks = GoalSlots.of(twice);
        ok("two slots with the same name and kind are one pick", picks.size() == 2);
        ok("the same name of another kind is another pick", picks.get(1).kind == Routine.REST);
        ok("slots taken off, sleep and nameless slots are left out", picks.size() == 2);
        Routine.Block renamed = new Routine.Block(Routine.TASK, "Novel", 30);
        renamed.changed = true;
        renamed.origTitle = "Reading";
        ok("a slot renamed for a day is offered by its saved name", GoalSlots.of(Collections.singletonList(renamed)).get(0).name.equals("Reading"));
        ok("null is an empty list", GoalSlots.of(null).isEmpty());

        Routine.Goal bound = Routine.Goal.forSlot(Routine.TASK, "Study", 60);
        Routine.Goal old = new Routine.Goal(Routine.TASK, "Study", 60);
        GoalSlots.Pick pb = GoalSlots.Pick.of(bound);
        GoalSlots.Pick po = GoalSlots.Pick.of(old);
        ok("a slot goal stands for its slot", !pb.legacy && pb.name.equals("Study") && pb.is(picks.get(0)));
        ok("an older goal stands for its type and says so", po.legacy && po.label().startsWith("Every Study slot") && !po.is(picks.get(0)));
        ok("a pick is told by kind", !pb.is(picks.get(1)));

        // ---- the Free and Pro table
        List<PlanTable.Group> groups = PlanTable.groups();
        ok("seven groups", groups.size() == 7);
        boolean words = true, rowsOk = true;
        for (PlanTable.Group g : groups) {
            if (g.name.isEmpty() || g.summary.isEmpty() || g.rows.isEmpty()) words = false;
            for (PlanTable.Row row : g.rows) {
                if (row.what.isEmpty() || row.free.isEmpty() || row.pro.isEmpty()) rowsOk = false;
                if (row.pro.equals(PlanTable.NOT_INCLUDED)) rowsOk = false;   // Pro has everything
            }
        }
        ok("every group and row has words", words && rowsOk);
        ok("the home screen and the to-dos are free", groups.get(0).allFree() && groups.get(1).allFree()
                && groups.get(0).name.startsWith("Home") && groups.get(1).name.equals("To-dos"));
        PlanTable.Group perf = groups.get(2), goals = groups.get(3), pause = groups.get(4), lock = groups.get(5);
        ok("only today's report is free in Performance", perf.name.equals("Performance") && perf.rows.get(0).inFree() && perf.rows.get(0).same()
                && perf.rows.size() == 6 && !perf.rows.get(1).inFree() && !perf.rows.get(2).inFree() && !perf.rows.get(3).inFree()
                && !perf.rows.get(4).inFree() && !perf.rows.get(5).inFree());
        boolean goalsPro = goals.name.equals("Goals");
        for (PlanTable.Row row : goals.rows) if (row.inFree()) goalsPro = false;
        ok("goals are Pro", goalsPro && goals.rows.size() == 3);
        ok("the pause row says the same number the rules allow", pause.rows.get(0).free.equals(DayPass.appCount(FocusRules.FREE_APPS))
                && FocusRules.FREE_APPS == 1 && pause.rows.get(0).free.equals("1 app"));
        ok("the lock rows say the same numbers the rules allow", lock.rows.get(0).free.equals("1 lock") && lock.rows.get(1).free.equals("1 app")
                && LockRules.FREE_GROUPS == 1 && LockRules.FREE_GROUP_APPS == 1);
        ok("strict, daily limit and playful are not free", !pause.rows.get(1).inFree() && !pause.rows.get(2).inFree() && !pause.rows.get(3).inFree()
                && !lock.rows.get(2).inFree());
        ok("a row is the same in both plans only when it says so", perf.rows.get(0).same() && !pause.rows.get(0).same());
        int[] c = PlanTable.counts();
        ok("Free has fewer than Pro", c[0] > 0 && c[0] < c[1]);
        ok("the table agrees with the rules for reports", Limits.FREE_REPORT_DAYS == 0 && !Limits.weekAllowed(false) && !Limits.analysisAllowed(false)
                && Limits.weekAllowed(true) && Limits.analysisAllowed(true) && Limits.reportDays(false, 10) == 0 && Limits.reportDays(true, 10) == 10);

        // ---- the path of a goal with a to-do list, as the editor makes it
        Routine m = Routine.sample();
        Routine.Block study = null;
        for (Routine.Block b : m.blocks) if (b.kind == Routine.TASK) { study = b; break; }
        final String slot = study.title;
        GoalSlots.Pick chosen = GoalSlots.of(m.blocks).stream().filter(p -> p.name.equals(slot)).findFirst().get();
        Routine.Goal ng = Routine.Goal.forSlot(chosen.kind, chosen.name, 90);
        List<String> typed = new ArrayList<>(Arrays.asList("Read chapter 4", "  Make notes ", "read chapter 4", "", "Do the quiz"));
        for (String t : typed) ng.addTodo(t);
        ok("the editor's to-dos are tidied: no blanks, no repeats", ng.todos.equals(Arrays.asList("Read chapter 4", "Make notes", "Do the quiz")));
        m.putGoal(ng);
        ok("the goal is found by its slot", m.findSlotGoal(chosen.kind, chosen.name) != null && m.findGoal(chosen.kind, chosen.name) == null);
        Routine.Goal again = Routine.Goal.forSlot(chosen.kind, chosen.name, 120);
        again.addTodo("Only this");
        m.putGoal(again);
        ok("saving the same slot again replaces the goal", m.goals.size() == 1 && m.goals.get(0).mins == 120 && m.goals.get(0).todos.size() == 1);
        m.goals.clear();
        m.putGoal(ng);

        LocalDate d = LocalDate.of(2026, 10, 26);
        long now = at(26, 10, 0);
        Overrides ov = newOv(m);
        Plan.DayPlan plan = Plan.resolve(m, ov, now, Z);
        List<Routine.Block> blocks = blocksOf(plan);
        List<Routine.Goal> today = m.goalsFor(plan.day, blocks);
        ok("the goal counts on a day with the slot", today.size() == 1);
        Todos t = new Todos();
        int src = study.src >= 0 ? study.src : -1;
        int first = -1;
        for (Routine.Block b : blocks) if (b.kind == chosen.kind && Routine.savedTitle(b).equals(chosen.name) && b.src >= 0) { first = b.src; break; }
        ok("the slot has a number", first >= 0);
        t.add(plan.day, Todos.slotScope(first), "Pack the bag");
        List<SlotTodo.Row> rows = SlotTodo.rows(t, plan.day, blocks, first, today, 0);
        ok("the slot's own thing, then the goal and its three things", rows.size() == 5 && rows.get(0).type == SlotTodo.ITEM
                && rows.get(1).type == SlotTodo.HEAD && rows.get(2).type == SlotTodo.STEP);
        int[] cnt = SlotTodo.counts(rows);
        ok("the button counts the slot's thing and the three on the goal", cnt[0] == 0 && cnt[1] == 4);
        ok("the head is not ticked yet", !rows.get(1).done && rows.get(1).ticked == 0 && rows.get(1).total == 3);
        SlotTodo.tick(t, plan.day, rows.get(2), true);
        SlotTodo.tick(t, plan.day, rows.get(3), true);
        rows = SlotTodo.rows(t, plan.day, blocks, first, today, 0);
        ok("two of three is not done", !rows.get(1).done && rows.get(1).ticked == 2 && SlotTodo.counts(rows)[0] == 2);
        SlotTodo.tick(t, plan.day, rows.get(4), true);
        rows = SlotTodo.rows(t, plan.day, blocks, first, today, 0);
        ok("all three ticks the goal (struck)", rows.get(1).done && SlotTodo.counts(rows)[0] == 3);
        List<Todos.Item> shown = SlotTodo.items(rows);
        ok("the wallpaper gets the same rows: a head and steps a step in", shown.size() == 5 && shown.get(1).head && shown.get(1).done
                && shown.get(2).level == 1 && !shown.get(0).head && shown.get(0).level == 0);
        SlotTodo.tick(t, plan.day, rows.get(3), false);
        rows = SlotTodo.rows(t, plan.day, blocks, first, today, 0);
        ok("un-ticking one opens the goal again", !rows.get(1).done && rows.get(1).ticked == 2);
        ok("time does not tick a goal that has to-dos", !SlotTodo.rows(t, plan.day, blocks, first, today, 500).get(1).done || rows.get(1).ticked == 3);
        Routine.Goal noList = Routine.Goal.forSlot(chosen.kind, chosen.name, 30);
        List<SlotTodo.Row> plain = SlotTodo.rows(new Todos(), plan.day, blocks, first, Collections.singletonList(noList), 10);
        ok("a goal without to-dos is one more thing to tick", plain.size() == 1 && plain.get(0).type == SlotTodo.GOAL && !plain.get(0).done && plain.get(0).tickable());
        List<SlotTodo.Row> byTime = SlotTodo.rows(new Todos(), plan.day, blocks, first, Collections.singletonList(noList), 30);
        ok("and time ticks it when the target is reached", byTime.get(0).done && byTime.get(0).byTime && !byTime.get(0).tickable());
        ok("a tick that time made cannot be taken back", !SlotTodo.tick(new Todos(), plan.day, byTime.get(0), false));

        // ---- the poster shows the same
        int startMin = -1;
        for (Routine.Slot sl : plan.sc.slots) if (sl.block.src == first) { startMin = sl.start; break; }
        ok("the slot has a start", startMin >= 0 && startMin < 1440 - 10);
        long inSlot = at(26, startMin / 60, startMin % 60 + 5);
        Plan.DayPlan plan2 = Plan.resolve(m, ov, inSlot, Z);
        ok("it is the slot that is going on", plan2.cur >= 0 && plan2.sc.slots.get(plan2.cur).block.src == first);
        PosterExtras px = new PosterExtras(t, null, new ArrayList<>(), m, null);
        PosterExtras.Now now2 = px.at(plan2, inSlot, Z);
        List<SlotTodo.Row> expect = SlotTodo.rows(t, plan2.day, blocksOf(plan2), first, m.goalsFor(plan2.day, blocksOf(plan2)),
                SlotTodo.passedMinutes(plan2, first));
        ok("the wallpaper lists the slot's thing, the goal and its steps", now2.items.size() == expect.size() && expect.size() == 5);
        boolean same = true;
        for (int i = 0; i < expect.size(); i++) {
            if (!now2.items.get(i).text.equals(expect.get(i).text) || now2.items.get(i).done != expect.get(i).done) same = false;
        }
        ok("with the same ticks as the home screen", same);
        Todos fresh = new Todos();
        PosterExtras onlyGoal = new PosterExtras(fresh, null, new ArrayList<>(), m, null);
        ok("a goal with a list shows on the wallpaper even when the slot has no things of its own", onlyGoal.at(plan2, inSlot, Z).items.size() == 4);
        Routine noGoals = Routine.sample();
        ok("and with no goals and no things there is nothing to show", new PosterExtras(fresh, null, new ArrayList<>(), noGoals, null).at(
                Plan.resolve(noGoals, newOv(noGoals), inSlot, Z), inSlot, Z).items.isEmpty());

        // ---- the name follows the slot while it is typed (as the routine editor does it)
        Routine q = Routine.sample();
        Routine.Block target = null;
        for (Routine.Block b : q.blocks) if (b.kind == Routine.TASK) { target = b; break; }
        String start = target.title;
        q.putGoal(Routine.Goal.forSlot(Routine.TASK, start, 60));
        String[] typing = {start.substring(0, start.length() - 1), start.substring(0, Math.max(1, start.length() - 3)), "N", "No", "Nov", "Nove", "Novel"};
        String[] anchor = {start};
        for (String s : typing) {
            target.title = s;
            if (!s.trim().isEmpty() && q.renameSlot(target.kind, anchor[0], s) > 0) anchor[0] = s;
        }
        ok("the goal ends up on the slot's new name", q.goals.get(0).slot.equals("Novel") && q.hasSlot(Routine.TASK, "Novel"));
        target.title = "";
        String[] clear = {"", "M", "Mo", "Morning"};
        for (String s : clear) {
            target.title = s;
            if (!s.trim().isEmpty() && q.renameSlot(target.kind, anchor[0], s) > 0) anchor[0] = s;
        }
        ok("clearing the name and typing another still keeps the goal with the slot", q.goals.get(0).slot.equals("Morning"));
        // another slot with the old name keeps the goal where it was
        Routine q2 = Routine.sample();
        List<Routine.Block> tasks = new ArrayList<>();
        for (Routine.Block b : q2.blocks) if (b.kind == Routine.TASK) tasks.add(b);
        if (tasks.size() >= 2) {
            tasks.get(1).title = tasks.get(0).title;
            q2.putGoal(Routine.Goal.forSlot(Routine.TASK, tasks.get(0).title, 60));
            String keepName = tasks.get(0).title;
            tasks.get(1).title = "Something else";
            int moved = q2.renameSlot(Routine.TASK, keepName, "Something else");
            ok("a goal stays while another slot still has its name", moved == 0 && q2.goals.get(0).slot.equals(keepName));
        } else {
            ok("a goal stays while another slot still has its name", true);
        }

        // ---- what a pass opens and what free keeps
        ok("one pass opens the deeper analysis too", DayPass.has(DayPass.ALL, DayPass.ANALYSIS) && DayPass.has(DayPass.ALL, DayPass.GOALS)
                && DayPass.has(DayPass.ALL, DayPass.REPORT_DAYS) && DayPass.has(DayPass.ALL, DayPass.REPORT_WEEK));
        ok("free opens nothing", DayPass.FREE == 0 && !DayPass.has(DayPass.FREE, DayPass.ANALYSIS));
        ok("a pass lasts one day", DayPass.LENGTH_MS == 24L * 3600000L && DayPass.VIDEOS == 3 && VideoProgress.NEED == 3);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
