package app.dinacharya.wallpaper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything Dinacharya needs from the phone, in the order it is asked for in the first-start setup: each step knows
 * whether it is done and how to ask for it. The same list drives the setup screen, the tour's last page and the Home card.
 */
final class Permissions {
    static final int NOTIF = 0, ALARMS_ON = 1, EXACT = 2, FULLSCREEN = 3, USAGE = 4, BATTERY = 5, MAKER = 6, HOME = 7, WALLPAPER = 8;
    static final int REQ_NOTIF = 7401, REQ_WALL = 7402;
    private static final String PREF_MAKER_DONE = "setup_maker_done";

    /** Android shows its own notification question once; after that the only way is the app's notification settings. */
    private static boolean notifAsked;

    static final class Step {
        final int id;
        final String title;
        final String why;
        final String action;
        final boolean ok;
        /** A second, smaller button for the step ("Greyed out?"), or null. */
        final String extra;

        Step(int id, String title, String why, String action, boolean ok) {
            this(id, title, why, action, ok, null);
        }

        Step(int id, String title, String why, String action, boolean ok, String extra) {
            this.id = id;
            this.title = title;
            this.why = why;
            this.action = action;
            this.ok = ok;
            this.extra = extra;
        }
    }

    private Permissions() {
    }

    /** The steps that apply to this phone, in order, each with its current state. */
    static List<Step> steps(Context c) {
        List<Step> out = new ArrayList<>();
        out.add(new Step(NOTIF, "Notifications", "Alarms and reminders show up here, also over the lock screen.", "Allow", isOk(c, NOTIF)));
        out.add(new Step(ALARMS_ON, "Alarms on", "Rings when a slot of your day starts and asks one question.", "Turn on", isOk(c, ALARMS_ON)));
        if (Build.VERSION.SDK_INT >= 31) {
            out.add(new Step(EXACT, "Exact alarms", "Rings on the minute a slot starts, not minutes late.", "Allow", isOk(c, EXACT)));
        }
        if (Build.VERSION.SDK_INT >= 34) {
            out.add(new Step(FULLSCREEN, "Alarm screen over the lock screen", "Opens the question by itself when the phone is locked.", "Allow", isOk(c, FULLSCREEN)));
        }
        boolean usage = isOk(c, USAGE);
        out.add(new Step(USAGE, "Phone-use time (optional)", "Shows your phone time and counts daily app limits. Read on this phone only.",
                "Allow", usage, !usage && RestrictedSettings.likely(c) ? "Greyed out or “Restricted setting”?" : null));
        boolean battery = isOk(c, BATTERY);
        out.add(new Step(BATTERY, "No battery limits", "Stops the phone from putting the app to sleep. Set Battery to Unrestricted on the app’s info page.",
                "Allow", battery, battery ? null : "Open Android’s battery list"));
        if (AlarmHealth.knownPhoneMaker()) {
            out.add(new Step(MAKER, "Allow background activity", "Your phone's maker stops apps in the background unless you allow it.", "Open", isOk(c, MAKER)));
        }
        out.add(new Step(HOME, "Make Dinacharya Launcher your home app", "Pressing Home shows your day.", "Set", isOk(c, HOME)));
        out.add(new Step(WALLPAPER, "Put the Dinacharya Launcher wallpaper in place",
                WallpaperSetup.lockOnly(c)
                        ? "Only the lock screen has it. On Android’s preview, choose Home screen (or both)."
                        : "Shows your day on the lock screen and in recent apps. Android asks you to confirm once.",
                "Set", isOk(c, WALLPAPER)));
        return out;
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
                default: return true;
            }
        } catch (RuntimeException e) {
            return true;   // cannot tell: do not nag about it
        }
    }

    /** The step's second button. */
    static void requestExtra(Activity a, int id) {
        try {
            if (id == USAGE) RestrictedSettings.explain(a);
            else if (id == BATTERY) BatterySetup.openList(a);
        } catch (RuntimeException e) {
            CrashLog.note(a, "permission extra " + id, e);
        }
    }

    static int missing(Context c) {
        int n = 0;
        for (Step s : steps(c)) if (!s.ok) n++;
        return n;
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
