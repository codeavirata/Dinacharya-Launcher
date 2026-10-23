package app.dinacharya.wallpaper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Backing up to and restoring from the hidden app space in Drive. Uses no Android classes. */
public final class CloudBackup {
    private CloudBackup() {
    }

    public static final class Result {
        public final CloudDrive.FileInfo file;
        public final long bytes;

        Result(CloudDrive.FileInfo file, long bytes) {
            this.file = file;
            this.bytes = bytes;
        }
    }

    /** Short facts kept with the file in Drive, so the restore question can say what the backup holds. */
    static Map<String, String> props(Map<String, String> info) {
        Map<String, String> p = new LinkedHashMap<>();
        for (String k : new String[]{"created", "device", "days", "app"}) {
            String v = info.get(k);
            if (v == null) continue;
            // Drive allows 124 bytes per key and value together.
            if (v.length() > 100) v = v.substring(0, 100);
            p.put(k, v);
        }
        return p;
    }

    /** Makes the backup file and puts it in Drive, replacing the previous one. */
    public static Result backUp(CloudDrive d, Map<String, byte[]> files, Map<String, String> info) throws IOException {
        byte[] zip = BackupArchive.pack(files, info);
        List<CloudDrive.FileInfo> existing = d.find(BackupArchive.NAME);
        String id = existing.isEmpty() ? null : existing.get(0).id;
        CloudDrive.FileInfo up = d.upload(BackupArchive.NAME, BackupArchive.MIME, zip, id, props(info));
        // Keep a single backup, like WhatsApp: remove any extra copies left by an earlier problem.
        for (CloudDrive.FileInfo old : existing) {
            if (old.id.equals(up.id)) continue;
            try {
                d.delete(old.id);
            } catch (IOException ignored) {
                // tried again next time
            }
        }
        return new Result(up, zip.length);
    }

    /** The newest backup in Drive (its description only), or null when there is none. */
    public static CloudDrive.FileInfo find(CloudDrive d) throws IOException {
        return d.latest(BackupArchive.NAME);
    }

    /** Downloads and checks a backup. */
    public static BackupArchive.Contents fetch(CloudDrive d, CloudDrive.FileInfo f) throws IOException {
        return BackupArchive.unpack(d.download(f.id));
    }
}
