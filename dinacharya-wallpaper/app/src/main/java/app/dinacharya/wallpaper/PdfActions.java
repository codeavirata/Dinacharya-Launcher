package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Save and share buttons for a report: the same PDF the Drive backup uses. */
final class PdfActions {
    static final int REQ_SAVE = 9101;

    private PdfActions() {
    }

    static String fileName(DayReport r) {
        return "Dinacharya " + r.day + (r.partial ? " so far" : "") + ".pdf";
    }

    /** Opens Android's "save as" picker, where Google Drive is one of the places to choose. */
    static void save(Activity a, DayReport r) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_TITLE, fileName(r));
        try {
            a.startActivityForResult(i, REQ_SAVE);
        } catch (RuntimeException e) {
            Lang.toast(a, "This phone has no save dialog. Use Share instead.", Toast.LENGTH_LONG).show();
        }
    }

    /** Call from onActivityResult when the requestCode is REQ_SAVE. */
    static void saved(final Activity a, final DayReport r, final Uri uri) {
        if (uri == null) return;
        final boolean h24 = Store.load(a).fmt24;
        new Thread(() -> {
            String msg;
            try (OutputStream os = a.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("could not open the file");
                ReportPdf.write(r, h24, os);
                msg = "PDF saved";
            } catch (IOException | RuntimeException e) {
                msg = "Could not save the PDF: " + e.getMessage();
            }
            final String m = msg;
            a.runOnUiThread(() -> Lang.toast(a, m, Toast.LENGTH_LONG).show());
        }, "pdf-save").start();
    }

    /** Makes the PDF and opens the share sheet. */
    static void share(final Activity a, final DayReport r) {
        final boolean h24 = Store.load(a).fmt24;
        new Thread(() -> {
            try {
                File dir = new File(a.getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("no folder");
                final String name = fileName(r).replace(' ', '-');
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    ReportPdf.write(r, h24, os);
                }
                final Uri uri = Uri.parse("content://" + ReportProvider.AUTHORITY + "/" + Uri.encode(name));
                a.runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/pdf");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_SUBJECT, "Dinacharya report " + r.day);
                    send.setClipData(ClipData.newRawUri("", uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(Intent.createChooser(send, "Share report"));
                });
            } catch (IOException | RuntimeException e) {
                final String m = "Could not make the PDF: " + e.getMessage();
                a.runOnUiThread(() -> Lang.toast(a, m, Toast.LENGTH_LONG).show());
            }
        }, "pdf-share").start();
    }
}
