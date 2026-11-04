package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A row of two or three choices where exactly one is picked, like Light / Night / Auto. */
final class Segmented extends LinearLayout {
    interface Listener {
        void onPicked(int index);
    }

    private final TextView[] items;
    private int selected = -1;
    private Listener listener;

    Segmented(Context c, String[] labels) {
        super(c);
        setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.dp(c, 4);
        setPadding(pad, pad, pad, pad);
        setBackground(Ui.box(c, Ui.SOFT, Ui.LINE, 16));
        items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setMinHeight(Ui.dp(c, 40));
            t.setMinimumHeight(Ui.dp(c, 40));
            t.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
            t.setOnClickListener(v -> pick(index, true));
            items[i] = t;
            addView(t, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    int selected() {
        return selected;
    }

    /** Shows {@code index} as picked; tells the listener only when {@code notify} is true and it changed. */
    void pick(int index, boolean notify) {
        if (index < 0 || index >= items.length) return;
        boolean changed = index != selected;
        selected = index;
        Context c = getContext();
        for (int i = 0; i < items.length; i++) {
            boolean on = i == index;
            items[i].setTextColor(on ? 0xFFFFFFFF : Ui.MUTED);
            items[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            items[i].setBackground(on ? Ui.box(c, Ui.TEAL, 0, 12) : Ui.pressable(c, 0x00FFFFFF, 0, 12, Ui.RIPPLE));
        }
        if (notify && changed && listener != null) listener.onPicked(index);
    }
}
