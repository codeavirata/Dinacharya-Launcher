package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps the part of today that has already happened out of reach of the Routine tab. A slot of the running schedule day whose start time
 * has passed is part of the day as it was lived: the Routine tab may not change it, nor move it in time, whether or not an alarm rang for
 * it (alarms can be off, and a slot can have no alert). Today is changed through Change plan, continue, swap, finishing early and Day
 * off, which are recorded. Everything else in the saved routine, every slot that has not started, and every other day can be edited as
 * before, and a change that would touch a started slot can instead be saved from tomorrow ({@link #pin} keeps today exactly as it was).
 *
 * With the strict switch on, nothing of the day that is running may be changed from the Routine tab at all. A person who has only just
 * finished the first setup has a day to arrange the routine without being stopped ({@link #inGrace}). Uses no Android classes.
 */
public final class RoutineLock {
    private RoutineLock() {
    }

    /** How long after the first setup the routine may be changed freely (the strict switch still applies). */
    public static final long GRACE_MS = 24L * 60 * 60 * 1000;

    /** True while {@code nowMs} is within a day after the first setup finished at {@code setupAtMs} (0 or less: no record, so no grace). */
    public static boolean inGrace(long setupAtMs, long nowMs) {
        return setupAtMs > 0 && nowMs >= setupAtMs && nowMs - setupAtMs < GRACE_MS;
    }

    /** The saved-routine slot numbers of the running day whose start time has passed (a slot Change plan split or moved counts once any part has started). */
    public static Set<Integer> lived(Plan.DayPlan p) {
        Set<Integer> out = new TreeSet<>();
        for (Routine.Slot s : p.sc.slots) if (s.start <= p.tt && s.block.src >= 0) out.add(s.block.src);
        return out;
    }

    /**
     * Everything about the routines that decides what a day looks like: starts, modes, every slot, the other routines, the ones made for
     * one date and which one each weekday uses. Equal for two routines means no day can differ, so a save that changes only settings,
     * goals or notes never needs the day worked out.
     */
    public static String fingerprint(Routine r) {
        StringBuilder sb = new StringBuilder();
        part(sb, r);
        for (Routine o : r.others) part(sb, o);
        for (Routine t : r.temps) {
            sb.append('~').append(t.tempDate);
            part(sb, t);
        }
        for (int d : r.dayUse) sb.append(',').append(d);
        return sb.toString();
    }

    private static void part(StringBuilder sb, Routine r) {
        sb.append('#').append(r.anchor).append(':').append(r.mode);
        for (Routine.Block b : r.blocks) {
            sb.append('|').append(b.kind).append(':').append(b.mins).append(':').append(b.alert).append(':').append(b.wake ? 1 : 0)
                    .append(':').append(b.sub).append(':').append(b.title);
        }
    }

    private static boolean sameBlock(Routine.Block x, Routine.Block y) {
        return x.kind == y.kind && x.mins == y.mins && x.alert == y.alert && x.wake == y.wake
                && x.title.equals(y.title) && x.sub.equals(y.sub);
    }

    /** True when the two routines give the day the same start and the same slots (notes do not count). */
    public static boolean sameDay(Routine a, Routine b) {
        if (Routine.mod(a.anchor, 1440) != Routine.mod(b.anchor, 1440) || a.mode != b.mode || a.blocks.size() != b.blocks.size()) return false;
        for (int i = 0; i < a.blocks.size(); i++) if (!sameBlock(a.blocks.get(i), b.blocks.get(i))) return false;
        return true;
    }

    /**
     * Null when {@code after} may be saved over {@code before}. Otherwise the name of the lived slot it would change or move (or "" when
     * the strict switch is on and the day would change at all).
     */
    public static String violation(Routine before, Routine after, LocalDate day, Set<Integer> lived, boolean strict) {
        Routine b = before.forDay(day);
        Routine a = after.forDay(day);
        if (sameDay(a, b)) return null;
        if (strict) return "";
        if (lived.isEmpty()) return null;
        Routine.Schedule sb = b.compute();
        Routine.Schedule sa = a.compute();
        for (int s : lived) {
            if (s < 0 || s >= b.blocks.size()) continue;
            String name = b.blocks.get(s).title;
            if (s >= a.blocks.size() || s >= sa.slots.size() || s >= sb.slots.size()) return name;
            if (!sameBlock(b.blocks.get(s), a.blocks.get(s))) return name;
            if (sb.slots.get(s).start != sa.slots.get(s).start) return name;
        }
        return null;
    }

    /**
     * {@code edited} with {@code day} kept exactly as {@code saved} had it: a routine for that one day is made from the saved one, so the
     * edit starts tomorrow. Returns {@code edited}.
     */
    public static Routine pin(Routine saved, Routine edited, LocalDate day) {
        Routine old = saved.forDay(day);
        edited.addTemp(day, old.name + " (today)", old);
        return edited;
    }

    /**
     * The changes made to today through Change plan, as they should stand once {@code edited} is saved over {@code saved} and today's
     * schedule is kept as it was (a change that is not about the slots of {@code day} cannot be kept: slot numbers mean a place in the old
     * list). Times (later and earlier starts) are kept for every day.
     */
    public static Overrides carry(Overrides ov, Routine edited, LocalDate day) {
        Overrides o = ov == null ? new Overrides() : ov.copy();
        o.keepSlotChangesOf(day.toString());
        o.sig = Plan.signature(edited);
        return o;
    }
}
