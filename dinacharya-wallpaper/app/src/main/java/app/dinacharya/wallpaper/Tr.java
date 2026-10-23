package app.dinacharya.wallpaper;

import java.util.function.UnaryOperator;

/**
 * Where the plain-Java parts of the app (which know nothing about Android) get their words in the chosen language. Until the app
 * has set a translator, and whenever the language is English, text comes back unchanged.
 */
public final class Tr {
    private static volatile UnaryOperator<String> impl = s -> s;

    private Tr() {
    }

    /** Sets what turns English text into the chosen language (null puts English back). */
    public static void use(UnaryOperator<String> translator) {
        impl = translator == null ? s -> s : translator;
    }

    /** The text in the chosen language; the text itself when it has no translation. */
    public static String t(String english) {
        if (english == null || english.isEmpty()) return english;
        try {
            String out = impl.apply(english);
            return out == null ? english : out;
        } catch (RuntimeException e) {
            return english;
        }
    }
}
