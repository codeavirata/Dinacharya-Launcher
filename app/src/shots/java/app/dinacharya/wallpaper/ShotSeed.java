package app.dinacharya.wallpaper;

import android.content.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives the test build something to show (test build only): Pro switched on, the sample routine saved, and a week of finished days with
 * answers and phone use, so the report, the insights and the compare screens have numbers in them and not just empty states.
 */
final class ShotSeed {
    private ShotSeed() {
    }

    private static final long MIN = 60000L;

    private static final String[][] APPS = {
            {"com.example.insta", "Instagram"}, {"com.example.video", "YouTube"}, {"com.example.chat", "WhatsApp"},
            {"com.example.news", "News"}, {"com.example.game", "Puzzle game"}};

    /** Pro on, setup and tour counted as seen, the routine saved, a week of days made. Safe to call twice. */
    static void run(Context c) {
        Pro.get(c).setDebugPro(true);
        Store.prefs(c).edit()
                .putBoolean(SetupActivity.PREF_SETUP_DONE, true)
                .putBoolean(LauncherActivity.PREF_TOUR_DONE, true)
                .apply();
        // Dinacharya "was" the home app and the emulator's own launcher is the home app now: the Home tab shows the way-back card
        c.getSharedPreferences("homewatch", Context.MODE_PRIVATE).edit().putBoolean("wasHome", true).apply();
        Routine r = Store.load(c);
        Store.save(c, r);
        history(c, r);
    }

    /** The routine the website pictures show: the UPSC day, alarms on, three goals (two of them with a small to-do list). */
    static Routine siteRoutine() {
        Routine r = RoutineTemplates.build(0);
        r.alarmOn = true;
        Routine.Goal gs = Routine.Goal.forSlot(Routine.TASK, "GS study", 150);
        gs.name = "GS study";
        gs.addTodo("Polity: Fundamental Rights");
        gs.addTodo("Revise the notes of yesterday");
        r.goals.add(gs);
        Routine.Goal aw = Routine.Goal.forSlot(Routine.TASK, "Answer writing", 90);
        aw.name = "Answer writing";
        aw.addTodo("Two answers, 150 words each");
        r.goals.add(aw);
        Routine.Goal ex = Routine.Goal.forSlot(Routine.PERSONAL, "Yoga & pranayama", 40);
        ex.name = "Morning yoga";
        r.goals.add(ex);
        return r;
    }

    /** Pro on, no nudge about the home app, the UPSC routine saved with its week of days and the first part of today answered. */
    static void site(Context c) {
        Pro.get(c).setDebugPro(true);
        Store.prefs(c).edit()
                .putBoolean(SetupActivity.PREF_SETUP_DONE, true)
                .putBoolean(LauncherActivity.PREF_TOUR_DONE, true)
                .putBoolean("first_day_done", true)
                .apply();
        c.getSharedPreferences("homewatch", Context.MODE_PRIVATE).edit().putBoolean("wasHome", false).apply();
        Routine r = siteRoutine();
        Store.save(c, r);
        history(c, r);
        today(c, r);
    }

    /** The slots of today that have already started, answered the way a person who follows the day would answer them. */
    private static void today(Context c, Routine r) {
        try {
            ZoneId zone = Store.zone();
            long now = System.currentTimeMillis();
            Plan.DayPlan p = Plan.plan(r, Plan.valid(r, null), LocalDate.now(zone), zone);
            HistoryStore h = Store.history(c);
            String day = p.day.toString();
            h.append(new Ev(p.startMs - 60 * MIN, Ev.ALARMS, day, -1, "1", "", ""));
            int n = 0;
            for (int i = 0; i < p.sc.slots.size(); i++) {
                Routine.Block b = p.sc.slots.get(i).block;
                long s = p.slotStartMs(i);
                long e = p.slotEndMs(i);
                if (e > now - 5 * MIN || b.src < 0 || !b.sounds()) continue;
                h.append(Ev.rang(s, day, b.src, b.title));
                h.append(Ev.ack(s + (1 + i % 3) * MIN, day, b.src, b.title, "K", ""));
                n++;
            }
            ShotTour t = ShotTour.peek();
            if (t != null) t.note("seeded today: " + n + " answered slots, now " + new java.util.Date(now));
        } catch (RuntimeException e) {
            ShotTour t = ShotTour.peek();
            if (t != null) t.note("SEED today failed: " + e);
        }
    }

    private static void history(Context c, Routine r) {
        ZoneId zone = Store.zone();
        LocalDate today = LocalDate.now(zone);
        HistoryStore h = Store.history(c);
        Overrides ov = Plan.valid(r, null);
        for (int back = 1; back <= 7; back++) {
            LocalDate d = today.minusDays(back);
            try {
                Plan.DayPlan p = Plan.plan(r, ov, d, zone);
                if (h.hasReport(p.day.toString())) continue;
                List<Ev> events = new ArrayList<>();
                events.add(new Ev(p.startMs - 60 * MIN, Ev.ALARMS, p.day.toString(), -1, "1", "", ""));
                List<long[]> use = new ArrayList<>();
                Map<String, Long> per = new HashMap<>();
                Map<String, List<long[]>> perIv = new HashMap<>();
                Map<String, String> names = new HashMap<>();
                for (String[] a : APPS) {
                    perIv.put(a[0], new ArrayList<long[]>());
                    names.put(a[0], a[1]);
                }
                for (int i = 0; i < p.sc.slots.size(); i++) {
                    Routine.Block b = p.sc.slots.get(i).block;
                    long s = p.slotStartMs(i);
                    long e = p.slotEndMs(i);
                    int pattern = (back * 7 + i * 3) % 10;
                    if (b.src >= 0 && b.sounds()) {
                        events.add(Ev.rang(s, p.day.toString(), b.src, b.title));
                        if (pattern == 4) {
                            events.add(Ev.missed(s + 5 * MIN, p.day.toString(), b.src, b.title));
                        } else if (pattern == 7) {
                            events.add(Ev.ack(s + 2 * MIN, p.day.toString(), b.src, b.title, "S", ""));
                        } else if (pattern == 2) {
                            events.add(Ev.ack(s + 3 * MIN, p.day.toString(), b.src, b.title, "R", b.title + " (short)"));
                        } else {
                            events.add(Ev.ack(s + (1 + pattern % 3) * MIN, p.day.toString(), b.src, b.title, "K", ""));
                        }
                    }
                    if (b.kind != Routine.SLEEP && e - s > 40 * MIN && pattern % 3 != 0) {
                        String[] app = APPS[(back + i) % APPS.length];
                        long from = s + (10 + pattern * 3) * MIN;
                        long to = Math.min(e - 5 * MIN, from + (4 + pattern * 2) * MIN);
                        if (to > from) {
                            use.add(new long[]{from, to});
                            per.put(app[1], (per.containsKey(app[1]) ? per.get(app[1]) : 0L) + (to - from));
                            perIv.get(app[0]).add(new long[]{from, to});
                        }
                    }
                }
                java.util.Collections.sort(use, (x, y) -> Long.compare(x[0], y[0]));
                Map<String, List<long[]>> byName = new HashMap<>();
                for (String[] a : APPS) byName.put(a[1], perIv.get(a[0]));
                UsageDetail u = new UsageDetail(p.startMs, p.endMs, use, per, byName, perIv, names);
                DayReport rep = ReportAssembler.assemble(p, events, u, true, p.endMs + MIN);
                for (Ev e : events) h.append(e);
                h.saveReport(rep);
            } catch (RuntimeException e) {
                ShotTour t = ShotTour.peek();
                if (t != null) t.note("SEED day -" + back + " failed: " + e);
            }
        }
        ShotTour t = ShotTour.peek();
        if (t != null) t.note("seeded; reports: " + h.reportDays().size());
    }
}
