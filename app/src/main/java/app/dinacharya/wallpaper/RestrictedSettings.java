package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Newer Android (13 and later) locks some special-access switches, Usage access among them on the latest versions, for
 * an app that was installed from a file instead of a store: the switch is greyed out and says "Restricted setting".
 * The person unlocks it once, in the app's own info page (the ⋮ menu, "Allow restricted settings"). This explains
 * that, and opens the page. An app installed from Google Play is not restricted.
 */
final class RestrictedSettings {
    private static final Set<String> STORES = new HashSet<>(Arrays.asList(
            "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.huawei.appmarket",
            "com.xiaomi.mipicks", "com.xiaomi.market", "com.heytap.market", "com.oppo.market", "com.vivo.appstore",
            "com.google.android.feedback"));

    private RestrictedSettings() {
    }

    /** True when this phone may well lock the switch: Android 13 or later, and the app did not come from a store. */
    static boolean likely(Context c) {
        if (Build.VERSION.SDK_INT < 33) return false;
        try {
            PackageManager pm = c.getPackageManager();
            InstallSourceInfo info = pm.getInstallSourceInfo(c.getPackageName());
            if (info.getPackageSource() == PackageInstaller.PACKAGE_SOURCE_STORE) return false;
            String installer = info.getInstallingPackageName();
            return installer == null || !STORES.contains(installer);
        } catch (RuntimeException | PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    /** Opens Android's info page for this app. */
    static void openAppInfo(Activity a) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (RuntimeException e) {
            Lang.toast(a, "Open Settings, Apps, Dinacharya Launcher", Toast.LENGTH_LONG).show();
        }
    }

    /** The plain steps, with a button that opens the right page. */
    static void explain(final Activity a) {
        try {
            new DBuilder(a)
                    .setTitle("Android is holding this setting back")
                    .setMessage("Android locks some switches for an app that was installed from a file instead of Google Play. "
                            + "It is a one-time unlock:\n\n"
                            + "1. Tap “Open app info” below.\n"
                            + "2. Tap the ⋮ menu at the top right.\n"
                            + "3. Choose “Allow restricted settings” and confirm with your PIN or fingerprint.\n"
                            + "4. Go back and switch on Usage access.\n\n"
                            + "If ⋮ does not show that choice yet, first try to switch Usage access on once: "
                            + "Android then shows it.")
                    .setPositiveButton("Open app info", (d, w) -> openAppInfo(a))
                    .setNegativeButton("Close", null)
                    .show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "restricted settings dialog", e);
        }
    }
}
