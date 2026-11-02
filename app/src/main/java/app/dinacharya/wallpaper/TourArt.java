package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * The small pictures in the tour. Each page has a drawing of the screen or the idea it explains, drawn
 * with simple shapes and a few words, so the tour needs no image files.
 */
final class TourArt extends View {
    static final int HOME = 0, DRAWER = 1, SWIPES = 2, ROUTINE = 3, ALARM = 4, CHANGE = 5, TODO = 6, PAUSE = 7, LOCK = 8, REPORT = 9,
            BACKUP = 10, LOOK = 11, PRO = 12, WALLPAPER = 13;
    static final int PAGES = 14;

    private static final int WHITE = 0xFFF1F3F2;
    private static final int MUTED = 0xFF8FA3A1;
    private static final int TEAL = 0xFF63D2CC;
    private static final int TEAL_DEEP = 0xFF0E7C7B;
    private static final int PANEL = 0xFF1B3330;
    private static final int PANEL2 = 0xFF254743;
    private static final int AMBER = 0xFFE6B04A;
    private static final int BLUE = 0xFF7FB3E6;
    private static final int CORAL = 0xFFE08A6C;
    private static final int LILAC = 0xFFA89BE0;
    private static final int PHONE = 0xFF000000;

    private int page;
    private final float d;

    TourArt(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void setPage(int page) {
        this.page = page;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        draw(c, page, getWidth(), getHeight(), d);
    }

    /** Draws page {@code page} into a box {@code w} by {@code h} pixels; {@code d} is the screen density. */
    static void draw(Canvas c, int page, float w, float h, float d) {
        new Artist(c, w, h, d).page(page);
    }

    /** The drawing itself, kept apart from the view so it can also be drawn onto an image. */
    private static final class Artist {
        final Canvas c;
        final float w, h, d;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final Path path = new Path();

        Artist(Canvas c, float w, float h, float d) {
            this.c = c;
            this.w = w;
            this.h = h;
            this.d = d;
        }

        void page(int which) {
            switch (which) {
                case HOME: home(); break;
                case DRAWER: drawer(); break;
                case SWIPES: swipes(); break;
                case ROUTINE: routine(); break;
                case ALARM: alarm(); break;
                case CHANGE: change(); break;
                case TODO: todo(); break;
                case PAUSE: pause(); break;
                case LOCK: lock(); break;
                case REPORT: report(); break;
                case BACKUP: backup(); break;
                case LOOK: look(); break;
                case PRO: pro(); break;
                case WALLPAPER: wallpaper(); break;
                default: break;
            }
        }

        // ---------------------------------------------------------------- tools

        void fill(float l, float t, float rr, float b, float radius, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            r.set(l, t, rr, b);
            c.drawRoundRect(r, radius, radius, p);
        }

        void outline(float l, float t, float rr, float b, float radius, int color, float width) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setColor(color);
            r.set(l, t, rr, b);
            c.drawRoundRect(r, radius, radius, p);
            p.setStyle(Paint.Style.FILL);
        }

        /** Text with its left edge ({@code align} 0), middle (1) or right edge (2) at x; y is the baseline. */
        void text(String s, float x, float y, float size, int color, int align, boolean bold, boolean light) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setLetterSpacing(0f);
            p.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD)
                    : Typeface.create(light ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
            p.setTextAlign(align == 1 ? Paint.Align.CENTER : (align == 2 ? Paint.Align.RIGHT : Paint.Align.LEFT));
            c.drawText(Lang.tr(s), x, y, p);
        }

        void line(float x1, float y1, float x2, float y2, int color, float width) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        /** An arrow from (x1,y1) to (x2,y2). */
        void arrow(float x1, float y1, float x2, float y2, int color) {
            line(x1, y1, x2, y2, color, 3 * d);
            double a = Math.atan2(y2 - y1, x2 - x1);
            float len = 9 * d;
            for (int s = -1; s <= 1; s += 2) {
                double b = a + Math.PI - s * 0.55;
                line(x2, y2, x2 + (float) (Math.cos(b) * len), y2 + (float) (Math.sin(b) * len), color, 3 * d);
            }
        }

        /** The phone: returns the screen rectangle in {@code r}. */
        void phone(float cx, float top, float height) {
            float width = height * 0.52f;
            fill(cx - width / 2, top, cx + width / 2, top + height, 16 * d, PHONE);
            outline(cx - width / 2, top, cx + width / 2, top + height, 16 * d, 0x66FFFFFF, 1.5f * d);
        }

        /** A small bell centred on (x, y). */
        void bell(float x, float y) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(WHITE);
            path.reset();
            path.moveTo(x - 9 * d, y + 6 * d);
            path.lineTo(x - 6 * d, y + 3 * d);
            path.lineTo(x - 5 * d, y - 4 * d);
            path.lineTo(x - 2 * d, y - 8 * d);
            path.lineTo(x + 2 * d, y - 8 * d);
            path.lineTo(x + 5 * d, y - 4 * d);
            path.lineTo(x + 6 * d, y + 3 * d);
            path.lineTo(x + 9 * d, y + 6 * d);
            path.close();
            c.drawPath(path, p);
            c.drawCircle(x, y + 9 * d, 2.6f * d, p);
        }

        void pill(float l, float t, float rr, float b, String label, boolean filled, float size) {
            float rad = (b - t) / 2;
            if (filled) fill(l, t, rr, b, rad, TEAL);
            else outline(l, t, rr, b, rad, MUTED, 1.3f * d);
            text(label, (l + rr) / 2, (t + b) / 2 + size * 0.36f, size, filled ? 0xFF001B1A : WHITE, 1, true, false);
        }

        // ---------------------------------------------------------------- the pages

        void home() {
            float ph = h * 0.96f, top = h * 0.02f, cx = w / 2;
            phone(cx, top, ph);
            poster(cx, top, ph);
        }

        /** The day poster inside a phone: time, date, phone time and a row for every slot, the current one outlined. */
        void poster(float cx, float top, float ph) {
            float pw = ph * 0.52f, l = cx - pw / 2 + pw * 0.09f, rr = cx + pw / 2 - pw * 0.09f;
            text("10:42", l, top + ph * 0.1f, pw * 0.17f, WHITE, 0, false, true);
            text("Tue 20 Oct", rr, top + ph * 0.1f, pw * 0.07f, MUTED, 2, false, false);
            text("Phone today  2h 05m", l, top + ph * 0.155f, pw * 0.06f, MUTED, 0, false, false);
            String[] names = {"Wake up", "Study", "Rest", "Meal", "Study", "Rest", "Sleep"};
            int[] cols = {AMBER, TEAL, LILAC, CORAL, TEAL, LILAC, BLUE};
            float t0 = top + ph * 0.21f, rowH = ph * 0.105f;
            for (int i = 0; i < names.length; i++) {
                float t = t0 + i * rowH;
                boolean now = i == 1;
                fill(l, t, rr, t + rowH - 5 * d, 8 * d, now ? PANEL2 : PANEL);
                if (now) outline(l, t, rr, t + rowH - 5 * d, 8 * d, TEAL, 1.4f * d);
                fill(l + 6 * d, t + 6 * d, l + 9 * d, t + rowH - 11 * d, 1.5f * d, cols[i]);
                text(names[i], l + 16 * d, t + (rowH - 5 * d) / 2 + 4 * d, pw * 0.075f, now ? WHITE : MUTED, 0, now, false);
            }
        }

        void drawer() {
            float ph = h * 0.96f, top = h * 0.02f, cx = w * 0.62f;
            phone(cx, top, ph);
            float pw = ph * 0.52f, x = cx - pw / 2 + pw * 0.1f;
            text("Search apps", x, top + ph * 0.11f, pw * 0.085f, MUTED, 0, false, false);
            line(x, top + ph * 0.135f, cx + pw / 2 - pw * 0.2f, top + ph * 0.135f, 0x44FFFFFF, 1.2f * d);
            String[] names = {"Books", "Calculator", "Calendar", "Camera", "Clock", "Contacts", "Drive"};
            for (int i = 0; i < names.length; i++) {
                text(names[i], x, top + ph * 0.23f + i * ph * 0.095f, pw * 0.085f, WHITE, 0, false, false);
            }
            String letters = "ABCDEFGHIJK";
            for (int i = 0; i < letters.length(); i++) {
                text(String.valueOf(letters.charAt(i)), cx + pw / 2 - pw * 0.09f, top + ph * 0.2f + i * ph * 0.062f,
                        pw * 0.05f, i == 2 ? TEAL : MUTED, 1, i == 2, false);
            }
            float ax = w * 0.17f;
            arrow(ax, h * 0.62f, ax, h * 0.22f, TEAL);
            text("Swipe up", ax, h * 0.78f, 15 * d, WHITE, 1, true, false);
            text("for every app", ax, h * 0.78f + 18 * d, 12 * d, MUTED, 1, false, false);
        }

        void swipes() {
            float cx = w / 2, cy = h / 2;
            float pw = Math.min(w, h) * 0.34f, ph = pw * 1.5f;
            fill(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, 12 * d, PHONE);
            outline(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, 12 * d, 0x66FFFFFF, 1.5f * d);
            text("10:42", cx, cy + pw * 0.08f, pw * 0.3f, WHITE, 1, false, true);
            float gap = 10 * d;
            // up
            arrow(cx, cy - ph / 2 - gap - 4 * d, cx, cy - ph / 2 - gap - 26 * d, TEAL);
            text("All apps", cx + 14 * d, cy - ph / 2 - gap - 10 * d, 13 * d, WHITE, 0, true, false);
            // down
            arrow(cx, cy + ph / 2 + gap + 4 * d, cx, cy + ph / 2 + gap + 26 * d, AMBER);
            text("Notifications", cx + 14 * d, cy + ph / 2 + gap + 22 * d, 13 * d, WHITE, 0, true, false);
            // left and right
            float side = cx - pw / 2 - gap;
            arrow(side, cy, side - 56 * d, cy, BLUE);
            text("Camera", side - 28 * d, cy - 12 * d, 12 * d, WHITE, 1, true, false);
            float sideR = cx + pw / 2 + gap;
            arrow(sideR, cy, sideR + 56 * d, cy, CORAL);
            text("Phone", sideR + 30 * d, cy - 12 * d, 12 * d, WHITE, 1, true, false);
        }

        void routine() {
            float left = w * 0.1f, right = w * 0.9f;
            c.translate(0, 4 * d);
            String[] chips = {"Weekdays", "Weekend", "Exam day"};
            float cwid = (right - left - 2 * 8 * d) / 3;
            for (int i = 0; i < 3; i++) {
                float l = left + i * (cwid + 8 * d);
                if (i == 0) fill(l, 0, l + cwid, 28 * d, 14 * d, TEAL);
                else outline(l, 0, l + cwid, 28 * d, 14 * d, MUTED, 1.2f * d);
                text(chips[i], l + cwid / 2, 18.5f * d, 12 * d, i == 0 ? 0xFF001B1A : WHITE, 1, i == 0, false);
            }
            String[] times = {"5:30", "6:00", "9:00", "9:30", "12:30", "10:30"};
            String[] names = {"Wake up", "Study", "Rest", "Meal", "Study", "Sleep"};
            int[] cols = {AMBER, TEAL, LILAC, CORAL, TEAL, BLUE};
            float rowH = (h - 40 * d) / 6f;
            for (int i = 0; i < 6; i++) {
                float t = 38 * d + i * rowH;
                boolean now = i == 1;
                fill(left, t, right, t + rowH - 6 * d, 10 * d, now ? PANEL2 : PANEL);
                if (now) outline(left, t, right, t + rowH - 6 * d, 10 * d, TEAL, 1.6f * d);
                fill(left + 8 * d, t + 7 * d, left + 12 * d, t + rowH - 13 * d, 2 * d, cols[i]);
                text(times[i], left + 22 * d, t + (rowH - 6 * d) / 2 + 4.5f * d, 13 * d, MUTED, 0, false, false);
                text(names[i], left + 84 * d, t + (rowH - 6 * d) / 2 + 5 * d, 15 * d, WHITE, 0, now, false);
                if (now) text("now", right - 12 * d, t + (rowH - 6 * d) / 2 + 4.5f * d, 12 * d, TEAL, 2, true, false);
            }
        }

        void alarm() {
            float left = w * 0.08f, right = w * 0.92f, top = h * 0.04f, bottom = h * 0.62f;
            fill(left, top, right, bottom, 18 * d, PANEL);
            fill(left + 14 * d, top + 14 * d, left + 46 * d, top + 46 * d, 16 * d, TEAL_DEEP);
            bell(left + 30 * d, top + 30 * d);
            text("Study starts now", left + 58 * d, top + 28 * d, 16 * d, WHITE, 0, true, false);
            text("Are you continuing, or changing the plan?", left + 14 * d, top + 66 * d, 12.5f * d, MUTED, 0, false, false);
            float bw = (right - left - 14 * d * 2 - 10 * d) / 2;
            pill(left + 14 * d, bottom - 50 * d, left + 14 * d + bw, bottom - 12 * d, "Continue", true, 14 * d);
            pill(left + 24 * d + bw, bottom - 50 * d, right - 14 * d, bottom - 12 * d, "Change", false, 14 * d);
            float t2 = h * 0.7f;
            fill(left, t2, right, t2 + h * 0.24f, 18 * d, PANEL);
            fill(left + 14 * d, t2 + 14 * d, left + 40 * d, t2 + 40 * d, 13 * d, 0xFF3A3F66);
            text("Bedtime", left + 52 * d, t2 + 24 * d, 15 * d, WHITE, 0, true, false);
            text("When did you go to sleep?", left + 52 * d, t2 + 42 * d, 12.5f * d, MUTED, 0, false, false);
        }

        /** Change plan: the three ways to change a slot, and the switch for the whole slot. */
        void change() {
            float left = w * 0.08f, right = w * 0.92f, top = h * 0.03f, bottom = h * 0.97f;
            fill(left, top, right, bottom, 18 * d, PANEL);
            text("Change plan", left + 16 * d, top + 28 * d, 17 * d, WHITE, 0, true, false);
            String[] names = {"Replace slot", "Continue previous slot", "Swap slot with"};
            float rowH = (bottom - top - 118 * d) / 3f;
            for (int i = 0; i < 3; i++) {
                float t = top + 44 * d + i * rowH;
                fill(left + 12 * d, t, right - 12 * d, t + rowH - 8 * d, 12 * d, i == 0 ? PANEL2 : 0xFF16302D);
                if (i == 0) outline(left + 12 * d, t, right - 12 * d, t + rowH - 8 * d, 12 * d, TEAL, 1.5f * d);
                float cy = t + (rowH - 8 * d) / 2;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * d);
                p.setColor(i == 0 ? TEAL : MUTED);
                c.drawCircle(left + 32 * d, cy, 8 * d, p);
                p.setStyle(Paint.Style.FILL);
                if (i == 0) c.drawCircle(left + 32 * d, cy, 4 * d, p);
                text(names[i], left + 50 * d, cy + 5 * d, 15 * d, i == 0 ? WHITE : 0xFFD3DEDC, 0, i == 0, false);
            }
            float sy = bottom - 40 * d;
            text("Whole slot", left + 18 * d, sy + 5 * d, 14 * d, WHITE, 0, false, false);
            fill(right - 70 * d, sy - 11 * d, right - 18 * d, sy + 13 * d, 12 * d, TEAL);
            p.setColor(0xFF001B1A);
            c.drawCircle(right - 30 * d, sy + d, 9 * d, p);
        }

        /** Breaks, to-dos and goals: the break control, a short to-do list with ticks, and a streak. */
        void todo() {
            float left = w * 0.08f, right = w * 0.92f, top = h * 0.03f;
            float bh = h * 0.2f;
            fill(left, top, right, top + bh, 16 * d, PANEL);
            text("Take a break", left + 16 * d, top + bh / 2 + 5 * d, 15 * d, WHITE, 0, true, false);
            fill(right - 142 * d, top + bh / 2 - 15 * d, right - 112 * d, top + bh / 2 + 15 * d, 15 * d, PANEL2);
            text("−", right - 127 * d, top + bh / 2 + 7 * d, 20 * d, WHITE, 1, true, false);
            text("10 min", right - 78 * d, top + bh / 2 + 5 * d, 14 * d, TEAL, 1, true, false);
            fill(right - 44 * d, top + bh / 2 - 15 * d, right - 14 * d, top + bh / 2 + 15 * d, 15 * d, PANEL2);
            text("+", right - 29 * d, top + bh / 2 + 7 * d, 20 * d, WHITE, 1, true, false);
            String[] tasks = {"Read chapter 3", "Practice questions", "Revise notes"};
            float t0 = top + bh + 10 * d, rowH = h * 0.17f;
            for (int i = 0; i < 3; i++) {
                float t = t0 + i * rowH;
                fill(left, t, right, t + rowH - 6 * d, 12 * d, PANEL);
                float cy = t + (rowH - 6 * d) / 2;
                if (i == 0) {
                    fill(left + 14 * d, cy - 9 * d, left + 32 * d, cy + 9 * d, 5 * d, TEAL);
                    line(left + 18 * d, cy, left + 22 * d, cy + 4 * d, 0xFF001B1A, 2.2f * d);
                    line(left + 22 * d, cy + 4 * d, left + 29 * d, cy - 5 * d, 0xFF001B1A, 2.2f * d);
                } else {
                    outline(left + 14 * d, cy - 9 * d, left + 32 * d, cy + 9 * d, 5 * d, MUTED, 1.6f * d);
                }
                text(tasks[i], left + 44 * d, cy + 5 * d, 14.5f * d, i == 0 ? MUTED : WHITE, 0, false, false);
            }
            float gy = t0 + 3 * rowH + 4 * d;
            fill(left, gy, right, h * 0.97f, 12 * d, PANEL2);
            text("Goal: 2 h of Study", left + 14 * d, (gy + h * 0.97f) / 2 + 5 * d, 14 * d, WHITE, 0, false, false);
            text("5 days in a row", right - 14 * d, (gy + h * 0.97f) / 2 + 5 * d, 13 * d, AMBER, 2, true, false);
        }

        /** Make it yours: Light, Night and Auto, the colour looks, and the languages. */
        void look() {
            float left = w * 0.08f, right = w * 0.92f;
            String[] themes = {"Light", "Night", "Auto"};
            float cw = (right - left - 2 * 10 * d) / 3, ty = h * 0.03f, th = h * 0.22f;
            for (int i = 0; i < 3; i++) {
                float l = left + i * (cw + 10 * d);
                fill(l, ty, l + cw, ty + th, 14 * d, i == 0 ? 0xFFEFF3F2 : (i == 1 ? 0xFF0C1716 : PANEL2));
                if (i == 1) outline(l, ty, l + cw, ty + th, 14 * d, TEAL, 2 * d);
                text(themes[i], l + cw / 2, ty + th / 2 + 5 * d, 14 * d, i == 0 ? 0xFF10201E : WHITE, 1, i == 1, false);
            }
            int[] looks = {TEAL, CORAL, BLUE, 0xFF7B8794};
            float dy = h * 0.37f;
            for (int i = 0; i < looks.length; i++) {
                float cx = left + 22 * d + i * 58 * d;
                p.setStyle(Paint.Style.FILL);
                p.setColor(looks[i]);
                c.drawCircle(cx, dy, 19 * d, p);
                if (i == 0) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.5f * d);
                    p.setColor(WHITE);
                    c.drawCircle(cx, dy, 25 * d, p);
                    p.setStyle(Paint.Style.FILL);
                }
            }
            String[] langs = {"English", "हिन्दी", "मराठी", "ગુજરાતી", "বাংলা", "தமிழ்", "తెలుగు"};
            float lx = left, ly = h * 0.52f, lh = 28 * d;
            for (int i = 0; i < langs.length; i++) {
                p.setTextSize(12.5f * d);
                float lw = p.measureText(langs[i]) + 22 * d;
                if (lx + lw > right) {
                    lx = left;
                    ly += lh + 8 * d;
                }
                outline(lx, ly, lx + lw, ly + lh, lh / 2, i == 0 ? TEAL : MUTED, 1.3f * d);
                text(langs[i], lx + lw / 2, ly + lh / 2 + 4.5f * d, 13 * d, i == 0 ? TEAL : WHITE, 1, i == 0, false);
                lx += lw + 8 * d;
            }
        }

        void pause() {
            float ph = h * 0.96f, top = h * 0.02f, cx = w / 2;
            phone(cx, top, ph);
            float pw = ph * 0.52f;
            text("Social app", cx, top + ph * 0.2f, pw * 0.12f, WHITE, 1, true, false);
            text("Right now: Study.", cx, top + ph * 0.27f, pw * 0.07f, MUTED, 1, false, false);
            text("Is this the best use of", cx, top + ph * 0.31f, pw * 0.07f, MUTED, 1, false, false);
            text("the next few minutes?", cx, top + ph * 0.35f, pw * 0.07f, MUTED, 1, false, false);
            text("7", cx, top + ph * 0.56f, pw * 0.34f, TEAL, 1, false, true);
            pill(cx - pw * 0.38f, top + ph * 0.66f, cx + pw * 0.38f, top + ph * 0.75f, "Go back", true, pw * 0.085f);
            pill(cx - pw * 0.38f, top + ph * 0.79f, cx + pw * 0.38f, top + ph * 0.88f, "Open anyway in 7", false, pw * 0.07f);
        }

        void report() {
            float cx = w * 0.25f, cy = h * 0.4f, rad = Math.min(w * 0.19f, h * 0.3f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(10 * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(PANEL2);
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            c.drawArc(r, 0, 360, false, p);
            p.setColor(TEAL);
            c.drawArc(r, -90, 295, false, p);
            p.setStyle(Paint.Style.FILL);
            text("82", cx, cy + 10 * d, 30 * d, WHITE, 1, true, false);
            text("Today's score", cx, cy + rad + 24 * d, 12 * d, MUTED, 1, false, false);

            float bl = w * 0.52f, br = w * 0.95f, bb = h * 0.62f, bt = h * 0.12f;
            int[] vals = {1, 2, 1, 0, 0, 3, 5, 7, 4, 6, 8, 5};
            float bw = (br - bl) / vals.length;
            for (int i = 0; i < vals.length; i++) {
                float bh = (bb - bt) * vals[i] / 8f;
                fill(bl + i * bw + 2 * d, bb - bh, bl + (i + 1) * bw - 2 * d, bb, 3 * d, i == 10 ? AMBER : PANEL2);
            }
            text("Phone use by hour", bl, bb + 20 * d, 12 * d, MUTED, 0, false, false);

            float gl = w * 0.08f, gr = w * 0.92f, gt = h * 0.84f;
            text("Study  5h 40m of 6h", gl, gt - 6 * d, 13 * d, WHITE, 0, true, false);
            text("5-day streak", gr, gt - 6 * d, 13 * d, AMBER, 2, true, false);
            fill(gl, gt + 2 * d, gr, gt + 10 * d, 4 * d, PANEL2);
            fill(gl, gt + 2 * d, gl + (gr - gl) * 0.94f, gt + 10 * d, 4 * d, TEAL);
        }

        void backup() {
            float cx = w * 0.3f, cy = h * 0.42f, s = Math.min(w * 0.22f, h * 0.3f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(PANEL2);
            c.drawCircle(cx - s * 0.5f, cy + s * 0.1f, s * 0.42f, p);
            c.drawCircle(cx + s * 0.05f, cy - s * 0.15f, s * 0.58f, p);
            c.drawCircle(cx + s * 0.62f, cy + s * 0.12f, s * 0.4f, p);
            r.set(cx - s * 0.5f, cy + s * 0.1f, cx + s * 0.62f, cy + s * 0.52f);
            c.drawRect(r.left, r.top, r.right, r.bottom, p);
            arrow(cx, cy + s * 0.42f, cx, cy - s * 0.2f, TEAL);
            text("Your own Google Drive", cx, cy + s + 26 * d, 13 * d, WHITE, 1, true, false);
            text("private to you", cx, cy + s + 44 * d, 12 * d, MUTED, 1, false, false);

            float bx = w * 0.74f, by = h * 0.4f;
            text("Daily, weekly", bx, by - 6 * d, 14 * d, WHITE, 1, true, false);
            text("or monthly", bx, by + 14 * d, 14 * d, WHITE, 1, true, false);
            text("Restore on a", bx, by + 40 * d, 12 * d, MUTED, 1, false, false);
            text("new phone", bx, by + 56 * d, 12 * d, MUTED, 1, false, false);
        }

        void lock() {
            float ph = h * 0.96f, top = h * 0.02f, cx = w * 0.3f;
            phone(cx, top, ph);
            float pw = ph * 0.52f;
            text("Social app", cx, top + ph * 0.2f, pw * 0.12f, WHITE, 1, true, false);
            text("Locked  ·  Social", cx, top + ph * 0.27f, pw * 0.07f, TEAL, 1, false, false);
            text("Until 6:00 AM", cx, top + ph * 0.38f, pw * 0.1f, WHITE, 1, false, true);
            text("4h 20m left", cx, top + ph * 0.44f, pw * 0.07f, MUTED, 1, false, false);
            pill(cx - pw * 0.38f, top + ph * 0.54f, cx + pw * 0.38f, top + ph * 0.63f, "Go back", true, pw * 0.085f);
            pill(cx - pw * 0.38f, top + ph * 0.67f, cx + pw * 0.38f, top + ph * 0.76f, "Break the lock", false, pw * 0.07f);
            float edge = cx + pw / 2 + 8 * d, tx = edge + 40 * d;
            float y1 = top + ph * 0.38f;
            arrow(tx - 6 * d, y1, edge, y1, TEAL);
            text("Shows when it", tx, y1 + 4 * d, 13 * d, TEAL, 0, true, false);
            text("opens again", tx, y1 + 20 * d, 13 * d, TEAL, 0, true, false);
            float y2 = top + ph * 0.715f;
            arrow(tx - 6 * d, y2, edge, y2, WHITE);
            text("Wait, then type", tx, y2 + 4 * d, 13 * d, WHITE, 0, true, false);
            text("a sentence", tx, y2 + 20 * d, 12 * d, MUTED, 0, false, false);
            text("Strict locks (Pro)", tx, y2 + 42 * d, 12 * d, AMBER, 0, true, false);
            text("can't be broken", tx, y2 + 58 * d, 12 * d, MUTED, 0, false, false);
        }

        void pro() {
            float top = h * 0.05f, bottom = h * 0.9f, gap = 12 * d;
            float lw = (w * 0.9f - gap) / 2, l1 = w * 0.05f, l2 = l1 + lw + gap;
            fill(l1, top, l1 + lw, bottom, 18 * d, PANEL);
            fill(l2, top, l2 + lw, bottom, 18 * d, PANEL);
            outline(l2, top, l2 + lw, bottom, 18 * d, AMBER, 1.6f * d);
            text("Free", l1 + 16 * d, top + 30 * d, 17 * d, WHITE, 0, true, false);
            text("Pro", l2 + 16 * d, top + 30 * d, 17 * d, AMBER, 0, true, false);
            String[] free = {"Home screen", "Alarms and to-dos", "Today's report", "1 paused app", "1 locked app", "One routine"};
            String[] pro = {"No ads", "Goals with to-dos", "All saved days", "Strict pauses", "Locked groups", "More routines"};
            float y = top + 62 * d, step = Math.min(26 * d, (bottom - top - 100 * d) / 6f);
            for (int i = 0; i < free.length; i++) {
                text(free[i], l1 + 16 * d, y + i * step, 13 * d, MUTED, 0, false, false);
                text(pro[i], l2 + 16 * d, y + i * step, 13 * d, WHITE, 0, false, false);
            }
            text("Monthly or once, or 3 short videos for a day", w / 2, bottom + 22 * d, 12 * d, MUTED, 1, false, false);
        }

        void wallpaper() {
            float ph = h * 0.96f, top = h * 0.02f, cx = w * 0.3f;
            phone(cx, top, ph);
            poster(cx, top, ph);
            float pw = ph * 0.52f, edge = cx + pw / 2 + 8 * d, tx = edge + 40 * d;
            float t0 = top + ph * 0.21f, rowH = ph * 0.105f;
            // time and date
            float y1 = top + ph * 0.085f;
            arrow(tx - 6 * d, y1, edge, y1, WHITE);
            text("Time, date", tx, y1 + 4 * d, 13 * d, WHITE, 0, true, false);
            text("and phone time", tx, y1 + 20 * d, 12 * d, MUTED, 0, false, false);
            // the slot you are in
            float y2 = t0 + rowH * 1.5f;
            arrow(tx - 6 * d, y2, edge, y2, TEAL);
            text("The slot you", tx, y2 + 4 * d, 13 * d, TEAL, 0, true, false);
            text("are in", tx, y2 + 20 * d, 13 * d, TEAL, 0, true, false);
            // every slot
            float y3 = t0 + rowH * 4.5f;
            arrow(tx - 6 * d, y3, edge, y3, WHITE);
            text("Every slot", tx, y3 + 4 * d, 13 * d, WHITE, 0, true, false);
            text("of your day", tx, y3 + 20 * d, 12 * d, MUTED, 0, false, false);
            // built in
            text("Part of the", tx, top + ph * 0.93f - 16 * d, 12 * d, MUTED, 0, false, false);
            text("home screen", tx, top + ph * 0.93f, 12 * d, MUTED, 0, false, false);
        }
    }
}
