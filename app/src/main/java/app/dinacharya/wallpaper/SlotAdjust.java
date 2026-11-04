package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * Giving a running slot more or less time for today, with the minutes coming from (or going to) another slot of the day. The rules are the
 * ones the whole app keeps: a task is never made shorter than 30 minutes, any other slot never shorter than 10, Sleep stays between 4 and 12
 * hours. A slot that is already shorter than its limit (an older routine) is left as it is: it cannot give more, it can still grow.
 * Uses no Android classes.
 */
public final class SlotAdjust {
    public static final int TASK_MIN = 30;
    public static final int OTHER_MIN = 10;
    public static final int STEP = SlotPlay.STEP;
    /** Minutes that must be left of the running slot after a cut, so its end is still ahead. */
    public static final int KEEP_LEFT = 5;

    private SlotAdjust() {
    }

    /** The fewest minutes a slot of this type may have. */
    public static int minFor(int kind) {
        return kind == Routine.SLEEP ? SlotOps.SLEEP_MIN : kind == Routine.TASK ? TASK_MIN : OTHER_MIN;
    }

    /** The fewest minutes a slot of this type with {@code mins} now may be taken down to (never more than it has). */
    public static int floorOf(int kind, int mins) {
        return Math.min(minFor(kind), mins);
    }

    /** The most minutes a slot of this type with {@code mins} now may grow to (only Sleep has a top). */
    public static int ceilOf(int kind, int mins) {
        return kind == Routine.SLEEP ? Math.max(SlotOps.SLEEP_MAX, mins) : 1440;
    }

    private static int down(int v) {
        return Math.max(0, (v / STEP) * STEP);
    }

    /** A slot that can swap minutes with the running one. */
    public static final class Option {
        /** Its place in the plan's slots. */
        public final int index;
        public final int src;
        public final String title;
        public final int kind;
        public final int mins;
        /** Minutes of the day it begins and ends (the plan's own minutes). */
        public final int start;
        public final int end;
        /** Most it can give and still keep its limit, and most it can take and still keep it (whole steps). */
        public final int canGive;
        public final int canTake;

        Option(int index, Routine.Slot s) {
            Routine.Block b = s.block;
            this.index = index;
            this.src = b.src;
            this.title = b.title.isEmpty() ? Routine.KIND_NAMES[b.kind] : b.title;
            this.kind = b.kind;
            this.mins = s.end - s.start;
            this.start = s.start;
            this.end = s.end;
            this.canGive = down(mins - floorOf(kind, mins));
            this.canTake = down(ceilOf(kind, mins) - mins);
        }
    }

    /** What can be done to the running slot of a plan right now. */
    public static final class Choices {
        public final int index;
        public final int mins;
        public final int kind;
        /** Minutes that are still ahead in the running slot. */
        public final int left;
        /** Most the running slot can lose (to keep its own limit and a few minutes ahead) and most it can gain, before the other slot's limit. */
        public final int ownCanShrink;
        public final int ownCanGrow;
        public final List<Option> others = new ArrayList<>();

        Choices(int index, Routine.Slot s, int left) {
            Routine.Block b = s.block;
            this.index = index;
            this.mins = s.end - s.start;
            this.kind = b.kind;
            this.left = left;
            this.ownCanShrink = down(Math.min(mins - floorOf(kind, mins), left - KEEP_LEFT));
            this.ownCanGrow = down(ceilOf(kind, mins) - mins);
        }

        /** The most the running slot can grow by when {@code o} gives the minutes. */
        public int maxGrow(Option o) {
            return down(Math.min(ownCanGrow, o.canGive));
        }

        /** The most the running slot can shrink by when {@code o} takes the minutes. */
        public int maxShrink(Option o) {
            return down(Math.min(ownCanShrink, o.canTake));
        }

        public boolean canGrow() {
            for (Option o : others) if (maxGrow(o) >= STEP) return true;
            return false;
        }

        public boolean canShrink() {
            for (Option o : others) if (maxShrink(o) >= STEP) return true;
            return false;
        }
    }

    /**
     * What the running slot of {@code p} (the saved plan of the day, with today's changes) can be given or give up at {@code nowMs}, or
     * null when it cannot be adjusted: nothing is running, it is taken off, or nothing real follows it.
     */
    public static Choices of(Plan.DayPlan p, long nowMs) {
        if (p == null || p.cur < 0 || p.cur >= p.sc.slots.size()) return null;
        Routine.Slot s = p.sc.slots.get(p.cur);
        if (s.block.src < 0 || s.block.skipped || s.block.isGap()) return null;
        int left = (int) ((p.slotEndMs(p.cur) - nowMs) / 60000L);
        if (left < 1) return null;
        Choices c = new Choices(p.cur, s, left);
        for (int i = p.cur + 1; i < p.sc.slots.size(); i++) {
            Routine.Slot o = p.sc.slots.get(i);
            if (o.block.src < 0 || o.block.skipped || o.block.isGap()) continue;
            c.others.add(new Option(i, o));
        }
        if (c.others.isEmpty()) return null;
        return c;
    }
}
