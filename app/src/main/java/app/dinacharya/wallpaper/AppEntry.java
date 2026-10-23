package app.dinacharya.wallpaper;

/** One app that can be opened from the home screen. Uses no Android classes. */
public final class AppEntry {
    public final String key;
    public final String label;
    public final String pkg;
    public final long user;
    /** Lives in a work (or other secondary) profile rather than the main one. */
    public final boolean work;

    public AppEntry(String key, String label, boolean work) {
        this.key = key;
        this.pkg = AppKey.pkg(key);
        this.user = AppKey.user(key);
        String l = label == null ? "" : label.replace('\n', ' ').replace('\t', ' ').trim();
        this.label = l.isEmpty() ? pkg : l;
        this.work = work;
    }

    /** The name as shown in lists: work-profile apps get a small marker so two copies can be told apart. */
    public String shown() {
        return work ? label + "  (work)" : label;
    }
}
