package app.dinacharya.wallpaper;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Library mode in the Library and Quiet mode card (Focus and locks): switch it on or off by hand, or let it come on by itself at fixed hours
 * on chosen days. Every change is saved at once.
 */
final class LibrarySettings {
    private final android.app.Activity a;
    private boolean binding;
    private Expander.Part part;
    private TextView status;
    private Switch handSwitch;
    private Switch autoSwitch;
    private LinearLayout autoBox;
    private Button fromButton;
    private Button toButton;
    private LinearLayout dayRow;
    private TextView dayNote;

    LibrarySettings(android.app.Activity host) {
        this.a = host;
    }

    /** Fills {@code part}, the "Library mode" part of the Library and Quiet mode card. */
    void build(Expander.Part part) {
        this.part = part;
        LinearLayout body = part.body;
        body.addView(Ui.label(a, "For the library, a class or an exam. While library mode is on, every alarm of the app only vibrates, with no "
                + "sound: the start-of-day alarm, the slot alarms, the bedtime question and the break timers. The questions still appear, "
                + "and groups of apps set to lock in library mode are locked.", 13, Ui.MUTED, false), Ui.wide(a, 4));
        status = Ui.label(a, "", 15, Ui.INK, true);
        body.addView(status, Ui.wide(a, 10));
        handSwitch = Ui.toggle(a, "Library mode", false, (sw, on) -> {
            if (binding) return;
            LibraryDialog.set(a, on, this::refresh);
        });
        body.addView(handSwitch, Ui.wide(a, 8));

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
            TimeSheet.show(a, 0, "Start time", lib.startMin, clock24(), (h, m) -> {
                lib.startMin = h * 60 + m;
                save(lib);
            });
        });
        toButton = Ui.button(a, "", false, v -> {
            final LibraryMode lib = Store.library(a);
            TimeSheet.show(a, 0, "End time", lib.endMin, clock24(), (h, m) -> {
                lib.endMin = h * 60 + m;
                save(lib);
            });
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dp(a, 4);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = Ui.dp(a, 4);
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
    }

    private boolean clock24() {
        try {
            return Store.load(a).fmt24;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private TextView preset(String text, final int mask) {
        TextView t = Ui.label(a, text, 13, Ui.TEAL_DARK, true);
        t.setPadding(0, Ui.dp(a, 6), Ui.dp(a, 16), Ui.dp(a, 6));
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
        Quiet.refresh(a);   // rules that hold in library mode follow it
        refresh();
    }

    void refresh() {
        if (status == null) return;
        LibraryMode lib = Store.library(a);
        long now = System.currentTimeMillis();
        boolean h24 = clock24();
        boolean on = lib.on(now, Store.zone());
        binding = true;
        autoSwitch.setChecked(lib.auto);
        binding = false;
        String line = lib.status(now, Store.zone(), h24);
        status.setText(line);
        part.setSummary(on ? "On" : lib.auto && lib.nextStartMs(now, Store.zone()) > 0 ? "Off, by the clock" : "Off");
        binding = true;
        handSwitch.setChecked(on);
        binding = false;
        autoBox.setVisibility(lib.auto ? ViewGroup.VISIBLE : ViewGroup.GONE);
        fromButton.setText(Lang.tr("From " + Routine.formatTime(lib.startMin, h24)));
        toButton.setText(Lang.tr("Until " + Routine.formatTime(lib.endMin, h24)));
        dayRow.removeAllViews();
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            boolean set = (lib.days & bit) != 0;
            TextView t = Ui.label(a, LockRules.dayName(i).substring(0, 1), 14, set ? Ui.ON_FILL : Ui.INK, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.box(a, set ? Ui.TEAL : Ui.SOFT, set ? 0 : Ui.LINE, 20));
            t.setContentDescription(LockRules.dayName(i));
            t.setOnClickListener(v -> {
                LibraryMode l = Store.library(a);
                l.days ^= bit;
                save(l);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(a, 40), 1f);
            p.rightMargin = Ui.dp(a, 4);
            dayRow.addView(t, p);
        }
        if (lib.days == 0) dayNote.setText("Pick at least one day, or it never comes on.");
        else if (lib.endMin <= lib.startMin) dayNote.setText("It runs from " + Routine.formatTime(lib.startMin, h24) + " into the next morning, until "
                + Routine.formatTime(lib.endMin, h24) + ".");
        else dayNote.setText(LockRules.daysText(lib.days) + ", " + Routine.formatTime(lib.startMin, h24) + " to " + Routine.formatTime(lib.endMin, h24) + ".");
    }
}
