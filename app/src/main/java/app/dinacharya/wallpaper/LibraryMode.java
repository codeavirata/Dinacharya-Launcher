package app.dinacharya.wallpaper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Library mode, for places where the phone must stay quiet. While it is on, every alarm of the app only vibrates (the start-of-day alarm,
 * the slot alarms, the bedtime question and the timers), no sound is played, and the groups of apps that are set to lock "in library mode"
 * are locked. It is switched on by hand for a while (or until it is switched off) and can also come on by itself at fixed hours on chosen
 * days. Optionally it also puts the phone's ringer on vibrate. Uses no Android classes.
 */
public final class LibraryMode {
    /** A manual session without an end: it lasts until it is switched off. */
    public static final long OPEN_ENDED = Long.MAX_VALUE;
    /** How far ahead an open-ended session is reported as lasting (for "locked until" lines and timers). */
    private static final long LOOKAHEAD_MS = 24L * 3600000L;
    /** The ready-made lengths of a session, in minutes; 0 means until it is switched off. */
    public static final int[] LENGTHS = {60, 120, 240, 0};

    /** On by hand until this moment (milliseconds since 1970); 0 when not; {@link #OPEN_ENDED} until switched off. */
    public long untilMs;
    /** Also on during the daily hours below, on the chosen days. */
    public boolean auto;
    /** The days the hours start on: bit 0 is Monday, bit 6 is Sunday. */
    public int days = LockRules.WEEKDAYS;
    /** The daily hours, minutes after midnight. An end at or before the start runs into the next day. */
    public int startMin = 9 * 60;
    public int endMin = 17 * 60;
    /** Put the phone's ringer on vibrate while library mode is on, and back afterwards. */
    public boolean ringer;

    /** True when library mode is on at {@code nowMs}. */
    public boolean on(long nowMs, ZoneId zone) {
        return endMs(nowMs, zone) != 0;
    }

    /**
     * When library mode ends if it is on at {@code nowMs}, otherwise 0. An open-ended session is reported as lasting a day more, so
     * a reader that wants to look again later has a time to look at. Back-to-back parts (by hand and by the clock) count as one.
     */
    public long endMs(long nowMs, ZoneId zone) {
        long manual = untilMs > nowMs ? (untilMs == OPEN_ENDED ? nowMs + LOOKAHEAD_MS : untilMs) : 0L;
        long clock = auto ? windowEnd(nowMs, zone) : 0L;
        return Math.max(manual, clock);
    }

    /** True when it was switched on by hand and has no end. */
    public boolean openEnded(long nowMs) {
        return untilMs == OPEN_ENDED;
    }

    /** Switches it on by hand for {@code minutes}, or until switched off when {@code minutes} is 0 or less. */
    public void turnOn(long nowMs, int minutes) {
        untilMs = minutes <= 0 ? OPEN_ENDED : nowMs + minutes * 60000L;
    }

    /** Switches off the session started by hand. (The daily hours, if on, still apply when they come.) */
    public void turnOff() {
        untilMs = 0L;
    }

    /** The daily hours in force at {@code nowMs}: their end, or 0 when they are not open. */
    private long windowEnd(long nowMs, ZoneId zone) {
        if (days == 0) return 0L;
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(zone);
        for (int back = 1; back >= 0; back--) {
            LocalDate d = now.toLocalDate().minusDays(back);
            if ((days & (1 << (d.getDayOfWeek().getValue() - 1))) == 0) continue;
            ZonedDateTime start = d.atStartOfDay(zone).plusMinutes(startMin);
            ZonedDateTime end = endMin > startMin
                    ? d.atStartOfDay(zone).plusMinutes(endMin)
                    : d.plusDays(1).atStartOfDay(zone).plusMinutes(endMin);
            if (!now.isBefore(start) && now.isBefore(end)) return end.toInstant().toEpochMilli();
        }
        return 0L;
    }

    /** When the daily hours next open after {@code nowMs}, or 0 when they never do. */
    public long nextStartMs(long nowMs, ZoneId zone) {
        if (!auto || days == 0) return 0L;
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(zone);
        for (int k = 0; k <= 7; k++) {
            LocalDate d = now.toLocalDate().plusDays(k);
            if ((days & (1 << (d.getDayOfWeek().getValue() - 1))) == 0) continue;
            long start = d.atStartOfDay(zone).plusMinutes(startMin).toInstant().toEpochMilli();
            if (start > nowMs) return start;
        }
        return 0L;
    }

    /**
     * The next moment library mode switches (on or off) after {@code nowMs}, or 0 when nothing is going to change. The alarms are set
     * again then, so each one is made with the right sound.
     */
    public long nextChangeMs(long nowMs, ZoneId zone) {
        if (on(nowMs, zone)) return untilMs == OPEN_ENDED ? 0L : endMs(nowMs, zone);
        return nextStartMs(nowMs, zone);
    }

    /** How an alert sounds when library mode decides: a ring becomes a vibration, the rest stays as it was. */
    public static int quietMode(LibraryMode lib, long atMs, ZoneId zone, int mode) {
        if (lib != null && mode == Routine.ALERT_RING && lib.on(atMs, zone)) return Routine.ALERT_VIBRATE;
        return mode;
    }

    // ------------------------------------------------------------------ the phone's ringer

    /** Android's ringer modes (AudioManager): silent 0, vibrate 1, normal 2. -1 stands for "none". */
    public static final int RINGER_SILENT = 0, RINGER_VIBRATE = 1, RINGER_NORMAL = 2;

    /** What to do with the ringer now, and what to remember for putting it back. */
    public static final class RingerStep {
        /** The mode to put the ringer in, or -1 to leave it. */
        public final int set;
        /** The mode to remember for later, or -1 to remember nothing (what was remembered is dropped). */
        public final int keep;

        RingerStep(int set, int keep) {
            this.set = set;
            this.keep = keep;
        }
    }

    /**
     * The ringer rule. While library mode is on and the ringer option is chosen, a ringer that is on goes to vibrate and its old mode is
     * remembered; one that was already on vibrate or silent is left alone and nothing is remembered. Once that is done it is not forced
     * again, so a person who turns the sound back on during library mode keeps it. When library mode ends, the old mode comes back, but
     * only if the ringer is still on vibrate (if the person changed it meanwhile, their choice stands).
     *
     * @param wantQuiet library mode is on and the ringer option is chosen
     * @param current   the ringer mode now
     * @param kept      the mode remembered earlier, or -1
     */
    public static RingerStep ringerStep(boolean wantQuiet, int current, int kept) {
        if (wantQuiet) {
            if (kept >= 0) return new RingerStep(-1, kept);
            if (current == RINGER_NORMAL) return new RingerStep(RINGER_VIBRATE, RINGER_NORMAL);
            return new RingerStep(-1, -1);
        }
        if (kept >= 0 && current == RINGER_VIBRATE) return new RingerStep(kept, -1);
        return new RingerStep(-1, -1);
    }

    // ------------------------------------------------------------------ saving

    private static final String MAGIC = "DINALIB1";

    public String serialize() {
        return MAGIC + "\nuntil=" + untilMs + "\nauto=" + (auto ? 1 : 0) + "\ndays=" + days + "\nstart=" + startMin + "\nend=" + endMin
                + "\nringer=" + (ringer ? 1 : 0) + "\n";
    }

    public static LibraryMode parse(String text) {
        LibraryMode m = new LibraryMode();
        if (text == null) return m;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return m;
        for (int i = 1; i < lines.length; i++) {
            int eq = lines[i].indexOf('=');
            if (eq <= 0) continue;
            String k = lines[i].substring(0, eq);
            String v = lines[i].substring(eq + 1).trim();
            try {
                switch (k) {
                    case "until": m.untilMs = Math.max(0L, Long.parseLong(v)); break;
                    case "auto": m.auto = v.equals("1"); break;
                    case "days": m.days = Math.max(0, Math.min(127, Integer.parseInt(v))); break;
                    case "start": m.startMin = Math.max(0, Math.min(1439, Integer.parseInt(v))); break;
                    case "end": m.endMin = Math.max(0, Math.min(1439, Integer.parseInt(v))); break;
                    case "ringer": m.ringer = v.equals("1"); break;
                    default: break;
                }
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        return m;
    }

    public LibraryMode copy() {
        return parse(serialize());
    }

    /** One line for the settings: "On until 5:00 PM", "On until you switch it off", "Next: Mon 9:00 AM" or "Off". */
    public String status(long nowMs, ZoneId zone, boolean h24) {
        if (on(nowMs, zone)) {
            if (untilMs == OPEN_ENDED) return "On until you switch it off";
            long end = endMs(nowMs, zone);
            return "On until " + LockRules.untilText(end, nowMs, zone, h24);
        }
        long next = nextStartMs(nowMs, zone);
        if (next != 0L) return "Off. Next: " + LockRules.untilText(next, nowMs, zone, h24);
        return "Off";
    }
}
