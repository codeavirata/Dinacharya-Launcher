package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Phone use over a stretch of time: when the phone was in use and which apps it was spent in. */
public final class UsageDetail {
    public final long fromMs;
    public final long toMs;
    /** Times the phone was in use, as [start, end) pairs in milliseconds, in order, not overlapping. */
    public final List<long[]> intervals;
    /** App name to milliseconds. */
    public final Map<String, Long> perApp;
    /** App name to the times it was in use, as [start, end) pairs in order. Empty when they were not kept. */
    public final Map<String, List<long[]>> appIntervals;

    public UsageDetail(long fromMs, long toMs, List<long[]> intervals, Map<String, Long> perApp) {
        this(fromMs, toMs, intervals, perApp, new java.util.HashMap<String, List<long[]>>());
    }

    public UsageDetail(long fromMs, long toMs, List<long[]> intervals, Map<String, Long> perApp, Map<String, List<long[]>> appIntervals) {
        this.fromMs = fromMs;
        this.toMs = toMs;
        this.intervals = intervals;
        this.perApp = perApp;
        this.appIntervals = appIntervals;
    }

    /**
     * How long each app was in use inside the given windows (pairs of start and end), longest first. Apps with no time in the
     * windows are left out.
     */
    public List<Object[]> appsDuring(List<long[]> windows) {
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, List<long[]>> en : appIntervals.entrySet()) {
            long t = 0;
            for (long[] iv : en.getValue()) {
                for (long[] w : windows) {
                    long a = Math.max(iv[0], w[0]);
                    long b = Math.min(iv[1], w[1]);
                    if (b > a) t += b - a;
                }
            }
            if (t > 0) out.add(new Object[]{en.getKey(), t});
        }
        Collections.sort(out, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] x, Object[] y) {
                int c = Long.compare((Long) y[1], (Long) x[1]);
                return c != 0 ? c : ((String) x[0]).compareTo((String) y[0]);
            }
        });
        return out;
    }

    /** Milliseconds of phone use inside the given windows. */
    public long overlap(List<long[]> windows) {
        long t = 0;
        for (long[] w : windows) t += overlap(w[0], w[1]);
        return t;
    }

    public long totalMs() {
        return overlap(fromMs, toMs);
    }

    /** Milliseconds of phone use inside [from, to). */
    public long overlap(long from, long to) {
        long t = 0;
        for (long[] iv : intervals) {
            long a = Math.max(iv[0], from);
            long b = Math.min(iv[1], to);
            if (b > a) t += b - a;
        }
        return t;
    }

    /** The {@code n} apps used longest, longest first. Each entry is {name, ms}. */
    public List<Object[]> top(int n) {
        List<Object[]> l = new ArrayList<>();
        for (Map.Entry<String, Long> e : perApp.entrySet()) {
            if (e.getValue() > 0) l.add(new Object[]{e.getKey(), e.getValue()});
        }
        Collections.sort(l, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] x, Object[] y) {
                int c = Long.compare((Long) y[1], (Long) x[1]);
                return c != 0 ? c : ((String) x[0]).compareTo((String) y[0]);
            }
        });
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }
}
