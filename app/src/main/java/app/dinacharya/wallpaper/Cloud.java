package app.dinacharya.wallpaper;

import android.content.Context;

import java.io.IOException;
import java.util.Map;

/** The Google account and Drive backup, put together for the screens and the background job. */
final class Cloud {
    private static final Object LOCK = new Object();

    private Cloud() {
    }

    static Tokens tokens(Context c) {
        AccountStore a = new AccountStore(c);
        if (AccountStore.CODE.equals(a.method())) return a.deviceAuth();
        return new PickerAuth(c);
    }

    static CloudDrive drive(Context c) {
        return new CloudDrive(new Http.Url(), tokens(c));
    }

    /** The outcome of one backup, for the screen. */
    static final class Outcome {
        final boolean ok;
        final long size;
        final String error;
        final boolean signInAgain;

        Outcome(boolean ok, long size, String error, boolean signInAgain) {
            this.ok = ok;
            this.size = size;
            this.error = error;
            this.signInAgain = signInAgain;
        }
    }

    /** Backs up now. Network work: never call on the main thread. */
    static Outcome backUp(Context context) {
        Context c = context.getApplicationContext();
        AccountStore a = new AccountStore(c);
        synchronized (LOCK) {
            if (!a.signedIn()) return new Outcome(false, 0, "Not signed in to Google.", true);
            try {
                Map<String, byte[]> files = BackupData.collect(c);
                CloudBackup.Result r = CloudBackup.backUp(drive(c), files, BackupData.info(c, files));
                a.backedUp(System.currentTimeMillis(), r.bytes);
                return new Outcome(true, r.bytes, "", false);
            } catch (Tokens.CloudException e) {
                a.failed(e.getMessage());
                return new Outcome(false, 0, e.getMessage(), e.signInExpired);
            } catch (IOException e) {
                String m = "Could not reach Google Drive" + (e.getMessage() == null ? "." : " (" + e.getMessage() + ").");
                a.failed(m);
                return new Outcome(false, 0, m, false);
            } catch (RuntimeException e) {
                CrashLog.note(c, "backup", e);
                a.failed("Backup failed: " + e.getMessage());
                return new Outcome(false, 0, "Backup failed: " + e.getMessage(), false);
            }
        }
    }

    /** Backs up if the chosen schedule says it is time. Network work. */
    /**
     * Whether a background backup may run. Never on a phone that has not yet asked about restoring or backed up before, and never while
     * a backup from another phone is waiting in Drive and was not restored: a fresh phone must not replace it with its own empty data.
     */
    static boolean autoAllowed(Context context) {
        AccountStore a = new AccountStore(context);
        return a.signedIn() && !a.holdAuto() && (a.lastOk() > 0 || a.restoreAsked());
    }

    /** A background backup made because something changed. */
    static void autoBackUp(Context context) {
        if (autoAllowed(context)) backUp(context);
    }

    static void runIfDue(Context context) {
        AccountStore a = new AccountStore(context);
        if (!autoAllowed(context)) return;
        if (!BackupPolicy.due(a.frequency(), a.lastOk(), System.currentTimeMillis())) return;
        backUp(context);
    }

    /** Signs out on this phone and withdraws the app's key at Google. Network work. */
    static void signOut(Context context) {
        Context c = context.getApplicationContext();
        AccountStore a = new AccountStore(c);
        try {
            if (AccountStore.CODE.equals(a.method())) {
                CloudDrive.revoke(new Http.Url(), a.deviceAuth().refreshToken());
            } else if (AccountStore.PICKER.equals(a.method())) {
                CloudDrive.revoke(new Http.Url(), new PickerAuth(c).token(false));
            }
        } catch (IOException | RuntimeException ignored) {
            // signing out here is what matters
        }
        a.forget();
        DriveJob.schedule(c);
    }
}
