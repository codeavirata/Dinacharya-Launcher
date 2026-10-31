package app.dinacharya.wallpaper;

/** How quiet the phone gets: which rule applies, and the steps that set the ringer and Do Not Disturb and put them back. */
public class QuietRulesTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static final int SIL = QuietRules.RINGER_SILENT, VIB = QuietRules.RINGER_VIBRATE, NOR = QuietRules.RINGER_NORMAL;

    public static void main(String[] args) {
        QuietRules.Rule vib = new QuietRules.Rule(QuietRules.VIBRATE, false);
        QuietRules.Rule silHold = new QuietRules.Rule(QuietRules.SILENT, true);
        QuietRules q = new QuietRules();
        ok("nothing set: nothing to do", q.isEmpty() && q.now(true, 0, "Study").isNone());
        q.library = vib;
        ok("library rule applies only while library mode is on", q.now(true, -1, "").sound == QuietRules.VIBRATE && q.now(false, -1, "").isNone());
        q.setType(LauncherConfig.typeKey(Routine.TASK, "Study"), silHold);
        ok("a type rule applies to its own type", q.now(false, Routine.TASK, "Study").sound == QuietRules.SILENT && q.now(false, Routine.TASK, "study ").hold);
        ok("another type of the same kind is not touched", q.now(false, Routine.TASK, "Cooking").isNone());
        ok("the quietest wins when library mode and a type apply together", q.now(true, Routine.TASK, "Study").sound == QuietRules.SILENT && q.now(true, Routine.TASK, "Cooking").sound == QuietRules.VIBRATE);
        q.setType(LauncherConfig.anyKey(Routine.REST), vib);
        ok("\"any\" of a kind covers its types", q.now(false, Routine.REST, "Nap").sound == QuietRules.VIBRATE);
        q.setType(LauncherConfig.typeKey(Routine.TASK, "Study"), QuietRules.Rule.NONE);
        ok("a rule that does nothing is removed", !q.types.containsKey(LauncherConfig.typeKey(Routine.TASK, "Study")));
        ok("silent and holding need access, vibrate does not", silHold.needsAccess() && !vib.needsAccess() && new QuietRules.Rule(0, true).needsAccess());
        ok("rule words", vib.text().equals("Vibrate") && silHold.text().equals("Silent, notifications held") && QuietRules.Rule.NONE.text().equals("Off")
                && new QuietRules.Rule(0, true).text().equals("Notifications held"));

        QuietRules back = QuietRules.parse(q.serialize());
        ok("rules survive saving", back.library.sound == QuietRules.VIBRATE && back.types.size() == 1
                && back.types.get(LauncherConfig.anyKey(Routine.REST)).sound == QuietRules.VIBRATE);
        QuietRules bad = QuietRules.parse("DINAQUIET1\nlibrary=x\ntype=9:*|1,0\ntype=0:study|9,1\nrubbish\n");
        ok("damaged text is ignored; levels are kept in range", bad.library.isNone() && bad.types.size() == 1 && bad.types.get("0:study").sound == QuietRules.SILENT);
        ok("no text gives the defaults", QuietRules.parse(null).isEmpty() && QuietRules.parse("nonsense").isEmpty());

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
        QuietRules.RingerStep r7 = QuietRules.ringerStep(QuietRules.LEAVE, VIB, NOR, VIB);
        ok("nothing wants quiet any more: the old mode comes back", r7.set == NOR && r7.keep == -1 && r7.ours == -1);
        QuietRules.RingerStep r8 = QuietRules.ringerStep(QuietRules.LEAVE, SIL, NOR, VIB);
        ok("the person changed it meanwhile: their choice stands", r8.set == -1 && r8.keep == -1);
        QuietRules.RingerStep r9 = QuietRules.ringerStep(QuietRules.LEAVE, VIB, -1, -1);
        ok("a phone on vibrate by choice is never changed", r9.set == -1 && r9.keep == -1);
        QuietRules.RingerStep r10 = QuietRules.ringerStep(QuietRules.LEAVE, NOR, NOR, VIB);
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
