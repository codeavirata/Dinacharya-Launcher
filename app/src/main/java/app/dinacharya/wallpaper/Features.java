package app.dinacharya.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * The features a person can choose in the setup, in plain words: what each one does, what it asks of the phone and why. A feature is
 * set up when the things it needs are allowed ({@link FeaturePlan} says which, and in what order). Nothing is asked for a feature that was
 * not chosen, and a chosen one can be finished later from Home or Settings.
 */
final class Features {
    private static final String PREF_CHOSEN = "features_chosen";

    private Features() {
    }

    // ------------------------------------------------------------------ the features

    static String title(int f) {
        switch (f) {
            case FeaturePlan.F_ALARMS: return "Day alarms";
            case FeaturePlan.F_USAGE: return "Phone time and app limits";
            case FeaturePlan.F_FOCUS: return "Pause and lock apps";
            case FeaturePlan.F_HOME: return "Home screen and wallpaper";
            case FeaturePlan.F_QUIET: return "Quiet phone";
            default: return "";
        }
    }

    static String tagline(int f) {
        switch (f) {
            case FeaturePlan.F_ALARMS: return "Rings when each part of your day begins and asks one quick question.";
            case FeaturePlan.F_USAGE: return "See where your phone time goes, and set a daily limit for any app.";
            case FeaturePlan.F_FOCUS: return "A pause before the apps that pull you away, and a lock when you need to focus.";
            case FeaturePlan.F_HOME: return "Your whole day on the home screen and lock screen, with nothing else on it.";
            case FeaturePlan.F_QUIET: return "Silences your phone for the hours and slots you choose.";
            default: return "";
        }
    }

    /** The scene (see {@link FeatureScenes}) that shows the feature. */
    static int scene(int f) {
        switch (f) {
            case FeaturePlan.F_ALARMS: return FeatureScenes.ALARMS;
            case FeaturePlan.F_USAGE: return FeatureScenes.USAGE;
            case FeaturePlan.F_FOCUS: return FeatureScenes.FOCUS;
            case FeaturePlan.F_HOME: return FeatureScenes.HOME;
            case FeaturePlan.F_QUIET: return FeatureScenes.QUIET;
            default: return FeatureScenes.DONE;
        }
    }

    static int accent(int f) {
        return FeatureScenes.accent(scene(f));
    }

    /** What the feature needs on this phone, in the order it is asked for. */
    static int[] needs(int f) {
        return FeaturePlan.needs(f, Build.VERSION.SDK_INT, AlarmHealth.knownPhoneMaker());
    }

    /** "Notifications · Exact alarms": the names of what the feature will ask for, so nothing comes as a surprise. */
    static String asks(int f) {
        StringBuilder sb = new StringBuilder();
        for (int s : needs(f)) {
            if (FeaturePlan.automatic(s)) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(Lang.tr(stepName(s)));
        }
        return sb.toString();
    }

    /** True when everything the feature needs (leaving out the extras that only make it better) is allowed. */
    static boolean ready(Context c, int f) {
        for (int s : needs(f)) {
            if (FeaturePlan.optional(s)) continue;
            if (!Permissions.isOk(c, s)) return false;
        }
        return true;
    }

    /** "Needs: notifications, exact alarms": what a chosen feature still lacks (the extras that only make it better are left out). */
    static String lacking(Context c, int f) {
        StringBuilder sb = new StringBuilder();
        for (int s : needs(f)) {
            if (FeaturePlan.optional(s) || Permissions.isOk(c, s)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Lang.tr(stepName(s)));
        }
        return Lang.tr("Needs") + ": " + sb;
    }

    // ------------------------------------------------------------------ what was chosen

    /** The features the person chose (a bit for each). Before this setup existed: alarms, when they are on. */
    static int chosen(Context c) {
        SharedPreferences p = Store.prefs(c);
        if (p.contains(PREF_CHOSEN)) return p.getInt(PREF_CHOSEN, 0);
        int m = 0;
        try {
            if (Store.load(c).alarmOn) m |= FeaturePlan.bit(FeaturePlan.F_ALARMS);
        } catch (RuntimeException ignored) {
            // nothing chosen
        }
        return m;
    }

    static void choose(Context c, int mask) {
        Store.prefs(c).edit().putInt(PREF_CHOSEN, chosen(c) | mask).apply();
    }

    /** The chosen features that still lack something they need. */
    static List<Integer> unfinished(Context c) {
        List<Integer> out = new ArrayList<>();
        int m = chosen(c);
        for (int f = 0; f < FeaturePlan.FEATURES; f++) {
            if (FeaturePlan.has(m, f) && !ready(c, f)) out.add(f);
        }
        return out;
    }

    // ------------------------------------------------------------------ the things it asks

    /** A few words naming what the phone is asked to allow. */
    static String stepName(int step) {
        switch (step) {
            case FeaturePlan.S_NOTIF: return "notifications";
            case FeaturePlan.S_EXACT: return "exact alarms";
            case FeaturePlan.S_FULLSCREEN: return "lock-screen alarm";
            case FeaturePlan.S_BATTERY: return "battery";
            case FeaturePlan.S_MAKER: return "background activity";
            case FeaturePlan.S_USAGE: return "usage access";
            case FeaturePlan.S_OVERLAY: return "display over apps";
            case FeaturePlan.S_DND: return "Do Not Disturb";
            case FeaturePlan.S_HOME: return "home app";
            case FeaturePlan.S_WALLPAPER: return "wallpaper";
            default: return "";
        }
    }

    /** The heading of the page that asks for it. */
    static String stepTitle(int step) {
        switch (step) {
            case FeaturePlan.S_NOTIF: return "Let alarms appear on your screen";
            case FeaturePlan.S_EXACT: return "Ring on the exact minute";
            case FeaturePlan.S_FULLSCREEN: return "Open the alarm over the lock screen";
            case FeaturePlan.S_BATTERY: return "Keep Dinacharya awake";
            case FeaturePlan.S_MAKER: return "Allow background activity";
            case FeaturePlan.S_USAGE: return "See your phone-use time";
            case FeaturePlan.S_OVERLAY: return "Hold locks in front of other apps";
            case FeaturePlan.S_DND: return "Let Dinacharya silence your phone";
            case FeaturePlan.S_HOME: return "Make this your home screen";
            case FeaturePlan.S_WALLPAPER: return "Put your day on the lock screen";
            default: return "";
        }
    }

    /** Why it is needed, and what happens without it, in plain words. */
    static String stepWhy(int step) {
        switch (step) {
            case FeaturePlan.S_NOTIF:
                return "Dinacharya shows each alarm as a notification, also over the lock screen. If you say no, nothing can ring.";
            case FeaturePlan.S_EXACT:
                return "Android lets an app ring on the minute only if you allow it. Without it, an alarm can come minutes late.";
            case FeaturePlan.S_FULLSCREEN:
                return "When your phone is locked, the question opens by itself, so you do not have to unlock first. Skip it and you get a notification instead.";
            case FeaturePlan.S_BATTERY:
                return "Some phones put apps to sleep to save battery, and a sleeping app misses its alarm. Set its battery use to Unrestricted.";
            case FeaturePlan.S_MAKER:
                return "Your phone’s maker has its own battery rules that can close apps. Allow Dinacharya to keep running in the background.";
            case FeaturePlan.S_USAGE:
                return "Android shares which app is open, and for how long, only if you allow usage access. It is read on this phone only and never sent anywhere.";
            case FeaturePlan.S_OVERLAY:
                return "So a lock still holds when an app is opened from Recents or a notification, Dinacharya shows its lock screen in front of that app.";
            case FeaturePlan.S_DND:
                return "Do Not Disturb access lets quiet hours truly silence the phone. Without it, the phone can only be set to vibrate.";
            case FeaturePlan.S_HOME:
                return "Pressing Home then shows your day. You can go back to your old home screen in Android’s settings at any time.";
            case FeaturePlan.S_WALLPAPER:
                return "Shows your day as the wallpaper, on the lock screen and in recent apps. Android asks you to confirm once.";
            default: return "";
        }
    }

    /** The main button of the page that asks for it. */
    static String stepAction(int step) {
        switch (step) {
            case FeaturePlan.S_NOTIF: return "Allow notifications";
            case FeaturePlan.S_BATTERY: return "Show me how";
            case FeaturePlan.S_USAGE: return "Continue";
            case FeaturePlan.S_HOME: return "Choose home app";
            case FeaturePlan.S_WALLPAPER: return "Set the wallpaper";
            default: return "Open the setting";
        }
    }

    /** The scene that shows what the step is for. */
    static int stepScene(int step) {
        switch (step) {
            case FeaturePlan.S_NOTIF: return FeatureScenes.NOTIF;
            case FeaturePlan.S_EXACT: return FeatureScenes.EXACT;
            case FeaturePlan.S_FULLSCREEN: return FeatureScenes.LOCKSCREEN;
            case FeaturePlan.S_BATTERY:
            case FeaturePlan.S_MAKER: return FeatureScenes.BATTERY;
            case FeaturePlan.S_USAGE: return FeatureScenes.USAGE;
            case FeaturePlan.S_OVERLAY: return FeatureScenes.FOCUS;
            case FeaturePlan.S_DND: return FeatureScenes.QUIET;
            case FeaturePlan.S_HOME:
            case FeaturePlan.S_WALLPAPER: return FeatureScenes.HOME;
            default: return FeatureScenes.DONE;
        }
    }

    /** A small second button for a step ("Greyed out?"), or null. */
    static String stepExtra(Context c, int step) {
        if (step == FeaturePlan.S_USAGE && RestrictedSettings.likely(c)) return "Greyed out or “Restricted setting”?";
        if (step == FeaturePlan.S_BATTERY) return "Open Android’s battery list";
        return null;
    }

    /** The accent a step page is drawn in. */
    static int stepAccent(int step) {
        return FeatureScenes.accent(stepScene(step));
    }
}
