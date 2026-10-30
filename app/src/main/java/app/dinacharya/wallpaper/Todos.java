package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small to-do lists that belong to one day: one for a slot of that day's routine, and one for a goal. They show on the home screen
 * while the slot (or a slot of the goal's type) is going on. A list does not carry over to another day, unless an item was given a
 * repeat: then a copy of it is put on each day it repeats on (every day, every other day, or chosen weekdays), ticked fresh each
 * day. Uses no Android classes.
 */
public final class Todos {
    public static final int MAX_ITEMS = 30;
    public static final int MAX_LEN = 80;
    /** How many repeating items can be kept. */
    public static final int MAX_REPEATS = 20;

    /** How an item repeats: not at all, every day, every second day from the day it was made, or on chosen weekdays (bit 0 is Monday). */
    public static final int NO_REPEAT = 0, EVERY_DAY = 1, EVERY_OTHER_DAY = 2, CHOSEN_DAYS = 3;

    /** One thing to do. */
    public static final class Item {
        public final String text;
        public boolean done;
        /** The repeating item this came from (see {@link Repeat}), or 0 when it does not repeat. */
        public int repeatId;
        /** How far in the row is drawn when it is shown in a list: 0, or 1 for a thing on a goal's own to-do list. */
        public int level;
        /** True for a goal that has to-dos of its own: it is not ticked itself, it is done when everything under it is. */
        public boolean head;

        Item(String text, boolean done) {
            this.text = text;
            this.done = done;
        }

        /** A row made for showing (see {@link SlotTodo}); it is not part of any list. */
        public Item(String text, boolean done, int level, boolean head) {
            this.text = text;
            this.done = done;
            this.level = level;
            this.head = head;
        }
    }

    /** A thing to do that comes back: where it goes, what it says and on which days. */
    public static final class Repeat {
        public final int id;
        public final int mode;
        public final int daysMask;
        /** The day it was made: the first day, and the day every-other-day counts from. */
        public final LocalDate start;
        /** A goal's list ({@link #goalScope}) or the lists of the slots with a given name ({@link #slotNameScope}). */
        public final String scope;
        public final String text;

        Repeat(int id, int mode, int daysMask, LocalDate start, String scope, String text) {
            this.id = id;
            this.mode = mode;
            this.daysMask = daysMask & 0x7F;
            this.start = start;
            this.scope = scope;
            this.text = text;
        }

        /** True when the item belongs on {@code day}. */
        public boolean matches(LocalDate day) {
            if (day == null || start == null || day.isBefore(start)) return false;
            switch (mode) {
                case EVERY_DAY:
                    return true;
                case EVERY_OTHER_DAY:
                    return java.time.temporal.ChronoUnit.DAYS.between(start, day) % 2L == 0L;
                case CHOSEN_DAYS:
                    return ((daysMask >> (day.getDayOfWeek().getValue() - 1)) & 1) != 0;
                default:
                    return false;
            }
        }

        /** "Every day", "Every other day" or "Mon, Wed, Fri". */
        public String ruleText() {
            return Todos.ruleText(mode, daysMask);
        }
    }

    /** The words for a repeat rule, for lists and buttons. */
    public static String ruleText(int mode, int daysMask) {
        switch (mode) {
            case EVERY_DAY:
                return "Every day";
            case EVERY_OTHER_DAY:
                return "Every other day";
            case CHOSEN_DAYS:
                StringBuilder sb = new StringBuilder();
                for (int d = 0; d < 7; d++) {
                    if (((daysMask >> d) & 1) == 0) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(Routine.DAY_SHORT[d]);
                }
                return sb.length() == 0 ? "No days" : sb.toString();
            default:
                return "Does not repeat";
        }
    }

    /**
     * What was ticked on a goal's side, by day: "day, goal, text". The text is a thing on the goal's own to-do list; an empty text is
     * the goal itself (a goal with no to-dos is ticked as a whole). Kept apart from the day's lists, because the things themselves
     * live with the goal and come back every day it counts.
     */
    private final java.util.Set<String> goalDone = new java.util.HashSet<>();
    private final Map<String, List<Item>> lists = new LinkedHashMap<>();
    private final Map<Integer, Repeat> repeats = new LinkedHashMap<>();
    /** Which days already got their copy of which repeating item (so one that was removed for the day does not come back). */
    private final java.util.Set<String> made = new java.util.HashSet<>();

    /** The scope of the list of one slot of the day's routine. */
    public static String slotScope(int src) {
        return "S" + src;
    }

    /** The scope of the list of the goal for a type. */
    public static String goalScope(int kind, String sub) {
        return "G" + kind + ":" + Routine.cleanSub(sub).toLowerCase(Locale.ROOT);
    }

    /** The name a goal's ticks are kept under: its slot (or, for an older goal, its type). */
    public static String goalTickScope(Routine.Goal g) {
        return "K" + g.ident();
    }

    private static String tickKey(LocalDate day, Routine.Goal g, String text) {
        return day + "\t" + goalTickScope(g) + "\t" + (text == null ? "" : cleanText(text).toLowerCase(Locale.ROOT));
    }

    /** True when the goal itself (one with no to-dos), or the thing {@code text} on its to-do list, is ticked on {@code day}. */
    public boolean goalDone(LocalDate day, Routine.Goal g, String text) {
        return goalDone.contains(tickKey(day, g, text));
    }

    /** Ticks or unticks the goal itself ({@code text} empty) or a thing on its to-do list. */
    public void setGoalDone(LocalDate day, Routine.Goal g, String text, boolean done) {
        String k = tickKey(day, g, text);
        if (done) goalDone.add(k);
        else goalDone.remove(k);
    }

    /** How many things on the goal's to-do list are ticked on {@code day}. */
    public int goalDoneCount(LocalDate day, Routine.Goal g) {
        int n = 0;
        for (String t : g.todos) if (goalDone(day, g, t)) n++;
        return n;
    }

    /** The scope of a repeating item of a slot: it goes to the first slot of the day with this kind and name. */
    public static String slotNameScope(int kind, String title) {
        return "T" + kind + ":" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
    }

    private static String key(LocalDate day, String scope) {
        return day + "\t" + scope;
    }

    /** One copy of a repeating item a day at most. */
    private static String madeKey(LocalDate day, int id) {
        return day + "\t" + id;
    }

    static String cleanText(String t) {
        if (t == null) return "";
        String c = t.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return c.length() > MAX_LEN ? c.substring(0, MAX_LEN).trim() : c;
    }

    /** The items of a list, in the order they were added (empty when there are none). The list is a copy. */
    public List<Item> items(LocalDate day, String scope) {
        List<Item> l = lists.get(key(day, scope));
        return l == null ? new ArrayList<Item>() : new ArrayList<>(l);
    }

    public int count(LocalDate day, String scope) {
        List<Item> l = lists.get(key(day, scope));
        return l == null ? 0 : l.size();
    }

    public int doneCount(LocalDate day, String scope) {
        int n = 0;
        List<Item> l = lists.get(key(day, scope));
        if (l != null) for (Item i : l) if (i.done) n++;
        return n;
    }

    /** Adds an item to the end; returns its place, or -1 when the text is empty or the list is full. */
    public int add(LocalDate day, String scope, String text) {
        String t = cleanText(text);
        if (t.isEmpty()) return -1;
        List<Item> l = lists.computeIfAbsent(key(day, scope), k -> new ArrayList<>());
        if (l.size() >= MAX_ITEMS) return -1;
        l.add(new Item(t, false));
        return l.size() - 1;
    }

    /**
     * Adds an item to {@code scope} on {@code day} that also comes back on other days. {@code repeatScope} says where the copies go (a
     * goal's scope, or {@link #slotNameScope}). Returns the item's place, or -1 when it could not be added.
     */
    public int addRepeating(LocalDate day, String scope, String repeatScope, String text, int mode, int daysMask) {
        if (mode == NO_REPEAT || repeatScope == null || repeatScope.isEmpty()) return add(day, scope, text);
        if (mode == CHOSEN_DAYS && (daysMask & 0x7F) == 0) return add(day, scope, text);
        String t = cleanText(text);
        if (t.isEmpty() || repeats.size() >= MAX_REPEATS) return -1;
        List<Item> l = lists.computeIfAbsent(key(day, scope), k -> new ArrayList<>());
        if (l.size() >= MAX_ITEMS) {
            if (l.isEmpty()) lists.remove(key(day, scope));
            return -1;
        }
        int id = nextRepeatId();
        repeats.put(id, new Repeat(id, mode, daysMask, day, repeatScope, t));
        Item it = new Item(t, false);
        it.repeatId = id;
        l.add(it);
        made.add(madeKey(day, id));
        return l.size() - 1;
    }

    private int nextRepeatId() {
        int max = 0;
        for (int id : repeats.keySet()) max = Math.max(max, id);
        return max + 1;
    }

    public Repeat repeatOf(int id) {
        return repeats.get(id);
    }

    public int repeatCount() {
        return repeats.size();
    }

    /** Stops an item coming back. The copies already on days stay, as plain items. */
    public void stopRepeat(int id) {
        repeats.remove(id);
        for (List<Item> l : lists.values()) for (Item i : l) if (i.repeatId == id) i.repeatId = 0;
        for (java.util.Iterator<String> it = made.iterator(); it.hasNext(); ) {
            String k = it.next();
            String[] p = k.split("\t", 2);
            if (p.length == 2 && p[1].equals(String.valueOf(id))) it.remove();
        }
    }

    /**
     * Puts the repeating items that belong on {@code day} into that day's lists, once each. {@code blocks} are the day's slots (null or empty:
     * only the goals' items are put in). Returns true when something was added, so the caller can save.
     */
    public boolean materialize(LocalDate day, List<Routine.Block> blocks) {
        boolean any = false;
        for (Repeat r : new ArrayList<>(repeats.values())) {
            if (!r.matches(day)) continue;
            String target = targetFor(r, blocks);
            if (target == null) continue;
            String mk = madeKey(day, r.id);
            if (made.contains(mk)) continue;
            List<Item> l = lists.computeIfAbsent(key(day, target), k -> new ArrayList<>());
            if (l.size() >= MAX_ITEMS) {
                if (l.isEmpty()) lists.remove(key(day, target));
                continue;
            }
            Item it = new Item(r.text, false);
            it.repeatId = r.id;
            l.add(it);
            made.add(mk);
            any = true;
        }
        return any;
    }

    private static String targetFor(Repeat r, List<Routine.Block> blocks) {
        if (r.scope.startsWith("G")) return r.scope;
        if (blocks == null) return null;
        for (Routine.Block b : blocks) {
            if (b.src < 0 || b.skipped) continue;
            if (slotNameScope(b.kind, b.title).equals(r.scope)) return slotScope(b.src);
        }
        return null;
    }

    public void setDone(LocalDate day, String scope, int index, boolean done) {
        List<Item> l = lists.get(key(day, scope));
        if (l != null && index >= 0 && index < l.size()) l.get(index).done = done;
    }

    public void remove(LocalDate day, String scope, int index) {
        String k = key(day, scope);
        List<Item> l = lists.get(k);
        if (l == null || index < 0 || index >= l.size()) return;
        l.remove(index);
        if (l.isEmpty()) lists.remove(k);
    }

    public void clear(LocalDate day, String scope) {
        lists.remove(key(day, scope));
    }

    /** Forgets the lists of days more than a day past (the day that began yesterday may still be running). */
    public boolean prune(LocalDate today) {
        LocalDate oldest = today.minusDays(1);
        boolean any = false;
        for (java.util.Iterator<String> it = lists.keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            try {
                if (LocalDate.parse(k.substring(0, k.indexOf('\t'))).isBefore(oldest)) {
                    it.remove();
                    any = true;
                }
            } catch (RuntimeException e) {
                it.remove();
                any = true;
            }
        }
        for (java.util.Iterator<String> it = made.iterator(); it.hasNext(); ) {
            String k = it.next();
            try {
                if (LocalDate.parse(k.substring(0, k.indexOf('\t'))).isBefore(oldest)) {
                    it.remove();
                    any = true;
                }
            } catch (RuntimeException e) {
                it.remove();
                any = true;
            }
        }
        for (java.util.Iterator<String> it = goalDone.iterator(); it.hasNext(); ) {
            String k = it.next();
            try {
                if (LocalDate.parse(k.substring(0, k.indexOf('\t'))).isBefore(oldest)) {
                    it.remove();
                    any = true;
                }
            } catch (RuntimeException e) {
                it.remove();
                any = true;
            }
        }
        return any;
    }

    /** True when there is nothing at all: no lists, nothing that repeats and no goal ticks. */
    public boolean isEmpty() {
        return lists.isEmpty() && repeats.isEmpty() && goalDone.isEmpty();
    }

    /**
     * What the home screen shows while a slot is going on: the slot's own items, then the items of each goal that counts today for
     * the slot's type.
     */
    public List<Item> forSlot(LocalDate day, int src, int kind, String sub, List<Routine.Goal> goalsToday) {
        List<Item> out = new ArrayList<>(items(day, slotScope(src)));
        if (goalsToday != null) {
            for (Routine.Goal g : goalsToday) {
                if (g.kind != kind) continue;
                if (!g.sub.isEmpty() && !g.sub.equalsIgnoreCase(sub == null ? "" : sub)) continue;
                out.addAll(items(day, goalScope(g.kind, g.sub)));
            }
        }
        return out;
    }

    /** How long each page of a long list stays on the home screen before it moves on to the next. */
    public static final long PAGE_MS = 8000L;

    /**
     * Where to start drawing when only {@code lines} fit. First the finished items at the top scroll up and out, so the next things to
     * do stay in view. When what is left is still more than fits, the window moves down through it one page at a time, a new page
     * for each {@code tick} (see {@link #PAGE_MS}), so every item gets its turn. When everything is finished the last ones are shown.
     */
    public static int windowStart(List<Item> items, int lines, long tick) {
        int n = items.size();
        if (lines <= 0 || n <= lines) return 0;
        int firstPending = n;
        for (int i = 0; i < n; i++) {
            if (!items.get(i).done) {
                firstPending = i;
                break;
            }
        }
        if (firstPending >= n) return n - lines;
        int remaining = n - firstPending;
        if (remaining <= lines) return n - lines;
        int pages = (remaining + lines - 1) / lines;
        int page = (int) Math.floorMod(tick, (long) pages);
        return Math.min(firstPending + page * lines, n - lines);
    }

    /** The first window (no paging yet). */
    public static int windowStart(List<Item> items, int lines) {
        return windowStart(items, lines, 0L);
    }

    /** How many pages the list takes when only {@code lines} fit once the finished items at the top are gone (1 when it all fits). */
    public static int pageCount(List<Item> items, int lines) {
        int n = items.size();
        if (lines <= 0 || n <= lines) return 1;
        int firstPending = n;
        for (int i = 0; i < n; i++) {
            if (!items.get(i).done) {
                firstPending = i;
                break;
            }
        }
        int remaining = n - firstPending;
        if (firstPending >= n || remaining <= lines) return 1;
        return (remaining + lines - 1) / lines;
    }

    // ------------------------------------------------------------------ saving

    /*
     * One line each. An item: day, scope, done (1 or 0, then "@" and its repeat number when it has one), text. A repeating item:
     * "R", number, mode, weekdays, first day, scope, text. A day that already got its copy: "M", day, number. A goal's tick: "K", day, goal,
     * text. Lines this version does not
     * know are skipped, and so are the new kinds of line by an older version.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Repeat r : repeats.values()) {
            sb.append("R\t").append(r.id).append('\t').append(r.mode).append('\t').append(r.daysMask).append('\t').append(r.start)
                    .append('\t').append(r.scope).append('\t').append(r.text).append('\n');
        }
        for (String m : made) sb.append("M\t").append(m).append('\n');
        for (String k : goalDone) sb.append("K\t").append(k).append('\n');
        for (Map.Entry<String, List<Item>> e : lists.entrySet()) {
            for (Item i : e.getValue()) {
                sb.append(e.getKey()).append('\t').append(i.done ? 1 : 0);
                if (i.repeatId > 0) sb.append('@').append(i.repeatId);
                sb.append('\t').append(i.text).append('\n');
            }
        }
        return sb.toString();
    }

    public static Todos parse(String s) {
        Todos t = new Todos();
        if (s == null || s.isEmpty()) return t;
        for (String ln : s.split("\n")) {
            try {
                if (ln.startsWith("R\t")) {
                    String[] p = ln.split("\t", 7);
                    if (p.length < 7 || t.repeats.size() >= MAX_REPEATS) continue;
                    int id = Integer.parseInt(p[1].trim());
                    int mode = Integer.parseInt(p[2].trim());
                    if (id <= 0 || mode < EVERY_DAY || mode > CHOSEN_DAYS) continue;
                    String text = cleanText(p[6]);
                    if (text.isEmpty() || p[5].isEmpty()) continue;
                    t.repeats.put(id, new Repeat(id, mode, Integer.parseInt(p[3].trim()), LocalDate.parse(p[4].trim()), p[5], text));
                    continue;
                }
                if (ln.startsWith("M\t")) {
                    String[] p = ln.split("\t", 3);
                    if (p.length < 3) continue;
                    LocalDate.parse(p[1]);
                    Integer.parseInt(p[2].trim());
                    t.made.add(p[1] + "\t" + p[2].trim());
                    continue;
                }
                if (ln.startsWith("K\t")) {
                    String[] p = ln.split("\t", 4);
                    if (p.length < 3 || p[2].isEmpty()) continue;
                    LocalDate.parse(p[1]);
                    t.goalDone.add(p[1] + "\t" + p[2] + "\t" + (p.length > 3 ? p[3] : ""));
                    continue;
                }
                String[] p = ln.split("\t", 4);
                if (p.length < 4) continue;
                LocalDate day = LocalDate.parse(p[0]);
                String flag = p[2].trim();
                int rid = 0;
                int at = flag.indexOf('@');
                if (at >= 0) {
                    rid = Integer.parseInt(flag.substring(at + 1));
                    flag = flag.substring(0, at);
                }
                int place = t.add(day, p[1], p[3]);
                if (place >= 0) {
                    if (flag.equals("1")) t.setDone(day, p[1], place, true);
                    if (rid > 0) t.lists.get(key(day, p[1])).get(place).repeatId = rid;
                }
            } catch (RuntimeException ignored) {
                // skip a damaged line
            }
        }
        // An item whose repeat is gone (damaged data) is a plain item.
        for (List<Item> l : t.lists.values()) for (Item i : l) if (i.repeatId > 0 && !t.repeats.containsKey(i.repeatId)) i.repeatId = 0;
        return t;
    }
}
