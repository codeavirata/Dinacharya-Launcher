package app.dinacharya.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;

/** Keeps the routine, today's temporary changes and the history in private app storage. */
final class Store {
    private static final String PREFS = "dinacharya";
    private static final String KEY = "routine";
    private static final String KEY_OVERRIDES = "overrides";
    private static final String KEY_ALARM = "alarmstate";
    private static final String KEY_SINCE = "since";
    private static final String KEY_TODOS = "todos";
    private static final String KEY_LIBRARY = "library";

    private Store() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Routine load(Context c) {
        String s = prefs(c).getString(KEY, null);
        Routine r = (s == null) ? null : Routine.parse(s);
        return (r != null) ? r : Routine.sample();
    }

    static void save(Context c, Routine r) {
        prefs(c).edit().putString(KEY, r.serialize()).apply();
    }

    static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    /** Today's temporary changes, or an empty set when the routine was edited since they were made. */
    static Overrides overrides(Context c, Routine base) {
        Overrides o = Plan.valid(base, Overrides.parse(prefs(c).getString(KEY_OVERRIDES, null)));
        o.prune(LocalDate.now(zone()));
        return o;
    }

    static void saveOverrides(Context c, Routine base, Overrides o) {
        o.sig = Plan.signature(base);
        prefs(c).edit().putString(KEY_OVERRIDES, o.serialize()).apply();
    }

    static AlarmState alarmState(Context c) {
        return AlarmState.parse(prefs(c).getString(KEY_ALARM, null));
    }

    static void saveAlarmState(Context c, AlarmState s) {
        prefs(c).edit().putString(KEY_ALARM, s.serialize()).apply();
    }

    /** The to-do lists of today (and yesterday, while that day is still running). */
    static Todos todos(Context c) {
        Todos t = Todos.parse(prefs(c).getString(KEY_TODOS, null));
        t.prune(LocalDate.now(zone()));
        return t;
    }

    /**
     * The lists, with the items that repeat on {@code day} put in (and saved when that added any). {@code blocks} are that day's slots, or
     * null when only a goal's list is wanted.
     */
    static Todos todosFor(Context c, java.time.LocalDate day, java.util.List<Routine.Block> blocks) {
        Todos t = todos(c);
        try {
            if (t.materialize(day, blocks)) saveTodos(c, t);
        } catch (RuntimeException e) {
            CrashLog.note(c, "repeating to-dos", e);
        }
        return t;
    }

    static void saveTodos(Context c, Todos t) {
        prefs(c).edit().putString(KEY_TODOS, t.serialize()).apply();
    }

    /** What the poster shows besides the routine: the day as it is going, the to-do list of the slot going on and the library pill. */
    static PosterExtras posterExtras(Context c) {
        return posterExtras(c, null);
    }

    static PosterExtras posterExtras(Context c, Routine main) {
        try {
            Routine r = main != null ? main : load(c);
            // how the slots went as far as phone use goes: only when the person turned that on
            SlotUse.Snapshot use = SlotUseCache.on(c, LauncherStore.load(c)) ? SlotUseCache.snapshot() : null;
            return new PosterExtras(todos(c), alarmState(c), history(c).events(), r, library(c), use);
        } catch (RuntimeException e) {
            return PosterExtras.NONE;
        }
    }

    /** Library mode: when it is on, every alarm only vibrates. */
    static LibraryMode library(Context c) {
        return LibraryMode.parse(prefs(c).getString(KEY_LIBRARY, null));
    }

    static void saveLibrary(Context c, LibraryMode m) {
        prefs(c).edit().putString(KEY_LIBRARY, m.serialize()).apply();
    }

    /**
     * Today's plan as it is going: {@code p} (the saved plan with today's changes) with a start put off, breaks, the slot before carried on,
     * a slot finished early and a day off applied. The same object comes back when nothing changed. Anything that asks "which slot is
     * going on now" (the Home card, the locks, a break, finishing early) must read this one; the alarms keep reading the saved plan.
     */
    static Plan.DayPlan livePlan(Context c, Routine base, Plan.DayPlan p, long nowMs) {
        try {
            AlarmState st = alarmState(c);
            return LivePlan.apply(p, history(c).events(), st, DayOff.active(base, st, p.day), nowMs);
        } catch (RuntimeException e) {
            CrashLog.note(c, "live plan", e);
            return p;
        }
    }

    /** The plan going on now, as it is going (see {@link #livePlan}). */
    static Plan.DayPlan livePlanNow(Context c, Routine base, long nowMs) {
        Plan.DayPlan p = Plan.resolve(base, overrides(c, base), nowMs, zone());
        return livePlan(c, base, p, nowMs);
    }

    static HistoryStore history(Context c) {
        return new HistoryStore(new File(c.getApplicationContext().getFilesDir(), "history"));
    }

    /** When the app was first used. Reports are only made for days after this. */
    static long since(Context c) {
        SharedPreferences p = prefs(c);
        long t = p.getLong(KEY_SINCE, 0);
        if (t == 0) {
            t = System.currentTimeMillis();
            p.edit().putLong(KEY_SINCE, t).apply();
        }
        return t;
    }
}
