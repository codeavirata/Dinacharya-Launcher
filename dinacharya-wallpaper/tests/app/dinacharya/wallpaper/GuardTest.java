package app.dinacharya.wallpaper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/** The strong lock: what is held when an app comes to the front, from wherever it was opened. */
public class GuardTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static String k(String pkg) { return AppKey.make(pkg, pkg + ".Main", 0); }
    static final long MIN = 60000L;

    /** Locks "Night" (a, b, c) all day, every day, from the start. */
    static LauncherConfig locked(boolean strict) {
        LauncherConfig c = new LauncherConfig();
        LauncherConfig.Group g = c.addGroup("Night");
        for (String p : new String[]{"a", "b", "c"}) c.setInGroup(g, k(p), true);
        g.days = LockRules.EVERY_DAY; g.startMin = 0; g.endMin = 0; g.strict = strict;
        return c;
    }

    public static void main(String[] args) {
        long now = at(19, 10, 0);
        FrontTracker f = new FrontTracker();

        // ---------- which app is in front
        f.feed(FrontTracker.RESUMED, "app.a", "app.a.Main", now);
        ok("front: an app that came to the front", f.front().equals("app.a"));
        f.feed(FrontTracker.PAUSED, "app.a", "app.a.Main", now + 10);
        f.feed(FrontTracker.RESUMED, "app.b", "app.b.Main", now + 20);
        f.feed(FrontTracker.STOPPED, "app.a", "app.a.Main", now + 30);
        ok("front: moving to another app, the old one stopping afterwards changes nothing", f.front().equals("app.b"));
        f.feed(FrontTracker.PAUSED, "app.b", "app.b.One", now + 40);
        f.feed(FrontTracker.RESUMED, "app.b", "app.b.Two", now + 50);
        f.feed(FrontTracker.STOPPED, "app.b", "app.b.One", now + 60);
        ok("front: an app moving between its own screens stays in front", f.front().equals("app.b"));
        f.feed(FrontTracker.PAUSED, "app.b", "app.b.Two", now + 70);
        ok("front: an app going behind with nothing in front yet is not in front", f.front().isEmpty());
        f.feed(FrontTracker.RESUMED, "launcher", "launcher.Home", now + 80);
        ok("front: the home screen", f.front().equals("launcher"));
        f.feed(FrontTracker.SCREEN_OFF, null, null, now + 90);
        ok("front: nothing is in front when the screen goes off", f.front().isEmpty() && f.lastTs() == now + 90);
        f.feed(FrontTracker.RESUMED, null, null, now + 100);
        f.feed(99, "app.z", "app.z.Main", now + 110);
        ok("front: events without a package and unknown events are ignored", f.front().isEmpty());
        f.reset();
        ok("front: reset forgets everything", f.front().isEmpty() && f.lastTs() == 0);

        // ---------- looking an app up
        LauncherConfig c = locked(false);
        c.setRule(k("p"), LauncherConfig.RULE_NUDGE, 0, false);
        ok("keys: all keys of a package in locks and pauses", GuardRules.keysOf(c, "a").equals(java.util.Arrays.asList(k("a")))
                && GuardRules.keysOf(c, "p").equals(java.util.Arrays.asList(k("p"))) && GuardRules.keysOf(c, "zzz").isEmpty()
                && GuardRules.keysOf(c, "").isEmpty() && GuardRules.keysOf(c, null).isEmpty());
        LauncherConfig two = new LauncherConfig();
        LauncherConfig.Group g2 = two.addGroup("Two");
        two.setInGroup(g2, AppKey.make("com.x", "com.x.Main", 0), true);
        two.setInGroup(g2, AppKey.make("com.x", "com.x.Other", 0), true);
        ok("keys: an app listed twice gives both keys", GuardRules.keysOf(two, "com.x").size() == 2);
        ok("watching: nothing to watch when nothing is locked or paused", !GuardRules.worthWatching(new LauncherConfig())
                && GuardRules.worthWatching(c) && !GuardRules.worthWatching(emptyGroupOnly()));
        ok("safe: never block the phone, settings and system screens", GuardRules.isSafe("com.android.settings", GuardRules.alwaysSafe())
                && GuardRules.isSafe("com.android.systemui", GuardRules.alwaysSafe()) && GuardRules.isSafe("com.android.dialer", GuardRules.alwaysSafe())
                && GuardRules.isSafe("", GuardRules.alwaysSafe()) && GuardRules.isSafe(null, null) && !GuardRules.isSafe("com.instagram.android", GuardRules.alwaysSafe()));

        // ---------- a lock holds the app wherever it opens from
        GuardRules.Tracker t = new GuardRules.Tracker();
        GuardRules.Verdict v = GuardRules.decide(c, "a", true, DayPass.FREE, -1, 0, now, Z, t);
        ok("lock: an app in a locked group is held when it comes to the front", v.type == GuardRules.LOCK && v.lock != null && v.key.equals(k("a")));
        v = GuardRules.decide(c, "a", false, DayPass.FREE, -1, 0, now, Z, t);
        ok("lock: and it is held while it stays in front (a lock that starts while using the app)", v.type == GuardRules.LOCK);
        ok("lock: an app that is not in any group is left alone", GuardRules.decide(c, "q", true, DayPass.FREE, -1, 0, now, Z, t).type == GuardRules.NONE
                && GuardRules.decide(c, "", true, DayPass.FREE, -1, 0, now, Z, t).type == GuardRules.NONE);
        ok("lock: the free version holds the first two apps of a group only", GuardRules.decide(c, "b", true, DayPass.FREE, -1, 0, now, Z, t).type == GuardRules.LOCK
                && GuardRules.decide(c, "c", true, DayPass.FREE, -1, 0, now, Z, t).type == GuardRules.NONE
                && GuardRules.decide(c, "c", true, DayPass.bit(DayPass.LOCK_APPS), -1, 0, now, Z, t).type == GuardRules.LOCK
                && GuardRules.decide(c, "c", true, DayPass.ALL, -1, 0, now, Z, t).type == GuardRules.LOCK);
        LauncherConfig off = locked(false);
        off.groups.get(0).days = 0; off.groups.get(0).startMin = 0; off.groups.get(0).endMin = 0;
        ok("lock: a group with no time set holds nothing", GuardRules.decide(off, "a", true, DayPass.FREE, -1, 0, now, Z, t).type == GuardRules.NONE);
        LauncherConfig timed = locked(false);
        timed.groups.get(0).days = 0;
        timed.groups.get(0).untilMs = now + 2 * 3600000L;
        ok("lock: a lock started by hand holds until its time", GuardRules.decide(timed, "a", true, DayPass.FREE, -1, 0, now, Z, t).type == GuardRules.LOCK
                && GuardRules.decide(timed, "a", true, DayPass.FREE, -1, 0, now + 3 * 3600000L, Z, t).type == GuardRules.NONE);

        // ---------- breaking a lock, and strict ones
        GuardRules.Tracker t2 = new GuardRules.Tracker();
        t2.passed("a", now);
        ok("break: a lock that was broken lets the app through while it is in use", GuardRules.decide(c, "a", false, DayPass.FREE, -1, 0, now + 5000, Z, t2).type == GuardRules.NONE);
        ok("break: but not the other apps of the group", GuardRules.decide(c, "b", true, DayPass.FREE, -1, 0, now + 5000, Z, t2).type == GuardRules.LOCK);
        LauncherConfig strict = locked(true);
        GuardRules.Tracker t3 = new GuardRules.Tracker();
        t3.passed("a", now);
        ok("strict: a strict lock without strict locks open is only an ordinary lock", GuardRules.decide(strict, "a", true, DayPass.FREE, -1, 0, now, Z, null).lock != null
                && !GuardRules.decide(strict, "a", true, DayPass.FREE, -1, 0, now, Z, null).lock.strict);
        ok("strict: with strict locks open it is strict, and getting past is not remembered",
                GuardRules.decide(strict, "a", true, DayPass.bit(DayPass.LOCK_STRICT), -1, 0, now + 1000, Z, t3).type == GuardRules.LOCK
                        && GuardRules.decide(strict, "a", true, DayPass.bit(DayPass.LOCK_STRICT), -1, 0, now + 1000, Z, t3).lock.strict);

        // ---------- pauses
        LauncherConfig pc = new LauncherConfig();
        pc.setRule(k("p"), LauncherConfig.RULE_NUDGE, 0, true);
        pc.setRule(k("s"), LauncherConfig.RULE_STRICT, 0, false);
        pc.setFocusKind(Routine.TASK, true);
        GuardRules.Tracker tp = new GuardRules.Tracker();
        v = GuardRules.decide(pc, "p", true, DayPass.FREE, Routine.TASK, 0, now, Z, tp);
        ok("pause: an app with a pause rule is paused when it comes to the front", v.type == GuardRules.PAUSE && v.pause.type == FocusRules.NUDGE && v.key.equals(k("p")));
        ok("pause: not again while it stays in front", GuardRules.decide(pc, "p", false, DayPass.FREE, Routine.TASK, 0, now, Z, tp).type == GuardRules.NONE);
        ok("pause: a strict rule is only a short pause without strict pauses", GuardRules.decide(pc, "s", true, DayPass.FREE, Routine.TASK, 0, now, Z, tp).pause.type == FocusRules.NUDGE
                && GuardRules.decide(pc, "s", true, DayPass.bit(DayPass.PAUSE_STRICT), Routine.TASK, 0, now, Z, tp).pause.type == FocusRules.STRICT);
        ok("pause: outside a focus time only a rule that says 'at any time' pauses", GuardRules.decide(pc, "p", true, DayPass.FREE, -1, 0, now, Z, tp).type == GuardRules.PAUSE
                && GuardRules.decide(pc, "s", true, DayPass.FREE, -1, 0, now, Z, tp).type == GuardRules.NONE);
        ok("pause: the pause is shown for the right name and words", GuardRules.pauseMessage(GuardRules.decide(pc, "p", true, DayPass.FREE, Routine.TASK, 0, now, Z, tp).pause, "Study")
                .equals("Right now: Study.\nIs this the best use of the next few minutes?")
                && GuardRules.pauseMessage(GuardRules.decide(pc, "s", true, DayPass.bit(DayPass.PAUSE_STRICT), Routine.TASK, 0, now, Z, tp).pause, "").equals("This is a focus time.")
                && GuardRules.pauseMessage(GuardRules.decide(pc, "p", true, DayPass.FREE, Routine.TASK, 0, now, Z, tp).pause, null).startsWith("Take a breath"));
        LauncherConfig lim = new LauncherConfig();
        lim.setRule(k("l"), LauncherConfig.RULE_NUDGE, 30, true);
        ok("limit: the day's use is only read when a limit is open and set", GuardRules.needsUsage(lim, "l", DayPass.bit(DayPass.PAUSE_LIMIT))
                && !GuardRules.needsUsage(lim, "l", DayPass.FREE) && !GuardRules.needsUsage(pc, "p", DayPass.ALL) && !GuardRules.needsUsage(lim, "zz", DayPass.ALL));
        GuardRules.Verdict used = GuardRules.decide(lim, "l", true, DayPass.bit(DayPass.PAUSE_LIMIT), -1, 31 * MIN, now, Z, null);
        ok("limit: a used-up daily limit pauses for long", used.type == GuardRules.PAUSE && used.pause.type == FocusRules.LIMIT
                && GuardRules.pauseMessage(used.pause, "").contains("Your limit is"));
        ok("limit: not used up means a short pause (outside focus times too, the rule says 'at any time')", GuardRules.decide(lim, "l", true, DayPass.bit(DayPass.PAUSE_LIMIT), -1, 5 * MIN, now, Z, null).pause.type == FocusRules.NUDGE);
        LauncherConfig over = new LauncherConfig();
        for (char ch = 'a'; ch <= 'd'; ch++) over.setRule(k(String.valueOf(ch)), LauncherConfig.RULE_NUDGE, 0, true);
        ok("pause: the free version keeps the first two rules only", GuardRules.decide(over, "a", true, DayPass.FREE, -1, 0, now, Z, null).type == GuardRules.PAUSE
                && GuardRules.decide(over, "c", true, DayPass.FREE, -1, 0, now, Z, null).type == GuardRules.NONE
                && GuardRules.decide(over, "c", true, DayPass.bit(DayPass.PAUSE_APPS), -1, 0, now, Z, null).type == GuardRules.PAUSE);

        // ---------- passed, and for how long
        GuardRules.Tracker tr = new GuardRules.Tracker();
        tr.passed("p", now);
        ok("passed: let through right after", tr.isPassed("p", now) && !tr.isPassed("other", now));
        ok("passed: still let through a short while after leaving", tr.isPassed("p", now + GuardRules.GRACE_MS) && !tr.isPassed("p", now + GuardRules.GRACE_MS + 1));
        ok("passed: a pause that was waited out is not asked again while the app stays in front",
                GuardRules.decide(pc, "p", false, DayPass.FREE, Routine.TASK, 0, now, Z, tr).type == GuardRules.NONE);
        ok("passed: coming back within the grace time is not paused again", GuardRules.decide(pc, "p", true, DayPass.FREE, Routine.TASK, 0, now + 10000, Z, tr).type == GuardRules.NONE);
        ok("passed: coming back after the grace time is paused again", GuardRules.decide(pc, "p", true, DayPass.FREE, Routine.TASK, 0, now + 40000, Z, tr).type == GuardRules.PAUSE);
        GuardRules.Tracker te = new GuardRules.Tracker();
        ok("enter: the first app in front has just entered; the same one again has not", te.enter("p", now) && !te.enter("p", now + 400) && te.enter("q", now + 800) && te.enter("p", now + 1200));
        GuardRules.Tracker tu = new GuardRules.Tracker();
        tu.passed("p", now);
        tu.enter("p", now + 10000);
        tu.enter("p", now + 25000);
        tu.enter("p", now + 50000);
        ok("enter: an app that is kept in use stays let through for as long as it is in front", tu.isPassed("p", now + 50000) && !tu.isPassed("p", now + 90000));
        tu.enter("q", now + 51000);
        ok("enter: once away for longer than the grace time it is forgotten", !tu.isPassed("p", now + 100000));
        tu.passed("p", now + 100000);
        tu.forgetFront();
        ok("enter: after the screen went off the same app counts as just entered again", tu.enter("p", now + 101000));
        tu.clear();
        ok("clear: forgets who was let through", !tu.isPassed("p", now + 101000));

        // ---------- the state shown in settings, and the loop
        ok("state: off, or what is missing, or on", GuardRules.state(false, true, true) == GuardRules.OFF && GuardRules.state(true, false, true) == GuardRules.NEEDS_USAGE
                && GuardRules.state(true, true, false) == GuardRules.NEEDS_OVERLAY && GuardRules.state(true, false, false) == GuardRules.NEEDS_BOTH
                && GuardRules.state(true, true, true) == GuardRules.ON && GuardRules.state(false, false, false) == GuardRules.OFF);
        ok("loop: quick when there is something to watch, slow when there is not or the screen is off",
                GuardRules.pollMs(true, true, false) < GuardRules.pollMs(false, true, false) && GuardRules.pollMs(true, true, true) <= GuardRules.pollMs(true, true, false)
                        && GuardRules.pollMs(true, false, false) >= 2000 && GuardRules.pollMs(true, true, false) <= 500);
        ok("config: the strong lock is saved, off by default, and an old saved config loads with it off", !new LauncherConfig().strong
                && LauncherConfig.parse(withStrong(true).serialize()).strong && !LauncherConfig.parse(withStrong(false).serialize()).strong
                && !LauncherConfig.parse("DINALAUNCH1\nswipes=2\ntheme=1\n").strong);
        LauncherConfig round = LauncherConfig.parse(locked(false).serialize());
        ok("config: locks and pauses still save and load as before", round.groups.size() == 1 && round.groups.get(0).apps.size() == 3);

        // ---------- one look of the loop
        final int[] usedCalls = {0};
        GuardLoop.Env env = new GuardLoop.Env() {
            public int slotKind() { return Routine.TASK; }
            public long usedToday(String pkg) { usedCalls[0]++; return 31 * MIN; }
        };
        Set<String> safe = GuardRules.alwaysSafe();
        safe.add("home.app");
        String own = "app.dinacharya.wallpaper";
        LauncherConfig lc = locked(false);
        lc.setRule(k("p"), LauncherConfig.RULE_NUDGE, 0, true);
        lc.setFocusKind(Routine.TASK, true);
        GuardLoop lp = new GuardLoop();
        GuardRules.Tracker lt = new GuardRules.Tracker();
        long n = now;
        GuardLoop.Out o = lp.look(lc, "home.app", "", safe, own, DayPass.FREE, n, Z, lt, env);
        ok("loop: a home app in front: nothing to do", !o.present && !o.dropCover && o.nextMs <= 500);
        o = lp.look(lc, "a", "", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        ok("loop: a locked app comes to the front: cover it and bring up the lock screen", o.present && o.pkg.equals("a") && o.verdict.type == GuardRules.LOCK);
        o = lp.look(lc, "a", "", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        ok("loop: the lock screen is still coming up: not brought up twice", !o.present && !o.dropCover);
        o = lp.look(lc, "a", "", safe, own, DayPass.FREE, n += GuardLoop.PENDING_MS, Z, lt, env);
        ok("loop: it never came up and the app is still in front: try again", o.present && o.verdict.type == GuardRules.LOCK);
        o = lp.look(lc, "a", "a", safe, own, DayPass.FREE, n += GuardLoop.PENDING_MS + 1000, Z, lt, env);
        ok("loop: a cover already holds the app: leave it be, so a pause countdown on it is not restarted", !o.present && !o.dropCover);
        o = lp.look(lc, own, "a", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        ok("loop: the lock screen is in front: the cover comes down", !o.present && o.dropCover);
        o = lp.look(lc, "", "a", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        ok("loop: between two screens (nothing known in front) the cover stays", !o.present && !o.dropCover);
        o = lp.look(lc, "z", "a", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        ok("loop: another app that is not held comes to the front: the cover comes down", !o.present && o.dropCover);
        lp.look(lc, "home.app", "", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        lp.done();
        o = lp.look(lc, "a", "", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        ok("loop: after going back, opening the app again is held again straight away", o.present && o.verdict.type == GuardRules.LOCK);
        o = lp.look(lc, "b", "a", safe, own, DayPass.FREE, n += 400, Z, lt, env);
        ok("loop: another locked app replaces the cover of the first", o.present && o.pkg.equals("b"));

        GuardLoop lp2 = new GuardLoop();
        GuardRules.Tracker lt2 = new GuardRules.Tracker();
        long m = now;
        usedCalls[0] = 0;
        o = lp2.look(lc, "p", "", safe, own, DayPass.FREE, m, Z, lt2, env);
        ok("loop: an app with a pause is paused when it comes to the front", o.present && o.verdict.type == GuardRules.PAUSE);
        lt2.passed("p", m + 6000);
        lp2.done();
        o = lp2.look(lc, "p", "", safe, own, DayPass.FREE, m + 6400, Z, lt2, env);
        ok("loop: once the pause was waited out it is not asked again while in use", !o.present);
        lp2.look(lc, "home.app", "", safe, own, DayPass.FREE, m + 9000, Z, lt2, env);
        o = lp2.look(lc, "p", "", safe, own, DayPass.FREE, m + 12000, Z, lt2, env);
        ok("loop: a quick trip to the home screen and back is not paused again", !o.present);
        lp2.look(lc, "home.app", "", safe, own, DayPass.FREE, m + 60000, Z, lt2, env);
        o = lp2.look(lc, "p", "", safe, own, DayPass.FREE, m + 100000, Z, lt2, env);
        ok("loop: coming back much later is paused again", o.present && o.verdict.type == GuardRules.PAUSE);
        ok("loop: the day's use was not read for rules without a limit", usedCalls[0] == 0);

        LauncherConfig limc = new LauncherConfig();
        limc.setRule(k("l"), LauncherConfig.RULE_NUDGE, 30, true);
        GuardLoop lp3 = new GuardLoop();
        GuardRules.Tracker lt3 = new GuardRules.Tracker();
        o = lp3.look(limc, "l", "", safe, own, DayPass.bit(DayPass.PAUSE_LIMIT), now, Z, lt3, env);
        ok("loop: a used-up limit pauses, and the use was read once, only when the app came to the front",
                o.present && o.verdict.pause.type == FocusRules.LIMIT && usedCalls[0] == 1);
        lt3.passed("l", now + 1000);
        lp3.done();
        lp3.look(limc, "l", "", safe, own, DayPass.bit(DayPass.PAUSE_LIMIT), now + 1400, Z, lt3, env);
        lp3.look(limc, "l", "", safe, own, DayPass.bit(DayPass.PAUSE_LIMIT), now + 1800, Z, lt3, env);
        ok("loop: and not read again while the app stays in front", usedCalls[0] == 1);

        GuardLoop lp4 = new GuardLoop();
        usedCalls[0] = 0;
        o = lp4.look(new LauncherConfig(), "a", "", safe, own, DayPass.FREE, now, Z, new GuardRules.Tracker(), env);
        ok("loop: with nothing locked or paused nothing is done, and it looks slowly", !o.present && usedCalls[0] == 0 && o.nextMs >= 2000);
        o = lp4.look(lc, "com.android.settings", "", safe, own, DayPass.FREE, now, Z, new GuardRules.Tracker(), env);
        ok("loop: Settings is never held", !o.present);
        LauncherConfig settingsLocked = new LauncherConfig();
        LauncherConfig.Group sg = settingsLocked.addGroup("S");
        settingsLocked.setInGroup(sg, AppKey.make("com.android.settings", "x", 0), true);
        settingsLocked.setInGroup(sg, AppKey.make(own, own + ".Main", 0), true);
        sg.days = LockRules.EVERY_DAY; sg.startMin = 0; sg.endMin = 0;
        o = lp4.look(settingsLocked, "com.android.settings", "", safe, own, DayPass.FREE, now, Z, new GuardRules.Tracker(), env);
        ok("loop: even when somebody locks it", !o.present);
        o = lp4.look(settingsLocked, own, "", safe, own, DayPass.FREE, now, Z, new GuardRules.Tracker(), env);
        ok("loop: Dinacharya itself is never held", !o.present);
        GuardLoop lp5 = new GuardLoop();
        GuardRules.Tracker lt5 = new GuardRules.Tracker();
        lp5.look(lc, "a", "", safe, own, DayPass.bit(DayPass.LOCK_STRICT), now, Z, lt5, env);
        lp5.clear();
        lt5.passed("a", now);
        o = lp5.look(locked(true), "a", "", safe, own, DayPass.bit(DayPass.LOCK_STRICT), now + 500, Z, lt5, env);
        ok("loop: a strict lock holds the app even after it was 'passed'", o.present && o.verdict.lock.strict);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static LauncherConfig emptyGroupOnly() {
        LauncherConfig c = new LauncherConfig();
        c.addGroup("Empty");
        return c;
    }

    static LauncherConfig withStrong(boolean on) {
        LauncherConfig c = new LauncherConfig();
        c.strong = on;
        return c;
    }
}
