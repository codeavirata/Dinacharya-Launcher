package app.dinacharya.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the app knows, as files for the backup: the routines and settings, to-dos, quiet rules, library mode, language, today's changes,
 * the alarm answers and every day report. The Google sign-in itself is never included.
 */
final class BackupData {
    static final String APP_VERSION = "1.0";
    private static final String[] PREF_KEYS = {"routine", "overrides", "alarmstate", "launcher", "todos", "library", "quiet"};

    private BackupData() {
    }

    static Map<String, byte[]> collect(Context context) throws IOException {
        Context c = context.getApplicationContext();
        Map<String, byte[]> out = new LinkedHashMap<>();
        SharedPreferences p = Store.prefs(c);
        for (String k : PREF_KEYS) {
            String v = p.getString(k, null);
            if (v != null) out.put("prefs/" + k + ".txt", v.getBytes(StandardCharsets.UTF_8));
        }
        if (!out.containsKey("prefs/routine.txt")) {
            out.put("prefs/routine.txt", Store.load(c).serialize().getBytes(StandardCharsets.UTF_8));
        }
        String lang = c.getSharedPreferences("language", Context.MODE_PRIVATE).getString("lang", null);
        if (lang != null) out.put("prefs/lang.txt", lang.getBytes(StandardCharsets.UTF_8));
        out.put("prefs/since.txt", String.valueOf(Store.since(c)).getBytes(StandardCharsets.UTF_8));
        File hist = new File(c.getFilesDir(), "history");
        addDir(out, hist, "history/");
        return out;
    }

    private static void addDir(Map<String, byte[]> out, File dir, String prefix) throws IOException {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) addDir(out, f, prefix + f.getName() + "/");
            else if (f.isFile()) out.put(prefix + f.getName(), Files.readAllBytes(f.toPath()));
        }
    }

    static Map<String, String> info(Context c, Map<String, byte[]> files) {
        int days = 0;
        for (String k : files.keySet()) if (k.startsWith("history/reports/")) days++;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("created", String.valueOf(System.currentTimeMillis()));
        m.put("device", (Build.MANUFACTURER + " " + Build.MODEL).trim());
        m.put("days", String.valueOf(days));
        m.put("app", APP_VERSION);
        return m;
    }

    /**
     * Replaces this phone's data with a backup. A copy of what was here is kept first in
     * "before-restore.zip" inside the app, so a mistake can be undone by support.
     */
    static void apply(Context context, BackupArchive.Contents b) throws IOException {
        Context c = context.getApplicationContext();
        Map<String, byte[]> now = collect(c);
        try (FileOutputStream fo = new FileOutputStream(new File(c.getFilesDir(), "before-restore.zip"))) {
            fo.write(BackupArchive.pack(now, info(c, now)));
        }

        // History: write the new files next to the old ones, then swap the folders.
        File base = c.getFilesDir();
        File fresh = new File(base, "history.restore");
        deleteTree(fresh);
        for (Map.Entry<String, byte[]> e : b.files.entrySet()) {
            String path = e.getKey();
            if (!path.startsWith("history/")) continue;
            File f = new File(fresh, path.substring("history/".length()));
            if (!f.getCanonicalPath().startsWith(fresh.getCanonicalPath() + File.separator)) continue;
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not make a folder for the backup.");
            try (FileOutputStream fo = new FileOutputStream(f)) {
                fo.write(e.getValue());
            }
        }
        File hist = new File(base, "history");
        File old = new File(base, "history.old");
        deleteTree(old);
        if (hist.exists() && !hist.renameTo(old)) throw new IOException("Could not replace the history.");
        if (fresh.exists() && !fresh.renameTo(hist)) {
            old.renameTo(hist);
            throw new IOException("Could not replace the history.");
        }
        deleteTree(old);

        // Settings and routines last, all at once.
        SharedPreferences.Editor ed = Store.prefs(c).edit();
        // A strict lock that is holding keeps the home settings as they are, so a restore cannot undo it.
        boolean keepHome = false;
        try {
            keepHome = LockRules.strictUntil(LauncherStore.load(c), Pro.open(c), System.currentTimeMillis(), Store.zone(), SlotNow.read(c).toLock()) != 0;
        } catch (RuntimeException ignored) {
            // no lock to keep
        }
        for (String k : PREF_KEYS) {
            if (keepHome && k.equals("launcher")) continue;
            byte[] v = b.files.get("prefs/" + k + ".txt");
            if (v != null) ed.putString(k, new String(v, StandardCharsets.UTF_8));
            else ed.remove(k);
        }
        byte[] lang = b.files.get("prefs/lang.txt");
        if (lang != null) {
            c.getSharedPreferences("language", Context.MODE_PRIVATE).edit()
                    .putString("lang", new String(lang, StandardCharsets.UTF_8)).apply();
        }
        byte[] since = b.files.get("prefs/since.txt");
        if (since != null) {
            try {
                ed.putLong("since", Long.parseLong(new String(since, StandardCharsets.UTF_8).trim()));
            } catch (NumberFormatException ignored) {
                // keep this phone's date
            }
        }
        if (!ed.commit()) throw new IOException("Could not save the restored settings.");
        // What a restore brings back is what the person chose to have, so a video pass must not take it away later.
        Pro.get(c).clearPassNotes();
        PassSettle.schedule(c);

        AlarmScheduler.reschedule(c);
        DayCloser.schedule(c);
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
