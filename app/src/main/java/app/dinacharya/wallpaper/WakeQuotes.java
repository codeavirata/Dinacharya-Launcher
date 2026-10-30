package app.dinacharya.wallpaper;

import java.time.LocalDate;

/**
 * The motivational lines of the start-of-day alarm. The person's own line, if they wrote one, is used instead. A different line comes up each
 * day, and "another quote" steps on to the next. Uses no Android classes.
 */
public final class WakeQuotes {
    /** A line and who said it (empty for sayings that belong to nobody in particular). */
    public static final class Quote {
        public final String text;
        public final String author;

        Quote(String text, String author) {
            this.text = text;
            this.author = author;
        }
    }

    private static Quote q(String text, String author) {
        return new Quote(text, author);
    }

    /** Written in English here; the app shows them in the language the person chose. The names stay as they are. */
    public static final Quote[] LIST = {
            q("Well begun is half done.", ""),
            q("Small steps every day add up to big change.", ""),
            q("The hardest part is getting up. You have already done it.", ""),
            q("Choose what you want most over what you want right now.", ""),
            q("Your future self is watching. Make them proud.", ""),
            q("Start now. You can polish it later.", ""),
            q("One focused hour today beats ten good intentions.", ""),
            q("Rest was for the night. The morning is for action.", ""),
            q("You do not have to feel ready. You only have to begin.", ""),
            q("Every morning is a fresh page. Write something worth reading.", ""),
            q("Progress, not perfection.", ""),
            q("The day belongs to those who start it.", ""),
            q("Five more minutes of sleep will not help your goals. Five minutes of work will.", ""),
            q("Be the person your plan needs today.", ""),
            q("Wake up with a purpose and the day will make room for it.", ""),
            q("The journey of a thousand miles begins with a single step.", "Lao Tzu"),
            q("It does not matter how slowly you go as long as you do not stop.", "Confucius"),
            q("Arise, awake, and stop not till the goal is reached.", "Swami Vivekananda"),
            q("Dream, dream, dream. Dreams transform into thoughts and thoughts result in action.", "A. P. J. Abdul Kalam"),
            q("If you want to shine like a sun, first burn like a sun.", "A. P. J. Abdul Kalam"),
            q("Look to this day, for it is life, the very life of life.", "Kalidasa"),
            q("At dawn, when you have trouble getting out of bed, tell yourself: I have to go to work as a human being.", "Marcus Aurelius"),
            q("Begin at once to live, and count each separate day as a separate life.", "Seneca"),
            q("While we are postponing, life speeds by.", "Seneca"),
            q("The early morning has gold in its mouth.", "Benjamin Franklin"),
            q("You may delay, but time will not.", "Benjamin Franklin"),
            q("Lost time is never found again.", "Benjamin Franklin"),
            q("Energy and persistence conquer all things.", "Benjamin Franklin"),
            q("We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant"),
            q("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
            q("It always seems impossible until it is done.", "Nelson Mandela"),
            q("Courage is not the absence of fear, but the triumph over it.", "Nelson Mandela"),
            q("Start where you are. Use what you have. Do what you can.", "Arthur Ashe"),
            q("Make each day your masterpiece.", "John Wooden"),
            q("Success is the sum of small efforts, repeated day in and day out.", "Robert Collier"),
            q("How we spend our days is, of course, how we spend our lives.", "Annie Dillard"),
            q("Do not watch the clock; do what it does. Keep going.", "Sam Levenson"),
            q("Discipline is the bridge between goals and accomplishment.", "Jim Rohn"),
            q("The only way to do great work is to love what you do.", "Steve Jobs"),
            q("Small deeds done are better than great deeds planned.", "Peter Marshall"),
            q("What you do today can improve all your tomorrows.", "Ralph Marston"),
            q("A year from now you may wish you had started today.", "Karen Lamb"),
            q("We must use time as a tool, not as a couch.", "John F. Kennedy"),
            q("The key is not to prioritize what is on your schedule, but to schedule your priorities.", "Stephen Covey"),
            q("Each day is a little life.", "Arthur Schopenhauer"),
            q("With the new day comes new strength and new thoughts.", "Eleanor Roosevelt"),
            q("The sun itself is weak when it first rises, and gathers strength and courage as the day goes on.", "Charles Dickens"),
            q("Nothing will work unless you do.", "Maya Angelou"),
            q("Happiness depends upon ourselves.", "Aristotle")};

    /** The same lines without their names (what older versions of the app listed). */
    public static final String[] BUILT_IN = texts();

    private static String[] texts() {
        String[] t = new String[LIST.length];
        for (int i = 0; i < LIST.length; i++) t[i] = LIST[i].text;
        return t;
    }

    /** The step from one quote to the next: it shares no factor with the number of quotes, so "another quote" never repeats before all were seen. */
    static final int STEP = 13;

    /** Sentences to type when the challenge for putting the day off is on (shown in the person's language, and typed in it). */
    public static final String[] PHRASES = {
            "I am choosing to put my day off",
            "My plan is waiting for me",
            "I could start right now",
            "I am putting this off on purpose",
            "Later is my choice, not the plan"};

    /** Sentences to type to start the day, when that is the challenge (a promise, not an excuse). */
    public static final String[] START_PHRASES = {
            "I am awake and ready to begin",
            "Today I do what I planned",
            "I start my day on purpose",
            "A good day begins right now",
            "I rise and I start"};

    private WakeQuotes() {
    }

    /** A sentence to type, in the language of the app. */
    public static String phrase(java.util.Random rnd) {
        return Tr.t(PHRASES[rnd.nextInt(PHRASES.length)]);
    }

    /** A sentence to type to start the day, in the language of the app. */
    public static String startPhrase(java.util.Random rnd) {
        return Tr.t(START_PHRASES[rnd.nextInt(START_PHRASES.length)]);
    }

    /**
     * The quote for a day: the person's own line if there is one, else one of the built-in lines, a different one each day.
     * {@code n} = 0 is the quote of the day, 1 the next one, and so on ("another quote").
     */
    public static Quote quote(String own, LocalDate day, int n) {
        String mine = Routine.cleanQuote(own);
        if (!mine.isEmpty()) return new Quote(mine, "");
        int dayIndex = day.getYear() * 366 + day.getDayOfYear();
        return LIST[Math.floorMod(dayIndex + Math.floorMod(n, LIST.length) * STEP, LIST.length)];
    }

    /** The line for a day: the person's own if there is one, else one of the built-in lines, a different one each day. */
    public static String forDay(String own, LocalDate day) {
        return quote(own, day, 0).text;
    }

    /** Another line than the one for the day (shown if the person is not moved by the first), or the person's own again. */
    public static String another(String own, LocalDate day) {
        return quote(own, day, 1).text;
    }
}
