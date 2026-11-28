package app.dinacharya.wallpaper;

import android.content.Context;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Android side of {@link RoutineLock}: reads which slots of today have started and the saved routine, decides whether a save from
 * the dashboard may go through, and keeps today's Change plan edits and skip marks when the routine is saved from tomorrow.
 */
final class RoutineGuard {
    private RoutineGuard() {
    }

    private static final String KEY_STRICT = "routine_strict";

    /** What to do with a routine about to be saved. */
    static final class Verdict {
        /** The save must not happen. */
        final boolean refused;
        /** The started slot the edit would change, or "" when the strict switch is the reason. */
        final String slot;
        /** The running schedule day. Null when nothing needed checking. */
        final LocalDate day;
        /** A refused edit can be saved from tomorrow instead. */
        final boolean canPin;
        /** Today's Change plan edits, for the new routine, when they are to be kept. Null when nothing is to be kept or none exist. */
        final Overrides keep;
        /** The edit changes the slots of the running day (it is allowed because nothing of the day was lived, or the strict switch is off). */
        final boolean editsToday;

        Verdict(boolean refused, String slot, LocalDate day, boolean canPin, Overrides keep, boolean editsToday) {
            this.refused = refused;
            this.slot = slot == null ? "" : slot;
            this.day = day;
            this.canPin = canPin;
            this.keep = keep;
            this.editsToday = editsToday;
        }

        static final Verdict ALLOW = new Verdict(false, "", null, false, null, false);
    }

    static boolean strict(Context c) {
        try {
            return Store.prefs(c).getBoolean(KEY_STRICT, false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void setStrict(Context c, boolean on) {
        Store.prefs(c).edit().putBoolean(KEY_STRICT, on).apply();
    }

    private static final String KEY_SETUP_AT = "setup_done_at";

    /** When the first setup finished (0 when not known, as for a phone that finished it in an older build). */
    static long setupAt(Context c) {
        try {
            return Store.prefs(c).getLong(KEY_SETUP_AT, 0L);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /** Called when the first setup is finished. Only the first time is kept, so replaying the setup later gives no new day of grace. */
    static void setupFinished(Context c) {
        try {
            android.content.SharedPreferences p = Store.prefs(c);
            if (!p.contains(KEY_SETUP_AT)) p.edit().putLong(KEY_SETUP_AT, System.currentTimeMillis()).apply();
        } catch (RuntimeException e) {
            CrashLog.note(c, "setup time", e);
        }
    }

    /** Whether {@code edited} may be saved over {@code saved} now. Never throws: when anything goes wrong the save is allowed. */
    static Verdict check(Context c, Routine saved, Routine edited) {
        try {
            if (RoutineLock.fingerprint(saved).equals(RoutineLock.fingerprint(edited))) return Verdict.ALLOW;
            Overrides ov = Store.overrides(c, saved);
            Plan.DayPlan p = Plan.resolve(saved, ov, System.currentTimeMillis(), Store.zone());
            // Just after the first setup the routine is still being arranged: only the strict switch can stop an edit then.
            Set<Integer> lived = RoutineLock.inGrace(setupAt(c), System.currentTimeMillis()) ? new TreeSet<Integer>() : RoutineLock.lived(p);
            String v = RoutineLock.violation(saved, edited, p.day, lived, strict(c));
            boolean canPin = saved.tempFor(p.day) == null;
            if (v != null) return new Verdict(true, v, p.day, canPin, null, false);
            boolean sameToday = RoutineLock.sameDay(saved.forDay(p.day), edited.forDay(p.day));
            // The edit leaves today as it is, but the saved routine's signature changes with it, which would drop today's Change plan edits.
            Overrides keep = sameToday && !Plan.signature(saved).equals(Plan.signature(edited))
                    ? RoutineLock.carry(ov, edited, p.day) : null;
            return new Verdict(false, "", p.day, canPin, keep, !sameToday);
        } catch (RuntimeException e) {
            CrashLog.note(c, "routine lock", e);
            return Verdict.ALLOW;
        }
    }

    /**
     * Today's schedule is kept as it was and {@code edited} starts tomorrow: a one-day routine is made for the running day from the saved
     * one, and today's Change plan edits and skip marks are kept for it. Call before saving; the caller saves the routine, then
     * {@link #afterSave}.
     */
    static Overrides pin(Context c, Routine saved, Routine edited, LocalDate day) {
        Overrides ov = Store.overrides(c, saved);
        RoutineLock.pin(saved, edited, day);
        return RoutineLock.carry(ov, edited, day);
    }

    /**
     * After the routine was saved and before the day is looked at again: today's Change plan edits (when they are to be kept) go with
     * the new routine, and the marks of slots started early follow the schedule they were made on.
     */
    static void afterSave(Context c, Routine saved, Routine now, Overrides keep) {
        try {
            if (keep == null) return;
            Store.saveOverrides(c, now, keep);
            AlarmState st = Store.alarmState(c);
            if (st.skipSig.equals(Plan.scheduleSignature(saved))) {
                st.skipSig = Plan.scheduleSignature(now);
                Store.saveAlarmState(c, st);
            }
        } catch (RuntimeException e) {
            CrashLog.note(c, "routine lock keep", e);
        }
    }

    /** Notes in the history that the routine was changed while the schedule day {@code day} was running. */
    static void logEdit(Context c, LocalDate day) {
        try {
            Store.history(c).append(Ev.planEdited(System.currentTimeMillis(), day.toString()));
        } catch (RuntimeException e) {
            CrashLog.note(c, "plan edit note", e);
        }
    }
}
