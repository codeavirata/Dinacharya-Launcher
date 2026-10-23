package app.dinacharya.wallpaper;

/**
 * Which screen the app's icon leads to. The first time: a welcome, then the setup, then the tour. After that, always
 * the dashboard. Anything left unfinished (the phone was switched off in the middle of the tour, say) is picked up
 * where it stopped, and someone who updated from an older version, who has already done both, goes straight in.
 */
public final class FirstRun {
    public static final int WELCOME = 0, TOUR = 1, DASHBOARD = 2;

    private FirstRun() {
    }

    public static int next(boolean setupDone, boolean tourDone) {
        if (!setupDone) return WELCOME;
        if (!tourDone) return TOUR;
        return DASHBOARD;
    }
}
