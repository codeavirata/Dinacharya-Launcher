package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The day as it is going, for the schedule on the home screen. It takes the day's plan and what happened today (a start put off, a break,
 * the slot before carried on, a slot finished early so the next began before its time, the rest of the day taken off) and moves the slot
 * boundaries, adds the break and waiting rows, and tags the parts that changed. The saved plan is not touched: alarms, reports and locks
 * keep reading that. Uses no Android classes.
 */
public final class LivePlan {
    private static final long MIN = 60000L;
    public static final String BREAK_TITLE = "Break";
    public static final String WAIT_TITLE = "Delayed start";
    /** The part of a slot that passes while nobody answers its alarm. */
    public static final String NORESP_TITLE = "No response";
    public static final String OFF_TITLE = "Day off";

    private LivePlan() {
    }

    /** A stretch inside a slot where its own activity is not going on. */
    private static final class Gap {
        long from;
        long to;
        int live;
        /** The wait is for an answer that has not come ("No response"), not for a start that was put off. */
        boolean noResp;

        Gap(long from, long to, int live) {
            this.from = from;
            this.to = to;
            this.live = live;
        }
    }

    /** One part of the day, in minutes from the start of the day. */
    private static final class Piece {
        final int from;
        final int to;
        final Routine.Block block;

        Piece(int from, int to, Routine.Block block) {
            this.from = from;
            this.to = to;
            this.block = block;
        }
    }

    private static int toInt(String s, int dflt) {
        return Ev.srcOf(s, dflt);
    }

    /**
     * The plan with today's changes applied, or {@code base} itself when nothing changed (so a caller can tell by identity).
     *
     * @param events  the history (any days; only those of the plan's day count)
     * @param dayOff  whether the day was taken off and not planned again (see {@link DayOff#active}); {@code st.offFrom} says from when
     */
    public static Plan.DayPlan apply(Plan.DayPlan base, List<Ev> events, AlarmState st, boolean dayOff, long nowMs) {
        if (base == null) return null;
        try {
            Plan.DayPlan live = build(base, events, st, dayOff, nowMs);
            return live == null ? base : live;
        } catch (RuntimeException e) {
            return base;
        }
    }

    private static Plan.DayPlan build(Plan.DayPlan base, List<Ev> events, AlarmState st, boolean dayOff, long nowMs) {
        String dayKey = base.day.toString();
        List<Ev> ev = new ArrayList<>();
        if (events != null) for (Ev e : events) if (e.day.equals(dayKey) && e.ts <= nowMs) ev.add(e);
        Collections.sort(ev, new Comparator<Ev>() {
            @Override
            public int compare(Ev a, Ev b) {
                return Long.compare(a.ts, b.ts);
            }
        });
        int n = base.sc.slots.size();
        if (n == 0) return null;
        boolean offNow = dayOff && st != null && st.offFrom > 0;
        if (ev.isEmpty() && !offNow) return null;

        long[] start = new long[n];
        long[] end = new long[n];
        int[] tagStart = new int[n];
        int[] tagEnd = new int[n];
        for (int i = 0; i < n; i++) {
            start[i] = base.slotStartMs(i);
            end[i] = base.slotEndMs(i);
        }
        boolean changed = false;

        // 1. The boundary between a slot and the one after it moves: earlier (the slot was finished early, or skipped when it began) or
        //    later (carried on). Slots taken off that lie between the two collapse to nothing.
        for (Ev e : ev) {
            boolean early = Ev.EARLY.equals(e.type);
            boolean skip = Ev.SKIP.equals(e.type);
            if (!early && !skip && !Ev.EXTEND.equals(e.type)) continue;
            // early: src begins, b ended.  skip: src was skipped, b begins.  extend: src begins, b carries on.
            // A slot changed in the middle is several parts with one number, so which part begins (x) and which one ends or goes on (p) is
            // worked out from the part and the time the answer was given for.
            int[] bd = boundary(base, e, early, skip);
            if (bd == null) continue;
            int p = bd[0];
            int x = bd[1];
            if (x < 1 || p < 0 || p >= x) continue;
            if (base.sc.slots.get(x).block.skipped) continue;
            if (skip ? !base.sc.slots.get(p).block.skipped : base.sc.slots.get(p).block.skipped) continue;
            if (!early && !skip && p != x - 1) continue;
            if (!takenOffBetween(base, p, x)) continue;
            long planned = base.slotStartMs(x);
            long t = e.aLong(0);
            if (skip) {
                if (t >= planned) continue;
                t = Math.max(t, start[p]);
                end[p] = t;
                start[x] = t;
                tagStart[x] = Routine.LIVE_EARLY;
            } else if (early) {
                if (t >= planned || t < start[p] + MIN) continue;
                end[p] = t;
                start[x] = t;
                tagEnd[p] = Routine.LIVE_CUT;
                tagStart[x] = Routine.LIVE_EARLY;
            } else {
                t = Math.min(t, end[x] - MIN);
                if (t <= planned) continue;
                end[p] = t;
                start[x] = t;
                tagEnd[p] = Routine.LIVE_CARRIED;
                tagStart[x] = Routine.LIVE_LATE;
            }
            for (int k = p + 1; k < x; k++) {
                start[k] = t;
                end[k] = t;
            }
            changed = true;
        }

        // 2. Gaps inside slots: a start that was put off, and breaks.
        List<List<Gap>> gaps = new ArrayList<>();
        for (int i = 0; i < n; i++) gaps.add(new ArrayList<Gap>());
        Map<Long, Ev> breaks = new LinkedHashMap<>();
        for (Ev e : ev) if (Ev.BREAK.equals(e.type)) breaks.put(e.ts, e);
        // the last answer about a slot's late start is the one that counts: it can end the wait early ("start now"). A slot changed in the
        // middle is several parts with one number; the answer belongs to the part that was going on (or starting) when it was given.
        Map<Integer, Ev> lastDelay = new LinkedHashMap<>();
        for (Ev e : ev) if (Ev.DELAY.equals(e.type)) lastDelay.put(partAnswered(base, ev, e), e);
        // a slot whose alarm went off and was not answered (yet) starts from the answer: until then it is "No response"
        Map<Integer, Long> workStart = new LinkedHashMap<>();
        // when the unanswered stretch of a slot ends (it is "No response" even after the late answer; a start put off after that is "Delayed start")
        Map<Integer, Long> noRespEnd = new LinkedHashMap<>();
        for (Map.Entry<Integer, Ev> de : lastDelay.entrySet()) if (de.getKey() >= 0) workStart.put(de.getKey(), de.getValue().aLong(0));
        for (int x = 0; x < n; x++) {
            Routine.Block sb = base.sc.slots.get(x).block;
            if (sb.src < 0 || sb.skipped || sb.kind == Routine.SLEEP) continue;
            long nr = noResponseOf(base, ev, x, nowMs);
            if (nr == 0L) continue;
            Long had = workStart.get(x);
            workStart.put(x, Math.max(had == null ? 0L : had, nr));
            noRespEnd.put(x, nr);
        }
        for (Map.Entry<Integer, Long> we : workStart.entrySet()) {
            int x = we.getKey();
            if (x < 0 || x >= n || base.sc.slots.get(x).block.skipped) continue;
            // a minute of the slot is always left, unless the slot goes on in the next part (a plan changed in the middle of it begins
            // right where the wait ended: the part before it is then all wait)
            long w = Math.min(we.getValue(), continues(base, x) ? end[x] : end[x] - MIN);
            // a wait that was ended by "Start" in the second half of a minute is not drawn up to the next minute: the card shows the slot at once
            if (w <= nowMs) w = Math.min(w, minuteStart(base, nowMs));
            if (w > start[x]) {
                // the time nobody answered, then the start that was put off after the answer: two stretches, named for what they were
                Long nrEnd = noRespEnd.get(x);
                long split = nrEnd == null ? start[x] : Math.min(nrEnd, w);
                if (split > start[x]) {
                    Gap missed = new Gap(start[x], split, Routine.LIVE_WAIT);
                    missed.noResp = true;
                    gaps.get(x).add(missed);
                }
                if (w > split) gaps.get(x).add(new Gap(split, w, Routine.LIVE_WAIT));
                changed = true;
            }
        }
        for (Ev e : breaks.values()) {
            // the part of the slot the break began in (a slot changed in the middle is several parts with one number)
            int x = partAt(base, start, end, e.src, e.ts);
            if (x < 0 || base.sc.slots.get(x).block.skipped) continue;
            long a = Math.max(e.ts, start[x]);
            long b = Math.min(e.aLong(0), end[x]);
            // the same for a break the person ended with "Back to work": whole minutes are drawn, so it must not run on to the next minute
            if (b <= nowMs) b = Math.min(b, minuteStart(base, nowMs));
            if (b > a) {
                gaps.get(x).add(new Gap(a, b, Routine.LIVE_BREAK));
                changed = true;
            }
        }

        // 3. The parts of the day, in order.
        List<Piece> pieces = new ArrayList<>();
        for (int x = 0; x < n; x++) {
            Routine.Block orig = base.sc.slots.get(x).block;
            List<Gap> g = merged(gaps.get(x));
            // never let the gaps swallow the whole slot: its last minute stays
            if (!g.isEmpty() && g.get(0).from <= start[x] && g.get(g.size() - 1).to >= end[x] && g.size() == 1 && !continues(base, x)) {
                g.get(0).to = Math.max(g.get(0).from, end[x] - MIN);
                if (g.get(0).to <= g.get(0).from) g.clear();
            }
            final long partMs = base.slotStartMs(x);
            if (g.isEmpty()) {
                Routine.Block b = orig.copy();
                b.partMs = partMs;
                int tag = tagEnd[x] != Routine.LIVE_NONE ? tagEnd[x] : tagStart[x];
                b.live = tag;
                addPiece(pieces, base, start[x], end[x], b, nowMs);
                continue;
            }
            long at = start[x];
            boolean anyWork = false;
            int afterFront = Routine.LIVE_NONE;   // what a gap right at the start of the slot says about the work that follows it
            for (Gap gap : g) {
                if (gap.from > at) {
                    Routine.Block w = orig.copy();
                    w.partMs = partMs;
                    w.live = anyWork ? Routine.LIVE_NONE : (afterFront != Routine.LIVE_NONE ? afterFront : tagStart[x]);
                    addPiece(pieces, base, at, gap.from, w, nowMs);
                    anyWork = true;
                }
                Routine.Block gb = gapBlock(gap.live, orig.src, gap.noResp);
                gb.partMs = partMs;
                addPiece(pieces, base, Math.max(gap.from, at), gap.to, gb, nowMs);
                if (!anyWork && gap.from <= start[x]) afterFront = gap.live == Routine.LIVE_WAIT ? Routine.LIVE_LATE : Routine.LIVE_NONE;
                at = Math.max(at, gap.to);
            }
            if (at < end[x]) {
                Routine.Block w = orig.copy();
                w.partMs = partMs;
                if (tagEnd[x] != Routine.LIVE_NONE) w.live = tagEnd[x];
                else if (anyWork) w.live = Routine.LIVE_NONE;
                else w.live = afterFront != Routine.LIVE_NONE ? afterFront : (g.get(0).from <= start[x] ? Routine.LIVE_NONE : tagStart[x]);
                addPiece(pieces, base, at, end[x], w, nowMs);
            }
        }
        if (pieces.isEmpty()) return null;

        // 4. The rest of the day taken off: one "Day off" part from then until sleep.
        if (offNow) {
            int off = minute(base, st.offFrom, nowMs);
            List<Piece> kept = new ArrayList<>();
            boolean placed = false;
            int offStart = off;
            for (Piece pc : pieces) {
                if (placed) {
                    kept.add(pc);
                    continue;
                }
                if (pc.to <= off) {
                    kept.add(pc);
                } else if (pc.from < off) {
                    if (pc.block.kind == Routine.SLEEP) {
                        kept.add(pc);
                        placed = true;   // it is night already: nothing to take off
                    } else {
                        kept.add(new Piece(pc.from, off, pc.block));
                    }
                } else if (pc.block.kind == Routine.SLEEP) {
                    if (pc.from > offStart) kept.add(new Piece(offStart, pc.from, offBlock(pc.from - offStart)));
                    kept.add(pc);
                    placed = true;
                    changed = true;
                }
                // a part between the start of the day off and sleep is dropped
            }
            if (!placed) {
                int last = pieces.get(pieces.size() - 1).to;
                if (last > offStart) {
                    kept.add(new Piece(offStart, last, offBlock(last - offStart)));
                    changed = true;
                }
            }
            pieces = kept;
        }
        if (!changed) return null;

        // 5. Back to a routine laid on the clock.
        Routine r = base.routine.copyLook();
        r.mode = base.routine.mode;
        r.anchor = base.routine.anchor;
        for (Piece pc : pieces) {
            Routine.Block b = pc.block;
            b.mins = Math.max(1, pc.to - pc.from);
            r.blocks.add(b);
        }
        Plan.DayPlan live = new Plan.DayPlan(base.day, r, base.startAbs, base.startMs, base.adjusted);
        live.endMs = base.endMs;
        long elapsed = Math.max(0, (nowMs - live.startMs) / MIN);
        live.tt = (int) (live.sc.start + elapsed);
        live.cur = -1;
        for (int i = 0; i < live.sc.slots.size(); i++) {
            Routine.Slot sl = live.sc.slots.get(i);
            if (live.tt >= sl.start && live.tt < sl.end) {
                live.cur = sl.block.skipped ? -1 : i;
                break;
            }
        }
        return live;
    }

    /**
     * The parts a boundary event is about: {p, x}, where p ends (or goes on, or was taken off) and x begins, or null when the plan has no
     * such pair. A slot changed in the middle is several parts with one number; the event says only the numbers, so the part is found by
     * the time: a part that began early is the one right after the moment it began, a part that was carried into is the one that began
     * before the answer, a part that was taken off is the first one that was.
     */
    private static int[] boundary(Plan.DayPlan base, Ev e, boolean early, boolean skip) {
        int n = base.sc.slots.size();
        int other = toInt(e.b, -1);
        if (skip) {
            int p = -1;
            for (int k = 0; k < n && p < 0; k++) {
                Routine.Block bk = base.sc.slots.get(k).block;
                if (bk.src == e.src && bk.skipped) p = k;
            }
            if (p < 0) return null;
            int x = -1;
            for (int k = p + 1; k < n && x < 0; k++) if (base.sc.slots.get(k).block.src == other) x = k;
            return x < 0 ? null : new int[]{p, x};
        }
        if (early) {
            long t = e.aLong(0);
            long named = Ev.partOf(e.b);
            // the slot that begins early is another slot, or (a plan changed in the middle) the next part of the same one
            for (int x = SlotPlay.indexOfSrc(base, e.src); x >= 0 && x < n; x++) {
                if (base.sc.slots.get(x).block.src != e.src) continue;
                int p = lastPartBefore(base, other, x);
                if (p < 0) continue;
                if (named > 0L) {
                    if (Math.abs(base.slotStartMs(x) - named) < MIN) return new int[]{p, x};
                    continue;
                }
                if (other != e.src) return new int[]{p, x};   // another slot: the first part of this one begins
                if (base.slotStartMs(p) <= t && t < base.slotStartMs(x)) return new int[]{p, x};
            }
            return null;
        }
        // carried on: the part that was starting is the last one that had begun when the answer was given
        int x = -1;
        for (int k = 0; k < n; k++) {
            if (base.sc.slots.get(k).block.src == e.src && (x < 0 || base.slotStartMs(k) <= e.ts)) x = k;
        }
        if (x < 0) return null;
        return new int[]{lastPartBefore(base, other, x), x};
    }

    /**
     * The part of slot {@code src} an answer given at {@code ts} belongs to: the last part that was planned to begin by then, else the first
     * (a slot in one piece has only that one). -1 when the plan has no such slot.
     */
    private static int partAtTime(Plan.DayPlan base, int src, long ts) {
        int found = -1;
        for (int k = 0; k < base.sc.slots.size(); k++) {
            if (base.sc.slots.get(k).block.src != src) continue;
            if (found < 0 || base.slotStartMs(k) <= ts) found = k;
        }
        return found;
    }

    /**
     * The part a late start (or the end of one) belongs to: the part whose alarm rang last before it was given, since that is the question it
     * answers; when no alarm rang, the part that was going on at the time. A change of plan made later does not move it (the part it
     * made begins at that moment, after the question).
     */
    private static int partAnswered(Plan.DayPlan base, List<Ev> ev, Ev answer) {
        long rang = -1L;
        for (Ev r : ev) if (Ev.RANG.equals(r.type) && r.src == answer.src && r.ts <= answer.ts && r.ts > rang) rang = r.ts;
        return partAtTime(base, answer.src, rang >= 0L ? rang : answer.ts);
    }

    /**
     * How long part {@code x} went unanswered (see {@link NoResponse#until}). A slot in one piece looks at all the alarms of its number; a
     * slot changed in the middle has an alarm at the start of each part, and each is answered on its own.
     */
    private static long noResponseOf(Plan.DayPlan base, List<Ev> ev, int x, long nowMs) {
        int src = base.sc.slots.get(x).block.src;
        int next = -1;
        int count = 0;
        for (int k = 0; k < base.sc.slots.size(); k++) {
            if (base.sc.slots.get(k).block.src != src) continue;
            count++;
            if (k > x && next < 0) next = k;
        }
        if (count < 2) return NoResponse.until(ev, src, nowMs);
        return NoResponse.untilPart(ev, src, base.slotStartMs(x), base.slotEndMs(x), next < 0 ? Long.MAX_VALUE : base.slotStartMs(next), nowMs);
    }

    /** The last part of slot {@code src} that lies before index {@code x}, or -1. */
    private static int lastPartBefore(Plan.DayPlan base, int src, int x) {
        if (src < 0) return -1;
        for (int k = Math.min(x, base.sc.slots.size()) - 1; k >= 0; k--) if (base.sc.slots.get(k).block.src == src) return k;
        return -1;
    }

    /**
     * The part of slot {@code src} that holds the moment {@code ms}, by the times as they are now (after finishing early and the like): the
     * first part when the slot is in one piece. When no part holds it, the last part that began before it, else the first.
     */
    private static int partAt(Plan.DayPlan base, long[] start, long[] end, int src, long ms) {
        int first = -1;
        int lastBefore = -1;
        for (int i = 0; i < base.sc.slots.size(); i++) {
            if (base.sc.slots.get(i).block.src != src) continue;
            if (first < 0) first = i;
            if (ms >= start[i] && ms < end[i]) return i;
            if (start[i] <= ms) lastBefore = i;
        }
        return lastBefore >= 0 ? lastBefore : first;
    }

    /** True when the slot after part {@code x} is another part of the same slot (a slot changed for its first or last minutes only). */
    private static boolean continues(Plan.DayPlan base, int x) {
        if (x < 0 || x + 1 >= base.sc.slots.size()) return false;
        Routine.Block a = base.sc.slots.get(x).block;
        Routine.Block b = base.sc.slots.get(x + 1).block;
        return a.src >= 0 && a.src == b.src && !b.skipped;
    }

    /** True when every slot between {@code p} and {@code x} (exclusive) was taken off, so nothing is lost when the boundary skips over them. */
    private static boolean takenOffBetween(Plan.DayPlan base, int p, int x) {
        for (int k = p + 1; k < x; k++) if (!base.sc.slots.get(k).block.skipped) return false;
        return true;
    }

    /** The start of the minute that {@code ms} is in, counted from the start of the day (the minute the card is in). */
    private static long minuteStart(Plan.DayPlan base, long ms) {
        return base.startMs + Math.floorDiv(ms - base.startMs, MIN) * MIN;
    }

    /** Minutes from the start of the day, to the nearest minute. */
    private static int minute(Plan.DayPlan base, long ms) {
        return (int) Math.round((ms - base.startMs) / (double) MIN);
    }

    /**
     * Minutes from the start of the day for a boundary of the card. The card is drawn in whole minutes, to the nearest one, but a boundary
     * that has already happened is never drawn in the future: a break, a finish, a carry-on or a day off pressed at 40 seconds past the
     * minute used to be drawn from the next minute, and the card went on showing the old slot for up to half a minute after the tap.
     */
    private static int minute(Plan.DayPlan base, long ms, long nowMs) {
        if (ms <= nowMs) ms = Math.min(ms, minuteStart(base, nowMs));
        return minute(base, ms);
    }

    private static void addPiece(List<Piece> out, Plan.DayPlan base, long from, long to, Routine.Block b, long nowMs) {
        int a = minute(base, from, nowMs);
        int z = minute(base, to, nowMs);
        if (z <= a) return;
        out.add(new Piece(a, z, b));
    }

    private static Routine.Block gapBlock(int live, int owner, boolean noResp) {
        Routine.Block b = new Routine.Block(Routine.REST, live == Routine.LIVE_BREAK ? BREAK_TITLE : noResp ? NORESP_TITLE : WAIT_TITLE, 1);
        b.setAlert(Routine.ALERT_NONE);
        b.live = live;
        b.owner = owner;
        return b;
    }

    private static Routine.Block offBlock(int mins) {
        Routine.Block b = new Routine.Block(Routine.REST, OFF_TITLE, Math.max(1, mins));
        b.setAlert(Routine.ALERT_NONE);
        b.live = Routine.LIVE_OFF;
        return b;
    }

    /** Sorts the gaps and joins those that overlap; a break wins over a wait where they meet. */
    private static List<Gap> merged(List<Gap> in) {
        List<Gap> list = new ArrayList<>();
        for (Gap g : in) if (g.to > g.from) {
            Gap c = new Gap(g.from, g.to, g.live);
            c.noResp = g.noResp;
            list.add(c);
        }
        Collections.sort(list, new Comparator<Gap>() {
            @Override
            public int compare(Gap a, Gap b) {
                return Long.compare(a.from, b.from);
            }
        });
        List<Gap> out = new ArrayList<>();
        for (Gap g : list) {
            // overlapping stretches join; stretches that only touch join when they are the same kind (no response stays apart from a later start)
            Gap prev = out.isEmpty() ? null : out.get(out.size() - 1);
            if (prev != null && (g.from < prev.to || g.from == prev.to && g.live == prev.live && g.noResp == prev.noResp)) {
                Gap last = out.get(out.size() - 1);
                last.to = Math.max(last.to, g.to);
                if (g.live == Routine.LIVE_BREAK) last.live = Routine.LIVE_BREAK;
                last.noResp |= g.noResp;
            } else {
                out.add(g);
            }
        }
        return out;
    }
}
