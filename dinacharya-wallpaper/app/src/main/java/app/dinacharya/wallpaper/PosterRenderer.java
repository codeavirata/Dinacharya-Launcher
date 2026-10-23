package app.dinacharya.wallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Draws the day poster. The wallpaper and the preview in the setup screen both use it.
 * All sizes are in "design units": 1 unit = screen width / 1080.
 */
final class PosterRenderer {

    private static final class Palette {
        final int bg, card, ink, muted, line, accent, accentInk;
        final int[] k, t;

        Palette(int bg, int card, int ink, int muted, int line, int accent, int accentInk, int[] k, int[] t) {
            this.bg = bg;
            this.card = card;
            this.ink = ink;
            this.muted = muted;
            this.line = line;
            this.accent = accent;
            this.accentInk = accentInk;
            this.k = k;
            this.t = t;
        }
    }

    // Order matches Routine kinds: task, rest, meal, personal, sleep.
    private static final Palette TEAL_LIGHT = new Palette(
            0xFFF3F7F6, 0xFFFFFFFF, 0xFF10201E, 0xFF587270, 0xFFCFDCDA, 0xFF0E7C7B, 0xFFFFFFFF,
            new int[]{0xFF0E7C7B, 0xFFC98A12, 0xFFC4553B, 0xFF4A6C94, 0xFF404685},
            new int[]{0xFFDDF0EE, 0xFFFBEFD3, 0xFFF8E1DA, 0xFFE0E9F3, 0xFFE3E4F2});
    private static final Palette TEAL_NIGHT = new Palette(
            0xFF0C1716, 0xFF142524, 0xFFE8F2F0, 0xFF8FA8A5, 0xFF274340, 0xFF3CC0BC, 0xFF06201F,
            new int[]{0xFF3CC0BC, 0xFFEDB84A, 0xFFE58A72, 0xFF86A9D2, 0xFF9A9FE0},
            new int[]{0xFF12302F, 0xFF33290F, 0xFF3A1F18, 0xFF1A2B3F, 0xFF1E2142});

    private static final Palette SUNSET_LIGHT = make(
            0xFFFBF3EA, 0xFFFFFFFF, 0xFF2A1810, 0xFF8A6A5C, 0xFFEBD8C9, 0xFFD4552B, 0xFFFFFFFF,
            new int[]{0xFFD4552B, 0xFFD99A1E, 0xFFB0305A, 0xFF7A5BA6, 0xFF4B3F86}, false);
    private static final Palette SUNSET_NIGHT = make(
            0xFF1B120E, 0xFF2A1C16, 0xFFF8EBE1, 0xFFBA9C8C, 0xFF48342B, 0xFFFF8E5E, 0xFF2A1208,
            new int[]{0xFFFF8E5E, 0xFFF2BC52, 0xFFEF6F95, 0xFFB59BE0, 0xFF9C94E6}, true);
    private static final Palette OCEAN_LIGHT = make(
            0xFFF0F5FB, 0xFFFFFFFF, 0xFF0D1C30, 0xFF5B7089, 0xFFD2DEEB, 0xFF1769C2, 0xFFFFFFFF,
            new int[]{0xFF1769C2, 0xFFE0952B, 0xFFD9573F, 0xFF1F9487, 0xFF3B3A9B}, false);
    private static final Palette OCEAN_NIGHT = make(
            0xFF08111F, 0xFF0F1C31, 0xFFE5EEF9, 0xFF8CA4C2, 0xFF20375A, 0xFF5BAAFF, 0xFF06182E,
            new int[]{0xFF5BAAFF, 0xFFF0B050, 0xFFF08A74, 0xFF4FCDBB, 0xFF9496F0}, true);
    private static final Palette GRAPHITE_LIGHT = make(
            0xFFF3F3F1, 0xFFFFFFFF, 0xFF181818, 0xFF6A6A68, 0xFFDCDCD8, 0xFF2B2B2B, 0xFFFFFFFF,
            new int[]{0xFF2B3440, 0xFFA17C2F, 0xFF9C4A3B, 0xFF4D6A78, 0xFF4A4C6B}, false);
    private static final Palette GRAPHITE_NIGHT = make(
            0xFF0E0E0F, 0xFF19191B, 0xFFEEEEEE, 0xFF9A9A98, 0xFF2E2E31, 0xFFE4E4E2, 0xFF111111,
            new int[]{0xFFB8C4D4, 0xFFD4B46A, 0xFFD98B7B, 0xFF8FB0C0, 0xFF9EA0C8}, true);

    // Indexed by Routine.PAL_*: light, then night.
    private static final Palette[][] PALETTES = {
            {TEAL_LIGHT, TEAL_NIGHT}, {SUNSET_LIGHT, SUNSET_NIGHT}, {OCEAN_LIGHT, OCEAN_NIGHT}, {GRAPHITE_LIGHT, GRAPHITE_NIGHT}};

    /** Builds a palette, working out the soft background of each kind from its colour. */
    private static Palette make(int bg, int card, int ink, int muted, int line, int accent, int accentInk,
                                int[] k, boolean night) {
        int[] t = new int[k.length];
        for (int i = 0; i < k.length; i++) t[i] = night ? mix(k[i], bg, 0.80f) : mix(k[i], 0xFFFFFFFF, 0.86f);
        return new Palette(bg, card, ink, muted, line, accent, accentInk, k, t);
    }

    /** Blends colour a toward colour b: 0 gives a, 1 gives b. */
    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static Palette palette(int id, boolean night) {
        int i = (id >= 0 && id < PALETTES.length) ? id : 0;
        return PALETTES[i][night ? 1 : 0];
    }

    /**
     * Colours for the theme picker: background, ink, accent, then the five kind colours.
     * Used by the setup screen so its swatches match the wallpaper exactly.
     */
    /** Whether the poster is drawn in its night colours: the routine's own choice, or the phone's when it says Auto. */
    static boolean isNight(Routine r, boolean systemNight) {
        return r.theme == Routine.THEME_NIGHT || (r.theme == Routine.THEME_AUTO && systemNight);
    }

    static int[] swatch(int paletteId, boolean night) {
        Palette p = palette(paletteId, night);
        return new int[]{p.bg, p.ink, p.accent, p.k[0], p.k[1], p.k[2], p.k[3], p.k[4]};
    }

    private final RectF markRect = new RectF();
    private static final Typeface REG = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private static final Typeface MONO = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);
    private static final Typeface MONO_B = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

    private static final float GAP = 8f;
    private static final float MIN_SCALE = 0.7f;
    private static final float MAX_SCALE = 1.2f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();
    private final Path path = new Path();
    private final Path cut = new Path();

    /** The to-do list and break of the slot going on, for the list that is being drawn (see {@link #drawLive}). */
    private PosterExtras.Now live = PosterExtras.NOTHING;
    /** How many to-do lines the current card has in the list being drawn (0 when none). */
    private int curLines;
    private static final float TODO_LINE = 34f;
    private static final int MAX_TODO_LINES = 8;
    /** Which page of a long to-do list is showing (it moves on every {@link Todos#PAGE_MS}). */
    private long liveTick;
    /** Whether the list drawn last was longer than its lines, so it keeps moving and the screen has to be redrawn soon. */
    private boolean scrolling;
    private static final float TODO_COMFORT_SCALE = 0.85f;

    // ------------------------------------------------------------------ entry point

    /**
     * @param day the day as it stands now: the saved routine with any temporary changes already applied
     *          (see {@link Plan#resolve}). The clock and date come from nowMs.
     */
    void draw(Canvas c, int w, int h, Plan.DayPlan day, long nowMs, boolean systemNight, ScreenTime.Result screen) {
        draw(c, w, h, day, null, nowMs, systemNight, screen);
    }

    /**
     * Draws the poster for {@code nowMs} from the saved routine: finds the running day and, between midnight and the
     * next wake-up time, the scroll from the old day to the new one.
     */
    void drawLive(Canvas c, int w, int h, Routine base, Overrides ov, long nowMs, ZoneId zone, boolean systemNight,
                  ScreenTime.Result screen) {
        drawLive(c, w, h, base, ov, nowMs, zone, systemNight, screen, PosterExtras.NONE);
    }

    /** Same, with the to-do list of the slot that is going on and a note while a break is on. */
    void drawLive(Canvas c, int w, int h, Routine base, Overrides ov, long nowMs, ZoneId zone, boolean systemNight,
                  ScreenTime.Result screen, PosterExtras extras) {
        Plan.DayPlan day = Plan.resolve(base, ov, nowMs, zone);
        live = extras == null ? PosterExtras.NOTHING : extras.at(day, nowMs);
        liveTick = nowMs / Todos.PAGE_MS;
        scrolling = false;
        try {
            draw(c, w, h, day, Plan.transition(base, ov, day, nowMs, zone), nowMs, systemNight, screen);
        } finally {
            live = PosterExtras.NOTHING;
        }
    }

    /**
     * @param change when not null, the old day (day) scrolls up and out of the list area while the next one rises into it
     */
    void draw(Canvas c, int w, int h, Plan.DayPlan day, Plan.Transition change, long nowMs, boolean systemNight,
              ScreenTime.Result screen) {
        Routine r = day.routine;
        boolean night = isNight(r, systemNight);
        Palette pal = palette(r.palette, night);
        float u = w / 1080f;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMs);

        background(c, w, h, pal);
        // Past the halfway point of the scroll the wake, first task and bed times in the header are the new day's.
        Plan.DayPlan head = (change != null && change.progress >= 0.5f) ? change.to : day;
        float listTop = header(c, w, u, h * r.topPct / 100f, pal, head.sc, r, cal, screen, head.adjusted);
        float bottom = h - h * r.bottomPct / 100f;
        if (r.showMark) {
            mark(c, w, u, bottom - 22 * u, pal);
            bottom -= 58 * u;
        }
        if (change == null) {
            list(c, w, u, listTop, bottom, pal, day.sc, r, day.cur, day.tt, true);
            return;
        }
        // The old schedule goes up and out under the header; the new one comes up from below and, by the wake-up time,
        // fills the list area exactly where the new day will be drawn.
        float span = (bottom - listTop) + 24 * u;
        float shift = change.progress * span;
        int keep = c.save();
        c.clipRect(0, listTop - 8 * u, w, bottom + 8 * u);
        c.save();
        c.translate(0, -shift);
        list(c, w, u, listTop, bottom, pal, day.sc, r, day.cur, day.tt, true);
        c.restore();
        c.save();
        c.translate(0, span - shift);
        Plan.DayPlan nx = change.to;
        list(c, w, u, listTop, bottom, pal, nx.sc, nx.routine, nx.cur, nx.tt, false);
        c.restore();
        c.restoreToCount(keep);
    }

    /** The small "Developed by ASG" mark under the list: a monogram badge and one line of text. */
    private void mark(Canvas c, int w, float u, float cy, Palette pal) {
        String words = "DEVELOPED BY ASG";
        float size = 19 * u;
        float tw = measure(words, size, BOLD, 0.16f);
        float badge = 34 * u;
        float gap = 12 * u;
        float x0 = (w - (badge + gap + tw)) / 2f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor((pal.accent & 0x00FFFFFF) | 0xD9000000);
        markRect.set(x0, cy - badge / 2f, x0 + badge, cy + badge / 2f);
        c.drawRoundRect(markRect, 9 * u, 9 * u, p);
        text(c, "ASG", x0 + badge / 2f, cy + 5f * u, 13 * u, pal.accentInk, BOLD, Paint.Align.CENTER, 0.02f);
        text(c, words, x0 + badge + gap, cy + 6.5f * u, size, (pal.muted & 0x00FFFFFF) | 0xCC000000, BOLD, Paint.Align.LEFT, 0.16f);
    }

    // ------------------------------------------------------------------ background and header

    private void background(Canvas c, int w, int h, Palette pal) {
        c.drawColor(pal.bg);
        int clear = pal.accent & 0x00FFFFFF;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w * 0.9f, h * 0.03f, w * 0.95f, clear | 0x3A000000, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private float header(Canvas c, int w, float u, float top, Palette pal, Routine.Schedule sc, Routine r,
                         Calendar cal, ScreenTime.Result screen, boolean adjusted) {
        float m = 56 * u;
        float right = w - m;
        Locale loc = Lang.locale();
        String weekday = new SimpleDateFormat("EEEE", loc).format(cal.getTime()).toUpperCase(loc);
        String dayNum = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
        String month = new SimpleDateFormat("MMMM", loc).format(cal.getTime());
        String year = String.valueOf(cal.get(Calendar.YEAR));
        float baseline = top + 190 * u;

        // Clock text, if shown.
        String digits = null;
        String ap = null;
        if (r.showClock) {
            int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            String full = Routine.formatTime(nowMin, r.fmt24);
            int sp = full.indexOf(' ');
            digits = sp > 0 ? full.substring(0, sp) : full;
            ap = sp > 0 ? full.substring(sp + 1) : null;
        }

        if (digits != null) {
            // Time on the left, date on the right.
            float apW = ap != null ? measure(ap, 38 * u, BOLD, 0.1f) : 0;
            float apGap = ap != null ? 14 * u : 0;
            float clockSize = 104 * u;
            float monthSize = 58 * u;
            float dayW = measure(dayNum, 176 * u, BLACK, -0.02f);
            float avail = w - 2 * m - 28 * u;
            // The date gives way a little first, then the clock, so the clock stays easy to read.
            while (dayW + 28 * u + Math.max(measure(month, monthSize, MED, 0f), measure(year, 42 * u, REG, 0.05f))
                    + measure(digits, clockSize, MED, -0.02f) + apW + apGap > avail && monthSize > 42 * u) {
                monthSize *= 0.94f;
            }
            while (dayW + 28 * u + Math.max(measure(month, monthSize, MED, 0f), measure(year, 42 * u, REG, 0.05f))
                    + measure(digits, clockSize, MED, -0.02f) + apW + apGap > avail && clockSize > 40 * u) {
                clockSize *= 0.94f;
            }
            float textW = Math.max(measure(month, monthSize, MED, 0f), measure(year, 42 * u, REG, 0.05f));
            text(c, weekday, right, top + 34 * u, 34 * u, pal.accent, BOLD, Paint.Align.RIGHT, 0.22f);
            text(c, year, right, baseline, 42 * u, pal.muted, REG, Paint.Align.RIGHT, 0.05f);
            text(c, month, right, baseline - 64 * u, monthSize, pal.ink, MED, Paint.Align.RIGHT, 0f);
            text(c, dayNum, right - textW - 28 * u, baseline, 176 * u, pal.ink, BLACK, Paint.Align.RIGHT, -0.02f);

            float x = m;
            float dw = text(c, digits, x, baseline, clockSize, pal.ink, MED, Paint.Align.LEFT, -0.02f);
            if (ap != null) text(c, ap, x + dw + apGap, baseline, 38 * u, pal.accent, BOLD, Paint.Align.LEFT, 0.1f);
            if (adjusted) pill(c, "ADJUSTED FOR TODAY", m, top + 34 * u, 24 * u, pal.k[Routine.REST], pal, false);
        } else {
            // No clock: the date stays on the left, as before.
            text(c, weekday, m, top + 34 * u, 34 * u, pal.accent, BOLD, Paint.Align.LEFT, 0.22f);
            float dw = text(c, dayNum, m - 4 * u, baseline, 176 * u, pal.ink, BLACK, Paint.Align.LEFT, -0.02f);
            float mx = m + dw + 28 * u;
            text(c, month, mx, baseline - 64 * u, 58 * u, pal.ink, MED, Paint.Align.LEFT, 0f);
            text(c, year, mx, baseline, 42 * u, pal.muted, REG, Paint.Align.LEFT, 0.05f);
            if (adjusted) pill(c, "ADJUSTED FOR TODAY", right, top + 34 * u, 24 * u, pal.k[Routine.REST], pal, true);
        }

        String first = sc.firstTaskStart >= 0 ? Routine.formatTime(sc.firstTaskStart, r.fmt24) : "none";
        String strip = "WAKE " + Routine.formatTime(sc.start, r.fmt24)
                + "   FIRST TASK " + first
                + "   " + (sc.hasSleep ? "BED " : "END ") + Routine.formatTime(sc.bedStart, r.fmt24);
        float size = 27 * u;
        float maxW = w - 2 * m;
        while (measure(strip, size, MED, 0.08f) > maxW && size > 12 * u) size *= 0.95f;
        float sy = baseline + 62 * u;
        text(c, strip, m, sy, size, pal.muted, MED, Paint.Align.LEFT, 0.08f);
        if (r.showScreen && screen != null) {
            return screenBlock(c, w, u, sy + 40 * u, pal, screen, cal.get(Calendar.HOUR_OF_DAY), r.fmt24);
        }
        return sy + 46 * u;
    }

    /**
     * A small rounded tag with letter-spaced capitals. The baseline is where the text sits; x is the
     * left edge, or the right edge when alignRight is true. Returns the tag's width.
     */
    private float pill(Canvas c, String label, float x, float baseline, float size, int color, Palette pal,
                       boolean alignRight) {
        float padX = size * 0.62f;
        float tw = measure(label, size, BOLD, 0.1f);
        float wTag = tw + 2 * padX;
        float left = alignRight ? x - wTag : x;
        rect.set(left, baseline - size * 1.18f, left + wTag, baseline + size * 0.48f);
        float rad = rect.height() / 2;
        p.setStyle(Paint.Style.FILL);
        p.setColor((color & 0x00FFFFFF) | 0x30000000);
        c.drawRoundRect(rect, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, size * 0.07f));
        p.setColor((color & 0x00FFFFFF) | 0xB0000000);
        c.drawRoundRect(rect, rad, rad, p);
        text(c, label, left + padX, baseline, size, color, BOLD, Paint.Align.LEFT, 0.1f);
        return wTag;
    }

    /** "Phone today": the total on the left and a bar for each hour of the day on the right. */
    private float screenBlock(Canvas c, int w, float u, float top, Palette pal, ScreenTime.Result s,
                              int nowHour, boolean h24) {
        float m = 56 * u;
        text(c, "PHONE TODAY", m, top + 22 * u, 24 * u, pal.muted, MED, Paint.Align.LEFT, 0.12f);
        int mins = (int) Math.round(s.totalMs / 60000.0);
        text(c, Routine.formatDuration(mins), m, top + 74 * u, 52 * u, pal.ink, MED, Paint.Align.LEFT, 0f);

        float x0 = m + 330 * u;
        float x1 = w - m;
        float gap = 4 * u;
        float bw = (x1 - x0 - gap * 23) / 24f;
        float base = top + 62 * u;
        float maxH = 56 * u;
        p.setStyle(Paint.Style.FILL);
        for (int hr = 0; hr < 24; hr++) {
            float frac = Math.min(1f, s.hourMs[hr] / 3600000f);
            float bh = Math.max(4 * u, frac * maxH);
            int col;
            if (hr == nowHour) col = pal.accent;
            else if (hr < nowHour) col = (pal.accent & 0x00FFFFFF) | 0x8C000000;
            else col = pal.line;
            p.setColor(col);
            float bx = x0 + hr * (bw + gap);
            rect.set(bx, base - bh, bx + bw, base);
            float rad = Math.min(bw / 2, 5 * u);
            c.drawRoundRect(rect, rad, rad, p);
        }
        String[] labels = h24 ? new String[]{"00", "06", "12", "18"} : new String[]{"12a", "6a", "12p", "6p"};
        for (int i = 0; i < 4; i++) {
            text(c, labels[i], x0 + i * 6 * (bw + gap), base + 28 * u, 19 * u, pal.muted, REG, Paint.Align.LEFT, 0f);
        }
        return top + 104 * u;
    }

    // ------------------------------------------------------------------ list

    private static float baseHeight(int kind) {
        switch (kind) {
            case Routine.TASK: return 124f;
            case Routine.REST: return 70f;
            case Routine.SLEEP: return 92f;
            default: return 104f;
        }
    }

    private static float rowHeight(int kind, boolean current) {
        float b = baseHeight(kind);
        return current ? Math.max(b + 40f, 128f) : b;
    }

    /** The height of a row; the card of the slot going on grows to hold {@code lines} lines of the to-do list under its title. */
    private static float rowHeight(int kind, boolean current, int lines) {
        float h = rowHeight(kind, current);
        return current && lines > 0 ? Math.max(h, TODO_LINE * lines + 130f) : h;
    }

    private static float need(Routine.Schedule sc, int lo, int hi, int cur, int lines) {
        float t = 0;
        for (int i = lo; i <= hi; i++) t += rowHeight(sc.slots.get(i).block.kind, i == cur, lines) + GAP;
        return t - GAP;
    }

    /**
     * How many lines of the to-do list the current card gets: as many as fit while the other cards stay a good size, then fewer while they
     * stay readable. None when even one line would squeeze the day too much, so the rest of the layout is never spoiled.
     */
    private int todoLinesFor(Routine.Schedule sc, int cur, float avail) {
        int n = sc.slots.size();
        int most = Math.min(live.items.size(), MAX_TODO_LINES);
        if (most == 0 || cur < 0) return 0;
        float[] steps = {TODO_COMFORT_SCALE, MIN_SCALE};
        for (float limit : steps) {
            for (int k = most; k >= 1; k--) {
                if (avail / need(sc, 0, n - 1, cur, k) >= limit) return k;
            }
        }
        // A crowded day already shows only a window around the current slot; two lines still fit there.
        return avail / need(sc, 0, n - 1, cur, 0) < MIN_SCALE ? Math.min(2, most) : 0;
    }

    private void list(Canvas c, int w, float u, float listTop, float bottom, Palette pal,
                      Routine.Schedule sc, Routine r, int cur, int tt, boolean withLive) {
        int n = sc.slots.size();
        if (n == 0) return;
        float avail = (bottom - listTop) / u;
        int lines = withLive ? todoLinesFor(sc, cur, avail) : 0;
        curLines = lines;

        int lo = 0;
        int hi = n - 1;
        float s = avail / need(sc, lo, hi, cur, lines);
        if (s < MIN_SCALE) {
            // Too many blocks for this screen: show a window that keeps the current slot in view.
            int focus = cur;
            if (focus < 0) {
                focus = n - 1;
                for (int i = 0; i < n; i++) {
                    if (sc.slots.get(i).start > tt) {
                        focus = i;
                        break;
                    }
                }
            }
            lo = Math.max(0, focus - 2);
            hi = lo;
            while (hi + 1 < n && need(sc, lo, hi + 1, cur, lines) * MIN_SCALE <= avail) hi++;
            while (lo > 0 && need(sc, lo - 1, hi, cur, lines) * MIN_SCALE <= avail) lo--;
            s = avail / need(sc, lo, hi, cur, lines);
        }
        s = Math.min(s, MAX_SCALE);
        float unit = u * s;

        float m = 56 * u;
        float timeRight = m + 190 * u;
        float railX = m + 214 * u;
        float cardL = m + 246 * u;
        float cardR = w - m;

        int cnt = hi - lo + 1;
        float[] tops = new float[cnt];
        float[] hs = new float[cnt];
        float y = listTop;
        for (int i = 0; i < cnt; i++) {
            int idx = lo + i;
            hs[i] = rowHeight(sc.slots.get(idx).block.kind, idx == cur, lines) * unit;
            tops[i] = y;
            y += hs[i] + GAP * unit;
        }

        // Rail: grey for the whole day, accent for the part already done.
        float railTop = tops[0] + hs[0] / 2;
        float railBottom = tops[cnt - 1] + hs[cnt - 1] / 2;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(3.5f * u);
        p.setColor(pal.line);
        c.drawLine(railX, railTop, railX, railBottom, p);
        if (cur >= lo && cur <= hi) {
            p.setColor(pal.accent);
            c.drawLine(railX, railTop, railX, tops[cur - lo] + hs[cur - lo] / 2, p);
        } else if (cur < 0 && tt >= sc.slots.get(hi).end) {
            p.setColor(pal.accent);
            c.drawLine(railX, railTop, railX, railBottom, p);
        }

        for (int i = 0; i < cnt; i++) {
            int idx = lo + i;
            Routine.Slot sl = sc.slots.get(idx);
            boolean isCur = idx == cur;
            boolean past = !isCur && sl.end <= tt;
            row(c, w, u, unit, tops[i], hs[i], timeRight, railX, cardL, cardR, pal, r, sl, isCur, past, tt);
        }
    }

    private void row(Canvas c, int w, float u, float unit, float top, float h, float timeRight, float railX,
                     float cardL, float cardR, Palette pal, Routine r, Routine.Slot sl,
                     boolean isCur, boolean past, int tt) {
        int kind = sl.block.kind;
        int kc = pal.k[kind];
        int kt = pal.t[kind];
        boolean tinted = kind == Routine.REST || kind == Routine.SLEEP;
        boolean twoLine = kind != Routine.REST;
        float mid = top + h / 2;

        boolean skipped = sl.block.skipped;
        boolean renamed = sl.block.changed && !skipped;
        boolean done = past && !skipped;

        int saved = -1;
        if (past || skipped) {
            rect.set(0, top - 4 * u, w, top + h + 4 * u);
            saved = c.saveLayerAlpha(rect, 120);
        }

        // Card.
        rect.set(cardL, top, cardR, top + h);
        float radius = 22 * unit;
        if (isCur) {
            rect2.set(cardL - 7 * u, top - 7 * u, cardR + 7 * u, top + h + 7 * u);
            p.setStyle(Paint.Style.FILL);
            p.setColor((kc & 0x00FFFFFF) | 0x38000000);
            c.drawRoundRect(rect2, radius + 7 * u, radius + 7 * u, p);
            p.setColor(kt);
            c.drawRoundRect(rect, radius, radius, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.5f * u);
            p.setColor(kc);
            c.drawRoundRect(rect, radius, radius, p);
        } else if (tinted) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(kt);
            c.drawRoundRect(rect, radius, radius, p);
        } else {
            p.setStyle(Paint.Style.FILL);
            p.setColor(pal.card);
            c.drawRoundRect(rect, radius, radius, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * u);
            p.setColor(pal.line);
            c.drawRoundRect(rect, radius, radius, p);
        }

        // Baselines.
        float titleBase;
        float subBase;
        float chipCy;
        if (isCur) {
            titleBase = top + 52 * unit;
            subBase = top + 88 * unit;
            chipCy = top + 58 * unit;
        } else if (twoLine) {
            titleBase = mid - 4 * unit;
            subBase = mid + 25 * unit;
            chipCy = mid;
        } else {
            titleBase = mid + 12 * unit;
            subBase = titleBase;
            chipCy = mid;
        }

        // Time column.
        text(c, Routine.formatTime(sl.start, r.fmt24), timeRight, titleBase, 32 * unit,
                isCur ? kc : pal.ink, isCur ? MONO_B : MONO, Paint.Align.RIGHT, 0f);
        if (twoLine || isCur) {
            text(c, "to " + Routine.formatTime(sl.end, r.fmt24), timeRight, subBase, 23 * unit,
                    pal.muted, MONO, Paint.Align.RIGHT, 0f);
        }

        // Rail node.
        float nodeR = isCur ? 15 * u : 10 * u;
        p.setStyle(Paint.Style.FILL);
        p.setColor(pal.bg);
        c.drawCircle(railX, mid, nodeR + 6 * u, p);
        p.setColor(skipped ? pal.line : (past ? pal.accent : kc));
        c.drawCircle(railX, mid, nodeR, p);
        if (isCur) {
            p.setColor(pal.bg);
            c.drawCircle(railX, mid, 6 * u, p);
        }

        // Icon chip.
        float chip = 52 * unit;
        float chipL = cardL + 16 * u;
        rect.set(chipL, chipCy - chip / 2, chipL + chip, chipCy + chip / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor((tinted && !isCur) ? pal.card : (isCur ? pal.card : kt));
        c.drawRoundRect(rect, 15 * unit, 15 * unit, p);
        if (done) {
            check(c, chipL + chip / 2, chipCy, chip * 0.5f, pal.accent, 3.2f * unit);
        } else {
            glyph(c, kind, chipL + chip / 2, chipCy, chip * 0.5f, kc, 2.8f * unit);
        }

        // Duration at the right.
        float durRight = cardR - 22 * u;
        String dur = Routine.formatDuration(sl.block.mins);
        float durW = text(c, dur, durRight, titleBase, 24 * unit, pal.muted, MONO, Paint.Align.RIGHT, 0f);

        // Title.
        float titleX = chipL + chip + 18 * u;
        float titleSize = (kind == Routine.TASK ? 38f : 35f) * unit;
        String title = sl.block.title.isEmpty() ? "Untitled" : sl.block.title;
        String tag = skipped ? "TAKEN OFF" : (renamed ? "TODAY ONLY" : null);
        float tagSize = 19 * unit;
        float tagW = tag == null ? 0 : measure(tag, tagSize, BOLD, 0.1f) + tagSize * 1.24f + 14 * u;
        title = fit(title, titleSize, MED, durRight - durW - 16 * u - titleX - tagW);
        float titleW = text(c, title, titleX, titleBase, titleSize, pal.ink, MED, Paint.Align.LEFT, 0f);
        if (skipped) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, 2.6f * unit));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(pal.ink);
            float sy = titleBase - titleSize * 0.3f;
            c.drawLine(titleX, sy, titleX + titleW, sy, p);
        }
        if (tag != null) {
            pill(c, tag, titleX + titleW + 14 * u, titleBase - 2 * unit, tagSize, skipped ? pal.muted : pal.accent, pal, false);
        }

        if (isCur) {
            int left = Math.max(0, sl.end - tt);
            String nowText = live.breakUntilMs > 0
                    ? "ON A BREAK  ·  back at " + clockText(live.breakUntilMs, r.fmt24)
                    : "NOW  ·  " + Routine.formatDuration(left) + " left";
            text(c, nowText, titleX, subBase, 25 * unit, kc, MONO_B, Paint.Align.LEFT, 0f);
            float frac = Math.min(1f, Math.max(0f, (tt - sl.start) / (float) sl.block.mins));
            float bh = 10 * unit;
            float by = top + h - 18 * unit - bh;
            float bx0 = titleX;
            float bx1 = durRight;
            rect.set(bx0, by, bx1, by + bh);
            p.setStyle(Paint.Style.FILL);
            p.setColor((kc & 0x00FFFFFF) | 0x40000000);
            c.drawRoundRect(rect, bh / 2, bh / 2, p);
            if (frac > 0f) {
                rect.set(bx0, by, bx0 + Math.max(bh, (bx1 - bx0) * frac), by + bh);
                p.setColor(kc);
                c.drawRoundRect(rect, bh / 2, bh / 2, p);
            }
            if (curLines > 0) todoBlock(c, unit, titleX, durRight, by, pal, kc);
        } else if (twoLine) {
            String subLine = sl.block.sub == null || sl.block.sub.isEmpty() || Routine.isOther(sl.block.sub) ? Routine.KIND_NAMES[kind] : sl.block.sub;
            text(c, subLine, titleX, subBase, 24 * unit, pal.muted, REG, Paint.Align.LEFT, 0f);
        }

        if (saved >= 0) c.restoreToCount(saved);
    }

    /** Whether the to-do list drawn last moves through its items, so it should be drawn again every {@link Todos#PAGE_MS}. */
    boolean scrolling() {
        return scrolling;
    }

    /**
     * The to-do lines of the current card, ending just above its progress bar. When the list is longer than the room, finished items scroll
     * off the top first; when what is left is still too long the window moves down through it, a page at a time.
     */
    private void todoBlock(Canvas c, float unit, float x0, float x1, float barTop, Palette pal, int accent) {
        List<Todos.Item> items = live.items;
        int k = Math.min(curLines, items.size());
        if (k <= 0) return;
        int start = Todos.windowStart(items, k, liveTick);
        boolean paged = items.size() > k;
        if (Todos.pageCount(items, k) > 1) scrolling = true;
        float size = 27 * unit;
        float box = 22 * unit;
        float textX = x0 + box + 14 * unit;
        for (int i = 0; i < k; i++) {
            Todos.Item it = items.get(start + i);
            float base = barTop - 14 * unit - (k - 1 - i) * TODO_LINE * unit;
            float cy = base - 9 * unit;
            rect2.set(x0, cy - box / 2, x0 + box, cy + box / 2);
            if (it.done) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(pal.accent);
                c.drawRoundRect(rect2, 6 * unit, 6 * unit, p);
                check(c, x0 + box / 2, cy, box * 0.62f, pal.accentInk, 3f * unit);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.6f * unit);
                p.setColor(accent);
                c.drawRoundRect(rect2, 6 * unit, 6 * unit, p);
            }
            float reserve = (i == k - 1 && paged) ? 170 * unit : 0;
            String line = fit(it.text, size, REG, x1 - textX - reserve);
            float tw = text(c, line, textX, base, size, it.done ? pal.muted : pal.ink, it.done ? REG : MED, Paint.Align.LEFT, 0f);
            if (it.done) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, 2.2f * unit));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(pal.muted);
                c.drawLine(textX, base - size * 0.3f, textX + tw, base - size * 0.3f, p);
            }
        }
        if (paged) {
            // Where the window is in the list: "4–8 of 12".
            text(c, (start + 1) + "–" + (start + k) + " of " + items.size(), x1, barTop - 14 * unit, 22 * unit, pal.muted, MONO, Paint.Align.RIGHT, 0f);
        }
    }

    private static String clockText(long ms, boolean h24) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return Routine.formatTime(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE), h24);
    }

    // ------------------------------------------------------------------ glyphs

    private void check(Canvas c, float cx, float cy, float sz, int color, float sw) {
        float h = sz / 2;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        path.reset();
        path.moveTo(cx - h * 0.7f, cy + h * 0.05f);
        path.lineTo(cx - h * 0.2f, cy + h * 0.55f);
        path.lineTo(cx + h * 0.75f, cy - h * 0.5f);
        c.drawPath(path, p);
    }

    private void glyph(Canvas c, int kind, float cx, float cy, float sz, int color, float sw) {
        float h = sz / 2;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        switch (kind) {
            case Routine.TASK:
                rect2.set(cx - h, cy - h, cx + h, cy + h);
                c.drawRoundRect(rect2, sz * 0.22f, sz * 0.22f, p);
                path.reset();
                path.moveTo(cx - h * 0.45f, cy + h * 0.02f);
                path.lineTo(cx - h * 0.1f, cy + h * 0.4f);
                path.lineTo(cx + h * 0.5f, cy - h * 0.36f);
                c.drawPath(path, p);
                break;
            case Routine.REST:
                rect2.set(cx - h * 0.8f, cy - h * 0.1f, cx + h * 0.4f, cy + h * 0.85f);
                c.drawRoundRect(rect2, h * 0.3f, h * 0.3f, p);
                rect2.set(cx + h * 0.15f, cy + h * 0.05f, cx + h * 0.95f, cy + h * 0.6f);
                c.drawArc(rect2, -90f, 180f, false, p);
                c.drawLine(cx - h * 0.45f, cy - h * 0.9f, cx - h * 0.45f, cy - h * 0.42f, p);
                c.drawLine(cx - h * 0.05f, cy - h * 0.9f, cx - h * 0.05f, cy - h * 0.42f, p);
                break;
            case Routine.MEAL:
                c.drawCircle(cx, cy, h * 0.95f, p);
                c.drawCircle(cx, cy, h * 0.45f, p);
                break;
            case Routine.PERSONAL:
                c.drawCircle(cx, cy, h * 0.38f, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    float ca = (float) Math.cos(a);
                    float sa = (float) Math.sin(a);
                    c.drawLine(cx + ca * h * 0.7f, cy + sa * h * 0.7f, cx + ca * h * 1.0f, cy + sa * h * 1.0f, p);
                }
                break;
            default: // SLEEP: a crescent
                path.reset();
                path.addCircle(cx, cy, h * 0.9f, Path.Direction.CW);
                cut.reset();
                cut.addCircle(cx + h * 0.5f, cy - h * 0.35f, h * 0.72f, Path.Direction.CW);
                path.op(cut, Path.Op.DIFFERENCE);
                p.setStyle(Paint.Style.FILL);
                c.drawPath(path, p);
                break;
        }
    }

    // ------------------------------------------------------------------ text helpers

    /** Draws text and returns its width. */
    private float text(Canvas c, String s, float x, float y, float size, int color, Typeface tf,
                       Paint.Align align, float spacing) {
        s = Lang.tr(s);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setTypeface(tf);
        tp.setTextAlign(align);
        tp.setLetterSpacing(spacing);
        c.drawText(s, x, y, tp);
        return tp.measureText(s);
    }

    private float measure(String s, float size, Typeface tf, float spacing) {
        s = Lang.tr(s);
        tp.setTextSize(size);
        tp.setTypeface(tf);
        tp.setLetterSpacing(spacing);
        return tp.measureText(s);
    }

    private String fit(String s, float size, Typeface tf, float maxW) {
        s = Lang.tr(s);
        if (measure(s, size, tf, 0f) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && measure(t + "…", size, tf, 0f) > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim() + "…";
    }
}
