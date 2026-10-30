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
        boolean both;
        boolean library;
        /** How the group locks: by time, by activity, or both (see {@link LockMode}). */
        int by = LockMode.TIME;
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
            d.both = existing.both;
            d.library = existing.library;
            d.by = LockMode.of(existing.days, existing.during.size(), existing.both);
            // a lock by activity has no days; these are the ones it would use if the person switches to a lock by time
            if (d.by == LockMode.ACTIVITY) d.days = LockRules.EVERY_DAY;
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

        final Runnable[] redraw = new Runnable[1];

        // ---- how it locks: by time, by activity, or by both together
        box.addView(Ui.label(c, "Lock by", 14, Ui.MUTED, true), Ui.wide(c, 16));
        final int[] ways = d.by == LockMode.EITHER
                ? new int[]{LockMode.TIME, LockMode.ACTIVITY, LockMode.BOTH, LockMode.EITHER}   // the fourth only for a lock made before this choice
                : new int[]{LockMode.TIME, LockMode.ACTIVITY, LockMode.BOTH};
        final TextView[] chips = new TextView[ways.length];
        LinearLayout chipRow = new LinearLayout(c);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout chipRow2 = new LinearLayout(c);
        chipRow2.setOrientation(LinearLayout.HORIZONTAL);
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
            (i < 3 ? chipRow : chipRow2).addView(t, cp);
            chips[i] = t;
        }
        box.addView(chipRow, Ui.wide(c, 6));
        if (ways.length > 3) box.addView(chipRow2, Ui.wide(c, 4));
        final TextView howNote = Ui.label(c, "", 12, Ui.MUTED, false);
        howNote.setLineSpacing(Ui.dp(c, 2), 1f);
        box.addView(howNote, Ui.wide(c, 8));

        // ---- the hours and days (a lock by time, and by time with activity)
        final LinearLayout timePanel = new LinearLayout(c);
        timePanel.setOrientation(LinearLayout.VERTICAL);
        timePanel.addView(Ui.label(c, "Locked between", 14, Ui.MUTED, true), Ui.wide(c, 8));
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
        timePanel.addView(presets, Ui.wide(c, 4));
        box.addView(timePanel, Ui.wide(c, 0));

        // ---- the activity types (a lock by activity, and by time with activity)
        final LinearLayout typePanel = new LinearLayout(c);
        typePanel.setOrientation(LinearLayout.VERTICAL);
        typePanel.addView(Ui.label(c, "Activity types", 14, Ui.MUTED, true), Ui.wide(c, 8));
        final TextView duringText = Ui.label(c, "", 14, Ui.INK, false);
        typePanel.addView(duringText, Ui.wide(c, 4));
        typePanel.addView(Ui.button(c, "Choose activity types", false, v -> chooseTypes(a, theme, d, () -> redraw[0].run())), Ui.wide(c, 6));
        TextView duringNote = Ui.label(c, "A break, a slot you took off and the time between slots do not lock.", 12, Ui.MUTED, false);
        duringNote.setLineSpacing(Ui.dp(c, 2), 1f);
        typePanel.addView(duringNote, Ui.wide(c, 4));
        box.addView(typePanel, Ui.wide(c, 0));

        // what the choices come to
        final TextView summary = Ui.label(c, "", 13, Ui.INK, true);
        summary.setLineSpacing(Ui.dp(c, 2), 1f);
        box.addView(summary, Ui.wide(c, 12));

        // ---- more options: kept out of sight unless one of them is already set
        final boolean moreSet = d.untilMs > nowMs || d.library || d.hide || d.strict;
        final LinearLayout moreBody = new LinearLayout(c);
        moreBody.setOrientation(LinearLayout.VERTICAL);
        moreBody.setVisibility(moreSet ? View.VISIBLE : View.GONE);
        LinearLayout moreHead = new LinearLayout(c);
        moreHead.setOrientation(LinearLayout.HORIZONTAL);
        moreHead.setGravity(Gravity.CENTER_VERTICAL);
        moreHead.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        final TextView moreArrow = Ui.label(c, moreSet ? "\u25B4" : "\u25BE", 14, Ui.TEAL, true);
        moreHead.addView(Ui.label(c, "More options", 14, Ui.TEAL, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        moreHead.addView(moreArrow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        moreHead.setOnClickListener(v -> {
            boolean shown = moreBody.getVisibility() != View.VISIBLE;
            moreBody.setVisibility(shown ? View.VISIBLE : View.GONE);
            moreArrow.setText(shown ? "\u25B4" : "\u25BE");
        });
        box.addView(moreHead, Ui.wide(c, 8));
        box.addView(moreBody, Ui.wide(c, 0));

        // lock now
        moreBody.addView(Ui.label(c, "Lock now for", 14, Ui.MUTED, true), Ui.wide(c, 4));
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
        moreBody.addView(nowRow, Ui.wide(c, 4));
        moreBody.addView(nowNote, Ui.wide(c, 4));
        TextView clear = Ui.label(c, "Unlock now", 14, Ui.TEAL, true);
        clear.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        clear.setOnClickListener(v -> {
            d.untilMs = 0;
            redraw[0].run();
        });
        moreBody.addView(clear, Ui.wide(c, 0));

        // library mode, hiding, strict
        moreBody.addView(Ui.toggle(c, "Also lock in library mode", d.library, (sw, on) -> d.library = on), Ui.wide(c, 6));
        moreBody.addView(Ui.toggle(c, "Hide the apps while they are locked", d.hide, (sw, on) -> d.hide = on), Ui.wide(c, 2));
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
        moreBody.addView(strict, Ui.wide(c, 2));
        TextView note = Ui.label(c, "Without strict, you can still break a lock: wait 30 seconds and type a sentence. "
                + "A strict lock cannot be broken, and cannot be changed while it holds, so it can hold for 12 hours in a row at most "
                + "and leaves at least 2 free hours every day. "
                + StrongMode.holdsLine(c, true), 12, Ui.MUTED, false);
        note.setLineSpacing(Ui.dp(c, 2), 1f);
        moreBody.addView(note, Ui.wide(c, 6));

        redraw[0] = () -> {
            for (int i = 0; i < ways.length; i++) {
                boolean on = ways[i] == d.by;
                chips[i].setTextColor(on ? 0xFFFFFFFF : Ui.INK);
                chips[i].setBackground(Ui.box(c, on ? Ui.TEAL : Ui.SOFT, on ? 0 : Ui.LINE, 20));
            }
            howNote.setText(LockMode.about(d.by));
            timePanel.setVisibility(d.by == LockMode.ACTIVITY ? View.GONE : View.VISIBLE);
            typePanel.setVisibility(d.by == LockMode.TIME ? View.GONE : View.VISIBLE);
            from.setText("From " + Routine.formatTime(d.start, h24));
            to.setText("to " + Routine.formatTime(d.end, h24));
            drawDays(c, dayRow, d, redraw[0]);
            duringText.setText(d.during.isEmpty() ? "No activity types chosen yet." : LockRules.duringText(d.during) + ".");
            summary.setText(summaryText(d, h24));
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
                ProPrompt.show(a, DayPass.LOCK_APPS, "Locking more than " + DayPass.appCount(LockRules.FREE_GROUP_APPS) + " (a group of apps)", theme,
                        () -> {
                            onUnlocked[0].run();
                            v.performClick();   // carry on with Save
                        });
                return;
            }
            int missing = LockMode.missing(d.by, d.days, d.during.size());
            if (missing == LockMode.NEED_TYPES) {
                Lang.toast(c, "Choose at least one activity type", Toast.LENGTH_SHORT).show();
                return;
            }
            if (missing == LockMode.NEED_DAYS) {
                Lang.toast(c, "Pick at least one day", Toast.LENGTH_SHORT).show();
                return;
            }
            final boolean named = DayPass.has(open[0], DayPass.LOCK_MORE);
            final boolean strictOk = DayPass.has(open[0], DayPass.LOCK_STRICT);
            if (d.strict && strictOk) {
                // a strict lock cannot be broken, so it must end: not too long in a row, and some free time every day
                LauncherConfig.Group t = new LauncherConfig().addGroup("check");
                t.startMin = d.start;
                t.endMin = d.end;
                t.days = LockMode.daysToSave(d.by, d.days);
                t.strict = true;
                if (LockMode.keepsTypes(d.by)) t.during.addAll(d.during);
                t.both = LockMode.bothToSave(d.by);
                t.library = d.library;
                Routine routine = null;
                try {
                    routine = Store.load(a);
                } catch (RuntimeException ex) {
                    // the activity part is then not checked
                }
                String problem = LockRules.strictProblem(t, routine, java.time.LocalDate.now(Store.zone()));
                if (problem != null) {
                    new DBuilder(a, theme).setTitle("This strict lock is too long")
                            .setMessage(problem).setPositiveButton("OK", null).show();
                    return;
                }
            }
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
            g.days = LockMode.daysToSave(d.by, d.days);
            g.strict = d.strict && strictOk;
            g.untilMs = d.untilMs;
            g.during.clear();
            if (LockMode.keepsTypes(d.by)) g.during.addAll(d.during);
            g.hide = d.hide;
            g.both = LockMode.bothToSave(d.by);
            g.library = d.library;
            LauncherStore.save(a, cfg);
            dlg.dismiss();
            if (changed != null) changed.run();
            StrongMode.offer(a);   // so the lock also holds from Recents and App info: asks for what Android still has to be told
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

    /** One line saying what the group will do, for the way chosen. */
    private static String summaryText(Draft d, boolean h24) {
        if (d.by != LockMode.TIME && d.during.isEmpty()) return "Choose at least one activity type";
        String types = LockRules.duringText(d.during);
        String days = LockRules.daysText(d.days).toLowerCase(java.util.Locale.ROOT);
        String from = Routine.formatTime(d.start, h24), to = Routine.formatTime(d.end, h24);
        switch (d.by) {
            case LockMode.ACTIVITY:
                return "Locked while a " + types + " slot is going on.";
            case LockMode.BOTH:
                if (d.days == 0) return "Pick at least one day";
                return "Locked only while a " + types + " slot is going on, " + days + " from " + from + " to " + to + ".";
            case LockMode.EITHER:
                if (d.days == 0) return "Locked while a " + types + " slot is going on.";
                return "Locked in the times above, and also whenever a " + types + " slot is going on.";
            default:
                if (d.days == 0) return "No daily schedule. Use Lock now, pick days, or lock during an activity type.";
                return "Locked " + days + ", " + from + " to " + to
                        + (d.end <= d.start ? (d.end == d.start ? " (the whole day)" : " (into the next morning)") : "") + ".";
        }
    }

    private static String appsLabelText(int open) {
        return DayPass.has(open, DayPass.LOCK_APPS) ? "Apps in the group" : "Apps to lock (" + DayPass.appCount(LockRules.FREE_GROUP_APPS) + " free)";
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
                            ProPrompt.show(a, DayPass.LOCK_APPS, "Locking more than " + DayPass.appCount(LockRules.FREE_GROUP_APPS) + " (a group of apps)", theme,
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
