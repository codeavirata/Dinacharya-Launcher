package app.dinacharya.wallpaper;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A small clock-style progress ring: a thin track, an arc that fills clockwise from the top, quarter ticks like a watch face (when it is
 * big enough), and a percentage or, once the goal is met, a tick mark in the middle. It grows to its value the first time it is shown.
 */
final class GoalRing extends View {
    private final float frac;
    private final int color;
    private final String text;
    private final boolean done;
    private final int sizePx;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path check = new Path();
    private float shown;

    /** @param frac 0..1 how much is done; @param text what to write in the middle (may be empty); @param done draws a tick instead of the text */
    GoalRing(Context c, int sizeDp, float frac, int color, String text, boolean done) {
        super(c);
        this.frac = Math.max(0f, Math.min(1f, frac));
        this.color = color;
        this.text = text == null ? "" : text;
        this.done = done;
        this.sizePx = Ui.dp(c, sizeDp);
        this.shown = 0f;
        setContentDescription(Lang.tr("Progress") + " " + Math.round(this.frac * 100) + "%");
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ValueAnimator va = ValueAnimator.ofFloat(0f, frac);
        va.setDuration(frac <= 0f ? 1 : 520);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        va.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(sizePx, sizePx);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight());
        float stroke = Math.max(Ui.dp(getContext(), 3), s * 0.11f);
        float pad = stroke / 2f + 1f;
        box.set(pad, pad, s - pad, s - pad);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Ui.night ? 0x33FFFFFF : 0x22000000);
        cv.drawOval(box, p);
        if (shown > 0.004f) {
            p.setColor(done ? Ui.TEAL : color);
            cv.drawArc(box, -90f, 360f * shown, false, p);
        }
        float r = s / 2f - stroke - Ui.dp(getContext(), 1);
        if (s >= Ui.dp(getContext(), 36) && r > 0) {
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setStrokeWidth(Math.max(1f, Ui.dp(getContext(), 1) * 0.8f));
            p.setColor(Ui.night ? 0x33FFFFFF : 0x26000000);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                float x1 = (float) (s / 2f + Math.sin(a) * r), y1 = (float) (s / 2f - Math.cos(a) * r);
                float x2 = (float) (s / 2f + Math.sin(a) * (r - Ui.dp(getContext(), 3))), y2 = (float) (s / 2f - Math.cos(a) * (r - Ui.dp(getContext(), 3)));
                cv.drawLine(x1, y1, x2, y2, p);
            }
        }
        if (done) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Math.max(Ui.dp(getContext(), 2), s * 0.075f));
            p.setColor(Ui.TEAL);
            check.reset();
            check.moveTo(s * 0.33f, s * 0.52f);
            check.lineTo(s * 0.45f, s * 0.64f);
            check.lineTo(s * 0.68f, s * 0.38f);
            cv.drawPath(check, p);
        } else if (!text.isEmpty()) {
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Ui.INK);
            p.setTextSize(s * (text.length() > 3 ? 0.24f : 0.3f));
            cv.drawText(text, s / 2f, s / 2f - (p.descent() + p.ascent()) / 2f, p);
        }
    }
}
