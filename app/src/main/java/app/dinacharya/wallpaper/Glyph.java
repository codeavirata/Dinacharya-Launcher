package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Small line icons drawn in code (24 x 24 design grid), so the app needs no image files. */
final class Glyph extends View {
    static final int HOME = 0, ROUTINE = 1, CHART = 2, CLOUD = 3, GEAR = 4, PALETTE = 5, BELL = 6, SHIELD = 7,
            HELP = 8, INFO = 9, PERSON = 10, TARGET = 11, CHEVRON = 12, GLOBE = 13;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path a = new Path();
    private final Path b = new Path();
    private final RectF r = new RectF();
    private int type;
    private int color;

    Glyph(Context c, int type, int color) {
        super(c);
        this.type = type;
        this.color = color;
        setWillNotDraw(false);
    }

    void set(int type, int color) {
        this.type = type;
        this.color = color;
        invalidate();
    }

    void setColor(int color) {
        this.color = color;
        invalidate();
    }

    private void stroke(float w) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
    }

    private void fill() {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        float size = Math.min(w, h);
        if (size <= 0) return;
        c.save();
        c.translate(getPaddingLeft() + (w - size) / 2f, getPaddingTop() + (h - size) / 2f);
        c.scale(size / 24f, size / 24f);
        draw24(c);
        c.restore();
    }

    /** Draws the icon on a 24 x 24 grid. Also used by the image tests. */
    void draw24(Canvas c) {
        stroke(1.9f);
        switch (type) {
            case HOME:
                a.reset();
                a.moveTo(3.5f, 11.5f);
                a.lineTo(12, 4.2f);
                a.lineTo(20.5f, 11.5f);
                c.drawPath(a, p);
                a.reset();
                a.moveTo(6, 10);
                a.lineTo(6, 20);
                a.lineTo(18, 20);
                a.lineTo(18, 10);
                c.drawPath(a, p);
                a.reset();
                a.moveTo(10, 20);
                a.lineTo(10, 15);
                a.lineTo(14, 15);
                a.lineTo(14, 20);
                c.drawPath(a, p);
                break;
            case ROUTINE:
                r.set(4, 5.5f, 20, 20.5f);
                c.drawRoundRect(r, 2.5f, 2.5f, p);
                c.drawLine(4, 9.5f, 20, 9.5f, p);
                c.drawLine(8.5f, 3.5f, 8.5f, 6.5f, p);
                c.drawLine(15.5f, 3.5f, 15.5f, 6.5f, p);
                c.drawLine(7.5f, 13.3f, 8.5f, 13.3f, p);
                c.drawLine(11, 13.3f, 16.5f, 13.3f, p);
                c.drawLine(7.5f, 16.8f, 8.5f, 16.8f, p);
                c.drawLine(11, 16.8f, 14.5f, 16.8f, p);
                break;
            case CHART:
                stroke(2.6f);
                c.drawLine(6, 19.5f, 6, 13, p);
                c.drawLine(12, 19.5f, 12, 6.5f, p);
                c.drawLine(18, 19.5f, 18, 10, p);
                break;
            case CLOUD: {
                a.reset();
                a.addCircle(9, 13.5f, 4.3f, Path.Direction.CW);
                b.reset();
                b.addCircle(14.2f, 11.2f, 5.2f, Path.Direction.CW);
                a.op(b, Path.Op.UNION);
                b.reset();
                r.set(4.7f, 13, 19.6f, 18.8f);
                b.addRoundRect(r, 2.9f, 2.9f, Path.Direction.CW);
                a.op(b, Path.Op.UNION);
                c.drawPath(a, p);
                break;
            }
            case GEAR: {
                stroke(2.7f);
                for (int i = 0; i < 8; i++) {
                    double ang = Math.PI * i / 4;
                    float cx = (float) Math.cos(ang), cy = (float) Math.sin(ang);
                    c.drawLine(12 + 6.8f * cx, 12 + 6.8f * cy, 12 + 9f * cx, 12 + 9f * cy, p);
                }
                stroke(1.9f);
                c.drawCircle(12, 12, 6.3f, p);
                c.drawCircle(12, 12, 2.4f, p);
                break;
            }
            case PALETTE:
                c.drawCircle(12, 12, 8.6f, p);
                fill();
                c.drawCircle(8.5f, 10, 1.5f, p);
                c.drawCircle(12.5f, 7.6f, 1.5f, p);
                c.drawCircle(16.2f, 10.6f, 1.5f, p);
                c.drawCircle(10.4f, 15.2f, 1.9f, p);
                break;
            case BELL: {
                a.reset();
                a.addCircle(12, 10.8f, 5.6f, Path.Direction.CW);
                b.reset();
                r.set(6.4f, 10.8f, 17.6f, 17);
                b.addRoundRect(r, 0.5f, 0.5f, Path.Direction.CW);
                a.op(b, Path.Op.UNION);
                c.drawPath(a, p);
                c.drawLine(4.8f, 17, 19.2f, 17, p);
                fill();
                c.drawCircle(12, 19.8f, 1.7f, p);
                break;
            }
            case SHIELD:
                a.reset();
                a.moveTo(12, 3);
                a.lineTo(19, 5.8f);
                a.lineTo(19, 11.5f);
                a.lineTo(12, 20.8f);
                a.lineTo(5, 11.5f);
                a.lineTo(5, 5.8f);
                a.close();
                c.drawPath(a, p);
                a.reset();
                a.moveTo(9, 11.6f);
                a.lineTo(11.2f, 13.8f);
                a.lineTo(15.2f, 9.6f);
                c.drawPath(a, p);
                break;
            case HELP:
            case INFO:
                c.drawCircle(12, 12, 9, p);
                fill();
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(type == HELP ? 12.5f : 12f);
                c.drawText(type == HELP ? "?" : "i", 12, 16.4f, p);
                break;
            case PERSON:
                c.drawCircle(12, 8.4f, 3.8f, p);
                c.save();
                c.clipRect(0, 0, 24, 20.6f);
                r.set(4.8f, 14.4f, 19.2f, 26);
                c.drawRoundRect(r, 6.5f, 6.5f, p);
                c.restore();
                break;
            case TARGET:
                c.drawCircle(12, 12, 8.6f, p);
                c.drawCircle(12, 12, 4.6f, p);
                fill();
                c.drawCircle(12, 12, 1.6f, p);
                break;
            case GLOBE:
                stroke(1.9f);
                c.drawCircle(12, 12, 8.6f, p);
                r.set(7.9f, 3.4f, 16.1f, 20.6f);
                c.drawOval(r, p);
                c.drawLine(3.4f, 12, 20.6f, 12, p);
                c.drawLine(12, 3.4f, 12, 20.6f, p);
                break;
            case CHEVRON:
                stroke(2.2f);
                a.reset();
                a.moveTo(9.5f, 6);
                a.lineTo(15.5f, 12);
                a.lineTo(9.5f, 18);
                c.drawPath(a, p);
                break;
            default:
                break;
        }
    }
}
