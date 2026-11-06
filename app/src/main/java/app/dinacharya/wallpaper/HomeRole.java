package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Is Dinacharya the phone's home app, and the ways to make it one (or to go back). */
final class HomeRole {
    private HomeRole() {
    }

    /** True when pressing Home opens Dinacharya's minimal home screen. */
    static boolean isDefault(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static android.content.SharedPreferences watch(Context c) {
        return c.getApplicationContext().getSharedPreferences("homewatch", Context.MODE_PRIVATE);
    }

    /**
     * Takes note of whether Dinacharya is the home app right now (see {@link HomeWatch}). Called whenever a screen of the
     * app opens, so a later loss of the home role can be noticed.
     */
    static void look(Context c) {
        try {
            android.content.SharedPreferences p = watch(c);
            boolean was = p.getBoolean("wasHome", false);
            boolean keep = HomeWatch.remember(was, isDefault(c));
            if (keep != was) p.edit().putBoolean("wasHome", keep).apply();
        } catch (RuntimeException e) {
            CrashLog.note(c, "home look", e);
        }
    }

    /** True when Dinacharya used to be the home app, is not now, and the person should be offered the way back. */
    static boolean lost(Context c) {
        try {
            android.content.SharedPreferences p = watch(c);
            return HomeWatch.shouldAsk(p.getBoolean("wasHome", false), isDefault(c), p.getLong("snooze", 0), System.currentTimeMillis());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * True while Dinacharya used to be the home app and is not now, whatever "Not now" was said before. The pop-up stays quiet for a day
     * after "Not now", but the card on the dashboard's Home tab stays until it is put right or the person says "Stop asking".
     */
    static boolean lostNow(Context c) {
        try {
            return watch(c).getBoolean("wasHome", false) && !isDefault(c);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * What the problem report says about the home app: which launcher the phone opens on Home right now, whether Dinacharya was it, and how
     * the app last ended. After a Force stop some phones switch back to their own launcher; this shows whether yours did.
     */
    static String report(Context c) {
        try {
            StringBuilder sb = new StringBuilder();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            sb.append("Home button opens: ").append(ri == null || ri.activityInfo == null ? "nothing found" : ri.activityInfo.packageName);
            sb.append("\nDinacharya is the home app: ").append(isDefault(c) ? "yes" : "NO");
            android.content.SharedPreferences p = watch(c);
            sb.append("\nIt was the home app before: ").append(p.getBoolean("wasHome", false) ? "yes" : "no");
            long snooze = p.getLong("snooze", 0);
            if (snooze > System.currentTimeMillis()) sb.append(" (the pop-up is quiet for ").append((snooze - System.currentTimeMillis()) / 3600000L).append(" h)");
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) c.getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    sb.append("\nAndroid's home role is held by Dinacharya: ").append(rm.isRoleHeld(RoleManager.ROLE_HOME) ? "yes" : "no");
                }
            }
            sb.append("\nHow the app last ended: ").append(Keeper.recentExits(c));
            return sb.toString();
        } catch (RuntimeException e) {
            return "Home app details unavailable.";
        }
    }

    /** "Not now": stay quiet for a day. */
    static void snooze(Context c) {
        try {
            watch(c).edit().putLong("snooze", HomeWatch.snoozeFrom(System.currentTimeMillis())).apply();
        } catch (RuntimeException ignored) {
            // asked again next time, at worst
        }
    }

    /** "Stop asking": the person does not want Dinacharya as the home app (until they make it one again). */
    static void forget(Context c) {
        try {
            watch(c).edit().putBoolean("wasHome", false).apply();
        } catch (RuntimeException ignored) {
            // nothing more to do
        }
    }

    /** The offer: tells the person the Home button no longer opens Dinacharya, with one tap to put it back. */
    static void offerBack(final Activity a, int theme) {
        if (a.isFinishing()) return;
        new DBuilder(a, theme)
                .setTitle("Dinacharya Launcher is not your home app any more")
                .setMessage("The Home button now opens another launcher. This happens after an update or a reinstall, when another "
                        + "home app is picked, or on some phones when the app is closed from App info (Force stop). "
                        + "Make Dinacharya Launcher your home app again?")
                .setPositiveButton("Make it my home app", (d, w) -> request(a))
                .setNegativeButton("Not now", (d, w) -> snooze(a))
                .setNeutralButton("Stop asking", (d, w) -> forget(a))
                .setOnCancelListener(d -> snooze(a))
                .show();
    }

    /** Asks Android to make Dinacharya the home app (a system dialog on Android 10 and later). */
    static void request(Activity a) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) a.getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7311);
                    return;
                }
            }
        } catch (RuntimeException e) {
            CrashLog.note(a, "home role", e);
        }
        openChooser(a);
    }

    /** Opens Android's own "Home app" setting, where any launcher can be picked (also the way back). */
    static void openChooser(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            return;
        } catch (RuntimeException ignored) {
            // try the next one
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (RuntimeException e) {
            Lang.toast(a, "Open Settings, then Apps, Default apps, Home app", Toast.LENGTH_LONG).show();
        }
    }
}
