package app.dinacharya.wallpaper;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/**
 * Keeps screens clear of the status bar, the navigation bar and the keyboard.
 *
 * From Android 15 an app that targets it is drawn edge to edge, under the system bars, and has to
 * leave that room itself. These helpers do that, and do nothing harmful on older Android where the
 * system already leaves the room (the insets they see are then zero).
 */
final class Insets {
    /** Receives the room, in pixels, that the system bars and the keyboard take on each side. */
    interface Room {
        void set(int left, int top, int right, int bottom);
    }

    private Insets() {
    }

    /** The room taken on each side: left, top, right, bottom. */
    static int[] room(WindowInsets w) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets s = w.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            return new int[]{s.left, s.top, s.right, s.bottom};
        }
        return new int[]{w.getSystemWindowInsetLeft(), w.getSystemWindowInsetTop(),
                w.getSystemWindowInsetRight(), w.getSystemWindowInsetBottom()};
    }

    /** Tells {@code room} how much space the system takes whenever it changes (bars shown, keyboard opened). */
    @SuppressWarnings("deprecation")
    static void watch(View v, final Room room) {
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            int[] r = room(insets);
            room.set(r[0], r[1], r[2], r[3]);
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        v.requestApplyInsets();
    }

    /** Pads {@code v} by that room, on top of the padding it already has. */
    static void pad(final View v) {
        final int l = v.getPaddingLeft();
        final int t = v.getPaddingTop();
        final int r = v.getPaddingRight();
        final int b = v.getPaddingBottom();
        watch(v, (left, top, right, bottom) -> v.setPadding(l + left, t + top, r + right, b + bottom));
    }

    /** Dark icons on the light bars, as the screens are light. */
    static void lightBars(Activity a) {
        bars(a, true);
    }

    /** Dark status and navigation bar icons ({@code darkIcons}) for a light screen, or light icons for a dark one. */
    @SuppressWarnings("deprecation")
    static void bars(Activity a, boolean darkIcons) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = a.getWindow().getInsetsController();
                if (c != null) {
                    int both = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    c.setSystemBarsAppearance(darkIcons ? both : 0, both);
                }
            } else {
                View d = a.getWindow().getDecorView();
                int f = d.getSystemUiVisibility();
                int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) mask |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                d.setSystemUiVisibility(darkIcons ? (f | mask) : (f & ~mask));
            }
        } catch (RuntimeException ignored) {
            // the icons just keep the theme's colour
        }
    }
}
