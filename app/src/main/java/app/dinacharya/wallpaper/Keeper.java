package app.dinacharya.wallpaper;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import java.util.List;

/**
 * Keeps what the launcher stands on in place: the alarms and the wallpaper. Android drops both without a word when the app is closed
 * from App info (Force stop), and nothing tells the app, so they are looked at again every time the app comes up, on a timer
 * ({@link HealthJob}), after a restart, and whenever the wallpaper or an alarm starts. Each look finds out what is gone, sets it again
 * and writes down what happened, so the Alarms screen and the problem report can say why.
 *
 * What it does, in order: gives a finished video pass its ending, reads how the app last ended, sets the next alarm (noting an alarm
 * that never came and one Android no longer held), tells the person about a missed alarm, looks at the wallpaper, starts the lock watcher
 * if it should be running and is not, and sets the jobs that run later. Safe to call often; a call within a few seconds of the last one does nothing.
 */
final class Keeper {
    private static long lastRun;

    private Keeper() {
    }

    static void ensure(Context context, String why) {
        final Context c = context.getApplicationContext();
        long t = SystemClock.elapsedRealtime();
        synchronized (Keeper.class) {
            if (lastRun != 0 && t - lastRun < 15000L) return;
            lastRun = t;
        }
        step(c, "pass", () -> PassSettle.run(c));
        readExit(c);
        AlarmScheduler.reschedule(c);
        step(c, "missed", () -> tellMissed(c));
        step(c, "wallpaper", () -> WallpaperSetup.status(c));
        step(c, "strong lock", () -> StrongMode.ensure(c));
        step(c, "day close", () -> DayCloser.schedule(c));
        step(c, "pass timer", () -> PassSettle.schedule(c));
        step(c, "health job", () -> HealthJob.schedule(c));
        AlarmScheduler.noteChecked(c);
    }

    private static void step(Context c, String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException | LinkageError e) {
            CrashLog.note(c, "keeper " + what, e);
        }
    }

    /** Reads how the app's process last ended (Android 11 and later) and keeps the newest end worth telling. */
    static void readExit(Context c) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            readExitNow(c);
        } catch (RuntimeException e) {
            CrashLog.note(c, "exit reasons", e);
        }
    }

    private static void readExitNow(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        AlarmScheduler.Diag d = AlarmScheduler.diag(c);
        List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 8);   // newest first
        long newest = d.exitSeen;
        long tellAt = 0;
        int tellReason = 0;
        for (ApplicationExitInfo info : list) {
            long ts = info.getTimestamp();
            if (ts <= d.exitSeen) break;
            newest = Math.max(newest, ts);
            if (tellAt == 0 && AlarmWatch.worthTelling(info.getReason())) {
                tellAt = ts;
                tellReason = info.getReason();
            }
        }
        if (newest > d.exitSeen) AlarmScheduler.noteExit(c, tellAt, tellReason, newest);
    }

    /** An alarm that never reached the app is told once, as a notification, with a way to open the app. */
    private static void tellMissed(Context c) {
        AlarmScheduler.Diag d = AlarmScheduler.diag(c);
        if (d.missedFor <= 0 || d.missedNoted == d.missedFor) return;
        AlarmScheduler.noteMissedTold(c, d.missedFor);
        if (!AlarmWatch.worthAnnouncing(d.missedFor, System.currentTimeMillis())) return;
        AlarmService.postNeverCame(c, d.missedTitle);
    }
}
