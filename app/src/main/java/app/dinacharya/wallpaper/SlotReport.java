package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The slot-by-slot analysis of a day report: for each slot that has run, and for each activity, how much of the time went to the activity
 * (the time devoted), how much phone use was other than the activity's own apps, the focus (the share of the time with no other phone
 * use), the longest run of focus, and the stretches of other phone use with their times. One place works the numbers out, so the screen
 * and the PDF agree. Uses no Android classes.
 */
public final class SlotReport {
    /** A focus of at least this much is good; at least {@link #FAIR_PCT} is fair; below it is low. */
    public static final int GOOD_PCT = 75, FAIR_PCT = 50;
    public static final int TONE_NONE = -1, TONE_GOOD = 0, TONE_FAIR = 1, TONE_LOW = 2;
    /** The most apps listed for one slot or activity. */
    public static final int MAX_APPS = 5;

    private SlotReport() {
    }

    /** How a focus percentage is judged: {@link #TONE_NONE} when unknown (a number below 0). */
    public static int tone(int pct) {
        if (pct < 0) return TONE_NONE;
        if (pct >= GOOD_PCT) return TONE_GOOD;
        return pct >= FAIR_PCT ? TONE_FAIR : TONE_LOW;
    }

    /** A length of time in a few characters: "0 min", "<1 min", "14 min", "1h 5m". */
    public static String dur(long ms) {
        if (ms <= 0) return "0 min";
        if (ms < 60000L) return "<1 min";
        return Routine.formatDuration((int) Math.round(ms / 60000.0));
    }

    /** One slot, one activity, or the whole day. */
    public static final class Item {
        public final String title;
        public final int kind;
        public final String sub;
        /** How many slots this stands for (1 for a slot). */
        public final int slots;
        /** For a slot: when it began and was due to end (0 for an activity or the day). */
        public final long startMs;
        public final long endMs;
        /** False when phone use could not be read: then nothing about it is known. */
        public final boolean known;
        public long workedMs;
        /** Time with an app in front, and the part of it that was in the activity's own apps. */
        public long phoneMs;
        public long partMs;
        /** The longest stretch with no other phone use; -1 when not known (a report made before this was kept, or no phone data). */
        public long longestFocusMs = -1;
        /** How many separate stretches of other phone use there were; {@link #uses} may hold fewer. */
        public int useCount;
        public final List<SlotUse.Episode> uses = new ArrayList<>();
        public final List<DayReport.App> apps = new ArrayList<>();

        Item(String title, int kind, String sub, int slots, long startMs, long endMs, boolean known) {
            this.title = title == null ? "" : title;
            this.kind = kind;
            this.sub = sub == null ? "" : sub;
            this.slots = slots;
            this.startMs = startMs;
            this.endMs = endMs;
            this.known = known;
        }

        /** Phone use that was not part of the activity. */
        public long offMs() {
            return known ? Math.max(0, phoneMs - Math.min(partMs, phoneMs)) : 0;
        }

        /** The time that went to the activity: the slot's time without the other phone use. */
        public long devotedMs() {
            return Math.max(0, workedMs - Math.min(workedMs, offMs()));
        }

        /** 0..100: the share of the time with no other phone use; -1 when phone use is not known or no time has passed. */
        public int focusPct() {
            if (!known || workedMs <= 0) return -1;
            double f = 100.0 * (workedMs - Math.min(workedMs, offMs())) / workedMs;
            return (int) Math.max(0, Math.min(100, Math.round(f)));
        }

        public int tone() {
            return SlotReport.tone(focusPct());
        }

        /** The exact stretches were kept for this one (reports made before that was added do not have them). */
        public boolean hasTimes() {
            return known && longestFocusMs >= 0;
        }

        public boolean timesCut() {
            return useCount > uses.size();
        }

        /** The words under a slot's name while it is closed. */
        public String summary() {
            if (!known) return "Phone use was not available";
            if (offMs() <= 0) return "Devoted " + dur(devotedMs()) + "  ·  No other phone use";
            return "Devoted " + dur(devotedMs()) + "  ·  Other phone use " + dur(offMs());
        }
    }

    private static String nameOf(DayReport.Row row) {
        String t = row.title == null || row.title.isEmpty() ? Routine.KIND_NAMES[Math.max(0, Math.min(Routine.KIND_NAMES.length - 1, row.kind))] : row.title;
        return row.newTitle == null || row.newTitle.isEmpty() ? t : t + "  →  " + row.newTitle;
    }

    /** Whether a row is a slot that has run (and so has something to say). */
    private static boolean ran(DayReport.Row row) {
        return row.kind != Routine.SLEEP && row.status != DayReport.SKIPPED && row.activeMs > 0;
    }

    /** The slots that have run, in the order of the day. */
    public static List<Item> slots(DayReport r) {
        List<Item> out = new ArrayList<>();
        for (DayReport.Row row : r.rows) {
            if (!ran(row)) continue;
            Item it = new Item(nameOf(row), row.kind, row.sub, 1, row.startMs, row.endMs, r.phoneKnown);
            it.workedMs = row.activeMs;
            it.phoneMs = r.phoneKnown ? row.phoneMs : 0;
            it.partMs = r.phoneKnown ? Math.min(row.partMs, row.phoneMs) : 0;
            it.longestFocusMs = r.phoneKnown ? row.longestFocusMs : -1;
            it.useCount = r.phoneKnown ? Math.max(row.useCount, row.uses.size()) : 0;
            if (r.phoneKnown) {
                it.uses.addAll(row.uses);
                it.apps.addAll(row.apps);
            }
            out.add(it);
        }
        return out;
    }

    /** Slots of the day that are not in {@link #slots}: not started yet, or taken off. */
    public static int others(DayReport r) {
        int n = 0;
        for (DayReport.Row row : r.rows) if (row.kind != Routine.SLEEP && !ran(row)) n++;
        return n;
    }

    /** The whole day's slots added up (its title is empty). Apps and stretches are not carried. */
    public static Item day(DayReport r) {
        Item all = new Item("", Routine.TASK, "", 0, 0, 0, r.phoneKnown);
        long longest = -1;
        int slots = 0;
        for (Item s : slots(r)) {
            slots++;
            all.workedMs += s.workedMs;
            all.phoneMs += s.phoneMs;
            all.partMs += s.partMs;
            all.useCount += s.useCount;
            longest = Math.max(longest, s.longestFocusMs);
        }
        all.longestFocusMs = longest;
        return withSlots(all, slots);
    }

    private static Item withSlots(Item it, int slots) {
        Item n = new Item(it.title, it.kind, it.sub, slots, it.startMs, it.endMs, it.known);
        n.workedMs = it.workedMs;
        n.phoneMs = it.phoneMs;
        n.partMs = it.partMs;
        n.longestFocusMs = it.longestFocusMs;
        n.useCount = it.useCount;
        return n;
    }

    /** One entry for each activity type that had a slot that ran, the one with the most time first. */
    public static List<Item> activities(DayReport r) {
        List<Item> out = new ArrayList<>();
        for (TypeStats.Line l : TypeStats.of(r)) {
            if (!l.slot.isEmpty() || l.slots <= 0 || l.workedMs <= 0) continue;   // a slot's goal is not an activity
            Item it = new Item(l.label(), l.kind, l.sub, l.slots, 0, 0, r.phoneKnown);
            it.workedMs = l.workedMs;
            it.phoneMs = r.phoneKnown ? l.phoneMs : 0;
            it.partMs = r.phoneKnown ? Math.min(l.partMs, l.phoneMs) : 0;
            it.longestFocusMs = r.phoneKnown ? l.longestFocusMs : -1;
            String k = TypeStats.key(l.kind, l.sub);
            for (DayReport.Row row : r.rows) {
                if (row.kind == Routine.SLEEP || row.status == DayReport.SKIPPED || !TypeStats.key(row.kind, row.sub).equals(k)) continue;
                if (r.phoneKnown) it.useCount += Math.max(row.useCount, row.uses.size());
            }
            if (r.phoneKnown) {
                for (Object[] a : TypeStats.topApps(l)) it.apps.add(new DayReport.App((String) a[0], (Long) a[1]));
            }
            out.add(it);
        }
        // stable: the more time, the earlier
        Collections.sort(out, (x, y) -> Long.compare(y.workedMs, x.workedMs));
        return out;
    }

    /** The first moment of a slot's timeline (the slot's start, or earlier if a stretch of phone use began before it). */
    public static long spanStartMs(Item s) {
        long a = s.startMs;
        if (!s.uses.isEmpty()) a = Math.min(a, s.uses.get(0).startMs);
        return a;
    }

    /** The last moment of a slot's timeline. */
    public static long spanEndMs(Item s) {
        long b = s.endMs;
        for (SlotUse.Episode e : s.uses) b = Math.max(b, e.endMs);
        return b;
    }

    /**
     * Where the stretches of other phone use fall along a slot's timeline, as {from, to} between 0 and 1 (to is never before from).
     * Empty when the slot has no length or no stretches.
     */
    public static List<float[]> marks(Item s) {
        List<float[]> out = new ArrayList<>();
        long a = spanStartMs(s);
        long b = spanEndMs(s);
        if (b <= a) return out;
        double span = b - a;
        for (SlotUse.Episode e : s.uses) {
            double f = (e.startMs - a) / span;
            double t = (e.endMs - a) / span;
            f = Math.max(0, Math.min(1, f));
            t = Math.max(f, Math.min(1, t));
            out.add(new float[]{(float) f, (float) t});
        }
        return out;
    }
}
