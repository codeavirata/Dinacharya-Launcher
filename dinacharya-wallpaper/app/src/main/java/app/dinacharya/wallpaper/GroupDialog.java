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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Makes or changes a group of apps that are locked for a time: which apps, when, and how firmly. */
final class GroupDialog {
    private static final int[] LOCK_MIN = {30, 60, 120, 240, 480};
    private static final String[] LOCK_TEXT = {"30 min", "1 h", "2 h", "4 h", "8 h"};

    /** The edits so far; nothing is kept until Save. */
    private static final class Draft {
        String name = "";
        final Set<String> apps = new LinkedHashSet<>();
        int start = 21 * 60;
        int end = 6 * 60;
        int days = LockRules.EVERY_DAY;
        boolean strict;
        long untilMs;
        final List<String> during = new ArrayList<>();
        boolean hide;
    }

    private GroupDialog() {
    }

    static void show(final Activity a, final int theme, final LauncherConfig cfg, final LauncherConfig.Group existing,
                     final List<AppEntry> apps, final int openNow, final boolean h24, final Runnable changed) {
        final long nowMs = System.currentTimeMillis();
        final LockRules.Slot slotNow = SlotNow.read(a).toLock();
        if (existing != null && existing.strict && DayPass.has(openNow, DayPass.LOCK_STRICT)
                && LockRules.active(cfg, existing, openNow) && LockRules.groupLocked(existing, nowMs, Store.zone(), slotNow)) {
            long until = LockRules.lockedUntil(existing, nowMs, Store.zone(), slotNow);
            new DBuilder(a, theme).setTitle(existing.name)
                    .setMessage("This is a strict lock and it is holding now, until " + clock(until, h24) + ". "
                            + "A strict lock cannot be changed or removed while it holds.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        final Draft d = new Draft();
        if (existing != null) {
            d.name = existing.name;
            d.apps.addAll(existing.apps);
            d.start = existing.startMin;
            d.end = existing.endMin;
            d.days = existing.days;
            d.strict = existing.strict;
            d.untilMs = existing.untilMs;
            d.during.addAll(existing.during);
            d.hide = existing.hide;
        }

        final AlertDialog.Builder b = new DBuilder(a, theme);
        final Context c = b.getContext();
        // What is open can change while this dialog is up (a video pass), so it is read from here. Each part has its own item:
        // names go with more locks, the number of apps with LOCK_APPS and strict with LOCK_STRICT.
        final int[] open = {openNow};
        final Runnable[] onUnlocked = new Runnable[1];
        ScrollView sv = new ScrollView(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 22);
        box.setPadding(pad, Ui.dp(c, 8), pad, Ui.dp(c, 4));
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText name = new EditText(c);
        name.setHint("Name, for example Social");
        name.setSingleLine(true);
        name.setText(d.name);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setVisibility(DayPass.has(open[0], DayPass.LOCK_MORE) ? View.VISIBLE : View.GONE);
        box.addView(name, Ui.wide(c, 0));

        // ---- apps
        final TextView appsLabel = Ui.label(c, appsLabelText(open[0]), 14, Ui.MUTED, true);
        box.addView(appsLabel, Ui.wide(c, 14));
        final TextView appsText = Ui.label(c, "", 14, Ui.INK, false);
        box.addView(appsText, Ui.wide(c, 4));
        Button choose = Ui.button(c, "Choose apps", false, v -> chooseApps(a, theme, apps, d, open, () -> onUnlocked[0].run(), () -> setApps(appsText, apps, d)));
        box.addView(choose, Ui.wide(c, 6));
        setApps(appsText, apps, d);

        // ---- window
        box.addView(Ui.label(c, "Locked between", 14, Ui.MUTED, true), Ui.wide(c, 14));
        final TextView summary = Ui.label(c, "", 13, Ui.MUTED, false);
        LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        final Button from = Ui.button(c, "", false, null);
        final Button to = Ui.button(c, "", false, null);
        final Runnable[] redraw = new Runnable[1];
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
        box.addView(times, Ui.wide(c, 4));

        box.addView(Ui.label(c, "On these days", 14, Ui.MUTED, true), Ui.wide(c, 12));
        final LinearLayout dayRow = new LinearLayout(c);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(dayRow, Ui.wide(c, 4));
        LinearLayout presets = new LinearLayout(c);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.addView(preset(c, "Every day", () -> {
            d.days = LockRules.EVERY_DAY;
            redraw[0].run();
        }));
        presets.addView(preset(c, "Weekdays", () -> {
            d.days = LockRules.WEEKDAYS;
            redraw[0].run();
        }));
        presets.addView(preset(c, "Weekends", () -> {
            d.days = LockRules.WEEKENDS;
            redraw[0].run();
        }));
        presets.addView(preset(c, "None", () -> {
            d.days = 0;
            redraw[0].run();
        }));
        box.addView(presets, Ui.wide(c, 6));
        box.addView(summary, Ui.wide(c, 8));

        // ---- lock now
        box.addView(Ui.label(c, "Lock now for", 14, Ui.MUTED, true), Ui.wide(c, 16));
        LinearLayout nowRow = new LinearLayout(c);
        nowRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView nowNote = Ui.label(c, "", 13, Ui.MUTED, false);
        for (int i = 0; i < LOCK_MIN.length; i++) {
            final int minutes = LOCK_MIN[i];
            Button chip = Ui.button(c, LOCK_TEXT[i], false, v -> {
                d.untilMs = System.currentTimeMillis() + minutes * 60000L;
                redraw[0].run();
            });
            chip.setTextSize(13);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.rightMargin = Ui.dp(c, 2);
            nowRow.addView(chip, p);
        }
        box.addView(nowRow, Ui.wide(c, 4));
        box.addView(nowNote, Ui.wide(c, 4));
        TextView clear = Ui.label(c, "Unlock now", 14, Ui.TEAL, true);
        clear.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        clear.setOnClickListener(v -> {
            d.untilMs = 0;
            redraw[0].run();
        });
        box.addView(clear, Ui.wide(c, 0));

        // ---- activity types
        box.addView(Ui.label(c, "Also lock during", 14, Ui.MUTED, true), Ui.wide(c, 16));
        final TextView duringText = Ui.label(c, "", 14, Ui.INK, false);
        box.addView(duringText, Ui.wide(c, 4));
        box.addView(Ui.button(c, "Choose activity types", false, v -> chooseTypes(a, theme, d, () -> redraw[0].run())), Ui.wide(c, 6));
        TextView duringNote = Ui.label(c, "The apps are locked while a slot of one of these types is going on, and open again when it ends. "
                + "A break, a slot you took off, and the time between slots do not lock.", 12, Ui.MUTED, false);
        duringNote.setLineSpacing(Ui.dp(c, 2), 1f);
        box.addView(duringNote, Ui.wide(c, 4));

        // ---- hide
        box.addView(Ui.toggle(c, "Hide the apps while they are locked", d.hide, (sw, on) -> d.hide = on), Ui.wide(c, 14));
        TextView hideNote = Ui.label(c, "A locked app then does not show in the list of all apps or in search. It comes back when the lock ends.",
                12, Ui.MUTED, false);
        hideNote.setLineSpacing(Ui.dp(c, 2), 1f);
        box.addView(hideNote, Ui.wide(c, 4));

        // ---- strict
        final Switch strict = Ui.toggle(c, strictText(open[0]), d.strict && DayPass.has(open[0], DayPass.LOCK_STRICT), (sw, on) -> {
            if (on && !DayPass.has(open[0], DayPass.LOCK_STRICT)) {
                sw.setChecked(false);
                ProPrompt.show(a, DayPass.LOCK_STRICT, "Strict locks", theme, () -> {
                    onUnlocked[0].run();
                    sw.setChecked(true);   // the switch the person just earned
                });
                return;
            }
            d.strict = on;
        });
        box.addView(strict, Ui.wide(c, 14));
        TextView note = Ui.label(c, "Without strict, you can still break a lock: wait 30 seconds and type a sentence. "
                + "A strict lock cannot be broken, and cannot be changed while it holds. "
                + (cfg.strong ? "With the strong lock on, locks hold wherever the app is opened from."
                : "Locks apply when you open an app from this home screen. The strong lock in the home settings holds them everywhere."), 12, Ui.MUTED, false);
        note.setLineSpacing(Ui.dp(c, 2), 1f);
        box.addView(note, Ui.wide(c, 6));

        redraw[0] = () -> {
            from.setText("From " + Routine.formatTime(d.start, h24));
            to.setText("to " + Routine.formatTime(d.end, h24));
            drawDays(c, dayRow, d, redraw[0]);
            duringText.setText(d.during.isEmpty() ? "Not locked by activity." : LockRules.duringText(d.during) + ".");
            if (d.days == 0) {
                summary.setText(d.during.isEmpty() ? "No daily schedule. Use Lock now, pick days, or lock during an activity type."
                        : "No daily times. This group locks during the activity types below.");
            } else {
                summary.setText("Locked " + LockRules.daysText(d.days).toLowerCase(java.util.Locale.ROOT) + ", "
                        + Routine.formatTime(d.start, h24) + " to " + Routine.formatTime(d.end, h24)
                        + (d.end <= d.start ? (d.end == d.start ? " (the whole day)" : " (into the next morning)") : "") + ".");
            }
            long now = System.currentTimeMillis();
            nowNote.setText(d.untilMs > now ? "Locked by hand until " + clock(d.untilMs, h24) + " (" + LockRules.left(d.untilMs, now) + " left)."
                    : "Not locked by hand.");
        };
        redraw[0].run();

        b.setTitle(titleText(open[0], existing != null)).setView(sv)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null);
        if (existing != null) b.setNeutralButton("Delete", null);
        final AlertDialog dlg = b.create();
        dlg.show();
        // After a video has opened one item, the words in this dialog follow (only that item's part changes).
        onUnlocked[0] = () -> {
            open[0] = Pro.open(a);
            name.setVisibility(DayPass.has(open[0], DayPass.LOCK_MORE) ? View.VISIBLE : View.GONE);
            appsLabel.setText(appsLabelText(open[0]));
            strict.setText(strictText(open[0]));
            dlg.setTitle(titleText(open[0], existing != null));
        };
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (d.apps.isEmpty()) {
                Lang.toast(c, "Choose at least one app", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!DayPass.has(open[0], DayPass.LOCK_APPS) && d.apps.size() > LockRules.FREE_GROUP_APPS) {
                ProPrompt.show(a, DayPass.LOCK_APPS, "Locking more than " + LockRules.FREE_GROUP_APPS + " apps (a group of apps)", theme,
                        () -> {
                            onUnlocked[0].run();
                            v.performClick();   // carry on with Save
                        });
                return;
            }
            final boolean named = DayPass.has(open[0], DayPass.LOCK_MORE);
            final boolean strictOk = DayPass.has(open[0], DayPass.LOCK_STRICT);
            LauncherConfig.Group g = existing;
            if (g == null) g = cfg.addGroup(named ? name.getText().toString() : "Locked apps");
            if (g == null) {
                Lang.toast(c, "That is the most groups there can be", Toast.LENGTH_SHORT).show();
                return;
            }
            g.name = LauncherConfig.cleanGroupName(named ? name.getText().toString() : (existing == null ? "Locked apps" : g.name));
            g.apps.clear();
            g.apps.addAll(d.apps);
            g.startMin = d.start;
            g.endMin = d.end;
            g.days = d.days;
            g.strict = d.strict && strictOk;
            g.untilMs = d.untilMs;
            g.during.clear();
            g.during.addAll(d.during);
            g.hide = d.hide;
            LauncherStore.save(a, cfg);
            dlg.dismiss();
            if (changed != null) changed.run();
        });
        if (existing != null) {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                cfg.removeGroup(existing);
                LauncherStore.save(a, cfg);
                dlg.dismiss();
                if (changed != null) changed.run();
            });
        }
    }

    private static String appsLabelText(int open) {
        return DayPass.has(open, DayPass.LOCK_APPS) ? "Apps in the group" : "Apps to lock (up to " + LockRules.FREE_GROUP_APPS + " free)";
    }

    private static String strictText(int open) {
        return DayPass.has(open, DayPass.LOCK_STRICT) ? "Strict: no way past until it ends" : "Strict: no way past (Pro)";
    }

    private static String titleText(int open, boolean existing) {
        return DayPass.has(open, DayPass.LOCK_MORE) ? (existing ? "Locked group" : "New locked group") : "Lock apps";
    }

    private static String clock(long ms, boolean h24) {
        return LockRules.untilText(ms, System.currentTimeMillis(), Store.zone(), h24);
    }

    private static TextView preset(Context c, String text, final Runnable r) {
        TextView t = Ui.label(c, text, 13, Ui.TEAL, true);
        t.setPadding(0, Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private static void drawDays(final Context c, LinearLayout row, final Draft d, final Runnable redraw) {
        row.removeAllViews();
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            boolean on = (d.days & bit) != 0;
            TextView t = Ui.label(c, LockRules.dayName(i).substring(0, 1), 14, on ? 0xFFFFFFFF : Ui.INK, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.box(c, on ? Ui.TEAL : Ui.SOFT, on ? 0 : Ui.LINE, 20));
            t.setContentDescription(LockRules.dayName(i));
            t.setOnClickListener(v -> {
                d.days ^= bit;
                redraw.run();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f);
            p.rightMargin = Ui.dp(c, 4);
            row.addView(t, p);
        }
    }

    private static void setApps(TextView t, List<AppEntry> all, Draft d) {
        if (d.apps.isEmpty()) {
            t.setText("No apps yet.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (String k : d.apps) {
            String n = AppKey.pkg(k);
            for (AppEntry e : all) if (e.key.equals(k)) n = e.label;
            names.add(n);
        }
        StringBuilder sb = new StringBuilder(d.apps.size() + (d.apps.size() == 1 ? " app: " : " apps: "));
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            if (i == 4 && names.size() > 5) {
                sb.append("and ").append(names.size() - 4).append(" more");
                break;
            }
            sb.append(names.get(i));
        }
        t.setText(sb.toString());
    }

    /** Which activity types lock the group: every type of a kind ("Any task"), or single types such as Study. */
    private static void chooseTypes(final Activity a, final int theme, final Draft d, final Runnable done) {
        Routine r;
        try {
            r = Store.load(a);
        } catch (RuntimeException e) {
            r = Routine.sample();
        }
        final List<String> keys = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) {
            keys.add(LauncherConfig.anyKey(k));
            labels.add("Any " + Routine.KIND_NAMES[k].toLowerCase(java.util.Locale.ROOT));
            for (Routine.Sub s : r.subsOf(k)) {
                keys.add(LauncherConfig.typeKey(k, s.name));
                labels.add((Routine.isOther(s.name) ? "Other " + Routine.KIND_NAMES[k].toLowerCase(java.util.Locale.ROOT) : s.name));
            }
        }
        final boolean[] on = new boolean[keys.size()];
        for (int i = 0; i < on.length; i++) on[i] = d.during.contains(keys.get(i));
        new DBuilder(a, theme)
                .setTitle("Lock during")
                .setMultiChoiceItems(labels.toArray(new String[0]), on, (dlg, which, checked) -> on[which] = checked)
                .setPositiveButton("Done", (dlg, w) -> {
                    d.during.clear();
                    for (int i = 0; i < on.length && d.during.size() < LauncherConfig.MAX_DURING; i++) if (on[i]) d.during.add(keys.get(i));
                    done.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void chooseApps(final Activity a, final int theme, final List<AppEntry> apps, final Draft d, final int[] open,
                                   final Runnable unlocked, final Runnable done) {
        final List<AppEntry> list = new ArrayList<>();
        for (AppEntry e : apps) if (!a.getPackageName().equals(e.pkg)) list.add(e);
        final String[] names = new String[list.size()];
        final boolean[] on = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).shown();
            on[i] = d.apps.contains(list.get(i).key);
        }
        // Apps that are in the group but not in the list (uninstalled) stay as they are.
        new DBuilder(a, theme).setAppChoices(names, on, (dlg, which, checked) -> {
                    String key = list.get(which).key;
                    if (checked) {
                        if (!DayPass.has(open[0], DayPass.LOCK_APPS) && d.apps.size() >= LockRules.FREE_GROUP_APPS) {
                            ((AlertDialog) dlg).getListView().setItemChecked(which, false);
                            ProPrompt.show(a, DayPass.LOCK_APPS, "Locking more than " + LockRules.FREE_GROUP_APPS + " apps (a group of apps)", theme,
                                    () -> {
                                        unlocked.run();
                                        // the app that was being added is added now
                                        if (!d.apps.contains(key) && d.apps.size() < LauncherConfig.MAX_GROUP_APPS) d.apps.add(key);
                                        try {
                                            ((AlertDialog) dlg).getListView().setItemChecked(which, d.apps.contains(key));
                                        } catch (RuntimeException ignored) {
                                            // the list is already gone
                                        }
                                    });
                            return;
                        }
                        if (d.apps.size() >= LauncherConfig.MAX_GROUP_APPS) {
                            ((AlertDialog) dlg).getListView().setItemChecked(which, false);
                            Lang.toast(a, "Up to " + LauncherConfig.MAX_GROUP_APPS + " apps in a group", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        d.apps.add(key);
                    } else {
                        d.apps.remove(key);
                    }
                })
                .setTitle("Apps to lock")
                .setPositiveButton("Done", (dlg, w) -> done.run())
                .setNegativeButton("Cancel", null)
                .show();
    }
}
