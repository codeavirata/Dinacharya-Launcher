package app.dinacharya.wallpaper;

import java.util.Arrays;
import java.util.List;

/** The feature chooser: what each feature asks of the phone, once, in order, and nothing for a feature that was not chosen. */
public class FeaturePlanTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static List<Integer> l(int... a) { Integer[] b = new Integer[a.length]; for (int i = 0; i < a.length; i++) b[i] = a[i]; return Arrays.asList(b); }

    public static void main(String[] x) {
        // alarms: switch on first (nothing asked), then what the phone needs, by Android version
        ok("alarms on Android 14: all five asks in order",
                Arrays.equals(FeaturePlan.needs(FeaturePlan.F_ALARMS, 34, false),
                        new int[]{FeaturePlan.S_ALARMS_ON, FeaturePlan.S_NOTIF, FeaturePlan.S_EXACT, FeaturePlan.S_FULLSCREEN, FeaturePlan.S_BATTERY}));
        ok("alarms on Android 12: no lock-screen setting",
                Arrays.equals(FeaturePlan.needs(FeaturePlan.F_ALARMS, 31, false),
                        new int[]{FeaturePlan.S_ALARMS_ON, FeaturePlan.S_NOTIF, FeaturePlan.S_EXACT, FeaturePlan.S_BATTERY}));
        ok("alarms on Android 10: no exact-alarm or lock-screen setting",
                Arrays.equals(FeaturePlan.needs(FeaturePlan.F_ALARMS, 29, false),
                        new int[]{FeaturePlan.S_ALARMS_ON, FeaturePlan.S_NOTIF, FeaturePlan.S_BATTERY}));
        ok("a phone maker with its own limits adds one more ask, last",
                Arrays.equals(FeaturePlan.needs(FeaturePlan.F_ALARMS, 34, true),
                        new int[]{FeaturePlan.S_ALARMS_ON, FeaturePlan.S_NOTIF, FeaturePlan.S_EXACT, FeaturePlan.S_FULLSCREEN, FeaturePlan.S_BATTERY, FeaturePlan.S_MAKER}));
        ok("usage: only usage access", Arrays.equals(FeaturePlan.needs(FeaturePlan.F_USAGE, 34, false), new int[]{FeaturePlan.S_USAGE}));
        ok("focus: the home app (pauses and locks hold from the home screen), usage access, then display over apps",
                Arrays.equals(FeaturePlan.needs(FeaturePlan.F_FOCUS, 34, false), new int[]{FeaturePlan.S_HOME, FeaturePlan.S_USAGE, FeaturePlan.S_OVERLAY}));
        ok("home: the home app, then the wallpaper",
                Arrays.equals(FeaturePlan.needs(FeaturePlan.F_HOME, 34, false), new int[]{FeaturePlan.S_HOME, FeaturePlan.S_WALLPAPER}));
        ok("quiet: Do Not Disturb access", Arrays.equals(FeaturePlan.needs(FeaturePlan.F_QUIET, 34, false), new int[]{FeaturePlan.S_DND}));
        ok("an unknown feature asks for nothing", FeaturePlan.needs(99, 34, true).length == 0);

        // nothing chosen, nothing asked
        ok("no feature chosen: nothing is asked", FeaturePlan.queue(new int[0], 34, true).isEmpty());
        ok("only usage chosen: no notification, battery or home question",
                FeaturePlan.queue(new int[]{FeaturePlan.F_USAGE}, 34, true).equals(l(FeaturePlan.S_USAGE)));
        // two features that share a permission ask for it once
        List<Integer> both = FeaturePlan.queue(new int[]{FeaturePlan.F_USAGE, FeaturePlan.F_FOCUS}, 34, false);
        ok("usage and focus share usage access: asked once", both.equals(l(FeaturePlan.S_USAGE, FeaturePlan.S_HOME, FeaturePlan.S_OVERLAY)));
        List<Integer> homeFocus = FeaturePlan.queue(new int[]{FeaturePlan.F_FOCUS, FeaturePlan.F_HOME}, 34, false);
        ok("focus and home share the home app: asked once", homeFocus.equals(l(FeaturePlan.S_HOME, FeaturePlan.S_USAGE, FeaturePlan.S_OVERLAY, FeaturePlan.S_WALLPAPER)));
        List<Integer> order = FeaturePlan.queue(new int[]{FeaturePlan.F_QUIET, FeaturePlan.F_ALARMS}, 29, false);
        ok("features keep the order they were chosen in",
                order.equals(l(FeaturePlan.S_DND, FeaturePlan.S_ALARMS_ON, FeaturePlan.S_NOTIF, FeaturePlan.S_BATTERY)));
        // every step is unique in a queue of all features
        List<Integer> all = FeaturePlan.queue(new int[]{0, 1, 2, 3, 4}, 34, true);
        ok("all features: each step once", all.size() == new java.util.HashSet<>(all).size());

        ok("only switching alarms on is automatic", FeaturePlan.automatic(FeaturePlan.S_ALARMS_ON) && !FeaturePlan.automatic(FeaturePlan.S_NOTIF));
        ok("the lock-screen alarm, battery, maker, overlay and wallpaper are optional",
                FeaturePlan.optional(FeaturePlan.S_FULLSCREEN) && FeaturePlan.optional(FeaturePlan.S_BATTERY) && FeaturePlan.optional(FeaturePlan.S_MAKER)
                        && FeaturePlan.optional(FeaturePlan.S_OVERLAY) && FeaturePlan.optional(FeaturePlan.S_WALLPAPER));
        ok("notifications, exact alarms, usage, home app and Do Not Disturb are not optional",
                !FeaturePlan.optional(FeaturePlan.S_NOTIF) && !FeaturePlan.optional(FeaturePlan.S_EXACT) && !FeaturePlan.optional(FeaturePlan.S_USAGE)
                        && !FeaturePlan.optional(FeaturePlan.S_HOME) && !FeaturePlan.optional(FeaturePlan.S_DND));

        int m = FeaturePlan.mask(new int[]{FeaturePlan.F_ALARMS, FeaturePlan.F_QUIET});
        ok("the chosen features are kept as a mask", FeaturePlan.has(m, FeaturePlan.F_ALARMS) && FeaturePlan.has(m, FeaturePlan.F_QUIET)
                && !FeaturePlan.has(m, FeaturePlan.F_USAGE) && !FeaturePlan.has(m, FeaturePlan.F_HOME));
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
