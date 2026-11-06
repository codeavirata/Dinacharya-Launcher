package app.dinacharya.wallpaper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Everything Dinacharya can ask of the phone: each step knows whether it is done and how to ask for it. Which steps a person is asked
 * for depends on the features they choose ({@link FeaturePlan}, {@link Features}); nothing is asked for a feature that was not chosen.
 */
final class Permissions {
    static final int NOTIF = FeaturePlan.S_NOTIF, ALARMS_ON = FeaturePlan.S_ALARMS_ON, EXACT = FeaturePlan.S_EXACT,
            FULLSCREEN = FeaturePlan.S_FULLSCREEN, USAGE = FeaturePlan.S_USAGE, BATTERY = FeaturePlan.S_BATTERY, MAKER = FeaturePlan.S_MAKER,
            HOME = FeaturePlan.S_HOME, WALLPAPER = FeaturePlan.S_WALLPAPER, OVERLAY = FeaturePlan.S_OVERLAY, DND = FeaturePlan.S_DND;
    static final int REQ_NOTIF = 7401, REQ_WALL = 7402;
    private static final String PREF_MAKER_DONE = "setup_maker_done";

    /** Android shows its own notification question once; after that the only way is the app's notification settings. */
    private static boolean notifAsked;

    private Permissions() {
    }

    static boolean isOk(Context c, int id) {
        try {
            switch (id) {
                case NOTIF: return AlarmHealth.notificationsOn(c);
                case ALARMS_ON: return Store.load(c).alarmOn;
                case EXACT: return Build.VERSION.SDK_INT < 31 || AlarmScheduler.exactAllowed(c);
                case FULLSCREEN: return AlarmHealth.fullScreenOk(c);
                case USAGE: return ScreenTime.hasAccess(c);
                case BATTERY: return AlarmHealth.batteryOk(c);
                case MAKER: return Store.prefs(c).getBoolean(PREF_MAKER_DONE, false);
                case HOME: return HomeRole.isDefault(c);
                case WALLPAPER: return WallpaperSetup.settled(c);
                case OVERLAY: return StrongMode.hasOverlay(c);
                case DND: return Quiet.hasAccess(c);
                default: return true;
            }
        } catch (RuntimeException e) {
            return true;   // cannot tell: do not nag about it
        }
    }

    /** The step's second button ("Greyed out?", the battery list). */
    static void requestExtra(Activity a, int id) {
        try {
            if (id == USAGE) RestrictedSettings.explain(a);
            else if (id == BATTERY) BatterySetup.openList(a);
        } catch (RuntimeException e) {
            CrashLog.note(a, "permission extra " + id, e);
        }
    }

    /** Asks for one step: a system question, the right settings page, or (for alarms) just switches it on. */
    static void request(Activity a, int id) {
        try {
            switch (id) {
                case NOTIF: askNotifications(a); break;
                case ALARMS_ON: switchAlarmsOn(a); break;
                case EXACT: openExact(a); break;
                case FULLSCREEN: openFullScreen(a); break;
                case USAGE: UsageAccess.ask(a); break;
                case BATTERY: BatterySetup.ask(a); break;
                case MAKER:
                    Store.prefs(a).edit().putBoolean(PREF_MAKER_DONE, true).apply();
                    AlarmHealth.openPhoneSettings(a);
                    break;
                case HOME: HomeRole.request(a); break;
                case WALLPAPER: WallpaperSetup.request(a, REQ_WALL); break;
                case OVERLAY: StrongMode.askOverlay(a); break;
                case DND: QuietDialog.openAccess(a); break;
                default: break;
            }
        } catch (RuntimeException e) {
            CrashLog.note(a, "permission step " + id, e);
        }
    }

    private static void askNotifications(Activity a) {
        if (Build.VERSION.SDK_INT >= 33 && !notifAsked
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifAsked = true;
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
        try {
            a.startActivity(i);
        } catch (RuntimeException e) {
            Lang.toast(a, "Open Settings, Apps, Dinacharya Launcher, Notifications", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static void switchAlarmsOn(Activity a) {
        Routine r = Store.load(a);
        if (r.alarmOn) return;
        r.alarmOn = true;
        Store.save(a, r);
        if (a instanceof MainActivity) ((MainActivity) a).syncAlarmSwitch();   // the dashboard keeps its own copy of the routine
        final long now = System.currentTimeMillis();
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            try {
                Store.history(app).append(Ev.alarms(now, true));
            } catch (RuntimeException e) {
                CrashLog.note(app, "alarm log", e);
            }
        }, "ev-alarms").start();
        AlarmScheduler.reschedule(a);
        DayCloser.schedule(a);
    }

    private static void openExact(Activity a) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            i.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (RuntimeException e) {
            Lang.toast(a, "Open Settings, Apps, Dinacharya Launcher, Alarms and reminders", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static void openFullScreen(Activity a) {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
            i.setData(Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (RuntimeException e) {
            Lang.toast(a, "Open Settings, Apps, Dinacharya Launcher, and allow full screen notifications", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
