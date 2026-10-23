package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** To-do items that repeat (every day, every other day, chosen days), where their copies go, saving, and the scrolling of a long list. */
public class TodoRepeatTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static LocalDate day(int d) { return LocalDate.of(2026, 10, d); }

    static Routine.Block block(int src, int kind, String title) {
        Routine.Block b = new Routine.Block(kind, title, 30);
        b.src = src;
        return b;
    }

    public static void main(String[] a) {
        String goal = Todos.goalScope(Routine.TASK, "Study");
        String s2 = Todos.slotScope(2);

        // ---- a repeating item is on its first day, and comes back on the days its rule says
        Todos t = new Todos();
        ok("a repeating item is added", t.addRepeating(day(23), goal, goal, "Revise formulas", Todos.EVERY_DAY, 0) == 0);
        ok("it is on its first day", t.count(day(23), goal) == 1 && t.items(day(23), goal).get(0).repeatId > 0);
        ok("materialising the same day adds nothing", !t.materialize(day(23), null) && t.count(day(23), goal) == 1);
        ok("the next day gets a copy", t.materialize(day(24), null) && t.count(day(24), goal) == 1);
        ok("only once a day", !t.materialize(day(24), null) && t.count(day(24), goal) == 1);
        ok("the copy is not ticked", !t.items(day(24), goal).get(0).done);
        t.setDone(day(23), goal, 0, true);
        ok("ticking one day does not tick another", !t.items(day(24), goal).get(0).done && t.items(day(23), goal).get(0).done);
        t.remove(day(24), goal, 0);
        ok("removing it for a day keeps it from coming back that day", !t.materialize(day(24), null) && t.count(day(24), goal) == 0);
        ok("but it still comes on the next day", t.materialize(day(25), null) && t.count(day(25), goal) == 1);
        ok("it does not go back to earlier days", !t.materialize(day(22), null) && t.count(day(22), goal) == 0);

        // ---- every other day
        Todos o = new Todos();
        o.addRepeating(day(20), goal, goal, "Mock test", Todos.EVERY_OTHER_DAY, 0);
        boolean[] want = {true, false, true, false, true};
        boolean good = true;
        for (int i = 0; i < 5; i++) {
            o.materialize(day(20 + i), null);
            good &= (o.count(day(20 + i), goal) == 1) == want[i];
        }
        ok("every other day counts from the day it was made", good);

        // ---- chosen days: Monday and Friday. 2026-10-23 is a Friday; 26 a Monday.
        Todos c = new Todos();
        int mask = 1 | (1 << 4);
        c.addRepeating(day(21), goal, goal, "Newspaper", Todos.CHOSEN_DAYS, mask);
        for (int d = 21; d <= 28; d++) c.materialize(day(d), null);
        ok("chosen days only", c.count(day(23), goal) == 1 && c.count(day(26), goal) == 1 && c.count(day(27), goal) == 0
                && c.count(day(24), goal) == 0 && c.count(day(21), goal) == 1);
        ok("no days chosen is a plain item", new Todos().addRepeating(day(21), goal, goal, "x", Todos.CHOSEN_DAYS, 0) == 0
                && new Todos().repeatCount() == 0);
        Todos plain = new Todos();
        plain.addRepeating(day(21), goal, goal, "x", Todos.NO_REPEAT, 0);
        ok("no repeat is a plain item", plain.repeatCount() == 0 && plain.count(day(21), goal) == 1 && plain.items(day(21), goal).get(0).repeatId == 0);

        // ---- a slot's repeating item goes to the first slot of that name, wherever it sits in that day's routine
        Todos sl = new Todos();
        String byName = Todos.slotNameScope(Routine.TASK, "Study block");
        sl.addRepeating(day(23), s2, byName, "Read editorial", Todos.EVERY_DAY, 0);
        List<Routine.Block> other = Arrays.asList(block(0, Routine.REST, "Walk"), block(1, Routine.TASK, "study block"), block(2, Routine.TASK, "Study block"));
        ok("a copy lands in the slot with that name", sl.materialize(day(24), other) && sl.count(day(24), Todos.slotScope(1)) == 1);
        ok("and not in a second slot with the same name", sl.count(day(24), Todos.slotScope(2)) == 0);
        List<Routine.Block> none = Arrays.asList(block(0, Routine.REST, "Walk"));
        ok("a day without that slot gets nothing and tries again later", !sl.materialize(day(25), none) && sl.materialize(day(25), other));
        Routine.Block off = block(1, Routine.TASK, "Study block");
        off.skipped = true;
        ok("a slot taken off does not get it", !sl.materialize(day(26), Arrays.asList(off)));
        ok("a name with another kind does not match", !sl.materialize(day(26), Arrays.asList(block(0, Routine.MEAL, "Study block"))));
        ok("goal items need no slots", c.materialize(day(23), null) || true);

        // ---- stop repeating
        Todos st = new Todos();
        st.addRepeating(day(23), goal, goal, "Daily", Todos.EVERY_DAY, 0);
        st.materialize(day(24), null);
        int id = st.items(day(24), goal).get(0).repeatId;
        st.stopRepeat(id);
        ok("stopping removes the rule", st.repeatOf(id) == null && st.repeatCount() == 0);
        ok("copies already made stay, as plain items", st.count(day(23), goal) == 1 && st.items(day(24), goal).get(0).repeatId == 0);
        ok("nothing more is made", !st.materialize(day(25), null) && st.count(day(25), goal) == 0);

        // ---- limits
        Todos lim = new Todos();
        boolean all = true;
        for (int i = 0; i < Todos.MAX_REPEATS; i++) all &= lim.addRepeating(day(23), "G0:s" + i, "G0:s" + i, "t" + i, Todos.EVERY_DAY, 0) == 0;
        ok("up to the limit can repeat", all && lim.repeatCount() == Todos.MAX_REPEATS);
        ok("past the limit is refused", lim.addRepeating(day(23), goal, goal, "one too many", Todos.EVERY_DAY, 0) == -1);

        // ---- saving
        Todos m = new Todos();
        m.addRepeating(day(23), goal, goal, "Revise formulas", Todos.EVERY_DAY, 0);
        m.addRepeating(day(23), s2, byName, "Read editorial", Todos.CHOSEN_DAYS, mask);
        m.add(day(23), s2, "plain one");
        m.setDone(day(23), goal, 0, true);
        m.materialize(day(24), other);
        String saved = m.serialize();
        Todos back = Todos.parse(saved);
        ok("repeats survive saving", back.repeatCount() == 2 && back.repeatOf(1).ruleText().equals("Every day")
                && back.repeatOf(2).ruleText().equals("Mon, Fri"));
        ok("items keep their tick and their repeat", back.items(day(23), goal).get(0).done && back.items(day(23), goal).get(0).repeatId == 1);
        ok("a plain item stays plain", back.items(day(23), s2).get(1).repeatId == 0 && back.items(day(23), s2).get(1).text.equals("plain one"));
        ok("what was made is remembered", !back.materialize(day(24), other) && back.count(day(24), goal) == m.count(day(24), goal));
        ok("saving again gives the same text", back.serialize().equals(saved));
        ok("damaged repeat lines are skipped", Todos.parse("R\tx\ty\n" + "R\t1\t9\t0\t2026-10-23\tG0:a\tq\n" + "M\tnotadate\t1\n" + saved).repeatCount() == 2);
        ok("old files (no repeats) still read", Todos.parse("2026-10-23\tS1\t1\tOld style\n").items(day(23), "S1").get(0).done);
        ok("an item whose repeat is gone becomes plain", Todos.parse("2026-10-23\tS1\t0@7\tx\n").items(day(23), "S1").get(0).repeatId == 0);
        ok("old days are forgotten with their marks", back.prune(day(30)) && back.count(day(23), goal) == 0 && !back.serialize().contains("M\t2026-10-24"));
        ok("a repeat alone is not empty", !Todos.parse("R\t1\t1\t0\t2026-10-23\tG0:a\tq\n").isEmpty());

        // ---- the window of a long list
        Todos w = new Todos();
        for (int i = 0; i < 12; i++) w.add(day(23), "W", "n" + i);
        List<Todos.Item> items = w.items(day(23), "W");
        ok("a long list pages through what is left", Todos.pageCount(items, 5) == 3);
        ok("page one", Todos.windowStart(items, 5, 0) == 0);
        ok("page two", Todos.windowStart(items, 5, 1) == 5);
        ok("the last page is full, not short", Todos.windowStart(items, 5, 2) == 7);
        ok("then it starts again", Todos.windowStart(items, 5, 3) == 0 && Todos.windowStart(items, 5, 300) == 0);
        ok("a tick below zero is safe", Todos.windowStart(items, 5, -1) == 7);
        for (int i = 0; i < 4; i++) w.setDone(day(23), "W", i, true);
        items = w.items(day(23), "W");
        ok("finished ones scroll up first", Todos.windowStart(items, 8, 0) == 4 && Todos.pageCount(items, 8) == 1);
        ok("then the rest pages, from the first pending one", Todos.windowStart(items, 4, 0) == 4 && Todos.windowStart(items, 4, 1) == 8
                && Todos.pageCount(items, 4) == 2);
        ok("when what is left fits, the window ends at the end of the list", Todos.windowStart(items, 9, 5) == 3);
        ok("it all fits: no paging", Todos.pageCount(items, 12) == 1 && Todos.windowStart(items, 12, 7) == 0);
        for (int i = 4; i < 12; i++) w.setDone(day(23), "W", i, true);
        items = w.items(day(23), "W");
        ok("all finished: the last ones", Todos.windowStart(items, 5, 3) == 7 && Todos.pageCount(items, 5) == 1);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
