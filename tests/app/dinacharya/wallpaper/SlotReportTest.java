package app.dinacharya.wallpaper;

import java.util.*;

/**
 * The slot-by-slot analysis of a day report (the numbers for each slot and each activity, the focus judgement, the timeline marks), and
 * the rule for dropdowns of which only one is open at a time.
 */
public class SlotReportTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final long MIN = 60000L;
    static final long T0 = 1_760_000_000_000L;

    static DayReport.Row row(int kind, String sub, String title, int status, long startMin, long lenMin, long active, long phone, long part, long longest, long... eps) {
        DayReport.Row r = new DayReport.Row();
        r.kind = kind;
        r.sub = sub;
        r.title = title;
        r.status = status;
        r.startMs = T0 + startMin * MIN;
        r.endMs = r.startMs + lenMin * MIN;
        r.plannedMin = (int) lenMin;
        r.activeMs = active * MIN;
        r.phoneMs = phone * MIN;
        r.partMs = part * MIN;
        r.longestFocusMs = longest < 0 ? -1 : longest * MIN;
        for (int i = 0; i + 1 < eps.length; i += 2) r.uses.add(new SlotUse.Episode(T0 + eps[i] * MIN, T0 + eps[i + 1] * MIN, "App" + i));
        r.useCount = r.uses.size();
        return r;
    }

    static DayReport report(boolean phoneKnown, DayReport.Row... rows) {
        DayReport d = new DayReport();
        d.day = "2026-10-19";
        d.startMs = T0;
        d.endMs = T0 + 24 * 60 * MIN;
        d.phoneKnown = phoneKnown;
        d.rows.addAll(Arrays.asList(rows));
        return d;
    }

    public static void main(String[] args) {
        // ---------- one open at a time
        OneOpen o = new OneOpen();
        ok("starts with every dropdown closed", o.open() == -1 && !o.isOpen(0));
        ok("tapping one opens it", o.tap(2) == 2 && o.isOpen(2));
        ok("opening another closes the first", o.tap(3) == 3 && o.isOpen(3) && !o.isOpen(2));
        ok("tapping the open one closes it", o.tap(3) == -1 && o.open() == -1);
        o.set(1);
        ok("a number below zero changes nothing", o.tap(-1) == 1 && o.isOpen(1));
        o.closeAll();
        ok("close all", o.open() == -1);
        o.set(-7);
        ok("setting a number below zero closes", o.open() == -1);
        ok("nothing below zero is ever open", !o.isOpen(-1));
        Random rnd = new Random(7);
        OneOpen f = new OneOpen();
        int model = -1;
        boolean fine = true;
        for (int i = 0; i < 20000; i++) {
            int n = rnd.nextInt(9) - 1;
            int act = rnd.nextInt(4);
            if (act == 0) { f.tap(n); if (n >= 0) model = model == n ? -1 : n; }
            else if (act == 1) { f.set(n); model = n < 0 ? -1 : n; }
            else if (act == 2) { f.closeAll(); model = -1; }
            else { f.tap(n); if (n >= 0) model = model == n ? -1 : n; }
            int count = 0;
            for (int k = 0; k < 9; k++) if (f.isOpen(k)) count++;
            if (count > 1 || f.open() != model || (model >= 0 && !f.isOpen(model))) { fine = false; break; }
        }
        ok("20000 random taps: never more than one open, always as the model says", fine);

        // ---------- the judgement of a focus
        ok("tone: unknown", SlotReport.tone(-1) == SlotReport.TONE_NONE && SlotReport.tone(-50) == SlotReport.TONE_NONE);
        ok("tone: good from 75", SlotReport.tone(75) == SlotReport.TONE_GOOD && SlotReport.tone(100) == SlotReport.TONE_GOOD && SlotReport.tone(74) == SlotReport.TONE_FAIR);
        ok("tone: fair from 50, low below", SlotReport.tone(50) == SlotReport.TONE_FAIR && SlotReport.tone(49) == SlotReport.TONE_LOW && SlotReport.tone(0) == SlotReport.TONE_LOW);
        ok("dur", SlotReport.dur(0).equals("0 min") && SlotReport.dur(-5).equals("0 min") && SlotReport.dur(30000).equals("<1 min")
                && SlotReport.dur(14 * MIN).equals("14 min") && SlotReport.dur(65 * MIN).equals("1h 5m") && SlotReport.dur(120 * MIN).equals("2h"));

        // ---------- slots
        DayReport.Row sleep = row(Routine.SLEEP, "", "Sleep", DayReport.UNTRACKED, 0, 420, 420, 0, 0, -1);
        DayReport.Row study = row(Routine.TASK, "Study", "Study", DayReport.KEPT, 480, 60, 60, 20, 8, 38, 520, 532, 535, 539);
        DayReport.Row off = row(Routine.REST, "Walk", "Walk", DayReport.SKIPPED, 560, 30, 0, 0, 0, -1);
        DayReport.Row later = row(Routine.TASK, "Study", "Study 2", DayReport.UNANSWERED, 900, 60, 0, 0, 0, -1);
        DayReport.Row lunch = row(Routine.MEAL, "Lunch", "Lunch", DayReport.CHANGED, 720, 40, 40, 5, 0, 20, 730, 735);
        lunch.newTitle = "Late lunch";
        DayReport d = report(true, sleep, study, off, later, lunch);

        List<SlotReport.Item> sl = SlotReport.slots(d);
        ok("only the slots that ran: not sleep, not taken off, not waiting", sl.size() == 2 && sl.get(0).title.equals("Study") && sl.get(1).title.startsWith("Lunch"));
        ok("a renamed slot shows both names", sl.get(1).title.equals("Lunch  →  Late lunch"));
        SlotReport.Item s = sl.get(0);
        ok("study: other phone use is the phone time without the activity's own apps", s.offMs() == 12 * MIN && s.partMs == 8 * MIN && s.phoneMs == 20 * MIN);
        ok("study: devoted = the slot without other phone use", s.devotedMs() == 48 * MIN);
        ok("study: focus 80% and the tone is good", s.focusPct() == 80 && s.tone() == SlotReport.TONE_GOOD);
        ok("study: longest focus and the stretches", s.longestFocusMs == 38 * MIN && s.useCount == 2 && s.uses.size() == 2 && s.hasTimes() && !s.timesCut());
        ok("study: the summary line", s.summary().equals("Devoted 48 min  ·  Other phone use 12 min"));
        ok("lunch: 5 minutes of other use in 40", sl.get(1).offMs() == 5 * MIN && sl.get(1).focusPct() == 88 && sl.get(1).tone() == SlotReport.TONE_GOOD);
        ok("slots that did not run are counted apart", SlotReport.others(d) == 2);

        DayReport.Row clean = row(Routine.TASK, "Study", "Clean", DayReport.KEPT, 0, 30, 30, 0, 0, 30);
        SlotReport.Item cs = SlotReport.slots(report(true, clean)).get(0);
        ok("no other phone use: full focus and says so", cs.offMs() == 0 && cs.focusPct() == 100 && cs.summary().equals("Devoted 30 min  ·  No other phone use"));
        DayReport.Row onlyPart = row(Routine.TASK, "Study", "Notes only", DayReport.KEPT, 0, 30, 30, 10, 10, 30);
        SlotReport.Item ps = SlotReport.slots(report(true, onlyPart)).get(0);
        ok("phone use only in the activity's apps is not other phone use", ps.offMs() == 0 && ps.focusPct() == 100 && ps.devotedMs() == 30 * MIN);

        // an unknown phone: nothing is claimed
        SlotReport.Item un = SlotReport.slots(report(false, study)).get(0);
        ok("phone use not available: nothing about it is claimed", !un.known && un.focusPct() == -1 && un.tone() == SlotReport.TONE_NONE && un.offMs() == 0
                && un.longestFocusMs == -1 && un.uses.isEmpty() && un.summary().equals("Phone use was not available") && !un.hasTimes());
        // a report from before the exact times were kept
        DayReport.Row oldRow = row(Routine.TASK, "Study", "Old", DayReport.KEPT, 0, 30, 30, 10, 0, -1);
        SlotReport.Item old = SlotReport.slots(report(true, oldRow)).get(0);
        ok("an older report: numbers shown, exact times not", old.focusPct() == 67 && !old.hasTimes() && old.longestFocusMs == -1);
        // phone time longer than the slot cannot make a negative time
        DayReport.Row odd = row(Routine.TASK, "Study", "Odd", DayReport.KEPT, 0, 30, 30, 50, 0, 0);
        SlotReport.Item os = SlotReport.slots(report(true, odd)).get(0);
        ok("phone time longer than the slot: devoted 0, focus 0", os.devotedMs() == 0 && os.focusPct() == 0);
        DayReport.Row moreParts = row(Routine.TASK, "Study", "Parts", DayReport.KEPT, 0, 30, 30, 10, 25, 30);
        ok("an activity part longer than the phone time is cut to it", SlotReport.slots(report(true, moreParts)).get(0).partMs == 10 * MIN
                && SlotReport.slots(report(true, moreParts)).get(0).offMs() == 0);
        // cut stretches
        DayReport.Row cut = row(Routine.TASK, "Study", "Cut", DayReport.KEPT, 0, 60, 60, 30, 0, 5, 1, 2);
        cut.useCount = 55;
        SlotReport.Item cutI = SlotReport.slots(report(true, cut)).get(0);
        ok("more stretches than kept: says so", cutI.timesCut() && cutI.useCount == 55);

        // ---------- the whole day
        SlotReport.Item all = SlotReport.day(d);
        ok("day: totals", all.slots == 2 && all.workedMs == 100 * MIN && all.phoneMs == 25 * MIN && all.partMs == 8 * MIN && all.offMs() == 17 * MIN);
        ok("day: focus and devoted", all.focusPct() == 83 && all.devotedMs() == 83 * MIN);
        ok("day: the longest focus is the best of the slots", all.longestFocusMs == 38 * MIN);
        SlotReport.Item none = SlotReport.day(report(true, sleep, off));
        ok("day with no slot that ran: nothing known", none.slots == 0 && none.workedMs == 0 && none.focusPct() == -1 && none.longestFocusMs == -1);
        ok("day with unknown phone", SlotReport.day(report(false, study)).focusPct() == -1);

        // ---------- activities
        DayReport.Row s2 = row(Routine.TASK, "Study", "Study again", DayReport.KEPT, 600, 30, 30, 6, 0, 20, 605, 611);
        s2.apps.add(new DayReport.App("Notes", 4 * MIN));
        s2.apps.add(new DayReport.App("Instagram", 2 * MIN));
        study.apps.add(new DayReport.App("Instagram", 12 * MIN));
        DayReport.Row walk = row(Routine.REST, "Walk", "Walk", DayReport.KEPT, 700, 100, 100, 0, 0, 100);
        List<SlotReport.Item> acts = SlotReport.activities(report(true, study, s2, walk, off));
        ok("activities: one entry per type that ran, the most time first", acts.size() == 2 && acts.get(0).title.equals("Walk") && acts.get(1).title.equals("Study"));
        SlotReport.Item st = acts.get(1);
        ok("activities: slots, time and phone add up", st.slots == 2 && st.workedMs == 90 * MIN && st.phoneMs == 26 * MIN && st.partMs == 8 * MIN && st.offMs() == 18 * MIN);
        ok("activities: devoted and focus", st.devotedMs() == 72 * MIN && st.focusPct() == 80);
        ok("activities: longest focus is the best slot's, stretches are counted", st.longestFocusMs == 38 * MIN && st.useCount == 3);
        ok("activities: apps added up, longest first", st.apps.size() == 2 && st.apps.get(0).name.equals("Instagram") && st.apps.get(0).ms == 14 * MIN && st.apps.get(1).name.equals("Notes"));
        ok("activities: a type with only a slot taken off is not listed", SlotReport.activities(report(true, off)).isEmpty());
        ok("activities: with an unknown phone, no phone numbers", SlotReport.activities(report(false, study)).get(0).focusPct() == -1);

        // ---------- the timeline
        SlotReport.Item tl = SlotReport.slots(report(true, study)).get(0);
        List<float[]> marks = SlotReport.marks(tl);
        ok("timeline: one mark for each stretch, between 0 and 1", marks.size() == 2 && marks.get(0)[0] > 0 && marks.get(1)[1] <= 1);
        ok("timeline: marks sit at the right places", Math.abs(marks.get(0)[0] - 40f / 60f) < 0.001 && Math.abs(marks.get(0)[1] - 52f / 60f) < 0.001);
        ok("timeline: marks are in order and never backwards", marks.get(0)[1] <= marks.get(1)[0] && marks.get(1)[0] <= marks.get(1)[1]);
        DayReport.Row early = row(Routine.TASK, "Study", "Early", DayReport.KEPT, 100, 30, 30, 5, 0, 25, 95, 100);
        SlotReport.Item ei = SlotReport.slots(report(true, early)).get(0);
        ok("timeline: a stretch before the slot began widens the timeline, nothing falls outside it", SlotReport.spanStartMs(ei) == T0 + 95 * MIN
                && SlotReport.marks(ei).get(0)[0] == 0f);
        ok("timeline: no stretches, no marks", SlotReport.marks(SlotReport.slots(report(true, clean)).get(0)).isEmpty());
        DayReport.Row flat = row(Routine.TASK, "Study", "Flat", DayReport.KEPT, 0, 0, 5, 1, 0, 4);
        flat.endMs = flat.startMs;
        ok("timeline: a slot with no length has no marks", SlotReport.marks(SlotReport.slots(report(true, flat)).get(0)).isEmpty());

        // ---------- saved and read back: the same analysis
        DayReport back = DayReport.parse(report(true, sleep, study, off, later, lunch).serialize());
        List<SlotReport.Item> b2 = SlotReport.slots(back);
        boolean same = b2.size() == sl.size();
        for (int i = 0; same && i < sl.size(); i++) {
            SlotReport.Item x = sl.get(i), y = b2.get(i);
            same = x.title.equals(y.title) && x.workedMs == y.workedMs && x.phoneMs == y.phoneMs && x.partMs == y.partMs
                    && x.longestFocusMs == y.longestFocusMs && x.useCount == y.useCount && x.uses.size() == y.uses.size()
                    && x.focusPct() == y.focusPct() && x.devotedMs() == y.devotedMs();
        }
        ok("saved and read back: the same analysis", same);

        // ---------- fuzz: whatever a report holds, the numbers stay sensible
        Random r = new Random(41);
        boolean good = true;
        String why = "";
        for (int round = 0; round < 4000 && good; round++) {
            List<DayReport.Row> rows = new ArrayList<>();
            int n = r.nextInt(7);
            for (int i = 0; i < n; i++) {
                int kind = r.nextInt(5);
                long len = r.nextInt(200);
                long act = r.nextInt(3) == 0 ? 0 : r.nextInt((int) len + 30);
                long ph = r.nextInt(250);
                long pt = r.nextInt(250);
                long lf = r.nextInt(4) == 0 ? -1 : r.nextInt(200);
                long[] eps = new long[r.nextInt(5) * 2];
                for (int k = 0; k < eps.length; k += 2) { long a = r.nextInt(300); eps[k] = a; eps[k + 1] = a + r.nextInt(40); }
                DayReport.Row w = row(kind, r.nextBoolean() ? "Study" : "Other", "T" + i, r.nextInt(6), r.nextInt(600), len, act, ph, pt, lf, eps);
                rows.add(w);
            }
            DayReport dr = report(r.nextInt(5) != 0, rows.toArray(new DayReport.Row[0]));
            List<SlotReport.Item> items = new ArrayList<>(SlotReport.slots(dr));
            items.addAll(SlotReport.activities(dr));
            items.add(SlotReport.day(dr));
            for (SlotReport.Item it : items) {
                int pct = it.focusPct();
                if (pct < -1 || pct > 100) { good = false; why = "pct " + pct; }
                if (it.devotedMs() < 0 || it.devotedMs() > it.workedMs) { good = false; why = "devoted " + it.devotedMs() + " of " + it.workedMs; }
                if (it.offMs() < 0 || it.offMs() > it.phoneMs) { good = false; why = "off " + it.offMs() + " of " + it.phoneMs; }
                if (it.known && it.workedMs > 0 && pct < 0) { good = false; why = "known but no pct"; }
                if (!it.known && (pct != -1 || it.offMs() != 0)) { good = false; why = "unknown claims"; }
                if (it.summary() == null || it.summary().isEmpty()) { good = false; why = "summary"; }
                if (it.tone() < -1 || it.tone() > 2) { good = false; why = "tone"; }
                float prev = 0;
                for (float[] m : SlotReport.marks(it)) {
                    if (m[0] < 0 || m[1] > 1 || m[1] < m[0]) { good = false; why = "mark " + m[0] + ".." + m[1]; }
                    prev = m[1];
                }
            }
            // every slot listed has run
            for (SlotReport.Item it : SlotReport.slots(dr)) if (it.workedMs <= 0) { good = false; why = "listed without time"; }
        }
        ok("fuzz: 4000 random reports keep every number in range (" + why + ")", good);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
