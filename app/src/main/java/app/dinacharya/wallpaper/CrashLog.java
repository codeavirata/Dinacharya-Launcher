package app.dinacharya.wallpaper;

import android.content.Context;
import android.os.Build;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps the app standing and leaves a note when something goes wrong.
 *
 * A failure in a background job (a report, a backup, phone-time reading) is written down and the
 * job is dropped, instead of closing the app. A failure on the screen itself still closes the app
 * (Android gives no safe way to carry on), but the details are saved and shown the next time the
 * app opens, with a button to copy them.
 */
final class CrashLog {
    private static final String CRASH = "last_crash.txt";
    private static final String NOTES = "problems.log";
    private static final long NOTES_MAX = 24 * 1024;

    private CrashLog() {
    }

    static void install(Context context) {
        final Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        if (prev instanceof Guard) return;
        Thread.setDefaultUncaughtExceptionHandler(new Guard(app, prev));
    }

    private static final class Guard implements Thread.UncaughtExceptionHandler {
        private final Context app;
        private final Thread.UncaughtExceptionHandler prev;

        Guard(Context app, Thread.UncaughtExceptionHandler prev) {
            this.app = app;
            this.prev = prev;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            boolean main = t == Looper.getMainLooper().getThread();
            try {
                if (main) {
                    write(new File(app.getFilesDir(), CRASH), report("The app closed", t, e), false);
                } else {
                    note(app, "background job " + t.getName(), e);
                }
            } catch (Throwable ignored) {
                // never fail while handling a failure
            }
            if (main && prev != null) prev.uncaughtException(t, e);
            // A background thread just ends; the app keeps running.
        }
    }

    private static String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String report(String what, Thread t, Throwable e) {
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        return what + " at " + when + " (thread " + t.getName() + ")\n"
                + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n"
                + stack(e);
    }

    private static void write(File f, String text, boolean append) throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f, append)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Writes down a problem that was caught and handled. */
    static void note(Context c, String where, Throwable e) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), NOTES);
            if (f.exists() && f.length() > NOTES_MAX) f.delete();
            write(f, report("Handled problem in " + where, Thread.currentThread(), e) + "\n", true);
        } catch (Throwable ignored) {
            // nothing more we can do
        }
    }

    /** Details of the last time the app closed unexpectedly, or null. */
    static String lastCrash(Context c) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), CRASH);
            if (!f.exists()) return null;
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The handled problems written down so far (may be empty). */
    static String notes(Context c) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), NOTES);
            if (!f.exists()) return "";
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    static void clearCrash(Context c) {
        try {
            new File(c.getApplicationContext().getFilesDir(), CRASH).delete();
        } catch (RuntimeException ignored) {
            // fine
        }
    }
}
