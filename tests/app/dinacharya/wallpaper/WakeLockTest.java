package app.dinacharya.wallpaper;

import java.io.File;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

/**
 * Locks by time AND activity type, locks in library mode, the start-of-day alarm's ways to turn it off, its quotes, the shake counter, the
 * day off in reports, the saved-history cache and what the poster is told.
 */
public class WakeLockTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static long at(int h, int m) { return at(21, h, m); }
    static String k(String pkg) { return AppKey.make(pkg, pkg + ".Main", 0); }

    public static void main(String[] args) throws Exception {
        locks();
        quotes();
        shake();
        routineSettings();
        dayOffReport();
        historyCache();
        poster();
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ locks
    static void locks() {
        LauncherConfig c = new LauncherConfig();
        String ig = k("com.insta"), wa = k("com.chat");
        LauncherConfig.Group g = c.addGroup("Social");
        c.setInGroup(g, ig, true);
        g.days = LockRules.WEEKDAYS;
        g.startMin = 9 * 60;
        g.endMin = 17 * 60;
        g.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));
        LockRules.Slot study = new LockRules.Slot(Routine.TASK, "Study", at(11, 0));
        LockRules.Slot other = new LockRules.Slot(Routine.TASK, "Practice", at(11, 0));
        LockRules.Slot rest = new LockRules.Slot(Routine.REST, "", at(11, 0));

        // either one locks (as before)
        ok("either: a Study slot before the window locks", LockRules.lockedUntil(g, at(8, 0), Z, new LockRules.Slot(Routine.TASK, "Study", at(9, 30))) == at(9, 30));
        ok("either: the window locks without a Study slot", LockRules.lockedUntil(g, at(10, 0), Z, rest) == at(17, 0));
        ok("either: both at once lock until the later end", LockRules.lockedUntil(g, at(10, 0), Z, study) == at(17, 0));
        ok("either: outside the window and any slot, no lock", LockRules.lockedUntil(g, at(18, 0), Z, other) == 0);

        // both together
        g.both = true;
        ok("both: a Study slot before the window does not lock", LockRules.lockedUntil(g, at(8, 0), Z, new LockRules.Slot(Routine.TASK, "Study", at(9, 30))) == 0);
        ok("both: inside the window with a Study slot locks until the first end", LockRules.lockedUntil(g, at(10, 0), Z, study) == at(11, 0));
        ok("both: inside the window with a slot that ends after it locks until the window ends", LockRules.lockedUntil(g, at(16, 0), Z, new LockRules.Slot(Routine.TASK, "Study", at(18, 0))) == at(17, 0));
        ok("both: inside the window but another type does not lock", LockRules.lockedUntil(g, at(10, 0), Z, other) == 0);
        ok("both: inside the window with no slot does not lock", LockRules.lockedUntil(g, at(10, 0), Z, LockRules.Slot.NONE) == 0);
        ok("both: after the window with a Study slot does not lock", LockRules.lockedUntil(g, at(18, 0), Z, new LockRules.Slot(Routine.TASK, "Study", at(19, 0))) == 0);
        ok("both: Saturday is outside the days", LockRules.lockedUntil(g, at(24, 10, 0), Z, new LockRules.Slot(Routine.TASK, "Study", at(24, 11, 0))) == 0);
        ok("both is only in force when there are days and types", LockRules.combined(g));
        LockRules.Hit hit = LockRules.check(c, ig, DayPass.ALL, at(10, 0), Z, study);
        ok("both: the lock says it is held by Study and ends at 11", hit != null && hit.during.equals("Study") && hit.untilMs == at(11, 0) && !hit.library);
        ok("both: an app of no group stays free", LockRules.check(c, wa, DayPass.ALL, at(10, 0), Z, study) == null);

        // by hand still locks
        g.untilMs = at(10, 30);
        ok("by hand still locks when the two do not hold", LockRules.lockedUntil(g, at(8, 0), Z, other) == at(10, 30));
        g.untilMs = 0;

        // both set, but no window or no types: it falls back
        g.days = 0;
        ok("both without days: the activity types lock on their own", !LockRules.combined(g) && LockRules.lockedUntil(g, at(8, 0), Z, study) == at(11, 0));
        g.days = LockRules.WEEKDAYS;
        g.during.clear();
        ok("both without types: the window locks on its own", !LockRules.combined(g) && LockRules.lockedUntil(g, at(10, 0), Z, study) == at(17, 0));
        g.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));

        // window past midnight
        g.startMin = 22 * 60;
        g.endMin = 6 * 60;
        ok("both: a window past midnight with a Study slot at 1 a.m.", LockRules.lockedUntil(g, at(22, 1, 0), Z, new LockRules.Slot(Routine.TASK, "Study", at(22, 2, 0))) == at(22, 2, 0));
        g.startMin = 9 * 60;
        g.endMin = 17 * 60;

        // library mode
        LauncherConfig lc = new LauncherConfig();
        LauncherConfig.Group lg = lc.addGroup("Games");
        lc.setInGroup(lg, ig, true);
        lg.days = 0;
        LockRules.Slot lib = LockRules.Slot.NONE.withLibrary(at(15, 0), false);
        ok("a group that does not lock in library mode ignores it", LockRules.check(lc, ig, DayPass.ALL, at(10, 0), Z, lib) == null);
        lg.library = true;
        LockRules.Hit lh = LockRules.check(lc, ig, DayPass.ALL, at(10, 0), Z, lib);
        ok("library mode locks the group until it ends", lh != null && lh.untilMs == at(15, 0) && lh.library && !lh.untilOpen && lh.during.isEmpty());
        ok("with library mode off it is free", LockRules.check(lc, ig, DayPass.ALL, at(10, 0), Z, LockRules.Slot.NONE) == null);
        ok("library mode that has ended no longer locks", LockRules.check(lc, ig, DayPass.ALL, at(15, 0), Z, lib) == null);
        LockRules.Slot open = LockRules.Slot.NONE.withLibrary(at(22, 10, 0), true);
        LockRules.Hit oh = LockRules.check(lc, ig, DayPass.ALL, at(10, 0), Z, open);
        ok("library mode without an end says so", oh != null && oh.library && oh.untilOpen);
        ok("an off library has no end to speak of", !LockRules.Slot.NONE.withLibrary(0, true).libraryOpen);
        LockRules.Slot studyInLib = new LockRules.Slot(Routine.TASK, "Study", at(11, 0), at(15, 0), false);
        lg.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));
        LockRules.Hit both = LockRules.check(lc, ig, DayPass.ALL, at(10, 0), Z, studyInLib);
        ok("library mode and a type together: the longer one holds", both != null && both.untilMs == at(15, 0) && both.library);
        lg.hide = true;
        ok("a hiding group hides its apps in library mode", LockRules.hiddenNow(lc, DayPass.ALL, at(10, 0), Z, lib).contains(ig)
                && LockRules.hiddenNow(lc, DayPass.ALL, at(10, 0), Z, LockRules.Slot.NONE).isEmpty());
        ok("the guard watches the slot when a group locks in library mode", GuardRules.anyDuring(lc));
        LauncherConfig only = new LauncherConfig();
        LauncherConfig.Group og = only.addGroup("X");
        only.setInGroup(og, ig, true);
        og.library = true;
        ok("even a group with nothing else is watched", GuardRules.anyDuring(only));

        // text
        ok("status: held by library mode", LockRules.status(lg, at(10, 0), Z, false, lib).startsWith("Locked until "));
        ok("status: open library", LockRules.status(lg, at(10, 0), Z, false, open).equals("Locked until library mode is switched off"));
        LauncherConfig.Group tg = new LauncherConfig().addGroup("T");
        tg.days = LockRules.WEEKDAYS;
        tg.startMin = 9 * 60;
        tg.endMin = 17 * 60;
        tg.during.add(LauncherConfig.typeKey(Routine.TASK, "Study"));
        tg.both = true;
        String st = LockRules.status(tg, at(18, 0), Z, false, LockRules.Slot.NONE);
        ok("status: both says when and what", st.startsWith("Locks during Study, weekdays, 9:00 AM to 5:00 PM"));
        tg.library = true;
        ok("status: and in library mode", LockRules.status(tg, at(18, 0), Z, false, LockRules.Slot.NONE).endsWith("and in library mode"));
        LauncherConfig.Group lonely = new LauncherConfig().addGroup("L");
        lonely.days = 0;
        lonely.library = true;
        ok("status: library only", LockRules.status(lonely, at(18, 0), Z, false, LockRules.Slot.NONE).equals("Locks in library mode"));

        // saving
        LauncherConfig sc = new LauncherConfig();
        LauncherConfig.Group a1 = sc.addGroup("One");
        sc.setInGroup(a1, ig, true);
        a1.both = true;
        LauncherConfig.Group a2 = sc.addGroup("Two");
        sc.setInGroup(a2, wa, true);
        a2.library = true;
        a2.during.add("0:study");
        LauncherConfig.Group a3 = sc.addGroup("Three");
        LauncherConfig back = LauncherConfig.parse(sc.serialize());
        ok("both and library are saved with the group they belong to", back.groups.size() == 3 && back.groups.get(0).both && !back.groups.get(0).library
                && !back.groups.get(1).both && back.groups.get(1).library && !back.groups.get(2).both && !back.groups.get(2).library);
        ok("the types survive next to them", back.groups.get(1).during.equals(Arrays.asList("0:study")));
        String old = sc.serialize().replaceAll("(?m)^GY\\t.*\\n", "");
        LauncherConfig oldBack = LauncherConfig.parse(old);
        ok("a configuration saved by an older version has neither", oldBack.groups.size() == 3 && !oldBack.groups.get(0).both && !oldBack.groups.get(1).library);
        LauncherConfig junk = LauncherConfig.parse(sc.serialize() + "GY\t99\t1\t1\nGY\tx\t1\nGY\t0\n");
        ok("damaged lines are ignored", junk.groups.size() == 3 && junk.groups.get(0).both);
        ok("the library flag alone is saved", LauncherConfig.parse(only.serialize()).groups.get(0).library);
    }

    // ------------------------------------------------------------------ quotes
    static void quotes() {
        WakeQuotes.Quote[] all = WakeQuotes.LIST;
        ok("there is a good number of quotes", all.length >= 40);
        ok("every quote is a short sentence", Arrays.stream(all).allMatch(q -> q.text.length() > 10 && q.text.length() <= Routine.MAX_QUOTE_LEN));
        ok("no quote is listed twice", Arrays.stream(all).map(q -> q.text).distinct().count() == all.length);
        ok("authors are names or empty, and have no quotes marks", Arrays.stream(all).allMatch(q -> q.author != null && !q.author.contains("\"") && q.author.length() <= 40));
        ok("most quotes are by someone", Arrays.stream(all).filter(q -> !q.author.isEmpty()).count() >= 25);
        ok("the older lines are still there", Arrays.asList(WakeQuotes.BUILT_IN).contains("Well begun is half done.") && WakeQuotes.BUILT_IN.length == all.length);
        ok("the step shares no factor with the number of quotes", gcd(WakeQuotes.STEP, all.length) == 1);
        LocalDate day = LocalDate.of(2026, 10, 21);
        Set<String> seen = new HashSet<>();
        for (int n = 0; n < all.length; n++) seen.add(WakeQuotes.quote("", day, n).text);
        ok("'another quote' goes through all of them before it repeats", seen.size() == all.length);
        ok("negative and large counts stay inside the list", WakeQuotes.quote("", day, -3).text != null && WakeQuotes.quote("", day, 100000).text != null);
        ok("the quote of the day is steady and changes with the day", WakeQuotes.quote("", day, 0).text.equals(WakeQuotes.quote(null, day, 0).text)
                && !WakeQuotes.quote("", day, 0).text.equals(WakeQuotes.quote("", day.plusDays(1), 0).text));
        ok("another is not the same", !WakeQuotes.quote("", day, 0).text.equals(WakeQuotes.quote("", day, 1).text));
        WakeQuotes.Quote mine = WakeQuotes.quote("  Be on time  ", day, 5);
        ok("the person's own line wins and has no author", mine.text.equals("Be on time") && mine.author.isEmpty());
        ok("the old calls agree with the new", WakeQuotes.forDay("", day).equals(WakeQuotes.quote("", day, 0).text) && WakeQuotes.another("", day).equals(WakeQuotes.quote("", day, 1).text));
        ok("a sentence to type to start the day", WakeQuotes.START_PHRASES.length >= 4 && Arrays.stream(WakeQuotes.START_PHRASES).allMatch(s -> s.length() > 10 && s.length() < 60));
    }

    static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    // ------------------------------------------------------------------ shake
    static void shake() {
        ShakeCounter s = new ShakeCounter(5, 0.9f, 250);
        float g = ShakeCounter.G;
        long t = 1000;
        for (int i = 0; i < 100; i++, t += 20) s.add(0, 0, g, t);
        ok("a phone lying still counts nothing", s.count() == 0 && !s.done());
        for (int i = 0; i < 100; i++, t += 20) s.add(0.3f, 0.2f, g * 1.1f, t);
        ok("small movements count nothing", s.count() == 0);
        s.add(0, 0, g * 2.6f, t);
        ok("one shake counts", s.count() == 1);
        s.add(0, 0, g * 2.8f, t + 50);
        s.add(g * 2f, 0, g, t + 100);
        ok("a jolt that goes on for a moment counts once", s.count() == 1);
        t += 300;
        s.add(0, 0, g * 2.6f, t);
        ok("the next shake after the pause counts", s.count() == 2);
        ok("weightlessness counts as a shake too", new ShakeCounter(1, 0.9f, 0).add(0, 0, 0, 5));
        t += 300;
        s.add(g * 2f, g * 2f, g * 2f, t);
        t += 300;
        s.add(-g * 3f, 0, 0, t);
        ok("shakes in any direction count", s.count() == 4 && !s.done() && Math.abs(s.progress() - 0.8f) < 0.001f);
        t += 300;
        ok("the last one finishes it", s.add(0, g * 3f, 0, t) && s.done() && s.count() == 5 && s.progress() == 1f);
        ok("it stays done and does not count past the target", s.add(0, 0, g * 3f, t + 1000) && s.count() == 5);
        s.reset();
        ok("reset starts again", s.count() == 0 && !s.done() && s.progress() == 0f);
        ok("a reading that is not a number is ignored", !new ShakeCounter(1, 0.9f, 0).add(Float.NaN, 0, 0, 5));
        ShakeCounter d = new ShakeCounter();
        ok("the default asks for a real shake", d.target() == 20 && ShakeCounter.DEFAULT_THRESHOLD_G >= 0.5f);
        ok("a silly target is made sensible", new ShakeCounter(-4, -1, -1).target() == 1);
    }

    // ------------------------------------------------------------------ routine settings
    static void routineSettings() {
        Routine r = Routine.sample();
        ok("by default the day starts with a tap, with a quote and the day-off option", r.wakeOff == Routine.OFF_NONE && r.wakeQuoteOn && r.wakeDayOff);
        r.wakeOff = Routine.OFF_SHAKE;
        r.wakeQuoteOn = false;
        r.wakeDayOff = false;
        Routine b = Routine.parse(r.serialize());
        ok("the settings are saved", b.wakeOff == Routine.OFF_SHAKE && !b.wakeQuoteOn && !b.wakeDayOff);
        Routine c = Routine.sample();
        c.replaceWith(r);
        ok("and copied with the routine", c.wakeOff == Routine.OFF_SHAKE && !c.wakeQuoteOn && !c.wakeDayOff);
        String text = r.serialize().replace("wakeOff=" + Routine.OFF_SHAKE, "wakeOff=99");
        ok("an unknown way to turn the alarm off falls back to a tap", Routine.parse(text).wakeOff == Routine.OFF_NONE);
        String oldText = r.serialize().replaceAll("(?m)^wake(Off|QuoteOn|DayOff)=.*\\n", "");
        Routine o = Routine.parse(oldText);
        ok("a routine saved before has the defaults", o.wakeOff == Routine.OFF_NONE && o.wakeQuoteOn && o.wakeDayOff);
        Routine look = r.copyLook();
        ok("the look copy carries them (a routine for one day follows the main one)", look.wakeOff == Routine.OFF_SHAKE && !look.wakeQuoteOn && !look.wakeDayOff);
        ok("the day off signature ignores alarm settings", DayOff.sig(r, LocalDate.of(2026, 10, 21)).equals(DayOff.sig(Routine.sample(), LocalDate.of(2026, 10, 21))));
        for (int i = Routine.OFF_NONE; i <= Routine.OFF_TYPE; i++) {
            r.wakeOff = i;
            ok("way " + i + " survives saving", Routine.parse(r.serialize()).wakeOff == i);
        }
    }

    // ------------------------------------------------------------------ the day off in reports
    static void dayOffReport() {
        Routine r = Routine.sample();
        r.alarmOn = true;
        Plan.DayPlan full = Plan.plan(r, new Overrides(), LocalDate.of(2026, 10, 21), Z);
        long off = at(8, 30);
        List<Ev> ev = new ArrayList<>(Arrays.asList(Ev.dayOff(off, "2026-10-21", full.endMs)));
        List<long[]> p = DayOff.periods(ev);
        ok("one open stretch", p.size() == 1 && p.get(0)[0] == off && p.get(0)[1] == Long.MAX_VALUE);
        ok("inside and outside it", DayOff.within(p, at(12, 0)) && !DayOff.within(p, at(8, 0)) && DayOff.within(p, off) && !DayOff.within(p, off - 1));
        ev.add(Ev.dayOffEnded(at(12, 0), "2026-10-21"));
        p = DayOff.periods(ev);
        ok("it ends when it is ended", p.size() == 1 && p.get(0)[1] == at(12, 0) && !DayOff.within(p, at(12, 0)) && DayOff.within(p, at(11, 59)));
        ev.add(Ev.dayOff(at(14, 0), "2026-10-21", full.endMs));
        ok("a second day off starts another stretch", DayOff.periods(ev).size() == 2);
        List<Ev> shuffled = new ArrayList<>(ev);
        Collections.reverse(shuffled);
        ok("the order of the history does not matter", DayOff.periods(shuffled).size() == 2);
        ok("an end with no start is ignored", DayOff.periods(Arrays.asList(Ev.dayOffEnded(at(9, 0), "2026-10-21"))).isEmpty());
        ok("a day off marker never reads as an end, whatever end it is given", !"0".equals(Ev.dayOff(1, "d", 0).a));
        ok("two starts in a row are one stretch", DayOff.periods(Arrays.asList(Ev.dayOff(at(9, 0), "d", 5), Ev.dayOff(at(10, 0), "d", 5))).size() == 1);

        // the report
        List<Ev> day = new ArrayList<>(Arrays.asList(Ev.dayOff(off, "2026-10-21", full.endMs)));
        DayReport rep = ReportAssembler.assemble(full, day, null, true, at(21, 12, 0));
        int cut = -1;
        for (int i = 0; i < full.sc.slots.size(); i++) if (full.slotStartMs(i) < off && full.slotEndMs(i) > off) cut = i;
        ok("a slot going on when the day was taken off ends there", cut >= 0 && rep.rows.get(cut).status != DayReport.SKIPPED
                && rep.rows.get(cut).activeMs == off - full.slotStartMs(cut));
        boolean after = true;
        boolean sleepKept = true;
        for (int i = 0; i < full.sc.slots.size(); i++) {
            Routine.Block b = full.sc.slots.get(i).block;
            if (b.kind == Routine.SLEEP) {
                if (rep.rows.get(i).status == DayReport.SKIPPED) sleepKept = false;
            } else if (full.slotStartMs(i) >= off && (rep.rows.get(i).status != DayReport.SKIPPED || rep.rows.get(i).activeMs != 0)) {
                after = false;
            }
        }
        ok("slots that begin after it are taken off", after);
        ok("the sleep slot is not taken off", sleepKept);
        ok("slots before it are as they were", rep.rows.get(0).status != DayReport.SKIPPED);
        day.add(Ev.dayOffEnded(at(12, 0), "2026-10-21"));
        DayReport again = ReportAssembler.assemble(full, day, null, true, at(21, 23, 0));
        boolean back = true;
        for (int i = 0; i < full.sc.slots.size(); i++) {
            Routine.Block b = full.sc.slots.get(i).block;
            long st = full.slotStartMs(i);
            if (b.kind != Routine.SLEEP && st >= at(12, 0) && again.rows.get(i).status == DayReport.SKIPPED) back = false;
            if (b.kind != Routine.SLEEP && st >= off && st < at(12, 0) && again.rows.get(i).status != DayReport.SKIPPED) back = false;
        }
        ok("once the day is planned again, slots from then on count again, and those in between stay off", back);
        SlotGaps.Result g = SlotGaps.of(day, full.sc.slots.get(cut).block.src, full.slotStartMs(cut), full.slotEndMs(cut), at(21, 12, 0));
        ok("the gaps know the slot was cut", g.effEnd == off && g.cutMs == full.slotEndMs(cut) - off);

        // after a replan
        Routine edited = Routine.sample();
        edited.blocks.get(3).title = "New plan";
        AlarmState st = new AlarmState();
        DayOff.start(r, st, LocalDate.of(2026, 10, 21), off, full.endMs);
        ok("not planned again yet", !DayOff.replanned(r, st) && DayOff.pending(r, st, at(10, 0)));
        ok("a new schedule means planned again", DayOff.replanned(edited, st));
        ok("nothing on record means nothing planned again", !DayOff.replanned(edited, new AlarmState()));
        ok("it is over when the day is over", !DayOff.pending(r, st, full.endMs) && DayOff.expired(st, full.endMs) && !DayOff.expired(st, full.endMs - 1));
        ok("a day off with no end known never expires", !DayOff.expired(mk(r, off), at(23, 59)));
        AlarmState saved = AlarmState.parse(st.serialize());
        ok("the end of the day off is saved", saved.offEnd == full.endMs);
        DayOff.end(st);
        ok("ending clears all of it", st.offDay.isEmpty() && st.offEnd == 0 && st.offFrom == 0 && st.offSig.isEmpty());
        AlarmState next = new AlarmState();
        DayOff.start(r, next, LocalDate.of(2026, 10, 21), off, full.endMs);
        ok("the question about a bedtime is held back during the day off and back after it", AlarmPlanner.upcoming(r, new Overrides(), withReask(next, at(21, 21, 45)), at(10, 0), Z)
                .stream().noneMatch(e -> e.kind == AlarmPlanner.REASK)
                && AlarmPlanner.upcoming(r, new Overrides(), withReask(next, at(22, 21, 45)), full.endMs + 1, Z).stream().anyMatch(e -> e.kind == AlarmPlanner.REASK));
    }

    static AlarmState mk(Routine r, long off) {
        AlarmState s = new AlarmState();
        DayOff.start(r, s, LocalDate.of(2026, 10, 21), off);
        return s;
    }

    static AlarmState withReask(AlarmState s, long when) {
        AlarmState c = AlarmState.parse(s.serialize());
        c.reaskAt = when;
        c.reaskDay = "2026-10-21";
        c.reaskSrc = 18;
        c.reaskTitle = "Sleep";
        c.reaskBedMs = when - 900000L;
        return c;
    }

    // ------------------------------------------------------------------ the history cache
    static void historyCache() throws Exception {
        File dir = Files.createTempDirectory("hist").toFile();
        HistoryStore a = new HistoryStore(dir);
        ok("an empty history", a.events().isEmpty());
        a.append(Ev.ack(1000, "d", 1, "x", "C", ""));
        ok("one event after one write", a.events().size() == 1);
        List<Ev> first = a.events();
        first.clear();
        ok("what a caller does to the list does not touch the next reading", a.events().size() == 1);
        HistoryStore b = new HistoryStore(dir);
        b.append(Ev.ack(2000, "d", 2, "y", "C", ""));
        ok("another store on the same file sees the write at once", a.events().size() == 2 && b.events().size() == 2);
        a.compact(Long.MAX_VALUE / 2);
        ok("compacting is seen too", a.events().isEmpty() && b.events().isEmpty());
        a.append(Ev.ack(3000, "d", 3, "z", "C", ""));
        ok("and writing after it", b.events().size() == 1 && b.events().get(0).src == 3);
        File other = Files.createTempDirectory("hist2").toFile();
        HistoryStore c = new HistoryStore(other);
        ok("a different folder is a different history", c.events().isEmpty() && a.events().size() == 1);
    }

    // ------------------------------------------------------------------ what the poster is told
    static void poster() {
        Routine r = Routine.sample();
        Plan.DayPlan p = Plan.resolve(r, new Overrides(), at(8, 10), Z);
        List<Ev> ev = new ArrayList<>(Arrays.asList(Ev.breakTaken(at(8, 0), at(8, 20), "2026-10-21", 3, "Morning focus block")));
        AlarmState st = new AlarmState();
        st.breakFrom = at(8, 0);
        st.breakTo = at(8, 20);
        st.breakDay = "2026-10-21";
        st.breakSrc = 3;
        Todos todos = new Todos();
        todos.add(LocalDate.of(2026, 10, 21), Todos.slotScope(3), "Read chapter 4");
        LibraryMode lib = new LibraryMode();
        PosterExtras x = new PosterExtras(todos, st, ev, r, lib);
        Plan.DayPlan live = x.live(p, at(8, 10));
        ok("the poster draws the break as its own row", live != p && live.sc.slots.get(live.cur).block.live == Routine.LIVE_BREAK);
        PosterExtras.Now n = x.at(live, at(8, 10), Z);
        ok("during the break the break is told and no list is on its card", n.breakUntilMs == at(8, 20) && n.items.isEmpty() && n.libraryEndMs == 0);
        Plan.DayPlan later = x.live(Plan.resolve(r, new Overrides(), at(8, 30), Z), at(8, 30));
        PosterExtras.Now n2 = x.at(later, at(8, 30), Z);
        ok("after the break the second part of the slot has its to-do list", later.sc.slots.get(later.cur).block.src == 3 && n2.items.size() == 1 && n2.breakUntilMs == 0);
        lib.turnOn(at(8, 0), 120);
        ok("library mode is told with its end", x.at(live, at(8, 10), Z).libraryEndMs == at(10, 0));
        ok("and not without a zone", x.at(live, at(8, 10)).libraryEndMs == 0);
        ok("with nothing to tell the poster draws the plan as it is", new PosterExtras(todos, st, new ArrayList<Ev>(), r, null).live(p, at(8, 10)) == p
                && PosterExtras.NONE.live(p, at(8, 10)) == p);
        ok("library mode alone is told even with no slot going on", x.at(Plan.resolve(r, new Overrides(), at(3, 0), Z), at(8, 10), Z).libraryEndMs == at(10, 0));
        AlarmState off = new AlarmState();
        DayOff.start(r, off, LocalDate.of(2026, 10, 21), at(7, 15), p.endMs);
        Plan.DayPlan offPlan = new PosterExtras(todos, off, new ArrayList<Ev>(), r, null).live(p, at(9, 0));
        ok("a day off is drawn as the day-off row", offPlan != p && offPlan.sc.slots.get(offPlan.cur).block.live == Routine.LIVE_OFF);
        Routine edited = Routine.sample();
        edited.blocks.get(3).title = "New plan";
        ok("planned again: the day is drawn as planned", new PosterExtras(todos, off, new ArrayList<Ev>(), edited, null).live(Plan.resolve(edited, new Overrides(), at(9, 0), Z), at(9, 0)).sc.slots.stream()
                .noneMatch(sl -> sl.block.live == Routine.LIVE_OFF));
    }
}
