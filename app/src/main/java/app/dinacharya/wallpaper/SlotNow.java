package app.dinacharya.wallpaper;

import android.content.Context;

/**
 * What part of the day it is right now (the kind of slot, its type, its title, when it ends and the day), for the pause and lock rules.
 * While a break is running the slot counts as none, so the rules of the slot are lifted until the break is over.
 */
final class SlotNow {
    int kind = -1;
    int src = -1;
    String day = "";
    String title = "";
    /** The type of the slot (its subtype name, "Other" when it has none) and when the slot ends; empty and 0 when none runs. */
    String sub = "";
    long endMs;
    /** When the run of back-to-back slots of this very type ends (the slot's own end when the next one is another type). */
    long runEndMs;
    boolean onBreak;

    /** Bumped whenever a break starts or ends, so a reader that keeps the answer for a while knows to read it again. */
    private static volatile long changedAt;

    static void changed() {
        changedAt = System.currentTimeMillis();
    }

    /** True when something changed after {@code readAt} (milliseconds). */
    static boolean changedSince(long readAt) {
        return changedAt >= readAt;
    }

    private SlotNow() {
    }

    /** The slot as the locks by activity type see it: none while a break is on, nothing is running or the slot was taken off. */
    LockRules.Slot toLock() {
        return kind < 0 || runEndMs <= 0 ? LockRules.Slot.NONE : new LockRules.Slot(kind, sub, runEndMs);
    }

    /** Nothing known yet (before the first read). */
    static SlotNow empty() {
        return new SlotNow();
    }

    static SlotNow read(Context c) {
        SlotNow n = new SlotNow();
        try {
            Routine base = Store.load(c);
            Overrides ov = Store.overrides(c, base);
            long t = System.currentTimeMillis();
            Plan.DayPlan p = Plan.resolve(base, ov, t, Store.zone());
            n.day = p.day.toString();
            if (p.cur >= 0 && p.cur < p.sc.slots.size()) {
                Routine.Block b = p.sc.slots.get(p.cur).block;
                if (!b.skipped) {
                    n.kind = b.kind;
                    n.src = b.src;
                    n.title = b.title;
                    n.sub = b.sub == null ? "" : b.sub;
                    n.endMs = p.slotEndMs(p.cur);
                    n.runEndMs = n.endMs;
                    for (int j = p.cur + 1; j < p.sc.slots.size(); j++) {
                        Routine.Block nb = p.sc.slots.get(j).block;
                        if (nb.skipped || nb.kind != b.kind || !n.sub.equalsIgnoreCase(nb.sub == null ? "" : nb.sub)
                                || p.slotStartMs(j) != n.runEndMs) break;
                        n.runEndMs = p.slotEndMs(j);
                    }
                }
            }
            if (Store.alarmState(c).onBreak(t)) {
                n.onBreak = true;
                n.kind = -1;
                n.sub = "";
                n.runEndMs = 0L;
            }
        } catch (RuntimeException e) {
            CrashLog.note(c, "slot now", e);
        }
        return n;
    }
}
