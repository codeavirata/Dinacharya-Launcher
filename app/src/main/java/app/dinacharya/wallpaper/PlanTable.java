package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the free version has and what Pro adds, as the "Free and Pro" screen shows it: groups of rows, and for each row what Free gives
 * and what Pro gives, side by side. The numbers come from the rules themselves ({@link FocusRules}, {@link LockRules}, {@link Limits}), so
 * the screen never says something the app does not do. Uses no Android classes.
 */
public final class PlanTable {
    /** What the free column says for something Free does not have. */
    public static final String NOT_INCLUDED = "Not included";
    /** What a column says for something it has in full. */
    public static final String INCLUDED = "Included";

    private PlanTable() {
    }

    /** One row: a feature, and what each plan gives. */
    public static final class Row {
        public final String what;
        /** What Free gives; {@link #NOT_INCLUDED} when it does not have it. */
        public final String free;
        public final String pro;

        Row(String what, String free, String pro) {
            this.what = what;
            this.free = free;
            this.pro = pro;
        }

        /** True when Free has this at all. */
        public boolean inFree() {
            return !NOT_INCLUDED.equals(free);
        }

        /** True when Free and Pro give the same. */
        public boolean same() {
            return free.equals(pro);
        }
    }

    /** A group of rows that opens and closes. */
    public static final class Group {
        public final String name;
        /** One line for the closed group: what each plan has. */
        public final String summary;
        public final List<Row> rows;

        Group(String name, String summary, List<Row> rows) {
            this.name = name;
            this.summary = summary;
            this.rows = Collections.unmodifiableList(rows);
        }

        /** True when Free has every row in full. */
        public boolean allFree() {
            for (Row r : rows) if (!r.same()) return false;
            return true;
        }
    }

    private static Row both(String what) {
        return new Row(what, INCLUDED, INCLUDED);
    }

    private static Row proOnly(String what) {
        return new Row(what, NOT_INCLUDED, INCLUDED);
    }

    private static Row differ(String what, String free, String pro) {
        return new Row(what, free, pro);
    }

    private static String apps(int n) {
        return n + (n == 1 ? " app" : " apps");
    }

    private static String locks(int n) {
        return n + (n == 1 ? " lock" : " locks");
    }

    /** The groups, in the order the screen shows them. */
    public static List<Group> groups() {
        List<Group> out = new ArrayList<>();

        List<Row> home = new ArrayList<>();
        home.add(both("Home screen and live wallpaper of your day"));
        home.add(both("Slot alarms that ask one question"));
        home.add(both("Start-of-day alarm, finish early, breaks, library mode"));
        home.add(both("Backup to your own Google Drive"));
        out.add(new Group("Home screen and alarms", "All free", home));

        List<Row> todo = new ArrayList<>();
        todo.add(both("A to-do list for every slot"));
        todo.add(both("To-dos that repeat every day or on chosen days"));
        out.add(new Group("To-dos", "All free", todo));

        List<Row> perf = new ArrayList<>();
        perf.add(both("Today's report, with a PDF to save or share"));
        perf.add(proOnly("Every saved day"));
        perf.add(proOnly("The week view and its tips"));
        perf.add(proOnly("Slot analysis: focus and phone use, slot by slot"));
        perf.add(proOnly("Habit cards"));
        perf.add(proOnly("Phone use in slots"));
        out.add(new Group("Performance", "Free: today's report. Pro: every day and the deeper analysis", perf));

        List<Row> goals = new ArrayList<>();
        goals.add(proOnly("A goal for a slot of your routine"));
        goals.add(proOnly("A to-do list inside a goal, in step with the slot's"));
        goals.add(proOnly("Streaks for goals"));
        out.add(new Group("Goals", "Pro: a goal for a slot, with its own to-dos", goals));

        List<Row> pause = new ArrayList<>();
        pause.add(differ("Apps with a pause before they open", apps(FocusRules.FREE_APPS), "Any number"));
        pause.add(proOnly("Strict pauses"));
        pause.add(proOnly("Daily limits"));
        pause.add(proOnly("Playful pauses: a sum, a sentence, breathing, why"));
        out.add(new Group("Pauses", "Free: " + apps(FocusRules.FREE_APPS) + ". Pro: any number, strict and playful", pause));

        List<Row> lock = new ArrayList<>();
        lock.add(differ("Locks for a time", locks(LockRules.FREE_GROUPS), "As many as you like"));
        lock.add(differ("Apps in a lock", apps(LockRules.FREE_GROUP_APPS), "Groups of apps"));
        lock.add(proOnly("Strict locks that cannot be broken"));
        out.add(new Group("Locks", "Free: " + locks(LockRules.FREE_GROUPS) + ", " + apps(LockRules.FREE_GROUP_APPS) + ". Pro: groups and strict locks", lock));

        List<Row> more = new ArrayList<>();
        more.add(differ("Routines", "One routine", "Weekday, weekend and one-day routines"));
        more.add(differ("Colour looks", "The first look", "All the looks"));
        more.add(differ("App colours", "Graphite & Amber", "Graphite & Amber and White & Black"));
        more.add(differ("Ads", "Small ads", "No ads"));
        out.add(new Group("Routines and looks", "Free: one routine and one look. Pro: all of them, and no ads", more));

        return out;
    }

    /** How many rows Free has in full or in part, and how many Pro has: the two numbers the screen can show under the headings. */
    public static int[] counts() {
        int free = 0;
        int pro = 0;
        for (Group g : groups()) {
            for (Row r : g.rows) {
                pro++;
                if (r.inFree()) free++;
            }
        }
        return new int[]{free, pro};
    }
}
