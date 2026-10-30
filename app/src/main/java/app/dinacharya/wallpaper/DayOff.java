package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Taking the rest of a schedule day off. From the moment it is chosen no alarm of that day rings (not the slots, not the bedtime question,
 * not a break or a later start) until the person makes a new schedule for the day. A new schedule means the day's slots or start time
 * changed after the day off was chosen, which is how the app knows the person has planned the day again. The next day is not affected.
 * Uses no Android classes.
 */
public final class DayOff {
    private DayOff() {
    }

    /** One slot the day off takes away. */
    public static final class Pick {
        public final int index;
        public final int src;
        public final String title;

        Pick(int index, int src, String title) {
            this.index = index;
            this.src = src;
            this.title = title;
        }
    }

    /** A short signature of the schedule the day follows: its start, and every slot's kind and name (and length, except for sleep). */
    public static String sig(Routine main, LocalDate day) {
        Routine r = main.forDay(day);
        StringBuilder sb = new StringBuilder();
        sb.append(r.compute().start).append('#').append(r.mode);
        for (Routine.Block b : r.blocks) {
            sb.append('|').append(b.kind).append(':');
            if (b.kind != Routine.SLEEP) sb.append(b.mins);
            sb.append(':').append(b.title);
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    /** True while the day {@code day} is off: chosen, and the day's schedule is still the one it was chosen on. */
    public static boolean active(Routine main, AlarmState st, LocalDate day) {
        if (st == null || day == null || st.offDay.isEmpty() || !st.offDay.equals(day.toString())) return false;
        return st.offSig.equals(sig(main, day));
    }

    /** True while the day that was taken off is still off (the schedule has not been changed since). */
    public static boolean pending(Routine main, AlarmState st) {
        if (st == null || st.offDay.isEmpty()) return false;
        try {
            return active(main, st, LocalDate.parse(st.offDay));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True while the day is off and still running at {@code nowMs}; a day off ends by itself when its schedule day is over. */
    public static boolean pending(Routine main, AlarmState st, long nowMs) {
        return pending(main, st) && (st.offEnd <= 0 || nowMs < st.offEnd);
    }

    /** True when a day off is on record whose day is over at {@code nowMs}: it is to be forgotten. */
    public static boolean expired(AlarmState st, long nowMs) {
        return st != null && !st.offDay.isEmpty() && st.offEnd > 0 && nowMs >= st.offEnd;
    }

    /** Takes the rest of {@code day} off from {@code fromMs}; {@code endMs} is when that schedule day ends. */
    public static void start(Routine main, AlarmState st, LocalDate day, long fromMs, long endMs) {
        start(main, st, day, fromMs);
        st.offEnd = Math.max(0L, endMs);
    }

    /** Takes the rest of {@code day} off from {@code fromMs}. Timers that were waiting (a break, a later start, a bedtime question) are dropped. */
    public static void start(Routine main, AlarmState st, LocalDate day, long fromMs) {
        st.offDay = day.toString();
        st.offFrom = fromMs;
        st.offEnd = 0L;
        st.offSig = sig(main, day);
        st.planAt = 0;
        st.breakFrom = 0;
        st.breakTo = 0;
        st.againAt = 0;
        st.againWake = false;
        st.reaskAt = 0;
    }

    /** Remembers when to ask about planning the day ("plan later"); 0 means do not ask. */
    public static void planLater(AlarmState st, long atMs) {
        st.planAt = Math.max(0L, atMs);
    }

    /** The day is no longer off. */
    public static void end(AlarmState st) {
        st.offDay = "";
        st.offFrom = 0;
        st.offEnd = 0L;
        st.offSig = "";
        st.planAt = 0;
    }

    /**
     * The stretches of the day that were off, from the day-off markers in the history: each runs from the moment the day was taken off to the
     * moment it was ended (or without end: {@link Long#MAX_VALUE}). {@code dayEvents} are the events of one schedule day, in any order.
     */
    public static List<long[]> periods(List<Ev> dayEvents) {
        List<Ev> marks = new ArrayList<>();
        for (Ev e : dayEvents) if (Ev.DAYOFF.equals(e.type)) marks.add(e);
        marks.sort((x, y) -> Long.compare(x.ts, y.ts));
        List<long[]> out = new ArrayList<>();
        long[] open = null;
        for (Ev e : marks) {
            boolean ends = "0".equals(e.a);
            if (!ends && open == null) {
                open = new long[]{e.ts, Long.MAX_VALUE};
                out.add(open);
            } else if (ends && open != null) {
                open[1] = e.ts;
                open = null;
            }
        }
        return out;
    }

    /** True when {@code ms} falls inside one of the stretches {@link #periods} gives. */
    public static boolean within(List<long[]> periods, long ms) {
        for (long[] p : periods) if (ms >= p[0] && ms < p[1]) return true;
        return false;
    }

    /**
     * Whether the day that was taken off has been planned again since: true when a day off is on record but the day's schedule is no longer
     * the one it was chosen on. The caller then writes the end of the day off to the history and clears it ({@link #end}).
     */
    public static boolean replanned(Routine main, AlarmState st) {
        if (st == null || st.offDay.isEmpty()) return false;
        try {
            return !active(main, st, LocalDate.parse(st.offDay));
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * The slots of {@code p} that the day off takes away from {@code fromMs}: every slot that has not ended and is not sleep or already
     * taken off. They are what gets a "taken off" answer in the history, so the report counts them as off and not as missed.
     */
    public static List<Pick> removed(Plan.DayPlan p, long fromMs) {
        List<Pick> out = new ArrayList<>();
        for (int i = 0; i < p.sc.slots.size(); i++) {
            Routine.Block b = p.sc.slots.get(i).block;
            if (b.kind == Routine.SLEEP || b.skipped || b.src < 0) continue;
            if (p.slotEndMs(i) <= fromMs) continue;
            out.add(new Pick(i, b.src, b.title));
        }
        return out;
    }
}
