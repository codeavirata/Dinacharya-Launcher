package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Letting Dinacharya run in the background so alarms are not late. Android's general battery list only shows apps that
 * are already exempt until you switch it to "All apps", so Dinacharya looked missing. The app's own info page always has
 * it, so that is where this sends the person, with the exact taps. (The one-tap "allow" popup is not used: Google Play
 * does not accept it for alarm apps.)
 */
final class BatterySetup {
    private BatterySetup() {
    }

    static void ask(final Activity a) {
        try {
            new DBuilder(a)
                    .setTitle("Let Dinacharya Launcher run in the background")
                    .setMessage("So alarms are never late, set Dinacharya Launcher’s battery use to “Unrestricted” (some phones say "
                            + "“Don’t optimise” or “No restrictions”).\n\n"
                            + "1. Tap “Open app info” below.\n"
                            + "2. Tap Battery (or “App battery usage”).\n"
                            + "3. Choose Unrestricted.\n\n"
                            + "Android’s general battery list hides Dinacharya Launcher until you switch its filter to “All apps”.")
                    .setPositiveButton("Open app info", (d, w) -> RestrictedSettings.openAppInfo(a))
                    .setNeutralButton("Battery list", (d, w) -> openList(a))
                    .setNegativeButton("Not now", null)
                    .show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "battery dialog", e);
        }
    }

    /** Android's list of apps that are exempt from battery limits (switch it to All apps to find Dinacharya). */
    static void openList(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            Lang.toast(a, "Switch the filter to All apps, then find Dinacharya Launcher.", Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            RestrictedSettings.openAppInfo(a);
        }
    }
}
