package android.graphics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

/** Preview stand-in for android.graphics.Canvas, drawn with Java2D, so the scene drawing can be looked at on a computer. */
public class Canvas {
    final Graphics2D g;
    private final Deque<Object[]> stack = new ArrayDeque<>();

    public Canvas(Graphics2D g) {
        this.g = g;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static Font font(Paint p) {
        String fam = p.typeface.family;
        int style = (p.typeface.style & Typeface.BOLD) != 0 || fam.contains("medium") ? Font.BOLD : Font.PLAIN;
        String name = fam.contains("light") ? "Inter Display" : "Inter";
        return new Font(name, style, 1).deriveFont(style, p.textSize);
    }

    public int save() { stack.push(new Object[]{g.getTransform(), g.getClip()}); return stack.size(); }
    public void restore() { Object[] s = stack.pop(); g.setTransform((AffineTransform) s[0]); g.setClip((Shape) s[1]); }
    public void translate(float x, float y) { g.translate(x, y); }
    public void rotate(float deg, float px, float py) { g.rotate(Math.toRadians(deg), px, py); }
    public void rotate(float deg) { g.rotate(Math.toRadians(deg)); }
    public void scale(float sx, float sy, float px, float py) { g.translate(px, py); g.scale(sx, sy); g.translate(-px, -py); }
    public void scale(float sx, float sy) { g.scale(sx, sy); }
    public void clipPath(Path p) { g.clip(p.p); }
    public void clipRect(float l, float t, float r, float b) { g.clip(new Rectangle2D.Float(l, t, r - l, b - t)); }

    private void paint(Shape s, Paint p) {
        if (p.shader != null) g.setPaint(p.shader.awt); else g.setColor(new Color(p.color, true));
        if (p.style == Paint.Style.FILL || p.style == Paint.Style.FILL_AND_STROKE) g.fill(s);
        if (p.style != Paint.Style.FILL) {
            int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT;
            int join = p.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND : p.join == Paint.Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER;
            g.setStroke(new BasicStroke(p.strokeWidth, cap, join));
            g.draw(s);
        }
    }

    public void drawRoundRect(RectF r, float rx, float ry, Paint p) { paint(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), 2 * rx, 2 * ry), p); }
    public void drawRect(RectF r, Paint p) { paint(new Rectangle2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawRect(float l, float t, float r, float b, Paint p) { paint(new Rectangle2D.Float(l, t, r - l, b - t), p); }
    public void drawCircle(float x, float y, float r, Paint p) { paint(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), p); }
    public void drawOval(RectF r, Paint p) { paint(new Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawLine(float x1, float y1, float x2, float y2, Paint p) {
        Paint q = new Paint(); q.color = p.color; q.shader = p.shader; q.style = Paint.Style.STROKE; q.strokeWidth = p.strokeWidth; q.cap = p.cap;
        paint(new Line2D.Float(x1, y1, x2, y2), q);
    }
    public void drawPath(Path path, Paint p) { paint(path.p, p); }
    public void drawArc(RectF o, float start, float sweep, boolean center, Paint p) {
        paint(new Arc2D.Float(o.left, o.top, o.width(), o.height(), -start, -sweep, center ? Arc2D.PIE : Arc2D.OPEN), p);
    }
    public void drawText(String s, float x, float y, Paint p) {
        Font f = font(p);
        g.setFont(f);
        float w = p.measureText(s);
        float dx = p.align == Paint.Align.CENTER ? -w / 2f : p.align == Paint.Align.RIGHT ? -w : 0f;
        if (p.shader != null) g.setPaint(p.shader.awt); else g.setColor(new Color(p.color, true));
        g.drawString(s, x + dx, y);
    }
}
