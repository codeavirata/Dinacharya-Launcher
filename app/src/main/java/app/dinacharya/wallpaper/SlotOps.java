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

    /** Sleep is never shorter than 4 hours or longer than 12 hours. */
    public static final int SLEEP_MIN = 240, SLEEP_MAX = 720;

    private SlotOps() {
    }

    /** True when the routine ends with its Sleep slot. */
    public static boolean hasSleep(Routine r) {
        int n = r.blocks.size();
        return n >= 2 && r.blocks.get(n - 1).kind == Routine.SLEEP;
    }

    /** The wake-up slot (the first) and the Sleep slot (the last) are permanent: they cannot be deleted or moved. */
    public static boolean isPermanent(Routine r, int i) {
        int n = r.blocks.size();
        if (i < 0 || i >= n) return false;
        return i == 0 || (i == n - 1 && r.blocks.get(i).kind == Routine.SLEEP);
    }

    /** Minutes Sleep can give away and still be 4 hours. */
    public static int sleepCanGive(Routine r) {
        return hasSleep(r) ? Math.max(0, r.blocks.get(r.blocks.size() - 1).mins - SLEEP_MIN) : 0;
    }

    /** Minutes Sleep can receive and still be 12 hours. */
    public static int sleepCanTake(Routine r) {
        return hasSleep(r) ? Math.max(0, SLEEP_MAX - r.blocks.get(r.blocks.size() - 1).mins) : 0;
    }

    // ------------------------------------------------------------------ delete

    public static boolean canDelete(Routine r, int i) {
        return i >= 0 && i < r.blocks.size() && r.blocks.size() > 1 && !isPermanent(r, i);
    }

    /** Whether Sleep has room for the minutes of slot i (it stays 12 hours or less). Always true when there is no Sleep slot. */
    public static boolean canDeleteNow(Routine r, int i) {
        if (!canDelete(r, i)) return false;
        return !hasSleep(r) || r.blocks.get(i).mins <= sleepCanTake(r);
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
        if (hasSleep(r)) {
            if (freed > sleepCanTake(r)) return false;
            r.blocks.get(r.blocks.size() - 1).mins += freed;
            r.blocks.remove(i);
            normalizeAnchor(r, oldStart);
            return true;
        }
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

    // ------------------------------------------------------------------ resize with cascade

    /** What {@link #resize} did. */
    public static final class Resized {
        /** The length slot i really got (may be less than asked when there is no room). */
        public int applied;
        /** True when the asked length did not fit and was capped. */
        public boolean capped;
        /** True when the sleep slot ended up shorter than 4 hours or longer than 12 hours. */
        public boolean sleepWarn;
    }

    /** Longest slot i can become while the day keeps its length (slots below give first, then slots above). */
    public static int maxMinutes(Routine r, int i) {
        if (hasSleep(r)) {
            int n = r.blocks.size();
            Routine.Block b = r.blocks.get(i);
            if (i == n - 1) return Math.min(SLEEP_MAX, b.mins + Math.max(0, r.blocks.get(n - 2).mins - MIN_MINS));
            return b.mins + sleepCanGive(r);
        }
        int cap = r.blocks.get(i).mins;
        for (int k = 0; k < r.blocks.size(); k++) {
            if (k != i) cap += Math.max(0, r.blocks.get(k).mins - MIN_MINS);
        }
        return Math.min(1440, cap);
    }

    /**
     * Gives slot i the length {@code want}; the difference is taken from (or handed to) the slots right
     * below it, nearest first, so the day keeps its length. If the slots below cannot cover it the slots
     * above help, nearest first. The last slot (normally Sleep) simply uses the slots above. Sleep is
     * never refused a length, but {@code sleepWarn} says when it ends up outside 4 to 12 hours.
     */
    public static Resized resize(Routine r, int i, int want) {
        Resized out = new Resized();
        int n = r.blocks.size();
        Routine.Block b = r.blocks.get(i);
        if (n < 2) {
            out.applied = b.mins;
            out.capped = want != b.mins;
            return out;
        }
        want = Math.max(MIN_MINS, want);
        if (hasSleep(r)) return resizeWithSleep(r, i, want, out);
        int cap = maxMinutes(r, i);
        if (want > cap) {
            want = cap;
            out.capped = true;
        }
        int delta = want - b.mins;
        if (delta > 0) {
            int need = delta;
            for (int k = i + 1; k < n && need > 0; k++) need -= takeFrom(r.blocks.get(k), need);
            for (int k = i - 1; k >= 0 && need > 0; k--) need -= takeFrom(r.blocks.get(k), need);
        } else if (delta < 0) {
            int t = i < n - 1 ? i + 1 : i - 1;
            r.blocks.get(t).mins += -delta;
        }
        b.mins = want;
        out.applied = want;
        out.sleepWarn = sleepOutOfRange(r);
        return out;
    }

    /** With a Sleep slot at the end, every other slot gives to or takes from Sleep; Sleep itself trades with the slot above it. */
    private static Resized resizeWithSleep(Routine r, int i, int want, Resized out) {
        int n = r.blocks.size();
        Routine.Block b = r.blocks.get(i);
        if (i == n - 1) {
            Routine.Block above = r.blocks.get(n - 2);
            int hi = Math.min(SLEEP_MAX, b.mins + Math.max(0, above.mins - MIN_MINS));
            int lo = Math.max(SLEEP_MIN, b.mins - (1440 - above.mins));
            int w = Math.max(Math.min(lo, b.mins), Math.min(Math.max(hi, b.mins), want));
            if (w != want) out.capped = true;
            above.mins -= w - b.mins;
            b.mins = w;
        } else {
            Routine.Block sleep = r.blocks.get(n - 1);
            int delta = want - b.mins;
            int room = delta > 0 ? sleepCanGive(r) : sleepCanTake(r);
            int move = Math.min(Math.abs(delta), room);
            if (move < Math.abs(delta)) out.capped = true;
            int d = delta > 0 ? move : -move;
            b.mins += d;
            sleep.mins -= d;
        }
        out.applied = b.mins;
        out.sleepWarn = sleepOutOfRange(r);
        return out;
    }

    private static int takeFrom(Routine.Block x, int need) {
        int t = Math.min(need, Math.max(0, x.mins - MIN_MINS));
        x.mins -= t;
        return t;
    }

    /** True when the last slot is Sleep and is shorter than 4 hours or longer than 12 hours. */
    public static boolean sleepOutOfRange(Routine r) {
        if (r.blocks.isEmpty()) return false;
        Routine.Block l = r.blocks.get(r.blocks.size() - 1);
        return l.kind == Routine.SLEEP && (l.mins < 240 || l.mins > 720);
    }

    /** The longest slot that can be added after slot {@code after}: every other slot can spare down to 5 minutes. */
    public static int maxAddMinutes(Routine r, int after) {
        if (after < 0 || after >= r.blocks.size()) return 0;
        if (hasSleep(r)) return after >= r.blocks.size() - 1 ? 0 : Math.min(1435, sleepCanGive(r));
        int cap = 0;
        for (Routine.Block b : r.blocks) cap += Math.max(0, b.mins - MIN_MINS);
        return Math.min(1435, cap);
    }

    /**
     * Adds a slot right after slot {@code after}. Its minutes come from the slots below it, nearest first,
     * then from the slots above (nearest first), so the day keeps its length. Returns null if it does not fit.
     */
    public static Routine.Block insertBelow(Routine r, int after, Routine.Block nb) {
        int need = nb.mins;
        if (need < 1 || need > maxAddMinutes(r, after)) return null;
        int oldStart = r.compute().start;
        int n = r.blocks.size();
        if (hasSleep(r)) {
            r.blocks.get(n - 1).mins -= need;
            r.blocks.add(after + 1, nb);
            normalizeAnchor(r, oldStart);
            return nb;
        }
        int left = need;
        for (int k = after + 1; k < n && left > 0; k++) left -= takeFrom(r.blocks.get(k), left);
        for (int k = after; k >= 0 && left > 0; k--) left -= takeFrom(r.blocks.get(k), left);
        r.blocks.add(after + 1, nb);
        normalizeAnchor(r, oldStart);
        return nb;
    }

    // ------------------------------------------------------------------ move

    /** Moves slot {@code from} so that it ends up at index {@code to}. */
    public static boolean move(Routine r, int from, int to) {
        int n = r.blocks.size();
        if (from < 0 || from >= n || to < 0 || to >= n || from == to) return false;
        if (isPermanent(r, from) || isPermanent(r, to)) return false;
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
