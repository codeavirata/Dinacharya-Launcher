package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * The choice the group editor offers (lock by time, by activity, or both): which way a saved group is, what is stored for each way, what is
 * missing before it can be saved, and that each way really locks and opens at the right moments.
 */
public class LockModeTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int h, int m) { return ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static String k(String pkg) { return AppKey.make(pkg, pkg + ".Main", 0); }

    /** What the editor stores for a way, the same way its Save does. */
    static void save(LauncherConfig.Group g, int by, int days, List<String> types) {
        g.days = LockMode.daysToSave(by, days);
        g.during.clear();
        if (LockMode.keepsTypes(by)) g.during.addAll(types);
        g.both = LockMode.bothToSave(by);
    }

    public static void main(String[] args) {
        // ---- which way a saved group is
        ok("no types: by time, whatever the rest says", LockMode.of(LockRules.EVERY_DAY, 0, false) == LockMode.TIME && LockMode.of(0, 0, false) == LockMode.TIME
                && LockMode.of(LockRules.WEEKDAYS, 0, true) == LockMode.TIME);
        ok("types and no days: by activity", LockMode.of(0, 2, false) == LockMode.ACTIVITY && LockMode.of(0, 1, true) == LockMode.ACTIVITY);
        ok("types, days and the both flag: time + activity", LockMode.of(LockRules.WEEKDAYS, 1, true) == LockMode.BOTH);
        ok("types and days without the flag: a lock made before the choice (either one)", LockMode.of(LockRules.EVERY_DAY, 3, false) == LockMode.EITHER);

        // ---- what is stored
        ok("an activity lock has no days; the others keep theirs", LockMode.daysToSave(LockMode.ACTIVITY, 127) == 0 && LockMode.daysToSave(LockMode.TIME, 31) == 31
                && LockMode.daysToSave(LockMode.BOTH, 96) == 96 && LockMode.daysToSave(LockMode.EITHER, 5) == 5);
        ok("a lock by time keeps no types", !LockMode.keepsTypes(LockMode.TIME) && LockMode.keepsTypes(LockMode.ACTIVITY) && LockMode.keepsTypes(LockMode.BOTH)
                && LockMode.keepsTypes(LockMode.EITHER));
        ok("only time + activity sets the both flag", LockMode.bothToSave(LockMode.BOTH) && !LockMode.bothToSave(LockMode.TIME)
                && !LockMode.bothToSave(LockMode.ACTIVITY) && !LockMode.bothToSave(LockMode.EITHER));

        // ---- what is missing before it can be saved
        ok("by time needs nothing (no days is a lock by hand only)", LockMode.missing(LockMode.TIME, 0, 0) == LockMode.OK && LockMode.missing(LockMode.TIME, 127, 0) == LockMode.OK);
        ok("by activity needs a type", LockMode.missing(LockMode.ACTIVITY, 0, 0) == LockMode.NEED_TYPES && LockMode.missing(LockMode.ACTIVITY, 127, 2) == LockMode.OK);
        ok("time + activity needs a type and a day", LockMode.missing(LockMode.BOTH, 127, 0) == LockMode.NEED_TYPES
                && LockMode.missing(LockMode.BOTH, 0, 1) == LockMode.NEED_DAYS && LockMode.missing(LockMode.BOTH, 31, 1) == LockMode.OK);
        ok("time or activity needs a type", LockMode.missing(LockMode.EITHER, 31, 0) == LockMode.NEED_TYPES && LockMode.missing(LockMode.EITHER, 31, 1) == LockMode.OK);

        // ---- the words
        boolean words = true;
        for (int m = 0; m <= 3; m++) words &= !LockMode.name(m).isEmpty() && !LockMode.about(m).isEmpty();
        ok("every way has a name and a line", words);
        ok("the names are different", new HashSet<>(Arrays.asList(LockMode.name(0), LockMode.name(1), LockMode.name(2), LockMode.name(3))).size() == 4);

        // ---- each way, saved as the editor saves it, locks when it should
        LauncherConfig c = new LauncherConfig();
        String ig = k("com.insta");
        LauncherConfig.Group g = c.addGroup("Social");
        c.setInGroup(g, ig, true);
        g.startMin = 21 * 60;      // 21:00 ...
        g.endMin = 6 * 60;         // ... to 06:00
        List<String> study = Collections.singletonList(LauncherConfig.typeKey(Routine.TASK, "Study"));
        LockRules.Slot studying = new LockRules.Slot(Routine.TASK, "Study", at(23, 59));
        LockRules.Slot resting = new LockRules.Slot(Routine.REST, "Nap", at(23, 59));
        long night = at(22, 0), noon = at(12, 0);

        save(g, LockMode.TIME, LockRules.EVERY_DAY, study);   // the types of an earlier choice are dropped
        ok("by time: no types are kept", g.during.isEmpty() && !g.both && g.days == LockRules.EVERY_DAY);
        ok("by time: locked at night whatever the slot", LockRules.check(c, ig, DayPass.ALL, night, Z, resting) != null && LockRules.check(c, ig, DayPass.ALL, night, Z, studying) != null);
        ok("by time: open at noon even in a Study slot", LockRules.check(c, ig, DayPass.ALL, noon, Z, studying) == null);

        save(g, LockMode.ACTIVITY, LockRules.EVERY_DAY, study);
        ok("by activity: no days, the types kept, not both", g.days == 0 && g.during.size() == 1 && !g.both);
        ok("by activity: locked in a Study slot at noon", LockRules.check(c, ig, DayPass.ALL, noon, Z, studying) != null);
        ok("by activity: open in another kind of slot", LockRules.check(c, ig, DayPass.ALL, noon, Z, resting) == null);
        ok("by activity: the night hours do not matter", LockRules.check(c, ig, DayPass.ALL, night, Z, LockRules.Slot.NONE) == null
                && LockRules.check(c, ig, DayPass.ALL, night, Z, resting) == null);
        ok("a saved activity lock reads back as one", LockMode.of(g.days, g.during.size(), g.both) == LockMode.ACTIVITY);

        save(g, LockMode.BOTH, LockRules.EVERY_DAY, study);
        ok("time + activity: days, types and the flag all kept", g.days == LockRules.EVERY_DAY && g.during.size() == 1 && g.both);
        ok("time + activity: locked at night in a Study slot", LockRules.check(c, ig, DayPass.ALL, night, Z, studying) != null);
        ok("time + activity: open at night in another slot", LockRules.check(c, ig, DayPass.ALL, night, Z, resting) == null);
        ok("time + activity: open at noon even in a Study slot", LockRules.check(c, ig, DayPass.ALL, noon, Z, studying) == null);
        ok("a saved time + activity lock reads back as one", LockMode.of(g.days, g.during.size(), g.both) == LockMode.BOTH);

        save(g, LockMode.EITHER, LockRules.EVERY_DAY, study);
        ok("time or activity: locked at night in any slot", LockRules.check(c, ig, DayPass.ALL, night, Z, resting) != null);
        ok("time or activity: locked at noon in a Study slot", LockRules.check(c, ig, DayPass.ALL, noon, Z, studying) != null);
        ok("time or activity: open at noon in another slot", LockRules.check(c, ig, DayPass.ALL, noon, Z, resting) == null);
        ok("a lock of that kind that is opened and saved again does not change", LockMode.of(g.days, g.during.size(), g.both) == LockMode.EITHER);

        // ---- switching from one way to another and back keeps what the person set
        int days = LockRules.WEEKDAYS;
        save(g, LockMode.TIME, days, study);
        ok("the days of a lock by time survive the save", g.days == days);
        // an activity lock opened in the editor offers every day if the person switches to a lock by time
        save(g, LockMode.ACTIVITY, days, study);
        int shown = LockMode.of(g.days, g.during.size(), g.both) == LockMode.ACTIVITY ? LockRules.EVERY_DAY : g.days;
        ok("an activity lock opens with every day ready for a switch to time", shown == LockRules.EVERY_DAY);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
