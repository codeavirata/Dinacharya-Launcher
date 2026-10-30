package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** Locks that hold during an activity type, hiding locked apps, and how both are saved. */
public class LockTypeTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static String k(String pkg) { return AppKey.make(pkg, pkg + ".Main", 0); }

    public static void main(String[] args) {
        LauncherConfig c = new LauncherConfig();
        String ig = k("com.insta"), yt = k("com.tube"), wa = k("com.chat");
        LauncherConfig.Group g = c.addGroup("Social");
        c.setInGroup(g, ig, true);
        c.setInGroup(g, yt, true);
        g.days = 0;
        long now = at(23, 10, 0);
        LockRules.Slot study = new LockRules.Slot(Routine.TASK, "Study", at(23, 11, 0));

        // ---- keys
        ok("a type key is lower case and kind first", LauncherConfig.typeKey(Routine.TASK, "Study").equals("0:study"));
        ok("a type with no name is Other", LauncherConfig.typeKey(Routine.REST, "").equals("1:other") && LauncherConfig.typeKey(Routine.REST, "  ").equals("1:other"));
        ok("the key for a whole kind", LauncherConfig.anyKey(Routine.MEAL).equals("2:*"));
        ok("keys are checked", LauncherConfig.validDuring("0:study") && LauncherConfig.validDuring("4:*") && !LauncherConfig.validDuring("9:x")
                && !LauncherConfig.validDuring("x:y") && !LauncherConfig.validDuring("0:") && !LauncherConfig.validDuring(null) && !LauncherConfig.validDuring("study"));

        // ---- nothing chosen: a slot locks nothing
        ok("no types: a running slot does not lock", LockRules.check(c, ig, DayPass.ALL, now, Z, study) == null);

        // ---- a single type
        g.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));
        LockRules.Hit h = LockRules.check(c, ig, DayPass.ALL, now, Z, study);
        ok("during Study: locked while a Study slot runs, until its end", h != null && h.untilMs == at(23, 11, 0));
        ok("the lock says which type holds it", h != null && h.during.equals("Study"));
        ok("an app that is not in the group stays free", LockRules.check(c, wa, DayPass.ALL, now, Z, study) == null);
        ok("a different type does not lock", LockRules.check(c, ig, DayPass.ALL, now, Z, new LockRules.Slot(Routine.TASK, "Practice", at(23, 11, 0))) == null);
        ok("the same name of another kind does not lock", LockRules.check(c, ig, DayPass.ALL, now, Z, new LockRules.Slot(Routine.REST, "Study", at(23, 11, 0))) == null);
        ok("the name is matched without regard to case", LockRules.check(c, ig, DayPass.ALL, now, Z, new LockRules.Slot(Routine.TASK, "STUDY", at(23, 11, 0))) != null);
        ok("no slot, no lock", LockRules.check(c, ig, DayPass.ALL, now, Z, LockRules.Slot.NONE) == null);
        ok("a slot that has ended does not lock", LockRules.check(c, ig, DayPass.ALL, at(23, 11, 0), Z, study) == null
                && LockRules.check(c, ig, DayPass.ALL, at(23, 11, 5), Z, study) == null);
        ok("a lock by type is not known to the old calls", LockRules.check(c, ig, DayPass.ALL, now, Z) == null && !LockRules.groupLocked(g, now, Z));
        ok("group locked, with the slot", LockRules.groupLocked(g, now, Z, study) && !LockRules.groupLocked(g, now, Z, LockRules.Slot.NONE));

        // ---- a whole kind
        g.during.clear();
        g.during.add(LauncherConfig.anyKey(Routine.TASK));
        ok("any task: every sort of task slot locks", LockRules.check(c, ig, DayPass.ALL, now, Z, study) != null
                && LockRules.check(c, ig, DayPass.ALL, now, Z, new LockRules.Slot(Routine.TASK, "Other", at(23, 11, 0))) != null
                && LockRules.check(c, ig, DayPass.ALL, now, Z, new LockRules.Slot(Routine.REST, "Nap", at(23, 11, 0))) == null);

        // ---- the Other type
        g.during.clear();
        g.during.add(LauncherConfig.typeKey(Routine.TASK, "Other"));
        ok("Other task locks a task with no type name too", LockRules.check(c, ig, DayPass.ALL, now, Z, new LockRules.Slot(Routine.TASK, "", at(23, 11, 0))) != null
                && LockRules.check(c, ig, DayPass.ALL, now, Z, new LockRules.Slot(Routine.TASK, "Other", at(23, 11, 0))) != null
                && LockRules.check(c, ig, DayPass.ALL, now, Z, study) == null);

        // ---- with a daily window as well: the later end wins, and the reason is only given when the type is what holds
        g.during.clear();
        g.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));
        g.days = LockRules.EVERY_DAY;
        g.startMin = 9 * 60;
        g.endMin = 12 * 60;
        h = LockRules.check(c, ig, DayPass.ALL, now, Z, study);
        ok("window and type together: the later end wins", h != null && h.untilMs == at(23, 12, 0) && h.during.isEmpty());
        g.endMin = 10 * 60 + 30;
        h = LockRules.check(c, ig, DayPass.ALL, now, Z, study);
        ok("the slot outlasts the window: the slot's end and its reason", h != null && h.untilMs == at(23, 11, 0) && h.during.equals("Study"));
        g.days = 0;

        // ---- free version limits still apply
        LauncherConfig.Group second = c.addGroup("Games");
        c.setInGroup(second, wa, true);
        second.during.add(LauncherConfig.anyKey(Routine.TASK));
        ok("free: only the first lock holds, also by type", LockRules.check(c, wa, DayPass.FREE, now, Z, study) == null);
        ok("pro: the second lock holds by type", LockRules.check(c, wa, DayPass.ALL, now, Z, study) != null);
        c.groups.remove(second);

        // ---- strict
        g.strict = true;
        ok("a strict lock by type counts as strict while its slot runs", LockRules.strictUntil(c, DayPass.ALL, now, Z, study) == at(23, 11, 0)
                && LockRules.strictUntil(c, DayPass.ALL, now, Z, LockRules.Slot.NONE) == 0 && LockRules.strictUntil(c, DayPass.FREE, now, Z, study) == 0);
        ok("and the hit is strict", LockRules.check(c, ig, DayPass.ALL, now, Z, study).strict);
        g.strict = false;

        // ---- hiding
        ok("a group that does not hide hides nothing", LockRules.hiddenNow(c, DayPass.ALL, now, Z, study).isEmpty());
        g.hide = true;
        Set<String> hid = LockRules.hiddenNow(c, DayPass.ALL, now, Z, study);
        ok("a group that hides takes its locked apps out", hid.size() == 2 && hid.contains(ig) && hid.contains(yt) && !hid.contains(wa));
        ok("not locked, not hidden", LockRules.hiddenNow(c, DayPass.ALL, now, Z, LockRules.Slot.NONE).isEmpty());
        ok("free: only the apps the lock really holds are hidden", LockRules.hiddenNow(c, DayPass.FREE, now, Z, study).size() == 1
                && LockRules.hiddenNow(c, DayPass.FREE, now, Z, study).contains(ig));
        c.setInGroup(g, wa, true);
        ok("free: the third app is not held, so not hidden", LockRules.hiddenNow(c, DayPass.FREE, now, Z, study).size() == 1
                && !LockRules.hiddenNow(c, DayPass.FREE, now, Z, study).contains(wa) && LockRules.hiddenNow(c, DayPass.ALL, now, Z, study).contains(wa));
        c.setInGroup(g, wa, false);
        g.untilMs = at(23, 11, 30);
        ok("a hand-started lock hides too", LockRules.hiddenNow(c, DayPass.ALL, now, Z, LockRules.Slot.NONE).size() == 2);
        g.untilMs = 0;

        // ---- words
        ok("type names", LockRules.typeName("0:study").equals("Study") && LockRules.typeName("0:*").equals("Any task")
                && LockRules.typeName("1:other").equals("Other rest") && LockRules.typeName("3:*").equals("Any personal"));
        g.during.clear();
        g.during.add("0:study");
        ok("one type in words", LockRules.duringText(g).equals("Study"));
        g.during.add("0:revision");
        ok("two types in words", LockRules.duringText(g).equals("Study and Revision"));
        g.during.add("2:*");
        g.during.add("1:nap");
        ok("many types in words", LockRules.duringText(g).equals("Study, Revision and 2 more"));
        // in another language the names and the joining word are put into it one by one
        java.util.Map<String, String> words = new java.util.HashMap<>();
        words.put("Study", "S"); words.put("and", "&"); words.put("and 2 more", "+2");
        Tr.use(x -> words.getOrDefault(x, x));
        ok("the names and the joining word are translated", LockRules.duringText(g).equals("S, Revision +2"));
        g.during.remove("2:*");
        g.during.remove("1:nap");
        ok("two types joined with the translated word", LockRules.duringText(g).equals("S & Revision"));
        Tr.use(null);
        g.during.add("2:*");
        g.during.add("1:nap");
        g.during.clear();
        ok("none in words", LockRules.duringText(g).isEmpty());

        // ---- the line in the settings
        g.during.add("0:study");
        ok("status: locked", LockRules.status(g, now, Z, false, study).equals("Locked until 11:00 AM"));
        ok("status: waiting for the type", LockRules.status(g, now, Z, false, LockRules.Slot.NONE).equals("Locks during Study"));
        g.days = LockRules.EVERY_DAY;
        g.startMin = 21 * 60;
        g.endMin = 6 * 60;
        ok("status: a window and a type", LockRules.status(g, now, Z, false, LockRules.Slot.NONE).equals("Next: 9:00 PM  ·  and during Study"));
        g.days = 0;
        g.during.clear();
        ok("status: nothing scheduled", LockRules.status(g, now, Z, false, LockRules.Slot.NONE).equals("Not scheduled"));

        // ---- saving
        g.during.add("0:study");
        g.during.add("1:*");
        g.hide = true;
        LauncherConfig.Group two = c.addGroup("Games");
        c.setInGroup(two, wa, true);
        two.hide = false;
        LauncherConfig back = LauncherConfig.parse(c.serialize());
        ok("types and hiding survive saving", back.groups.size() == 2 && back.groups.get(0).during.equals(g.during) && back.groups.get(0).hide
                && back.groups.get(1).during.isEmpty() && !back.groups.get(1).hide);
        ok("the apps of the groups are as before", back.groups.get(0).apps.size() == 2 && back.groups.get(1).apps.contains(wa));
        String plain = new LauncherConfig().serialize();
        ok("a config with no types writes no new lines", !plain.contains("GX") && !c.serialize().replace("GX\t0\t1\t0:study\t1:*\n", "").contains("GX"));
        // text from before this version has no such lines
        String old = c.serialize();
        StringBuilder sb = new StringBuilder();
        for (String ln : old.split("\n")) if (!ln.startsWith("GX\t")) sb.append(ln).append('\n');
        LauncherConfig older = LauncherConfig.parse(sb.toString());
        ok("an older saved config loads with no types and nothing hidden", older.groups.size() == 2 && older.groups.get(0).during.isEmpty() && !older.groups.get(0).hide);
        // damaged lines are skipped
        String bad = c.serialize() + "GX\t9\t1\t0:x\nGX\tz\t1\nGX\t0\n";
        LauncherConfig dam = LauncherConfig.parse(bad);
        ok("damaged lines are skipped", dam.groups.size() == 2 && dam.groups.get(0).during.equals(g.during));
        String junk = c.serialize() + "GX\t1\t0\t7:zzz\t0:\tnotakey\t2:*\t2:*\n";
        LauncherConfig jk = LauncherConfig.parse(junk);
        ok("bad keys are dropped, repeats counted once", jk.groups.get(1).during.equals(Arrays.asList("2:*")));
        for (int i = 0; i < 40; i++) g.during.add("0:t" + i);
        // the limit is applied when reading
        LauncherConfig big = LauncherConfig.parse(c.serialize());
        ok("the number of types is limited when reading", big.groups.get(0).during.size() == LauncherConfig.MAX_DURING);

        // ---- the strong lock's loop reads the slot at every look
        c.groups.get(0).during.clear();
        c.groups.get(0).during.add("0:study");
        c.groups.get(0).hide = false;
        ok("the loop needs the slot when a group locks by type", GuardRules.anyDuring(c) && !GuardRules.anyDuring(new LauncherConfig()));
        GuardRules.Tracker t = new GuardRules.Tracker();
        GuardRules.Verdict v = GuardRules.decide(c, "com.insta", true, DayPass.ALL, Routine.TASK, 0, now, Z, t, study);
        ok("the strong lock holds the app while the type runs", v.type == GuardRules.LOCK && v.lock.during.equals("Study"));
        ok("and lets it go with no slot (a break, or between slots)", GuardRules.decide(c, "com.insta", true, DayPass.ALL, Routine.TASK, 0, now, Z, t, LockRules.Slot.NONE).type == GuardRules.NONE);
        ok("the old call knows nothing of slots", GuardRules.decide(c, "com.insta", true, DayPass.ALL, Routine.TASK, 0, now, Z, t).type == GuardRules.NONE);
        GuardLoop loop = new GuardLoop();
        Set<String> safe = GuardRules.alwaysSafe();
        final boolean[] asked = {false};
        GuardLoop.Env env = new GuardLoop.Env() {
            public int slotKind() { return Routine.TASK; }
            public long usedToday(String pkg) { return 0; }
            public LockRules.Slot lockSlot() { asked[0] = true; return study; }
        };
        GuardLoop.Out out = loop.look(c, "com.insta", "", safe, "app.dinacharya.wallpaper", DayPass.ALL, now, Z, new GuardRules.Tracker(), env);
        ok("a look of the loop covers the app when its type is running", out.present && out.verdict.type == GuardRules.LOCK && asked[0]);
        GuardLoop.Env plainEnv = new GuardLoop.Env() {
            public int slotKind() { return Routine.TASK; }
            public long usedToday(String pkg) { return 0; }
        };
        ok("an environment that does not know the slot never locks by type", !new GuardLoop().look(c, "com.insta", "", safe, "app.dinacharya.wallpaper",
                DayPass.ALL, now, Z, new GuardRules.Tracker(), plainEnv).present);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
