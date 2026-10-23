package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * What the home screen poster shows besides the routine: the to-do list of the slot that is going on, and a note while a break is on.
 * Holds the saved data; {@link #at} works out what applies at one moment. Uses no Android classes.
 */
final class PosterExtras {
    static final PosterExtras NONE = new PosterExtras(null, null);

    private final Todos todos;
    private final AlarmState state;

    PosterExtras(Todos todos, AlarmState state) {
        this.todos = todos;
        this.state = state;
    }

    /** What applies now. */
    static final class Now {
        /** The things to do in the slot that is going on (empty when none). */
        final List<Todos.Item> items;
        /** When the break that is on ends, or 0 when none is. */
        final long breakUntilMs;

        Now(List<Todos.Item> items, long breakUntilMs) {
            this.items = items;
            this.breakUntilMs = breakUntilMs;
        }
    }

    static final Now NOTHING = new Now(new ArrayList<Todos.Item>(), 0L);

    Now at(Plan.DayPlan day, long nowMs) {
        if (day == null || day.cur < 0 || day.cur >= day.sc.slots.size()) return NOTHING;
        Routine.Block b = day.sc.slots.get(day.cur).block;
        if (b.skipped) return NOTHING;
        List<Todos.Item> items = new ArrayList<>();
        if (todos != null && !todos.isEmpty() && b.src >= 0) {
            List<Routine.Block> blocks = new ArrayList<>();
            for (Routine.Slot s : day.sc.slots) blocks.add(s.block);
            todos.materialize(day.day, blocks);   // items that repeat today (only in memory here: the app saves them when it shows them)
            items = todos.forSlot(day.day, b.src, b.kind, b.sub, null);
        }
        long until = 0L;
        if (state != null && state.onBreak(nowMs) && state.breakSrc == b.src && state.breakDay.equals(day.day.toString())) until = state.breakTo;
        return items.isEmpty() && until == 0L ? NOTHING : new Now(items, until);
    }
}
