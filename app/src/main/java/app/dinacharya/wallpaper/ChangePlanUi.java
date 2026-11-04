package app.dinacharya.wallpaper;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The pieces of the Change plan screens that look the same on the alarm screen and the Home screen. */
final class ChangePlanUi {
    private ChangePlanUi() {
    }

    /** Cards to pick one slot from: the slot's name, its length, and a tick on the one that is chosen. */
    static final class Cards {
        final LinearLayout view;
        private final List<LinearLayout> cards = new ArrayList<>();
        private final List<TextView> ticks = new ArrayList<>();
        private final Context c;
        private int sel;

        Cards(Context c, List<String> names, List<String> notes) {
            this.c = c;
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < names.size(); i++) {
                final int k = i;
                LinearLayout card = new LinearLayout(c);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
                LinearLayout text = new LinearLayout(c);
                text.setOrientation(LinearLayout.VERTICAL);
                text.addView(Ui.label(c, names.get(i), 16, Ui.INK, true));
                text.addView(Ui.label(c, notes.get(i), 12, Ui.MUTED, false));
                card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                TextView tick = Ui.label(c, "✓", 18, Ui.TEAL_DARK, true);
                card.addView(tick);
                card.setOnClickListener(v -> select(k));
                cards.add(card);
                ticks.add(tick);
                view.addView(card, Ui.wide(c, i == 0 ? 0 : 8));
            }
            select(0);
        }

        void select(int i) {
            sel = i;
            for (int k = 0; k < cards.size(); k++) {
                boolean on = k == i;
                cards.get(k).setBackground(Ui.pressable(c, on ? Ui.TONAL : Ui.SURFACE, on ? Ui.TEAL : Ui.LINE, 14, Ui.RIPPLE));
                ticks.get(k).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            }
        }

        int selected() {
            return sel;
        }
    }

    /** A row of round chips; one is chosen. */
    static final class Chips {
        final HorizontalScrollView view;
        private final LinearLayout row;
        private final List<TextView> chips = new ArrayList<>();
        private final Context c;
        private int sel = -1;
        private final Runnable[] onPick = new Runnable[1];

        Chips(Context c) {
            this.c = c;
            view = new HorizontalScrollView(c);
            view.setHorizontalScrollBarEnabled(false);
            row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            view.addView(row);
        }

        void set(List<String> names, int selected, Runnable picked) {
            row.removeAllViews();
            chips.clear();
            onPick[0] = picked;
            for (int i = 0; i < names.size(); i++) {
                final int k = i;
                TextView t = Ui.label(c, names.get(i), 14, Ui.TEAL_DARK, true);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
                t.setOnClickListener(v -> {
                    pick(k);
                    if (onPick[0] != null) onPick[0].run();
                });
                chips.add(t);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = Ui.dp(c, 8);
                row.addView(t, lp);
            }
            pick(Math.max(0, Math.min(selected, names.size() - 1)));
        }

        void pick(int i) {
            sel = i;
            for (int k = 0; k < chips.size(); k++) {
                boolean on = k == i;
                TextView t = chips.get(k);
                t.setTextColor(on ? 0xFFFFFFFF : Ui.TEAL_DARK);
                t.setBackground(Ui.pressable(c, on ? Ui.TEAL : Ui.SURFACE, Ui.TEAL, 18, Ui.RIPPLE));
            }
        }

        int selected() {
            return sel;
        }
    }

    /** Type and sub type for the replacing activity; starts on the slot's own. */
    static final class TypePicker {
        final LinearLayout view;
        private final Chips kinds;
        private final Chips subs;
        private final Routine routine;
        private final int[] kindOf = new int[4];
        private List<Routine.Sub> subList = new ArrayList<>();
        private int kind;

        TypePicker(Context c, Routine routine, int curKind, String curSub) {
            this.routine = routine;
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.VERTICAL);
            kinds = new Chips(c);
            subs = new Chips(c);
            List<String> names = new ArrayList<>();
            int at = 0;
            for (int k = 0; k < Routine.SLEEP; k++) {
                kindOf[k] = k;
                names.add(Routine.KIND_NAMES[k]);
            }
            kind = curKind >= 0 && curKind < Routine.SLEEP ? curKind : Routine.TASK;
            at = kind;
            view.addView(Ui.label(c, "Type", 12, Ui.MUTED, true), Ui.wide(c, 10));
            view.addView(kinds.view, Ui.wide(c, 4));
            view.addView(Ui.label(c, "Sub type", 12, Ui.MUTED, true), Ui.wide(c, 10));
            view.addView(subs.view, Ui.wide(c, 4));
            kinds.set(names, at, () -> {
                kind = kinds.selected();
                fillSubs("");
            });
            fillSubs(curSub);
        }

        private void fillSubs(String want) {
            subList = routine.subsOf(kind);
            List<String> names = new ArrayList<>();
            int at = subList.size() - 1;
            for (int i = 0; i < subList.size(); i++) {
                names.add(subList.get(i).name);
                if (want != null && subList.get(i).name.equalsIgnoreCase(want)) at = i;
            }
            if (names.isEmpty()) names.add(Routine.OTHER);
            subs.set(names, Math.max(0, at), null);
        }

        int kind() {
            return kind;
        }

        String sub() {
            int i = subs.selected();
            return i >= 0 && i < subList.size() ? subList.get(i).name : Routine.OTHER;
        }
    }
}
