package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The timers the person started (a later start, a break, a bedtime question asked again) remember the slot they were for. When the routine
 * is changed afterwards, such a timer can point at a slot that no longer exists, has another name or another end, and would ring for the
 * old routine next to the alarms of the new one. {@link #drop} removes those timers. Uses no Android classes.
 */
public final class StaleTimers {
    private StaleTimers() {
    }

    /** True when {@code day} has a slot number {@code src} called {@code title} that ends at {@code endMs} (or any end when {@code endMs} is 0). */
    private static boolean slotIs(Routine base, Overrides ov, ZoneId zone, String day, int src, String title, long endMs) {
        if (day == null || day.isEmpty() || src < 0) return false;
        try {
            Plan.DayPlan p = Plan.plan(base, ov, LocalDate.parse(day), zone);
            for (int i = 0; i < p.sc.slots.size(); i++) {
                Routine.Block b = p.sc.slots.get(i).block;
                if (b.src != src) continue;
                if (title != null && !title.isEmpty() && !title.equals(b.title)) return false;
                return endMs <= 0 || Math.abs(p.slotEndMs(i) - endMs) < 60000L;
            }
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    /** Removes the timers whose slot is not in the schedule any more. Returns true when anything was removed (the state is then to be saved). */
    public static boolean drop(Routine base, Overrides ov, AlarmState st, ZoneId zone) {
        if (st == null) return false;
        boolean changed = false;
        if (st.againAt > 0 && !slotIs(base, ov, zone, st.againDay, st.againSrc, st.againTitle, st.againSlotEnd)) {
            st.againAt = 0;
            st.againWake = false;
            changed = true;
        }
        if ((st.breakFrom > 0 || st.breakTo > 0) && !slotIs(base, ov, zone, st.breakDay, st.breakSrc, st.breakTitle, st.breakSlotEnd)) {
            st.breakFrom = 0;
            st.breakTo = 0;
            changed = true;
        }
        if (st.reaskAt > 0 && !slotIs(base, ov, zone, st.reaskDay, st.reaskSrc, st.reaskTitle, 0)) {
            st.reaskAt = 0;
            changed = true;
        }
        return changed;
    }
}
