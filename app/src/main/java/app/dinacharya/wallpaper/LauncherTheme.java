package app.dinacharya.wallpaper;

import android.content.Context;
import android.content.res.Configuration;

/** The colours of the list of all apps, the pause screen and the dialogs (Auto, Dark or Light). The home screen is the day poster. */
final class LauncherTheme {
    /** True when the screen is dark (so its text is light and the dialogs are dark). */
    final boolean dark;
    final int bg;
    final int text;
    final int muted;
    final int accent;

    private LauncherTheme(boolean dark, int bg, int text, int muted, int accent) {
        this.dark = dark;
        this.bg = bg;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
    }

    static LauncherTheme of(Context c, LauncherConfig cfg) {
        int t = cfg.theme;
        if (t == LauncherConfig.THEME_AUTO) {
            boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            t = night ? LauncherConfig.THEME_DARK : LauncherConfig.THEME_LIGHT;
        }
        if (t == LauncherConfig.THEME_DARK) {
            return new LauncherTheme(true, 0xFF0B1A2A, 0xFFF1F5F5, 0xFF9FB1B5, 0xFF7FE0C3);
        }
        return new LauncherTheme(false, 0xFFEAF2F4, 0xFF10201E, 0xFF4E6866, 0xFF0E7C7B);
    }

    /** The background colour with the given opacity (0 to 255), for the see-through screens over the poster. */
    int scrim(int alpha) {
        return (bg & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** The dialog theme to use so dialogs match the screen. */
    int dialog() {
        return dark ? R.style.GlassDialogNight : R.style.GlassDialogDay;
    }
}
