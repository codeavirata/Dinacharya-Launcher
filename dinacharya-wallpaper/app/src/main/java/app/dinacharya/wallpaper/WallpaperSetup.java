package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * The Dinacharya live wallpaper is part of the launcher: it shows your day on the home screen, on the lock
 * screen and in the list of recent apps. Android never lets an app swap the live wallpaper silently, so the
 * launcher asks the system for it, and the person confirms once on Android's own preview screen.
 */
final class WallpaperSetup {
    private WallpaperSetup() {
    }

    /** The screens the wallpaper can be on (the same numbers Android uses). */
    static final int HOME = WallpaperManager.FLAG_SYSTEM, LOCK = WallpaperManager.FLAG_LOCK;

    private static final String STATE = "wallpaper_state";
    private static final String K_LIVE = "live", K_OK_AT = "confirmed_at";
    private static final long CONFIRMED_FOR_MS = 30L * 24 * 3600000L;

    private static android.content.SharedPreferences state(Context c) {
        return c.getApplicationContext().getSharedPreferences(STATE, Context.MODE_PRIVATE);
    }

    private static boolean matches(WallpaperInfo info, Context c) {
        return info != null && c.getPackageName().equals(info.getPackageName());
    }

    /**
     * Which screens show the Dinacharya wallpaper (HOME and/or LOCK; 0 for none). Two witnesses are used, because
     * some phones do not report a live wallpaper through the usual call: what Android says is set, and what the
     * wallpaper itself knows (it records when it is running for real, as opposed to in a preview).
     */
    static int where(Context c) {
        int w = 0;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(c);
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                if (matches(wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM), c)) w |= HOME;
                if (matches(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK), c)) w |= LOCK;
            }
            if (matches(wm.getWallpaperInfo(), c)) w |= HOME;
        } catch (RuntimeException e) {
            // fall back on what the wallpaper itself recorded
        }
        try {
            w |= state(c).getInt(K_LIVE, 0) & (HOME | LOCK);
        } catch (RuntimeException ignored) {
            // no record
        }
        return w;
    }

    /** Whether the Dinacharya live wallpaper is the one behind the home screen. */
    static boolean isOurs(Context c) {
        return (where(c) & HOME) != 0;
    }

    /** True when it is on the lock screen but not behind the home screen (the person picked "Lock screen" only). */
    static boolean lockOnly(Context c) {
        int w = where(c);
        return (w & LOCK) != 0 && (w & HOME) == 0;
    }

    /**
     * Whether to stop asking: it is in place, or the person confirmed Android's preview recently and nothing says it
     * went to the lock screen only. (Some phones do not report the wallpaper back, and asking again every day was wrong.)
     */
    static boolean settled(Context c) {
        int w = where(c);
        if ((w & HOME) != 0) return true;
        if ((w & LOCK) != 0) return false;
        long at = state(c).getLong(K_OK_AT, 0);
        return at > 0 && System.currentTimeMillis() - at < CONFIRMED_FOR_MS;
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
            int live = state(c).getInt(K_LIVE, 0);
            live = (live & ~before) | after;
            state(c).edit().putInt(K_LIVE, live & (HOME | LOCK)).apply();
        } catch (RuntimeException ignored) {
            // the usual check still works on most phones
        }
    }

    /** The request to Android: preview the Dinacharya wallpaper, where one tap sets it. */
    static Intent intent(Context c) {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(c, PosterWallpaperService.class));
        return i;
    }

    /**
     * Asks Android to put the Dinacharya wallpaper in place. The result (OK when the person confirmed) comes back
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
