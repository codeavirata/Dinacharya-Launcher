package app.dinacharya.wallpaper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Gives each finished video pass its ending: what was made with the pass goes back to the free version (the rules are in
 * {@link PassReset}). {@link #run} does it as soon as it is called after the pass has ended; the system also calls it at the
 * moment the pass is up ({@link #schedule}), after a restart, and whenever a screen opens, so it does not depend on
 * the app being open. Having Pro keeps everything, so nothing is taken away from someone who got it in the meantime.
 */
final class PassSettle {
    static final String ACTION = "app.dinacharya.wallpaper.PASS_END";
    private static final int REQUEST = 4004;
    private static final Object LOCK = new Object();
    /** Counts the times saved settings were changed here, so a screen holding its own copy knows to read them again. */
    private static volatile int epoch;

    private PassSettle() {
    }

    /** Changes with every reset that altered saved settings. */
    static int epoch() {
        return epoch;
    }

    /** What to tell the person about a reset that has happened, once; null when there is nothing to tell. */
    static String takeMessage(Context c) {
        try {
            return Pro.get(c).takePassMessage();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * For every pass that has ended and whose changes have not been put back yet, puts them back now (the oldest pass first). Safe to
     * call often (it costs one small read when there is nothing to do). Passes that are still running are left alone. Returns true
     * when saved settings were changed.
     */
    static boolean run(Context context) {
        final Context c = context.getApplicationContext();
        synchronized (LOCK) {
            Pro pro = Pro.get(c);
            List<PassLedger.Entry> due = pro.endedPasses();
            if (due.isEmpty()) return false;
            try {
                if (pro.owned()) {   // Pro was bought (or kept): everything stays
                    pro.forgetPasses(due);
                    return false;
                }
                int all = 0;
                boolean routine = false;
                LauncherConfig cfg = null;
                Routine r = null;
                boolean cfgChanged = false, routineChanged = false;
                for (PassLedger.Entry e : due) {
                    int mask = PassReset.maskOf(e.note);
                    if (mask == 0) continue;   // nothing to put back
                    int changes = 0;
                    if (PassReset.touchesLauncherMask(mask)) {
                        if (cfg == null) cfg = LauncherStore.load(c);
                        int n = PassReset.undoLauncher(e.note, cfg);
                        if (n > 0) {
                            changes += n;
                            cfgChanged = true;
                        }
                    }
                    if (PassReset.touchesRoutineMask(mask)) {
                        if (r == null) r = Store.load(c);
                        int n = PassReset.undoRoutine(e.note, r);
                        if (n > 0) {
                            changes += n;
                            routineChanged = true;
                        }
                    }
                    if (changes > 0) {
                        all += changes;
                        pro.addPassMessage(PassReset.messageMask(mask, changes));
                    }
                }
                if (cfgChanged) LauncherStore.save(c, cfg);
                if (routineChanged) {
                    Store.save(c, r);
                    routine = true;
                }
                pro.forgetPasses(due);
                if (all > 0) {
                    epoch++;
                    if (routine) AlarmScheduler.reschedule(c);
                }
                return all > 0;
            } catch (RuntimeException e) {
                CrashLog.note(c, "pass reset", e);
                try {
                    pro.forgetPasses(due);   // gating already ends the pass; do not try again and again
                } catch (RuntimeException ignored) {
                    // nothing more to do
                }
                return false;
            }
        }
    }

    /** Asks the system to call {@link #run} when the next pass ends (or cancels the call when no pass is waiting to end). */
    static void schedule(Context context) {
        try {
            Context c = context.getApplicationContext();
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, PassEndReceiver.class);
            i.setAction(ACTION);
            PendingIntent op = PendingIntent.getBroadcast(c, REQUEST, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long end = Pro.get(c).nextPassNoteEnd();
            if (end <= 0) {
                am.cancel(op);
                return;
            }
            // An inexact alarm is enough, and it needs no special permission; a little after the end, never before it.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(end, System.currentTimeMillis()) + 20000L, op);
        } catch (RuntimeException e) {
            CrashLog.note(context, "pass schedule", e);
        }
    }

    /** The system calls this when a pass has run its time. */
    public static class PassEndReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Context c = context.getApplicationContext();
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    run(c);
                    schedule(c);
                } catch (RuntimeException e) {
                    CrashLog.note(c, "pass end", e);
                } finally {
                    pr.finish();
                }
            }, "pass-end").start();
        }
    }
}
