package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Temporary changes to the schedule. They belong to one calendar date each and are always applied
 * on top of the saved routine, never on top of each other, so a bad night cannot carry into the
 * days after it. Uses no Android classes.
 *
 * The signature ties the changes to the saved routine they were made for. When the routine is
 * edited in the app (a permanent change) the signature no longer matches and the temporary
 * changes are dropped.
 */
public final class Overrides {
    /** The whole morning moves later; bedtime stays where it was. */
    public static final int WHOLE = 1;
    /** The morning starts later and the slots covered by the extra sleep are dropped. */
    public static final int SKIP = 2;

    private static final String MAGIC = "DINAOV1";

    /** A later start for one day. */
    public static final class Shift {
        public final int type;
        public final int mins;

        Shift(int type, int mins) {
            this.type = type;
            this.mins = mins;
        }
    }

    /** A rename or a "take it off" for one slot on one day. */
    public static final class Edit {
        public final String day;
        public final int src;
        public final String orig;
        public final boolean skip;
        public final String name;

        Edit(String day, int src, String orig, boolean skip, String name) {
            this.day = day;
            this.src = src;
            this.orig = orig;
            this.skip = skip;
            this.name = name;
        }
    }

    public String sig = "";
    public final Map<String, Shift> shifts = new TreeMap<>();
    public final List<Edit> edits = new ArrayList<>();
    /** Days that began before their time because the person woke early and started the day: the minutes earlier, by date. */
    public final Map<String, Integer> wakes = new TreeMap<>();

    /** How many minutes earlier than planned the day that starts on {@code d} was started (0 for none). */
    public int earlyFor(LocalDate d) {
        Integer m = wakes.get(d.toString());
        return m == null ? 0 : Math.max(0, m);
    }

    /** Starts the day of {@code d} that many minutes early ("Early wake"); 0 or less takes it back. */
    public void setEarly(LocalDate d, int mins) {
        if (mins <= 0) wakes.remove(d.toString());
        else wakes.put(d.toString(), Math.min(mins, 720));
    }

    public Shift shiftFor(LocalDate d) {
        Shift s = shifts.get(d.toString());
        return (s != null && s.mins > 0) ? s : null;
    }

    public void setShift(LocalDate d, int type, int mins) {
        if (mins <= 0 || (type != WHOLE && type != SKIP)) shifts.remove(d.toString());
        else shifts.put(d.toString(), new Shift(type, Math.min(mins, 720)));
    }

    /** Sets the change for one slot of one day. A second change to the same slot replaces the first. */
    public void setEdit(LocalDate d, int src, String orig, boolean skip, String name) {
        String key = d.toString();
        for (int i = edits.size() - 1; i >= 0; i--) {
            Edit e = edits.get(i);
            if (e.day.equals(key) && e.src == src) edits.remove(i);
        }
        edits.add(new Edit(key, src, Routine.clean(orig), skip, Routine.clean(name == null ? "" : name)));
    }

    public boolean isEmpty() {
        return shifts.isEmpty() && edits.isEmpty() && wakes.isEmpty();
    }

    public int editCount(LocalDate d) {
        int n = 0;
        for (Edit e : edits) if (e.day.equals(d.toString())) n++;
        return n;
    }

    /** Forgets everything for one day's slots (the wake-up shift stays). */
    public void clearEdits(LocalDate d) {
        for (int i = edits.size() - 1; i >= 0; i--) {
            if (edits.get(i).day.equals(d.toString())) edits.remove(i);
        }
    }

    /** Drops changes for days that are over. */
    public void prune(LocalDate today) {
        String oldest = today.minusDays(3).toString();
        shifts.keySet().removeIf(k -> k.compareTo(oldest) < 0);
        wakes.keySet().removeIf(k -> k.compareTo(oldest) < 0);
        edits.removeIf(e -> e.day.compareTo(oldest) < 0);
    }

    public Overrides copy() {
        Overrides o = new Overrides();
        o.sig = sig;
        for (Map.Entry<String, Shift> e : shifts.entrySet()) o.shifts.put(e.getKey(), e.getValue());
        o.edits.addAll(edits);
        o.wakes.putAll(wakes);
        return o;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        sb.append("sig=").append(sig).append('\n');
        for (Map.Entry<String, Shift> e : shifts.entrySet()) {
            sb.append("S\t").append(e.getKey()).append('\t').append(e.getValue().type).append('\t')
                    .append(e.getValue().mins).append('\n');
        }
        for (Map.Entry<String, Integer> e : wakes.entrySet()) {
            sb.append("W\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        for (Edit e : edits) {
            sb.append("E\t").append(e.day).append('\t').append(e.src).append('\t').append(e.skip ? 1 : 0).append('\t')
                    .append(e.orig).append('\t').append(e.name).append('\n');
        }
        return sb.toString();
    }

    /** Reads text made by {@link #serialize()}. Anything unreadable gives an empty set of changes. */
    public static Overrides parse(String text) {
        Overrides o = new Overrides();
        if (text == null) return o;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return o;
        for (int i = 1; i < lines.length; i++) {
            String ln = lines[i];
            try {
                if (ln.startsWith("sig=")) {
                    o.sig = ln.substring(4).trim();
                } else if (ln.startsWith("S\t")) {
                    String[] p = ln.split("\t", 4);
                    LocalDate.parse(p[1]);
                    o.shifts.put(p[1], new Shift(Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())));
                } else if (ln.startsWith("W\t")) {
                    String[] p = ln.split("\t", 3);
                    LocalDate.parse(p[1]);
                    int m = Integer.parseInt(p[2].trim());
                    if (m > 0) o.wakes.put(p[1], Math.min(m, 720));
                } else if (ln.startsWith("E\t")) {
                    String[] p = ln.split("\t", 6);
                    LocalDate.parse(p[1]);
                    o.edits.add(new Edit(p[1], Integer.parseInt(p[2].trim()), p[4], p[3].trim().equals("1"),
                            p.length > 5 ? p[5] : ""));
                }
            } catch (RuntimeException ignored) {
                // skip a damaged line
            }
        }
        return o;
    }
}
