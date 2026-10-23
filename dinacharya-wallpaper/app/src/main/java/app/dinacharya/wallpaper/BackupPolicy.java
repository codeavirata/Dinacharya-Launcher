package app.dinacharya.wallpaper;

/** When an automatic backup is due, like WhatsApp's "Back up to Google Drive" setting. Uses no Android classes. */
public final class BackupPolicy {
    public static final int NEVER = 0, MANUAL = 1, DAILY = 2, WEEKLY = 3, MONTHLY = 4;
    public static final String[] NAMES = {"Never", "Only when I tap “Back up”", "Daily", "Weekly", "Monthly"};
    private static final long HOUR = 3600000L;

    private BackupPolicy() {
    }

    /** The shortest time between automatic backups, or -1 for none. A little slack so a daily backup does not drift later each day. */
    public static long interval(int freq) {
        switch (freq) {
            case DAILY: return 20 * HOUR;
            case WEEKLY: return 6 * 24 * HOUR + 12 * HOUR;
            case MONTHLY: return 29 * 24 * HOUR;
            default: return -1;
        }
    }

    /** Whether an automatic backup should run now. */
    public static boolean due(int freq, long lastOkMs, long nowMs) {
        long iv = interval(freq);
        if (iv < 0) return false;
        if (lastOkMs <= 0) return true;
        if (nowMs < lastOkMs) return true;   // the clock went back: back up to be safe
        return nowMs - lastOkMs >= iv;
    }

    public static boolean automatic(int freq) {
        return interval(freq) > 0;
    }

    public static String name(int freq) {
        return freq >= 0 && freq < NAMES.length ? NAMES[freq] : NAMES[DAILY];
    }

    /** "48 KB", "1.2 MB". */
    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " KB";
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
