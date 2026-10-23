package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * The strong lock: locks and pauses held for an app wherever it is opened from, not only from this home screen. It runs as a
 * small foreground service ({@link GuardService}) that looks at which app is in front. It needs two permissions that the
 * person gives in Android's own settings: Usage access (to see which app is in front, the same permission the daily limits
 * use) and Display over other apps (to put the lock or pause screen in front of the app). It is off until the person
 * turns it on in the home settings.
 */
final class StrongMode {
    private StrongMode() {
    }

    static boolean enabled(Context c) {
        try {
            return LauncherStore.load(c).strong;
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

    /** One of the {@link GuardRules} states: off, what is missing, or on. */
    static int state(Context c) {
        return GuardRules.state(enabled(c), hasUsage(c), hasOverlay(c));
    }

    /**
     * Makes the service match the setting: running when the strong lock is on and allowed, stopped otherwise. Safe to call
     * whenever a screen opens, after a restart and after the setting changes.
     */
    static void ensure(Context context) {
        final Context c = context.getApplicationContext();
        try {
            if (state(c) == GuardRules.ON) {
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

    /** One line for the settings, from the state. */
    static String line(int state) {
        switch (state) {
            case GuardRules.ON:
                return "On. Locks and pauses are held wherever an app opens from.";
            case GuardRules.NEEDS_USAGE:
                return "Almost on: allow Usage access below.";
            case GuardRules.NEEDS_OVERLAY:
                return "Almost on: allow Display over other apps below.";
            case GuardRules.NEEDS_BOTH:
                return "Almost on: allow the two things below.";
            default:
                return "Off. Locks and pauses work when you open an app from this home screen.";
        }
    }
}
