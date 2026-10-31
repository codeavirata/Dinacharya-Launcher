package app.dinacharya.wallpaper;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * What the home screen poster shows besides the routine: the day as it is going (breaks, a start put off, the slot before carried on, a slot
 * finished early, a day off), the to-do list of the slot that is going on, a note while a break is on, and whether library mode is on.
 * Holds the saved data; {@link #live} and {@link #at} work out what applies at one moment. Uses no Android classes.
 */
final class PosterExtras {
    static final PosterExtras NONE = new PosterExtras(null, null);

    private final Todos todos;
    private final AlarmState state;
    private final List<Ev> events;
    private final Routine main;
    private final LibraryMode library;
    private final SlotUse.Snapshot use;

    PosterExtras(Todos todos, AlarmState state) {
        this(todos, state, null, null, null);
    }

    PosterExtras(Todos todos, AlarmState state, List<Ev> events, Routine main, LibraryMode library) {
        this(todos, state, events, main, library, null);
    }

    /** {@code use}: how today's slots went as far as phone use goes (null when phone use in slots is off or could not be read). */
    PosterExtras(Todos todos, AlarmState state, List<Ev> events, Routine main, LibraryMode library, SlotUse.Snapshot use) {
        this.todos = todos;
        this.state = state;
        this.events = events;
        this.main = main;
        this.library = library;
        this.use = use;
    }

    /** What applies now. */
    static final class Now {
        /** The things to do in the slot that is going on (empty when none). */
        final List<Todos.Item> items;
        /** When the break that is on ends, or 0 when none is. */
        final long breakUntilMs;
        /** When library mode ends (a day later for one without an end), or 0 when it is off. */
        final long libraryEndMs;
        /** How the slot going on is going as far as phone use goes, or null when that is not shown. */
        final SlotUse.Info use;
        /** How today's slots went, by their number in the routine; null when phone use is not shown. */
        final SlotUse.Snapshot slots;
        /** Today's goals and how far they are, for a small strip on the poster; empty when there are none. */
        List<GoalGlance> goals = new ArrayList<>();

        Now(List<Todos.Item> items, long breakUntilMs) {
            this(items, breakUntilMs, 0L);
        }

        Now(List<Todos.Item> items, long breakUntilMs, long libraryEndMs) {
            this(items, breakUntilMs, libraryEndMs, null, null);
        }

        Now(List<Todos.Item> items, long breakUntilMs, long libraryEndMs, SlotUse.Info use, SlotUse.Snapshot slots) {
            this.items = items;
            this.breakUntilMs = breakUntilMs;
            this.libraryEndMs = libraryEndMs;
            this.use = use;
            this.slots = slots;
        }

        /** How the slot with this number in the routine went, or null when nothing is known. */
        SlotUse.Info useOf(int src) {
            return slots == null || src < 0 ? null : slots.of(src);
        }
    }

    static final Now NOTHING = new Now(new ArrayList<Todos.Item>(), 0L, 0L);

    /** The day with what happened in it applied, or {@code day} itself when nothing did (or nothing is known). */
    Plan.DayPlan live(Plan.DayPlan day, long nowMs) {
        if (day == null || events == null) return day;
        boolean off = main != null && state != null && DayOff.active(main, state, day.day);
        return LivePlan.apply(day, events, state, off, nowMs);
    }

    /** What applies now in {@code day}, which is the live day (see {@link #live}). */
    Now at(Plan.DayPlan day, long nowMs) {
        return at(day, nowMs, null);
    }

    Now at(Plan.DayPlan day, long nowMs, ZoneId zone) {
        Now n = base(day, nowMs, zone);
        List<GoalGlance> g = GoalGlance.of(main, day);
        if (g.isEmpty()) return n;
        // the shared "nothing" is never changed: a copy carries the goals
        Now c = new Now(n.items, n.breakUntilMs, n.libraryEndMs, n.use, n.slots);
        c.goals = g;
        return c;
    }

    private Now base(Plan.DayPlan day, long nowMs, ZoneId zone) {
        long lib = library != null && zone != null ? library.endMs(nowMs, zone) : 0L;
        // how today's slots went, when it is for this very day
        SlotUse.Snapshot slots = use != null && day != null && use.day.equals(day.day.toString()) && !use.bySrc.isEmpty() ? use : null;
        if (day == null || day.cur < 0 || day.cur >= day.sc.slots.size()) return lib == 0L && slots == null ? NOTHING : new Now(new ArrayList<Todos.Item>(), 0L, lib, null, slots);
        Routine.Block b = day.sc.slots.get(day.cur).block;
        if (b.skipped) return lib == 0L && slots == null ? NOTHING : new Now(new ArrayList<Todos.Item>(), 0L, lib, null, slots);
        List<Todos.Item> items = new ArrayList<>();
        if (todos != null && b.src >= 0 && (!todos.isEmpty() || (main != null && !main.goals.isEmpty()))) {
            List<Routine.Block> blocks = new ArrayList<>();
            for (Routine.Slot s : day.sc.slots) {
                // the parts a slot is cut into, and the rows for breaks, waits and the day off, are not slots of the plan
                if (s.block.src >= 0 && !blocks.contains(s.block)) blocks.add(s.block);
            }
            todos.materialize(day.day, blocks);   // items that repeat today (only in memory here: the app saves them when it shows them)
            // the slot's own things, then its goals: a goal is a thing to do, or heads a list of its own (see SlotTodo)
            List<Routine.Goal> goals = main == null ? null : main.goalsFor(day.day, blocks);
            items = SlotTodo.items(SlotTodo.rows(todos, day.day, blocks, b.slotSrc(), goals, SlotTodo.passedMinutes(day, b.slotSrc())));
        }
        long until = 0L;
        if (state != null && state.onBreak(nowMs) && state.breakSrc == b.slotSrc() && state.breakDay.equals(day.day.toString())) until = state.breakTo;
        // the slot going on, while it is a slot of the plan itself (not a break, a wait or the day off)
        SlotUse.Info cur = slots != null && b.src >= 0 && !b.isGap() && until == 0L ? slots.of(b.src) : null;
        return items.isEmpty() && until == 0L && lib == 0L && slots == null ? NOTHING : new Now(items, until, lib, cur, slots);
    }
}
