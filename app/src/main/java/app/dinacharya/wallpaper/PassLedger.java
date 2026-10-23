package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The video passes a person has earned, as a list. Every pass is its own entry: the items it opens (a {@link DayPass} mask), when it
 * started, when it ends, and the note of what existed when it began (see {@link PassReset}). Passes run side by side and are
 * independent: a video for strict pauses and a video for pauses for more apps are two passes with two timers, so needing both
 * for one thing (a strict pause on a third app, say) means watching one video for each, and neither opens the other.
 *
 * An item is in at most one running pass at a time: a new pass only opens the items that are not open yet, so the notes of two passes
 * never claim the same thing. Uses no Android classes.
 */
public final class PassLedger {
    /** More than this many passes are never kept (there are only {@link DayPass#LAST} items and a pass needs a new one to be given). */
    static final int MAX_ENTRIES = 32;

    /** One pass. */
    public static final class Entry {
        public int mask;
        public long from;
        public long until;
        public String note = "";

        public Entry(int mask, long from, long until, String note) {
            this.mask = mask;
            this.from = from;
            this.until = until;
            this.note = note == null ? "" : note;
        }

        Entry copy() {
            return new Entry(mask, from, until, note);
        }

        /** True when this pass opens something at {@code now}. */
        public boolean running(long now) {
            return DayPass.maskRunning(mask, from, until, now);
        }
    }

    private final List<Entry> list = new ArrayList<>();

    public PassLedger() {
    }

    public PassLedger copy() {
        PassLedger l = new PassLedger();
        for (Entry e : list) l.list.add(e.copy());
        return l;
    }

    public int size() {
        return list.size();
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(list);
    }

    // ------------------------------------------------------------------ what is open

    /** Everything the running passes open at {@code now}, as a {@link DayPass} mask. */
    public int open(long now) {
        int m = DayPass.FREE;
        for (Entry e : list) if (e.running(now)) m |= DayPass.openMask(e.mask, e.from, e.until, now);
        return m;
    }

    /** The running pass that opens {@code item}, or null. */
    public Entry covering(int item, long now) {
        for (Entry e : list) if (e.running(now) && DayPass.has(e.mask, item)) return e;
        return null;
    }

    /** The passes running at {@code now}, the oldest first. */
    public List<Entry> running(long now) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : list) if (e.running(now)) out.add(e);
        out.sort((a, b) -> Long.compare(a.from, b.from));
        return out;
    }

    /** When the first of the running passes ends, or 0 when none runs. */
    public long nextEnd(long now) {
        long best = 0;
        for (Entry e : list) if (e.running(now) && (best == 0 || e.until < best)) best = e.until;
        return best;
    }

    /** The items of {@code mask} that a new pass could open now: the ones that are not open yet. */
    public int free(int mask, long now) {
        if (!DayPass.validMask(mask)) return DayPass.FREE;
        return mask & DayPass.ALL & ~open(now);
    }

    // ------------------------------------------------------------------ giving

    /**
     * Adds a pass for the items of {@code mask} that are not open yet, lasting {@code lengthMs} from {@code now}. Returns the items it
     * opens (0 when nothing was added: nothing to open, a bad length, or the list is full). {@code note} is what to keep for putting
     * things back; the caller takes it for the items returned by {@link #free}.
     */
    public int add(int mask, long lengthMs, long now, String note) {
        if (lengthMs <= 0 || lengthMs > DayPass.MAX_MS || list.size() >= MAX_ENTRIES) return DayPass.FREE;
        int m = free(mask, now);
        if (m == 0) return DayPass.FREE;
        list.add(new Entry(m, now, now + lengthMs, note));
        return m;
    }

    /** Adds an entry as it is, without any check (for tests that move time). */
    void rawAdd(Entry e) {
        list.add(e.copy());
    }

    /** True when the running pass on {@code item} is a day long, for that item alone, and one more video can stretch it to three days. */
    public boolean canExtend(int item, long now) {
        Entry e = covering(item, now);
        return e != null && DayPass.canExtend(e.mask, e.from, e.until, now);
    }

    /** Stretches that pass to three days from when it began. Returns false when that is not possible. */
    public boolean extend(int item, long now) {
        Entry e = covering(item, now);
        if (e == null || !DayPass.canExtend(e.mask, e.from, e.until, now)) return false;
        e.until = DayPass.extendedUntil(e.from);
        return true;
    }

    // ------------------------------------------------------------------ ending

    /** The passes that are over (or not valid), the oldest first: what was made with them is still to be put back. */
    public List<Entry> ended(long now) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : list) if (!e.running(now)) out.add(e);
        out.sort((a, b) -> Long.compare(a.from, b.from));
        return out;
    }

    /** Takes the given passes out of the list. */
    public void remove(List<Entry> gone) {
        list.removeAll(gone);
    }

    /** Forgets what to put back, for every pass (what a restored backup brings is what the person chose to keep). */
    public void clearNotes() {
        for (Entry e : list) e.note = "";
    }

    /** When the first pass that still has something to put back is due (running or over), or 0 when none has. */
    public long nextNoteEnd() {
        long best = 0;
        for (Entry e : list) if (!e.note.isEmpty() && (best == 0 || e.until < best)) best = e.until;
        return best;
    }

    // ------------------------------------------------------------------ keeping

    private static final String MAGIC = "DINAPASSES1";

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unesc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    default: sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The list as text to keep. */
    public String write() {
        StringBuilder sb = new StringBuilder(MAGIC);
        for (Entry e : list) {
            sb.append('\n').append(e.mask).append('\t').append(e.from).append('\t').append(e.until).append('\t').append(esc(e.note));
        }
        return sb.toString();
    }

    /** The list from text kept by {@link #write}. Anything damaged is left out; nothing ever opens because of it. */
    public static PassLedger parse(String text) {
        PassLedger l = new PassLedger();
        if (text == null || !(text.equals(MAGIC) || text.startsWith(MAGIC + "\n"))) return l;
        String[] lines = text.split("\n", -1);
        for (int i = 1; i < lines.length && l.list.size() < MAX_ENTRIES; i++) {
            if (lines[i].isEmpty()) continue;
            String[] p = lines[i].split("\t", 4);
            if (p.length < 3) continue;
            try {
                int mask = Integer.parseInt(p[0]);
                long from = Long.parseLong(p[1]);
                long until = Long.parseLong(p[2]);
                if (!DayPass.validMask(mask)) continue;
                l.list.add(new Entry(mask, from, until, p.length > 3 ? unesc(p[3]) : ""));
            } catch (NumberFormatException ignored) {
                // a damaged line opens nothing
            }
        }
        return l;
    }

    /** The list for a record from before 2.8, which held one pass in separate values. */
    public static PassLedger fromOnePass(int mask, long from, long until, String note) {
        PassLedger l = new PassLedger();
        if (DayPass.validMask(mask)) l.list.add(new Entry(mask, from, until, note));
        return l;
    }
}
