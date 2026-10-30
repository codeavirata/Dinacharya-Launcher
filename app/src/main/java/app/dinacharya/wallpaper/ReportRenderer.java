package app.dinacharya.wallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a day report or the week page. The Performance screen and the PDF use the same code, so what
 * you see on screen is what you print. Sizes are in points on a page 595 wide (A4); with a page
 * height the content is laid out so no block is cut by a page break, and with 0 it is one long page.
 */
final class ReportRenderer {
    static final float W = 595f;
    static final float PAGE_H = 842f;
    private static final float M = 36f;
    private static final float CW = W - 2 * M;
    private static final float FOOT = 34f;

    static final int INK = 0xFF10201E;
    static final int MUTED = 0xFF587270;
    static final int LINE = 0xFFCFDCDA;
    static final int ACCENT = 0xFF0E7C7B;
    static final int SOFT = 0xFFF3F7F6;
    static final int AMBER = 0xFFC98A12;
    /** A slot you renamed for the day. Not the Rest colour, so the two are not mixed up. */
    static final int CHANGED_COL = 0xFF8A63B8;
    static final int RED = 0xFFC4553B;
    static final int[] KIND = {0xFF0E7C7B, 0xFFC98A12, 0xFFC4553B, 0xFF4A6C94, 0xFF404685};
    private static final int[] KIND_NAME_ORDER = {Routine.TASK, Routine.REST, Routine.MEAL, Routine.PERSONAL};

    private static final Typeface REG = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface MONO = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Canvas cv;
    private final boolean h24;
    private final float pageH;
    private float y;

    private ReportRenderer(Canvas cv, boolean h24, float pageH) {
        this.cv = cv;
        this.h24 = h24;
        this.pageH = pageH;
    }

    // ------------------------------------------------------------------ entry points

    /**
     * The whole day report (the PDF), the top half of it (up to the sleep), the rest (the table of slots and the suggestions), or the
     * whole report without the slot-by-slot focus part, which is part of Pro (the free PDF).
     */
    static final int ALL = 0, HEAD = 1, TAIL = 2, BASIC = 3;

    /** Total height of a day report. */
    static float measure(DayReport r, boolean h24, float pageH) {
        return measure(r, h24, pageH, true);
    }

    static float measure(DayReport r, boolean h24, float pageH, boolean analysis) {
        return new ReportRenderer(null, h24, pageH).day(r, analysis ? ALL : BASIC);
    }

    static void draw(Canvas c, DayReport r, boolean h24, float pageH) {
        draw(c, r, h24, pageH, true);
    }

    static void draw(Canvas c, DayReport r, boolean h24, float pageH, boolean analysis) {
        new ReportRenderer(c, h24, pageH).day(r, analysis ? ALL : BASIC);
    }

    /** One part of a day report on a long page, for the screen, which puts the slot analysis between the two parts. */
    static float measurePart(DayReport r, boolean h24, int part) {
        return new ReportRenderer(null, h24, 0).day(r, part);
    }

    static void drawPart(Canvas c, DayReport r, boolean h24, int part) {
        new ReportRenderer(c, h24, 0).day(r, part);
    }

    static float measureWeek(WeekStats w, List<String> tips, boolean h24) {
        return new ReportRenderer(null, h24, 0).week(w, tips);
    }

    static void drawWeek(Canvas c, WeekStats w, List<String> tips, boolean h24) {
        new ReportRenderer(c, h24, 0).week(w, tips);
    }

    // ------------------------------------------------------------------ drawing helpers (do nothing when only measuring)

    private void fill(float l, float t, float r, float b, float rad, int color) {
        if (cv == null) return;
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        rect.set(l, t, r, b);
        cv.drawRoundRect(rect, rad, rad, p);
    }

    private void outline(float l, float t, float r, float b, float rad, int color, float w) {
        if (cv == null) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        rect.set(l, t, r, b);
        cv.drawRoundRect(rect, rad, rad, p);
    }

    private void line(float x1, float y1, float x2, float y2, int color, float w) {
        if (cv == null) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        cv.drawLine(x1, y1, x2, y2, p);
    }

    private void dot(float cx, float cy, float r, int color) {
        if (cv == null) return;
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        cv.drawCircle(cx, cy, r, p);
    }

    private void arc(float cx, float cy, float r, float startDeg, float sweepDeg, int color, float w) {
        if (cv == null) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        rect.set(cx - r, cy - r, cx + r, cy + r);
        cv.drawArc(rect, startDeg, sweepDeg, false, p);
    }

    private float text(String s, float x, float base, float size, int color, Typeface tf, Paint.Align align, float spacing) {
        s = Lang.tr(s);
        tp.setTextSize(size);
        tp.setTypeface(tf);
        tp.setLetterSpacing(spacing);
        float w = tp.measureText(s);
        if (cv != null) {
            tp.setColor(color);
            tp.setTextAlign(align);
            cv.drawText(s, x, base, tp);
        }
        return w;
    }

    private float width(String s, float size, Typeface tf, float spacing) {
        s = Lang.tr(s);
        tp.setTextSize(size);
        tp.setTypeface(tf);
        tp.setLetterSpacing(spacing);
        return tp.measureText(s);
    }

    private String fit(String s, float size, Typeface tf, float maxW) {
        s = Lang.tr(s);
        if (width(s, size, tf, 0f) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && width(t + "…", size, tf, 0f) > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    private List<String> wrap(String s, float size, Typeface tf, float maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && width(test, size, tf, 0f) > maxW) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void eyebrow(String s) {
        text(s.toUpperCase(Locale.ROOT), M, y + 10, 8.5f, MUTED, BOLD, Paint.Align.LEFT, 0.16f);
        y += 22;
    }

    /** Moves down to the next page when the next block would be cut by a page break. */
    private void ensure(float h) {
        if (pageH <= 0) return;
        float top = (float) Math.floor(y / pageH) * pageH;
        float limit = top + pageH - FOOT;
        if (y + h > limit && y > top + M + 1) y = top + pageH + M;
    }

    private void rule() {
        line(M, y, W - M, y, LINE, 0.8f);
        y += 16;
    }

    // ------------------------------------------------------------------ formatting

    private String clock(long ms) {
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
    }

    private static String dur(long ms) {
        return Routine.formatDuration((int) Math.round(ms / 60000.0));
    }

    private int scoreColor(int s) {
        return s >= 70 ? ACCENT : (s >= 50 ? AMBER : RED);
    }

    // ------------------------------------------------------------------ the day report

    private float day(DayReport r, int part) {
        y = M;
        if (part != TAIL) {
            header(r);
            breakdown(r);
            phone(r);
            topApps(r);
            focus(r);
            timeBudget(r);
            types(TypeStats.of(r), r.partial ? "Time by activity type, so far" : "Time by activity type", false);
            sleep(r);
        }
        if (part != HEAD) {
            slots(r);
            if (part == ALL) focusSlots(r);   // on the screen this is the card between the two parts
            suggestions(r.tips, "Suggestions for tomorrow");
            y += 6;
        }
        return y;
    }

    /**
     * The slot-by-slot analysis for the printed report: for each slot that ran, the time devoted, the focus, the longest focus and the
     * other phone use with its times; then the same by activity. The screen shows this as dropdown cards (see {@link SlotCards}).
     */
    private void focusSlots(DayReport r) {
        if (!r.phoneKnown) return;
        List<SlotReport.Item> items = SlotReport.slots(r);
        if (items.isEmpty()) return;
        ensure(70);
        eyebrow("Focus, slot by slot");
        text(fit("Focus is the share of a slot's time with no other phone use. Apps chosen for an activity do not count against it.", 8.5f, REG, CW),
                M, y + 8, 8.5f, MUTED, REG, Paint.Align.LEFT, 0f);
        y += 20;
        for (SlotReport.Item s : items) {
            List<String> stretches = stretchLines(s);
            float h = 36f + stretches.size() * 12f;
            ensure(h);
            text(clock(s.startMs), M, y + 11, 9.5f, INK, MONO, Paint.Align.LEFT, 0f);
            dot(M + 72, y + 8, 3.2f, KIND[Math.max(0, Math.min(KIND.length - 1, s.kind))]);
            text(fit(s.title, 10.5f, MED, 300), M + 82, y + 11, 10.5f, INK, MED, Paint.Align.LEFT, 0f);
            int pct = s.focusPct();
            text(pct < 0 ? "–" : pct + "%", W - M, y + 11, 11f, pct < 0 ? MUTED : toneColor(pct), BOLD, Paint.Align.RIGHT, 0f);
            text(fit(figures(s), 9f, REG, CW - 82), M + 82, y + 24, 9f, MUTED, REG, Paint.Align.LEFT, 0f);
            for (int i = 0; i < stretches.size(); i++) text(stretches.get(i), M + 82, y + 36 + i * 12f, 8.5f, RED, REG, Paint.Align.LEFT, 0f);
            y += h;
        }
        y += 4;
        List<SlotReport.Item> acts = SlotReport.activities(r);
        if (!acts.isEmpty()) {
            ensure(34);
            text("BY ACTIVITY", M, y + 8, 8f, MUTED, BOLD, Paint.Align.LEFT, 0.12f);
            y += 16;
            for (SlotReport.Item a : acts) {
                ensure(24);
                dot(M + 4, y + 7, 3.5f, KIND[Math.max(0, Math.min(KIND.length - 1, a.kind))]);
                text(fit(a.title, 10.5f, MED, 100), M + 12, y + 11, 10.5f, INK, MED, Paint.Align.LEFT, 0f);
                float x0 = M + 126;
                float x1 = W - M - 190;
                fill(x0, y + 3, x1, y + 13, 5, SOFT);
                float share = a.workedMs <= 0 ? 0f : Math.min(1f, a.devotedMs() / (float) a.workedMs);
                if (share > 0) fill(x0, y + 3, x0 + Math.max(5f, (x1 - x0) * share), y + 13, 5, ACCENT);
                String v = "Devoted " + SlotReport.dur(a.devotedMs()) + " of " + SlotReport.dur(a.workedMs);
                text(v, W - M, y + 11, 9.5f, MUTED, REG, Paint.Align.RIGHT, 0f);
                y += 22;
            }
        }
        y += 2;
        rule();
    }

    private static int toneColor(int pct) {
        int t = SlotReport.tone(pct);
        return t == SlotReport.TONE_GOOD ? ACCENT : (t == SlotReport.TONE_FAIR ? AMBER : RED);
    }

    /** The figures of a slot on one line: devoted, longest focus, other phone use. */
    private static String figures(SlotReport.Item s) {
        StringBuilder sb = new StringBuilder("Devoted ").append(SlotReport.dur(s.devotedMs()));
        if (s.hasTimes()) sb.append("  ·  Longest focus ").append(SlotReport.dur(s.longestFocusMs));
        sb.append(s.offMs() > 0 ? "  ·  Other phone use " + SlotReport.dur(s.offMs()) : "  ·  No other phone use");
        return sb.toString();
    }

    /** The stretches of other phone use as at most two lines of text with their times. */
    private List<String> stretchLines(SlotReport.Item s) {
        List<String> out = new ArrayList<>();
        if (s.uses.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        int shown = 0;
        for (SlotUse.Episode e : s.uses) {
            String piece = clock(e.startMs) + "–" + clock(e.endMs) + (e.app.isEmpty() ? "" : " " + e.app);
            String test = cur.length() == 0 ? piece : cur + ";  " + piece;
            if (cur.length() > 0 && width(test, 8.5f, REG, 0f) > CW - 90) {
                out.add(cur.toString());
                if (out.size() == 2) break;
                cur = new StringBuilder(piece);
            } else {
                cur = new StringBuilder(test);
            }
            shown++;
        }
        if (out.size() < 2 && cur.length() > 0) out.add(cur.toString());
        int total = Math.max(s.useCount, s.uses.size());
        if (shown < total && !out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, out.get(last) + "  +" + (total - shown) + " more");
        }
        return out;
    }

    private void header(DayReport r) {
        ensure(120);
        text(r.partial ? "TODAY SO FAR" : "DAILY REPORT", M, y + 10, 9f, ACCENT, BOLD, Paint.Align.LEFT, 0.2f);
        String title;
        try {
            title = LocalDate.parse(r.day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Lang.locale()));
        } catch (RuntimeException e) {
            title = r.day;
        }
        text(fit(title, 21f, MED, CW - 100), M, y + 40, 21f, INK, MED, Paint.Align.LEFT, 0f);
        String span = clock(r.startMs) + " to " + clock(r.endMs) + "   ·   " + Routine.formatDuration((int) ((r.endMs - r.startMs) / 60000L));
        text(span, M, y + 60, 10.5f, MUTED, REG, Paint.Align.LEFT, 0f);
        if (r.partial) text("Updated " + clock(System.currentTimeMillis()), M, y + 76, 10f, MUTED, REG, Paint.Align.LEFT, 0f);

        float cx = W - M - 38;
        float cy = y + 46;
        arc(cx, cy, 32, 0, 360, SOFT, 7f);
        arc(cx, cy, 32, 0, 360, LINE, 1f);
        if (r.score >= 0) {
            arc(cx, cy, 32, -90, 360f * r.score / 100f, scoreColor(r.score), 7f);
            text(String.valueOf(r.score), cx, cy + 9, 26f, INK, BOLD, Paint.Align.CENTER, 0f);
        } else {
            text("–", cx, cy + 8, 24f, MUTED, BOLD, Paint.Align.CENTER, 0f);
        }
        text(Analyzer.grade(r.score), cx, cy + 52, 9.5f, MUTED, MED, Paint.Align.CENTER, 0f);
        y += 112;
        rule();
    }

    private void breakdown(DayReport r) {
        ensure(130);
        eyebrow("How the score is made");
        int kept = 0, changed = 0, off = 0, none = 0, tracked = 0;
        for (DayReport.Row row : r.rows) {
            if (row.kind == Routine.SLEEP) continue;
            if (row.status == DayReport.KEPT) { kept++; tracked++; }
            else if (row.status == DayReport.CHANGED) { changed++; tracked++; }
            else if (row.status == DayReport.SKIPPED) { off++; tracked++; }
            else if (row.status == DayReport.UNANSWERED) { none++; tracked++; }
        }
        String planNote = r.planKept >= 0
                ? kept + " kept, " + changed + " changed, " + off + " taken off, " + none + " not answered, out of " + tracked + " slots."
                : (r.alarmsOn ? "No slot has been answered yet." : "Turn on alarms to measure this.");
        scoreRow("Plan kept", "40%", r.planKept, planNote);

        String focusNote;
        if (r.focus >= 0) {
            long active = 0, phone = 0;
            for (DayReport.Row row : r.rows) {
                if (row.kind != Routine.TASK || row.status == DayReport.SKIPPED) continue;
                active += row.activeMs;
                phone += row.phoneMs;
            }
            focusNote = "Phone in use for " + Math.round(100.0 * phone / Math.max(1, active)) + "% of task time (" + dur(phone) + " of " + dur(active) + ").";
        } else {
            focusNote = r.phoneKnown ? "Not enough task time yet." : "Allow usage access to measure this.";
        }
        scoreRow("Focus", "35%", r.focus, focusNote);

        String sleepNote;
        if (r.sleep >= 0) {
            long late = Math.max(0, (r.actualBedMs - r.plannedBedMs) / 60000L);
            sleepNote = (late < 5 ? "On time" : Routine.formatDuration((int) late) + " late") + ", slept "
                    + Routine.formatDuration((int) Math.max(0, (r.endMs - r.actualBedMs) / 60000L))
                    + " of a planned " + Routine.formatDuration(r.plannedSleepMin) + ".";
        } else {
            sleepNote = r.partial ? "Measured after bedtime." : "Answer the bedtime alarm to measure this.";
        }
        scoreRow("Sleep", "25%", r.sleep, sleepNote);
        y += 6;
        rule();
    }

    private void scoreRow(String name, String weight, int value, String note) {
        text(name, M, y + 9, 11f, INK, MED, Paint.Align.LEFT, 0f);
        text(weight, M + 62, y + 9, 8.5f, MUTED, REG, Paint.Align.LEFT, 0f);
        float x0 = M + 110;
        float x1 = W - M - 42;
        fill(x0, y + 2, x1, y + 10, 4, SOFT);
        if (value > 0) fill(x0, y + 2, x0 + (x1 - x0) * value / 100f, y + 10, 4, scoreColor(value));
        text(value >= 0 ? String.valueOf(value) : "–", W - M, y + 10, 12f, value >= 0 ? INK : MUTED, BOLD, Paint.Align.RIGHT, 0f);
        text(fit(note, 9f, REG, CW), M, y + 24, 9f, MUTED, REG, Paint.Align.LEFT, 0f);
        y += 36;
    }

    private void phone(DayReport r) {
        ensure(r.phoneKnown ? 190 : 50);
        eyebrow("Phone use");
        if (!r.phoneKnown) {
            text("Phone use was not available. Allow usage access in the app to include it.", M, y + 10, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
            y += 30;
            rule();
            return;
        }
        text(Routine.formatDuration((int) Math.round(r.phoneMs / 60000.0)), M, y + 26, 28f, INK, MED, Paint.Align.LEFT, 0f);
        long taskPhone = 0, taskActive = 0;
        for (DayReport.Row row : r.rows) {
            if (row.kind != Routine.TASK || row.status == DayReport.SKIPPED) continue;
            taskPhone += row.phoneMs;
            taskActive += row.activeMs;
        }
        float tx = M + 150;
        if (taskActive > 0) {
            text(dur(taskPhone) + " during task time (" + Math.round(100.0 * taskPhone / taskActive) + "% of it)", tx, y + 12, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
        }
        if (r.beforeBedMs >= 0) {
            text(dur(r.beforeBedMs) + " in the hour before bed", tx, y + 27, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
        }

        float top = y + 44;
        float h = 74f;
        float gap = 3f;
        float bw = (CW - gap * 23) / 24f;
        long max = 1;
        for (long v : r.hourMs) max = Math.max(max, v);
        float scaleMs = Math.max(max, 20 * 60000L);
        int peak = 0;
        for (int i = 0; i < 24; i++) if (r.hourMs[i] > r.hourMs[peak]) peak = i;
        for (int i = 0; i < 24; i++) {
            float bh = r.hourMs[i] <= 0 ? 1.5f : Math.max(2f, h * r.hourMs[i] / scaleMs);
            int col = r.hourMs[i] <= 0 ? LINE : (i == peak ? ACCENT : (ACCENT & 0x00FFFFFF) | 0xB0000000);
            fill(M + i * (bw + gap), top + h - bh, M + i * (bw + gap) + bw, top + h, Math.min(bw / 2, 3f), col);
        }
        float stripTop = top + h + 6;
        long span = Math.max(1, 24L * 3600000L);
        for (DayReport.Row row : r.rows) {
            float a = M + CW * Math.max(0, row.startMs - r.startMs) / span;
            float b = M + CW * Math.min(span, row.endMs - r.startMs) / span;
            if (b - a < 0.5f) continue;
            fill(a, stripTop, Math.min(W - M, b), stripTop + 7, 0, row.status == DayReport.SKIPPED ? LINE : KIND[row.kind]);
        }
        if (cv != null) {
            for (int i = 0; i < 24; i += 3) {
                long t = r.startMs + i * 3600000L;
                ZonedDateTime z = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault());
                int hh = z.getHour();
                String lab = h24 ? String.format(Locale.ROOT, "%02d", hh) : ((hh % 12 == 0 ? 12 : hh % 12) + (hh < 12 ? "a" : "p"));
                text(lab, M + i * (bw + gap), stripTop + 20, 8f, MUTED, REG, Paint.Align.LEFT, 0f);
            }
        }
        text("Bars: phone use each hour.  Strip: your plan by type.", M, stripTop + 34, 8f, MUTED, REG, Paint.Align.LEFT, 0f);
        y = stripTop + 48;
        rule();
    }

    private void topApps(DayReport r) {
        if (r.apps.isEmpty()) return;
        ensure(30 + r.apps.size() * 22);
        eyebrow("Top apps");
        long max = r.apps.get(0).ms;
        for (int i = 0; i < r.apps.size(); i++) {
            DayReport.App a = r.apps.get(i);
            text(fit(a.name, 10.5f, REG, 130), M, y + 10, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            float x0 = M + 140;
            float x1 = W - M - 62;
            fill(x0, y + 2, x1, y + 11, 4.5f, SOFT);
            fill(x0, y + 2, x0 + Math.max(6f, (x1 - x0) * a.ms / max), y + 11, 4.5f, i == 0 ? ACCENT : (ACCENT & 0x00FFFFFF) | 0xA0000000);
            text(dur(a.ms), W - M, y + 10, 10f, MUTED, MONO, Paint.Align.RIGHT, 0f);
            y += 22;
        }
        y += 2;
        rule();
    }

    /** What the home screen's focus pauses did today. Only drawn when there was at least one. */
    private void focus(DayReport r) {
        if (r.pauses <= 0) return;
        ensure(96);
        eyebrow("Focus at the home screen");
        float half = CW / 2f;
        text(String.valueOf(r.wentBack), M, y + 26, 26f, ACCENT, BOLD, Paint.Align.LEFT, 0f);
        text("times you went back after the pause", M, y + 42, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
        text(String.valueOf(r.openedAfter), M + half, y + 26, 26f, r.openedAfter > r.wentBack ? AMBER : INK, BOLD, Paint.Align.LEFT, 0f);
        text("times you opened the app anyway", M + half, y + 42, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
        y += 52;
        if (!r.pausedMost.isEmpty()) {
            text("Paused most: " + fit(r.pausedMost, 10.5f, REG, CW - 90), M, y + 10, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            y += 20;
        }
        y += 2;
        rule();
    }

    private void timeBudget(DayReport r) {
        int[] planned = new int[5];
        int[] kept = new int[5];
        int changed = 0;
        int plannedTotal = 0;
        boolean tracked = false;
        for (DayReport.Row row : r.rows) {
            if (row.kind == Routine.SLEEP) continue;
            planned[row.kind] += row.plannedMin;
            plannedTotal += row.plannedMin;
            if (row.status == DayReport.KEPT) { kept[row.kind] += row.plannedMin; tracked = true; }
            else if (row.status == DayReport.CHANGED) { changed += row.plannedMin; tracked = true; }
            else if (row.status == DayReport.SKIPPED || row.status == DayReport.UNANSWERED) tracked = true;
        }
        if (plannedTotal == 0) return;
        ensure(130);
        eyebrow("Time planned and kept");
        float x0 = M + 58;
        float x1 = W - M - 96;
        text("Planned", M, y + 12, 10f, INK, MED, Paint.Align.LEFT, 0f);
        stack(x0, x1, y, planned, 0, plannedTotal, false);
        text(Routine.formatDuration(plannedTotal), W - M, y + 12, 10f, MUTED, MONO, Paint.Align.RIGHT, 0f);
        y += 28;
        text("Kept", M, y + 12, 10f, INK, MED, Paint.Align.LEFT, 0f);
        if (tracked) {
            stack(x0, x1, y, kept, changed, plannedTotal, true);
            int k = 0;
            for (int v : kept) k += v;
            String kv = r.partial ? Routine.formatDuration(k) + " so far"
                    : Routine.formatDuration(k) + " (" + Math.round(100.0 * k / plannedTotal) + "%)";
            text(kv, W - M, y + 12, 10f, MUTED, MONO, Paint.Align.RIGHT, 0f);
        } else {
            fill(x0, y + 1, x1, y + 17, 5, SOFT);
            text("not tracked", x0 + 8, y + 12, 9.5f, MUTED, REG, Paint.Align.LEFT, 0f);
        }
        y += 30;
        float lx = M;
        String[] names = {"Task", "Rest", "Meal", "Personal"};
        for (int i = 0; i < 4; i++) {
            dot(lx + 4, y + 6, 3.5f, KIND[KIND_NAME_ORDER[i]]);
            lx += 10 + text(names[i], lx + 11, y + 9, 9f, MUTED, REG, Paint.Align.LEFT, 0f) + 14;
        }
        if (tracked) {
            dot(lx + 4, y + 6, 3.5f, CHANGED_COL);
            lx += 10 + text("Changed", lx + 11, y + 9, 9f, MUTED, REG, Paint.Align.LEFT, 0f) + 14;
            dot(lx + 4, y + 6, 3.5f, SOFT);
            text("Not kept", lx + 11, y + 9, 9f, MUTED, REG, Paint.Align.LEFT, 0f);
        }
        y += 22;
        rule();
    }

    /**
     * One bar per activity type: what was done against the goal (or against the plan when there is no
     * goal). Met goals are marked. In the week view the numbers are averages per day.
     */
    private void types(List<TypeStats.Line> lines, String title, boolean average) {
        if (lines.isEmpty()) return;
        ensure(40 + 30 * Math.min(lines.size(), 4));
        eyebrow(title);
        float x0 = M + 96;
        float x1 = W - M - 150;
        for (TypeStats.Line l : lines) {
            ensure(30);
            int col = KIND[Math.max(0, Math.min(4, l.kind))];
            dot(M + 4, y + 7, 3.5f, col);
            text(fit(l.label(), 10.5f, MED, 82), M + 12, y + 11, 10.5f, INK, MED, Paint.Align.LEFT, 0f);
            int target = l.hasGoal() ? l.goalMin : Math.max(l.plannedMin, 1);
            fill(x0, y + 3, x1, y + 13, 5, SOFT);
            float done = Math.min(1f, l.doneMin / (float) target);
            if (done > 0) fill(x0, y + 3, x0 + (x1 - x0) * done, y + 13, 5, l.met() ? ACCENT : col);
            if (l.hasGoal() && l.plannedMin < l.goalMin && l.plannedMin > 0) {
                // a tick where the plan itself ends, when the plan is short of the goal
                float px = x0 + (x1 - x0) * l.plannedMin / (float) target;
                line(px, y + 1, px, y + 15, MUTED, 1f);
            }
            String v = Routine.formatDuration(l.doneMin)
                    + (l.hasGoal() ? " of " + Routine.formatDuration(l.goalMin) + (average ? " goal" : (l.met() ? " goal ✓" : " goal"))
                    : " of " + Routine.formatDuration(l.plannedMin) + " planned");   // one unit per shape: "{1} of {2} goal ✓" ...
            text(v, W - M, y + 11, 9.5f, l.met() ? ACCENT : MUTED, l.met() ? BOLD : REG, Paint.Align.RIGHT, 0f);
            y += 24;
        }
        text(average ? "Average per day. Slots you kept, or that had no alarm to ask you, count as done."
                        : "Slots you kept, or that had no alarm to ask you, count as done.",
                M, y + 6, 8.5f, MUTED, REG, Paint.Align.LEFT, 0f);
        y += 20;
        rule();
    }

    /** One stacked bar: minutes by type, then optionally "changed" in amber, the rest empty. */
    private void stack(float x0, float x1, float top, int[] byKind, int changed, int total, boolean withChanged) {
        fill(x0, top + 1, x1, top + 17, 5, SOFT);
        if (cv == null) return;
        float x = x0;
        for (int k : KIND_NAME_ORDER) {
            if (byKind[k] <= 0) continue;
            float w = (x1 - x0) * byKind[k] / total;
            fill(x, top + 1, x + w, top + 17, 0, KIND[k]);
            x += w;
        }
        if (withChanged && changed > 0) {
            float w = (x1 - x0) * changed / total;
            fill(x, top + 1, x + w, top + 17, 0, CHANGED_COL);
        }
        outline(x0, top + 1, x1, top + 17, 5, LINE, 0.8f);
    }

    private void sleep(DayReport r) {
        ensure(80);
        eyebrow("Sleep");
        if (r.plannedBedMs > 0) {
            String a = "Planned bedtime " + clock(r.plannedBedMs);
            if (r.actualBedMs > 0) a += "   ·   went to sleep " + clock(r.actualBedMs);
            a += "   ·   wake-up " + clock(r.endMs);
            text(fit(a, 10.5f, REG, CW), M, y + 10, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            y += 18;
        }
        if (r.actualBedMs > 0) {
            long slept = Math.max(0, (r.endMs - r.actualBedMs) / 60000L);
            text("Slept " + Routine.formatDuration((int) slept) + " of a planned " + Routine.formatDuration(r.plannedSleepMin) + ".", M, y + 10, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            y += 18;
        } else {
            text(r.partial ? "Not recorded yet." : "Not recorded. Answer the bedtime alarm and the app will track it.", M, y + 10, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
            y += 18;
        }
        if (r.snoozes > 0) {
            text("You asked for more time " + r.snoozes + (r.snoozes == 1 ? " time" : " times") + " (" + Routine.formatDuration(r.snoozeMinutes) + " in all).", M, y + 10, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
            y += 18;
        }
        y += 4;
        rule();
    }

    private void slots(DayReport r) {
        ensure(60);
        eyebrow("Your day, slot by slot");
        float row = 19f;
        text("TIME", M, y + 8, 8f, MUTED, BOLD, Paint.Align.LEFT, 0.12f);
        text("SLOT", M + 80, y + 8, 8f, MUTED, BOLD, Paint.Align.LEFT, 0.12f);
        text("STATUS", M + 360, y + 8, 8f, MUTED, BOLD, Paint.Align.LEFT, 0.12f);
        text("PHONE", W - M, y + 8, 8f, MUTED, BOLD, Paint.Align.RIGHT, 0.12f);
        y += 16;
        int i = 0;
        for (DayReport.Row s : r.rows) {
            ensure(row);
            if (i++ % 2 == 0) fill(M - 4, y, W - M + 4, y + row, 3, SOFT);
            text(clock(s.startMs), M, y + 13, 9.5f, INK, MONO, Paint.Align.LEFT, 0f);
            dot(M + 72, y + row / 2, 3.2f, s.status == DayReport.SKIPPED ? LINE : KIND[s.kind]);
            String t = s.newTitle.isEmpty() ? s.title : s.title + "  →  " + s.newTitle;
            text(fit(t, 10.5f, REG, 270), M + 82, y + 13, 10.5f, s.status == DayReport.SKIPPED ? MUTED : INK, REG, Paint.Align.LEFT, 0f);
            int col = MUTED;
            if (s.status == DayReport.KEPT) col = ACCENT;
            else if (s.status == DayReport.CHANGED) col = CHANGED_COL;
            else if (s.status == DayReport.SKIPPED || s.status == DayReport.UNANSWERED) col = RED;
            String st = s.status == DayReport.UNTRACKED ? "–" : DayReport.STATUS_NAMES[s.status];
            if (s.kind == Routine.SLEEP && s.status == DayReport.UNTRACKED) st = "Sleep";
            text(st, M + 360, y + 13, 9.5f, col, MED, Paint.Align.LEFT, 0f);
            String ph = r.phoneKnown && s.activeMs > 0 && s.kind != Routine.SLEEP ? dur(s.phoneMs) : "–";
            text(ph, W - M, y + 13, 9.5f, MUTED, MONO, Paint.Align.RIGHT, 0f);
            y += row;
        }
        y += 8;
        rule();
    }

    private void suggestions(List<String> tips, String title) {
        if (tips == null || tips.isEmpty()) return;
        ensure(60);
        eyebrow(title);
        int n = 1;
        for (String tip : tips) {
            List<String> lines = wrap(tip, 10.5f, REG, CW - 24);
            float h = lines.size() * 14f + 8;
            ensure(h);
            dot(M + 7, y + 8, 7, ACCENT);
            text(String.valueOf(n++), M + 7, y + 11.5f, 8.5f, 0xFFFFFFFF, BOLD, Paint.Align.CENTER, 0f);
            for (int i = 0; i < lines.size(); i++) text(lines.get(i), M + 24, y + 11 + i * 14f, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            y += h;
        }
        y += 4;
    }

    // ------------------------------------------------------------------ the week page

    private float week(WeekStats w, List<String> tips) {
        y = M;
        text("LAST DAYS", M, y + 10, 9f, ACCENT, BOLD, Paint.Align.LEFT, 0.2f);
        text("Your week", M, y + 38, 21f, INK, MED, Paint.Align.LEFT, 0f);
        text(w.days.size() == 1 ? "1 closed day" : w.days.size() + " closed days", M, y + 56, 10.5f, MUTED, REG, Paint.Align.LEFT, 0f);
        y += 72;
        rule();

        float tw = (CW - 16) / 3f;
        String[] big = {w.avgScore >= 0 ? String.valueOf(w.avgScore) : "–",
                w.avgPhoneMin >= 0 ? Routine.formatDuration(w.avgPhoneMin) : "–",
                w.avgLateMin >= 0 ? (w.avgLateMin < 5 ? "On time" : Routine.formatDuration(w.avgLateMin)) : "–"};
        String[] cap = {"Average score", "Phone per day", "Late to bed, average"};
        for (int i = 0; i < 3; i++) {
            float x = M + i * (tw + 8);
            fill(x, y, x + tw, y + 62, 10, SOFT);
            text(big[i], x + 12, y + 33, 22f, i == 0 && w.avgScore >= 0 ? scoreColor(w.avgScore) : INK, MED, Paint.Align.LEFT, 0f);
            text(cap[i], x + 12, y + 51, 9f, MUTED, REG, Paint.Align.LEFT, 0f);
        }
        y += 82;

        eyebrow("Daily score");
        float top = y + 4;
        float h = 110f;
        int[] marks = {0, 50, 70, 100};
        for (int m : marks) {
            float gy = top + h - h * m / 100f;
            line(M + 24, gy, W - M, gy, m == 70 ? 0xFFB8D6D4 : LINE, m == 70 ? 1f : 0.6f);
            text(String.valueOf(m), M + 18, gy + 3, 8f, MUTED, REG, Paint.Align.RIGHT, 0f);
        }
        int n = w.days.size();
        float cx0 = M + 44;
        float cx1 = W - M - 20;
        float step = n > 1 ? (cx1 - cx0) / (n - 1) : 0;
        float px = -1, py = -1;
        for (int i = 0; i < n; i++) {
            DayReport d = w.days.get(i);
            float x = n > 1 ? cx0 + i * step : (cx0 + cx1) / 2;
            text(weekday(d.day), x, top + h + 18, 9f, MUTED, REG, Paint.Align.CENTER, 0f);
            if (d.score < 0) {
                px = -1;
                continue;
            }
            float yy = top + h - h * d.score / 100f;
            if (px >= 0) line(px, py, x, yy, ACCENT, 2f);
            px = x;
            py = yy;
        }
        for (int i = 0; i < n; i++) {
            DayReport d = w.days.get(i);
            if (d.score < 0) continue;
            float x = n > 1 ? cx0 + i * step : (cx0 + cx1) / 2;
            float yy = top + h - h * d.score / 100f;
            dot(x, yy, 4.5f, 0xFFFFFFFF);
            dot(x, yy, 3.2f, scoreColor(d.score));
        }
        y = top + h + 36;
        rule();

        eyebrow("Phone time per day");
        long maxMs = 60 * 60000L;
        for (DayReport d : w.days) if (d.phoneKnown) maxMs = Math.max(maxMs, d.phoneMs);
        float bt = y + 14;
        float bh = 80f;
        float bwid = Math.min(40f, (CW - 20) / Math.max(1, n) - 8);
        for (int i = 0; i < n; i++) {
            DayReport d = w.days.get(i);
            float x = n > 1 ? cx0 + i * step : (cx0 + cx1) / 2;
            text(weekday(d.day), x, bt + bh + 16, 9f, MUTED, REG, Paint.Align.CENTER, 0f);
            if (!d.phoneKnown) {
                text("–", x, bt + bh - 4, 10f, MUTED, REG, Paint.Align.CENTER, 0f);
                continue;
            }
            float hh = Math.max(2f, bh * d.phoneMs / maxMs);
            fill(x - bwid / 2, bt + bh - hh, x + bwid / 2, bt + bh, 4, ACCENT);
            text(Routine.formatDuration((int) Math.round(d.phoneMs / 60000.0)), x, bt + bh - hh - 4, 8f, INK, REG, Paint.Align.CENTER, 0f);
        }
        y = bt + bh + 34;
        rule();

        types(TypeStats.average(w.days), "Time by activity type", true);

        eyebrow("Habits");
        int shown = 0;
        if (!w.bestSlot.isEmpty()) {
            text("Most reliable: " + fit(w.bestSlot, 10.5f, MED, 300) + ", kept " + w.bestPct + "% of the time.", M, y + 10, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            y += 18;
            shown++;
        }
        if (!w.worstSlot.isEmpty()) {
            text("Needs attention: " + fit(w.worstSlot, 10.5f, MED, 280) + ", kept " + w.worstPct + "% of the time.", M, y + 10, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            y += 18;
            shown++;
        }
        if (w.avgScore >= 0) {
            text("Days with a score of 70 or more: " + w.goodDays + " of " + w.days.size() + ".", M, y + 10, 10.5f, INK, REG, Paint.Align.LEFT, 0f);
            y += 18;
            shown++;
        }
        if (shown == 0) {
            text("Habits appear after a few answered days.", M, y + 10, 10f, MUTED, REG, Paint.Align.LEFT, 0f);
            y += 18;
        }
        y += 4;
        rule();
        suggestions(tips, "Suggestions");
        y += 6;
        return y;
    }

    private static String weekday(String day) {
        try {
            return LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEE", Lang.locale()));
        } catch (RuntimeException e) {
            return "";
        }
    }
}
