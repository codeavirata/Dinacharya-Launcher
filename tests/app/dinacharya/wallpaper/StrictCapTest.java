package app.dinacharya.wallpaper;

import java.time.LocalDate;

/** A strict lock must end: at most 12 hours in a row, at least 2 free hours a day, and no library mode. */
public class StrictCapTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static final LocalDate DAY = LocalDate.of(2026, 11, 2);   // a Monday

    static LauncherConfig.Group window(int startH, int endH, int days, boolean strict) {
        LauncherConfig.Group g = new LauncherConfig().addGroup("t");
        g.startMin = startH * 60;
        g.endMin = endH * 60;
        g.days = days;
        g.strict = strict;
        return g;
    }

    static String p(LauncherConfig.Group g) { return LockRules.strictProblem(g, null, DAY); }

    public static void main(String[] args) {
        ok("a night window of 9 hours is fine", p(window(21, 6, LockRules.EVERY_DAY, true)) == null);
        ok("exactly 12 hours in a row is fine", p(window(8, 20, LockRules.EVERY_DAY, true)) == null);
        ok("13 hours in a row is refused", p(window(8, 21, LockRules.EVERY_DAY, true)) != null);
        ok("14 hours (9 to 11 at night) is refused", p(window(9, 23, LockRules.EVERY_DAY, true)) != null);
        ok("a window with the same start and end is 24 hours: refused", p(window(6, 6, LockRules.EVERY_DAY, true)) != null);
        ok("a window that runs into the next day and back to back counts as one run (22:00-12:00 is 14 hours)",
                p(window(22, 12, LockRules.EVERY_DAY, true)) != null);
        ok("a lock that is not strict is never refused here", p(window(0, 0, LockRules.EVERY_DAY, false)) == null && p(null) == null);
        ok("no days and no types: nothing to hold, so nothing refused", p(window(8, 21, 0, true)) == null);
        ok("one day only, 12 hours: fine", p(window(8, 20, 1, true)) == null);
        ok("a long lock on one day only is still refused (14 hours on Monday)", p(window(8, 22, 1, true)) != null);

        // two back-to-back days: 12:00-24:00 Monday and 00:00... are merged when the window runs over midnight
        LauncherConfig.Group g = window(12, 6, LockRules.EVERY_DAY, true);   // 18 hours, over midnight
        ok("18 hours over midnight is refused", p(g) != null);

        // free time each day: 11h59 on, 1 minute off is not enough free time even though each run is under 12 hours
        LauncherConfig.Group a = window(0, 12, LockRules.EVERY_DAY, true);
        ok("12 hours from midnight every day: the run is 12 hours and there are 12 free: fine", p(a) == null);

        // library mode
        LauncherConfig.Group lib = window(21, 6, LockRules.EVERY_DAY, true);
        lib.library = true;
        ok("strict with library mode is refused (it can have no end)", p(lib) != null && p(lib).contains("Library"));
        lib.strict = false;
        ok("library mode without strict is fine", p(lib) == null);

        // activity types, with the sample routine
        Routine sample = Routine.sample();
        LauncherConfig.Group all = new LauncherConfig().addGroup("t");
        all.strict = true;
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) all.during.add(LauncherConfig.anyKey(k));
        Routine.Schedule s = sample.compute();
        ok("every kind of activity strict-locked covers the day: refused (planned day is " + s.total + " min)",
                s.total <= 12 * 60 || LockRules.strictProblem(all, sample, DAY) != null);
        LauncherConfig.Group none = new LauncherConfig().addGroup("t");
        none.strict = true;
        none.during.add(LauncherConfig.anyKey(Routine.SLEEP));
        int sleepMins = 0;
        for (Routine.Slot sl : s.slots) if (sl.block.kind == Routine.SLEEP) sleepMins += sl.end - sl.start;
        ok("Any sleep (" + sleepMins + " min) is judged by its real length", (sleepMins > 12 * 60) == (LockRules.strictProblem(none, sample, DAY) != null));
        ok("activity types without a routine are not checked", LockRules.strictProblem(none, null, DAY) == null);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
