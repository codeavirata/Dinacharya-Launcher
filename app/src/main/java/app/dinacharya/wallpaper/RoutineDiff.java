package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Says in short lines what an edit of the routine changes, for the "save from tomorrow" pop-up: a renamed slot ("Study → Revision"), a
 * slot made longer or shorter ("Lunch: 60 min → 45 min"), a slot deleted or added, another start of the day, a new order. Slots are
 * matched by name first, then a slot that only has a new name is told from a deleted one by its other settings. Uses no Android classes.
 */
public final class RoutineDiff {
    private RoutineDiff() {
    }

    /** How many lines the pop-up shows before "+ N more". */
    public static final int SHOWN = 3;

    /** The changes {@code after} makes to the day {@code day} compared with {@code before}, in the order of the slots. Never empty when the two days differ. */
    public static List<String> changes(Routine before, Routine after, LocalDate day, boolean h24) {
        Routine b = day == null ? before : before.forDay(day);
        Routine a = day == null ? after : after.forDay(day);
        return between(b, a, h24);
    }

    /** The changes between two routines as they stand (not looking at which day they are for). */
    public static List<String> between(Routine b, Routine a, boolean h24) {
        List<String> out = new ArrayList<>();
        int n = b.blocks.size();
        int m = a.blocks.size();
        if (Routine.mod(a.anchor, 1440) != Routine.mod(b.anchor, 1440)) {
            out.add("Start of day: " + clock(b.anchor, h24) + " → " + clock(a.anchor, h24));
        } else if (a.mode != b.mode) {
            out.add("Start of day setting changed");
        }

        // Which slot of the new list each old slot became (-1: none). Same name first, in order.
        int[] to = new int[n];
        boolean[] taken = new boolean[m];
        for (int i = 0; i < n; i++) {
            to[i] = -1;
            for (int j = 0; j < m; j++) {
                if (!taken[j] && a.blocks.get(j).title.equals(b.blocks.get(i).title)) {
                    to[i] = j;
                    taken[j] = true;
                    break;
                }
            }
        }
        // A slot with a new name: the one of the same kind at the same place, or else one that is the same in everything but its name.
        for (int i = 0; i < n; i++) {
            if (to[i] >= 0) continue;
            int j = i < m && !taken[i] && a.blocks.get(i).kind == b.blocks.get(i).kind ? i : lookAlike(b.blocks.get(i), a, taken);
            if (j >= 0) {
                to[i] = j;
                taken[j] = true;
            }
        }

        boolean reordered = false;
        int last = -1;
        for (int i = 0; i < n; i++) {
            Routine.Block x = b.blocks.get(i);
            if (to[i] < 0) {
                out.add("Deleted: " + x.title);
                continue;
            }
            Routine.Block y = a.blocks.get(to[i]);
            if (!x.title.equals(y.title)) out.add(x.title + " → " + y.title);
            if (x.mins != y.mins) out.add(y.title + ": " + x.mins + " min → " + y.mins + " min");
            if (x.kind != y.kind || x.alert != y.alert || x.wake != y.wake || !x.sub.equals(y.sub)) out.add(y.title + ": settings changed");
            if (to[i] < last) reordered = true;
            last = Math.max(last, to[i]);
        }
        if (reordered) out.add("Order of slots changed");
        for (int j = 0; j < m; j++) if (!taken[j]) out.add("Added: " + a.blocks.get(j).title);
        if (out.isEmpty() && !RoutineLock.sameDay(b, a)) out.add("Routine changed");
        return out;
    }

    /** A free slot of {@code a} that is the same as {@code x} in kind, length and settings but not in name, or -1. */
    private static int lookAlike(Routine.Block x, Routine a, boolean[] taken) {
        for (int j = 0; j < a.blocks.size(); j++) {
            if (taken[j]) continue;
            Routine.Block y = a.blocks.get(j);
            if (y.kind == x.kind && y.mins == x.mins && y.alert == x.alert && y.wake == x.wake && y.sub.equals(x.sub)) return j;
        }
        return -1;
    }

    /** The clock time of a minute of the day ("5:30 AM", or "05:30" with the 24-hour clock). */
    public static String clock(int minute, boolean h24) {
        int t = Routine.mod(minute, 1440);
        int h = t / 60;
        int mi = t % 60;
        if (h24) return (h < 10 ? "0" : "") + h + ":" + (mi < 10 ? "0" : "") + mi;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (mi < 10 ? "0" : "") + mi + (h < 12 ? " AM" : " PM");
    }

    /** The "+ N more" line for the changes beyond the first {@link #SHOWN}, or "" when all fit. */
    public static String more(int total) {
        return total > SHOWN ? "+ " + (total - SHOWN) + " more" : "";
    }
}
