package app.dinacharya.wallpaper;

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
                + "againWake=" + (againWake ? 1 : 0) + "\n";
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
                    default: break;
                }
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        return s;
    }
}
