package app.dinacharya.wallpaper;

import android.content.Context;

import java.time.LocalDate;

/**
 * What the answers to a slot question do, shared by the question screen, the notification buttons and the Home screen:
 * start now, start after some minutes, carry the slot before on, take a break. Each one writes what happened to the history and sets
 * the next alarm. Everything stays inside the one slot, so the slots after it do not move.
 */
final class SlotActions {
    static final String ACTION_START_NOW = "app.dinacharya.wallpaper.SLOT_START_NOW";
    static final String ACTION_START_LATER = "app.dinacharya.wallpaper.SLOT_START_LATER";
    static final int NOTIFICATION_LATER_MIN = 10;

    private SlotActions() {
    }

    /** The slot before the one that is starting, when it can be carried on. */
    static final class Prev {
        final int src;
        final String title;

        Prev(int src, String title) {
            this.src = src;
            this.title = title;
        }
    }

    static Prev previous(Context c, AlarmPlanner.Event e) {
        try {
            Routine base = Store.load(c);
            Overrides ov = Store.overrides(c, base);
            Plan.DayPlan p = Plan.plan(base, ov, LocalDate.parse(e.day), Store.zone());
            int i = SlotPlay.indexOfSrc(p, e.src);
            int prev = i < 0 ? -1 : SlotPlay.previousIndex(p, i);
            if (prev < 0) return null;
            Routine.Block b = p.sc.slots.get(prev).block;
            return new Prev(b.src, b.title);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void finish(Context c) {
        SlotNow.changed();
        AlarmScheduler.reschedule(c);
        DayCloser.schedule(c);
    }

    /** "Start now": the slot goes ahead as planned. */
    static void startNow(Context c, AlarmPlanner.Event e) {
        long now = System.currentTimeMillis();
        Store.history(c).append(Ev.ack(now, e.day, e.src, e.title, "C", ""));
        AlarmState st = Store.alarmState(c);
        if (st.againAt > 0 && st.againSrc == e.src) {
            st.againAt = 0;
            st.againWake = false;
            Store.saveAlarmState(c, st);
        }
        finish(c);
    }

    /** "Start after N min": the slot's first minutes are not worked, and it asks again then. Returns when. */
    static long startAfter(Context c, AlarmPlanner.Event e, int minutes) {
        long now = System.currentTimeMillis();
        long until = SlotPlay.startAfter(now, minutes, e.slotEndMs > 0 ? e.slotEndMs : now + minutes * 60000L + 120000L);
        Store.history(c).append(Ev.ack(now, e.day, e.src, e.title, "C", ""));
        Store.history(c).append(Ev.delay(now, e.day, e.src, e.title, until));
        AlarmState st = Store.alarmState(c);
        st.againAt = until;
        st.againDay = e.day;
        st.againSrc = e.src;
        st.againTitle = e.title;
        st.againSlotStart = e.slotStartMs;
        st.againSlotEnd = e.slotEndMs;
        st.againWake = e.wake;
        Store.saveAlarmState(c, st);
        finish(c);
        return until;
    }

    /** "Continue the slot before": it goes on for some minutes of this slot, then this one is asked about again. Returns when. */
    static long continuePrevious(Context c, AlarmPlanner.Event e, Prev prev, int minutes) {
        long now = System.currentTimeMillis();
        long until = SlotPlay.continueUntil(now, minutes, e.slotEndMs);
        Store.history(c).append(Ev.ack(now, e.day, e.src, e.title, "C", ""));
        Store.history(c).append(Ev.extend(now, e.day, e.src, prev.src, prev.title, until));
        AlarmState st = Store.alarmState(c);
        st.againAt = until;
        st.againDay = e.day;
        st.againSrc = e.src;
        st.againTitle = e.title;
        st.againSlotStart = e.slotStartMs;
        st.againSlotEnd = e.slotEndMs;
        st.againWake = false;
        Store.saveAlarmState(c, st);
        finish(c);
        return until;
    }

    /** "Take a break" in the slot that is running. Returns when it ends, or -1 when no slot can take one now. */
    static long takeBreak(Context c, int minutes) {
        try {
            Routine base = Store.load(c);
            Overrides ov = Store.overrides(c, base);
            long now = System.currentTimeMillis();
            Plan.DayPlan p = Plan.resolve(base, ov, now, Store.zone());
            if (!SlotPlay.canBreak(p)) return -1;
            Routine.Block b = p.sc.slots.get(p.cur).block;
            long slotEnd = p.slotEndMs(p.cur);
            long end = SlotPlay.breakEnd(now, minutes, slotEnd);
            if (end <= now) return -1;
            AlarmState st = Store.alarmState(c);
            st.breakFrom = now;
            st.breakTo = end;
            st.breakDay = p.day.toString();
            st.breakSrc = b.src;
            st.breakTitle = b.title;
            st.breakSlotEnd = slotEnd;
            Store.saveAlarmState(c, st);
            Store.history(c).append(Ev.breakTaken(now, end, st.breakDay, b.src, b.title));
            finish(c);
            return end;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "take a break", ex);
            return -1;
        }
    }

    /** "N more minutes" when a break is over: it goes on, never past the end of the slot. Returns when it ends, or -1. */
    static long extendBreak(Context c, int minutes) {
        try {
            AlarmState st = Store.alarmState(c);
            if (st.breakFrom <= 0) return -1;
            long now = System.currentTimeMillis();
            long end = SlotPlay.breakEnd(now, minutes, st.breakSlotEnd);
            if (end <= now) return -1;
            st.breakTo = end;
            Store.saveAlarmState(c, st);
            // same start time as the first record, so the longer break replaces it
            Store.history(c).append(Ev.breakTaken(st.breakFrom, end, st.breakDay, st.breakSrc, st.breakTitle));
            finish(c);
            return end;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "extend break", ex);
            return -1;
        }
    }

    /** Ends a break now ("Back to work"). */
    static void endBreak(Context c) {
        try {
            AlarmState st = Store.alarmState(c);
            long now = System.currentTimeMillis();
            if (!st.onBreak(now)) return;
            Store.history(c).append(Ev.breakTaken(st.breakFrom, now, st.breakDay, st.breakSrc, st.breakTitle));
            st.breakTo = now;
            Store.saveAlarmState(c, st);
            finish(c);
        } catch (RuntimeException ex) {
            CrashLog.note(c, "end break", ex);
        }
    }

    /** True while a break is running now. */
    static boolean onBreak(Context c) {
        try {
            return Store.alarmState(c).onBreak(System.currentTimeMillis());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
