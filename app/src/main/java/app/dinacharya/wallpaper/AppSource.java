package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The apps installed on the phone, found through Android's launcher interface (the same one every home
 * screen uses, so no special "see all apps" permission is needed), and the ways to open one.
 */
final class AppSource {
    interface Listener {
        void onAppsChanged();
    }

    private final Context app;
    private final LauncherApps apps;
    private final UserManager users;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LauncherApps.Callback callback;
    private Listener listener;
    private final Runnable fire = new Runnable() {
        @Override
        public void run() {
            if (listener != null) listener.onAppsChanged();
        }
    };

    AppSource(Context c) {
        app = c.getApplicationContext();
        apps = (LauncherApps) app.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        users = (UserManager) app.getSystemService(Context.USER_SERVICE);
    }

    /** Every app that has a launcher icon, in every profile (personal and work), sorted by name (Dinacharya is in it too: opening it shows the dashboard). Do not call on the main thread. */
    List<AppEntry> load() {
        List<AppEntry> out = new ArrayList<>();
        try {
            if (apps != null && users != null) {
                UserHandle me = Process.myUserHandle();
                for (UserHandle u : users.getUserProfiles()) {
                    long serial = users.getSerialNumberForUser(u);
                    boolean work = !u.equals(me);
                    for (LauncherActivityInfo info : apps.getActivityList(null, u)) {
                        ComponentName cn = info.getComponentName();
                        CharSequence label = info.getLabel();
                        out.add(new AppEntry(AppKey.make(cn.getPackageName(), cn.getClassName(), serial),
                                label == null ? "" : label.toString(), work));
                    }
                }
            }
        } catch (RuntimeException e) {
            CrashLog.note(app, "app list", e);
            out.clear();
        }
        if (out.isEmpty()) out = fallback();
        AppSearch.sort(out);
        return out;
    }

    /** The older way, for the rare phone where the launcher interface will not answer. Main profile only. */
    private List<AppEntry> fallback() {
        List<AppEntry> out = new ArrayList<>();
        try {
            PackageManager pm = app.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (ri.activityInfo == null) continue;
                CharSequence label = ri.loadLabel(pm);
                out.add(new AppEntry(AppKey.make(ri.activityInfo.packageName, ri.activityInfo.name, 0),
                        label == null ? "" : label.toString(), false));
            }
        } catch (RuntimeException e) {
            CrashLog.note(app, "app list fallback", e);
        }
        return out;
    }

    private UserHandle userOf(String key) {
        try {
            UserHandle u = users == null ? null : users.getUserForSerialNumber(AppKey.user(key));
            return u == null ? Process.myUserHandle() : u;
        } catch (RuntimeException e) {
            return Process.myUserHandle();
        }
    }

    /** Opens the app. False if Android refused (it may have been removed). */
    boolean launch(Activity from, String key) {
        try {
            ComponentName cn = new ComponentName(AppKey.pkg(key), AppKey.cls(key));
            if (apps != null) {
                apps.startMainActivity(cn, userOf(key), null, null);
                return true;
            }
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            from.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Opens Android's page for the app (where it can be uninstalled or its permissions changed). */
    boolean details(Activity from, String key) {
        try {
            if (apps == null) return false;
            apps.startAppDetailsActivity(new ComponentName(AppKey.pkg(key), AppKey.cls(key)), userOf(key), null, null);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Calls {@code l} (on the main thread) when apps are installed, removed or updated. */
    void watch(Listener l) {
        unwatch();
        if (apps == null) return;
        listener = l;
        callback = new LauncherApps.Callback() {
            private void changed() {
                ui.removeCallbacks(fire);
                ui.postDelayed(fire, 700);
            }

            @Override
            public void onPackageRemoved(String packageName, UserHandle user) {
                changed();
            }

            @Override
            public void onPackageAdded(String packageName, UserHandle user) {
                changed();
            }

            @Override
            public void onPackageChanged(String packageName, UserHandle user) {
                changed();
            }

            @Override
            public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                changed();
            }

            @Override
            public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
                changed();
            }
        };
        try {
            apps.registerCallback(callback, ui);
        } catch (RuntimeException e) {
            callback = null;
        }
    }

    void unwatch() {
        ui.removeCallbacks(fire);
        if (callback != null && apps != null) {
            try {
                apps.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        callback = null;
        listener = null;
    }
}
