package app.dinacharya.wallpaper;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Works out how long the phone has been used today, from Android's usage events.
 * Needs the "Usage access" permission, which the user switches on in Settings.
 * Everything stays on the phone.
 *
 * "Used" means an app was on screen, the same idea as Android's own screen time.
 * The home screen and the system bars do not count.
 */
final class ScreenTime {

    /** Today's total and the minutes spent in each hour of the day. */
    static final class Result {
        final long totalMs;
        final long[] hourMs;
        final long dayStart;

        Result(long totalMs, long[] hourMs, long dayStart) {
            this.totalMs = totalMs;
            this.hourMs = hourMs;
            this.dayStart = dayStart;
        }
    }

    /** The counting rules, kept free of Android classes so they can be tested on a plain JVM. */
    static final class Accumulator {
        private final long dayStart;
        private final long[] hours = new long[24];
        private final Set<String> open = new HashSet<>();
        private final List<long[]> intervals;
        private long unionStart = -1;
        private long total;

        Accumulator(long dayStart) {
            this(dayStart, false);
        }

        /** With {@code capture} the times of use are kept too, not only the totals. */
        Accumulator(long dayStart, boolean capture) {
            this.dayStart = dayStart;
            this.intervals = capture ? new ArrayList<long[]>() : null;
        }

        List<long[]> intervals() {
            return intervals;
        }

        long total() {
            return total;
        }

        /** An app screen came to the front. Two screens open together count once. */
        void resume(String key, long ts) {
            if (open.add(key) && open.size() == 1) unionStart = ts;
        }

        /** An app screen left the front. */
        void pause(String key, long ts) {
            if (open.remove(key) && open.isEmpty()) close(ts);
        }

        /** The screen turned off or the phone shut down: everything open stops here. */
        void flush(long ts) {
            if (!open.isEmpty()) {
                open.clear();
                close(ts);
            }
        }

        Result finish(long now) {
            if (!open.isEmpty()) close(now);
            return new Result(total, hours.clone(), dayStart);
        }

        private void close(long end) {
            long a = Math.max(unionStart, dayStart);
            unionStart = -1;
            if (end <= a) return;
            total += end - a;
            if (intervals != null) intervals.add(new long[]{a, end});
            for (int h = 0; h < 24; h++) {
                long hs = dayStart + h * 3600000L;
                long he = hs + 3600000L;
                long overlap = Math.min(end, he) - Math.max(a, hs);
                if (overlap > 0) hours[h] += overlap;
            }
        }
    }

    // Usage event types, written as numbers so older Android versions compile too.
    private static final int EV_RESUMED = 1;
    private static final int EV_PAUSED = 2;
    private static final int EV_SCREEN_OFF = 16;
    private static final int EV_STOPPED = 23;
    private static final int EV_SHUTDOWN = 26;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "screen-time");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** The latest result, or null when there is none (no permission yet, or not read yet). */
    static volatile Result last;

    private ScreenTime() {
    }

    static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            // Some phones answer "default": then the permission itself says whether it is allowed.
            return c.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Reads today's usage in the background, stores it in {@link #last}, then runs onDone on the main thread. */
    static void refresh(final Context context, final Runnable onDone) {
        final Context c = context.getApplicationContext();
        WORKER.execute(() -> {
            Result r = null;
            try {
                if (hasAccess(c)) r = compute(c, System.currentTimeMillis());
            } catch (RuntimeException ignored) {
                // Leave r null: the wallpaper simply hides the panel until the next try.
            }
            last = r;
            if (onDone != null) MAIN.post(onDone);
        });
    }

    private static Result compute(Context c, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();

        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        UsageEvents events = usm.queryEvents(dayStart, now);
        if (events == null) return null;

        Set<String> skip = ignoredPackages(c);
        Accumulator acc = new Accumulator(dayStart);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            long ts = e.getTimeStamp();
            if (type == EV_SCREEN_OFF || type == EV_SHUTDOWN) {
                acc.flush(ts);
                continue;
            }
            String pkg = e.getPackageName();
            if (pkg == null || skip.contains(pkg)) continue;
            String key = pkg + "/" + e.getClassName();
            if (type == EV_RESUMED) acc.resume(key, ts);
            else if (type == EV_PAUSED || type == EV_STOPPED) acc.pause(key, ts);
        }
        return acc.finish(now);
    }

    /**
     * Phone use between two times: when it happened and in which apps. Needs the Usage access permission;
     * returns null when it is missing or unreadable.
     */
    static UsageDetail detail(Context context, long from, long to) {
        Context c = context.getApplicationContext();
        if (to <= from || !hasAccess(c)) return null;
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            // Start a little early so an app already open at "from" has its start event; the counting clips to "from".
            UsageEvents events = usm.queryEvents(from - 6 * 3600000L, to);
            if (events == null) return null;
            Set<String> skip = ignoredPackages(c);
            Accumulator all = new Accumulator(from, true);
            Map<String, Accumulator> per = new HashMap<>();
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                int type = e.getEventType();
                long ts = e.getTimeStamp();
                if (type == EV_SCREEN_OFF || type == EV_SHUTDOWN) {
                    all.flush(ts);
                    for (Accumulator a : per.values()) a.flush(ts);
                    continue;
                }
                String pkg = e.getPackageName();
                if (pkg == null || skip.contains(pkg)) continue;
                String key = pkg + "/" + e.getClassName();
                Accumulator pa = per.get(pkg);
                if (pa == null) {
                    pa = new Accumulator(from, true);
                    per.put(pkg, pa);
                }
                if (type == EV_RESUMED) {
                    all.resume(key, ts);
                    pa.resume(key, ts);
                } else if (type == EV_PAUSED || type == EV_STOPPED) {
                    all.pause(key, ts);
                    pa.pause(key, ts);
                }
            }
            all.finish(to);
            Map<String, Long> names = new LinkedHashMap<>();
            Map<String, List<long[]>> times = new HashMap<>();
            for (Map.Entry<String, Accumulator> en : per.entrySet()) {
                long ms = en.getValue().finish(to).totalMs;
                if (ms <= 0) continue;
                String label = appLabel(c, en.getKey());
                Long prev = names.get(label);
                names.put(label, (prev == null ? 0L : prev) + ms);
                List<long[]> mine = times.get(label);
                if (mine == null) {
                    mine = new ArrayList<>();
                    times.put(label, mine);
                }
                if (en.getValue().intervals() != null) mine.addAll(en.getValue().intervals());
            }
            return new UsageDetail(from, to, all.intervals(), names, times);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static String appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            CharSequence l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (l != null && l.length() > 0) return l.toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // not visible to this app: fall back to the package name
        }
        int dot = pkg.lastIndexOf('.');
        String last = dot >= 0 && dot < pkg.length() - 1 ? pkg.substring(dot + 1) : pkg;
        return last.isEmpty() ? pkg : Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    private static Set<String> ignoredPackages(Context c) {
        Set<String> s = new HashSet<>();
        s.add("com.android.systemui");
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
            s.add(ri.activityInfo.packageName);
        }
        return s;
    }
}
