package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * The strong lock: locks and pauses held for an app wherever it is opened from, not only from this home screen. It runs as a
 * small foreground service ({@link GuardService}) that looks at which app is in front. It needs two permissions that the
 * person gives in Android's own settings: Usage access (to see which app is in front, the same permission the daily limits
 * use) and Display over other apps (to put the lock or pause screen in front of the app).
 *
 * It is part of what a lock is, not an extra: it is on from the start and starts by itself as soon as something is locked or paused
 * and both permissions are given. The person is asked for the permissions right after saving a lock or a pause, again by a chip on
 * the home screen and a card on the dashboard for as long as one is missing, and the service is looked at again every time the app
 * comes up, after a restart, on the three-hourly job and when the task is swiped away.
 */
final class StrongMode {
    private static final String PREF_DECLINED = "strong_declined_at";
    private static final String PREF_CHAIN = "strong_chain_missing";
    private static final int RESTART_CODE = 7003;

    /** What {@link #configChanged} saw last, so that saving the settings starts or stops the service only when that changes. */
    private static Boolean lastShould;

    private StrongMode() {
    }

    // ------------------------------------------------------------------ what is true now

    static boolean enabled(Context c) {
        try {
            return GuardRules.wantsStrong(LauncherStore.load(c));
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean hasUsage(Context c) {
        try {
            return ScreenTime.hasAccess(c);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean hasOverlay(Context c) {
        try {
            return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** One of the {@link GuardRules} states: off, nothing to hold yet, what is missing, or on. */
    static int state(Context c) {
        try {
            LauncherConfig cfg = LauncherStore.load(c);
            return GuardRules.state(GuardRules.wantsStrong(cfg), GuardRules.worthWatching(cfg), hasUsage(c), hasOverlay(c));
        } catch (RuntimeException e) {
            return GuardRules.OFF;
        }
    }

    /** True when something is locked or paused but a permission is missing, so it holds on the home screen only. */
    static boolean needsAttention(Context c) {
        return GuardRules.needsPermission(state(c));
    }

    /** True when the watcher has written a beat lately. */
    static boolean running() {
        return GuardRules.watching(GuardService.lastBeat(), System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ keeping the service in step

    /**
     * Makes the service match the settings: running when something is locked or paused and both permissions are given, stopped
     * otherwise. Safe to call whenever a screen opens, after a restart and after the settings change.
     */
    static void ensure(Context context) {
        final Context c = context.getApplicationContext();
        try {
            if (state(c) == GuardRules.ON) {
                if (running()) return;   // already looking at the screen: nothing to start
                Intent i = new Intent(c, GuardService.class);
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
                else c.startService(i);
            } else {
                c.stopService(new Intent(c, GuardService.class));
            }
        } catch (RuntimeException e) {
            // Android may refuse to start a service from where we are (the app is in the background): the next screen opens it.
            CrashLog.note(c, "strong lock start", e);
        }
    }

    /**
     * The settings were saved: start or stop the service when that changes whether anything has to be held. Called from the one place
     * that saves them, so a lock made on any screen starts holding at once.
     */
    static void configChanged(Context c, LauncherConfig cfg) {
        boolean should = GuardRules.shouldRun(cfg);
        synchronized (StrongMode.class) {
            if (lastShould != null && lastShould == should) return;
            lastShould = should;
        }
        ensure(c);
    }

    /**
     * The service was taken down with the task (some phones stop a whole app when it is swiped away): set it going again in a moment.
     * The permission to show over other apps and the exact-alarm permission both let Android start it from the background.
     */
    static void restartSoon(Context context) {
        final Context c = context.getApplicationContext();
        try {
            if (state(c) != GuardRules.ON) return;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = PendingIntent.getForegroundService(c, RESTART_CODE, new Intent(c, GuardService.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = SystemClock.elapsedRealtime() + 2000L;
            if (AlarmScheduler.exactAllowed(c)) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        } catch (RuntimeException e) {
            CrashLog.note(c, "strong lock restart", e);
        }
    }

    // ------------------------------------------------------------------ asking for what is missing

    /**
     * Right after a lock or a pause was saved: if a permission is missing, ask for it (unless the person just said "Not now"). One
     * step at a time: the first missing one now, the next when the person comes back from Android's page ({@link #afterReturn}).
     */
    static void offer(Activity a) {
        try {
            long last = Store.prefs(a).getLong(PREF_DECLINED, 0);
            long since = last == 0 ? -1 : Math.max(0, System.currentTimeMillis() - last);
            if (GuardRules.shouldOffer(state(a), since)) ask(a);
        } catch (RuntimeException e) {
            CrashLog.note(a, "strong lock offer", e);
        }
    }

    /** Starts asking for what is missing (from the chip, the dashboard card, the settings card). */
    static void ask(Activity a) {
        try {
            if (!GuardRules.needsPermission(state(a))) return;
            Store.prefs(a).edit().putInt(PREF_CHAIN, GuardRules.missing(hasUsage(a), hasOverlay(a))).apply();
            askNext(a);
        } catch (RuntimeException e) {
            CrashLog.note(a, "strong lock ask", e);
        }
    }

    private static void askNext(Activity a) {
        switch (GuardRules.nextAsk(hasUsage(a), hasOverlay(a))) {
            case GuardRules.ASK_USAGE:
                UsageAccess.ask(a, true);
                break;
            case GuardRules.ASK_OVERLAY:
                askOverlayExplained(a);
                break;
            default:
                break;
        }
    }

    /** The person said "Not now": do not offer again right after the next save, and do not carry on asking. */
    static void declined(Context c) {
        try {
            Store.prefs(c).edit().putLong(PREF_DECLINED, System.currentTimeMillis()).remove(PREF_CHAIN).apply();
        } catch (RuntimeException e) {
            CrashLog.note(c, "strong lock declined", e);
        }
    }

    /**
     * Back from one of Android's pages (or any screen coming to the front): start or stop the service to match, and when the page just
     * visited was given and the other permission is still missing, ask for that one next.
     */
    static void afterReturn(final Activity a) {
        ensure(a);
        try {
            int before = Store.prefs(a).getInt(PREF_CHAIN, -1);
            if (before < 0) return;
            Store.prefs(a).edit().remove(PREF_CHAIN).apply();
            int now = GuardRules.missing(hasUsage(a), hasOverlay(a));
            if (!GuardRules.continueAsking(before, now)) return;
            Store.prefs(a).edit().putInt(PREF_CHAIN, now).apply();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!a.isFinishing() && !a.isDestroyed()) askNext(a);
            }, 500L);
        } catch (RuntimeException e) {
            CrashLog.note(a, "strong lock chain", e);
        }
    }

    /** Explains, then opens Android's page where Display over other apps is allowed for this app. */
    static void askOverlayExplained(final Activity a) {
        try {
            new DBuilder(a)
                    .setTitle("Allow display over other apps?")
                    .setMessage("To hold a lock when an app is opened from Recents, App info or a notification, Dinacharya Launcher shows "
                            + "its lock screen in front of that app. Android asks you to allow this.\n\n"
                            + "Next, Android's settings open. Find Dinacharya Launcher in the list and switch it on. "
                            + "You can switch it off there at any time.")
                    .setPositiveButton("Continue", (d, w) -> askOverlay(a))
                    .setNegativeButton("Not now", (d, w) -> declined(a))
                    .show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "overlay dialog", e);
        }
    }

    /** Opens Android's page where Display over other apps is allowed for this app. */
    static void askOverlay(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName())));
        } catch (RuntimeException e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
            } catch (RuntimeException e2) {
                Lang.toast(a, "Open Settings, Apps, Special access, Display over other apps", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------------ words

    /** One line for the settings, from the state. */
    static String line(int state) {
        switch (state) {
            case GuardRules.ON:
                return "On. Locks and pauses are held wherever an app opens from.";
            case GuardRules.IDLE:
                return "On. Nothing is locked or paused yet, so nothing is being watched.";
            case GuardRules.NEEDS_USAGE:
                return "Not holding yet: allow Usage access below.";
            case GuardRules.NEEDS_OVERLAY:
                return "Not holding yet: allow Display over other apps below.";
            case GuardRules.NEEDS_BOTH:
                return "Not holding yet: allow the two things below.";
            default:
                return "Off. Locks and pauses work when you open an app from this home screen.";
        }
    }

    /** The sentence about where locks (or pauses) hold, for the lock and pause screens. */
    static String holdsLine(Context c, boolean locks) {
        boolean on = enabled(c);
        if (on && hasUsage(c) && hasOverlay(c)) {
            return locks ? "Locks hold wherever the app is opened from."
                    : "Pauses hold wherever the app is opened from.";
        }
        if (on) {
            return locks ? "For now locks hold only when you open an app from this home screen. Allow the two things in Settings > Locked apps and they hold everywhere."
                    : "For now pauses work only when you open an app from this home screen. Allow the two things in Settings > Locked apps and they hold everywhere.";
        }
        return locks ? "Locks apply when you open an app from this home screen. A strict lock also holds when an app is opened from Recents, App info or a notification."
                : "Pauses work when you open an app from this home screen. A strict pause also holds when an app is opened from Recents, App info or a notification.";
    }

    /** What the problem report says about the locks: what is wanted, what is allowed, and whether the watcher is running. */
    static String report(Context c) {
        try {
            LauncherConfig cfg = LauncherStore.load(c);
            boolean usage = hasUsage(c), overlay = hasOverlay(c);
            StringBuilder sb = new StringBuilder();
            sb.append("Strong lock: ").append(GuardRules.wantsStrong(cfg) ? "on (a strict lock or pause is set)" : "off (no strict lock or pause)");
            sb.append("\nSomething locked or paused: ").append(GuardRules.worthWatching(cfg) ? "yes" : "no");
            sb.append("\nUsage access: ").append(usage ? "allowed" : "MISSING");
            sb.append("\nDisplay over other apps: ").append(overlay ? "allowed" : "MISSING");
            long beat = GuardService.lastBeat();
            sb.append("\nWatcher: ");
            if (running()) sb.append("running");
            else if (beat > 0) sb.append("not running (last look ").append(Math.max(0, (System.currentTimeMillis() - beat) / 1000L)).append(" s ago)");
            else sb.append("has not run since the app started");
            sb.append(GuardRules.state(GuardRules.wantsStrong(cfg), GuardRules.worthWatching(cfg), usage, overlay) == GuardRules.ON && !running()
                    ? "\nIt should be running and is not: Android may have stopped it." : "");
            return sb.toString();
        } catch (RuntimeException e) {
            return "Lock details unavailable.";
        }
    }
}
