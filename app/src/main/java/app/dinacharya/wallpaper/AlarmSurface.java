package app.dinacharya.wallpaper;

/**
 * Which one thing shows when an alarm rings. An alarm used to show two at once on a phone in use: Android's drop-down banner and the
 * full-screen question on top of it. Now exactly one of these is chosen, at the moment it rings:
 * <ul>
 * <li>{@link #FULL_SCREEN}: the phone is locked or its screen is off, so the question opens by itself over the lock screen.</li>
 * <li>{@link #BANNER}: the phone is unlocked and in use in another app, so one drop-down banner (with its quick answers) shows and the
 * question waits behind a tap.</li>
 * <li>{@link #IN_APP}: Dinacharya itself is on screen, so the question opens right there and no banner is added on top of it.</li>
 * </ul>
 * Uses no Android classes, so the rule is tested on its own.
 */
public final class AlarmSurface {
    public static final int FULL_SCREEN = 0, BANNER = 1, IN_APP = 2;

    private AlarmSurface() {
    }

    /**
     * @param screenOn   the screen is on (the phone is awake)
     * @param locked     the lock screen is showing
     * @param appInFront one of Dinacharya's own screens is in front
     */
    public static int choose(boolean screenOn, boolean locked, boolean appInFront) {
        if (!screenOn || locked) return FULL_SCREEN;
        return appInFront ? IN_APP : BANNER;
    }

    /** True when the question should be opened by the program itself (the banner waits for a tap instead). */
    public static boolean opensQuestion(int surface) {
        return surface != BANNER;
    }

    /** True when the notification may pop up as a banner (it stays quiet when the question is already on screen). */
    public static boolean popsBanner(int surface) {
        return surface != IN_APP;
    }

    /** True when the notification carries the full-screen intent (only when the phone is locked or asleep). */
    public static boolean carriesFullScreen(int surface) {
        return surface == FULL_SCREEN;
    }
}
