package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The "Dinacharya shots" icon (test build only). It starts the picture tour, shows how far it has got, and, when it is done, hands the
 * pictures over: as one zip file, or as all the pictures together. Started with the extra {@code auto} it starts the tour by itself
 * (that is how the GitHub emulator run does it).
 */
public class ShotActivity extends Activity {
    static final String EXTRA_AUTO = "auto";

    private TextView status;
    private TextView logTail;
    private Button start;
    private Button zip;
    private Button each;
    private ShotTour tour;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tour = ShotTour.get(getApplication());
        build();
        listenToTour();
        refresh();
        if (getIntent().getBooleanExtra(EXTRA_AUTO, false) && !tour.running()) startTour();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_AUTO, false) && !tour.running()) startTour();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        listenToTour();
        refresh();
    }

    private File folder() {
        File d = getExternalFilesDir("shots");
        return d != null ? d : new File(getFilesDir(), "shots");
    }

    /**
     * The tour closes this screen on the way (it restarts the app in each set of colours), so when it is finished it brings the screen
     * back to show the result. The application is the one thing that outlives the restarts, so the callback only uses that.
     */
    private void listenToTour() {
        final android.app.Application app = getApplication();
        tour.listen(this::refresh, () -> app.startActivity(new Intent(app, ShotActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)));
    }

    private void startTour() {
        listenToTour();
        tour.start(folder());
        refresh();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF15151A);
        root.setPadding(dp(20), dp(40), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Dinacharya shots");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("A test build. It turns Pro on, then walks through the whole app in two sets of colours and two languages, and saves a "
                + "picture of every screen and sheet. It is not in the Play build.");
        sub.setTextColor(0xFFB0B0B8);
        sub.setTextSize(14);
        root.addView(sub, lp(8));

        status = new TextView(this);
        status.setTextColor(0xFFFFC66B);
        status.setTextSize(16);
        root.addView(status, lp(18));

        start = button("Start the tour", v -> {
            if (!tour.running()) startTour();
        });
        root.addView(start, lp(14));
        zip = button("Share all as one zip", v -> shareZip());
        root.addView(zip, lp(8));
        each = button("Share each picture", v -> shareEach());
        root.addView(each, lp(8));

        ScrollView sv = new ScrollView(this);
        logTail = new TextView(this);
        logTail.setTextColor(0xFF8A8A94);
        logTail.setTextSize(11);
        logTail.setTypeface(Typeface.MONOSPACE);
        sv.addView(logTail);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void refresh() {
        if (status == null || tour == null) return;
        String s;
        if (tour.running()) {
            int total = Math.max(1, tour.stepsTotal());
            s = "Running: pass " + tour.pass() + ", " + tour.shots() + " pictures, " + (100 - 100 * tour.stepsLeft() / total) + "% of the steps.";
        } else if (tour.done()) {
            s = "Done: " + tour.shots() + " pictures in\n" + tour.folder();
        } else {
            s = "Not started. It takes a few minutes; leave the phone on.";
        }
        status.setText(s);
        start.setEnabled(!tour.running());
        zip.setEnabled(tour.done());
        each.setEnabled(tour.done() && !tour.saved().isEmpty());
        File log = tour.folder() == null ? null : new File(tour.folder(), "log.txt");
        if (log != null && log.exists() && tour.done()) {
            try (InputStream in = new FileInputStream(log)) {
                byte[] all = new byte[(int) Math.min(log.length(), 6000)];
                long skip = log.length() - all.length;
                if (skip > 0) in.skip(skip);
                int n = in.read(all);
                logTail.setText(new String(all, 0, Math.max(0, n)));
            } catch (java.io.IOException ignored) {
                // the log stays in the folder
            }
        }
    }

    // ------------------------------------------------------------------ handing the pictures over

    private void shareEach() {
        ArrayList<Uri> uris = new ArrayList<>(tour.saved());
        if (uris.isEmpty()) {
            Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share the pictures"));
    }

    private void shareZip() {
        final File dir = tour.folder();
        if (dir == null) return;
        Toast.makeText(this, "Making the zip…", Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                File zipFile = new File(dir.getParentFile(), "dinacharya-shots.zip");
                try (ZipOutputStream z = new ZipOutputStream(new java.io.FileOutputStream(zipFile))) {
                    add(z, dir, "");
                }
                Uri uri = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, "dinacharya-shots-" + (System.currentTimeMillis() / 1000) + ".zip");
                    cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download");
                    uri = app.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri != null) {
                        try (OutputStream out = app.getContentResolver().openOutputStream(uri);
                             InputStream in = new FileInputStream(zipFile)) {
                            byte[] buf = new byte[64 * 1024];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                    }
                }
                final Uri shared = uri;
                runOnUiThread(() -> {
                    if (shared == null) {
                        Toast.makeText(this, "Zip is in " + zipFile, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Intent send = new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, shared)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Share the zip (also saved in Downloads)"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Zip failed: " + e, Toast.LENGTH_LONG).show());
            }
        }, "shots-zip").start();
    }

    private static void add(ZipOutputStream z, File f, String prefix) throws java.io.IOException {
        File[] kids = f.listFiles();
        if (kids == null) return;
        java.util.Arrays.sort(kids);
        for (File k : kids) {
            if (k.isDirectory()) {
                add(z, k, prefix + k.getName() + "/");
            } else if (!k.getName().equals("DONE")) {
                z.putNextEntry(new ZipEntry(prefix + k.getName()));
                try (InputStream in = new FileInputStream(k)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                }
                z.closeEntry();
            }
        }
    }
}
