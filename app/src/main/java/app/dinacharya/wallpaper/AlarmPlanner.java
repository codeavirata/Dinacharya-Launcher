package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lists the alarms that should ring, from the routine with today's changes applied. Uses no Android classes. */
public final class AlarmPlanner {
    public static final int SLOT = 0, BEDTIME = 1, REASK = 2;
    /** A break taken inside a slot is over, and a slot that was told to start later is due to start. */
    public static final int BREAK_END = 3, START_AGAIN = 4;

    /** Slot number of the "ring a test alarm" event. Real slots are never below 0, so it cannot clash. */
    public static final int TEST_SRC = -2;

    public static boolean isTest(Event e) {
        return e != null && e.src == TEST_SRC;
    }

    /** One alarm. */
    public static final class Event {
        public final long timeMs;
        public final int kind;
        public final String day;
        public final int src;
        public final String title;
        public final int blockKind;
        public final long slotStartMs;
        public final long slotEndMs;
        public final long plannedBedMs;
        public final boolean firstOfDay;
        /** How it alerts: one of {@link Routine#ALERT_RING}, {@link Routine#ALERT_VIBRATE} or {@link Routine#ALERT_SILENT}. */
        public final int mode;
        /** The start-of-day alarm: keeps going until the day is started or delayed. */
        public final boolean wake;

        Event(long timeMs, int kind, String day, int src, String title, int blockKind, long slotStartMs,
              long slotEndMs, long plannedBedMs, boolean firstOfDay) {
            this(timeMs, kind, day, src, title, blockKind, slotStartMs, slotEndMs, plannedBedMs, firstOfDay, Routine.ALERT_RING, false);
        }

        Event(long timeMs, int kind, String day, int src, String title, int blockKind, long slotStartMs,
              long slotEndMs, long plannedBedMs, boolean firstOfDay, int mode, boolean wake) {
            this.timeMs = timeMs;
            this.kind = kind;
            this.day = day;
            this.src = src;
            this.title = title;
            this.blockKind = blockKind;
            this.slotStartMs = slotStartMs;
            this.slotEndMs = slotEndMs;
            this.plannedBedMs = plannedBedMs;
            this.firstOfDay = firstOfDay;
            this.mode = mode;
            this.wake = wake;
        }

        /** True for the two kinds that are timers the person started (a break, a later start) and not slots of the plan. */
        public boolean isTimer() {
            return kind == BREAK_END || kind == START_AGAIN;
        }
    }

    private AlarmPlanner() {
    }

    /**
     * Alarms strictly after {@code afterMs}, earliest first. Slots of the plan are listed only while alarms are on: a slot whose alert is
     * none is left out, and so is a silent one when the silent questions are turned off. The two timers the person starts (a break, a
     * later start) are always listed.
     */
    public static List<Event> upcoming(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone) {
        List<Event> out = new ArrayList<>();
        if (st != null) {
            // A timer asks the way the alarms do; with alarms off it only buzzes.
            int timerMode = base.alarmOn ? Routine.ALERT_RING : Routine.ALERT_VIBRATE;
            if (st.breakTo > afterMs && st.breakFrom > 0) {
                out.add(new Event(st.breakTo, BREAK_END, st.breakDay, st.breakSrc, st.breakTitle, Routine.TASK, st.breakFrom,
                        st.breakSlotEnd, -1L, false, timerMode, false));
            }
            if (st.againAt > afterMs) {
                out.add(new Event(st.againAt, START_AGAIN, st.againDay, st.againSrc, st.againTitle, Routine.TASK, st.againSlotStart,
                        st.againSlotEnd, -1L, false, timerMode, st.againWake && base.alarmOn));
            }
        }
        if (base.alarmOn) {
            Plan.DayPlan now = Plan.resolve(base, ov, afterMs, zone);
            for (LocalDate d = now.day.minusDays(1); !d.isAfter(now.day.plusDays(2)); d = d.plusDays(1)) {
                Plan.DayPlan p = Plan.plan(base, ov, d, zone);
                for (int i = 0; i < p.sc.slots.size(); i++) {
                    Routine.Block b = p.sc.slots.get(i).block;
                    if (b.skipped || b.src < 0 || b.alert == Routine.ALERT_NONE) continue;
                    if (b.alert == Routine.ALERT_SILENT && !base.askSilent) continue;
                    long t = p.slotStartMs(i);
                    if (t <= afterMs) continue;
                    boolean bed = b.kind == Routine.SLEEP;
                    out.add(new Event(t, bed ? BEDTIME : SLOT, d.toString(), b.src, b.title, b.kind, t,
                            p.slotEndMs(i), bed ? t : -1L, i == 0, b.alert, b.wake && b.sounds() && !bed));
                }
            }
            if (st != null && st.reaskAt > afterMs) {
                out.add(new Event(st.reaskAt, REASK, st.reaskDay, st.reaskSrc, st.reaskTitle, Routine.SLEEP,
                        st.reaskBedMs, st.reaskAt, st.reaskBedMs, false));
            }
        }
        Collections.sort(out, new Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return out;
    }

    /** The next event that is an alarm of the plan (a slot, bedtime or a bedtime question), leaving the timers out; null if none. */
    public static Event nextAlarm(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone) {
        for (Event e : upcoming(base, ov, st, afterMs, zone)) if (!e.isTimer()) return e;
        return null;
    }

    public static Event next(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone) {
        List<Event> l = upcoming(base, ov, st, afterMs, zone);
        return l.isEmpty() ? null : l.get(0);
    }
}
