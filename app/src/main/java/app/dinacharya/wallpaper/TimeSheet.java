package app.dinacharya.wallpaper;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.Locale;

/**
 * Picks a time of day in the app's own glass sheet (in place of the system's clock dialog): the hour and the minute each with a
 * minus and a plus that keep repeating while held, and AM or PM when the phone shows a 12-hour clock.
 */
final class TimeSheet {
    /** Gets the time that was set. */
    interface Picked {
        void time(int hour, int minute);
    }

    private TimeSheet() {
    }

    /** Shows the sheet with {@code minuteOfDay} (minutes since midnight) in it; {@code theme} is the dialog theme of the screen, or 0. */
    static void show(Context c, int theme, String title, int minuteOfDay, final boolean h24, final Picked done) {
        boolean dark = theme == R.style.GlassDialogNight ? true : theme == R.style.GlassDialogDay ? false : Ui.night;
        boolean was = Ui.night;
        int at = Routine.mod(minuteOfDay, 1440);
        final LinearLayout box = new LinearLayout(c);
        final Glass.Stepper hour;
        final Glass.Stepper minute;
        final Glass.Segments half;
        if (dark != was) Ui.mode(dark);   // the stepper takes its colours when it is made
        try {
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(Ui.dp(c, 22), Ui.dp(c, 6), Ui.dp(c, 22), Ui.dp(c, 6));
            int h = at / 60;
            int shownHour = h24 ? h : (h % 12 == 0 ? 12 : h % 12);
            hour = new Glass.Stepper(c, shownHour, h24 ? 0 : 1, h24 ? 23 : 12, 1, "Hour", null)
                    .words(v -> String.format(Locale.US, h24 ? "%02d" : "%d", v));
            minute = new Glass.Stepper(c, at % 60, 0, 59, 1, "Minute", null)
                    .words(v -> String.format(Locale.US, "%02d", v));
            box.addView(hour.view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(minute.view, Glass.gap(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 10));
            if (h24) {
                half = null;
            } else {
                half = new Glass.Segments(c, null, "AM", "PM");
                half.select(h >= 12 ? 1 : 0, false);
                box.addView(half.view, Glass.gap(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 10));
            }
        } finally {
            if (dark != was) Ui.mode(was);
        }
        new DBuilder(c, theme)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Set", (d, w) -> {
                    int h = hour.value();
                    if (!h24) h = (h % 12) + (half != null && half.selected() == 1 ? 12 : 0);
                    done.time(h, minute.value());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
