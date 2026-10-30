package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * Goals that belong to a slot of the routine, with a to-do list of their own: how they are kept and found, how the slot's list and the
 * goals' lists become one list that stays in step, how the reports and streaks follow the slot, and that older goals work as before.
 */
public class SlotGoalTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static LocalDate day(int d) { return LocalDate.of(2026, 10, d); }

    static List<Routine.Block> blocksOf(Plan.DayPlan p) {
        List<Routine.Block> l = new ArrayList<>();
        for (Routine.Slot s : p.sc.slots) l.add(s.block);
        return l;
    }

    static Routine.Block blockNamed(Routine r, String title) {
        for (Routine.Block b : r.blocks) if (b.title.equals(title)) return b;
        return null;
    }

    static int srcOf(Plan.DayPlan p, String title) {
        for (Routine.Slot s : p.sc.slots) if (s.block.title.equals(title)) return s.block.src;
        return -1;
    }

    public static void main(String[] a) {
        // ---- a goal that belongs to a slot
        Routine.Goal g = Routine.Goal.forSlot(Routine.TASK, "  Reading & notes ", 60);
        ok("a slot goal knows its slot", g.bound() && g.slot.equals("Reading & notes") && g.sub.isEmpty() && g.kind == Routine.TASK);
        ok("its name is the slot", g.label().equals("Reading & notes"));
        ok("an older goal is not bound and keeps its label", !new Routine.Goal(Routine.TASK, "Study", 60).bound()
                && new Routine.Goal(Routine.TASK, "Study", 60).label().equals("Study"));
        ok("the slot and the type are told apart", !g.ident().equals(new Routine.Goal(Routine.TASK, "Reading & notes", 60).ident()));
        ok("a slot goal is told by kind and name, whatever the case",
                g.ident().equals(Routine.Goal.forSlot(Routine.TASK, "READING & notes", 10).ident())
                        && !g.ident().equals(Routine.Goal.forSlot(Routine.REST, "Reading & notes", 10).ident()));
        ok("minutes stay in range", Routine.Goal.forSlot(Routine.TASK, "x", 1).mins == 5 && Routine.Goal.forSlot(Routine.TASK, "x", 99999).mins == 1440);

        Routine r = Routine.sample();
        List<Routine.Block> blocks = new ArrayList<>(r.blocks);
        ok("it counts the slot", g.presentIn(blocks) && g.covers(blockNamed(r, "Reading & notes")));
        ok("and not another slot of the same type", !g.covers(blockNamed(r, "Morning focus block")));
        Routine.Block rn = blockNamed(r, "Reading & notes").copy();
        rn.skipped = true;
        ok("a slot taken off for the day is not counted", !g.covers(rn));
        Routine.Block renamed = blockNamed(r, "Reading & notes").copy();
        renamed.title = "Novel";
        renamed.origTitle = "Reading & notes";
        renamed.changed = true;
        ok("a slot renamed for one day is still the same slot", g.covers(renamed));
        Routine.Block wrongKind = blockNamed(r, "Reading & notes").copy();
        wrongKind.kind = Routine.REST;
        ok("the kind has to match", !g.covers(wrongKind));
        ok("an older goal still counts by type", new Routine.Goal(Routine.TASK, "Study", 60).covers(blockNamed(r, "Morning focus block"))
                && !new Routine.Goal(Routine.TASK, "Study", 60).covers(blockNamed(r, "Project work")));

        // ---- to-dos of a goal
        ok("a goal's to-do list starts empty", g.todos.isEmpty());
        ok("a thing is added", g.addTodo("  Read chapter 4 ") && g.todos.get(0).equals("Read chapter 4"));
        ok("the same thing twice is not added", !g.addTodo("read CHAPTER 4") && g.todos.size() == 1);
        ok("blank is not added", !g.addTodo("   ") && !g.addTodo(null));
        ok("a bar in the text cannot break the file", g.addTodo("a|b") && g.todos.get(1).equals("a/b"));
        Routine.Goal full = Routine.Goal.forSlot(Routine.TASK, "x", 30);
        for (int i = 0; i < Routine.Goal.MAX_TODOS + 4; i++) full.addTodo("thing " + i);
        ok("the list holds at most the limit", full.todos.size() == Routine.Goal.MAX_TODOS);
        ok("long text is cut", Routine.Goal.forSlot(Routine.TASK, "x", 30).addTodo("y".repeat(300)));
        Routine.Goal copy = g.copy();
        copy.addTodo("only in the copy");
        ok("a copy keeps the slot and the list and is its own", copy.slot.equals(g.slot) && copy.todos.size() == g.todos.size() + 1 && g.todos.size() == 2);

        // ---- keeping goals in the routine
        Routine k = Routine.sample();
        Routine.Goal sg = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60);
        sg.addTodo("Read chapter 4");
        sg.addTodo("Make notes");
        k.putGoal(sg);
        Routine.Goal wk = Routine.Goal.forSlot(Routine.TASK, "Project work", 45);
        wk.repeat = Routine.Goal.WEEKDAYS;
        wk.daysMask = 0x1F;
        wk.with.add(new Routine.Sub(Routine.TASK, "Study"));
        k.putGoal(wk);
        k.putGoal(new Routine.Goal(Routine.TASK, "Study", 120));   // an older goal beside them
        Routine back = Routine.parse(k.serialize());
        ok("slot goals come back", back.goals.size() == 3 && back.findSlotGoal(Routine.TASK, "reading & NOTES") != null);
        Routine.Goal rb = back.findSlotGoal(Routine.TASK, "Reading & notes");
        ok("with their list", rb.mins == 60 && rb.todos.size() == 2 && rb.todos.get(0).equals("Read chapter 4") && rb.todos.get(1).equals("Make notes"));
        Routine.Goal wb = back.findSlotGoal(Routine.TASK, "Project work");
        ok("and their rule and conditions", wb.repeat == Routine.Goal.WEEKDAYS && wb.daysMask == 0x1F && wb.with.size() == 1 && wb.todos.isEmpty());
        ok("an older goal is still an older goal", back.findGoal(Routine.TASK, "Study") != null && !back.findGoal(Routine.TASK, "Study").bound());
        ok("the older goal lookup does not find a slot goal", back.findGoal(Routine.TASK, "Reading & notes") == null);
        ok("saving again gives the same text", back.serialize().equals(k.serialize()));
        String old = "DINACHARYA1\nB\t0\t60\tReading\nG\t0\t90\tStudy\nG\t0\t30\tExercise\t3\t2026-10-26\t127\t\n";
        Routine oldR = Routine.parse(old);
        ok("goals saved by an earlier version still read", oldR.goals.size() == 2 && !oldR.goals.get(0).bound() && oldR.goals.get(0).todos.isEmpty()
                && oldR.goals.get(1).repeat == Routine.Goal.ONCE);
        ok("a damaged slot goal line is skipped", Routine.parse("DINACHARYA1\nB\t0\t60\tReading\nG\tx\ty\tz\t\t\t\nG\t0\t60\t\t0\t\t127\t\tSlot\tA|B\n").goals.size() == 1);
        Routine one = new Routine();
        one.blocks.add(new Routine.Block(Routine.TASK, "Revision", 60));
        one.putGoal(Routine.Goal.onceForSlot(Routine.TASK, "Revision", 30, day(26)));
        one.putGoal(Routine.Goal.onceForSlot(Routine.TASK, "Revision", 45, day(27)));
        Routine oneBack = Routine.parse(one.serialize());
        ok("one-day goals of a slot come back, one per day", oneBack.goals.size() == 2 && oneBack.findSlotOnce(Routine.TASK, "Revision", day(27)).mins == 45);

        // ---- replacing and finding
        Routine p = new Routine();
        p.putGoal(Routine.Goal.forSlot(Routine.TASK, "Revision", 30));
        p.putGoal(Routine.Goal.forSlot(Routine.TASK, "revision", 50));
        ok("a second goal for the same slot replaces the first", p.goals.size() == 1 && p.goals.get(0).mins == 50);
        p.putGoal(Routine.Goal.forSlot(Routine.REST, "Revision", 20));
        ok("another kind with the same name is another slot", p.goals.size() == 2);
        p.putGoal(Routine.Goal.onceForSlot(Routine.TASK, "Revision", 10, day(26)));
        ok("a one-day goal sits beside the repeating one", p.goals.size() == 3);
        p.putGoal(Routine.Goal.onceForSlot(Routine.TASK, "Revision", 15, day(26)));
        ok("and is replaced for the same day", p.goals.size() == 3 && p.findSlotOnce(Routine.TASK, "Revision", day(26)).mins == 15);
        ok("an older type goal is separate from the slot goal", p.findGoal(Routine.TASK, "Revision") == null);
        p.putGoal(new Routine.Goal(Routine.TASK, "Revision", 60));
        ok("an older goal for the type is kept beside it", p.goals.size() == 4 && p.findGoal(Routine.TASK, "Revision").mins == 60);
        ok("setGoal only touches the older goal", p.findSlotGoal(Routine.TASK, "Revision").mins == 50);
        p.setGoal(Routine.TASK, "Revision", 0);
        ok("removing the older goal leaves the slot goals", p.goals.size() == 3 && p.findSlotGoal(Routine.TASK, "Revision") != null);
        ok("has a goal for the type counts a slot of that type", k.hasGoalFor(Routine.TASK, "Study") && k.hasGoalFor(Routine.TASK, "Practice") == false);
        Routine h = Routine.sample();
        h.putGoal(Routine.Goal.forSlot(Routine.TASK, "Practice problems", 40));
        ok("a slot goal makes its slot's type count as having a goal", h.hasGoalFor(Routine.TASK, "Practice"));

        // ---- which goals count on a day
        Routine d = Routine.sample();
        Routine.Goal read = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60);
        Routine.Goal proj = Routine.Goal.forSlot(Routine.TASK, "Project work", 45);
        proj.repeat = Routine.Goal.WEEKDAYS;
        proj.daysMask = 1;   // Mondays only
        Routine.Goal gone = Routine.Goal.forSlot(Routine.TASK, "A slot that is not there", 30);
        d.putGoal(read);
        d.putGoal(proj);
        d.putGoal(gone);
        List<Routine.Block> mon = new ArrayList<>(d.blocks);
        List<Routine.Goal> mondayGoals = d.goalsFor(day(26), mon);   // 2026-10-26 is a Monday
        ok("on a day its rule and slot fit, a goal counts", mondayGoals.contains(read) && mondayGoals.contains(proj));
        ok("a goal for a slot that is not in the routine does not count", !mondayGoals.contains(gone));
        ok("on another weekday the rule leaves it out", !d.goalsFor(day(27), mon).contains(proj) && d.goalsFor(day(27), mon).contains(read));
        List<Routine.Block> noRead = new ArrayList<>();
        for (Routine.Block b : d.blocks) if (!b.title.equals("Reading & notes")) noRead.add(b);
        ok("on a day without the slot, it does not count", !d.goalsFor(day(26), noRead).contains(read));
        List<Routine.Block> skippedRead = new ArrayList<>();
        for (Routine.Block b : d.blocks) {
            Routine.Block c = b.copy();
            if (b.title.equals("Reading & notes")) c.skipped = true;
            skippedRead.add(c);
        }
        ok("on a day the slot is taken off, it does not count", !d.goalsFor(day(26), skippedRead).contains(read));
        Routine.Goal readOnce = Routine.Goal.onceForSlot(Routine.TASK, "Reading & notes", 20, day(26));
        d.putGoal(readOnce);
        List<Routine.Goal> withOnce = d.goalsFor(day(26), mon);
        ok("a one-day goal replaces the repeating goal of its slot for that day", withOnce.contains(readOnce) && !withOnce.contains(read));
        ok("and only for that day", d.goalsFor(day(27), mon).contains(read) && !d.goalsFor(day(27), mon).contains(readOnce));
        d.putGoal(new Routine.Goal(Routine.TASK, "Study", 100));
        ok("an older type goal counts beside slot goals", d.goalsFor(day(27), mon).stream().anyMatch(x -> !x.bound() && x.sub.equals("Study")));

        // ---- renaming and removing slots and types
        Routine rr = Routine.sample();
        Routine.Goal rg = Routine.Goal.forSlot(Routine.TASK, "Revision", 30);
        rg.addTodo("Past papers");
        rr.putGoal(rg);
        blockNamed(rr, "Revision").title = "Exam revision";
        ok("a renamed slot takes its goal along", rr.renameSlot(Routine.TASK, "Revision", "Exam revision") == 1
                && rr.findSlotGoal(Routine.TASK, "Exam revision") != null && rr.findSlotGoal(Routine.TASK, "Revision") == null);
        ok("with its list", rr.findSlotGoal(Routine.TASK, "Exam revision").todos.size() == 1);
        Routine two = Routine.sample();
        two.putGoal(Routine.Goal.forSlot(Routine.TASK, "Revision", 30));
        two.blocks.add(new Routine.Block(Routine.TASK, "Revision", 20));   // another slot with the same name stays
        blockNamed(two, "Revision").title = "Revision";
        for (Routine.Block b : two.blocks) if (b.title.equals("Revision")) { b.title = "Exam revision"; break; }
        ok("while another slot has the old name, the goal stays", two.renameSlot(Routine.TASK, "Revision", "Exam revision") == 0
                && two.findSlotGoal(Routine.TASK, "Revision") != null);
        Routine three = Routine.sample();
        three.putGoal(Routine.Goal.forSlot(Routine.TASK, "Revision", 30));
        three.putGoal(Routine.Goal.forSlot(Routine.TASK, "Exam revision", 45));
        blockNamed(three, "Revision").title = "Exam revision";
        ok("a goal for the new name is not overwritten", three.renameSlot(Routine.TASK, "Revision", "Exam revision") == 0
                && three.goals.size() == 2 && three.findSlotGoal(Routine.TASK, "Exam revision").mins == 45);
        ok("renaming to the same name or nothing does nothing", three.renameSlot(Routine.TASK, "Exam revision", "exam revision") == 0
                && three.renameSlot(Routine.TASK, "Exam revision", "") == 0 && three.renameSlot(Routine.TASK, "", "x") == 0);
        Routine ty = Routine.sample();
        ty.putGoal(Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 30));
        ty.putGoal(new Routine.Goal(Routine.TASK, "Study", 30));
        ty.removeSub(ty.findSub(Routine.TASK, "Study"));
        ok("removing a type removes the older goal but keeps the goal of a slot", ty.goals.size() == 1 && ty.goals.get(0).bound());
        ok("hasSlot looks through the routine", ty.hasSlot(Routine.TASK, "reading & notes") && !ty.hasSlot(Routine.REST, "Reading & notes")
                && !ty.hasSlot(Routine.TASK, "Nothing"));

        // ---- the to-do ticks of a goal
        Todos t = new Todos();
        Routine.Goal tg = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60);
        tg.addTodo("Read chapter 4");
        tg.addTodo("Make notes");
        ok("nothing is ticked at first", !t.goalDone(day(26), tg, "Read chapter 4") && t.goalDoneCount(day(26), tg) == 0 && t.isEmpty());
        t.setGoalDone(day(26), tg, "Read chapter 4", true);
        ok("a tick is kept for the day", t.goalDone(day(26), tg, "Read chapter 4") && t.goalDoneCount(day(26), tg) == 1 && !t.isEmpty());
        ok("not for another day", !t.goalDone(day(27), tg, "Read chapter 4"));
        ok("whatever the case of the text", t.goalDone(day(26), tg, "READ chapter 4  "));
        t.setGoalDone(day(26), tg, "", true);
        ok("the goal itself is ticked apart from its list", t.goalDone(day(26), tg, "") && t.goalDoneCount(day(26), tg) == 1);
        Todos tb = Todos.parse(t.serialize());
        ok("ticks survive saving", tb.goalDone(day(26), tg, "Read chapter 4") && tb.goalDone(day(26), tg, "") && !tb.goalDone(day(26), tg, "Make notes"));
        ok("saving again gives the same text", tb.serialize().equals(t.serialize()));
        t.setGoalDone(day(26), tg, "Read chapter 4", false);
        ok("a tick is taken back", !t.goalDone(day(26), tg, "Read chapter 4"));
        Routine.Goal other = Routine.Goal.forSlot(Routine.TASK, "Project work", 60);
        t.setGoalDone(day(26), tg, "Make notes", true);
        ok("another goal does not share the tick", !t.goalDone(day(26), other, "Make notes"));
        ok("damaged tick lines are skipped", Todos.parse("K\tnotaday\tx\ty\nK\t2026-10-26\n" + t.serialize()).goalDone(day(26), tg, "Make notes"));
        ok("old files without ticks still read", Todos.parse("2026-10-26\tS1\t1\tOld style\n").items(day(26), "S1").get(0).done);
        Todos old2 = new Todos();
        old2.setGoalDone(day(20), tg, "Make notes", true);
        old2.setGoalDone(day(25), tg, "Make notes", true);
        old2.setGoalDone(day(26), tg, "Make notes", true);
        ok("old ticks are forgotten with old days", old2.prune(day(26)) && !old2.goalDone(day(20), tg, "Make notes")
                && old2.goalDone(day(25), tg, "Make notes") && old2.goalDone(day(26), tg, "Make notes"));

        // ---- one list for a slot
        Routine sr = Routine.sample();
        Routine.Goal quiet = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60);   // a goal with no to-dos
        sr.putGoal(quiet);
        Plan.DayPlan pp = Plan.resolve(sr, newOv(sr), at(26, 10, 0), Z);
        List<Routine.Block> day26 = blocksOf(pp);
        int src = srcOf(pp, "Reading & notes");
        ok("the sample day has the slot", src >= 0);
        List<Routine.Goal> goals26 = sr.goalsFor(pp.day, day26);
        Todos st = new Todos();
        st.add(pp.day, Todos.slotScope(src), "Bring the book");
        List<SlotTodo.Row> rows = SlotTodo.rows(st, pp.day, day26, src, goals26, 0);
        ok("the slot's things come first, then its goal", rows.size() == 2 && rows.get(0).type == SlotTodo.ITEM && rows.get(1).type == SlotTodo.GOAL);
        ok("a goal with no to-dos is a thing to do", rows.get(1).text.equals("Goal · 1h") && !rows.get(1).done && rows.get(1).tickable());
        ok("it is struck when ticked", SlotTodo.tick(st, pp.day, rows.get(1), true)
                && SlotTodo.rows(st, pp.day, day26, src, goals26, 0).get(1).done);
        ok("and can be ticked off again", SlotTodo.tick(st, pp.day, rows.get(1), false)
                && !SlotTodo.rows(st, pp.day, day26, src, goals26, 0).get(1).done);
        ok("time does it too: before the time it is not done", !SlotTodo.rows(st, pp.day, day26, src, goals26, 59).get(1).done);
        List<SlotTodo.Row> byTime = SlotTodo.rows(st, pp.day, day26, src, goals26, 60);
        ok("once the time target has passed, it is struck by itself", byTime.get(1).done && byTime.get(1).byTime && !byTime.get(1).tickable());
        ok("it cannot be un-ticked while time says done", !SlotTodo.tick(st, pp.day, byTime.get(1), false));
        ok("passed time after the target also counts", SlotTodo.rows(st, pp.day, day26, src, goals26, 500).get(1).done);
        ok("the slot's own thing is its own", !rows.get(0).done && SlotTodo.tick(st, pp.day, rows.get(0), true)
                && st.items(pp.day, Todos.slotScope(src)).get(0).done);

        quiet.addTodo("Read chapter 4");
        quiet.addTodo("Make notes");
        List<SlotTodo.Row> head = SlotTodo.rows(st, pp.day, day26, src, goals26, 0);
        ok("a goal with to-dos heads them: the slot's thing, the goal, then its things",
                head.size() == 4 && head.get(1).type == SlotTodo.HEAD && head.get(2).type == SlotTodo.STEP && head.get(3).type == SlotTodo.STEP);
        ok("the head is not a thing to do and cannot be ticked", !head.get(1).tickable() && !SlotTodo.tick(st, pp.day, head.get(1), true));
        ok("it says how many are ticked", head.get(1).total == 2 && head.get(1).ticked == 0 && !head.get(1).done);
        ok("time does not strike a goal that has to-dos", !SlotTodo.rows(st, pp.day, day26, src, goals26, 500).get(1).done);
        SlotTodo.tick(st, pp.day, head.get(2), true);
        List<SlotTodo.Row> half = SlotTodo.rows(st, pp.day, day26, src, goals26, 0);
        ok("one of two ticked: the goal is not struck", half.get(2).done && !half.get(3).done && !half.get(1).done && half.get(1).ticked == 1);
        SlotTodo.tick(st, pp.day, half.get(3), true);
        List<SlotTodo.Row> all = SlotTodo.rows(st, pp.day, day26, src, goals26, 0);
        ok("all ticked: the goal is struck", all.get(1).done && all.get(1).ticked == 2);
        SlotTodo.tick(st, pp.day, all.get(2), false);
        ok("untick one and the goal is open again", !SlotTodo.rows(st, pp.day, day26, src, goals26, 0).get(1).done);
        st.setGoalDone(pp.day, quiet, "", true);
        ok("a tick on the goal itself does not strike a goal with to-dos", !SlotTodo.rows(st, pp.day, day26, src, goals26, 0).get(1).done);
        st.setGoalDone(pp.day, quiet, "", false);
        SlotTodo.tick(st, pp.day, all.get(2), true);

        int[] cnt = SlotTodo.counts(all);
        ok("the count is the things to do: the slot's, and the goal's, not the goal that heads them", cnt[1] == 3 && cnt[0] == 3);
        SlotTodo.tick(st, pp.day, all.get(3), false);
        int[] cnt2 = SlotTodo.counts(SlotTodo.rows(st, pp.day, day26, src, goals26, 0));
        ok("and the done part follows", cnt2[1] == 3 && cnt2[0] == 2);
        List<Todos.Item> items = SlotTodo.items(all);
        ok("for the wallpaper: a thing on a goal's list is a step in, the head is marked",
                items.size() == 4 && items.get(0).level == 0 && !items.get(0).head && items.get(1).head && items.get(1).level == 0
                        && items.get(2).level == 1 && items.get(3).level == 1 && items.get(1).done);

        // the same list from the other side: the goal's list in the editor is the one on the slot
        Routine.Goal edited = quiet.copy();
        edited.addTodo("Review");
        List<Routine.Goal> viaEdit = new ArrayList<>();
        viaEdit.add(edited);
        ok("a thing added to the goal shows in the slot's list", SlotTodo.rows(st, pp.day, day26, src, viaEdit, 0).size() == 5);
        edited.todos.remove("Make notes");
        ok("a thing removed from the goal leaves the slot's list", SlotTodo.rows(st, pp.day, day26, src, viaEdit, 0).stream().noneMatch(x -> x.text.equals("Make notes")));
        ok("a tick made on the slot is the tick the goal sees", st.goalDone(pp.day, quiet, "Read chapter 4")
                && !st.goalDone(pp.day, quiet, "Make notes"));

        // other slots, two slots of the same name, skipped slots
        int other2 = srcOf(pp, "Project work");
        ok("another slot has none of this goal's rows", SlotTodo.rows(st, pp.day, day26, other2, goals26, 0).isEmpty());
        ok("goalsOf is the goals whose slot it is", SlotTodo.goalsOf(day26, src, goals26).size() == 1 && SlotTodo.goalsOf(day26, other2, goals26).isEmpty());
        ok("nothing without goals or a number", SlotTodo.goalsOf(day26, -1, goals26).isEmpty() && SlotTodo.goalsOf(day26, src, null).isEmpty()
                && SlotTodo.goalsOf(null, src, goals26).isEmpty());
        Routine dup = Routine.sample();
        dup.blocks.add(dup.blocks.size() - 1, new Routine.Block(Routine.TASK, "Reading & notes", 30));
        dup.putGoal(Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 30));
        Plan.DayPlan dp = Plan.resolve(dup, newOv(dup), at(26, 10, 0), Z);
        List<Routine.Block> dBlocks = blocksOf(dp);
        List<Routine.Goal> dGoals = dup.goalsFor(dp.day, dBlocks);
        int first = -1, second = -1;
        for (Routine.Slot s : dp.sc.slots) if (s.block.title.equals("Reading & notes")) { if (first < 0) first = s.block.src; else second = s.block.src; }
        ok("two slots with the same name: the goal sits with the first", SlotTodo.goalsOf(dBlocks, first, dGoals).size() == 1
                && SlotTodo.goalsOf(dBlocks, second, dGoals).isEmpty());
        ok("so its to-dos are not shown twice", SlotTodo.rows(new Todos(), dp.day, dBlocks, second, dGoals, 0).isEmpty());
        List<Routine.Block> skipFirst = new ArrayList<>();
        for (Routine.Block b : dBlocks) {
            Routine.Block c = b.copy();
            if (b.src == first) c.skipped = true;
            skipFirst.add(c);
        }
        ok("if the first is taken off, the goal moves to the next one", SlotTodo.goalsOf(skipFirst, second, dup.goalsFor(dp.day, skipFirst)).size() == 1);

        // minutes of the slot that have passed
        Plan.DayPlan nine = Plan.resolve(sr, newOv(sr), at(26, 9, 0), Z);
        ok("before the slot: none have passed", SlotTodo.passedMinutes(nine, src) == 0);
        ok("half an hour into it: 30", SlotTodo.passedMinutes(pp, src) == 30);
        Plan.DayPlan after = Plan.resolve(sr, newOv(sr), at(26, 13, 0), Z);
        ok("after it: the whole slot, no more", SlotTodo.passedMinutes(after, src) == 90);
        ok("no plan or no slot: none", SlotTodo.passedMinutes(null, src) == 0 && SlotTodo.passedMinutes(pp, -1) == 0);

        // the wallpaper
        Todos wt = new Todos();
        wt.add(pp.day, Todos.slotScope(src), "Bring the book");
        Routine wr = Routine.sample();
        Routine.Goal wg = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 45);
        wg.addTodo("Read chapter 4");
        wr.putGoal(wg);
        PosterExtras px = new PosterExtras(wt, null, null, wr, null, null);
        PosterExtras.Now now = px.at(Plan.resolve(wr, newOv(wr), at(26, 10, 0), Z), at(26, 10, 0));
        ok("the wallpaper shows the slot's thing, the goal and its list", now.items.size() == 3 && now.items.get(1).head && now.items.get(2).level == 1
                && now.items.get(0).text.equals("Bring the book"));
        wt.setGoalDone(pp.day, wg, "Read chapter 4", true);
        PosterExtras.Now now2 = px.at(Plan.resolve(wr, newOv(wr), at(26, 10, 0), Z), at(26, 10, 0));
        ok("a tick on the phone shows on the wallpaper", now2.items.get(2).done && now2.items.get(1).done);
        Routine wr2 = Routine.sample();
        Routine.Goal wg2 = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 30);
        wr2.putGoal(wg2);
        PosterExtras px2 = new PosterExtras(wt, null, null, wr2, null, null);
        PosterExtras.Now at1000 = px2.at(Plan.resolve(wr2, newOv(wr2), at(26, 10, 0), Z), at(26, 10, 0));
        PosterExtras.Now at0940 = px2.at(Plan.resolve(wr2, newOv(wr2), at(26, 9, 40), Z), at(26, 9, 40));
        ok("a goal with no to-dos is struck on the wallpaper when its time has passed",
                at1000.items.get(at1000.items.size() - 1).done && !at0940.items.get(at0940.items.size() - 1).done);
        PosterExtras none = new PosterExtras(new Todos(), null, null, null, null, null);
        ok("with no routine given there are no goals", none.at(Plan.resolve(wr2, newOv(wr2), at(26, 10, 0), Z), at(26, 10, 0)).items.isEmpty());

        // ---- reports follow the slot
        DayReport rep = new DayReport();
        rep.day = day(26).toString();
        rep.goals.add(Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60));
        rep.goals.add(Routine.Goal.forSlot(Routine.TASK, "Project work", 60));
        rep.rows.add(row(Routine.TASK, "Study", "Reading & notes", 90, 75));
        rep.rows.add(row(Routine.TASK, "Study", "Morning focus block", 120, 120));
        rep.rows.add(row(Routine.TASK, "Work", "Project work", 120, 30));
        List<TypeStats.Line> lines = TypeStats.of(rep);
        TypeStats.Line rl = TypeStats.findGoal(lines, rep.goals.get(0));
        TypeStats.Line pl = TypeStats.findGoal(lines, rep.goals.get(1));
        ok("each slot goal has its own line with its own time", rl != null && pl != null && rl.doneMin == 75 && pl.doneMin == 30);
        ok("the line carries the slot and is named after it", rl.slot.equals("Reading & notes") && rl.label().equals("Reading & notes") && rl.met() && !pl.met());
        ok("the time of other slots of the type is not counted", rl.plannedMin == 90 && pl.plannedMin == 120);
        ok("the goal lines come first", lines.get(0) == rl && lines.get(1) == pl);
        ok("the types are still listed as before", TypeStats.find(lines, Routine.TASK, "Study") != null
                && TypeStats.find(lines, Routine.TASK, "Study").doneMin == 195 && TypeStats.find(lines, Routine.TASK, "Work").doneMin == 30);
        ok("a type lookup never returns a slot goal's line", TypeStats.find(lines, Routine.TASK, "") == null);
        ok("lines are told apart by slot", !TypeStats.lineKey(rl).equals(TypeStats.lineKey(TypeStats.find(lines, Routine.TASK, "Study"))));
        DayReport rep2 = new DayReport();
        rep2.day = day(25).toString();
        rep2.goals.add(Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60));
        rep2.rows.add(row(Routine.TASK, "Study", "Reading & notes", 90, 20));
        List<DayReport> avgDays = new ArrayList<>();
        avgDays.add(rep);
        avgDays.add(rep2);
        List<TypeStats.Line> avg = TypeStats.average(avgDays);
        TypeStats.Line ar = null;
        TypeStats.Line at2 = null;
        for (TypeStats.Line l : avg) {
            if (l.slot.equals("Reading & notes")) ar = l;
            if (l.slot.isEmpty() && l.sub.equals("Study")) at2 = l;
        }
        ok("the average keeps the slot apart from its type", ar != null && at2 != null && ar.doneMin == 48 && at2.doneMin == 108);
        DayReport rep3 = new DayReport();
        rep3.day = day(26).toString();
        rep3.goals.add(Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60));
        DayReport.Row skipped = row(Routine.TASK, "Study", "Reading & notes", 90, 90);
        skipped.status = DayReport.SKIPPED;
        rep3.rows.add(skipped);
        ok("a slot taken off adds nothing", TypeStats.of(rep3).get(0).doneMin == 0 && TypeStats.of(rep3).get(0).plannedMin == 0);
        DayReport renamedRep = new DayReport();
        renamedRep.day = day(26).toString();
        renamedRep.goals.add(Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60));
        DayReport.Row rowRenamed = row(Routine.TASK, "Study", "Reading & notes", 90, 70);
        rowRenamed.newTitle = "Novel";
        renamedRep.rows.add(rowRenamed);
        ok("a slot renamed for the day still counts for its goal", TypeStats.of(renamedRep).get(0).doneMin == 70);

        // streaks by slot
        Routine.Goal sgoal = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60);
        List<DayReport> history = new ArrayList<>();
        history.add(slotDay(day(25), sgoal, 70));
        history.add(slotDay(day(24), sgoal, 65));
        history.add(slotDay(day(23), sgoal, 10));
        history.add(slotDay(day(22), sgoal, 80));
        DayReport today = slotDay(day(26), sgoal, 61);
        ok("the streak counts days the slot's time was met", TypeStats.streak(sgoal, today, history) == 3);
        ok("without today it counts the finished days", TypeStats.streak(sgoal, null, history) == 2);
        DayReport todayShort = slotDay(day(26), sgoal, 20);
        ok("today not met yet does not break it", TypeStats.streak(sgoal, todayShort, history) == 2);
        Routine.Goal otherSlot = Routine.Goal.forSlot(Routine.TASK, "Project work", 60);
        ok("another slot's goal does not use these days", TypeStats.streak(otherSlot, today, history) == 0);
        ok("a one-day slot goal has no streak", TypeStats.streak(Routine.Goal.onceForSlot(Routine.TASK, "Reading & notes", 60, day(26)), today, history) == 0);

        // the report keeps the slot of its goals
        DayReport saved = DayReport.parse(rep.serialize());
        ok("a saved report keeps the slot of each goal", saved.goals.size() == 2 && saved.goals.get(0).bound() && saved.goals.get(0).slot.equals("Reading & notes")
                && saved.goals.get(1).slot.equals("Project work") && saved.goals.get(1).mins == 60);
        ok("the lines are the same after saving", TypeStats.of(saved).get(0).doneMin == 75 && TypeStats.of(saved).get(0).slot.equals("Reading & notes"));
        DayReport olderReport = new DayReport();
        olderReport.day = day(20).toString();
        olderReport.goals.add(new Routine.Goal(Routine.TASK, "Study", 90));
        DayReport olderBack = DayReport.parse(olderReport.serialize());
        ok("a goal of a whole type is saved as before", olderBack.goals.size() == 1 && !olderBack.goals.get(0).bound() && olderBack.goals.get(0).sub.equals("Study"));
        ok("a report written before still reads", DayReport.parse("DINAREPORT1\nday=2026-10-20\nG\t0\t90\tStudy\n").goals.get(0).sub.equals("Study"));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static Overrides newOv(Routine r) {
        Overrides o = new Overrides();
        o.sig = Plan.signature(r);
        return o;
    }

    static DayReport.Row row(int kind, String sub, String title, int planned, int doneMin) {
        DayReport.Row row = new DayReport.Row();
        row.kind = kind;
        row.sub = sub;
        row.title = title;
        row.plannedMin = planned;
        row.activeMs = doneMin * 60000L;
        row.status = DayReport.KEPT;
        return row;
    }

    static DayReport slotDay(LocalDate d, Routine.Goal g, int doneMin) {
        DayReport r = new DayReport();
        r.day = d.toString();
        r.goals.add(g.copy());
        r.rows.add(row(g.kind, "Study", g.slot, 90, doneMin));
        return r;
    }
}
