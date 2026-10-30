package app.dinacharya.wallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The daily routine and the scheduling maths. Uses no Android classes so it can be
 * unit-tested on a plain JVM.
 *
 * Times are minutes since midnight. A slot may end after 1440 when the day crosses midnight.
 */
public final class Routine {
    public static final int TASK = 0, REST = 1, MEAL = 2, PERSONAL = 3, SLEEP = 4;
    public static final String[] KIND_NAMES = {"Task", "Rest", "Meal", "Personal", "Sleep"};

    public static final int MODE_WAKE = 0, MODE_TASK = 1;
    public static final int THEME_LIGHT = 0, THEME_NIGHT = 1, THEME_AUTO = 2;
    /** Colour themes. Each one has a light and a night version (see THEME_*). */
    public static final int PAL_TEAL = 0, PAL_SUNSET = 1, PAL_OCEAN = 2, PAL_GRAPHITE = 3;
    public static final String[] PALETTE_NAMES = {"Teal", "Sunset", "Ocean", "Graphite"};
    /**
     * What a slot does when it starts. The numbers are what the saved text holds: 1 and 0 were "bell on" and "bell off" before 2.9,
     * so an older save reads as ring and silent notification.
     */
    public static final int ALERT_SILENT = 0, ALERT_RING = 1, ALERT_VIBRATE = 2, ALERT_NONE = 3;
    /** Saved texts hold the alert plus this when the slot is the wake-up slot. */
    private static final int WAKE_FLAG = 10;
    public static final int MAX_SUB_LEN = 24;
    public static final int MAX_QUOTE_LEN = 200;
    /** The activity type every kind of slot always has, and the one a slot gets when no other was chosen. */
    public static final String OTHER = "Other";
    public static final int MAX_NOTE_LEN = 400;
    public static final int MAX_NAME_LEN = 30;
    /** Monday first, matching {@link #dayUse}. */
    public static final String[] DAY_SHORT = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private static final String MAGIC = "DINACHARYA1";

    /** One block of the day. */
    /**
     * What happened to a part of today's schedule, as the home screen shows it: it began late, the slot before was carried on into it, a
     * break, a wait before a later start, it began before its time, the slot before was finished early, or the rest of the day is off.
     */
    public static final int LIVE_NONE = 0, LIVE_LATE = 1, LIVE_CARRIED = 2, LIVE_BREAK = 3, LIVE_WAIT = 4, LIVE_EARLY = 5, LIVE_CUT = 6,
            LIVE_OFF = 7;

    public static final class Block {
        public int kind;
        public String title;
        public int mins;
        /** Whether this block makes a sound or vibrates when it starts (only used while alarms are on). Follows {@link #alert}. */
        public boolean alarm = true;
        /** What happens when the slot starts: {@link #ALERT_RING}, {@link #ALERT_VIBRATE}, {@link #ALERT_SILENT} or {@link #ALERT_NONE}. */
        public int alert = ALERT_RING;
        /** The start-of-day slot: the first alarm of the day, which does not stop until the day is started or delayed. At most one. */
        public boolean wake;
        /** The activity type of this block, such as "Study" for a Task. Never empty: "Other" when none was chosen. */
        public String sub = OTHER;
        /** Your own note for this slot, such as the topics to cover. May hold line breaks. */
        public String note = "";

        // Set only on the temporary copy made for one day; never saved.
        /** Index of this block in the saved routine, or -1 for a block made up for the day. */
        public int src = -1;
        /** Taken off for today. The slot keeps its time but is shown as skipped. */
        public boolean skipped;
        /** Renamed or taken off for today. */
        public boolean changed;
        /** The saved name, when it was renamed for today. */
        public String origTitle;
        /** Minutes added to this block's end because the next morning starts later. */
        public int extended;
        /** What happened to this part of the day so far, for the schedule on the home screen (see {@link LivePlan}). Never saved. */
        public int live = LIVE_NONE;
        /** For a break or waiting part made inside a slot: the number of that slot. Never saved. */
        public int owner = -1;

        public Block(int kind, String title, int mins) {
            this.kind = clampKind(kind);
            this.title = clean(title);
            this.mins = clampMins(mins);
        }

        public Block copy() {
            Block b = new Block(kind, title, mins);
            b.mins = mins;
            b.alarm = alarm;
            b.alert = alert;
            b.wake = wake;
            b.sub = sub;
            b.note = note;
            b.src = src;
            b.skipped = skipped;
            b.changed = changed;
            b.origTitle = origTitle;
            b.extended = extended;
            b.live = live;
            b.owner = owner;
            return b;
        }

        /** Sets what happens at the start; keeps {@link #alarm} in step. */
        public void setAlert(int mode) {
            alert = (mode >= ALERT_SILENT && mode <= ALERT_NONE) ? mode : ALERT_RING;
            alarm = alert == ALERT_RING || alert == ALERT_VIBRATE;
            if (!alarm) wake = false;
        }

        /** The slot this part belongs to: its own number, or for a break or waiting part the slot it sits in; -1 for none. */
        public int slotSrc() {
            return src >= 0 ? src : owner;
        }

        /** True for a part of today made for a break, a wait before a later start, or the day off (not a slot of the plan). */
        public boolean isGap() {
            return live == LIVE_BREAK || live == LIVE_WAIT || live == LIVE_OFF;
        }

        /** True when the start makes a sound or vibrates. */
        public boolean sounds() {
            return alert == ALERT_RING || alert == ALERT_VIBRATE;
        }
    }

    /** An activity type that belongs to one kind of block: "Study" is a type of Task. */
    public static final class Sub {
        public final String name;
        public final int kind;

        Sub(int kind, String name) {
            this.kind = clampKind(kind);
            this.name = cleanSub(name);
        }
    }

    /**
     * A target. A goal belongs to one slot of the routine (such as "Reading" in the morning): it is the time to spend in that slot, with
     * an optional to-do list of its own. An older goal belongs to a whole activity type instead (6 hours of Study) and still works as it
     * did. It applies on the days its rule says, and only when that day's schedule has the slot (or the type) and, when it names
     * others, at least one of them.
     */
    public static final class Goal {
        /** How many things a goal's own to-do list holds. */
        public static final int MAX_TODOS = 12;

        /** How it repeats: every day, every second day from {@link #date}, on chosen weekdays, or on one day only ({@link #date}). */
        public static final int DAILY = 0, ALTERNATE = 1, WEEKDAYS = 2, ONCE = 3;

        public final int kind;
        public final String sub;
        public final int mins;
        public int repeat = DAILY;
        /** The day of a one-day goal, or the first day of an every-other-day goal. */
        public LocalDate date;
        /** The weekdays of a goal on chosen days: bit 0 is Monday, bit 6 Sunday. */
        public int daysMask = 0x7F;
        /** Other types the day's schedule must have (at least one of them) for the goal to count that day. Empty: no condition. */
        public final List<Sub> with = new ArrayList<>();
        /** The name of the slot the goal belongs to, or empty for an older goal that covers a whole activity type. */
        public String slot = "";
        /** The goal's own to-do list: the same things on every day the goal counts, ticked fresh each day. */
        public final List<String> todos = new ArrayList<>();

        public Goal(int kind, String sub, int mins) {
            this.kind = clampKind(kind);
            this.sub = cleanSub(sub);
            this.mins = clamp(mins, 5, 1440);
        }

        /** A goal for one slot of the routine (by its kind and name). */
        public static Goal forSlot(int kind, String slot, int mins) {
            Goal g = new Goal(kind, "", mins);
            g.slot = cleanSlot(slot);
            return g;
        }

        /** A goal for one day only. */
        public static Goal once(int kind, String sub, int mins, LocalDate day) {
            Goal g = new Goal(kind, sub, mins);
            g.repeat = ONCE;
            g.date = day;
            return g;
        }

        /** A goal for one slot, for one day only. */
        public static Goal onceForSlot(int kind, String slot, int mins, LocalDate day) {
            Goal g = forSlot(kind, slot, mins);
            g.repeat = ONCE;
            g.date = day;
            return g;
        }

        /** True when the goal belongs to one slot (and not to a whole activity type). */
        public boolean bound() {
            return !slot.isEmpty();
        }

        /** The name shown for the goal: its slot, or the activity type of an older goal. */
        public String label() {
            if (bound()) return slot;
            return sub.isEmpty() ? KIND_NAMES[kind] : sub;
        }

        /** Adds a thing to the goal's to-do list; false when the text is empty, already there, or the list is full. */
        public boolean addTodo(String text) {
            String t = cleanTodo(text);
            if (t.isEmpty() || todos.size() >= MAX_TODOS) return false;
            for (String x : todos) if (x.equalsIgnoreCase(t)) return false;
            todos.add(t);
            return true;
        }

        /** Tells the same goal apart from another: the slot (or the type of an older goal), not the day. */
        public String ident() {
            return bound() ? kind + "#" + slot.toLowerCase(java.util.Locale.ROOT) : kind + ":" + sub.toLowerCase(java.util.Locale.ROOT);
        }

        /** True when the block is a slot this goal counts: not taken off, and of its kind and name (or, for an older goal, its type). */
        public boolean covers(Block b) {
            if (b == null || b.skipped || b.kind != kind) return false;
            if (bound()) return savedTitle(b).equalsIgnoreCase(slot);
            return sub.isEmpty() || sub.equalsIgnoreCase(b.sub);
        }

        public boolean repeats() {
            return repeat != ONCE;
        }

        /** True when the rule (not the schedule) says the goal is for {@code day}. */
        public boolean ruleMatches(LocalDate day) {
            if (day == null) return false;
            switch (repeat) {
                case ONCE:
                    return date != null && date.equals(day);
                case ALTERNATE:
                    return date == null || Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(date, day), 2L) == 0L;
                case WEEKDAYS:
                    return ((daysMask >> (day.getDayOfWeek().getValue() - 1)) & 1) != 0;
                default:
                    return true;
            }
        }

        /** True when one of the (not taken off) blocks is this goal's slot, or of its type for an older goal. */
        public boolean presentIn(List<Block> schedule) {
            for (Block b : schedule) if (covers(b)) return true;
            return false;
        }

        /** The first block of the schedule that is a slot of this goal (the one its to-do list belongs to), or null. */
        public Block firstSlot(List<Block> schedule) {
            for (Block b : schedule) if (b.src >= 0 && covers(b)) return b;
            return null;
        }

        /** True when there is no condition on other types, or the schedule has at least one of them. */
        public boolean withMet(List<Block> schedule) {
            if (with.isEmpty()) return true;
            for (Sub w : with) {
                for (Block b : schedule) {
                    if (!b.skipped && b.kind == w.kind && b.sub.equalsIgnoreCase(w.name)) return true;
                }
            }
            return false;
        }

        /** Tells goals apart: the slot or type, and for a one-day goal the day. */
        public String key() {
            return ident() + (repeat == ONCE && date != null ? "@" + date : "");
        }

        /** The rule in words, for lists: "Every day", "Every other day", "Mon, Wed, Fri", "Today only". */
        public String ruleText(LocalDate today) {
            String r;
            switch (repeat) {
                case ONCE:
                    r = date != null && date.equals(today) ? "Today only"
                            : date != null && date.equals(today.plusDays(1)) ? "Tomorrow only"
                            : "One day only";
                    break;
                case ALTERNATE:
                    r = "Every other day";
                    break;
                case WEEKDAYS:
                    StringBuilder sb = new StringBuilder();
                    for (int d = 0; d < 7; d++) {
                        if (((daysMask >> d) & 1) == 0) continue;
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(DAY_SHORT[d]);
                    }
                    r = sb.length() == 0 ? "No days" : sb.toString();
                    break;
                default:
                    r = "Every day";
            }
            if (!with.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < with.size(); i++) names.append(i == 0 ? "" : ", ").append(with.get(i).name);
                r = r + ", only on days with " + names;
            }
            return r;
        }

        public Goal copy() {
            Goal g = new Goal(kind, sub, mins);
            g.repeat = repeat;
            g.date = date;
            g.daysMask = daysMask;
            g.with.addAll(with);
            g.slot = slot;
            g.todos.addAll(todos);
            return g;
        }
    }

    /** A block placed on the clock. */
    public static final class Slot {
        public final Block block;
        public final int start;
        public final int end;

        Slot(Block block, int start, int end) {
            this.block = block;
            this.start = start;
            this.end = end;
        }
    }

    /** The whole day laid out on the clock. */
    public static final class Schedule {
        public final List<Slot> slots = new ArrayList<>();
        public final int[] byKind = new int[5];
        public int start;
        public int total;
        public int mode;
        public int firstTaskStart = -1;
        public int bedStart;
        public boolean hasSleep;
    }

    // What the user sets.
    /** The routine's own name, such as "Weekdays". */
    public String name = "My routine";
    public int mode = MODE_WAKE;
    public int anchor = 330; // 5:30 AM
    public final List<Block> blocks = new ArrayList<>();

    /** The activity types you can give to a block. */
    public final List<Sub> subs = new ArrayList<>();

    /** Daily targets per activity type. */
    public final List<Goal> goals = new ArrayList<>();

    /**
     * Other routines, such as "Weekend". Only their name, start setting and blocks are used; the look,
     * the types and the goals always come from this (the main) routine.
     */
    public final List<Routine> others = new ArrayList<>();

    /** Which routine each day of the week uses, Monday first: 0 = this one, k = others.get(k - 1). */
    public final int[] dayUse = new int[7];

    /**
     * Temporary routines, each for one calendar date (today, tomorrow or a date picked). On its date a temporary routine is
     * followed instead of the one the weekday would use. Only the main routine holds them; they are dropped once the date is past.
     */
    public final List<Routine> temps = new ArrayList<>();

    /** The date this routine is for when it is one of {@link #temps}; null for every other routine. */
    public LocalDate tempDate;

    // How the wallpaper looks.
    public int palette = PAL_TEAL;
    public int theme = THEME_AUTO;
    public boolean fmt24 = false;
    public int topPct = 8;
    public int bottomPct = 9;
    public boolean showClock = true;
    public boolean showScreen = true;
    /** The small "Developed by" mark at the bottom of the wallpaper. */
    public boolean showMark = true;

    // Reminders.
    public boolean alarmOn = false;
    /** Slots whose alert is "silent notification" still ask what you are doing (on unless turned off). */
    public boolean askSilent = true;
    /** Minutes a wake-up can be delayed by each time (a step of 5). */
    public int wakeDelayMin = 5;
    /** What has to be done before the wake-up can be delayed: nothing, a quick sum or a sentence typed. */
    public static final int CHALLENGE_NONE = 0, CHALLENGE_SUM = 1, CHALLENGE_TYPE = 2;
    public int wakeChallenge = CHALLENGE_NONE;
    /** The person's own quote for the wake-up, or empty to use the built-in ones. */
    public String wakeQuote = "";
    /**
     * What has to be done to turn the start-of-day alarm off by starting the day: nothing (a tap), a few sums, a short pause (slow
     * breathing), shaking the phone, or a sentence typed.
     */
    public static final int OFF_NONE = 0, OFF_SUM = 1, OFF_PAUSE = 2, OFF_SHAKE = 3, OFF_TYPE = 4;
    public int wakeOff = OFF_NONE;
    /** A motivating quote is shown on the start-of-day alarm (on unless turned off). */
    public boolean wakeQuoteOn = true;
    /** The start-of-day alarm offers "Take the day off" (on unless turned off). */
    public boolean wakeDayOff = true;

    public Routine() {
        addDefaultSubs();
    }

    private void addDefaultSubs() {
        subs.clear();
        subs.add(new Sub(TASK, "Study"));
        subs.add(new Sub(TASK, "Revision"));
        subs.add(new Sub(TASK, "Practice"));
        subs.add(new Sub(TASK, "Work"));
        subs.add(new Sub(REST, "Nap"));
        subs.add(new Sub(PERSONAL, "Exercise"));
        subs.add(new Sub(PERSONAL, "Reading"));
        subs.add(new Sub(PERSONAL, "Family time"));
        addMissingDefaults();
    }

    /**
     * Makes sure the types added in 2.9 are there: Meal, Personal and Sleep under their own kinds, and Other under every kind.
     * Types that are already there are left alone.
     */
    private void addMissingDefaults() {
        if (findSub(MEAL, "Meal") == null) subs.add(new Sub(MEAL, "Meal"));
        if (findSub(PERSONAL, "Personal") == null) subs.add(new Sub(PERSONAL, "Personal"));
        if (findSub(SLEEP, "Sleep") == null) subs.add(new Sub(SLEEP, "Sleep"));
        ensureOther();
    }

    /** Every kind has an "Other" type. */
    private void ensureOther() {
        for (int k = 0; k < KIND_NAMES.length; k++) {
            if (findSub(k, OTHER) == null) subs.add(new Sub(k, OTHER));
        }
    }

    // ---------------------------------------------------------------- activity types

    public Sub findSub(int kind, String name) {
        String n = cleanSub(name);
        for (Sub s : subs) {
            if (s.kind == kind && s.name.equalsIgnoreCase(n)) return s;
        }
        return null;
    }

    /** Adds an activity type under a kind. Returns the existing one if there already is one with that name, or null for an empty name. */
    public Sub addSub(int kind, String name) {
        String n = cleanSub(name);
        if (n.isEmpty()) return null;
        Sub old = findSub(clampKind(kind), n);
        if (old != null) return old;
        Sub s = new Sub(kind, n);
        subs.add(s);
        return s;
    }

    /** Whether this is the "Other" type, which cannot be removed. */
    public static boolean isOther(String name) {
        return cleanSub(name).equalsIgnoreCase(OTHER);
    }

    /** Removes an activity type ("Other" stays). Blocks that had it keep their time and name and become "Other". */
    public void removeSub(Sub s) {
        if (isOther(s.name) || !subs.remove(s)) return;
        for (Routine r : everyRoutine()) {
            for (Block b : r.blocks) {
                if (b.kind == s.kind && b.sub.equalsIgnoreCase(s.name)) b.sub = OTHER;
            }
        }
        goals.removeIf(g -> !g.bound() && g.kind == s.kind && g.sub.equalsIgnoreCase(s.name));
        for (Goal g : goals) g.with.removeIf(w -> w.kind == s.kind && w.name.equalsIgnoreCase(s.name));
    }

    /** The types of one kind, in the order they were added, with "Other" last. */
    public List<Sub> subsOf(int kind) {
        List<Sub> out = new ArrayList<>();
        Sub other = null;
        for (Sub s : subs) {
            if (s.kind != kind) continue;
            if (isOther(s.name)) other = s;
            else out.add(s);
        }
        if (other != null) out.add(other);
        return out;
    }

    /**
     * Makes the list of types agree with the blocks: a block's type is added if missing, and a block with no type becomes
     * "Other". Every kind keeps its "Other".
     */
    public void syncSubs() {
        normalizeWake();
        ensureOther();
        for (Routine r : everyRoutine()) {
            for (Block b : r.blocks) {
                b.sub = cleanSub(b.sub);
                if (b.sub.isEmpty()) b.sub = OTHER;
                if (findSub(b.kind, b.sub) == null) subs.add(new Sub(b.kind, b.sub));
            }
        }
    }

    /** The routines that hold slots: this one, the others and the temporary ones. */
    private List<Routine> everyRoutine() {
        List<Routine> l = all();
        l.addAll(temps);
        return l;
    }

    // ---------------------------------------------------------------- routines for days of the week

    /** True when there is more than one routine. */
    public boolean hasWeek() {
        return !others.isEmpty();
    }

    /** All routines: this one first, then the others. */
    public List<Routine> all() {
        List<Routine> l = new ArrayList<>();
        l.add(this);
        l.addAll(others);
        return l;
    }

    /** Routine number k (0 = this one). */
    public Routine routineAt(int k) {
        return (k <= 0 || k > others.size()) ? this : others.get(k - 1);
    }

    /** The number of the routine used on {@code day} (0 = this one). */
    public int useOn(LocalDate day) {
        int k = dayUse[day.getDayOfWeek().getValue() - 1];
        return (k < 0 || k > others.size()) ? 0 : k;
    }

    /**
     * The routine to follow on {@code day}: a temporary routine made for that date if there is one, else this one, or one of
     * the others dressed in this one's look, types and goals. Returns {@code this} itself when the day uses the main routine.
     */
    public Routine forDay(LocalDate day) {
        Routine t = tempFor(day);
        if (t != null) return dressed(t);
        int k = useOn(day);
        if (k == 0) return this;
        return dressed(others.get(k - 1));
    }

    private Routine dressed(Routine o) {
        Routine r = copyLook();
        r.name = o.name;
        r.mode = o.mode;
        r.anchor = o.anchor;
        for (Block b : o.blocks) r.blocks.add(b.copy());
        return r;
    }

    // ---------------------------------------------------------------- temporary routines

    /** True when there is a temporary routine for some date. */
    public boolean hasTemps() {
        return !temps.isEmpty();
    }

    /** The temporary routine made for {@code day}, or null. */
    public Routine tempFor(LocalDate day) {
        if (day == null) return null;
        for (Routine t : temps) if (day.equals(t.tempDate)) return t;
        return null;
    }

    /**
     * A number that says which routine {@code day} follows: 0 for this one, k for the k-th other routine, 1000 + i for the i-th
     * temporary one. Two days with the same number follow the same routine.
     */
    public int dayKey(LocalDate day) {
        for (int i = 0; i < temps.size(); i++) if (day.equals(temps.get(i).tempDate)) return 1000 + i;
        return useOn(day);
    }

    /**
     * Makes a temporary routine for {@code date}, a copy of {@code from}'s slots and start. A date has one temporary routine:
     * an earlier one for the same date is replaced. Returns the new one.
     */
    public Routine addTemp(LocalDate date, String name, Routine from) {
        for (int i = temps.size() - 1; i >= 0; i--) if (date.equals(temps.get(i).tempDate)) temps.remove(i);
        Routine o = new Routine();
        o.subs.clear();
        o.name = cleanName(name);
        o.mode = from.mode;
        o.anchor = from.anchor;
        for (Block b : from.blocks) {
            Block c = new Block(b.kind, b.title, b.mins);
            c.alarm = b.alarm;
            c.alert = b.alert;
            c.wake = b.wake;
            c.sub = b.sub;
            c.note = b.note;
            o.blocks.add(c);
        }
        o.tempDate = date;
        temps.add(o);
        temps.sort((x, y) -> x.tempDate.compareTo(y.tempDate));
        return o;
    }

    /** Moves a temporary routine to another date (replacing any temporary routine already on that date). */
    public void moveTemp(Routine t, LocalDate date) {
        if (!temps.contains(t)) return;
        for (int i = temps.size() - 1; i >= 0; i--) {
            Routine o = temps.get(i);
            if (o != t && date.equals(o.tempDate)) temps.remove(i);
        }
        t.tempDate = date;
        temps.sort((x, y) -> x.tempDate.compareTo(y.tempDate));
    }

    /** Forgets temporary routines whose date is more than a day past (the day that began yesterday may still be running). */
    public boolean pruneTemps(LocalDate today) {
        LocalDate oldest = today.minusDays(1);
        return temps.removeIf(t -> t.tempDate == null || t.tempDate.isBefore(oldest));
    }

    /** Adds another routine, a copy of {@code from}, and returns it. */
    public Routine addOther(String name, Routine from) {
        Routine o = new Routine();
        o.subs.clear();
        o.name = cleanName(name);
        o.mode = from.mode;
        o.anchor = from.anchor;
        for (Block b : from.blocks) {
            Block c = new Block(b.kind, b.title, b.mins);
            c.alarm = b.alarm;
            c.alert = b.alert;
            c.wake = b.wake;
            c.sub = b.sub;
            c.note = b.note;
            o.blocks.add(c);
        }
        others.add(o);
        return o;
    }

    /** Removes routine number k (k >= 1). Days that used it go back to the main routine. */
    public void removeOther(int k) {
        if (k < 1 || k > others.size()) return;
        others.remove(k - 1);
        for (int d = 0; d < 7; d++) {
            if (dayUse[d] == k) dayUse[d] = 0;
            else if (dayUse[d] > k) dayUse[d]--;
        }
    }

    /** Makes this routine an exact copy of {@code o}, keeping the same object (used by undo and restore). */
    public void replaceWith(Routine o) {
        name = o.name;
        mode = o.mode;
        anchor = o.anchor;
        blocks.clear();
        for (Block b : o.blocks) blocks.add(b.copy());
        subs.clear();
        subs.addAll(o.subs);
        goals.clear();
        goals.addAll(o.goals);
        others.clear();
        others.addAll(o.others);
        temps.clear();
        temps.addAll(o.temps);
        System.arraycopy(o.dayUse, 0, dayUse, 0, 7);
        palette = o.palette;
        theme = o.theme;
        fmt24 = o.fmt24;
        topPct = o.topPct;
        bottomPct = o.bottomPct;
        showClock = o.showClock;
        showScreen = o.showScreen;
        showMark = o.showMark;
        alarmOn = o.alarmOn;
        askSilent = o.askSilent;
        wakeDelayMin = o.wakeDelayMin;
        wakeChallenge = o.wakeChallenge;
        wakeQuote = o.wakeQuote;
        wakeOff = o.wakeOff;
        wakeQuoteOn = o.wakeQuoteOn;
        wakeDayOff = o.wakeDayOff;
    }

    // ---------------------------------------------------------------- goals

    /** The name a slot is known by: the saved name, also on a day it was renamed. */
    public static String savedTitle(Block b) {
        return clean(b.changed && b.origTitle != null ? b.origTitle : b.title).trim();
    }

    /** A slot's name as a goal keeps it. */
    static String cleanSlot(String t) {
        return clean(t).trim();
    }

    /** A thing on a goal's to-do list: one line, no separators, a sensible length. */
    static String cleanTodo(String t) {
        if (t == null) return "";
        String c = t.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim();
        return c.length() > Todos.MAX_LEN ? c.substring(0, Todos.MAX_LEN).trim() : c;
    }

    /** The repeating goal for an activity type that covers the whole type (one-day goals and goals of one slot are not counted), or null. */
    public Goal findGoal(int kind, String sub) {
        String n = cleanSub(sub);
        for (Goal g : goals) if (g.repeats() && !g.bound() && g.kind == kind && g.sub.equalsIgnoreCase(n)) return g;
        return null;
    }

    /** The one-day goal for an activity type on a day, or null. */
    public Goal findOnce(int kind, String sub, LocalDate day) {
        String n = cleanSub(sub);
        for (Goal g : goals) {
            if (!g.repeats() && !g.bound() && g.kind == kind && g.sub.equalsIgnoreCase(n) && day != null && day.equals(g.date)) return g;
        }
        return null;
    }

    /** The repeating goal of one slot (by its kind and name), or null. */
    public Goal findSlotGoal(int kind, String slot) {
        String n = cleanSlot(slot);
        if (n.isEmpty()) return null;
        for (Goal g : goals) if (g.repeats() && g.bound() && g.kind == kind && g.slot.equalsIgnoreCase(n)) return g;
        return null;
    }

    /** The one-day goal of one slot on a day, or null. */
    public Goal findSlotOnce(int kind, String slot, LocalDate day) {
        String n = cleanSlot(slot);
        if (n.isEmpty() || day == null) return null;
        for (Goal g : goals) {
            if (!g.repeats() && g.bound() && g.kind == kind && g.slot.equalsIgnoreCase(n) && day.equals(g.date)) return g;
        }
        return null;
    }

    /** The goal that would be replaced by {@code g}: the same slot (or type) with the same kind of rule (repeating, or the same day). */
    public Goal findSame(Goal g) {
        if (g.bound()) return g.repeats() ? findSlotGoal(g.kind, g.slot) : findSlotOnce(g.kind, g.slot, g.date);
        return g.repeats() ? findGoal(g.kind, g.sub) : findOnce(g.kind, g.sub, g.date);
    }

    /** Sets (or with 0 minutes removes) the every-day target for a type. */
    public void setGoal(int kind, String sub, int mins) {
        Goal old = findGoal(kind, sub);
        if (old != null) goals.remove(old);
        if (mins > 0) goals.add(new Goal(kind, sub, mins));
    }

    /** Adds a goal. It replaces the repeating goal of the same slot (or type), or the one-day goal of the same slot (or type) and day. */
    public void putGoal(Goal g) {
        Goal old = findSame(g);
        if (old != null) goals.remove(old);
        goals.add(g);
    }

    /** Drops one-day goals whose day is more than a day past. */
    public boolean pruneGoals(LocalDate today) {
        LocalDate oldest = today.minusDays(1);
        return goals.removeIf(g -> !g.repeats() && (g.date == null || g.date.isBefore(oldest)));
    }

    /**
     * The goals that count on {@code day}, given the blocks of that day's schedule: the rule must say so, the schedule must have the
     * slot (or, for an older goal, the type) and one of the types it asks for. A one-day goal replaces the repeating goal of the same
     * slot (or type) for its day.
     */
    public List<Goal> goalsFor(LocalDate day, List<Block> schedule) {
        List<Goal> out = new ArrayList<>();
        java.util.Set<String> onceTypes = new java.util.HashSet<>();
        for (Goal g : goals) {
            if (!g.repeats() && g.ruleMatches(day) && g.presentIn(schedule)) onceTypes.add(g.ident());
        }
        for (Goal g : goals) {
            if (!g.ruleMatches(day) || !g.presentIn(schedule)) continue;
            if (g.repeats()) {
                if (!g.withMet(schedule)) continue;
                if (onceTypes.contains(g.ident())) continue;
            }
            out.add(g);
        }
        return out;
    }

    /** True when some goal (of any rule) covers this activity type as a whole or belongs to a slot of this type. */
    public boolean hasGoalFor(int kind, String sub) {
        String n = cleanSub(sub);
        for (Goal g : goals) if (g.kind == kind && (g.bound() ? slotHasSub(g, n) : g.sub.equalsIgnoreCase(n))) return true;
        return false;
    }

    private boolean slotHasSub(Goal g, String sub) {
        for (Routine r : everyRoutine()) {
            for (Block b : r.blocks) if (g.covers(b) && b.sub.equalsIgnoreCase(sub)) return true;
        }
        return false;
    }

    /** The activity type of the first slot of this kind and name in any routine (for its colour), or empty. */
    public String slotSub(int kind, String title) {
        String n = cleanSlot(title);
        for (Routine r : everyRoutine()) {
            for (Block b : r.blocks) if (b.kind == kind && clean(b.title).trim().equalsIgnoreCase(n)) return b.sub;
        }
        return "";
    }

    /** True when some routine (the main one, the others and the one-day ones) has a slot of this kind and name. */
    public boolean hasSlot(int kind, String title) {
        String n = cleanSlot(title);
        for (Routine r : everyRoutine()) {
            for (Block b : r.blocks) if (b.kind == kind && clean(b.title).trim().equalsIgnoreCase(n)) return true;
        }
        return false;
    }

    /**
     * A slot was renamed in the routine: the goals of that slot follow it, unless another slot still has the old name or a goal for the
     * new name exists already (then the goals stay as they are). Returns how many goals moved.
     */
    public int renameSlot(int kind, String oldTitle, String newTitle) {
        String from = cleanSlot(oldTitle);
        String to = cleanSlot(newTitle);
        if (from.isEmpty() || to.isEmpty() || from.equalsIgnoreCase(to) || hasSlot(kind, from)) return 0;
        int moved = 0;
        for (Goal g : new ArrayList<>(goals)) {
            if (!g.bound() || g.kind != kind || !g.slot.equalsIgnoreCase(from)) continue;
            g.slot = to;
            if (findSameOther(g) != null) {   // the new name has a goal of this kind already: keep that one
                g.slot = from;
                continue;
            }
            moved++;
        }
        return moved;
    }

    private Goal findSameOther(Goal g) {
        for (Goal o : goals) {
            if (o == g || o.bound() != g.bound() || o.kind != g.kind || !o.slot.equalsIgnoreCase(g.slot)) continue;
            if (o.repeats() != g.repeats()) continue;
            if (o.repeats() || (o.date != null && o.date.equals(g.date))) return o;
        }
        return null;
    }

    // ---------------------------------------------------------------- scheduling

    public Schedule compute() {
        Schedule s = new Schedule();
        int n = blocks.size();
        int[] offs = new int[n];
        int t = 0;
        for (int i = 0; i < n; i++) {
            offs[i] = t;
            t += blocks.get(i).mins;
        }
        int ft = -1;
        for (int i = 0; i < n; i++) {
            if (blocks.get(i).kind == TASK) {
                ft = i;
                break;
            }
        }
        int si = -1;
        for (int i = n - 1; i >= 0; i--) {
            if (blocks.get(i).kind == SLEEP) {
                si = i;
                break;
            }
        }
        s.mode = (mode == MODE_TASK && ft >= 0) ? MODE_TASK : MODE_WAKE;
        int start = s.mode == MODE_TASK ? anchor - offs[ft] : anchor;
        s.start = mod(start, 1440);
        s.total = t;
        for (int i = 0; i < n; i++) {
            Block b = blocks.get(i);
            s.slots.add(new Slot(b, s.start + offs[i], s.start + offs[i] + b.mins));
            s.byKind[b.kind] += b.mins;
        }
        s.firstTaskStart = ft >= 0 ? s.slots.get(ft).start : -1;
        s.hasSleep = si >= 0;
        s.bedStart = si >= 0 ? s.slots.get(si).start : s.start + t;
        return s;
    }

    /** Places a clock minute (0..1439) inside the schedule's day, which runs [start, start + 1440). */
    public static int frameMinute(Schedule s, int nowMin) {
        int t = mod(nowMin, 1440);
        return t >= s.start ? t : t + 1440;
    }

    /** Index of the slot running now, or -1 when the clock falls in unplanned time. */
    public static int currentIndex(Schedule s, int nowMin) {
        int t = frameMinute(s, nowMin);
        for (int i = 0; i < s.slots.size(); i++) {
            Slot sl = s.slots.get(i);
            if (t >= sl.start && t < sl.end) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- formatting

    public static String formatTime(int minutes, boolean h24) {
        int m = mod(minutes, 1440);
        int h = m / 60;
        int mm = m % 60;
        if (h24) return pad2(h) + ":" + pad2(mm);
        int h12 = (h % 12 == 0) ? 12 : h % 12;
        return h12 + ":" + pad2(mm) + (h < 12 ? " AM" : " PM");
    }

    public static String formatDuration(int m) {
        if (m < 60) return m + " min";
        int h = m / 60;
        int r = m % 60;
        return r == 0 ? h + "h" : h + "h " + r + "m";
    }

    // ---------------------------------------------------------------- persistence

    public String serialize() {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        sb.append("mode=").append(mode).append('\n');
        sb.append("anchor=").append(anchor).append('\n');
        sb.append("palette=").append(palette).append('\n');
        sb.append("theme=").append(theme).append('\n');
        sb.append("fmt24=").append(fmt24 ? 1 : 0).append('\n');
        sb.append("top=").append(topPct).append('\n');
        sb.append("bottom=").append(bottomPct).append('\n');
        sb.append("clock=").append(showClock ? 1 : 0).append('\n');
        sb.append("screen=").append(showScreen ? 1 : 0).append('\n');
        sb.append("alarm=").append(alarmOn ? 1 : 0).append('\n');
        sb.append("askSilent=").append(askSilent ? 1 : 0).append('\n');
        sb.append("wakeDelay=").append(wakeDelayMin).append('\n');
        sb.append("wakeChallenge=").append(wakeChallenge).append('\n');
        sb.append("wakeOff=").append(wakeOff).append('\n');
        sb.append("wakeQuoteOn=").append(wakeQuoteOn ? 1 : 0).append('\n');
        sb.append("wakeDayOff=").append(wakeDayOff ? 1 : 0).append('\n');
        if (!cleanQuote(wakeQuote).isEmpty()) sb.append("Q\t").append(cleanQuote(wakeQuote)).append('\n');
        sb.append("mark=").append(showMark ? 1 : 0).append('\n');
        sb.append("N\t").append(cleanName(name)).append('\n');
        sb.append("subs=2\n");
        for (Sub u : subs) {
            sb.append("U\t").append(u.kind).append('\t').append(u.name).append('\n');
        }
        for (Goal g : goals) {
            sb.append("G\t").append(g.kind).append('\t').append(g.mins).append('\t').append(g.sub);
            if (g.repeat != Goal.DAILY || !g.with.isEmpty() || g.bound() || !g.todos.isEmpty()) {
                sb.append('\t').append(g.repeat).append('\t').append(g.date == null ? "" : g.date.toString())
                        .append('\t').append(g.daysMask).append('\t');
                for (int i = 0; i < g.with.size(); i++) {
                    sb.append(i == 0 ? "" : "|").append(g.with.get(i).kind).append(':').append(g.with.get(i).name.replace('|', '/'));
                }
                // The slot and the to-do list come last, so an older reader still gets the rest right.
                if (g.bound() || !g.todos.isEmpty()) {
                    sb.append('\t').append(g.slot);
                    sb.append('\t');
                    for (int i = 0; i < g.todos.size(); i++) sb.append(i == 0 ? "" : "|").append(cleanTodo(g.todos.get(i)));
                }
            }
            sb.append('\n');
        }
        writeBlocks(sb, blocks);
        if (!others.isEmpty()) {
            sb.append("days=");
            for (int d = 0; d < 7; d++) sb.append(d == 0 ? "" : ",").append(dayUse[d]);
            sb.append('\n');
            for (Routine o : others) {
                sb.append("P\t").append(cleanName(o.name)).append('\t').append(o.mode).append('\t').append(o.anchor).append('\n');
                writeBlocks(sb, o.blocks, "B");
            }
        }
        // Temporary routines. Their slots use their own line type ("TB") so an older version reads past them.
        for (Routine t : temps) {
            if (t.tempDate == null) continue;
            sb.append("T\t").append(t.tempDate).append('\t').append(cleanName(t.name)).append('\t').append(t.mode)
                    .append('\t').append(t.anchor).append('\n');
            writeBlocks(sb, t.blocks, "TB");
        }
        return sb.toString();
    }

    private static void writeBlocks(StringBuilder sb, List<Block> list) {
        writeBlocks(sb, list, "B");
    }

    private static void writeBlocks(StringBuilder sb, List<Block> list, String tag) {
        for (Block b : list) {
            sb.append(tag).append('\t').append(clampKind(b.kind)).append('\t').append(clampMins(b.mins)).append('\t')
                    .append(clean(b.title)).append('\t').append(alertCode(b)).append('\t')
                    .append(cleanSub(b.sub)).append('\t').append(escapeNote(b.note)).append('\n');
        }
    }

    /** Reads text made by {@link #serialize()} (or the web app's copy button). Returns null if it is not a routine. */
    public static Routine parse(String text) {
        if (text == null) return null;
        String[] lines = text.replace("\r", "").split("\n");
        int i = 0;
        while (i < lines.length && lines[i].trim().isEmpty()) i++;
        if (i >= lines.length || !lines[i].trim().equals(MAGIC)) return null;
        Routine r = new Routine();
        Routine target = r;          // where "B" lines go: the main routine until a "P" line starts another
        Routine tempTarget = null;   // where "TB" lines go: the temporary routine started by the last "T" line
        int[] days = null;
        boolean legacySubs = false;  // saved by a version before 2.9: its type list lacks Meal, Personal and Sleep
        for (i++; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.startsWith("N\t")) {
                r.name = cleanName(ln.substring(2));
                continue;
            }
            if (ln.startsWith("P\t")) {
                String[] p = ln.split("\t", 4);
                Routine o = new Routine();
                o.subs.clear();
                o.name = cleanName(p.length > 1 ? p[1] : "");
                try {
                    if (p.length > 2) o.mode = Integer.parseInt(p[2].trim()) == MODE_TASK ? MODE_TASK : MODE_WAKE;
                    if (p.length > 3) o.anchor = mod(Integer.parseInt(p[3].trim()), 1440);
                } catch (NumberFormatException ignored) {
                    // keep the defaults
                }
                r.others.add(o);
                target = o;
                continue;
            }
            if (ln.startsWith("T\t")) {
                String[] p = ln.split("\t", 5);
                tempTarget = null;
                if (p.length < 2) continue;
                try {
                    Routine t = new Routine();
                    t.subs.clear();
                    t.tempDate = LocalDate.parse(p[1].trim());
                    t.name = cleanName(p.length > 2 ? p[2] : "");
                    if (p.length > 3) t.mode = Integer.parseInt(p[3].trim()) == MODE_TASK ? MODE_TASK : MODE_WAKE;
                    if (p.length > 4) t.anchor = mod(Integer.parseInt(p[4].trim()), 1440);
                    if (r.tempFor(t.tempDate) == null) {
                        r.temps.add(t);
                        tempTarget = t;
                    }
                } catch (RuntimeException ignored) {
                    // skip a damaged line (and the slots after it)
                }
                continue;
            }
            if (ln.startsWith("TB\t")) {
                if (tempTarget == null) continue;
                String[] p = ln.split("\t", 7);
                if (p.length < 4) continue;
                try {
                    Block nb = new Block(Integer.parseInt(p[1].trim()), p[3], Integer.parseInt(p[2].trim()));
                    if (p.length >= 5) readAlert(nb, p[4]);
                    if (p.length >= 6) nb.sub = cleanSub(p[5]);
                    if (p.length >= 7) nb.note = unescapeNote(p[6]);
                    tempTarget.blocks.add(nb);
                } catch (NumberFormatException ignored) {
                    // skip a damaged line
                }
                continue;
            }
            if (ln.startsWith("G\t")) {
                String[] p = ln.split("\t", 10);
                if (p.length < 4) continue;
                try {
                    Goal g = new Goal(Integer.parseInt(p[1].trim()), p[3], Integer.parseInt(p[2].trim()));
                    if (p.length >= 7) {
                        int rep = Integer.parseInt(p[4].trim());
                        g.repeat = rep >= Goal.DAILY && rep <= Goal.ONCE ? rep : Goal.DAILY;
                        g.date = p[5].trim().isEmpty() ? null : LocalDate.parse(p[5].trim());
                        g.daysMask = Integer.parseInt(p[6].trim()) & 0x7F;
                        if (p.length >= 8) {
                            for (String w : p[7].split("\\|")) {
                                int c = w.indexOf(':');
                                if (c < 1) continue;
                                g.with.add(new Sub(Integer.parseInt(w.substring(0, c).trim()), w.substring(c + 1)));
                            }
                        }
                        if (p.length >= 9) g.slot = cleanSlot(p[8]);
                        if (p.length >= 10) {
                            for (String t : p[9].split("\\|")) g.addTodo(t);
                        }
                        if ((g.repeat == Goal.ONCE && g.date == null)) continue;
                    }
                    boolean dup = r.findSame(g) != null;
                    if (!dup) r.goals.add(g);
                } catch (NumberFormatException | java.time.format.DateTimeParseException ignored) {
                    // skip a damaged line
                }
                continue;
            }
            if (ln.startsWith("Q\t")) {
                r.wakeQuote = cleanQuote(ln.substring(2));
                continue;
            }
            if (ln.startsWith("days=")) {
                String[] p = ln.substring(5).split(",");
                days = new int[7];
                for (int d = 0; d < 7 && d < p.length; d++) {
                    try {
                        days[d] = Integer.parseInt(p[d].trim());
                    } catch (NumberFormatException e) {
                        days[d] = 0;
                    }
                }
                continue;
            }
            if (ln.startsWith("U\t")) {
                String[] p = ln.split("\t", 3);
                if (p.length < 3) continue;
                try {
                    int kind = Integer.parseInt(p[1].trim());
                    if (kind >= 0 && kind < KIND_NAMES.length && r.findSub(kind, p[2]) == null && !cleanSub(p[2]).isEmpty()) {
                        r.subs.add(new Sub(kind, p[2]));
                    }
                } catch (NumberFormatException ignored) {
                    // skip a damaged line
                }
                continue;
            }
            if (ln.startsWith("B\t")) {
                String[] p = ln.split("\t", 7);
                if (p.length < 4) continue;
                try {
                    Block nb = new Block(Integer.parseInt(p[1].trim()), p[3], Integer.parseInt(p[2].trim()));
                    if (p.length >= 5) readAlert(nb, p[4]);
                    if (p.length >= 6) nb.sub = cleanSub(p[5]);
                    if (p.length >= 7) nb.note = unescapeNote(p[6]);
                    target.blocks.add(nb);
                } catch (NumberFormatException ignored) {
                    // skip a damaged line
                }
                continue;
            }
            int eq = ln.indexOf('=');
            if (eq <= 0) continue;
            String k = ln.substring(0, eq).trim();
            int v;
            try {
                v = Integer.parseInt(ln.substring(eq + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            switch (k) {
                case "mode": r.mode = (v == MODE_TASK) ? MODE_TASK : MODE_WAKE; break;
                case "anchor": r.anchor = mod(v, 1440); break;
                case "theme": r.theme = (v >= 0 && v <= 2) ? v : THEME_AUTO; break;
                case "palette": r.palette = (v >= 0 && v < PALETTE_NAMES.length) ? v : PAL_TEAL; break;
                case "subs": r.subs.clear(); legacySubs = v < 2; break;   // the list that follows is the whole list
                case "fmt24": r.fmt24 = v == 1; break;
                case "top": r.topPct = clamp(v, 0, 35); break;
                case "bottom": r.bottomPct = clamp(v, 0, 35); break;
                case "clock": r.showClock = v == 1; break;
                case "screen": r.showScreen = v == 1; break;
                case "alarm": r.alarmOn = v == 1; break;
                case "askSilent": r.askSilent = v != 0; break;
                case "wakeDelay": r.wakeDelayMin = clamp(v, 1, 120); break;
                case "wakeChallenge": r.wakeChallenge = (v >= CHALLENGE_NONE && v <= CHALLENGE_TYPE) ? v : CHALLENGE_NONE; break;
                case "wakeOff": r.wakeOff = (v >= OFF_NONE && v <= OFF_TYPE) ? v : OFF_NONE; break;
                case "wakeQuoteOn": r.wakeQuoteOn = v != 0; break;
                case "wakeDayOff": r.wakeDayOff = v != 0; break;
                case "mark": r.showMark = v == 1; break;
                default: break;
            }
        }
        if (r.blocks.isEmpty()) return null;
        // A routine with no slots cannot be followed; drop it (days that used it fall back to the main one).
        for (int k = r.others.size(); k >= 1; k--) {
            if (r.others.get(k - 1).blocks.isEmpty()) {
                if (days != null) {
                    for (int d = 0; d < 7; d++) {
                        if (days[d] == k) days[d] = 0;
                        else if (days[d] > k) days[d]--;
                    }
                }
                r.others.remove(k - 1);
            }
        }
        if (days != null) {
            for (int d = 0; d < 7; d++) r.dayUse[d] = (days[d] >= 0 && days[d] <= r.others.size()) ? days[d] : 0;
        }
        // A temporary routine with no slots cannot be followed either.
        r.temps.removeIf(t -> t.blocks.isEmpty());
        if (legacySubs) r.addMissingDefaults();
        r.syncSubs();
        return r;
    }

    /** What the saved text holds for a block's alert: the mode, plus 10 for the wake-up slot. */
    private static int alertCode(Block b) {
        return clampAlert(b.alert) + (b.wake ? WAKE_FLAG : 0);
    }

    private static int clampAlert(int a) {
        return (a >= ALERT_SILENT && a <= ALERT_NONE) ? a : ALERT_RING;
    }

    /** Reads the alert column: 0 = silent notification (a bell that was off), 1 = ring, 2 = vibrate, 3 = none; 10 and up also marks the wake-up. */
    private static void readAlert(Block b, String field) {
        int v;
        try {
            v = Integer.parseInt(field.trim());
        } catch (NumberFormatException e) {
            v = ALERT_RING;
        }
        boolean wake = v >= WAKE_FLAG;
        if (wake) v -= WAKE_FLAG;
        b.setAlert(v);
        b.wake = wake && b.sounds();
    }

    static String cleanQuote(String t) {
        if (t == null) return "";
        String c = t.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return c.length() > MAX_QUOTE_LEN ? c.substring(0, MAX_QUOTE_LEN).trim() : c;
    }

    // ---------------------------------------------------------------- the wake-up slot

    /** Index of the wake-up slot in this routine, or -1. */
    public int wakeIndex() {
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).wake) return i;
        return -1;
    }

    /**
     * Whether slot {@code i} may be the wake-up slot: it has to sound (ring or vibrate), and no slot before it may have an
     * alert (the wake-up is the first alarm of the day).
     */
    public boolean canBeWake(int i) {
        if (i < 0 || i >= blocks.size() || !blocks.get(i).sounds()) return false;
        for (int k = 0; k < i; k++) if (blocks.get(k).alert != ALERT_NONE) return false;
        return true;
    }

    /** Makes slot {@code i} the only wake-up slot (or, with {@code on} false, takes the mark off). Returns false if it cannot be. */
    public boolean setWake(int i, boolean on) {
        for (Block b : blocks) b.wake = false;
        if (!on) return true;
        if (!canBeWake(i)) return false;
        blocks.get(i).wake = true;
        return true;
    }

    /** Keeps the rule that the wake-up is a single slot that is the first alert of the day; drops the mark where it no longer holds. */
    public void normalizeWake() {
        for (Routine r : everyRoutine()) {
            int first = -1;
            for (int i = 0; i < r.blocks.size(); i++) {
                if (r.blocks.get(i).wake) {
                    if (first < 0 && r.canBeWake(i)) first = i;
                    else r.blocks.get(i).wake = false;
                }
            }
        }
    }

    /** Takes the schedule (not the look) from another routine, for importing from the web app. */
    public void copyScheduleFrom(Routine o) {
        mode = o.mode;
        anchor = o.anchor;
        blocks.clear();
        for (Block b : o.blocks) {
            Block nb = new Block(b.kind, b.title, b.mins);
            nb.alarm = b.alarm;
            nb.alert = b.alert;
            nb.wake = b.wake;
            nb.sub = b.sub;
            nb.note = b.note;
            blocks.add(nb);
        }
        syncSubs();
    }

    /** A routine with the same look and alarm switch but no blocks. */
    public Routine copyLook() {
        Routine r = new Routine();
        r.palette = palette;
        r.theme = theme;
        r.fmt24 = fmt24;
        r.topPct = topPct;
        r.bottomPct = bottomPct;
        r.showClock = showClock;
        r.showScreen = showScreen;
        r.alarmOn = alarmOn;
        r.askSilent = askSilent;
        r.wakeDelayMin = wakeDelayMin;
        r.wakeChallenge = wakeChallenge;
        r.wakeQuote = wakeQuote;
        r.wakeOff = wakeOff;
        r.wakeQuoteOn = wakeQuoteOn;
        r.wakeDayOff = wakeDayOff;
        r.showMark = showMark;
        r.name = name;
        r.subs.clear();
        r.subs.addAll(subs);
        r.goals.addAll(goals);
        return r;
    }

    public int totalMinutes() {
        int t = 0;
        for (Block b : blocks) t += b.mins;
        return t;
    }

    public static Routine sample() {
        Routine r = new Routine();
        Object[][] s = {
            {PERSONAL, "Wake up & freshen up", 30}, {PERSONAL, "Yoga & stretching", 45}, {MEAL, "Breakfast", 30},
            {TASK, "Morning focus block", 120}, {REST, "Short break", 15},
            {TASK, "Reading & notes", 90}, {REST, "Tea break", 10},
            {TASK, "Practice problems", 90}, {MEAL, "Lunch", 60}, {REST, "Power nap", 20},
            {TASK, "Project work", 120}, {REST, "Walk & tea", 20},
            {TASK, "Revision", 90}, {PERSONAL, "Evening walk", 45}, {MEAL, "Dinner", 45},
            {TASK, "Review & plan tomorrow", 45}, {PERSONAL, "Family & free time", 55},
            {REST, "Wind down", 30}, {SLEEP, "Sleep", 480}
        };
        for (Object[] x : s) r.blocks.add(new Block((Integer) x[0], (String) x[1], (Integer) x[2]));
        String[][] types = {{"Yoga & stretching", "Exercise"}, {"Morning focus block", "Study"}, {"Reading & notes", "Study"},
            {"Practice problems", "Practice"}, {"Power nap", "Nap"}, {"Project work", "Work"}, {"Revision", "Revision"},
            {"Evening walk", "Exercise"}, {"Review & plan tomorrow", "Revision"}, {"Family & free time", "Family time"},
            {"Wake up & freshen up", "Personal"}, {"Breakfast", "Meal"}, {"Lunch", "Meal"}, {"Dinner", "Meal"}, {"Sleep", "Sleep"}};
        for (String[] t : types) {
            for (Block b : r.blocks) if (b.title.equals(t[0])) b.sub = t[1];
        }
        r.blocks.get(0).wake = true;   // the first alarm of the day is the start-of-day alarm, unless the person changes it
        return r;
    }

    // ---------------------------------------------------------------- helpers

    public static int mod(int a, int m) {
        return ((a % m) + m) % m;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.min(hi, Math.max(lo, v));
    }

    private static int clampKind(int k) {
        return (k >= 0 && k < KIND_NAMES.length) ? k : TASK;
    }

    private static int clampMins(int m) {
        return clamp(m, 1, 1440);
    }

    static String clean(String t) {
        if (t == null) return "";
        String c = t.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        return c.length() > 80 ? c.substring(0, 80) : c;
    }

    static String cleanName(String t) {
        if (t == null) return "Routine";
        String c = t.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (c.length() > MAX_NAME_LEN) c = c.substring(0, MAX_NAME_LEN).trim();
        return c.isEmpty() ? "Routine" : c;
    }

    /** Notes may hold line breaks; they are stored on one line with \\n and \\t written out. */
    static String escapeNote(String t) {
        if (t == null) return "";
        String c = t.replace("\r", "");
        if (c.length() > MAX_NOTE_LEN) c = c.substring(0, MAX_NOTE_LEN);
        return c.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
    }

    static String unescapeNote(String t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '\\' && i + 1 < t.length()) {
                char nx = t.charAt(++i);
                sb.append(nx == 'n' ? '\n' : nx == 't' ? '\t' : nx);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    static String cleanSub(String t) {
        if (t == null) return "";
        String c = t.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return c.length() > MAX_SUB_LEN ? c.substring(0, MAX_SUB_LEN).trim() : c;
    }

    private static String pad2(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
