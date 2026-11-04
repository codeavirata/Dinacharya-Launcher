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
            return new LauncherTheme(true, 0xFF0C0C0F, 0xFFF3F3F1, 0xFFA3A3A8, 0xFFFFC66B);
        }
        return new LauncherTheme(false, 0xFFF6F6F4, 0xFF111111, 0xFF666666, 0xFF111111);
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
