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
    public static final String OFF_TITLE = "Day off";

    private LivePlan() {
    }

    /** A stretch inside a slot where its own activity is not going on. */
    private static final class Gap {
        long from;
        long to;
        int live;

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
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
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
            int x = SlotPlay.indexOfSrc(base, skip ? toInt(e.b, -1) : e.src);
            int p = SlotPlay.indexOfSrc(base, skip ? e.src : toInt(e.b, -1));
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
        // the last answer about a slot's late start is the one that counts: it can end the wait early ("start now")
        Map<Integer, Ev> lastDelay = new LinkedHashMap<>();
        for (Ev e : ev) if (Ev.DELAY.equals(e.type)) lastDelay.put(e.src, e);
        for (Ev e : lastDelay.values()) {
            int x = SlotPlay.indexOfSrc(base, e.src);
            if (x < 0 || base.sc.slots.get(x).block.skipped) continue;
            long w = Math.min(e.aLong(0), end[x] - MIN);
            if (w > start[x]) {
                gaps.get(x).add(new Gap(start[x], w, Routine.LIVE_WAIT));
                changed = true;
            }
        }
        for (Ev e : breaks.values()) {
            int x = SlotPlay.indexOfSrc(base, e.src);
            if (x < 0 || base.sc.slots.get(x).block.skipped) continue;
            long a = Math.max(e.ts, start[x]);
            long b = Math.min(e.aLong(0), end[x]);
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
            if (!g.isEmpty() && g.get(0).from <= start[x] && g.get(g.size() - 1).to >= end[x] && g.size() == 1) {
                g.get(0).to = Math.max(g.get(0).from, end[x] - MIN);
                if (g.get(0).to <= g.get(0).from) g.clear();
            }
            if (g.isEmpty()) {
                Routine.Block b = orig.copy();
                int tag = tagEnd[x] != Routine.LIVE_NONE ? tagEnd[x] : tagStart[x];
                b.live = tag;
                addPiece(pieces, base, start[x], end[x], b);
                continue;
            }
            long at = start[x];
            boolean anyWork = false;
            int afterFront = Routine.LIVE_NONE;   // what a gap right at the start of the slot says about the work that follows it
            for (Gap gap : g) {
                if (gap.from > at) {
                    Routine.Block w = orig.copy();
                    w.live = anyWork ? Routine.LIVE_NONE : (afterFront != Routine.LIVE_NONE ? afterFront : tagStart[x]);
                    addPiece(pieces, base, at, gap.from, w);
                    anyWork = true;
                }
                addPiece(pieces, base, Math.max(gap.from, at), gap.to, gapBlock(gap.live, orig.src));
                if (!anyWork && gap.from <= start[x]) afterFront = gap.live == Routine.LIVE_WAIT ? Routine.LIVE_LATE : Routine.LIVE_NONE;
                at = Math.max(at, gap.to);
            }
            if (at < end[x]) {
                Routine.Block w = orig.copy();
                if (tagEnd[x] != Routine.LIVE_NONE) w.live = tagEnd[x];
                else if (anyWork) w.live = Routine.LIVE_NONE;
                else w.live = afterFront != Routine.LIVE_NONE ? afterFront : (g.get(0).from <= start[x] ? Routine.LIVE_NONE : tagStart[x]);
                addPiece(pieces, base, at, end[x], w);
            }
        }
        if (pieces.isEmpty()) return null;

        // 4. The rest of the day taken off: one "Day off" part from then until sleep.
        if (offNow) {
            int off = minute(base, st.offFrom);
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

    /** True when every slot between {@code p} and {@code x} (exclusive) was taken off, so nothing is lost when the boundary skips over them. */
    private static boolean takenOffBetween(Plan.DayPlan base, int p, int x) {
        for (int k = p + 1; k < x; k++) if (!base.sc.slots.get(k).block.skipped) return false;
        return true;
    }

    /** Minutes from the start of the day, to the nearest minute. */
    private static int minute(Plan.DayPlan base, long ms) {
        return (int) Math.round((ms - base.startMs) / (double) MIN);
    }

    private static void addPiece(List<Piece> out, Plan.DayPlan base, long from, long to, Routine.Block b) {
        int a = minute(base, from);
        int z = minute(base, to);
        if (z <= a) return;
        out.add(new Piece(a, z, b));
    }

    private static Routine.Block gapBlock(int live, int owner) {
        Routine.Block b = new Routine.Block(Routine.REST, live == Routine.LIVE_BREAK ? BREAK_TITLE : WAIT_TITLE, 1);
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
        for (Gap g : in) if (g.to > g.from) list.add(new Gap(g.from, g.to, g.live));
        Collections.sort(list, new Comparator<Gap>() {
            @Override
            public int compare(Gap a, Gap b) {
                return Long.compare(a.from, b.from);
            }
        });
        List<Gap> out = new ArrayList<>();
        for (Gap g : list) {
            if (!out.isEmpty() && g.from <= out.get(out.size() - 1).to) {
                Gap last = out.get(out.size() - 1);
                last.to = Math.max(last.to, g.to);
                if (g.live == Routine.LIVE_BREAK) last.live = Routine.LIVE_BREAK;
            } else {
                out.add(g);
            }
        }
        return out;
    }
}
