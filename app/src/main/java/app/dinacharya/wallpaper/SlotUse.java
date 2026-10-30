package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phone use inside a slot, told apart by whether the app is part of the slot's activity. Time in the apps chosen for the activity (the
 * notes app for Study, say) is part of the slot; time in any other app is "other phone use", it breaks the focus, and it is what the home
 * screen marks and the reports count. Works out, for one slot: the time spent in apps, the part of it that was in the activity's own apps,
 * the other phone use as stretches with exact times, how long the slot went without any (the focus, and the longest unbroken run of it)
 * and the time truly devoted to the activity. Uses no Android classes.
 */
public final class SlotUse {
    /** Other phone use closer together than this (switching from one app to another) is one stretch. */
    public static final long JOIN_MS = 15000L;
    /** The most stretches kept for one slot (a day of heavy use cannot make a report grow without end). */
    public static final int MAX_EPISODES = 40;

    private SlotUse() {
    }

    /** A length of time for people: "Under a minute", "14 min", "1h 5m". */
    public static String dur(long ms) {
        if (ms < 60000L) return ms <= 0 ? "0 min" : "Under a minute";
        return Routine.formatDuration((int) Math.round(ms / 60000.0));
    }

    /** Which apps are part of an activity: the packages for a slot of this kind and type (never null). */
    public interface Parts {
        Set<String> packagesFor(int kind, String sub);
    }

    /** One stretch of other phone use: when it began and ended and the app used longest in it. */
    public static final class Episode {
        public final long startMs;
        public final long endMs;
        public final String app;

        public Episode(long startMs, long endMs, String app) {
            this.startMs = startMs;
            this.endMs = Math.max(startMs, endMs);
            this.app = app == null ? "" : app;
        }

        public long ms() {
            return endMs - startMs;
        }
    }

    /** What one slot looked like. */
    public static final class Info {
        /** False when phone use could not be read (no permission): then only {@link #workedMs} is known. */
        public final boolean known;
        /** The slot's own time that has passed (breaks and a put-off start left out). */
        public long workedMs;
        /** Time with an app in front, in any app. */
        public long appMs;
        /** The part of {@link #appMs} that was only in apps that are part of the activity. */
        public long partMs;
        /** Time in other apps (with an app of the activity open as well, it counts here). */
        public long offMs;
        /** The longest stretch of the slot with no other phone use, in one piece (a break ends a stretch); -1 when not known. */
        public long longestFocusMs = -1;
        /** How long the focus has lasted up to now, or 0 while other phone use is going on or when now is not inside the slot. */
        public long focusSinceMs;
        /** Other phone use is going on right now. */
        public boolean ongoing;
        /** The latest stretches of other phone use, in order. At most {@link #MAX_EPISODES}; {@link #episodeCount} counts all of them. */
        public final List<Episode> episodes = new ArrayList<>();
        public int episodeCount;

        Info(boolean known) {
            this.known = known;
        }

        /** Slot time not spent in other apps: the time given to the activity. */
        public long devotedMs() {
            return Math.max(0, workedMs - offMs);
        }

        /** 0..100: the share of the slot's time with no other phone use; -1 when nothing is known. */
        public int focusPct() {
            if (!known || workedMs <= 0) return -1;
            double f = 100.0 * (workedMs - Math.min(workedMs, offMs)) / workedMs;
            return (int) Math.max(0, Math.min(100, Math.round(f)));
        }
    }

    /**
     * @param worked the slot's own time as [start, end) windows, already cut at "now" (breaks and a put-off start are not in it)
     * @param u      phone use covering the windows, or null when it could not be read
     * @param part   the packages that are part of the activity (may be empty or null: then every app is other phone use)
     * @param nowMs  now, for the focus that is running
     */
    public static Info analyze(List<long[]> worked, UsageDetail u, Set<String> part, long nowMs) {
        Info in = new Info(u != null);
        List<long[]> windows = new ArrayList<>();
        if (worked != null) for (long[] w : worked) if (w[1] > w[0]) windows.add(w);
        for (long[] w : windows) in.workedMs += w[1] - w[0];
        if (u == null || windows.isEmpty()) {
            if (u != null) in.longestFocusMs = 0;
            return in;
        }
        in.appMs = u.overlap(windows);

        // everything but the activity's own apps
        List<long[]> offAll = new ArrayList<>();
        Map<String, List<long[]>> offBy = new HashMap<>();
        boolean haveParts = part != null && !part.isEmpty();
        if (u.pkgIntervals != null && !u.pkgIntervals.isEmpty()) {
            for (Map.Entry<String, List<long[]>> en : u.pkgIntervals.entrySet()) {
                if (haveParts && part.contains(en.getKey())) continue;
                offAll.addAll(en.getValue());
                offBy.put(en.getKey(), en.getValue());
            }
        } else {
            offAll.addAll(u.intervals);   // no word on single apps: all of it counts as other phone use
        }
        List<long[]> off = SlotGaps.merge(offAll);
        in.offMs = Math.min(in.appMs, overlap(off, windows));
        in.partMs = Math.max(0, in.appMs - in.offMs);

        long longest = 0;
        Episode lastInWindow = null;   // the last stretch of the window being looked at
        long runFrom = 0;              // where the focus run that is open at the end of that window began
        for (long[] w : windows) {
            // the other phone use inside this window, joined where the gap is small
            List<long[]> joined = new ArrayList<>();
            for (long[] o : off) {
                long a = Math.max(o[0], w[0]);
                long b = Math.min(o[1], w[1]);
                if (b <= a) continue;
                if (!joined.isEmpty() && a - joined.get(joined.size() - 1)[1] <= JOIN_MS) {
                    joined.get(joined.size() - 1)[1] = Math.max(joined.get(joined.size() - 1)[1], b);
                } else {
                    joined.add(new long[]{a, b});
                }
            }
            long prevEnd = w[0];
            lastInWindow = null;
            for (long[] j : joined) {
                longest = Math.max(longest, j[0] - prevEnd);
                prevEnd = j[1];
                in.episodeCount++;
                lastInWindow = new Episode(j[0], j[1], appIn(u, offBy, j[0], j[1]));
                in.episodes.add(lastInWindow);
                if (in.episodes.size() > MAX_EPISODES) in.episodes.remove(0);   // the newest are the ones kept
            }
            longest = Math.max(longest, w[1] - prevEnd);
            runFrom = prevEnd;
        }
        in.longestFocusMs = longest;

        // the focus that is running: only while "now" is inside the last window (the slot is going on)
        long[] lw = windows.get(windows.size() - 1);
        if (nowMs >= lw[0] && nowMs <= lw[1] + 1500L) {
            long ref = Math.min(nowMs, lw[1]);
            if (lastInWindow != null && lastInWindow.endMs >= ref - 2000L) {
                in.ongoing = true;
                in.focusSinceMs = 0;
            } else {
                in.focusSinceMs = Math.max(0, ref - runFrom);
            }
        }
        return in;
    }

    /** Milliseconds of {@code a} (merged, in order) that fall inside the windows. */
    private static long overlap(List<long[]> a, List<long[]> windows) {
        long t = 0;
        for (long[] x : a) {
            for (long[] w : windows) {
                long s = Math.max(x[0], w[0]);
                long e = Math.min(x[1], w[1]);
                if (e > s) t += e - s;
            }
        }
        return t;
    }

    /** The app used longest between two times, among the apps that are other phone use ("" when there is no word on single apps). */
    private static String appIn(UsageDetail u, Map<String, List<long[]>> offBy, long from, long to) {
        String best = "";
        long bestMs = -1;
        for (Map.Entry<String, List<long[]>> en : offBy.entrySet()) {
            long t = 0;
            for (long[] iv : en.getValue()) {
                long s = Math.max(iv[0], from);
                long e = Math.min(iv[1], to);
                if (e > s) t += e - s;
            }
            if (t <= 0) continue;
            String name = u.pkgNames == null ? null : u.pkgNames.get(en.getKey());
            if (name == null || name.isEmpty()) name = shortName(en.getKey());
            if (t > bestMs || (t == bestMs && name.compareTo(best) < 0)) {
                bestMs = t;
                best = name;
            }
        }
        return best;
    }

    private static String shortName(String pkg) {
        int dot = pkg.lastIndexOf('.');
        String last = dot >= 0 && dot < pkg.length() - 1 ? pkg.substring(dot + 1) : pkg;
        return last.isEmpty() ? pkg : Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    // ------------------------------------------------------------------ the slots of a day

    /**
     * The slot's own time as windows up to "now": from when it really began to when it really ended, without breaks, a put-off start and
     * the start given to the slot before. Empty when the slot has not started, was taken off, or the day was off when it began.
     */
    public static List<long[]> workedWindows(List<Ev> dayEvents, List<long[]> offPeriods, Routine.Block b, long startMs, long endMs, long clip) {
        List<long[]> none = new ArrayList<>();
        if (b.kind == Routine.SLEEP || b.skipped || b.src < 0 || startMs < 0) return none;
        if (DayOff.within(offPeriods, startMs)) return none;
        SlotGaps.Result gaps = SlotGaps.of(dayEvents, b.src, startMs, endMs, clip);
        long stop = Math.min(gaps.effEnd, clip);
        if (stop <= gaps.effStart) return none;
        return SlotGaps.complement(gaps.effStart, stop, gaps.gaps);
    }

    /** What the home screen needs: for each slot of today that has started (by its number in the routine), how it went. */
    public static final class Snapshot {
        public final String day;
        public final long atMs;
        public final Map<Integer, Info> bySrc = new HashMap<>();

        public Snapshot(String day, long atMs) {
            this.day = day;
            this.atMs = atMs;
        }

        public Info of(int src) {
            return bySrc.get(src);
        }
    }

    /**
     * The snapshot of {@code p} (a day with the temporary changes applied) at {@code nowMs}.
     *
     * @param u     phone use for the day up to now, or null when it could not be read (the snapshot is then empty)
     * @param parts which apps are part of which activity (null: none)
     */
    public static Snapshot snapshot(Plan.DayPlan p, List<Ev> events, UsageDetail u, Parts parts, long nowMs) {
        Snapshot s = new Snapshot(p.day.toString(), nowMs);
        if (u == null) return s;
        List<Ev> dayEv = new ArrayList<>();
        for (Ev e : events) if (e.day.equals(s.day) && e.ts <= nowMs) dayEv.add(e);
        List<long[]> offPeriods = DayOff.periods(dayEv);
        long clip = Math.min(nowMs, p.endMs);
        for (int i = 0; i < p.sc.slots.size(); i++) {
            Routine.Block b = p.sc.slots.get(i).block;
            if (b.src < 0 || b.kind == Routine.SLEEP || b.isGap()) continue;
            List<long[]> worked = workedWindows(dayEv, offPeriods, b, p.slotStartMs(i), p.slotEndMs(i), clip);
            if (worked.isEmpty()) continue;
            Set<String> part = parts == null ? Collections.<String>emptySet() : parts.packagesFor(b.kind, b.sub);
            s.bySrc.put(b.src, analyze(worked, u, part, nowMs));
        }
        return s;
    }
}
