package android.graphics;

import java.awt.Color;
import java.awt.geom.Point2D;

public class LinearGradient extends Shader {
    public LinearGradient(float x0, float y0, float x1, float y1, int c0, int c1, TileMode m) {
        if (x0 == x1 && y0 == y1) y1 = y0 + 0.001f;
        awt = new java.awt.GradientPaint(new Point2D.Float(x0, y0), new Color(c0, true), new Point2D.Float(x1, y1), new Color(c1, true));
    }
}
