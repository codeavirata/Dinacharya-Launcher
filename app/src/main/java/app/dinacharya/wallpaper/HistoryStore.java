package app.dinacharya.wallpaper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The on-phone history: a log of what you did with each alarm and one file per closed day.
 * Plain files in a folder; uses no Android classes.
 */
public final class HistoryStore {
    private static final long KEEP_EVENTS_MS = 120L * 24 * 3600 * 1000;
    private final File dir;
    private final File log;
    private final File reports;

    public HistoryStore(File dir) {
        this.dir = dir;
        this.log = new File(dir, "events.log");
        this.reports = new File(dir, "reports");
    }

    private void ensure() {
        if (!dir.exists()) dir.mkdirs();
        if (!reports.exists()) reports.mkdirs();
    }

    public synchronized void append(Ev e) {
        try {
            ensure();
            Files.write(log.toPath(), (e.line() + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            forgetCache();
        } catch (IOException ignored) {
            // History is a convenience; losing one line must never break the alarm.
        }
    }

    // The poster asks for the events every minute and the Home screen every time it draws, so the last reading is kept while the file is
    // the same (same path, size and time of change). It is shared by every HistoryStore on that file.
    private static final Object CACHE_LOCK = new Object();
    private static String cachePath = "";
    private static long cacheLen = -1L;
    private static long cacheMod = -1L;
    private static List<Ev> cacheList = new ArrayList<>();

    private static void forgetCache() {
        synchronized (CACHE_LOCK) {
            cacheLen = -1L;
        }
    }

    public synchronized List<Ev> events() {
        List<Ev> out = new ArrayList<>();
        try {
            if (!log.exists()) return out;
            String path = log.getPath();
            long len = log.length();
            long mod = log.lastModified();
            synchronized (CACHE_LOCK) {
                if (path.equals(cachePath) && len == cacheLen && mod == cacheMod && len > 0) return new ArrayList<>(cacheList);
            }
            for (String ln : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)) {
                Ev e = Ev.parse(ln);
                if (e != null) out.add(e);
            }
            synchronized (CACHE_LOCK) {
                cachePath = path;
                cacheLen = len;
                cacheMod = mod;
                cacheList = new ArrayList<>(out);
            }
        } catch (IOException ignored) {
            // return what we have
        }
        return out;
    }

    /** Drops old lines. Call now and then. */
    public synchronized void compact(long nowMs) {
        List<Ev> all = events();
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (Ev e : all) {
            if (e.ts >= nowMs - KEEP_EVENTS_MS) {
                sb.append(e.line()).append('\n');
                kept++;
            }
        }
        if (kept == all.size()) return;
        try {
            Files.write(log.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            forgetCache();
        } catch (IOException ignored) {
            // try again next time
        }
    }

    private File reportFile(String day) {
        return new File(reports, day.replaceAll("[^0-9-]", "") + ".txt");
    }

    public synchronized void saveReport(DayReport r) {
        try {
            ensure();
            Files.write(reportFile(r.day).toPath(), r.serialize().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // the day is rebuilt next time
        }
    }

    public synchronized boolean hasReport(String day) {
        return reportFile(day).exists();
    }

    public synchronized DayReport loadReport(String day) {
        try {
            File f = reportFile(day);
            if (!f.exists()) return null;
            return DayReport.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** Days that have a report, newest first. */
    public synchronized List<String> reportDays() {
        List<String> days = new ArrayList<>();
        File[] fs = reports.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName();
                if (n.endsWith(".txt")) days.add(n.substring(0, n.length() - 4));
            }
        }
        Collections.sort(days, Collections.reverseOrder());
        return days;
    }

    /** The latest closed reports, newest first. */
    public synchronized List<DayReport> recent(int n) {
        List<DayReport> out = new ArrayList<>();
        for (String d : reportDays()) {
            if (out.size() >= n) break;
            DayReport r = loadReport(d);
            if (r != null) out.add(r);
        }
        return out;
    }
}
