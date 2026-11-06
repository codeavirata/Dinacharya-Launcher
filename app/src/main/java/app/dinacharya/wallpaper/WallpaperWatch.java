package app.dinacharya.wallpaper;

/**
 * Decides whether the Dinacharya Launcher wallpaper is really in place, from the facts the phone can give, and when the launcher
 * should ask Android to put it back. It is looked at every time the home screen comes up, not trusted from an old note: closing the
 * app from App info makes Android drop the live wallpaper, and nothing is told to the app.
 *
 * <p>The wallpaper is a choice, not a requirement: without it the home screen draws the same day itself. Once the person has said no
 * (skipped it in the setup, or turned Android's preview down), nothing asks again and nothing warns; it stays in the Settings.
 * Uses no Android classes.
 */
public final class WallpaperWatch {
    /** The wallpaper is in place behind the home screen. */
    public static final int OK = 0;
    /** The app has only just started, or the person has only just confirmed: Android may still be putting it in place. Look again soon. */
    public static final int CHECKING = 1;
    /** It was in place before and is not now (the app was closed from App info, or the person chose another wallpaper). */
    public static final int LOST = 2;
    /** It has never been put in place. */
    public static final int NEVER = 3;
    /** Only the lock screen has it. */
    public static final int LOCK_ONLY = 4;

    /** The screens, the same numbers Android uses (WallpaperManager.FLAG_SYSTEM and FLAG_LOCK). */
    public static final int HOME = 1, LOCK = 2;

    /** How long after the app process starts Android is given to bind the wallpaper again before it counts as gone. */
    public static final long GRACE_MS = 20000L;
    /** How long after the person confirmed Android's preview the wallpaper is given to start. */
    public static final long CONFIRM_MS = 120000L;
    /** The wait before a wallpaper that went missing is asked for again after the person turned the preview down. */
    public static final long ASK_AGAIN_LOST_MS = 10L * 60000L;

    private WallpaperWatch() {
    }

    /**
     * @param reported    the screens Android says show this wallpaper
     * @param engine      the screens our own wallpaper engine is drawing for right now, in this process
     * @param persisted   the screens the engine last said it was running for, kept from before this process started
     * @param processAge  how long this process has been running (milliseconds), or -1 when not known
     * @param sinceOk     how long ago the person confirmed Android's preview (milliseconds), or -1 when never
     * @param everOurs    true when the wallpaper has been seen in place at some time
     */
    public static int status(int reported, int engine, int persisted, long processAge, long sinceOk, boolean everOurs) {
        int w = (reported | engine) & (HOME | LOCK);
        if ((w & HOME) != 0) return OK;
        if ((w & LOCK) != 0) return LOCK_ONLY;
        // Nothing says it is there. A process that has only just started has not had the wallpaper handed back yet.
        if ((persisted & (HOME | LOCK)) != 0 && processAge >= 0 && processAge < GRACE_MS) return CHECKING;
        if (sinceOk >= 0 && sinceOk < CONFIRM_MS) return CHECKING;
        return everOurs ? LOST : NEVER;
    }

    /** True when the wallpaper is, or is about to be, behind the home screen: the launcher then does not paint its own poster. */
    public static boolean inPlace(int status) {
        return status == OK || status == CHECKING;
    }

    /** Milliseconds after which a {@link #CHECKING} answer should be looked at again. */
    public static long checkAgainMs(long processAge, long sinceOk) {
        long a = processAge >= 0 && processAge < GRACE_MS ? GRACE_MS - processAge : Long.MAX_VALUE;
        long b = sinceOk >= 0 && sinceOk < CONFIRM_MS ? CONFIRM_MS - sinceOk : Long.MAX_VALUE;
        long m = Math.min(a, b);
        return m == Long.MAX_VALUE ? 0 : m + 500L;
    }

    /**
     * Whether the launcher should open Android's wallpaper preview now. A wallpaper that went missing (it was in place before) is asked
     * for at once; one that was never set is asked for once, the first time and never again (it is offered from the Settings and from a
     * small note); one on the lock screen only is a choice already made and is not asked for. The launcher paints the poster itself
     * meanwhile, so the home screen is never plain.
     */
    public static boolean shouldAsk(int status, boolean setupDone, long sinceAsk) {
        if (!setupDone) return false;
        switch (status) {
            case LOST: return sinceAsk < 0 || sinceAsk >= ASK_AGAIN_LOST_MS;
            case NEVER: return sinceAsk < 0;
            default: return false;
        }
    }

    /** The same, for a person who may have said no: after that nothing is asked. */
    public static boolean shouldAsk(int status, boolean setupDone, long sinceAsk, boolean declined) {
        return !declined && shouldAsk(status, setupDone, sinceAsk);
    }

    /** True when the status is one the person should see a plain note about on the dashboard (it is not in place and not about to be). */
    public static boolean needsAttention(int status) {
        return status == LOST || status == NEVER || status == LOCK_ONLY;
    }

    /** The same, for a person who may have said no to the wallpaper: then there is nothing to tell them. */
    public static boolean needsAttention(int status, boolean declined) {
        return !declined && needsAttention(status);
    }
}
