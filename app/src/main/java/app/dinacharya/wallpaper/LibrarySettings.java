package app.dinacharya.wallpaper;

import android.app.TimePickerDialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Library mode in the Alarms settings: switch it on or off by hand, let it come on by itself at fixed hours on chosen days, and below it
 * how quiet the phone gets (ringer and notifications) while library mode is on and during chosen activity types. Every change is saved at once.
 */
final class LibrarySettings {
    private final MainActivity a;
    private boolean binding;
    private Expander.Part part;
    private TextView status;
    private Button handButton;
    private Switch autoSwitch;
    private LinearLayout autoBox;
    private Button fromButton;
    private Button toButton;
    private LinearLayout dayRow;
    private TextView dayNote;
    private TextView libraryRule;
    private LinearLayout typeRows;
    private TextView accessNote;

    LibrarySettings(MainActivity host) {
        this.a = host;
    }

    void build(LinearLayout root) {
        part = Expander.more(a, root, "Quiet and library mode", "alarm_library", false);
        LinearLayout body = part.body;
        body.addView(Ui.label(a, "For the library, a class or an exam. While library mode is on, every alarm of the app only vibrates, with no "
                + "sound: the start-of-day alarm, the slot alarms, the bedtime question and the break timers. The questions still appear, "
                + "and groups of apps set to lock in library mode are locked.", 13, Ui.MUTED, false), Ui.wide(a, 4));
        status = Ui.label(a, "", 15, Ui.INK, true);
        body.addView(status, Ui.wide(a, 10));
        handButton = Ui.button(a, "Turn on", true, v -> {
            LibraryMode lib = Store.library(a);
            long now = System.currentTimeMillis();
            if (lib.untilMs > now) LibraryDialog.turnOff(a, this::refresh);
            else LibraryDialog.turnOn(a, this::refresh);
        });
        body.addView(handButton, Ui.wide(a, 8));

        autoSwitch = Ui.toggle(a, "Turn it on by itself at fixed hours", false, (b, on) -> {
            if (binding) return;
            LibraryMode lib = Store.library(a);
            lib.auto = on;
            save(lib);
        });
        body.addView(autoSwitch, Ui.wide(a, 12));

        autoBox = new LinearLayout(a);
        autoBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(autoBox, Ui.wide(a, 0));
        LinearLayout times = new LinearLayout(a);
        times.setOrientation(LinearLayout.HORIZONTAL);
        fromButton = Ui.button(a, "", false, v -> {
            final LibraryMode lib = Store.library(a);
            new TimePickerDialog(a, (tp, h, m) -> {
                lib.startMin = h * 60 + m;
                save(lib);
            }, lib.startMin / 60, lib.startMin % 60, a.routine().fmt24).show();
        });
        toButton = Ui.button(a, "", false, v -> {
            final LibraryMode lib = Store.library(a);
            new TimePickerDialog(a, (tp, h, m) -> {
                lib.endMin = h * 60 + m;
                save(lib);
            }, lib.endMin / 60, lib.endMin % 60, a.routine().fmt24).show();
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = a.dp(4);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = a.dp(4);
        times.addView(fromButton, l1);
        times.addView(toButton, l2);
        autoBox.addView(times, Ui.wide(a, 4));
        autoBox.addView(Ui.label(a, "On these days", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        dayRow = new LinearLayout(a);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        autoBox.addView(dayRow, Ui.wide(a, 4));
        LinearLayout presets = new LinearLayout(a);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.addView(preset("Every day", LockRules.EVERY_DAY));
        presets.addView(preset("Weekdays", LockRules.WEEKDAYS));
        presets.addView(preset("Weekends", LockRules.WEEKENDS));
        autoBox.addView(presets, Ui.wide(a, 4));
        dayNote = Ui.label(a, "", 12, Ui.MUTED, false);
        autoBox.addView(dayNote, Ui.wide(a, 4));

        body.addView(Ui.label(a, "Quiet phone", 15, Ui.INK, true), Ui.wide(a, 18));
        body.addView(Ui.label(a, "Choose how quiet the phone gets: the ringer, and the notifications of other apps (calls from people you "
                + "allow still come through). When it ends, everything goes back to how it was, unless you changed it yourself.", 12, Ui.MUTED, false), Ui.wide(a, 2));
        libraryRule = ruleRow(body, "While library mode is on", v -> editRule("While library mode is on", Store.quiet(a).library, null, r -> {
            QuietRules q = Store.quiet(a);
            q.library = r;
            saveQuiet(q, r);
        }));
        body.addView(Ui.label(a, "During activities", 13, Ui.MUTED, true), Ui.wrap(a, 14));
        typeRows = new LinearLayout(a);
        typeRows.setOrientation(LinearLayout.VERTICAL);
        body.addView(typeRows, Ui.wide(a, 2));
        body.addView(Ui.button(a, "Add an activity type", false, v -> addType()), Ui.wide(a, 6));
        accessNote = Ui.label(a, "Silent and holding back notifications need Do Not Disturb access. Tap to allow it. Until then the phone only vibrates.",
                12, Ui.TEAL, true);
        accessNote.setOnClickListener(v -> openAccess());
        body.addView(accessNote, Ui.wide(a, 8));
    }

    /** One tappable line: the label on the left, the rule in words on the right. Returns the right-hand text. */
    private TextView ruleRow(LinearLayout into, String label, android.view.View.OnClickListener click) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(a.dp(48));
        row.setPadding(a.dp(12), a.dp(6), a.dp(12), a.dp(6));
        row.setBackground(Ui.box(a, Ui.SOFT, Ui.LINE, 14));
        TextView name = Ui.label(a, label, 14, Ui.INK, false);
        TextView value = Ui.label(a, "", 14, Ui.TEAL, true);
        value.setGravity(Gravity.END);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(click);
        into.addView(row, Ui.wide(a, 8));
        return value;
    }

    private interface Picked {
        void on(QuietRules.Rule r);
    }

    /** The ringer (as it is / vibrate / silent) and whether notifications are held back; "Remove" when {@code remove} is given. */
    private void editRule(String title, QuietRules.Rule now, final Runnable remove, final Picked done) {
        final Segmented sound = new Segmented(a, new String[]{Lang.tr("As it is"), Lang.tr("Vibrate"), Lang.tr("Silent")});
        sound.pick(now.sound, false);
        final boolean[] hold = {now.hold};
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        box.addView(Ui.label(a, "Phone ringer", 13, Ui.MUTED, true), Ui.wrap(a, 0));
        box.addView(sound, Ui.wide(a, 6));
        box.addView(Ui.toggle(a, "Hold back notifications", hold[0], (b, on) -> hold[0] = on), Ui.wide(a, 14));
        android.app.AlertDialog.Builder b = new DBuilder(a)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    QuietRules.Rule r = new QuietRules.Rule(Math.max(0, sound.selected()), hold[0]);
                    done.on(r);
                })
                .setNegativeButton("Cancel", null);
        if (remove != null) b.setNeutralButton("Remove", (d, w) -> {
            remove.run();
            refresh();
        });
        b.show();
    }

    /** Saves the rules; the first time one needs Do Not Disturb access that is missing, says why and offers the phone's settings. */
    private void saveQuiet(QuietRules q, QuietRules.Rule changed) {
        Store.saveQuiet(a, q);
        SlotNow.changed();
        Quiet.refresh(a);
        refresh();
        if (changed.needsAccess() && !Quiet.hasAccess(a)) {
            new DBuilder(a)
                    .setTitle("Do Not Disturb access")
                    .setMessage("To go fully silent, or to hold back notifications, Android needs your permission to change Do Not Disturb. "
                            + "Dinacharya Launcher only uses it for this and never reads your notifications. Until you allow it, the phone only vibrates.")
                    .setPositiveButton("Open settings", (d, w) -> openAccess())
                    .setNegativeButton("Not now", null)
                    .show();
        }
    }

    private void openAccess() {
        try {
            a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (RuntimeException e) {
            a.toastText("Open Do Not Disturb access in your phone's settings");
        }
    }

    /** Pick an activity type that has no rule yet, then its rule. */
    private void addType() {
        final QuietRules q = Store.quiet(a);
        if (q.types.size() >= QuietRules.MAX_TYPES) {
            a.toastText("That is the most types you can add");
            return;
        }
        Routine r;
        try {
            r = Store.load(a);
        } catch (RuntimeException e) {
            r = Routine.sample();
        }
        final java.util.List<String> keys = new java.util.ArrayList<>();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) {
            if (!q.types.containsKey(LauncherConfig.anyKey(k))) {
                keys.add(LauncherConfig.anyKey(k));
                labels.add(Lang.tr(LockRules.typeName(LauncherConfig.anyKey(k))));
            }
            for (Routine.Sub s : r.subsOf(k)) {
                String key = LauncherConfig.typeKey(k, s.name);
                if (q.types.containsKey(key)) continue;
                keys.add(key);
                labels.add(Routine.isOther(s.name) ? Lang.tr(LockRules.typeName(key)) : s.name);
            }
        }
        if (keys.isEmpty()) {
            a.toastText("Every type has a rule already");
            return;
        }
        new DBuilder(a)
                .setTitle("Quiet during")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    final String key = keys.get(which);
                    editRule(labels.get(which), new QuietRules.Rule(QuietRules.VIBRATE, false), null, rule -> setType(key, rule));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setType(String key, QuietRules.Rule rule) {
        QuietRules q = Store.quiet(a);
        q.setType(key, rule);
        saveQuiet(q, rule);
    }

    private void drawTypes(QuietRules q) {
        typeRows.removeAllViews();
        if (q.types.isEmpty()) {
            typeRows.addView(Ui.label(a, "No activity types yet. For example, vibrate while you study.", 12, Ui.MUTED, false), Ui.wide(a, 2));
            return;
        }
        for (final java.util.Map.Entry<String, QuietRules.Rule> e : q.types.entrySet()) {
            final String key = e.getKey();
            final String name = Lang.tr(LockRules.typeName(key));
            ruleRow(typeRows, name, v -> editRule(name, e.getValue(), () -> setType(key, QuietRules.Rule.NONE), r -> setType(key, r)))
                    .setText(Lang.tr(e.getValue().text()));
        }
    }

    private TextView preset(String text, final int mask) {
        TextView t = Ui.label(a, text, 13, Ui.TEAL, true);
        t.setPadding(0, a.dp(6), a.dp(16), a.dp(6));
        t.setOnClickListener(v -> {
            LibraryMode lib = Store.library(a);
            lib.days = mask;
            save(lib);
        });
        return t;
    }

    private void save(LibraryMode lib) {
        Store.saveLibrary(a, lib);
        SlotNow.changed();
        Quiet.refresh(a);
        a.previewChanged();
        refresh();
    }

    void refresh() {
        if (status == null) return;
        LibraryMode lib = Store.library(a);
        long now = System.currentTimeMillis();
        boolean h24 = a.routine().fmt24;
        boolean on = lib.on(now, Store.zone());
        binding = true;
        autoSwitch.setChecked(lib.auto);
        binding = false;
        QuietRules q = Store.quiet(a);
        libraryRule.setText(Lang.tr(q.library.text()));
        drawTypes(q);
        boolean needs = q.library.needsAccess();
        for (QuietRules.Rule r : q.types.values()) needs |= r.needsAccess();
        accessNote.setVisibility(needs && !Quiet.hasAccess(a) ? ViewGroup.VISIBLE : ViewGroup.GONE);
        String line = lib.status(now, Store.zone(), h24);
        status.setText(line);
        part.setSummary(on ? "On" : lib.auto && lib.nextStartMs(now, Store.zone()) > 0 ? "Off, by the clock" : "Off");
        boolean byHand = lib.untilMs > now;
        handButton.setText(Lang.tr(byHand ? "Turn off" : "Turn on"));
        handButton.setVisibility(on && !byHand ? ViewGroup.GONE : ViewGroup.VISIBLE);
        autoBox.setVisibility(lib.auto ? ViewGroup.VISIBLE : ViewGroup.GONE);
        fromButton.setText(Lang.tr("From " + Routine.formatTime(lib.startMin, h24)));
        toButton.setText(Lang.tr("Until " + Routine.formatTime(lib.endMin, h24)));
        dayRow.removeAllViews();
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            boolean set = (lib.days & bit) != 0;
            TextView t = Ui.label(a, LockRules.dayName(i).substring(0, 1), 14, set ? 0xFFFFFFFF : Ui.INK, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.box(a, set ? Ui.TEAL : Ui.SOFT, set ? 0 : Ui.LINE, 20));
            t.setContentDescription(LockRules.dayName(i));
            t.setOnClickListener(v -> {
                LibraryMode l = Store.library(a);
                l.days ^= bit;
                save(l);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, a.dp(40), 1f);
            p.rightMargin = a.dp(4);
            dayRow.addView(t, p);
        }
        if (lib.days == 0) dayNote.setText("Pick at least one day, or it never comes on.");
        else if (lib.endMin <= lib.startMin) dayNote.setText("It runs from " + Routine.formatTime(lib.startMin, h24) + " into the next morning, until "
                + Routine.formatTime(lib.endMin, h24) + ".");
        else dayNote.setText(LockRules.daysText(lib.days) + ", " + Routine.formatTime(lib.startMin, h24) + " to " + Routine.formatTime(lib.endMin, h24) + ".");
    }
}
