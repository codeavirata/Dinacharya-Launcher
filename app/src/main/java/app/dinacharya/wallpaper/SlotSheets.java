package app.dinacharya.wallpaper;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The questions about the running slot, as glass sheets: Adjust time, Finish (rest, start the next slot, a new activity), Take a break and
 * Change plan. The Home screen and the alarm screen share them, so the same question always looks and works the same. The rules behind
 * them are in {@link SlotAdjust}, {@link FinishChoices} and {@link SlotActions}.
 */
final class SlotSheets {
    private SlotSheets() {
    }

    // ------------------------------------------------------------------ small helpers

    static String clock(long ms, boolean h24) {
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(Store.zone());
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
    }

    private static String named(String title, int kind) {
        return title == null || title.isEmpty() ? Routine.KIND_NAMES[kind] : title;
    }

    private static void say(Activity a, String msg) {
        Lang.toast(a, msg, Toast.LENGTH_LONG).show();
    }

    private static LinearLayout column(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ------------------------------------------------------------------ adjust time

    /** More or less time for the slot that is running, for today: the minutes come from, or go to, another slot of the day. */
    static void adjustTime(final Activity a, final Runnable changed) {
        final SlotAdjust.Choices ch = SlotActions.adjustChoices(a);
        if (ch == null) {
            say(a, "This slot cannot be adjusted now");
            return;
        }
        final Routine m = Store.load(a);
        final boolean h24 = m.fmt24;
        long now = System.currentTimeMillis();
        final Plan.DayPlan saved = Plan.resolve(m, Store.overrides(a, m), now, Store.zone());
        final Routine.Slot cs = saved.sc.slots.get(ch.index);
        final String cur = named(cs.block.title, cs.block.kind);
        final long endMs = saved.slotEndMs(ch.index);
        final Glass.Sheet sheet = new Glass.Sheet(a, "Adjust time", "“" + cur + "” ends at " + clock(endMs, h24) + ". Today only.");
        final int[] mode = {ch.canGrow() ? 0 : 1};   // 0 more time, 1 less time
        final TextView from = Glass.eyebrow(a, "Take the time from", Glass.FAINT);
        final Glass.Choices[] list = new Glass.Choices[1];
        final Glass.Stepper[] st = new Glass.Stepper[1];
        final TextView preview = Glass.note(a, "");
        final TextView apply = Glass.main(a, "Apply", null);

        final Runnable redraw = new Runnable() {
            @Override
            public void run() {
                int i = list[0].selected();
                if (i < 0) return;
                SlotAdjust.Option o = ch.others.get(i);
                int most = mode[0] == 0 ? ch.maxGrow(o) : ch.maxShrink(o);
                st[0].setMax(Math.max(SlotAdjust.STEP, most));
                st[0].setActive(most >= SlotAdjust.STEP);
                Glass.enable(apply, most >= SlotAdjust.STEP);
                int v = st[0].value();
                if (most < SlotAdjust.STEP) {
                    preview.setText(mode[0] == 0 ? "“" + o.title + "” has no time to give." : "“" + o.title + "” cannot take more time.");
                    return;
                }
                long newEnd = endMs + (mode[0] == 0 ? v : -v) * 60000L;
                int other = o.mins + (mode[0] == 0 ? -v : v);
                String t = "“" + cur + "” will end at " + clock(newEnd, h24) + " instead of " + clock(endMs, h24) + ". “" + o.title
                        + "” becomes " + other + " min.";
                if (i > 0) t += " The slots in between move " + v + " min " + (mode[0] == 0 ? "later" : "earlier") + ".";
                preview.setText(t);
            }
        };
        final Runnable relabel = () -> {
            for (int i = 0; i < ch.others.size(); i++) {
                SlotAdjust.Option o = ch.others.get(i);
                int most = mode[0] == 0 ? ch.maxGrow(o) : ch.maxShrink(o);
                list[0].side(i, most >= SlotAdjust.STEP ? "up to " + most + " min" : "none");
            }
            from.setText(Lang.tr(mode[0] == 0 ? "Take the time from" : "Give the time to").toUpperCase(java.util.Locale.ROOT));
        };
        list[0] = new Glass.Choices(a, i -> redraw.run());
        for (SlotAdjust.Option o : ch.others) {
            list[0].add(o.title, clock(minuteMs(saved, o.start), h24) + " – " + clock(minuteMs(saved, o.end), h24) + "  ·  " + o.mins + " min", "");
        }
        st[0] = new Glass.Stepper(a, 15, SlotAdjust.STEP, SlotAdjust.STEP, SlotAdjust.STEP, "min", v -> redraw.run());
        final Glass.Segments seg = new Glass.Segments(a, i -> {
            mode[0] = i;
            relabel.run();
            // keep the pick when it can still do something, otherwise the first slot that can
            int sel = list[0].selected();
            boolean ok = sel >= 0 && (i == 0 ? ch.maxGrow(ch.others.get(sel)) : ch.maxShrink(ch.others.get(sel))) >= SlotAdjust.STEP;
            if (!ok) {
                for (int k = 0; k < ch.others.size(); k++) {
                    if ((i == 0 ? ch.maxGrow(ch.others.get(k)) : ch.maxShrink(ch.others.get(k))) >= SlotAdjust.STEP) {
                        list[0].select(k, false);
                        break;
                    }
                }
            }
            redraw.run();
        }, "More time", "Less time");
        seg.enable(0, ch.canGrow());
        seg.enable(1, ch.canShrink());

        LinearLayout body = column(a);
        body.addView(seg.view);
        body.addView(from, Glass.wide(a, 20));
        body.addView(list[0].view, Glass.wide(a, 8));
        body.addView(st[0].view, Glass.wide(a, 18));
        body.addView(preview, Glass.wide(a, 12));
        apply.setOnClickListener(v -> {
            int i = list[0].selected();
            if (i < 0) return;
            SlotAdjust.Option o = ch.others.get(i);
            int delta = mode[0] == 0 ? st[0].value() : -st[0].value();
            if (SlotActions.adjustTime(a, o.index, delta)) {
                say(a, "“" + cur + "” now ends at " + clock(endMs + delta * 60000L, h24));
                sheet.dismiss();
                if (changed != null) changed.run();
            } else {
                say(a, "That cannot be done now");
            }
        });
        body.addView(apply, Glass.wide(a, 20));
        body.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 4));
        sheet.body.addView(body);
        // start on the first slot that can do something in the first mode
        seg.select(mode[0], false);
        for (int k = 0; k < ch.others.size(); k++) {
            SlotAdjust.Option o = ch.others.get(k);
            if ((mode[0] == 0 ? ch.maxGrow(o) : ch.maxShrink(o)) >= SlotAdjust.STEP) {
                list[0].select(k, false);
                break;
            }
        }
        if (list[0].selected() < 0) list[0].select(0, false);
        relabel.run();
        redraw.run();
        sheet.show();
    }

    private static long minuteMs(Plan.DayPlan p, int minute) {
        return p.startMs + (minute - p.sc.start) * 60000L;
    }

    // ------------------------------------------------------------------ finish

    /** The slot that is running is done before its time. */
    static void finish(final Activity a, final EarlyFinish.Offer o, final Runnable changed) {
        if (o.kind == EarlyFinish.SLEEP) {
            sleepNow(a, o, changed);
            return;
        }
        if (o.kind == EarlyFinish.WAKE) {
            wakeNow(a, o, changed);
            return;
        }
        final boolean h24 = Store.load(a).fmt24;
        final String cur = named(o.titleCur, Routine.TASK);
        final String next = o.titleNext.isEmpty() ? "the next slot" : o.titleNext;
        final FinishChoices fc = FinishChoices.of(o);
        final Glass.Sheet sheet = new Glass.Sheet(a, o.curIsSleep ? "Wake up early?" : "Finish “" + cur + "”?", null);
        final LinearLayout first = column(a);
        first.addView(Glass.note(a, "“" + cur + "” was to end at " + clock(o.plannedEndMs, h24) + ". “" + next + "” still ends at "
                + clock(o.nextEndMs, h24) + ", so it gets the time that is left."));
        if (fc.confirmOnly()) {
            first.addView(Glass.main(a, "Yes, finish", v -> {
                sheet.dismiss();
                EarlyDialog.run(a, o, SlotActions.EARLY_NOW, 0, changed);
            }), Glass.wide(a, 22));
            first.addView(Glass.quiet(a, "Not yet", v -> sheet.dismiss()), Glass.wide(a, 4));
        } else {
            first.addView(Glass.main(a, "Start “" + next + "”", v -> {
                sheet.dismiss();
                EarlyDialog.run(a, o, SlotActions.EARLY_NOW, 0, changed);
            }), Glass.wide(a, 22));
            if (fc.rest) {
                first.addView(Glass.glass(a, "Take a rest", v -> restStep(a, sheet, first, o, changed)), Glass.wide(a, 10));
            }
            if (fc.newActivity) {
                first.addView(Glass.glass(a, "New activity", v -> activityStep(a, sheet, first, o, changed)), Glass.wide(a, 10));
            }
            first.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 4));
        }
        sheet.body.addView(first);
        sheet.show();
    }

    /** "Take a rest": the time that was left is rested for a while, and then the next slot starts. */
    private static void restStep(final Activity a, final Glass.Sheet sheet, final View back, final EarlyFinish.Offer o, final Runnable changed) {
        final boolean h24 = Store.load(a).fmt24;
        final String next = o.titleNext.isEmpty() ? "the next slot" : o.titleNext;
        final int max = o.maxRestMin();
        final TextView caption = Glass.note(a, "");
        final Glass.Stepper st = new Glass.Stepper(a, Math.min(10, max), SlotAdjust.STEP, Math.max(SlotAdjust.STEP, max), SlotAdjust.STEP, "min", null);
        final Runnable draw = () -> {
            long at = Math.min(System.currentTimeMillis() + st.value() * 60000L, o.plannedEndMs - 60000L);
            caption.setText("Rest until " + clock(at, h24) + ". Then “" + next + "” starts and ends at " + clock(o.nextEndMs, h24) + ".");
        };
        st.onChange(v -> draw.run());
        LinearLayout box = column(a);
        box.addView(st.view);
        box.addView(caption, Glass.wide(a, 14));
        box.addView(Glass.main(a, "Start rest", v -> {
            sheet.dismiss();
            EarlyDialog.run(a, o, SlotActions.EARLY_BREAK, st.value(), changed);
        }), Glass.wide(a, 22));
        box.addView(Glass.quiet(a, "Back", v -> sheet.step("Finish “" + named(o.titleCur, Routine.TASK) + "”?", null, back)), Glass.wide(a, 4));
        draw.run();
        sheet.step("Take a rest", "You have " + o.roomMin + " min left in “" + named(o.titleCur, Routine.TASK) + "”.", box);
    }

    /** "New activity": something else begins now, in the time that was left and, if needed, in time taken from the next slot. */
    private static void activityStep(final Activity a, final Glass.Sheet sheet, final View back, final EarlyFinish.Offer o, final Runnable changed) {
        final Routine m = Store.load(a);
        final boolean h24 = m.fmt24;
        final String cur = named(o.titleCur, Routine.TASK);
        final String next = o.titleNext.isEmpty() ? "the next slot" : o.titleNext;
        final EditText name = Glass.field(a, "What will you do?", "");
        final Glass.TypePick type = new Glass.TypePick(a, m, Routine.TASK, Routine.OTHER);
        final int leftMax = FinishChoices.maxFromLeft(o);
        final Glass.Stepper fromLeft = new Glass.Stepper(a, Math.min(30, leftMax), 0, Math.max(SlotAdjust.STEP, leftMax), SlotAdjust.STEP, "min from the time left", null);
        final Glass.Stepper fromNext = new Glass.Stepper(a, 0, 0, Math.max(SlotAdjust.STEP, FinishChoices.maxFromNext(o, fromLeft.value())), SlotAdjust.STEP, "min taken from “" + next + "”", null);
        final TextView summary = Glass.note(a, "");
        final TextView warn = Glass.text(a, "", 13.5f, Glass.AMBER, true);
        final TextView go = Glass.main(a, "Start", null);

        final Runnable draw = () -> {
            fromNext.setMax(Math.max(0, FinishChoices.maxFromNext(o, fromLeft.value())));
            int total = fromLeft.value() + fromNext.value();
            long now = System.currentTimeMillis();
            long endsAt = now + total * 60000L;
            int nextLeft = o.nextMins + (o.roomMin - fromLeft.value()) - fromNext.value();
            summary.setText("It lasts " + total + " min, until " + clock(endsAt, h24) + ". Then “" + next + "” starts and ends at "
                    + clock(o.nextEndMs, h24) + " (" + Math.max(0, nextLeft) + " min).");
            int need = FinishChoices.minLength(type.kind());
            boolean enough = total >= need;
            warn.setText(enough ? "" : (type.kind() == Routine.TASK ? "A task needs at least " + need + " min." : "It needs at least " + need + " min."));
            warn.setVisibility(enough ? View.GONE : View.VISIBLE);
            Glass.enable(go, enough);
        };
        type.onChange(draw);
        fromLeft.onChange(v -> draw.run());
        fromNext.onChange(v -> draw.run());
        name.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                draw.run();
            }
        });

        LinearLayout box = column(a);
        box.addView(name);
        box.addView(type.view, Glass.wide(a, 18));
        box.addView(Glass.eyebrow(a, "How long", Glass.FAINT), Glass.wide(a, 22));
        box.addView(fromLeft.view, Glass.wide(a, 8));
        box.addView(fromNext.view, Glass.wide(a, 10));
        box.addView(summary, Glass.wide(a, 14));
        box.addView(warn, Glass.wide(a, 6));
        go.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                say(a, "Type a name first");
                return;
            }
            if (SlotActions.finishWithActivity(a, n, type.kind(), type.sub(), fromLeft.value(), fromNext.value())) {
                say(a, "“" + n + "” has started");
                sheet.dismiss();
                if (changed != null) changed.run();
            } else {
                say(a, "That cannot be done now");
            }
        });
        box.addView(go, Glass.wide(a, 18));
        box.addView(Glass.quiet(a, "Back", v -> sheet.step("Finish “" + cur + "”?", null, back)), Glass.wide(a, 4));
        sheet.step("New activity", "“" + cur + "” ends now.", box);
        draw.run();
    }

    private static void sleepNow(final Activity a, final EarlyFinish.Offer o, final Runnable changed) {
        final boolean h24 = Store.load(a).fmt24;
        final String cur = named(o.titleCur, Routine.TASK);
        final Glass.Sheet sheet = new Glass.Sheet(a, "Go to sleep early?", "Sleep was to begin at " + clock(o.plannedEndMs, h24) + ". If you go to sleep now, “"
                + cur + "” ends here and your wake-up alarm stays as planned.");
        LinearLayout box = column(a);
        box.addView(Glass.main(a, "I am going to sleep", v -> {
            sheet.dismiss();
            try {
                a.startActivity(AlarmActivity.earlySleepIntent(a, o.day, o.srcNext, o.titleNext, o.plannedEndMs, o.nextEndMs));
            } catch (RuntimeException ex) {
                say(a, "That cannot be done now");
            }
            if (changed != null) changed.run();
        }));
        box.addView(Glass.quiet(a, "Not yet", v -> sheet.dismiss()), Glass.wide(a, 6));
        sheet.body.addView(box);
        sheet.show();
    }

    private static void wakeNow(final Activity a, final EarlyFinish.Offer o, final Runnable changed) {
        final boolean h24 = Store.load(a).fmt24;
        final String next = o.titleNext.isEmpty() ? "the first slot" : o.titleNext;
        final Glass.Sheet sheet = new Glass.Sheet(a, "Wake up early?", "Your day was to start at " + clock(o.plannedEndMs, h24) + ". Start it now, and “" + next
                + "” begins early. It still ends at " + clock(o.nextEndMs, h24) + ", so it gets the time that was left.");
        LinearLayout box = column(a);
        box.addView(Glass.main(a, "Start my day now", v -> {
            sheet.dismiss();
            if (SlotActions.earlyWake(a) < 0) say(a, "That cannot be done now");
            else say(a, "Your day has started.");
            if (a instanceof MainActivity) ((MainActivity) a).previewChanged();
            if (changed != null) changed.run();
        }));
        box.addView(Glass.quiet(a, "Not yet", v -> sheet.dismiss()), Glass.wide(a, 6));
        sheet.body.addView(box);
        sheet.show();
    }

    // ------------------------------------------------------------------ take a break

    /** How long a break? Round buttons in steps of five minutes, or until the slot ends; never past the end of the slot. */
    static void takeBreak(final Activity a, Plan.DayPlan p, final Runnable changed) {
        final long now = System.currentTimeMillis();
        final int room = SlotPlay.roomMinutes(now, p.slotEndMs(p.cur));
        if (room < 1) {
            say(a, "This slot is almost over");
            return;
        }
        final long slotEnd = p.slotEndMs(p.cur);
        final boolean h24 = Store.load(a).fmt24;
        final boolean canStep = room >= SlotPlay.STEP;
        final boolean[] untilEnd = {!canStep};   // a slot with under 5 minutes left can only be rested to its end
        final Glass.Sheet sheet = new Glass.Sheet(a, "Take a break", "The slot goes on after it and keeps its end.");
        final Glass.Stepper st = new Glass.Stepper(a, 10, SlotPlay.STEP, Math.max(room, SlotPlay.STEP), SlotPlay.STEP, "min", null);
        final TextView caption = Glass.note(a, "");
        final Runnable draw = () -> {
            long end = untilEnd[0] ? slotEnd : SlotPlay.breakEnd(System.currentTimeMillis(), st.value(), slotEnd);
            caption.setText("Back at " + clock(end, h24) + (untilEnd[0] ? ", when this slot ends." : "."));
        };
        LinearLayout box = column(a);
        if (canStep) box.addView(st.view);
        box.addView(caption, Glass.wide(a, 14));
        LinearLayout sw = Glass.switchRow(a, "Until this slot ends", untilEnd[0], on -> {
            untilEnd[0] = on || !canStep;
            st.setActive(!untilEnd[0]);
            draw.run();
        });
        if (!canStep) sw.setEnabled(false);
        box.addView(sw, Glass.wide(a, 10));
        st.setActive(!untilEnd[0]);
        st.onChange(v -> draw.run());
        draw.run();
        box.addView(Glass.main(a, "Start the break", v -> {
            // "until the slot ends" asks for a minute more than is left; the break is cut at the end of the slot
            long end = SlotActions.takeBreak(a, untilEnd[0] ? room + 1 : st.value());
            if (end < 0) {
                say(a, "A break cannot be taken now");
                return;
            }
            say(a, "Enjoy your break. Back at " + clock(end, h24));
            sheet.dismiss();
            if (changed != null) changed.run();
        }), Glass.wide(a, 20));
        box.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 4));
        sheet.body.addView(box);
        sheet.show();
    }

    // ------------------------------------------------------------------ continue the slot before

    /**
     * "Continue": the slot before goes on until a time chosen with the round buttons (or for the whole slot), and then the slot that is
     * starting begins. {@code room} is the most minutes that fit; {@code go} gets the minutes.
     */
    static void continueSheet(final Activity a, String prevTitle, final String nextTitle, int initial, final int room, final java.util.function.IntConsumer go) {
        if (room < 1) {
            say(a, "This slot is almost over");
            return;
        }
        final boolean h24 = Store.load(a).fmt24;
        final String prev = prevTitle == null || prevTitle.isEmpty() ? "The slot before" : prevTitle;
        final String next = nextTitle == null || nextTitle.isEmpty() ? "the next slot" : nextTitle;
        final Glass.Sheet sheet = new Glass.Sheet(a, "Continue “" + prev + "”", "Keep it going until a time you choose. Then “" + next + "” starts.");
        final boolean canStep = room >= SlotPlay.STEP;
        final boolean[] whole = {!canStep};
        final Glass.Stepper st = new Glass.Stepper(a, Math.max(SlotPlay.STEP, initial), SlotPlay.STEP, Math.max(room, SlotPlay.STEP), SlotPlay.STEP, "", null);
        final TextView caption = Glass.note(a, "");
        st.words(m -> clock(System.currentTimeMillis() + m * 60000L, h24));
        final Runnable draw = () -> {
            int m = whole[0] ? room : st.value();
            long at = System.currentTimeMillis() + m * 60000L;
            st.setUnit(whole[0] ? "whole slot" : m + " min from now");
            caption.setText("“" + prev + "” goes on until " + clock(at, h24) + ". Then “" + next + "” starts.");
        };
        st.onChange(v -> draw.run());
        LinearLayout box = column(a);
        if (canStep) box.addView(st.view);
        box.addView(caption, Glass.wide(a, 14));
        LinearLayout sw = Glass.switchRow(a, "Whole slot", whole[0], on -> {
            whole[0] = on || !canStep;
            st.setActive(!whole[0]);
            draw.run();
        });
        if (!canStep) sw.setEnabled(false);
        box.addView(sw, Glass.wide(a, 10));
        st.setActive(!whole[0]);
        draw.run();
        box.addView(Glass.main(a, "Keep going", v -> {
            sheet.dismiss();
            go.accept(whole[0] ? room : st.value());
        }), Glass.wide(a, 20));
        box.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 4));
        sheet.body.addView(box);
        sheet.show();
    }

    /** "Rest first": the slot starts after a rest of the minutes chosen with the round buttons; the alarm asks again then. */
    static void restFirstSheet(final Activity a, final String slotTitle, int initial, int room, final java.util.function.IntConsumer go) {
        if (room < SlotPlay.STEP) {
            say(a, "This slot is almost over");
            return;
        }
        final boolean h24 = Store.load(a).fmt24;
        final String title = slotTitle == null || slotTitle.isEmpty() ? "The slot" : slotTitle;
        final Glass.Sheet sheet = new Glass.Sheet(a, "Rest first", "Rest for a while, then “" + title + "” starts. I will ask again then.");
        final Glass.Stepper st = new Glass.Stepper(a, initial, SlotPlay.STEP, room, SlotPlay.STEP, "min", null);
        final TextView caption = Glass.note(a, "");
        final Runnable draw = () -> caption.setText("Rest until " + clock(System.currentTimeMillis() + st.value() * 60000L, h24) + ".");
        st.onChange(v -> draw.run());
        draw.run();
        LinearLayout box = column(a);
        box.addView(st.view);
        box.addView(caption, Glass.wide(a, 14));
        box.addView(Glass.main(a, "Start rest", v -> {
            sheet.dismiss();
            go.accept(st.value());
        }), Glass.wide(a, 20));
        box.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 4));
        sheet.body.addView(box);
        sheet.show();
    }

    /**
     * "Start it now" for a slot whose alarm was not answered: how long it runs now (its planned length, or less, or more up to what the
     * slot that is starting leaves), and what happens after: that slot carries on for the rest of its time.
     */
    static void missedSheet(final Activity a, final String missedTitle, final String currentTitle, final long currentEndMs,
                            final int plannedMin, int initial, int room, final java.util.function.IntConsumer go) {
        if (room < SlotPlay.STEP) {
            say(a, "There is no room for it before the next slot");
            return;
        }
        final boolean h24 = Store.load(a).fmt24;
        final String mt = missedTitle == null || missedTitle.isEmpty() ? "The slot" : missedTitle;
        final String ct = currentTitle == null || currentTitle.isEmpty() ? "the next slot" : currentTitle;
        final Glass.Sheet sheet = new Glass.Sheet(a, "Start “" + mt + "” now", "You did not answer its alarm. Start it now for as long as you choose; “" + ct
                + "” carries on after it, and the rest of your day stays as it is.");
        final Glass.Stepper st = new Glass.Stepper(a, Math.min(Math.max(initial, SlotPlay.STEP), room), SlotPlay.STEP, room, SlotPlay.STEP, "min", null);
        final TextView caption = Glass.note(a, "");
        final Runnable draw = () -> {
            long end = System.currentTimeMillis() + st.value() * 60000L;
            caption.setText("“" + mt + "” until " + clock(end, h24) + ", then “" + ct + "” until " + clock(currentEndMs, h24) + ".");
        };
        st.onChange(v -> draw.run());
        draw.run();
        LinearLayout box = column(a);
        box.addView(st.view);
        if (plannedMin >= SlotPlay.STEP && plannedMin <= room && plannedMin != st.value()) {
            box.addView(Glass.chip(a, "Its full length: " + Routine.formatDuration(plannedMin), v -> {
                st.setValue(plannedMin);
                draw.run();
            }), Glass.wide(a, 12));
        }
        box.addView(caption, Glass.wide(a, 14));
        box.addView(Glass.main(a, "Start it now", v -> {
            sheet.dismiss();
            go.accept(st.value());
        }), Glass.wide(a, 20));
        box.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 4));
        sheet.body.addView(box);
        sheet.show();
    }

    // ------------------------------------------------------------------ change plan

    /** What the caller does with the answers of "Change plan" (the Home screen and the alarm screen record them differently). */
    interface ChangeHost {
        /** The name and type are as they were. */
        void unchanged();

        /** The slot is called {@code name} today, for its first {@code mins} minutes (0 for all of it), with this type and sub type. */
        void replace(String name, int mins, int kind, String sub);

        /** The slot before goes on into this one for {@code minutes}. */
        void continuePrevious(SlotActions.Prev prev, int minutes);

        /** This slot trades with {@code other}: places and times, or names (all the time, or the first {@code mins} minutes). */
        void swap(Routine.Block other, boolean places, boolean whole, int mins);

        /** Takes the slot off for today; null when that is not offered here. */
        Runnable takeOff();
    }

    /**
     * "Change plan": replace the slot for today, carry the slot before on into it, or swap it with another one. {@code allowPrev} says
     * whether the slot before can be carried on (not when this is a start that was put off).
     */
    static void changePlan(final Activity a, final String day, final int src, final String title, boolean allowPrev, final ChangeHost host) {
        final Routine base = Store.load(a);
        final Overrides ov = Store.overrides(a, base);
        final LocalDate d = LocalDate.parse(day);
        Plan.DayPlan dp = Plan.plan(base, ov, d, Store.zone());
        int mi = SlotPlay.indexOfSrc(dp, src);
        // the slot as a whole, first part to last (a slot changed earlier today is several parts with one number)
        long[] span = ReplaceFromNow.span(dp, src);
        final int slotMins = span != null ? Math.max(SlotPlay.STEP, (int) Math.round((span[1] - span[0]) / 60000.0))
                : mi < 0 ? 60 : Math.max(SlotPlay.STEP, dp.sc.slots.get(mi).end - dp.sc.slots.get(mi).start);
        final int part = Math.max(SlotPlay.STEP, slotMins - SlotPlay.STEP);
        // A slot that is running already: the new plan begins now, so its length is counted from now and what is left of the slot is the
        // most it can have (see ReplaceFromNow).
        final ReplaceFromNow rf = span == null ? null : ReplaceFromNow.of(span[0], span[1], System.currentTimeMillis());
        final boolean late = rf != null && rf.late();
        final int roomNow = late ? rf.room : slotMins;
        final int partNow = Math.max(SlotPlay.STEP, roomNow - SlotPlay.STEP);
        int pi = mi < 0 || !allowPrev ? -1 : SlotPlay.previousIndex(dp, mi);
        final SlotActions.Prev prev = pi < 0 ? null : new SlotActions.Prev(dp.sc.slots.get(pi).block.src, dp.sc.slots.get(pi).block.title);
        final List<Routine.Block> others = new ArrayList<>();
        for (Routine.Slot sl : dp.sc.slots) {
            Routine.Block ob = sl.block;
            if (ob.src < 0 || ob.src == src || ob.skipped || ob.kind == Routine.SLEEP) continue;
            boolean seen = false;
            for (Routine.Block x : others) if (x.src == ob.src) seen = true;
            if (seen) continue;
            Routine.Block c = ob.copy();
            c.src = ob.src;
            c.mins = sl.end - sl.start;
            others.add(c);
        }
        int ck = Routine.TASK;
        String cs = Routine.OTHER;
        try {
            Routine.Block cb = base.forDay(d).blocks.get(src);
            ck = cb.kind;
            cs = cb.sub;
        } catch (RuntimeException ignored) {
            // the slot keeps the defaults
        }
        final int baseKind = ck;
        final String baseSub = cs;

        final Glass.Sheet sheet = new Glass.Sheet(a, "Change plan", "This changes today only. The rest of the slot goes back to your saved plan.");
        final EditText name = Glass.field(a, "What will you do instead?", title);
        final Glass.TypePick type = new Glass.TypePick(a, base, ck, cs);
        final LinearLayout[] panel = new LinearLayout[3];
        final Glass.Stepper[] steps = new Glass.Stepper[3];
        final boolean[] whole = {true, true, true};
        final LinearLayout[] wholeRow = new LinearLayout[3];
        final Glass.Choices target = new Glass.Choices(a, null);
        for (Routine.Block ob : others) target.add(named(ob.title, ob.kind), ob.mins + " min", "");
        if (!others.isEmpty()) target.select(0, false);
        final boolean[] places = {false};
        // swapping places moves the whole slot, so there is no length to choose
        final Glass.Segments swapMode = new Glass.Segments(a, i -> {
            places[0] = i == 1;
            if (wholeRow[2] != null) wholeRow[2].setVisibility(places[0] ? View.GONE : View.VISIBLE);
            if (steps[2] != null) steps[2].view.setVisibility(places[0] || whole[2] ? View.GONE : View.VISIBLE);
        }, "Swap names", "Swap places");
        swapMode.select(0, false);

        final List<String> modes = new ArrayList<>();
        final List<Integer> modeAt = new ArrayList<>();
        modes.add("Replace");
        modeAt.add(0);
        if (prev != null) {
            modes.add("Continue");
            modeAt.add(1);
        }
        if (!others.isEmpty()) {
            modes.add("Swap");
            modeAt.add(2);
        }
        final int[] picked = {0};
        LinearLayout body = column(a);
        final Glass.Segments seg = new Glass.Segments(a, i -> {
            picked[0] = modeAt.get(i);
            for (int k = 0; k < 3; k++) if (panel[k] != null) panel[k].setVisibility(k == picked[0] ? View.VISIBLE : View.GONE);
        }, modes.toArray(new String[0]));
        if (modes.size() > 1) body.addView(seg.view);

        for (int k = 0; k < 3; k++) {
            if (k == 1 && prev == null) continue;
            if (k == 2 && others.isEmpty()) continue;
            final int idx = k;
            LinearLayout p = column(a);
            p.setVisibility(View.GONE);
            if (k == 0) {
                p.addView(name);
                p.addView(type.view, Glass.wide(a, 18));
                if (late) {
                    java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(rf.startMs).atZone(Store.zone());
                    p.addView(Glass.note(a, "Starts now, at " + Routine.formatTime(z.getHour() * 60 + z.getMinute(), base.fmt24)
                            + ". The time before stays as it was."), Glass.wide(a, 14));
                }
            } else if (k == 1) {
                p.addView(Glass.note(a, "“" + prev.title + "” goes on into this slot."));
            } else {
                p.addView(Glass.eyebrow(a, "Swap with which slot?", Glass.FAINT));
                p.addView(target.view, Glass.wide(a, 8));
                p.addView(swapMode.view, Glass.wide(a, 12));
            }
            final int most = idx == 0 ? partNow : part;
            final Glass.Stepper st = new Glass.Stepper(a, Math.min(30, most), SlotPlay.STEP, most, SlotPlay.STEP, "min", null);
            steps[k] = st;
            st.view.setVisibility(View.GONE);
            LinearLayout wr = Glass.switchRow(a, idx == 0 && late ? "Rest of the slot (" + roomNow + " min)" : "Whole slot (" + slotMins + " min)", true, on -> {
                whole[idx] = on;
                st.view.setVisibility(on ? View.GONE : View.VISIBLE);
            });
            wholeRow[k] = wr;
            p.addView(wr, Glass.wide(a, 14));
            p.addView(st.view, Glass.wide(a, 8));
            panel[k] = p;
            body.addView(p, Glass.wide(a, 18));
        }
        panel[0].setVisibility(View.VISIBLE);
        seg.select(0, false);

        body.addView(Glass.main(a, "Save for today", v -> {
            int pick = picked[0];
            boolean full = whole[pick];
            int mins = steps[pick].value();
            if (pick == 0) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                    say(a, "Type a name first");
                    return;
                }
                sheet.dismiss();
                if (n.equals(title) && type.kind() == baseKind && type.sub().equalsIgnoreCase(baseSub)) host.unchanged();
                else host.replace(n, full ? 0 : mins, type.kind(), type.sub());
            } else if (pick == 1) {
                sheet.dismiss();
                host.continuePrevious(prev, full ? slotMins : mins);
            } else {
                int t = target.selected();
                if (t < 0 || t >= others.size()) return;
                sheet.dismiss();
                host.swap(others.get(t), places[0], full || places[0], mins);
            }
        }), Glass.wide(a, 22));
        final Runnable off = host.takeOff();
        if (off != null) body.addView(Glass.glass(a, "Take it off for today", v -> {
            sheet.dismiss();
            off.run();
        }), Glass.wide(a, 10));
        body.addView(Glass.quiet(a, "Cancel", v -> sheet.dismiss()), Glass.wide(a, 4));
        sheet.body.addView(body);
        sheet.show();
    }
}
