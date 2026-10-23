package app.dinacharya.wallpaper;

/**
 * Turns a day report into a score. The score has three parts, shown on the Performance page:
 * plan kept (40%), focus (35%) and sleep (25%). A part that cannot be measured is left out and
 * the others share its weight.
 */
public final class Analyzer {
    static final int W_PLAN = 40, W_FOCUS = 35, W_SLEEP = 25;
    /** Phone use in this share of task time or more gives a focus score of 0. */
    static final double FOCUS_ZERO_AT = 0.35;
    static final long MIN_TASK_MS = 10 * 60000L;

    private Analyzer() {
    }

    private static int clamp(double v) {
        return (int) Math.round(Math.max(0, Math.min(100, v)));
    }

    public static void apply(DayReport r, long nowMs) {
        // Plan kept: kept = 1, changed = half, taken off or not answered = 0.
        double pts = 0;
        int n = 0;
        for (DayReport.Row row : r.rows) {
            if (row.kind == Routine.SLEEP) continue;
            switch (row.status) {
                case DayReport.KEPT: pts += 1; n++; break;
                case DayReport.CHANGED: pts += 0.5; n++; break;
                case DayReport.SKIPPED:
                case DayReport.UNANSWERED: n++; break;
                default: break;
            }
        }
        r.planKept = n == 0 ? -1 : clamp(100.0 * pts / n);

        // Focus: how much of your task time the phone was in use.
        r.focus = -1;
        if (r.phoneKnown) {
            long active = 0;
            long phone = 0;
            for (DayReport.Row row : r.rows) {
                if (row.kind != Routine.TASK || row.status == DayReport.SKIPPED) continue;
                active += row.activeMs;
                phone += row.phoneMs;
            }
            if (active >= MIN_TASK_MS) {
                r.focus = clamp(100.0 - 100.0 * ((double) phone / active) / FOCUS_ZERO_AT);
            }
        }

        // Sleep: bedtime within 10 minutes of the plan is full marks, 2 hours late is zero; and how much of the planned sleep you got.
        r.sleep = -1;
        if (r.actualBedMs > 0 && r.plannedBedMs > 0) {
            double late = Math.max(0, (r.actualBedMs - r.plannedBedMs) / 60000.0);
            double bed = clamp(100.0 - Math.max(0, late - 10) * 100.0 / 110.0);
            double asleep = Math.max(0, (r.endMs - r.actualBedMs) / 60000.0);
            double dur = r.plannedSleepMin > 0 ? clamp(100.0 * Math.min(1.0, asleep / r.plannedSleepMin)) : 100;
            r.sleep = clamp(0.5 * bed + 0.5 * dur);
        }

        double sum = 0;
        double wsum = 0;
        if (r.planKept >= 0) { sum += W_PLAN * r.planKept; wsum += W_PLAN; }
        if (r.focus >= 0) { sum += W_FOCUS * r.focus; wsum += W_FOCUS; }
        if (r.sleep >= 0) { sum += W_SLEEP * r.sleep; wsum += W_SLEEP; }
        r.score = wsum == 0 ? -1 : clamp(sum / wsum);
    }

    public static String grade(int score) {
        if (score < 0) return "Not enough data";
        if (score >= 85) return "Excellent";
        if (score >= 70) return "Good";
        if (score >= 50) return "Fair";
        return "Needs work";
    }
}
