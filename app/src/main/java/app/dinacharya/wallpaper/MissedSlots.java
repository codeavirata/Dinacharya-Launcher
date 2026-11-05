package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The slots whose alarm went off and were never answered, newest first: what the alarm screen offers to "Start it now" when the next
 * slot's alarm rings. A slot is open while it has a ring on record and no answer; any answer (start, change, skip) closes it. Uses no
 * Android classes.
 */
public final class MissedSlots {
    /** Only slots whose alarm rang within this time are offered: later than that, starting it is more a new plan than a catch-up. */
    public static final long WITHIN_MS = 3 * 3600000L;

    private MissedSlots() {
    }

    /** One slot that rang and was not answered. */
    public static final class Key {
        public final String day;
        public final int src;
        public final long rangMs;

        Key(String day, int src, long rangMs) {
            this.day = day;
            this.src = src;
            this.rangMs = rangMs;
        }
    }

    /**
     * The open slots of {@code day} whose alarm rang within {@code withinMs} before {@code nowMs}, the one that rang last first. The slot
     * {@code exceptSrc} (the one that is asking now) is left out.
     */
    public static List<Key> pending(List<Ev> events, String day, int exceptSrc, long nowMs, long withinMs) {
        List<Key> out = new ArrayList<>();
        if (events == null || day == null) return out;
        List<Integer> seen = new ArrayList<>();
        for (Ev e : events) {
            if (!Ev.RANG.equals(e.type) || e.src < 0 || e.src == exceptSrc || !day.equals(e.day)) continue;
            if (e.ts > nowMs || nowMs - e.ts > withinMs || seen.contains(e.src)) continue;
            seen.add(e.src);
            long rang = 0L;
            boolean answered = false;
            for (Ev x : events) {
                if (x.src != e.src || !day.equals(x.day)) continue;
                if (Ev.RANG.equals(x.type) && x.ts <= nowMs) rang = Math.max(rang, x.ts);
                if (Ev.ACK.equals(x.type) && x.ts <= nowMs) answered = true;
            }
            if (!answered && rang > 0L && nowMs - rang <= withinMs) out.add(new Key(day, e.src, rang));
        }
        Collections.sort(out, (a, b) -> Long.compare(b.rangMs, a.rangMs));
        return out;
    }
}
