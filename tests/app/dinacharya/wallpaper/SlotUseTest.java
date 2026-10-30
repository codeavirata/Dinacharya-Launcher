package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * Phone use inside a slot, told apart by the apps chosen as part of the activity: the numbers for one slot (time in apps, other phone use,
 * focus, the longest unbroken focus, time devoted, the stretches with exact times), the settings that say which apps those are, the
 * report rows and their saved text, and the snapshot the home screen reads.
 */
public class SlotUseTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final long MIN = 60000L, SEC = 1000L;
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static long at(int h, int m) { return at(19, h, m); }

    /** Phone use built from packages and their times; the combined times and the names are made the way the app makes them. */
    static UsageDetail det(Object... pkgAndTimes) {
        Map<String, List<long[]>> by = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        List<long[]> all = new ArrayList<>();
        for (int i = 0; i < pkgAndTimes.length; i += 2) {
            String pkg = (String) pkgAndTimes[i];
            long[][] t = (long[][]) pkgAndTimes[i + 1];
            List<long[]> l = new ArrayList<>();
            for (long[] x : t) { l.add(new long[]{x[0], x[1]}); all.add(new long[]{x[0], x[1]}); }
            by.put(pkg, l);
            names.put(pkg, pkg.substring(pkg.lastIndexOf('.') + 1).substring(0, 1).toUpperCase() + pkg.substring(pkg.lastIndexOf('.') + 2));
        }
        return new UsageDetail(0, Long.MAX_VALUE / 4, SlotGaps.merge(all), new LinkedHashMap<String, Long>(), new HashMap<String, List<long[]>>(), by, names);
    }
    static long[][] t(long... v) { long[][] r = new long[v.length / 2][]; for (int i = 0; i < r.length; i++) r[i] = new long[]{v[2 * i], v[2 * i + 1]}; return r; }
    static List<long[]> win(long... v) { return new ArrayList<>(Arrays.asList(t(v))); }
    static Set<String> set(String... s) { return new HashSet<>(Arrays.asList(s)); }

    public static void main(String[] args) {
        long s0 = at(9, 0), s1 = at(10, 0);   // a one-hour slot

        // ---------- one slot: the numbers
        SlotUse.Info none = SlotUse.analyze(win(s0, s1), det(), set(), s1);
        ok("no phone use: full focus, nothing counted", none.known && none.workedMs == 60 * MIN && none.appMs == 0 && none.offMs == 0 && none.focusPct() == 100
                && none.longestFocusMs == 60 * MIN && none.episodes.isEmpty() && none.devotedMs() == 60 * MIN);

        SlotUse.Info off10 = SlotUse.analyze(win(s0, s1), det("com.insta", t(s0 + 20 * MIN, s0 + 30 * MIN)), set(), s1);
        ok("ten minutes in another app: 10 off, 83% focus", off10.offMs == 10 * MIN && off10.appMs == 10 * MIN && off10.partMs == 0 && off10.focusPct() == 83);
        ok("the longest unbroken focus is the longer side of the stretch", off10.longestFocusMs == 30 * MIN);
        ok("one stretch with exact times and the app", off10.episodeCount == 1 && off10.episodes.get(0).startMs == s0 + 20 * MIN
                && off10.episodes.get(0).endMs == s0 + 30 * MIN && off10.episodes.get(0).app.equals("Insta"));
        ok("time devoted is the slot without the other use", off10.devotedMs() == 50 * MIN);

        SlotUse.Info part = SlotUse.analyze(win(s0, s1), det("com.notes", t(s0 + 5 * MIN, s0 + 25 * MIN)), set("com.notes"), s1);
        ok("an app of the activity is not other phone use", part.offMs == 0 && part.partMs == 20 * MIN && part.appMs == 20 * MIN && part.focusPct() == 100
                && part.episodes.isEmpty() && part.longestFocusMs == 60 * MIN);
        SlotUse.Info notPart = SlotUse.analyze(win(s0, s1), det("com.notes", t(s0 + 5 * MIN, s0 + 25 * MIN)), set(), s1);
        ok("the same app with nothing chosen is other phone use", notPart.offMs == 20 * MIN && notPart.partMs == 0 && notPart.focusPct() == 67);

        SlotUse.Info mix = SlotUse.analyze(win(s0, s1), det("com.notes", t(s0, s0 + 20 * MIN), "com.insta", t(s0 + 10 * MIN, s0 + 30 * MIN)), set("com.notes"), s1);
        ok("both open at once counts as other phone use", mix.appMs == 30 * MIN && mix.offMs == 20 * MIN && mix.partMs == 10 * MIN);
        ok("the time of apps is never counted twice", mix.offMs + mix.partMs == mix.appMs);

        // the use outside the slot does not count
        SlotUse.Info outside = SlotUse.analyze(win(s0, s1), det("com.insta", t(s0 - 30 * MIN, s0 + 5 * MIN, s1 - 5 * MIN, s1 + 40 * MIN)), set(), s1);
        ok("use before and after the slot is cut off", outside.offMs == 10 * MIN && outside.episodeCount == 2 && outside.episodes.get(0).startMs == s0
                && outside.episodes.get(1).endMs == s1);
        ok("focus run between the two stretches is the longest", outside.longestFocusMs == 50 * MIN);

        // joining: a switch from one app to another is one stretch
        SlotUse.Info join = SlotUse.analyze(win(s0, s1), det("com.insta", t(s0 + 10 * MIN, s0 + 15 * MIN), "com.chat", t(s0 + 15 * MIN + 10 * SEC, s0 + 20 * MIN)), set(), s1);
        ok("two apps 10 seconds apart are one stretch", join.episodeCount == 1 && join.episodes.get(0).startMs == s0 + 10 * MIN && join.episodes.get(0).endMs == s0 + 20 * MIN);
        ok("the time between them is not counted as use", join.offMs == 10 * MIN - 10 * SEC);
        SlotUse.Info apart = SlotUse.analyze(win(s0, s1), det("com.insta", t(s0 + 10 * MIN, s0 + 15 * MIN), "com.chat", t(s0 + 15 * MIN + 16 * SEC, s0 + 20 * MIN)), set(), s1);
        ok("two apps 16 seconds apart are two stretches", apart.episodeCount == 2);
        ok("exactly 15 seconds apart still joins", SlotUse.analyze(win(s0, s1), det("com.insta", t(s0, s0 + MIN), "com.chat", t(s0 + MIN + 15 * SEC, s0 + 2 * MIN)), set(), s1).episodeCount == 1);
        ok("the stretch is named for the app used longest in it", join.episodes.get(0).app.equals("Insta") || join.episodes.get(0).app.equals("Chat"));
        SlotUse.Info named = SlotUse.analyze(win(s0, s1), det("com.insta", t(s0 + 10 * MIN, s0 + 12 * MIN), "com.chat", t(s0 + 12 * MIN, s0 + 20 * MIN)), set(), s1);
        ok("longer app names the stretch", named.episodes.get(0).app.equals("Chat"));

        // breaks: the slot's time is in pieces, and a break ends a stretch of focus
        SlotUse.Info split = SlotUse.analyze(win(s0, s0 + 20 * MIN, s0 + 30 * MIN, s1), det("com.insta", t(s0 + 40 * MIN, s0 + 45 * MIN)), set(), s1);
        ok("worked time leaves out the break", split.workedMs == 50 * MIN);
        ok("focus does not run across a break", split.longestFocusMs == 20 * MIN);
        SlotUse.Info inBreak = SlotUse.analyze(win(s0, s0 + 20 * MIN, s0 + 30 * MIN, s1), det("com.insta", t(s0 + 21 * MIN, s0 + 29 * MIN)), set(), s1);
        ok("use during a break is not counted", inBreak.offMs == 0 && inBreak.episodes.isEmpty());

        // right now
        long now = s0 + 40 * MIN;
        SlotUse.Info live = SlotUse.analyze(win(s0, now), det("com.insta", t(s0 + 10 * MIN, s0 + 15 * MIN)), set(), now);
        ok("focus running since the last stretch ended", !live.ongoing && live.focusSinceMs == 25 * MIN);
        SlotUse.Info using = SlotUse.analyze(win(s0, now), det("com.insta", t(now - 3 * MIN, now)), set(), now);
        ok("use going on right now is ongoing, no focus yet", using.ongoing && using.focusSinceMs == 0 && using.episodes.get(0).endMs == now);
        SlotUse.Info fresh = SlotUse.analyze(win(s0, now), det(), set(), now);
        ok("no use at all: focus since the slot began", fresh.focusSinceMs == 40 * MIN && !fresh.ongoing);
        SlotUse.Info past = SlotUse.analyze(win(s0, s1), det("com.insta", t(s0 + 10 * MIN, s0 + 15 * MIN)), set(), s1 + 3 * 3600000L);
        ok("a slot that is over has no running focus", past.focusSinceMs == 0 && !past.ongoing);

        // not known
        SlotUse.Info unk = SlotUse.analyze(win(s0, s1), null, set(), s1);
        ok("no usage access: only the slot's time is known", !unk.known && unk.workedMs == 60 * MIN && unk.focusPct() == -1 && unk.longestFocusMs == -1);
        SlotUse.Info empty = SlotUse.analyze(new ArrayList<long[]>(), det("com.insta", t(s0, s1)), set(), s1);
        ok("a slot that has not started has nothing", empty.workedMs == 0 && empty.focusPct() == -1 && empty.offMs == 0);
        ok("a null window list is the same", SlotUse.analyze(null, det(), set(), s1).workedMs == 0);
        ok("backwards and empty windows are dropped", SlotUse.analyze(win(s1, s0, s0, s0), det(), set(), s1).workedMs == 0);

        // old data: no word on single apps
        UsageDetail blind = new UsageDetail(0, Long.MAX_VALUE / 4, win(s0 + 10 * MIN, s0 + 20 * MIN), new LinkedHashMap<String, Long>());
        SlotUse.Info b = SlotUse.analyze(win(s0, s1), blind, set("com.notes"), s1);
        ok("without per-app times all use counts as other phone use", b.offMs == 10 * MIN && b.partMs == 0 && b.episodes.get(0).app.isEmpty());

        // a lot of stretches
        List<long[]> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) many.add(new long[]{s0 + i * 30 * SEC, s0 + i * 30 * SEC + 5 * SEC});
        long[][] manyArr = many.toArray(new long[0][]);
        UsageDetail dm = det("com.insta", manyArr);
        SlotUse.Info mm = SlotUse.analyze(win(s0, s0 + 60 * MIN), dm, set(), s1);
        ok("the stretches kept are limited, all are counted", mm.episodes.size() == SlotUse.MAX_EPISODES && mm.episodeCount == 100);

        // ---------- the settings: which apps are part of which activity
        LauncherConfig c = new LauncherConfig();
        String notes = AppKey.make("com.notes", "com.notes.Main", 0), kindle = AppKey.make("com.kindle", "com.kindle.Main", 0);
        ok("off by default, no apps", !c.trackUse && c.typeApps.isEmpty() && c.partPackages(Routine.TASK, "Study").isEmpty());
        ok("setting apps for a type", c.setTypeApps(Routine.TASK, "Study", Arrays.asList(notes, kindle, notes, "junk", AppKey.DASHBOARD)));
        ok("repeats and things that are not apps are left out", c.appsOfType(Routine.TASK, "Study").size() == 2);
        ok("nothing counts while the switch is off", c.partPackages(Routine.TASK, "Study").isEmpty());
        c.trackUse = true;
        ok("with the switch on the packages are the apps' packages", c.partPackages(Routine.TASK, "Study").equals(set("com.notes", "com.kindle")));
        ok("the type is found whatever its case", c.partPackages(Routine.TASK, "STUDY").size() == 2 && c.partPackages(Routine.TASK, " study ").size() == 2);
        ok("another type or kind has none", c.partPackages(Routine.TASK, "Work").isEmpty() && c.partPackages(Routine.REST, "Study").isEmpty());
        LauncherConfig back = LauncherConfig.parse(c.serialize());
        ok("saved and read back", back.trackUse && back.appsOfType(Routine.TASK, "Study").equals(c.appsOfType(Routine.TASK, "Study")));
        ok("round trip is stable", back.serialize().equals(c.serialize()));
        LauncherConfig plain = new LauncherConfig();
        ok("a config that never used it saves nothing new", !plain.serialize().contains("trackUse") && !plain.serialize().contains("AU\t"));
        ok("a config saved before this existed reads as off", !LauncherConfig.parse(plain.serialize()).trackUse);
        c.forget(notes);
        ok("forgetting an app takes it out of the lists", c.appsOfType(Routine.TASK, "Study").equals(Collections.singletonList(kindle)));
        c.forget(kindle);
        ok("an empty list goes away", c.typeApps.isEmpty());
        ok("setting an empty list clears the type", c.setTypeApps(Routine.TASK, "Study", Collections.singletonList(notes)) && c.setTypeApps(Routine.TASK, "Study", new ArrayList<String>()) && c.typeApps.isEmpty());
        ok("a kind that does not exist is refused", !c.setTypeApps(9, "x", Collections.singletonList(notes)) && !c.setTypeApps(-1, "x", Collections.singletonList(notes)));
        LauncherConfig lim = new LauncherConfig();
        boolean refused = false;
        for (int i = 0; i < LauncherConfig.MAX_USE_TYPES + 5; i++) if (!lim.setTypeApps(Routine.TASK, "type" + i, Collections.singletonList(notes))) refused = true;
        ok("only so many types can have a list", refused && lim.typeApps.size() == LauncherConfig.MAX_USE_TYPES);
        List<String> lots = new ArrayList<>();
        for (int i = 0; i < LauncherConfig.MAX_USE_APPS + 10; i++) lots.add(AppKey.make("com.app" + i, "com.app" + i + ".Main", 0));
        LauncherConfig big = new LauncherConfig();
        big.setTypeApps(Routine.REST, "Other", lots);
        ok("only so many apps in a list", big.appsOfType(Routine.REST, "Other").size() == LauncherConfig.MAX_USE_APPS);
        LauncherConfig dmg = LauncherConfig.parse("DINALAUNCH1\ntrackUse=1\nAU\t0:study\tcom.notes/com.notes.Main@0\tnot an app\nAU\t0:*\tcom.x/com.x.Main@0\nAU\tbad\tcom.x/com.x.Main@0\nAU\t0:work\nAU\n");
        ok("damaged lines are skipped", dmg.trackUse && dmg.typeApps.size() == 1 && dmg.appsOfType(0, "study").size() == 1);
        ok("reset clears the lists with everything else", new LauncherConfig().typeApps.isEmpty());

        // ---------- the report rows
        Routine base = Routine.sample();
        base.alarmOn = true;
        Overrides ov = new Overrides(); ov.sig = Plan.signature(base);
        LocalDate d19 = LocalDate.of(2026, 10, 19);
        Plan.DayPlan p = Plan.plan(base, ov, d19, Z);
        List<Ev> evs = new ArrayList<>();
        evs.add(Ev.alarms(at(18, 20, 0), true));
        SlotUse.Parts parts = (kind, sub) -> kind == Routine.TASK && "Study".equalsIgnoreCase(sub) ? set("com.notes") : new HashSet<String>();
        // "Reading & notes" is slot 5, Study, 09:30-11:00
        UsageDetail u = det("com.notes", t(at(9, 40), at(9, 55)), "com.insta", t(at(10, 5), at(10, 17), at(10, 40), at(10, 43)),
                "com.chat", t(at(11, 20), at(11, 30)));
        long closeNow = at(20, 5, 31);
        DayReport plainRep = ReportAssembler.assemble(p, evs, u, true, closeNow);
        DayReport r = ReportAssembler.assemble(p, evs, u, true, closeNow, parts);
        DayReport.Row row5 = r.rows.get(5), plain5 = plainRep.rows.get(5);
        ok("without the settings nothing is marked as part of the activity", plain5.partMs == 0 && plain5.offMs() == plain5.phoneMs && plain5.phoneMs == 30 * MIN);
        ok("phone use in the slot is the same either way", row5.phoneMs == plain5.phoneMs);
        ok("with the settings the notes app is part of the slot", row5.partMs == 15 * MIN && row5.offMs() == 15 * MIN);
        ok("the other phone use is two stretches with exact times", row5.useCount == 2 && row5.uses.get(0).startMs == at(10, 5) && row5.uses.get(0).endMs == at(10, 17)
                && row5.uses.get(1).startMs == at(10, 40) && row5.uses.get(1).app.equals("Insta"));
        ok("the longest focus is the longest gap between them", row5.longestFocusMs == 35 * MIN);
        ok("focus and devoted time follow", row5.activeMs == 90 * MIN && row5.focusPct() == 83 && row5.devotedMs() == 75 * MIN);
        ok("a slot with no use is fully focused", r.rows.get(3).focusPct() == 100 && r.rows.get(3).longestFocusMs == 120 * MIN && r.rows.get(3).useCount == 0);
        DayReport.Row chatRow = r.rows.get(7);   // 11:10-12:40 Practice, chat 11:20-11:30
        ok("another slot's use is counted with no apps chosen for it", chatRow.partMs == 0 && chatRow.offMs() == 10 * MIN && chatRow.useCount == 1);
        ok("sleep is not analysed", r.rows.get(18).longestFocusMs == -1 && r.rows.get(18).useCount == 0);
        ok("devoted time of every slot never exceeds its time", allSane(r));

        // focus in the score: the activity's own apps do not count against it
        ok("the focus score is better when the notes app is part of the activity", r.focus > plainRep.focus);
        DayReport onlyNotes = ReportAssembler.assemble(p, evs, det("com.notes", t(at(9, 40), at(10, 40))), true, closeNow, parts);
        DayReport onlyNotesPlain = ReportAssembler.assemble(p, evs, det("com.notes", t(at(9, 40), at(10, 40))), true, closeNow);
        ok("an hour in the notes app costs no focus when it is chosen", onlyNotes.focus == 100 && onlyNotesPlain.focus < 100);

        // saved and read back
        DayReport again = DayReport.parse(r.serialize());
        DayReport.Row ar = again.rows.get(5);
        ok("the new numbers survive saving", ar.partMs == row5.partMs && ar.longestFocusMs == row5.longestFocusMs && ar.useCount == 2 && ar.uses.size() == 2
                && ar.uses.get(1).startMs == row5.uses.get(1).startMs && ar.uses.get(1).app.equals("Insta"));
        ok("saving again gives the same text", again.serialize().equals(r.serialize()));
        String old = plainRep.serialize();
        DayReport oldRead = DayReport.parse(old.replaceAll("(?m)^RF\t.*\n", "").replaceAll("(?m)^RU\t.*\n", ""));
        ok("a report saved before this reads with focus not known", oldRead != null && oldRead.rows.get(5).longestFocusMs == -1 && oldRead.rows.get(5).partMs == 0
                && oldRead.rows.get(5).uses.isEmpty() && oldRead.rows.get(5).focusPct() >= 0);
        ok("a damaged focus line is skipped", DayReport.parse(r.serialize() + "RF\t99\tx\ny\n") != null);
        ok("a row for a slot that is not there is skipped", DayReport.parse(r.serialize() + "RU\t99\t1\t2\tX\n").rows.size() == r.rows.size());

        // the per-type lines
        List<TypeStats.Line> lines = TypeStats.of(r);
        TypeStats.Line study = TypeStats.find(lines, Routine.TASK, "Study");
        ok("a type adds up its slots' numbers", study != null && study.slots >= 2 && study.partMs == 15 * MIN && study.offMs() == 15 * MIN);
        ok("a type's devoted time and focus", study.devotedMs() == study.workedMs - study.offMs() && study.focusPct() >= 0 && study.focusPct() < 100);
        ok("a type's longest focus is the longest slot's", study.longestFocusMs == 120 * MIN);

        // ---------- a break inside the slot
        List<Ev> withBreak = new ArrayList<>(evs);
        withBreak.add(Ev.breakTaken(at(10, 0), at(10, 30), "2026-10-19", 5, "Reading & notes"));
        DayReport br = ReportAssembler.assemble(p, withBreak, u, true, closeNow, parts);
        DayReport.Row brow = br.rows.get(5);
        ok("a break leaves the slot's worked time shorter", brow.breakMs == 30 * MIN && brow.activeMs == 60 * MIN);
        ok("use in the break is not other phone use", brow.useCount == 1 && brow.uses.get(0).startMs == at(10, 40));
        ok("focus does not run across the break", brow.longestFocusMs == 30 * MIN);

        // ---------- what the home screen reads
        long mid = at(10, 20);
        SlotUse.Snapshot snap = SlotUse.snapshot(p, evs, u, parts, mid);
        SlotUse.Info si = snap.of(5);
        DayReport midRep = ReportAssembler.assemble(p, evs, u, true, mid, parts);
        ok("the snapshot has the slots that have started", snap.of(0) != null && snap.of(5) != null && snap.of(6) == null && snap.of(18) == null && snap.day.equals("2026-10-19"));
        ok("the snapshot agrees with the report for the same moment", si.offMs == midRep.rows.get(5).offMs() && si.partMs == midRep.rows.get(5).partMs
                && si.longestFocusMs == midRep.rows.get(5).longestFocusMs && si.episodeCount == midRep.rows.get(5).useCount);
        ok("the slot going on shows its stretch and the focus since", si.offMs == 12 * MIN && si.episodes.size() == 1 && !si.ongoing && si.focusSinceMs == 3 * MIN);
        ok("without usage access the snapshot is empty", SlotUse.snapshot(p, evs, null, parts, mid).bySrc.isEmpty());
        ok("an empty list of events works", SlotUse.snapshot(p, new ArrayList<Ev>(), u, parts, mid).bySrc.size() == snap.bySrc.size());
        SlotUse.Snapshot noParts = SlotUse.snapshot(p, evs, u, null, mid);
        ok("without parts everything is other phone use", noParts.of(5).offMs == 27 * MIN && noParts.of(5).partMs == 0);
        Overrides skip = ov.copy(); Plan.edit(base, skip, d19, 5, true, "");
        Plan.DayPlan pSkip = Plan.plan(base, skip, d19, Z);
        ok("a slot taken off has no numbers", SlotUse.snapshot(pSkip, evs, u, parts, mid).of(5) == null);

        // ---------- random: the numbers agree with counting second by second
        Random rnd = new Random(20261026L);
        int bad = 0, runs = 0;
        for (int round = 0; round < 3000; round++) {
            long base0 = 1_000_000_000L * 1000L;   // a round number of seconds
            int nWin = 1 + rnd.nextInt(3);
            List<long[]> wins = new ArrayList<>();
            long cursor = base0;
            for (int i = 0; i < nWin; i++) {
                cursor += rnd.nextInt(120) * SEC;
                long len = (1 + rnd.nextInt(1800)) * SEC;
                wins.add(new long[]{cursor, cursor + len});
                cursor += len;
            }
            long end = cursor + 600 * SEC;
            String[] pkgs = {"com.a", "com.b", "com.c", "com.notes"};
            Object[] spec = new Object[0];
            List<Object> sp = new ArrayList<>();
            for (String pkg : pkgs) {
                if (rnd.nextInt(3) == 0) continue;
                List<long[]> iv = new ArrayList<>();
                long c2 = base0 - 300 * SEC;
                int k = 1 + rnd.nextInt(6);
                for (int i = 0; i < k; i++) {
                    c2 += rnd.nextInt(900) * SEC;
                    long len = (1 + rnd.nextInt(600)) * SEC;
                    iv.add(new long[]{c2, c2 + len});
                    c2 += len;
                }
                sp.add(pkg);
                sp.add(iv.toArray(new long[0][]));
            }
            UsageDetail ud = det(sp.toArray());
            Set<String> pt = rnd.nextBoolean() ? set("com.notes") : set();
            long nowR = wins.get(wins.size() - 1)[1] - rnd.nextInt(60) * SEC;
            SlotUse.Info in = SlotUse.analyze(wins, ud, pt, nowR);
            runs++;
            // the oracle: every second of every window
            long off = 0, app = 0, longest = 0;
            for (long[] w : wins) {
                int n = (int) ((w[1] - w[0]) / SEC);
                boolean[] isOff = new boolean[n];
                for (int i = 0; i < n; i++) {
                    long s = w[0] + i * SEC;
                    boolean anyApp = false, anyOff = false;
                    for (Map.Entry<String, List<long[]>> en : ud.pkgIntervals.entrySet()) {
                        for (long[] iv : en.getValue()) if (iv[0] <= s && s < iv[1]) {
                            anyApp = true;
                            if (!pt.contains(en.getKey())) anyOff = true;
                        }
                    }
                    if (anyApp) app++;
                    if (anyOff) { off++; isOff[i] = true; }
                }
                // runs of focus; one between two stretches of use and no longer than the joining gap is use too
                int i = 0;
                int firstOff = -1, lastOff = -1;
                for (int j = 0; j < n; j++) if (isOff[j]) { if (firstOff < 0) firstOff = j; lastOff = j; }
                while (i < n) {
                    if (isOff[i]) { i++; continue; }
                    int j = i;
                    while (j < n && !isOff[j]) j++;
                    boolean inner = i > 0 && j < n;   // between two stretches
                    if (!(inner && (j - i) <= 15)) longest = Math.max(longest, (long) (j - i) * SEC);
                    i = j;
                }
            }
            boolean good = in.workedMs == sum(wins) && in.appMs == app * SEC && in.offMs == off * SEC && in.partMs == in.appMs - in.offMs
                    && in.longestFocusMs == longest && in.offMs <= in.appMs && in.devotedMs() <= in.workedMs && in.longestFocusMs <= in.workedMs
                    && in.focusPct() >= 0 && in.focusPct() <= 100 && in.episodeCount >= in.episodes.size();
            // the stretches are inside the windows, in order, apart by more than the joining gap
            long prev = Long.MIN_VALUE;
            for (SlotUse.Episode e : in.episodes) {
                boolean inside = false;
                for (long[] w : wins) if (e.startMs >= w[0] && e.endMs <= w[1]) inside = true;
                if (!inside || e.endMs <= e.startMs || e.startMs < prev) good = false;
                prev = e.endMs;
            }
            long epTotal = 0;
            for (SlotUse.Episode e : in.episodes) epTotal += e.ms();
            if (in.episodeCount == in.episodes.size() && epTotal < in.offMs) good = false;   // a stretch is at least the time in other apps it holds
            if (!good) { bad++; if (bad < 4) System.out.println("   mismatch in round " + round + ": off " + in.offMs + " vs " + off * SEC + ", longest " + in.longestFocusMs + " vs " + longest); }
        }
        ok("3000 random slots agree with counting second by second (" + runs + " checked)", bad == 0);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static long sum(List<long[]> w) { long t = 0; for (long[] x : w) t += x[1] - x[0]; return t; }

    static boolean allSane(DayReport r) {
        for (DayReport.Row x : r.rows) {
            if (x.devotedMs() > x.activeMs || x.partMs > x.phoneMs || x.offMs() > x.phoneMs || x.focusPct() > 100) return false;
            if (x.longestFocusMs > x.activeMs) return false;
        }
        return true;
    }
}
