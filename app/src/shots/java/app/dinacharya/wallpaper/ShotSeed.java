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
        Routine r = Store.load(c);
        Store.save(c, r);
        history(c, r);
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
