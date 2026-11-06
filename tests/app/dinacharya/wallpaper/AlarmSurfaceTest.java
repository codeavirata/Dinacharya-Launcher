package app.dinacharya.wallpaper;

/** One surface per ringing alarm: the full-screen question, or one banner, or the question inside the app, never two. */
public class AlarmSurfaceTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    public static void main(String[] a) {
        // screenOn, locked, appInFront
        ok("locked phone: full screen", AlarmSurface.choose(true, true, false) == AlarmSurface.FULL_SCREEN);
        ok("screen off: full screen", AlarmSurface.choose(false, false, false) == AlarmSurface.FULL_SCREEN);
        ok("screen off and locked: full screen", AlarmSurface.choose(false, true, false) == AlarmSurface.FULL_SCREEN);
        ok("locked beats the app being in front", AlarmSurface.choose(true, true, true) == AlarmSurface.FULL_SCREEN);
        ok("screen off beats the app being in front", AlarmSurface.choose(false, false, true) == AlarmSurface.FULL_SCREEN);
        ok("in use in another app: one banner", AlarmSurface.choose(true, false, false) == AlarmSurface.BANNER);
        ok("inside Dinacharya: the question in the app", AlarmSurface.choose(true, false, true) == AlarmSurface.IN_APP);

        for (int s : new int[]{AlarmSurface.FULL_SCREEN, AlarmSurface.BANNER, AlarmSurface.IN_APP}) {
            int shown = 0;
            // the window and the banner together would be the old double alert
            boolean window = AlarmSurface.opensQuestion(s);
            boolean banner = AlarmSurface.popsBanner(s);
            if (window) shown++;
            if (banner) shown++;
            // locked or asleep: the window comes through the full-screen intent and the notification is just its carrier
            if (s == AlarmSurface.FULL_SCREEN) {
                ok("full screen: the window is the one surface, the notification carries it", window && AlarmSurface.carriesFullScreen(s));
            } else {
                ok("surface " + s + ": exactly one of window or banner", shown == 1);
                ok("surface " + s + ": no full-screen intent", !AlarmSurface.carriesFullScreen(s));
            }
        }
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
