package app.dinacharya.wallpaper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The backup file: a zip with a short description ("manifest.txt") and the app's data files. Reading
 * is strict: only known folders, no odd paths, and a size limit, so a damaged or foreign file can
 * never write outside the app's data. Uses no Android classes.
 */
public final class BackupArchive {
    public static final String NAME = "dinacharya-backup.zip";
    public static final String MIME = "application/zip";
    public static final int FORMAT = 1;
    static final String MANIFEST = "manifest.txt";
    /** The paths a backup may contain. */
    static final String[] ALLOWED = {"prefs/", "history/"};
    static final long MAX_TOTAL = 64L * 1024 * 1024;
    static final int MAX_FILES = 5000;

    private BackupArchive() {
    }

    /** What came out of a backup file. */
    public static final class Contents {
        public final Map<String, byte[]> files = new LinkedHashMap<>();
        public final Map<String, String> info = new LinkedHashMap<>();

        public int format() {
            try {
                return Integer.parseInt(info.get("format"));
            } catch (RuntimeException e) {
                return -1;
            }
        }

        public long created() {
            try {
                return Long.parseLong(info.get("created"));
            } catch (RuntimeException e) {
                return 0;
            }
        }
    }

    static boolean allowed(String path) {
        if (path == null || path.isEmpty() || path.length() > 200) return false;
        if (path.contains("..") || path.contains("\\") || path.startsWith("/") || path.contains("//")) return false;
        for (String a : ALLOWED) if (path.startsWith(a) && path.length() > a.length()) return true;
        return false;
    }

    public static byte[] pack(Map<String, byte[]> files, Map<String, String> info) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            StringBuilder m = new StringBuilder();
            m.append("format=").append(FORMAT).append('\n');
            for (Map.Entry<String, String> e : info.entrySet()) {
                if (e.getKey().equals("format")) continue;
                m.append(e.getKey().replace('=', '_').replace('\n', ' ')).append('=')
                        .append(String.valueOf(e.getValue()).replace('\n', ' ')).append('\n');
            }
            z.putNextEntry(new ZipEntry(MANIFEST));
            z.write(m.toString().getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                if (!allowed(e.getKey())) throw new IOException("not a backup path: " + e.getKey());
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    /** Reads a backup file. Throws IOException when it is not a Dinacharya backup this version can read. */
    public static Contents unpack(byte[] data) throws IOException {
        Contents c = new Contents();
        boolean manifest = false;
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                boolean isManifest = name.equals(MANIFEST);
                if (!isManifest && !allowed(name)) throw new IOException("The backup file contains an unexpected entry.");
                if (c.files.size() >= MAX_FILES) throw new IOException("The backup file has too many entries.");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                int n;
                while ((n = z.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_TOTAL) throw new IOException("The backup file is too large.");
                    bo.write(buf, 0, n);
                }
                if (isManifest) {
                    manifest = true;
                    for (String ln : new String(bo.toByteArray(), StandardCharsets.UTF_8).split("\n")) {
                        int eq = ln.indexOf('=');
                        if (eq > 0) c.info.put(ln.substring(0, eq), ln.substring(eq + 1));
                    }
                } else {
                    c.files.put(name, bo.toByteArray());
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new IOException("The backup file is damaged.");
        }
        if (!manifest) throw new IOException("This is not a Dinacharya Launcher backup.");
        if (c.format() < 1) throw new IOException("This is not a Dinacharya Launcher backup.");
        if (c.format() > FORMAT) throw new IOException("This backup was made by a newer version of the app. Update the app first.");
        byte[] routine = c.files.get("prefs/routine.txt");
        if (routine == null || Routine.parse(new String(routine, StandardCharsets.UTF_8)) == null) {
            throw new IOException("The backup has no readable routine.");
        }
        return c;
    }
}
