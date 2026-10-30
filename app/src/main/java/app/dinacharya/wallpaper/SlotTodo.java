package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The to-do list of one slot as the person sees it everywhere (the home screen, the to-do box, the wallpaper): the slot's own things,
 * then the goals of that slot.
 *
 * <ul>
 * <li>A goal with no to-dos of its own is a thing to do like the others: tick it, or let time do it: it is also done as soon as the
 * time target of the goal has passed in the slot.</li>
 * <li>A goal with to-dos is not a thing to do itself. It heads its own list and is done only when everything under it is ticked.</li>
 * </ul>
 *
 * Nothing is copied: the slot's things live in {@link Todos} under the slot, the goal's things live with the goal ({@link
 * Routine.Goal#todos}) and their ticks in {@link Todos} under the day, so a tick made anywhere shows everywhere. Uses no Android classes.
 */
public final class SlotTodo {
    /** What a row is: a thing of the slot, a goal that is a thing to do, a goal that heads a list, or a thing on that list. */
    public static final int ITEM = 0, GOAL = 1, HEAD = 2, STEP = 3;

    private SlotTodo() {
    }

    /** One row of the list. */
    public static final class Row {
        public final int type;
        public final String text;
        public final boolean done;
        /** A goal that is done because its time has passed, so it cannot be un-ticked. */
        public final boolean byTime;
        /** For {@link #ITEM}: the slot's list; for goals, steps and heads: the goal's name for its ticks. */
        public final String scope;
        /** For {@link #ITEM}: the place in the slot's list. For {@link #STEP}: the place on the goal's list. */
        public final int index;
        /** The goal, for {@link #GOAL}, {@link #HEAD} and {@link #STEP}; null for a thing of the slot. */
        public final Routine.Goal goal;
        /** For {@link #HEAD}: how many things are on the goal's list and how many are ticked. */
        public final int total;
        public final int ticked;
        /** For {@link #ITEM}: the repeat it came from (0: none). */
        public final int repeatId;

        Row(int type, String text, boolean done, boolean byTime, String scope, int index, Routine.Goal goal, int total, int ticked,
            int repeatId) {
            this.type = type;
            this.text = text;
            this.done = done;
            this.byTime = byTime;
            this.scope = scope;
            this.index = index;
            this.goal = goal;
            this.total = total;
            this.ticked = ticked;
            this.repeatId = repeatId;
        }

        /** True when the person can tick this row (a goal that heads a list, and one done by time, cannot be ticked). */
        public boolean tickable() {
            return type != HEAD && !byTime;
        }
    }

    /** The goals that belong to the slot with this number: the goals of that day whose first slot it is. */
    public static List<Routine.Goal> goalsOf(List<Routine.Block> blocks, int src, List<Routine.Goal> goalsToday) {
        List<Routine.Goal> out = new ArrayList<>();
        if (goalsToday == null || blocks == null || src < 0) return out;
        for (Routine.Goal g : goalsToday) {
            if (!g.bound()) continue;
            Routine.Block first = g.firstSlot(blocks);
            if (first != null && first.src == src) out.add(g);
        }
        return out;
    }

    /** The words for a goal as a row: "Goal · 1h 30m". */
    public static String goalText(Routine.Goal g) {
        return "Goal · " + Routine.formatDuration(g.mins);
    }

    /**
     * The whole list of one slot of {@code day}.
     *
     * @param blocks      the slots of the day (with their numbers)
     * @param src         the number of the slot in the routine
     * @param goalsToday  the goals that count that day (see {@link Routine#goalsFor})
     * @param passedMin   how many minutes of the slot have passed (the time a goal without to-dos is measured against)
     */
    public static List<Row> rows(Todos t, LocalDate day, List<Routine.Block> blocks, int src, List<Routine.Goal> goalsToday, int passedMin) {
        List<Row> out = new ArrayList<>();
        String scope = Todos.slotScope(src);
        List<Todos.Item> mine = t.items(day, scope);
        for (int i = 0; i < mine.size(); i++) {
            Todos.Item it = mine.get(i);
            out.add(new Row(ITEM, it.text, it.done, false, scope, i, null, 0, 0, it.repeatId));
        }
        for (Routine.Goal g : goalsOf(blocks, src, goalsToday)) {
            String gs = Todos.goalTickScope(g);
            if (g.todos.isEmpty()) {
                boolean ticked = t.goalDone(day, g, "");
                boolean byTime = passedMin >= g.mins;
                out.add(new Row(GOAL, goalText(g), ticked || byTime, byTime, gs, -1, g, 0, 0, 0));
                continue;
            }
            int ticked = t.goalDoneCount(day, g);
            out.add(new Row(HEAD, goalText(g), ticked >= g.todos.size(), false, gs, -1, g, g.todos.size(), ticked, 0));
            for (int i = 0; i < g.todos.size(); i++) {
                String text = g.todos.get(i);
                out.add(new Row(STEP, text, t.goalDone(day, g, text), false, gs, i, g, 0, 0, 0));
            }
        }
        return out;
    }

    /** Ticks or unticks a row. Returns true when something changed (a head, or a goal done by time, cannot be changed). */
    public static boolean tick(Todos t, LocalDate day, Row r, boolean on) {
        switch (r.type) {
            case ITEM:
                t.setDone(day, r.scope, r.index, on);
                return true;
            case GOAL:
                if (r.byTime) return false;
                t.setGoalDone(day, r.goal, "", on);
                return true;
            case STEP:
                t.setGoalDone(day, r.goal, r.text, on);
                return true;
            default:
                return false;
        }
    }

    /** How many things there are to do and how many are done: the slot's things, goals without to-dos and the things on goals' lists. */
    public static int[] counts(List<Row> rows) {
        int done = 0;
        int total = 0;
        for (Row r : rows) {
            if (r.type == HEAD) continue;
            total++;
            if (r.done) done++;
        }
        return new int[]{done, total};
    }

    /** The rows as items for drawing (the wallpaper): a thing on a goal's list is shown a step in, a goal that heads a list is not ticked. */
    public static List<Todos.Item> items(List<Row> rows) {
        List<Todos.Item> out = new ArrayList<>();
        for (Row r : rows) {
            out.add(new Todos.Item(r.text, r.done, r.type == STEP ? 1 : 0, r.type == HEAD));
        }
        return out;
    }

    /**
     * The minutes of the slot with this number that have passed at {@code day.tt}: the time its goal is measured against. Breaks and
     * waiting parts do not count; the parts a slot was cut into do.
     */
    public static int passedMinutes(Plan.DayPlan day, int src) {
        if (day == null || src < 0) return 0;
        int total = 0;
        for (Routine.Slot sl : day.sc.slots) {
            Routine.Block b = sl.block;
            if (b.slotSrc() != src || b.isGap()) continue;
            int to = Math.min(sl.end, day.tt);
            if (to > sl.start) total += to - sl.start;
        }
        return total;
    }
}
