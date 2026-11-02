package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The Backup tab, laid out like WhatsApp's "Chat backup": the last backup, a Back up button, the
 * Google account, how often to back up and over which network, and restore.
 */
final class BackupPage extends Page {
    private TextView lastLine;
    private TextView sizeLine;
    private TextView errorLine;
    private Button backupBtn;
    private ProgressBar progress;
    private TextView accountLine;
    private LinearLayout accountButtons;
    private TextView freqValue;
    private TextView netValue;
    private LinearLayout settingsBox;
    private Button restoreBtn;
    private boolean working;
    private final Handler main = new Handler(Looper.getMainLooper());

    BackupPage(MainActivity host) {
        super(host);
    }

    private AccountStore acc() {
        return new AccountStore(a);
    }

    // ------------------------------------------------------------------ layout

    @Override
    void build(LinearLayout body) {
        title("Backup", "Your days, safe in Google Drive");

        LinearLayout top = Ui.section(a, body, "Google Drive backup", null);
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Glyph cloud = new Glyph(a, Glyph.CLOUD, Ui.TEAL);
        cloud.setBackground(Ui.box(a, Ui.TONAL, 0, 26));
        cloud.setPadding(a.dp(10), a.dp(10), a.dp(10), a.dp(10));
        head.addView(cloud, new LinearLayout.LayoutParams(a.dp(52), a.dp(52)));
        LinearLayout lines = new LinearLayout(a);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setPadding(a.dp(14), 0, 0, 0);
        lastLine = Ui.label(a, "", 15, Ui.INK, true);
        sizeLine = Ui.label(a, "", 13, Ui.MUTED, false);
        lines.addView(lastLine);
        lines.addView(sizeLine, Ui.wide(a, 2));
        head.addView(lines, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(head, Ui.wide(a, 12));
        Expander.Part about = Expander.more(a, top, "About this backup", "backup_about", false);
        about.body.addView(a.note("Back up your routines, settings, alarm answers and every day report to Google Drive. "
                + "When you reinstall Dinacharya Launcher or move to a new phone, sign in with the same account and restore them. "
                + "The backup sits in a private space in your Drive: it does not show among your files and only this app can open it."),
                Ui.wide(a, 4));
        errorLine = Ui.label(a, "", 13, Ui.RED, false);
        top.addView(errorLine, Ui.wide(a, 8));
        progress = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        top.addView(progress, Ui.wide(a, 8));
        backupBtn = Ui.button(a, "Back up", true, v -> backUpNow());
        top.addView(backupBtn, Ui.wide(a, 10));

        // Account.
        LinearLayout account = Ui.section(a, body, "Google Account", null);
        accountLine = Ui.label(a, "", 15, Ui.INK, false);
        account.addView(accountLine, Ui.wide(a, 8));
        accountButtons = new LinearLayout(a);
        accountButtons.setOrientation(LinearLayout.VERTICAL);
        account.addView(accountButtons, Ui.wide(a, 8));

        // When and how.
        settingsBox = Expander.card(a, body, "Settings", null, "How often, and over which network", "backup_settings", false).body;
        freqValue = settingRow(settingsBox, "Back up to Google Drive", v -> pickFrequency());
        netValue = settingRow(settingsBox, "Back up using", v -> pickNetwork());
        settingsBox.addView(a.note("Automatic backups run in the background when the phone is online and the battery is not low."),
                Ui.wide(a, 8));

        // Restore.
        LinearLayout restore = Expander.card(a, body, "Restore", null, "For a new phone, or after reinstalling", "backup_restore", false).body;
        restore.addView(a.note("Replace what is on this phone with the backup in your Google Drive. "
                + "Use it on a new phone or after reinstalling. You are asked before anything changes."), Ui.wide(a, 4));
        restoreBtn = Ui.button(a, "Restore from Google Drive", false, v -> lookForRestore(true));
        restore.addView(restoreBtn, Ui.wide(a, 10));

        // Other ways to sign in.
        LinearLayout other = Expander.card(a, body, "Other ways to sign in", null, "A short code, if the account list does not open",
                "backup_other", false).body;
        other.addView(a.note("If the account list does not open on this phone, you can sign in with a short code instead: "
                + "you type it at google.com/device on any phone or computer."), Ui.wide(a, 4));
        other.addView(Ui.button(a, "Check sign-in setup", false, v -> showSetupCheck()), Ui.wide(a, 10));
        other.addView(Ui.button(a, "Sign in with a code", false, v -> codeSignIn()), Ui.wide(a, 8));
        other.addView(Ui.button(a, "Code sign-in setup (client ID)", false, v -> askCodeClient()), Ui.wide(a, 8));
        a.addFooter(body);
    }

    private TextView settingRow(LinearLayout parent, String name, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(a.dp(4), a.dp(10), a.dp(4), a.dp(10));
        row.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 12, 0x200E7C7B));
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.label(a, name, 15, Ui.INK, false));
        TextView value = Ui.label(a, "", 13, Ui.TEAL, false);
        words.addView(value, Ui.wide(a, 2));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Glyph(a, Glyph.CHEVRON, Ui.MUTED), new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        row.setOnClickListener(click);
        parent.addView(row, Ui.wide(a, 6));
        return value;
    }

    // ------------------------------------------------------------------ state

    private static String when(long ms) {
        if (ms <= 0) return "Never";
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(Store.zone());
        LocalDate today = LocalDate.now(Store.zone());
        String day;
        if (z.toLocalDate().equals(today)) day = "Today";
        else if (z.toLocalDate().equals(today.minusDays(1))) day = "Yesterday";
        else day = z.format(DateTimeFormatter.ofPattern("d MMM yyyy", Lang.locale()));
        return day + ", " + Routine.formatTime(z.getHour() * 60 + z.getMinute(), false);
    }

    @Override
    void refresh() {
        if (!built()) return;
        AccountStore s = acc();
        boolean in = s.signedIn();
        lastLine.setText("Last backup: " + when(s.lastOk()));
        sizeLine.setText(s.lastOk() > 0 ? "Size: " + BackupPolicy.size(s.lastSize()) : (in ? "Tap Back up to make the first one." : "Sign in to start backing up."));
        String err = s.lastError();
        errorLine.setText(err);
        errorLine.setVisibility(err.isEmpty() ? View.GONE : View.VISIBLE);
        backupBtn.setEnabled(in && !working);
        backupBtn.setAlpha(in && !working ? 1f : 0.5f);
        backupBtn.setText(working ? "Working…" : "Back up");
        progress.setVisibility(working ? View.VISIBLE : View.GONE);
        restoreBtn.setEnabled(in && !working);
        restoreBtn.setAlpha(in && !working ? 1f : 0.5f);

        accountButtons.removeAllViews();
        if (in) {
            String who = s.name().isEmpty() ? s.email() : s.name() + "\n" + s.email();
            accountLine.setText(who.isEmpty() ? "Signed in" : who);
            accountLine.setTextColor(Ui.INK);
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            l.rightMargin = a.dp(5);
            row.addView(Ui.button(a, "Switch account", false, v -> switchAccount()), l);
            LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            r.leftMargin = a.dp(5);
            row.addView(Ui.button(a, "Sign out", false, v -> confirmSignOut()), r);
            accountButtons.addView(row);
            if (AccountStore.CODE.equals(s.method())) {
                accountButtons.addView(a.note("Signed in with a code."), Ui.wide(a, 6));
            }
        } else {
            accountLine.setText("Not signed in");
            accountLine.setTextColor(Ui.MUTED);
            accountButtons.addView(Ui.button(a, "Sign in with Google", true, v -> pickerSignIn()));
        }
        freqValue.setText(BackupPolicy.name(s.frequency()));
        netValue.setText(s.wifiOnly() ? "Wi-Fi only" : "Wi-Fi or mobile data");
    }

    private void setWorking(boolean w) {
        working = w;
        refresh();
    }

    // ------------------------------------------------------------------ sign-in

    private void pickerSignIn() {
        setWorking(true);
        a.signInWithGoogle(new PickerAuth.Callback() {
            @Override
            public void token(final String accessToken) {
                finishSignIn(AccountStore.PICKER, new Tokens() {
                    private boolean first = true;

                    @Override
                    public String token(boolean fresh) throws java.io.IOException {
                        if (first && !fresh && accessToken != null) {
                            first = false;
                            return accessToken;
                        }
                        return new PickerAuth(a).token(fresh);
                    }
                });
            }

            @Override
            public void failed(String message, boolean useCodeInstead) {
                setWorking(false);
                if (!useCodeInstead) {
                    a.toastText(message);
                    return;
                }
                new DBuilder(a)
                        .setTitle("Could not sign in")
                        .setMessage(message)
                        .setPositiveButton("Check setup", (d, w) -> showSetupCheck())
                        .setNeutralButton("Sign in with a code", (d, w) -> codeSignIn())
                        .setNegativeButton("Close", null)
                        .show();
            }
        });
    }

    /** Reads the account's name and email, saves the sign-in and offers a restore if a backup exists. */
    private void finishSignIn(final String method, final Tokens tokens) {
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            String err = null;
            CloudDrive.Profile me = null;
            CloudDrive.FileInfo found = null;
            String findErr = null;
            try {
                CloudDrive d = new CloudDrive(new Http.Url(), tokens);
                me = d.me();
                AccountStore s = new AccountStore(app);
                s.signedInAs(method, me.email, me.name);
                try {
                    found = CloudBackup.find(d);
                } catch (java.io.IOException | RuntimeException e) {
                    findErr = e.getMessage() == null ? "Could not look for your backup." : e.getMessage();
                }
            } catch (java.io.IOException | RuntimeException e) {
                err = e.getMessage() == null ? "Sign-in failed." : e.getMessage();
            }
            final String lookError = findErr;
            final String error = err;
            final CloudDrive.Profile who = me;
            final CloudDrive.FileInfo backup = found;
            main.post(() -> {
                if (a.isFinishing()) return;
                setWorking(false);
                if (error != null && who == null) {
                    new DBuilder(a).setTitle("Could not sign in").setMessage(error).setPositiveButton("OK", null).show();
                    return;
                }
                DriveJob.schedule(a);
                a.routineChanged();
                a.toastText("Signed in as " + (who.email.isEmpty() ? who.name : who.email));
                if (lookError != null) {
                    // do not save this phone over a backup that could not be looked at
                    new DBuilder(a).setTitle("Could not look for your backup")
                            .setMessage(lookError + "\n\nYou are signed in. Nothing was changed or saved. Tap Restore from Google Drive to try again.")
                            .setPositiveButton("Try again", (d, w) -> lookForRestore(true))
                            .setNegativeButton("Later", null)
                            .show();
                } else if (backup != null) {
                    offerRestore(backup, false);
                } else {
                    new AccountStore(a).setRestoreAsked();
                    final String who2 = who.email.isEmpty() ? who.name : who.email;
                    new DBuilder(a).setTitle("No backup found")
                            .setMessage("There is no Dinacharya backup in the Google Drive of " + who2 + ". "
                                    + "If your old phone used another Google account, sign out and sign in with that one. "
                                    + "Otherwise open Dinacharya on the old phone, go to Backup and tap “Back up now”, then restore here.\n\n"
                                    + "Back up this phone now instead?")
                            .setPositiveButton("Back up this phone", (d, w) -> backUpNow())
                            .setNegativeButton("Not now", null)
                            .show();
                }
            });
        }, "sign-in").start();
    }

    private void switchAccount() {
        new DBuilder(a)
                .setTitle("Switch Google account?")
                .setMessage("You are signed out here, then you pick another account. Backups already in Drive stay there.")
                .setPositiveButton("Switch", (d, w) -> signOut(this::pickerSignIn))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmSignOut() {
        new DBuilder(a)
                .setTitle("Sign out?")
                .setMessage("Automatic backups stop. Your data stays on this phone, and the last backup stays in your Google Drive.")
                .setPositiveButton("Sign out", (d, w) -> signOut(null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void signOut(final Runnable then) {
        setWorking(true);
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            Cloud.signOut(app);
            main.post(() -> {
                if (a.isFinishing()) return;
                setWorking(false);
                a.routineChanged();
                if (then != null) then.run();
                else a.toastText("Signed out");
            });
        }, "sign-out").start();
    }

    // ------------------------------------------------------------------ setup check

    /** Shows what Google Cloud must contain for the account picker, with this installed app's real values. */
    private void showSetupCheck() {
        final String pkg = a.getPackageName();
        final String sha = PickerAuth.signingSha1(a);
        boolean match = sha.equalsIgnoreCase(PickerAuth.EXPECTED_SHA1);
        StringBuilder m = new StringBuilder();
        m.append("Package name\n").append(pkg).append("\n\n");
        m.append("SHA-1 of this installed app\n").append(sha.isEmpty() ? "(could not read)" : sha).append("\n");
        if (sha.isEmpty()) {
            m.append("\n");
        } else if (match) {
            m.append("\u2713 Same as in the README.\n\n");
        } else {
            m.append("\u2717 Different from the README. This APK was signed with another key, so register THIS SHA-1 in Google Cloud "
                    + "(or build from the project as it is, which uses the included key).\n\n");
        }
        m.append("In console.cloud.google.com, in ONE project:\n");
        m.append("1. Google Drive API is enabled.\n");
        m.append("2. Google Auth Platform > Branding is filled in (app name, your email).\n");
        m.append("3. Audience: External, and either \u201cIn production\u201d (press Publish app) or your Gmail listed under Test users.\n");
        m.append("4. Clients: an Android client with exactly the package name and SHA-1 above.\n");
        m.append("5. Wait 5 to 10 minutes after saving, then try again.\n\n");
        m.append("Still failing? Use \u201cSign in with a code\u201d, or copy these details and send them with Settings > Help > Copy problem report.");
        final String copy = "Package: " + pkg + "\nSHA-1: " + sha;
        new DBuilder(a)
                .setTitle("Sign-in setup check")
                .setMessage(m.toString())
                .setPositiveButton("Copy values", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Dinacharya sign-in values", copy));
                        a.toastText("Copied");
                    } catch (RuntimeException e) {
                        a.toastText("Could not copy");
                    }
                })
                .setNeutralButton("Open Google Cloud", (d, w) -> {
                    try {
                        a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/auth/clients")));
                    } catch (RuntimeException e) {
                        a.toastText("Open console.cloud.google.com in a browser");
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ------------------------------------------------------------------ code sign-in

    private void codeSignIn() {
        final AccountStore s = acc();
        final DeviceAuth auth = s.deviceAuth();
        if (!auth.configured()) {
            askCodeClient();
            return;
        }
        setWorking(true);
        new Thread(() -> {
            try {
                final DeviceAuth.DeviceCode dc = auth.start();
                main.post(() -> showCode(auth, dc));
            } catch (java.io.IOException | RuntimeException e) {
                main.post(() -> {
                    setWorking(false);
                    a.toastText(String.valueOf(e.getMessage()));
                });
            }
        }, "code-sign-in").start();
    }

    private void showCode(final DeviceAuth auth, final DeviceAuth.DeviceCode dc) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        box.addView(a.note("1. Open " + dc.url + " on any phone or computer.\n2. Type this code and pick your Google account:"));
        TextView code = Ui.label(a, dc.userCode, 30, Ui.INK, true);
        code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        code.setGravity(Gravity.CENTER);
        code.setPadding(0, a.dp(14), 0, a.dp(14));
        code.setBackground(Ui.box(a, Ui.SOFT, Ui.LINE, 14));
        box.addView(code, Ui.wide(a, 10));
        final TextView status = a.note("Waiting for you…");
        box.addView(status, Ui.wide(a, 10));
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Google code", dc.userCode));
            status.setText("The code is copied. Waiting for you…");
        } catch (RuntimeException ignored) {
            // the code is on screen anyway
        }
        final boolean[] open = {true};
        final AlertDialog dlg = new DBuilder(a)
                .setTitle("Sign in with a code")
                .setView(box)
                .setPositiveButton("Open the page", null)
                .setNegativeButton("Cancel", (d, w) -> {
                    open[0] = false;
                    setWorking(false);
                })
                .create();
        dlg.setOnDismissListener(d -> open[0] = false);
        dlg.show();
        Button go = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (go != null) {
            go.setOnClickListener(v -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(dc.url)));
                } catch (RuntimeException e) {
                    a.toastText("Open " + dc.url + " in a browser");
                }
            });
        }
        new Thread(() -> {
            long until = System.currentTimeMillis() + dc.expiresInSec * 1000L;
            int wait = dc.intervalSec;
            String result = null;
            boolean done = false;
            while (open[0] && System.currentTimeMillis() < until) {
                try {
                    Thread.sleep(wait * 1000L);
                } catch (InterruptedException e) {
                    break;
                }
                if (!open[0]) break;
                try {
                    DeviceAuth.Poll p = auth.poll(dc);
                    if (p == DeviceAuth.Poll.DONE) {
                        done = true;
                        break;
                    }
                    if (p == DeviceAuth.Poll.SLOW_DOWN) wait += 5;
                    if (p == DeviceAuth.Poll.DENIED) {
                        result = "You said no at Google. Nothing was changed.";
                        break;
                    }
                    if (p == DeviceAuth.Poll.EXPIRED) break;
                } catch (java.io.IOException | RuntimeException e) {
                    result = e.getMessage();
                    break;
                }
            }
            final boolean ok = done;
            final String msg = result != null ? result : (ok ? null : (open[0] ? "The code ran out. Try again." : null));
            main.post(() -> {
                if (a.isFinishing()) return;
                if (dlg.isShowing()) dlg.dismiss();
                if (ok) {
                    new AccountStore(a).put("method", AccountStore.CODE);
                    finishSignIn(AccountStore.CODE, auth);
                } else {
                    setWorking(false);
                    if (msg != null) a.toastText(msg);
                }
            });
        }, "code-poll").start();
    }

    private void askCodeClient() {
        final AccountStore s = acc();
        final EditText id = new EditText(a);
        id.setSingleLine(true);
        id.setHint("Client ID (…apps.googleusercontent.com)");
        id.setText(s.codeClientId());
        final EditText secret = new EditText(a);
        secret.setSingleLine(true);
        secret.setHint("Client secret");
        secret.setText(s.codeClientSecret());
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(22), a.dp(8), a.dp(22), 0);
        box.addView(a.note("Code sign-in needs an OAuth client of type “TVs and Limited Input devices” from your Google Cloud project "
                + "(the README shows how, in a few minutes). Paste its ID and secret here."));
        box.addView(id, Ui.wide(a, 8));
        box.addView(secret, Ui.wide(a, 4));
        new DBuilder(a)
                .setTitle("Code sign-in setup")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    s.setCodeClient(id.getText().toString(), secret.getText().toString());
                    if (s.deviceAuth().configured()) codeSignIn();
                    else a.toastText("Both the ID and the secret are needed");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ back up and restore

    private void backUpNow() {
        if (working) return;
        if (acc().holdAuto()) {
            new DBuilder(a).setTitle("Replace the backup in Drive?")
                    .setMessage("Your Google Drive holds a backup from another phone that has not been restored here. "
                            + "Backing up now replaces it with what is on this phone. To keep it, tap Cancel and use “Restore from Google Drive”.")
                    .setPositiveButton("Replace it", (d, w) -> {
                        acc().setHold(false);
                        backUpNow();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        a.flushSave();
        setWorking(true);
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            final Cloud.Outcome o = Cloud.backUp(app);
            main.post(() -> {
                if (a.isFinishing()) return;
                setWorking(false);
                if (o.ok) a.toastText("Backed up (" + BackupPolicy.size(o.size) + ")");
                else if (o.signInAgain) signInAgain(o.error);
                else a.toastText(o.error);
            });
        }, "backup-now").start();
    }

    private void signInAgain(String why) {
        new DBuilder(a)
                .setTitle("Sign in again")
                .setMessage(why + "\n\nSign in again to keep backing up.")
                .setPositiveButton("Sign in", (d, w) -> {
                    if (AccountStore.CODE.equals(acc().method())) codeSignIn();
                    else pickerSignIn();
                })
                .setNegativeButton("Later", null)
                .show();
    }

    /** Looks for a backup in Drive; when {@code asked} the user tapped Restore. */
    private void lookForRestore(final boolean asked) {
        if (working) return;
        setWorking(true);
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            CloudDrive.FileInfo f = null;
            String err = null;
            try {
                f = CloudBackup.find(Cloud.drive(app));
            } catch (java.io.IOException | RuntimeException e) {
                err = e.getMessage();
            }
            final CloudDrive.FileInfo found = f;
            final String error = err;
            main.post(() -> {
                if (a.isFinishing()) return;
                setWorking(false);
                if (error != null) a.toastText(error);
                else if (found == null) a.toastText("There is no backup in this Google account yet.");
                else offerRestore(found, asked);
            });
        }, "find-backup").start();
    }

    private static String describe(CloudDrive.FileInfo f) {
        String when = f.modified;
        try {
            long created = Long.parseLong(f.prop("created"));
            when = BackupPage.when(created);
        } catch (NumberFormatException ignored) {
            if (when.length() >= 10) when = when.substring(0, 10);
        }
        StringBuilder sb = new StringBuilder("Made: " + when).append("\nSize: " + BackupPolicy.size(f.size));
        if (!f.prop("device").isEmpty()) sb.append("\nPhone: ").append(f.prop("device"));
        if (!f.prop("days").isEmpty()) sb.append("\nDay reports: ").append(f.prop("days"));
        return sb.toString();
    }

    private void offerRestore(final CloudDrive.FileInfo f, boolean asked) {
        new AccountStore(a).setRestoreAsked();
        // until this backup is restored (or this phone is backed up on purpose), nothing may replace it in Drive
        if (new AccountStore(a).lastOk() <= 0) new AccountStore(a).setHold(true);
        new DBuilder(a)
                .setTitle(asked ? "Restore this backup?" : "Backup found")
                .setMessage(describe(f) + "\n\nRestoring replaces the routines, settings and history on this phone with the backup. "
                        + "What is on this phone now is kept aside inside the app, just in case.")
                .setPositiveButton("Restore", (d, w) -> restore(f))
                .setNegativeButton(asked ? "Cancel" : "Not now", null)
                .show();
    }

    private void restore(final CloudDrive.FileInfo f) {
        setWorking(true);
        a.beginRestore();
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            String err = null;
            try {
                BackupArchive.Contents c = CloudBackup.fetch(Cloud.drive(app), f);
                BackupData.apply(app, c);
                new AccountStore(app).setHold(false);
                byte[] want = c.files.get("prefs/routine.txt");
                if (want != null) {
                    Routine saved = Store.load(app);
                    Routine wanted = Routine.parse(new String(want, java.nio.charset.StandardCharsets.UTF_8));
                    if (wanted != null && !saved.serialize().equals(wanted.serialize())) {
                        throw new java.io.IOException("The backup was read but your routine did not change. Please try again.");
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                CrashLog.note(app, "restore", e);
                err = e.getMessage() == null ? "Restore failed." : e.getMessage();
            }
            final String error = err;
            main.post(() -> {
                if (a.isFinishing()) return;
                setWorking(false);
                if (error != null) {
                    a.endRestore();
                    new DBuilder(a).setTitle("Could not restore").setMessage(error + "\n\nNothing on this phone was changed.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                a.reloadAll();
                Routine r = a.routine();
                Routine fromDisk = Store.load(app);
                String msg = "Your routine is now “" + r.name + "”: " + r.blocks.size() + " slots, " + r.others.size()
                        + " more routines, " + r.temps.size() + " one-day routines. Your settings, to-dos and reports came back too.";
                if (!fromDisk.serialize().equals(r.serialize())) msg += "\n\n(The screen and the saved copy differed. Close the app and open it again.)";
                new DBuilder(a).setTitle("Restored").setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }, "restore").start();
    }

    // ------------------------------------------------------------------ settings

    private void pickFrequency() {
        final AccountStore s = acc();
        final int[] order = {BackupPolicy.NEVER, BackupPolicy.DAILY, BackupPolicy.WEEKLY, BackupPolicy.MONTHLY, BackupPolicy.MANUAL};
        String[] names = new String[order.length];
        int checked = 0;
        for (int i = 0; i < order.length; i++) {
            names[i] = BackupPolicy.name(order[i]);
            if (order[i] == s.frequency()) checked = i;
        }
        new DBuilder(a)
                .setTitle("Back up to Google Drive")
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    s.setFrequency(order[which]);
                    DriveJob.schedule(a);
                    d.dismiss();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickNetwork() {
        final AccountStore s = acc();
        String[] names = {"Wi-Fi only", "Wi-Fi or mobile data"};
        new DBuilder(a)
                .setTitle("Back up using")
                .setSingleChoiceItems(names, s.wifiOnly() ? 0 : 1, (d, which) -> {
                    s.setWifiOnly(which == 0);
                    DriveJob.schedule(a);
                    d.dismiss();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
