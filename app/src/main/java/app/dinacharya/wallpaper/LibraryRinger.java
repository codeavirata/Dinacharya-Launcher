package app.dinacharya.wallpaper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/**
 * Puts the phone's ringer on vibrate while library mode is on (when that option is chosen) and back afterwards, and sets a call for the
 * moment library mode next switches on or off. Everything is looked at again whenever the app opens, after a restart and at each switch,
 * so it does not depend on the app being open. If Android refuses (it needs Do Not Disturb access to leave silent), nothing is changed.
 */
final class LibraryRinger {
    static final String ACTION = "app.dinacharya.wallpaper.LIBRARY_CHANGE";
    private static final int REQUEST = 4007;
    private static final String KEY_KEPT = "libraryRingerKept";
    private static final Object LOCK = new Object();

    private LibraryRinger() {
    }

    /** Applies the ringer rule for this moment and sets the call for the next switch. Safe to call often. */
    static void refresh(Context context) {
        Context c = context.getApplicationContext();
        synchronized (LOCK) {
            try {
                applyRinger(c);
            } catch (RuntimeException e) {
                CrashLog.note(c, "library ringer", e);
            }
            try {
                schedule(c);
            } catch (RuntimeException e) {
                CrashLog.note(c, "library schedule", e);
            }
        }
    }

    private static void applyRinger(Context c) {
        LibraryMode lib = Store.library(c);
        long now = System.currentTimeMillis();
        boolean want = lib.ringer && lib.on(now, Store.zone());
        int kept = Store.prefs(c).getInt(KEY_KEPT, -1);
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        LibraryMode.RingerStep step = LibraryMode.ringerStep(want, am.getRingerMode(), kept);
        int keep = step.keep;
        if (step.set >= 0) {
            try {
                am.setRingerMode(step.set);
            } catch (SecurityException e) {
                // Android wants Do Not Disturb access for this: leave the ringer alone and remember nothing
                keep = kept >= 0 && step.set != LibraryMode.RINGER_VIBRATE ? kept : -1;
            }
        }
        if (keep != kept) Store.prefs(c).edit().putInt(KEY_KEPT, keep).apply();
    }

    /** Asks the system to call {@link Receiver} when library mode next switches on or off (or cancels the call when nothing will). */
    private static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, Receiver.class);
        i.setAction(ACTION);
        PendingIntent op = PendingIntent.getBroadcast(c, REQUEST, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long now = System.currentTimeMillis();
        long at = Store.library(c).nextChangeMs(now, Store.zone());
        if (at <= 0) {
            am.cancel(op);
            return;
        }
        // An inexact alarm is enough, and needs no special permission; a little after the switch, never before it.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(at, now) + 2000L, op);
    }

    /** The system calls this when library mode switches. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Context c = context.getApplicationContext();
            refresh(c);
            try {
                SlotNow.changed();   // the lock and the home screen's pill follow
            } catch (RuntimeException e) {
                CrashLog.note(c, "library switch", e);
            }
        }
    }
}
