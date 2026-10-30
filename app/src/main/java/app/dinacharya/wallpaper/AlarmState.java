package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/** What the alarm system has to remember between alarms. Uses no Android classes. */
public final class AlarmState {
    /** When to ask the bedtime question again; 0 when no re-ask is waiting. */
    public long reaskAt;
    /** The planned bedtime of the night the re-ask is about. */
    public long reaskBedMs;
    public String reaskDay = "";
    public int reaskSrc = -1;
    public String reaskTitle = "Sleep";
    /** The time of the last alarm that was started, so the same alarm never rings twice. */
    public long lastFired;

    /** A break taken inside a slot: from, until (milliseconds), the slot it belongs to and its end. 0 when there is none. */
    public long breakFrom;
    public long breakTo;
    public String breakDay = "";
    public int breakSrc = -1;
    public String breakTitle = "";
    public long breakSlotEnd;

    /** A slot that was told to start later: when it should start (asked about again then) and what it is. 0 when none. */
    public long againAt;
    public String againDay = "";
    public int againSrc = -1;
    public String againTitle = "";
    public long againSlotStart;
    public long againSlotEnd;
    /** The later start is the wake-up one, which keeps ringing until the day is started or delayed again. */
    public boolean againWake;

    /**
     * Slots whose planned alarm must not ring because they were started before their time ("day:number"). The slot is already going,
     * so its alarm would only ask a question that was answered.
     */
    public final List<String> skipAlarms = new ArrayList<>();
    /** The schedule those marks were made on (see {@link #bindSkips}). A mark is a slot's number, so it means nothing once the schedule changed. */
    public String skipSig = "";

    /**
     * The rest of this schedule day was taken off: the day (as the plan names it), from when, and the signature of that day's schedule
     * at that moment. While the schedule is still the same no alarm rings that day; a new schedule for the day ends the day off.
     */
    public String offDay = "";
    public long offFrom;
    /** When the day that was taken off ends (milliseconds); 0 when not known. After that the day off is over by itself. */
    public long offEnd;
    public String offSig = "";
    /** When to ask again whether to plan the day off ("plan later"); 0 when no question is waiting. */
    public long planAt;

    /** Remembers that this slot's planned alarm is not wanted. */
    public void skipAlarm(String day, int src) {
        String k = day + ":" + src;
        if (!skipAlarms.contains(k)) skipAlarms.add(k);
        while (skipAlarms.size() > 40) skipAlarms.remove(0);
    }

    public boolean skipsAlarm(String day, int src) {
        return skipAlarms.contains(day + ":" + src);
    }

    /**
     * Ties the skip marks to the schedule they were made on. When the schedule is different from the one the marks belong to (a routine
     * for one day was made, a slot was added, the days of the week were changed) the marks are dropped, because the number of a slot is
     * a place in the list and would point at another slot now. Returns true when anything changed and the state is to be saved.
     */
    public boolean bindSkips(String sig) {
        String now = sig == null ? "" : sig;
        if (now.equals(skipSig)) return false;
        boolean had = !skipSig.isEmpty() && !skipAlarms.isEmpty();
        if (had) skipAlarms.clear();
        skipSig = now;
        return true;
    }

    /** Forgets skip marks for days before {@code oldestDay} (a "yyyy-mm-dd" text compares in date order). */
    public void pruneSkips(String oldestDay) {
        skipAlarms.removeIf(k -> k.indexOf(':') < 0 || k.substring(0, k.indexOf(':')).compareTo(oldestDay) < 0);
    }

    /** True while a break is running at {@code nowMs}. */
    public boolean onBreak(long nowMs) {
        return breakFrom > 0 && nowMs >= breakFrom && nowMs < breakTo;
    }

    public String serialize() {
        return "reaskAt=" + reaskAt + "\n"
                + "reaskBed=" + reaskBedMs + "\n"
                + "reaskDay=" + reaskDay + "\n"
                + "reaskSrc=" + reaskSrc + "\n"
                + "reaskTitle=" + Routine.clean(reaskTitle) + "\n"
                + "lastFired=" + lastFired + "\n"
                + "breakFrom=" + breakFrom + "\n"
                + "breakTo=" + breakTo + "\n"
                + "breakDay=" + breakDay + "\n"
                + "breakSrc=" + breakSrc + "\n"
                + "breakTitle=" + Routine.clean(breakTitle) + "\n"
                + "breakSlotEnd=" + breakSlotEnd + "\n"
                + "againAt=" + againAt + "\n"
                + "againDay=" + againDay + "\n"
                + "againSrc=" + againSrc + "\n"
                + "againTitle=" + Routine.clean(againTitle) + "\n"
                + "againSlotStart=" + againSlotStart + "\n"
                + "againSlotEnd=" + againSlotEnd + "\n"
                + "againWake=" + (againWake ? 1 : 0) + "\n"
                + "skip=" + String.join(",", skipAlarms) + "\n"
                + "skipSig=" + skipSig + "\n"
                + "offDay=" + offDay + "\n"
                + "offFrom=" + offFrom + "\n"
                + "offEnd=" + offEnd + "\n"
                + "offSig=" + offSig + "\n"
                + "planAt=" + planAt + "\n";
    }

    public static AlarmState parse(String text) {
        AlarmState s = new AlarmState();
        if (text == null) return s;
        for (String ln : text.replace("\r", "").split("\n")) {
            int eq = ln.indexOf('=');
            if (eq <= 0) continue;
            String k = ln.substring(0, eq);
            String v = ln.substring(eq + 1);
            try {
                switch (k) {
                    case "reaskAt": s.reaskAt = Long.parseLong(v.trim()); break;
                    case "reaskBed": s.reaskBedMs = Long.parseLong(v.trim()); break;
                    case "reaskDay": s.reaskDay = v.trim(); break;
                    case "reaskSrc": s.reaskSrc = Integer.parseInt(v.trim()); break;
                    case "reaskTitle": s.reaskTitle = v; break;
                    case "lastFired": s.lastFired = Long.parseLong(v.trim()); break;
                    case "breakFrom": s.breakFrom = Long.parseLong(v.trim()); break;
                    case "breakTo": s.breakTo = Long.parseLong(v.trim()); break;
                    case "breakDay": s.breakDay = v.trim(); break;
                    case "breakSrc": s.breakSrc = Integer.parseInt(v.trim()); break;
                    case "breakTitle": s.breakTitle = v; break;
                    case "breakSlotEnd": s.breakSlotEnd = Long.parseLong(v.trim()); break;
                    case "againAt": s.againAt = Long.parseLong(v.trim()); break;
                    case "againDay": s.againDay = v.trim(); break;
                    case "againSrc": s.againSrc = Integer.parseInt(v.trim()); break;
                    case "againTitle": s.againTitle = v; break;
                    case "againSlotStart": s.againSlotStart = Long.parseLong(v.trim()); break;
                    case "againSlotEnd": s.againSlotEnd = Long.parseLong(v.trim()); break;
                    case "againWake": s.againWake = v.trim().equals("1"); break;
                    case "skip":
                        for (String part : v.split(",")) {
                            String t = part.trim();
                            if (t.matches("\\d{4}-\\d{2}-\\d{2}:-?\\d+") && !s.skipAlarms.contains(t)) s.skipAlarms.add(t);
                        }
                        break;
                    case "skipSig": s.skipSig = v.trim(); break;
                    case "offDay": s.offDay = v.trim(); break;
                    case "offFrom": s.offFrom = Long.parseLong(v.trim()); break;
                    case "offEnd": s.offEnd = Long.parseLong(v.trim()); break;
                    case "offSig": s.offSig = v.trim(); break;
                    case "planAt": s.planAt = Long.parseLong(v.trim()); break;
                    default: break;
                }
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        return s;
    }
}
