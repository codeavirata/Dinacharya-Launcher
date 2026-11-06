package app.dinacharya.wallpaper;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * The little moving pictures of the setup: one drawn scene for each feature and for each thing the phone is asked to allow, so a new
 * person sees what a feature does (and why a permission is needed) before being asked for anything. Everything is drawn with shapes in
 * code, so there are no image or video files and the words follow the chosen language.
 *
 * A scene is drawn for any moment {@code t} of its loop (0 to 1); the view calls it again and again with a growing {@code t}. The scenes
 * use only android.graphics, so they can also be drawn on a computer (tools/preview) and looked at before they go on a phone.
 */
final class FeatureScenes {
    // the features
    static final int ALARMS = 0, USAGE = 1, FOCUS = 2, HOME = 3, QUIET = 4;
    // what the phone is asked to allow
    static final int NOTIF = 5, EXACT = 6, LOCKSCREEN = 7, BATTERY = 8;
    // all set
    static final int DONE = 9;
    static final int COUNT = 10;
    /** How long one loop of a scene takes. */
    static final long LOOP_MS = 4800L;

    private static final int AMBER = 0xFFFFC66B, BLUE = 0xFF7FB3E6, CORAL = 0xFFE9906F, TEAL = 0xFF63D2CC, LILAC = 0xFFB0A3EA;
    private static final int[] ACCENT = {AMBER, BLUE, CORAL, TEAL, LILAC, AMBER, AMBER, AMBER, AMBER, TEAL};
    private static final int INK = 0xFFF4F6F5;
    private static final int GLASS = 0x1AFFFFFF;
    private static final int EDGE = 0x2EFFFFFF;
    /** The height of a scene in its own units; the width follows the shape of the view. */
    private static final float H = 170f;

    private FeatureScenes() {
    }

    /** The colour a scene is built around (also used for the buttons and ticks that go with it). */
    static int accent(int scene) {
        return scene >= 0 && scene < COUNT ? ACCENT[scene] : AMBER;
    }

    /** Draws scene {@code scene} at moment {@code t} of its loop, filling a box {@code w} by {@code h} pixels. */
    static void draw(Canvas c, int scene, float t, float w, float h) {
        float u = h / H;
        c.save();
        c.scale(u, u, 0f, 0f);
        new Stage(c, w / u, accent(scene)).scene(scene, t - (float) Math.floor(t));
        c.restore();
    }

    // ------------------------------------------------------------------ the stage

    private static final class Stage {
        final Canvas c;
        final float w;
        final int accent;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final Path path = new Path();
        /** The screen of the phone drawn last. */
        final RectF screen = new RectF();

        Stage(Canvas c, float w, int accent) {
            this.c = c;
            this.w = w;
            this.accent = accent;
        }

        // ---------------------------------------------------------- numbers

        float seg(float t, float a, float b) {
            if (t <= a) return 0f;
            if (t >= b) return 1f;
            return (t - a) / (b - a);
        }

        float ease(float x) {
            return x * x * (3f - 2f * x);
        }

        float out(float x) {
            float k = 1f - x;
            return 1f - k * k * k;
        }

        /** Goes past 1 a little and settles: for things that pop in. */
        float back(float x) {
            if (x <= 0f) return 0f;
            if (x >= 1f) return 1f;
            float k = x - 1f;
            return 1f + 2.4f * k * k * k + 1.4f * k * k;
        }

        /** 0 before a, rises to 1 at b, stays, falls to 0 between c and d. */
        float hold(float t, float a, float b, float cc, float d) {
            return Math.min(ease(seg(t, a, b)), 1f - ease(seg(t, cc, d)));
        }

        float lerp(float a, float b, float f) {
            return a + (b - a) * f;
        }

        int alpha(int color, float f) {
            int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
            return (color & 0x00FFFFFF) | (a << 24);
        }

        int mix(int c1, int c2, float f) {
            f = Math.max(0f, Math.min(1f, f));
            int a = Math.round(lerp((c1 >>> 24) & 0xFF, (c2 >>> 24) & 0xFF, f));
            int rr = Math.round(lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, f));
            int g = Math.round(lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, f));
            int b = Math.round(lerp(c1 & 0xFF, c2 & 0xFF, f));
            return (a << 24) | (rr << 16) | (g << 8) | b;
        }

        // ---------------------------------------------------------- shapes

        void fill(float l, float t, float rr, float b, float rad, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            r.set(l, t, rr, b);
            c.drawRoundRect(r, rad, rad, p);
        }

        void edge(float l, float t, float rr, float b, float rad, int color, float width) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setColor(color);
            r.set(l, t, rr, b);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
        }

        void dot(float x, float y, float rad, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(x, y, rad, p);
        }

        void ring(float x, float y, float rad, float width, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setColor(color);
            c.drawCircle(x, y, rad, p);
            p.setStyle(Paint.Style.FILL);
        }

        void line(float x1, float y1, float x2, float y2, float width, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        void stroke(Path pa, float width, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
            c.drawPath(pa, p);
            p.setStyle(Paint.Style.FILL);
        }

        void arc(float x, float y, float rad, float from, float sweep, float width, int color) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
            r.set(x - rad, y - rad, x + rad, y + rad);
            c.drawArc(r, from, sweep, false, p);
            p.setStyle(Paint.Style.FILL);
        }

        /** Text with its middle (align 1), left (0) or right (2) at x; y is the baseline. Translated into the chosen language. */
        void text(String s, float x, float y, float size, int color, int align, boolean bold) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setLetterSpacing(0f);
            p.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextAlign(align == 1 ? Paint.Align.CENTER : align == 2 ? Paint.Align.RIGHT : Paint.Align.LEFT);
            c.drawText(Tr.t(s), x, y, p);
        }

        /** Text that is not words (a time, a number): drawn as it is. */
        void digits(String s, float x, float y, float size, int color, int align, boolean bold) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setLetterSpacing(0f);
            p.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif-light", Typeface.NORMAL));
            p.setTextAlign(align == 1 ? Paint.Align.CENTER : align == 2 ? Paint.Align.RIGHT : Paint.Align.LEFT);
            c.drawText(s, x, y, p);
        }

        float width(String s, float size, boolean bold) {
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
            return p.measureText(Tr.t(s));
        }

        /** A small word on a rounded pill: the pill is as wide as the word needs. */
        float pill(float cx, float cy, String s, float size, int fill, int ink, float scale) {
            float tw = width(s, size, true);
            float pw = tw + size * 1.5f;
            float ph = size * 2.0f;
            c.save();
            c.scale(scale, scale, cx, cy);
            fill(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f, ph / 2f, fill);
            text(s, cx, cy + size * 0.36f, size, ink, 1, true);
            c.restore();
            return pw;
        }

        /** Skeleton text: a quiet line standing for words, so a picture needs no words that would have to be translated. */
        void bar(float x, float y, float len, float thick, int color) {
            fill(x, y - thick / 2f, x + len, y + thick / 2f, thick / 2f, color);
        }

        // ---------------------------------------------------------- the ground

        void ground(float glowX, float glowY, float glow) {
            p.setShader(new LinearGradient(0f, 0f, 0f, H, mix(accent, 0xFF15181D, 0.86f), 0xFF0C0E11, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            r.set(0f, 0f, w, H);
            c.drawRect(r, p);
            p.setShader(new RadialGradient(glowX, glowY, 120f, alpha(accent, 0.34f * glow), alpha(accent, 0f), Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(null);
        }

        /** A phone, its top at {@code top}, as wide as {@code pw} and as tall as {@code ph} (it may run off the bottom). Sets {@link #screen}. */
        void phone(float cx, float top, float pw, float ph) {
            fill(cx - pw / 2f, top, cx + pw / 2f, top + ph, 17f, 0xFF050607);
            edge(cx - pw / 2f, top, cx + pw / 2f, top + ph, 17f, 0xFF3A4048, 1.6f);
            screen.set(cx - pw / 2f + 4f, top + 4f, cx + pw / 2f - 4f, top + ph - 4f);
            fill(screen.left, screen.top, screen.right, screen.bottom, 13f, 0xFF171A1F);
            dot(cx, top + 9.5f, 2f, 0xFF050607);
        }

        void glass(float l, float t, float rr, float b, float rad) {
            fill(l, t, rr, b, rad, 0xFC1D2229);
            edge(l, t, rr, b, rad, EDGE, 1f);
        }

        /** A padlock, its middle at (x, y), open by {@code open} (0 shut, 1 open). */
        void lock(float x, float y, float s, int color, float open) {
            fill(x - s * 0.62f, y - s * 0.12f, x + s * 0.62f, y + s * 0.78f, s * 0.2f, color);
            path.reset();
            float lift = s * 0.34f * open;
            path.moveTo(x - s * 0.36f, y - s * 0.12f);
            path.lineTo(x - s * 0.36f, y - s * 0.46f - lift);
            r.set(x - s * 0.36f, y - s * 0.84f - lift, x + s * 0.36f, y - s * 0.08f - lift);
            path.arcTo(r, 180f, 180f);
            path.lineTo(x + s * 0.36f, y - s * 0.12f - (open > 0.5f ? s * 0.28f : 0f));
            stroke(path, s * 0.22f, color);
        }

        /** A bell, its middle at (x, y), about {@code s} units across. */
        void bell(float x, float y, float s, int color) {
            path.reset();
            path.moveTo(x - s * 0.5f, y + s * 0.34f);
            path.cubicTo(x - s * 0.34f, y + s * 0.2f, x - s * 0.34f, y - s * 0.02f, x - s * 0.34f, y - s * 0.14f);
            path.cubicTo(x - s * 0.34f, y - s * 0.52f, x + s * 0.34f, y - s * 0.52f, x + s * 0.34f, y - s * 0.14f);
            path.cubicTo(x + s * 0.34f, y - s * 0.02f, x + s * 0.34f, y + s * 0.2f, x + s * 0.5f, y + s * 0.34f);
            path.close();
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawPath(path, p);
            dot(x, y + s * 0.5f, s * 0.12f, color);
            dot(x, y - s * 0.5f, s * 0.07f, color);
        }

        void tick(float x, float y, float s, float progress, float width, int color) {
            path.reset();
            float x1 = x - s * 0.5f, y1 = y + s * 0.02f, x2 = x - s * 0.12f, y2 = y + s * 0.4f, x3 = x + s * 0.55f, y3 = y - s * 0.38f;
            float first = 0.38f;
            if (progress <= first) {
                float f = progress / first;
                path.moveTo(x1, y1);
                path.lineTo(lerp(x1, x2, f), lerp(y1, y2, f));
            } else {
                float f = (progress - first) / (1f - first);
                path.moveTo(x1, y1);
                path.lineTo(x2, y2);
                path.lineTo(lerp(x2, x3, f), lerp(y2, y3, f));
            }
            if (progress > 0f) stroke(path, width, color);
        }

        /** An app tile: a rounded square with a soft top light. */
        void tile(float x, float y, float s, int color, float alphaMul) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x, y, x, y + s, alpha(mix(color, 0xFFFFFFFF, 0.22f), alphaMul), alpha(mix(color, 0xFF000000, 0.18f), alphaMul), Shader.TileMode.CLAMP));
            r.set(x, y, x + s, y + s);
            c.drawRoundRect(r, s * 0.26f, s * 0.26f, p);
            p.setShader(null);
        }

        // ---------------------------------------------------------- the scenes

        void scene(int which, float t) {
            switch (which) {
                case ALARMS: alarms(t); break;
                case USAGE: usage(t); break;
                case FOCUS: focus(t); break;
                case HOME: home(t); break;
                case QUIET: quiet(t); break;
                case NOTIF: notif(t); break;
                case EXACT: exact(t); break;
                case LOCKSCREEN: lockscreen(t); break;
                case BATTERY: battery(t); break;
                case DONE: done(t); break;
                default: ground(w / 2f, H / 2f, 0.5f); break;
            }
        }

        /** Day alarms: the phone rings, one banner drops down with two quick answers, a tap starts the slot. */
        void alarms(float t) {
            float cx = w / 2f;
            float ringing = seg(t, 0.06f, 0.56f);
            float on = ringing > 0f && ringing < 1f ? 1f : 0f;
            ground(cx, 96f, 0.6f + 0.4f * on);
            // rings that leave the clock while it rings
            for (int i = 0; i < 3; i++) {
                float f = (seg(t, 0.06f, 0.6f) * 2.2f - i * 0.45f);
                if (f <= 0f || f >= 1f) continue;
                ring(cx, 82f, 14f + 62f * out(f), 1.6f, alpha(accent, 0.55f * (1f - f)));
            }
            float shake = on > 0f ? (float) Math.sin(t * 6.2832f * 20f) * 1.7f * (float) Math.sin(ringing * 3.1416f) : 0f;
            c.save();
            c.rotate(shake, cx, 130f);
            phone(cx, 26f, 98f, 200f);
            float started = ease(seg(t, 0.84f, 0.92f));
            // the clock gives way to the banner that drops over it, and comes back when the banner is gone
            float clockA = Math.max(0f, Math.min(1f, 1f - ease(seg(t, 0.56f, 0.66f)) + ease(seg(t, 0.92f, 0.98f))));
            if (clockA > 0.01f) {
                digits("6:30", cx, screen.top + 46f, 27f, alpha(INK, clockA), 1, false);
                bar(cx - 20f, screen.top + 60f, 40f, 4f, alpha(INK, 0.28f * clockA));
            }
            // the day below: three slots, the first one fills when it is started
            for (int i = 0; i < 3; i++) {
                float y = screen.top + 82f + i * 15f;
                fill(screen.left + 9f, y - 5f, screen.right - 9f, y + 5f, 5f, 0x14FFFFFF);
                if (i == 0) fill(screen.left + 9f, y - 5f, screen.left + 9f + (screen.width() - 18f) * (0.16f + 0.84f * started), y + 5f, 5f, accent);
            }
            c.restore();
            // two arcs either side of the phone: it is ringing and buzzing
            if (on > 0f) {
                float k = 0.5f + 0.5f * (float) Math.sin(t * 6.2832f * 10f);
                for (int s = -1; s <= 1; s += 2) {
                    arc(cx + s * 62f, 84f, 14f, s < 0 ? 140f : -40f, 80f, 2.4f, alpha(accent, 0.35f + 0.5f * k));
                    arc(cx + s * 62f, 84f, 24f, s < 0 ? 148f : -32f, 64f, 2.0f, alpha(accent, 0.2f + 0.3f * k));
                }
            }
            // the banner
            float drop = ease(seg(t, 0.56f, 0.68f)) - ease(seg(t, 0.9f, 0.98f));
            if (drop > 0.001f) {
                float bw = Math.min(210f, w - 30f);
                float top = lerp(-62f, 14f, drop);
                glass(cx - bw / 2f, top, cx + bw / 2f, top + 56f, 15f);
                dot(cx - bw / 2f + 17f, top + 17f, 8f, accent);
                bell(cx - bw / 2f + 17f, top + 17f, 11f, 0xFF1F1608);
                bar(cx - bw / 2f + 33f, top + 14f, 62f, 5f, alpha(INK, 0.85f));
                bar(cx - bw / 2f + 33f, top + 25f, 96f, 3.5f, alpha(INK, 0.35f));
                float tap = seg(t, 0.76f, 0.84f);
                float pw = pill(cx - bw / 2f + 50f, top + 41f, "Start now", 8.4f, accent, 0xFF1F1608, 1f - 0.07f * (float) Math.sin(tap * 3.1416f));
                pill(cx - bw / 2f + 50f + pw / 2f + 6f + 22f, top + 41f, "In 10 min", 8.4f, 0x2EFFFFFF, INK, 1f);
                if (tap > 0f && tap < 1f) ring(cx - bw / 2f + 50f, top + 41f, 5f + 22f * out(tap), 1.6f, alpha(INK, 0.6f * (1f - tap)));
            }
        }

        /** Phone-use time and app limits: today's time counts up, and an app that reaches its limit is stopped. */
        void usage(float t) {
            float cx = w / 2f;
            ground(cx, 84f, 0.9f);
            float cw = Math.min(236f, w - 36f);
            float l = cx - cw / 2f, rr = cx + cw / 2f;
            glass(l, 22f, rr, 148f, 18f);
            float fill = ease(seg(t, 0.06f, 0.62f));
            int minutes = Math.round(lerp(58f, 194f, fill));
            bar(l + 16f, 41f, 26f, 4f, alpha(INK, 0.4f));
            digits((minutes / 60) + "h " + (minutes % 60 < 10 ? "0" : "") + (minutes % 60) + "m", l + 16f, 66f, 25f, INK, 0, false);
            // seven days; today's bar is the one that grows
            float[] days = {0.5f, 0.74f, 0.42f, 0.88f, 0.66f, 0.55f};
            float bx = rr - 16f - 7 * 10f - 6 * 4f;
            for (int i = 0; i < 7; i++) {
                float hgt = i < 6 ? days[i] * 34f : lerp(0.12f, 0.8f, fill) * 34f;
                float x = bx + i * 14f;
                fill(x, 64f - hgt, x + 10f, 64f, 3.5f, i < 6 ? 0x30FFFFFF : accent);
            }
            // an app with a daily limit
            float y = 108f;
            tile(l + 16f, y - 11f, 22f, CORAL, 1f);
            bar(l + 46f, y - 4f, 58f, 4.5f, alpha(INK, 0.8f));
            float lim = ease(seg(t, 0.3f, 0.7f));
            float reached = ease(seg(t, 0.7f, 0.78f));
            fill(l + 46f, y + 5f, rr - 16f, y + 11f, 3f, 0x24FFFFFF);
            fill(l + 46f, y + 5f, l + 46f + (rr - 16f - l - 46f) * lim, y + 11f, 3f, mix(accent, CORAL, reached));
            if (reached > 0f) {
                float pop = back(seg(t, 0.7f, 0.82f));
                lock(rr - 27f, y - 4f, 10f * pop, CORAL, 0f);
            }
            if (t > 0.9f) {
                // the loop starts again: the card dims for a moment
                fill(l, 22f, rr, 148f, 18f, alpha(0xFF0C0E11, 0.5f * ease(seg(t, 0.9f, 0.99f))));
            }
        }

        /** Lock and focus mode: a timer runs, and the apps that pull you away are locked one by one. */
        void focus(float t) {
            float cx = w / 2f;
            ground(cx - 50f, 86f, 0.9f);
            float gx = cx - 74f;
            float prog = ease(seg(t, 0.04f, 0.96f));
            ring(gx, 86f, 46f, 7f, 0x1FFFFFFF);
            arc(gx, 86f, 46f, -90f, 360f * prog, 7f, accent);
            int left = Math.round(25f * 60f * (1f - prog));
            digits((left / 60) + ":" + (left % 60 < 10 ? "0" : "") + (left % 60), gx, 94f, 24f, INK, 1, false);
            // nine apps; three of them pull you away and get locked
            int[] colours = {0xFF5B9BD5, 0xFFE06C75, 0xFF7BC47F, 0xFFC792EA, 0xFFE5C07B, 0xFF56B6C2, 0xFFE8825B, 0xFF9AA5B1, 0xFF6C7BDB};
            boolean[] bad = {false, true, false, false, true, false, true, false, false};
            float[] when = {0, 0.22f, 0, 0, 0.42f, 0, 0.62f, 0, 0};
            float gridX = cx + 6f, gridY = 36f;
            float fade = 1f - ease(seg(t, 0.93f, 1f));
            for (int i = 0; i < 9; i++) {
                float x = gridX + (i % 3) * 38f, y = gridY + (i / 3) * 38f;
                float locked = bad[i] ? back(seg(t, when[i], when[i] + 0.1f)) * fade : 0f;
                tile(x, y, 30f, colours[i], 1f - 0.72f * Math.min(1f, locked));
                if (locked > 0.01f) {
                    dot(x + 15f, y + 15f, 11f * Math.min(1f, locked), alpha(0xFF111317, 0.8f));
                    lock(x + 15f, y + 14.5f, 9f * locked, accent, 0f);
                }
            }
        }

        /** Home screen and wallpaper: the day is on the home screen, and the same day is on the lock screen. */
        void home(float t) {
            float cx = w / 2f;
            ground(cx, 90f, 0.9f);
            // the lock screen behind, with the day as its picture
            float back = ease(seg(t, 0.42f, 0.62f)) * (1f - ease(seg(t, 0.94f, 1f)));
            c.save();
            c.translate(lerp(26f, 0f, back), 0f);
            c.rotate(7f, cx + 58f, 150f);
            c.save();
            p.setAlpha(255);
            if (back > 0f) {
                phone(cx + 56f, 38f, 84f, 190f);
                p.setShader(new LinearGradient(0f, screen.top, 0f, screen.bottom, 0xFF1E3A3A, 0xFF121A22, Shader.TileMode.CLAMP));
                r.set(screen.left, screen.top, screen.right, screen.bottom);
                c.drawRoundRect(r, 13f, 13f, p);
                p.setShader(null);
                digits("6:30", cx + 56f, screen.top + 36f, 22f, alpha(INK, back), 1, false);
                for (int i = 0; i < 3; i++) {
                    float y = screen.top + 56f + i * 10f;
                    fill(screen.left + 10f, y - 3f, screen.right - 10f - i * 8f, y + 3f, 3f, alpha(accent, back * (0.9f - i * 0.2f)));
                }
            }
            c.restore();
            c.restore();
            // the home screen in front
            phone(cx - 34f, 28f, 94f, 200f);
            bar(screen.left + 10f, screen.top + 22f, 26f, 3.5f, alpha(INK, 0.4f));
            digits("Tue", screen.left + 10f, screen.top + 42f, 17f, INK, 0, false);
            int[] colours = {TEAL, AMBER, BLUE, LILAC};
            float[] len = {0.9f, 0.7f, 0.82f, 0.6f};
            float now = seg(t, 0.46f, 0.88f) * 3f;
            c.save();
            c.clipRect(screen.left, screen.top, screen.right, screen.bottom);
            for (int i = 0; i < 4; i++) {
                float in = ease(seg(t, 0.04f + i * 0.09f, 0.24f + i * 0.09f));
                float y = screen.top + 62f + i * 17f;
                float x = lerp(screen.right + 10f, screen.left + 9f, in);
                float active = Math.max(0f, 1f - Math.abs(now - i));
                float run = screen.width() - 18f;
                fill(x, y - 6.5f, x + run, y + 6.5f, 6.5f, alpha(colours[i], 0.2f + 0.16f * active));
                fill(x, y - 6.5f, x + run * len[i] * (0.5f + 0.5f * active), y + 6.5f, 6.5f, alpha(colours[i], (0.55f + 0.45f * active) * in));
            }
            c.restore();
        }

        /** Quiet phone: notifications that pile up are held back, and the phone goes quiet for the hours you chose. */
        void quiet(float t) {
            float cx = w / 2f;
            ground(cx, 80f, 0.9f);
            float calm = ease(seg(t, 0.44f, 0.6f));
            float chips = ease(seg(t, 0.04f, 0.3f)) * (1f - ease(seg(t, 0.4f, 0.58f)));
            float[][] at = {{-92f, 40f}, {58f, 52f}, {-66f, 118f}, {64f, 112f}};
            int[] colours = {BLUE, TEAL, CORAL, AMBER};
            for (int i = 0; i < 4; i++) {
                float bob = (float) Math.sin(t * 6.2832f * 2f + i * 1.7f) * 2.4f;
                float drift = (1f - chips) * (at[i][0] < 0 ? -14f : 14f);
                if (chips * 1f <= 0.01f) continue;
                float x = cx + at[i][0] + drift, y = at[i][1] + bob;
                float pw = 84f;
                c.save();
                c.scale(0.8f + 0.2f * chips, 0.8f + 0.2f * chips, x + pw / 2f, y + 11f);
                fill(x, y, x + pw, y + 22f, 11f, alpha(0xFF222830, 0.92f * chips));
                edge(x, y, x + pw, y + 22f, 11f, alpha(EDGE, chips), 1f);
                dot(x + 11f, y + 11f, 5f, alpha(colours[i], chips));
                bar(x + 22f, y + 8.5f, 36f, 3.4f, alpha(INK, 0.7f * chips));
                bar(x + 22f, y + 15f, 24f, 2.6f, alpha(INK, 0.3f * chips));
                c.restore();
            }
            // the ripple that sends them away
            float rip = seg(t, 0.4f, 0.78f);
            if (rip > 0f && rip < 1f) ring(cx, 82f, 20f + 120f * out(rip), 2f, alpha(accent, 0.6f * (1f - rip)));
            float wiggle = calm < 0.05f ? (float) Math.sin(t * 6.2832f * 9f) * 3f * ease(seg(t, 0.1f, 0.3f)) : 0f;
            c.save();
            c.rotate(wiggle, cx, 70f);
            bell(cx, 84f, 46f, mix(INK, accent, calm));
            c.restore();
            // the bell is crossed out
            float slash = ease(seg(t, 0.5f, 0.64f));
            if (slash > 0f) {
                line(cx - 24f, 58f, cx - 24f + 48f * slash, 58f + 52f * slash, 5.5f, 0xFF15181D);
                line(cx - 24f, 58f, cx - 24f + 48f * slash, 58f + 52f * slash, 3f, accent);
            }
            float pop = back(seg(t, 0.62f, 0.74f)) * (1f - ease(seg(t, 0.92f, 1f)));
            if (pop > 0.01f) pill(cx, 142f, "Quiet until 9:00", 9.4f, accent, 0xFF1A1530, pop);
        }

        /** Notifications: the reminder lands on the lock screen under the clock. */
        void notif(float t) {
            float cx = w / 2f;
            ground(cx, 100f, 0.8f);
            phone(cx, 22f, 102f, 200f);
            digits("7:00", cx, screen.top + 42f, 30f, INK, 1, false);
            bar(cx - 16f, screen.top + 56f, 32f, 3.5f, alpha(INK, 0.3f));
            float in = back(seg(t, 0.12f, 0.34f)) * (1f - ease(seg(t, 0.92f, 1f)));
            float y = lerp(screen.bottom + 6f, screen.top + 74f, Math.min(1f, in));
            float l = screen.left + 7f, rr = screen.right - 7f;
            if (in > 0.01f) {
                fill(l, y, rr, y + 40f, 11f, 0xF02A313B);
                edge(l, y, rr, y + 40f, 11f, EDGE, 1f);
                dot(l + 13f, y + 13f, 7f, accent);
                bell(l + 13f, y + 13f, 9.5f, 0xFF1F1608);
                bar(l + 25f, y + 11f, 28f, 3.2f, alpha(INK, 0.5f));
                bar(l + 9f, y + 27f, rr - l - 18f, 3.6f, alpha(INK, 0.85f));
                // a pulse around the little bell, so it is the thing to look at
                float pulse = (t * 2.2f) % 1f;
                if (t > 0.34f && t < 0.9f) ring(l + 13f, y + 13f, 7f + 11f * pulse, 1.6f, alpha(accent, 0.7f * (1f - pulse)));
            }
            // sound, either side
            float k = seg(t, 0.34f, 0.9f);
            if (k > 0f && k < 1f) {
                float s = 0.5f + 0.5f * (float) Math.sin(t * 6.2832f * 7f);
                for (int sd = -1; sd <= 1; sd += 2) {
                    arc(cx + sd * 66f, 100f, 13f, sd < 0 ? 140f : -40f, 80f, 2.4f, alpha(accent, 0.3f + 0.5f * s));
                    arc(cx + sd * 66f, 100f, 23f, sd < 0 ? 148f : -32f, 64f, 2f, alpha(accent, 0.18f + 0.3f * s));
                }
            }
        }

        /** Exact alarms: the second hand sweeps round and stops on the exact minute. */
        void exact(float t) {
            float cx = w / 2f, cy = 86f, R = 60f;
            ground(cx, cy, 0.8f);
            fill(cx - R - 7f, cy - R - 7f, cx + R + 7f, cy + R + 7f, R + 7f, 0xFF0C0E11);
            edge(cx - R - 7f, cy - R - 7f, cx + R + 7f, cy + R + 7f, R + 7f, EDGE, 1.4f);
            float hit = seg(t, 0.74f, 0.9f);
            for (int i = 0; i < 60; i++) {
                double a = Math.toRadians(i * 6 - 90);
                boolean big = i % 5 == 0;
                float len = big ? 7f : 3.4f;
                int col = i == 0 ? mix(alpha(INK, 0.5f), accent, ease(seg(t, 0.7f, 0.78f))) : alpha(INK, big ? 0.5f : 0.2f);
                line(cx + (float) Math.cos(a) * (R - 2f), cy + (float) Math.sin(a) * (R - 2f),
                        cx + (float) Math.cos(a) * (R - 2f - len), cy + (float) Math.sin(a) * (R - 2f - len), i == 0 ? 3f : 1.6f, col);
            }
            // two full turns that slow down and land exactly on twelve
            float turn = out(seg(t, 0.04f, 0.74f)) * 720f;
            double a = Math.toRadians(turn - 90);
            float hx = cx + (float) Math.cos(a) * (R - 8f), hy = cy + (float) Math.sin(a) * (R - 8f);
            float tx = cx - (float) Math.cos(a) * 12f, ty = cy - (float) Math.sin(a) * 12f;
            line(tx, ty, hx, hy, 2.4f, accent);
            digits("7:00", cx, cy + 36f, 20f, alpha(INK, ease(seg(t, 0.74f, 0.88f))), 1, false);
            dot(cx, cy, 4.2f, accent);
            dot(cx, cy, 1.6f, 0xFF0C0E11);
            if (hit > 0f && hit < 1f) ring(cx, cy - R + 4f, 5f + 26f * out(hit), 2f, alpha(accent, 0.7f * (1f - hit)));
        }

        /** Lock-screen alarm: the question opens over the lock screen by itself. */
        void lockscreen(float t) {
            float cx = w / 2f;
            ground(cx, 90f, 0.7f);
            phone(cx, 22f, 102f, 200f);
            float grow = ease(seg(t, 0.22f, 0.5f)) * (1f - ease(seg(t, 0.9f, 0.99f)));
            // the lock screen under it
            float opening = ease(seg(t, 0.12f, 0.26f));
            lock(cx, screen.top + 15f, 8.5f, alpha(INK, 0.8f), opening);
            digits("7:00", cx, screen.top + 56f, 30f, alpha(INK, 1f - 0.7f * grow), 1, false);
            bar(cx - 16f, screen.top + 70f, 32f, 3.5f, alpha(INK, 0.3f * (1f - grow)));
            // the alarm window grows from a small card to the whole screen
            if (grow > 0.001f) {
                float l = lerp(screen.left + 12f, screen.left, grow), rr = lerp(screen.right - 12f, screen.right, grow);
                float tp = lerp(H - 54f, screen.top, grow), bt = lerp(H - 10f, screen.bottom, grow);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0f, tp, 0f, bt, 0xFF2A2415, 0xFF171A1F, Shader.TileMode.CLAMP));
                r.set(l, tp, rr, bt);
                c.drawRoundRect(r, lerp(10f, 13f, grow), lerp(10f, 13f, grow), p);
                p.setShader(null);
                float inner = ease(seg(grow, 0.55f, 1f));
                if (inner > 0f) {
                    bell(cx, screen.top + 40f, 22f * inner, accent);
                    bar(cx - 26f, screen.top + 68f, 52f, 5f, alpha(INK, 0.9f * inner));
                    bar(cx - 36f, screen.top + 80f, 72f, 3.5f, alpha(INK, 0.35f * inner));
                    pill(cx, screen.top + 108f, "Start now", 9f, alpha(accent, inner), alpha(0xFF1F1608, inner), 0.6f + 0.4f * inner);
                    pill(cx, screen.top + 132f, "In 10 min", 8.4f, alpha(0x2EFFFFFF, inner), alpha(INK, inner), 0.6f + 0.4f * inner);
                }
            }
        }

        /** No battery limits: the app is put to sleep by the phone; with the limit off it stays awake and rings. */
        void battery(float t) {
            float cx = w / 2f;
            ground(cx, 84f, 0.8f);
            float awake = ease(seg(t, 0.42f, 0.56f));
            // the battery
            float bx = cx - 100f, by = 70f;
            edge(bx, by, bx + 52f, by + 26f, 7f, alpha(INK, 0.7f), 2.2f);
            fill(bx + 53.5f, by + 8f, bx + 57f, by + 18f, 2f, alpha(INK, 0.7f));
            fill(bx + 5f, by + 5f, bx + 5f + 42f * 0.72f, by + 21f, 4f, accent);
            // the app, asleep and then awake
            float ax = cx - 19f, ay = 56f;
            float bob = awake > 0.5f ? 0f : (float) Math.sin(t * 6.2832f * 2f) * 1.5f;
            tile(ax, ay + bob, 44f, accent, 0.4f + 0.6f * awake);
            if (awake < 0.99f) {
                for (int i = 0; i < 3; i++) {
                    float f = (t * 1.6f + i * 0.33f) % 1f;
                    digits("z", ax + 52f + f * 14f, ay + 6f - f * 26f - i * 6f, 11f + i * 3f, alpha(INK, 0.7f * (1f - f) * (1f - awake)), 0, true);
                }
            } else {
                bell(ax + 22f, ay + 22f, 24f, 0xFF1F1608);
            }
            bell(ax + 22f, ay + 22f, 24f, alpha(0xFF1F1608, 0.4f + 0.6f * awake));
            // the shield that keeps it awake
            float sh = seg(t, 0.4f, 0.62f);
            if (sh > 0f) {
                float sx = cx + 70f, sy = 85f;
                path.reset();
                path.moveTo(sx, sy - 24f);
                path.cubicTo(sx + 10f, sy - 18f, sx + 18f, sy - 17f, sx + 22f, sy - 17f);
                path.cubicTo(sx + 22f, sy + 6f, sx + 14f, sy + 20f, sx, sy + 27f);
                path.cubicTo(sx - 14f, sy + 20f, sx - 22f, sy + 6f, sx - 22f, sy - 17f);
                path.cubicTo(sx - 18f, sy - 17f, sx - 10f, sy - 18f, sx, sy - 24f);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(alpha(accent, 0.18f * ease(sh)));
                c.drawPath(path, p);
                stroke(path, 2.6f, alpha(accent, ease(sh)));
                tick(sx, sy + 2f, 22f, ease(seg(t, 0.5f, 0.7f)), 3.4f, alpha(accent, ease(sh)));
            }
            float burst = seg(t, 0.5f, 0.82f);
            if (burst > 0f && burst < 1f) ring(cx, 78f, 24f + 64f * out(burst), 2f, alpha(accent, 0.5f * (1f - burst)));
        }

        /** All set: a tick draws itself and a ring spreads. */
        void done(float t) {
            float cx = w / 2f, cy = 84f;
            ground(cx, cy, 1f);
            float pop = back(seg(t, 0.04f, 0.3f));
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(cx, cy - 36f, cx, cy + 36f, mix(accent, 0xFFFFFFFF, 0.3f), mix(accent, 0xFF000000, 0.15f), Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, 36f * pop, p);
            p.setShader(null);
            tick(cx, cy + 1f, 34f, ease(seg(t, 0.24f, 0.52f)), 5.6f, 0xFF10231F);
            float burst = seg(t, 0.44f, 0.92f);
            if (burst > 0f && burst < 1f) ring(cx, cy, 36f + 62f * out(burst), 2.2f, alpha(accent, 0.6f * (1f - burst)));
            for (int i = 0; i < 10; i++) {
                float f = out(seg(t, 0.42f, 0.95f));
                if (f <= 0f || f >= 1f) continue;
                double a = Math.toRadians(i * 36 + 12);
                float dist = 44f + (i % 2 == 0 ? 52f : 36f) * f;
                dot(cx + (float) Math.cos(a) * dist, cy + (float) Math.sin(a) * dist * 0.9f, (i % 3 == 0 ? 3.2f : 2.1f) * (1f - f * 0.6f), alpha(i % 2 == 0 ? accent : INK, 1f - f));
            }
        }
    }
}
