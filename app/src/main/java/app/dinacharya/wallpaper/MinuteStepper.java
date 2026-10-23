package app.dinacharya.wallpaper;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A − and + that move a number of minutes by {@link SlotPlay#STEP}, with the number between them. */
final class MinuteStepper {
    interface Change {
        void changed(int minutes);
    }

    /** What the middle shows for a number of minutes (by default "10 min"). */
    interface Words {
        String of(int minutes);
    }

    final LinearLayout view;
    private final TextView value;
    private final Button minus;
    private final Button plus;
    private final int hi;
    private final Change change;
    private Words words;
    private int minutes;
    private boolean active = true;

    /**
     * @param initial the first value (rounded to a step and kept within the limits)
     * @param maxFit  the most minutes that fit; the buttons stop at the last full step below it
     */
    MinuteStepper(Context c, int initial, int maxFit, Change change) {
        this.change = change;
        this.hi = Math.max(SlotPlay.STEP, (maxFit / SlotPlay.STEP) * SlotPlay.STEP);
        this.minutes = Math.max(SlotPlay.STEP, Math.min(hi, Math.round(initial / (float) SlotPlay.STEP) * SlotPlay.STEP));
        view = new LinearLayout(c);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        minus = Ui.iconButton(c, "−", Ui.TEAL_DARK, v -> move(false));
        minus.setContentDescription("Less");
        plus = Ui.iconButton(c, "+", Ui.TEAL_DARK, v -> move(true));
        plus.setContentDescription("More");
        value = Ui.label(c, "", 17, Ui.INK, true);
        value.setGravity(Gravity.CENTER);
        view.addView(minus);
        view.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        view.addView(plus);
        draw();
    }

    int minutes() {
        return minutes;
    }

    /** Shows something else than "N min" between the buttons, such as the time that many minutes from now. */
    MinuteStepper words(Words w) {
        words = w;
        draw();
        return this;
    }

    /** Dims and stops the buttons (for when another choice replaces the number). */
    void setActive(boolean on) {
        active = on;
        draw();
    }

    private void move(boolean up) {
        if (!active) return;
        minutes = SlotPlay.stepMinutes(minutes, up, hi);
        draw();
        if (change != null) change.changed(minutes);
    }

    private void draw() {
        value.setText(words == null ? minutes + " min" : words.of(minutes));
        view.setAlpha(active ? 1f : 0.4f);
        minus.setAlpha(active && minutes > SlotPlay.STEP ? 1f : 0.35f);
        plus.setAlpha(active && minutes < hi ? 1f : 0.35f);
        minus.setEnabled(active);
        plus.setEnabled(active);
    }
}
