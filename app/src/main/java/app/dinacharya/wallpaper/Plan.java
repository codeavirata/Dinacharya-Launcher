package app.dinacharya.wallpaper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out what a given day looks like once temporary changes are applied to the saved routine,
 * and the choices offered when you go to sleep later than planned. Uses no Android classes.
 */
public final class Plan {
    /** Rest and personal slots are never made shorter than half, or 5 minutes, whichever is more. */
    static final int FLEX_MIN = 5;
    /** The longest late-night delay the app will try to make up for. */
    static final int MAX_SHIFT = 720;

    private Plan() {
    }

    // ------------------------------------------------------------------ one day

    /** One schedule day: the routine with temporary changes applied, placed on the clock. */
    public static final class DayPlan {
        public final LocalDate day;
        public final Routine routine;
        public final Routine.Schedule sc;
        public final int startAbs;
        public final long startMs;
        public long endMs;
        /** "Now" in the schedule's own minutes (sc.start + minutes since the day started). */
        public int tt;
        /** Index of the slot running now, or -1. */
        public int cur = -1;
        /** True when temporary changes are in effect on this day. */
        public final boolean adjusted;

        DayPlan(LocalDate day, Routine routine, int startAbs, long startMs, boolean adjusted) {
            this.day = day;
            this.routine = routine;
            this.sc = routine.compute();
            this.startAbs = startAbs;
            this.startMs = startMs;
            this.adjusted = adjusted;
        }

        public long slotStartMs(int i) {
            return startMs + (sc.slots.get(i).start - sc.start) * 60000L;
        }

        public long slotEndMs(int i) {
            return startMs + (sc.slots.get(i).end - sc.start) * 60000L;
        }

        public int lastSleepIndex() {
            for (int i = sc.slots.size() - 1; i >= 0; i--) {
                if (sc.slots.get(i).block.kind == Routine.SLEEP) return i;
            }
            return -1;
        }
    }

    public static String signature(Routine base) {
        StringBuilder sb = new StringBuilder();
        sign(sb, base);
        // Other routines and the days they are used on (only when there are any, so older saves keep their signature).
        if (base.hasWeek()) {
            sb.append("#days");
            for (int d : base.dayUse) sb.append(',').append(d);
            for (Routine o : base.others) {
                sb.append('#');
                sign(sb, o);
            }
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    /**
     * Like {@link #signature} but also covering the routines made for one date. A number of a slot means a place in a list, so anything
     * remembered by slot number is only good while this stays the same.
     */
    public static String scheduleSignature(Routine base) {
        StringBuilder sb = new StringBuilder(signature(base));
        for (Routine t : base.temps) {
            if (t.tempDate == null) continue;
            sb.append('#').append(t.tempDate).append(':').append(t.compute().start);
            for (Routine.Block b : t.blocks) sb.append('|').append(b.kind).append(':').append(b.mins).append(':').append(b.title);
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private static void sign(StringBuilder sb, Routine r) {
        sb.append(r.compute().start);
        for (Routine.Block b : r.blocks) {
            sb.append('|').append(b.kind).append(':').append(b.mins).append(':').append(b.title);
        }
    }

    /** The clock minute (0..1439) the routine of {@code day} starts at, before any temporary change. */
    static int baseStartOn(Routine base, LocalDate day) {
        return base.forDay(day).compute().start;
    }

    /** Overrides that belong to this routine, or an empty set when the routine was edited since. */
    public static Overrides valid(Routine base, Overrides ov) {
        Overrides fresh = new Overrides();
        fresh.sig = signature(base);
        if (ov != null && fresh.sig.equals(ov.sig)) return ov;
        return fresh;
    }

    private static long startMsOf(LocalDate d, int baseStart, Overrides ov, ZoneId zone) {
        Overrides.Shift sh = ov == null ? null : ov.shiftFor(d);
        int abs = baseStart + (sh == null ? 0 : sh.mins) - (ov == null ? 0 : ov.earlyFor(d));
        return d.atStartOfDay(zone).plusMinutes(abs).toInstant().toEpochMilli();
    }

    /** The routine for the schedule day that starts on {@code day}. */
    public static DayPlan plan(Routine main, Overrides ov, LocalDate day, ZoneId zone) {
        Routine base = main.forDay(day);
        int baseStart = base.compute().start;
        List<Routine.Block> blocks = new ArrayList<>();
        for (int i = 0; i < base.blocks.size(); i++) {
            Routine.Block c = base.blocks.get(i).copy();
            c.src = i;
            blocks.add(c);
        }
        boolean adjusted = false;
        int startAbs = baseStart;

        // When tomorrow follows a different routine (a weekday before a weekend day), the night stretches
        // or shrinks so it ends exactly when tomorrow starts.
        int nextStart = baseStartOn(main, day.plusDays(1));
        if ((main.hasWeek() || main.hasTemps()) && main.dayKey(day) != main.dayKey(day.plusDays(1))) {
            int gap = (1440 + nextStart) - (baseStart + base.totalMinutes());
            if (gap > 0) extendTail(blocks, gap, false);
            else if (gap < 0) trimTail(blocks, -gap);
        }

        Overrides.Shift sh = ov == null ? null : ov.shiftFor(day);
        if (sh != null) {
            if (sh.type == Overrides.WHOLE) absorb(blocks, sh.mins);
            else trimFront(blocks, sh.mins);
            startAbs += sh.mins;
            adjusted = true;
        }
        // The person woke early and started the day: it begins that much earlier, and its first slot keeps its end, so it gets the time.
        int early = ov == null ? 0 : ov.earlyFor(day);
        if (early > 0 && !blocks.isEmpty()) {
            blocks.get(0).mins += early;
            startAbs -= early;
            adjusted = true;
        }
        Overrides.Shift nx = ov == null ? null : ov.shiftFor(day.plusDays(1));
        if (nx != null) {
            extendTail(blocks, nx.mins, true);
            adjusted = true;
        }
        // ...and the night before it ends that much earlier.
        int wokeEarly = ov == null ? 0 : ov.earlyFor(day.plusDays(1));
        if (wokeEarly > 0) {
            shortenNight(blocks, wokeEarly);
            adjusted = true;
        }
        if (ov != null) {
            String key = day.toString();
            for (Overrides.Edit e : ov.edits) {
                if (!e.day.equals(key)) continue;
                if (e.src < 0 || e.src >= base.blocks.size() || !base.blocks.get(e.src).title.equals(e.orig)) continue;
                if (e.with >= 0 && e.name.isEmpty() && !e.skip) {
                    int i1 = indexOfSrc(blocks, e.src);
                    int i2 = indexOfSrc(blocks, e.with);
                    if (i1 < 0 || i2 < 0 || i1 == i2) continue;
                    Routine.Block x = blocks.get(i1);
                    Routine.Block y = blocks.get(i2);
                    blocks.set(i1, y);
                    blocks.set(i2, x);
                    x.changed = true;
                    y.changed = true;
                    x.origTitle = x.title;
                    y.origTitle = y.title;
                    adjusted = true;
                    continue;
                }
                if ((e.mins > 0 || e.from > 0) && !e.skip) {
                    int i = indexOfSrc(blocks, e.src);
                    if (i < 0) continue;
                    Routine.Block b = blocks.get(i);
                    // a plan changed in the middle of the slot begins there: the minutes before stay the slot's own (always a minute is left of them)
                    int lead = Math.min(e.from, Math.max(0, b.mins - 1));
                    int room = b.mins - lead;
                    int len = e.mins > 0 ? Math.min(e.mins, room) : room;
                    if (!e.name.isEmpty() && (lead > 0 || e.mins < b.mins)) {
                        Routine.Block first = b.copy();
                        first.src = b.src;
                        first.mins = len;
                        first.changed = true;
                        first.origTitle = b.title;
                        first.title = e.name;
                        if (e.kind >= 0 && e.kind < Routine.SLEEP) first.kind = e.kind;
                        if (!e.sub.isEmpty()) first.sub = e.sub;
                        Routine.Block before = lead > 0 ? b.copy() : null;
                        if (before != null) {
                            before.src = b.src;
                            before.mins = lead;
                            first.wake = false;   // the slot's own start (the wake-up alarm) is in the part before
                        }
                        b.mins = room - len;
                        b.wake = false;
                        int at = i;
                        if (before != null) blocks.add(at++, before);
                        blocks.add(at++, first);
                        if (b.mins <= 0) blocks.remove(at);   // the new plan took all that was left
                        adjusted = true;
                        continue;
                    }
                }
                for (Routine.Block b : blocks) {
                    if (b.src != e.src) continue;
                    b.changed = true;
                    b.origTitle = b.title;
                    if (e.skip) b.skipped = true;
                    else if (!e.name.isEmpty()) b.title = e.name;
                    if (!e.skip && e.kind >= 0 && e.kind < Routine.SLEEP && b.kind != Routine.SLEEP) b.kind = e.kind;
                    if (!e.skip && !e.sub.isEmpty()) b.sub = e.sub;
                    adjusted = true;
                }
            }
        }
        if (ov != null && !ov.steps.isEmpty() && applySteps(base, blocks, ov, day.toString())) adjusted = true;
        Routine r = base.copyLook();
        r.mode = Routine.MODE_WAKE;
        r.anchor = Routine.mod(startAbs, 1440);
        r.blocks.addAll(blocks);
        long startMs = day.atStartOfDay(zone).plusMinutes(startAbs).toInstant().toEpochMilli();
        DayPlan p = new DayPlan(day, r, startAbs, startMs, adjusted);
        p.endMs = startMsOf(day.plusDays(1), nextStart, ov, zone);
        return p;
    }

    /** The schedule day that contains {@code nowMs}, with the running slot worked out. */
    public static DayPlan resolve(Routine base, Overrides ov, long nowMs, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
        LocalDate best = today.minusDays(2);
        for (LocalDate d = today.minusDays(2); !d.isAfter(today.plusDays(1)); d = d.plusDays(1)) {
            if (startMsOf(d, baseStartOn(base, d), ov, zone) <= nowMs) best = d;
        }
        DayPlan p = plan(base, ov, best, zone);
        long elapsed = Math.max(0, (nowMs - p.startMs) / 60000L);
        p.tt = (int) (p.sc.start + elapsed);
        p.cur = -1;
        for (int i = 0; i < p.sc.slots.size(); i++) {
            Routine.Slot sl = p.sc.slots.get(i);
            if (p.tt >= sl.start && p.tt < sl.end) {
                p.cur = sl.block.skipped ? -1 : i;
                break;
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ the day changes

    /**
     * The hours between midnight and the next wake-up time, when the calendar says it is a new day but the schedule
     * day is still the old one. The poster uses it to scroll the old schedule up and out while the new one rises, so
     * the new day has fully taken its place when it starts.
     */
    public static final class Transition {
        /** The schedule day that is still running. */
        public final DayPlan from;
        /** The schedule day that starts at the next wake-up time. */
        public final DayPlan to;
        /** 0 at midnight, 1 at the wake-up time. */
        public final float progress;

        Transition(DayPlan from, DayPlan to, float progress) {
            this.from = from;
            this.to = to;
            this.progress = progress;
        }
    }

    /** The scroll between two days at {@code nowMs}, or null when the calendar day and the schedule day agree. */
    public static Transition transition(Routine base, Overrides ov, DayPlan cur, long nowMs, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
        if (!today.isAfter(cur.day)) return null;
        DayPlan next = plan(base, ov, cur.day.plusDays(1), zone);
        long midnight = today.atStartOfDay(zone).toInstant().toEpochMilli();
        if (next.startMs <= midnight || nowMs >= next.startMs) return null;
        float p = (nowMs - midnight) / (float) (next.startMs - midnight);
        next.cur = -1;
        next.tt = next.sc.start - 1;
        return new Transition(cur, next, Math.max(0f, Math.min(1f, p)));
    }

    // ------------------------------------------------------------------ making room

    /** What happened to one slot when the day was adjusted. */
    public static final class Change {
        public final String title;
        public final int from;
        public final int to;

        Change(String title, int from, int to) {
            this.title = title;
            this.from = from;
            this.to = to;
        }

        public boolean dropped() {
            return to <= 0;
        }
    }

    public static final class Absorb {
        public boolean fits = true;
        public int left;
        public final List<Change> changes = new ArrayList<>();
    }

    private static boolean flexible(int kind) {
        return kind == Routine.REST || kind == Routine.PERSONAL;
    }

    /**
     * Takes {@code need} minutes out of the day without touching tasks, meals or sleep: rests are
     * shortened first, then personal slots, and if that is not enough the last rest or personal
     * slots are dropped.
     */
    static Absorb absorb(List<Routine.Block> blocks, int need) {
        Absorb res = new Absorb();
        Map<Routine.Block, Integer> before = new IdentityHashMap<>();
        List<Routine.Block> order = new ArrayList<>(blocks);
        for (Routine.Block b : blocks) before.put(b, b.mins);
        int remaining = Math.max(0, need);

        int[] classes = {Routine.REST, Routine.PERSONAL};
        for (int kind : classes) {
            if (remaining <= 0) break;
            List<Routine.Block> group = new ArrayList<>();
            for (Routine.Block b : blocks) if (b.kind == kind) group.add(b);
            int n = group.size();
            long[] cap = new long[n];
            long total = 0;
            for (int i = 0; i < n; i++) {
                int m = group.get(i).mins;
                int min = Math.min(m, Math.max(FLEX_MIN, (m + 1) / 2));
                cap[i] = m - min;
                total += cap[i];
            }
            if (total == 0) continue;
            long take = Math.min(remaining, total);
            long[] share = new long[n];
            long[] frac = new long[n];
            long given = 0;
            for (int i = 0; i < n; i++) {
                share[i] = take * cap[i] / total;
                frac[i] = take * cap[i] % total;
                given += share[i];
            }
            while (given < take) {
                int bestI = -1;
                for (int i = 0; i < n; i++) {
                    if (share[i] < cap[i] && (bestI < 0 || frac[i] > frac[bestI])) bestI = i;
                }
                if (bestI < 0) break;
                share[bestI]++;
                frac[bestI] = -1;
                given++;
            }
            for (int i = 0; i < n; i++) group.get(i).mins -= (int) share[i];
            remaining -= (int) given;
        }

        for (int i = blocks.size() - 1; i >= 0 && remaining > 0; i--) {
            Routine.Block b = blocks.get(i);
            if (!flexible(b.kind)) continue;
            if (b.mins <= remaining) {
                remaining -= b.mins;
                blocks.remove(i);
            } else {
                b.mins -= remaining;
                remaining = 0;
            }
        }
        res.left = remaining;
        res.fits = remaining == 0;
        for (Routine.Block b : order) {
            int was = before.get(b);
            boolean gone = !containsIdentity(blocks, b);
            if (gone) res.changes.add(new Change(b.title, was, 0));
            else if (b.mins != was) res.changes.add(new Change(b.title, was, b.mins));
        }
        return res;
    }

    /** Drops the first {@code d} minutes of the day. Slots wholly inside are removed, one partly inside is trimmed. */
    static Absorb trimFront(List<Routine.Block> blocks, int d) {
        Absorb res = new Absorb();
        Map<Routine.Block, Integer> before = new IdentityHashMap<>();
        List<Routine.Block> order = new ArrayList<>(blocks);
        for (Routine.Block b : blocks) before.put(b, b.mins);
        int rem = Math.max(0, d);
        while (rem > 0 && !blocks.isEmpty()) {
            Routine.Block b = blocks.get(0);
            if (b.kind == Routine.SLEEP) break;
            if (b.mins <= rem) {
                rem -= b.mins;
                blocks.remove(0);
            } else {
                b.mins -= rem;
                rem = 0;
            }
        }
        res.left = rem;
        res.fits = rem == 0;
        for (Routine.Block b : order) {
            int was = before.get(b);
            if (!containsIdentity(blocks, b)) res.changes.add(new Change(b.title, was, 0));
            else if (b.mins != was) res.changes.add(new Change(b.title, was, b.mins));
        }
        return res;
    }

    private static boolean containsIdentity(List<Routine.Block> list, Routine.Block b) {
        for (Routine.Block x : list) if (x == b) return true;
        return false;
    }

    /**
     * Makes the last sleep run on by {@code d} minutes, because the next morning starts that much later.
     * {@code temporary} marks it as a change for this night only (not part of the planned sleep).
     */
    static void extendTail(List<Routine.Block> blocks, int d, boolean temporary) {
        if (d <= 0) return;
        if (!blocks.isEmpty()) {
            Routine.Block last = blocks.get(blocks.size() - 1);
            if (last.kind == Routine.SLEEP) {
                last.mins += d;
                if (temporary) last.extended += d;
                return;
            }
        }
        Routine.Block extra = new Routine.Block(Routine.SLEEP, "Sleep", d);
        if (temporary) extra.extended = d;
        blocks.add(extra);
    }

    static void extendTail(List<Routine.Block> blocks, int d) {
        extendTail(blocks, d, true);
    }

    /**
     * Ends the night {@code d} minutes sooner because the next day was started early. Only this night: the sleep that was planned stays
     * what it was (the cut is taken off {@code extended} as well), so a report still compares the sleep with the plan.
     */
    static void shortenNight(List<Routine.Block> blocks, int d) {
        for (int i = blocks.size() - 1; i >= 0 && d > 0; i--) {
            Routine.Block b = blocks.get(i);
            int take = Math.min(d, b.mins - 1);
            if (take <= 0) continue;
            b.mins -= take;
            if (b.kind == Routine.SLEEP) b.extended -= take;
            d -= take;
        }
    }

    /** Shortens the end of the day by {@code d} minutes because the next day starts that much earlier. */
    static void trimTail(List<Routine.Block> blocks, int d) {
        for (int i = blocks.size() - 1; i >= 0 && d > 0; i--) {
            Routine.Block b = blocks.get(i);
            int can = b.mins - FLEX_MIN;
            if (can <= 0) continue;
            int take = Math.min(can, d);
            b.mins -= take;
            d -= take;
        }
    }

    // ------------------------------------------------------------------ going to sleep late

    public static final int OPT_NORMAL = 0, OPT_WHOLE = 1, OPT_SKIP = 2;

    /** One way to set tomorrow's wake-up. */
    public static final class WakeOption {
        public final int type;
        public final long wakeMs;
        public final int shiftMin;
        public final boolean fits;
        public final int sleepMin;
        /** The sleep the plan asks for (set on the first option; 0 when unknown). */
        public int plannedMin;
        public final List<String> notes = new ArrayList<>();

        WakeOption(int type, long wakeMs, int shiftMin, boolean fits, int sleepMin) {
            this.type = type;
            this.wakeMs = wakeMs;
            this.shiftMin = shiftMin;
            this.fits = fits;
            this.sleepMin = sleepMin;
        }
    }

    /**
     * The choices when you go to sleep at {@code sleepAtMs} in the schedule day {@code day}.
     * The first option always keeps the normal alarm. When you are not late the list has that one option only.
     */
    public static List<WakeOption> wakeOptions(Routine base, Overrides ov, LocalDate day, long sleepAtMs,
                                               ZoneId zone, boolean h24) {
        Overrides plain = (ov == null ? new Overrides() : ov.copy());
        plain.setShift(day.plusDays(1), 0, 0);
        plain.setEarly(day.plusDays(1), 0);
        DayPlan today = plan(base, plain, day, zone);
        DayPlan tomorrow = plan(base, plain, day.plusDays(1), zone);
        long normalWake = tomorrow.startMs;

        int plannedSleep = 480;
        int si = today.lastSleepIndex();
        if (si >= 0) {
            Routine.Block sb = today.sc.slots.get(si).block;
            plannedSleep = Math.max(1, sb.mins - sb.extended);
        }
        int sleepNormal = (int) Math.max(0, (normalWake - sleepAtMs) / 60000L);
        long wishWake = sleepAtMs + plannedSleep * 60000L;
        int shift = (int) Math.max(0, (wishWake - normalWake + 59999L) / 60000L);

        List<WakeOption> out = new ArrayList<>();
        WakeOption normal = new WakeOption(OPT_NORMAL, normalWake, 0, true, sleepNormal);
        normal.plannedMin = plannedSleep;
        normal.notes.add(shift > 0
                ? "Alarm at " + clock(normalWake, zone, h24) + ". You sleep " + Routine.formatDuration(sleepNormal)
                + " instead of " + Routine.formatDuration(plannedSleep) + "."
                : "Alarm at " + clock(normalWake, zone, h24) + ". You sleep " + Routine.formatDuration(sleepNormal) + ".");
        normal.notes.add("Nothing changes tomorrow.");
        out.add(normal);
        if (shift <= 0) return out;

        long wake = normalWake + shift * 60000L;

        // Option 2: the morning starts later and rests / personal slots give back the time.
        List<Routine.Block> b2 = tomorrowBlocks(base, tomorrow);
        boolean tooLate = shift > MAX_SHIFT;
        Absorb a2 = tooLate ? null : absorb(b2, shift);
        boolean fits2 = a2 != null && a2.fits;
        WakeOption whole = new WakeOption(OPT_WHOLE, wake, shift, fits2, plannedSleep);
        if (tooLate) {
            whole.notes.add("A delay over " + Routine.formatDuration(MAX_SHIFT) + " is too long to make up.");
        } else {
            whole.notes.add("Alarm at " + clock(wake, zone, h24) + ". You sleep the full "
                    + Routine.formatDuration(plannedSleep) + ".");
            whole.notes.add("Every slot moves " + Routine.formatDuration(shift) + " later. Bedtime stays at "
                    + clock(tomorrow.slotStartMs(Math.max(0, tomorrow.lastSleepIndex())), zone, h24) + ".");
            if (!fits2) {
                whole.notes.add("Not enough rest or free time to give back " + Routine.formatDuration(a2.left)
                        + ". Pick another option.");
            } else if (a2.changes.isEmpty()) {
                whole.notes.add("No slot needs changing.");
            } else {
                describe(whole.notes, a2);
            }
        }
        out.add(whole);

        // Option 3: the morning starts later; slots covered by the extra sleep are dropped.
        List<Routine.Block> b3 = tomorrowBlocks(base, tomorrow);
        Absorb a3 = tooLate ? null : trimFront(b3, shift);
        boolean fits3 = a3 != null && a3.fits;
        WakeOption skip = new WakeOption(OPT_SKIP, wake, shift, fits3, plannedSleep);
        if (tooLate) {
            skip.notes.add("A delay over " + Routine.formatDuration(MAX_SHIFT) + " is too long to make up.");
        } else {
            skip.notes.add("Alarm at " + clock(wake, zone, h24) + ". You sleep the full "
                    + Routine.formatDuration(plannedSleep) + ".");
            if (!fits3) {
                skip.notes.add("The extra sleep would cover your whole morning. Pick another option.");
            } else {
                skip.notes.add("Later slots keep their normal times.");
                describe(skip.notes, a3);
                if (!b3.isEmpty() && !a3.changes.isEmpty()) {
                    Routine.Block first = b3.get(0);
                    long normalFirstStart = wake;
                    for (int i = 0; i < tomorrow.sc.slots.size(); i++) {
                        if (tomorrow.sc.slots.get(i).block.src == first.src) {
                            normalFirstStart = tomorrow.slotStartMs(i);
                            break;
                        }
                    }
                    // A slot only partly covered by the extra sleep begins at the alarm.
                    long firstStart = Math.max(wake, normalFirstStart);
                    skip.notes.add("Your day starts with \"" + first.title + "\" at " + clock(firstStart, zone, h24) + ".");
                }
            }
        }
        out.add(skip);
        return out;
    }

    private static List<Routine.Block> tomorrowBlocks(Routine base, DayPlan tomorrow) {
        List<Routine.Block> l = new ArrayList<>();
        for (Routine.Slot s : tomorrow.sc.slots) l.add(s.block.copy());
        return l;
    }

    private static void describe(List<String> notes, Absorb a) {
        StringBuilder shortened = new StringBuilder();
        StringBuilder dropped = new StringBuilder();
        for (Change c : a.changes) {
            if (c.dropped()) {
                if (dropped.length() > 0) dropped.append(", ");
                dropped.append(c.title);
            } else {
                if (shortened.length() > 0) shortened.append(", ");
                shortened.append(c.title).append(' ').append(c.from).append("→").append(c.to).append(" min");
            }
        }
        if (shortened.length() > 0) notes.add("Shortened: " + shortened + ".");
        if (dropped.length() > 0) notes.add("Dropped: " + dropped + ".");
    }

    private static int indexOfSrc(List<Routine.Block> blocks, int src) {
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).src == src) return i;
        return -1;
    }

    /** The place of the {@code ord}-th part (0 is the first) of the slot {@code src}, or -1. A slot split in two for the day has two parts. */
    private static int indexOfPart(List<Routine.Block> blocks, int src, int ord) {
        int seen = 0;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).src != src) continue;
            if (seen == ord) return i;
            seen++;
        }
        return -1;
    }

    private static boolean sameSlot(Routine base, int src, String orig) {
        return src >= 0 && src < base.blocks.size() && base.blocks.get(src).title.equals(orig);
    }

    /**
     * Applies the day's time moves and new activities ({@link Overrides.Step}) to the slots, in the order they were made. A step that no
     * longer fits (a slot it names is gone, or it would leave a slot with no time) is left out. The day keeps its length.
     */
    private static boolean applySteps(Routine base, List<Routine.Block> blocks, Overrides ov, String key) {
        boolean any = false;
        for (Overrides.Step t : ov.steps) {
            if (!t.day.equals(key)) continue;
            if (!sameSlot(base, t.src, t.orig) || !sameSlot(base, t.to, t.toOrig)) continue;
            int i = indexOfPart(blocks, t.src, t.ord);
            int j = indexOfPart(blocks, t.to, t.toOrd);
            if (i < 0 || j < 0 || i == j) continue;
            Routine.Block x = blocks.get(i);
            Routine.Block y = blocks.get(j);
            if (t.type == Overrides.MOVE) {
                int d = t.mins;
                if (d > 0) d = Math.min(d, y.mins - 1);
                else d = -Math.min(-d, x.mins - 1);
                if (d == 0) continue;
                x.mins += d;
                y.mins -= d;
                any = true;
            } else {
                // the slot ends after t.mins; a new activity takes its place up to len; the slot after it keeps its own end
                if (j != i + 1) continue;
                int keep = Math.max(1, Math.min(t.mins, x.mins));
                int freed = x.mins - keep;
                int len = Math.min(Math.max(1, t.len), y.mins + freed - 1);
                if (len < 1) continue;
                Routine.Block n = y.copy();
                n.src = y.src;
                n.mins = len;
                n.wake = false;
                n.changed = true;
                n.origTitle = y.title;
                if (!t.name.isEmpty()) n.title = t.name;
                if (t.kind >= 0 && t.kind < Routine.SLEEP && y.kind != Routine.SLEEP) n.kind = t.kind;
                if (!t.sub.isEmpty()) n.sub = t.sub;
                x.mins = keep;
                y.mins = y.mins + freed - len;
                blocks.add(j, n);
                any = true;
            }
        }
        return any;
    }

    /** Which part of its slot the slot at {@code index} of {@code p} is: 0 for the first (or only) part. */
    public static int partOf(DayPlan p, int index) {
        int ord = 0;
        int src = p.sc.slots.get(index).block.src;
        for (int k = 0; k < index; k++) if (p.sc.slots.get(k).block.src == src) ord++;
        return ord;
    }

    private static String origTitle(Routine main, DayPlan p, int index) {
        Routine base = main.forDay(p.day);
        int src = p.sc.slots.get(index).block.src;
        return src >= 0 && src < base.blocks.size() ? base.blocks.get(src).title : "";
    }

    /**
     * Moves {@code mins} minutes between two slots of {@code p} for this day: the slot at {@code index} grows by that much and the one at
     * {@code from} gives it (a negative number: the first shrinks and the other grows). Returns false when it cannot be recorded.
     */
    public static boolean moveMinutes(Routine main, Overrides ov, DayPlan p, int index, int from, int mins) {
        if (index < 0 || from < 0 || index >= p.sc.slots.size() || from >= p.sc.slots.size() || index == from || mins == 0) return false;
        Routine.Block x = p.sc.slots.get(index).block;
        Routine.Block y = p.sc.slots.get(from).block;
        if (x.src < 0 || y.src < 0) return false;
        ov.sig = signature(main);
        ov.addMove(p.day, x.src, partOf(p, index), origTitle(main, p, index), y.src, partOf(p, from), origTitle(main, p, from), mins);
        return true;
    }

    /**
     * The slot at {@code index} of {@code p} ends after {@code keep} minutes and a new activity of {@code len} minutes begins, in front of
     * the slot right after it ({@code next}). Returns false when it cannot be recorded.
     */
    public static boolean startActivity(Routine main, Overrides ov, DayPlan p, int index, int next, int keep, String name, int kind,
                                        String sub, int len) {
        if (index < 0 || next != index + 1 || next >= p.sc.slots.size()) return false;
        Routine.Block x = p.sc.slots.get(index).block;
        Routine.Block y = p.sc.slots.get(next).block;
        if (x.src < 0 || y.src < 0) return false;
        ov.sig = signature(main);
        ov.addFresh(p.day, x.src, partOf(p, index), origTitle(main, p, index), keep, y.src, partOf(p, next), origTitle(main, p, next), name, kind, sub, len);
        return true;
    }

    /** Renames only the first {@code mins} minutes of a slot for this day; the rest of the slot goes back to the saved plan. */
    public static void editPart(Routine main, Overrides ov, LocalDate day, int src, String newName, int mins) {
        editPart(main, ov, day, src, newName, mins, -1, "");
    }

    /** The same with a type and sub type for the changed part ({@code mins} 0 changes the whole slot). */
    public static void editPart(Routine main, Overrides ov, LocalDate day, int src, String newName, int mins, int kind, String sub) {
        editPart(main, ov, day, src, newName, mins, kind, sub, 0);
    }

    /**
     * The same, beginning {@code from} minutes after the slot's start: the minutes before it stay the slot's own (a plan changed in the
     * middle of a slot starts when it was changed). {@code mins} is then the length of the new plan; 0 means all that is left.
     */
    public static void editPart(Routine main, Overrides ov, LocalDate day, int src, String newName, int mins, int kind, String sub, int from) {
        Routine base = main.forDay(day);
        if (src < 0 || src >= base.blocks.size()) return;
        ov.sig = signature(main);
        ov.setPartial(day, src, base.blocks.get(src).title, newName, mins, kind, sub, from);
    }

    /** This slot and slot {@code other} trade places for this day. */
    public static void swapPlaces(Routine main, Overrides ov, LocalDate day, int src, int other) {
        Routine base = main.forDay(day);
        if (src < 0 || other < 0 || src >= base.blocks.size() || other >= base.blocks.size() || src == other) return;
        ov.sig = signature(main);
        ov.setSwap(day, src, base.blocks.get(src).title, other);
    }

    /** Renames a slot for this day only, or takes it off. {@code src} is the block's index in the saved routine. */
    public static void edit(Routine main, Overrides ov, LocalDate day, int src, boolean skip, String newName) {
        Routine base = main.forDay(day);
        if (src < 0 || src >= base.blocks.size()) return;
        ov.sig = signature(main);
        ov.setEdit(day, src, base.blocks.get(src).title, skip, newName);
    }

    /** Saves the choice. Normal wake-up means no change. */
    public static void commit(Routine base, Overrides ov, LocalDate day, WakeOption opt) {
        ov.sig = signature(base);
        LocalDate next = day.plusDays(1);
        if (opt == null || opt.type == OPT_NORMAL || !opt.fits) ov.setShift(next, 0, 0);
        else ov.setShift(next, opt.type == OPT_WHOLE ? Overrides.WHOLE : Overrides.SKIP, opt.shiftMin);
    }

    static String clock(long ms, ZoneId zone, boolean h24) {
        java.time.ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(zone);
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
    }
}
