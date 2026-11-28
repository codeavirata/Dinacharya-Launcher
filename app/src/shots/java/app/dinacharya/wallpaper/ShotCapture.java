package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.inspector.WindowInspector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a picture of what the app shows (test build only): every window of the app, the screen and any dialog or sheet over it, drawn
 * one over the other in the order they were opened. Nothing outside the app can be captured (the system's own permission screens).
 */
final class ShotCapture {
    private ShotCapture() {
    }

    /** The windows of the app that are on screen, bottom first. */
    static List<View> windows(Activity top) {
        List<View> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 29) {
            for (View v : WindowInspector.getGlobalWindowViews()) {
                if (v.isAttachedToWindow() && v.getVisibility() == View.VISIBLE && v.getWidth() > 0 && v.getHeight() > 0) out.add(v);
            }
        }
        if (out.isEmpty() && top != null) out.add(top.getWindow().getDecorView());
        return out;
    }

    /** The picture, as big as the screen. */
    static Bitmap take(Activity top) {
        DisplayMetrics dm = top.getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;
        int[] where = new int[2];
        for (View v : windows(top)) {   // the screen is as tall as its tallest window (the bar at the bottom counts)
            v.getLocationOnScreen(where);
            w = Math.max(w, where[0] + v.getWidth());
            h = Math.max(h, where[1] + v.getHeight());
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.BLACK);
        int[] at = new int[2];
        for (View v : windows(top)) {
            v.getLocationOnScreen(at);
            canvas.save();
            canvas.translate(at[0], at[1]);
            try {
                v.draw(canvas);
            } catch (RuntimeException e) {
                // a window that cannot be drawn is left out of the picture
            }
            canvas.restore();
        }
        return bmp;
    }

    /** Saves the picture in the app's folder (and, on Android 10 and later, in Pictures/DinacharyaShots, so it can be shared). */
    static Uri save(Context c, Bitmap bmp, File dir, String fileName, String galleryFolder) throws java.io.IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream(512 * 1024);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, png);
        byte[] bytes = png.toByteArray();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("cannot make " + dir);
        try (FileOutputStream out = new FileOutputStream(new File(dir, fileName))) {
            out.write(bytes);
        }
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DinacharyaShots/" + galleryFolder);
            Uri uri = c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                if (out != null) out.write(bytes);
            }
            return uri;
        } catch (RuntimeException e) {
            return null;   // the copy in the app's folder is the one that counts
        }
    }
}
