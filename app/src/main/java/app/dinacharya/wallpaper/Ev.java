package app.dinacharya.wallpaper;

/** One thing that happened, kept in the on-phone history. Uses no Android classes. */
public final class Ev {
    /** Answer to a slot alarm. a = C (continue), R (renamed) or S (taken off); b = the new name. */
    public static final String ACK = "ack";
    /** An alarm that rang and was never answered. */
    public static final String MISSED = "missed";
    /** Going to sleep. ts = when; a = planned bedtime (ms); b = "option:shiftMinutes". */
    public static final String SLEEP = "sleep";
    /** "More time" at bedtime. a = minutes until the next question. */
    public static final String SNOOZE = "snooze";
    /** The alarm switch was changed. a = 1 or 0. */
    public static final String ALARMS = "alarms";
    /** The home screen paused you before an app. a = B (you went back) or O (you opened it after the pause); b = package; title = app name. */
    public static final String LAUNCH = "launch";

    /**
     * A break taken inside a slot. ts = when it began; a = when it ends (milliseconds; written again with the same ts if it ended
     * early); src = the slot; title = the slot's name.
     */
    public static final String BREAK = "break";
    /** A slot that was told to start later. ts = when it was answered; a = when work starts (milliseconds); src = the slot. */
    public static final String DELAY = "delay";
    /**
     * The slot before was carried on into this one. ts = when it was answered; a = until when (milliseconds); src = the slot that
     * was starting; b = the number of the slot that went on; title = that slot's name.
     */
    public static final String EXTEND = "extend";
    /**
     * A slot was finished early and the next one began before its planned time. ts = when it was answered; a = the moment the slot
     * before ended and this one began (milliseconds, earlier than this slot's planned start); src = the slot that begins; b = the number
     * of the slot that ended early; title = that slot's name.
     */
    public static final String EARLY = "early";
    /**
     * A slot was skipped when it began, and the next one began at once. ts = when it was answered; a = the moment the next slot began
     * (milliseconds, earlier than its planned start); src = the slot that was skipped; b = the number of the slot that begins; title =
     * the skipped slot's name.
     */
    public static final String SKIP = "skip";
    /**
     * The person woke before the alarm and started the day. ts = when; day = the day that began; src = its first slot; a = when the day
     * was to begin (milliseconds); b = how many minutes earlier it began; title = the first slot's name.
     */
    public static final String WOKE = "woke";
    /**
     * The rest of the schedule day was taken off (a = the schedule day's end in milliseconds), or the day off was ended because the day was
     * planned again or the person went back to the schedule (a = "0"). ts = when; src = -1.
     */
    public static final String DAYOFF = "dayoff";

    public final long ts;
    public final String type;
    public final String day;
    public final int src;
    public final String a;
    public final String b;
    public final String title;

    public Ev(long ts, String type, String day, int src, String a, String b, String title) {
        this.ts = ts;
        this.type = type;
        this.day = day == null ? "" : day;
        this.src = src;
        this.a = Routine.clean(a == null ? "" : a);
        this.b = Routine.clean(b == null ? "" : b);
        this.title = Routine.clean(title == null ? "" : title);
    }

    public static Ev ack(long ts, String day, int src, String title, String action, String newName) {
        return new Ev(ts, ACK, day, src, action, newName, title);
    }

    public static Ev missed(long ts, String day, int src, String title) {
        return new Ev(ts, MISSED, day, src, "", "", title);
    }

    public static Ev sleep(long ts, String day, long plannedBedMs, int option, int shiftMin) {
        return new Ev(ts, SLEEP, day, -1, String.valueOf(plannedBedMs), option + ":" + shiftMin, "");
    }

    public static Ev snooze(long ts, String day, int minutes) {
        return new Ev(ts, SNOOZE, day, -1, String.valueOf(minutes), "", "");
    }

    public static Ev launch(long ts, String day, int src, String pkg, String label, boolean wentBack) {
        return new Ev(ts, LAUNCH, day, src, wentBack ? "B" : "O", pkg, label);
    }

    public static Ev breakTaken(long from, long until, String day, int src, String title) {
        return new Ev(from, BREAK, day, src, String.valueOf(until), "", title);
    }

    public static Ev delay(long ts, String day, int src, String title, long workStartMs) {
        return new Ev(ts, DELAY, day, src, String.valueOf(workStartMs), "", title);
    }

    public static Ev extend(long ts, String day, int src, int prevSrc, String prevTitle, long untilMs) {
        return new Ev(ts, EXTEND, day, src, String.valueOf(untilMs), String.valueOf(prevSrc), prevTitle);
    }

    public static Ev early(long ts, String day, int src, int prevSrc, String prevTitle, long boundaryMs) {
        return new Ev(ts, EARLY, day, src, String.valueOf(boundaryMs), String.valueOf(prevSrc), prevTitle);
    }

    public static Ev skip(long ts, String day, int src, String title, int nextSrc, long boundaryMs) {
        return new Ev(ts, SKIP, day, src, String.valueOf(boundaryMs), String.valueOf(nextSrc), title);
    }

    public static Ev woke(long ts, String day, int firstSrc, String firstTitle, long plannedStartMs, int minutesEarlier) {
        return new Ev(ts, WOKE, day, firstSrc, String.valueOf(plannedStartMs), String.valueOf(minutesEarlier), firstTitle);
    }

    public static Ev dayOff(long ts, String day, long dayEndMs) {
        return new Ev(ts, DAYOFF, day, -1, String.valueOf(Math.max(1L, dayEndMs)), "", "");
    }

    public static Ev dayOffEnded(long ts, String day) {
        return new Ev(ts, DAYOFF, day, -1, "0", "", "");
    }

    public static Ev alarms(long ts, boolean on) {
        return new Ev(ts, ALARMS, "", -1, on ? "1" : "0", "", "");
    }

    public long aLong(long dflt) {
        try {
            return Long.parseLong(a);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    public String line() {
        return ts + "\t" + type + "\t" + day + "\t" + src + "\t" + a + "\t" + b + "\t" + title;
    }

    public static Ev parse(String line) {
        if (line == null) return null;
        String[] p = line.split("\t", 7);
        if (p.length < 7) return null;
        try {
            return new Ev(Long.parseLong(p[0]), p[1], p[2], Integer.parseInt(p[3]), p[4], p[5], p[6]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
