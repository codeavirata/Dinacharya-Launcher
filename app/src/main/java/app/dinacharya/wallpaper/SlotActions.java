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
            long now = System.currentTimeMillis();
            // the slot that is going on now, which may have begun early or be the second part of a slot that had a break
            Plan.DayPlan p = Store.livePlanNow(c, base, now);
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

    // ------------------------------------------------------------------ finishing early and the day off

    /** What to do with the next slot when the one going on is finished early. */
    static final int EARLY_NOW = 0, EARLY_AFTER = 1, EARLY_BREAK = 2;

    /** What finishing the slot going on early offers right now, or null when it cannot be done. */
    static EarlyFinish.Offer earlyOffer(Context c) {
        try {
            Routine base = Store.load(c);
            long now = System.currentTimeMillis();
            Plan.DayPlan live = Store.livePlanNow(c, base, now);
            return EarlyFinish.of(live, nextDayOf(c, base, live), now);
        } catch (RuntimeException ex) {
            CrashLog.note(c, "early offer", ex);
            return null;
        }
    }

    /**
     * Finishes the slot that is going on now and begins the next one before its time: right away ({@link #EARLY_NOW}), after
     * {@code minutes} of waiting ({@link #EARLY_AFTER}) or after a break of {@code minutes} ({@link #EARLY_BREAK}). The next slot keeps
     * its planned end, so it gets the time that was left, and its own alarm is not rung again. Returns when the next slot begins, or -1
     * when this cannot be done any more.
     */
    static long finishEarly(Context c, int how, int minutes) {
        try {
            Routine base = Store.load(c);
            long now = System.currentTimeMillis();
            Plan.DayPlan saved = Plan.resolve(base, Store.overrides(c, base), now, Store.zone());
            EarlyFinish.Offer o = EarlyFinish.of(Store.livePlan(c, base, saved, now), now);
            if (o == null || o.kind != EarlyFinish.FINISH) return -1;
            if (o.curIsSleep && how != EARLY_NOW) return -1;
            if (o.carried && how != EARLY_NOW) return -1;
            String day = saved.day.toString();
            AlarmState st = Store.alarmState(c);
            long begin = now;
            if (how != EARLY_NOW) {
                int m = Math.max(1, Math.min(minutes, Math.max(1, o.maxRestMin())));
                begin = Math.min(now + m * 60000L, Math.max(now, o.plannedEndMs - 60000L));
            }
            HistoryStore h = Store.history(c);
            // a slot that was carried on ends now and the next begins now (the carry is cut short); any other slot is cut short
            if (o.carried) h.append(Ev.extend(now, day, o.srcNext, o.srcCur, o.titleCur, now));
            else h.append(Ev.early(now, day, o.srcNext, o.srcCur, o.titleCur, now));
            h.append(Ev.ack(now, day, o.srcNext, o.titleNext, "C", ""));
            st.bindSkips(Plan.scheduleSignature(base));   // marks of an older schedule go first
            st.skipAlarm(day, o.srcNext);   // it has begun, so its own alarm at the planned time stays quiet
            st.breakFrom = 0;
            st.breakTo = 0;
            st.againAt = 0;
            st.againWake = false;
            if (how == EARLY_AFTER && begin > now) {
                h.append(Ev.delay(now, day, o.srcNext, o.titleNext, begin));
                st.againAt = begin;
                st.againDay = day;
                st.againSrc = o.srcNext;
                st.againTitle = o.titleNext;
                st.againSlotStart = o.plannedEndMs;
                st.againSlotEnd = o.nextEndMs;
            } else if (how == EARLY_BREAK && begin > now) {
                h.append(Ev.breakTaken(now, begin, day, o.srcNext, o.titleNext));
                st.breakFrom = now;
                st.breakTo = begin;
                st.breakDay = day;
                st.breakSrc = o.srcNext;
                st.breakTitle = o.titleNext;
                st.breakSlotEnd = o.nextEndMs;
            }
            Store.saveAlarmState(c, st);
            finish(c);
            return begin;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "finish early", ex);
            return -1;
        }
    }

    /** The plan of the day after the one that is going on (the night's sleep is the last slot of its day), or null when not known. */
    private static Plan.DayPlan nextDayOf(Context c, Routine base, Plan.DayPlan live) {
        if (live == null) return null;
        return Plan.plan(base, Store.overrides(c, base), live.day.plusDays(1), Store.zone());
    }

    /**
     * "Early wake": the person is up before the alarm, so the next day starts now. Its first slot keeps its planned end and gets the
     * time; the night before ends now. Returns when the day began (now), or -1 when this cannot be done any more.
     */
    static long earlyWake(Context c) {
        try {
            Routine base = Store.load(c);
            long now = System.currentTimeMillis();
            Overrides ov = Store.overrides(c, base);
            Plan.DayPlan saved = Plan.resolve(base, ov, now, Store.zone());
            Plan.DayPlan nextDay = Plan.plan(base, ov, saved.day.plusDays(1), Store.zone());
            EarlyFinish.Offer o = EarlyFinish.of(Store.livePlan(c, base, saved, now), nextDay, now);
            if (o == null || o.kind != EarlyFinish.WAKE) return -1;
            int m = EarlyFinish.minutesEarlier(nextDay.startMs, now);
            if (m <= 0) return -1;
            String day = nextDay.day.toString();
            ov.setEarly(nextDay.day, ov.earlyFor(nextDay.day) + m);
            Store.saveOverrides(c, base, ov);
            HistoryStore h = Store.history(c);
            h.append(Ev.woke(now, day, o.srcNext, o.titleNext, nextDay.startMs, m));
            h.append(Ev.ack(now, day, o.srcNext, o.titleNext, "C", ""));
            AlarmState st = Store.alarmState(c);
            st.reaskAt = 0;   // no bedtime question is left to ask
            st.breakFrom = 0;
            st.breakTo = 0;
            st.againAt = 0;
            st.againWake = false;
            Store.saveAlarmState(c, st);
            finish(c);
            return now;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "early wake", ex);
            return -1;
        }
    }

    /** What skipping a slot at its start left to do. */
    static final class Skipped {
        /** The slot that begins instead (its number and name). */
        final int nextSrc;
        final String nextTitle;
        /** True when that slot is sleep: the person goes on to the bedtime question, and sleep begins when it is answered. */
        final boolean nextIsSleep;
        /** When the next slot was to begin, and (for sleep) when it ends. */
        final long nextPlannedMs;
        final long nextEndMs;

        Skipped(int nextSrc, String nextTitle, boolean nextIsSleep, long nextPlannedMs, long nextEndMs) {
            this.nextSrc = nextSrc;
            this.nextTitle = nextTitle;
            this.nextIsSleep = nextIsSleep;
            this.nextPlannedMs = nextPlannedMs;
            this.nextEndMs = nextEndMs;
        }
    }

    /** The slot after {@code i} that is a real one and not taken off, or -1. */
    private static int nextReal(Plan.DayPlan p, int i) {
        for (int k = i + 1; k < p.sc.slots.size(); k++) {
            Routine.Block b = p.sc.slots.get(k).block;
            if (b.src >= 0 && !b.skipped) return k;
        }
        return -1;
    }

    /** The slot that would begin if the slot of {@code e} were skipped, or null when there is none (it is the last of the day). */
    static Skipped whatSkipping(Context c, AlarmPlanner.Event e) {
        try {
            if (e == null || e.day.isEmpty() || e.src < 0 || AlarmPlanner.isTest(e)) return null;
            Routine base = Store.load(c);
            Plan.DayPlan p = Plan.plan(base, Store.overrides(c, base), LocalDate.parse(e.day), Store.zone());
            int i = SlotPlay.indexOfSrc(p, e.src);
            if (i < 0 || p.sc.slots.get(i).block.kind == Routine.SLEEP) return null;
            int n = nextReal(p, i);
            if (n < 0) return null;
            Routine.Block nb = p.sc.slots.get(n).block;
            return new Skipped(nb.src, nb.title, nb.kind == Routine.SLEEP, p.slotStartMs(n), p.slotEndMs(n));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Skips the slot that is starting and begins the next one at once. The skipped slot is taken off for the day (the same as "Take it off
     * for today") and the next slot begins now; it keeps its planned end, so it gets the skipped slot's time, and its own alarm at the
     * planned time stays quiet. When the next slot is sleep, the slot is only taken off here: sleep begins when the person answers the
     * bedtime question ({@link #sleepEarly}). Returns null when it cannot be done.
     */
    static Skipped skipSlot(Context c, AlarmPlanner.Event e) {
        try {
            Skipped sk = whatSkipping(c, e);
            if (sk == null) return null;
            Routine base = Store.load(c);
            Overrides ov = Store.overrides(c, base);
            long now = System.currentTimeMillis();
            Plan.edit(base, ov, LocalDate.parse(e.day), e.src, true, "");
            Store.saveOverrides(c, base, ov);
            HistoryStore h = Store.history(c);
            h.append(Ev.ack(now, e.day, e.src, e.title, "S", ""));
            AlarmState st = Store.alarmState(c);
            if (st.againAt > 0 && st.againSrc == e.src) {
                st.againAt = 0;
                st.againWake = false;
            }
            if (!sk.nextIsSleep && now < sk.nextPlannedMs - 60000L) {
                h.append(Ev.skip(now, e.day, e.src, e.title, sk.nextSrc, now));
                h.append(Ev.ack(now, e.day, sk.nextSrc, sk.nextTitle, "C", ""));
                st.bindSkips(Plan.scheduleSignature(base));   // marks of an older schedule go first
                st.skipAlarm(e.day, sk.nextSrc);   // it has begun, so its own alarm at the planned time stays quiet
                st.breakFrom = 0;
                st.breakTo = 0;
            }
            Store.saveAlarmState(c, st);
            finish(c);
            return sk;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "skip slot", ex);
            return null;
        }
    }

    /**
     * Sleep begins before its planned time, because the person said they are going to sleep: the slot before it ends now and sleep begins
     * now (it keeps its planned end), and the bedtime question that was to come at the planned time stays quiet. Called when the person
     * has set the wake-up alarm. Does nothing when it is not early.
     */
    static void sleepEarly(Context c, String day, int sleepSrc, long now) {
        try {
            Routine base = Store.load(c);
            Overrides ov = Store.overrides(c, base);
            Plan.DayPlan saved = Plan.plan(base, ov, LocalDate.parse(day), Store.zone());
            int si = SlotPlay.indexOfSrc(saved, sleepSrc);
            if (si < 1 || now >= saved.slotStartMs(si) - 60000L) return;
            Routine.Block sb = saved.sc.slots.get(si).block;
            HistoryStore h = Store.history(c);
            EarlyFinish.Offer o = EarlyFinish.of(Store.livePlan(c, base, Plan.resolve(base, ov, now, Store.zone()), now), now);
            Routine.Block before = saved.sc.slots.get(si - 1).block;
            if (o != null && o.kind == EarlyFinish.SLEEP && o.srcNext == sleepSrc) {
                h.append(Ev.early(now, day, sleepSrc, o.srcCur, o.titleCur, now));
            } else if (before.skipped && before.src >= 0) {
                h.append(Ev.skip(now, day, before.src, before.title, sleepSrc, now));
            }
            AlarmState st = Store.alarmState(c);
            st.bindSkips(Plan.scheduleSignature(base));
            st.skipAlarm(day, sb.src);
            st.breakFrom = 0;
            st.breakTo = 0;
            st.againAt = 0;
            st.againWake = false;
            Store.saveAlarmState(c, st);
            finish(c);
        } catch (RuntimeException ex) {
            CrashLog.note(c, "early sleep", ex);
        }
    }

    /**
     * Takes the rest of today off from now: no alarm rings until a new schedule is made for the day. The slots still to come are written
     * to the history as taken off, and the slot going on ends now. {@code planAtMs} (0 for none) is when to ask "plan your day?" again.
     * Returns false when it could not be done.
     */
    static boolean takeDayOff(Context c, long planAtMs) {
        try {
            Routine base = Store.load(c);
            long now = System.currentTimeMillis();
            Plan.DayPlan saved = Plan.resolve(base, Store.overrides(c, base), now, Store.zone());
            String day = saved.day.toString();
            AlarmState st = Store.alarmState(c);
            HistoryStore h = Store.history(c);
            // The report works out which slots were off from this marker (a slot that begins after it is off; the one going on is cut).
            h.append(Ev.dayOff(now, day, saved.endMs));
            DayOff.start(base, st, saved.day, now, saved.endMs);
            if (planAtMs > now) DayOff.planLater(st, planAtMs);
            Store.saveAlarmState(c, st);
            finish(c);
            return true;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "day off", ex);
            return false;
        }
    }

    /** Asks about planning the day again at {@code atMs} ("plan later"), or not at all when 0. */
    static void planLater(Context c, long atMs) {
        AlarmState st = Store.alarmState(c);
        DayOff.planLater(st, atMs);
        Store.saveAlarmState(c, st);
        finish(c);
    }

    /** Brings today's alarms back without a new schedule ("go back to my schedule"). */
    static void endDayOff(Context c) {
        AlarmState st = Store.alarmState(c);
        if (!st.offDay.isEmpty()) Store.history(c).append(Ev.dayOffEnded(System.currentTimeMillis(), st.offDay));
        DayOff.end(st);
        Store.saveAlarmState(c, st);
        finish(c);
    }

    /**
     * Called after the routine was saved: when the day that was taken off now has another schedule, the day is planned again, so the day
     * off ends (and is written to the history). Does not set the alarms; the caller does that next.
     */
    static void noticeReplan(Context c, Routine saved) {
        try {
            AlarmState st = Store.alarmState(c);
            boolean changed = false;
            if (DayOff.replanned(saved, st)) {
                Store.history(c).append(Ev.dayOffEnded(System.currentTimeMillis(), st.offDay));
                DayOff.end(st);
                changed = true;
            }
            // Slots started early have their own alarm switched off by their place in the list; that place means another slot once the
            // schedule is different (a routine for one day, a slot added), so the marks go with the old schedule.
            if (st.bindSkips(Plan.scheduleSignature(saved))) changed = true;
            if (changed) Store.saveAlarmState(c, st);
        } catch (RuntimeException ex) {
            CrashLog.note(c, "day planned again", ex);
        }
    }

    /** True while the rest of today is off (taken off and not planned again, and the day is not over). */
    static boolean dayIsOff(Context c) {
        try {
            return DayOff.pending(Store.load(c), Store.alarmState(c), System.currentTimeMillis());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Forgets a day off whose day is over, so nothing of it is left to hold back the alarms of a later day. */
    static void forgetOldDayOff(Context c) {
        try {
            AlarmState st = Store.alarmState(c);
            if (!DayOff.expired(st, System.currentTimeMillis())) return;
            DayOff.end(st);
            Store.saveAlarmState(c, st);
        } catch (RuntimeException e) {
            CrashLog.note(c, "old day off", e);
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
