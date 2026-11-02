package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** The Routine tab: your routines (weekday, weekend…), their days, start time, slots and activity types. */
final class RoutinePage extends Page {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int MENU_ADD = 1, MENU_NOTE = 2, MENU_DELETE = 3, MENU_DUPLICATE = 4;

    /** Selections from this number up are temporary routines: TEMP_BASE + i = temps.get(i). */
    private static final int TEMP_BASE = 1000;
    /** Which routine is being edited: 0 = the main one, k = others.get(k - 1), TEMP_BASE + i = temps.get(i). */
    private int sel;
    private LinearLayout chipsBox;
    private LinearLayout daysBox;
    private LinearLayout routineActions;
    private TextView anchorLabel;
    private Button timeBtn;
    private TextView facts;
    private LinearLayout allotBox;
    private Segmented modeSeg;
    private LinearLayout alarmBanner;
    private LinearLayout rowsBox;
    private LinearLayout typesBox;
    private RowDragger dragger;
    /** Per row: start time, end time, length. Filled every time the rows are built. */
    private final List<TextView[]> rowLabels = new ArrayList<>();
    /** Redraws the short line of each slot card (and shows or hides its details), in the order of the cards. */
    private final List<Runnable> rowRefresh = new ArrayList<>();
    /** The slots whose details (type, length, alarm, more) are open. They stay open while the list is rebuilt. */
    private final java.util.Set<Routine.Block> openRows = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private boolean busy;

    RoutinePage(MainActivity host) {
        super(host);
    }

    private abstract static class Watcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void afterTextChanged(Editable e) {
            changed(e.toString());
        }

        abstract void changed(String s);
    }

    private abstract static class Picked implements AdapterView.OnItemSelectedListener {
        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    // ------------------------------------------------------------------ small helpers

    private Routine main() {
        return a.routine();
    }

    /** The routine being edited. */
    private Routine sched() {
        if (sel >= TEMP_BASE) {
            int i = sel - TEMP_BASE;
            if (i < main().temps.size()) return main().temps.get(i);
            sel = 0;
        }
        if (sel > main().others.size()) sel = 0;
        return main().routineAt(sel);
    }

    private boolean tempSelected() {
        return sel >= TEMP_BASE && sel - TEMP_BASE < main().temps.size();
    }

    /** "Today", "Tomorrow" or "Mon 26 Oct". */
    private String dateText(LocalDate d) {
        LocalDate today = LocalDate.now(Store.zone());
        if (d.equals(today)) return "Today";
        if (d.equals(today.plusDays(1))) return "Tomorrow";
        return d.format(DateTimeFormatter.ofPattern("EEE d MMM", Lang.locale()));
    }

    private int dp(float v) {
        return a.dp(v);
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        return Ui.label(a, text, sp, color, bold);
    }

    private Button button(String text, boolean primary, View.OnClickListener click) {
        return Ui.button(a, text, primary, click);
    }

    private LinearLayout.LayoutParams wide(float top) {
        return Ui.wide(a, top);
    }

    private LinearLayout.LayoutParams wrap(float top) {
        return Ui.wrap(a, top);
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout pair(Button left, Button right) {
        LinearLayout l = row();
        LinearLayout.LayoutParams x = new LinearLayout.LayoutParams(0, WRAP, 1f);
        x.rightMargin = dp(5);
        LinearLayout.LayoutParams y = new LinearLayout.LayoutParams(0, WRAP, 1f);
        y.leftMargin = dp(5);
        l.addView(left, x);
        l.addView(right, y);
        return l;
    }

    private int kindColor(int kind) {
        int[] c = PosterRenderer.swatch(main().palette, false);
        return c[3 + Math.max(0, Math.min(Routine.SLEEP, kind))];
    }

    private static String nameOf(Routine.Block b) {
        String t = b.title == null ? "" : b.title.trim();
        return t.isEmpty() ? Routine.KIND_NAMES[b.kind] : t;
    }

    private static int mixWhite(int c, float f) {
        int r = Math.round(((c >> 16) & 255) * (1 - f) + 255 * f);
        int g = Math.round(((c >> 8) & 255) * (1 - f) + 255 * f);
        int b = Math.round((c & 255) * (1 - f) + 255 * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private TextView chip(String text, boolean on, int fill, View.OnClickListener click) {
        TextView t = label(text, 14, on ? 0xFFFFFFFF : Ui.INK, on);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setBackground(on ? Ui.pressable(a, Ui.TEAL, 0, 18, 0x40FFFFFF) : Ui.pressable(a, fill, 0, 18, 0x300E7C7B));
        t.setOnClickListener(click);
        return t;
    }

    private TextView outlineChip(String text, View.OnClickListener click) {
        TextView t = label(text, 14, Ui.TEAL, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setBackground(Ui.pressable(a, Ui.SURFACE, Ui.TEAL, 18, 0x300E7C7B));
        t.setOnClickListener(click);
        return t;
    }

    /** Saves, then lets every tab and the home screen know. */
    private void changed() {
        a.persist();
        a.routineChanged();
        askFirstEdit();
    }

    // ------------------------------------------------------------------ first edit: change this routine, or make a new one

    private String baseline;
    private boolean askingFirst;

    private boolean firstEditDone() {
        try {
            return Store.prefs(a).getBoolean("first_edit_asked", false);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void markFirstEditDone() {
        try {
            Store.prefs(a).edit().putBoolean("first_edit_asked", true).apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** The first time a new user edits the only routine, asks whether to change it or to keep it and make a new one. */
    private void askFirstEdit() {
        if (askingFirst || firstEditDone()) return;
        final Routine m = main();
        if (!m.others.isEmpty() || !m.temps.isEmpty() || sel != 0 || baseline == null) {
            markFirstEditDone();
            return;
        }
        askingFirst = true;
        final String before = baseline;
        final String edited = a.snapshot();
        new DBuilder(a)
                .setTitle("Change this routine, or make a new one?")
                .setMessage("You just changed your routine. Do you want to keep the change in this routine, or leave this routine as it was and put the change in a new routine?")
                .setCancelable(false)
                .setPositiveButton("Change this routine", (d, w) -> {
                    askingFirst = false;
                    markFirstEditDone();
                    baseline = a.snapshot();
                })
                .setNegativeButton("Make a new routine", (d, w) -> {
                    askingFirst = false;
                    if (!a.requirePro(DayPass.ROUTINES, "Weekday and weekend routines", () -> { })) {
                        baseline = a.snapshot();
                        return;
                    }
                    markFirstEditDone();
                    Routine changedCopy = Routine.parse(edited);
                    Routine old = Routine.parse(before);
                    if (changedCopy == null || old == null) return;
                    m.replaceWith(old);
                    m.addOther("My new routine", changedCopy);
                    sel = m.others.size();
                    baseline = a.snapshot();
                    a.persist();
                    a.routineChanged();
                    a.toastText("Your first routine is unchanged. The change is in “My new routine”.");
                })
                .show();
    }

    // ------------------------------------------------------------------ building

    @Override
    void build(LinearLayout body) {
        title("Routine", "Your day, slot by slot");
        baseline = a.snapshot();

        alarmBanner = new LinearLayout(a);
        alarmBanner.setOrientation(LinearLayout.VERTICAL);
        body.addView(alarmBanner, wide(12));

        // Which routine, and on which days.
        LinearLayout pick = Ui.section(a, body, "Routines", null);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        chipsBox = row();
        hs.addView(chipsBox, new ViewGroup.LayoutParams(WRAP, WRAP));
        pick.addView(hs, wide(10));
        daysBox = row();
        pick.addView(daysBox, wide(12));
        routineActions = row();
        pick.addView(routineActions, wide(10));

        // Start of day for the routine being edited.
        LinearLayout start = Ui.section(a, body, "Start of day", "Set one time, the day follows");
        modeSeg = new Segmented(a, new String[]{"Wake-up time", "First task time"});
        modeSeg.setListener(i -> setMode(i == 1 ? Routine.MODE_TASK : Routine.MODE_WAKE));
        start.addView(modeSeg, wide(14));
        anchorLabel = label("I wake up at", 13, Ui.MUTED, true);
        start.addView(anchorLabel, wrap(14));
        timeBtn = new Button(a);
        timeBtn.setAllCaps(false);
        timeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        timeBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBtn.setTextColor(Ui.INK);
        timeBtn.setStateListAnimator(null);
        timeBtn.setBackground(Ui.pressable(a, Ui.SOFT, Ui.LINE, 18, 0x300E7C7B));
        timeBtn.setPadding(dp(12), dp(14), dp(12), dp(14));
        timeBtn.setOnClickListener(v -> pickTime());
        start.addView(timeBtn, wide(6));
        facts = label("", 13, Ui.MUTED, false);
        facts.setLineSpacing(dp(2), 1f);
        start.addView(facts, wide(10));
        // How the day is shared by type: one tap away, so the card stays short.
        Expander.Part allot = Expander.more(a, start, "Time by type", "routine_allot", false);
        allotBox = allot.body;

        // Slots.
        Ui.heading(a, body, "Slots", "Tap a name to rename it, ⋮ for more");
        Expander.Part help = Expander.more(a, body, "How to edit slots", "routine_help", false);
        help.body.addView(label("Hold ≡ and drag to move a slot. Swipe a slot left to delete it, right to add one below it. "
                + "Tap the arrow on a slot for its type, length, alarm and more. "
                + "The day keeps its length: minutes only move from one slot to another.", 13, Ui.MUTED, false), wide(2));
        body.addView(pair(button("＋  Add slot", true, v -> showAddDialog(defaultAddAfter())),
                button("Add rests", false, v -> addRests())), wide(12));
        rowsBox = column();
        body.addView(rowsBox, wide(2));
        dragger = new RowDragger(rowsBox, scroll, dp(10), dp(80), dp(30), (from, to) -> moveSlot(from, to));
        Expander.Part tools = Expander.more(a, body, "More tools", "routine_tools", false);
        tools.body.addView(pair(button("Paste from web app", false, v -> pasteRoutine()),
                button("Reset to sample", false, v -> confirmReset())), wide(6));

        // Activity types (shared by all routines).
        LinearLayout types = Expander.card(a, body, "Activity types", "Give a slot a type", "Study, Exercise, Meal and the rest, by kind",
                "routine_types", false).body;
        types.addView(label("A type is a kind of activity inside a main kind, for example Study inside Task. "
                + "Pick it on any slot. The home screen shows it under the slot's name; reports and goals count it. "
                + "Tap a type to remove it.", 13, Ui.MUTED, false), wide(4));
        typesBox = column();
        types.addView(typesBox, wide(6));
        a.addFooter(body);
    }

    @Override
    void refresh() {
        if (!built()) return;
        main().pruneTemps(LocalDate.now(Store.zone()));
        refreshAlarmBanner();
        rebuildPicker();
        rebuildRows();
        refreshStart();
    }

    @Override
    void hidden() {
        a.flushSave();
    }

    @Override
    void alarmsChanged() {
        refreshAlarmBanner();
        if (rowsBox != null) rebuildRows();   // the bells on the slots show the new state
    }

    /** While alarms are off, says so at the top, because the bells on the slots do nothing then. */
    private void refreshAlarmBanner() {
        if (alarmBanner == null) return;
        alarmBanner.removeAllViews();
        if (a.routine().alarmOn) return;
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.AMBER, 20));
        card.addView(label("Alarms are off", 16, Ui.INK, true));
        card.addView(label("The bells on the slots below will not ring until you turn alarms on.", 13, Ui.MUTED, false), wide(4));
        card.addView(button("Turn on alarms", true, v -> a.extras.enableAlarms()), wide(10));
        alarmBanner.addView(card, wide(0));
    }

    private void askTurnOnAlarms() {
        new android.app.AlertDialog.Builder(a)
                .setTitle("Alarms are off")
                .setMessage("Turn them on so the bells on your slots ring when a slot starts?")
                .setPositiveButton("Turn on", (d, w) -> a.extras.enableAlarms())
                .setNegativeButton("Not now", null)
                .show();
    }

    // ------------------------------------------------------------------ routines and days

    private void rebuildPicker() {
        chipsBox.removeAllViews();
        Routine m = main();
        List<Routine> all = m.all();
        for (int k = 0; k < all.size(); k++) {
            final int idx = k;
            TextView c = chip(all.get(k).name, k == sel, Ui.SOFT, v -> {
                if (sel == idx) return;
                a.flushSave();
                sel = idx;
                refresh();
            });
            c.setOnLongClickListener(v -> {
                sel = idx;
                refresh();
                askRename();
                return true;
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
            p.rightMargin = dp(8);
            chipsBox.addView(c, p);
        }
        for (int i = 0; i < m.temps.size(); i++) {
            final int idx = TEMP_BASE + i;
            TextView c = chip(dateText(m.temps.get(i).tempDate), idx == sel, Ui.AMBER_SOFT, v -> {
                if (sel == idx) return;
                a.flushSave();
                sel = idx;
                refresh();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
            p.rightMargin = dp(8);
            chipsBox.addView(c, p);
        }
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(WRAP, WRAP);
        np.rightMargin = dp(8);
        chipsBox.addView(outlineChip(m.hasWeek() ? "＋ New" : "＋ Weekend routine", v -> askNewRoutine()), np);
        chipsBox.addView(outlineChip("＋ One day only", v -> askNewTemp()));

        daysBox.removeAllViews();
        routineActions.removeAllViews();
        if (tempSelected()) {
            daysBox.setVisibility(View.GONE);
            Routine t = sched();
            routineActions.addView(label("Only for " + dateText(t.tempDate) + ". After that day the usual routine comes back.",
                    12, Ui.MUTED, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
            routineActions.addView(outlineChip("Date", v -> askTempDate()));
            LinearLayout.LayoutParams delp = new LinearLayout.LayoutParams(WRAP, WRAP);
            delp.leftMargin = dp(6);
            routineActions.addView(outlineChip("Delete", v -> confirmDeleteTemp()), delp);
            return;
        }
        if (!m.hasWeek()) {
            daysBox.setVisibility(View.GONE);
            routineActions.addView(label("Want a different day on weekends or exam days? Add another routine and pick its days.",
                    12, Ui.MUTED, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
            return;
        }
        daysBox.setVisibility(View.VISIBLE);
        for (int d = 0; d < 7; d++) {
            final int day = d;
            boolean mine = m.dayUse[d] == sel;
            TextView t = label(Routine.DAY_SHORT[d].substring(0, 2), 13, mine ? 0xFFFFFFFF : Ui.MUTED, mine);
            t.setGravity(Gravity.CENTER);
            t.setBackground(mine ? Ui.box(a, Ui.TEAL, 0, 20) : Ui.pressable(a, Ui.SOFT, Ui.LINE, 20, 0x300E7C7B));
            t.setContentDescription(Routine.DAY_SHORT[d] + (mine ? ": uses this routine" : ": uses " + m.routineAt(m.dayUse[d]).name));
            t.setOnClickListener(v -> toggleDay(day));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (d > 0) p.leftMargin = dp(4);
            daysBox.addView(t, p);
        }
        int count = 0;
        for (int d = 0; d < 7; d++) if (m.dayUse[d] == sel) count++;
        String used = count == 0 ? "Not used on any day yet: tap the days that should follow it."
                : count == 1 ? "Used on 1 day. Tap a day to change." : "Used on " + count + " days. Tap a day to change.";
        routineActions.addView(label(used, 12, Ui.MUTED, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView rename = outlineChip("Rename", v -> askRename());
        routineActions.addView(rename);
        if (sel > 0) {
            TextView del = outlineChip("Delete", v -> confirmDeleteRoutine());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
            p.leftMargin = dp(6);
            routineActions.addView(del, p);
        }
    }

    private void toggleDay(int d) {
        Routine m = main();
        String snap = a.snapshot();
        if (m.dayUse[d] == sel) {
            if (sel == 0) {
                a.toastText("Every day needs a routine. Pick another routine, then tap this day to give it to that one.");
                return;
            }
            m.dayUse[d] = 0;
        } else {
            m.dayUse[d] = sel;
        }
        changed();
        a.undoable(Routine.DAY_SHORT[d] + " now follows “" + m.routineAt(m.dayUse[d]).name + "”.", snap);
    }

    private void askNewRoutine() {
        if (!a.requirePro(DayPass.ROUTINES, "Weekday and weekend routines", this::askNewRoutine)) return;
        final Routine m = main();
        final EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setText(m.hasWeek() ? "Exam day" : "Weekend");
        name.setSelectAllOnFocus(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Routine.MAX_NAME_LEN)});
        final Spinner from = new Spinner(a);
        List<String> names = new ArrayList<>();
        for (Routine r : m.all()) names.add("Copy of “" + r.name + "”");
        ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        from.setAdapter(ad);
        from.setSelection(sel);
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(label("Name", 12, Ui.MUTED, true));
        box.addView(name, wide(0));
        box.addView(label("Start from", 12, Ui.MUTED, true), wrap(10));
        box.addView(from, wide(0));
        box.addView(label("You can change its slots and start time after, and pick the days it is used on.", 12, Ui.MUTED, false), wide(8));
        new DBuilder(a)
                .setTitle("New routine")
                .setView(box)
                .setPositiveButton("Create", (dlg, w) -> {
                    String snap = a.snapshot();
                    if (m.name.equals("My routine")) m.name = "Weekdays";
                    int src = Math.max(0, from.getSelectedItemPosition());
                    m.addOther(name.getText().toString(), m.routineAt(src));
                    sel = m.others.size();
                    boolean weekend = name.getText().toString().toLowerCase(java.util.Locale.ROOT).contains("weekend");
                    if (weekend) {
                        if (m.dayUse[5] == 0) m.dayUse[5] = sel;
                        if (m.dayUse[6] == 0) m.dayUse[6] = sel;
                    }
                    changed();
                    a.undoable(weekend ? "Made “" + m.routineAt(sel).name + "” for Saturday and Sunday."
                            : "Made “" + m.routineAt(sel).name + "”. Tap the days that should use it.", snap);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Which routine a date would follow without a temporary one: the main routine, or one of the others. */
    private int usualFor(LocalDate d) {
        return main().useOn(d);
    }

    /** Makes a temporary routine for today, tomorrow or a picked date. */
    private void askNewTemp() {
        if (!a.requirePro(DayPass.ROUTINES, "A routine for one day", this::askNewTemp)) return;
        final Routine m = main();
        final LocalDate today = LocalDate.now(Store.zone());
        final LocalDate[] date = {today.plusDays(1)};
        final Spinner from = new Spinner(a);
        final List<String> names = new ArrayList<>();
        for (Routine r : m.all()) names.add("Copy of “" + r.name + "”");
        ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        from.setAdapter(ad);
        from.setSelection(Math.min(usualFor(date[0]), names.size() - 1));
        final RadioGroup when = new RadioGroup(a);
        final RadioButton rToday = new RadioButton(a);
        rToday.setId(View.generateViewId());
        rToday.setText("Today");
        when.addView(rToday);
        final RadioButton rTomorrow = new RadioButton(a);
        rTomorrow.setId(View.generateViewId());
        rTomorrow.setText("Tomorrow");
        when.addView(rTomorrow);
        final RadioButton rPick = new RadioButton(a);
        rPick.setId(View.generateViewId());
        rPick.setText("Another date…");
        when.addView(rPick);
        when.check(rTomorrow.getId());
        final TextView pickedText = label("", 13, Ui.MUTED, false);
        when.setOnCheckedChangeListener((g, id) -> {
            if (id == rToday.getId()) date[0] = today;
            else if (id == rTomorrow.getId()) date[0] = today.plusDays(1);
            else {
                try {
                    android.app.DatePickerDialog picker = new android.app.DatePickerDialog(a, (view, y, mo, d) -> {
                        date[0] = LocalDate.of(y, mo + 1, d);
                        pickedText.setText(dateText(date[0]));
                        from.setSelection(Math.min(usualFor(date[0]), names.size() - 1));
                    }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth());
                    picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000L);
                    picker.show();
                } catch (RuntimeException e) {
                    CrashLog.note(a, "date picker", e);
                }
            }
            if (id != rPick.getId()) pickedText.setText("");
            from.setSelection(Math.min(usualFor(date[0]), names.size() - 1));
        });
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(label("A routine that is followed on one date only, then the usual one comes back.", 13, Ui.MUTED, false));
        box.addView(label("Which day", 12, Ui.MUTED, true), wrap(12));
        box.addView(when, wide(0));
        box.addView(pickedText, wide(2));
        box.addView(label("Start from", 12, Ui.MUTED, true), wrap(10));
        box.addView(from, wide(0));
        box.addView(label("You can change its slots and start time after.", 12, Ui.MUTED, false), wide(8));
        new DBuilder(a)
                .setTitle("A routine for one day")
                .setView(box)
                .setPositiveButton("Create", (dlg, w) -> {
                    LocalDate d = date[0];
                    if (d.isBefore(today)) d = today;
                    String snap = a.snapshot();
                    int src = Math.max(0, Math.min(from.getSelectedItemPosition(), m.all().size() - 1));
                    boolean had = m.tempFor(d) != null;
                    Routine t = m.addTemp(d, dateText(d), m.all().get(src));
                    // Changes made to that day's slots (renamed, taken off) belonged to the routine it followed before.
                    Overrides ov = Store.overrides(a, m);
                    ov.clearEdits(d);
                    Store.saveOverrides(a, m, ov);
                    sel = TEMP_BASE + m.temps.indexOf(t);
                    changed();
                    a.undoable(had ? "Replaced the routine for " + dateText(d) + ". Change its slots below."
                            : "Made a routine for " + dateText(d) + ". Change its slots below.", snap);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askTempDate() {
        if (!tempSelected()) return;
        final Routine t = sched();
        final LocalDate today = LocalDate.now(Store.zone());
        try {
            LocalDate cur = t.tempDate.isBefore(today) ? today : t.tempDate;
            android.app.DatePickerDialog picker = new android.app.DatePickerDialog(a, (view, y, mo, d) -> {
                LocalDate nd = LocalDate.of(y, mo + 1, d);
                String snap = a.snapshot();
                LocalDate old = t.tempDate;
                main().moveTemp(t, nd);
                Overrides ov = Store.overrides(a, main());
                ov.clearEdits(nd);
                ov.clearEdits(old);
                Store.saveOverrides(a, main(), ov);
                sel = TEMP_BASE + main().temps.indexOf(t);
                changed();
                a.undoable("Routine moved to " + dateText(nd) + ".", snap);
            }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth());
            picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000L);
            picker.show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "date picker", e);
        }
    }

    private void confirmDeleteTemp() {
        if (!tempSelected()) return;
        final Routine t = sched();
        new DBuilder(a)
                .setTitle("Delete the routine for " + dateText(t.tempDate) + "?")
                .setMessage("That day follows its usual routine again.")
                .setPositiveButton("Delete", (d, w) -> {
                    String snap = a.snapshot();
                    LocalDate day = t.tempDate;
                    main().temps.remove(t);
                    Overrides ov = Store.overrides(a, main());
                    ov.clearEdits(day);
                    Store.saveOverrides(a, main(), ov);
                    sel = 0;
                    changed();
                    a.undoable("Deleted the routine for " + dateText(day) + ".", snap);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askRename() {
        final Routine r = sched();
        final EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setText(r.name);
        name.setSelectAllOnFocus(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Routine.MAX_NAME_LEN)});
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(name, wide(0));
        new DBuilder(a)
                .setTitle("Rename routine")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    r.name = Routine.cleanName(name.getText().toString());
                    changed();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteRoutine() {
        if (sel == 0) return;
        final Routine r = sched();
        new DBuilder(a)
                .setTitle("Delete “" + r.name + "”?")
                .setMessage("Its days go back to “" + main().name + "”. Its slots are deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    String snap = a.snapshot();
                    main().removeOther(sel);
                    sel = 0;
                    changed();
                    a.undoable("Deleted “" + r.name + "”.", snap);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ start of day

    private void refreshStart() {
        Routine r = sched();
        Routine.Schedule sc = r.compute();
        boolean taskMode = sc.mode == Routine.MODE_TASK;
        boolean h24 = main().fmt24;
        anchorLabel.setText((main().hasWeek() ? "“" + r.name + "”: " : "") + (taskMode ? "my first task starts at" : "I wake up at"));
        timeBtn.setText(Routine.formatTime(r.anchor, h24));
        modeSeg.pick(taskMode ? 1 : 0, false);
        String first = sc.firstTaskStart >= 0 ? Routine.formatTime(sc.firstTaskStart, h24) : "none";
        String note;
        if (sc.total > 1440) note = "Over by " + Routine.formatDuration(sc.total - 1440) + ". Your sleep runs past the next wake-up.";
        else if (sc.total < 1440) note = "Unplanned time: " + Routine.formatDuration(1440 - sc.total) + " of the 24 hours.";
        else note = "Your slots add up to exactly 24 hours.";
        facts.setText("Wake-up " + Routine.formatTime(sc.start, h24) + "   ·   First task " + first + "   ·   "
                + (sc.hasSleep ? "Bedtime " + Routine.formatTime(sc.bedStart, h24) : "Day ends " + Routine.formatTime(sc.bedStart, h24))
                + "\n" + note);
        refreshAllot(r);
        updateTimes(sc);
    }

    /** "Time by type": how the routine's minutes are shared between kinds of slot and, inside each, activity types. */
    private void refreshAllot(Routine r) {
        allotBox.removeAllViews();
        int total = r.totalMinutes();
        if (total <= 0) return;
        List<Allotment.Item> items = Allotment.bySub(r);
        // One bar, a slice for each type in its own colour.
        LinearLayout bar = row();
        bar.setBackground(Ui.box(a, Ui.SOFT, 0, 5));
        for (Allotment.Item it : items) {
            View seg = new View(a);
            seg.setBackgroundColor(SubColors.of(main(), it.kind, it.sub));
            bar.addView(seg, new LinearLayout.LayoutParams(0, dp(12), it.mins));
        }
        allotBox.addView(bar, Ui.lp(a, MATCH, dp(12), 6));
        int[] kinds = Allotment.byKind(r);
        for (int k = 0; k < kinds.length; k++) {
            if (kinds[k] <= 0) continue;
            LinearLayout head = row();
            head.addView(label(Routine.KIND_NAMES[k], 14, Ui.INK, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
            head.addView(label(Routine.formatDuration(kinds[k]) + "  ·  " + Math.round(100f * kinds[k] / total) + "%", 13, Ui.MUTED, false));
            allotBox.addView(head, wide(10));
            for (Allotment.Item it : items) {
                if (it.kind != k) continue;
                LinearLayout line = row();
                View dot = new View(a);
                dot.setBackground(Ui.box(a, SubColors.of(main(), it.kind, it.sub), 0, 6));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
                dlp.leftMargin = dp(4);
                dlp.rightMargin = dp(10);
                line.addView(dot, dlp);
                line.addView(label(it.sub, 13, Ui.INK, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
                line.addView(label(Routine.formatDuration(it.mins), 13, Ui.MUTED, false));
                allotBox.addView(line, wide(3));
            }
        }
    }

    private void setMode(int m) {
        Routine r = sched();
        Routine.Schedule sc = r.compute();
        if (m == Routine.MODE_TASK && sc.firstTaskStart < 0) {
            a.toastText("Add a Task slot first");
            refreshStart();
            return;
        }
        if (m == sc.mode && m == r.mode) return;
        r.anchor = Routine.mod(m == Routine.MODE_TASK ? sc.firstTaskStart : sc.start, 1440);
        r.mode = m;
        changed();
    }

    private void pickTime() {
        final Routine r = sched();
        try {
            new TimePickerDialog(a, (view, h, m) -> {
                r.anchor = h * 60 + m;
                changed();
            }, r.anchor / 60, r.anchor % 60, main().fmt24).show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "time picker", e);
        }
    }

    // ------------------------------------------------------------------ activity types

    private void rebuildTypes() {
        typesBox.removeAllViews();
        Routine m = main();
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) {
            final int kind = k;
            typesBox.addView(label(Routine.KIND_NAMES[k], 13, kindColor(k), true), wrap(k == 0 ? 6 : 14));
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout chips = row();
            for (final Routine.Sub s : m.subsOf(k)) {
                int col = SubColors.of(m, s.kind, s.name);
                TextView c = chip(s.name, false, mixWhite(col, 0.82f), v -> confirmRemoveType(s));
                c.setCompoundDrawablePadding(dp(8));
                c.setCompoundDrawablesWithIntrinsicBounds(dotDrawable(col), null, null, null);
                c.setContentDescription(Routine.isOther(s.name) ? "Type " + s.name + ". Every kind keeps it."
                        : "Type " + s.name + ". Tap to remove.");
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
                p.rightMargin = dp(8);
                chips.addView(c, p);
            }
            chips.addView(outlineChip("＋ Add", v -> askNewType(kind, null)), new LinearLayout.LayoutParams(WRAP, WRAP));
            hs.addView(chips, new ViewGroup.LayoutParams(WRAP, WRAP));
            typesBox.addView(hs, wide(6));
        }
    }

    /** A small round dot for the left of a chip. */
    private android.graphics.drawable.Drawable dotDrawable(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(dp(10), dp(10));
        return g;
    }

    private void confirmRemoveType(final Routine.Sub s) {
        if (Routine.isOther(s.name)) {
            a.toastText("“Other” stays: slots with no type of their own use it");
            return;
        }
        int used = 0;
        List<Routine> every = main().all();
        every.addAll(main().temps);
        for (Routine r : every) {
            for (Routine.Block b : r.blocks) if (b.kind == s.kind && b.sub.equalsIgnoreCase(s.name)) used++;
        }
        boolean goal = main().findGoal(s.kind, s.name) != null;
        String msg = (used == 0 ? "No slot uses this type."
                : used == 1 ? "1 slot uses it. It keeps its name and time and becomes “Other”."
                : used + " slots use it. They keep their name and time and become “Other”.")
                + (goal ? " Its daily goal is removed too." : "");
        new DBuilder(a)
                .setTitle("Remove “" + s.name + "”?")
                .setMessage(msg)
                .setPositiveButton("Remove", (d, w) -> {
                    String snap = a.snapshot();
                    main().removeSub(s);
                    changed();
                    a.undoable("Removed “" + s.name + "”.", snap);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Asks for a new type under {@code kind}; when {@code b} is given, the slot gets the new type. */
    private void askNewType(final int kind, final Routine.Block b) {
        final EditText in = new EditText(a);
        in.setSingleLine(true);
        in.setHint(kind == Routine.TASK ? "e.g. Maths" : "Type name");
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Routine.MAX_SUB_LEN)});
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(in, new LinearLayout.LayoutParams(MATCH, WRAP));
        new DBuilder(a)
                .setTitle("New " + Routine.KIND_NAMES[kind] + " type")
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    boolean existed = main().findSub(kind, in.getText().toString()) != null;
                    Routine.Sub s = main().addSub(kind, in.getText().toString());
                    if (s == null) {
                        a.toastText("Write a name for the type");
                        return;
                    }
                    if (b != null && b.kind == kind) b.sub = s.name;
                    changed();
                    a.toastText(existed ? "“" + s.name + "” was already there" : "Added “" + s.name + "” under " + Routine.KIND_NAMES[kind]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Spinner kindSpinner() {
        String[] names = new String[Routine.SLEEP];
        System.arraycopy(Routine.KIND_NAMES, 0, names, 0, names.length);
        Spinner sp = new Spinner(a);
        ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        return sp;
    }

    // ------------------------------------------------------------------ rows

    private void rebuildRows() {
        rowsBox.removeAllViews();
        rowLabels.clear();
        rowRefresh.clear();
        Routine r = sched();
        for (int i = 0; i < r.blocks.size(); i++) {
            final int index = i;
            SwipeRow row = new SwipeRow(a, buildRow(r, i));
            row.setListener(new SwipeRow.Listener() {
                @Override
                public void onSwipedLeft() {
                    confirmDelete(index);
                }

                @Override
                public void onSwipedRight() {
                    showAddDialog(index);
                }
            });
            rowsBox.addView(row, wide(10));
        }
        updateTimes(r.compute());
        rebuildTypes();
    }

    /** Rebuilds after the current touch or selection has finished. */
    private void rebuildSoon() {
        rowsBox.post(() -> a.routineChanged());
    }

    private View buildRow(final Routine r, final int index) {
        final Routine.Block b = r.blocks.get(index);
        LinearLayout card = row();
        card.setGravity(Gravity.NO_GRAVITY);
        card.setBackground(Ui.box(a, Ui.SURFACE, 0, 18));
        card.setElevation(dp(1));
        card.setPadding(0, 0, dp(10), 0);

        View bar = new View(a);
        bar.setBackground(Ui.box(a, kindColor(b.kind), 0, 3));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(5), MATCH);
        bp.leftMargin = dp(8);
        bp.topMargin = dp(14);
        bp.bottomMargin = dp(14);
        card.addView(bar, bp);

        LinearLayout body = column();
        body.setPadding(dp(4), dp(6), 0, dp(8));
        card.addView(body, new LinearLayout.LayoutParams(0, WRAP, 1f));

        // Line 1: drag handle, start and end time, name.
        LinearLayout l1 = row();
        TextView handle = label("≡", 22, Ui.MUTED, true);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Drag to move this slot");
        handle.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return dragger.onHandleTouch(index, e);
        });
        l1.addView(handle, new LinearLayout.LayoutParams(dp(36), dp(46)));
        LinearLayout times = column();
        TextView start = label("", 14, Ui.TEAL, true);
        start.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        start.setSingleLine(true);
        TextView end = label("", 11, Ui.MUTED, false);
        end.setTypeface(Typeface.MONOSPACE);
        end.setSingleLine(true);
        times.addView(start);
        times.addView(end);
        l1.addView(times, new LinearLayout.LayoutParams(dp(80), WRAP));
        EditText title = new EditText(a);
        title.setSingleLine(true);
        title.setText(b.title);
        title.setHint("Slot name");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.INK);
        title.setBackground(null);
        title.setPadding(dp(4), dp(8), dp(4), dp(8));
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        final String[] goalSlot = {b.title};   // the name the goals of this slot have now: they follow it while it is being typed
        title.addTextChangedListener(new Watcher() {
            @Override
            void changed(String s) {
                if (s.equals(b.title)) return;
                b.title = s;
                if (!s.trim().isEmpty() && main().renameSlot(b.kind, goalSlot[0], s) > 0) goalSlot[0] = s;
                a.persistSoon();
                a.previewChanged();
            }
        });
        l1.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        body.addView(l1);

        // The note, when there is one.
        if (b.note != null && !b.note.trim().isEmpty()) {
            TextView note = label("📝  " + b.note.trim(), 13, Ui.MUTED, false);
            note.setMaxLines(3);
            note.setPadding(dp(36), dp(2), dp(4), dp(4));
            note.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 10, 0x200E7C7B));
            note.setOnClickListener(v -> askNote(b));
            body.addView(note);
        }

        // The rest of the card (type, length, alarm, more) is one tap away: a short line says what the slot is.
        final LinearLayout details = column();
        final LinearLayout sumRow = row();
        sumRow.setPadding(dp(36), 0, dp(2), dp(2));
        sumRow.setMinimumHeight(dp(36));
        sumRow.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 10, 0x200E7C7B));
        final TextView summary = label("", 13, Ui.MUTED, false);
        sumRow.addView(summary, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView chev = label("▾", 16, Ui.TEAL, true);
        chev.setGravity(Gravity.CENTER);
        sumRow.addView(chev, new LinearLayout.LayoutParams(dp(36), dp(32)));
        final Runnable drawDetails = () -> {
            boolean open = openRows.contains(b);
            details.setVisibility(open ? View.VISIBLE : View.GONE);
            summary.setText(open ? Lang.tr("Details") : summaryOf(b));
            chev.setRotation(open ? 180f : 0f);
            sumRow.setContentDescription((b.title.isEmpty() ? Lang.tr(Routine.KIND_NAMES[b.kind]) : b.title) + ", "
                    + Lang.tr(open ? "details shown. Tap to hide." : "details hidden. Tap to show."));
        };
        sumRow.setOnClickListener(v -> {
            if (!openRows.remove(b)) openRows.add(b);
            drawDetails.run();
        });
        rowRefresh.add(drawDetails);
        body.addView(sumRow);
        body.addView(details);

        // Line 2: kind and activity type.
        LinearLayout l2 = row();
        l2.setPadding(dp(30), 0, 0, 0);
        Spinner kind = new Spinner(a);
        ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, Routine.KIND_NAMES);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        kind.setAdapter(ad);
        kind.setSelection(b.kind);
        kind.setOnItemSelectedListener(new Picked() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == b.kind || position < 0 || position >= Routine.KIND_NAMES.length) return;
                int oldStart = r.compute().start;
                b.kind = position;
                if (b.sub.isEmpty() || main().findSub(b.kind, b.sub) == null) b.sub = Routine.OTHER;
                SlotOps.normalizeAnchor(r, oldStart);
                a.persist();
                a.previewChanged();
                rebuildSoon();
            }
        });
        l2.addView(kind, new LinearLayout.LayoutParams(dp(116), WRAP));
        Spinner sub = new Spinner(a);
        bindSub(sub, b);
        l2.addView(sub, new LinearLayout.LayoutParams(0, WRAP, 1f));
        details.addView(l2);

        // Line 3: length with − and +, alarm bell, more.
        LinearLayout l3 = row();
        final TextView len = label(Routine.formatDuration(b.mins), 15, Ui.INK, true);
        len.setGravity(Gravity.CENTER);
        len.setBackground(Ui.pressable(a, Ui.SOFT, 0, 12, 0x300E7C7B));
        len.setContentDescription("Length. Tap to type the minutes.");
        len.setOnClickListener(v -> askMinutes(b));
        Button minus = Ui.iconButton(a, "−", Ui.INK, v -> stepMinutes(b, -5));
        minus.setOnLongClickListener(v -> {
            stepMinutes(b, -30);
            return true;
        });
        minus.setContentDescription("5 minutes shorter. Hold for 30.");
        l3.addView(minus, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams lenP = new LinearLayout.LayoutParams(dp(72), dp(40));
        lenP.leftMargin = dp(6);
        l3.addView(len, lenP);
        Button plus = Ui.iconButton(a, "+", Ui.INK, v -> stepMinutes(b, 5));
        plus.setOnLongClickListener(v -> {
            stepMinutes(b, 30);
            return true;
        });
        plus.setContentDescription("5 minutes longer. Hold for 30.");
        l3.addView(plus);
        l3.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        final Button bell = Ui.iconButton(a, "", Ui.INK, null);
        styleBell(bell, b);
        bell.setOnClickListener(v -> {
            if (!a.routine().alarmOn) {
                askTurnOnAlarms();
                return;
            }
            pickAlert(bell, b, index);
        });
        l3.addView(bell);
        final Button more = Ui.iconButton(a, "⋮", Ui.INK, null);
        more.setContentDescription("More: add below, note, duplicate, delete");
        more.setOnClickListener(v -> showMenu(more, b, index));
        l3.addView(more);
        details.addView(l3, wide(2));
        drawDetails.run();

        rowLabels.add(new TextView[]{start, end, len});
        return card;
    }

    private void showMenu(View anchor, final Routine.Block b, final int index) {
        PopupMenu pm = new PopupMenu(a, anchor);
        Menu m = pm.getMenu();
        m.add(0, MENU_ADD, 0, Lang.tr("Add a slot below"));
        m.add(0, MENU_NOTE, 1, Lang.tr(b.note.trim().isEmpty() ? "Add a note" : "Edit the note"));
        m.add(0, MENU_DUPLICATE, 2, Lang.tr("Duplicate (splits this slot in two)"));
        m.add(0, MENU_DELETE, 3, Lang.tr("Delete"));
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_ADD: showAddDialog(index); return true;
                case MENU_NOTE: askNote(b); return true;
                case MENU_DUPLICATE: duplicate(index); return true;
                case MENU_DELETE: confirmDelete(index); return true;
                default: return false;
            }
        });
        pm.show();
    }

    /** The picture on a slot's bell: what happens when the slot starts. The start-of-day slot has a sun. */
    private void styleBell(Button bell, Routine.Block b) {
        boolean master = a.routine().alarmOn;
        boolean lit = master && b.alert != Routine.ALERT_NONE;
        bell.setText(bellIcon(b));
        bell.setBackground(Ui.pressable(a, lit ? Ui.TONAL : Ui.SOFT, 0, 20, 0x300E7C7B));
        bell.setAlpha(lit ? 1f : 0.55f);
        bell.setContentDescription(!master ? "Alarms are off. Tap to turn them on."
                : "When this slot starts: " + alertName(b) + ". Tap to change.");
    }

    private static String alertName(Routine.Block b) {
        String n = b.alert == Routine.ALERT_RING ? "notification with ring" : b.alert == Routine.ALERT_VIBRATE ? "notification with vibration"
                : b.alert == Routine.ALERT_SILENT ? "silent notification" : "no notification";
        return b.wake ? "start-of-day alarm, " + n : n;
    }

    /** What happens when this slot starts: ring, vibration only, a silent notification or nothing; and whether it is the start-of-day alarm. */
    private void pickAlert(final Button bell, final Routine.Block b, final int index) {
        final int[] modes = {Routine.ALERT_RING, Routine.ALERT_VIBRATE, Routine.ALERT_SILENT, Routine.ALERT_NONE};
        final String[] names = {"Notification with ring", "Notification with vibration", "Silent notification", "No notification"};
        PopupMenu pm = new PopupMenu(a, bell);
        Menu m = pm.getMenu();
        for (int i = 0; i < modes.length; i++) {
            m.add(0, 100 + i, i, (b.alert == modes[i] ? "✓  " : "      ") + Lang.tr(names[i]));
        }
        final Routine r = sched();
        boolean sounds = b.sounds();
        if (sounds && r.blocks.indexOf(b) >= 0) {
            m.add(0, 200, 10, Lang.tr(b.wake ? "This is the start-of-day alarm: tap to undo" : "Make this the start-of-day alarm"));
        }
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id >= 100 && id < 100 + modes.length) {
                b.setAlert(modes[id - 100]);
                main().normalizeWake();   // the start-of-day alarm stays the first alert of the day
                afterAlertChange(bell, b, names[id - 100]);
                return true;
            }
            if (id == 200) {
                int at = r.blocks.indexOf(b);
                if (b.wake) {
                    r.setWake(at, false);
                    afterAlertChange(bell, b, "Not the start-of-day alarm any more");
                } else if (r.setWake(at, true)) {
                    afterAlertChange(bell, b, "Start-of-day alarm");
                } else {
                    a.toastText("The start-of-day alarm has to be the first alarm of the day. Set the slots before it to No notification first.");
                }
                return true;
            }
            return false;
        });
        pm.show();
    }

    private void afterAlertChange(Button bell, Routine.Block b, String what) {
        styleBell(bell, b);
        a.persist();   // saves now and sets the alarm from what was saved (the alarm is read from the saved routine, not from this screen)
        a.toastText("“" + nameOf(b) + "”: " + what.toLowerCase(Lang.locale()));
        rebuildSoon();
    }

    private void stepMinutes(Routine.Block b, int delta) {
        int next = SlotOps.stepped(b.mins, delta);
        if (next == b.mins) return;
        applyLength(b, next);
        a.persistSoon();
        askFirstEdit();
        refreshStart();
        a.previewChanged();
    }

    private void askMinutes(final Routine.Block b) {
        final EditText in = new EditText(a);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setSingleLine(true);
        in.setText(String.valueOf(b.mins));
        in.setSelectAllOnFocus(true);
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(label("Minutes (1 to 1440)", 13, Ui.MUTED, false));
        box.addView(in, new LinearLayout.LayoutParams(MATCH, WRAP));
        new DBuilder(a)
                .setTitle("Length of “" + nameOf(b) + "”")
                .setView(box)
                .setPositiveButton("Set", (d, w) -> {
                    int v;
                    try {
                        v = Integer.parseInt(in.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        a.toastText("Write a number of minutes");
                        return;
                    }
                    if (v < 1 || v > 1440) {
                        a.toastText("Use 1 to 1440 minutes");
                        return;
                    }
                    applyLength(b, v);
                    changed();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Sets a slot's length; the minutes it gains or loses come from or go to the slots right below, so the day stays 24 hours. */
    private void applyLength(Routine.Block b, int want) {
        Routine r = sched();
        int i = r.blocks.indexOf(b);
        if (i < 0) {
            b.mins = want;
            return;
        }
        SlotOps.Resized x = SlotOps.resize(r, i, want);
        if (x.capped) a.toastText("The day only has room for " + x.applied + " minutes here.");
        else if (x.sleepWarn) a.toastText("Sleep is now outside 4 to 12 hours.");
    }

    private void askNote(final Routine.Block b) {
        final EditText in = new EditText(a);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        in.setMinLines(3);
        in.setMaxLines(8);
        in.setGravity(Gravity.TOP | Gravity.START);
        in.setText(b.note);
        in.setHint("e.g. Polity chapter 4, then 20 practice questions");
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Routine.MAX_NOTE_LEN)});
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(in, new LinearLayout.LayoutParams(MATCH, WRAP));
        box.addView(label("Shown on this slot and on its alarm.", 12, Ui.MUTED, false), wide(6));
        AlertDialog.Builder bld = new DBuilder(a)
                .setTitle("Note for “" + nameOf(b) + "”")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    b.note = in.getText().toString().trim();
                    changed();
                })
                .setNegativeButton("Cancel", null);
        if (!b.note.trim().isEmpty()) {
            bld.setNeutralButton("Remove", (d, w) -> {
                String snap = a.snapshot();
                b.note = "";
                changed();
                a.undoable("Note removed.", snap);
            });
        }
        bld.show();
    }

    private static int subIndex(List<Routine.Sub> opts, String sub) {
        for (int i = 0; i < opts.size(); i++) if (opts.get(i).name.equalsIgnoreCase(sub)) return i;
        return Math.max(0, opts.size() - 1);   // "Other" is last
    }

    /** Fills a slot's type spinner: the types of its kind ("Other" last), then "＋ New type…". */
    private void bindSub(final Spinner sp, final Routine.Block b) {
        final List<Routine.Sub> opts = main().subsOf(b.kind);
        final List<String> names = new ArrayList<>();
        for (Routine.Sub s : opts) names.add(s.name);
        names.add("＋ New type…");
        ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(subIndex(opts, b.sub));
        sp.setOnItemSelectedListener(new Picked() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= names.size()) return;
                if (position == names.size() - 1) {
                    sp.setSelection(subIndex(opts, b.sub));
                    askNewType(b.kind, b);
                    return;
                }
                String pick = opts.get(position).name;
                if (pick.equalsIgnoreCase(b.sub)) return;
                b.sub = pick;
                a.persistSoon();
                a.previewChanged();
            }
        });
    }

    // ------------------------------------------------------------------ move, delete, add, duplicate

    private void moveSlot(int from, int to) {
        String snap = a.snapshot();
        String name = from < sched().blocks.size() ? nameOf(sched().blocks.get(from)) : "";
        if (!SlotOps.move(sched(), from, to)) return;
        changed();
        a.undoable("Moved “" + name + "”.", snap);
    }

    private void confirmDelete(final int i) {
        final Routine r = sched();
        if (busy) return;
        if (!SlotOps.canDelete(r, i)) {
            a.toastText("Keep at least one slot");
            return;
        }
        final Routine.Block b = r.blocks.get(i);
        final int n = r.blocks.size();
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(label("Its " + Routine.formatDuration(b.mins) + " stay in your day. Where should they go?", 14, Ui.INK, false));
        final RadioGroup group = new RadioGroup(a);
        final int[] modeOf = new int[3];
        final int[] idOf = new int[3];
        int count = 0;
        int preselect = -1;
        for (int mode = SlotOps.TO_ABOVE; mode <= SlotOps.TO_ALL; mode++) {
            if (mode == SlotOps.TO_ABOVE && i == 0) continue;
            if (mode == SlotOps.TO_BELOW && i == n - 1) continue;
            String text;
            if (mode == SlotOps.TO_ALL) {
                text = "Share them out over the other slots";
            } else {
                Routine.Block to = r.blocks.get(SlotOps.receiver(r, i, mode));
                text = mode == SlotOps.TO_ABOVE ? "Add to the slot above: “" + nameOf(to) + "”"
                        : "Add to the slot below: “" + nameOf(to) + "”";
            }
            RadioButton rb = new RadioButton(a);
            rb.setId(View.generateViewId());
            rb.setText(text);
            rb.setMinHeight(dp(44));
            rb.setMinimumHeight(dp(44));
            group.addView(rb);
            modeOf[count] = mode;
            idOf[count] = rb.getId();
            if (mode == SlotOps.TO_BELOW || (preselect < 0 && mode == SlotOps.TO_ABOVE)) preselect = count;
            count++;
        }
        final int options = count;
        if (preselect >= 0) group.check(idOf[preselect]);
        box.addView(group, wide(8));
        new DBuilder(a)
                .setTitle("Delete “" + nameOf(b) + "”?")
                .setView(box)
                .setPositiveButton("Delete", (d, w) -> {
                    int mode = SlotOps.TO_ALL;
                    for (int k = 0; k < options; k++) if (idOf[k] == group.getCheckedRadioButtonId()) mode = modeOf[k];
                    int rcv = SlotOps.receiver(r, i, mode);
                    deleteAnimated(i, mode, rcv >= 0
                            ? "Deleted “" + nameOf(b) + "”. Its " + b.mins + " min went to “" + nameOf(r.blocks.get(rcv)) + "”."
                            : "Deleted “" + nameOf(b) + "”. Its " + b.mins + " min were shared out.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAnimated(final int i, final int mode, final String msg) {
        if (busy) return;
        busy = true;
        final String snap = a.snapshot();
        final Runnable apply = () -> {
            busy = false;
            if (i >= sched().blocks.size() || !SlotOps.delete(sched(), i, mode)) {
                refresh();
                return;
            }
            changed();
            a.undoable(msg, snap);
        };
        View row = rowsBox.getChildAt(i);
        if (row == null) {
            apply.run();
            return;
        }
        row.animate().translationX(-row.getWidth()).alpha(0f).setDuration(180).withEndAction(apply).start();
    }

    /** Splits a slot into two equal halves with the same name, type and note. */
    private void duplicate(int i) {
        Routine r = sched();
        if (i < 0 || i >= r.blocks.size()) return;
        Routine.Block b = r.blocks.get(i);
        if (b.mins < 2 * SlotOps.MIN_MINS) {
            a.toastText("This slot is too short to split");
            return;
        }
        String snap = a.snapshot();
        Routine.Block c = b.copy();
        c.src = -1;
        c.mins = b.mins / 2;
        if (SlotOps.insertAfter(r, i, c, SlotOps.FROM_THIS) == null) {
            a.toastText("This slot is too short to split");
            return;
        }
        changed();
        animateIn(i + 1);
        a.undoable("Split “" + nameOf(b) + "” into two slots.", snap);
    }

    private void animateIn(int index) {
        View v = rowsBox.getChildAt(index);
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(-dp(10));
        v.animate().alpha(1f).translationY(0).setDuration(240).start();
    }

    private int defaultAddAfter() {
        Routine r = sched();
        int n = r.blocks.size();
        if (n >= 2 && r.blocks.get(n - 1).kind == Routine.SLEEP) return n - 2;
        return Math.max(0, n - 1);
    }

    private void showAddDialog(final int after) {
        final Routine r = sched();
        final int n = r.blocks.size();
        if (busy || after < 0 || after >= n) return;
        final Routine.Block here = r.blocks.get(after);
        final Routine.Block next = after + 1 < n ? r.blocks.get(after + 1) : null;

        ScrollView sc = new ScrollView(a);
        LinearLayout box = column();
        box.setPadding(dp(22), dp(8), dp(22), dp(4));
        sc.addView(box, new ViewGroup.LayoutParams(MATCH, WRAP));
        box.addView(label("It goes right after “" + nameOf(here) + "”. Your day stays the same length, so its time is taken from other slots.",
                13, Ui.MUTED, false));
        final EditText name = new EditText(a);
        name.setHint("Name, e.g. Maths homework");
        name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        box.addView(name, wide(8));
        box.addView(label("Kind", 12, Ui.MUTED, true), wrap(8));
        final Spinner kind = kindSpinner();
        box.addView(kind, wide(0));
        box.addView(label("Type (optional)", 12, Ui.MUTED, true), wrap(8));
        final Spinner sub = new Spinner(a);
        final List<Routine.Sub> subOpts = new ArrayList<>();
        box.addView(sub, wide(0));
        final EditText newType = new EditText(a);
        newType.setHint("or write a new type");
        newType.setSingleLine(true);
        newType.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Routine.MAX_SUB_LEN)});
        box.addView(newType, wide(0));
        fillSubSpinner(sub, subOpts, Routine.TASK);
        kind.setOnItemSelectedListener(new Picked() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < Routine.SLEEP) fillSubSpinner(sub, subOpts, position);
            }
        });
        box.addView(label("Minutes", 12, Ui.MUTED, true), wrap(8));
        final EditText mins = new EditText(a);
        mins.setInputType(InputType.TYPE_CLASS_NUMBER);
        mins.setSingleLine(true);
        mins.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        box.addView(mins, Ui.lp(a, dp(110), WRAP, 0));
        mins.setText(String.valueOf(Math.max(1, Math.min(30, SlotOps.maxAddMinutes(r, after)))));
        final TextView info = label("", 12, Ui.MUTED, false);
        box.addView(info, wide(4));
        final TextView err = label("", 13, Ui.RED, true);
        box.addView(err, wide(4));
        final Runnable updateInfo = () -> {
            int max = SlotOps.maxAddMinutes(r, after);
            info.setText(Lang.tr("The minutes are taken from the slots right below this one, so the day stays 24 hours.") + " " + max + " min");
            err.setText("");
        };
        updateInfo.run();

        final AlertDialog dlg = new DBuilder(a)
                .setTitle("Add a slot")
                .setView(sc)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
        Button ok = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
        if (ok == null) return;
        ok.setOnClickListener(v -> {
            int k = kind.getSelectedItemPosition();
            if (k < 0 || k >= Routine.SLEEP) k = Routine.TASK;
            String typed = Routine.cleanSub(newType.getText().toString());
            int sp = sub.getSelectedItemPosition();
            String type = !typed.isEmpty() ? typed : (sp >= 0 && sp < subOpts.size() ? subOpts.get(sp).name : Routine.OTHER);
            String t = name.getText().toString().trim();
            if (t.isEmpty() && !Routine.isOther(type)) t = type;
            if (t.isEmpty()) {
                err.setText("Give the slot a name, or pick a type.");
                return;
            }
            int m;
            try {
                m = Integer.parseInt(mins.getText().toString().trim());
            } catch (NumberFormatException e) {
                err.setText("Write how many minutes it takes.");
                return;
            }
            int max = SlotOps.maxAddMinutes(r, after);
            if (m < 1) {
                err.setText("It needs at least 1 minute.");
                return;
            }
            if (m > max) {
                err.setText("Only " + max + " min fit in the day. Use fewer minutes.");
                return;
            }
            String snap = a.snapshot();
            if (!typed.isEmpty()) main().addSub(k, typed);
            Routine.Block nb = new Routine.Block(k, t, m);
            nb.sub = type;
            if (SlotOps.insertBelow(r, after, nb) == null) {
                err.setText("That did not fit. Try fewer minutes.");
                return;
            }
            dlg.dismiss();
            changed();
            animateIn(after + 1);
            scrollToRow(after + 1);
            a.undoable("Added “" + t + "”, " + m + " min, taken from the slots below.", snap);
        });
    }

    private void fillSubSpinner(Spinner sp, List<Routine.Sub> opts, int kind) {
        opts.clear();
        opts.addAll(main().subsOf(kind));
        List<String> names = new ArrayList<>();
        for (Routine.Sub s : opts) names.add(s.name);
        ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(Math.max(0, opts.size() - 1));   // "Other" unless a type is picked
    }

    private void scrollToRow(final int index) {
        scroll.postDelayed(() -> {
            View r = rowsBox.getChildAt(index);
            if (r == null) return;
            scroll.smoothScrollTo(0, Math.max(0, rowsBox.getTop() + r.getTop() - dp(140)));
        }, 150);
    }

    private void addRests() {
        String snap = a.snapshot();
        int[] res = SlotOps.addRests(sched());
        if (res[0] == 0 && res[1] == 0) {
            a.toastText("No back-to-back tasks found");
            return;
        }
        if (res[0] == 0) {
            a.toastText("No task has time to spare for a rest");
            return;
        }
        changed();
        a.undoable((res[0] == 1 ? "1 rest added, taken from the tasks next to them." : res[0] + " rests added, taken from the tasks next to them.")
                + (res[1] == 0 ? "" : res[1] == 1 ? " 1 gap had no room." : " " + res[1] + " gaps had no room."), snap);
    }

    private void confirmReset() {
        final Routine r = sched();
        new DBuilder(a)
                .setTitle("Reset “" + r.name + "”?")
                .setMessage("Its slots are replaced with the sample day (wake-up 5:30 AM). You can undo this.")
                .setPositiveButton("Reset", (d, w) -> {
                    String snap = a.snapshot();
                    r.copyScheduleFrom(Routine.sample());
                    r.mode = Routine.MODE_WAKE;
                    r.anchor = 330;
                    main().syncSubs();
                    changed();
                    a.undoable("“" + r.name + "” reset to the sample day.", snap);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pasteRoutine() {
        String text = null;
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData d = cm.getPrimaryClip();
                if (d != null && d.getItemCount() > 0) {
                    CharSequence cs = d.getItemAt(0).coerceToText(a);
                    if (cs != null) text = cs.toString();
                }
            }
        } catch (RuntimeException e) {
            text = null;
        }
        Routine in = Routine.parse(text);
        if (in == null) {
            a.toastText("Copy your routine in the web app first");
            return;
        }
        String snap = a.snapshot();
        sched().copyScheduleFrom(in);
        main().syncSubs();
        changed();
        a.undoable("Routine imported into “" + sched().name + "”.", snap);
    }

    // ------------------------------------------------------------------ times

    private void updateTimes(Routine.Schedule sc) {
        boolean h24 = main().fmt24;
        for (int i = 0; i < rowLabels.size() && i < sc.slots.size(); i++) {
            Routine.Slot s = sc.slots.get(i);
            TextView[] t = rowLabels.get(i);
            t[0].setText(Routine.formatTime(s.start, h24));
            t[1].setText("to " + Routine.formatTime(s.end, h24));
            t[2].setText(Routine.formatDuration(s.block.mins));
        }
        for (Runnable r : rowRefresh) r.run();
    }

    /** The short line of a slot card: its type and length, and what happens when it starts. */
    private String summaryOf(Routine.Block b) {
        String type = b.sub.isEmpty() || Routine.isOther(b.sub)
                ? Lang.tr("Other") + " " + Lang.tr(Routine.KIND_NAMES[b.kind]).toLowerCase(Lang.locale()) : b.sub;
        return type + "  ·  " + Routine.formatDuration(b.mins) + (a.routine().alarmOn ? "   " + bellIcon(b) : "");
    }

    private static String bellIcon(Routine.Block b) {
        return b.wake ? "🌅" : b.alert == Routine.ALERT_RING ? "🔔" : b.alert == Routine.ALERT_VIBRATE ? "📳"
                : b.alert == Routine.ALERT_SILENT ? "🔕" : "🚫";
    }
}
