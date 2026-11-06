package app.dinacharya.wallpaper;

/**
 * "Change plan" on a slot that is already running: the new plan begins at the moment of the change, not at the slot's planned start.
 *
 * <p>Take a slot planned 09:30 to 11:00 (90 minutes). The alarm rings at 09:30 and the person answers at 09:50, "Gym for 30 minutes".
 * The 20 minutes before the answer are a delayed start (or "No response") and stay out of Gym: Gym is 09:50 to 10:20, and the last
 * 40 minutes go back to the saved plan. Counted from the planned start, Gym would have been 09:30 to 10:00, with only 10 minutes left
 * of it after the answer.
 *
 * <p>The plan stores this as a start offset ({@link #lead}, minutes after the slot's planned start) and the length of the new plan.
 * A change made before the slot begins (or in its first half minute) has no offset and behaves as it always did. Uses no Android
 * classes, so the rules are tested on their own.
 */
public final class ReplaceFromNow {
    private static final long MIN = 60000L;

    /** The slot's planned length in minutes (at least 1). */
    public final int slotMins;
    /** Minutes of the slot that went by before this moment, to the nearest minute; 0 when it has not begun. Always leaves a minute. */
    public final int lead;
    /** Minutes from the start of the new plan to the end of the slot (at least 1). */
    public final int room;
    /** When the new plan begins. */
    public final long startMs;
    /** When the slot ends. */
    public final long endMs;

    private ReplaceFromNow(long slotStartMs, long slotEndMs, long nowMs) {
        this.endMs = slotEndMs;
        this.slotMins = (int) Math.max(1L, Math.round((slotEndMs - slotStartMs) / (double) MIN));
        this.lead = nowMs <= slotStartMs ? 0 : (int) Math.min(slotMins - 1L, Math.round((nowMs - slotStartMs) / (double) MIN));
        this.room = slotMins - lead;
        this.startMs = slotStartMs + lead * MIN;
    }

    /** The timing of a change made at {@code nowMs} to the slot planned from {@code slotStartMs} to {@code slotEndMs}. */
    public static ReplaceFromNow of(long slotStartMs, long slotEndMs, long nowMs) {
        return new ReplaceFromNow(slotStartMs, Math.max(slotEndMs, slotStartMs + MIN), nowMs);
    }

    /** True when part of the slot has gone by already, so the new plan starts later than the slot did. */
    public boolean late() {
        return lead > 0;
    }

    /**
     * What to store for the length of the new plan, given what the person asked for ({@code asked} 0 for all that is left of the slot).
     * Before the slot began that is what it always was (0 for the whole slot); after, it is always a number of minutes, never more than
     * what is left.
     */
    public int stored(int asked) {
        if (lead == 0) return asked <= 0 || asked >= slotMins ? 0 : asked;
        int m = asked <= 0 ? room : Math.min(asked, room);
        return Math.max(1, m);
    }

    /** The minutes the new plan lasts, for a stored length (0 meaning the whole slot from its start). */
    public int minutes(int stored) {
        return stored <= 0 ? room : Math.min(stored, room);
    }

    /** When the new plan ends, for a stored length. */
    public long toMs(int stored) {
        return startMs + minutes(stored) * MIN;
    }

    /** True when some of the slot is left after the new plan, which then goes back to the saved plan. */
    public boolean restBack(int stored) {
        return minutes(stored) < room;
    }

    /** What a change came to: when the new plan begins and ends, and whether the rest of the slot goes back to the saved plan. */
    public static final class Placed {
        public final ReplaceFromNow when;
        /** The length that was stored (0: the whole slot from its start). */
        public final int stored;

        Placed(ReplaceFromNow when, int stored) {
            this.when = when;
            this.stored = stored;
        }

        public long fromMs() {
            return when.startMs;
        }

        public long toMs() {
            return when.toMs(stored);
        }

        /** The new plan began later than the slot did. */
        public boolean late() {
            return when.late();
        }

        public boolean restBack() {
            return when.restBack(stored);
        }
    }

    /** What is stored and how it ends for what the person asked for ({@code asked} 0 for all that is left of the slot). */
    public Placed place(int asked) {
        return new Placed(this, stored(asked));
    }

    /** The planned start and end of the slot {@code src} in a plan (first to last part of it), or null when it is not in it. */
    public static long[] span(Plan.DayPlan dp, int src) {
        if (dp == null || src < 0) return null;
        int first = -1;
        int last = -1;
        for (int i = 0; i < dp.sc.slots.size(); i++) {
            if (dp.sc.slots.get(i).block.src != src) continue;
            if (first < 0) first = i;
            last = i;
        }
        if (first < 0) return null;
        return new long[]{dp.slotStartMs(first), dp.slotEndMs(last)};
    }
}
