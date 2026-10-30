package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hours per activity type and progress towards daily goals, from day reports. Uses no Android classes.
 *
 * A slot counts as done when you kept it ("continue as planned"), or when it had no alarm to ask you
 * (the app cannot know, so it trusts the plan). Renamed, taken-off and unanswered slots do not count.
 * A slot still running counts only the part that has passed.
 */
public final class TypeStats {
    private TypeStats() {
    }

    public static final class Line {
        public final int kind;
        public final String sub;
        /** The slot this line is the goal of, or empty for a line about an activity type. */
        public String slot = "";
        public int plannedMin;
        public int doneMin;
        /** The daily goal in minutes, or 0 when there is none. */
        public int goalMin;
        /** The slot time so far (breaks and put-off time left out) and how much of it had an app in use. */
        public long workedMs;
        public long phoneMs;
        /** Of {@link #phoneMs}, the time in apps chosen as part of the activity. */
        public long partMs;
        /** The longest stretch with no other phone use in one slot of this type; -1 when no slot of it has that known. */
        public long longestFocusMs = -1;
        /** How many slots of this type have run (taken-off ones left out). */
        public int slots;
        /** Time in each app while these slots were going on, longest first. */
        public final Map<String, Long> apps = new LinkedHashMap<>();

        /** Slot time with no app in use. */
        public long noAppMs() {
            return Math.max(0, workedMs - phoneMs);
        }

        /** Phone use that was not part of the activity. */
        public long offMs() {
            return Math.max(0, phoneMs - partMs);
        }

        /** The time that went to the activity itself: slot time without the other phone use. */
        public long devotedMs() {
            return Math.max(0, workedMs - offMs());
        }

        /** 0..100: the share of the slots' time with no other phone use; -1 when no slot has run. */
        public int focusPct() {
            if (workedMs <= 0) return -1;
            return (int) Math.max(0, Math.min(100, Math.round(100.0 * (workedMs - Math.min(workedMs, offMs())) / workedMs)));
        }

        Line(int kind, String sub) {
            this.kind = kind;
            this.sub = sub;
        }

        /** The name of the slot of a slot's goal, or of the type; "Other" says which kind it is the other of ("Other task"), since every kind has one. */
        public String label() {
            if (!slot.isEmpty()) return slot;
            if (sub.isEmpty()) return Routine.KIND_NAMES[kind];
            return Routine.isOther(sub) ? "Other " + Routine.KIND_NAMES[kind].toLowerCase(java.util.Locale.ROOT) : sub;
        }

        public boolean hasGoal() {
            return goalMin > 0;
        }

        public boolean met() {
            return goalMin > 0 && doneMin >= goalMin;
        }

        /** 0..100 towards the goal (or towards the plan when there is no goal). */
        public int pct() {
            int target = goalMin > 0 ? goalMin : plannedMin;
            if (target <= 0) return 0;
            return Math.min(100, Math.round(100f * doneMin / target));
        }
    }

    static String key(int kind, String sub) {
        return kind + ":" + Routine.cleanSub(sub).toLowerCase(Locale.ROOT);
    }

    /** Tells the lines apart: a type, or a slot's goal. */
    public static String lineKey(Line l) {
        return l.slot.isEmpty() ? key(l.kind, l.sub) : l.kind + "#" + l.slot.toLowerCase(Locale.ROOT);
    }

    /** The key of the line a goal makes. */
    public static String goalKey(Routine.Goal g) {
        return g.ident();
    }

    /** True when the row is a slot the goal counts: of its slot's kind and name, or (an older goal) of its type. */
    static boolean covers(Routine.Goal g, DayReport.Row row) {
        if (row.kind == Routine.SLEEP) return false;
        if (g.bound()) return row.kind == g.kind && Routine.clean(row.title).trim().equalsIgnoreCase(g.slot);
        return key(row.kind, row.sub).equals(key(g.kind, g.sub));
    }

    /** Minutes of this row that count as done (see the class note). */
    public static int doneMinutes(DayReport.Row row) {
        if (row.status != DayReport.KEPT && row.status != DayReport.UNTRACKED) return 0;
        if (row.kind == Routine.SLEEP) return 0;
        int passed = (int) Math.max(0, row.activeMs / 60000L);
        // a slot that was carried into the next one has that time as well; the next slot's own time was already cut by it
        return Math.min(row.plannedMin, passed) + (int) Math.max(0, row.extMs / 60000L);
    }

    /** One line per goal (in goal order), then one per other activity type used that day, longest first. */
    public static List<Line> of(DayReport r) {
        Map<String, Line> map = new LinkedHashMap<>();
        for (Routine.Goal g : r.goals) {
            Line l = new Line(g.kind, g.sub);
            l.slot = g.slot;
            l.goalMin = g.mins;
            map.put(g.ident(), l);
        }
        List<Line> extra = new ArrayList<>();
        for (DayReport.Row row : r.rows) {
            if (row.kind == Routine.SLEEP) continue;
            // the goals of slots: this row counts for the one whose slot it is
            for (Line sl : map.values()) {
                if (sl.slot.isEmpty() || sl.kind != row.kind || !Routine.clean(row.title).trim().equalsIgnoreCase(sl.slot)) continue;
                if (row.status != DayReport.SKIPPED) sl.plannedMin += row.plannedMin;
                sl.doneMin += doneMinutes(row);
                addTime(sl, row);
            }
            if (row.sub == null || row.sub.isEmpty()) continue;
            String k = key(row.kind, row.sub);
            Line l = map.get(k);
            if (l == null || !l.slot.isEmpty()) {
                l = new Line(row.kind, row.sub);
                map.put(k, l);
                extra.add(l);
            }
            if (row.status != DayReport.SKIPPED) l.plannedMin += row.plannedMin;
            l.doneMin += doneMinutes(row);
            addTime(l, row);
        }
        List<Line> out = new ArrayList<>();
        for (Line l : map.values()) if (l.hasGoal()) out.add(l);
        extra.sort((x, y) -> Integer.compare(y.plannedMin, x.plannedMin));
        out.addAll(extra);
        return out;
    }

    /** Adds a slot's own time, phone time and per-app time to a line. */
    private static void addTime(Line l, DayReport.Row row) {
        if (row.status == DayReport.SKIPPED) return;
        l.workedMs += row.activeMs;
        l.phoneMs += row.phoneMs;
        l.partMs += row.partMs;
        if (row.activeMs > 0) l.slots++;
        if (row.longestFocusMs > l.longestFocusMs) l.longestFocusMs = row.longestFocusMs;
        for (DayReport.App a : row.apps) {
            Long prev = l.apps.get(a.name);
            l.apps.put(a.name, (prev == null ? 0L : prev) + a.ms);
        }
    }

    /** The apps of a line, longest first. Each entry is {name, ms}. */
    public static List<Object[]> topApps(Line l) {
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : l.apps.entrySet()) if (e.getValue() > 0) out.add(new Object[]{e.getKey(), e.getValue()});
        out.sort((x, y) -> Long.compare((Long) y[1], (Long) x[1]));
        return out;
    }

    /** The line for one type, or null (the goals of slots are not about a type). */
    public static Line find(List<Line> lines, int kind, String sub) {
        String k = key(kind, sub);
        for (Line l : lines) if (l.slot.isEmpty() && key(l.kind, l.sub).equals(k)) return l;
        return null;
    }

    /** The line a goal makes, or null. */
    public static Line findGoal(List<Line> lines, Routine.Goal g) {
        String k = g.ident();
        for (Line l : lines) if (l.hasGoal() && lineKey(l).equals(k)) return l;
        return null;
    }

    /**
     * How many days in a row the goal was met, counting back from the newest day. Today (when given
     * and not finished) only adds to the streak once met, and never breaks it. A day whose routine
     * has no slot of that type neither adds nor breaks.
     *
     * @param today      today's report so far, or null
     * @param newestFirst finished days, newest first
     */
    public static int streak(Routine.Goal g, DayReport today, List<DayReport> newestFirst) {
        if (!g.repeats()) return 0;
        int n = 0;
        if (today != null) {
            int target = targetOn(g, today);
            Line t = target < 0 ? null : lineFor(today, g);
            if (t != null && t.doneMin >= target) n++;
        }
        for (DayReport d : newestFirst) {
            if (today != null && d.day.equals(today.day)) continue;
            int target = targetOn(g, d);
            if (target < 0) continue;   // the goal did not count that day: it neither adds nor breaks
            Line l = lineFor(d, g);
            if (l == null || l.plannedMin == 0) continue;
            if (l.doneMin >= target) n++;
            else break;
        }
        return n;
    }

    /**
     * The minutes the goal asked for on that day, or -1 when it did not count: the report lists the goals of its day, so that is
     * where to look; a report that lists none for this type counts when the goal's rule says so.
     */
    private static int targetOn(Routine.Goal g, DayReport d) {
        String k = g.ident();
        for (Routine.Goal x : d.goals) if (x.ident().equals(k)) return x.mins;
        try {
            return g.ruleMatches(java.time.LocalDate.parse(d.day)) ? g.mins : -1;
        } catch (RuntimeException e) {
            return g.mins;
        }
    }

    private static Line lineFor(DayReport d, Routine.Goal g) {
        int planned = 0;
        int done = 0;
        for (DayReport.Row row : d.rows) {
            if (!covers(g, row)) continue;
            if (row.status != DayReport.SKIPPED) planned += row.plannedMin;
            done += doneMinutes(row);
        }
        Line l = new Line(g.kind, g.sub);
        l.slot = g.slot;
        l.plannedMin = planned;
        l.doneMin = done;
        l.goalMin = g.mins;
        return l;
    }

    /** Average minutes done per day for each type over the given days (types in order of first use). */
    public static List<Line> average(List<DayReport> days) {
        Map<String, Line> map = new LinkedHashMap<>();
        if (days.isEmpty()) return new ArrayList<>();
        for (DayReport d : days) {
            for (Line l : of(d)) {
                String k = lineKey(l);
                Line a = map.get(k);
                if (a == null) {
                    a = new Line(l.kind, l.sub);
                    a.slot = l.slot;
                    map.put(k, a);
                }
                a.plannedMin += l.plannedMin;
                a.doneMin += l.doneMin;
                a.goalMin = Math.max(a.goalMin, l.goalMin);
            }
        }
        List<Line> out = new ArrayList<>(map.values());
        for (Line a : out) {
            a.plannedMin = Math.round(a.plannedMin / (float) days.size());
            a.doneMin = Math.round(a.doneMin / (float) days.size());
        }
        return out;
    }
}
