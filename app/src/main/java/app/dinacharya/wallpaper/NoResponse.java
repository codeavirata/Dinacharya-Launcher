package app.dinacharya.wallpaper;

import java.util.List;

/**
 * "No response": a slot's alarm or notification went off and the person did not answer. The slot then counts from the moment of the answer
 * (or never, when it is not answered), and the time before that is marked "No response". An answer within {@link #GRACE_MS} of the alarm is
 * quick enough to count as none. Uses no Android classes.
 */
public final class NoResponse {
    /** Answering within this time of the alarm is not "no response". */
    public static final long GRACE_MS = 3 * 60000L;
    /** The answer is still to come. */
    public static final long PENDING = Long.MAX_VALUE;

    private NoResponse() {
    }

    /**
     * When the no-response stretch of slot {@code src} ends, given the events of its day: the time of the first answer, {@link #PENDING}
     * when it has not been answered yet, or 0 when there is none (no alarm went off, it was answered in time, or the slot began before its
     * alarm). The stretch starts where the slot starts.
     */
    public static long until(List<Ev> dayEvents, int src, long nowMs) {
        if (dayEvents == null || src < 0) return 0L;
        long rang = 0L;
        long answered = 0L;
        for (Ev e : dayEvents) {
            if (e.src != src || e.ts > nowMs) continue;
            if (Ev.RANG.equals(e.type) && (rang == 0L || e.ts < rang)) rang = e.ts;
            if (Ev.ACK.equals(e.type) && (answered == 0L || e.ts < answered)) answered = e.ts;
        }
        if (rang == 0L) return 0L;
        if (answered != 0L) return answered - rang > GRACE_MS ? answered : 0L;
        return nowMs - rang > GRACE_MS ? PENDING : 0L;
    }
}
