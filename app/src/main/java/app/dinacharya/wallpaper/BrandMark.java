package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** The small "Developed by" line at the bottom of the setup screen: a monogram badge and the name. */
final class BrandMark extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private int accent = Ui.TEAL;
    private int ink = Ui.MUTED;

    BrandMark(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void setColors(int accent, int ink) {
        this.accent = accent;
        this.ink = ink;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), (int) (44 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float cy = getHeight() / 2f;
        float badge = 22 * d;

        String pre = "DEVELOPED BY";
        String name = "ASG";
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(8.5f * d);
        p.setLetterSpacing(0.16f);
        float preW = p.measureText(pre);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(12.5f * d);
        p.setLetterSpacing(0.02f);
        float nameW = p.measureText(name);
        float textW = Math.max(preW, nameW);
        float total = badge + 9 * d + textW;
        float x0 = (w - total) / 2f;

        // Badge: rounded square with a monogram.
        r.set(x0, cy - badge / 2f, x0 + badge, cy + badge / 2f);
        p.setColor(accent);
        c.drawRoundRect(r, 6 * d, 6 * d, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(8f * d);
        p.setLetterSpacing(0f);
        c.drawText("ASG", x0 + badge / 2f, cy + 2.8f * d, p);

        // Words.
        float tx = x0 + badge + 9 * d;
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(ink);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(8.5f * d);
        p.setLetterSpacing(0.16f);
        c.drawText(pre, tx, cy - 3.5f * d, p);
        p.setColor(Ui.INK);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(12.5f * d);
        p.setLetterSpacing(0.02f);
        c.drawText(name, tx, cy + 9.5f * d, p);
        p.setLetterSpacing(0f);
    }
}
