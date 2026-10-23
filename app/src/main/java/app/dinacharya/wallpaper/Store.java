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

    /** What the poster shows besides the routine: the to-do list of the slot going on and a note while a break is on. */
    static PosterExtras posterExtras(Context c) {
        try {
            return new PosterExtras(todos(c), alarmState(c));
        } catch (RuntimeException e) {
            return PosterExtras.NONE;
        }
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
