package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.time.LocalDate;

/**
 * Rating the app on Google Play: the line in Settings, and the card that Home shows once in a while ({@link RatePrompt} decides when).
 * Both open the app's own page in the Play Store app, or on the web when the Play Store app is not there.
 */
final class RateApp {
    private static final String KEY = "rate_prompt";

    private RateApp() {
    }

    private static long today() {
        return LocalDate.now(Store.zone()).toEpochDay();
    }

    private static RatePrompt load(Context c) {
        try {
            return RatePrompt.parse(Store.prefs(c).getString(KEY, null));
        } catch (RuntimeException e) {
            return new RatePrompt();
        }
    }

    private static void save(Context c, RatePrompt r) {
        try {
            Store.prefs(c).edit().putString(KEY, r.serialize()).apply();
        } catch (RuntimeException e) {
            CrashLog.note(c, "rate prompt", e);
        }
    }

    /** The dashboard was opened today. */
    static void used(Context c) {
        RatePrompt r = load(c);
        if (r.done()) return;
        r.used(today());
        save(c, r);
    }

    /** True when Home should show the card now. */
    static boolean due(Context c) {
        return load(c).due(today());
    }

    /** "Not now" on the card. */
    static void later(Context c) {
        RatePrompt r = load(c);
        r.later(today());
        save(c, r);
    }

    /** Opens the app's page on Google Play, and the card stops coming back. */
    static void open(Activity a) {
        RatePrompt r = load(a);
        r.rated();
        save(a, r);
        String pkg = a.getPackageName();
        Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).setPackage("com.android.vending");
        try {
            a.startActivity(store);
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            // no Play Store app: the web page instead
        }
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        } catch (ActivityNotFoundException | SecurityException e) {
            CrashLog.note(a, "rate: no way to open Google Play", e);
            android.widget.Toast.makeText(a, Lang.tr("Google Play could not be opened on this phone"), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
