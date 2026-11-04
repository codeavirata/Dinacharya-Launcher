package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * The language of the app. All the app's words are written in English in the program; for another language each screen's text is
 * turned into it on the way to the screen ({@link Translator}, with the word lists in assets/lang). Screens are done as they appear and
 * whenever their text changes, dialogs and messages as they are made, so no screen has to know about languages.
 *
 * The person picks a language in Settings, or leaves it on "Automatic", which follows the phone's language when the app has it.
 * Dates and day names follow the chosen language too ({@link #locale}); digits stay 0-9.
 */
final class Lang {
    /** The languages that have a word list, as {code, name in its own script}. English is the program's own text. */
    static final String[][] LANGS = {
            {"en", "English"},
            {"hi", "हिन्दी"},
            {"mr", "मराठी"},
            {"gu", "ગુજરાતી"},
            {"bn", "বাংলা"},
            {"ta", "தமிழ்"},
            {"te", "తెలుగు"},
    };
    /** The setting for "follow the phone". */
    static final String AUTO = "";

    private static final String PREFS = "language";
    private static final String KEY = "lang";

    private static volatile String code = "en";
    private static volatile Translator translator;
    private static volatile boolean ready = true;   // the word list for `code` is loaded (English has none)
    private static Context appContext;
    private static int version;
    private static boolean installed;

    private Lang() {
    }

    // ------------------------------------------------------------------ choosing

    /** True when there is a word list for {@code lang}. */
    static boolean has(String lang) {
        for (String[] l : LANGS) if (l[0].equals(lang)) return true;
        return false;
    }

    /** The name of {@code lang} in its own script. */
    static String nameOf(String lang) {
        for (String[] l : LANGS) if (l[0].equals(lang)) return l[1];
        return lang;
    }

    /** What the person chose: a language code, or {@link #AUTO}. */
    static String setting(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AUTO);
            return s == null || (!s.isEmpty() && !has(s)) ? AUTO : s;
        } catch (RuntimeException e) {
            return AUTO;
        }
    }

    /** The language that is really used for {@code setting}: the phone's language for Automatic when the app has it, else English. */
    static String resolve(String setting) {
        if (setting != null && !setting.isEmpty() && has(setting)) return setting;
        try {
            return has(Locale.getDefault().getLanguage()) ? Locale.getDefault().getLanguage() : "en";
        } catch (RuntimeException e) {
            return "en";
        }
    }

    /** The language in use now. */
    static String current() {
        return code;
    }

    /** Changes every time the language changes (screens use it to know their words are out of date). */
    static int version() {
        return version;
    }

    /** Remembers the person's choice and switches to it at once. */
    static void set(Context c, String setting) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, setting == null ? AUTO : setting).apply();
        } catch (RuntimeException e) {
            CrashLog.note(c, "language save", e);
        }
        use(c, resolve(setting));
    }

    /** Called once when the app starts: reads the choice and starts following the screens. */
    static void init(Application app) {
        use(app, resolve(setting(app)));
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                watch(a.getWindow().getDecorView());
            }

            @Override
            public void onActivityResumed(Activity a) {
                refresh(a.getWindow().getDecorView());
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityPaused(Activity a) {
            }

            @Override
            public void onActivityStopped(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            @Override
            public void onActivityDestroyed(Activity a) {
            }
        });
    }

    /** Switches to {@code lang}; its word list is read the first time a text needs it. */
    private static synchronized void use(Context c, String lang) {
        appContext = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        code = lang;
        translator = null;
        ready = "en".equals(lang);
        version++;
        Tr.use("en".equals(lang) ? null : s -> tr(s));
    }

    /** The word list of the language in use, read from the app's files when first needed; null for English. */
    private static Translator translator() {
        if (ready) return translator;
        synchronized (Lang.class) {
            if (ready) return translator;
            if (appContext == null) {   // before the app has started (a plain test): English
                ready = true;
                return null;
            }
            String lang = code;
            Translator t = null;
            try (InputStream in = appContext.getAssets().open("lang/" + lang + ".tsv")) {
                t = new Translator(Translator.read(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (java.io.IOException | RuntimeException e) {
                CrashLog.note(appContext, "language load " + lang, e);
                code = "en";
                version++;
            }
            translator = t;
            ready = true;
            return t;
        }
    }

    // ------------------------------------------------------------------ words

    /** The text in the language in use (the text itself in English, or where there is nothing to say it in). */
    static String tr(String english) {
        if (english == null || english.isEmpty() || ready && translator == null) return english;
        Translator t = translator();
        return t == null ? english : t.translate(english);
    }

    static CharSequence tr(CharSequence english) {
        if (english == null || english.length() == 0 || english instanceof Spanned) return english;
        return tr(english.toString());
    }

    static String[] tr(String[] english) {
        if (english == null || ready && translator == null) return english;
        String[] out = new String[english.length];
        for (int i = 0; i < out.length; i++) out[i] = tr(english[i]);
        return out;
    }

    static CharSequence[] tr(CharSequence[] english) {
        if (english == null || ready && translator == null) return english;
        CharSequence[] out = new CharSequence[english.length];
        for (int i = 0; i < out.length; i++) out[i] = tr(english[i]);
        return out;
    }

    /** A short message in the language in use. */
    static Toast toast(Context c, CharSequence text, int length) {
        return Toast.makeText(c, tr(text), length);
    }

    /** The locale for dates and day names: the language in use, with digits 0-9. */
    static Locale locale() {
        return "en".equals(code) ? Locale.getDefault() : Locale.forLanguageTag(code + "-u-nu-latn");
    }

    /** A context whose system words (OK, Cancel, the time picker...) follow the language chosen in the app. */
    static Context wrap(Context base) {
        try {
            String s = setting(base);
            if (s.isEmpty()) return base;   // Automatic: the phone's own language
            Configuration conf = new Configuration(base.getResources().getConfiguration());
            conf.setLocale(Locale.forLanguageTag("en".equals(s) ? "en" : s + "-u-nu-latn"));
            return base.createConfigurationContext(conf);
        } catch (RuntimeException e) {
            return base;
        }
    }

    /** A list adapter for spinners whose items are put into the language in use. */
    static ArrayAdapter<String> adapter(Context c, int layout, List<String> items) {
        List<String> out = new ArrayList<>(items.size());
        for (String s : items) out.add(tr(s));
        return new ArrayAdapter<>(c, layout, out);
    }

    static ArrayAdapter<String> adapter(Context c, int layout, String[] items) {
        return new ArrayAdapter<>(c, layout, tr(items));
    }

    // ------------------------------------------------------------------ screens

    private static final class Seen {
        String src;
        String out;
        int version;
        /** The very text object last looked at: when the view still holds it, nothing was set since and there is nothing to do. */
        CharSequence ref;
    }

    private static final WeakHashMap<View, Seen> TEXTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Seen> HINTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Seen> DESCRIPTIONS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> WATCHED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> KEPT = new WeakHashMap<>();

    /** Leaves {@code v} and everything in it as it is: the names of the person's apps are shown as the phone gives them. */
    static void keep(View v) {
        if (v != null) KEPT.put(v, Boolean.TRUE);
    }

    /** Translates {@code root} and everything in it now, and again whenever its layout changes (text set by the program later). */
    static void watch(final View root) {
        if (root == null || WATCHED.containsKey(root)) return;
        WATCHED.put(root, Boolean.TRUE);
        try {
            root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    refresh(root, false);   // pages that are away (GONE) are looked at when they come back: that is a layout too
                }
            });
        } catch (RuntimeException e) {
            CrashLog.note(root.getContext(), "language watch", e);
        }
        refresh(root);
    }

    /** Puts the text of {@code v} and everything inside it into the language in use, hidden parts too. */
    static void refresh(View v) {
        refresh(v, true);
    }

    /** As {@link #refresh(View)}; with {@code hidden} false the parts that are GONE are left (nothing is drawn there, and nothing shows). */
    private static void refresh(View v, boolean hidden) {
        if (v == null || KEPT.containsKey(v)) return;
        try {
            String d = next(DESCRIPTIONS, v, v.getContentDescription());
            if (d != null) v.setContentDescription(d);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                if (!(tv instanceof EditText)) {
                    String t = next(TEXTS, tv, tv.getText());
                    if (t != null) tv.setText(t);
                }
                String h = next(HINTS, tv, tv.getHint());
                if (h != null) tv.setHint(h);
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View c = g.getChildAt(i);
                    if (!hidden && c.getVisibility() == View.GONE) continue;
                    refresh(c, hidden);
                }
            }
        } catch (RuntimeException e) {
            CrashLog.note(v.getContext(), "language refresh", e);
        }
    }

    /**
     * What to put on {@code v} instead of {@code cur}, or null to leave it. The text the program set is remembered, so that when the
     * language changes the original English is translated again, and text the program sets later is told apart from our own.
     */
    private static String next(WeakHashMap<View, Seen> map, View v, CharSequence cur) {
        if (cur == null || cur.length() == 0) return null;
        Seen s = map.get(v);
        if (s != null && s.ref == cur && s.version == version) return null;   // the same text as at the last look, in the same language
        if (!plain(cur)) return null;
        String now = cur.toString();
        String src;
        if (s == null || !now.equals(s.out)) src = now;      // new text from the program
        else if (s.version != version) src = s.src;          // the language changed
        else {
            s.ref = cur;
            return null;
        }
        String out = tr(src);
        if (s == null) {
            s = new Seen();
            map.put(v, s);
        }
        s.src = src;
        s.out = out;
        s.version = version;
        boolean same = out.equals(now);
        s.ref = same ? cur : out;   // a text set from `out` is handed back as that same object
        return same ? null : out;
    }

    /** True for text without styling (the app styles no text, so anything with spans is left as it is). */
    private static boolean plain(CharSequence t) {
        if (t == null || t.length() == 0) return false;
        if (!(t instanceof Spanned)) return true;
        Spanned sp = (Spanned) t;
        return sp.nextSpanTransition(-1, sp.length(), Object.class) >= sp.length();
    }
}
