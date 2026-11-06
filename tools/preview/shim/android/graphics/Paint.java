package android.graphics;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Align { LEFT, CENTER, RIGHT }
    int color = 0xFF000000;
    Style style = Style.FILL;
    float strokeWidth = 1f;
    Cap cap = Cap.BUTT;
    Join join = Join.MITER;
    Shader shader;
    float textSize = 14f;
    Typeface typeface = Typeface.DEFAULT;
    Align align = Align.LEFT;
    float letter = 0f;
    public Paint() {}
    public Paint(int flags) {}
    public void setAntiAlias(boolean b) {}
    public void setColor(int c) { color = c; }
    public int getColor() { return color; }
    public void setAlpha(int a) { color = (color & 0x00FFFFFF) | ((a & 0xFF) << 24); }
    public void setStyle(Style s) { style = s; }
    public void setStrokeWidth(float w) { strokeWidth = w; }
    public void setStrokeCap(Cap c) { cap = c; }
    public void setStrokeJoin(Join j) { join = j; }
    public Shader setShader(Shader s) { shader = s; return s; }
    public void setTextSize(float s) { textSize = s; }
    public void setTypeface(Typeface t) { typeface = t; }
    public void setTextAlign(Align a) { align = a; }
    public void setLetterSpacing(float l) { letter = l; }
    public float measureText(String s) {
        java.awt.Font f = Canvas.font(this);
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
        return (float) f.getStringBounds(s, frc).getWidth();
    }
}
