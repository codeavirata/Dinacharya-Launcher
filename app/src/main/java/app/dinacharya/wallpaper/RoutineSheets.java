package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The parts of the Routine tab that start a day or send one out: the two round buttons at the top (Templates and Share), the
 * "Exam Specific" tiles for a person who has no schedule of their own yet, the Share sheet and the Templates sheet. Every day is
 * shown as a bar in the colours of the five kinds of slot, so the shape of a day can be compared at a glance. The rules for what is shown
 * when are in {@link FirstDay}; this class only draws.
 */
final class RoutineSheets {
    private RoutineSheets() {
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ------------------------------------------------------------------ drawn parts

    /** A small line icon, drawn on a 24 by 24 grid. */
    static final class Icon extends View {
        static final int GRID = 0, SHARE = 1, SEND = 2, PICTURE = 3, PASTE = 4;
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        Icon(Context c, int kind, int color) {
            super(c);
            this.kind = kind;
            p.setStyle(Paint.Style.STROKE);
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private void box(Canvas cv, float u, float l, float t, float r, float b, float rad) {
            rect.set(l * u, t * u, r * u, b * u);
            cv.drawRoundRect(rect, rad * u, rad * u, p);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float u = Math.min(getWidth(), getHeight()) / 24f;
            p.setStrokeWidth(1.8f * u);
            path.reset();
            switch (kind) {
                case GRID:
                    box(cv, u, 3, 3, 10, 10, 2.4f);
                    box(cv, u, 14, 3, 21, 10, 2.4f);
                    box(cv, u, 3, 14, 10, 21, 2.4f);
                    box(cv, u, 14, 14, 21, 21, 2.4f);
                    break;
                case SHARE:
                    path.moveTo(12 * u, 15 * u);
                    path.lineTo(12 * u, 3.5f * u);
                    path.moveTo(8 * u, 7.5f * u);
                    path.lineTo(12 * u, 3.5f * u);
                    path.lineTo(16 * u, 7.5f * u);
                    path.moveTo(8 * u, 10.5f * u);
                    path.lineTo(6 * u, 10.5f * u);
                    path.lineTo(6 * u, 20 * u);
                    path.lineTo(18 * u, 20 * u);
                    path.lineTo(18 * u, 10.5f * u);
                    path.lineTo(16 * u, 10.5f * u);
                    cv.drawPath(path, p);
                    break;
                case SEND:
                    path.moveTo(21 * u, 3 * u);
                    path.lineTo(3 * u, 10 * u);
                    path.lineTo(10.5f * u, 13.5f * u);
                    path.lineTo(14 * u, 21 * u);
                    path.close();
                    path.moveTo(10.5f * u, 13.5f * u);
                    path.lineTo(21 * u, 3 * u);
                    cv.drawPath(path, p);
                    break;
                case PICTURE:
                    box(cv, u, 4, 4, 20, 20, 3f);
                    cv.drawCircle(9 * u, 9 * u, 1.6f * u, p);
                    path.moveTo(4 * u, 17 * u);
                    path.lineTo(9 * u, 12.5f * u);
                    path.lineTo(13 * u, 16.5f * u);
                    path.lineTo(16 * u, 13.5f * u);
                    path.lineTo(20 * u, 17.5f * u);
                    cv.drawPath(path, p);
                    break;
                default:
                    box(cv, u, 5, 5.5f, 19, 21.5f, 2.5f);
                    box(cv, u, 9, 3, 15, 7, 1.5f);
                    break;
            }
        }
    }

    /** The shape of a day: one flush bar with a thin gap between runs of the five kinds of slot, as wide as the day is long. */
    static final class DayBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF rect = new RectF();
        private List<int[]> runs;
        private int[] colors;

        DayBar(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        DayBar set(Routine r, int[] kindColors) {
            runs = FirstDay.shape(r);
            colors = kindColors;
            invalidate();
            return this;
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (runs == null || colors == null || runs.isEmpty()) return;
            int total = 0;
            for (int[] x : runs) total += x[1];
            if (total <= 0) return;
            float w = getWidth();
            float h = getHeight();
            float gap = Math.max(1f, getResources().getDisplayMetrics().density);
            clip.reset();
            rect.set(0, 0, w, h);
            clip.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            float x = 0;
            for (int i = 0; i < runs.size(); i++) {
                boolean last = i == runs.size() - 1;
                float x1 = last ? w : x + w * runs.get(i)[1] / total;
                p.setColor(colors[Math.max(0, Math.min(colors.length - 1, runs.get(i)[0]))]);
                cv.drawRect(x, 0, Math.max(x, last ? x1 : x1 - gap), h, p);
                x = x1;
            }
            cv.restore();
        }
    }

    /** The five kind colours of the look the routine is set to (task, rest, meal, personal, sleep). */
    static int[] kindColors(Routine r) {
        int[] s = PosterRenderer.swatch(r.palette, Ui.night);
        return new int[]{s[3], s[4], s[5], s[6], s[7]};
    }

    // ------------------------------------------------------------------ the top of the tab

    /** A round button with a line icon. A long press shows what it is called. */
    static View roundButton(Activity a, int icon, String label, View.OnClickListener click) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(Ui.pressable(a, Ui.SOFT, Ui.LINE, 22, Ui.RIPPLE));
        f.addView(new Icon(a, icon, Ui.INK), new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.CENTER));
        f.setClickable(true);
        f.setFocusable(true);
        f.setContentDescription(label);
        f.setTooltipText(Lang.tr(label));
        f.setOnClickListener(click);
        return f;
    }

    /** The title of the tab with its two round buttons on the right. */
    static LinearLayout header(Activity a, String title, String line, View first, View second) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.label(a, title, 28, Ui.INK, true));
        words.addView(Ui.label(a, line, 14, Ui.MUTED, false), Ui.wide(a, 2));
        row.addView(words, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44));
        row.addView(first, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44));
        lp2.leftMargin = Ui.dp(a, 10);
        row.addView(second, lp2);
        return row;
    }

    /**
     * "Exam Specific": the days for an exam as tiles to slide through, each with its start time and the shape of the day. {@code pick}
     * gets the number of the template tapped; {@code hide} runs when Hide is tapped.
     */
    static LinearLayout examSection(Activity a, int[] colors, boolean h24, IntConsumer pick, Runnable hide) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.label(a, "Exam Specific", 20, Ui.INK, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView hideBtn = Ui.label(a, "Hide", 14, Ui.MUTED, false);
        hideBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        hideBtn.setMinHeight(Ui.dp(a, 48));
        hideBtn.setPadding(Ui.dp(a, 14), 0, 0, 0);
        hideBtn.setClickable(true);
        hideBtn.setFocusable(true);
        hideBtn.setOnClickListener(v -> hide.run());
        head.addView(hideBtn, new LinearLayout.LayoutParams(WRAP, WRAP));
        box.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));
        box.addView(Ui.label(a, "Pick one, then change any slot you like.", 14, Ui.MUTED, false), Ui.wide(a, 0));

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout rail = new LinearLayout(a);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < RoutineTemplates.EXAMS; i++) {
            final int which = i;
            LinearLayout tile = new LinearLayout(a);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 16));
            tile.setBackground(Ui.pressable(a, Ui.SOFT, Ui.LINE, 18, Ui.RIPPLE));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(v -> pick.accept(which));
            TextView name = Ui.label(a, RoutineTemplates.name(i), 16, Ui.INK, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(name, new LinearLayout.LayoutParams(MATCH, WRAP));
            tile.addView(Ui.label(a, "Wakes at " + RoutineDiff.clock(RoutineTemplates.wake(i), h24), 13, Ui.MUTED, false), Ui.wide(a, 2));
            LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(MATCH, Ui.dp(a, 6));
            bar.topMargin = Ui.dp(a, 14);
            tile.addView(new DayBar(a).set(RoutineTemplates.build(i), colors), bar);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 190), WRAP);
            if (i > 0) lp.leftMargin = Ui.dp(a, 10);
            rail.addView(tile, lp);
        }
        hs.addView(rail, new ViewGroup.LayoutParams(WRAP, WRAP));
        box.addView(hs, Ui.wide(a, 10));
        return box;
    }

    // ------------------------------------------------------------------ the sheets

    private static LinearLayout sheetColumn(Activity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Glass.dp(a, 22), Glass.dp(a, 4), Glass.dp(a, 22), Glass.dp(a, 6));
        return col;
    }

    private static View hairline(Activity a, int leftDp) {
        View v = new View(a);
        v.setBackgroundColor(Glass.EDGE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, Math.max(1, Glass.dp(a, 0.5f)));
        lp.leftMargin = Glass.dp(a, leftDp);
        v.setLayoutParams(lp);
        return v;
    }

    /** Share: what leaves or comes into the app, in one place. Each row closes the sheet and then does its work. */
    static void share(Activity a, String routineName, Runnable send, Runnable picture, Runnable paste) {
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout col = sheetColumn(a);
        col.addView(shareRow(a, dlg, Icon.SEND, "Send my routine", "A message with a code for friends", send));
        col.addView(hairline(a, 58));
        col.addView(shareRow(a, dlg, Icon.PICTURE, "Share my day as a picture", "Today's slots, ready for WhatsApp", picture));
        col.addView(hairline(a, 58));
        col.addView(shareRow(a, dlg, Icon.PASTE, "Paste a routine", "Use a code someone sent you", paste));
        dlg[0] = new DBuilder(a).setTitle("Share").setMessage("From “" + routineName + "”").setView(col).create();
        dlg[0].show();
    }

    private static View shareRow(Activity a, final AlertDialog[] dlg, int icon, String title, String line, final Runnable action) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Glass.dp(a, 72));
        row.setPadding(0, Glass.dp(a, 10), 0, Glass.dp(a, 10));
        row.setBackground(Glass.press(a, 0, 0, 12, Glass.RIPPLE));
        row.setClickable(true);
        row.setFocusable(true);
        FrameLayout tile = new FrameLayout(a);
        tile.setBackground(Glass.shape(a, Glass.FILL, Glass.EDGE, 14));
        tile.addView(new Icon(a, icon, Glass.WHITE), new FrameLayout.LayoutParams(Glass.dp(a, 22), Glass.dp(a, 22), Gravity.CENTER));
        row.addView(tile, new LinearLayout.LayoutParams(Glass.dp(a, 44), Glass.dp(a, 44)));
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Glass.text(a, title, 17, Glass.WHITE, true));
        words.addView(Glass.text(a, line, 14, Glass.MUTE, false), Glass.wide(a, 2));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        wp.leftMargin = Glass.dp(a, 14);
        row.addView(words, wp);
        row.setOnClickListener(v -> {
            if (dlg[0] != null) dlg[0].dismiss();
            action.run();
        });
        return row;
    }

    /**
     * Templates: the days to start from, in two groups (Exam Specific and Everyday), each with what it is, when it starts and the shape
     * of the day. "Sample day" is the day the app starts with.
     */
    static void templates(Activity a, String routineName, boolean h24, int[] colors, final IntConsumer pick, final Runnable sample) {
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout col = sheetColumn(a);
        col.addView(legend(a, colors), new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(group(a, "Exam Specific"), Glass.wide(a, 16));
        for (int i = 0; i < RoutineTemplates.count(); i++) {
            if (!RoutineTemplates.isExam(i)) continue;
            final int which = i;
            col.addView(templateRow(a, dlg, RoutineTemplates.name(i), RoutineTemplates.about(i), RoutineDiff.clock(RoutineTemplates.wake(i), h24),
                    RoutineTemplates.build(i), colors, () -> pick.accept(which)));
            col.addView(hairline(a, 0));
        }
        col.addView(group(a, "Everyday"), Glass.wide(a, 18));
        for (int i = 0; i < RoutineTemplates.count(); i++) {
            if (RoutineTemplates.isExam(i)) continue;
            final int which = i;
            col.addView(templateRow(a, dlg, RoutineTemplates.name(i), RoutineTemplates.about(i), RoutineDiff.clock(RoutineTemplates.wake(i), h24),
                    RoutineTemplates.build(i), colors, () -> pick.accept(which)));
            col.addView(hairline(a, 0));
        }
        Routine s = Routine.sample();
        col.addView(templateRow(a, dlg, "Sample day", "The day the app begins with.", RoutineDiff.clock(s.anchor, h24), s, colors, sample));
        dlg[0] = new DBuilder(a).setTitle("Templates")
                .setMessage("Replaces the slots of “" + routineName + "”. You can undo it.").setView(col).create();
        dlg[0].show();
    }

    private static View group(Activity a, String name) {
        return Glass.text(a, name, 15, Glass.WHITE, true);
    }

    /** The five kinds with their colours, so the bars can be read. */
    private static View legend(Activity a, int[] colors) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int k = 0; k < Routine.KIND_NAMES.length && k < colors.length; k++) {
            View dot = new View(a);
            dot.setBackground(Glass.shape(a, colors[k], 0, 2.5f));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Glass.dp(a, 9), Glass.dp(a, 9));
            if (k > 0) dp.leftMargin = Glass.dp(a, 16);
            row.addView(dot, dp);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(WRAP, WRAP);
            tp.leftMargin = Glass.dp(a, 6);
            row.addView(Glass.text(a, Routine.KIND_NAMES[k], 13, Glass.MUTE, false), tp);
        }
        hs.addView(row, new ViewGroup.LayoutParams(WRAP, WRAP));
        return hs;
    }

    private static View templateRow(Activity a, final AlertDialog[] dlg, String name, String about, String wake, Routine day, int[] colors,
                                    final Runnable action) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, Glass.dp(a, 14), 0, Glass.dp(a, 16));
        row.setBackground(Glass.press(a, 0, 0, 12, Glass.RIPPLE));
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Glass.text(a, name, 17, Glass.WHITE, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(Glass.text(a, wake, 15, Glass.MUTE, false), new LinearLayout.LayoutParams(WRAP, WRAP));
        row.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView t = Glass.text(a, about, 14, Glass.MUTE, false);
        t.setLineSpacing(Glass.dp(a, 2), 1f);
        row.addView(t, Glass.wide(a, 4));
        row.addView(new DayBar(a).set(day, colors), Glass.gap(a, MATCH, Glass.dp(a, 6), 12));
        row.setOnClickListener(v -> {
            if (dlg[0] != null) dlg[0].dismiss();
            action.run();
        });
        return row;
    }
}
