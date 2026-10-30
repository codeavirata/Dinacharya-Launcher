package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The slots a goal can be set for. A slot is known by its activity kind and its name in the routine (the saved name, so a slot renamed
 * for one day is still the same slot). Sleep and slots taken off are not offered. Uses no Android classes.
 */
public final class GoalSlots {
    private GoalSlots() {
    }

    /** One choice in the list: a slot (or, for an older goal, a whole activity type). */
    public static final class Pick {
        public final int kind;
        /** The slot's name, or for an older goal the activity type. */
        public final String name;
        /** The activity type of the slot (for its colour); may be empty. */
        public final String sub;
        /** True for an older goal that covers a whole activity type. */
        public final boolean legacy;

        Pick(int kind, String name, String sub, boolean legacy) {
            this.kind = kind;
            this.name = name;
            this.sub = sub == null ? "" : sub;
            this.legacy = legacy;
        }

        /** The pick that an existing goal stands for. */
        public static Pick of(Routine.Goal g) {
            return g.bound() ? new Pick(g.kind, g.slot, "", false) : new Pick(g.kind, g.sub.isEmpty() ? Routine.KIND_NAMES[g.kind] : g.sub, g.sub, true);
        }

        public boolean is(Pick o) {
            return o != null && o.kind == kind && o.legacy == legacy && o.name.equalsIgnoreCase(name);
        }

        /** The text in the list: "Morning study  (Task)". An older goal reads "Every Study slot  (Task)". */
        public String label() {
            return (legacy ? "Every " + name + " slot" : name) + "  (" + Routine.KIND_NAMES[kind] + ")";
        }
    }

    /** The slots of the blocks, each once, in the order they first appear. Sleep, slots taken off and slots without a name are left out. */
    public static List<Pick> of(List<Routine.Block> blocks) {
        List<Pick> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        if (blocks == null) return out;
        for (Routine.Block b : blocks) {
            if (b == null || b.skipped || b.kind == Routine.SLEEP) continue;
            String name = Routine.savedTitle(b);
            if (name.isEmpty()) continue;
            String key = b.kind + "#" + name.toLowerCase(Locale.ROOT);
            if (seen.contains(key)) continue;
            seen.add(key);
            out.add(new Pick(b.kind, name, b.sub, false));
        }
        return out;
    }
}
