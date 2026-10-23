package app.dinacharya.wallpaper;

import java.time.ZoneId;
import java.util.Set;

/**
 * One look of the strong lock at the app in front: whether to put a cover over it, or take a cover down, and how long to wait
 * before looking again. It holds the little memory the loop needs (which app a lock screen is being brought up for, so it
 * is not brought up twice). Free of Android classes so it can be tested on a plain JVM; {@link GuardService} does what it says.
 */
public final class GuardLoop {
    /** Things only Android can answer, read when they are needed. */
    public interface Env {
        /** The kind of slot running now, or -1. */
        int slotKind();

        /** How long the app has been used today. */
        long usedToday(String pkg);

        /** The slot going on, for the locks that hold during an activity type. Nothing when the environment does not know. */
        default LockRules.Slot lockSlot() {
            return LockRules.Slot.NONE;
        }
    }

    /** What to do after one look. */
    public static final class Out {
        /** Put a cover over {@link #pkg} and bring up the lock or pause screen for {@link #verdict}. */
        public final boolean present;
        public final String pkg;
        public final GuardRules.Verdict verdict;
        /** Take the cover down (something else is in front). */
        public final boolean dropCover;
        /** Wait this long before the next look. */
        public final long nextMs;

        Out(boolean present, String pkg, GuardRules.Verdict verdict, boolean dropCover, long nextMs) {
            this.present = present;
            this.pkg = pkg;
            this.verdict = verdict;
            this.dropCover = dropCover;
            this.nextMs = nextMs;
        }
    }

    /** How long a lock screen that is being brought up is waited for before it is looked at again. */
    public static final long PENDING_MS = 4000L;

    private String pending = "";
    private long pendingAt;

    /** The lock screen was left or closed: the app may be held again at the next look. */
    public synchronized void done() {
        pending = "";
    }

    public synchronized void clear() {
        pending = "";
        pendingAt = 0;
    }

    /**
     * @param front    the package in front now ("" when not known)
     * @param coverPkg the package a cover is up for now ("" when none)
     * @param safe     packages that are never held (phone, settings, keyboard, home apps, this app...)
     * @param own      this app's package
     */
    public synchronized Out look(LauncherConfig cfg, String front, String coverPkg, Set<String> safe, String own, int open,
                                 long now, ZoneId zone, GuardRules.Tracker tracker, Env env) {
        String f = front == null ? "" : front;
        String cover = coverPkg == null ? "" : coverPkg;
        boolean worth = GuardRules.worthWatching(cfg);
        boolean ignore = f.isEmpty() || own.equals(f) || GuardRules.isSafe(f, safe);

        if (ignore) {
            tracker.enter(f, now);
            // An empty answer is only the moment between two screens: the cover stays. A real app that is never held takes it down.
            return new Out(false, f, GuardRules.NO, !f.isEmpty() && !cover.isEmpty(), GuardRules.pollMs(worth, true, false));
        }
        boolean entered = tracker.enter(f, now);
        // Another app has come to the front: a cover for the one before is of no use.
        boolean drop = !cover.isEmpty() && !cover.equals(f);
        if (!worth) return new Out(false, f, GuardRules.NO, drop, GuardRules.pollMs(false, true, false));
        // The cover for this very app is up and holds it by itself, or its lock screen is still coming up.
        if (cover.equals(f) || (f.equals(pending) && now - pendingAt < PENDING_MS)) {
            return new Out(false, f, GuardRules.NO, false, GuardRules.pollMs(true, true, true));
        }
        if (!f.equals(pending)) pending = "";

        int kind = entered ? env.slotKind() : -1;
        long used = entered && GuardRules.needsUsage(cfg, f, open) ? env.usedToday(f) : 0;
        LockRules.Slot slot = GuardRules.anyDuring(cfg) ? env.lockSlot() : LockRules.Slot.NONE;
        GuardRules.Verdict v = GuardRules.decide(cfg, f, entered, open, kind, used, now, zone, tracker, slot);
        if (v.type == GuardRules.NONE) return new Out(false, f, v, drop, GuardRules.pollMs(true, true, false));
        pending = f;
        pendingAt = now;
        return new Out(true, f, v, false, GuardRules.pollMs(true, true, true));
    }
}
