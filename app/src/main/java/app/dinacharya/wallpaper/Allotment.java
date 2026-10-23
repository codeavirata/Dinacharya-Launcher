package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/** How the minutes of one routine are shared between kinds of slot and between activity types. Uses no Android classes. */
public final class Allotment {
    private Allotment() {
    }

    /** Minutes given to one activity type (kind and name). */
    public static final class Item {
        public final int kind;
        public final String sub;
        public int mins;

        Item(int kind, String sub) {
            this.kind = kind;
            this.sub = sub;
        }
    }

    /** Minutes per kind of slot (task, rest, meal, personal, sleep). */
    public static int[] byKind(Routine r) {
        int[] out = new int[Routine.KIND_NAMES.length];
        for (Routine.Block b : r.blocks) out[Math.max(0, Math.min(out.length - 1, b.kind))] += b.mins;
        return out;
    }

    /**
     * Minutes per activity type that has any, kinds in order and, inside a kind, the longest first with "Other" last.
     * A slot with no type counts under "Other" of its kind.
     */
    public static List<Item> bySub(Routine r) {
        List<Item> items = new ArrayList<>();
        for (Routine.Block b : r.blocks) {
            String sub = b.sub == null || b.sub.isEmpty() ? Routine.OTHER : b.sub;
            Item hit = null;
            for (Item it : items) {
                if (it.kind == b.kind && it.sub.equalsIgnoreCase(sub)) {
                    hit = it;
                    break;
                }
            }
            if (hit == null) {
                hit = new Item(b.kind, sub);
                items.add(hit);
            }
            hit.mins += b.mins;
        }
        items.sort((x, y) -> {
            if (x.kind != y.kind) return Integer.compare(x.kind, y.kind);
            boolean xo = Routine.isOther(x.sub);
            boolean yo = Routine.isOther(y.sub);
            if (xo != yo) return xo ? 1 : -1;
            return Integer.compare(y.mins, x.mins);
        });
        return items;
    }
}
