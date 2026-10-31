package app.dinacharya.wallpaper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How quiet the phone should be: while library mode is on, and during chosen activity types. Each rule has a sound level for the ringer
 * (leave it, vibrate, silent) and whether notifications of other apps are held back (Do Not Disturb, priority only). When several rules
 * apply at once the quietest wins. Also the steps that set the ringer and the Do Not Disturb filter and put them back afterwards. Uses no
 * Android classes.
 */
public final class QuietRules {
    public static final int LEAVE = 0, VIBRATE = 1, SILENT = 2;
    /** How many activity types can have a rule. */
    public static final int MAX_TYPES = 12;

    /** One rule: the ringer and the notifications. */
    public static final class Rule {
        public static final Rule NONE = new Rule(LEAVE, false);
        public final int sound;
        public final boolean hold;

        public Rule(int sound, boolean hold) {
            this.sound = Math.max(LEAVE, Math.min(SILENT, sound));
            this.hold = hold;
        }

        public boolean isNone() {
            return sound == LEAVE && !hold;
        }

        /** Both rules at once: the quieter sound, and notifications held if either holds them. */
        public Rule with(Rule o) {
            return new Rule(Math.max(sound, o.sound), hold || o.hold);
        }

        /** True when this rule needs Do Not Disturb access (silent, or holding notifications). */
        public boolean needsAccess() {
            return sound == SILENT || hold;
        }

        /** In words: "Vibrate", "Silent, notifications held", "Notifications held" or "Off". */
        public String text() {
            String s = sound == SILENT ? "Silent" : sound == VIBRATE ? "Vibrate" : "";
            if (hold) return s.isEmpty() ? "Notifications held" : s + ", notifications held";
            return s.isEmpty() ? "Off" : s;
        }
    }

    /** While library mode is on. */
    public Rule library = Rule.NONE;
    /** By type key (see {@link LauncherConfig#typeKey} and {@link LauncherConfig#anyKey}); only rules that do something are kept. */
    public final Map<String, Rule> types = new LinkedHashMap<>();

    /** True when no rule does anything. */
    public boolean isEmpty() {
        return library.isNone() && types.isEmpty();
    }

    /** What applies now: library mode if it is on, and the rule of the running activity (its own type, else "any" of its kind). */
    public Rule now(boolean libraryOn, int kind, String sub) {
        Rule r = libraryOn ? library : Rule.NONE;
        if (kind < 0) return r;
        Rule t = types.get(LauncherConfig.typeKey(kind, sub));
        if (t == null) t = types.get(LauncherConfig.anyKey(kind));
        return t == null ? r : r.with(t);
    }

    public void setType(String key, Rule r) {
        if (r == null || r.isNone()) types.remove(key);
        else types.put(key, r);
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
     * The ringer rule. A ringer that is louder than the wanted level goes down to it and its old mode is remembered; one that is already as
     * quiet (or quieter) is left alone and nothing is remembered. Once that is done it is not forced again, so a person who turns the sound
     * back on keeps it; only a change from one rule to another (vibrate to silent) moves the ringer we set. When nothing wants quiet any more
     * the old mode comes back, but only if the ringer is still the way we left it (if the person changed it meanwhile, their choice stands).
     *
     * @param sound   the wanted level: LEAVE, VIBRATE or SILENT
     * @param current the ringer mode now
     * @param kept    the mode remembered earlier, or -1
     * @param ours    the mode we set earlier, or -1
     */
    public static RingerStep ringerStep(int sound, int current, int kept, int ours) {
        int target = sound == SILENT ? RINGER_SILENT : sound == VIBRATE ? RINGER_VIBRATE : -1;
        if (target >= 0) {
            if (kept >= 0) {
                if (current == ours && ours != target) return new RingerStep(target, kept, target);
                return new RingerStep(-1, kept, ours);
            }
            if (current > target) return new RingerStep(target, current, target);
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
     * Holding notifications back sets "priority only" when Do Not Disturb is off and puts it back when the rule ends, if it is still
     * "priority only". Do Not Disturb that the person had on already is never touched.
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

    private static final String MAGIC = "DINAQUIET1";

    private static String one(Rule r) {
        return r.sound + "," + (r.hold ? 1 : 0);
    }

    private static Rule read(String v) {
        String[] p = v.split(",");
        if (p.length != 2) return null;
        try {
            return new Rule(Integer.parseInt(p[0].trim()), p[1].trim().equals("1"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(MAGIC).append("\nlibrary=").append(one(library)).append('\n');
        for (Map.Entry<String, Rule> e : types.entrySet()) sb.append("type=").append(e.getKey()).append('|').append(one(e.getValue())).append('\n');
        return sb.toString();
    }

    public static QuietRules parse(String text) {
        QuietRules q = new QuietRules();
        if (text == null) return q;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return q;
        for (int i = 1; i < lines.length; i++) {
            int eq = lines[i].indexOf('=');
            if (eq <= 0) continue;
            String k = lines[i].substring(0, eq);
            String v = lines[i].substring(eq + 1).trim();
            if (k.equals("library")) {
                Rule r = read(v);
                if (r != null) q.library = r;
            } else if (k.equals("type") && q.types.size() < MAX_TYPES) {
                int bar = v.lastIndexOf('|');
                if (bar <= 0) continue;
                String key = v.substring(0, bar).toLowerCase(Locale.ROOT);
                Rule r = read(v.substring(bar + 1));
                if (r != null && !r.isNone() && LauncherConfig.validDuring(key)) q.types.put(key, r);
            }
        }
        return q;
    }
}
