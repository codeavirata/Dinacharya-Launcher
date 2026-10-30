package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import java.util.List;

/** Shows a day report or the week page on screen with the same drawing code the PDF uses. */
final class ReportView extends View {
    private DayReport day;
    private WeekStats week;
    private List<String> tips;
    private boolean h24;
    private float contentH = 100f;
    /** Which part of the day report is shown (see {@link ReportRenderer#ALL}). */
    private int part = ReportRenderer.ALL;

    ReportView(Context context) {
        super(context);
    }

    void showDay(DayReport r, boolean h24) {
        showDay(r, h24, ReportRenderer.ALL);
    }

    /** One part of the day report: {@link ReportRenderer#HEAD} or {@link ReportRenderer#TAIL} (the screen puts the slot analysis between them). */
    void showDay(DayReport r, boolean h24, int part) {
        this.day = r;
        this.week = null;
        this.h24 = h24;
        this.part = part;
        this.contentH = ReportRenderer.measurePart(r, h24, part);
        requestLayout();
        invalidate();
    }

    void showWeek(WeekStats w, List<String> tips, boolean h24) {
        this.week = w;
        this.day = null;
        this.tips = tips;
        this.h24 = h24;
        this.contentH = ReportRenderer.measureWeek(w, tips, h24);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = View.MeasureSpec.getSize(widthSpec);
        float scale = w / ReportRenderer.W;
        setMeasuredDimension(w, (int) Math.ceil(contentH * scale));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = getWidth() / ReportRenderer.W;
        canvas.drawColor(0xFFFFFFFF);
        canvas.save();
        canvas.scale(scale, scale);
        if (day != null) ReportRenderer.drawPart(canvas, day, h24, part);
        else if (week != null) ReportRenderer.drawWeek(canvas, week, tips, h24);
        canvas.restore();
    }
}
