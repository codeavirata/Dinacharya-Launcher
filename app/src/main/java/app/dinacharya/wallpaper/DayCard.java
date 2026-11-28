package app.dinacharya.wallpaper;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.app.Activity;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * "Share my day": today's slots drawn as one picture (1080 by 1920, the size chat and story apps like), made on the phone in the
 * colours of the person's chosen look, and handed to the share sheet. Nothing is uploaded by the app.
 */
final class DayCard {
    private static final int W = 1080, H = 1920;

    private DayCard() {
    }

    /** Draws the card for {@code day} from the routine that day follows. */
    static Bitmap draw(Routine main, LocalDate day) {
        Routine r = main.forDay(day);
        List<DayCardLayout.Row> rows = DayCardLayout.pick(DayCardLayout.rows(r.compute(), main.fmt24));
        int[] sw = PosterRenderer.swatch(main.palette, true);
        int bg = sw[0], ink = sw[1], accent = sw[2];
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, H, bg, darker(bg), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);

        p.setColor(accent);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(40);
        p.setLetterSpacing(0.2f);
        c.drawText("MY DAY", 80, 150, p);
        p.setLetterSpacing(0f);
        p.setColor(ink);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(84);
        c.drawText(day.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())), 80, 260, p);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setTextSize(44);
        p.setColor(fade(ink, 0.7f));
        c.drawText(day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())) + "  ·  " + fit(r.name, p, 560), 80, 330, p);

        float top = 400, rowH = Math.min(124f, (H - top - 260f) / Math.max(1, rows.size()));
        RectF rect = new RectF();
        for (int i = 0; i < rows.size(); i++) {
            DayCardLayout.Row row = rows.get(i);
            float y = top + i * rowH;
            if (row.more) {
                p.setColor(fade(ink, 0.6f));
                p.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                p.setTextSize(42);
                c.drawText(row.title, 80 + 250, y + rowH * 0.62f, p);
                continue;
            }
            int kc = sw[3 + Math.max(0, Math.min(Routine.SLEEP, row.kind))];
            rect.set(80, y + 14, 94, y + rowH - 14);
            p.setColor(kc);
            c.drawRoundRect(rect, 7, 7, p);
            p.setColor(fade(ink, 0.72f));
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
            p.setTextSize(36);
            c.drawText(row.time, 124, y + rowH * 0.62f, p);
            p.setColor(ink);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextSize(44);
            c.drawText(fit(row.title, p, W - 360 - 80 - 170), 360, y + rowH * 0.62f, p);
            p.setColor(fade(ink, 0.6f));
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(34);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Routine.formatDuration(row.mins), W - 80, y + rowH * 0.62f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        p.setColor(accent);
        p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        p.setTextSize(52);
        c.drawText("Dinacharya Launcher", 80, H - 140, p);
        p.setColor(fade(ink, 0.6f));
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setTextSize(34);
        c.drawText("Plan your day. Free on Google Play.", 80, H - 84, p);
        return bmp;
    }

    private static String fit(String s, Paint p, float maxPx) {
        if (s == null) return "";
        if (p.measureText(s) <= maxPx) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("…") > maxPx) n--;
        return s.substring(0, n).trim() + "…";
    }

    private static int fade(int color, float a) {
        return (color & 0x00FFFFFF) | ((int) (255 * a) << 24);
    }

    private static int darker(int c) {
        int r = (int) (((c >> 16) & 0xFF) * 0.78f), g = (int) (((c >> 8) & 0xFF) * 0.78f), b = (int) ((c & 0xFF) * 0.78f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Draws today's card in the background and opens the share sheet with it. */
    static void share(final Activity a) {
        final Routine main = Store.load(a);
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            try {
                File dir = new File(app.getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("no folder");
                final String name = "dinacharya-my-day.png";
                File f = new File(dir, name);
                Bitmap bmp = draw(main, LocalDate.now(Store.zone()));
                try (FileOutputStream os = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                } finally {
                    bmp.recycle();
                }
                final Uri uri = Uri.parse("content://" + ReportProvider.AUTHORITY + "/" + Uri.encode(name));
                a.runOnUiThread(() -> {
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                                .putExtra(Intent.EXTRA_TEXT, "My day with Dinacharya Launcher: " + RoutineShare.PLAY)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        send.setClipData(ClipData.newRawUri("", uri));
                        a.startActivity(Intent.createChooser(send, "Share my day"));
                    } catch (RuntimeException e) {
                        CrashLog.note(a, "share day card", e);
                    }
                });
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                CrashLog.note(app, "day card", e instanceof Exception ? (Exception) e : new RuntimeException(e));
                a.runOnUiThread(() -> Lang.toast(a, "The picture could not be made", Toast.LENGTH_LONG).show());
            }
        }, "day-card").start();
    }
}
