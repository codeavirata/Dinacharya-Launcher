package app.dinacharya.wallpaper;

import java.util.List;

/**
 * An automatic colour for every activity type, so each type has its own slice in a pie chart and its own dot in a list.
 * The colour follows the type's place in the routine's list of types, so it stays the same while the list only grows.
 * The "Other" type of each kind gets a muted tint of its own so the five of them can be told apart. Uses no Android classes.
 */
public final class SubColors {
    private SubColors() {
    }

    /** Distinct, readable on both light and dark backgrounds (ARGB). */
    private static final int[] PALETTE = {
            0xFF0E7C7B, 0xFFE07A1F, 0xFF4A6FD1, 0xFFC2410C, 0xFF7C3AED, 0xFF2E9E4F, 0xFFD6336C, 0xFF1C8FBF,
            0xFFB08900, 0xFF8E5A3C, 0xFF3D8B6E, 0xFFAD3FA0, 0xFF5B6B8C, 0xFFCC4B4B, 0xFF6E9B1E, 0xFF2C6FA3};

    /** A muted tint for "Other", one per kind (task, rest, meal, personal, sleep). */
    private static final int[] OTHER = {0xFF6B8A8A, 0xFF9A8F6B, 0xFF9C7B72, 0xFF7384A0, 0xFF7C7FA6};

    /** The colour of this type in this routine. */
    public static int of(Routine r, int kind, String sub) {
        if (sub == null || sub.isEmpty() || Routine.isOther(sub)) return OTHER[Math.max(0, Math.min(OTHER.length - 1, kind))];
        int index = 0;
        for (Routine.Sub s : r.subs) {
            if (Routine.isOther(s.name)) continue;
            if (s.kind == kind && s.name.equalsIgnoreCase(sub)) return pick(index);
            index++;
        }
        // not in the list (a type from an old report): a colour from its name
        return pick(Math.abs(sub.toLowerCase(java.util.Locale.ROOT).hashCode()));
    }

    /** The colour for the number-th type, going on past the palette with hues that keep their distance. */
    static int pick(int number) {
        if (number < PALETTE.length) return PALETTE[number];
        float hue = (number * 137.508f) % 360f;
        return hsv(hue, 0.62f, 0.78f);
    }

    /** ARGB from hue (0-360), saturation and value (0-1). */
    static int hsv(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    /** The colour of every type in the list, in the list's order (for a legend). */
    public static int[] all(Routine r, List<Routine.Sub> subs) {
        int[] out = new int[subs.size()];
        for (int i = 0; i < out.length; i++) out[i] = of(r, subs.get(i).kind, subs.get(i).name);
        return out;
    }
}
