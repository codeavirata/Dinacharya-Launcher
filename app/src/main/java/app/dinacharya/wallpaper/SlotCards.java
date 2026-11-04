package app.dinacharya.wallpaper;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * The slot-by-slot analysis of a day on the Performance screen: the day at a glance (focus, time devoted, other phone use, longest focus),
 * then the slots, or the activities, as dropdowns of which only one is open at a time. A slot opens to show its time and phone figures, a
 * timeline with the other phone use marked, the stretches with their exact times and the apps it used. The numbers come from
 * {@link SlotReport}.
 */
final class SlotCards {

    private SlotCards() {
    }

    // ------------------------------------------------------------------ colours

    static int toneInk(int tone) {
        switch (tone) {
            case SlotReport.TONE_GOOD: return Ui.TEAL_DARK;
            case SlotReport.TONE_FAIR: return Ui.AMBER;
            case SlotReport.TONE_LOW: return Ui.RED;
            default: return Ui.MUTED;
        }
    }

    static int toneFill(int tone) {
        switch (tone) {
            case SlotReport.TONE_GOOD: return Ui.TONAL;
            case SlotReport.TONE_FAIR: return Ui.AMBER_SOFT;
            case SlotReport.TONE_LOW: return (Ui.RED & 0x00FFFFFF) | 0x33000000;
            default: return Ui.SOFT;
        }
    }

    private static int kindColor(int kind) {
        return ReportRenderer.KIND[Math.max(0, Math.min(ReportRenderer.KIND.length - 1, kind))];
    }

    // ------------------------------------------------------------------ the card

    /**
     * What the free version shows in place of the analysis: a short card that says what it holds, with "See Pro" and the way to watch
     * the videos. {@code unlocked} runs after the videos have opened it, so the page can be drawn again.
     */
    static LinearLayout locked(final android.app.Activity a, final Runnable unlocked) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 18));
        card.setBackground(Ui.box(a, Ui.SURFACE, 0, 22));
        card.addView(Ui.eyebrow(a, "Slot analysis · Pro"));
        card.addView(Ui.label(a, "How each slot went", 20, Ui.INK, true), Ui.wide(a, 4));
        card.addView(note(a, "Focus and phone use slot by slot, with a timeline and the exact times, and the same by activity. "
                + "Today's report above stays free."), Ui.wide(a, 8));
        card.addView(Ui.button(a, "See Pro", true, v -> ProPrompt.open(a)), Ui.wide(a, 12));
        ProPrompt.addOffer(a, card, 8, unlocked);
        return card;
    }

    /** The whole analysis as one card, ready to be put on the page. */
    static LinearLayout build(final Context c, final DayReport r, final boolean h24) {
        final List<SlotReport.Item> slots = SlotReport.slots(r);
        final List<SlotReport.Item> acts = SlotReport.activities(r);
        final SlotReport.Item day = SlotReport.day(r);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 18));
        card.setBackground(Ui.box(c, Ui.SURFACE, 0, 22));
        card.setLayoutTransition(new LayoutTransition());
        card.addView(Ui.eyebrow(c, "Slot analysis"));
        card.addView(Ui.label(c, "How each slot went", 20, Ui.INK, true), Ui.wide(c, 4));

        if (slots.isEmpty()) {
            card.addView(note(c, "No slot has run yet, so there is nothing to look at."), Ui.wide(c, 8));
            return card;
        }
        if (!r.phoneKnown) {
            card.addView(note(c, "Phone use was not available. Allow usage access in the app to include it."), Ui.wide(c, 8));
        } else {
            card.addView(note(c, "Focus is the share of a slot's time with no other phone use. Apps chosen for an activity do not count against it."),
                    Ui.wide(c, 8));
        }

        // the day at a glance
        LinearLayout glance = new LinearLayout(c);
        glance.setOrientation(LinearLayout.VERTICAL);
        glance.addView(pair(c, tile(c, "Focus", day.focusPct() < 0 ? "–" : day.focusPct() + "%", day.focusPct() < 0 ? Ui.INK : toneInk(day.tone())),
                tile(c, "Time devoted", day.known ? SlotReport.dur(day.devotedMs()) : "–", Ui.INK)), Ui.wide(c, 0));
        glance.addView(pair(c, tile(c, "Other phone use", day.known ? SlotReport.dur(day.offMs()) : "–", Ui.INK),
                tile(c, "Longest focus", day.known && day.longestFocusMs >= 0 ? SlotReport.dur(day.longestFocusMs) : "–", Ui.INK)), Ui.wide(c, 8));
        card.addView(glance, Ui.wide(c, 14));

        // slots or activities
        final TextView[] seg = new TextView[2];
        LinearLayout switcher = new LinearLayout(c);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        switcher.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
        switcher.setBackground(Ui.box(c, Ui.SOFT, 0, 14));
        final LinearLayout holder = new LinearLayout(c);
        holder.setOrientation(LinearLayout.VERTICAL);   // no layout transition here: switching list would fade the old rows out over the new ones
        final Runnable[] show = new Runnable[1];
        final int[] which = {0};
        String[] names = {"By slot", "By activity"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView t = Ui.label(c, names[i], 14, Ui.INK, true);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Ui.dp(c, 48));
            t.setClickable(true);
            t.setFocusable(true);
            t.setOnClickListener(v -> {
                if (which[0] == idx) return;
                which[0] = idx;
                show[0].run();
            });
            switcher.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            seg[i] = t;
        }
        card.addView(switcher, Ui.wide(c, 16));
        card.addView(holder, Ui.wide(c, 10));

        show[0] = () -> {
            for (int i = 0; i < 2; i++) {
                boolean on = i == which[0];
                seg[i].setTextColor(on ? 0xFFFFFFFF : Ui.INK);
                seg[i].setBackground(on ? Ui.box(c, Ui.TEAL, 0, 11) : null);
                seg[i].setContentDescription(Lang.tr(i == 0 ? "By slot" : "By activity") + ", " + Lang.tr(on ? "selected" : "not selected"));
            }
            holder.removeAllViews();
            Accordion acc = new Accordion();
            if (which[0] == 0) {
                for (SlotReport.Item s : slots) holder.addView(slotDrop(c, acc, s, h24), Ui.wide(c, 8));
            } else if (acts.isEmpty()) {
                holder.addView(note(c, "No activity has run yet."), Ui.wide(c, 4));
            } else {
                for (SlotReport.Item a : acts) holder.addView(activityDrop(c, acc, a), Ui.wide(c, 8));
            }
        };
        show[0].run();

        int more = SlotReport.others(r);
        if (more > 0) {
            card.addView(note(c, more == 1 ? "1 other slot has not started or was taken off." : more + " other slots have not started or were taken off."),
                    Ui.wide(c, 12));
        }
        return card;
    }

    // ------------------------------------------------------------------ one slot, one activity

    private static LinearLayout slotDrop(Context c, Accordion acc, SlotReport.Item s, boolean h24) {
        int pct = s.focusPct();
        Drop d = acc.add(new Drop(c, kindColor(s.kind), clock(s.startMs, h24) + " – " + clock(s.endMs, h24), s.title, s.summary(),
                pct < 0 ? "–" : pct + "%", "focus", toneInk(s.tone()), toneFill(s.tone()), pct < 0 ? "" : pct + "% focus"));
        fillSlot(c, d.body, s, h24);
        return d.root;
    }

    private static LinearLayout activityDrop(Context c, Accordion acc, SlotReport.Item a) {
        int pct = a.focusPct();
        String summary = a.known ? "Devoted " + SlotReport.dur(a.devotedMs()) + " of " + SlotReport.dur(a.workedMs) : "Phone use was not available";
        Drop d = acc.add(new Drop(c, kindColor(a.kind), Routine.KIND_NAMES[Math.max(0, Math.min(Routine.KIND_NAMES.length - 1, a.kind))],
                a.title, summary, pct < 0 ? "–" : pct + "%", "focus", toneInk(a.tone()), toneFill(a.tone()), pct < 0 ? "" : pct + "% focus"));
        fillActivity(c, d.body, a);
        return d.root;
    }

    private static void fillSlot(Context c, LinearLayout body, SlotReport.Item s, boolean h24) {
        figures(c, body, s, "In the slot");
        if (!s.known) return;
        // the timeline
        body.addView(heading(c, "Timeline"), Ui.wide(c, 16));
        List<float[]> marks = SlotReport.marks(s);
        body.addView(new Marks(c, marks), Ui.lp(c, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 16), 8));
        LinearLayout ends = new LinearLayout(c);
        ends.setOrientation(LinearLayout.HORIZONTAL);
        TextView a = Ui.label(c, clock(SlotReport.spanStartMs(s), h24), 12, Ui.MUTED, false);
        TextView b = Ui.label(c, clock(SlotReport.spanEndMs(s), h24), 12, Ui.MUTED, false);
        b.setGravity(Gravity.END);
        ends.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ends.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(ends, Ui.wide(c, 4));
        if (s.offMs() <= 0 && s.useCount == 0) {
            body.addView(note(c, "No other phone use in this slot."), Ui.wide(c, 6));
        } else {
            body.addView(note(c, "Red marks show other phone use."), Ui.wide(c, 6));
        }
        // the stretches, with their exact times
        if (!s.uses.isEmpty()) {
            body.addView(heading(c, "Other phone use, with the exact times"), Ui.wide(c, 16));
            for (SlotUse.Episode e : s.uses) {
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Ui.dp(c, 32));
                row.addView(Ui.label(c, clock(e.startMs, h24) + " – " + clock(e.endMs, h24), 13, Ui.INK, true));
                TextView app = Ui.label(c, e.app.isEmpty() ? "–" : e.app, 13, Ui.INK, false);
                app.setSingleLine(true);
                app.setEllipsize(TextUtils.TruncateAt.END);
                Lang.keep(app);   // the name of an app stays as the phone gives it
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ap.leftMargin = Ui.dp(c, 10);
                ap.rightMargin = Ui.dp(c, 8);
                row.addView(app, ap);
                row.addView(Ui.label(c, SlotReport.dur(e.ms()), 13, Ui.MUTED, false));
                body.addView(row, Ui.wide(c, 2));
            }
            if (s.timesCut()) body.addView(note(c, "Showing the latest " + s.uses.size() + " of " + s.useCount + "."), Ui.wide(c, 6));
        } else if (s.offMs() > 0) {
            body.addView(note(c, "The exact times were not saved for this day."), Ui.wide(c, 12));
        }
        apps(c, body, s.apps, "Apps used in this slot");
    }

    private static void fillActivity(Context c, LinearLayout body, SlotReport.Item a) {
        figures(c, body, a, "Time in slots");
        if (!a.known) return;
        body.addView(pair(c, tile(c, "Slots", String.valueOf(a.slots), Ui.INK), tile(c, "Interruptions", String.valueOf(a.useCount), Ui.INK)), Ui.wide(c, 8));
        apps(c, body, a.apps, "Apps used in these slots");
    }

    /** The figures of a slot or an activity as small tiles. */
    private static void figures(Context c, LinearLayout body, SlotReport.Item s, String worked) {
        int pct = s.focusPct();
        body.addView(pair(c, tile(c, worked, SlotReport.dur(s.workedMs), Ui.INK),
                tile(c, "Time devoted", s.known ? SlotReport.dur(s.devotedMs()) : "–", Ui.INK)), Ui.wide(c, 8));
        body.addView(pair(c, tile(c, "Focus", pct < 0 ? "–" : pct + "%", pct < 0 ? Ui.INK : toneInk(s.tone())),
                tile(c, "Longest focus", s.hasTimes() ? SlotReport.dur(s.longestFocusMs) : "–", Ui.INK)), Ui.wide(c, 8));
        if (!s.known) {
            body.addView(note(c, "Phone use was not available. Allow usage access in the app to include it."), Ui.wide(c, 10));
            return;
        }
        body.addView(pair(c, tile(c, "Phone in total", SlotReport.dur(s.phoneMs), Ui.INK),
                tile(c, "Other phone use", SlotReport.dur(s.offMs()), s.offMs() > 0 ? Ui.RED : Ui.INK)), Ui.wide(c, 8));
        if (s.partMs > 0) {
            body.addView(note(c, SlotReport.dur(s.partMs) + " of the phone time was in apps chosen for this activity."), Ui.wide(c, 8));
        }
    }

    private static void apps(Context c, LinearLayout body, List<DayReport.App> apps, String title) {
        if (apps.isEmpty()) return;
        body.addView(heading(c, title), Ui.wide(c, 16));
        long max = Math.max(1, apps.get(0).ms);
        int shown = Math.min(SlotReport.MAX_APPS, apps.size());
        for (int i = 0; i < shown; i++) {
            DayReport.App ap = apps.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Ui.label(c, ap.name, 13, Ui.INK, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            Lang.keep(name);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, Ui.dp(c, 8), 1f);
            bp.leftMargin = Ui.dp(c, 8);
            bp.rightMargin = Ui.dp(c, 8);
            row.addView(new Bar(c, ap.ms / (float) max, i == 0 ? Ui.TEAL : (Ui.TEAL & 0x00FFFFFF) | 0xB0000000), bp);
            TextView t = Ui.label(c, SlotReport.dur(ap.ms), 13, Ui.MUTED, false);
            t.setGravity(Gravity.END);
            t.setMinWidth(Ui.dp(c, 52));
            row.addView(t);
            row.setMinimumHeight(Ui.dp(c, 30));
            body.addView(row, Ui.wide(c, 2));
        }
        if (apps.size() > shown) body.addView(note(c, "and " + (apps.size() - shown) + " more"), Ui.wide(c, 4));
    }

    // ------------------------------------------------------------------ small pieces

    private static String clock(long ms, boolean h24) {
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
    }

    private static TextView note(Context c, String text) {
        TextView t = Ui.label(c, text, 13, Ui.MUTED, false);
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        return t;
    }

    private static TextView heading(Context c, String text) {
        return Ui.label(c, text, 14, Ui.TEAL_DARK, true);
    }

    private static LinearLayout tile(Context c, String label, String value, int ink) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        t.setBackground(Ui.box(c, Ui.SOFT, 0, 12));
        t.addView(Ui.label(c, label, 12, Ui.MUTED, false));
        t.addView(Ui.label(c, value, 20, ink, true), Ui.wide(c, 2));
        return t;
    }

    /** Two tiles side by side, of the same width. */
    private static LinearLayout pair(Context c, View left, View right) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        a.rightMargin = Ui.dp(c, 4);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        b.leftMargin = Ui.dp(c, 4);
        row.addView(left, a);
        row.addView(right, b);
        return row;
    }

    /** A thin bar filled to a share of its width. */
    private static final class Bar extends View {
        private final float share;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        Bar(Context c, float share, int color) {
            super(c);
            this.share = Math.max(0f, Math.min(1f, share));
            this.color = color;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float w = getWidth();
            paint.setColor(Ui.SOFT);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, h / 2, h / 2, paint);
            paint.setColor(color);
            rect.set(0, 0, Math.max(h, w * share), h);
            canvas.drawRoundRect(rect, h / 2, h / 2, paint);
        }
    }

    /** The timeline of a slot: a bar for the whole slot with the other phone use marked in red. */
    private static final class Marks extends View {
        private final List<float[]> marks;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        Marks(Context c, List<float[]> marks) {
            super(c);
            this.marks = marks;
            setContentDescription(marks.isEmpty() ? "Timeline of the slot: no other phone use"
                    : marks.size() == 1 ? "Timeline of the slot: 1 stretch of other phone use"
                    : "Timeline of the slot: " + marks.size() + " stretches of other phone use");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float w = getWidth();
            paint.setColor(Ui.TONAL);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, h / 2, h / 2, paint);
            canvas.save();
            canvas.clipRect(0, 0, w, h);
            paint.setColor(Ui.RED);
            float min = Ui.dp(getContext(), 3);
            for (float[] m : marks) {
                float x0 = w * m[0];
                float x1 = Math.max(x0 + min, w * m[1]);
                rect.set(x0, 0, Math.min(w, x1), h);
                canvas.drawRoundRect(rect, 3, 3, paint);
            }
            canvas.restore();
        }
    }
}
