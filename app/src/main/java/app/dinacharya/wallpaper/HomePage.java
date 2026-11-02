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
import android.widget.Switch;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** The Home tab: a greeting, what is happening now and next, today's changes, a preview of the home screen and today's goals. */
final class HomePage extends Page {
    private TextView hello;
    private TextView dateLine;
    private TextView avatar;
    private LinearLayout crashBox;
    private LinearLayout nowBox;
    private LinearLayout alarmBox;
    private LinearLayout libraryBox;
    private LinearLayout homeBox;
    private LinearLayout goalsBox;
    private AdSlot adSlot;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshNow();
            refreshAlarmCard();
            refreshLibrary();
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
        // a small "Day off" chip on its own line, so the greeting keeps its room
        LinearLayout dayOffRow = new LinearLayout(a);
        dayOffRow.setOrientation(LinearLayout.HORIZONTAL);
        dayOffRow.setGravity(Gravity.END);
        dayOffRow.addView(DayOffDialog.chip(a, this::afterChange));
        body.addView(dayOffRow, Ui.wide(a, 8));

        crashBox = new LinearLayout(a);
        crashBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(crashBox, Ui.wide(a, 0));

        nowBox = new LinearLayout(a);
        nowBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(nowBox, Ui.wide(a, 16));

        alarmBox = new LinearLayout(a);
        alarmBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(alarmBox, Ui.wide(a, 0));

        libraryBox = new LinearLayout(a);
        libraryBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(libraryBox, Ui.wide(a, 0));

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
        refreshLibrary();
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
        Plan.DayPlan saved;
        Plan.DayPlan p;
        try {
            saved = Plan.resolve(m, Store.overrides(a, m), now, Store.zone());
            // The day as it is going: a start put off, a break, a slot carried on or finished early, the day off.
            p = Store.livePlan(a, m, saved, now);
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
            if (s.start > p.tt && !s.block.skipped && !s.block.isGap()) {
                next = i;
                break;
            }
        }
        final AlarmState state = Store.alarmState(a);
        Routine.Block now0 = p.cur >= 0 ? p.sc.slots.get(p.cur).block : null;
        final boolean dayOff = now0 != null && now0.live == Routine.LIVE_OFF;
        final boolean onBreak = now0 != null && now0.live == Routine.LIVE_BREAK;
        final boolean waiting = now0 != null && now0.live == Routine.LIVE_WAIT;
        // the alarm went off and has not been answered: the slot starts when you answer
        final boolean noResponse = waiting && LivePlan.NORESP_TITLE.equals(now0.title);
        if (p.cur >= 0) {
            Routine.Slot s = p.sc.slots.get(p.cur);
            Routine.Block b = s.block;
            Routine.Block owner = ownerBlock(saved, b);
            String sub = owner.sub.isEmpty() || Routine.isOther(owner.sub) ? "" : "  ·  " + owner.sub.toUpperCase(Lang.locale());
            String head = dayOff ? "DAY OFF" : onBreak ? "ON A BREAK" : noResponse ? "NO RESPONSE" : waiting ? "STARTING LATER" : "NOW";
            TextView eb = Ui.label(a, head + (dayOff ? "" : sub), 11, 0xCCFFFFFF, true);
            eb.setLetterSpacing(0.14f);
            LinearLayout headRow = new LinearLayout(a);
            headRow.setOrientation(LinearLayout.HORIZONTAL);
            headRow.setGravity(Gravity.CENTER_VERTICAL);
            headRow.addView(eb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!dayOff && !onBreak && !waiting && owner.src >= 0 && !owner.skipped && owner.kind != Routine.SLEEP) {
                // a small pill: this slot only, for today (the slots before and after it stay as they are)
                final Routine.Block ownerBlock = owner;
                final Plan.DayPlan savedPlan = saved;
                TextView pill = Ui.label(a, "Change plan", 12, 0xFFFFFFFF, true);
                pill.setPadding(a.dp(11), a.dp(6), a.dp(11), a.dp(6));
                pill.setBackground(Ui.box(a, 0x33FFFFFF, 0, 16));
                pill.setContentDescription(Lang.tr("Change plan"));
                pill.setOnClickListener(v -> askNewName(savedPlan.day.toString(), ownerBlock.src, ownerBlock.title, 0L, false));
                headRow.addView(pill);
            }
            card.addView(headRow);
            card.addView(Ui.label(a, dayOff ? "Enjoy your day off" : titleOf(owner), 22, 0xFFFFFFFF, true), Ui.wide(a, 4));
            int left = Math.max(0, s.end - p.tt);
            if (dayOff) {
                card.addView(Ui.label(a, "No alarm will ring until you plan the day again, or tomorrow.", 14, 0xE6FFFFFF, false), Ui.wide(a, 4));
                if (state.planAt > now) {
                    card.addView(Ui.label(a, "I will ask again at " + Routine.formatTime(minuteOf(state.planAt), m.fmt24), 14, 0xFFFFFFFF, true),
                            Ui.wide(a, 8));
                }
            } else if (onBreak) {
                card.addView(Ui.label(a, "Break until " + Routine.formatTime(s.end, m.fmt24) + "   ·   " + Routine.formatDuration(left) + " left",
                        14, 0xE6FFFFFF, false), Ui.wide(a, 2));
                card.addView(Ui.label(a, "Then back to " + titleOf(owner) + ", which ends at " + Routine.formatTime(slotEnd(saved, owner), m.fmt24),
                        13, 0xCCFFFFFF, false), Ui.wide(a, 2));
            } else if (noResponse) {
                card.addView(Ui.label(a, "No answer yet. The slot starts when you answer.", 14, 0xE6FFFFFF, false), Ui.wide(a, 2));
                card.addView(Ui.label(a, "It was to start at " + Routine.formatTime(slotStart(saved, owner), m.fmt24) + ", and ends at "
                        + Routine.formatTime(slotEnd(saved, owner), m.fmt24), 13, 0xCCFFFFFF, false), Ui.wide(a, 2));
            } else if (waiting) {
                card.addView(Ui.label(a, "Starts at " + Routine.formatTime(s.end, m.fmt24) + "   ·   in " + Routine.formatDuration(left),
                        14, 0xE6FFFFFF, false), Ui.wide(a, 2));
                card.addView(Ui.label(a, "It was to start at " + Routine.formatTime(slotStart(saved, owner), m.fmt24) + ", and ends at "
                        + Routine.formatTime(slotEnd(saved, owner), m.fmt24), 13, 0xCCFFFFFF, false), Ui.wide(a, 2));
            } else {
                card.addView(Ui.label(a, Routine.formatTime(s.start, m.fmt24) + " – " + Routine.formatTime(s.end, m.fmt24)
                        + "   ·   " + Routine.formatDuration(left) + " left", 14, 0xE6FFFFFF, false), Ui.wide(a, 2));
                String changed = changeNote(b, s, saved, owner, m.fmt24);
                if (!changed.isEmpty()) card.addView(Ui.label(a, changed, 13, 0xFFFFFFFF, true), Ui.wide(a, 4));
            }
            if (!dayOff) {
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
            }
            if (!dayOff && owner.note != null && !owner.note.trim().isEmpty()) {
                TextView note = Ui.label(a, "📝  " + owner.note.trim(), 13, 0xF0FFFFFF, false);
                note.setMaxLines(3);
                card.addView(note, Ui.wide(a, 10));
            }
            if (dayOff) addDayOffActions(card);
            else addNowActions(card, saved, p, now, state);
        } else {
            TextView eb = Ui.label(a, "NOW", 11, 0xCCFFFFFF, true);
            eb.setLetterSpacing(0.14f);
            card.addView(eb);
            card.addView(Ui.label(a, "Free time", 22, 0xFFFFFFFF, true), Ui.wide(a, 4));
        }
        if (next >= 0 && !dayOff) {
            Routine.Slot s = p.sc.slots.get(next);
            View rule = new View(a);
            rule.setBackgroundColor(0x33FFFFFF);
            card.addView(rule, Ui.lp(a, ViewGroup.LayoutParams.MATCH_PARENT, a.dp(1), 14));
            int in = Math.max(0, s.start - p.tt);
            TextView nx = Ui.label(a, "Next:  " + titleOf(s.block)
                    + "  at " + Routine.formatTime(s.start, m.fmt24) + "  (in " + Routine.formatDuration(in) + ")", 14, 0xFFFFFFFF, false);
            nx.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(nx, Ui.wide(a, 10));
            final int nextIndex = SlotPlay.indexOfSrc(saved, s.block.slotSrc());
            final Plan.DayPlan plan = saved;
            nx.setOnClickListener(v -> openTodos(plan, nextIndex));
            card.addView(Ui.label(a, "Tap it to add a to-do list for it.", 12, 0xCCFFFFFF, false), Ui.wide(a, 2));
        }
        nowBox.addView(card);
    }

    private static String titleOf(Routine.Block b) {
        return b.title.isEmpty() ? Routine.KIND_NAMES[b.kind] : b.title;
    }

    /** The block of the saved plan this part of the live day belongs to (a break or a wait belongs to the slot it sits in). */
    private static Routine.Block ownerBlock(Plan.DayPlan saved, Routine.Block b) {
        int i = SlotPlay.indexOfSrc(saved, b.slotSrc());
        return i >= 0 ? saved.sc.slots.get(i).block : b;
    }

    private static int slotStart(Plan.DayPlan saved, Routine.Block owner) {
        int i = SlotPlay.indexOfSrc(saved, owner.src);
        return i >= 0 ? saved.sc.slots.get(i).start : 0;
    }

    private static int slotEnd(Plan.DayPlan saved, Routine.Block owner) {
        int i = SlotPlay.indexOfSrc(saved, owner.src);
        return i >= 0 ? saved.sc.slots.get(i).end : 0;
    }

    /** One line under the time when the slot began or ended other than planned: "Started late", "Carried on", "Started early". */
    private static String changeNote(Routine.Block b, Routine.Slot s, Plan.DayPlan saved, Routine.Block owner, boolean h24) {
        int ps = slotStart(saved, owner);
        int pe = slotEnd(saved, owner);
        switch (b.live) {
            case Routine.LIVE_LATE:
                return "Started late, at " + Routine.formatTime(s.start, h24) + " instead of " + Routine.formatTime(ps, h24);
            case Routine.LIVE_EARLY:
                return "Started early, at " + Routine.formatTime(s.start, h24) + " instead of " + Routine.formatTime(ps, h24);
            case Routine.LIVE_CARRIED:
                return "Carried on, so it ends at " + Routine.formatTime(s.end, h24) + " instead of " + Routine.formatTime(pe, h24);
            case Routine.LIVE_CUT:
                return "Finished early, at " + Routine.formatTime(s.end, h24) + " instead of " + Routine.formatTime(pe, h24);
            default:
                return "";
        }
    }

    // ------------------------------------------------------------------ break, finishing early and to-do

    /** Under a day off: plan the day, or go back to the schedule as it was. */
    private void addDayOffActions(LinearLayout card) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(Ui.button(a, "Plan the day", true, v -> DayOffDialog.openPlanner(a)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = a.dp(8);
        row.addView(Ui.button(a, "Back to my schedule", false, v -> {
            SlotActions.endDayOff(a);
            a.toastText("Your schedule is back. The alarms ring again.");
            afterChange();
        }), lp);
        card.addView(row, Ui.wide(a, 14));
    }

    /** Under the slot that is going on: a button to take a break (or to end the one that is on), the to-do list and "Finish early". */
    private void addNowActions(LinearLayout card, final Plan.DayPlan saved, final Plan.DayPlan p, long now, AlarmState state) {
        Routine.Block b = p.sc.slots.get(p.cur).block;
        final boolean onBreak = b.live == Routine.LIVE_BREAK;
        if (b.live == Routine.LIVE_WAIT) addWaitChoices(card, saved, p);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        boolean canBreak = SlotPlay.canBreak(p);
        if (onBreak || canBreak) {
            android.widget.Button br = Ui.button(a, onBreak ? "Back to work" : "Take a break", false, v -> {
                if (onBreak) {
                    SlotActions.endBreak(a);
                    afterChange();
                } else {
                    askBreak(p);
                }
            });
            row.addView(br, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        final int sIdx = SlotPlay.indexOfSrc(saved, b.slotSrc());
        if (sIdx >= 0 && !saved.sc.slots.get(sIdx).block.skipped) {
            Routine.Block ob = saved.sc.slots.get(sIdx).block;
            Todos t = Store.todosFor(a, saved.day, blocksOf(saved));
            int[] cnt = SlotTodo.counts(todoRows(saved, ob, t));   // the slot's things, its goals and what is on their lists
            android.widget.Button td = Ui.button(a, cnt[1] == 0 ? "To-do" : "To-do  " + cnt[0] + "/" + cnt[1], false,
                    v -> openTodos(saved, sIdx));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (row.getChildCount() > 0) tp.leftMargin = a.dp(8);
            row.addView(td, tp);
        }
        if (row.getChildCount() > 0) card.addView(row, Ui.wide(a, 14));
        final EarlyFinish.Offer offer = b.live == Routine.LIVE_WAIT ? null : EarlyFinish.of(p, nextDayOf(p), now);
        if (offer != null) {
            card.addView(Ui.button(a, offer.label(), false, v -> EarlyDialog.show(a, offer, this::afterChange)), Ui.wide(a, 8));
        }
        addInlineTodos(card, saved, sIdx);
    }

    /**
     * While a slot's start is put off: start it now, carry on the slot before for a while (the wait then ends where that stops), or
     * take another plan in its place for today. The same three answers the alarm screen has.
     */
    private void addWaitChoices(LinearLayout card, final Plan.DayPlan saved, final Plan.DayPlan p) {
        Routine.Block w = p.sc.slots.get(p.cur).block;
        final int oi = SlotPlay.indexOfSrc(saved, w.slotSrc());
        if (oi < 0) return;
        final Routine.Block ob = saved.sc.slots.get(oi).block;
        final String day = saved.day.toString();
        final long sStart = saved.slotStartMs(oi);
        final long sEnd = saved.slotEndMs(oi);
        final long waitEnd = p.slotEndMs(p.cur);
        final String title = ob.title.isEmpty() ? Routine.KIND_NAMES[ob.kind] : ob.title;
        card.addView(Ui.button(a, "Start “" + title + "” now", true, v -> {
            SlotActions.startWaitingNow(a, day, ob.src, ob.title);
            afterChange();
        }), Ui.wide(a, 14));
        int pi = SlotPlay.previousIndex(saved, oi);
        if (pi >= 0) {
            Routine.Block pb = saved.sc.slots.get(pi).block;
            final SlotActions.Prev prev = new SlotActions.Prev(pb.src, pb.title);
            final String prevTitle = pb.title.isEmpty() ? Routine.KIND_NAMES[pb.kind] : pb.title;
            card.addView(Ui.button(a, "Continue “" + prevTitle + "”", false,
                    v -> askContinue(day, ob, title, sStart, sEnd, waitEnd, prev, prevTitle)), Ui.wide(a, 8));
        }
        card.addView(Ui.button(a, "Other plan", false, v -> askNewName(day, ob.src, ob.title, sStart, true)), Ui.wide(a, 8));
    }

    /** "Continue the slot before until ...": a − and + for the time, and what happens then. The wait ends when it does. */
    private void askContinue(final String day, final Routine.Block ob, final String title, final long sStart, final long sEnd, long waitEnd,
                             final SlotActions.Prev prev, String prevTitle) {
        final long now = System.currentTimeMillis();
        int room = Math.min(60, SlotPlay.roomMinutes(now, sEnd));
        if (room < 1) {
            a.toastText("This slot is almost over");
            return;
        }
        final boolean h24 = a.routine().fmt24;
        int initial = Math.max(SlotPlay.STEP, Math.round(Math.max(0L, waitEnd - now) / 60000f));
        final MinuteStepper[] st = new MinuteStepper[1];
        final TextView caption = Ui.label(a, "", 13, Ui.MUTED, false);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        final Runnable draw = () -> caption.setText("Then “" + title + "” starts, at " + Routine.formatTime(minuteOf(System.currentTimeMillis()
                + st[0].minutes() * 60000L), h24) + ".");
        st[0] = new MinuteStepper(a, initial, Math.max(room, SlotPlay.STEP), m -> draw.run());
        st[0].words(m -> Routine.formatTime(minuteOf(System.currentTimeMillis() + m * 60000L), h24));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        box.addView(Ui.label(a, "Keep going until", 13, Ui.MUTED, true));
        box.addView(st[0].view, Ui.wide(a, 4));
        box.addView(caption, Ui.wide(a, 6));
        draw.run();
        new DBuilder(a)
                .setTitle("Continue “" + prevTitle + "”")
                .setView(box)
                .setPositiveButton("Keep going", (d, w) -> {
                    long until = SlotActions.continuePreviousWhileWaiting(a, day, ob.src, ob.title, sStart, sEnd, prev, st[0].minutes());
                    a.toastText("“" + title + "” starts at " + Routine.formatTime(minuteOf(until), a.routine().fmt24));
                    afterChange();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * "What will you do instead?": the slot is called something else for today only. During a late start ({@code endWait}) the new plan takes
     * the place of the slot from now to its end; on the slot that is running it changes only that slot. The slot keeps its type.
     */
    private void askNewName(final String day, final int src, final String title, final long sStart, final boolean endWait) {
        final Routine base = a.routine();
        final Overrides ov = Store.overrides(a, base);
        final java.time.LocalDate d = java.time.LocalDate.parse(day);
        Plan.DayPlan dp = Plan.plan(base, ov, d, Store.zone());
        int mi = SlotPlay.indexOfSrc(dp, src);
        final int slotMins = mi < 0 ? 60 : Math.max(SlotPlay.STEP, dp.sc.slots.get(mi).end - dp.sc.slots.get(mi).start);
        final long sEnd = mi < 0 ? sStart + slotMins * 60000L : dp.slotEndMs(mi);
        final long slotStart = mi < 0 ? sStart : dp.slotStartMs(mi);
        final int part = Math.max(SlotPlay.STEP, slotMins - SlotPlay.STEP);
        int pi = mi < 0 ? -1 : SlotPlay.previousIndex(dp, mi);
        final SlotActions.Prev prev = pi < 0 ? null : new SlotActions.Prev(dp.sc.slots.get(pi).block.src, dp.sc.slots.get(pi).block.title);
        final List<Routine.Block> others = new ArrayList<>();
        final List<String> otherNames = new ArrayList<>();
        for (Routine.Slot sl : dp.sc.slots) {
            Routine.Block ob = sl.block;
            if (ob.src < 0 || ob.src == src || ob.skipped || ob.kind == Routine.SLEEP) continue;
            boolean seen = false;
            for (Routine.Block x : others) if (x.src == ob.src) seen = true;
            if (seen) continue;
            others.add(ob);
            otherNames.add(ob.title + " (" + (sl.end - sl.start) + " min)");
        }

        final android.widget.EditText name = new android.widget.EditText(a);
        name.setSingleLine(true);
        name.setHint("What will you do instead?");
        name.setText(title);
        name.setSelectAllOnFocus(true);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        name.setTextColor(Ui.INK);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final android.widget.Spinner target = new android.widget.Spinner(a);
        target.setAdapter(Lang.adapter(a, android.R.layout.simple_spinner_item, otherNames));
        final android.widget.RadioButton swapNames = new android.widget.RadioButton(a);
        swapNames.setText("Swap names (times stay)");
        swapNames.setTextColor(Ui.INK);
        final android.widget.RadioButton swapPlaces = new android.widget.RadioButton(a);
        swapPlaces.setText("Swap places and times");
        swapPlaces.setTextColor(Ui.INK);
        android.widget.RadioGroup mg = new android.widget.RadioGroup(a);
        swapNames.setId(View.generateViewId());
        swapPlaces.setId(View.generateViewId());
        mg.addView(swapNames);
        mg.addView(swapPlaces);
        swapNames.setChecked(true);

        final String[] labels = {"Replace slot", "Continue previous slot", "Swap slot with"};
        final android.widget.RadioButton[] rb = new android.widget.RadioButton[3];
        final LinearLayout[] pan = new LinearLayout[3];
        final MinuteStepper[] steps = new MinuteStepper[3];
        final Switch[] whole = new Switch[3];
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        box.addView(Ui.label(a, "Pick one. This changes today only. The rest of the slot goes back to your saved plan.", 13, Ui.MUTED, false), Ui.wide(a, 4));
        for (int i = 0; i < 3; i++) {
            final int k = i;
            if (i == 1 && prev == null) continue;
            if (i == 2 && others.isEmpty()) continue;
            rb[i] = new android.widget.RadioButton(a);
            rb[i].setText(labels[i]);
            rb[i].setTextColor(Ui.INK);
            rb[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
            LinearLayout pn = new LinearLayout(a);
            pn.setOrientation(LinearLayout.VERTICAL);
            pn.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(10));
            pn.setVisibility(View.GONE);
            pan[i] = pn;
            if (i == 0) pn.addView(name, Ui.wide(a, 0));
            else if (i == 1) pn.addView(Ui.label(a, "“" + prev.title + "” goes on into this slot.", 13, Ui.MUTED, false), Ui.wide(a, 0));
            else {
                pn.addView(target, Ui.wide(a, 0));
                pn.addView(mg, Ui.wide(a, 6));
            }
            whole[i] = new Switch(a);
            whole[i].setText("Whole slot (" + slotMins + " min)");
            whole[i].setTextColor(Ui.INK);
            whole[i].setChecked(true);
            pn.addView(whole[i], Ui.wide(a, 8));
            steps[i] = new MinuteStepper(a, Math.min(30, part), part, m -> { });
            steps[i].view.setVisibility(View.GONE);
            pn.addView(steps[i].view, Ui.wide(a, 6));
            whole[i].setOnCheckedChangeListener((c, on) -> steps[k].view.setVisibility(on ? View.GONE : View.VISIBLE));
            box.addView(rb[i], Ui.wide(a, 8));
            box.addView(pn, Ui.wide(a, 2));
        }
        swapPlaces.setOnCheckedChangeListener((c, on) -> {
            if (on && whole[2] != null) whole[2].setChecked(true);
            if (whole[2] != null) whole[2].setEnabled(!on);
        });
        for (int i = 0; i < 3; i++) {
            final int k = i;
            if (rb[i] == null) continue;
            rb[i].setOnCheckedChangeListener((c, on) -> {
                if (!on) return;
                for (int j = 0; j < 3; j++) {
                    if (rb[j] == null) continue;
                    if (j != k) rb[j].setChecked(false);
                    pan[j].setVisibility(j == k ? View.VISIBLE : View.GONE);
                }
            });
        }
        rb[0].setChecked(true);
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        new DBuilder(a)
                .setTitle("Change the plan")
                .setView(sv)
                .setPositiveButton("Save for today", (dlg, w) -> {
                    int pick = rb[1] != null && rb[1].isChecked() ? 1 : rb[2] != null && rb[2].isChecked() ? 2 : 0;
                    boolean full = whole[pick].isChecked();
                    int mins = steps[pick].minutes();
                    if (pick == 0) {
                        String n = name.getText().toString().trim();
                        if (n.isEmpty()) {
                            a.toastText("Type a name first");
                            return;
                        }
                        if (n.equals(title)) {
                            if (endWait) SlotActions.startWaitingNow(a, day, src, title);
                            afterChange();
                            return;
                        }
                        if (full) SlotActions.changePlan(a, day, src, title, n, sStart, endWait);
                        else SlotActions.changePart(a, day, src, title, n, mins, sStart, endWait);
                        a.toastText(full ? "Changed for today: " + n : n + " for " + mins + " min, then back to the plan");
                    } else if (pick == 1) {
                        long until = SlotActions.continueFrom(a, day, src, title, slotStart, sEnd, prev, full ? slotMins : mins, endWait);
                        a.toastText("“" + prev.title + "” goes on until " + Routine.formatTime(minuteOf(until), a.routine().fmt24));
                    } else {
                        int t = target.getSelectedItemPosition();
                        if (t < 0 || t >= others.size()) return;
                        Routine.Block o = others.get(t);
                        boolean places = swapPlaces.isChecked();
                        SlotActions.swapWith(a, day, src, title, o.src, o.title, o.mins, places, full || places ? 0 : mins);
                        a.toastText("Swapped with " + o.title);
                    }
                    afterChange();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** The plan of the day after {@code p}: the night's sleep is the last slot of its day, and "Early wake" starts the next one. */
    private Plan.DayPlan nextDayOf(Plan.DayPlan p) {
        try {
            Routine m = a.routine();
            return Plan.plan(m, Store.overrides(a, m), p.day.plusDays(1), Store.zone());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The whole list of a slot of the saved plan: its own things, then its goals with the things on their lists (see {@link SlotTodo}). */
    private List<SlotTodo.Row> todoRows(Plan.DayPlan saved, Routine.Block b, Todos t) {
        List<Routine.Block> blocks = blocksOf(saved);
        return SlotTodo.rows(t, saved.day, blocks, b.slotSrc(), a.routine().goalsFor(saved.day, blocks), SlotTodo.passedMinutes(saved, b.slotSrc()));
    }

    private static List<Routine.Block> blocksOf(Plan.DayPlan p) {
        List<Routine.Block> blocks = new java.util.ArrayList<>();
        for (Routine.Slot sl : p.sc.slots) blocks.add(sl.block);
        return blocks;
    }

    /**
     * Every to-do of the slot going on, right on the card: tick them here, and add or repeat them with
     * the To-do button. The list opens and closes (it stays as it was left).
     */
    private void addInlineTodos(LinearLayout card, final Plan.DayPlan saved, int sIdx) {
        if (sIdx < 0) return;
        Routine.Block b = saved.sc.slots.get(sIdx).block;
        if (b.src < 0 || b.skipped) return;
        final Todos t = Store.todosFor(a, saved.day, blocksOf(saved));
        List<SlotTodo.Row> rows = todoRows(saved, b, t);
        int[] cnt = SlotTodo.counts(rows);
        if (cnt[1] == 0) return;
        Expander.Part part = Expander.moreOnDark(a, card, "Your to-do list", "home_todo", true);
        part.setSummary(cnt[0] + " of " + cnt[1] + " done");
        for (final SlotTodo.Row row : rows) {
            if (row.type == SlotTodo.HEAD) {
                // a goal with its own to-dos: not a thing to tick, it heads its list and is done when everything under it is
                LinearLayout hl = new LinearLayout(a);
                hl.setOrientation(LinearLayout.HORIZONTAL);
                TextView h = Ui.label(a, row.text, 13, 0xE6FFFFFF, true);
                if (row.done) h.setPaintFlags(h.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                hl.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                hl.addView(Ui.label(a, row.ticked + " of " + row.total, 13, 0xCCFFFFFF, false));
                part.body.addView(hl, Ui.wide(a, 8));
                continue;
            }
            CheckBox cb = new CheckBox(a);
            cb.setText(row.byTime ? row.text + "  ·  " + Lang.tr("time reached") : row.text);
            cb.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            cb.setTextColor(row.done ? 0xB3FFFFFF : 0xFFFFFFFF);
            cb.setButtonTintList(ColorStateList.valueOf(0xFFFFFFFF));
            if (row.done) cb.setPaintFlags(cb.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            cb.setChecked(row.done);
            cb.setEnabled(row.tickable());
            cb.setOnCheckedChangeListener((v, on) -> {
                if (!SlotTodo.tick(t, saved.day, row, on)) return;
                Store.saveTodos(a, t);
                a.previewChanged();
                refreshNow();
            });
            LinearLayout.LayoutParams lp = Ui.wide(a, 0);
            if (row.type == SlotTodo.STEP) lp.leftMargin = a.dp(22);
            part.body.addView(cb, lp);
        }
    }

    private static int minuteOf(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    /** Redraws what a change of the day touches: the Now card, the next alarm and the library card. */
    private void afterChange() {
        a.previewChanged();
        refreshNow();
        refreshAlarmCard();
        refreshLibrary();
        a.extras.refresh();
    }

    /** The to-do list of one slot of today's saved plan (index in the saved plan). */
    private void openTodos(Plan.DayPlan p, int index) {
        if (index < 0 || index >= p.sc.slots.size()) return;
        Routine.Block b = p.sc.slots.get(index).block;
        if (b.src < 0 || b.skipped) {
            a.toastText("This slot is taken off, so it has no to-do list");
            return;
        }
        List<Routine.Block> blocks = blocksOf(p);
        TodoDialog.showSlot(a, p, b, a.routine().goalsFor(p.day, blocks), TodoDialog.sectionsFor(a.routine().forDay(p.day), p.day, b, blocks), () -> {
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
                    afterChange();
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
                        : diag.missedExit != 0 ? "Android closed the app and cleared the alarm set for " + a.extras.when(diag.missedFor) + ". It is set again now."
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
        addWallpaperCard();
        addLockCard();
    }

    /**
     * The wallpaper is part of the launcher, so when it is not in place the dashboard says so at the top and has the way back. Android
     * removes a live wallpaper when the app is closed from App info; the home screen shows the day by itself meanwhile.
     */
    private void addWallpaperCard() {
        try {
            if (!Store.prefs(a).getBoolean(SetupActivity.PREF_SETUP_DONE, false)) return;
            int st = WallpaperSetup.status(a);
            if (!WallpaperWatch.needsAttention(st)) return;
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
            card.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.AMBER, 22));
            card.addView(Ui.eyebrow(a, "Wallpaper"));
            card.addView(Ui.label(a, st == WallpaperWatch.LOST ? "Your day wallpaper is gone"
                    : st == WallpaperWatch.LOCK_ONLY ? "Only the lock screen has your day" : "Your day wallpaper is not set", 18, Ui.INK, true), Ui.wide(a, 4));
            card.addView(Ui.label(a, st == WallpaperWatch.LOST
                    ? "Android removes a live wallpaper when the app is closed from App info. The home screen still shows your day. "
                    + "Bring the wallpaper back for the lock screen and recent apps."
                    : "It shows your day on the lock screen and in recent apps. Android asks you to confirm once.", 13, Ui.MUTED, false), Ui.wide(a, 4));
            card.addView(Ui.button(a, "Bring it back", true, v -> {
                Store.prefs(a).edit().putLong("wallpaper_asked_at", System.currentTimeMillis()).apply();
                WallpaperSetup.request(a, Permissions.REQ_WALL);
            }), Ui.wide(a, 12));
            alarmBox.addView(card, Ui.wide(a, 16));
        } catch (RuntimeException e) {
            CrashLog.note(a, "wallpaper card", e);
        }
    }

    /**
     * Locks are part of the launcher too. While something is locked or paused and a permission Android keeps for itself is missing, the
     * lock holds only when an app is opened from the home screen; the dashboard says so, and has the way to fix it.
     */
    private void addLockCard() {
        try {
            if (!StrongMode.needsAttention(a)) return;
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
            card.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.AMBER, 22));
            card.addView(Ui.eyebrow(a, "Locks"));
            card.addView(Ui.label(a, "Your locks hold only on the home screen", 18, Ui.INK, true), Ui.wide(a, 4));
            card.addView(Ui.label(a, "An app can still be opened from Recents or App info. Allow what Android asks for, and a lock "
                    + "holds wherever the app is opened from.", 13, Ui.MUTED, false), Ui.wide(a, 4));
            card.addView(Ui.button(a, "Hold them everywhere", true, v -> StrongMode.ask(a)), Ui.wide(a, 12));
            alarmBox.addView(card, Ui.wide(a, 16));
        } catch (RuntimeException e) {
            CrashLog.note(a, "lock card", e);
        }
    }

    // ------------------------------------------------------------------ library mode

    /** Library mode: on or off, for how long, and the way to turn it on or off. */
    private void refreshLibrary() {
        if (libraryBox == null) return;
        libraryBox.removeAllViews();
        try {
            final LibraryMode lib = Store.library(a);
            long now = System.currentTimeMillis();
            boolean on = lib.on(now, Store.zone());
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
            card.setBackground(Ui.box(a, on ? Ui.TONAL : Ui.SURFACE, 0, 22));
            card.setElevation(a.dp(1.5f));
            card.addView(Ui.eyebrow(a, "Library mode"));
            card.addView(Ui.label(a, on ? lib.status(now, Store.zone(), a.routine().fmt24) : "Off", 16, Ui.INK, true), Ui.wide(a, 2));
            card.addView(Ui.label(a, on
                    ? "Every alarm only vibrates, with no sound."
                    : "Keeps every alarm silent, with vibration only, for the library, a class or an exam.", 12, Ui.MUTED, false), Ui.wide(a, 2));
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Switch sw = Ui.toggle(a, "Library mode", on, (s, isOn) -> {
                if (isOn != lib.on(System.currentTimeMillis(), Store.zone())) LibraryDialog.set(a, isOn, this::afterChange);
            });
            row.addView(sw, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = a.dp(8);
            row.addView(Ui.button(a, "Settings", false, v -> a.startActivity(new Intent(a, LauncherSettingsActivity.class)
                    .putExtra(LauncherSettingsActivity.EXTRA_SECTION, LauncherSettingsActivity.SEC_FOCUS)
                    .putExtra(LauncherSettingsActivity.EXTRA_CARD, "quiet"))), lp);
            card.addView(row, Ui.wide(a, 10));
            libraryBox.addView(card, Ui.wide(a, 12));
        } catch (RuntimeException e) {
            CrashLog.note(a, "library card", e);
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
