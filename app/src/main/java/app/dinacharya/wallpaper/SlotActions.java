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
    /** The buttons of a break that is over: back to work, five more minutes, or just silence it. */
    static final String ACTION_BREAK_BACK = "app.dinacharya.wallpaper.BREAK_BACK";
    static final String ACTION_BREAK_MORE = "app.dinacharya.wallpaper.BREAK_MORE";
    /** Silences an alarm without answering it: the question stays in a quiet notification. */
    static final String ACTION_STOP = "app.dinacharya.wallpaper.ALARM_STOP";
    static final int BREAK_MORE_MIN = 5;
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
            // the part that is starting: a slot changed in the middle is several parts, and the one before it is what goes on
            int i = SlotPlay.indexOfPartStart(p, e.src, e.slotStartMs);
            int prev = i < 0 ? -1 : SlotPlay.previousIndex(p, i);
            if (prev < 0) return null;
            Routine.Block b = p.sc.slots.get(prev).block;
            return new Prev(b.src, b.title);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * The slot {@code src} has begun before its planned time, so its own alarm at that time stays quiet. A slot changed in the middle has an
     * alarm for each part and only the one that began is meant: the check as the alarm rings (see {@link AlarmPlanner#begunEarly}) says which.
     */
    private static void markBegun(Context c, Routine base, AlarmState st, String day, int src) {
        try {
            Plan.DayPlan p = Plan.plan(base, Store.overrides(c, base), LocalDate.parse(day), Store.zone());
            if (SlotPlay.partCount(p, src) > 1) return;
        } catch (RuntimeException ignored) {
            // an unreadable plan: mark the slot, as before
        }
        st.skipAlarm(day, src);
    }

    private static void finish(Context c) {
        SlotNow.changed();
        AlarmScheduler.reschedule(c);
        DayCloser.schedule(c);
        Quiet.refresh(c);   // a break, a late start or a changed slot can change how quiet the phone should be
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
        return carryOn(c, e.day, e.src, e.title, e.slotStartMs, e.slotEndMs, prev, minutes, false);
    }

    /**
     * The same from the Home screen while this slot's start is put off: the slot before goes on until then, and the wait is cut or
     * stretched to that moment, so the late start ends exactly when the slot before stops.
     */
    static long continuePreviousWhileWaiting(Context c, String day, int src, String title, long slotStartMs, long slotEndMs, Prev prev,
                                             int minutes) {
        return carryOn(c, day, src, title, slotStartMs, slotEndMs, prev, minutes, true);
    }

    private static long carryOn(Context c, String day, int src, String title, long slotStartMs, long slotEndMs, Prev prev, int minutes,
                                boolean endWait) {
        long now = System.currentTimeMillis();
        long until = SlotPlay.continueUntil(now, minutes, slotEndMs);
        Store.history(c).append(Ev.ack(now, day, src, title, "C", ""));
        Store.history(c).append(Ev.extend(now, day, src, prev.src, prev.title, until));
        // the last word about the late start: it ends when the slot before stops
        if (endWait) Store.history(c).append(Ev.delay(now, day, src, title, until));
        AlarmState st = Store.alarmState(c);
        st.againAt = until;
        st.againDay = day;
        st.againSrc = src;
        st.againTitle = title;
        st.againSlotStart = slotStartMs;
        st.againSlotEnd = slotEndMs;
        st.againWake = false;
        Store.saveAlarmState(c, st);
        finish(c);
        return until;
    }

    /** "Start now" from the Home screen while the start is put off: the wait ends this moment and the slot begins. */
    static void startWaitingNow(Context c, String day, int src, String title) {
        long now = System.currentTimeMillis();
        Store.history(c).append(Ev.ack(now, day, src, title, "C", ""));
        Store.history(c).append(Ev.delay(now, day, src, title, now));
        AlarmState st = Store.alarmState(c);
        if (st.againAt > 0 && st.againSrc == src) {
            st.againAt = 0;
            st.againWake = false;
            Store.saveAlarmState(c, st);
        }
        finish(c);
    }

    /**
     * "Other plan" / "Change plan": the slot is called something else for today only (the saved routine and every other slot stay as they
     * are, and the slot keeps its type, so the locks of that type still hold). It takes all that is left of the slot, from now.
     */
    static ReplaceFromNow.Placed changePlan(Context c, String day, int src, String title, String newName, long slotStartMs, boolean endWait) {
        return changePart(c, day, src, title, newName, 0, slotStartMs, endWait, -1, "");
    }

    /** Only the first {@code mins} minutes of the slot are called {@code newName} today; the rest goes back to the saved plan. */
    static ReplaceFromNow.Placed changePart(Context c, String day, int src, String title, String newName, int mins, long slotStartMs, boolean endWait) {
        return changePart(c, day, src, title, newName, mins, slotStartMs, endWait, -1, "");
    }

    /**
     * The same with the type and sub type the new plan takes ({@code mins} 0 for all that is left of the slot).
     *
     * <p>The new plan begins <b>now</b>, not where the slot planned to begin: the time that went by since then (a late start, an alarm
     * nobody answered) stays out of it and keeps showing as "Delayed start" or "No response", and the minutes asked for are counted from
     * the moment of the change (see {@link ReplaceFromNow}). When {@code endWait} is set, the wait for a put-off start ends now too.
     */
    static ReplaceFromNow.Placed changePart(Context c, String day, int src, String title, String newName, int mins, long slotStartMs, boolean endWait,
                                            int kind, String sub) {
        return changePart(c, day, src, title, newName, mins, slotStartMs, endWait, kind, sub, 0L);
    }

    /**
     * The same for a part that is going on ({@code partStartMs}: when the part was planned to begin, 0 when not known). A part that began early
     * is changed as of its planned start, not as of now: the saved plan has the part before it running until then, so a plan that began at
     * "now" would land on that part and leave this one as it was.
     */
    static ReplaceFromNow.Placed changePart(Context c, String day, int src, String title, String newName, int mins, long slotStartMs, boolean endWait,
                                            int kind, String sub, long partStartMs) {
        Routine base = Store.load(c);
        Overrides ov = Store.overrides(c, base);
        LocalDate d = LocalDate.parse(day);
        long now = System.currentTimeMillis();
        long ref = Math.max(now, partStartMs);
        long[] span = ReplaceFromNow.span(Plan.plan(base, ov, d, Store.zone()), src);
        // a slot that cannot be found in the plan keeps the old way: from its start, for as long as asked
        ReplaceFromNow when = span != null ? ReplaceFromNow.of(span[0], span[1], ref)
                : ReplaceFromNow.of(slotStartMs, slotStartMs + Math.max(1, mins) * 60000L, slotStartMs);
        ReplaceFromNow.Placed placed = when.place(mins);
        Plan.editPart(base, ov, d, src, newName, placed.stored, kind, sub, when.lead);
        Store.saveOverrides(c, base, ov);
        Store.history(c).append(Ev.ack(now, day, src, title, "R", newName));
        // the wait for a put-off start is over now (not at the slot's start: that would wipe the delay out of the day)
        if (endWait) Store.history(c).append(Ev.delay(now, day, src, title, now));
        AlarmState st = Store.alarmState(c);
        if ((endWait || placed.stored <= 0) && st.againAt > 0 && st.againSrc == src) {
            st.againAt = 0;
            st.againWake = false;
            Store.saveAlarmState(c, st);
        }
        finish(c);
        return placed;
    }

    /** What to tell the person after a change of plan: the name, and when it runs when that is not simply "all of the slot". */
    static String placedText(Context c, String name, ReplaceFromNow.Placed p) {
        if (!p.late()) return p.stored <= 0 ? "Changed for today: " + name : name + " for " + p.stored + " min, then back to the plan";
        String t = "Changed for today from " + clockText(c, p.fromMs()) + " to " + clockText(c, p.toMs()) + ": " + name;
        return p.restBack() ? t + ", then back to the plan" : t;
    }

    static String clockText(Context c, long ms) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(ms).atZone(Store.zone());
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), Store.load(c).fmt24);
    }

    /**
     * This slot and slot {@code other} change for today only: {@code places} trade places and times; otherwise they trade names, all of
     * their time ({@code mins} 0) or only the first {@code mins} minutes of each. {@code partStartMs} says which part of this slot is going on
     * (a slot changed in the middle is several parts): what was done before it stays.
     */
    static void swapWith(Context c, String day, int src, String title, long partStartMs, int other, String otherTitle, int otherMins, boolean places, int mins) {
        Routine base = Store.load(c);
        Overrides ov = Store.overrides(c, base);
        LocalDate d = LocalDate.parse(day);
        int from = 0;
        try {
            Plan.DayPlan dp = Plan.plan(base, ov, d, Store.zone());
            from = SlotPlay.minutesBefore(dp, SlotPlay.indexOfPartStart(dp, src, partStartMs));
        } catch (RuntimeException ignored) {
            // the whole slot, as before
        }
        Plan.swap(base, ov, d, src, title, from, other, otherTitle, otherMins, places, mins);
        Store.saveOverrides(c, base, ov);
        Store.history(c).append(Ev.ack(System.currentTimeMillis(), day, src, title, "R", otherTitle));
        finish(c);
    }

    /** "Continue the slot before" from the Home screen, for the running slot or a late start. */
    static long continueFrom(Context c, String day, int src, String title, long slotStartMs, long slotEndMs, Prev prev, int minutes, boolean endWait) {
        return carryOn(c, day, src, title, slotStartMs, slotEndMs, prev, minutes, endWait);
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

    // ------------------------------------------------------------------ more or less time, and a new activity

    /** What "Adjust time" can do to the slot that is running now, or null when it cannot be adjusted. */
    static SlotAdjust.Choices adjustChoices(Context c) {
        try {
            Routine base = Store.load(c);
            long now = System.currentTimeMillis();
            Plan.DayPlan saved = Plan.resolve(base, Store.overrides(c, base), now, Store.zone());
            Plan.DayPlan live = Store.livePlan(c, base, saved, now);
            if (live == null || live.cur < 0 || saved.cur < 0) return null;
            // only when what is running is the slot of the plan (not a break, a late start or a slot carried on)
            Routine.Block lb = live.sc.slots.get(live.cur).block;
            Routine.Block sb = saved.sc.slots.get(saved.cur).block;
            if (lb.src < 0 || lb.src != sb.src || lb.live == Routine.LIVE_CARRIED) return null;
            return SlotAdjust.of(saved, now);
        } catch (RuntimeException ex) {
            CrashLog.note(c, "adjust choices", ex);
            return null;
        }
    }

    /**
     * "Adjust time": the slot that is running ends {@code minutes} later (taking them from the slot at {@code otherIndex} of today's plan)
     * or, when {@code minutes} is negative, earlier (giving them to it). For today only. Returns false when the limits do not allow it.
     */
    static boolean adjustTime(Context c, int otherIndex, int minutes) {
        try {
            if (minutes == 0) return false;
            Routine base = Store.load(c);
            long now = System.currentTimeMillis();
            Overrides ov = Store.overrides(c, base);
            Plan.DayPlan saved = Plan.resolve(base, ov, now, Store.zone());
            SlotAdjust.Choices ch = SlotAdjust.of(saved, now);
            if (ch == null) return false;
            SlotAdjust.Option o = null;
            for (SlotAdjust.Option x : ch.others) if (x.index == otherIndex) o = x;
            if (o == null) return false;
            int most = minutes > 0 ? ch.maxGrow(o) : ch.maxShrink(o);
            if (Math.abs(minutes) > most) return false;
            if (!Plan.moveMinutes(base, ov, saved, ch.index, o.index, minutes)) return false;
            Store.saveOverrides(c, base, ov);
            finish(c);
            return true;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "adjust time", ex);
            return false;
        }
    }

    /**
     * "Finish" and then "New activity": the slot that is running ends now and {@code name} begins, lasting {@code fromLeft} minutes of the
     * time that was left plus {@code fromNext} taken from the next slot. The next slot begins after it and keeps its own end. For today only.
     * Returns false when it cannot be done any more (the time changed, or the limits do not allow it).
     */
    static boolean finishWithActivity(Context c, String name, int kind, String sub, int fromLeft, int fromNext) {
        try {
            Routine base = Store.load(c);
            long now = System.currentTimeMillis();
            Overrides ov = Store.overrides(c, base);
            Plan.DayPlan saved = Plan.resolve(base, ov, now, Store.zone());
            EarlyFinish.Offer o = EarlyFinish.of(Store.livePlan(c, base, saved, now), now);
            if (o == null || !FinishChoices.of(o).newActivity || name == null || name.trim().isEmpty()) return false;
            int ci = saved.cur;
            if (ci < 0 || ci + 1 >= saved.sc.slots.size()) return false;
            Routine.Slot cs = saved.sc.slots.get(ci);
            Routine.Slot ns = saved.sc.slots.get(ci + 1);
            if (cs.block.src != o.srcCur || ns.block.src != o.srcNext) return false;
            int slotMins = cs.end - cs.start;
            int elapsed = (int) ((now - saved.slotStartMs(ci)) / 60000L);
            int keep = Math.max(1, Math.min(elapsed, slotMins - 1));
            int left = slotMins - keep;
            int nextMins = ns.end - ns.start;
            int a = Math.max(0, Math.min(fromLeft, left));
            int floor = SlotAdjust.floorOf(ns.block.kind, nextMins);
            int b = Math.max(0, Math.min(fromNext, nextMins + (left - a) - floor));
            int len = a + b;
            if (len < FinishChoices.minLength(kind)) return false;
            if (!Plan.startActivity(base, ov, saved, ci, ci + 1, keep, name.trim(), kind, sub, len)) return false;
            Store.saveOverrides(c, base, ov);
            HistoryStore h = Store.history(c);
            h.append(Ev.ack(now, saved.day.toString(), o.srcNext, o.titleNext, "R", name.trim()));
            AlarmState st = Store.alarmState(c);
            closeBreak(h, st, now);
            st.breakFrom = 0;
            st.breakTo = 0;
            st.againAt = 0;
            st.againWake = false;
            Store.saveAlarmState(c, st);
            finish(c);
            return true;
        } catch (RuntimeException ex) {
            CrashLog.note(c, "finish with activity", ex);
            return false;
        }
    }

    // ------------------------------------------------------------------ finishing early and the day off

    /**
     * A break that is going on ends now, because the slot it is in was finished: its end is written as now, so that it cannot come back when
     * the slot is carried on later (the first end it had was in the future, and a longer slot would hold it again).
     */
    private static void closeBreak(HistoryStore h, AlarmState st, long now) {
        if (st.onBreak(now)) h.append(Ev.breakTaken(st.breakFrom, now, st.breakDay, st.breakSrc, st.breakTitle));
    }

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
            closeBreak(h, st, now);
            // a slot that was carried on ends now and the next begins now (the carry is cut short); any other slot is cut short
            if (o.carried) {
                h.append(Ev.extend(now, day, o.srcNext, o.srcCur, o.titleCur, now));
                // a late start that was put off until the carry was to end is over now too
                for (Ev x : h.events()) {
                    if (Ev.DELAY.equals(x.type) && x.day.equals(day) && x.src == o.srcNext && x.aLong(0) > now) {
                        h.append(Ev.delay(now, day, o.srcNext, o.titleNext, now));
                        break;
                    }
                }
            } else {
                h.append(Ev.early(now, day, o.srcNext, o.srcCur, o.titleCur, now, o.nextStartMs));
            }
            h.append(Ev.ack(now, day, o.srcNext, o.titleNext, "C", ""));
            st.bindSkips(Plan.scheduleSignature(base));   // marks of an older schedule go first
            markBegun(c, base, st, day, o.srcNext);   // it has begun, so its own alarm at the planned time stays quiet
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
        /** Minutes from the start of the slot to the part that is skipped (0 for a slot in one piece): what was done before it stays. */
        final int fromMin;

        Skipped(int nextSrc, String nextTitle, boolean nextIsSleep, long nextPlannedMs, long nextEndMs, int fromMin) {
            this.nextSrc = nextSrc;
            this.nextTitle = nextTitle;
            this.nextIsSleep = nextIsSleep;
            this.nextPlannedMs = nextPlannedMs;
            this.nextEndMs = nextEndMs;
            this.fromMin = fromMin;
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
            // the part that is starting (a slot changed in the middle is several parts); what comes next is after the last of them
            int i = SlotPlay.indexOfPartStart(p, e.src, e.slotStartMs);
            if (i < 0 || p.sc.slots.get(i).block.kind == Routine.SLEEP) return null;
            int n = nextReal(p, SlotPlay.lastIndexOfSrc(p, e.src));
            if (n < 0) return null;
            Routine.Block nb = p.sc.slots.get(n).block;
            return new Skipped(nb.src, nb.title, nb.kind == Routine.SLEEP, p.slotStartMs(n), p.slotEndMs(n), SlotPlay.minutesBefore(p, i));
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
            Plan.skipFrom(base, ov, LocalDate.parse(e.day), e.src, sk.fromMin);   // from this part on: what was done before it stays
            Store.saveOverrides(c, base, ov);
            HistoryStore h = Store.history(c);
            h.append(Ev.ack(now, e.day, e.src, e.title, "S", ""));
            AlarmState st = Store.alarmState(c);
            if (st.againAt > 0 && st.againSrc == e.src) {
                st.againAt = 0;
                st.againWake = false;
            }
            if (!sk.nextIsSleep && now < sk.nextPlannedMs - 60000L) {
                h.append(Ev.skip(now, e.day, e.src, e.title, sk.nextSrc, now, sk.nextPlannedMs));
                h.append(Ev.ack(now, e.day, sk.nextSrc, sk.nextTitle, "C", ""));
                st.bindSkips(Plan.scheduleSignature(base));   // marks of an older schedule go first
                markBegun(c, base, st, e.day, sk.nextSrc);   // it has begun, so its own alarm at the planned time stays quiet
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
                h.append(Ev.early(now, day, sleepSrc, o.srcCur, o.titleCur, now, o.nextStartMs));
            } else if (before.skipped && before.src >= 0) {
                h.append(Ev.skip(now, day, before.src, before.title, sleepSrc, now, saved.slotStartMs(si)));
            }
            AlarmState st = Store.alarmState(c);
            closeBreak(h, st, now);
            st.bindSkips(Plan.scheduleSignature(base));
            markBegun(c, base, st, day, sb.src);
            st.breakFrom = 0;
            st.breakTo = 0;
            st.againAt = 0;
            st.againWake = false;
            st.reaskAt = 0;   // a bedtime question asked again later is not wanted either
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
            // A later start, a break or a bedtime question asked again was made for a slot of the old routine; when that slot is gone, renamed
            // or ends elsewhere, the timer would ring for the old routine next to the alarms of the new one.
            if (StaleTimers.drop(saved, Store.overrides(c, saved), st, Store.zone())) changed = true;
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
