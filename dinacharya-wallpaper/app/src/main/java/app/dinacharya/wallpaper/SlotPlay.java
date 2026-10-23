package app.dinacharya.wallpaper;

/**
 * The small rules behind "take a break", "start after N minutes" and "continue the slot before": how long each can be and where it
 * ends. Everything stays inside the one slot, so the slots after it never move. Uses no Android classes.
 */
public final class SlotPlay {
    private static final long MIN = 60000L;
    /** The steps the minute buttons move by, and the default for each. */
    public static final int STEP = 5, DEFAULT_START_DELAY = 10, DEFAULT_CONTINUE = 10, DEFAULT_WAKE_DELAY = 5;
    /** The ready-made break lengths. */
    public static final int[] BREAKS = {5, 10, 15, 30};

    private SlotPlay() {
    }

    /** The most minutes that can be added from now before the slot is over (always at least 0). */
    public static int roomMinutes(long nowMs, long slotEndMs) {
        return (int) Math.max(0, (slotEndMs - nowMs) / MIN);
    }

    /** When a break of {@code minutes} from now ends: at most the end of the slot. */
    public static long breakEnd(long nowMs, int minutes, long slotEndMs) {
        return Math.min(nowMs + Math.max(1, minutes) * MIN, slotEndMs);
    }

    /**
     * When the slot starts after being put off by {@code minutes} from now. At least one minute of the slot is left, so a put-off
     * start never swallows the slot.
     */
    public static long startAfter(long nowMs, int minutes, long slotEndMs) {
        long t = nowMs + Math.max(1, minutes) * MIN;
        long last = slotEndMs - MIN;
        return Math.max(Math.min(t, last), Math.min(nowMs, last));
    }

    /** When the slot before stops being carried on: {@code minutes} from now, never past the end of the slot that is starting. */
    public static long continueUntil(long nowMs, int minutes, long slotEndMs) {
        return Math.min(nowMs + Math.max(1, minutes) * MIN, Math.max(nowMs, slotEndMs - MIN));
    }

    /** A step of the minutes button: up or down by {@link #STEP}, kept between one step and what fits. */
    public static int stepMinutes(int current, boolean up, int maxFit) {
        int v = up ? current + STEP : current - STEP;
        int hi = Math.max(STEP, (maxFit / STEP) * STEP);
        return Math.max(STEP, Math.min(hi, v));
    }

    /** The slot before slot {@code index} that can be carried on, or -1: it has to be a real slot of the plan, not taken off, and not sleep. */
    public static int previousIndex(Plan.DayPlan p, int index) {
        int i = index - 1;
        if (i < 0 || i >= p.sc.slots.size()) return -1;
        Routine.Block b = p.sc.slots.get(i).block;
        if (b.skipped || b.src < 0 || b.kind == Routine.SLEEP) return -1;
        return i;
    }

    /** Index of the slot with this number in the day's plan, or -1. */
    public static int indexOfSrc(Plan.DayPlan p, int src) {
        for (int i = 0; i < p.sc.slots.size(); i++) if (p.sc.slots.get(i).block.src == src) return i;
        return -1;
    }

    /** Whether a break can be taken in this slot now: a slot of the plan is running and it is not sleep and not taken off. */
    public static boolean canBreak(Plan.DayPlan p) {
        if (p.cur < 0 || p.cur >= p.sc.slots.size()) return false;
        Routine.Block b = p.sc.slots.get(p.cur).block;
        return !b.skipped && b.src >= 0 && b.kind != Routine.SLEEP;
    }
}
