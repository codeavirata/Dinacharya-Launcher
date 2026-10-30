package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/** Puts a day's plan, the answers you gave and your phone use together into a {@link DayReport}. */
public final class ReportAssembler {
    private static final long HOUR = 3600000L;

    private ReportAssembler() {
    }

    /** Whether alarms were on at time {@code t}, worked out from the switch changes in the history. */
    static boolean alarmsOnAt(List<Ev> events, boolean onNow, long t) {
        Ev before = null;
        Ev after = null;
        for (Ev e : events) {
            if (!Ev.ALARMS.equals(e.type)) continue;
            if (e.ts <= t) {
                if (before == null || e.ts >= before.ts) before = e;
            } else if (after == null || e.ts < after.ts) {
                after = e;
            }
        }
        if (before != null) return before.a.equals("1");
        if (after != null) return !after.a.equals("1");
        return onNow;
    }

    /**
     * @param p      the day, with temporary changes applied
     * @param events everything in the history (the switch changes are needed from before the day too)
     * @param u      phone use for the day, or null when it could not be read
     */
    public static DayReport assemble(Plan.DayPlan p, List<Ev> events, UsageDetail u, boolean alarmsOnNow, long nowMs) {
        return assemble(p, events, u, alarmsOnNow, nowMs, null);
    }

    /**
     * Same, knowing which apps are part of which activity: time in them is kept apart from other phone use in each slot (and does not
     * count against focus). With {@code parts} null every app is other phone use, as before.
     */
    public static DayReport assemble(Plan.DayPlan p, List<Ev> events, UsageDetail u, boolean alarmsOnNow, long nowMs, SlotUse.Parts parts) {
        DayReport r = new DayReport();
        r.day = p.day.toString();
        r.startMs = p.startMs;
        r.endMs = p.endMs;
        r.partial = nowMs < p.endMs;
        long clip = Math.min(nowMs, p.endMs);
        r.routineTotalMin = p.routine.totalMinutes();
        r.schedule = p.routine.serialize();
        // the goals that count on this day: by their rule, and only for types this day's schedule has
        List<Routine.Block> dayBlocks = new ArrayList<>();
        for (Routine.Slot sl : p.sc.slots) dayBlocks.add(sl.block);
        r.goals.addAll(p.routine.goalsFor(p.day, dayBlocks));

        List<Ev> dayEv = new ArrayList<>();
        for (Ev e : events) if (e.day.equals(r.day) && e.ts <= nowMs) dayEv.add(e);

        boolean anyTracked = alarmsOnNow;
        List<long[]> offPeriods = DayOff.periods(dayEv);
        for (int i = 0; i < p.sc.slots.size(); i++) {
            Routine.Block b = p.sc.slots.get(i).block;
            DayReport.Row row = new DayReport.Row();
            row.kind = b.kind;
            row.plannedMin = b.mins;
            row.startMs = p.slotStartMs(i);
            row.endMs = p.slotEndMs(i);
            row.title = (b.changed && b.origTitle != null) ? b.origTitle : b.title;
            row.newTitle = (b.changed && !b.skipped) ? b.title : "";
            row.sub = b.sub == null ? "" : b.sub;
            SlotGaps.Result gaps = SlotGaps.of(dayEv, b.src, row.startMs, row.endMs, clip);
            // a slot started before its time, or finished early, counts from when it really began to when it really ended
            long stop = Math.min(gaps.effEnd, clip);
            row.breakMs = gaps.breakMs;
            row.delayMs = gaps.delayMs;
            row.lentMs = gaps.lentMs;
            row.extMs = gaps.extMs;
            row.activeMs = Math.max(0, Math.max(0, stop - gaps.effStart) - gaps.gapMs());

            // A slot that begins while the day is off is taken off: nothing was asked and nothing is counted.
            boolean off = b.kind != Routine.SLEEP && row.startMs >= 0 && DayOff.within(offPeriods, row.startMs);
            if (off) row.activeMs = 0;
            Ev last = null;
            if (b.src >= 0 && !off) {
                for (Ev e : dayEv) {
                    if (e.src != b.src) continue;
                    if (!Ev.ACK.equals(e.type) && !Ev.MISSED.equals(e.type)) continue;
                    if (last == null || e.ts >= last.ts) last = e;
                }
            }
            if (last != null) {
                anyTracked = true;
                if (Ev.MISSED.equals(last.type)) row.status = DayReport.UNANSWERED;
                else if (last.a.equals("R")) row.status = DayReport.CHANGED;
                else if (last.a.equals("S")) row.status = DayReport.SKIPPED;
                else row.status = DayReport.KEPT;
            } else if (b.skipped || off) {
                row.status = DayReport.SKIPPED;
            } else if (b.kind == Routine.SLEEP) {
                row.status = DayReport.UNTRACKED;
            } else if (row.endMs > clip || row.startMs >= clip) {
                row.status = DayReport.UPCOMING;
            } else {
                boolean tracked = b.sounds() && alarmsOnAt(events, alarmsOnNow, row.startMs);
                row.status = tracked ? DayReport.UNANSWERED : DayReport.UNTRACKED;
                if (tracked) anyTracked = true;
            }
            if (u != null && stop > gaps.effStart) {
                // Phone use counts only while the slot's own activity was going on: not during a break or a put-off start.
                List<long[]> worked = SlotGaps.complement(gaps.effStart, stop, gaps.gaps);
                row.phoneMs = u.overlap(worked);
                for (Object[] a : u.appsDuring(worked)) row.apps.add(new DayReport.App((String) a[0], (Long) a[1]));
                if (b.kind != Routine.SLEEP && !b.skipped && !off) {
                    // which of it was in the activity's own apps, the other phone use with its times, and the longest unbroken focus
                    java.util.Set<String> part = parts == null ? java.util.Collections.<String>emptySet() : parts.packagesFor(b.kind, b.sub);
                    SlotUse.Info info = SlotUse.analyze(worked, u, part, nowMs);
                    row.partMs = info.partMs;
                    row.longestFocusMs = info.longestFocusMs;
                    row.useCount = info.episodeCount;
                    row.uses.addAll(info.episodes);
                }
            }
            r.rows.add(row);
        }
        r.alarmsOn = anyTracked;

        if (u != null) {
            r.phoneKnown = true;
            r.phoneMs = u.overlap(p.startMs, clip);
            for (int h = 0; h < 24; h++) {
                long hs = p.startMs + h * HOUR;
                long he = Math.min(hs + HOUR, clip);
                r.hourMs[h] = he > hs ? u.overlap(hs, he) : 0;
            }
            for (Object[] a : u.top(5)) r.apps.add(new DayReport.App((String) a[0], (Long) a[1]));
        }

        int si = p.lastSleepIndex();
        if (si >= 0) {
            Routine.Block sb = p.sc.slots.get(si).block;
            r.plannedBedMs = p.slotStartMs(si);
            r.plannedSleepMin = Math.max(1, sb.mins - sb.extended);
        }
        Ev sleep = null;
        for (Ev e : dayEv) {
            if (Ev.SLEEP.equals(e.type) && (sleep == null || e.ts >= sleep.ts)) sleep = e;
            if (Ev.SNOOZE.equals(e.type)) {
                r.snoozes++;
                r.snoozeMinutes += (int) e.aLong(0);
            }
        }
        if (sleep != null) r.actualBedMs = sleep.ts;
        long bed = r.actualBedMs > 0 ? r.actualBedMs : r.plannedBedMs;
        if (u != null && bed > 0 && bed <= clip) r.beforeBedMs = u.overlap(bed - HOUR, bed);

        java.util.Map<String, Integer> pausedBy = new java.util.LinkedHashMap<>();
        for (Ev e : dayEv) {
            if (!Ev.LAUNCH.equals(e.type)) continue;
            r.pauses++;
            if ("B".equals(e.a)) r.wentBack++;
            else r.openedAfter++;
            String name = e.title.isEmpty() ? e.b : e.title;
            Integer n = pausedBy.get(name);
            pausedBy.put(name, n == null ? 1 : n + 1);
        }
        int most = 0;
        for (java.util.Map.Entry<String, Integer> en : pausedBy.entrySet()) {
            if (en.getValue() > most) {
                most = en.getValue();
                r.pausedMost = en.getKey();
            }
        }

        Analyzer.apply(r, nowMs);
        return r;
    }
}
