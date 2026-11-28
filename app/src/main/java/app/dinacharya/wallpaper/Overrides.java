package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Temporary changes to the schedule. They belong to one calendar date each and are always applied
 * on top of the saved routine, never on top of each other, so a bad night cannot carry into the
 * days after it. Uses no Android classes.
 *
 * The signature ties the changes to the saved routine they were made for. When the routine is
 * edited in the app (a permanent change) the signature no longer matches and the temporary
 * changes are dropped.
 */
public final class Overrides {
    /** The whole morning moves later; bedtime stays where it was. */
    public static final int WHOLE = 1;
    /** The morning starts later and the slots covered by the extra sleep are dropped. */
    public static final int SKIP = 2;

    private static final String MAGIC = "DINAOV1";

    /** A later start for one day. */
    public static final class Shift {
        public final int type;
        public final int mins;

        Shift(int type, int mins) {
            this.type = type;
            this.mins = mins;
        }
    }

    /** A rename or a "take it off" for one slot on one day. */
    public static final class Edit {
        public final String day;
        public final int src;
        public final String orig;
        public final boolean skip;
        public final String name;
        /** 0 for the whole slot; otherwise only the first this many minutes are changed and the rest goes back to the saved plan. */
        public final int mins;
        /** When 0 or more (and no name, not skipped): this slot and slot number {@code with} trade places for the day. */
        public final int with;
        /** The type (Task, Rest...) the changed part takes, or -1 to keep the slot's own. */
        public final int kind;
        /** The sub type the changed part takes, or empty to keep the slot's own. */
        public final String sub;
        /**
         * Minutes after the slot's start at which the changed part begins (0: at the start, as it always was). The minutes before it stay
         * the slot's own, so a plan changed in the middle of a slot starts when it was changed (see {@link ReplaceFromNow}).
         */
        public final int from;
        /**
         * The order the changes of the day were made in (1, 2, 3...; 0 for one saved before changes were numbered). The day is rebuilt by
         * replaying its edits and steps in this order, so a change that was made after another one sees what the other one made.
         */
        public final int seq;

        Edit(String day, int src, String orig, boolean skip, String name) {
            this(day, src, orig, skip, name, 0, -1, -1, "");
        }

        Edit(String day, int src, String orig, boolean skip, String name, int mins, int with) {
            this(day, src, orig, skip, name, mins, with, -1, "");
        }

        Edit(String day, int src, String orig, boolean skip, String name, int mins, int with, int kind, String sub) {
            this(day, src, orig, skip, name, mins, with, kind, sub, 0);
        }

        Edit(String day, int src, String orig, boolean skip, String name, int mins, int with, int kind, String sub, int from) {
            this(day, src, orig, skip, name, mins, with, kind, sub, from, 0);
        }

        Edit(String day, int src, String orig, boolean skip, String name, int mins, int with, int kind, String sub, int from, int seq) {
            this.seq = Math.max(0, seq);
            this.from = Math.max(0, from);
            this.kind = kind;
            this.sub = sub == null ? "" : sub;
            this.day = day;
            this.src = src;
            this.orig = orig;
            this.skip = skip;
            this.name = name;
            this.mins = Math.max(0, mins);
            this.with = with;
        }
    }

    /** Time moved between slots for one day: this slot grows by {@code mins} and the other one gives them (or the other way round when negative). */
    public static final int MOVE = 0;
    /** A new activity that begins now: the slot that was going on ends here, and the activity takes the time up to the next slot's end. */
    public static final int FRESH = 1;

    /**
     * A change to the lengths of the day's slots, applied after the renames and splits, in the order it was made. Slots are named by their
     * place in the saved routine and, when a slot was split in two for the day, by which part (0 is the first).
     */
    public static final class Step {
        public final int type;
        public final String day;
        /** The slot that grows ({@link #MOVE}) or ends here ({@link #FRESH}). */
        public final int src;
        public final int ord;
        public final String orig;
        /** The slot that gives the time ({@link #MOVE}) or the one the new activity comes before ({@link #FRESH}). */
        public final int to;
        public final int toOrd;
        public final String toOrig;
        /** {@link #MOVE}: minutes the first slot grows by (negative: it shrinks). {@link #FRESH}: how long the first slot now lasts. */
        public final int mins;
        /** {@link #FRESH}: the activity (name, type, sub type) and how long it lasts. */
        public final String name;
        public final int kind;
        public final String sub;
        public final int len;
        /** The order the changes of the day were made in (see {@link Edit#seq}). */
        public final int seq;

        Step(int type, String day, int src, int ord, String orig, int to, int toOrd, String toOrig, int mins, String name, int kind, String sub, int len) {
            this(type, day, src, ord, orig, to, toOrd, toOrig, mins, name, kind, sub, len, 0);
        }

        Step(int type, String day, int src, int ord, String orig, int to, int toOrd, String toOrig, int mins, String name, int kind, String sub, int len, int seq) {
            this.seq = Math.max(0, seq);
            this.type = type;
            this.day = day;
            this.src = src;
            this.ord = Math.max(0, ord);
            this.orig = orig == null ? "" : orig;
            this.to = to;
            this.toOrd = Math.max(0, toOrd);
            this.toOrig = toOrig == null ? "" : toOrig;
            this.mins = mins;
            this.name = name == null ? "" : name;
            this.kind = kind;
            this.sub = sub == null ? "" : sub;
            this.len = len;
        }
    }

    public String sig = "";
    public final List<Step> steps = new ArrayList<>();
    public final Map<String, Shift> shifts = new TreeMap<>();
    public final List<Edit> edits = new ArrayList<>();
    /** Days that began before their time because the person woke early and started the day: the minutes earlier, by date. */
    public final Map<String, Integer> wakes = new TreeMap<>();

    /** How many minutes earlier than planned the day that starts on {@code d} was started (0 for none). */
    public int earlyFor(LocalDate d) {
        Integer m = wakes.get(d.toString());
        return m == null ? 0 : Math.max(0, m);
    }

    /** Starts the day of {@code d} that many minutes early ("Early wake"); 0 or less takes it back. */
    public void setEarly(LocalDate d, int mins) {
        if (mins <= 0) wakes.remove(d.toString());
        else wakes.put(d.toString(), Math.min(mins, 720));
    }

    public Shift shiftFor(LocalDate d) {
        Shift s = shifts.get(d.toString());
        return (s != null && s.mins > 0) ? s : null;
    }

    public void setShift(LocalDate d, int type, int mins) {
        if (mins <= 0 || (type != WHOLE && type != SKIP)) shifts.remove(d.toString());
        else shifts.put(d.toString(), new Shift(type, Math.min(mins, 720)));
    }

    /** The number the next change of the day will get: one more than the highest so far (see {@link Edit#seq}). */
    private int nextSeq() {
        int m = 0;
        for (Edit e : edits) m = Math.max(m, e.seq);
        for (Step t : steps) m = Math.max(m, t.seq);
        return m + 1;
    }

    /** True when a change of this day was made after the one numbered {@code seq}. */
    private boolean changedSince(String key, int seq) {
        for (Edit e : edits) if (e.day.equals(key) && e.seq > seq) return true;
        for (Step t : steps) if (t.day.equals(key) && t.seq > seq) return true;
        return false;
    }

    private void dropEditsOf(String key, int src, int other) {
        for (int i = edits.size() - 1; i >= 0; i--) {
            Edit e = edits.get(i);
            if (e.day.equals(key) && (e.src == src || e.src == other)) edits.remove(i);
        }
    }

    /**
     * Sets the change for the whole of one slot of one day (a name, or taking it off). It covers everything an earlier change to the same
     * slot made, so it replaces them.
     */
    public void setEdit(LocalDate d, int src, String orig, boolean skip, String name) {
        String key = d.toString();
        dropEditsOf(key, src, -1);
        edits.add(new Edit(key, src, Routine.clean(orig), skip, Routine.clean(name == null ? "" : name), 0, -1, -1, "", 0, nextSeq()));
    }

    /**
     * Takes off the part of a slot that begins {@code from} minutes after the slot's start, and everything after it: what was done before
     * stays. 0 takes off the whole slot (see {@link #setEdit}).
     */
    public void setSkipFrom(LocalDate d, int src, String orig, int from) {
        if (from <= 0) {
            setEdit(d, src, orig, true, "");
            return;
        }
        edits.add(new Edit(d.toString(), src, Routine.clean(orig), true, "", 0, -1, -1, "", from, nextSeq()));
    }

    /** Renames only the first {@code mins} minutes of a slot for one day; the rest of the slot stays as saved. */
    public void setPartial(LocalDate d, int src, String orig, String name, int mins) {
        setPartial(d, src, orig, name, mins, -1, "");
    }

    /** Renames the first {@code mins} minutes (0 for the whole slot) and gives them a type and sub type (-1 and "" keep the slot's own). */
    public void setPartial(LocalDate d, int src, String orig, String name, int mins, int kind, String sub) {
        setPartial(d, src, orig, name, mins, kind, sub, 0);
    }

    /**
     * The same, beginning {@code from} minutes after the slot's start (the minutes before stay the slot's own). A change adds to what
     * earlier changes to the slot made and takes over only the minutes it covers, so an earlier one is not lost; one that covers the
     * whole slot replaces them.
     */
    public void setPartial(LocalDate d, int src, String orig, String name, int mins, int kind, String sub, int from) {
        String key = d.toString();
        if (from <= 0 && mins <= 0) dropEditsOf(key, src, -1);
        edits.add(new Edit(key, src, Routine.clean(orig), false, Routine.clean(name == null ? "" : name), mins, -1, kind, Routine.clean(sub == null ? "" : sub), from, nextSeq()));
    }

    /** Makes this slot and slot {@code other} trade places for one day. */
    public void setSwap(LocalDate d, int src, String orig, int other) {
        String key = d.toString();
        dropEditsOf(key, src, other);
        edits.add(new Edit(key, src, Routine.clean(orig), false, "", 0, other, -1, "", 0, nextSeq()));
    }

    /**
     * Slot {@code src} (part {@code ord}) grows by {@code mins} minutes for one day and slot {@code to} (part {@code toOrd}) gives them; a
     * negative number does the opposite. A second move between the same two adds to the first.
     */
    public void addMove(LocalDate d, int src, int ord, String orig, int to, int toOrd, String toOrig, int mins) {
        if (mins == 0) return;
        String key = d.toString();
        for (int i = steps.size() - 1; i >= 0; i--) {
            Step t = steps.get(i);
            // joined only when nothing was changed since: a move that came before another change must stay before it
            if (t.type == MOVE && t.day.equals(key) && t.src == src && t.ord == ord && t.to == to && t.toOrd == toOrd && !changedSince(key, t.seq)) {
                steps.remove(i);
                mins += t.mins;
                break;
            }
        }
        if (mins != 0) steps.add(new Step(MOVE, key, src, ord, Routine.clean(orig), to, toOrd, Routine.clean(toOrig), mins, "", -1, "", 0, nextSeq()));
    }

    /**
     * The slot {@code src} (part {@code ord}) ends after {@code keep} minutes, and a new activity of {@code len} minutes begins there, in
     * front of slot {@code before}. The slot after the activity keeps its own end.
     */
    public void addFresh(LocalDate d, int src, int ord, String orig, int keep, int before, int beforeOrd, String beforeOrig, String name,
                         int kind, String sub, int len) {
        steps.add(new Step(FRESH, d.toString(), src, ord, Routine.clean(orig), before, beforeOrd, Routine.clean(beforeOrig), keep,
                Routine.clean(name == null ? "" : name), kind, Routine.clean(sub == null ? "" : sub), len, nextSeq()));
    }

    public boolean isEmpty() {
        return shifts.isEmpty() && edits.isEmpty() && wakes.isEmpty() && steps.isEmpty();
    }

    public int editCount(LocalDate d) {
        int n = 0;
        for (Edit e : edits) if (e.day.equals(d.toString())) n++;
        for (Step t : steps) if (t.day.equals(d.toString())) n++;
        return n;
    }

    /** Forgets everything for one day's slots (the wake-up shift stays). */
    public void clearEdits(LocalDate d) {
        for (int i = edits.size() - 1; i >= 0; i--) {
            if (edits.get(i).day.equals(d.toString())) edits.remove(i);
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).day.equals(d.toString())) steps.remove(i);
        }
    }

    /** Drops changes for days that are over. */
    public void prune(LocalDate today) {
        String oldest = today.minusDays(3).toString();
        shifts.keySet().removeIf(k -> k.compareTo(oldest) < 0);
        wakes.keySet().removeIf(k -> k.compareTo(oldest) < 0);
        edits.removeIf(e -> e.day.compareTo(oldest) < 0);
        steps.removeIf(t -> t.day.compareTo(oldest) < 0);
    }

    /**
     * Keeps the changes made to the slots of one day and forgets those of every other day. A slot change names a place in the saved
     * routine, so it only means something for the day it was made on; later and earlier starts and early wake-ups are times and stay.
     * Used when a routine is saved so that today stays as it was and the new one starts tomorrow.
     */
    public void keepSlotChangesOf(String day) {
        edits.removeIf(e -> !e.day.equals(day));
        steps.removeIf(t -> !t.day.equals(day));
    }

    public Overrides copy() {
        Overrides o = new Overrides();
        o.sig = sig;
        for (Map.Entry<String, Shift> e : shifts.entrySet()) o.shifts.put(e.getKey(), e.getValue());
        o.edits.addAll(edits);
        o.steps.addAll(steps);
        o.wakes.putAll(wakes);
        return o;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        sb.append("sig=").append(sig).append('\n');
        for (Map.Entry<String, Shift> e : shifts.entrySet()) {
            sb.append("S\t").append(e.getKey()).append('\t').append(e.getValue().type).append('\t')
                    .append(e.getValue().mins).append('\n');
        }
        for (Map.Entry<String, Integer> e : wakes.entrySet()) {
            sb.append("W\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        for (Edit e : edits) {
            sb.append("E\t").append(e.day).append('\t').append(e.src).append('\t').append(e.skip ? 1 : 0).append('\t')
                    .append(e.orig).append('\t').append(e.name).append('\t').append(e.mins).append('\t').append(e.with).append('\t').append(e.kind).append('\t').append(e.sub);
            if (e.from > 0 || e.seq > 0) sb.append('\t').append(e.from);   // only when there is one, so an older app still reads every other line as before
            if (e.seq > 0) sb.append('\t').append(e.seq);
            sb.append('\n');
        }
        for (Step t : steps) {
            sb.append(t.type == FRESH ? "N\t" : "M\t").append(t.day).append('\t').append(t.src).append('\t').append(t.ord).append('\t')
                    .append(t.orig).append('\t').append(t.to).append('\t').append(t.toOrd).append('\t').append(t.toOrig).append('\t')
                    .append(t.mins).append('\t').append(t.name).append('\t').append(t.kind).append('\t').append(t.sub).append('\t')
                    .append(t.len);
            if (t.seq > 0) sb.append('\t').append(t.seq);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Reads text made by {@link #serialize()}. Anything unreadable gives an empty set of changes. */
    public static Overrides parse(String text) {
        Overrides o = new Overrides();
        if (text == null) return o;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return o;
        for (int i = 1; i < lines.length; i++) {
            String ln = lines[i];
            try {
                if (ln.startsWith("sig=")) {
                    o.sig = ln.substring(4).trim();
                } else if (ln.startsWith("S\t")) {
                    String[] p = ln.split("\t", 4);
                    LocalDate.parse(p[1]);
                    o.shifts.put(p[1], new Shift(Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())));
                } else if (ln.startsWith("W\t")) {
                    String[] p = ln.split("\t", 3);
                    LocalDate.parse(p[1]);
                    int m = Integer.parseInt(p[2].trim());
                    if (m > 0) o.wakes.put(p[1], Math.min(m, 720));
                } else if (ln.startsWith("M\t") || ln.startsWith("N\t")) {
                    String[] p = ln.split("\t", 14);
                    LocalDate.parse(p[1]);
                    int ps = 0;
                    try {
                        if (p.length > 13) ps = Integer.parseInt(p[13].trim());
                    } catch (NumberFormatException ignored) {
                    }
                    o.steps.add(new Step(ln.charAt(0) == 'N' ? FRESH : MOVE, p[1], Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()),
                            p[4], Integer.parseInt(p[5].trim()), Integer.parseInt(p[6].trim()), p[7], Integer.parseInt(p[8].trim()),
                            p[9], Integer.parseInt(p[10].trim()), p[11], p.length > 12 ? Integer.parseInt(p[12].trim()) : 0, ps));
                } else if (ln.startsWith("E\t")) {
                    String[] p = ln.split("\t", 12);
                    LocalDate.parse(p[1]);
                    int pm = 0;
                    int pw = -1;
                    int pk = -1;
                    int pf = 0;
                    int pq = 0;
                    try {
                        if (p.length > 6) pm = Integer.parseInt(p[6].trim());
                        if (p.length > 7) pw = Integer.parseInt(p[7].trim());
                        if (p.length > 8) pk = Integer.parseInt(p[8].trim());
                        if (p.length > 10) pf = Integer.parseInt(p[10].trim());
                        if (p.length > 11) pq = Integer.parseInt(p[11].trim());
                    } catch (NumberFormatException ignored) {
                    }
                    o.edits.add(new Edit(p[1], Integer.parseInt(p[2].trim()), p[4], p[3].trim().equals("1"),
                            p.length > 5 ? p[5] : "", pm, pw, pk, p.length > 9 ? p[9] : "", pf, pq));
                }
            } catch (RuntimeException ignored) {
                // skip a damaged line
            }
        }
        return o;
    }
}
