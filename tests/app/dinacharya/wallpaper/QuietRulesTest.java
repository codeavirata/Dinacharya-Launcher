package app.dinacharya.wallpaper;

/** How quiet the phone gets: which rule applies, and the steps that set the ringer and Do Not Disturb and put them back. */
public class QuietRulesTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static final int SIL = QuietRules.RINGER_SILENT, VIB = QuietRules.RINGER_VIBRATE, NOR = QuietRules.RINGER_NORMAL;

    static final java.time.ZoneId Z = java.time.ZoneId.of("Asia/Kolkata");
    // Wednesday 21 October 2026
    static long at(int h, int m) { return java.time.ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static LockRules.Slot study(long endMs) { return new LockRules.Slot(Routine.TASK, "Study", endMs); }

    public static void main(String[] args) {
        QuietRules q = new QuietRules();
        ok("no rules: nothing wanted", q.now(at(10, 0), Z, LockRules.Slot.NONE) == -1 && q.isEmpty());

        QuietRules.Rule work = new QuietRules.Rule("Work hours");
        work.sound = QuietRules.SILENT; work.days = LockRules.WEEKDAYS; work.startMin = 9 * 60; work.endMin = 17 * 60;
        q.rules.add(work);
        ok("by time: inside the hours", q.now(at(10, 0), Z, LockRules.Slot.NONE) == QuietRules.SILENT);
        ok("by time: outside the hours", q.now(at(18, 0), Z, LockRules.Slot.NONE) == -1);
        ok("by time: the edge is the end of the window, then the next start", q.nextEdge(at(10, 0), Z) == at(17, 0)
                && q.nextEdge(at(18, 0), Z) == at(9, 0) + 24 * 3600000L);
        QuietRules.Rule weekend = new QuietRules.Rule("Weekend");
        weekend.days = LockRules.WEEKENDS;
        ok("a day that is not chosen does not hold", !weekend.holds(at(10, 0), Z, LockRules.Slot.NONE));

        QuietRules.Rule study = new QuietRules.Rule("Study");
        study.sound = QuietRules.VIBRATE; study.days = 0; study.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));
        q.rules.clear(); q.rules.add(study);
        ok("by activity: holds while that type runs, at any hour", q.now(at(23, 0), Z, study(at(23, 30))) == QuietRules.VIBRATE);
        ok("by activity: another type does not hold", q.now(at(23, 0), Z, new LockRules.Slot(Routine.TASK, "Cooking", at(23, 30))) == -1);
        ok("by activity: no slot going on (a break), nothing holds", q.now(at(23, 0), Z, LockRules.Slot.NONE) == -1);
        ok("by activity: a slot that has ended does not hold", q.now(at(23, 0), Z, study(at(22, 0))) == -1);
        QuietRules.Rule anyRest = new QuietRules.Rule("Rest");
        anyRest.sound = QuietRules.RING; anyRest.days = 0; anyRest.during.add(LauncherConfig.anyKey(Routine.REST));
        ok("\"any\" of a kind covers its types", anyRest.holds(at(13, 0), Z, new LockRules.Slot(Routine.REST, "Nap", at(14, 0))));

        QuietRules.Rule both = new QuietRules.Rule("Study at work");
        both.sound = QuietRules.DND; both.days = LockRules.WEEKDAYS; both.startMin = 9 * 60; both.endMin = 17 * 60; both.both = true;
        both.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));
        ok("both: the type inside the hours holds", both.holds(at(10, 0), Z, study(at(11, 0))));
        ok("both: the type outside the hours does not", !both.holds(at(20, 0), Z, study(at(21, 0))));
        ok("both: the hours without the type do not", !both.holds(at(10, 0), Z, LockRules.Slot.NONE));
        both.both = false;
        ok("either: the type outside the hours holds, and so do the hours alone", both.holds(at(20, 0), Z, study(at(21, 0))) && both.holds(at(10, 0), Z, LockRules.Slot.NONE));

        QuietRules.Rule lib = new QuietRules.Rule("Library");
        lib.sound = QuietRules.VIBRATE; lib.library = true; lib.days = 0;
        LockRules.Slot inLibrary = LockRules.Slot.NONE.withLibrary(at(12, 0), false);
        ok("library: holds only while library mode is on", lib.holds(at(10, 0), Z, inLibrary) && !lib.holds(at(10, 0), Z, LockRules.Slot.NONE));

        q.rules.clear(); q.rules.add(lib); q.rules.add(work);
        ok("the quietest wins when two hold", q.now(at(10, 0), Z, inLibrary) == QuietRules.SILENT);
        work.sound = QuietRules.RING;
        ok("ring gives way to vibrate", q.now(at(10, 0), Z, inLibrary) == QuietRules.VIBRATE);
        work.sound = QuietRules.DND;
        ok("Do Not Disturb is the quietest", q.now(at(10, 0), Z, inLibrary) == QuietRules.DND);
        QuietRules.Rule never = new QuietRules.Rule("Nothing");
        never.days = 0;
        ok("a rule with no hours, types or library mode can never hold", !never.valid());

        QuietRules back = QuietRules.parse(q.serialize());
        ok("rules survive saving", back.rules.size() == 2 && back.rules.get(1).name.equals("Work hours") && back.rules.get(1).sound == QuietRules.DND
                && back.rules.get(1).days == LockRules.WEEKDAYS && back.rules.get(0).library);
        q.rules.clear(); q.rules.add(both);
        QuietRules b2 = QuietRules.parse(q.serialize());
        ok("types and the either/both choice survive saving", b2.rules.size() == 1 && b2.rules.get(0).during.equals(both.during) && !b2.rules.get(0).both);
        QuietRules bad = QuietRules.parse("DINAQUIET2\nrule=a|9|127|-5|99999|0|0|\nrule=short\nrule=b|x|1|1|1|0|0|\nrule=Ok|2|0|540|1020|0|0|0:study,9:*\n");
        ok("damaged lines are skipped, numbers kept in range", bad.rules.size() == 2 && bad.rules.get(0).sound == QuietRules.DND && bad.rules.get(0).startMin == 0
                && bad.rules.get(0).endMin == 1439 && bad.rules.get(1).name.equals("Ok") && bad.rules.get(1).during.size() == 1);
        ok("old or missing text gives no rules", QuietRules.parse(null).isEmpty() && QuietRules.parse("DINAQUIET1\nlibrary=1,0\n").isEmpty());
        ok("names are cleaned", QuietRules.clean("  a|b\tc  ").equals("a b c") && QuietRules.clean("").equals("Quiet"));

        // ---- the ringer
        QuietRules.RingerStep r1 = QuietRules.ringerStep(QuietRules.VIBRATE, NOR, -1, -1);
        ok("ringer loud, vibrate wanted: go to vibrate, remember loud", r1.set == VIB && r1.keep == NOR && r1.ours == VIB);
        QuietRules.RingerStep r2 = QuietRules.ringerStep(QuietRules.VIBRATE, SIL, -1, -1);
        ok("ringer already quieter: leave it, remember nothing", r2.set == -1 && r2.keep == -1);
        QuietRules.RingerStep r3 = QuietRules.ringerStep(QuietRules.SILENT, VIB, -1, -1);
        ok("on vibrate, silent wanted: go silent", r3.set == SIL && r3.keep == VIB && r3.ours == SIL);
        QuietRules.RingerStep r4 = QuietRules.ringerStep(QuietRules.VIBRATE, VIB, NOR, VIB);
        ok("already done: nothing more", r4.set == -1 && r4.keep == NOR && r4.ours == VIB);
        QuietRules.RingerStep r5 = QuietRules.ringerStep(QuietRules.VIBRATE, NOR, NOR, VIB);
        ok("the person turned the sound back on: not forced again", r5.set == -1 && r5.keep == NOR);
        QuietRules.RingerStep r6 = QuietRules.ringerStep(QuietRules.SILENT, VIB, NOR, VIB);
        ok("one rule gives way to a quieter one: moves on, keeps the first memory", r6.set == SIL && r6.keep == NOR && r6.ours == SIL);
        QuietRules.RingerStep r7 = QuietRules.ringerStep(-1, VIB, NOR, VIB);
        ok("nothing wants quiet any more: the old mode comes back", r7.set == NOR && r7.keep == -1 && r7.ours == -1);
        QuietRules.RingerStep r8 = QuietRules.ringerStep(-1, SIL, NOR, VIB);
        ok("the person changed it meanwhile: their choice stands", r8.set == -1 && r8.keep == -1);
        QuietRules.RingerStep r9 = QuietRules.ringerStep(-1, VIB, -1, -1);
        ok("a phone on vibrate by choice is never changed", r9.set == -1 && r9.keep == -1);
        QuietRules.RingerStep r11 = QuietRules.ringerStep(QuietRules.RING, VIB, -1, -1);
        ok("on vibrate, ring wanted: turned up, old mode remembered", r11.set == NOR && r11.keep == VIB && r11.ours == NOR);
        QuietRules.RingerStep r12 = QuietRules.ringerStep(QuietRules.RING, NOR, -1, -1);
        ok("already ringing: nothing to do", r12.set == -1 && r12.keep == -1);
        QuietRules.RingerStep r13 = QuietRules.ringerStep(QuietRules.DND, NOR, -1, -1);
        ok("Do Not Disturb leaves the ringer alone", r13.set == -1 && r13.keep == -1);
        QuietRules.RingerStep r14 = QuietRules.ringerStep(QuietRules.DND, VIB, NOR, VIB);
        ok("a vibrate rule gives way to Do Not Disturb: the ringer goes back", r14.set == NOR && r14.keep == -1);
        QuietRules.RingerStep r15 = QuietRules.ringerStep(-1, NOR, VIB, NOR);
        ok("ring rule over: the mode before it comes back", r15.set == VIB && r15.keep == -1);
        QuietRules.RingerStep r10 = QuietRules.ringerStep(-1, NOR, NOR, VIB);
        ok("the person already restored it: memory dropped", r10.set == -1 && r10.keep == -1);

        // ---- Do Not Disturb
        QuietRules.HoldStep h1 = QuietRules.holdStep(true, QuietRules.FILTER_ALL, 0);
        ok("hold wanted, Do Not Disturb off: priority only, remember all", h1.set == QuietRules.FILTER_PRIORITY && h1.keep == QuietRules.FILTER_ALL);
        QuietRules.HoldStep h2 = QuietRules.holdStep(true, 3, 0);
        ok("Do Not Disturb already on by the person: never touched", h2.set == 0 && h2.keep == 0);
        QuietRules.HoldStep h3 = QuietRules.holdStep(true, QuietRules.FILTER_PRIORITY, QuietRules.FILTER_ALL);
        ok("already held: nothing more", h3.set == 0 && h3.keep == QuietRules.FILTER_ALL);
        QuietRules.HoldStep h4 = QuietRules.holdStep(false, QuietRules.FILTER_PRIORITY, QuietRules.FILTER_ALL);
        ok("rule over: notifications come back", h4.set == QuietRules.FILTER_ALL && h4.keep == 0);
        QuietRules.HoldStep h5 = QuietRules.holdStep(false, 3, QuietRules.FILTER_ALL);
        ok("the person changed it meanwhile: their choice stands", h5.set == 0 && h5.keep == 0);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        if (fails != 0) System.exit(1);
    }
}
