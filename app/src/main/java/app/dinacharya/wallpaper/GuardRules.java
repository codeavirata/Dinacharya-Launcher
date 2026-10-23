package app.dinacharya.wallpaper;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules of the strong lock: what to do when an app comes to the front of the screen, wherever it was opened from
 * (the Recents list, a notification, a shortcut, another home app). They use the same lock and pause rules as the home
 * screen ({@link LockRules}, {@link FocusRules}), looked up by the app's package, so the two always agree. Uses no
 * Android classes; the service that watches the screen is {@code GuardService}.
 *
 * What it never does: block the phone, settings and system screens, the keyboard, a home app or Dinacharya itself
 * (see {@code GuardService} for the list), and it never blocks anything when nothing is locked or paused.
 */
public final class GuardRules {
    /** What to do about the app in front. */
    public static final int NONE = 0, LOCK = 1, PAUSE = 2;
    /** How long an app that was let through stays let through after it leaves the front (a quick glance elsewhere). */
    public static final long GRACE_MS = 30000L;
    /** The state of the strong lock as the settings show it. */
    public static final int OFF = 0, NEEDS_USAGE = 1, NEEDS_OVERLAY = 2, NEEDS_BOTH = 3, ON = 4;

    /** The answer for one app in front. */
    public static final class Verdict {
        public final int type;
        /** The app (as the lock or pause rule names it) the answer is about; empty for {@link #NONE}. */
        public final String key;
        /** The lock that holds the app, for {@link #LOCK}. */
        public final LockRules.Hit lock;
        /** The pause to show, for {@link #PAUSE}. */
        public final FocusRules.Decision pause;

        Verdict(int type, String key, LockRules.Hit lock, FocusRules.Decision pause) {
            this.type = type;
            this.key = key;
            this.lock = lock;
            this.pause = pause;
        }
    }

    public static final Verdict NO = new Verdict(NONE, "", null, null);

    private GuardRules() {
    }

    // ------------------------------------------------------------------ what is let through

    /**
     * Remembers which apps the person has already got past (a pause waited out, a lock broken), so they are not stopped again
     * while they are using the app, or for a short while after they step away from it.
     */
    public static final class Tracker {
        private final Map<String, Long> passed = new HashMap<>();
        private String front = "";

        /** The person got past the lock or pause for {@code pkg}. */
        public synchronized void passed(String pkg, long now) {
            if (pkg != null && !pkg.isEmpty()) passed.put(pkg, now);
        }

        /** True while {@code pkg} was let through and has not been away for longer than {@link #GRACE_MS}. */
        public synchronized boolean isPassed(String pkg, long now) {
            Long t = passed.get(pkg);
            return t != null && now - t <= GRACE_MS;
        }

        /**
         * Records what is in front now. Returns true when {@code pkg} has just come to the front (it was not the last
         * one in front); an app that was let through and is still in front keeps being let through.
         */
        public synchronized boolean enter(String pkg, long now) {
            String p = pkg == null ? "" : pkg;
            boolean entered = !p.equals(front);
            front = p;
            if (passed.containsKey(p) && now - passed.get(p) <= GRACE_MS) passed.put(p, now);   // still in use
            java.util.Iterator<Map.Entry<String, Long>> it = passed.entrySet().iterator();
            while (it.hasNext()) if (now - it.next().getValue() > GRACE_MS) it.remove();
            return entered;
        }

        /** The screen went off or was locked: what was in front is not known any more. */
        public synchronized void forgetFront() {
            front = "";
        }

        public synchronized void clear() {
            passed.clear();
            front = "";
        }
    }

    // ------------------------------------------------------------------ looking an app up

    /** Every lock or pause key in {@code c} that belongs to the package (an app can be listed more than once). */
    public static List<String> keysOf(LauncherConfig c, String pkg) {
        List<String> out = new ArrayList<>();
        if (pkg == null || pkg.isEmpty()) return out;
        for (LauncherConfig.Group g : c.groups) {
            for (String k : g.apps) if (pkg.equals(AppKey.pkg(k)) && !out.contains(k)) out.add(k);
        }
        for (LauncherConfig.Rule r : c.rules) if (pkg.equals(AppKey.pkg(r.key)) && !out.contains(r.key)) out.add(r.key);
        return out;
    }

    /** True when there is anything at all to watch for: a group with apps, or a pause rule. */
    public static boolean worthWatching(LauncherConfig c) {
        if (!c.rules.isEmpty()) return true;
        for (LauncherConfig.Group g : c.groups) if (!g.apps.isEmpty()) return true;
        return false;
    }

    /** True when a group is locked by activity type, so the slot has to be known at every look and not only when an app comes to the front. */
    public static boolean anyDuring(LauncherConfig c) {
        for (LauncherConfig.Group g : c.groups) if (!g.during.isEmpty() && !g.apps.isEmpty()) return true;
        return false;
    }

    /** True when this app's daily limit has to be read (a rule with a limit, and daily limits are open). */
    public static boolean needsUsage(LauncherConfig c, String pkg, int open) {
        if (!DayPass.has(open, DayPass.PAUSE_LIMIT)) return false;
        for (String k : keysOf(c, pkg)) {
            LauncherConfig.Rule r = c.ruleFor(k);
            if (r != null && r.limitMin > 0) return true;
        }
        return false;
    }

    public static boolean isSafe(String pkg, Set<String> safe) {
        return pkg == null || pkg.isEmpty() || (safe != null && safe.contains(pkg));
    }

    // ------------------------------------------------------------------ the decision

    /**
     * What to do about {@code pkg} now in front.
     *
     * @param entered     true when it has just come to the front (a pause is asked for then, not while the app is being used)
     * @param open        what is open ({@link Pro#open}); the free limits and passes apply exactly as on the home screen
     * @param kind        the kind of slot running now, or -1
     * @param usedTodayMs how long the app has been used today (only read when a daily limit is open and set)
     * @param tracker     who was already let through; may be null
     */
    public static Verdict decide(LauncherConfig c, String pkg, boolean entered, int open, int kind, long usedTodayMs,
                                 long nowMs, ZoneId zone, Tracker tracker) {
        return decide(c, pkg, entered, open, kind, usedTodayMs, nowMs, zone, tracker, LockRules.Slot.NONE);
    }

    /** Same, with the slot going on, for the locks that hold during an activity type. */
    public static Verdict decide(LauncherConfig c, String pkg, boolean entered, int open, int kind, long usedTodayMs,
                                 long nowMs, ZoneId zone, Tracker tracker, LockRules.Slot slot) {
        if (pkg == null || pkg.isEmpty()) return NO;
        List<String> keys = keysOf(c, pkg);
        if (keys.isEmpty()) return NO;
        boolean passed = tracker != null && tracker.isPassed(pkg, nowMs);

        // A lock holds the app for as long as it lasts. One that was broken stays broken while the app is in use, a strict one never is.
        LockRules.Hit best = null;
        String bestKey = "";
        for (String k : keys) {
            LockRules.Hit h = LockRules.check(c, k, open, nowMs, zone, slot);
            if (h == null) continue;
            if (best == null || h.untilMs > best.untilMs || (h.untilMs == best.untilMs && h.strict && !best.strict)) {
                best = h;
                bestKey = k;
            }
        }
        if (best != null && (best.strict || !passed)) return new Verdict(LOCK, bestKey, best, null);

        if (!entered || passed) return NO;
        FocusRules.Decision worst = null;
        String worstKey = "";
        for (String k : keys) {
            if (c.ruleFor(k) == null) continue;
            FocusRules.Decision d = FocusRules.decide(c, k, open, kind, usedTodayMs);
            if (d.type == FocusRules.ALLOW) continue;
            if (worst == null || severity(d) > severity(worst) || (severity(d) == severity(worst) && d.waitSec > worst.waitSec)) {
                worst = d;
                worstKey = k;
            }
        }
        return worst == null ? NO : new Verdict(PAUSE, worstKey, null, worst);
    }

    private static int severity(FocusRules.Decision d) {
        return d.heavy() ? 2 : 1;
    }

    // ------------------------------------------------------------------ the settings and the loop

    /** The state of the strong lock as the settings show it. */
    public static int state(boolean enabled, boolean usageAccess, boolean overlayAllowed) {
        if (!enabled) return OFF;
        if (!usageAccess && !overlayAllowed) return NEEDS_BOTH;
        if (!usageAccess) return NEEDS_USAGE;
        if (!overlayAllowed) return NEEDS_OVERLAY;
        return ON;
    }

    /** How long to wait before looking at the screen again. */
    public static long pollMs(boolean worthWatching, boolean screenOn, boolean busy) {
        if (!screenOn) return 3000L;
        if (!worthWatching) return 2500L;
        return busy ? 300L : 400L;
    }

    /** The words of a pause, the same as on the home screen. */
    public static String pauseMessage(FocusRules.Decision dec, String slotTitle) {
        String t = slotTitle == null ? "" : slotTitle;
        if (dec.type == FocusRules.LIMIT) {
            return "You have used it for " + Routine.formatDuration(Math.max(1, dec.usedMin)) + " today.\nYour limit is "
                    + Routine.formatDuration(dec.limitMin) + ".";
        }
        if (dec.type == FocusRules.STRICT) {
            return t.isEmpty() ? "This is a focus time." : "Right now: " + t + ".\nThis is a focus time.";
        }
        if (!t.isEmpty()) return "Right now: " + t + ".\nIs this the best use of the next few minutes?";
        return "Take a breath.\nIs this what you meant to open?";
    }

    /** The package names that are never blocked, whatever the person locks (so the phone can always call, and Settings can always undo this). */
    public static Set<String> alwaysSafe() {
        Set<String> s = new HashSet<>();
        for (String p : new String[]{
                "android", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom",
                "com.android.emergency", "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
                "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.android.packageinstaller",
                "com.google.android.packageinstaller", "com.google.android.gms",
                "com.android.inputmethod.latin", "com.google.android.inputmethod.latin"}) {
            s.add(p);
        }
        return s;
    }
}
