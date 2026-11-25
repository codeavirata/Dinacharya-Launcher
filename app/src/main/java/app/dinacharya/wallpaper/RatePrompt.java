package app.dinacharya.wallpaper;

/**
 * When the Home tab asks, once in a while, for a rating on Google Play. It asks only after the app has been in use for a while (on
 * {@link #MIN_DAYS_USED} different days, the first of them at least {@link #MIN_AGE_DAYS} days ago), never twice in {@link #SNOOZE_DAYS}
 * days, and stops for good once the person rates, or has said "Not now" {@link #MAX_LATER} times. The card asks nothing about how the
 * person likes the app (Google Play's rules): it only offers the way to Google Play. Days are counted as epoch days. Uses no Android
 * classes.
 */
public final class RatePrompt {
    public static final int MIN_DAYS_USED = 5;
    public static final int MIN_AGE_DAYS = 7;
    public static final int SNOOZE_DAYS = 14;
    public static final int MAX_LATER = 2;

    private static final String MAGIC = "DINARATE1";

    /** The first day the app was used, and the last one; 0 when not yet known (stored as epoch day + 1). */
    long firstDay;
    long lastDay;
    /** On how many different days the app was opened. */
    int daysUsed;
    /** The card does not come back before this day (epoch day + 1; 0: no wait). */
    long snoozeUntil;
    /** How many times the person said "Not now". */
    int laters;
    /** Rated, or asked enough: the card never comes back. */
    boolean done;

    /** Notes that the app was opened on {@code today} (an epoch day). */
    public void used(long today) {
        long d = today + 1;
        if (firstDay == 0) firstDay = d;
        if (d != lastDay) {
            if (d > lastDay) daysUsed++;
            lastDay = Math.max(lastDay, d);
        }
    }

    /** True when the card should be on Home on {@code today}. */
    public boolean due(long today) {
        long d = today + 1;
        return !done && firstDay != 0 && daysUsed >= MIN_DAYS_USED && d - firstDay >= MIN_AGE_DAYS && d >= snoozeUntil;
    }

    /** "Not now": the card goes away for a while, or for good after the last time. */
    public void later(long today) {
        laters++;
        if (laters >= MAX_LATER) done = true;
        else snoozeUntil = today + 1 + SNOOZE_DAYS;
    }

    /** The person went to Google Play (from the card or from Settings): the card does not come back. */
    public void rated() {
        done = true;
    }

    public boolean done() {
        return done;
    }

    public String serialize() {
        return MAGIC + "\nfirst=" + firstDay + "\nlast=" + lastDay + "\ndays=" + daysUsed + "\nsnooze=" + snoozeUntil + "\nlater=" + laters
                + "\ndone=" + (done ? 1 : 0) + "\n";
    }

    /** Reads text made by {@link #serialize()}; anything missing or damaged starts again from nothing. */
    public static RatePrompt parse(String text) {
        RatePrompt r = new RatePrompt();
        if (text == null) return r;
        String[] lines = text.replace("\r", "").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(MAGIC)) return r;
        for (int i = 1; i < lines.length; i++) {
            int eq = lines[i].indexOf('=');
            if (eq <= 0) continue;
            String k = lines[i].substring(0, eq);
            String v = lines[i].substring(eq + 1).trim();
            try {
                switch (k) {
                    case "first": r.firstDay = Math.max(0L, Long.parseLong(v)); break;
                    case "last": r.lastDay = Math.max(0L, Long.parseLong(v)); break;
                    case "days": r.daysUsed = Math.max(0, Integer.parseInt(v)); break;
                    case "snooze": r.snoozeUntil = Math.max(0L, Long.parseLong(v)); break;
                    case "later": r.laters = Math.max(0, Integer.parseInt(v)); break;
                    case "done": r.done = v.equals("1"); break;
                    default: break;
                }
            } catch (NumberFormatException e) {
                // keep the default for a damaged line
            }
        }
        return r;
    }
}
