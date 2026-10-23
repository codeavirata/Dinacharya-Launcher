package app.dinacharya.wallpaper;

import java.time.LocalDate;

/** The motivational lines shown before a wake-up is delayed. The person's own line, if they wrote one, is used instead. Uses no Android classes. */
public final class WakeQuotes {
    /** Written in English here; the app shows them in the language the person chose. */
    public static final String[] BUILT_IN = {
            "Well begun is half done.",
            "Small steps every day add up to big change.",
            "The hardest part is getting up. You have already done it.",
            "Choose what you want most over what you want right now.",
            "Your future self is watching. Make them proud.",
            "Start now. You can polish it later.",
            "One focused hour today beats ten good intentions.",
            "Rest was for the night. The morning is for action.",
            "You do not have to feel ready. You only have to begin.",
            "Every morning is a fresh page. Write something worth reading.",
            "Progress, not perfection.",
            "The day belongs to those who start it.",
            "Five more minutes of sleep will not help your goals. Five minutes of work will.",
            "Be the person your plan needs today.",
            "Wake up with a purpose and the day will make room for it."};

    /** Sentences to type when the challenge for putting the day off is on (shown in the person's language, and typed in it). */
    public static final String[] PHRASES = {
            "I am choosing to put my day off",
            "My plan is waiting for me",
            "I could start right now",
            "I am putting this off on purpose",
            "Later is my choice, not the plan"};

    private WakeQuotes() {
    }

    /** A sentence to type, in the language of the app. */
    public static String phrase(java.util.Random rnd) {
        return Tr.t(PHRASES[rnd.nextInt(PHRASES.length)]);
    }

    /** The line for a day: the person's own if there is one, else one of the built-in lines, a different one each day. */
    public static String forDay(String own, LocalDate day) {
        String mine = Routine.cleanQuote(own);
        if (!mine.isEmpty()) return mine;
        return BUILT_IN[Math.floorMod(day.getYear() * 366 + day.getDayOfYear(), BUILT_IN.length)];
    }

    /** Another line than the one for the day (shown if the person is not moved by the first), or the person's own again. */
    public static String another(String own, LocalDate day) {
        String mine = Routine.cleanQuote(own);
        if (!mine.isEmpty()) return mine;
        return BUILT_IN[Math.floorMod(day.getYear() * 366 + day.getDayOfYear() + 7, BUILT_IN.length)];
    }
}
