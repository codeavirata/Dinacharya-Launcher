package app.dinacharya.wallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.IOException;
import java.io.OutputStream;

/** Makes a PDF of a day report with Android's own PDF writer, so no extra library is needed. */
final class ReportPdf {
    private ReportPdf() {
    }

    /** {@code analysis} false leaves out the slot-by-slot focus part (it is part of Pro). */
    static void write(DayReport r, boolean h24, boolean analysis, OutputStream out) throws IOException {
        float pageH = ReportRenderer.PAGE_H;
        float total = ReportRenderer.measure(r, h24, pageH, analysis);
        int pages = Math.max(1, (int) Math.ceil((total - 1) / pageH));
        PdfDocument doc = new PdfDocument();
        try {
            Paint foot = new Paint(Paint.ANTI_ALIAS_FLAG);
            foot.setTextSize(8f);
            foot.setColor(ReportRenderer.MUTED);
            foot.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            foot.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < pages; i++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder((int) ReportRenderer.W, (int) pageH, i + 1).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas c = page.getCanvas();
                c.save();
                c.translate(0, -i * pageH);
                c.clipRect(0, i * pageH, ReportRenderer.W, (i + 1) * pageH);
                ReportRenderer.draw(c, r, h24, pageH, analysis);
                c.restore();
                c.drawText(Lang.tr("Made on your phone by Dinacharya Launcher  ·  page " + (i + 1) + " of " + pages),
                        ReportRenderer.W / 2f, pageH - 16f, foot);
                doc.finishPage(page);
            }
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }
}
