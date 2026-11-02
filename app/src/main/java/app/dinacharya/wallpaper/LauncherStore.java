package app.dinacharya.wallpaper;

import android.content.Context;

/** Keeps the minimal home screen's settings with the rest of the app's settings (so the backup includes them). */
final class LauncherStore {
    static final String KEY = "launcher";

    private LauncherStore() {
    }

    static LauncherConfig load(Context c) {
        try {
            return LauncherConfig.parse(Store.prefs(c).getString(KEY, null));
        } catch (RuntimeException e) {
            return new LauncherConfig();
        }
    }

    static void save(Context c, LauncherConfig cfg) {
        Store.prefs(c).edit().putString(KEY, cfg.serialize()).apply();
        DriveJob.changedSoon(c);
        StrongMode.configChanged(c, cfg);   // a lock made on any screen starts holding at once; the last one removed stops it
    }

    /** Whether the person has made any choice yet (used to decide whether to show first-time hints). */
    static boolean touched(Context c) {
        return Store.prefs(c).contains(KEY);
    }
}
