package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Makes or changes a quiet rule: what the phone does (ring, vibrate, silent, Do Not Disturb) and when, set the way a lock is set: at hours
 * on chosen days, during chosen activity types, or only when both hold, and optionally also in library mode.
 */
final class QuietDialog {
    private QuietDialog() {
    }

    /** The edits so far; nothing is kept until Save. */
    private static final class Draft {
        String name = "";
        int sound = QuietRules.VIBRATE;
        int days = LockRules.WEEKDAYS;
        int start = 9 * 60;
        int end = 17 * 60;
        final java.util.List<String> during = new java.util.ArrayList<>();
        boolean library;
        int by = LockMode.TIME;
    }

    static void show(final Activity a, final int theme, final QuietRules all, final QuietRules.Rule existing, final boolean h24, final Runnable changed) {
        final Draft d = new Draft();
        if (existing != null) {
            d.name = existing.name;
            d.sound = existing.sound;
            d.days = existing.days;
            d.start = existing.startMin;
            d.end = existing.endMin;
            d.during.addAll(existing.during);
            d.library = existing.library;
            d.by = LockMode.of(existing.days, existing.during.size(), existing.both);
            // a rule by activity has no days; these are the ones it would use if the person switches to a rule by time
            if (d.by == LockMode.ACTIVITY) d.days = LockRules.WEEKDAYS;
            // a rule for library mode only: it starts as a rule by time, with no days yet
            if (existing.days == 0 && existing.during.isEmpty()) d.days = LockRules.WEEKDAYS;
        }
        final AlertDialog.Builder b = new DBuilder(a, theme);
        final Context c = b.getContext();
        ScrollView sv = new ScrollView(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 22);
        box.setPadding(pad, Ui.dp(c, 8), pad, Ui.dp(c, 4));
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText name = new EditText(c);
        name.setHint("Name, for example Study time");
        name.setSingleLine(true);
        name.setText(d.name);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name, Ui.wide(c, 0));

        // ---- what the phone does
        box.addView(Ui.label(c, "The phone", 14, Ui.MUTED, true), Ui.wide(c, 14));
        final Segmented sound = new Segmented(c, new String[]{Lang.tr("Ring"), Lang.tr("Vibrate"), Lang.tr("Silent"), Lang.tr("DND")});
        sound.pick(d.sound, false);
        box.addView(sound, Ui.wide(c, 6));
        final TextView soundNote = Ui.label(c, "", 12, Ui.MUTED, false);
        soundNote.setLineSpacing(Ui.dp(c, 2), 1f);
        box.addView(soundNote, Ui.wide(c, 6));

        final Runnable[] redraw = new Runnable[1];
        sound.setListener(i -> {
            d.sound = i;
            redraw[0].run();
        });

        // ---- when: by time, by activity, or both
        box.addView(Ui.label(c, "When", 14, Ui.MUTED, true), Ui.wide(c, 16));
        final int[] ways = {LockMode.TIME, LockMode.ACTIVITY, LockMode.BOTH};
        final TextView[] chips = new TextView[ways.length];
        LinearLayout chipRow = new LinearLayout(c);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < ways.length; i++) {
            final int way = ways[i];
            TextView t = Ui.label(c, LockMode.name(way), 13, Ui.INK, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10));
            t.setOnClickListener(v -> {
                d.by = way;
                redraw[0].run();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.rightMargin = Ui.dp(c, 4);
            chipRow.addView(t, cp);
            chips[i] = t;
        }
        box.addView(chipRow, Ui.wide(c, 6));
        final TextView howNote = Ui.label(c, "", 12, Ui.MUTED, false);
        howNote.setLineSpacing(Ui.dp(c, 2), 1f);
        box.addView(howNote, Ui.wide(c, 8));

        // the hours and days
        final LinearLayout timePanel = new LinearLayout(c);
        timePanel.setOrientation(LinearLayout.VERTICAL);
        timePanel.addView(Ui.label(c, "Between", 14, Ui.MUTED, true), Ui.wide(c, 8));
        LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        final Button from = Ui.button(c, "", false, null);
        final Button to = Ui.button(c, "", false, null);
        from.setOnClickListener(v -> new TimePickerDialog(c, theme, (tp, h, m) -> {
            d.start = h * 60 + m;
            redraw[0].run();
        }, d.start / 60, d.start % 60, h24).show());
        to.setOnClickListener(v -> new TimePickerDialog(c, theme, (tp, h, m) -> {
            d.end = h * 60 + m;
            redraw[0].run();
        }, d.end / 60, d.end % 60, h24).show());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Ui.dp(c, 4);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = Ui.dp(c, 4);
        times.addView(from, l1);
        times.addView(to, l2);
        timePanel.addView(times, Ui.wide(c, 4));
        timePanel.addView(Ui.label(c, "On these days", 14, Ui.MUTED, true), Ui.wide(c, 12));
        final LinearLayout dayRow = new LinearLayout(c);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        timePanel.addView(dayRow, Ui.wide(c, 4));
        LinearLayout presets = new LinearLayout(c);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.addView(GroupDialog.preset(c, "Every day", () -> {
            d.days = LockRules.EVERY_DAY;
            redraw[0].run();
        }));
        presets.addView(GroupDialog.preset(c, "Weekdays", () -> {
            d.days = LockRules.WEEKDAYS;
            redraw[0].run();
        }));
        presets.addView(GroupDialog.preset(c, "Weekends", () -> {
            d.days = LockRules.WEEKENDS;
            redraw[0].run();
        }));
        timePanel.addView(presets, Ui.wide(c, 4));
        box.addView(timePanel, Ui.wide(c, 0));

        // the activity types
        final LinearLayout typePanel = new LinearLayout(c);
        typePanel.setOrientation(LinearLayout.VERTICAL);
        typePanel.addView(Ui.label(c, "Activity types", 14, Ui.MUTED, true), Ui.wide(c, 8));
        final TextView duringText = Ui.label(c, "", 14, Ui.INK, false);
        typePanel.addView(duringText, Ui.wide(c, 4));
        typePanel.addView(Ui.button(c, "Choose activity types", false, v -> GroupDialog.chooseTypes(a, theme, "Quiet during", d.during, () -> redraw[0].run())),
                Ui.wide(c, 6));
        TextView duringNote = Ui.label(c, "A break, a slot you took off and the time between slots do not count.", 12, Ui.MUTED, false);
        duringNote.setLineSpacing(Ui.dp(c, 2), 1f);
        typePanel.addView(duringNote, Ui.wide(c, 4));
        box.addView(typePanel, Ui.wide(c, 0));

        box.addView(Ui.toggle(c, "Also in library mode", d.library, (sw, on) -> d.library = on), Ui.wide(c, 14));

        redraw[0] = () -> {
            for (int i = 0; i < ways.length; i++) {
                boolean on = ways[i] == d.by;
                chips[i].setTextColor(on ? Ui.ON_FILL : Ui.INK);
                chips[i].setBackground(Ui.box(c, on ? Ui.TEAL : Ui.SOFT, on ? 0 : Ui.LINE, 20));
            }
            soundNote.setText(soundAbout(d.sound));
            howNote.setText(about(d.by));
            timePanel.setVisibility(d.by == LockMode.ACTIVITY ? View.GONE : View.VISIBLE);
            typePanel.setVisibility(d.by == LockMode.TIME ? View.GONE : View.VISIBLE);
            from.setText("From " + Routine.formatTime(d.start, h24));
            to.setText("to " + Routine.formatTime(d.end, h24));
            GroupDialog.drawDays(c, dayRow, d.days, bit -> {
                d.days ^= bit;
                redraw[0].run();
            });
            duringText.setText(d.during.isEmpty() ? "No activity types chosen yet." : LockRules.duringText(d.during) + ".");
        };
        redraw[0].run();

        b.setTitle(existing == null ? "New quiet rule" : "Quiet rule").setView(sv)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null);
        if (existing != null) b.setNeutralButton("Delete", null);
        final AlertDialog dlg = b.create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int missing = LockMode.missing(d.by, d.days, d.during.size());
            if (missing == LockMode.NEED_TYPES) {
                Lang.toast(c, "Choose at least one activity type", Toast.LENGTH_SHORT).show();
                return;
            }
            if (d.by != LockMode.ACTIVITY && d.days == 0 && !d.library) {
                Lang.toast(c, "Pick at least one day", Toast.LENGTH_SHORT).show();
                return;
            }
            QuietRules.Rule r = existing != null ? existing : new QuietRules.Rule(name.getText().toString());
            if (existing != null) r.name = QuietRules.clean(name.getText().toString());
            r.sound = d.sound;
            r.days = LockMode.daysToSave(d.by, d.days);
            r.startMin = d.start;
            r.endMin = d.end;
            r.during.clear();
            if (LockMode.keepsTypes(d.by)) r.during.addAll(d.during);
            r.both = LockMode.bothToSave(d.by);
            r.library = d.library;
            if (existing == null) {
                if (all.rules.size() >= QuietRules.MAX_RULES) {
                    Lang.toast(c, "That is the most rules you can have", Toast.LENGTH_SHORT).show();
                    return;
                }
                all.rules.add(r);
            }
            save(a, all);
            dlg.dismiss();
            if (changed != null) changed.run();
            if ((r.sound == QuietRules.SILENT || r.sound == QuietRules.DND) && !Quiet.hasAccess(a)) askAccess(a, theme);
        });
        if (existing != null) {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                all.rules.remove(existing);
                save(a, all);
                dlg.dismiss();
                if (changed != null) changed.run();
            });
        }
    }

    private static void save(Context c, QuietRules q) {
        Store.saveQuiet(c, q);
        SlotNow.changed();
        Quiet.refresh(c);
    }

    /** Says why Do Not Disturb access is needed and opens the phone's page for it. */
    static void askAccess(final Activity a, int theme) {
        new DBuilder(a, theme)
                .setTitle("Do Not Disturb access")
                .setMessage("To go fully silent, or to use Do Not Disturb, Android needs your permission to change it. "
                        + "Dinacharya Launcher only uses it for this and never reads your notifications. Until you allow it, the phone only vibrates.")
                .setPositiveButton("Open settings", (d, w) -> openAccess(a))
                .setNegativeButton("Not now", null)
                .show();
    }

    static void openAccess(Activity a) {
        try {
            a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (RuntimeException e) {
            Lang.toast(a, "Open Do Not Disturb access in your phone's settings", Toast.LENGTH_LONG).show();
        }
    }

    static String soundAbout(int sound) {
        switch (sound) {
            case QuietRules.RING:
                return "The ringer is turned up to ring while the rule holds.";
            case QuietRules.SILENT:
                return "The ringer is turned off: no sound and no vibration.";
            case QuietRules.DND:
                return "Do Not Disturb (priority only): notifications of other apps are held back, and calls from people your phone allows still ring.";
            default:
                return "The ringer only vibrates while the rule holds.";
        }
    }

    static String about(int by) {
        switch (by) {
            case LockMode.ACTIVITY:
                return "Holds whenever a slot of the types you pick is going on, at any hour.";
            case LockMode.BOTH:
                return "Holds only when a slot of the types you pick is going on inside the hours you set.";
            default:
                return "Holds at the hours you set, whatever you are doing.";
        }
    }
}
