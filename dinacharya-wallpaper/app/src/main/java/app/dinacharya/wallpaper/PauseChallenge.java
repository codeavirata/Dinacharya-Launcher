package app.dinacharya.wallpaper;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Random;

/**
 * The playful ways to pause before an app opens: a quick sum, typing a sentence, a few slow breaths, or saying why you
 * are opening it. All but the plain wait are part of Dinacharya Pro. They are friction, never a lock: the screen always has
 * Go back, and a way past once a longer wait is over. Uses no Android classes.
 */
public final class PauseChallenge {
    public static final int WAIT = 0, MATH = 1, TYPE = 2, BREATHE = 3, WHY = 4;
    public static final String[] NAMES = {"Wait", "Quick sum", "Type a sentence", "Breathe", "Say why"};
    public static final String[] BLURBS = {
            "Count down a few seconds",
            "Solve a simple sum to open the app",
            "Type a short sentence on purpose",
            "Take slow breaths with a moving circle",
            "Pick why you are opening it"};

    /** The sentences to type: short, calm, and a little bit of a promise. */
    private static final String[] PHRASES = {
            "I choose to open this app",
            "I am in control of my time",
            "This can wait a few minutes",
            "I am opening this on purpose",
            "I will come back to my day"};

    /** What a person can say about why. Only the first one opens the app straight away. */
    public static final String[] REASONS = {
            "I need it for something specific",
            "I am bored",
            "It is just a habit",
            "I am avoiding something"};
    public static final String[] REASON_TIPS = {
            "",
            "Boredom passes. Stand up and stretch for a minute, then decide.",
            "Habits fade once you notice them. Take one slow breath first.",
            "Do the smallest next step of your task for two minutes. Then decide."};
    /** Seconds to sit with a reason that is not a real need, before the app can open. */
    public static final int REASON_WAIT_SEC = 6;

    private PauseChallenge() {
    }

    public static boolean valid(int challenge) {
        return challenge >= WAIT && challenge <= WHY;
    }

    /** How many sums to solve or breaths to take: one for a short pause, three for a strict pause or a used-up limit. */
    public static int rounds(int challenge, boolean strict) {
        if (challenge == MATH) return strict ? 3 : 1;
        if (challenge == BREATHE) return strict ? 4 : 2;
        return 1;
    }

    /** Seconds before the way past appears on a challenge screen. It is always there at some point: nothing locks. */
    public static int escapeSec(boolean strict) {
        return strict ? 60 : 30;
    }

    /** A sum and its answer. */
    public static final class Sum {
        public final String question;
        public final int answer;

        Sum(String question, int answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    /**
     * A simple sum. {@code round} 0 is an addition, 1 a subtraction, 2 a multiplication, then they repeat; the numbers stay
     * small enough to do in your head.
     */
    public static Sum sum(Random rnd, int round) {
        int kind = Math.floorMod(round, 3);
        if (kind == 0) {
            int a = 6 + rnd.nextInt(34);
            int b = 6 + rnd.nextInt(34);
            return new Sum(a + " + " + b, a + b);
        }
        if (kind == 1) {
            int a = 20 + rnd.nextInt(41);
            int b = 5 + rnd.nextInt(a - 8);
            return new Sum(a + " − " + b, a - b);
        }
        int a = 3 + rnd.nextInt(7);
        int b = 3 + rnd.nextInt(7);
        return new Sum(a + " × " + b, a * b);
    }

    /** True when what was typed is exactly the answer (spaces around it do not matter). */
    public static boolean answerMatches(String typed, int answer) {
        if (typed == null) return false;
        String t = typed.trim();
        if (t.isEmpty() || t.length() > 9) return false;
        try {
            return Integer.parseInt(t) == answer;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** A sentence to type, in the language of the app (what is shown is what has to be typed). */
    public static String phrase(Random rnd) {
        return Tr.t(PHRASES[rnd.nextInt(PHRASES.length)]);
    }

    /** True when the typed sentence is the one asked for: capitals, extra spaces and a full stop at the end do not matter. */
    public static boolean phraseMatches(String typed, String phrase) {
        return squash(typed).equals(squash(phrase));
    }

    private static String squash(String s) {
        if (s == null) return "";
        // letters are compared in their composed form, without the invisible joiners some keyboards add, so the same sentence typed
        // on another keyboard still matches (matters for the Indian scripts)
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC).replace("\u200c", "").replace("\u200d", "");
        t = t.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        while (t.endsWith(".") || t.endsWith("!") || t.endsWith("\u0964") || t.endsWith("\u0965") || t.endsWith("\u3002")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    /** True for the reason that is a real need (so the app can open at once). */
    public static boolean reasonIsNeed(int reason) {
        return reason == 0;
    }

    public static String name(int challenge) {
        return valid(challenge) ? NAMES[challenge] : NAMES[WAIT];
    }
}
