package app.dinacharya.wallpaper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Keeps one real alarm set for the next event on the timeline. When it rings, the next one is set.
 * Uses the system alarm-clock call, which Android lets through battery saving (see {@link #reschedule}).
 */
final class AlarmScheduler {
    static final String ACTION_ALARM = "app.dinacharya.wallpaper.ALARM";

    static final String X_KIND = "kind";
    static final String X_DAY = "day";
    static final String X_SRC = "src";
    static final String X_TITLE = "title";
    static final String X_BLOCK_KIND = "blockKind";
    static final String X_START = "slotStart";
    static final String X_END = "slotEnd";
    static final String X_BED = "plannedBed";
    static final String X_TIME = "time";
    static final String X_FIRST = "first";
    static final String X_MODE = "mode";
    static final String X_WAKE = "wake";

    private AlarmScheduler() {
    }

    static void put(Intent i, AlarmPlanner.Event e) {
        i.putExtra(X_KIND, e.kind);
        i.putExtra(X_DAY, e.day);
        i.putExtra(X_SRC, e.src);
        i.putExtra(X_TITLE, e.title);
        i.putExtra(X_BLOCK_KIND, e.blockKind);
        i.putExtra(X_START, e.slotStartMs);
        i.putExtra(X_END, e.slotEndMs);
        i.putExtra(X_BED, e.plannedBedMs);
        i.putExtra(X_TIME, e.timeMs);
        i.putExtra(X_FIRST, e.firstOfDay);
        i.putExtra(X_MODE, e.mode);
        i.putExtra(X_WAKE, e.wake);
    }

    /** Null when the intent carries no alarm. */
    static AlarmPlanner.Event read(Intent i) {
        if (i == null || !i.hasExtra(X_TIME)) return null;
        String t = i.getStringExtra(X_TITLE);
        String d = i.getStringExtra(X_DAY);
        return new AlarmPlanner.Event(i.getLongExtra(X_TIME, 0), i.getIntExtra(X_KIND, AlarmPlanner.SLOT),
                d == null ? "" : d, i.getIntExtra(X_SRC, -1), t == null ? "" : t,
                i.getIntExtra(X_BLOCK_KIND, Routine.TASK), i.getLongExtra(X_START, 0), i.getLongExtra(X_END, 0),
                i.getLongExtra(X_BED, -1), i.getBooleanExtra(X_FIRST, false),
                i.getIntExtra(X_MODE, Routine.ALERT_RING), i.getBooleanExtra(X_WAKE, false));
    }

    // ------------------------------------------------------------------ what Android did with our alarms
    // A few facts kept on the phone so the Alarms screen can show where an alarm got stuck:
    // set in Android, handed to the app, started ringing. Not part of the backup.

    private static final String DIAG = "alarm_diag";

    /** What the app last learned about its alarms. Zero means "never". */
    static final class Diag {
        long armedFor;        // when the next alarm was last set to ring
        boolean armedExact;   // set as an exact alarm (rings on the minute)
        long armedAt;         // when it was set
        long firedAt;         // when Android last handed an alarm to the app
        long firedDue;        // the time that alarm was meant for
        boolean firedTest;
        long ringAt;          // when the ringing service last got going
        long fallbackAt;      // when the backup notification had to be used instead
        long missedFor;       // an alarm set for this time never reached the app
    }

    private static android.content.SharedPreferences diagPrefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(DIAG, Context.MODE_PRIVATE);
    }

    static Diag diag(Context c) {
        Diag d = new Diag();
        try {
            android.content.SharedPreferences p = diagPrefs(c);
            d.armedFor = p.getLong("armedFor", 0);
            d.armedExact = p.getBoolean("armedExact", false);
            d.armedAt = p.getLong("armedAt", 0);
            d.firedAt = p.getLong("firedAt", 0);
            d.firedDue = p.getLong("firedDue", 0);
            d.firedTest = p.getBoolean("firedTest", false);
            d.ringAt = p.getLong("ringAt", 0);
            d.fallbackAt = p.getLong("fallbackAt", 0);
            d.missedFor = p.getLong("missedFor", 0);
        } catch (RuntimeException ignored) {
            // nothing known
        }
        return d;
    }

    private static void noteArmed(Context c, long forMs, boolean exact) {
        try {
            diagPrefs(c).edit().putLong("armedFor", forMs).putBoolean("armedExact", exact)
                    .putLong("armedAt", System.currentTimeMillis()).apply();
        } catch (RuntimeException ignored) {
            // diagnostics only
        }
    }

    static void noteFired(Context c, long dueMs, boolean test) {
        try {
            android.content.SharedPreferences.Editor ed = diagPrefs(c).edit().putLong("firedAt", System.currentTimeMillis())
                    .putLong("firedDue", dueMs).putBoolean("firedTest", test).putLong("ringAt", 0).putLong("fallbackAt", 0);
            ed.remove("missedFor");   // an alarm got through, so an earlier miss is history
            ed.apply();
        } catch (RuntimeException ignored) {
            // diagnostics only
        }
    }

    static void noteRinging(Context c) {
        try {
            diagPrefs(c).edit().putLong("ringAt", System.currentTimeMillis()).apply();
        } catch (RuntimeException ignored) {
            // diagnostics only
        }
    }

    static void noteFallback(Context c) {
        try {
            diagPrefs(c).edit().putLong("fallbackAt", System.currentTimeMillis()).apply();
        } catch (RuntimeException ignored) {
            // diagnostics only
        }
    }

    /**
     * Rings a test alarm after {@code seconds}, through the same path as a real one (Android's alarm
     * clock, then the ringing service, then the question screen). It never touches the real alarms.
     * Returns true when it was set to ring on the exact second.
     */
    static boolean scheduleTest(Context context, int seconds) {
        Context c = context.getApplicationContext();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        long t = System.currentTimeMillis() + Math.max(3, seconds) * 1000L;
        AlarmPlanner.Event e = new AlarmPlanner.Event(t, AlarmPlanner.SLOT, java.time.LocalDate.now().toString(),
                AlarmPlanner.TEST_SRC, "Test alarm", Routine.TASK, t, t + 60000L, -1L, false);
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        put(i, e);
        PendingIntent op = PendingIntent.getBroadcast(c, 4003, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (exactAllowed(c)) {
            try {
                Intent open = new Intent(c, MainActivity.class);
                PendingIntent show = PendingIntent.getActivity(c, 4002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), op);
                return true;
            } catch (SecurityException refused) {
                // fall through to the normal alarm below
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op);
        return false;
    }

    /**
     * Called before every re-set. If the alarm we set earlier is long past and never reached the app,
     * Android (or the phone maker) did not deliver it: remember that so the Alarms screen can say so.
     */
    private static void noteIfMissed(Context c) {
        try {
            Diag d = diag(c);
            if (d.armedFor > 0 && d.armedFor < System.currentTimeMillis() - 90000L && d.firedDue < d.armedFor) {
                diagPrefs(c).edit().putLong("missedFor", d.armedFor).apply();
            }
        } catch (RuntimeException ignored) {
            // diagnostics only
        }
    }

    private static PendingIntent operation(Context c, AlarmPlanner.Event e) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        if (e != null) put(i, e);
        return PendingIntent.getBroadcast(c, 4001, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** True when Android lets this app ring at an exact time (always before Android 12). */
    static boolean exactAllowed(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        try {
            AlarmManager am = (AlarmManager) context.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            return am == null || am.canScheduleExactAlarms();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Sets the alarm for the next event, or cancels it when alarms are off. Call after anything that
     * changes the timeline. Never throws: a problem here must not close the app or the wallpaper.
     *
     * The alarm-clock call rings on time and gets through battery saving, but from Android 12 it
     * needs the exact-alarm permission (declared in the manifest). If Android still refuses it, the
     * alarm is set as a normal one, which can be a few minutes late, and the setup screen says so.
     */
    static void reschedule(Context context) {
        try {
            doReschedule(context.getApplicationContext());
        } catch (RuntimeException e) {
            CrashLog.note(context, "reschedule", e);
        }
    }

    private static void doReschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        noteIfMissed(c);
        Routine base = Store.load(c);
        Overrides ov = Store.overrides(c, base);
        AlarmState st = Store.alarmState(c);
        long after = Math.max(System.currentTimeMillis(), st.lastFired);
        AlarmPlanner.Event next = AlarmPlanner.next(base, ov, st, after, Store.zone());
        PendingIntent op = operation(c, next);
        if (next == null) {
            am.cancel(op);
            noteArmed(c, 0, false);
            return;
        }
        if (exactAllowed(c)) {
            try {
                Intent open = new Intent(c, MainActivity.class);
                PendingIntent show = PendingIntent.getActivity(c, 4002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(next.timeMs, show), op);
                noteArmed(c, next.timeMs, true);
                return;
            } catch (SecurityException refused) {
                // fall through to the normal alarm below
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeMs, op);
        noteArmed(c, next.timeMs, false);
    }
}
