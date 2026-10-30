package app.dinacharya.wallpaper;

import java.time.ZoneId;

/**
 * The video pass: watching three short videos opens everything in Pro for 24 hours, and then it all closes again. These are only the
 * rules (which items there are, whether a pass is running, how long is left); the phone keeps the record in {@link Pro}.
 *
 * An item is one single thing Pro adds: the saved days of the report, the week view, the deeper analysis (slot analysis, habits, phone use in
 * slots), pauses for more apps, strict pauses, daily limits, playful pauses, a second lock, more apps in a lock, strict locks, extra
 * routines, goals, or one colour look. The pass opens all of them at once; the rule classes ({@link FocusRules}, {@link LockRules}) still
 * ask for one item at a time, so each feature can be gated on its own.
 *
 * A pass is the items it opens (a mask), the moment it started and the moment it ends. It only counts while "now" is inside that
 * window, and a window longer than a day is refused (so a record changed by hand does not open anything for good). An item that is
 * open already is not opened again. Pro itself (the purchase) always opens everything: see {@link #ALL}.
 *
 * What is open at a given moment is kept as a mask (one bit per item, see {@link #bit}), which the rule classes take instead of a plain
 * "is Pro" answer.
 */
public final class DayPass {
    /** The items a video can open. {@link #NONE} means no pass. */
    public static final int NONE = 0,
            REPORT_DAYS = 1, REPORT_WEEK = 2,
            PAUSE_APPS = 3, PAUSE_STRICT = 4, PAUSE_LIMIT = 5, PAUSE_PLAYFUL = 6,
            LOCK_MORE = 7, LOCK_APPS = 8, LOCK_STRICT = 9,
            ROUTINES = 10, GOALS = 11,
            LOOK_SUNSET = 12, LOOK_OCEAN = 13, LOOK_GRAPHITE = 14,
            ANALYSIS = 15;
    public static final int FIRST = REPORT_DAYS, LAST = ANALYSIS;
    /** How many videos earn a pass. */
    public static final int VIDEOS = 3;
    /** The groups of items, as the Pro screen lists them. */
    public static final int AREA_REPORTS = 0, AREA_PAUSES = 1, AREA_LOCKS = 2, AREA_ROUTINES = 3, AREA_GOALS = 4, AREA_LOOKS = 5;
    /** How long a pass lasts: one day. */
    public static final long LENGTH_MS = 24L * 3600000L;
    /** The longest a pass can last: the same day (a longer window in the record is not trusted). */
    public static final long MAX_MS = LENGTH_MS;
    /** Everything open: what Pro (the purchase) gives. */
    public static final int ALL = ((1 << (LAST + 1)) - 1) & ~1;
    /** Nothing open: the free version. */
    public static final int FREE = 0;

    private static String apps(int n) {
        return n + (n == 1 ? " app" : " apps");
    }

    /** "1 app", "2 apps": a number of apps in words. */
    public static String appCount(int n) {
        return apps(n);
    }

    private static final String[] NAME = {
            "",
            "every saved day in Performance",
            "the week view in Performance",
            "pauses for more than " + apps(FocusRules.FREE_APPS),
            "strict pauses",
            "daily limits on pauses",
            "playful pauses",
            "more than " + LockRules.FREE_GROUPS + " lock",
            "more than " + apps(LockRules.FREE_GROUP_APPS) + " in a lock",
            "strict locks",
            "weekday and weekend routines",
            "goals",
            "the Sunset colour look",
            "the Ocean colour look",
            "the Graphite colour look",
            "the deeper analysis in Performance",
    };
    private static final String[] TITLE = {
            "",
            "Performance: every saved day",
            "Performance: the week view",
            "Pauses: more than " + apps(FocusRules.FREE_APPS),
            "Pauses: strict",
            "Pauses: daily limits",
            "Pauses: playful (sum, sentence, breathing, why)",
            "Locks: more than one lock",
            "Locks: more than " + apps(LockRules.FREE_GROUP_APPS) + " in a lock",
            "Locks: strict",
            "Routines: weekday, weekend, exam day",
            "Goals: a goal for a slot, with its own to-dos",
            "Looks: Sunset",
            "Looks: Ocean",
            "Looks: Graphite",
            "Performance: slot analysis, habits, phone use in slots",
    };
    private static final String[] SHORT = {
            "", "Saved days", "Week view", "More paused apps", "Strict pauses", "Daily limits", "Playful pauses",
            "More locks", "More locked apps", "Strict locks", "Routines", "Goals", "Sunset look", "Ocean look", "Graphite look",
            "Deeper analysis",
    };
    private static final String[] AREA_NAME = {"Performance", "Pauses", "Locks", "Routines", "Goals", "Looks"};

    private DayPass() {
    }

    public static boolean valid(int item) {
        return item >= FIRST && item <= LAST;
    }

    /** The item in a sentence ("strict pauses"). */
    public static String name(int item) {
        return valid(item) ? NAME[item] : "";
    }

    /** The item as a list entry. */
    public static String title(int item) {
        return valid(item) ? TITLE[item] : "";
    }

    /** The item in a few words ("Strict pauses"). */
    public static String shortName(int item) {
        return valid(item) ? SHORT[item] : "";
    }

    /** The group an item belongs to, as one of the AREA_ numbers, or -1 for no item. */
    public static int area(int item) {
        if (!valid(item)) return -1;
        if (item <= REPORT_WEEK) return AREA_REPORTS;
        if (item <= PAUSE_PLAYFUL) return AREA_PAUSES;
        if (item <= LOCK_STRICT) return AREA_LOCKS;
        if (item == ROUTINES) return AREA_ROUTINES;
        if (item == GOALS) return AREA_GOALS;
        if (item == ANALYSIS) return AREA_REPORTS;
        return AREA_LOOKS;
    }

    public static String areaName(int area) {
        return area >= 0 && area < AREA_NAME.length ? AREA_NAME[area] : "";
    }

    /** The item that opens colour look number {@code palette} (1 = Sunset, 2 = Ocean, 3 = Graphite); {@link #NONE} for the free look. */
    public static int lookItem(int palette) {
        return palette >= 1 && palette <= 3 ? LOOK_SUNSET + palette - 1 : NONE;
    }

    /** The colour look an item opens (1 to 3), or -1 when the item is not a look. */
    public static int lookOf(int item) {
        return item >= LOOK_SUNSET && item <= LOOK_GRAPHITE ? item - LOOK_SUNSET + 1 : -1;
    }

    // ------------------------------------------------------------------ what is open

    /** The mask bit of one item (0 for anything that is not an item). */
    public static int bit(int item) {
        return valid(item) ? 1 << item : 0;
    }

    /** True when {@code item} is open in {@code mask}. */
    public static boolean has(int mask, int item) {
        return valid(item) && (mask & bit(item)) != 0;
    }

    /** What is open for a pass on {@code item} that started at {@code from} and ends at {@code until}, at {@code now}. */
    public static int mask(int item, long from, long until, long now) {
        return running(item, from, until, now) ? bit(item) : FREE;
    }

    /** True when a pass (its item, start and end) is running at {@code now}. */
    public static boolean running(int passItem, long from, long until, long now) {
        return valid(passItem) && window(from, until, now);
    }

    private static boolean window(long from, long until, long now) {
        return until > from && until - from <= MAX_MS && now >= from && now < until;
    }

    /** True when {@code mask} holds at least one item and nothing else. */
    public static boolean validMask(int mask) {
        return mask != 0 && (mask & ~ALL) == 0;
    }

    /** What is open for a pass on the items in {@code mask}, at {@code now}: those items while it runs, nothing otherwise. */
    public static int openMask(int mask, long from, long until, long now) {
        return validMask(mask) && window(from, until, now) ? mask : FREE;
    }

    /** True when a pass on the items in {@code mask} is running at {@code now}. */
    public static boolean maskRunning(int mask, long from, long until, long now) {
        return openMask(mask, from, until, now) != FREE;
    }

    /** How many items are in the mask. */
    public static int count(int mask) {
        return Integer.bitCount(mask & ALL);
    }

    /** The lowest item in the mask, or {@link #NONE}. */
    public static int firstItem(int mask) {
        int m = mask & ALL;
        return m == 0 ? NONE : Integer.numberOfTrailingZeros(m);
    }

    /** The mask of every item in a group (an AREA_ number); 0 for an unknown group. */
    public static int areaMask(int area) {
        int m = 0;
        for (int i = FIRST; i <= LAST; i++) if (area(i) == area) m |= bit(i);
        return m;
    }

    /** The items a pass opens, in words: one item ("strict pauses"), a whole group ("everything in Pauses") or a few. */
    public static String maskName(int mask) {
        int m = mask & ALL;
        int n = count(m);
        if (n == 0) return "";
        if (m == ALL) return "everything in Pro";
        if (n == 1) return name(firstItem(m));
        int area = area(firstItem(m));
        if (m == areaMask(area)) return "everything in " + areaName(area);
        return n + " Pro features";
    }

    /** The items in {@code mask} written as a short list ("Strict pauses and Daily limits"), for a message about what a pass opened. */
    public static String shortList(int mask) {
        StringBuilder sb = new StringBuilder();
        int n = count(mask);
        int k = 0;
        for (int i = FIRST; i <= LAST; i++) {
            if (!has(mask, i)) continue;
            if (k > 0) sb.append(k == n - 1 ? " and " : ", ");
            sb.append(k == 0 ? shortName(i) : shortName(i).toLowerCase(java.util.Locale.ROOT));
            k++;
        }
        return sb.toString();
    }

    /** True when that pass is for {@code item} and is running at {@code now}. */
    public static boolean covers(int item, int passItem, long from, long until, long now) {
        return valid(item) && item == passItem && running(passItem, from, until, now);
    }

    /** When a pass started at {@code now} ends. */
    public static long endOf(long now) {
        return now + LENGTH_MS;
    }

    /** A new video may be watched only when no pass is running. */
    public static boolean canStart(int passItem, long from, long until, long now) {
        return !running(passItem, from, until, now);
    }

    /** How long is left, in words ("23h 40m"); empty when no pass is running. */
    public static String left(int passItem, long from, long until, long now) {
        return running(passItem, from, until, now) ? LockRules.left(until, now) : "";
    }

    /** One line about the running pass ("Open until tomorrow 4:30 PM"); empty when none is running. */
    public static String status(int passItem, long from, long until, long now, ZoneId zone, boolean h24) {
        if (!running(passItem, from, until, now)) return "";
        return "Open until " + LockRules.untilText(until, now, zone, h24);
    }
}
