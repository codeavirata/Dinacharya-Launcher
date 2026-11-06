package app.dinacharya.wallpaper;

/**
 * Which screen the app's icon leads to. The first time: a welcome, then the setup, then the tour. After that, always
 * the dashboard. Anything left unfinished (the phone was switched off in the middle of the tour, say) is picked up
 * where it stopped, and someone who updated from an older version, who has already done both, goes straight in.
 */
public final class FirstRun {
    public static final int WELCOME = 0, TOUR = 1, DASHBOARD = 2, SETUP = 3;

    private FirstRun() {
    }

    public static int next(boolean setupDone, boolean tourDone) {
        return next(setupDone, tourDone, false);
    }

    /**
     * @param setupBegun the person already left the welcome and started the setup. If the setup is cut short then (Android's own page
     *                   for the home app opens the home screen when it closes, the phone is switched off), the app picks the setup up
     *                   again where it was, and does not start over from the welcome.
     */
    public static int next(boolean setupDone, boolean tourDone, boolean setupBegun) {
        if (!setupDone) return setupBegun ? SETUP : WELCOME;
        if (!tourDone) return TOUR;
        return DASHBOARD;
    }
}
