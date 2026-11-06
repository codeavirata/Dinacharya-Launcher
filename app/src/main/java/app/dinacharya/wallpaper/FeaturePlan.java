package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * What each feature of the app needs from the phone, and in what order it is asked for. The setup starts by asking which features the
 * person wants, and asks for a permission only when a chosen feature needs it. Uses no Android classes, so the rules are tested on their own.
 */
public final class FeaturePlan {
    /** The features a person can choose. */
    public static final int F_ALARMS = 0, F_USAGE = 1, F_FOCUS = 2, F_HOME = 3, F_QUIET = 4;
    public static final int FEATURES = 5;

    /** The things the phone is asked to allow or switch on. */
    public static final int S_NOTIF = 0, S_ALARMS_ON = 1, S_EXACT = 2, S_FULLSCREEN = 3, S_USAGE = 4, S_BATTERY = 5, S_MAKER = 6,
            S_HOME = 7, S_WALLPAPER = 8, S_OVERLAY = 9, S_DND = 10;
    public static final int STEPS = 11;

    private FeaturePlan() {
    }

    /**
     * What a feature needs on this phone, in the order it is asked for.
     *
     * @param sdk        the Android version of the phone (exact alarms came with 12, the lock-screen alarm setting with 14)
     * @param makerKnown the phone's maker is one that adds its own limits on apps running in the background
     */
    public static int[] needs(int feature, int sdk, boolean makerKnown) {
        List<Integer> out = new ArrayList<>();
        switch (feature) {
            case F_ALARMS:
                out.add(S_ALARMS_ON);
                out.add(S_NOTIF);
                if (sdk >= 31) out.add(S_EXACT);
                if (sdk >= 34) out.add(S_FULLSCREEN);
                out.add(S_BATTERY);
                if (makerKnown) out.add(S_MAKER);
                break;
            case F_USAGE:
                out.add(S_USAGE);
                break;
            case F_FOCUS:
                out.add(S_HOME);   // an ordinary pause or lock holds when an app is opened from this home screen
                out.add(S_USAGE);
                out.add(S_OVERLAY);
                break;
            case F_HOME:
                out.add(S_HOME);
                out.add(S_WALLPAPER);
                break;
            case F_QUIET:
                out.add(S_DND);
                break;
            default:
                break;
        }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** The steps of the chosen features, each once, in the order the features were chosen. */
    public static List<Integer> queue(int[] chosen, int sdk, boolean makerKnown) {
        List<Integer> out = new ArrayList<>();
        for (int f : chosen) {
            for (int s : needs(f, sdk, makerKnown)) {
                if (!out.contains(s)) out.add(s);
            }
        }
        return out;
    }

    /** A step that only switches something on inside the app: nothing is asked of the phone, so it has no page of its own. */
    public static boolean automatic(int step) {
        return step == S_ALARMS_ON;
    }

    /**
     * A step that makes a feature better but is not needed for it to work. A feature counts as set up without these, so one skipped
     * never leaves a reminder behind.
     */
    public static boolean optional(int step) {
        return step == S_FULLSCREEN || step == S_BATTERY || step == S_MAKER || step == S_OVERLAY || step == S_WALLPAPER;
    }

    public static int bit(int feature) {
        return 1 << feature;
    }

    public static boolean has(int mask, int feature) {
        return (mask & bit(feature)) != 0;
    }

    public static int mask(int[] features) {
        int m = 0;
        for (int f : features) m |= bit(f);
        return m;
    }
}
