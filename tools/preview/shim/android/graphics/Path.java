package android.graphics;

import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

public class Path {
    public enum Direction { CW, CCW }
    final Path2D.Float p = new Path2D.Float();
    public void moveTo(float x, float y) { p.moveTo(x, y); }
    public void lineTo(float x, float y) { if (p.getCurrentPoint() == null) p.moveTo(x, y); else p.lineTo(x, y); }
    public void quadTo(float x1, float y1, float x2, float y2) { p.quadTo(x1, y1, x2, y2); }
    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) { p.curveTo(x1, y1, x2, y2, x3, y3); }
    public void close() { p.closePath(); }
    public void reset() { p.reset(); }
    public void addCircle(float x, float y, float r, Direction d) { p.append(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), false); }
    public void addRoundRect(RectF r, float rx, float ry, Direction d) {
        p.append(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), 2 * rx, 2 * ry), false);
    }
    public void arcTo(RectF o, float start, float sweep) {
        p.append(new Arc2D.Float(o.left, o.top, o.width(), o.height(), -start, -sweep, Arc2D.OPEN), true);
    }
    public void addArc(RectF o, float start, float sweep) {
        p.append(new Arc2D.Float(o.left, o.top, o.width(), o.height(), -start, -sweep, Arc2D.OPEN), false);
    }
}
