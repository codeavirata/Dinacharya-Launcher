package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What happens to the things made with a video pass ({@link DayPass}) when it is over: they go back to the free version.
 * A pass opens one item (or, for three videos, the items of one group) for a day or three days, so only what those items made
 * possible is taken back. At the start the phone takes a note of what already existed ({@link #captureMask}); when the pass
 * has ended, whatever was added or switched on with the items is removed or set back ({@link #undoLauncher},
 * {@link #undoRoutine}) and everything that was there before stays exactly as it was. A pass for a whole group is put back
 * item by item, in the same order, with the same rules as a pass for the single item.
 *
 * What goes back, per item:
 *   Pauses for more apps   - pause rules beyond the free number of apps that were made during the pass are removed.
 *   Strict pauses          - a pause that was made strict goes back to the short pause.
 *   Daily limits           - a daily limit that was set goes back to what it was (none, for a new rule).
 *   Playful pauses         - a sum, sentence, breathing or "say why" pause goes back to the plain wait.
 *   More than one lock     - locks beyond the free one that were made during the pass are removed.
 *   More apps in a lock    - a lock holds the free number of apps again (or the number it held before).
 *   Strict locks           - a lock that was made strict is no longer strict.
 *   Routines               - the weekday, weekend and other routines made are removed (those days use the main routine).
 *   Goals                  - the goals added are removed.
 *   A colour look          - that look goes back to the one used before.
 *   Saved days, week view  - nothing is stored, so nothing needs to go back.
 *
 * Choices that the free version allows are never touched. Having Pro keeps everything (the caller then does not
 * reset at all). Uses no Android classes.
 */
public final class PassReset {
    private static final String MAGIC = "DINAPASS3";
    /** The note of a pass for one item, as earlier versions kept it. Still read, so a pass running during an update ends properly. */
    private static final String MAGIC_ONE = "DINAPASS2";

    private PassReset() {
    }

    private static boolean isPause(int item) {
        return item >= DayPass.PAUSE_APPS && item <= DayPass.PAUSE_PLAYFUL;
    }

    private static boolean isLock(int item) {
        return item >= DayPass.LOCK_MORE && item <= DayPass.LOCK_STRICT;
    }

    private static boolean isLook(int item) {
        return DayPass.lookOf(item) > 0;
    }

    /** True for the items whose settings live in the home-screen settings. */
    public static boolean touchesLauncher(int item) {
        return isPause(item) || isLock(item);
    }

    /** True for the items whose settings live in the routine. */
    public static boolean touchesRoutine(int item) {
        return item == DayPass.ROUTINES || item == DayPass.GOALS || isLook(item);
    }

    /** True when a pass for this item can leave something behind that has to be put back. */
    public static boolean needsReset(int item) {
        return touchesLauncher(item) || touchesRoutine(item);
    }

    /** The items that have something to put back. */
    private static int resetItems() {
        int m = 0;
        for (int i = DayPass.FIRST; i <= DayPass.LAST; i++) if (needsReset(i)) m |= DayPass.bit(i);
        return m;
    }

    /** True when a pass on these items can leave something behind that has to be put back. */
    public static boolean needsResetMask(int mask) {
        return (mask & resetItems()) != 0;
    }

    /** True when any of the items keeps its settings in the home-screen settings. */
    public static boolean touchesLauncherMask(int mask) {
        for (int i = DayPass.FIRST; i <= DayPass.LAST; i++) if (DayPass.has(mask, i) && touchesLauncher(i)) return true;
        return false;
    }

    /** True when any of the items keeps its settings in the routine. */
    public static boolean touchesRoutineMask(int mask) {
        for (int i = DayPass.FIRST; i <= DayPass.LAST; i++) if (DayPass.has(mask, i) && touchesRoutine(i)) return true;
        return false;
    }

    private static boolean anyPause(int mask) {
        for (int i = DayPass.PAUSE_APPS; i <= DayPass.PAUSE_PLAYFUL; i++) if (DayPass.has(mask, i)) return true;
        return false;
    }

    private static boolean anyLock(int mask) {
        for (int i = DayPass.LOCK_MORE; i <= DayPass.LOCK_STRICT; i++) if (DayPass.has(mask, i)) return true;
        return false;
    }

    private static boolean anyLook(int mask) {
        for (int i = DayPass.LOOK_SUNSET; i <= DayPass.LOOK_GRAPHITE; i++) if (DayPass.has(mask, i)) return true;
        return false;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // ------------------------------------------------------------------ the note taken at the start

    /**
     * What exists now for one {@code item}, as text to keep until the pass has ended. Empty for an item with nothing
     * to put back.
     */
    public static String capture(int item, LauncherConfig cfg, Routine r) {
        return captureMask(DayPass.bit(item), cfg, r);
    }

    /**
     * What exists now for the items in {@code mask}, as text to keep until the pass has ended. Empty when none of the
     * items has anything to put back.
     */
    public static String captureMask(int mask, LauncherConfig cfg, Routine r) {
        int m = mask & resetItems();
        if (m == 0) return "";
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        sb.append("M\t").append(m).append('\n');
        if (anyPause(m)) {
            for (LauncherConfig.Rule x : cfg.rules) {
                sb.append("R\t").append(x.key).append('\t').append(x.mode).append('\t').append(x.limitMin).append('\t')
                        .append(x.always ? 1 : 0).append('\t').append(x.challenge).append('\n');
            }
        }
        if (anyLock(m)) {
            for (LauncherConfig.Group g : cfg.groups) {
                sb.append("G\t").append(clean(g.name)).append('\t').append(g.strict ? 1 : 0).append('\t').append(g.apps.size());
                for (String a : g.apps) sb.append('\t').append(a);
                sb.append('\n');
            }
        }
        if (DayPass.has(m, DayPass.ROUTINES)) {
            for (Routine o : r.others) sb.append("O\t").append(clean(o.name)).append('\n');
            for (Routine t : r.temps) if (t.tempDate != null) sb.append("T\t").append(t.tempDate).append('\n');
        }
        if (DayPass.has(m, DayPass.GOALS)) {
            for (Routine.Goal g : r.goals) sb.append("L\t").append(g.kind).append('\t').append(clean(g.sub)).append('\t').append(g.repeats() ? "R" : "O" + g.date).append('\n');
        }
        if (anyLook(m)) {
            sb.append("P\t").append(r.palette).append('\n');
        }
        return sb.toString();
    }

    /** The first item a note was taken for, or {@link DayPass#NONE} when the note is empty or damaged. */
    public static int feature(String note) {
        return DayPass.firstItem(maskOf(note));
    }

    /** Every item a note was taken for (0 when the note is empty or damaged). */
    public static int maskOf(String note) {
        List<String[]> rows = rows(note);
        if (rows == null) return 0;
        boolean old = note.startsWith(MAGIC_ONE + "\n");
        for (String[] p : rows) {
            if (p.length < 2) continue;
            if (!old && p[0].equals("M")) {
                try {
                    int m = Integer.parseInt(p[1]);
                    return DayPass.validMask(m) ? m & resetItems() : 0;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            if (old && p[0].equals("F")) {
                try {
                    int f = Integer.parseInt(p[1]);
                    return needsReset(f) ? DayPass.bit(f) : 0;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** The note's lines split at tabs, or null when it is not a note. */
    private static List<String[]> rows(String note) {
        if (note == null || !(note.startsWith(MAGIC + "\n") || note.startsWith(MAGIC_ONE + "\n"))) return null;
        List<String[]> out = new ArrayList<>();
        String[] lines = note.split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            out.add(lines[i].split("\t", -1));
        }
        return out;
    }

    private static int num(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ putting things back

    /**
     * Puts the home-screen settings (pauses, locks) back after a pause or lock pass. Returns how many things were
     * removed or set back; 0 means nothing changed (or the note is no use).
     */
    public static int undoLauncher(String note, LauncherConfig cfg) {
        int m = maskOf(note);
        if (m == 0) return 0;
        List<String[]> rows = rows(note);
        int changes = 0;
        // In the order of the items: more apps first (it removes rules), then what each rule was made with.
        for (int i = DayPass.FIRST; i <= DayPass.LAST; i++) {
            if (!DayPass.has(m, i)) continue;
            if (isPause(i)) changes += undoPauses(i, rows, cfg);
            else if (isLock(i)) changes += undoLocks(i, rows, cfg);
        }
        return changes;
    }

    /**
     * Puts the routine's settings (weekday and weekend routines, goals, the colour look) back after a routines, goals or
     * looks pass. Returns how many things were removed or set back.
     */
    public static int undoRoutine(String note, Routine r) {
        int m = maskOf(note);
        if (m == 0) return 0;
        List<String[]> rows = rows(note);
        int changes = 0;
        for (int i = DayPass.FIRST; i <= DayPass.LAST; i++) {
            if (!DayPass.has(m, i)) continue;
            if (i == DayPass.ROUTINES) changes += undoRoutines(rows, r);
            else if (i == DayPass.GOALS) changes += undoGoals(rows, r);
            else if (isLook(i)) changes += undoLook(i, rows, r);
        }
        return changes;
    }

    private static int undoPauses(int item, List<String[]> rows, LauncherConfig cfg) {
        Map<String, int[]> before = new HashMap<>();
        for (String[] p : rows) {
            if (p[0].equals("R") && p.length >= 6) {
                before.put(p[1], new int[]{num(p[2], 0), num(p[3], 0), num(p[4], 0), num(p[5], PauseChallenge.WAIT)});
            }
        }
        int changes = 0;
        int pos = 0;   // the place the rule has in the list once the ones before it are dealt with
        for (LauncherConfig.Rule r : new ArrayList<>(cfg.rules)) {
            int[] b = before.get(r.key);
            int mode = r.mode, limit = r.limitMin, ch = r.challenge;
            if (item == DayPass.PAUSE_APPS) {
                if (b == null && pos >= FocusRules.FREE_APPS) {   // a rule the free version would not keep
                    cfg.removeRule(r.key);
                    changes++;
                    continue;
                }
            } else if (item == DayPass.PAUSE_STRICT) {
                int was = b == null ? LauncherConfig.RULE_NUDGE : b[0];
                if (mode == LauncherConfig.RULE_STRICT && was != LauncherConfig.RULE_STRICT) mode = was;
            } else if (item == DayPass.PAUSE_LIMIT) {
                int was = b == null ? 0 : b[1];
                if (limit > 0 && limit != was) limit = was;
            } else if (item == DayPass.PAUSE_PLAYFUL) {
                int was = b == null ? PauseChallenge.WAIT : b[3];
                if (ch != PauseChallenge.WAIT && ch != was) ch = was;
            }
            if (mode != r.mode || limit != r.limitMin || ch != r.challenge) {
                changes++;
                if (cfg.setRule(r.key, mode, limit, r.always, ch) == null) continue;   // left doing nothing, so it was removed
            }
            pos++;
        }
        return changes;
    }

    private static int undoLocks(int item, List<String[]> rows, LauncherConfig cfg) {
        List<String[]> before = new ArrayList<>();
        for (String[] p : rows) if (p[0].equals("G") && p.length >= 4) before.add(p);
        boolean[] used = new boolean[before.size()];
        int changes = 0;
        List<LauncherConfig.Group> now = new ArrayList<>(cfg.groups);
        for (int i = 0; i < now.size(); i++) {
            LauncherConfig.Group g = now.get(i);
            String[] b = null;
            for (int k = 0; k < before.size(); k++) {
                if (!used[k] && before.get(k)[1].equals(clean(g.name))) {
                    used[k] = true;
                    b = before.get(k);
                    break;
                }
            }
            if (item == DayPass.LOCK_MORE) {
                if (b == null && i >= LockRules.FREE_GROUPS) {   // a lock the free version would not keep
                    cfg.removeGroup(g);
                    changes++;
                }
            } else if (item == DayPass.LOCK_APPS) {
                int allowed = b == null ? LockRules.FREE_GROUP_APPS : Math.max(LockRules.FREE_GROUP_APPS, num(b[3], 0));
                boolean changed = false;
                while (g.apps.size() > allowed) {
                    g.apps.remove(g.apps.size() - 1);
                    changed = true;
                }
                if (changed) changes++;
            } else if (item == DayPass.LOCK_STRICT) {
                boolean wasStrict = b != null && b[2].equals("1");
                if (g.strict && !wasStrict) {
                    g.strict = false;
                    changes++;
                }
            }
        }
        return changes;
    }

    private static int undoRoutines(List<String[]> rows, Routine r) {
        // The names of the routines there were before; a name can be used twice, so each is used up once.
        Map<String, Integer> before = new HashMap<>();
        for (String[] p : rows) {
            if (p[0].equals("O") && p.length >= 2) before.merge(p[1], 1, Integer::sum);
        }
        List<Integer> added = new ArrayList<>();
        for (int k = 0; k < r.others.size(); k++) {
            String n = clean(r.others.get(k).name);
            Integer left = before.get(n);
            if (left != null && left > 0) before.put(n, left - 1);
            else added.add(k + 1);   // routine numbers start at 1 (0 is the main one)
        }
        for (int i = added.size() - 1; i >= 0; i--) r.removeOther(added.get(i));   // from the end, so the numbers stay right
        // Temporary routines made during the pass go too (those that were there before are kept).
        Set<String> tempsBefore = new HashSet<>();
        for (String[] p : rows) {
            if (p[0].equals("T") && p.length >= 2) tempsBefore.add(p[1]);
        }
        int gone = 0;
        for (int k = r.temps.size() - 1; k >= 0; k--) {
            if (r.temps.get(k).tempDate != null && !tempsBefore.contains(r.temps.get(k).tempDate.toString())) {
                r.temps.remove(k);
                gone++;
            }
        }
        return added.size() + gone;
    }

    private static int undoGoals(List<String[]> rows, Routine r) {
        Set<String> before = new HashSet<>();
        for (String[] p : rows) {
            if (p[0].equals("L") && p.length >= 3) before.add(goalKey(num(p[1], -1), p[2]) + "|" + (p.length >= 4 ? p[3] : "R"));
        }
        int changes = 0;
        for (Routine.Goal g : new ArrayList<>(r.goals)) {
            if (!before.contains(goalKey(g.kind, g.sub) + "|" + (g.repeats() ? "R" : "O" + g.date))) {
                r.goals.remove(g);
                changes++;
            }
        }
        return changes;
    }

    private static String goalKey(int kind, String sub) {
        return kind + "|" + clean(sub).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int undoLook(int item, List<String[]> rows, Routine r) {
        int was = -1;
        for (String[] p : rows) {
            if (p[0].equals("P") && p.length >= 2) was = num(p[1], -1);
        }
        if (was < 0 || was >= Routine.PALETTE_NAMES.length) return 0;
        // Only the look this pass opened goes back; the free look (Teal) and any other look are left as they are.
        if (r.palette != DayPass.lookOf(item) || r.palette == was) return 0;
        r.palette = was;
        return 1;
    }

    // ------------------------------------------------------------------ telling the person

    /**
     * What to say once the pass for one {@code item} is over and {@code changes} things went back (empty when nothing did).
     */
    public static String message(int item, int changes) {
        return messageMask(DayPass.bit(item), changes);
    }

    /** The same for a pass on the items in {@code mask}. */
    public static String messageMask(int mask, int changes) {
        if (changes <= 0 || !needsResetMask(mask)) return "";
        return "Your pass for " + DayPass.maskName(mask) + " has ended. "
                + (changes == 1 ? "1 change" : changes + " changes") + " you made with it went back to the free version. "
                + "What you had before is untouched. Dinacharya Pro keeps such things for good.";
    }
}
