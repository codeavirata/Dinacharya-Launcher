package app.dinacharya.wallpaper;

/** Noticing that Android stopped the app: a missed alarm, an alarm Android no longer holds, how the app ended, and the wallpaper. */
public class AlarmWatchTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final long MIN = 60000L, HOUR = 60 * MIN;

    public static void main(String[] a) {
        long now = 1_800_000_000_000L;

        // ---- an alarm that never came
        ok("an alarm long past that never came is missed", AlarmWatch.missed(now - 2 * HOUR, now - 20 * HOUR, now));
        ok("an alarm that did come is not missed", !AlarmWatch.missed(now - 2 * HOUR, now - 2 * HOUR, now));
        ok("a later alarm that came covers it", !AlarmWatch.missed(now - 2 * HOUR, now - HOUR, now));
        ok("an alarm a few seconds late is not missed yet", !AlarmWatch.missed(now - 20000L, 0, now));
        ok("an alarm for later is not missed", !AlarmWatch.missed(now + HOUR, 0, now));
        ok("nothing was set: nothing is missed", !AlarmWatch.missed(0, 0, now));
        ok("a missed alarm of this morning is announced", AlarmWatch.worthAnnouncing(now - 3 * HOUR, now));
        ok("one from days ago is not", !AlarmWatch.worthAnnouncing(now - 50 * HOUR, now));
        ok("nothing missed is nothing to announce", !AlarmWatch.worthAnnouncing(0, now));

        // ---- an alarm Android no longer holds
        ok("set for later but gone from Android: lost", AlarmWatch.lost(now + HOUR, false, now));
        ok("set for later and held: not lost", !AlarmWatch.lost(now + HOUR, true, now));
        ok("already past: not lost, it just rang", !AlarmWatch.lost(now - HOUR, false, now));
        ok("nothing set: not lost", !AlarmWatch.lost(0, false, now));

        // ---- how the app ended
        ok("Force stop clears the alarms", AlarmWatch.clearsAlarms(AlarmWatch.EXIT_USER_REQUESTED));
        ok("a stopped user clears them", AlarmWatch.clearsAlarms(AlarmWatch.EXIT_USER_STOPPED));
        ok("a changed permission clears them", AlarmWatch.clearsAlarms(AlarmWatch.EXIT_PERMISSION_CHANGE));
        ok("an update clears them", AlarmWatch.clearsAlarms(AlarmWatch.EXIT_PACKAGE_UPDATED));
        ok("a crash keeps them", !AlarmWatch.clearsAlarms(AlarmWatch.EXIT_CRASH));
        ok("running out of memory keeps them", !AlarmWatch.clearsAlarms(AlarmWatch.EXIT_LOW_MEMORY));
        ok("the app leaving by itself keeps them", !AlarmWatch.clearsAlarms(AlarmWatch.EXIT_SELF));
        ok("Force stop is worth telling", AlarmWatch.worthTelling(AlarmWatch.EXIT_USER_REQUESTED));
        ok("a crash is worth telling", AlarmWatch.worthTelling(AlarmWatch.EXIT_CRASH) && AlarmWatch.worthTelling(AlarmWatch.EXIT_ANR));
        ok("closed for memory is not (it happens all day)", !AlarmWatch.worthTelling(AlarmWatch.EXIT_LOW_MEMORY));
        ok("leaving by itself is not", !AlarmWatch.worthTelling(AlarmWatch.EXIT_SELF));
        ok("an update is not (the alarms are set again by themselves)", !AlarmWatch.worthTelling(AlarmWatch.EXIT_PACKAGE_UPDATED));
        boolean words = true;
        for (int r = 0; r <= 16; r++) words &= !AlarmWatch.what(r).isEmpty() && !AlarmWatch.consequence(r).isEmpty();
        ok("every way of ending has plain words", words);
        ok("Force stop is described as App info", AlarmWatch.what(AlarmWatch.EXIT_USER_REQUESTED).contains("App info"));
        ok("the words for Force stop say the alarms were cleared", AlarmWatch.consequence(AlarmWatch.EXIT_USER_REQUESTED).contains("clears every alarm"));
        ok("the words for a crash say the alarms are kept", AlarmWatch.consequence(AlarmWatch.EXIT_CRASH).contains("keeps the alarms"));

        // ---- the wallpaper
        final int H = WallpaperWatch.HOME, L = WallpaperWatch.LOCK;
        ok("Android says it is on the home screen: in place", WallpaperWatch.status(H, 0, 0, 999999, -1, false) == WallpaperWatch.OK);
        ok("Android says home and lock: in place", WallpaperWatch.status(H | L, 0, 0, 999999, -1, true) == WallpaperWatch.OK);
        ok("our engine is running here: in place, even if Android does not say", WallpaperWatch.status(0, H, 0, 999999, -1, true) == WallpaperWatch.OK);
        ok("lock screen only", WallpaperWatch.status(L, 0, 0, 999999, -1, true) == WallpaperWatch.LOCK_ONLY);
        ok("an old note that it was running does not count once the app has been running a while (the wallpaper is gone after Force stop)",
                WallpaperWatch.status(0, 0, H, 5 * 60000L, -1, true) == WallpaperWatch.LOST);
        ok("an old note counts in the first seconds of a new process (Android puts the wallpaper back by itself)",
                WallpaperWatch.status(0, 0, H, 4000L, -1, true) == WallpaperWatch.CHECKING);
        ok("just confirmed on Android's preview: it is starting", WallpaperWatch.status(0, 0, 0, 999999, 30000L, false) == WallpaperWatch.CHECKING);
        ok("confirmed long ago and nothing says it is there: lost, not 'fine for a month'", WallpaperWatch.status(0, 0, 0, 999999, 20L * 24 * HOUR, true) == WallpaperWatch.LOST);
        ok("was there before and is gone: lost", WallpaperWatch.status(0, 0, 0, 999999, -1, true) == WallpaperWatch.LOST);
        ok("never there: never", WallpaperWatch.status(0, 0, 0, 999999, -1, false) == WallpaperWatch.NEVER);
        ok("lost and never need attention, in place does not", WallpaperWatch.needsAttention(WallpaperWatch.LOST)
                && WallpaperWatch.needsAttention(WallpaperWatch.NEVER) && WallpaperWatch.needsAttention(WallpaperWatch.LOCK_ONLY)
                && !WallpaperWatch.needsAttention(WallpaperWatch.OK) && !WallpaperWatch.needsAttention(WallpaperWatch.CHECKING));
        ok("in place or starting: the launcher paints no poster of its own", WallpaperWatch.inPlace(WallpaperWatch.OK)
                && WallpaperWatch.inPlace(WallpaperWatch.CHECKING) && !WallpaperWatch.inPlace(WallpaperWatch.LOST));
        ok("look again after the grace of a new process", WallpaperWatch.checkAgainMs(4000L, -1) == WallpaperWatch.GRACE_MS - 4000L + 500L);
        ok("look again after a confirmation", WallpaperWatch.checkAgainMs(999999, 30000L) == WallpaperWatch.CONFIRM_MS - 30000L + 500L);
        ok("no need to look again when nothing is pending", WallpaperWatch.checkAgainMs(999999, -1) == 0);

        ok("lost: asked at once when never asked", WallpaperWatch.shouldAsk(WallpaperWatch.LOST, true, -1));
        ok("lost: not asked again straight after the preview was turned down", !WallpaperWatch.shouldAsk(WallpaperWatch.LOST, true, 20000L));
        ok("lost: asked again after ten minutes", WallpaperWatch.shouldAsk(WallpaperWatch.LOST, true, 11 * MIN));
        ok("never set: asked once, the first time, and not again (it is optional)", WallpaperWatch.shouldAsk(WallpaperWatch.NEVER, true, -1)
                && !WallpaperWatch.shouldAsk(WallpaperWatch.NEVER, true, 5 * HOUR) && !WallpaperWatch.shouldAsk(WallpaperWatch.NEVER, true, 25 * HOUR)
                && !WallpaperWatch.shouldAsk(WallpaperWatch.NEVER, true, 400 * HOUR));
        ok("on the lock screen only is a choice already made: not asked for", !WallpaperWatch.shouldAsk(WallpaperWatch.LOCK_ONLY, true, -1)
                && !WallpaperWatch.shouldAsk(WallpaperWatch.LOCK_ONLY, true, 25 * HOUR));
        // a person who said no (skipped it in the setup, or turned Android's preview down)
        ok("said no: never asked, whatever the state", !WallpaperWatch.shouldAsk(WallpaperWatch.NEVER, true, -1, true)
                && !WallpaperWatch.shouldAsk(WallpaperWatch.LOST, true, -1, true) && !WallpaperWatch.shouldAsk(WallpaperWatch.LOST, true, 99 * HOUR, true));
        ok("said no: nothing on the dashboard or the home screen", !WallpaperWatch.needsAttention(WallpaperWatch.NEVER, true)
                && !WallpaperWatch.needsAttention(WallpaperWatch.LOST, true) && !WallpaperWatch.needsAttention(WallpaperWatch.LOCK_ONLY, true));
        ok("not said no: as it was", WallpaperWatch.shouldAsk(WallpaperWatch.LOST, true, -1, false) && WallpaperWatch.needsAttention(WallpaperWatch.NEVER, false)
                && WallpaperWatch.needsAttention(WallpaperWatch.LOST, false));
        ok("said no and then set it: in place, so nothing to ask or tell", !WallpaperWatch.shouldAsk(WallpaperWatch.OK, true, -1, false)
                && !WallpaperWatch.needsAttention(WallpaperWatch.OK, false));
        ok("in place: never asked", !WallpaperWatch.shouldAsk(WallpaperWatch.OK, true, -1) && !WallpaperWatch.shouldAsk(WallpaperWatch.CHECKING, true, -1));
        ok("before the first-start setup is done: not asked here", !WallpaperWatch.shouldAsk(WallpaperWatch.LOST, false, -1));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
    }
}
