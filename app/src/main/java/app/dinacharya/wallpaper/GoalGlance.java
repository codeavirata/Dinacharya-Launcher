package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * Today's goals at a glance, for the live wallpaper: each goal's name and how far through it the day has taken it. The progress is
 * the plan so far: the part of each slot that counts for the goal which has already gone by (a slot taken off does not count). The
 * Insights tab works from what really happened, so the two can differ a little. Uses no Android classes.
 */
final class GoalGlance {
    /** The most goals the wallpaper shows. */
    static final int MAX = 4;

    final String name;
    /** 0..1 */
    final float frac;
    final boolean met;

    GoalGlance(String name, float frac, boolean met) {
        this.name = name;
        this.frac = frac;
        this.met = met;
    }

    /** True when the slot is one the goal counts: the slot of its name, or (an older goal) a slot of its activity type. */
    static boolean counts(Routine.Goal g, Routine.Block b) {
        if (b.kind == Routine.SLEEP || b.isGap() || b.skipped) return false;
        if (g.bound()) return b.kind == g.kind && Routine.clean(b.title).trim().equalsIgnoreCase(g.slot);
        return TypeStats.key(b.kind, b.sub).equals(TypeStats.key(g.kind, g.sub));
    }

    /** The name a goal goes by. */
    static String nameOf(Routine.Goal g) {
        if (g.bound()) return g.slot;
        if (g.sub == null || g.sub.isEmpty() || Routine.isOther(g.sub)) return "Other " + Routine.KIND_NAMES[g.kind].toLowerCase(java.util.Locale.ROOT);
        return g.sub;
    }

    /** At most {@link #MAX} goals of the day: those still going first, the nearest to done first, then the finished ones. */
    static List<GoalGlance> of(Routine main, Plan.DayPlan day) {
        List<GoalGlance> out = new ArrayList<>();
        if (main == null || day == null || main.goals.isEmpty()) return out;
        List<Routine.Block> blocks = new ArrayList<>();
        for (Routine.Slot s : day.sc.slots) if (s.block.src >= 0 && !blocks.contains(s.block)) blocks.add(s.block);
        for (Routine.Goal g : main.forDay(day.day).goalsFor(day.day, blocks)) {
            if (g.mins <= 0) continue;
            int done = 0;
            for (Routine.Slot s : day.sc.slots) {
                if (!counts(g, s.block)) continue;
                done += Math.max(0, Math.min(s.end, day.tt) - s.start);
            }
            boolean met = done >= g.mins;
            out.add(new GoalGlance(nameOf(g), Math.min(1f, done / (float) g.mins), met));
        }
        out.sort((x, y) -> x.met != y.met ? (x.met ? 1 : -1) : Float.compare(y.frac, x.frac));
        return out.size() > MAX ? new ArrayList<>(out.subList(0, MAX)) : out;
    }
}
