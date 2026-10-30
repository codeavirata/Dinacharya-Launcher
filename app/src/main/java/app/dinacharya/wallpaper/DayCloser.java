package app.dinacharya.wallpaper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.util.List;

/**
 * Closes each day: when a schedule day ends, its report is built from the plan, your answers and
 * your phone use, and saved on the phone. Days that were missed (phone off, app stopped) are
 * caught up the next time the app wakes, as far back as Android still keeps usage details.
 */
final class DayCloser {
    static final String ACTION = "app.dinacharya.wallpaper.DAY_END";
    private static final int CATCH_UP_DAYS = 5;
    private static final Object LOCK = new Object();

    private DayCloser() {
    }

    /** A report for one day with the phone use read now. */
    static DayReport build(Context c, Routine base, Overrides ov, Plan.DayPlan p, List<Ev> events, long now) {
        long to = Math.min(now, p.endMs);
        UsageDetail u = ScreenTime.detail(c, p.startMs, to);
        DayReport r = ReportAssembler.assemble(p, events, u, base.alarmOn, now, SlotUseCache.parts(c));
        r.tips.clear();
        r.tips.addAll(Suggestions.generate(r, Store.history(c).recent(14), base));
        return r;
    }

    /** Today's report so far (not saved). */
    static DayReport today(Context context) {
        Context c = context.getApplicationContext();
        Routine base = Store.load(c);
        Overrides ov = Store.overrides(c, base);
        long now = System.currentTimeMillis();
        Plan.DayPlan p = Plan.resolve(base, ov, now, Store.zone());
        return build(c, base, ov, Plan.plan(base, ov, p.day, Store.zone()), Store.history(c).events(), now);
    }

    /** Builds and saves reports for every finished day that has none. Safe to call often; does file work, so not on the main thread. */
    static void closeFinished(Context context) {
        Context c = context.getApplicationContext();
        synchronized (LOCK) {
            Routine base = Store.load(c);
            Overrides ov = Store.overrides(c, base);
            long now = System.currentTimeMillis();
            long since = Store.since(c);
            HistoryStore h = Store.history(c);
            Plan.DayPlan cur = Plan.resolve(base, ov, now, Store.zone());
            List<Ev> events = h.events();
            boolean any = false;
            for (LocalDate d = cur.day.minusDays(CATCH_UP_DAYS); d.isBefore(cur.day); d = d.plusDays(1)) {
                if (h.hasReport(d.toString())) continue;
                Plan.DayPlan p = Plan.plan(base, ov, d, Store.zone());
                if (p.endMs > now || p.endMs <= since) continue;
                h.saveReport(build(c, base, ov, p, events, now));
                any = true;
            }
            if (any) {
                h.compact(now);
                DriveJob.schedule(c);
            }
        }
    }

    /** Sets the system to wake the app a minute after the current day ends. */
    static void schedule(Context context) {
        try {
            doSchedule(context.getApplicationContext());
        } catch (RuntimeException e) {
            CrashLog.note(context, "day-close schedule", e);
        }
    }

    private static void doSchedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Routine base = Store.load(c);
        Overrides ov = Store.overrides(c, base);
        Plan.DayPlan p = Plan.resolve(base, ov, System.currentTimeMillis(), Store.zone());
        Intent i = new Intent(c, DayCloseReceiver.class);
        i.setAction(ACTION);
        PendingIntent op = PendingIntent.getBroadcast(c, 4003, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // An inexact alarm is enough here, and it needs no special permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.endMs + 60000L, op);
    }

    /** The system calls this when the day has ended. */
    public static class DayCloseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Context c = context.getApplicationContext();
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    closeFinished(c);
                    schedule(c);
                } catch (RuntimeException e) {
                    CrashLog.note(c, "day-close", e);
                } finally {
                    pr.finish();
                }
            }, "day-close").start();
        }
    }
}
