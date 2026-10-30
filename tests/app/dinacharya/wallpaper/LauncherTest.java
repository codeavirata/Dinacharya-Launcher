package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

public class LauncherTest {
    static int fails = 0;
    private static boolean notScheduled(LauncherConfig.Group g) {
        g.days = 0; g.untilMs = 0;
        return LockRules.status(g, at(19, 12, 0), Z, false).equals("Not scheduled");
    }

    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static final long MIN = 60000L;

    static String k(String pkg) { return AppKey.make(pkg, pkg + ".Main", 0); }
    /** A config with {@code n} paused apps, named a, b, c, ... (the third and later are over the free limit). */
    static LauncherConfig fcfg(int n) {
        LauncherConfig c = new LauncherConfig();
        for (int i = 0; i < n; i++) c.setRule(k(String.valueOf((char) ('a' + i))), LauncherConfig.RULE_NUDGE, 0, false);
        return c;
    }
    static AppEntry app(String label, String pkg) { return new AppEntry(k(pkg), label, false); }

    public static void main(String[] args) {
        // ---------- AppKey
        String key = AppKey.make("com.whatsapp", "com.whatsapp.Main", 10);
        ok("key parts", AppKey.pkg(key).equals("com.whatsapp") && AppKey.cls(key).equals("com.whatsapp.Main") && AppKey.user(key) == 10);
        ok("key with inner class", AppKey.cls(AppKey.make("a.b", "a.b.C$D", 0)).equals("a.b.C$D"));
        ok("dashboard is an action and valid", AppKey.isAction(AppKey.DASHBOARD) && AppKey.valid(AppKey.DASHBOARD) && AppKey.pkg(AppKey.DASHBOARD).isEmpty());
        ok("junk is not valid", !AppKey.valid("") && !AppKey.valid(null) && !AppKey.valid("act:other") && !AppKey.valid("nothing") && AppKey.valid(key));
        ok("tabs and @ cannot break a key", !AppKey.make("a\tb", "c@d", 1).contains("\t") && AppKey.user(AppKey.make("a", "c@d", 7)) == 7);

        // ---------- AppSearch
        List<AppEntry> all = new ArrayList<>(Arrays.asList(
                app("YouTube", "yt"), app("Calculator", "calc"), app("café maps", "cafe"), app("Clock", "clock"),
                app("Google Calendar", "gcal"), app("Calendar", "cal"), app("1Password", "pw"), app("Zoom", "zoom"), app("Instagram", "ig")));
        AppSearch.sort(all);
        StringBuilder order = new StringBuilder();
        for (AppEntry e : all) order.append(e.label).append("|");
        ok("sorted ignoring case and accents, digits first", order.toString().equals("1Password|café maps|Calculator|Calendar|Clock|Google Calendar|Instagram|YouTube|Zoom|"));
        ok("letters", AppSearch.letter("café") == 'C' && AppSearch.letter("1Password") == '#' && AppSearch.letter("") == '#' && AppSearch.letter("Zoom") == 'Z');
        List<AppEntry> f = AppSearch.filter(all, "cal");
        ok("search ranks prefix before word-start", f.size() == 3 && f.get(0).label.equals("Calculator") && f.get(1).label.equals("Calendar") && f.get(2).label.equals("Google Calendar"));
        ok("search finds accented names", AppSearch.filter(all, "cafe").size() == 1);
        ok("search matches inside names", AppSearch.filter(all, "tube").size() == 1);
        ok("empty search returns everything", AppSearch.filter(all, "  ").size() == all.size());
        ok("no match", AppSearch.filter(all, "qqq").isEmpty());
        ok("auto open on a single match", AppSearch.autoOpen(AppSearch.filter(all, "zoo"), "zoo", true).label.equals("Zoom"));
        ok("no auto open when off, empty, or several", AppSearch.autoOpen(AppSearch.filter(all, "zoo"), "zoo", false) == null
                && AppSearch.autoOpen(all, "", true) == null && AppSearch.autoOpen(AppSearch.filter(all, "cal"), "cal", true) == null);
        ok("work apps are marked", new AppEntry(key, "Chat", true).shown().contains("work") && !new AppEntry(key, "Chat", false).shown().contains("work"));
        ok("empty label falls back to package", new AppEntry(key, "  ", false).label.equals("com.whatsapp"));

        // ---------- LauncherConfig
        LauncherConfig c = new LauncherConfig();
        ok("defaults: camera on the left, phone on the right", c.focusKinds == (1 << Routine.TASK) && c.swipeRight.equals(AppKey.PHONE)
                && c.swipeLeft.equals(AppKey.CAMERA) && c.pauseSec == 10 && c.strictSec == 30 && c.hidden.isEmpty() && c.rules.isEmpty());
        ok("keyboard: the list of all apps opens the keyboard unless it is turned off", c.keyboard);
        LauncherConfig kb = new LauncherConfig();
        kb.keyboard = false;
        ok("keyboard: turned off, it stays off after saving and reading back", !LauncherConfig.parse(kb.serialize()).keyboard);
        ok("keyboard: an old saved 'off' (it was only the old default) does not keep the keyboard shut", LauncherConfig.parse("keyboard=0\nsize=2\n").keyboard);
        ok("keyboard: nothing saved means on", LauncherConfig.parse("").keyboard && LauncherConfig.parse(null).keyboard);
        c.hidden.add(k("secret"));
        c.size = 3; c.theme = LauncherConfig.THEME_DARK;
        c.autoOpen = false; c.keyboard = true; c.pauseSec = 15; c.strictSec = 45;
        c.swipeLeft = k("phone");
        c.swipeRight = AppKey.DASHBOARD;
        c.setFocusKind(Routine.REST, true);
        c.setRule(k("ig"), LauncherConfig.RULE_STRICT, 45, true);
        c.setRule(k("yt"), LauncherConfig.RULE_NUDGE, 0, false);
        LauncherConfig back = LauncherConfig.parse(c.serialize());
        ok("round trip: hidden apps", back.hidden.contains(k("secret")) && back.hidden.size() == 1);
        ok("round trip: look", back.size == 3 && back.theme == LauncherConfig.THEME_DARK && !back.autoOpen && back.keyboard && back.pauseSec == 15 && back.strictSec == 45);
        ok("round trip: swipes and focus kinds", back.swipeLeft.equals(k("phone")) && back.swipeRight.equals(AppKey.DASHBOARD)
                && back.isFocusKind(Routine.TASK) && back.isFocusKind(Routine.REST) && !back.isFocusKind(Routine.SLEEP));
        ok("round trip: rules in order", back.rules.size() == 2 && back.rules.get(0).key.equals(k("ig")) && back.rules.get(0).mode == 2
                && back.rules.get(0).limitMin == 45 && back.rules.get(0).always && back.rules.get(1).mode == 1 && !back.rules.get(1).always);
        ok("serialize is stable", back.serialize().equals(c.serialize()));
        ok("garbage gives defaults", LauncherConfig.parse("hello").hidden.isEmpty() && LauncherConfig.parse(null).pauseSec == 10 && LauncherConfig.parse("").size == 1);
        LauncherConfig bad = LauncherConfig.parse("DINALAUNCH1\nhomeCount=99\npauseSec=-4\nsize=x\nH\tnonsense\nR\tnot-a-key\t1\t0\t0\nK\t9\t" + k("a") + "\nswipeLeft=zzz\nunknown=1\n");
        ok("damaged and old lines are clamped or skipped", bad.pauseSec == 3 && bad.size == 1 && bad.hidden.isEmpty()
                && bad.rules.isEmpty() && bad.swipeLeft.isEmpty());
        ok("old home-list lines are ignored", LauncherConfig.parse("DINALAUNCH1\nhomeCount=6\nslotAware=1\nH\t" + k("a") + "\nK\t0\t" + k("b") + "\ntheme=1\n").theme == 1);
        ok("the default colours are Auto", new LauncherConfig().theme == LauncherConfig.THEME_AUTO && LauncherConfig.THEME_NAMES.length == 3);
        ok("the old Wallpaper look (3) now means Auto: the day poster is always the home screen",
                LauncherConfig.parse("DINALAUNCH1\ntheme=3\n").theme == LauncherConfig.THEME_AUTO);
        ok("old clock, date, now and position lines are ignored", LauncherConfig.parse("DINALAUNCH1\nalign=2\nshowClock=0\nshowDate=0\nshowNow=0\ntheme=2\nsize=3\n").theme == LauncherConfig.THEME_LIGHT
                && LauncherConfig.parse("DINALAUNCH1\nalign=2\nshowClock=0\nsize=3\n").size == 3);
        ok("a rule that does nothing is removed", c.setRule(k("yt"), 0, 0, false) == null && c.ruleFor(k("yt")) == null && c.rules.size() == 1);
        ok("rule values are clamped", c.setRule(k("ig"), 9, 99999, false).mode == 2 && c.ruleFor(k("ig")).limitMin == 1440);
        ok("limit alone makes a rule", c.setRule(k("x"), 0, 30, false) != null && c.ruleFor(k("x")).mode == 0);
        c.forget(k("ig"));
        ok("forget removes it everywhere", c.ruleFor(k("ig")) == null);
        c.forget(k("phone"));
        ok("forget clears a swipe target", c.swipeLeft.isEmpty());

        // ---------- FocusRules
        LauncherConfig r = new LauncherConfig();
        r.setRule(k("ig"), LauncherConfig.RULE_STRICT, 0, false);
        r.setRule(k("yt"), LauncherConfig.RULE_NUDGE, 0, true);
        r.setRule(k("x3"), LauncherConfig.RULE_NUDGE, 0, false);
        r.setRule(k("x4"), LauncherConfig.RULE_NUDGE, 0, false);
        r.setRule(k("lim"), LauncherConfig.RULE_NONE, 20, false);
        ok("no rule means straight in", FocusRules.decide(r, k("other"), true, Routine.TASK, 0).type == FocusRules.ALLOW);
        ok("pro: strict in a focus slot", FocusRules.decide(r, k("ig"), true, Routine.TASK, 0).type == FocusRules.STRICT
                && FocusRules.decide(r, k("ig"), true, Routine.TASK, 0).waitSec == 30);
        ok("strict outside focus slots lets you in", FocusRules.decide(r, k("ig"), true, Routine.REST, 0).type == FocusRules.ALLOW
                && FocusRules.decide(r, k("ig"), true, -1, 0).type == FocusRules.ALLOW);
        ok("nudge in a focus slot", FocusRules.decide(r, k("yt"), true, Routine.TASK, 0).type == FocusRules.NUDGE
                && FocusRules.decide(r, k("yt"), true, Routine.TASK, 0).waitSec == 10);
        ok("'also outside' pauses outside focus slots", FocusRules.decide(r, k("yt"), true, Routine.REST, 0).type == FocusRules.NUDGE
                && FocusRules.decide(r, k("yt"), true, -1, 0).type == FocusRules.NUDGE);
        ok("free: strict becomes a nudge", FocusRules.decide(r, k("ig"), false, Routine.TASK, 0).type == FocusRules.NUDGE);
        ok("free: only the first rule counts", FocusRules.FREE_APPS == 1
                && FocusRules.decide(r, k("ig"), false, Routine.TASK, 0).type == FocusRules.NUDGE
                && FocusRules.decide(r, k("yt"), false, Routine.TASK, 0).type == FocusRules.ALLOW
                && FocusRules.decide(r, k("yt"), true, Routine.TASK, 0).type == FocusRules.NUDGE
                && FocusRules.decide(r, k("x3"), false, Routine.TASK, 0).type == FocusRules.ALLOW
                && FocusRules.decide(r, k("x4"), false, Routine.TASK, 0).type == FocusRules.ALLOW
                && FocusRules.decide(r, k("x4"), true, Routine.TASK, 0).type == FocusRules.NUDGE);
        ok("daily limit not reached", FocusRules.decide(r, k("lim"), true, Routine.REST, 19 * MIN).type == FocusRules.ALLOW);
        FocusRules.Decision lim = FocusRules.decide(r, k("lim"), true, Routine.REST, 21 * MIN);
        ok("daily limit reached, any time of day", lim.type == FocusRules.LIMIT && lim.limitMin == 20 && lim.usedMin == 21 && lim.waitSec == 30);
        ok("free: limits are not applied", FocusRules.decide(r, k("lim"), false, Routine.REST, 99 * MIN).type == FocusRules.ALLOW);
        r.setFocusKind(Routine.TASK, false);
        ok("no focus kinds means no focus pauses", FocusRules.decide(r, k("ig"), true, Routine.TASK, 0).type == FocusRules.ALLOW);

        // ---------- camera and phone shortcuts, and the move from the old default
        ok("camera and phone are actions and valid", AppKey.isAction(AppKey.CAMERA) && AppKey.valid(AppKey.CAMERA)
                && AppKey.isAction(AppKey.PHONE) && AppKey.valid(AppKey.PHONE) && AppKey.actionName(AppKey.CAMERA).equals("Camera")
                && AppKey.actionName(AppKey.PHONE).equals("Phone"));
        LauncherConfig old = LauncherConfig.parse("DINALAUNCH1\ntheme=1\nswipeLeft=\nswipeRight=" + AppKey.DASHBOARD + "\n");
        ok("the old default (nothing, dashboard) moves to camera and phone", old.swipeLeft.equals(AppKey.CAMERA) && old.swipeRight.equals(AppKey.PHONE));
        LauncherConfig chosen = LauncherConfig.parse("DINALAUNCH1\nswipes=2\nswipeLeft=\nswipeRight=" + AppKey.DASHBOARD + "\n");
        ok("a choice made after the change is kept", chosen.swipeLeft.isEmpty() && chosen.swipeRight.equals(AppKey.DASHBOARD));
        ok("an action cannot be given a pause", new LauncherConfig().setRule(AppKey.CAMERA, LauncherConfig.RULE_NUDGE, 0, false) == null);

        // ---------- pause challenges
        ok("challenge names and blurbs line up", PauseChallenge.NAMES.length == 5 && PauseChallenge.BLURBS.length == 5
                && PauseChallenge.REASONS.length == PauseChallenge.REASON_TIPS.length);
        ok("validity", PauseChallenge.valid(0) && PauseChallenge.valid(4) && !PauseChallenge.valid(5) && !PauseChallenge.valid(-1));
        Random rnd = new Random(42);
        boolean sumsOk = true;
        for (int i = 0; i < 3000 && sumsOk; i++) {
            PauseChallenge.Sum sm = PauseChallenge.sum(rnd, i % 6);
            String[] part = sm.question.split(" ");
            int a1 = Integer.parseInt(part[0]);
            int b1 = Integer.parseInt(part[2]);
            int want = part[1].equals("+") ? a1 + b1 : part[1].equals("\u2212") ? a1 - b1 : a1 * b1;
            sumsOk = want == sm.answer && sm.answer >= 0 && sm.answer < 100 && b1 > 0;
        }
        ok("sums are right, small and never negative", sumsOk);
        ok("sum kinds go + - x", PauseChallenge.sum(new Random(1), 0).question.contains("+") && PauseChallenge.sum(new Random(1), 1).question.contains("\u2212")
                && PauseChallenge.sum(new Random(1), 2).question.contains("\u00d7") && PauseChallenge.sum(new Random(1), 3).question.contains("+"));
        ok("answers: exact, with spaces; not text or empty", PauseChallenge.answerMatches(" 42 ", 42) && !PauseChallenge.answerMatches("43", 42)
                && !PauseChallenge.answerMatches("", 42) && !PauseChallenge.answerMatches("4 2", 42) && !PauseChallenge.answerMatches("forty", 42)
                && !PauseChallenge.answerMatches(null, 42) && !PauseChallenge.answerMatches("99999999999", 42));
        String ph = PauseChallenge.phrase(new Random(3));
        ok("typed sentence: case, spacing and a full stop do not matter", PauseChallenge.phraseMatches("  " + ph.toUpperCase(Locale.ROOT).replace(" ", "   ") + ".", ph)
                && !PauseChallenge.phraseMatches(ph + " now", ph) && !PauseChallenge.phraseMatches("", ph) && !PauseChallenge.phraseMatches(null, ph));
        ok("rounds grow with strict", PauseChallenge.rounds(PauseChallenge.MATH, false) == 1 && PauseChallenge.rounds(PauseChallenge.MATH, true) == 3
                && PauseChallenge.rounds(PauseChallenge.BREATHE, false) == 2 && PauseChallenge.rounds(PauseChallenge.BREATHE, true) == 4);
        ok("there is always a way past, later for strict", PauseChallenge.escapeSec(false) == 30 && PauseChallenge.escapeSec(true) == 60);
        ok("only the first reason is a real need", PauseChallenge.reasonIsNeed(0) && !PauseChallenge.reasonIsNeed(1) && !PauseChallenge.reasonIsNeed(3));
        LauncherConfig ch = new LauncherConfig();
        ch.setRule(k("ig"), LauncherConfig.RULE_STRICT, 0, false, PauseChallenge.MATH);
        ch.setRule(k("yt"), LauncherConfig.RULE_NUDGE, 0, false, PauseChallenge.BREATHE);
        ok("the challenge is kept when the rule is saved", ch.ruleFor(k("ig")).challenge == PauseChallenge.MATH
                && LauncherConfig.parse(ch.serialize()).ruleFor(k("yt")).challenge == PauseChallenge.BREATHE);
        ok("a changed rule keeps its challenge (4-argument form)", ch.setRule(k("ig"), LauncherConfig.RULE_STRICT, 30, true).challenge == PauseChallenge.MATH);
        ok("an unknown challenge falls back to the plain wait", ch.setRule(k("ig"), LauncherConfig.RULE_STRICT, 30, true, 99).challenge == PauseChallenge.WAIT);
        ch.setRule(k("ig"), LauncherConfig.RULE_STRICT, 0, false, PauseChallenge.MATH);
        ok("pro: the challenge applies", FocusRules.decide(ch, k("ig"), true, Routine.TASK, 0).challenge == PauseChallenge.MATH
                && FocusRules.decide(ch, k("ig"), true, Routine.TASK, 0).heavy());
        ok("free: always the plain wait", FocusRules.decide(ch, k("ig"), false, Routine.TASK, 0).challenge == PauseChallenge.WAIT
                && FocusRules.decide(ch, k("yt"), false, Routine.TASK, 0).challenge == PauseChallenge.WAIT);
        ok("a short pause is not heavy, a used-up limit is", !FocusRules.decide(ch, k("yt"), true, Routine.TASK, 0).heavy());
        LauncherConfig lm = new LauncherConfig();
        lm.setRule(k("lim"), LauncherConfig.RULE_NONE, 10, false, PauseChallenge.TYPE);
        ok("a used-up limit asks for the challenge too", FocusRules.decide(lm, k("lim"), true, Routine.REST, 11 * MIN).challenge == PauseChallenge.TYPE
                && FocusRules.decide(lm, k("lim"), true, Routine.REST, 11 * MIN).heavy());
        LauncherConfig oldRule = LauncherConfig.parse("DINALAUNCH1\nswipes=2\nR\t" + k("ig") + "\t2\t0\t1\n");
        ok("a rule saved before challenges was added still loads", oldRule.ruleFor(k("ig")) != null && oldRule.ruleFor(k("ig")).challenge == PauseChallenge.WAIT);

        // ---------- the pause actually applies (the reasons it did not before)
        LauncherConfig fresh = new LauncherConfig();
        fresh.setRule(k("any"), LauncherConfig.RULE_NUDGE, 0, false);
        ok("a new rule pauses during a task slot by default", FocusRules.decide(fresh, k("any"), false, Routine.TASK, 0).type == FocusRules.NUDGE);

        // ---------- what a snooze costs
        List<Ev> sn = new ArrayList<>();
        sn.add(Ev.snooze(at(19, 22, 0), "2026-10-19", 15));
        sn.add(Ev.snooze(at(19, 22, 20), "2026-10-19", 30));
        sn.add(Ev.snooze(at(18, 22, 0), "2026-10-18", 60));
        ok("snoozes are counted for the day only", SnoozeCost.snoozedBefore(sn, "2026-10-19") == 45 && SnoozeCost.snoozedBefore(sn, "2026-10-20") == 0
                && SnoozeCost.snoozedBefore(null, "x") == 0);
        ok("sleep left until the alarm", SnoozeCost.sleepLeft(at(19, 23, 0), at(20, 6, 30)) == 450 && SnoozeCost.sleepLeft(at(20, 7, 0), at(20, 6, 30)) == 0);
        List<String> cl = SnoozeCost.lines(30, 450, 480, 45);
        String cs = String.join(" | ", cl);
        ok("cost lines: minutes lost, sleep before and after, shortfall and total", cs.contains("costs you 30 min") && cs.contains("7h 30m") && cs.contains("7h.")
                && cs.contains("short of the 8h") && cs.contains("75") == false && cs.contains("1h 15m"));
        ok("under six hours is called out", String.join(" ", SnoozeCost.lines(120, 450, 480, 0)).contains("Under 6 hours"));
        ok("past the alarm means no sleep", String.join(" ", SnoozeCost.lines(500, 450, 480, 0)).contains("no sleep left")
                && !String.join(" ", SnoozeCost.lines(500, 450, 480, 0)).contains("Under 6"));
        ok("no snooze, no lines; no earlier snooze, no total", SnoozeCost.lines(0, 450, 480, 0).isEmpty()
                && !String.join(" ", SnoozeCost.lines(10, 450, 480, 0)).contains("Already snoozed"));
        ok("a snooze on a plan met stays quiet about shortfall", !String.join(" ", SnoozeCost.lines(10, 500, 480, 0)).contains("short of"));

        // ---------- the day changes at midnight, the schedule at wake-up time: the scroll between them
        Routine tb = Routine.sample();
        tb.alarmOn = true;
        Overrides tov = new Overrides(); tov.sig = Plan.signature(tb);
        Plan.DayPlan before = Plan.resolve(tb, tov, at(19, 23, 30), Z);
        ok("before midnight there is no scroll", Plan.transition(tb, tov, before, at(19, 23, 30), Z) == null);
        Plan.DayPlan late = Plan.resolve(tb, tov, at(20, 0, 50), Z);
        Plan.Transition tr = Plan.transition(tb, tov, late, at(20, 0, 50), Z);
        Plan.DayPlan nextDay = Plan.resolve(tb, tov, at(20, 12, 0), Z);
        if (late.day.equals(nextDay.day)) {
            ok("(the sample day changes at midnight here: no scroll to test)", tr == null);
        } else {
            long wake = nextDay.startMs;
            long midnight = ZonedDateTime.of(2026, 10, 20, 0, 0, 0, 0, Z).toInstant().toEpochMilli();
            ok("after midnight, before wake-up: the old day is still running, the new one comes", tr != null && tr.from.day.equals(late.day) && tr.to.day.equals(nextDay.day));
            if (tr != null) {
                float want = (at(20, 0, 50) - midnight) / (float) (wake - midnight);
                ok("progress is how far midnight to wake-up has gone", Math.abs(tr.progress - want) < 0.001f && tr.progress > 0f && tr.progress < 1f);
            }
            ok("at the wake-up time the new day has fully taken over", Plan.transition(tb, tov, late, wake, Z) == null);
            Plan.Transition early = Plan.transition(tb, tov, late, midnight, Z);
            ok("at midnight sharp the scroll has just begun", early != null && early.progress == 0f);
        }

        // ---------- locking a group of apps for a time
        LauncherConfig lk = new LauncherConfig();
        LauncherConfig.Group work = lk.addGroup("  Social\tapps  ");
        ok("group name is cleaned", work.name.equals("Social apps") && lk.addGroup("").name.equals("Group"));
        lk.removeGroup(lk.groups.get(1));
        ok("apps go in and out, no duplicates", lk.setInGroup(work, k("ig"), true) && lk.setInGroup(work, k("yt"), true)
                && !lk.setInGroup(work, k("ig"), true) && work.apps.size() == 2 && lk.setInGroup(work, k("yt"), false) && work.apps.size() == 1);
        ok("an action cannot be locked", !lk.setInGroup(work, AppKey.CAMERA, true) && !lk.setInGroup(work, "junk", true));
        work.days = LockRules.WEEKDAYS; work.startMin = 9 * 60; work.endMin = 17 * 60;
        ok("daytime window: locked inside, until the end", LockRules.check(lk, k("ig"), true, at(19, 10, 0), Z) != null
                && LockRules.check(lk, k("ig"), true, at(19, 10, 0), Z).untilMs == at(19, 17, 0));
        ok("daytime window: not before, not at the end, not on the weekend", LockRules.check(lk, k("ig"), true, at(19, 8, 59), Z) == null
                && LockRules.check(lk, k("ig"), true, at(19, 17, 0), Z) == null && LockRules.check(lk, k("ig"), true, at(24, 10, 0), Z) == null);
        ok("an app that is not in the group is never locked", LockRules.check(lk, k("other"), true, at(19, 10, 0), Z) == null);
        work.startMin = 22 * 60; work.endMin = 6 * 60;
        ok("overnight: still locked after midnight, from the day it started", LockRules.check(lk, k("ig"), true, at(20, 3, 0), Z) != null
                && LockRules.check(lk, k("ig"), true, at(20, 3, 0), Z).untilMs == at(20, 6, 0) && LockRules.check(lk, k("ig"), true, at(19, 23, 0), Z).untilMs == at(20, 6, 0));
        ok("overnight: Friday night runs into Saturday, Saturday night is not locked", LockRules.check(lk, k("ig"), true, at(24, 3, 0), Z) != null
                && LockRules.check(lk, k("ig"), true, at(24, 23, 0), Z) == null && LockRules.check(lk, k("ig"), true, at(25, 3, 0), Z) == null);
        ok("overnight: not in the daytime", LockRules.check(lk, k("ig"), true, at(20, 12, 0), Z) == null);
        work.days = 1 | 2; work.startMin = 0; work.endMin = 0;
        ok("start equal to end means the whole day, and back-to-back days count as one", LockRules.check(lk, k("ig"), true, at(19, 10, 0), Z) != null
                && LockRules.check(lk, k("ig"), true, at(19, 10, 0), Z).untilMs == at(21, 0, 0) && LockRules.check(lk, k("ig"), true, at(21, 10, 0), Z) == null);
        work.days = 0; work.untilMs = at(19, 12, 0);
        ok("lock now: held until the moment, free after it", LockRules.check(lk, k("ig"), true, at(19, 11, 0), Z).untilMs == at(19, 12, 0)
                && LockRules.check(lk, k("ig"), true, at(19, 12, 0), Z) == null && LockRules.check(lk, k("ig"), true, at(19, 13, 0), Z) == null);
        work.days = LockRules.EVERY_DAY; work.startMin = 11 * 60; work.endMin = 14 * 60;
        ok("a lock started by hand and a window together: the later end wins", LockRules.check(lk, k("ig"), true, at(19, 11, 30), Z).untilMs == at(19, 14, 0));
        work.untilMs = 0;
        ok("no daily window and no lock now: never locked", new LauncherConfig().groups.isEmpty() && LockRules.nextStart(work, at(19, 8, 0), Z) == at(19, 11, 0));
        work.days = LockRules.WEEKDAYS; work.startMin = 9 * 60; work.endMin = 17 * 60;
        ok("next start skips the weekend", LockRules.nextStart(work, at(23, 18, 0), Z) == at(26, 9, 0) && LockRules.nextStart(work, at(19, 8, 0), Z) == at(19, 9, 0));
        work.days = 0;
        ok("no days: no next start", LockRules.nextStart(work, at(19, 8, 0), Z) == 0);
        work.days = LockRules.EVERY_DAY; work.startMin = 0; work.endMin = 0;
        LauncherConfig.Group second = lk.addGroup("Games");
        lk.setInGroup(second, k("ig"), true);
        second.days = LockRules.EVERY_DAY; second.startMin = 0; second.endMin = 0; second.strict = true;
        ok("free: only the first group is kept", LockRules.check(lk, k("ig"), false, at(19, 10, 0), Z).group == work
                && !LockRules.check(lk, k("ig"), false, at(19, 10, 0), Z).strict);
        lk.setInGroup(work, k("ig"), false);
        ok("free: the second group does not hold", LockRules.check(lk, k("ig"), false, at(19, 10, 0), Z) == null);
        ok("pro: the second group holds, and strict counts", LockRules.check(lk, k("ig"), true, at(19, 10, 0), Z).group == second
                && LockRules.check(lk, k("ig"), true, at(19, 10, 0), Z).strict);
        ok("a strict group is only strict with pro", !LockRules.check(lk, k("ig"), true, at(19, 10, 0), Z).group.equals(null)
                && LockRules.active(lk, second, true) && !LockRules.active(lk, second, false) && LockRules.active(lk, work, false));
        work.days = LockRules.WEEKDAYS; work.startMin = 9 * 60; work.endMin = 17 * 60; work.untilMs = at(19, 20, 0); work.strict = false;
        lk.setInGroup(work, k("ig"), true); lk.setInGroup(work, k("yt"), true);
        LauncherConfig back2 = LauncherConfig.parse(lk.serialize());
        ok("groups survive saving", back2.groups.size() == 2 && back2.groups.get(0).name.equals("Social apps") && back2.groups.get(0).apps.size() == 2
                && back2.groups.get(0).days == LockRules.WEEKDAYS && back2.groups.get(0).startMin == 540 && back2.groups.get(0).endMin == 1020
                && back2.groups.get(0).untilMs == at(19, 20, 0) && !back2.groups.get(0).strict && back2.groups.get(1).strict && back2.serialize().equals(lk.serialize()));
        LauncherConfig dmg = LauncherConfig.parse("DINALAUNCH1\nswipes=2\nG\tX\t99999\t-5\t999\t1\tabc\t" + k("a") + "\tjunk\t" + k("a") + "\nG\tshort\nG\t\t1\t2\t3\t0\t0\n");
        ok("a damaged group line is clamped, a short one skipped", dmg.groups.size() == 2 && dmg.groups.get(0).startMin == 1439 && dmg.groups.get(0).endMin == 0
                && dmg.groups.get(0).days == 127 && dmg.groups.get(0).untilMs == 0 && dmg.groups.get(0).apps.size() == 1 && dmg.groups.get(1).name.equals("Group"));
        lk.forget(k("yt"));
        ok("an uninstalled app leaves its groups", !lk.groups.get(0).has(k("yt")));
        LauncherConfig many = new LauncherConfig();
        for (int i = 0; i < 12; i++) many.addGroup("g" + i);
        ok("there is a limit on groups and on apps in a group", many.groups.size() == LauncherConfig.MAX_GROUPS
                && !many.setInGroup(many.groups.get(0), "x", true));
        LauncherConfig.Group big = many.groups.get(0);
        for (int i = 0; i < 60; i++) many.setInGroup(big, k("app" + i), true);
        ok("a group holds at most " + LauncherConfig.MAX_GROUP_APPS + " apps", big.apps.size() == LauncherConfig.MAX_GROUP_APPS);
        ok("day names", LockRules.daysText(127).equals("Every day") && LockRules.daysText(31).equals("Weekdays") && LockRules.daysText(96).equals("Weekends")
                && LockRules.daysText(1 | 4).equals("Mon, Wed") && LockRules.daysText(0).equals("No days"));
        ok("time left in words", LockRules.left(at(19, 12, 0), at(19, 10, 40)).equals("1h 20m") && LockRules.left(at(19, 12, 0), at(19, 11, 15)).equals("45 min")
                && LockRules.left(at(19, 12, 0), at(19, 11, 59) + 30000L).equals("1 min"));
        ok("a moment read as today, tomorrow or a weekday", LockRules.untilText(at(19, 17, 0), at(19, 10, 0), Z, false).equals("5:00 PM")
                && LockRules.untilText(at(20, 6, 0), at(19, 23, 0), Z, true).equals("tomorrow 06:00")
                && LockRules.untilText(at(22, 21, 30), at(19, 10, 0), Z, false).equals("Thu 9:30 PM"));
        LauncherConfig.Group st = new LauncherConfig().addGroup("Night");
        st.days = LockRules.EVERY_DAY; st.startMin = 21 * 60; st.endMin = 6 * 60;
        ok("settings line: locked, next, or not scheduled", LockRules.status(st, at(19, 22, 0), Z, false).equals("Locked until tomorrow 6:00 AM")
                && LockRules.status(st, at(19, 12, 0), Z, false).equals("Next: 9:00 PM") && notScheduled(st));
        LauncherConfig sc = new LauncherConfig();
        LauncherConfig.Group sg = sc.addGroup("Strict");
        sg.days = LockRules.EVERY_DAY; sg.startMin = 9 * 60; sg.endMin = 17 * 60; sg.strict = true;
        ok("a strict lock that holds is found, only with pro, and only while it holds", LockRules.strictUntil(sc, true, at(19, 10, 0), Z) == at(19, 17, 0)
                && LockRules.strictUntil(sc, false, at(19, 10, 0), Z) == 0 && LockRules.strictUntil(sc, true, at(19, 18, 0), Z) == 0);
        sg.strict = false;
        ok("a lock that is not strict never counts as strict", LockRules.strictUntil(sc, true, at(19, 10, 0), Z) == 0);
        LauncherConfig fc = new LauncherConfig();
        LauncherConfig.Group fg = fc.addGroup("Social");
        for (String a : new String[]{"a", "b", "c", "d"}) fc.setInGroup(fg, k(a), true);
        fg.days = LockRules.EVERY_DAY; fg.startMin = 0; fg.endMin = 0;
        ok("free: a lock holds the first app only", LockRules.check(fc, k("a"), false, at(19, 10, 0), Z) != null
                && LockRules.check(fc, k("b"), false, at(19, 10, 0), Z) == null
                && LockRules.check(fc, k("c"), false, at(19, 10, 0), Z) == null && LockRules.check(fc, k("d"), false, at(19, 10, 0), Z) == null);
        ok("pro: a lock holds every app in the group", LockRules.check(fc, k("c"), true, at(19, 10, 0), Z) != null
                && LockRules.check(fc, k("d"), true, at(19, 10, 0), Z) != null);
        ok("held apps list: one for free, all for pro", LockRules.heldApps(fc, fg, false).size() == 1 && LockRules.heldApps(fc, fg, true).size() == 4
                && LockRules.FREE_GROUP_APPS == 1 && LockRules.FREE_GROUPS == 1);
        ok("free: only today's report, no saved day", Limits.FREE_REPORT_DAYS == 0 && Limits.reportDays(false, 10) == 0 && Limits.reportDays(false, 2) == 0
                && Limits.reportDays(false, 0) == 0 && Limits.reportDays(true, 10) == 10 && Limits.reportDays(true, 0) == 0);
        ok("the week view is part of pro", !Limits.weekAllowed(false) && Limits.weekAllowed(true));
        ok("the deeper analysis is part of pro", !Limits.analysisAllowed(false) && Limits.analysisAllowed(true));
        ok("an old saved 'icons' line is ignored", LauncherConfig.parse("DINALAUNCH1\nswipes=2\nicons=0\ntheme=1\n").theme == 1);
        ok("an old config without groups loads", LauncherConfig.parse("DINALAUNCH1\nswipes=2\ntheme=1\n").groups.isEmpty());

        // ---------- the one-day video pass: ONE item per video, never a whole area and never the whole app
        long t0 = at(19, 10, 0), t1 = DayPass.endOf(t0);
        ok("a pass lasts exactly a day", t1 - t0 == 24L * 3600000L);
        ok("there are 15 items, numbered 1 to 15", DayPass.FIRST == 1 && DayPass.LAST == 15 && Integer.bitCount(DayPass.ALL) == 15
                && DayPass.FREE == 0);
        boolean exactlyOne = true, nothingBefore = true, nothingAfter = true;
        for (int itm = DayPass.FIRST; itm <= DayPass.LAST; itm++) {
            int m = DayPass.mask(itm, t0, t1, t0 + 1000);
            if (m != DayPass.bit(itm) || Integer.bitCount(m) != 1) exactlyOne = false;
            for (int g = DayPass.FIRST; g <= DayPass.LAST; g++) if (g != itm && DayPass.has(m, g)) exactlyOne = false;
            if (DayPass.mask(itm, t0, t1, t0 - 1) != DayPass.FREE) nothingBefore = false;
            if (DayPass.mask(itm, t0, t1, t1) != DayPass.FREE) nothingAfter = false;
        }
        ok("a video for any item opens exactly that item and no other (all 15 checked)", exactlyOne);
        ok("a pass opens nothing before it starts (clock set back) and nothing at its end", nothingBefore && nothingAfter);
        ok("Pro (the purchase) opens all 15 items", DayPass.has(DayPass.ALL, DayPass.REPORT_DAYS) && DayPass.has(DayPass.ALL, DayPass.LOOK_GRAPHITE)
                && DayPass.has(DayPass.ALL, DayPass.ANALYSIS) && DayPass.has(DayPass.ALL, DayPass.PAUSE_STRICT) && !DayPass.has(DayPass.ALL, DayPass.NONE)
                && !DayPass.has(DayPass.ALL, 16));
        ok("the free version opens none", !DayPass.has(DayPass.FREE, DayPass.PAUSE_APPS) && !DayPass.has(DayPass.FREE, DayPass.GOALS));
        ok("a pass is open from its first moment to the last before its end", DayPass.covers(DayPass.LOCK_STRICT, DayPass.LOCK_STRICT, t0, t1, t0)
                && DayPass.covers(DayPass.LOCK_STRICT, DayPass.LOCK_STRICT, t0, t1, t1 - 1) && !DayPass.covers(DayPass.LOCK_STRICT, DayPass.LOCK_STRICT, t0, t1, t1));
        ok("covers is true only for the item the pass is for", !DayPass.covers(DayPass.LOCK_APPS, DayPass.LOCK_STRICT, t0, t1, t0 + 1)
                && !DayPass.covers(DayPass.PAUSE_STRICT, DayPass.PAUSE_LIMIT, t0, t1, t0 + 1));
        ok("a pass longer than a day is refused", !DayPass.running(DayPass.PAUSE_STRICT, t0, t0 + 25L * 3600000L, t0 + 1000)
                && !DayPass.running(DayPass.PAUSE_STRICT, t0, t0 + 72L * 3600000L, t0 + 1000)
                && DayPass.running(DayPass.PAUSE_STRICT, t0, t0 + 24L * 3600000L, t0 + 1000));
        ok("an empty or reversed pass never opens anything", !DayPass.running(DayPass.PAUSE_STRICT, t0, t0, t0) && !DayPass.running(DayPass.PAUSE_STRICT, t1, t0, t0 + 1000)
                && !DayPass.running(DayPass.NONE, t0, t1, t0 + 1000) && !DayPass.running(99, t0, t1, t0 + 1000));
        ok("only the 15 items count", DayPass.valid(DayPass.REPORT_DAYS) && DayPass.valid(DayPass.ANALYSIS) && !DayPass.valid(0) && !DayPass.valid(16)
                && !DayPass.covers(0, 0, t0, t1, t0 + 1000) && DayPass.bit(0) == 0 && DayPass.bit(16) == 0);
        ok("one video at a time", !DayPass.canStart(DayPass.PAUSE_STRICT, t0, t1, t0 + 5000) && DayPass.canStart(DayPass.PAUSE_STRICT, t0, t1, t1)
                && DayPass.canStart(DayPass.NONE, 0, 0, t0));

        // ---------- the offer: 3 videos = everything in Pro for a day
        ok("the offer: three videos, a day, everything", DayPass.VIDEOS == 3 && DayPass.LENGTH_MS == 24L * 3600000L && DayPass.MAX_MS == DayPass.LENGTH_MS
                && DayPass.count(DayPass.ALL) == 15);
        int pauseGroup = DayPass.bit(DayPass.PAUSE_APPS) | DayPass.bit(DayPass.PAUSE_STRICT) | DayPass.bit(DayPass.PAUSE_LIMIT) | DayPass.bit(DayPass.PAUSE_PLAYFUL);
        ok("the groups: the pauses are four items, the reports three", DayPass.areaMask(DayPass.AREA_PAUSES) == pauseGroup
                && !DayPass.has(pauseGroup, DayPass.LOCK_MORE) && !DayPass.has(pauseGroup, DayPass.REPORT_DAYS)
                && DayPass.count(DayPass.areaMask(DayPass.AREA_REPORTS)) == 3 && DayPass.has(DayPass.areaMask(DayPass.AREA_REPORTS), DayPass.ANALYSIS));
        long u1 = t0 + DayPass.MAX_MS;
        ok("pass: it is open the whole day and not a moment after", DayPass.openMask(DayPass.ALL, t0, u1, t0 + 23L * 3600000L) == DayPass.ALL
                && DayPass.openMask(DayPass.ALL, t0, u1, u1) == DayPass.FREE && DayPass.openMask(DayPass.ALL, t0, u1, t0 - 1) == DayPass.FREE);
        ok("pass: a group pass opens all of its items and no other", DayPass.openMask(pauseGroup, t0, t1, t0 + 1000) == pauseGroup
                && !DayPass.has(DayPass.openMask(pauseGroup, t0, t1, t0 + 1000), DayPass.LOCK_STRICT));
        ok("pass: a window over a day, an empty or reversed one, or no items opens nothing", DayPass.openMask(pauseGroup, t0, t0 + 25L * 3600000L, t0 + 1000) == DayPass.FREE
                && DayPass.openMask(pauseGroup, t0, t0, t0) == DayPass.FREE && DayPass.openMask(pauseGroup, t1, t0, t0 + 1000) == DayPass.FREE
                && DayPass.openMask(0, t0, t1, t0 + 1000) == DayPass.FREE && DayPass.openMask(1 << 20, t0, t1, t0 + 1000) == DayPass.FREE
                && !DayPass.validMask(0) && !DayPass.validMask(1) && DayPass.validMask(DayPass.ALL) && !DayPass.validMask(DayPass.ALL | (1 << 16)));
        ok("pass: Pro still opens everything whatever the record says", DayPass.count(DayPass.ALL) == 15);
        ok("words: one item, a whole group, a few", DayPass.maskName(DayPass.bit(DayPass.PAUSE_STRICT)).equals("strict pauses")
                && DayPass.maskName(pauseGroup).equals("everything in Pauses")
                && DayPass.maskName(DayPass.areaMask(DayPass.AREA_LOOKS)).equals("everything in Looks")
                && DayPass.maskName(DayPass.bit(DayPass.GOALS) | DayPass.bit(DayPass.ROUTINES)).equals("2 Pro features") && DayPass.maskName(0).isEmpty()
                && DayPass.maskName(DayPass.ALL).equals("everything in Pro"));
        ok("mask helpers", DayPass.firstItem(pauseGroup) == DayPass.PAUSE_APPS && DayPass.firstItem(0) == DayPass.NONE && DayPass.count(pauseGroup) == 4);
        boolean words = true;
        for (int itm = DayPass.FIRST; itm <= DayPass.LAST; itm++) {
            if (DayPass.name(itm).length() < 4 || DayPass.title(itm).length() < 4 || DayPass.shortName(itm).length() < 4 || DayPass.area(itm) < 0) words = false;
        }
        ok("every item has words and a place in a group", words && DayPass.name(0).isEmpty() && DayPass.title(99).isEmpty() && DayPass.area(0) == -1);
        ok("the groups: reports, pauses, locks, routines, goals, looks", DayPass.area(DayPass.REPORT_WEEK) == DayPass.AREA_REPORTS
                && DayPass.area(DayPass.PAUSE_PLAYFUL) == DayPass.AREA_PAUSES && DayPass.area(DayPass.LOCK_MORE) == DayPass.AREA_LOCKS
                && DayPass.area(DayPass.ROUTINES) == DayPass.AREA_ROUTINES && DayPass.area(DayPass.GOALS) == DayPass.AREA_GOALS
                && DayPass.area(DayPass.LOOK_OCEAN) == DayPass.AREA_LOOKS && DayPass.area(DayPass.ANALYSIS) == DayPass.AREA_REPORTS);
        ok("each colour look is its own item", DayPass.lookItem(1) == DayPass.LOOK_SUNSET && DayPass.lookItem(2) == DayPass.LOOK_OCEAN
                && DayPass.lookItem(3) == DayPass.LOOK_GRAPHITE && DayPass.lookItem(0) == DayPass.NONE && DayPass.lookOf(DayPass.LOOK_OCEAN) == 2
                && DayPass.lookOf(DayPass.GOALS) == -1);
        ok("time left and the line about a running pass", DayPass.left(DayPass.PAUSE_STRICT, t0, t1, t0 + 5 * 3600000L).equals("19h")
                && DayPass.left(DayPass.PAUSE_STRICT, t0, t1, t1).isEmpty()
                && DayPass.status(DayPass.PAUSE_STRICT, t0, t1, t0, Z, false).equals("Open until tomorrow 10:00 AM")
                && DayPass.status(DayPass.PAUSE_STRICT, t0, t1, t1, Z, false).isEmpty());

        // pauses: each of the four items lifts only its own limit
        final int[] pauseItems = {DayPass.PAUSE_APPS, DayPass.PAUSE_STRICT, DayPass.PAUSE_LIMIT, DayPass.PAUSE_PLAYFUL};
        LauncherConfig pc = fcfg(3);   // a is free, b and c are over the free number
        LauncherConfig.Rule pr = pc.setRule(k("a"), LauncherConfig.RULE_STRICT, 30, true, PauseChallenge.MATH);
        boolean appsOnlyForApps = true;
        for (int itm = DayPass.FIRST; itm <= DayPass.LAST; itm++) {
            boolean active = FocusRules.active(pc, k("c"), DayPass.bit(itm));
            if (active != (itm == DayPass.PAUSE_APPS)) appsOnlyForApps = false;
        }
        ok("only 'pauses for more apps' makes a third app's pause work (all 15 items tried)", appsOnlyForApps
                && FocusRules.active(pc, k("a"), DayPass.FREE) && FocusRules.active(pc, k("c"), DayPass.ALL) && !FocusRules.active(pc, k("c"), DayPass.FREE));
        int slot = Routine.TASK;
        pc.setFocusKind(slot, true);
        FocusRules.Decision dFree = FocusRules.decide(pc, k("a"), DayPass.FREE, slot, 31 * MIN);
        ok("free: the strict rule is a short pause, the limit and the playful kind are ignored",
                dFree.type == FocusRules.NUDGE && dFree.challenge == PauseChallenge.WAIT);
        FocusRules.Decision dStrict = FocusRules.decide(pc, k("a"), DayPass.bit(DayPass.PAUSE_STRICT), slot, 31 * MIN);
        ok("a video for strict pauses gives the strict pause only (no limit, no playful kind)",
                dStrict.type == FocusRules.STRICT && dStrict.challenge == PauseChallenge.WAIT);
        FocusRules.Decision dLimit = FocusRules.decide(pc, k("a"), DayPass.bit(DayPass.PAUSE_LIMIT), slot, 31 * MIN);
        ok("a video for daily limits gives the limit only (still a short pause kind, no playful kind)",
                dLimit.type == FocusRules.LIMIT && dLimit.challenge == PauseChallenge.WAIT);
        ok("a video for daily limits does not make the pause strict", FocusRules.decide(pc, k("a"), DayPass.bit(DayPass.PAUSE_LIMIT), slot, 5 * MIN).type == FocusRules.NUDGE);
        FocusRules.Decision dPlay = FocusRules.decide(pc, k("a"), DayPass.bit(DayPass.PAUSE_PLAYFUL), slot, 31 * MIN);
        ok("a video for playful pauses gives the playful kind only (a short pause, no limit, not strict)",
                dPlay.type == FocusRules.NUDGE && dPlay.challenge == PauseChallenge.MATH);
        FocusRules.Decision dAll = FocusRules.decide(pc, k("a"), DayPass.ALL, slot, 31 * MIN);
        ok("Pro gives all three at once", dAll.type == FocusRules.LIMIT && dAll.challenge == PauseChallenge.MATH);
        boolean otherItemsChangeNothing = true;
        for (int itm = DayPass.FIRST; itm <= DayPass.LAST; itm++) {
            if (itm == DayPass.PAUSE_STRICT || itm == DayPass.PAUSE_LIMIT || itm == DayPass.PAUSE_PLAYFUL) continue;
            FocusRules.Decision d = FocusRules.decide(pc, k("a"), DayPass.bit(itm), slot, 31 * MIN);
            if (d.type != dFree.type || d.challenge != dFree.challenge) otherItemsChangeNothing = false;
        }
        ok("a pass for anything else (12 other items) changes nothing about a pause", otherItemsChangeNothing);

        // locks: each of the three items lifts only its own limit
        boolean moreOnlyForMore = true;
        LauncherConfig lc = new LauncherConfig();
        LauncherConfig.Group l1 = lc.addGroup("One");
        LauncherConfig.Group l2 = lc.addGroup("Two");
        for (String a : new String[]{"a", "b", "c", "d"}) { lc.setInGroup(l1, k(a), true); lc.setInGroup(l2, k(a + a), true); }
        l1.days = LockRules.EVERY_DAY; l1.startMin = 0; l1.endMin = 0; l1.strict = true;
        l2.days = LockRules.EVERY_DAY; l2.startMin = 0; l2.endMin = 0; l2.strict = true;
        for (int itm = DayPass.FIRST; itm <= DayPass.LAST; itm++) {
            if (LockRules.active(lc, l2, DayPass.bit(itm)) != (itm == DayPass.LOCK_MORE)) moreOnlyForMore = false;
        }
        ok("only 'more than one lock' makes the second lock work (all 15 items tried)", moreOnlyForMore && LockRules.active(lc, l1, DayPass.FREE)
                && LockRules.active(lc, l2, DayPass.ALL));
        ok("a video for 'more apps in a lock' holds all four apps, and still not a second lock",
                LockRules.heldApps(lc, l1, DayPass.bit(DayPass.LOCK_APPS)).size() == 4 && LockRules.heldApps(lc, l2, DayPass.bit(DayPass.LOCK_APPS)).isEmpty());
        ok("free: a lock holds one app", LockRules.heldApps(lc, l1, DayPass.FREE).size() == 1);
        ok("a pass for a second lock still holds only one app in each lock",
                LockRules.heldApps(lc, l1, DayPass.bit(DayPass.LOCK_MORE)).size() == 1 && LockRules.heldApps(lc, l2, DayPass.bit(DayPass.LOCK_MORE)).size() == 1);
        LockRules.Hit hFree = LockRules.check(lc, k("a"), DayPass.FREE, at(19, 10, 0), Z);
        LockRules.Hit hStrict = LockRules.check(lc, k("a"), DayPass.bit(DayPass.LOCK_STRICT), at(19, 10, 0), Z);
        ok("a lock is strict only while 'strict locks' is open", hFree != null && !hFree.strict && hStrict != null && hStrict.strict
                && !LockRules.check(lc, k("a"), DayPass.bit(DayPass.LOCK_MORE) | DayPass.bit(DayPass.LOCK_APPS), at(19, 10, 0), Z).strict);
        ok("a video for strict locks does not lock a third app or a second lock", LockRules.check(lc, k("c"), DayPass.bit(DayPass.LOCK_STRICT), at(19, 10, 0), Z) == null
                && LockRules.check(lc, k("aa"), DayPass.bit(DayPass.LOCK_STRICT), at(19, 10, 0), Z) == null);
        ok("strictUntil finds a strict lock only with 'strict locks' open", LockRules.strictUntil(lc, DayPass.FREE, at(19, 10, 0), Z) == 0
                && LockRules.strictUntil(lc, DayPass.bit(DayPass.LOCK_MORE), at(19, 10, 0), Z) == 0
                && LockRules.strictUntil(lc, DayPass.bit(DayPass.LOCK_STRICT), at(19, 10, 0), Z) != 0);

        // reports: the saved days and the week view are separate items; the old boolean calls still work
        ok("the week view is part of its own item", Limits.weekAllowed(DayPass.has(DayPass.bit(DayPass.REPORT_WEEK), DayPass.REPORT_WEEK))
                && !Limits.weekAllowed(DayPass.has(DayPass.bit(DayPass.REPORT_DAYS), DayPass.REPORT_WEEK)));
        ok("every saved day is part of its own item", Limits.reportDays(DayPass.has(DayPass.bit(DayPass.REPORT_DAYS), DayPass.REPORT_DAYS), 10) == 10
                && Limits.reportDays(DayPass.has(DayPass.bit(DayPass.REPORT_WEEK), DayPass.REPORT_DAYS), 10) == Limits.FREE_REPORT_DAYS);
        ok("the deeper analysis is its own item", Limits.analysisAllowed(DayPass.has(DayPass.bit(DayPass.ANALYSIS), DayPass.ANALYSIS))
                && !Limits.analysisAllowed(DayPass.has(DayPass.bit(DayPass.REPORT_DAYS) | DayPass.bit(DayPass.REPORT_WEEK), DayPass.ANALYSIS)));

        // ---------- the Home button: noticing that Dinacharya lost the home role
        ok("home: asked when it was home, is not now, and not snoozed", HomeWatch.shouldAsk(true, false, 0, t0));
        ok("home: never asked of someone who did not use it as their home app", !HomeWatch.shouldAsk(false, false, 0, t0));
        ok("home: not asked while it is the home app", !HomeWatch.shouldAsk(true, true, 0, t0));
        ok("home: 'Not now' is quiet for a day, then asks again", !HomeWatch.shouldAsk(true, false, HomeWatch.snoozeFrom(t0), t0 + 1000)
                && !HomeWatch.shouldAsk(true, false, HomeWatch.snoozeFrom(t0), t0 + HomeWatch.SNOOZE_MS - 1)
                && HomeWatch.shouldAsk(true, false, HomeWatch.snoozeFrom(t0), t0 + HomeWatch.SNOOZE_MS));
        ok("home: remembered once it has been home, and stays remembered", HomeWatch.remember(false, true) && HomeWatch.remember(true, false)
                && !HomeWatch.remember(false, false));

        // ---------- what the app's icon leads to
        ok("first run: welcome, then the tour, then the dashboard", FirstRun.next(false, false) == FirstRun.WELCOME
                && FirstRun.next(true, false) == FirstRun.TOUR && FirstRun.next(true, true) == FirstRun.DASHBOARD);
        ok("an unfinished setup starts again from the welcome", FirstRun.next(false, true) == FirstRun.WELCOME);

        // ---------- reports: counting the pauses
        Routine base = Routine.sample();
        base.alarmOn = true;
        Overrides ov = new Overrides(); ov.sig = Plan.signature(base);
        long now = at(19, 10, 43);
        Plan.DayPlan p = Plan.resolve(base, ov, now, Z);
        List<Ev> events = new ArrayList<>();
        events.add(Ev.launch(at(19, 9, 40), p.day.toString(), 5, "com.ig", "Instagram", true));
        events.add(Ev.launch(at(19, 9, 50), p.day.toString(), 5, "com.ig", "Instagram", false));
        events.add(Ev.launch(at(19, 10, 5), p.day.toString(), 5, "com.ig", "Instagram", false));
        events.add(Ev.launch(at(19, 10, 20), p.day.toString(), 5, "com.yt", "YouTube", true));
        events.add(Ev.launch(at(18, 10, 20), "2026-10-18", 5, "com.yt", "YouTube", false));
        DayReport rep = ReportAssembler.assemble(p, events, null, true, now);
        ok("report counts the day's pauses", rep.pauses == 4 && rep.wentBack == 2 && rep.openedAfter == 2 && rep.pausedMost.equals("Instagram"));
        DayReport again = DayReport.parse(rep.serialize());
        ok("report keeps them when saved", again != null && again.pauses == 4 && again.wentBack == 2 && again.openedAfter == 2 && again.pausedMost.equals("Instagram"));
        DayReport oldStyle = DayReport.parse(rep.serialize().replaceAll("(?m)^(pauses|wentBack|openedAfter|pausedMost)=.*\n", ""));
        ok("an old report without them still opens", oldStyle != null && oldStyle.pauses == 0 && oldStyle.pausedMost.isEmpty());
        ok("a day with no pauses has none", ReportAssembler.assemble(p, new ArrayList<Ev>(), null, true, now).pauses == 0);

        // ---------- suggestions
        DayReport t = new DayReport();
        t.day = "2026-10-19"; t.pauses = 6; t.openedAfter = 5; t.wentBack = 1; t.pausedMost = "Instagram"; t.alarmsOn = true;
        String joined = String.join(" | ", Suggestions.generate(t, new ArrayList<DayReport>(), base));
        ok("tip when pauses are ignored", joined.contains("Instagram") && joined.contains("Strict"));
        t.openedAfter = 1; t.wentBack = 5;
        ok("praise when you go back", String.join(" | ", Suggestions.generate(t, new ArrayList<DayReport>(), base)).contains("stopped yourself 5 times"));
        t.pauses = 2; t.wentBack = 2; t.openedAfter = 0;
        ok("no tip for a couple of pauses", !String.join(" | ", Suggestions.generate(t, new ArrayList<DayReport>(), base)).contains("home screen"));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
