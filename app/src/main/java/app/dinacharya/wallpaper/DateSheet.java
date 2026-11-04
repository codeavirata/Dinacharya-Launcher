package app.dinacharya.wallpaper;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * Picks a day in the app's own glass sheet (in place of the system's calendar dialog): one month at a time with arrows, the days
 * before the earliest allowed one dimmed, today ringed and the chosen day filled.
 */
final class DateSheet {
    /** Gets the day that was set. */
    interface Picked {
        void date(LocalDate date);
    }

    private final Context c;
    private final LocalDate min;
    private final Locale locale;
    private final LinearLayout box;
    private final TextView month;
    private final TextView back;
    private final TextView next;
    private final TextView[] cells = new TextView[42];
    private final int white;
    private final int mute;
    private final int accent;
    private final int onAccent;
    private final int ripple;
    private final int edge;
    private final DayOfWeek first;
    private YearMonth shown;
    private LocalDate picked;

    private DateSheet(Context c, LocalDate initial, LocalDate min) {
        this.c = c;
        this.min = min;
        this.locale = Lang.locale();
        this.picked = initial.isBefore(min) ? min : initial;
        this.shown = YearMonth.from(picked);
        this.first = WeekFields.of(locale).getFirstDayOfWeek();
        white = Glass.WHITE;
        mute = Glass.MUTE;
        accent = Ui.TEAL;
        onAccent = Ui.ON_FILL;
        ripple = Glass.RIPPLE;
        edge = Glass.PICKED_EDGE;

        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        back = arrow("‹", "Earlier month", -1);
        next = arrow("›", "Later month", 1);
        month = Glass.text(c, "", 17, white, true);
        month.setGravity(Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        bar.addView(month, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(next, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            TextView t = Glass.text(c, first.plus(i).getDisplayName(TextStyle.NARROW, locale), 12, mute, true);
            t.setGravity(Gravity.CENTER);
            names.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(c, 28), 1f));
        }
        box.addView(names, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 7; k++) {
                TextView t = Glass.text(c, "", 15, white, false);
                t.setGravity(Gravity.CENTER);
                cells[r * 7 + k] = t;
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f);
                p.setMargins(Ui.dp(c, 2), Ui.dp(c, 1), Ui.dp(c, 2), Ui.dp(c, 1));
                row.addView(t, p);
            }
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        draw();
    }

    private TextView arrow(String sign, String words, final int by) {
        TextView t = Glass.text(c, sign, 26, white, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Glass.press(c, 0, 0, 24, ripple));
        t.setClickable(true);
        t.setFocusable(true);
        t.setContentDescription(Lang.tr(words));
        t.setOnClickListener(v -> {
            shown = shown.plusMonths(by);
            draw();
        });
        return t;
    }

    private void draw() {
        month.setText(shown.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)));
        boolean canBack = shown.isAfter(YearMonth.from(min));
        back.setAlpha(canBack ? 1f : 0.3f);
        back.setEnabled(canBack);
        LocalDate day1 = shown.atDay(1);
        int lead = Math.floorMod(day1.getDayOfWeek().getValue() - first.getValue(), 7);
        LocalDate today = LocalDate.now(Store.zone());
        DateTimeFormatter said = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale);
        for (int i = 0; i < cells.length; i++) {
            TextView t = cells[i];
            final LocalDate d = day1.plusDays(i - lead);
            if (!YearMonth.from(d).equals(shown)) {
                t.setText("");
                t.setBackground(null);
                t.setClickable(false);
                t.setFocusable(false);
                t.setContentDescription(null);
                t.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                continue;
            }
            boolean allowed = !d.isBefore(min);
            boolean on = d.equals(picked);
            t.setText(String.valueOf(d.getDayOfMonth()));
            t.setTextColor(on ? onAccent : white);
            t.setAlpha(allowed ? 1f : 0.3f);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", on || d.equals(today) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            t.setBackground(on ? Glass.shape(c, accent, 0, 22)
                    : d.equals(today) ? Glass.press(c, 0, edge, 22, ripple) : Glass.press(c, 0, 0, 22, ripple));
            t.setOnClickListener(allowed ? v -> {
                picked = d;
                draw();
            } : null);
            t.setClickable(allowed);   // after the listener: setting one makes a view clickable
            t.setFocusable(allowed);
            t.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            t.setContentDescription(d.format(said));
        }
    }

    /**
     * Shows the sheet on {@code initial} (or on {@code min} when that is earlier); days before {@code min} cannot be chosen. {@code theme}
     * is the dialog theme of the screen, or 0.
     */
    static void show(Context c, int theme, String title, LocalDate initial, LocalDate min, final Picked done) {
        boolean dark = theme == R.style.GlassDialogNight ? true : theme == R.style.GlassDialogDay ? false : Ui.night;
        boolean was = Ui.night;
        if (dark != was) Ui.mode(dark);   // the sheet takes its colours when it is made
        final DateSheet s;
        try {
            s = new DateSheet(c, initial, min);
        } finally {
            if (dark != was) Ui.mode(was);
        }
        new DBuilder(c, theme)
                .setTitle(title)
                .setView(s.box)
                .setPositiveButton("Set", (d, w) -> done.date(s.picked))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
