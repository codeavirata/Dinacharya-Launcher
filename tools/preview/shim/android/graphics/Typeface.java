package android.graphics;

public class Typeface {
    public static final int NORMAL = 0, BOLD = 1, ITALIC = 2;
    public static final Typeface DEFAULT = new Typeface("sans-serif", NORMAL);
    public static final Typeface DEFAULT_BOLD = new Typeface("sans-serif", BOLD);
    final String family;
    final int style;
    Typeface(String f, int s) { family = f; style = s; }
    public static Typeface create(String family, int style) { return new Typeface(family == null ? "sans-serif" : family, style); }
}
