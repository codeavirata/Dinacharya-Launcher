package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything that has to be right for an alarm to ring on a real phone, each with a plain
 * yes or no. Shown on Home (a short summary) and on Settings > Alarms (the full list).
 */
final class AlarmHealth {
    static final int ON = 0, NOTIF = 1, EXACT = 2, FULL = 3, BATTERY = 4, PHONE = 5, POPUP = 6;

    /** Fine. */
    static final int OK = 0;
    /** Missing, and alarms cannot work properly without it. */
    static final int NEEDED = 1;
    /** Not required, but alarms are more reliable with it. */
    static final int ADVICE = 2;

    static final class Item {
        final int id;
        final String title;
        final String why;
        final int state;

        Item(int id, String title, String why, int state) {
            this.id = id;
            this.title = title;
            this.why = why;
            this.state = state;
        }
    }

    private AlarmHealth() {
    }

    static List<Item> check(Context c, Routine r) {
        List<Item> out = new ArrayList<>();
        out.add(new Item(ON, "Alarms switched on", "Nothing rings while this is off.", r.alarmOn ? OK : NEEDED));
        out.add(new Item(NOTIF, "Notifications", "The alarm question appears here, also on the lock screen.",
                notificationsOn(c) ? OK : NEEDED));
        out.add(new Item(EXACT, "Exact alarms", "Rings on the minute a slot starts, not a few minutes late.",
                AlarmScheduler.exactAllowed(c) ? OK : NEEDED));
        out.add(new Item(FULL, "Alarm screen over the lock screen", "Opens the question by itself when the phone is locked.",
                fullScreenOk(c) ? OK : ADVICE));
        out.add(new Item(POPUP, "Alarm pop-up allowed", "Shows the question on top while you use the phone.",
                popupOk(c) ? OK : NEEDED));
        out.add(new Item(BATTERY, "No battery limits", "Stops the phone from putting the app to sleep. Set Battery to Unrestricted on the app info page.",
                batteryOk(c) ? OK : ADVICE));
        if (knownPhoneMaker()) {
            out.add(new Item(PHONE, "Allow background activity", "Some phones stop apps unless you allow this in their own settings.",
                    ADVICE));
        }
        return out;
    }

    /** The needed things that are missing, apart from the on/off switch, as "Notifications, Exact alarms". */
    static String missingNames(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            if (it.id == ON || it.state != NEEDED) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(it.title);
        }
        return sb.toString();
    }

    static boolean notificationsOn(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** False when the alarm notification channel was switched to a quiet one (then nothing pops up over what you are doing). */
    static boolean popupOk(Context c) {
        if (Build.VERSION.SDK_INT < 26) return true;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return true;
            android.app.NotificationChannel ch = nm.getNotificationChannel("ring");
            return ch == null || ch.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
        } catch (RuntimeException e) {
            return true;
        }
    }

    static boolean fullScreenOk(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.canUseFullScreenIntent();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static boolean batteryOk(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ phone makers that stop apps

    private static String maker() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String b = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.ROOT);
        return m + " " + b;
    }

    private static boolean makerIs(String... names) {
        String m = maker();
        for (String n : names) if (m.contains(n)) return true;
        return false;
    }

    static boolean knownPhoneMaker() {
        return makerIs("oppo", "realme", "oneplus", "xiaomi", "redmi", "poco", "vivo", "iqoo", "huawei", "honor",
                "samsung", "asus", "tecno", "infinix", "itel");
    }

    private static Intent comp(String pkg, String cls) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(pkg, cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** The maker's own "auto-start" or "background" screens, best guesses first. */
    private static List<Intent> makerScreens() {
        List<Intent> l = new ArrayList<>();
        if (makerIs("oppo", "realme", "oneplus")) {
            l.add(comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            l.add(comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"));
            l.add(comp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"));
        }
        if (makerIs("xiaomi", "redmi", "poco")) {
            l.add(comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        }
        if (makerIs("vivo", "iqoo")) {
            l.add(comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            l.add(comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
        }
        if (makerIs("huawei", "honor")) {
            l.add(comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
            l.add(comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
        }
        if (makerIs("samsung")) {
            l.add(comp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"));
        }
        if (makerIs("asus")) {
            l.add(comp("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"));
        }
        return l;
    }

    /** Opens the phone maker's background settings if it can, otherwise this app's own settings page. */
    static void openPhoneSettings(Activity a) {
        for (Intent i : makerScreens()) {
            try {
                a.startActivity(i);
                say(a, "Find Dinacharya Launcher and allow auto-start or background activity.");
                return;
            } catch (RuntimeException ignored) {
                // not on this phone; try the next
            }
        }
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
            say(a, "Open Battery, and choose Unrestricted or Allow background activity.");
        } catch (RuntimeException e) {
            say(a, "Open Settings, Apps, Dinacharya Launcher, Battery");
        }
    }

    private static void say(Context c, String s) {
        Lang.toast(c, s, android.widget.Toast.LENGTH_LONG).show();
    }
}
