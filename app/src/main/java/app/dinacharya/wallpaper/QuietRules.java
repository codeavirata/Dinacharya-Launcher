package app.dinacharya.wallpaper;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quiet mode: rules that set the phone's ringer on their own. A rule says what the phone does (ring, vibrate, silent, or Do Not Disturb)
 * and when, the same ways a lock does: at hours on chosen days, during chosen activity types, or only when both hold (see {@link LockMode}),
 * and optionally also while library mode is on. When several rules hold at once the quietest wins. Also the steps that set the ringer and
 * Do Not Disturb and put them back afterwards. Uses no Android classes.
 */
public final class QuietRules {
    /** What the phone does. Higher is quieter. */
    public static final int RING = 0, VIBRATE = 1, SILENT = 2, DND = 3;
    /** How many rules there can be. */
    public static final int MAX_RULES = 10;
    public static final String[] SOUND_NAMES = {"Ring", "Vibrate", "Silent", "Do Not Disturb"};

    /** One rule. */
    public static final class Rule {
        public String name;
        public int sound = VIBRATE;
        /** The daily window, as for a lock: days mask (0: no daily window), start and end in minutes after midnight. */
        public int days;
        public int startMin = 9 * 60;
        public int endMin = 17 * 60;
        /** The activity types (see {@link LauncherConfig#typeKey} and {@link LauncherConfig#anyKey}). */
        public final List<String> during = new ArrayList<>();
        /** With both a window and types: only while both hold. */
        public boolean both;
        /** Also while library mode is on. */
        public boolean library;

        public Rule(String name) {
            this.name = clean(name);
        }

        public Rule copy() {
            Rule r = new Rule(name);
            r.sound = sound;
            r.days = days;
            r.startMin = startMin;
            r.endMin = endMin;
            r.during.addAll(during);
            r.both = both;
            r.library = library;
            return r;
        }

        /** How the rule holds: by time, by activity, by both (or either, for a rule set both ways). */
        public int by() {
            return LockMode.of(days, during.size(), both);
        }

        /** True when the rule can ever hold: it has hours, activity types or library mode. */
        public boolean valid() {
            return days != 0 || !during.isEmpty() || library;
        }

        /** True when this rule holds now, with {@code slot} going on. */
        public boolean holds(long nowMs, ZoneId zone, LockRules.Slot slot) {
            if (library && slot != null && slot.libraryEndMs > nowMs) return true;
            boolean window = days != 0 && LockRules.windowEnd(days, startMin, endMin, nowMs, zone) != 0;
            boolean type = LockRules.during(during, slot, nowMs);
            boolean together = both && days != 0 && !during.isEmpty();
            return together ? window && type : window || type;
        }

        /** The next time the daily window opens or closes after {@code nowMs}, or 0 (no window). */
        public long nextEdge(long nowMs, ZoneId zone) {
            if (days == 0) return 0L;
            long end = LockRules.windowEnd(days, startMin, endMin, nowMs, zone);
            return end != 0 ? end : LockRules.nextStart(days, startMin, nowMs, zone);
        }

        /** "Vibrate  ·  Weekdays 9:00 AM to 5:00 PM" and the like. */
        public String text(boolean h24) {
            List<String> parts = new ArrayList<>();
            boolean hours = days != 0, types = !during.isEmpty();
            String time = LockRules.daysText(days) + ", " + Routine.formatTime(startMin, h24) + " to " + Routine.formatTime(endMin, h24);
            if (hours && types) parts.add(Tr.t("During") + " " + LockRules.duringText(during) + (both ? ", " : " " + Tr.t("or") + " ") + time);
            else if (types) parts.add(Tr.t("During") + " " + LockRules.duringText(during));
            else if (hours) parts.add(time);
            if (library) parts.add(Tr.t("library mode"));
            StringBuilder sb = new StringBuilder(Tr.t(soundName(sound)));
            for (int i = 0; i < parts.size(); i++) sb.append(i == 0 ? "  ·  " : " + ").append(parts.get(i));
            return sb.toString();
        }
    }

    public final List<Rule> rules = new ArrayList<>();

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public static String soundName(int sound) {
        return SOUND_NAMES[Math.max(RING, Math.min(DND, sound))];
    }

    static String clean(String n) {
        String t = n == null ? "" : n.replace('|', ' ').replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > 24) t = t.substring(0, 24).trim();
        return t.isEmpty() ? "Quiet" : t;
    }

    /** What the phone should do now: the quietest of the rules that hold, or -1 when none does. */
    public int now(long nowMs, ZoneId zone, LockRules.Slot slot) {
        int best = -1;
        for (Rule r : rules) if (r.valid() && r.holds(nowMs, zone, slot)) best = Math.max(best, r.sound);
        return best;
    }

    /** The next moment a daily window of any rule opens or closes, or 0. */
    public long nextEdge(long nowMs, ZoneId zone) {
        long best = 0L;
        for (Rule r : rules) {
            long e = r.nextEdge(nowMs, zone);
            if (e > 0L && (best == 0L || e < best)) best = e;
        }
        return best;
    }

    // ------------------------------------------------------------------ the phone

    /** AudioManager ringer modes: silent 0, vibrate 1, normal 2. -1 stands for "none". */
    public static final int RINGER_SILENT = 0, RINGER_VIBRATE = 1, RINGER_NORMAL = 2;
    /** NotificationManager interruption filters: all 1, priority only 2. 0 stands for "none". */
    public static final int FILTER_ALL = 1, FILTER_PRIORITY = 2;

    /** What to do with the ringer: the mode to set (-1: leave it), what to remember to put back (-1: nothing) and the mode set by us (-1: none). */
    public static final class RingerStep {
        public final int set, keep, ours;

        RingerStep(int set, int keep, int ours) {
            this.set = set;
            this.keep = keep;
            this.ours = ours;
        }
    }

    /**
     * The ringer rule. Vibrate and silent only ever lower the ringer (one that is already as quiet is left alone); ring only raises it. The
     * old mode is remembered the first time we change it. Once that is done it is not forced again, so a person who changes the ringer
     * keeps their choice; only a change from one rule to another moves the ringer we set. When nothing wants the ringer any more (no rule,
     * or Do Not Disturb), the old mode comes back, but only if the ringer is still the way we left it.
     *
     * @param sound   what the rules want: RING, VIBRATE, SILENT, DND or -1 for none
     * @param current the ringer mode now
     * @param kept    the mode remembered earlier, or -1
     * @param ours    the mode we set earlier, or -1
     */
    public static RingerStep ringerStep(int sound, int current, int kept, int ours) {
        int target = sound == SILENT ? RINGER_SILENT : sound == VIBRATE ? RINGER_VIBRATE : sound == RING ? RINGER_NORMAL : -1;
        if (target >= 0) {
            if (kept >= 0) {
                if (current == ours && ours != target) return new RingerStep(target, kept, target);
                return new RingerStep(-1, kept, ours);
            }
            boolean change = target == RINGER_NORMAL ? current < RINGER_NORMAL : current > target;
            if (change) return new RingerStep(target, current, target);
            return new RingerStep(-1, -1, -1);
        }
        if (kept >= 0 && current == ours) return new RingerStep(kept, -1, -1);
        return new RingerStep(-1, -1, -1);
    }

    /** What to do with Do Not Disturb: the filter to set (0: leave it) and the one to remember (0: nothing). */
    public static final class HoldStep {
        public final int set, keep;

        HoldStep(int set, int keep) {
            this.set = set;
            this.keep = keep;
        }
    }

    /**
     * Do Not Disturb (priority only) goes on when Do Not Disturb is off and a rule wants it, and back off when the rule ends, if it is
     * still "priority only". Do Not Disturb that the person had on already is never touched.
     */
    public static HoldStep holdStep(boolean want, int current, int kept) {
        if (want) {
            if (kept > 0) return new HoldStep(0, kept);
            if (current == FILTER_ALL) return new HoldStep(FILTER_PRIORITY, FILTER_ALL);
            return new HoldStep(0, 0);
        }
        if (kept > 0 && current == FILTER_PRIORITY) return new HoldStep(kept, 0);
        return new HoldStep(0, 0);
    }

    // ------------------------------------------------------------------ saving

    private static final String MAGIC = "DINAQUIET2";

    public String serialize() {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        for (Rule r : rules) {
            sb.append("rule=").append(r.name).append('|').append(r.sound).append('|').append(r.days).append('|').append(r.startMin).append('|')
                    .append(r.endMin).append('|').append(r.both ? 1 : 0).append('|').append(r.library ? 1 : 0).append('|');
            for (int i = 0; i < r.during.size(); i++) sb.append(i > 0 ? "," : "").append(r.during.get(i));
            sb.append('\n');
        }
        return sb.toString();
    }

    public static QuietRules parse(String text) {
        QuietRules q = new QuietRules();
        if (text == null) return q;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return q;
        for (int i = 1; i < lines.length && q.rules.size() < MAX_RULES; i++) {
            if (!lines[i].startsWith("rule=")) continue;
            String[] f = lines[i].substring(5).split("\\|", -1);
            if (f.length < 8) continue;
            try {
                Rule r = new Rule(f[0]);
                r.sound = Math.max(RING, Math.min(DND, Integer.parseInt(f[1].trim())));
                r.days = Math.max(0, Math.min(127, Integer.parseInt(f[2].trim())));
                r.startMin = Math.max(0, Math.min(1439, Integer.parseInt(f[3].trim())));
                r.endMin = Math.max(0, Math.min(1439, Integer.parseInt(f[4].trim())));
                r.both = f[5].trim().equals("1");
                r.library = f[6].trim().equals("1");
                for (String k : f[7].split(",")) {
                    String key = k.trim().toLowerCase(Locale.ROOT);
                    if (LauncherConfig.validDuring(key) && !r.during.contains(key)) r.during.add(key);
                }
                if (r.valid()) q.rules.add(r);
            } catch (NumberFormatException ignored) {
                // a damaged line is skipped
            }
        }
        return q;
    }
}
