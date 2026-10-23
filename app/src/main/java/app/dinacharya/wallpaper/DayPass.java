package app.dinacharya.wallpaper;

import java.time.ZoneId;

/**
 * The video pass: watching a short video opens ONE Pro item for 24 hours, and nothing else; more videos open more (see below).
 * These are only the rules (which items can be opened, whether a pass is running, how long is left); the phone keeps the
 * record in {@link Pro}.
 *
 * An item is one single thing Pro adds: the saved days of the report, the week view, pauses for more apps, strict pauses,
 * daily limits, playful pauses, a second lock, more apps in a lock, strict locks, extra routines, daily goals, or one
 * colour look. A video opens the one item it was asked for; a different item needs its own videos, after this pass ends.
 *
 * More videos in a row open more (the reward layers, see {@link #tierMask} and {@link #tierLength}): one video opens one item
 * for a day, two videos open the same item for three days, three videos open the whole group the item belongs to (all the pauses,
 * say) for a day.
 *
 * A pass is the items it opens (a mask), the moment it started and the moment it ends. It only counts while "now" is inside that
 * window, and a window longer than three days is refused (so a record changed by hand does not open anything for good). Passes run
 * side by side, one for each thing the person unlocked, each with its own video and its own end (see {@link PassLedger}); an item that
 * is open already is not opened again. Pro itself (the purchase) always opens everything: see {@link #ALL}.
 *
 * What is open at a given moment is kept as a mask (one bit per item, see {@link #bit}), which the rule classes
 * ({@link FocusRules}, {@link LockRules}) take instead of a plain "is Pro" answer.
 */
public final class DayPass {
    /** The items a video can open. {@link #NONE} means no pass. */
    public static final int NONE = 0,
            REPORT_DAYS = 1, REPORT_WEEK = 2,
            PAUSE_APPS = 3, PAUSE_STRICT = 4, PAUSE_LIMIT = 5, PAUSE_PLAYFUL = 6,
            LOCK_MORE = 7, LOCK_APPS = 8, LOCK_STRICT = 9,
            ROUTINES = 10, GOALS = 11,
            LOOK_SUNSET = 12, LOOK_OCEAN = 13, LOOK_GRAPHITE = 14;
    public static final int FIRST = REPORT_DAYS, LAST = LOOK_GRAPHITE;
    /** The groups of items, as the Pro screen lists them. */
    public static final int AREA_REPORTS = 0, AREA_PAUSES = 1, AREA_LOCKS = 2, AREA_ROUTINES = 3, AREA_GOALS = 4, AREA_LOOKS = 5;
    /** How long a pass lasts by default: one day. */
    public static final long LENGTH_MS = 24L * 3600000L;
    /** The longest a pass can last: three days (the reward for a second video). */
    public static final long MAX_MS = 3L * LENGTH_MS;
    /** The rewards, by how many videos are watched: one item for a day, the same item for three days, a whole group for a day. */
    public static final int TIER_DAY = 1, TIER_3DAYS = 2, TIER_GROUP = 3;
    /** Everything open: what Pro (the purchase) gives. */
    public static final int ALL = ((1 << (LAST + 1)) - 1) & ~1;
    /** Nothing open: the free version. */
    public static final int FREE = 0;

    private static final String[] NAME = {
            "",
            "every saved day in Performance",
            "the week view in Performance",
            "pauses for more than " + FocusRules.FREE_APPS + " apps",
            "strict pauses",
            "daily limits on pauses",
            "playful pauses",
            "more than " + LockRules.FREE_GROUPS + " lock",
            "more than " + LockRules.FREE_GROUP_APPS + " apps in a lock",
            "strict locks",
            "weekday and weekend routines",
            "daily goals",
            "the Sunset colour look",
            "the Ocean colour look",
            "the Graphite colour look",
    };
    private static final String[] TITLE = {
            "",
            "Performance: every saved day",
            "Performance: the week view",
            "Pauses: more than " + FocusRules.FREE_APPS + " apps",
            "Pauses: strict",
            "Pauses: daily limits",
            "Pauses: playful (sum, sentence, breathing, why)",
            "Locks: more than one lock",
            "Locks: more than " + LockRules.FREE_GROUP_APPS + " apps in a lock",
            "Locks: strict",
            "Routines: weekday, weekend, exam day",
            "Goals: daily goals with streaks",
            "Looks: Sunset",
            "Looks: Ocean",
            "Looks: Graphite",
    };
    private static final String[] SHORT = {
            "", "Saved days", "Week view", "More paused apps", "Strict pauses", "Daily limits", "Playful pauses",
            "More locks", "More locked apps", "Strict locks", "Routines", "Goals", "Sunset look", "Ocean look", "Graphite look",
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

    /** True when the item shares its group with at least one other item (so a whole-group reward means something more). */
    public static boolean hasGroup(int item) {
        return valid(item) && count(areaMask(area(item))) > 1;
    }

    // ------------------------------------------------------------------ the reward layers

    /** True when the reward for {@code tier} can be earned for {@code item}: the group reward only where there is a group. */
    public static boolean tierOffered(int tier, int item) {
        if (!valid(item)) return false;
        if (tier == TIER_DAY || tier == TIER_3DAYS) return true;
        return tier == TIER_GROUP && hasGroup(item);
    }

    /** How many videos earn {@code tier}. */
    public static int videosFor(int tier) {
        return tier < TIER_DAY ? 1 : Math.min(tier, TIER_GROUP);
    }

    /** What {@code tier} opens for {@code item}: the item, or every item of its group. */
    public static int tierMask(int tier, int item) {
        if (!valid(item)) return FREE;
        return tier == TIER_GROUP && hasGroup(item) ? areaMask(area(item)) : bit(item);
    }

    /** How long {@code tier} stays open. */
    public static long tierLength(int tier) {
        return tier == TIER_3DAYS ? MAX_MS : LENGTH_MS;
    }

    /**
     * The best reward for {@code item} that {@code videos} watched videos earn: one video the day, two the three days, three
     * the whole group (or, where an item has no group, still the three days).
     */
    public static int tierForVideos(int videos, int item) {
        if (videos <= 0) return 0;
        int t = Math.min(videos, TIER_GROUP);
        return tierOffered(t, item) ? t : TIER_3DAYS;
    }

    /**
     * What a tier is worth, as a button says it: "1 video: strict pauses for a day". The third video builds on the first two: the
     * item stays open for three days and the rest of its group opens for a day.
     */
    public static String tierLabel(int tier, int item) {
        if (!tierOffered(tier, item)) return "";
        int n = videosFor(tier);
        String head = n + (n == 1 ? " video: " : " videos: ");
        if (tier == TIER_GROUP) {
            return head + maskName(tierMask(tier, item)) + " (" + name(item) + " for 3 days, the rest for a day)";
        }
        return tier == TIER_3DAYS ? head + name(item) + " for 3 days" : head + name(item) + " for a day";
    }

    /**
     * What the {@code n}th video of a run opens that the earlier ones did not: the first opens the item for a day, the second
     * stretches it to three days (it opens nothing new, see {@link #stepExtends}), the third opens the rest of the item's group
     * for a day.
     */
    public static int stepMask(int n, int item) {
        if (!valid(item)) return FREE;
        if (n == TIER_DAY) return bit(item);
        if (n >= TIER_GROUP && hasGroup(item)) return areaMask(area(item)) & ~bit(item);
        return FREE;
    }

    /** True when the {@code n}th video stretches the item's pass to three days instead of opening something new. */
    public static boolean stepExtends(int n) {
        return n == TIER_3DAYS;
    }

    /** True when a day-long pass on one item is running and one more video can stretch it to three days. */
    public static boolean canExtend(int mask, long from, long until, long now) {
        return maskRunning(mask, from, until, now) && count(mask) == 1 && until - from <= LENGTH_MS;
    }

    /** When a pass that started at {@code from} ends once it is stretched to the full three days. */
    public static long extendedUntil(long from) {
        return from + MAX_MS;
    }

    /** The items a pass opens, in words: one item ("strict pauses"), a whole group ("everything in Pauses") or a few. */
    public static String maskName(int mask) {
        int m = mask & ALL;
        int n = count(m);
        if (n == 0) return "";
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
