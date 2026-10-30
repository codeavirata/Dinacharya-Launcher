package app.dinacharya.wallpaper;

import java.util.*;

/**
 * The habit notes: split into a heading, what was seen and what to try, with the same words as the plain tips they replace. Checked against
 * a copy of the old tips on thousands of random days, plus the cases that matter one by one.
 */
public class HabitsTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final long MIN = 60000L;
    static final long T0 = 1_760_000_000_000L;

    static DayReport.Row row(int kind, String title, int status, String newTitle) {
        DayReport.Row r = new DayReport.Row();
        r.kind = kind;
        r.title = title;
        r.status = status;
        r.newTitle = newTitle;
        r.activeMs = 60 * MIN;
        r.plannedMin = 60;
        return r;
    }

    static DayReport day(String date, DayReport.Row... rows) {
        DayReport d = new DayReport();
        d.day = date;
        d.alarmsOn = true;
        d.phoneKnown = true;
        d.startMs = T0;
        d.endMs = T0 + 24 * 60 * MIN;
        d.rows.addAll(Arrays.asList(rows));
        return d;
    }

    static DayReport randomDay(Random r, int n) {
        String[] titles = {"Study", "Walk", "Lunch", "Reading", "Project work"};
        int[] kinds = {Routine.TASK, Routine.REST, Routine.MEAL, Routine.TASK, Routine.TASK};
        DayReport.Row[] rows = new DayReport.Row[r.nextInt(6)];
        for (int i = 0; i < rows.length; i++) {
            int t = r.nextInt(titles.length);
            int st = r.nextInt(6);
            DayReport.Row w = row(kinds[t], titles[t], st, st == DayReport.CHANGED ? "Else" + r.nextInt(2) : "");
            w.activeMs = r.nextInt(120) * MIN;
            w.phoneMs = r.nextInt(100) * MIN;
            w.partMs = r.nextInt(3) == 0 ? Math.min(w.phoneMs, r.nextInt(50) * MIN) : 0;
            rows[i] = w;
        }
        DayReport d = day("2026-10-" + (10 + n), rows);
        d.alarmsOn = r.nextInt(5) != 0;
        d.phoneKnown = r.nextInt(5) != 0;
        d.phoneMs = r.nextInt(400) * MIN;
        if (r.nextBoolean()) d.apps.add(new DayReport.App("Insta", r.nextInt(200) * MIN));
        d.beforeBedMs = r.nextInt(3) == 0 ? -1 : r.nextInt(60) * MIN;
        d.plannedBedMs = T0 + 1300 * MIN;
        d.actualBedMs = r.nextInt(4) == 0 ? -1 : d.plannedBedMs + r.nextInt(120) * MIN;
        d.pauses = r.nextInt(8);
        d.wentBack = r.nextInt(6);
        d.openedAfter = r.nextInt(6);
        d.pausedMost = r.nextBoolean() ? "Insta" : "";
        d.score = r.nextInt(4) == 0 ? -1 : r.nextInt(100);
        return d;
    }

    public static void main(String[] args) {
        Routine base = Routine.sample();

        // ---------- the words did not change
        Random rnd = new Random(11);
        boolean same = true;
        int nonEmpty = 0;
        String why = "";
        Set<String> heads = new TreeSet<>();
        for (int round = 0; round < 6000 && same; round++) {
            DayReport latest = rnd.nextInt(6) == 0 ? null : randomDay(rnd, 0);
            List<DayReport> past = new ArrayList<>();
            int np = rnd.nextInt(9);
            for (int i = 0; i < np; i++) past.add(randomDay(rnd, i + 1));
            List<String> now = Suggestions.generate(latest, past, base);
            List<String> then = SuggestionsOracle.generate(latest, past, base);
            if (!now.equals(then)) { same = false; why = now + " vs " + then; }
            List<Suggestions.Habit> hs = Suggestions.habits(latest, past, base);
            if (hs.size() != now.size()) { same = false; why = "size"; }
            for (int i = 0; same && i < hs.size(); i++) {
                Suggestions.Habit h = hs.get(i);
                if (!h.text().equals(now.get(i))) { same = false; why = "text " + h.text(); }
                if (h.head.isEmpty() || h.head.trim().length() != h.head.length()) { same = false; why = "head '" + h.head + "'"; }
                if (h.tone < 0 || h.tone > 2) { same = false; why = "tone"; }
                if (h.saw.isEmpty() && h.advice.isEmpty()) { same = false; why = "empty"; }
                if (h.text().trim().length() != h.text().length() || h.text().contains("  ")) { same = false; why = "spaces in '" + h.text() + "'"; }
                heads.add(h.head);
            }
            if (hs.size() > Suggestions.MAX_TIPS) { same = false; why = "too many"; }
            if (!hs.isEmpty()) nonEmpty++;
        }
        ok("6000 random days: the tips read exactly as before, and each habit has a heading, a tone and its pieces" + (same ? "" : " (" + why + ")"), same);
        ok("the random days reached many kinds of habit (" + heads.size() + ")", heads.size() >= 10);
        ok("a habit is always found for a day with any history", nonEmpty > 5000);

        // ---------- cases one by one
        List<DayReport> skipped = new ArrayList<>();
        for (int i = 0; i < 4; i++) skipped.add(day("2026-10-" + (10 + i), row(Routine.TASK, "Study", DayReport.SKIPPED, "")));
        List<Suggestions.Habit> a = Suggestions.habits(null, skipped, base);
        Suggestions.Habit h0 = null;
        for (Suggestions.Habit h : a) if (h.head.equals("A slot you often take off")) h0 = h;
        ok("a slot taken off often: watch, with what was seen and what to try", h0 != null && h0.tone == Suggestions.Habit.WATCH
                && h0.saw.equals("You took off “Study” on 4 of the last 4 days.") && h0.advice.equals("Make it shorter, or move it to a quieter time."));

        List<DayReport> kept = new ArrayList<>();
        for (int i = 0; i < 6; i++) kept.add(day("2026-10-" + (10 + i), row(Routine.TASK, "Study", DayReport.KEPT, "")));
        Suggestions.Habit good = null;
        for (Suggestions.Habit h : Suggestions.habits(null, kept, base)) if (h.head.equals("A habit that works")) good = h;
        ok("a slot kept six days running: going well", good != null && good.tone == Suggestions.Habit.GOOD && good.saw.contains("6 days in a row"));

        DayReport off = day("2026-10-12");
        off.alarmsOn = false;
        off.phoneKnown = false;
        List<Suggestions.Habit> setup = Suggestions.habits(off, new ArrayList<DayReport>(), base);
        ok("alarms off and no usage access: two things to set up, first", setup.size() >= 2 && setup.get(0).tone == Suggestions.Habit.SETUP && setup.get(1).tone == Suggestions.Habit.SETUP
                && setup.get(0).saw.isEmpty() && !setup.get(0).advice.isEmpty());

        List<Suggestions.Habit> none = Suggestions.habits(null, new ArrayList<DayReport>(), base);
        ok("no days at all: one note, nothing to measure yet", none.size() == 1 && none.get(0).head.equals("Not enough days yet") && none.get(0).tone == Suggestions.Habit.SETUP);

        DayReport solid = day("2026-10-12");
        solid.score = 85;
        List<Suggestions.Habit> sol = Suggestions.habits(solid, new ArrayList<DayReport>(), base);
        boolean hasSolid = false;
        for (Suggestions.Habit h : sol) if (h.head.equals("A solid day")) hasSolid = h.tone == Suggestions.Habit.GOOD;
        ok("a good day with nothing to flag: a solid day, going well", sol.size() == 1 && hasSolid);

        // text() of the pieces
        Suggestions.Habit only = new Suggestions.Habit(Suggestions.Habit.GOOD, "H", "Saw.", "");
        Suggestions.Habit both = new Suggestions.Habit(Suggestions.Habit.GOOD, "H", "Saw.", "Do.");
        Suggestions.Habit adv = new Suggestions.Habit(Suggestions.Habit.SETUP, "H", "", "Do.");
        ok("text(): one piece or two", only.text().equals("Saw.") && both.text().equals("Saw. Do.") && adv.text().equals("Do."));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
