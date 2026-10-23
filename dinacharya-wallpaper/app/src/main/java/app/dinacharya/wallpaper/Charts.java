package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** The two small charts of the Insights tab: a ring chart (pie) of time by type, and phone use by hour. Drawn by hand, no libraries. */
final class Charts {
    private Charts() {
    }

    /** One slice of the ring: a name, an amount and a colour. */
    static final class Slice {
        final String label;
        final long value;
        final int color;

        Slice(String label, long value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    /** The share of each slice of the total, in whole degrees that add up to exactly 360 (none for an empty chart). Uses no Android classes' state. */
    static float[] sweeps(List<Slice> slices) {
        float[] out = new float[slices.size()];
        long total = 0;
        for (Slice s : slices) total += Math.max(0, s.value);
        if (total <= 0) return out;
        for (int i = 0; i < out.length; i++) out[i] = 360f * Math.max(0, slices.get(i).value) / total;
        return out;
    }

    /** A ring chart with a line of text in the middle. */
    static final class Ring extends View {
        private final List<Slice> slices;
        private final String top;
        private final String bottom;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        Ring(Context c, List<Slice> slices, String top, String bottom) {
            super(c);
            this.slices = new ArrayList<>(slices);
            this.top = top;
            this.bottom = bottom;
        }

        @Override
        protected void onMeasure(int w, int h) {
            int size = Math.min(MeasureSpec.getSize(w), Ui.dp(getContext(), 220));
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float stroke = w * 0.17f;
            box.set(stroke / 2, stroke / 2, w - stroke / 2, w - stroke / 2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.BUTT);
            float[] sw = sweeps(slices);
            float at = -90f;
            boolean any = false;
            for (int i = 0; i < sw.length; i++) {
                if (sw[i] <= 0) continue;
                any = true;
                p.setColor(slices.get(i).color);
                float gap = sw[i] > 6f ? 1.2f : 0f;
                c.drawArc(box, at + gap / 2, Math.max(0.5f, sw[i] - gap), false, p);
                at += sw[i];
            }
            if (!any) {
                p.setColor(Ui.SOFT);
                c.drawArc(box, 0, 360, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Ui.INK);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(w * 0.16f);
            c.drawText(Lang.tr(top), w / 2, w / 2 + w * 0.02f, p);
            p.setColor(Ui.MUTED);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(w * 0.07f);
            c.drawText(Lang.tr(bottom), w / 2, w / 2 + w * 0.12f, p);
        }
    }

    /** Phone use in each hour of the day: 24 bars, with the clock hour under every sixth. */
    static final class HourBars extends View {
        private final long[] hourMs;
        private final String[] marks;
        private final int color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** @param marks the text under each of the 24 bars (empty for most) */
        HourBars(Context c, long[] hourMs, String[] marks, int color) {
            super(c);
            this.hourMs = hourMs;
            this.marks = marks;
            this.color = color;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), 110));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float labelH = Ui.dp(getContext(), 16);
            float chartH = getHeight() - labelH;
            long max = 3600000L;   // a bar is a full hour when the phone was in use the whole hour
            float gap = w / 24f * 0.28f;
            float bw = (w - gap * 23) / 24f;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 24; i++) {
                float frac = Math.min(1f, hourMs[i] / (float) max);
                float bh = Math.max(Ui.dp(getContext(), 2), frac * chartH);
                float x = i * (bw + gap);
                p.setColor(hourMs[i] > 0 ? color : Ui.SOFT);
                c.drawRoundRect(x, chartH - bh, x + bw, chartH, bw / 3, bw / 3, p);
            }
            p.setColor(Ui.MUTED);
            p.setTextSize(Ui.dp(getContext(), 10));
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < 24; i++) {
                if (marks[i] == null || marks[i].isEmpty()) continue;
                float cx = i * (bw + gap) + bw / 2;
                p.setTextAlign(i == 0 ? Paint.Align.LEFT : Paint.Align.CENTER);
                c.drawText(marks[i], i == 0 ? 0 : cx, getHeight() - Ui.dp(getContext(), 3), p);
            }
        }
    }
}
