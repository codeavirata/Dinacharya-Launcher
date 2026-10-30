package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Asks for Android's "Usage access" in two steps: first this plain explanation of what is read
 * and where it goes (Google Play requires that before a sensitive permission is requested),
 * then Android's own settings screen, where the person switches it on.
 */
final class UsageAccess {
    private UsageAccess() {
    }

    static void ask(final Activity a) {
        ask(a, false);
    }

    /** {@code forLocks}: asked so that a lock can see which app is in front, which the explanation then says too. */
    static void ask(final Activity a, final boolean forLocks) {
        try {
            AlertDialog.Builder b = new DBuilder(a)
                    .setTitle("Allow usage access?")
                    .setMessage("Dinacharya Launcher reads which apps you used and for how long, so it can show your phone "
                            + "time on the home screen and in your reports, and count the minutes for any daily app "
                            + "limit you set on the home screen.\n\n"
                            + "It is read on this phone only. It is not sent to the developer or to anyone else. "
                            + "If you turn on Google Drive backup, your saved reports (including the names of the "
                            + "apps you used most) go to your own private space in your own Drive, and nowhere else.\n\n"
                            + "Next, Android's settings open. Find Dinacharya Launcher in the list and switch it on. "
                            + "You can switch it off there at any time."
                            + (forLocks
                            ? "\n\nTo hold a lock when an app is opened from Recents, App info or a notification, it also needs to see which app is in front. "
                            + "Only the name of that app is compared with your own list of locked apps. Nothing is saved or sent."
                            : "")
                            + (RestrictedSettings.likely(a)
                            ? "\n\nIf the switch is greyed out (“Restricted setting”), tap “Greyed out?” for the one-time unlock."
                            : ""))
                    .setPositiveButton("Continue", (d, w) -> open(a))
                    .setNegativeButton("Not now", (d, w) -> {
                        if (forLocks) StrongMode.declined(a);
                    });
            if (RestrictedSettings.likely(a)) {
                b.setNeutralButton("Greyed out?", (d, w) -> RestrictedSettings.explain(a));
            }
            b.show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "usage access dialog", e);
        }
    }

    private static void open(Activity a) {
        Intent direct = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        if (Build.VERSION.SDK_INT >= 29) direct.setData(Uri.parse("package:" + a.getPackageName()));
        try {
            a.startActivity(direct);
        } catch (RuntimeException e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (RuntimeException e2) {
                Lang.toast(a, "Open Settings, then Apps, Special app access, Usage access",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
}
