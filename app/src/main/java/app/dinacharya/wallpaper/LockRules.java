package app.dinacharya.wallpaper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * When a group of apps is locked. A group is locked while a hand-started lock ("Lock now") has not run out, and while the
 * daily window of one of its chosen days is open (a window can run past midnight), and while a slot of one of its chosen activity
 * types is going on (not during a break). The free version keeps the first group; Pro keeps any number and adds strict locks.
 * Uses no Android classes.
 */
public final class LockRules {
    /** Free: one lock holding one app. Pro: groups (any number of locks, many apps each) and strict locks. */
    public static final int FREE_GROUPS = 1, FREE_GROUP_APPS = 1;
    public static final int EVERY_DAY = 127, WEEKDAYS = 31, WEEKENDS = 96;

    /**
     * The slot going on, for the locks that hold during an activity type. {@link #NONE} when no slot runs, the slot was taken off
     * or a break is on. {@code endMs} is when the run of slots of this very type ends (back-to-back slots of one type count as one).
     */
    public static final class Slot {
        public static final Slot NONE = new Slot(-1, "", 0L);
        public final int kind;
        public final String sub;
        public final long endMs;
        /** When library mode ends (0 when it is off). An open-ended session reports a moment a day ahead and {@link #libraryOpen}. */
        public final long libraryEndMs;
        /** Library mode has no end: it lasts until it is switched off. */
        public final boolean libraryOpen;

        public Slot(int kind, String sub, long endMs) {
            this(kind, sub, endMs, 0L, false);
        }

        public Slot(int kind, String sub, long endMs, long libraryEndMs, boolean libraryOpen) {
            this.kind = kind;
            this.sub = sub == null ? "" : sub;
            this.endMs = endMs;
            this.libraryEndMs = Math.max(0L, libraryEndMs);
            this.libraryOpen = libraryOpen && libraryEndMs > 0;
        }

        /** The same slot, with library mode on until {@code endMs} (0 for off). */
        public Slot withLibrary(long endMs, boolean open) {
            return new Slot(kind, sub, this.endMs, endMs, open);
        }
    }

    /** An app that is locked right now, and by what. */
    public static final class Hit {
        public final LauncherConfig.Group group;
        /** When the lock ends (milliseconds since 1970). */
        public final long untilMs;
        /** True when there is no way past it (strict, and Pro). */
        public final boolean strict;
        /** The name of the activity type that holds the lock now ("Study"), or empty when it is held by time. */
        public final String during;
        /** True when library mode is what holds the lock; {@link #untilOpen} when that has no end. */
        public final boolean library;
        public final boolean untilOpen;

        Hit(LauncherConfig.Group group, long untilMs, boolean strict) {
            this(group, untilMs, strict, "");
        }

        Hit(LauncherConfig.Group group, long untilMs, boolean strict, String during) {
            this(group, untilMs, strict, during, false, false);
        }

        Hit(LauncherConfig.Group group, long untilMs, boolean strict, String during, boolean library, boolean untilOpen) {
            this.group = group;
            this.untilMs = untilMs;
            this.strict = strict;
            this.during = during == null ? "" : during;
            this.library = library;
            this.untilOpen = untilOpen;
        }
    }

    private LockRules() {
    }

    /** Whether this group's lock is in force (in the free version only the first group's is). */
    public static boolean active(LauncherConfig c, LauncherConfig.Group g, boolean pro) {
        return active(c, g, pro ? DayPass.ALL : DayPass.FREE);
    }

    /**
     * Same, for what is open ({@link DayPass#ALL} with Pro, or just the one item a video pass opened): only
     * {@link DayPass#LOCK_MORE} lifts the limit of one lock.
     */
    public static boolean active(LauncherConfig c, LauncherConfig.Group g, int open) {
        int i = c.groups.indexOf(g);
        return i >= 0 && (DayPass.has(open, DayPass.LOCK_MORE) || i < FREE_GROUPS);
    }

    /** Whether this app is held by this group's lock for this person (in the free version only the first app is). */
    public static boolean holds(LauncherConfig c, LauncherConfig.Group g, String appKey, boolean pro) {
        return holds(c, g, appKey, pro ? DayPass.ALL : DayPass.FREE);
    }

    /** Same, for what is open: only {@link DayPass#LOCK_APPS} lifts the limit of one app in a lock. */
    public static boolean holds(LauncherConfig c, LauncherConfig.Group g, String appKey, int open) {
        if (!active(c, g, open)) return false;
        int i = g.apps.indexOf(appKey);
        return i >= 0 && (DayPass.has(open, DayPass.LOCK_APPS) || i < FREE_GROUP_APPS);
    }

    /** The apps of this group that its lock holds for this person. */
    public static java.util.List<String> heldApps(LauncherConfig c, LauncherConfig.Group g, boolean pro) {
        return heldApps(c, g, pro ? DayPass.ALL : DayPass.FREE);
    }

    public static java.util.List<String> heldApps(LauncherConfig c, LauncherConfig.Group g, int open) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String k : g.apps) if (holds(c, g, k, open)) out.add(k);
        return out;
    }

    /** The end of the daily window that is open at {@code nowMs}, or 0 when none is. Back-to-back windows count as one. */
    public static long windowEnd(LauncherConfig.Group g, long nowMs, ZoneId zone) {
        long end = once(g, nowMs, zone);
        if (end == 0) return 0;
        for (int i = 0; i < 14; i++) {
            long more = once(g, end, zone);
            if (more <= end) break;
            end = more;
        }
        return end;
    }

    private static long once(LauncherConfig.Group g, long nowMs, ZoneId zone) {
        if (g.days == 0) return 0;
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(zone);
        for (int back = 1; back >= 0; back--) {
            LocalDate d = now.toLocalDate().minusDays(back);
            if (!g.hasDay(d.getDayOfWeek())) continue;
            ZonedDateTime start = d.atStartOfDay(zone).plusMinutes(g.startMin);
            ZonedDateTime end = g.endMin > g.startMin
                    ? d.atStartOfDay(zone).plusMinutes(g.endMin)
                    : d.plusDays(1).atStartOfDay(zone).plusMinutes(g.endMin);
            if (!now.isBefore(start) && now.isBefore(end)) return end.toInstant().toEpochMilli();
        }
        return 0;
    }

    /** The next time the daily window opens after {@code nowMs}, or 0 when there is no daily window. */
    public static long nextStart(LauncherConfig.Group g, long nowMs, ZoneId zone) {
        if (g.days == 0) return 0;
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(zone);
        for (int k = 0; k <= 7; k++) {
            LocalDate d = now.toLocalDate().plusDays(k);
            if (!g.hasDay(d.getDayOfWeek())) continue;
            ZonedDateTime start = d.atStartOfDay(zone).plusMinutes(g.startMin);
            if (start.toInstant().toEpochMilli() > nowMs) return start.toInstant().toEpochMilli();
        }
        return 0;
    }

    /** When this group's lock ends if it is locked at {@code nowMs}, otherwise 0 (not counting locks by activity type). */
    public static long lockedUntil(LauncherConfig.Group g, long nowMs, ZoneId zone) {
        return lockedUntil(g, nowMs, zone, Slot.NONE);
    }

    /**
     * Same, with the slot going on: the group is also locked while a slot of one of its chosen types runs, and while library mode is on
     * when the group is set to lock then. With {@link LauncherConfig.Group#both} the daily window and the activity types hold together:
     * the group is locked only while the window is open and a slot of a chosen type is going on (until the first of the two ends).
     */
    public static long lockedUntil(LauncherConfig.Group g, long nowMs, ZoneId zone, Slot slot) {
        long manual = g.untilMs > nowMs ? g.untilMs : 0;
        long window = windowEnd(g, nowMs, zone);
        long byType = during(g, slot, nowMs) ? slot.endMs : 0;
        long timed = combined(g) ? (window != 0 && byType != 0 ? Math.min(window, byType) : 0) : Math.max(window, byType);
        return Math.max(Math.max(manual, timed), libraryEnd(g, slot, nowMs));
    }

    /** True when this group's window and activity types must both hold (it has both, and is set that way). */
    public static boolean combined(LauncherConfig.Group g) {
        return g.both && g.days != 0 && !g.during.isEmpty();
    }

    /** When library mode ends, if the group locks in library mode and it is on at {@code nowMs}; otherwise 0. */
    public static long libraryEnd(LauncherConfig.Group g, Slot slot, long nowMs) {
        return g != null && g.library && slot != null && slot.libraryEndMs > nowMs ? slot.libraryEndMs : 0L;
    }

    /** True when the slot going on is of a type that locks this group. */
    public static boolean during(LauncherConfig.Group g, Slot slot, long nowMs) {
        if (g == null || slot == null || slot.kind < 0 || slot.endMs <= nowMs || g.during.isEmpty()) return false;
        return g.during.contains(LauncherConfig.typeKey(slot.kind, slot.sub)) || g.during.contains(LauncherConfig.anyKey(slot.kind));
    }

    /** The name of one activity type key as the person reads it: "Study", "Any task", "Other rest". */
    public static String typeName(String key) {
        if (!LauncherConfig.validDuring(key)) return key == null ? "" : key;
        int c = key.indexOf(':');
        int kind = Integer.parseInt(key.substring(0, c));
        String name = key.substring(c + 1);
        String kindName = Routine.KIND_NAMES[kind];
        if (name.equals("*")) return "Any " + kindName.toLowerCase(java.util.Locale.ROOT);
        if (name.equalsIgnoreCase(Routine.OTHER)) return "Other " + kindName.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** The chosen types of a group in words: "Study", "Study and Revision", "Study, Revision and 2 more". */
    public static String duringText(LauncherConfig.Group g) {
        return duringText(g.during);
    }

    /** Same, for a list of type keys. */
    public static String duringText(java.util.List<String> keys) {
        java.util.List<String> names = new java.util.ArrayList<>();
        // each name and the joining word are put into the person's language here (a text of the form "A and B" has no entry of its own)
        for (String k : keys) names.add(Tr.t(typeName(k)));
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " " + Tr.t("and") + " " + names.get(1);
        return names.get(0) + ", " + names.get(1) + " " + Tr.t("and " + (names.size() - 2) + " more");
    }

    /** The lock that holds this app right now (the one that lasts longest), or null. */
    public static Hit check(LauncherConfig c, String appKey, boolean pro, long nowMs, ZoneId zone) {
        return check(c, appKey, pro ? DayPass.ALL : DayPass.FREE, nowMs, zone);
    }

    /** Same, for what is open: a lock is only strict while {@link DayPass#LOCK_STRICT} is open. */
    public static Hit check(LauncherConfig c, String appKey, int open, long nowMs, ZoneId zone) {
        return check(c, appKey, open, nowMs, zone, Slot.NONE);
    }

    /** Same, with the slot going on (locks by activity type count). */
    public static Hit check(LauncherConfig c, String appKey, int open, long nowMs, ZoneId zone, Slot slot) {
        Hit best = null;
        for (LauncherConfig.Group g : c.groups) {
            if (!holds(c, g, appKey, open)) continue;
            long until = lockedUntil(g, nowMs, zone, slot);
            if (until == 0) continue;
            if (best == null || until > best.untilMs) {
                long lib = libraryEnd(g, slot, nowMs);
                boolean byLibrary = lib != 0 && until == lib;
                long window = windowEnd(g, nowMs, zone);
                boolean byType = !byLibrary && during(g, slot, nowMs)
                        && (combined(g) ? until == Math.min(window, slot.endMs) : until == slot.endMs);
                best = new Hit(g, until, g.strict && DayPass.has(open, DayPass.LOCK_STRICT),
                        byType ? typeName(LauncherConfig.typeKey(slot.kind, slot.sub)) : "", byLibrary, byLibrary && slot.libraryOpen);
            }
        }
        return best;
    }

    /** When the latest strict lock that holds at {@code nowMs} ends, or 0 when none holds (strict locks are Pro). */
    public static long strictUntil(LauncherConfig c, boolean pro, long nowMs, ZoneId zone) {
        return strictUntil(c, pro ? DayPass.ALL : DayPass.FREE, nowMs, zone);
    }

    /** Same, for what is open: only a strict lock that is itself in force counts, and only while strict locks are open. */
    public static long strictUntil(LauncherConfig c, int open, long nowMs, ZoneId zone) {
        return strictUntil(c, open, nowMs, zone, Slot.NONE);
    }

    /** Same, with the slot going on (a strict lock by activity type counts while its type runs). */
    public static long strictUntil(LauncherConfig c, int open, long nowMs, ZoneId zone, Slot slot) {
        if (!DayPass.has(open, DayPass.LOCK_STRICT)) return 0;
        long best = 0;
        for (LauncherConfig.Group g : c.groups) {
            if (g.strict && active(c, g, open)) best = Math.max(best, lockedUntil(g, nowMs, zone, slot));
        }
        return best;
    }

    // ------------------------------------------------------------------ a strict lock must not last for ever

    /** A strict lock holds for at most this many minutes in a row. */
    public static final int STRICT_MAX_RUN_MIN = 12 * 60;
    /** A strict lock leaves at least this many minutes of every day unlocked. */
    public static final int STRICT_MIN_FREE_MIN = 2 * 60;

    /**
     * Why this strict lock cannot be saved, in a sentence for the person, or null when it is fine (or not strict). A strict lock cannot be
     * broken or changed while it holds, so it must always end: it may hold for {@value #STRICT_MAX_RUN_MIN} minutes in a row at most, and
     * must leave {@value #STRICT_MIN_FREE_MIN} minutes of every day free. Library mode is refused because it can have no end. The week is
     * laid out minute by minute from {@code firstDay}: the daily window, and the slots of the chosen activity types in the routine of each day.
     * {@code routine} may be null when it could not be read (the activity part is then not checked).
     */
    public static String strictProblem(LauncherConfig.Group g, Routine routine, LocalDate firstDay) {
        if (g == null || !g.strict) return null;
        if (g.library) {
            return "Library mode can go on without an end, so a strict lock cannot include it. "
                    + "Switch off \"Also lock in library mode\", or switch off strict.";
        }
        final int day = 1440, week = 7 * day;
        boolean[] window = new boolean[week];
        boolean[] type = new boolean[week];
        boolean hasWindow = g.days != 0;
        boolean hasType = !g.during.isEmpty() && routine != null;
        for (int i = 0; i < 7; i++) {
            LocalDate date = firstDay.plusDays(i);
            if (hasWindow && g.hasDay(date.getDayOfWeek())) {
                int from = i * day + g.startMin;
                int to = i * day + (g.endMin > g.startMin ? g.endMin : g.endMin + day);
                for (int m = from; m < to; m++) window[m % week] = true;
            }
            if (hasType) {
                Routine r;
                try {
                    r = routine.forDay(date);
                } catch (RuntimeException e) {
                    r = routine;
                }
                for (Routine.Slot sl : r.compute().slots) {
                    if (sl.block.skipped) continue;
                    if (!g.during.contains(LauncherConfig.typeKey(sl.block.kind, sl.block.sub))
                            && !g.during.contains(LauncherConfig.anyKey(sl.block.kind))) continue;
                    for (int m = sl.start; m < sl.end; m++) type[(i * day + m) % week] = true;
                }
            }
        }
        boolean[] locked = new boolean[week];
        boolean both = combined(g) && hasType;
        for (int m = 0; m < week; m++) {
            locked[m] = both ? window[m] && type[m] : (hasWindow && window[m]) || (hasType && type[m]);
        }
        int first = -1;
        for (int m = 0; m < week; m++) if (!locked[m]) {
            first = m;
            break;
        }
        int longest = first < 0 ? week : 0, run = 0;
        for (int k = 1; first >= 0 && k <= week; k++) {
            if (locked[(first + k) % week]) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 0;
            }
        }
        if (longest > STRICT_MAX_RUN_MIN) {
            return "A strict lock can hold for 12 hours in a row at most, because there is no way past it. "
                    + "Shorten the hours, or choose fewer activity types.";
        }
        for (int i = 0; i < 7; i++) {
            int n = 0;
            for (int m = i * day; m < (i + 1) * day; m++) if (locked[m]) n++;
            if (day - n < STRICT_MIN_FREE_MIN) {
                return "A strict lock has to leave at least 2 free hours every day. Shorten the hours, or pick fewer days.";
            }
        }
        return null;
    }

    /** True when this group is locked now (by hand or by its daily window). */
    public static boolean groupLocked(LauncherConfig.Group g, long nowMs, ZoneId zone) {
        return lockedUntil(g, nowMs, zone) != 0;
    }

    /** Same, with the slot going on. */
    public static boolean groupLocked(LauncherConfig.Group g, long nowMs, ZoneId zone, Slot slot) {
        return lockedUntil(g, nowMs, zone, slot) != 0;
    }

    /**
     * The apps to keep out of the list of all apps and out of search now: the apps of the groups that are locked now and are set to hide
     * their apps (only the apps the lock really holds for this person).
     */
    public static java.util.Set<String> hiddenNow(LauncherConfig c, int open, long nowMs, ZoneId zone, Slot slot) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (LauncherConfig.Group g : c.groups) {
            if (g.hide && groupLocked(g, nowMs, zone, slot)) out.addAll(heldApps(c, g, open));
        }
        return out;
    }

    private static final String[] DAY_SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    /** "Every day", "Weekdays", "Weekends", or the days by name. */
    public static String daysText(int mask) {
        int m = mask & 127;
        if (m == 0) return "No days";
        if (m == EVERY_DAY) return "Every day";
        if (m == WEEKDAYS) return "Weekdays";
        if (m == WEEKENDS) return "Weekends";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((m & (1 << i)) == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(DAY_SHORT[i]);
        }
        return sb.toString();
    }

    public static String dayName(int index) {
        return DAY_SHORT[Math.max(0, Math.min(6, index))];
    }

    /** How long is left, in words ("1h 20m", "45 min"). */
    public static String left(long untilMs, long nowMs) {
        long mins = Math.max(1, (untilMs - nowMs + 59999L) / 60000L);
        return Routine.formatDuration((int) Math.min(mins, 99999));
    }

    /** A moment as the person reads it: "6:00 AM" today, "tomorrow 6:00 AM", or "Sat 6:00 AM". */
    public static String untilText(long ms, long nowMs, ZoneId zone, boolean h24) {
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(zone);
        String t = Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
        LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
        LocalDate day = z.toLocalDate();
        if (day.equals(today)) return t;
        if (day.equals(today.plusDays(1))) return "tomorrow " + t;
        return dayName(day.getDayOfWeek().getValue() - 1) + " " + t;
    }

    /** One line about a group for the settings: "Locked until 6:00 AM", "Next: Mon 9:00 PM" or "Not scheduled". */
    public static String status(LauncherConfig.Group g, long nowMs, ZoneId zone, boolean h24) {
        return status(g, nowMs, zone, h24, Slot.NONE);
    }

    /** Same, with the slot going on; a group locked by activity type says which ("Locks during Study"). */
    public static String status(LauncherConfig.Group g, long nowMs, ZoneId zone, boolean h24, Slot slot) {
        long until = lockedUntil(g, nowMs, zone, slot);
        String types = duringText(g);
        if (until != 0) {
            long lib = libraryEnd(g, slot, nowMs);
            if (lib != 0 && until == lib && slot.libraryOpen) return "Locked until library mode is switched off";
            return "Locked until " + untilText(until, nowMs, zone, h24);
        }
        long next = nextStart(g, nowMs, zone);
        String lib = g.library ? "  ·  and in library mode" : "";
        if (combined(g)) {
            String when = LockRules.daysText(g.days).toLowerCase(java.util.Locale.ROOT) + ", " + hm(g.startMin, h24) + " to " + hm(g.endMin, h24);
            return "Locks during " + types + ", " + when + lib;
        }
        if (next != 0) return "Next: " + untilText(next, nowMs, zone, h24) + (types.isEmpty() ? "" : "  ·  and during " + types) + lib;
        if (!types.isEmpty()) return "Locks during " + types + lib;
        if (g.library) return "Locks in library mode";
        return "Not scheduled";
    }

    private static String hm(int minutes, boolean h24) {
        return Routine.formatTime(minutes, h24);
    }
}
