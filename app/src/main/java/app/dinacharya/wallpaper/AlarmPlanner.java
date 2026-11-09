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
    /** The question "plan your day now, later, or leave it" after the day was taken off and the person chose to be asked later. */
    public static final int PLAN_ASK = 5;

    /** Slot number of the "ring a test alarm" event. Real slots are never below 0, so it cannot clash. */
    public static final int TEST_SRC = -2;

    /**
     * True when the slot {@code src} of {@code day} already began (started early, or the slot before it was skipped) before its planned time
     * {@code plannedMs}. Its own alarm and notification are not wanted then, even if one was set before the person began it.
     */
    public static boolean begunEarly(java.util.List<Ev> events, String day, int src, long plannedMs) {
        if (events == null || src < 0) return false;
        for (Ev e : events) {
            if (!day.equals(e.day)) continue;
            boolean early = Ev.EARLY.equals(e.type) && e.src == src;
            boolean skip = Ev.SKIP.equals(e.type) && Ev.srcOf(e.b, Integer.MIN_VALUE) == src;
            if (!early && !skip) continue;
            // a slot changed in the middle is several parts with one number: only the part that began is meant (older entries do not say which)
            long part = Ev.partOf(e.b);
            if (part > 0L && Math.abs(part - plannedMs) >= 60000L) continue;
            if (e.aLong(plannedMs) < plannedMs - 60000L) return true;
        }
        return false;
    }

    /**
     * True when the person already went to sleep for this night before {@code bedMs} (they answered the bedtime question, or chose early
     * sleep), so a bedtime alarm set for {@code bedMs} is not wanted. Unlike {@link #begunEarly} this does not depend on a slot's number, so
     * it still holds after the routine was edited.
     */
    public static boolean sleptAlready(java.util.List<Ev> events, String day, long bedMs) {
        if (events == null) return false;
        for (Ev e : events) {
            if (Ev.SLEEP.equals(e.type) && day.equals(e.day) && e.ts <= bedMs && e.ts > bedMs - 12 * 3600000L) return true;
        }
        return false;
    }

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

        /** True for the kinds that are timers the person started (a break, a later start, a plan question) and not slots of the plan. */
        public boolean isTimer() {
            return kind == BREAK_END || kind == START_AGAIN || kind == PLAN_ASK;
        }

        /** The same alarm with another way of alerting (library mode turns a ring into a vibration). */
        public Event withMode(int newMode) {
            if (newMode == mode) return this;
            return new Event(timeMs, kind, day, src, title, blockKind, slotStartMs, slotEndMs, plannedBedMs, firstOfDay, newMode, wake);
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
        return upcoming(base, ov, st, afterMs, zone, null);
    }

    /**
     * Same, with library mode: an alarm that falls while library mode is on only vibrates. A day that was taken off has no alarms from
     * the moment it was taken off, and a slot that was started before its time has no alarm of its own.
     */
    public static List<Event> upcoming(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone, LibraryMode lib) {
        List<Event> out = new ArrayList<>();
        if (st != null) {
            // A timer asks the way the alarms do; with alarms off it only buzzes. Library mode makes it a buzz as well.
            int timerMode = base.alarmOn ? Routine.ALERT_RING : Routine.ALERT_VIBRATE;
            // Taking the day off drops the timers that were waiting; one that is somehow still there rings no more than the slots do.
            if (st.breakTo > afterMs && st.breakFrom > 0 && !dayOffAt(base, st, st.breakTo)) {
                out.add(new Event(st.breakTo, BREAK_END, st.breakDay, st.breakSrc, st.breakTitle, Routine.TASK, st.breakFrom,
                        st.breakSlotEnd, -1L, false, LibraryMode.quietMode(lib, st.breakTo, zone, timerMode), false));
            }
            if (st.againAt > afterMs && !dayOffAt(base, st, st.againAt)) {
                out.add(new Event(st.againAt, START_AGAIN, st.againDay, st.againSrc, st.againTitle, Routine.TASK, st.againSlotStart,
                        st.againSlotEnd, -1L, false, LibraryMode.quietMode(lib, st.againAt, zone, timerMode), st.againWake && base.alarmOn));
            }
            if (st.planAt > afterMs && DayOff.pending(base, st, afterMs)) {
                out.add(new Event(st.planAt, PLAN_ASK, st.offDay, -1, "", Routine.TASK, st.planAt, st.planAt, -1L, false,
                        LibraryMode.quietMode(lib, st.planAt, zone, timerMode), false));
            }
        }
        if (base.alarmOn) {
            Plan.DayPlan now = Plan.resolve(base, ov, afterMs, zone);
            for (LocalDate d = now.day.minusDays(1); !d.isAfter(now.day.plusDays(2)); d = d.plusDays(1)) {
                Plan.DayPlan p = Plan.plan(base, ov, d, zone);
                boolean off = DayOff.active(base, st, d);
                for (int i = 0; i < p.sc.slots.size(); i++) {
                    Routine.Block b = p.sc.slots.get(i).block;
                    if (b.skipped || b.src < 0 || b.alert == Routine.ALERT_NONE) continue;
                    if (b.alert == Routine.ALERT_SILENT && !base.askSilent) continue;
                    long t = p.slotStartMs(i);
                    if (t <= afterMs) continue;
                    if (off && t >= st.offFrom) continue;
                    if (st != null && st.skipsAlarm(d.toString(), b.src)) continue;
                    boolean bed = b.kind == Routine.SLEEP;
                    out.add(new Event(t, bed ? BEDTIME : SLOT, d.toString(), b.src, b.title, b.kind, t,
                            p.slotEndMs(i), bed ? t : -1L, i == 0, LibraryMode.quietMode(lib, t, zone, b.alert), b.wake && b.sounds() && !bed));
                }
            }
            if (st != null && st.reaskAt > afterMs && !DayOff.pending(base, st, afterMs)) {
                out.add(new Event(st.reaskAt, REASK, st.reaskDay, st.reaskSrc, st.reaskTitle, Routine.SLEEP,
                        st.reaskBedMs, st.reaskAt, st.reaskBedMs, false, LibraryMode.quietMode(lib, st.reaskAt, zone, Routine.ALERT_RING), false));
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

    /** True when a day that was taken off is still off at {@code atMs} and that moment is after the day was taken off. */
    private static boolean dayOffAt(Routine base, AlarmState st, long atMs) {
        return st != null && st.offFrom <= atMs && DayOff.pending(base, st, atMs);
    }

    /** The next event that is an alarm of the plan (a slot, bedtime or a bedtime question), leaving the timers out; null if none. */
    public static Event nextAlarm(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone) {
        return nextAlarm(base, ov, st, afterMs, zone, null);
    }

    public static Event nextAlarm(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone, LibraryMode lib) {
        for (Event e : upcoming(base, ov, st, afterMs, zone, lib)) if (!e.isTimer()) return e;
        return null;
    }

    public static Event next(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone) {
        return next(base, ov, st, afterMs, zone, null);
    }

    public static Event next(Routine base, Overrides ov, AlarmState st, long afterMs, ZoneId zone, LibraryMode lib) {
        List<Event> l = upcoming(base, ov, st, afterMs, zone, lib);
        return l.isEmpty() ? null : l.get(0);
    }
}
