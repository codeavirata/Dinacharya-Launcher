package android.graphics;

import java.awt.Color;
import java.awt.geom.Point2D;

public class RadialGradient extends Shader {
    public RadialGradient(float cx, float cy, float r, int c0, int c1, TileMode m) {
        awt = new java.awt.RadialGradientPaint(new Point2D.Float(cx, cy), Math.max(0.01f, r), new float[]{0f, 1f},
                new Color[]{new Color(c0, true), new Color(c1, true)});
    }
}
