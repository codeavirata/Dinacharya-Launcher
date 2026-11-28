package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The sheet that comes up when a change in the Routine tab would touch a slot that has already started today. It says which slot, lists
 * what the change is (up to {@link RoutineDiff#SHOWN} lines and "+ N more"), and offers to save the change from tomorrow with today left
 * exactly as it was, or to throw the change away. When today cannot be left as it was (it already has a routine for that one day, or
 * the change is refused for another reason) it only explains, with an OK button. The screen behind it already shows the saved routine.
 */
final class SaveTomorrowDialog {
    private SaveTomorrowDialog() {
    }

    /**
     * @param slot    the started slot the change would touch, or "" when the strict switch is the reason
     * @param canPin  today can be left as it was and the change started tomorrow
     * @param changes the lines from {@link RoutineDiff}, in the person's words
     * @param save    runs when the person chooses "Save from tomorrow"
     */
    static AlertDialog show(Activity a, String slot, boolean canPin, List<String> changes, final Runnable save) {
        boolean strict = slot == null || slot.isEmpty();
        String title;
        String message;
        if (canPin) {
            title = strict ? "Strict routine is on" : "Save this change from tomorrow?";
            message = strict
                    ? "Today's routine can't be changed from here. Your change can start tomorrow, and today stays exactly as it is."
                    : "“" + slot + "” has already started today, so today can't be changed from here. Your change can start tomorrow, and today stays exactly as it is.";
        } else {
            title = "Today can't be changed here";
            message = strict
                    ? "Strict routine is on, so today's routine can't be changed here. To change today, use Change plan on Home."
                    : "“" + slot + "” has already started today, so it can't be changed here. To change today, use Change plan on Home.";
        }
        DBuilder b = new DBuilder(a);
        b.setTitle(title).setMessage(message).setCancelable(false);
        if (canPin) {
            b.setView(content(a, changes));
            b.setPositiveButton("Save from tomorrow", (d, w) -> save.run());
            b.setNegativeButton("Discard", null);
        } else {
            b.setPositiveButton("OK", null);
        }
        AlertDialog d = b.create();
        d.show();
        return d;
    }

    /** The box with what the change is, and under it where today can still be changed. */
    private static View content(Activity a, List<String> changes) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Glass.dp(a, 22), Glass.dp(a, 4), Glass.dp(a, 22), Glass.dp(a, 4));
        if (changes != null && !changes.isEmpty()) {
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(Glass.shape(a, Glass.FILL, Glass.EDGE, 16));
            box.setPadding(Glass.dp(a, 16), Glass.dp(a, 12), Glass.dp(a, 16), Glass.dp(a, 14));
            TextView label = Glass.text(a, "Your change", 13, Glass.MUTE, true);
            box.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int n = Math.min(RoutineDiff.SHOWN, changes.size());
            for (int i = 0; i < n; i++) {
                TextView line = Glass.text(a, changes.get(i), 15, Glass.WHITE, false);
                box.addView(line, Glass.gap(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, i == 0 ? 8 : 6));
            }
            String more = RoutineDiff.more(changes.size());
            if (!more.isEmpty()) {
                box.addView(Glass.text(a, more, 13, Glass.MUTE, false), Glass.gap(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8));
            }
            col.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView hint = Glass.text(a, "To change only today, use Change plan on Home.", 13, Glass.MUTE, false);
        hint.setLineSpacing(Glass.dp(a, 2), 1f);
        col.addView(hint, Glass.gap(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 12));
        return col;
    }
}
