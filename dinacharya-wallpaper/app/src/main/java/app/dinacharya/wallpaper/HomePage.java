package app.dinacharya.wallpaper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** The Home tab: a greeting, what is happening now and next, today's changes, a preview of the home screen and today's goals. */
final class HomePage extends Page {
    private TextView hello;
    private TextView dateLine;
    private TextView avatar;
    private LinearLayout crashBox;
    private LinearLayout nowBox;
    private LinearLayout alarmBox;
    private LinearLayout homeBox;
    private LinearLayout goalsBox;
    private AdSlot adSlot;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshNow();
            refreshAlarmCard();
            scroll.postDelayed(this, 60000L - (System.currentTimeMillis() % 60000L) + 200L);
        }
    };

    HomePage(MainActivity host) {
        super(host);
    }

    @Override
    void build(LinearLayout body) {
        // Greeting, with the account initial on the right (opens Backup).
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        hello = Ui.label(a, "", 26, Ui.INK, true);
        dateLine = Ui.label(a, "", 14, Ui.MUTED, false);
        words.addView(hello);
        words.addView(dateLine, Ui.wide(a, 2));
        head.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        avatar = Ui.label(a, "", 18, 0xFFFFFFFF, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setOnClickListener(v -> a.go(MainActivity.TAB_BACKUP));
        head.addView(avatar, new LinearLayout.LayoutParams(a.dp(44), a.dp(44)));
        body.addView(head);

        crashBox = new LinearLayout(a);
        crashBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(crashBox, Ui.wide(a, 0));

        nowBox = new LinearLayout(a);
        nowBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(nowBox, Ui.wide(a, 16));

        alarmBox = new LinearLayout(a);
        alarmBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(alarmBox, Ui.wide(a, 0));

        a.extras.buildChanges(body);

        // Offered until Dinacharya is the phone's home app.
        homeBox = new LinearLayout(a);
        homeBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(homeBox, Ui.wide(a, 0));

        // The preview of the home screen, which is the day poster. It is tall, so it opens and closes (closed at first).
        final Expander.Part pp = Expander.card(a, body, "Your home screen", null, "A preview, and the button to change its look",
                "home_preview", false);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int pw = Math.min((int) (dm.widthPixels * 0.56f), a.dp(250));
        int ph = (int) (pw * (dm.heightPixels / (float) dm.widthPixels));
        final PosterView preview = new PosterView(a);
        preview.setElevation(a.dp(3));
        a.registerPreview(preview);
        LinearLayout.LayoutParams plp = Ui.lp(a, pw, ph, 12);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.body.addView(preview, plp);
        pp.body.addView(Ui.button(a, "Change the look", true, v -> {
            a.go(MainActivity.TAB_SETTINGS);
            ((SettingsPage) a.page(MainActivity.TAB_SETTINGS)).open(SettingsPage.LOOK);
        }), Ui.wide(a, 16));
        // A closed preview is not drawn or refreshed.
        preview.setVisibility(pp.isOpen() ? View.VISIBLE : View.GONE);
        pp.onToggled(open -> preview.setVisibility(open ? View.VISIBLE : View.GONE));

        goalsBox = new LinearLayout(a);
        goalsBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(goalsBox, Ui.wide(a, 0));

        adSlot = new AdSlot(a, a::openPro);
        body.addView(adSlot, Ui.wide(a, 16));

        a.addFooter(body);
    }

    @Override
    void refresh() {
        if (!built()) return;
        AccountStore acc = new AccountStore(a);
        int h = ZonedDateTime.now(Store.zone()).getHour();
        String part = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String first = acc.signedIn() ? acc.firstName() : "";
        hello.setText(first.isEmpty() ? part : part + ", " + first);
        String initial = first.isEmpty() ? "" : first.substring(0, 1).toUpperCase(Lang.locale());
        avatar.setText(initial);
        avatar.setBackground(Ui.box(a, initial.isEmpty() ? Ui.TONAL : Ui.TEAL, 0, 22));
        avatar.setContentDescription(acc.signedIn() ? "Signed in as " + acc.email() + ". Open Backup." : "Not signed in. Open Backup.");
        if (initial.isEmpty()) {
            avatar.setText("☁");
            avatar.setTextColor(Ui.TEAL_DARK);
        } else {
            avatar.setTextColor(0xFFFFFFFF);
        }
        showCrashIfAny();
        refreshHomeCard();
        refreshNow();
        a.extras.refresh();
        refreshAlarmCard();
        refreshGoals();
        adSlot.refresh();
        scroll.removeCallbacks(tick);
        scroll.postDelayed(tick, 60000L - (System.currentTimeMillis() % 60000L) + 200L);
    }

    @Override
    void hidden() {
        if (scroll != null) scroll.removeCallbacks(tick);
    }

    @Override
    void destroy() {
        if (adSlot != null) adSlot.release();
    }

    // ------------------------------------------------------------------ now and next

    /** The setup card: shown while any permission or setting from the first-start setup is still missing. */
    private void refreshHomeCard() {
        homeBox.removeAllViews();
        int missing = Permissions.missing(a);
        if (missing == 0) return;
        LinearLayout card = Ui.section(a, homeBox, "Setup", missing == 1 ? "1 setting still to do" : missing + " settings still to do");
        card.addView(a.note("These make the alarms ring on time and the home screen work. Each opens Android\u2019s own screen."), Ui.wide(a, 6));
        boolean first = true;
        for (final Permissions.Step st : Permissions.steps(a)) {
            if (st.ok) continue;
            card.addView(Ui.statusRow(a, st.title, st.why, false, st.action, () -> Permissions.request(a, st.id)), Ui.wide(a, first ? 8 : 2));
            first = false;
        }
        card.addView(Ui.button(a, "Open the setup guide", true, v -> a.startActivity(new Intent(a, SetupActivity.class)
                .putExtra(SetupActivity.EXTRA_REVIEW, true))), Ui.wide(a, 10));
        card.addView(Ui.button(a, "Take the tour", false, v -> a.startActivity(new Intent(a, TourActivity.class))), Ui.wide(a, 6));
    }

    private void refreshNow() {
        nowBox.removeAllViews();
        Routine m = a.routine();
        long now = System.currentTimeMillis();
        Plan.DayPlan p;
        try {
            p = Plan.resolve(m, Store.overrides(a, m), now, Store.zone());
        } catch (RuntimeException e) {
            CrashLog.note(a, "now card", e);
            return;
        }
        String day = p.day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Lang.locale()));
        dateLine.setText(m.hasWeek() || m.tempFor(p.day) != null ? day + "  ·  " + m.forDay(p.day).name : day);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.dp(18), a.dp(16), a.dp(18), a.dp(16));
        card.setBackground(Ui.box(a, Ui.TEAL, 0, 24));
        card.setElevation(a.dp(2));
        card.setOnClickListener(v -> a.go(MainActivity.TAB_ROUTINE));

        int next = -1;
        for (int i = 0; i < p.sc.slots.size(); i++) {
            Routine.Slot s = p.sc.slots.get(i);
            if (s.start > p.tt && !s.block.skipped) {
                next = i;
                break;
            }
        }
        final AlarmState state = Store.alarmState(a);
        final boolean onBreak = p.cur >= 0 && state.onBreak(now) && SlotPlay.canBreak(p);
        if (p.cur >= 0) {
            Routine.Slot s = p.sc.slots.get(p.cur);
            Routine.Block b = s.block;
            TextView eb = Ui.label(a, (onBreak ? "ON A BREAK" : "NOW") + (b.sub.isEmpty() || Routine.isOther(b.sub) ? "" : "  ·  " + b.sub.toUpperCase(Lang.locale())), 11, 0xCCFFFFFF, true);
            eb.setLetterSpacing(0.14f);
            card.addView(eb);
            card.addView(Ui.label(a, b.title.isEmpty() ? Routine.KIND_NAMES[b.kind] : b.title, 22, 0xFFFFFFFF, true), Ui.wide(a, 4));
            int left = Math.max(0, s.end - p.tt);
            card.addView(Ui.label(a, Routine.formatTime(s.start, m.fmt24) + " – " + Routine.formatTime(s.end, m.fmt24)
                    + "   ·   " + Routine.formatDuration(left) + " left", 14, 0xE6FFFFFF, false), Ui.wide(a, 2));
            // progress
            FrameLayout bar = new FrameLayout(a);
            bar.setBackground(Ui.box(a, 0x40FFFFFF, 0, 4));
            View fill = new View(a);
            fill.setBackground(Ui.box(a, 0xFFFFFFFF, 0, 4));
            float frac = Math.max(0.02f, Math.min(1f, (p.tt - s.start) / (float) Math.max(1, s.end - s.start)));
            bar.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            final float f = frac;
            bar.post(() -> {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = Math.round(bar.getWidth() * f);
                fill.setLayoutParams(lp);
            });
            card.addView(bar, Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, a.dp(6), 12));
            if (b.note != null && !b.note.trim().isEmpty()) {
                TextView note = Ui.label(a, "📝  " + b.note.trim(), 13, 0xF0FFFFFF, false);
                note.setMaxLines(3);
                card.addView(note, Ui.wide(a, 10));
            }
            addNowActions(card, p, m, now, state, onBreak);
        } else {
            TextView eb = Ui.label(a, "NOW", 11, 0xCCFFFFFF, true);
            eb.setLetterSpacing(0.14f);
            card.addView(eb);
            card.addView(Ui.label(a, "Free time", 22, 0xFFFFFFFF, true), Ui.wide(a, 4));
        }
        if (next >= 0) {
            Routine.Slot s = p.sc.slots.get(next);
            View rule = new View(a);
            rule.setBackgroundColor(0x33FFFFFF);
            card.addView(rule, Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, a.dp(1), 14));
            int in = Math.max(0, s.start - p.tt);
            TextView nx = Ui.label(a, "Next:  " + (s.block.title.isEmpty() ? Routine.KIND_NAMES[s.block.kind] : s.block.title)
                    + "  at " + Routine.formatTime(s.start, m.fmt24) + "  (in " + Routine.formatDuration(in) + ")", 14, 0xFFFFFFFF, false);
            nx.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(nx, Ui.wide(a, 10));
            final int nextIndex = next;
            final Plan.DayPlan plan = p;
            nx.setOnClickListener(v -> openTodos(plan, nextIndex));
            card.addView(Ui.label(a, "Tap it to add a to-do list for it.", 12, 0xCCFFFFFF, false), Ui.wide(a, 2));
        }
        nowBox.addView(card);
    }

    // ------------------------------------------------------------------ break and to-do

    /** Under the slot that is going on: a button to take a break (or to end the one that is on) and the to-do list of the slot. */
    private void addNowActions(LinearLayout card, final Plan.DayPlan p, Routine m, long now, AlarmState state, boolean onBreak) {
        Routine.Block b = p.sc.slots.get(p.cur).block;
        if (onBreak) {
            card.addView(Ui.label(a, "On a break until " + Routine.formatTime(minuteOf(state.breakTo), m.fmt24), 15, 0xFFFFFFFF, true), Ui.wide(a, 12));
        }
        if (!onBreak && state.againAt > now && state.againSrc == b.src && state.againDay.equals(p.day.toString())) {
            card.addView(Ui.label(a, "You said you would start at " + Routine.formatTime(minuteOf(state.againAt), m.fmt24), 15, 0xFFFFFFFF, true), Ui.wide(a, 12));
        }
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        boolean canBreak = SlotPlay.canBreak(p);
        if (canBreak) {
            android.widget.Button br = Ui.button(a, onBreak ? "Back to work" : "Take a break", false, v -> {
                if (onBreak) {
                    SlotActions.endBreak(a);
                    a.previewChanged();
                    refreshNow();
                } else {
                    askBreak(p);
                }
            });
            row.addView(br, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (b.src >= 0 && !b.skipped) {
            List<Routine.Block> blocks = new java.util.ArrayList<>();
            for (Routine.Slot sl : p.sc.slots) blocks.add(sl.block);
            Todos t = Store.todosFor(a, p.day, blocks);
            List<Todos.Item> items = t.forSlot(p.day, b.src, b.kind, b.sub, null);
            int done = 0;
            for (Todos.Item it : items) if (it.done) done++;
            android.widget.Button td = Ui.button(a, items.isEmpty() ? "To-do" : "To-do  " + done + "/" + items.size(), false,
                    v -> openTodos(p, p.cur));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (canBreak) tp.leftMargin = a.dp(8);
            row.addView(td, tp);
        }
        if (row.getChildCount() > 0) card.addView(row, Ui.wide(a, onBreak ? 8 : 14));
        addInlineTodos(card, p);
    }

    /**
     * Every to-do of the slot going on, right on the card: tick them here, and add or repeat them with
     * the To-do button. The list opens and closes (it stays as it was left).
     */
    private void addInlineTodos(LinearLayout card, final Plan.DayPlan p) {
        Routine.Block b = p.sc.slots.get(p.cur).block;
        if (b.src < 0 || b.skipped) return;
        List<Routine.Block> blocks = new java.util.ArrayList<>();
        for (Routine.Slot sl : p.sc.slots) blocks.add(sl.block);
        final Todos t = Store.todosFor(a, p.day, blocks);
        final List<TodoDialog.Section> secs = TodoDialog.sectionsFor(a.routine().forDay(p.day), p.day, b, blocks);
        int total = 0;
        int done = 0;
        int filled = 0;
        for (TodoDialog.Section sec : secs) {
            int n = t.count(p.day, sec.scope);
            total += n;
            done += t.doneCount(p.day, sec.scope);
            if (n > 0) filled++;
        }
        if (total == 0) return;
        Expander.Part part = Expander.moreOnDark(a, card, "Your to-do list", "home_todo", true);
        part.setSummary(done + " of " + total + " done");
        for (final TodoDialog.Section sec : secs) {
            List<Todos.Item> items = t.items(p.day, sec.scope);
            if (items.isEmpty()) continue;
            if (filled > 1) {
                TextView h = Ui.label(a, sec.heading, 12, 0xCCFFFFFF, true);
                part.body.addView(h, Ui.wide(a, 8));
            }
            for (int i = 0; i < items.size(); i++) {
                final int index = i;
                Todos.Item it = items.get(i);
                CheckBox cb = new CheckBox(a);
                cb.setText(it.text);
                cb.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                cb.setTextColor(it.done ? 0xB3FFFFFF : 0xFFFFFFFF);
                cb.setButtonTintList(ColorStateList.valueOf(0xFFFFFFFF));
                if (it.done) cb.setPaintFlags(cb.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                cb.setChecked(it.done);
                cb.setOnCheckedChangeListener((v, on) -> {
                    t.setDone(p.day, sec.scope, index, on);
                    Store.saveTodos(a, t);
                    a.previewChanged();
                    refreshNow();
                });
                part.body.addView(cb, Ui.wide(a, 0));
            }
        }
    }

    private static int minuteOf(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    /** The to-do list of one slot of today's plan. */
    private void openTodos(Plan.DayPlan p, int index) {
        if (index < 0 || index >= p.sc.slots.size()) return;
        Routine.Block b = p.sc.slots.get(index).block;
        if (b.src < 0 || b.skipped) {
            a.toastText("This slot is taken off, so it has no to-do list");
            return;
        }
        List<Routine.Block> blocks = new java.util.ArrayList<>();
        for (Routine.Slot sl : p.sc.slots) blocks.add(sl.block);
        TodoDialog.show(a, p.day, "To-do", TodoDialog.sectionsFor(a.routine().forDay(p.day), p.day, b, blocks), blocks, () -> {
            a.previewChanged();
            refreshNow();
        });
    }

    /** How long a break? − and + in steps of five minutes, or until the slot ends; never past the end of the slot. */
    private void askBreak(Plan.DayPlan p) {
        final long now = System.currentTimeMillis();
        int room = SlotPlay.roomMinutes(now, p.slotEndMs(p.cur));
        if (room < 1) {
            a.toastText("This slot is almost over");
            return;
        }
        final long slotEnd = p.slotEndMs(p.cur);
        final boolean h24 = a.routine().fmt24;
        final boolean canStep = room >= SlotPlay.STEP;
        final MinuteStepper[] stepper = new MinuteStepper[1];
        final boolean[] untilEnd = {!canStep};   // a slot with under 5 minutes left can only be rested to its end
        final TextView caption = Ui.label(a, "", 13, Ui.MUTED, false);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        final Runnable draw = () -> {
            long end = untilEnd[0] ? slotEnd : SlotPlay.breakEnd(System.currentTimeMillis(), stepper[0].minutes(), slotEnd);
            caption.setText("Back at " + Routine.formatTime(minuteOf(end), h24) + (untilEnd[0] ? ", when this slot ends" : ""));
        };
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        stepper[0] = new MinuteStepper(a, 10, Math.max(room, SlotPlay.STEP), m -> draw.run());
        if (canStep) {
            box.addView(stepper[0].view, Ui.wide(a, 4));
        }
        box.addView(caption, Ui.wide(a, 6));
        box.addView(Ui.toggle(a, "Until this slot ends", untilEnd[0], (sw, on) -> {
            untilEnd[0] = on || !canStep;
            stepper[0].setActive(!untilEnd[0]);
            draw.run();
        }), Ui.wide(a, 10));
        if (!canStep) box.getChildAt(box.getChildCount() - 1).setEnabled(false);
        stepper[0].setActive(!untilEnd[0]);
        draw.run();
        new DBuilder(a)
                .setTitle("Take a break")
                .setView(box)
                .setPositiveButton("Start the break", (d, w) -> {
                    // "until the slot ends" asks for a minute more than is left; the break is cut at the end of the slot
                    long end = SlotActions.takeBreak(a, untilEnd[0] ? room + 1 : stepper[0].minutes());
                    if (end < 0) {
                        a.toastText("A break cannot be taken now");
                        return;
                    }
                    a.toastText("Enjoy your break. Back at " + Routine.formatTime(minuteOf(end), a.routine().fmt24));
                    a.previewChanged();
                    refreshNow();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ alarms

    @Override
    void alarmsChanged() {
        refreshAlarmCard();
    }

    private void openAlarmSettings() {
        a.go(MainActivity.TAB_SETTINGS);
        ((SettingsPage) a.page(MainActivity.TAB_SETTINGS)).open(SettingsPage.ALARMS);
    }

    /** One card that says whether alarms will ring, what is next, and what is wrong if something is. */
    private void refreshAlarmCard() {
        if (alarmBox == null) return;
        alarmBox.removeAllViews();
        final Routine r = a.routine();
        try {
            java.util.List<AlarmHealth.Item> items = AlarmHealth.check(a, r);
            String missing = AlarmHealth.missingNames(items);
            AlarmScheduler.Diag diag = AlarmScheduler.diag(a);

            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));

            if (!r.alarmOn) {
                card.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.AMBER, 22));
                card.addView(Ui.eyebrow(a, "Alarms"));
                card.addView(Ui.label(a, "Alarms are off", 18, Ui.INK, true), Ui.wide(a, 4));
                card.addView(Ui.label(a, "Nothing will ring until you turn them on. When a slot starts you hear an alarm "
                        + "and answer: continue as planned, or change it.", 13, Ui.MUTED, false), Ui.wide(a, 4));
                card.addView(Ui.button(a, "Turn on alarms", true, v -> a.extras.enableAlarms()), Ui.wide(a, 12));
            } else if (!missing.isEmpty() || diag.missedFor > 0) {
                card.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.AMBER, 22));
                card.addView(Ui.eyebrow(a, "Alarms"));
                card.addView(Ui.label(a, "Alarms need your attention", 18, Ui.INK, true), Ui.wide(a, 4));
                String why = !missing.isEmpty() ? "Allow: " + missing + "."
                        : "The alarm set for " + a.extras.when(diag.missedFor) + " never reached the app. The phone may be stopping it.";
                card.addView(Ui.label(a, why, 13, Ui.MUTED, false), Ui.wide(a, 4));
                card.addView(Ui.button(a, "Fix now", true, v -> openAlarmSettings()), Ui.wide(a, 12));
            } else {
                card.setBackground(Ui.box(a, Ui.SURFACE, 0, 22));
                card.setElevation(a.dp(1.5f));
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                Glyph g = new Glyph(a, Glyph.BELL, Ui.TEAL_DARK);
                g.setBackground(Ui.box(a, Ui.TONAL, 0, 14));
                g.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
                row.addView(g, new LinearLayout.LayoutParams(a.dp(42), a.dp(42)));
                LinearLayout words = new LinearLayout(a);
                words.setOrientation(LinearLayout.VERTICAL);
                words.setPadding(a.dp(12), 0, a.dp(8), 0);
                AlarmPlanner.Event e = a.extras.nextEvent();
                words.addView(Ui.eyebrow(a, "Next alarm"));
                words.addView(Ui.label(a, e == null ? "None planned" : a.extras.when(e.timeMs)
                        + "  \u00b7  " + (e.title.isEmpty() ? "Untitled" : e.title), 15, Ui.INK, true), Ui.wide(a, 2));
                if (e == null) {
                    words.addView(Ui.label(a, "Every slot has its bell off.", 12, Ui.MUTED, false), Ui.wide(a, 1));
                }
                row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView test = Ui.label(a, "Test", 14, Ui.TEAL, true);
                test.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10));
                test.setBackground(Ui.pressable(a, Ui.TONAL, 0, 16, 0x300E7C7B));
                test.setOnClickListener(v -> a.extras.testAlarm());
                test.setContentDescription("Ring a test alarm in 10 seconds");
                card.setOnClickListener(v -> openAlarmSettings());
                row.addView(test);
                card.addView(row);
                if (!AlarmHealth.batteryOk(a)) {
                    TextView tip = Ui.label(a, "Tip: allow background use so alarms are never late. Tap to open.", 12, Ui.MUTED, false);
                    tip.setOnClickListener(v -> openAlarmSettings());
                    card.addView(tip, Ui.wide(a, 8));
                }
            }
            alarmBox.addView(card, Ui.wide(a, 16));
        } catch (RuntimeException e) {
            CrashLog.note(a, "alarm card", e);
        }
    }

    // ------------------------------------------------------------------ goals today

    private void refreshGoals() {
        goalsBox.removeAllViews();
        final Routine m = a.routine();
        if (m.goals.isEmpty()) return;
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            DayReport today;
            try {
                today = DayCloser.today(app);
            } catch (RuntimeException e) {
                CrashLog.note(app, "goals today", e);
                return;
            }
            final List<TypeStats.Line> lines = TypeStats.of(today);
            a.runOnUiThread(() -> {
                if (a.isFinishing() || goalsBox == null) return;
                goalsBox.removeAllViews();
                LinearLayout card = InsightsPage.goalsCard(a, lines, null, "Today's goals");
                card.setOnClickListener(v -> a.go(MainActivity.TAB_INSIGHTS));
                goalsBox.addView(card, Ui.wide(a, 16));
            });
        }, "home-goals").start();
    }

    // ------------------------------------------------------------------ crash note

    private void showCrashIfAny() {
        crashBox.removeAllViews();
        final String crash = CrashLog.lastCrash(a);
        if (crash == null) return;
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
        card.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.AMBER, 18));
        card.addView(Ui.label(a, "Dinacharya Launcher closed unexpectedly last time", 15, Ui.INK, true));
        card.addView(Ui.label(a, "Sorry about that. Tap Copy details and send them to the developer so it can be fixed.",
                13, Ui.MUTED, false), Ui.wide(a, 4));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.rightMargin = a.dp(5);
        row.addView(Ui.button(a, "Copy details", false, v -> copy(crash + "\n\n" + CrashLog.notes(a))), l);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        r.leftMargin = a.dp(5);
        row.addView(Ui.button(a, "Dismiss", false, v -> {
            CrashLog.clearCrash(a);
            crashBox.removeAllViews();
        }), r);
        card.addView(row, Ui.wide(a, 10));
        crashBox.addView(card, Ui.wide(a, 14));
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Dinacharya problem report", text));
                a.toastText("Details copied");
            }
        } catch (RuntimeException e) {
            a.toastText("Could not copy");
        }
    }

}
