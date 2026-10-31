package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stretches inside a slot where the slot's own activity was not going on: a break, a start that was put off, and the beginning of
 * the slot given to the slot before it ("continue the previous slot"). All of them stay inside the one slot, so the slots after it never
 * move. Worked out from the history of what you answered. Uses no Android classes.
 */
public final class SlotGaps {
    private SlotGaps() {
    }

    /** What happened inside one slot. All times are milliseconds and clipped to the slot and to "now". */
    public static final class Result {
        /** The gaps, in order, not overlapping. */
        public final List<long[]> gaps = new ArrayList<>();
        public long breakMs;
        public long delayMs;
        /** The start of this slot that went to the slot before. */
        public long lentMs;
        /** Time after this slot's end that this slot went on, because it was carried into the next slot. */
        public long extMs;
        /** When this slot really began and ended: earlier than planned when it was started or finished early, else the planned times. */
        public long effStart;
        public long effEnd;
        /** Minutes (as milliseconds) before the planned start that already belonged to this slot, and the planned end that it gave back. */
        public long preMs;
        public long cutMs;

        /** All the gap time together (an overlap of two kinds counts once). */
        public long gapMs() {
            long t = 0;
            for (long[] g : gaps) t += g[1] - g[0];
            return t;
        }
    }

    /** Sorts the intervals and joins those that touch or overlap. Empty or backwards ones are dropped. */
    public static List<long[]> merge(List<long[]> in) {
        List<long[]> list = new ArrayList<>();
        for (long[] x : in) if (x[1] > x[0]) list.add(new long[]{x[0], x[1]});
        list.sort(Comparator.comparingLong(x -> x[0]));
        List<long[]> out = new ArrayList<>();
        for (long[] x : list) {
            if (!out.isEmpty() && x[0] <= out.get(out.size() - 1)[1]) {
                long[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], x[1]);
            } else {
                out.add(x);
            }
        }
        return out;
    }

    /** What is left of [from, to) once the gaps are taken out. */
    public static List<long[]> complement(long from, long to, List<long[]> gaps) {
        List<long[]> out = new ArrayList<>();
        long at = from;
        for (long[] g : gaps) {
            long a = Math.max(g[0], from);
            long b = Math.min(g[1], to);
            if (b <= a) continue;
            if (a > at) out.add(new long[]{at, a});
            at = Math.max(at, b);
        }
        if (to > at) out.add(new long[]{at, to});
        return out;
    }

    private static int toInt(String s, int dflt) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    /** The events of one kind for one slot of the day, the last one written for each start time (a break that ended early is written twice). */
    private static List<Ev> latest(List<Ev> dayEvents, String type) {
        Map<Long, Ev> byTs = new LinkedHashMap<>();
        for (Ev e : dayEvents) if (type.equals(e.type)) byTs.put(e.ts, e);
        return new ArrayList<>(byTs.values());
    }

    /**
     * @param dayEvents everything in the history for this schedule day
     * @param src       the slot's number in its routine (the slot with no number, a made-up one, has no gaps)
     * @param start     when the slot starts
     * @param end       when the slot ends
     * @param clip      "now": nothing after it has happened yet
     */
    public static Result of(List<Ev> dayEvents, int src, long start, long end, long clip) {
        Result r = new Result();
        r.effStart = start;
        r.effEnd = end;
        if (src < 0) return r;
        // A slot started before its time begins earlier; a slot finished early ends earlier. The latest answer of each kind counts.
        Ev began = null;
        Ev finished = null;
        for (Ev e : dayEvents) {
            boolean skip = Ev.SKIP.equals(e.type);   // a slot skipped when it began: the one named in b begins at that moment
            if (!skip && !Ev.EARLY.equals(e.type)) continue;
            if ((skip ? toInt(e.b, -1) : e.src) == src && (began == null || e.ts >= began.ts)) began = e;
            if (!skip && toInt(e.b, -1) == src && (finished == null || e.ts >= finished.ts)) finished = e;
        }
        if (began != null) {
            long b = began.aLong(start);
            if (b > 0 && b < start) r.effStart = b;
        }
        if (finished != null) {
            long c = finished.aLong(end);
            if (c > start && c < end) r.effEnd = c;
        }
        // The day taken off while this slot was going on: it ends there.
        for (long[] period : DayOff.periods(dayEvents)) {
            if (period[0] > r.effStart && period[0] < r.effEnd) r.effEnd = period[0];
        }
        long stop = Math.min(r.effEnd, clip);
        r.preMs = Math.max(0, Math.min(start, clip) - r.effStart);
        r.cutMs = Math.max(0, Math.min(end, clip) - Math.max(r.effEnd, start));
        List<long[]> breaks = new ArrayList<>();
        for (Ev e : latest(dayEvents, Ev.BREAK)) {
            if (e.src != src) continue;
            long a = Math.max(e.ts, r.effStart);
            long b = Math.min(e.aLong(0), stop);
            if (b > a) breaks.add(new long[]{a, b});
        }
        List<long[]> delays = new ArrayList<>();
        List<long[]> lent = new ArrayList<>();
        List<long[]> carried = new ArrayList<>();
        // the last answer about this slot's late start is the one that counts (it can end the wait early)
        Ev lastDelay = null;
        for (Ev e : dayEvents) if (Ev.DELAY.equals(e.type) && e.src == src && (lastDelay == null || e.ts >= lastDelay.ts)) lastDelay = e;
        if (lastDelay != null) {
            long b = Math.min(lastDelay.aLong(0), stop);
            if (b > r.effStart) delays.add(new long[]{r.effStart, b});
        }
        for (Ev e : dayEvents) {
            if (Ev.DELAY.equals(e.type)) {
                continue;
            } else if (Ev.EXTEND.equals(e.type)) {
                if (e.src == src) {
                    long b = Math.min(e.aLong(0), stop);
                    if (b > start) lent.add(new long[]{start, b});
                } else if (toInt(e.b, -1) == src) {
                    // this slot was carried on into the one after it, from its own end
                    long b = Math.min(e.aLong(0), clip);
                    if (b > end) carried.add(new long[]{end, b});
                }
            }
        }
        r.breakMs = length(merge(breaks));
        r.delayMs = length(merge(delays));
        r.lentMs = length(merge(lent));
        r.extMs = length(merge(carried));
        List<long[]> all = new ArrayList<>(breaks);
        all.addAll(delays);
        all.addAll(lent);
        r.gaps.addAll(merge(all));
        return r;
    }

    private static long length(List<long[]> l) {
        long t = 0;
        for (long[] x : l) t += x[1] - x[0];
        return t;
    }
}
