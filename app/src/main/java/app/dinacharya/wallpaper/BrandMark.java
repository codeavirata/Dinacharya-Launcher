package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * The small credit at the bottom of a page, on one line: "© 2026 | Developed by Code Avirata". The year and the words are quiet,
 * a hairline stands between them, and the name is a little stronger. It shrinks, never wraps, when the screen is narrow.
 */
final class BrandMark extends View {
    private static final String YEAR = "© 2026";
    private static final String BY = "Developed by";
    private static final String WHO = "Code Avirata";

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int accent = Ui.TEAL_DARK;
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

    /** Draws one piece of the line (or only measures it when c is null) and returns its width. */
    private float part(Canvas c, String s, float x, float baseline, float size, int color, Typeface tf, float spacing) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setLetterSpacing(spacing);
        p.setColor(color);
        float width = p.measureText(s);
        if (c != null) c.drawText(s, x, baseline, p);
        return width;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float cy = getHeight() / 2f;
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);

        // The whole line is measured at its natural size first; on a narrow screen every part shrinks by the same amount.
        float size = 12.5f * d;
        float gap = 11 * d;
        float space = 4.5f * d;
        float natural = part(null, YEAR, 0, 0, size, 0, medium, 0.04f) + gap + Math.max(1f, d) + gap
                + part(null, BY, 0, 0, size, 0, regular, 0.04f) + space + part(null, WHO, 0, 0, size, 0, bold, 0.1f);
        float room = w - 24 * d;
        float s = (natural > room && room > 0) ? room / natural : 1f;
        size *= s;
        gap *= s;
        space *= s;
        float bar = Math.max(1f, d);

        int quiet = ink;
        int strong = ink == Ui.MUTED ? Ui.INK : ink;
        float total = part(null, YEAR, 0, 0, size, 0, medium, 0.04f) + gap + bar + gap
                + part(null, BY, 0, 0, size, 0, regular, 0.04f) + space + part(null, WHO, 0, 0, size, 0, bold, 0.1f);
        float x = (w - total) / 2f;
        float base = cy + 0.36f * size;

        x += part(c, YEAR, x, base, size, quiet, medium, 0.04f) + gap;
        p.setColor((accent & 0x00FFFFFF) | 0xB3000000);
        c.drawRect(x, cy - 0.5f * size, x + bar, cy + 0.5f * size, p);
        x += bar + gap;
        x += part(c, BY, x, base, size, quiet, regular, 0.04f) + space;
        part(c, WHO, x, base, size, strong, bold, 0.1f);
        p.setLetterSpacing(0f);
    }
}
