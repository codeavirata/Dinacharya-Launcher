package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Numbers for the Week tab, from the last few closed days. */
public final class WeekStats {
    /** Oldest first. */
    public final List<DayReport> days = new ArrayList<>();
    public int avgScore = -1;
    public int avgPhoneMin = -1;
    public int avgLateMin = -1;
    public int goodDays;
    public String bestSlot = "";
    public int bestPct = -1;
    public String worstSlot = "";
    public int worstPct = -1;

    /** @param newestFirst closed reports, newest first */
    public static WeekStats of(List<DayReport> newestFirst, int n) {
        WeekStats w = new WeekStats();
        for (int i = Math.min(n, newestFirst.size()) - 1; i >= 0; i--) w.days.add(newestFirst.get(i));
        int sc = 0, scN = 0, ph = 0, phN = 0, late = 0, lateN = 0;
        Map<String, int[]> kept = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (DayReport d : w.days) {
            if (d.score >= 0) {
                sc += d.score;
                scN++;
                if (d.score >= 70) w.goodDays++;
            }
            if (d.phoneKnown) {
                ph += (int) Math.round(d.phoneMs / 60000.0);
                phN++;
            }
            if (d.actualBedMs > 0 && d.plannedBedMs > 0) {
                late += (int) Math.max(0, (d.actualBedMs - d.plannedBedMs) / 60000L);
                lateN++;
            }
            for (DayReport.Row r : d.rows) {
                if (r.kind == Routine.SLEEP) continue;
                if (r.status > DayReport.UNANSWERED) continue;
                int[] c = kept.get(r.title);
                if (c == null) {
                    c = new int[2];
                    kept.put(r.title, c);
                    order.add(r.title);
                }
                c[1]++;
                if (r.status == DayReport.KEPT) c[0]++;
            }
        }
        if (scN > 0) w.avgScore = Math.round(sc / (float) scN);
        if (phN > 0) w.avgPhoneMin = Math.round(ph / (float) phN);
        if (lateN > 0) w.avgLateMin = Math.round(late / (float) lateN);
        int best = -1, worst = 101;
        for (String t : order) {
            int[] c = kept.get(t);
            if (c[1] < 3) continue;
            int pct = Math.round(100f * c[0] / c[1]);
            if (pct > best) { best = pct; w.bestSlot = t; w.bestPct = pct; }
            if (pct < worst) { worst = pct; w.worstSlot = t; w.worstPct = pct; }
        }
        if (w.bestSlot.equals(w.worstSlot)) {
            w.worstSlot = "";
            w.worstPct = -1;
        }
        return w;
    }
}
