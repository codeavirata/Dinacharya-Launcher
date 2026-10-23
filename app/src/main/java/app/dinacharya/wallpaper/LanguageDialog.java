package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * The choice of the app's language: "Automatic" (the phone's language, when the app has it) and each language written in its own
 * script, so that anyone can find theirs whatever language the app is in now. Picking one changes the whole app at once.
 */
final class LanguageDialog {
    private LanguageDialog() {
    }

    /** What the row in Settings says the language is now. */
    static String summary(Activity a) {
        String s = Lang.setting(a);
        if (s.isEmpty()) return "Automatic: " + Lang.nameOf(Lang.resolve(Lang.AUTO));
        return Lang.nameOf(s);
    }

    static void show(final Activity a, int dialogTheme) {
        try {
            final String[] codes = new String[Lang.LANGS.length + 1];
            final String[] names = new String[codes.length];
            codes[0] = Lang.AUTO;
            names[0] = "Automatic (the phone's language)";
            for (int i = 0; i < Lang.LANGS.length; i++) {
                codes[i + 1] = Lang.LANGS[i][0];
                names[i + 1] = Lang.LANGS[i][1];
            }
            String now = Lang.setting(a);
            int checked = 0;
            for (int i = 0; i < codes.length; i++) if (codes[i].equals(now)) checked = i;
            new DBuilder(a, dialogTheme).setTitle("Language")
                    .setSingleChoiceItems(names, checked, (d, which) -> {
                        d.dismiss();
                        if (which < 0 || which >= codes.length || codes[which].equals(Lang.setting(a))) return;
                        Lang.set(a, codes[which]);
                        a.recreate();   // every screen is drawn again in the new language
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "language dialog", e);
        }
    }
}
