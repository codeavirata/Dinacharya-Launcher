package app.dinacharya.wallpaper;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/**
 * Sets the phone's ringer (ring, vibrate, silent) or Do Not Disturb (priority only) by the quiet rules in {@link QuietRules}, and puts both
 * back afterwards. A call is set for the next moment anything can change (a rule's hours starting or ending, a slot starting or ending,
 * a break ending, library mode on or off). Everything is looked at again whenever the app opens, after a restart, at each switch and when
 * you answer a slot, so it does not depend on the app being open. Silent and Do Not Disturb need Do Not Disturb access; without it the
 * ringer only goes to vibrate and Do Not Disturb is left alone.
 */
final class Quiet {
    static final String ACTION = "app.dinacharya.wallpaper.LIBRARY_CHANGE";
    private static final int REQUEST = 4007;
    private static final String KEY_KEPT = "libraryRingerKept";
    private static final String KEY_OURS = "quietRingerOurs";
    private static final String KEY_HOLD_KEPT = "quietHoldKept";
    private static final Object LOCK = new Object();

    private Quiet() {
    }

    /** True when Android lets the app use Do Not Disturb (needed for silent and for holding notifications). */
    static boolean hasAccess(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    /** Applies the rules for this moment and sets the call for the next change. Safe to call often. */
    static void refresh(Context context) {
        Context c = context.getApplicationContext();
        synchronized (LOCK) {
            long next = 0L;
            try {
                next = apply(c);
            } catch (RuntimeException e) {
                CrashLog.note(c, "quiet", e);
            }
            try {
                schedule(c, next);
            } catch (RuntimeException e) {
                CrashLog.note(c, "quiet schedule", e);
            }
        }
    }

    /** Sets the ringer and the filter; returns when to look again (0: nothing will change by itself). */
    private static long apply(Context c) {
        QuietRules rules = Store.quiet(c);
        long now = System.currentTimeMillis();
        int want = -1;
        long next = 0L;
        if (!rules.isEmpty() || Store.prefs(c).getInt(KEY_KEPT, -1) >= 0 || Store.prefs(c).getInt(KEY_HOLD_KEPT, 0) > 0) {
            SlotNow n = SlotNow.read(c);
            want = rules.now(now, Store.zone(), n.toLock());
            if (!rules.isEmpty()) {
                next = soonest(rules.nextEdge(now, Store.zone()), n.runEndMs, n.nextStartMs, n.onBreak ? n.breakEndMs : 0L,
                        Store.library(c).nextChangeMs(now, Store.zone()));
            }
        }
        boolean access = hasAccess(c);
        applyRinger(c, want == QuietRules.DND && !access ? QuietRules.VIBRATE : want);
        applyHold(c, want == QuietRules.DND && access);
        return next;
    }

    private static long soonest(long... times) {
        long best = 0L;
        for (long t : times) if (t > 0L && (best == 0L || t < best)) best = t;
        return best;
    }

    private static void applyRinger(Context c, int sound) {
        android.content.SharedPreferences p = Store.prefs(c);
        int kept = p.getInt(KEY_KEPT, -1);
        // what an older version left behind was always "vibrate"
        int ours = p.getInt(KEY_OURS, kept >= 0 ? QuietRules.RINGER_VIBRATE : -1);
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        QuietRules.RingerStep step = QuietRules.ringerStep(sound, am.getRingerMode(), kept, ours);
        int keep = step.keep;
        int our = step.ours;
        if (step.set >= 0) {
            try {
                am.setRingerMode(step.set);
            } catch (SecurityException e) {
                // silent needs Do Not Disturb access; vibrate is the nearest without it
                try {
                    if (step.set == QuietRules.RINGER_SILENT) {
                        am.setRingerMode(QuietRules.RINGER_VIBRATE);
                        our = QuietRules.RINGER_VIBRATE;
                    } else {
                        keep = kept;
                        our = ours;
                    }
                } catch (SecurityException again) {
                    keep = kept;
                    our = ours;
                }
            }
        }
        if (keep != kept || our != ours) p.edit().putInt(KEY_KEPT, keep).putInt(KEY_OURS, our).apply();
    }

    private static void applyHold(Context c, boolean want) {
        android.content.SharedPreferences p = Store.prefs(c);
        int kept = p.getInt(KEY_HOLD_KEPT, 0);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        boolean access = nm.isNotificationPolicyAccessGranted();
        // when the access was taken back there is nothing to undo any more
        if (!access) {
            if (kept != 0) p.edit().putInt(KEY_HOLD_KEPT, 0).apply();
            return;
        }
        QuietRules.HoldStep step = QuietRules.holdStep(want, nm.getCurrentInterruptionFilter(), kept);
        int keep = step.keep;
        if (step.set > 0) {
            try {
                nm.setInterruptionFilter(step.set);
            } catch (SecurityException e) {
                keep = kept;
            }
        }
        if (keep != kept) p.edit().putInt(KEY_HOLD_KEPT, keep).apply();
    }

    /** Asks the system to call {@link Receiver} at {@code at} (or cancels the call when nothing will change). */
    private static void schedule(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, Receiver.class);
        i.setAction(ACTION);
        PendingIntent op = PendingIntent.getBroadcast(c, REQUEST, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long now = System.currentTimeMillis();
        if (at <= 0) {
            am.cancel(op);
            return;
        }
        // An inexact alarm is enough, and needs no special permission; a little after the switch, never before it.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(at, now) + 2000L, op);
    }

    /** The system calls this when something may change. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Context c = context.getApplicationContext();
            refresh(c);
            try {
                SlotNow.changed();   // the lock and the home screen's pill follow
            } catch (RuntimeException e) {
                CrashLog.note(c, "quiet switch", e);
            }
        }
    }
}
