package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * The Dinacharya Launcher live wallpaper is part of the launcher: it shows your day on the home screen, on the lock
 * screen and in the list of recent apps. Android never lets an app swap the live wallpaper silently, so the
 * launcher asks the system for it, and the person confirms once on Android's own preview screen.
 */
final class WallpaperSetup {
    private WallpaperSetup() {
    }

    /** The screens the wallpaper can be on (the same numbers Android uses). */
    static final int HOME = WallpaperManager.FLAG_SYSTEM, LOCK = WallpaperManager.FLAG_LOCK;

    private static final String STATE = "wallpaper_state";
    private static final String K_LIVE = "live", K_OK_AT = "confirmed_at", K_EVER = "ever", K_LOST_AT = "lost_at";

    /** The screens our own wallpaper engine is drawing for right now. Only true while it runs in this process. */
    private static volatile int engineBits;

    private static android.content.SharedPreferences state(Context c) {
        return c.getApplicationContext().getSharedPreferences(STATE, Context.MODE_PRIVATE);
    }

    private static boolean matches(WallpaperInfo info, Context c) {
        return info != null && c.getPackageName().equals(info.getPackageName());
    }

    /** What Android itself says: the screens that show the Dinacharya Launcher wallpaper. */
    private static int reported(Context c) {
        int w = 0;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(c);
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                if (matches(wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM), c)) w |= HOME;
                if (matches(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK), c)) w |= LOCK;
            }
            if (matches(wm.getWallpaperInfo(), c)) w |= HOME;
        } catch (RuntimeException e) {
            // the wallpaper engine, if it is running, still tells
        }
        return w;
    }

    /** How long this process has been running, in milliseconds (-1 when not known). */
    private static long processAge() {
        try {
            return android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static long sinceConfirmed(Context c) {
        long at = state(c).getLong(K_OK_AT, 0);
        return at > 0 ? Math.max(0, System.currentTimeMillis() - at) : -1;
    }

    /**
     * Which screens show the Dinacharya Launcher wallpaper right now (HOME and/or LOCK; 0 for none). Two witnesses: what Android says,
     * and the wallpaper engine itself when it is running in this process (some phones do not report a live wallpaper through the usual
     * call). A note kept from an earlier run is not one: closing the app from App info drops the wallpaper without the engine ever
     * saying so, so an old note would go on claiming it is there.
     */
    static int where(Context c) {
        return (reported(c) | engineBits) & (HOME | LOCK);
    }

    /**
     * The state of the wallpaper, one of the {@link WallpaperWatch} answers: in place, being put in place, lost (it was there before),
     * never set, or on the lock screen only. Looks at the phone every time; notes the first moment it found the wallpaper lost.
     */
    static int status(Context c) {
        try {
            android.content.SharedPreferences p = state(c);
            boolean ever = p.getBoolean(K_EVER, false);
            int st = WallpaperWatch.status(reported(c), engineBits, p.getInt(K_LIVE, 0), processAge(), sinceConfirmed(c), ever);
            if (st == WallpaperWatch.OK) {
                boolean wasLost = p.getLong(K_LOST_AT, 0) != 0;
                if (!ever || wasLost) {
                    android.content.SharedPreferences.Editor ed = p.edit().putBoolean(K_EVER, true);
                    if (wasLost) ed.remove(K_LOST_AT);
                    ed.apply();
                }
            } else if (st == WallpaperWatch.LOST && p.getLong(K_LOST_AT, 0) == 0) {
                p.edit().putLong(K_LOST_AT, System.currentTimeMillis()).apply();
                String seen = "?";
                try {
                    android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(c);
                    android.app.WallpaperInfo wi = wm.getWallpaperInfo();
                    seen = wi == null ? "none (a still picture, or the phone does not say)" : wi.getPackageName() + "/" + wi.getServiceName();
                } catch (RuntimeException e) {
                    seen = "error " + e.getMessage();
                }
                CrashLog.note(c, "wallpaper", new RuntimeException("The Dinacharya Launcher wallpaper was no longer in place when the home screen came up; it is asked for again."
                        + " Android says the wallpaper is: " + seen
                        + "; Android reports ours on screens=" + reported(c) + "; our engine runs for screens=" + engineBits
                        + "; kept from before=" + p.getInt(K_LIVE, 0) + "; this process has run " + (processAge() / 1000) + " s"
                        + "; confirmed " + (sinceConfirmed(c) < 0 ? "never" : (sinceConfirmed(c) / 1000) + " s ago")));
            }
            return st;
        } catch (RuntimeException e) {
            return WallpaperWatch.OK;   // cannot tell: do not nag
        }
    }

    /** When the wallpaper was first found gone and is still gone (milliseconds); 0 when it is in place or was never set. */
    static long lostAt(Context c) {
        try {
            return state(c).getLong(K_LOST_AT, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** When to look again, if the last answer was "being put in place" (milliseconds from now; 0 for never). */
    static long checkAgainMs(Context c) {
        try {
            return WallpaperWatch.checkAgainMs(processAge(), sinceConfirmed(c));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Whether the Dinacharya Launcher live wallpaper is the one behind the home screen (or is about to be). */
    static boolean isOurs(Context c) {
        return WallpaperWatch.inPlace(status(c));
    }

    /** True when it is on the lock screen but not behind the home screen (the person picked "Lock screen" only). */
    static boolean lockOnly(Context c) {
        return status(c) == WallpaperWatch.LOCK_ONLY;
    }

    /** Whether it is in place (or about to be), so nothing needs asking. */
    static boolean settled(Context c) {
        return WallpaperWatch.inPlace(status(c));
    }

    /** Remembers how Android's preview ended: confirmed (the person tapped Set) or not. */
    static void noteResult(Context c, boolean confirmed) {
        try {
            android.content.SharedPreferences.Editor ed = state(c).edit();
            if (confirmed) ed.putLong(K_OK_AT, System.currentTimeMillis());
            else ed.remove(K_OK_AT);
            ed.apply();
        } catch (RuntimeException ignored) {
            // asking once more is harmless
        }
    }

    /** Called by the wallpaper when its screens change while it is running for real (not in a preview). */
    static void engineScreens(Context c, int before, int after) {
        try {
            engineBits = ((engineBits & ~before) | after) & (HOME | LOCK);
            int live = state(c).getInt(K_LIVE, 0);
            live = (live & ~before) | after;
            state(c).edit().putInt(K_LIVE, live & (HOME | LOCK)).apply();
        } catch (RuntimeException ignored) {
            // the usual check still works on most phones
        }
    }

    /** The request to Android: preview the Dinacharya Launcher wallpaper, where one tap sets it. */
    static Intent intent(Context c) {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(c, PosterWallpaperService.class));
        return i;
    }

    /**
     * Asks Android to put the Dinacharya Launcher wallpaper in place. The result (OK when the person confirmed) comes back
     * to {@code onActivityResult} with {@code requestCode}. Returns false when this phone has no live wallpaper screen.
     */
    static boolean request(Activity a, int requestCode) {
        try {
            a.startActivityForResult(intent(a), requestCode);
            return true;
        } catch (RuntimeException e) {
            try {
                a.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                return true;
            } catch (RuntimeException e2) {
                Lang.toast(a, "This phone has no live wallpaper screen", Toast.LENGTH_LONG).show();
                return false;
            }
        }
    }

    /** Same, without wanting the answer. */
    static void open(Activity a) {
        request(a, 0);
    }
}
