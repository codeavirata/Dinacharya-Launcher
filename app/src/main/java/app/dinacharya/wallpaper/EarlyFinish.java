package app.dinacharya.wallpaper;

/**
 * Finishing the slot that is going on before its time. When it is possible and what the choices are: take a break for a while, start the
 * next slot now, start it after a while, or take the rest of the day off. The rest of the day does not move: the next slot simply begins
 * earlier and keeps its planned end, so it gets the time that was left over.
 *
 * <p>Every slot can be finished early. Three cases read differently on the screen: a slot like any other ("Finish early"), the slot
 * just before sleep (the person goes to sleep now: "Early sleep") and sleep itself (the person is up and the next day starts now:
 * "Early wake"). Uses no Android classes.
 */
public final class EarlyFinish {
    /** Fewest whole minutes left in the slot for finishing it early to mean anything. */
    public static final int MIN_ROOM = 3;

    /** The next slot is an ordinary one. */
    public static final int FINISH = 0;
    /** The next slot is sleep: going to sleep now instead of at the planned time. */
    public static final int SLEEP = 1;
    /** The slot going on is the night's sleep, and nothing follows it today: the next day starts now instead of at the alarm. */
    public static final int WAKE = 2;

    private EarlyFinish() {
    }

    /** The slot that can be finished, the one after it, and how much time there is. */
    public static final class Offer {
        public final int kind;
        /** True when the slot going on is sleep (the button then says "Early wake"). */
        public final boolean curIsSleep;
        /** The schedule day the slot going on belongs to (for {@link #WAKE} the day that begins is the one after). */
        public final String day;
        public final int cur;
        /** The slot that begins (its place in the day it belongs to; for {@link #WAKE} in the next day's plan). */
        public final int next;
        public final int srcCur;
        public final int srcNext;
        public final String titleCur;
        public final String titleNext;
        /** When the slot was to end, which is when the next one was to begin (milliseconds). */
        public final long plannedEndMs;
        /** When the next slot ends. */
        public final long nextEndMs;
        /** Whole minutes from now until the planned end. */
        public final int roomMin;

        Offer(int kind, boolean curIsSleep, String day, int cur, int next, Routine.Block a, Routine.Block b, long plannedEndMs, long nextEndMs, int roomMin) {
            this.kind = kind;
            this.day = day;
            this.curIsSleep = curIsSleep;
            this.cur = cur;
            this.next = next;
            this.srcCur = a.src;
            this.srcNext = b.src;
            this.titleCur = a.title;
            this.titleNext = b.title;
            this.plannedEndMs = plannedEndMs;
            this.nextEndMs = nextEndMs;
            this.roomMin = roomMin;
        }

        /**
         * The longest break or wait that still ends before the next slot was due to begin: one minute less than the time left, so
         * the two alarms never fall on the same minute.
         */
        public int maxRestMin() {
            return Math.max(0, roomMin - 1);
        }

        /** The words on the button: "Early sleep" before sleep, "Early wake" for sleep, "Finish early" for the rest. */
        public String label() {
            if (kind == SLEEP) return "Early sleep";
            if (curIsSleep) return "Early wake";
            return "Finish early";
        }

        /** True when the choices of a break or a later start make sense (only between two ordinary slots). */
        public boolean canWait() {
            return kind == FINISH && !curIsSleep && maxRestMin() >= 1;
        }
    }

    /**
     * What finishing early offers at {@code nowMs} in the day as it is going ({@link LivePlan} applied), or null when it cannot be done:
     * no slot of the plan is running, it was already carried on or cut, nothing real follows it, or there is hardly any time left.
     * Sleep and the slot before it can be finished too; the night's sleep needs {@link #of(Plan.DayPlan, Plan.DayPlan, long)}.
     */
    public static Offer of(Plan.DayPlan live, long nowMs) {
        return of(live, null, nowMs);
    }

    /**
     * Same, with the plan of the day after: the night's sleep is the last slot of its day, and finishing it early means the next day
     * starts now. {@code nextDay} is that day's plan as it stands (null when not known).
     */
    public static Offer of(Plan.DayPlan live, Plan.DayPlan nextDay, long nowMs) {
        if (live == null || live.cur < 0) return null;
        int n = live.sc.slots.size();
        Routine.Block a = live.sc.slots.get(live.cur).block;
        if (a.src < 0 || a.skipped || a.live == Routine.LIVE_CARRIED || a.live == Routine.LIVE_CUT) return null;
        boolean aSleep = a.kind == Routine.SLEEP;
        // the next slot of the plan; ones that were taken off are passed over
        int j = live.cur + 1;
        while (j < n && live.sc.slots.get(j).block.skipped && live.sc.slots.get(j).block.src >= 0) j++;
        if (j >= n) return aSleep ? wake(live, nextDay, a, nowMs) : null;
        Routine.Block b = live.sc.slots.get(j).block;
        if (b.src < 0 || b.skipped || b.live == Routine.LIVE_BREAK || b.live == Routine.LIVE_WAIT) return null;
        long end = live.slotEndMs(live.cur);
        int room = (int) ((end - nowMs) / 60000L);
        if (room < MIN_ROOM) return null;
        int kind = b.kind == Routine.SLEEP && !aSleep ? SLEEP : FINISH;
        return new Offer(kind, aSleep, live.day.toString(), live.cur, j, a, b, end, live.slotEndMs(j), room);
    }

    private static Offer wake(Plan.DayPlan live, Plan.DayPlan nextDay, Routine.Block a, long nowMs) {
        if (nextDay == null || nextDay.sc.slots.isEmpty()) return null;
        Routine.Block b = nextDay.sc.slots.get(0).block;
        if (b.src < 0 || b.skipped) return null;
        long start = nextDay.startMs;
        int room = (int) ((start - nowMs) / 60000L);
        if (room < MIN_ROOM || room > Plan.MAX_SHIFT) return null;
        return new Offer(WAKE, true, live.day.toString(), live.cur, 0, a, b, start, nextDay.slotEndMs(0), room);
    }

    /**
     * The minutes by which a day that was to start at {@code plannedStartMs} starts earlier when it starts at {@code nowMs}: rounded up,
     * so the day begins on the minute at or just before now and is already the running day.
     */
    public static int minutesEarlier(long plannedStartMs, long nowMs) {
        long ms = plannedStartMs - nowMs;
        if (ms <= 0) return 0;
        return (int) Math.min(Plan.MAX_SHIFT, (ms + 59999L) / 60000L);
    }
}
