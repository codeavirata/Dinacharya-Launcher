package app.dinacharya.wallpaper;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/** How long each app has been in front today, for the daily limits. Needs the Usage access permission. */
final class AppUsage {
    private static Map<String, Long> cache = new HashMap<>();
    private static long cachedAt;
    private static long cachedDay;

    private AppUsage() {
    }

    /** Milliseconds this package was in front today (since midnight); 0 when it cannot be read. */
    static synchronized long todayMs(Context context, String pkg) {
        try {
            Context c = context.getApplicationContext();
            if (!ScreenTime.hasAccess(c)) return 0;
            long now = System.currentTimeMillis();
            long midnight = java.time.LocalDate.now(Store.zone()).atStartOfDay(Store.zone()).toInstant().toEpochMilli();
            if (cachedDay != midnight || now - cachedAt > 15000L) {
                Map<String, Long> m = new HashMap<>();
                UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    Map<String, UsageStats> all = usm.queryAndAggregateUsageStats(midnight, now);
                    if (all != null) {
                        for (Map.Entry<String, UsageStats> e : all.entrySet()) m.put(e.getKey(), e.getValue().getTotalTimeInForeground());
                    }
                }
                cache = m;
                cachedAt = now;
                cachedDay = midnight;
            }
            Long v = cache.get(pkg);
            return v == null ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
