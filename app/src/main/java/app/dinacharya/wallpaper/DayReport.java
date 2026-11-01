package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/** What one day looked like: the plan as it ran, phone use, sleep, a score and suggestions. */
public final class DayReport {
    public static final int KEPT = 0, CHANGED = 1, SKIPPED = 2, UNANSWERED = 3, UNTRACKED = 4, UPCOMING = 5;
    public static final String[] STATUS_NAMES = {"Kept", "Changed", "Taken off", "No answer", "Not tracked", "Ahead"};

    private static final String MAGIC = "DINAREPORT1";

    public static final class Row {
        public int kind;
        public int status;
        public long startMs;
        public long endMs;
        public int plannedMin;
        public long phoneMs;
        /** How much of the slot has already passed. */
        public long activeMs;
        public String title = "";
        /** What it was renamed to for the day, or empty. */
        public String newTitle = "";
        /** The slot's activity type, such as "Study", or empty. */
        public String sub = "";
        /** Time inside the slot spent on a break, with the start put off, and given to the slot before it. */
        public long breakMs;
        public long delayMs;
        /** Time before the slot was answered, when its alarm went off and nobody responded ("No response"). */
        public long noRespMs;
        public long lentMs;
        /** Time after the slot's end that it went on, carried into the next slot. */
        public long extMs;
        /** The apps used while the slot's own activity was going on (breaks and put-off time left out), longest first. */
        public final List<App> apps = new ArrayList<>();
        /** Time in apps chosen as part of the slot's activity (a part of {@link #phoneMs}); 0 unless phone use in slots was on. */
        public long partMs;
        /** The longest stretch of the slot with no other phone use; -1 when not known (an older report, or no phone use data). */
        public long longestFocusMs = -1;
        /** How many separate stretches of other phone use there were; {@link #uses} may hold fewer. */
        public int useCount;
        /** The stretches of other phone use with their exact times, in order. */
        public final List<SlotUse.Episode> uses = new ArrayList<>();

        /** Time in other apps: phone use that was not part of the activity. */
        public long offMs() {
            return Math.max(0, phoneMs - partMs);
        }

        /** The slot's time that went to its activity: its worked time without the other phone use. */
        public long devotedMs() {
            return Math.max(0, activeMs - offMs());
        }

        /** 0..100: the share of the slot's time with no other phone use; -1 when the slot has not run. */
        public int focusPct() {
            if (activeMs <= 0) return -1;
            double f = 100.0 * (activeMs - Math.min(activeMs, offMs())) / activeMs;
            return (int) Math.max(0, Math.min(100, Math.round(f)));
        }

        /** The slot's own time that has passed: its length so far without breaks and the put-off start. */
        public long workedMs() {
            return activeMs;
        }

        /** Worked time with no app in use. */
        public long noAppMs() {
            return Math.max(0, activeMs - phoneMs);
        }
    }

    public static final class App {
        public final String name;
        public final long ms;

        public App(String name, long ms) {
            this.name = Routine.clean(name);
            this.ms = ms;
        }
    }

    public String day = "";
    public long startMs;
    public long endMs;
    public boolean partial;
    public boolean alarmsOn;
    public boolean phoneKnown;
    public long phoneMs;
    /** Phone use in each hour, counted from the start of the day. */
    public long[] hourMs = new long[24];
    public long plannedBedMs = -1;
    public long actualBedMs = -1;
    public int plannedSleepMin;
    public int snoozes;
    public int snoozeMinutes;
    public long beforeBedMs = -1;
    public int routineTotalMin;
    /** At the home screen: how many times a focus pause was shown, how often you went back, and how often you opened the app after it. */
    public int pauses;
    public int wentBack;
    public int openedAfter;
    /** The app that was paused most often, or empty. */
    public String pausedMost = "";
    public int planKept = -1;
    public int focus = -1;
    public int sleep = -1;
    public int score = -1;
    public final List<Row> rows = new ArrayList<>();
    public final List<App> apps = new ArrayList<>();
    public final List<String> tips = new ArrayList<>();
    /** The daily targets that were set on this day. */
    public final List<Routine.Goal> goals = new ArrayList<>();
    /** The schedule of the day in the same text form the app saves routines in, for backup. */
    public String schedule = "";

    public String serialize() {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        sb.append("day=").append(day).append('\n');
        sb.append("start=").append(startMs).append('\n');
        sb.append("end=").append(endMs).append('\n');
        sb.append("partial=").append(partial ? 1 : 0).append('\n');
        sb.append("alarms=").append(alarmsOn ? 1 : 0).append('\n');
        sb.append("phoneKnown=").append(phoneKnown ? 1 : 0).append('\n');
        sb.append("phone=").append(phoneMs).append('\n');
        sb.append("hours=");
        for (int i = 0; i < 24; i++) sb.append(i == 0 ? "" : ",").append(hourMs[i]);
        sb.append('\n');
        sb.append("bedPlanned=").append(plannedBedMs).append('\n');
        sb.append("bedActual=").append(actualBedMs).append('\n');
        sb.append("sleepPlanned=").append(plannedSleepMin).append('\n');
        sb.append("snoozes=").append(snoozes).append('\n');
        sb.append("snoozeMin=").append(snoozeMinutes).append('\n');
        sb.append("beforeBed=").append(beforeBedMs).append('\n');
        sb.append("total=").append(routineTotalMin).append('\n');
        sb.append("pauses=").append(pauses).append('\n');
        sb.append("wentBack=").append(wentBack).append('\n');
        sb.append("openedAfter=").append(openedAfter).append('\n');
        sb.append("pausedMost=").append(Routine.clean(pausedMost)).append('\n');
        sb.append("planKept=").append(planKept).append('\n');
        sb.append("focus=").append(focus).append('\n');
        sb.append("sleep=").append(sleep).append('\n');
        sb.append("score=").append(score).append('\n');
        for (Row r : rows) {
            sb.append("R\t").append(r.kind).append('\t').append(r.status).append('\t').append(r.startMs).append('\t')
                    .append(r.endMs).append('\t').append(r.plannedMin).append('\t').append(r.phoneMs).append('\t')
                    .append(r.activeMs).append('\t').append(Routine.clean(r.title)).append('\t')
                    .append(Routine.clean(r.newTitle)).append('\t').append(Routine.cleanSub(r.sub)).append('\n');
        }
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.breakMs != 0 || r.delayMs != 0 || r.lentMs != 0 || r.extMs != 0 || r.noRespMs != 0) {
                sb.append("RX\t").append(i).append('\t').append(r.breakMs).append('\t').append(r.delayMs).append('\t')
                        .append(r.lentMs).append('\t').append(r.extMs).append('\t').append(r.noRespMs).append('\n');
            }
            for (App a : r.apps) sb.append("RA\t").append(i).append('\t').append(a.ms).append('\t').append(a.name).append('\n');
            if (r.partMs != 0 || r.longestFocusMs >= 0 || r.useCount != 0) {
                sb.append("RF\t").append(i).append('\t').append(r.partMs).append('\t').append(r.longestFocusMs).append('\t')
                        .append(r.useCount).append('\n');
            }
            for (SlotUse.Episode e : r.uses) {
                sb.append("RU\t").append(i).append('\t').append(e.startMs).append('\t').append(e.endMs).append('\t')
                        .append(Routine.clean(e.app)).append('\n');
            }
        }
        for (Routine.Goal g : goals) {
            sb.append("G\t").append(g.kind).append('\t').append(g.mins).append('\t').append(g.sub);
            if (g.bound()) sb.append('\t').append(g.slot);   // the slot comes last, so a report from before still reads the same
            sb.append('\n');
        }
        for (App a : apps) sb.append("A\t").append(a.ms).append('\t').append(a.name).append('\n');
        for (String t : tips) sb.append("T\t").append(Routine.clean(t)).append('\n');
        if (!schedule.isEmpty()) {
            for (String ln : schedule.replace("\r", "").split("\n")) sb.append("Z\t").append(ln).append('\n');
        }
        return sb.toString();
    }

    /** Reads text made by {@link #serialize()}; null if it is not a report. */
    public static DayReport parse(String text) {
        if (text == null) return null;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return null;
        DayReport r = new DayReport();
        StringBuilder snap = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            String ln = lines[i];
            try {
                if (ln.startsWith("R\t")) {
                    String[] p = ln.split("\t", 11);
                    Row row = new Row();
                    row.kind = Integer.parseInt(p[1]);
                    row.status = Integer.parseInt(p[2]);
                    row.startMs = Long.parseLong(p[3]);
                    row.endMs = Long.parseLong(p[4]);
                    row.plannedMin = Integer.parseInt(p[5]);
                    row.phoneMs = Long.parseLong(p[6]);
                    row.activeMs = Long.parseLong(p[7]);
                    row.title = p[8];
                    row.newTitle = p.length > 9 ? p[9] : "";
                    row.sub = p.length > 10 ? Routine.cleanSub(p[10]) : "";
                    r.rows.add(row);
                } else if (ln.startsWith("RX\t")) {
                    String[] p = ln.split("\t", 7);
                    Row row = r.rows.get(Integer.parseInt(p[1].trim()));
                    row.breakMs = Long.parseLong(p[2].trim());
                    row.delayMs = Long.parseLong(p[3].trim());
                    row.lentMs = Long.parseLong(p[4].trim());
                    row.extMs = Long.parseLong(p[5].trim());
                    row.noRespMs = p.length > 6 ? Long.parseLong(p[6].trim()) : 0L;
                } else if (ln.startsWith("RA\t")) {
                    String[] p = ln.split("\t", 4);
                    r.rows.get(Integer.parseInt(p[1].trim())).apps.add(new App(p[3], Long.parseLong(p[2].trim())));
                } else if (ln.startsWith("RF\t")) {
                    String[] p = ln.split("\t", 5);
                    Row row = r.rows.get(Integer.parseInt(p[1].trim()));
                    row.partMs = Long.parseLong(p[2].trim());
                    row.longestFocusMs = Long.parseLong(p[3].trim());
                    row.useCount = Integer.parseInt(p[4].trim());
                } else if (ln.startsWith("RU\t")) {
                    String[] p = ln.split("\t", 5);
                    Row row = r.rows.get(Integer.parseInt(p[1].trim()));
                    row.uses.add(new SlotUse.Episode(Long.parseLong(p[2].trim()), Long.parseLong(p[3].trim()), p.length > 4 ? p[4] : ""));
                } else if (ln.startsWith("G\t")) {
                    String[] p = ln.split("\t", 5);
                    Routine.Goal g = new Routine.Goal(Integer.parseInt(p[1]), p[3], Integer.parseInt(p[2]));
                    if (p.length > 4) g.slot = Routine.cleanSlot(p[4]);
                    r.goals.add(g);
                } else if (ln.startsWith("A\t")) {
                    String[] p = ln.split("\t", 3);
                    r.apps.add(new App(p[2], Long.parseLong(p[1])));
                } else if (ln.startsWith("T\t")) {
                    r.tips.add(ln.substring(2));
                } else if (ln.startsWith("Z\t")) {
                    snap.append(ln.substring(2)).append('\n');
                } else {
                    int eq = ln.indexOf('=');
                    if (eq <= 0) continue;
                    String k = ln.substring(0, eq);
                    String v = ln.substring(eq + 1).trim();
                    switch (k) {
                        case "day": r.day = v; break;
                        case "start": r.startMs = Long.parseLong(v); break;
                        case "end": r.endMs = Long.parseLong(v); break;
                        case "partial": r.partial = v.equals("1"); break;
                        case "alarms": r.alarmsOn = v.equals("1"); break;
                        case "phoneKnown": r.phoneKnown = v.equals("1"); break;
                        case "phone": r.phoneMs = Long.parseLong(v); break;
                        case "hours": {
                            String[] h = v.split(",");
                            for (int j = 0; j < 24 && j < h.length; j++) r.hourMs[j] = Long.parseLong(h[j].trim());
                            break;
                        }
                        case "bedPlanned": r.plannedBedMs = Long.parseLong(v); break;
                        case "bedActual": r.actualBedMs = Long.parseLong(v); break;
                        case "sleepPlanned": r.plannedSleepMin = Integer.parseInt(v); break;
                        case "snoozes": r.snoozes = Integer.parseInt(v); break;
                        case "snoozeMin": r.snoozeMinutes = Integer.parseInt(v); break;
                        case "beforeBed": r.beforeBedMs = Long.parseLong(v); break;
                        case "total": r.routineTotalMin = Integer.parseInt(v); break;
                        case "pauses": r.pauses = Integer.parseInt(v); break;
                        case "wentBack": r.wentBack = Integer.parseInt(v); break;
                        case "openedAfter": r.openedAfter = Integer.parseInt(v); break;
                        case "pausedMost": r.pausedMost = v; break;
                        case "planKept": r.planKept = Integer.parseInt(v); break;
                        case "focus": r.focus = Integer.parseInt(v); break;
                        case "sleep": r.sleep = Integer.parseInt(v); break;
                        case "score": r.score = Integer.parseInt(v); break;
                        default: break;
                    }
                }
            } catch (RuntimeException ignored) {
                // skip a damaged line
            }
        }
        r.schedule = snap.toString();
        return r.day.isEmpty() ? null : r;
    }
}
