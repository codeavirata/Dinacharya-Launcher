package app.dinacharya.wallpaper;

/**
 * Decides what happens when an app is opened from the home screen: straight in, or after a pause.
 * This is friction, not a lock: the person can always choose "Open anyway" once the wait is over.
 * Uses no Android classes.
 */
public final class FocusRules {
    public static final int ALLOW = 0, NUDGE = 1, STRICT = 2, LIMIT = 3;
    /** How many apps keep a rule in the free version (one); Pro has no limit and also unlocks Strict and daily limits. */
    public static final int FREE_APPS = 1;

    public static final class Decision {
        public final int type;
        public final int waitSec;
        public final int limitMin;
        public final int usedMin;
        /** The way it goes ({@link PauseChallenge}); a plain wait unless the rule asks for more and this is Pro. */
        public final int challenge;

        Decision(int type, int waitSec, int limitMin, int usedMin, int challenge) {
            this.type = type;
            this.waitSec = waitSec;
            this.limitMin = limitMin;
            this.usedMin = usedMin;
            this.challenge = challenge;
        }

        /** The long kinds of pause (strict, or a used-up daily limit) ask for more: more sums, more breaths. */
        public boolean heavy() {
            return type == STRICT || type == LIMIT;
        }
    }

    private static final Decision GO = new Decision(ALLOW, 0, 0, 0, PauseChallenge.WAIT);

    private FocusRules() {
    }

    /** Whether this app's rule is in force (in the free version only the first app's rule is). */
    public static boolean active(LauncherConfig c, String appKey, boolean pro) {
        return active(c, appKey, pro ? DayPass.ALL : DayPass.FREE);
    }

    /**
     * Same, for what is open ({@link DayPass#ALL} with Pro, or just the one item a video pass opened):
     * only {@link DayPass#PAUSE_APPS} lifts the limit on the number of apps.
     */
    public static boolean active(LauncherConfig c, String appKey, int open) {
        int idx = c.ruleIndex(appKey);
        return idx >= 0 && (DayPass.has(open, DayPass.PAUSE_APPS) || idx < FREE_APPS);
    }

    /**
     * @param kind        the kind of slot running now, or -1
     * @param usedTodayMs how long the app has been used today (only matters for a daily limit)
     */
    public static Decision decide(LauncherConfig c, String appKey, boolean pro, int kind, long usedTodayMs) {
        return decide(c, appKey, pro ? DayPass.ALL : DayPass.FREE, kind, usedTodayMs);
    }

    /**
     * Same, for what is open: {@link DayPass#PAUSE_STRICT} allows the strict pause, {@link DayPass#PAUSE_LIMIT} the daily
     * limit and {@link DayPass#PAUSE_PLAYFUL} the playful pauses. Each works on its own; none opens another.
     */
    public static Decision decide(LauncherConfig c, String appKey, int open, int kind, long usedTodayMs) {
        if (!active(c, appKey, open)) return GO;
        LauncherConfig.Rule r = c.ruleFor(appKey);
        if (r == null) return GO;
        int usedMin = (int) (usedTodayMs / 60000L);
        // The playful pauses are part of Pro; without them every pause is the plain wait.
        int ch = DayPass.has(open, DayPass.PAUSE_PLAYFUL) && PauseChallenge.valid(r.challenge) ? r.challenge : PauseChallenge.WAIT;
        if (DayPass.has(open, DayPass.PAUSE_LIMIT) && r.limitMin > 0 && usedTodayMs >= r.limitMin * 60000L) {
            return new Decision(LIMIT, c.strictSec, r.limitMin, usedMin, ch);
        }
        int mode = r.mode;
        if (!DayPass.has(open, DayPass.PAUSE_STRICT) && mode == LauncherConfig.RULE_STRICT) mode = LauncherConfig.RULE_NUDGE;
        if (mode == LauncherConfig.RULE_NONE) return GO;
        if (c.isFocusKind(kind)) {
            return mode == LauncherConfig.RULE_STRICT
                    ? new Decision(STRICT, c.strictSec, r.limitMin, usedMin, ch)
                    : new Decision(NUDGE, c.pauseSec, r.limitMin, usedMin, ch);
        }
        // Outside the focus slots only the short pause applies, and only if the rule says to pause at any time.
        return r.always ? new Decision(NUDGE, c.pauseSec, r.limitMin, usedMin, ch) : GO;
    }
}
