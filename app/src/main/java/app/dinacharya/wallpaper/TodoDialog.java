package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The to-do lists of one day: a box that shows each list with its items to tick, remove and add to. The lists belong to that day and
 * show on the home screen while their slot (or a slot of their goal's type) is going on. An item can be set to repeat (every day, every
 * other day or on chosen days): it then comes back on those days, ticked fresh.
 */
final class TodoDialog {
    /** One list in the box: a heading, which list it is, and where a repeating item of it goes on other days (null: it cannot repeat). */
    static final class Section {
        final String heading;
        final String scope;
        final String repeatScope;

        Section(String heading, String scope) {
            this(heading, scope, null);
        }

        Section(String heading, String scope, String repeatScope) {
            this.heading = heading;
            this.scope = scope;
            this.repeatScope = repeatScope;
        }
    }

    private final Context c;
    private final LocalDate day;
    private final List<Section> sections;
    private final Runnable changed;
    private final Todos todos;
    private final LinearLayout box;

    private TodoDialog(Context c, LocalDate day, List<Section> sections, List<Routine.Block> blocks, Runnable changed) {
        this.c = c;
        this.day = day;
        this.sections = sections;
        this.changed = changed;
        this.todos = Store.todosFor(c, day, blocks);
        this.box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), 0);
    }

    /** Opens the box for these lists on {@code day}. {@code changed} runs after every change (to redraw what shows them). */
    static void show(Context c, LocalDate day, String title, List<Section> sections, Runnable changed) {
        show(c, day, title, sections, null, changed);
    }

    /** Same, with the day's slots, so that the items that repeat into slots are in their lists. */
    static void show(Context c, LocalDate day, String title, List<Section> sections, List<Routine.Block> blocks, Runnable changed) {
        TodoDialog d = new TodoDialog(c, day, sections, blocks, changed);
        d.fill();
        ScrollView sv = new ScrollView(c);
        sv.addView(d.box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new DBuilder(c)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("Done", null)
                .show();
    }

    private void save() {
        Store.saveTodos(c, todos);
        if (changed != null) changed.run();
    }

    private void fill() {
        box.removeAllViews();
        for (int s = 0; s < sections.size(); s++) {
            final Section sec = sections.get(s);
            if (sections.size() > 1 || !sec.heading.isEmpty()) {
                TextView h = Ui.label(c, sec.heading, 13, Ui.TEAL_DARK, true);
                box.addView(h, Ui.wide(c, s == 0 ? 0 : 16));
            }
            List<Todos.Item> items = todos.items(day, sec.scope);
            if (items.isEmpty()) {
                box.addView(Ui.label(c, "Nothing yet. Add what you want to get done in this slot.", 13, Ui.MUTED, false), Ui.wide(c, 4));
            }
            for (int i = 0; i < items.size(); i++) {
                final int index = i;
                final Todos.Item it = items.get(i);
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                CheckBox cb = new CheckBox(c);
                cb.setChecked(it.done);
                cb.setText(it.text);
                cb.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
                cb.setTextColor(it.done ? Ui.MUTED : Ui.INK);
                cb.setOnCheckedChangeListener((b, on) -> {
                    todos.setDone(day, sec.scope, index, on);
                    save();
                    fill();
                });
                row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(Ui.iconButton(c, "✕", Ui.MUTED, v -> {
                    todos.remove(day, sec.scope, index);
                    save();
                    fill();
                }));
                box.addView(row, Ui.wide(c, 2));
                Todos.Repeat rp = it.repeatId > 0 ? todos.repeatOf(it.repeatId) : null;
                if (rp != null) {
                    final int rid = rp.id;
                    final String what = it.text;
                    TextView tag = Ui.label(c, "↻  " + Lang.tr(rp.ruleText()) + "  ·  " + Lang.tr("tap to stop repeating"), 12, Ui.TEAL_DARK, false);
                    tag.setPadding(Ui.dp(c, 48), 0, 0, Ui.dp(c, 4));
                    tag.setOnClickListener(v -> new DBuilder(c)
                            .setTitle("Stop repeating this?")
                            .setMessage("“" + what + "” will not come back on other days. Where it already is stays as it is.")
                            .setPositiveButton("Stop repeating", (d, w) -> {
                                todos.stopRepeat(rid);
                                save();
                                fill();
                            })
                            .setNegativeButton("Keep it", null)
                            .show());
                    box.addView(tag, Ui.wide(c, 0));
                }
            }
            final EditText add = new EditText(c);
            add.setSingleLine(true);
            add.setHint("Add a to-do");
            add.setTextColor(Ui.INK);
            add.setImeOptions(EditorInfo.IME_ACTION_DONE);
            add.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            add.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Todos.MAX_LEN)});
            // How the new item repeats: set with the ↻ button, and back to "does not repeat" after each add.
            final int[] mode = {Todos.NO_REPEAT};
            final int[] mask = {31};
            final TextView rule = Ui.label(c, "", 12, Ui.TEAL_DARK, true);
            final LinearLayout dayChips = new LinearLayout(c);
            dayChips.setOrientation(LinearLayout.HORIZONTAL);
            final TextView[] chips = new TextView[7];
            final Runnable paint = () -> {
                boolean chosen = mode[0] == Todos.CHOSEN_DAYS;
                dayChips.setVisibility(chosen ? View.VISIBLE : View.GONE);
                rule.setVisibility(mode[0] == Todos.NO_REPEAT ? View.GONE : View.VISIBLE);
                rule.setText("↻  " + Lang.tr(Todos.ruleText(mode[0], mask[0])));
                for (int d = 0; d < 7; d++) {
                    boolean on = ((mask[0] >> d) & 1) != 0;
                    chips[d].setTextColor(on ? 0xFFFFFFFF : Ui.MUTED);
                    chips[d].setBackground(Ui.box(c, on ? Ui.TEAL : Ui.SOFT, 0, 10));
                }
            };
            for (int d = 0; d < 7; d++) {
                final int dd = d;
                TextView t = Ui.label(c, Routine.DAY_SHORT[d].substring(0, 2), 13, Ui.MUTED, true);
                t.setGravity(Gravity.CENTER);
                t.setOnClickListener(v -> {
                    mask[0] ^= 1 << dd;
                    paint.run();
                });
                chips[d] = t;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 36), 1f);
                if (d > 0) lp.leftMargin = Ui.dp(c, 4);
                dayChips.addView(t, lp);
            }
            final Runnable put = () -> {
                String text = add.getText().toString();
                if (sec.repeatScope == null) mode[0] = Todos.NO_REPEAT;
                int at = mode[0] == Todos.NO_REPEAT ? todos.add(day, sec.scope, text)
                        : todos.addRepeating(day, sec.scope, sec.repeatScope, text, mode[0], mask[0]);
                if (at < 0) {
                    if (!text.trim().isEmpty()) {
                        boolean repeating = mode[0] != Todos.NO_REPEAT;
                        Lang.toast(c, repeating && todos.repeatCount() >= Todos.MAX_REPEATS
                                ? "Up to " + Todos.MAX_REPEATS + " things can repeat"
                                : "A list can hold " + Todos.MAX_ITEMS + " items", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                save();
                fill();
            };
            add.setOnEditorActionListener((v, action, e) -> {
                put.run();
                return true;
            });
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(add, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (sec.repeatScope != null) {
                final android.widget.Button again = Ui.iconButton(c, "↻", Ui.TEAL_DARK, null);
                again.setContentDescription("Repeat");
                again.setOnClickListener(v -> {
                    PopupMenu pm = new PopupMenu(c, again);
                    Menu m = pm.getMenu();
                    int[] modes = {Todos.NO_REPEAT, Todos.EVERY_DAY, Todos.EVERY_OTHER_DAY, Todos.CHOSEN_DAYS};
                    String[] names = {"Does not repeat", "Every day", "Every other day", "On chosen days"};
                    for (int i = 0; i < modes.length; i++) m.add(0, i, i, (mode[0] == modes[i] ? "✓  " : "      ") + Lang.tr(names[i]));
                    pm.setOnMenuItemClickListener(item -> {
                        mode[0] = modes[item.getItemId()];
                        if (mode[0] == Todos.CHOSEN_DAYS && (mask[0] & 0x7F) == 0) mask[0] = 31;
                        paint.run();
                        return true;
                    });
                    pm.show();
                });
                line.addView(again);
            }
            line.addView(Ui.iconButton(c, "＋", Ui.TEAL_DARK, v -> put.run()));
            box.addView(line, Ui.wide(c, 6));
            if (sec.repeatScope != null) {
                box.addView(rule, Ui.wide(c, 2));
                box.addView(dayChips, Ui.wide(c, 6));
            }
            paint.run();
        }
        box.addView(Ui.label(c, "A to-do is for this day only, unless you set it to repeat with the ↻ button.", 12, Ui.MUTED, false), Ui.wide(c, 14));
        Lang.watch(box);
    }

    /** The lists for a slot of a day: its own, then one for each goal of the same type that counts that day. */
    static List<Section> sectionsFor(Routine day, LocalDate date, Routine.Block b, List<Routine.Block> schedule) {
        List<Section> out = new ArrayList<>();
        String name = b.title.isEmpty() ? Routine.KIND_NAMES[b.kind] : b.title;
        out.add(new Section(name, Todos.slotScope(b.src), Todos.slotNameScope(b.kind, b.title)));   // a to-do belongs to its slot only
        return out;
    }
}
