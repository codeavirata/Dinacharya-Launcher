package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the minimal home screen remembers: which apps are hidden from the list of all apps, the
 * swipe targets, the look and the focus rules. The home screen itself lists no apps. Saved as text, like
 * the routine. Uses no Android classes.
 */
public final class LauncherConfig {
    public static final int MAX_RULES = 60;
    public static final int MAX_GROUPS = 8, MAX_GROUP_APPS = 40, MAX_DURING = 20;
    /** Phone use in slots: how many activity types can have a list of apps, and how many apps one list holds. */
    public static final int MAX_USE_TYPES = 40, MAX_USE_APPS = 40;
    /** The colours of the list of all apps, the pause screen and the settings. The home screen itself always shows the day poster. */
    public static final int THEME_AUTO = 0, THEME_DARK = 1, THEME_LIGHT = 2;
    public static final String[] THEME_NAMES = {"Auto", "Dark", "Light"};
    public static final String[] SIZE_NAMES = {"Small", "Medium", "Large", "Huge"};
    private static final int[] SIZE_SP = {20, 24, 29, 35};

    /** How an app is treated when it is opened from the home screen. */
    public static final int RULE_NONE = 0, RULE_NUDGE = 1, RULE_STRICT = 2;

    private static final String MAGIC = "DINALAUNCH1";

    /** A focus rule for one app. */
    public static final class Rule {
        public final String key;
        /** {@link #RULE_NUDGE}: a short pause. {@link #RULE_STRICT}: a long pause (Pro). */
        public int mode;
        /** Minutes of use per day after which the app is paused for a long time (Pro). 0 means no limit. */
        public int limitMin;
        /** Also pause when it is opened outside focus slots. */
        public boolean always;
        /** How the pause goes: a plain wait, or one of the playful ones in {@link PauseChallenge} (Pro). */
        public int challenge;

        Rule(String key, int mode, int limitMin, boolean always, int challenge) {
            this.key = key;
            this.mode = mode;
            this.limitMin = limitMin;
            this.always = always;
            this.challenge = challenge;
        }
    }

    /**
     * A group of apps that are locked together: on chosen days between two times (a window that may run past midnight),
     * or until a set moment ("Lock now for 2 hours"). Uses no Android classes.
     */
    public static final class Group {
        public String name;
        public final List<String> apps = new ArrayList<>();
        /** Start and end of the daily window, in minutes after midnight. An end at or before the start runs into the next day. */
        public int startMin = 21 * 60;
        public int endMin = 6 * 60;
        /** The days the window starts on: bit 0 is Monday, bit 6 is Sunday. 0 means there is no daily window. */
        public int days;
        /** Strict (Pro): no way past the lock, and it cannot be changed while it holds. */
        public boolean strict;
        /** A lock started by hand lasts until this moment (milliseconds since 1970); 0 when there is none. */
        public long untilMs;
        /**
         * Activity types that lock the group while a slot of that type is going on (see {@link #typeKey} and {@link #anyKey}). A break,
         * a slot taken off and the time between slots do not lock. Empty: the group is not locked by activity.
         */
        public final List<String> during = new ArrayList<>();
        /** Keep the apps of the group out of the list of all apps and out of search while their lock holds. */
        public boolean hide;
        /**
         * With both a daily window and activity types set: lock only while the window is open AND a slot of a chosen type is going on
         * ("Study, but only from 9 to 5"). Off: either one locks the group.
         */
        public boolean both;
        /** Also lock the group while library mode is on. */
        public boolean library;

        Group(String name) {
            this.name = cleanGroupName(name);
        }

        public boolean hasDay(java.time.DayOfWeek d) {
            return (days & (1 << (d.getValue() - 1))) != 0;
        }

        public boolean has(String key) {
            return apps.contains(key);
        }
    }

    /** The key of one activity type ("Study" of the task kind). A type with no name is the "Other" of its kind. */
    public static String typeKey(int kind, String sub) {
        String n = Routine.cleanSub(sub);
        return kind + ":" + (n.isEmpty() ? Routine.OTHER : n).toLowerCase(java.util.Locale.ROOT);
    }

    /** The key for every type of a kind ("any task"). */
    public static String anyKey(int kind) {
        return kind + ":*";
    }

    /** True for a key of either sort that names a real kind. */
    static boolean validDuring(String k) {
        if (k == null) return false;
        int c = k.indexOf(':');
        if (c <= 0 || c == k.length() - 1) return false;
        try {
            int kind = Integer.parseInt(k.substring(0, c));
            return kind >= 0 && kind < Routine.KIND_NAMES.length && k.indexOf('\t') < 0 && k.indexOf('\n') < 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static String cleanGroupName(String n) {
        String t = n == null ? "" : n.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > 24) t = t.substring(0, 24).trim();
        return t.isEmpty() ? "Group" : t;
    }

    /** Apps kept out of the list of all apps and out of search. */
    public final Set<String> hidden = new LinkedHashSet<>();
    /** Swipe left opens the camera and swipe right the phone, until the person picks something else. */
    public String swipeLeft = AppKey.CAMERA;
    public String swipeRight = AppKey.PHONE;
    public int theme = THEME_AUTO;
    public int size = 1;
    /** Open an app at once when the search finds just one. */
    public boolean autoOpen = true;
    /**
     * Open the keyboard, with the cursor in the search field, as soon as the list of all apps opens. On unless the person turns it off.
     * (Kept under its own name since 2.8: before that it was off by default, and a saved "off" that was only the default must not stay.)
     */
    public boolean keyboard = true;
    /** The kinds of slot where focus rules apply, as bits (1 shifted by the kind). Task by default. */
    public int focusKinds = 1 << Routine.TASK;
    /** Seconds of a short pause (nudge). */
    public int pauseSec = 10;
    /** Seconds of a long pause (strict, or a daily limit that is used up). */
    public int strictSec = 30;
    /**
     * Strong lock: locks and pauses are also held when an app is opened from somewhere other than this home screen
     * (Recents, App info, a notification, another launcher). On from the start, because a lock that only holds on one screen is not a
     * lock; it works as soon as the person has allowed what Android asks for, and only while something is locked or paused. Only
     * a person who switches it off by hand stores a 0 here.
     */
    public boolean strong = true;
    public final List<Rule> rules = new ArrayList<>();
    /** Groups of apps that are locked for a time. */
    public final List<Group> groups = new ArrayList<>();
    /**
     * Phone use in slots (optional, off until the person turns it on): time in apps that are not part of the slot's activity is marked on
     * the home screen and counted against focus in the reports.
     */
    public boolean trackUse = false;
    /**
     * For each activity type (see {@link #typeKey}), the apps that count as part of it: time in them is not phone use during a slot of that
     * type. A type with no entry has none, so every app counts as phone use in its slots.
     */
    public final java.util.Map<String, List<String>> typeApps = new java.util.LinkedHashMap<>();

    public LauncherConfig() {
    }

    // ------------------------------------------------------------------ small helpers

    public static int textSp(int size) {
        return SIZE_SP[Math.max(0, Math.min(SIZE_SP.length - 1, size))];
    }

    public boolean isFocusKind(int kind) {
        return kind >= 0 && kind < Routine.KIND_NAMES.length && (focusKinds & (1 << kind)) != 0;
    }

    public void setFocusKind(int kind, boolean on) {
        if (kind < 0 || kind >= Routine.KIND_NAMES.length) return;
        if (on) focusKinds |= 1 << kind;
        else focusKinds &= ~(1 << kind);
    }

    public Rule ruleFor(String key) {
        for (Rule r : rules) if (r.key.equals(key)) return r;
        return null;
    }

    /** Position of the app's rule in the list (the first few are the ones the free version keeps), or -1. */
    public int ruleIndex(String key) {
        for (int i = 0; i < rules.size(); i++) if (rules.get(i).key.equals(key)) return i;
        return -1;
    }

    /**
     * Sets or changes an app's rule; a rule that does nothing is removed. Returns the rule, or null if removed.
     */
    public Rule setRule(String key, int mode, int limitMin, boolean always) {
        Rule old = ruleFor(key);
        return setRule(key, mode, limitMin, always, old == null ? PauseChallenge.WAIT : old.challenge);
    }

    /** Same, with the way the pause goes ({@link PauseChallenge}). */
    public Rule setRule(String key, int mode, int limitMin, boolean always, int challenge) {
        if (!AppKey.valid(key) || AppKey.isAction(key)) return null;
        int ch = PauseChallenge.valid(challenge) ? challenge : PauseChallenge.WAIT;
        int m = Math.max(RULE_NONE, Math.min(RULE_STRICT, mode));
        int lim = Math.max(0, Math.min(1440, limitMin));
        Rule r = ruleFor(key);
        if (m == RULE_NONE && lim == 0) {
            if (r != null) rules.remove(r);
            return null;
        }
        if (r == null) {
            if (rules.size() >= MAX_RULES) return null;
            r = new Rule(key, m, lim, always, ch);
            rules.add(r);
        } else {
            r.mode = m;
            r.limitMin = lim;
            r.always = always;
            r.challenge = ch;
        }
        return r;
    }

    public void removeRule(String key) {
        Rule r = ruleFor(key);
        if (r != null) rules.remove(r);
    }

    /** A new, empty group with no daily window yet, or null when there are too many. */
    public Group addGroup(String name) {
        if (groups.size() >= MAX_GROUPS) return null;
        Group g = new Group(name);
        groups.add(g);
        return g;
    }

    public void removeGroup(Group g) {
        groups.remove(g);
    }

    /** Puts an app into a group, or takes it out. */
    public boolean setInGroup(Group g, String key, boolean in) {
        if (!AppKey.valid(key) || AppKey.isAction(key)) return false;
        if (!in) return g.apps.remove(key);
        if (g.apps.contains(key) || g.apps.size() >= MAX_GROUP_APPS) return false;
        g.apps.add(key);
        return true;
    }

    // ------------------------------------------------------------------ phone use in slots

    /** The apps that count as part of an activity type (never null; a copy). */
    public List<String> appsOfType(int kind, String sub) {
        List<String> l = typeApps.get(typeKey(kind, sub));
        return l == null ? new ArrayList<String>() : new ArrayList<>(l);
    }

    /**
     * Sets the apps that count as part of an activity type. Keys that are not apps, repeats and anything past the limit are left out; an empty
     * list takes the entry away. Returns false when the type has no room left (too many types already have a list).
     */
    public boolean setTypeApps(int kind, String sub, List<String> keys) {
        if (kind < 0 || kind >= Routine.KIND_NAMES.length) return false;
        String tk = typeKey(kind, sub);
        List<String> clean = new ArrayList<>();
        if (keys != null) {
            for (String k : keys) {
                if (AppKey.valid(k) && !AppKey.isAction(k) && !clean.contains(k) && clean.size() < MAX_USE_APPS) clean.add(k);
            }
        }
        if (clean.isEmpty()) {
            typeApps.remove(tk);
            return true;
        }
        if (!typeApps.containsKey(tk) && typeApps.size() >= MAX_USE_TYPES) return false;
        typeApps.put(tk, clean);
        return true;
    }

    /**
     * The packages whose use counts as part of the activity of a slot (empty when phone use in slots is off, so every app counts as
     * phone use then). Apps are matched by package, whichever profile they are in.
     */
    public java.util.Set<String> partPackages(int kind, String sub) {
        java.util.Set<String> out = new LinkedHashSet<>();
        if (!trackUse) return out;
        List<String> l = typeApps.get(typeKey(kind, sub));
        if (l != null) for (String k : l) out.add(AppKey.pkg(k));
        out.remove("");
        return out;
    }

    /** Forgets an app everywhere (it was uninstalled). */
    public void forget(String key) {
        hidden.remove(key);
        removeRule(key);
        for (Group g : groups) g.apps.remove(key);
        java.util.Iterator<java.util.Map.Entry<String, List<String>>> it = typeApps.entrySet().iterator();
        while (it.hasNext()) {
            List<String> l = it.next().getValue();
            l.remove(key);
            if (l.isEmpty()) it.remove();
        }
        if (key.equals(swipeLeft)) swipeLeft = "";
        if (key.equals(swipeRight)) swipeRight = "";
    }

    // ------------------------------------------------------------------ saving

    public String serialize() {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        sb.append("theme=").append(theme).append('\n');
        sb.append("size=").append(size).append('\n');
        sb.append("autoOpen=").append(autoOpen ? 1 : 0).append('\n');
        sb.append("kbd=").append(keyboard ? 1 : 0).append('\n');
        sb.append("focusKinds=").append(focusKinds).append('\n');
        sb.append("pauseSec=").append(pauseSec).append('\n');
        sb.append("strictSec=").append(strictSec).append('\n');
        if (!strong) sb.append("strong=").append(0).append('\n');
        if (trackUse) sb.append("trackUse=1\n");
        sb.append("swipes=2\n");
        sb.append("swipeLeft=").append(swipeLeft).append('\n');
        sb.append("swipeRight=").append(swipeRight).append('\n');
        for (String k : hidden) sb.append("X\t").append(k).append('\n');
        for (Rule r : rules) {
            sb.append("R\t").append(r.key).append('\t').append(r.mode).append('\t').append(r.limitMin)
                    .append('\t').append(r.always ? 1 : 0).append('\t').append(r.challenge).append('\n');
        }
        for (Group g : groups) {
            sb.append("G\t").append(g.name).append('\t').append(g.startMin).append('\t').append(g.endMin).append('\t')
                    .append(g.days).append('\t').append(g.strict ? 1 : 0).append('\t').append(g.untilMs);
            for (String k : g.apps) sb.append('\t').append(k);
            sb.append('\n');
        }
        // The activity types of a group, by its place in the list above. An older version leaves these lines out.
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (g.during.isEmpty() && !g.hide) continue;
            sb.append("GX\t").append(i).append('\t').append(g.hide ? 1 : 0);
            for (String k : g.during) sb.append('\t').append(k);
            sb.append('\n');
        }
        // How the window and the activity types combine, and the library flag, by the group's place in the list. An older version leaves these out.
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (!g.both && !g.library) continue;
            sb.append("GY\t").append(i).append('\t').append(g.both ? 1 : 0).append('\t').append(g.library ? 1 : 0).append('\n');
        }
        // The apps that count as part of an activity type. An older version leaves these lines out.
        for (java.util.Map.Entry<String, List<String>> en : typeApps.entrySet()) {
            sb.append("AU\t").append(en.getKey());
            for (String k : en.getValue()) sb.append('\t').append(k);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int num(String v, int dflt, int lo, int hi) {
        try {
            return Math.max(lo, Math.min(hi, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    /** Reads text made by {@link #serialize()}. Anything missing or damaged falls back to the default. */
    public static LauncherConfig parse(String text) {
        LauncherConfig c = new LauncherConfig();
        if (text == null) return c;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return c;
        boolean newSwipes = false;
        for (int i = 1; i < lines.length; i++) {
            String ln = lines[i];
            try {
                if (ln.startsWith("X\t")) {
                    String k = ln.substring(2);
                    if (AppKey.valid(k) && !AppKey.isAction(k)) c.hidden.add(k);
                } else if (ln.startsWith("R\t")) {
                    String[] p = ln.split("\t", 6);
                    c.setRule(p[1], num(p[2], 0, 0, 2), num(p[3], 0, 0, 1440), p.length > 4 && p[4].trim().equals("1"),
                            p.length > 5 ? num(p[5], 0, 0, PauseChallenge.WHY) : PauseChallenge.WAIT);
                } else if (ln.startsWith("G\t")) {
                    String[] p = ln.split("\t", -1);
                    if (p.length >= 7 && c.groups.size() < MAX_GROUPS) {
                        Group g = new Group(p[1]);
                        g.startMin = num(p[2], 21 * 60, 0, 1439);
                        g.endMin = num(p[3], 6 * 60, 0, 1439);
                        g.days = num(p[4], 0, 0, 127);
                        g.strict = p[5].trim().equals("1");
                        try {
                            g.untilMs = Math.max(0L, Long.parseLong(p[6].trim()));
                        } catch (NumberFormatException e) {
                            g.untilMs = 0L;
                        }
                        for (int k = 7; k < p.length; k++) {
                            if (AppKey.valid(p[k]) && !AppKey.isAction(p[k]) && !g.apps.contains(p[k]) && g.apps.size() < MAX_GROUP_APPS) g.apps.add(p[k]);
                        }
                        c.groups.add(g);
                    }
                } else if (ln.startsWith("GX\t")) {
                    String[] p = ln.split("\t", -1);
                    int gi = p.length >= 3 ? num(p[1], -1, -1, MAX_GROUPS) : -1;
                    if (gi >= 0 && gi < c.groups.size()) {
                        Group g = c.groups.get(gi);
                        g.hide = p[2].trim().equals("1");
                        for (int k = 3; k < p.length; k++) {
                            if (validDuring(p[k]) && !g.during.contains(p[k]) && g.during.size() < MAX_DURING) g.during.add(p[k]);
                        }
                    }
                } else if (ln.startsWith("GY\t")) {
                    String[] p = ln.split("\t", -1);
                    int gi = p.length >= 4 ? num(p[1], -1, -1, MAX_GROUPS) : -1;
                    if (gi >= 0 && gi < c.groups.size()) {
                        c.groups.get(gi).both = p[2].trim().equals("1");
                        c.groups.get(gi).library = p[3].trim().equals("1");
                    }
                } else if (ln.startsWith("AU\t")) {
                    String[] p = ln.split("\t", -1);
                    if (p.length >= 3 && validDuring(p[1]) && !p[1].endsWith(":*")) {
                        List<String> keys = new ArrayList<>();
                        for (int k = 2; k < p.length; k++) {
                            if (AppKey.valid(p[k]) && !AppKey.isAction(p[k]) && !keys.contains(p[k]) && keys.size() < MAX_USE_APPS) keys.add(p[k]);
                        }
                        if (!keys.isEmpty() && (c.typeApps.containsKey(p[1]) || c.typeApps.size() < MAX_USE_TYPES)) c.typeApps.put(p[1], keys);
                    }
                } else {
                    int eq = ln.indexOf('=');
                    if (eq <= 0) continue;
                    String k = ln.substring(0, eq);
                    String v = ln.substring(eq + 1);
                    switch (k) {
                        // 3 was the old "Wallpaper" look: the home screen now always shows the day poster, so it means Auto.
                        case "theme": { int t = num(v, THEME_AUTO, 0, 3); c.theme = t > THEME_LIGHT ? THEME_AUTO : t; break; }
                        case "size": c.size = num(v, 1, 0, 3); break;
                        case "autoOpen": c.autoOpen = v.trim().equals("1"); break;
                        case "kbd": c.keyboard = v.trim().equals("1"); break;
                        case "focusKinds": c.focusKinds = num(v, 1 << Routine.TASK, 0, (1 << Routine.KIND_NAMES.length) - 1); break;
                        case "pauseSec": c.pauseSec = num(v, 10, 3, 60); break;
                        case "strictSec": c.strictSec = num(v, 30, 5, 120); break;
                        case "strong": c.strong = v.trim().equals("1"); break;
                        case "trackUse": c.trackUse = v.trim().equals("1"); break;
                        case "swipes": newSwipes = true; break;
                        case "swipeLeft": c.swipeLeft = AppKey.valid(v.trim()) ? v.trim() : ""; break;
                        case "swipeRight": c.swipeRight = AppKey.valid(v.trim()) ? v.trim() : ""; break;
                        default: break;
                    }
                }
            } catch (RuntimeException ignored) {
                // skip a damaged line
            }
        }
        // Saved before the camera and phone became the defaults: nothing on the left and the dashboard on the right
        // was the old default, not a choice, so it moves to the new one.
        if (!newSwipes && c.swipeLeft.isEmpty() && AppKey.DASHBOARD.equals(c.swipeRight)) {
            c.swipeLeft = AppKey.CAMERA;
            c.swipeRight = AppKey.PHONE;
        }
        return c;
    }
}
