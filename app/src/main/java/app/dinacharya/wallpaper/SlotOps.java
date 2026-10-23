package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * Deleting, adding and moving slots while the day stays exactly as long as it was (normally 24 hours).
 * Minutes never appear or disappear: a deleted slot's minutes go to other slots, and a new slot's
 * minutes are taken from other slots. Uses no Android classes.
 */
public final class SlotOps {
    /** A slot is never made shorter than this when it lends minutes to a new slot. */
    public static final int MIN_MINS = 5;

    /** Where a deleted slot's minutes go. */
    public static final int TO_ABOVE = 0, TO_BELOW = 1, TO_ALL = 2;
    /** Where a new slot's minutes come from. */
    public static final int FROM_THIS = 0, FROM_NEXT = 1, FROM_ALL = 2;

    private SlotOps() {
    }

    // ------------------------------------------------------------------ delete

    public static boolean canDelete(Routine r, int i) {
        return i >= 0 && i < r.blocks.size() && r.blocks.size() > 1;
    }

    /** The slot that gets the minutes for the given choice, or -1 for "spread over the others". */
    public static int receiver(Routine r, int i, int mode) {
        int n = r.blocks.size();
        if (mode == TO_ALL || n < 2) return -1;
        if (mode == TO_ABOVE) return i > 0 ? i - 1 : i + 1;
        return i < n - 1 ? i + 1 : i - 1;
    }

    /**
     * Deletes slot i and hands its minutes on. Returns false (and changes nothing) if it is the last slot.
     * The day keeps its length and, as far as the setting allows, its wake-up time.
     */
    public static boolean delete(Routine r, int i, int mode) {
        if (!canDelete(r, i)) return false;
        int oldStart = r.compute().start;
        int freed = r.blocks.get(i).mins;
        int target = receiver(r, i, mode);
        List<Routine.Block> rest = new ArrayList<>(r.blocks);
        rest.remove(i);
        int left = freed;
        if (target >= 0) {
            Routine.Block t = r.blocks.get(target);
            int give = Math.min(freed, 1440 - t.mins);
            t.mins += give;
            left -= give;
        }
        if (left > 0) spreadAdd(rest, left);
        r.blocks.remove(i);
        normalizeAnchor(r, oldStart);
        return true;
    }

    /** Adds minutes to the given slots in proportion to their length (sleep is left out when other slots exist). */
    private static void spreadAdd(List<Routine.Block> pool, int minutes) {
        List<Routine.Block> eligible = new ArrayList<>();
        for (Routine.Block b : pool) if (b.kind != Routine.SLEEP) eligible.add(b);
        if (eligible.isEmpty()) eligible.addAll(pool);
        long sum = 0;
        for (Routine.Block b : eligible) sum += b.mins;
        int m = eligible.size();
        int[] part = new int[m];
        long[] rem = new long[m];
        int given = 0;
        for (int k = 0; k < m; k++) {
            long num = (long) minutes * eligible.get(k).mins;
            part[k] = (int) (num / sum);
            rem[k] = num % sum;
            given += part[k];
        }
        int extra = minutes - given;
        while (extra > 0) {
            int best = -1;
            for (int k = 0; k < m; k++) {
                if (best < 0 || rem[k] > rem[best]) best = k;
            }
            part[best]++;
            rem[best] = -1;
            extra--;
        }
        for (int k = 0; k < m; k++) eligible.get(k).mins += part[k];
    }

    // ------------------------------------------------------------------ add

    /** The slot lending minutes for the given choice, or -1 for "all the others". */
    public static int lender(Routine r, int after, int from) {
        int n = r.blocks.size();
        if (from == FROM_ALL) return -1;
        if (from == FROM_NEXT && after + 1 < n) return after + 1;
        return after;
    }

    /** The longest new slot that can be added after slot {@code after} with the given source; 0 when there is no room. */
    public static int maxNewMinutes(Routine r, int after, int from) {
        int n = r.blocks.size();
        if (after < 0 || after >= n) return 0;
        int src = lender(r, after, from);
        if (src >= 0) return Math.max(0, r.blocks.get(src).mins - MIN_MINS);
        int cap = 0;
        for (Routine.Block b : eligibleLenders(r)) cap += Math.max(0, b.mins - MIN_MINS);
        return cap;
    }

    private static List<Routine.Block> eligibleLenders(Routine r) {
        List<Routine.Block> out = new ArrayList<>();
        for (Routine.Block b : r.blocks) if (b.kind != Routine.SLEEP) out.add(b);
        if (out.isEmpty()) out.addAll(r.blocks);
        return out;
    }

    /**
     * Puts a new slot right after slot {@code after}, taking its minutes from other slots. Returns the
     * inserted block, or null (and changes nothing) when there is not enough room.
     */
    public static Routine.Block insertAfter(Routine r, int after, Routine.Block nb, int from) {
        int need = nb.mins;
        if (need < 1 || need > maxNewMinutes(r, after, from)) return null;
        int oldStart = r.compute().start;
        int src = lender(r, after, from);
        if (src >= 0) {
            r.blocks.get(src).mins -= need;
        } else {
            takeSpread(eligibleLenders(r), need);
        }
        r.blocks.add(after + 1, nb);
        normalizeAnchor(r, oldStart);
        return nb;
    }

    /** Takes minutes from the given slots in proportion to how much each can spare (never below MIN_MINS). */
    private static void takeSpread(List<Routine.Block> pool, int minutes) {
        int m = pool.size();
        long[] cap = new long[m];
        long sum = 0;
        for (int k = 0; k < m; k++) {
            cap[k] = Math.max(0, pool.get(k).mins - MIN_MINS);
            sum += cap[k];
        }
        int[] part = new int[m];
        long[] rem = new long[m];
        int taken = 0;
        for (int k = 0; k < m; k++) {
            long num = (long) minutes * cap[k];
            part[k] = (int) (num / sum);
            rem[k] = num % sum;
            taken += part[k];
        }
        int extra = minutes - taken;
        while (extra > 0) {
            int best = -1;
            for (int k = 0; k < m; k++) {
                if (part[k] >= cap[k]) continue;
                if (best < 0 || rem[k] > rem[best]) best = k;
            }
            if (best < 0) break;
            part[best]++;
            rem[best] = -1;
            extra--;
        }
        for (int k = 0; k < m; k++) pool.get(k).mins -= part[k];
    }

    // ------------------------------------------------------------------ rests between tasks

    /**
     * Puts a short Rest between every pair of back-to-back Tasks, taking its minutes from the task
     * before it (or, if that one is too short, the task after it) so the day keeps its length.
     *
     * @return {added, notFitted}: how many rests were added and how many gaps had no room for one
     */
    public static int[] addRests(Routine r) {
        int added = 0;
        int notFitted = 0;
        for (int i = r.blocks.size() - 2; i >= 0; i--) {
            if (r.blocks.get(i).kind != Routine.TASK || r.blocks.get(i + 1).kind != Routine.TASK) continue;
            boolean done = false;
            for (int from = FROM_THIS; from <= FROM_NEXT && !done; from++) {
                for (int len = 10; len >= MIN_MINS && !done; len -= 5) {
                    done = insertAfter(r, i, new Routine.Block(Routine.REST, "Rest", len), from) != null;
                }
            }
            if (done) added++;
            else notFitted++;
        }
        return new int[]{added, notFitted};
    }

    // ------------------------------------------------------------------ the − / + buttons

    /**
     * The length after one tap of − or + ({@code delta} is -30, -5, 5 or 30): snaps to whole steps
     * (52 + 5 gives 55, 52 - 5 gives 50), never below 5 minutes (a shorter slot stays as it is) and
     * never above a full day.
     */
    public static int stepped(int mins, int delta) {
        if (delta == 0) return mins;
        int step = Math.abs(delta);
        int next;
        if (delta > 0) next = (mins / step + 1) * step;
        else next = mins % step == 0 ? mins - step : (mins / step) * step;
        return Math.max(Math.min(MIN_MINS, mins), Math.min(1440, next));
    }

    // ------------------------------------------------------------------ move

    /** Moves slot {@code from} so that it ends up at index {@code to}. */
    public static boolean move(Routine r, int from, int to) {
        int n = r.blocks.size();
        if (from < 0 || from >= n || to < 0 || to >= n || from == to) return false;
        Routine.Block b = r.blocks.remove(from);
        r.blocks.add(to, b);
        return true;
    }

    // ------------------------------------------------------------------ keeping the wake-up time

    /**
     * In "first task time" mode the day is pinned to the first Task. If a change leaves the routine with
     * no Task at all, the setting quietly becomes "wake-up time" at the wake-up the day had before, so
     * the whole day does not jump.
     */
    public static void normalizeAnchor(Routine r, int oldStart) {
        if (r.mode != Routine.MODE_TASK) return;
        for (Routine.Block b : r.blocks) if (b.kind == Routine.TASK) return;
        r.mode = Routine.MODE_WAKE;
        r.anchor = Routine.mod(oldStart, 1440);
    }
}
