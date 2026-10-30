package app.dinacharya.wallpaper;

/** What goes back to the free version when a one-day video pass is over (see PassReset). One item per pass. */
public class PassResetTest {
    static int fails = 0;

    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static String k(String pkg) { return AppKey.make(pkg, pkg + ".Main", 0); }

    static LauncherConfig.Group group(LauncherConfig c, String name, boolean strict, String... pkgs) {
        LauncherConfig.Group g = c.addGroup(name);
        for (String p : pkgs) c.setInGroup(g, k(p), true);
        g.strict = strict;
        return g;
    }

    static String names(Routine r) {
        StringBuilder sb = new StringBuilder();
        for (Routine o : r.others) sb.append(o.name).append('|');
        return sb.toString();
    }

    /** A config with rule "a": strict, 30 minutes a day, the sum kind, plus rules c and d (over the free number) and a lock. */
    static LauncherConfig busy() {
        LauncherConfig c = new LauncherConfig();
        c.setRule(k("a"), LauncherConfig.RULE_NUDGE, 0, true);
        c.setRule(k("b"), LauncherConfig.RULE_NUDGE, 0, false);
        return c;
    }

    static void everythingOn(LauncherConfig c) {
        c.setRule(k("a"), LauncherConfig.RULE_STRICT, 30, true, PauseChallenge.MATH);
        c.setRule(k("c"), LauncherConfig.RULE_STRICT, 20, false, PauseChallenge.TYPE);
        c.setRule(k("d"), LauncherConfig.RULE_NUDGE, 0, false);
    }

    public static void main(String[] args) {
        // ---------- the note
        ok("items that keep something are known", PassReset.needsReset(DayPass.PAUSE_APPS) && PassReset.needsReset(DayPass.PAUSE_STRICT)
                && PassReset.needsReset(DayPass.PAUSE_LIMIT) && PassReset.needsReset(DayPass.PAUSE_PLAYFUL) && PassReset.needsReset(DayPass.LOCK_MORE)
                && PassReset.needsReset(DayPass.LOCK_APPS) && PassReset.needsReset(DayPass.LOCK_STRICT) && PassReset.needsReset(DayPass.ROUTINES)
                && PassReset.needsReset(DayPass.GOALS) && PassReset.needsReset(DayPass.LOOK_SUNSET) && PassReset.needsReset(DayPass.LOOK_OCEAN)
                && PassReset.needsReset(DayPass.LOOK_GRAPHITE));
        ok("the reports keep nothing, so they have no note", !PassReset.needsReset(DayPass.REPORT_DAYS) && !PassReset.needsReset(DayPass.REPORT_WEEK)
                && !PassReset.needsReset(DayPass.NONE) && !PassReset.needsReset(99)
                && PassReset.capture(DayPass.REPORT_DAYS, new LauncherConfig(), Routine.sample()).isEmpty()
                && PassReset.capture(DayPass.REPORT_WEEK, new LauncherConfig(), Routine.sample()).isEmpty());
        ok("pauses and locks live in the home settings; routines, goals and looks in the routine",
                PassReset.touchesLauncher(DayPass.PAUSE_LIMIT) && PassReset.touchesLauncher(DayPass.LOCK_STRICT) && !PassReset.touchesLauncher(DayPass.GOALS)
                        && PassReset.touchesRoutine(DayPass.ROUTINES) && PassReset.touchesRoutine(DayPass.LOOK_OCEAN) && !PassReset.touchesRoutine(DayPass.PAUSE_APPS));
        ok("a damaged or empty note names no item and changes nothing",
                PassReset.feature("") == DayPass.NONE && PassReset.feature(null) == DayPass.NONE && PassReset.feature("junk") == DayPass.NONE
                        && PassReset.feature("DINAPASS2\nF\tx\n") == DayPass.NONE && PassReset.feature("DINAPASS2\nF\t1\n") == DayPass.NONE
                        && PassReset.feature("DINAPASS1\nF\t3\n") == DayPass.NONE && PassReset.feature("DINAPASS2\nF\t99\n") == DayPass.NONE);
        LauncherConfig j = new LauncherConfig();
        j.setRule(k("a"), LauncherConfig.RULE_STRICT, 30, true, PauseChallenge.MATH);
        ok("junk does nothing to the settings", PassReset.undoLauncher("junk", j) == 0 && PassReset.undoRoutine("junk", Routine.sample()) == 0
                && j.ruleFor(k("a")).mode == LauncherConfig.RULE_STRICT);
        for (int f = DayPass.FIRST; f <= DayPass.LAST; f++) {
            if (!PassReset.needsReset(f)) continue;
            LauncherConfig c = new LauncherConfig();
            c.setRule(k("a"), LauncherConfig.RULE_NUDGE, 0, false);
            group(c, "Night", false, "x", "y");
            Routine r = Routine.sample();
            r.setGoal(Routine.TASK, "", 60);
            String note = PassReset.capture(f, c, r);
            ok("item " + f + " (" + DayPass.shortName(f) + "): nothing made means nothing taken away", PassReset.feature(note) == f
                    && PassReset.undoLauncher(note, c) == 0 && PassReset.undoRoutine(note, r) == 0);
        }

        // ---------- pauses: each item puts back only its own part
        {
            LauncherConfig c = new LauncherConfig();
            String note = PassReset.capture(DayPass.PAUSE_APPS, c, Routine.sample());
            c.setRule(k("a"), LauncherConfig.RULE_NUDGE, 0, false);
            c.setRule(k("b"), LauncherConfig.RULE_NUDGE, 0, false);
            c.setRule(k("c"), LauncherConfig.RULE_NUDGE, 0, false);
            c.setRule(k("d"), LauncherConfig.RULE_NUDGE, 0, false);
            int n = PassReset.undoLauncher(note, c);
            ok("more paused apps: the first one stays, the rest go", n == 3 && c.rules.size() == 1 && c.ruleFor(k("a")) != null && c.ruleFor(k("b")) == null
                    && c.ruleFor(k("c")) == null && c.ruleFor(k("d")) == null);
            ok("more paused apps: doing it again changes nothing", PassReset.undoLauncher(note, c) == 0 && c.rules.size() == 1);
        }
        {
            LauncherConfig c = busy();
            String note = PassReset.capture(DayPass.PAUSE_APPS, c, Routine.sample());
            everythingOn(c);
            PassReset.undoLauncher(note, c);
            LauncherConfig.Rule a = c.ruleFor(k("a"));
            ok("more paused apps: only the extra apps go, strict, the limit and the kind of the first two are left as they were",
                    c.rules.size() == 2 && a.mode == LauncherConfig.RULE_STRICT && a.limitMin == 30 && a.challenge == PauseChallenge.MATH);
        }
        {
            LauncherConfig c = busy();
            String note = PassReset.capture(DayPass.PAUSE_STRICT, c, Routine.sample());
            everythingOn(c);
            int n = PassReset.undoLauncher(note, c);
            LauncherConfig.Rule a = c.ruleFor(k("a")), cc = c.ruleFor(k("c"));
            ok("strict pauses: the strict pause goes back to the short pause", a.mode == LauncherConfig.RULE_NUDGE && cc.mode == LauncherConfig.RULE_NUDGE && n == 2);
            ok("strict pauses: the limit, the kind and the extra apps are not touched", a.limitMin == 30 && a.challenge == PauseChallenge.MATH
                    && cc.limitMin == 20 && cc.challenge == PauseChallenge.TYPE && c.rules.size() == 4 && a.always);
            ok("strict pauses: doing it again changes nothing", PassReset.undoLauncher(note, c) == 0);
        }
        {
            LauncherConfig c = busy();
            c.setRule(k("b"), LauncherConfig.RULE_STRICT, 0, false);   // strict from before (say a refunded Pro)
            String note = PassReset.capture(DayPass.PAUSE_STRICT, c, Routine.sample());
            c.setRule(k("a"), LauncherConfig.RULE_STRICT, 0, true);
            PassReset.undoLauncher(note, c);
            ok("strict pauses: a pause that was strict before keeps its strictness", c.ruleFor(k("b")).mode == LauncherConfig.RULE_STRICT
                    && c.ruleFor(k("a")).mode == LauncherConfig.RULE_NUDGE);
        }
        {
            LauncherConfig c = busy();
            c.setRule(k("b"), LauncherConfig.RULE_NUDGE, 45, false);   // a limit from before
            String note = PassReset.capture(DayPass.PAUSE_LIMIT, c, Routine.sample());
            everythingOn(c);
            c.setRule(k("b"), LauncherConfig.RULE_NUDGE, 90, false);
            c.setRule(k("l"), LauncherConfig.RULE_NONE, 40, false);   // a limit and nothing else
            PassReset.undoLauncher(note, c);
            LauncherConfig.Rule a = c.ruleFor(k("a")), b = c.ruleFor(k("b"));
            ok("daily limits: a limit set during the pass goes back to none, an older limit goes back to what it was",
                    a.limitMin == 0 && b.limitMin == 45 && c.ruleFor(k("c")).limitMin == 0);
            ok("daily limits: a rule that was only a limit is removed", c.ruleFor(k("l")) == null);
            ok("daily limits: strict, the kind and the apps are not touched", a.mode == LauncherConfig.RULE_STRICT && a.challenge == PauseChallenge.MATH
                    && c.ruleFor(k("c")) != null && c.ruleFor(k("c")).mode == LauncherConfig.RULE_STRICT);
        }
        {
            LauncherConfig c = busy();
            c.setRule(k("b"), LauncherConfig.RULE_NUDGE, 0, false, PauseChallenge.BREATHE);   // an older kind
            String note = PassReset.capture(DayPass.PAUSE_PLAYFUL, c, Routine.sample());
            everythingOn(c);
            c.setRule(k("b"), LauncherConfig.RULE_NUDGE, 0, false, PauseChallenge.WHY);
            int n = PassReset.undoLauncher(note, c);
            LauncherConfig.Rule a = c.ruleFor(k("a")), b = c.ruleFor(k("b"));
            ok("playful pauses: the kind goes back to the plain wait, an older kind goes back to what it was",
                    a.challenge == PauseChallenge.WAIT && b.challenge == PauseChallenge.BREATHE && c.ruleFor(k("c")).challenge == PauseChallenge.WAIT && n == 3);
            ok("playful pauses: strict, the limit and the apps are not touched", a.mode == LauncherConfig.RULE_STRICT && a.limitMin == 30
                    && c.ruleFor(k("c")).limitMin == 20 && c.rules.size() == 4);
        }
        {
            // a lock note must not touch the pauses; a pause note must not touch the locks
            LauncherConfig c = new LauncherConfig();
            String note = PassReset.capture(DayPass.LOCK_MORE, c, Routine.sample());
            for (char ch = 'a'; ch <= 'e'; ch++) c.setRule(k(String.valueOf(ch)), LauncherConfig.RULE_STRICT, 10, false);
            ok("a lock pass leaves the pauses alone", PassReset.undoLauncher(note, c) == 0 && c.rules.size() == 5);
            LauncherConfig c2 = new LauncherConfig();
            String note2 = PassReset.capture(DayPass.PAUSE_STRICT, c2, Routine.sample());
            group(c2, "Study", true, "a", "b", "c");
            group(c2, "Night", true, "d");
            ok("a pause pass leaves the locks alone", PassReset.undoLauncher(note2, c2) == 0 && c2.groups.size() == 2 && c2.groups.get(0).strict);
            Routine r = Routine.sample();
            r.addOther("Weekend", r);
            ok("a pause or lock pass leaves the routines alone", PassReset.undoRoutine(note2, r) == 0 && r.others.size() == 1);
        }

        // ---------- locks: each item puts back only its own part
        {
            LauncherConfig c = new LauncherConfig();
            String note = PassReset.capture(DayPass.LOCK_MORE, c, Routine.sample());
            LauncherConfig.Group g1 = group(c, "Study", true, "a", "b", "c", "d");
            g1.days = 0x1F; g1.startMin = 9 * 60; g1.endMin = 17 * 60;
            group(c, "Night", false, "e", "f", "g");
            group(c, "Weekend", true, "h");
            int n = PassReset.undoLauncher(note, c);
            ok("more locks: the first one stays, the others go", n == 2 && c.groups.size() == 1 && c.groups.get(0).name.equals("Study"));
            LauncherConfig.Group s = c.groups.get(0);
            ok("more locks: the one kept is left as it is (apps, strict, days and times)", s.apps.size() == 4 && s.strict && s.days == 0x1F
                    && s.startMin == 9 * 60 && s.endMin == 17 * 60);
            ok("more locks: doing it again changes nothing", PassReset.undoLauncher(note, c) == 0);
        }
        {
            LauncherConfig c = new LauncherConfig();
            group(c, "Night", false, "a", "b");
            group(c, "Old", true, "p", "q", "r", "s");   // from before (say a refunded Pro)
            String note = PassReset.capture(DayPass.LOCK_APPS, c, Routine.sample());
            LauncherConfig.Group night = c.groups.get(0), old = c.groups.get(1);
            c.setInGroup(night, k("c"), true);
            c.setInGroup(night, k("d"), true);
            night.strict = true;
            c.setInGroup(old, k("t"), true);
            group(c, "Extra", true, "z", "y", "x");
            int n = PassReset.undoLauncher(note, c);
            ok("more apps in a lock: a two-app lock goes back to its two apps, an older four-app lock to its four", night.apps.size() == 2
                    && night.apps.contains(k("a")) && night.apps.contains(k("b")) && old.apps.size() == 4 && !old.apps.contains(k("t")));
            ok("more apps in a lock: a lock made during the pass holds one app again", c.groups.get(2).apps.size() == 1 && n == 3);
            ok("more apps in a lock: strict and the number of locks are not touched", night.strict && old.strict && c.groups.size() == 3 && c.groups.get(2).strict);
        }
        {
            LauncherConfig c = new LauncherConfig();
            group(c, "Night", false, "a", "b");
            group(c, "Old", true, "p", "q");   // strict from before
            String note = PassReset.capture(DayPass.LOCK_STRICT, c, Routine.sample());
            LauncherConfig.Group night = c.groups.get(0), old = c.groups.get(1);
            night.strict = true;
            c.setInGroup(night, k("c"), true);
            LauncherConfig.Group extra = group(c, "Extra", true, "z");
            int n = PassReset.undoLauncher(note, c);
            ok("strict locks: a lock made strict goes back to not strict, an older strict lock stays strict", !night.strict && old.strict && !extra.strict && n == 2);
            ok("strict locks: the apps and the number of locks are not touched", night.apps.size() == 3 && c.groups.size() == 3);
            ok("strict locks: doing it again changes nothing", PassReset.undoLauncher(note, c) == 0);
        }
        {
            // a timed lock that is free is kept as it is
            LauncherConfig c = new LauncherConfig();
            String note = PassReset.capture(DayPass.LOCK_MORE, c, Routine.sample());
            LauncherConfig.Group g = group(c, "Now", false, "a");
            g.untilMs = 123456789L;
            PassReset.undoLauncher(note, c);
            ok("a plain timed lock with one app is kept", c.groups.size() == 1 && c.groups.get(0).untilMs == 123456789L);
        }

        // ---------- routines
        {
            Routine r = Routine.sample();
            int blocks = r.blocks.size();
            String main = r.name;
            String note = PassReset.capture(DayPass.ROUTINES, new LauncherConfig(), r);
            r.addOther("Weekend", r);
            r.addOther("Exam day", r);
            r.dayUse[5] = 1;
            r.dayUse[6] = 1;
            r.dayUse[2] = 2;
            int n = PassReset.undoRoutine(note, r);
            boolean noDays = true;
            for (int d : r.dayUse) if (d != 0) noDays = false;
            ok("routines: the routines made with the pass go and every day uses the main one again", n == 2 && r.others.isEmpty() && noDays && !r.hasWeek());
            ok("routines: the main routine is untouched", r.blocks.size() == blocks && r.name.equals(main));
            ok("routines: doing it again changes nothing", PassReset.undoRoutine(note, r) == 0);
        }
        {
            Routine r = Routine.sample();
            r.addOther("Weekend", r);
            r.dayUse[5] = 1;
            String note = PassReset.capture(DayPass.ROUTINES, new LauncherConfig(), r);
            r.addOther("Exam day", r);
            r.dayUse[2] = 2;
            r.addOther("Weekend", r);   // a second one with the same name
            r.dayUse[6] = 3;
            int n = PassReset.undoRoutine(note, r);
            ok("routines: one that was there before stays, with its day", n == 2 && names(r).equals("Weekend|") && r.dayUse[5] == 1);
            ok("routines: days that used the removed ones use the main routine", r.dayUse[2] == 0 && r.dayUse[6] == 0);
        }
        {
            // the numbers of the routines left stay right when one in the middle goes
            Routine r = Routine.sample();
            String note = PassReset.capture(DayPass.ROUTINES, new LauncherConfig(), r);
            r.addOther("A", r);
            r.addOther("B", r);
            r.addOther("C", r);
            r.dayUse[0] = 3;
            PassReset.undoRoutine(note, r);
            ok("routines: all of them went, no day points at a routine that is gone", r.others.isEmpty() && r.dayUse[0] == 0);
        }

        // ---------- goals
        {
            Routine r = Routine.sample();
            r.setGoal(Routine.TASK, "", 120);
            r.setGoal(Routine.REST, "Walk", 30);
            String note = PassReset.capture(DayPass.GOALS, new LauncherConfig(), r);
            r.setGoal(Routine.MEAL, "", 45);
            r.setGoal(Routine.PERSONAL, "Reading", 60);
            r.setGoal(Routine.TASK, "", 150);   // a changed target of an earlier goal
            r.setGoal(Routine.REST, "WALK", 40);   // the same goal, written another way
            int n = PassReset.undoRoutine(note, r);
            ok("goals: the goals made with the pass go", n == 2 && r.goals.size() == 2 && r.findGoal(Routine.MEAL, "") == null
                    && r.findGoal(Routine.PERSONAL, "Reading") == null);
            ok("goals: an earlier goal stays, even with a changed target", r.findGoal(Routine.TASK, "") != null && r.findGoal(Routine.REST, "walk") != null);
        }

        // ---------- goals of a slot, with their to-dos
        {
            Routine r = Routine.sample();
            Routine.Goal before = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 60);
            before.addTodo("Read chapter 4");
            r.putGoal(before);
            r.setGoal(Routine.TASK, "Study", 120);   // an older goal for a whole type
            String note = PassReset.capture(DayPass.GOALS, new LauncherConfig(), r);
            Routine.Goal during = Routine.Goal.forSlot(Routine.TASK, "Project work", 45);
            during.addTodo("Fix the bug");
            r.putGoal(during);
            Routine.Goal once = Routine.Goal.onceForSlot(Routine.TASK, "Revision", 30, java.time.LocalDate.of(2026, 10, 26));
            r.putGoal(once);
            Routine.Goal changed = Routine.Goal.forSlot(Routine.TASK, "Reading & notes", 90);   // a changed target of a goal from before
            changed.addTodo("Read chapter 4");
            r.putGoal(changed);
            int n = PassReset.undoRoutine(note, r);
            ok("slot goals: the goals made with the pass go, with their to-dos", n == 2 && r.findSlotGoal(Routine.TASK, "Project work") == null
                    && r.findSlotOnce(Routine.TASK, "Revision", java.time.LocalDate.of(2026, 10, 26)) == null);
            ok("slot goals: a goal from before stays, with its to-dos", r.findSlotGoal(Routine.TASK, "Reading & notes") != null
                    && r.findSlotGoal(Routine.TASK, "reading & NOTES").todos.size() == 1 && r.findGoal(Routine.TASK, "Study") != null);
            ok("slot goals: doing it again changes nothing", PassReset.undoRoutine(note, r) == 0 && r.goals.size() == 2);
            ok("slot goals: a slot goal is not mistaken for a goal of the same name for a type", PassReset.undoRoutine(
                    PassReset.capture(DayPass.GOALS, new LauncherConfig(), r), r) == 0);
            Routine r2 = Routine.sample();
            String empty = PassReset.capture(DayPass.GOALS, new LauncherConfig(), r2);
            r2.putGoal(Routine.Goal.forSlot(Routine.TASK, "Study", 30));
            r2.setGoal(Routine.TASK, "Study", 30);
            ok("slot goals: with nothing before, both the slot goal and the type goal go", PassReset.undoRoutine(empty, r2) == 2 && r2.goals.isEmpty());
            Routine r3 = Routine.sample();
            r3.putGoal(Routine.Goal.forSlot(Routine.TASK, "Study", 30));
            String slotNote = PassReset.capture(DayPass.GOALS, new LauncherConfig(), r3);
            r3.setGoal(Routine.TASK, "Study", 30);
            ok("slot goals: an older type goal of the same words made during the pass goes, the slot goal stays",
                    PassReset.undoRoutine(slotNote, r3) == 1 && r3.goals.size() == 1 && r3.goals.get(0).bound());
            ok("slot goals: a note from before (no slot) still reads", PassReset.undoRoutine("DINAPASS3\nM\t" + DayPass.bit(DayPass.GOALS) + "\nL\t0\tStudy\tR\n",
                    Routine.sample()) == 0);
        }

        // ---------- colour looks
        {
            Routine r = Routine.sample();
            r.palette = Routine.PAL_TEAL;
            String note = PassReset.capture(DayPass.LOOK_OCEAN, new LauncherConfig(), r);
            r.palette = Routine.PAL_OCEAN;
            ok("looks: a Pro look goes back to the one before", PassReset.undoRoutine(note, r) == 1 && r.palette == Routine.PAL_TEAL);
            r.palette = Routine.PAL_SUNSET;   // a look this pass did not open: left alone
            ok("looks: another look is not taken away by the Ocean pass ending", PassReset.undoRoutine(note, r) == 0 && r.palette == Routine.PAL_SUNSET);
            r.palette = Routine.PAL_TEAL;
            ok("looks: picking the free look again leaves nothing to undo", PassReset.undoRoutine(note, r) == 0 && r.palette == Routine.PAL_TEAL);
        }
        {
            Routine r = Routine.sample();
            r.palette = Routine.PAL_SUNSET;   // from before
            String note = PassReset.capture(DayPass.LOOK_GRAPHITE, new LauncherConfig(), r);
            r.palette = Routine.PAL_GRAPHITE;
            ok("looks: goes back to the earlier look, not to Teal", PassReset.undoRoutine(note, r) == 1 && r.palette == Routine.PAL_SUNSET);
            r.palette = Routine.PAL_TEAL;
            ok("looks: the free look is never taken away", PassReset.undoRoutine(note, r) == 0 && r.palette == Routine.PAL_TEAL);
        }

        // ---------- a pass for a whole group (three videos): every item of the group is put back, nothing outside it
        final int pausesAll = DayPass.areaMask(DayPass.AREA_PAUSES), locksAll = DayPass.areaMask(DayPass.AREA_LOCKS), looksAll = DayPass.areaMask(DayPass.AREA_LOOKS);
        {
            LauncherConfig c = busy();
            String note = PassReset.captureMask(pausesAll, c, Routine.sample());
            ok("group of pauses: the note names every pause item", PassReset.maskOf(note) == pausesAll && PassReset.feature(note) == DayPass.PAUSE_APPS);
            everythingOn(c);
            group(c, "Study", true, "m", "n", "o");
            int n = PassReset.undoLauncher(note, c);
            LauncherConfig.Rule a = c.ruleFor(k("a"));
            ok("group of pauses: extra apps go, and strict, the limit and the kind go back on the apps that stay",
                    c.rules.size() == 2 && c.ruleFor(k("c")) == null && c.ruleFor(k("d")) == null && a.mode == LauncherConfig.RULE_NUDGE
                            && a.limitMin == 0 && a.challenge == PauseChallenge.WAIT && n == 5);
            ok("group of pauses: what was there before stays, and the locks are not touched", a.always && c.ruleFor(k("b")) != null
                    && c.groups.size() == 1 && c.groups.get(0).strict && c.groups.get(0).apps.size() == 3);
            ok("group of pauses: doing it again changes nothing", PassReset.undoLauncher(note, c) == 0);
            ok("group of pauses: the routine is not touched", PassReset.undoRoutine(note, Routine.sample()) == 0);
        }
        {
            LauncherConfig c = new LauncherConfig();
            group(c, "Night", false, "a", "b");
            group(c, "Old", true, "p", "q", "r", "s");
            String note = PassReset.captureMask(locksAll, c, Routine.sample());
            LauncherConfig.Group night = c.groups.get(0), old = c.groups.get(1);
            c.setInGroup(night, k("c"), true);
            night.strict = true;
            c.setInGroup(old, k("t"), true);
            group(c, "Extra", true, "z", "y", "x");
            int n = PassReset.undoLauncher(note, c);
            ok("group of locks: the extra lock goes, the long lock shrinks to its old size, strict goes back",
                    c.groups.size() == 2 && night.apps.size() == 2 && !night.strict && old.apps.size() == 4 && old.strict && n == 4);
            ok("group of locks: doing it again changes nothing", PassReset.undoLauncher(note, c) == 0);
            LauncherConfig other = busy();
            ok("group of locks: the pauses are not touched", PassReset.undoLauncher(note, other) == 0 && other.rules.size() == 2);
        }
        {
            Routine r = Routine.sample();
            r.palette = Routine.PAL_SUNSET;
            String note = PassReset.captureMask(looksAll, new LauncherConfig(), r);
            r.palette = Routine.PAL_OCEAN;
            ok("group of looks: the look used during the pass goes back to the one before", PassReset.undoRoutine(note, r) == 1 && r.palette == Routine.PAL_SUNSET);
            r.palette = Routine.PAL_TEAL;
            ok("group of looks: the free look is left alone", PassReset.undoRoutine(note, r) == 0 && r.palette == Routine.PAL_TEAL);
            ok("group of looks: nothing in the home settings", PassReset.undoLauncher(note, busy()) == 0);
        }
        ok("group of reports: nothing to put back, so no note", PassReset.captureMask(DayPass.areaMask(DayPass.AREA_REPORTS), new LauncherConfig(), Routine.sample()).isEmpty()
                && !PassReset.needsResetMask(DayPass.areaMask(DayPass.AREA_REPORTS)) && PassReset.needsResetMask(pausesAll));
        {
            String mixed = PassReset.captureMask(DayPass.bit(DayPass.REPORT_DAYS) | DayPass.bit(DayPass.PAUSE_STRICT), busy(), Routine.sample());
            ok("a mix of items keeps a note only for the ones that need it", PassReset.maskOf(mixed) == DayPass.bit(DayPass.PAUSE_STRICT));
        }
        ok("which settings a group touches", PassReset.touchesLauncherMask(pausesAll) && PassReset.touchesLauncherMask(locksAll) && !PassReset.touchesRoutineMask(pausesAll)
                && PassReset.touchesRoutineMask(looksAll) && !PassReset.touchesLauncherMask(looksAll) && !PassReset.touchesLauncherMask(0));
        {
            // a pass that was running when the app was updated left a note in the old one-item form
            LauncherConfig c = busy();
            String old = "DINAPASS2\nF\t" + DayPass.PAUSE_STRICT + "\nR\t" + k("a") + "\t" + LauncherConfig.RULE_NUDGE + "\t0\t1\t0\nR\t" + k("b") + "\t" + LauncherConfig.RULE_NUDGE + "\t0\t0\t0\n";
            ok("an old one-item note is still understood", PassReset.maskOf(old) == DayPass.bit(DayPass.PAUSE_STRICT) && PassReset.feature(old) == DayPass.PAUSE_STRICT);
            c.setRule(k("a"), LauncherConfig.RULE_STRICT, 0, true);
            ok("…and still puts things back", PassReset.undoLauncher(old, c) == 1 && c.ruleFor(k("a")).mode == LauncherConfig.RULE_NUDGE);
        }
        ok("damaged group notes do nothing", PassReset.maskOf("DINAPASS3\nM\tx\n") == 0 && PassReset.maskOf("DINAPASS3\nM\t0\n") == 0
                && PassReset.maskOf("DINAPASS3\nM\t1\n") == 0 && PassReset.maskOf("DINAPASS3\nM\t" + DayPass.bit(DayPass.REPORT_DAYS) + "\n") == 0
                && PassReset.maskOf("DINAPASS3\n") == 0 && PassReset.maskOf("DINAPASS9\nM\t8\n") == 0
                && PassReset.undoLauncher("DINAPASS3\nM\tx\n", busy()) == 0);
        ok("message: a group pass names the group", PassReset.messageMask(pausesAll, 3).contains("everything in Pauses") && PassReset.messageMask(pausesAll, 3).contains("3 changes")
                && PassReset.messageMask(locksAll, 1).contains("everything in Locks") && PassReset.messageMask(DayPass.areaMask(DayPass.AREA_REPORTS), 4).isEmpty()
                && PassReset.messageMask(pausesAll, 0).isEmpty() && !PassReset.messageMask(pausesAll, 3).contains("one-day"));

        // ---------- what is said
        ok("message: says which pass and how many changes", PassReset.message(DayPass.PAUSE_STRICT, 3).contains("strict pauses")
                && PassReset.message(DayPass.PAUSE_STRICT, 3).contains("3 changes"));
        ok("message: one change", PassReset.message(DayPass.LOCK_APPS, 1).contains("1 change ") && !PassReset.message(DayPass.LOCK_APPS, 1).contains("1 changes"));
        ok("message: nothing to say when nothing went back", PassReset.message(DayPass.PAUSE_APPS, 0).isEmpty()
                && PassReset.message(DayPass.REPORT_DAYS, 4).isEmpty() && PassReset.message(DayPass.REPORT_WEEK, 4).isEmpty());
        ok("every item has a short name and a group", DayPass.shortName(DayPass.REPORT_DAYS).equals("Saved days") && DayPass.areaName(DayPass.area(DayPass.LOOK_OCEAN)).equals("Looks")
                && DayPass.shortName(DayPass.ANALYSIS).equals("Deeper analysis") && DayPass.shortName(0).isEmpty() && DayPass.shortName(16).isEmpty());

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
