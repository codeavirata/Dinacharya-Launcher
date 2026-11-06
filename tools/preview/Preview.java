package app.dinacharya.wallpaper;

import android.graphics.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Draws the setup scenes (FeatureScenes) on a computer, six moments of each loop on one sheet, so they can be looked at. */
public class Preview {
    public static void main(String[] args) throws Exception {
        int w = 330, h = 170;
        float scale = 2f;
        String out = args.length > 0 ? args[0] : "/tmp/claude-0/preview";
        new File(out).mkdirs();
        float[] times = {0.08f, 0.22f, 0.38f, 0.54f, 0.70f, 0.86f};
        int only = args.length > 1 ? Integer.parseInt(args[1]) : -1;
        for (int s = 0; s < FeatureScenes.COUNT; s++) {
            if (only >= 0 && s != only) continue;
            int cols = 3, rows = 2, gap = 8;
            int sw = Math.round(w * scale), sh = Math.round(h * scale);
            BufferedImage sheet = new BufferedImage(cols * sw + (cols + 1) * gap, rows * sh + (rows + 1) * gap, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(0x2A2A2E));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            for (int i = 0; i < times.length; i++) {
                BufferedImage img = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gi = img.createGraphics();
                gi.scale(scale, scale);
                Canvas c = new Canvas(gi);
                // the view clips the scene to a rounded box
                gi.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 44, 44));
                FeatureScenes.draw(c, s, times[i], w, h);
                gi.dispose();
                g.drawImage(img, gap + (i % cols) * (sw + gap), gap + (i / cols) * (sh + gap), null);
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(out, "scene" + s + ".png"));
        }
    }
}
