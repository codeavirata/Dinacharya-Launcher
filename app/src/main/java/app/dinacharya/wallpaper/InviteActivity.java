package app.dinacharya.wallpaper;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Invite friends: your code, a button to share it (the phone's share sheet), the reward schedule, a box for a
 * friend's code, and what your invites have earned. It is the only screen that talks to the invite server, always in the background,
 * with short time-outs: a server that is down only shows a short message here, and nothing else in the app notices.
 * A link like dinacharya://invite/ABCD2345 (from the invite web page) opens this screen with the code filled in.
 */
public class InviteActivity extends LangActivity {
    private static final String LINK = InviteClient.BASE + "/i/";
    private LinearLayout body;
    private TextView codeView, statusView, noteView, tiersView;
    private EditText friendCode;
    private String code = "";
    private int first = 4, firstDays = 7, laterDays = 3, max = 10;
    private volatile boolean gone;

    /** Extras from the sheet that offers the code of the friend's link (see {@link InviteOffer}). */
    static final String EXTRA_CODE = "friend_code", EXTRA_AUTO = "friend_code_use";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        Pro.get(this).setInviteAsked();
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Insets.pad(sv);
        setContentView(sv);
        String pre = linkedCode(getIntent());
        String stored = InstallReferrerReader.pendingCode(this);
        if (pre.isEmpty()) pre = stored;
        build(pre);
        refresh();
        // The code is used by itself only when it is the one kept from the install link and the person chose to use it in the sheet.
        Intent in = getIntent();
        if (in != null && in.getBooleanExtra(EXTRA_AUTO, false) && !stored.isEmpty() && stored.equals(in.getStringExtra(EXTRA_CODE)) && friendCode != null) redeem();
    }

    @Override
    protected void onDestroy() {
        gone = true;
        super.onDestroy();
    }

    /** The code in a link such as dinacharya://invite/ABCD2345, or "". */
    private static String linkedCode(Intent i) {
        try {
            Uri u = i == null ? null : i.getData();
            if (u == null || !"dinacharya".equals(u.getScheme()) || !"invite".equals(u.getHost())) return "";
            String p = u.getLastPathSegment();
            return p == null ? "" : p.toUpperCase().replaceAll("[^A-Z0-9]", "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private TextView small(String text, int color) {
        TextView t = Ui.label(this, text, 13, color, false);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        return t;
    }

    private void build(String prefill) {
        final Pro pro = Pro.get(this);
        body.addView(Ui.eyebrow(this, "Dinacharya"));
        body.addView(Ui.label(this, "Invite friends", 28, Ui.INK, true), Ui.wide(this, 4));
        body.addView(small("Share Dinacharya with friends. When a friend uses your code and starts using the app, you both get free Pro days.", Ui.MUTED), Ui.wide(this, 6));

        LinearLayout mine = Ui.section(this, body, "Your code", null);
        codeView = Ui.label(this, "…", 34, Ui.INK, true);
        codeView.setLetterSpacing(0.15f);
        codeView.setGravity(android.view.Gravity.CENTER);
        codeView.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        mine.addView(codeView, Ui.wide(this, 6));
        mine.addView(Ui.button(this, "Share", true, v -> share()), Ui.wide(this, 8));
        statusView = small("", Ui.MUTED);
        mine.addView(statusView, Ui.wide(this, 10));

        LinearLayout how = Ui.section(this, body, "What you earn", null);
        tiersView = small("", Ui.INK);
        how.addView(tiersView, Ui.wide(this, 4));
        how.addView(small("A friend counts once they have used the app on two different days. Each friend can use one code, on one phone.", Ui.MUTED), Ui.wide(this, 6));
        showTiers();

        if (pro.inviteUsed().isEmpty()) {
            LinearLayout have = Ui.section(this, body, "Have a friend's code?", null);
            have.addView(small("Enter it to get 7 days of free Pro.", Ui.MUTED), Ui.wide(this, 4));
            friendCode = new EditText(this);
            friendCode.setSingleLine(true);
            friendCode.setHint("Code");
            friendCode.setText(prefill);
            friendCode.setTextColor(Ui.INK);
            friendCode.setHintTextColor(Ui.MUTED);
            friendCode.setTextSize(18);
            friendCode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            friendCode.setBackground(Ui.box(this, Ui.SURFACE, 0, 14));
            friendCode.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            have.addView(friendCode, Ui.wide(this, 8));
            have.addView(Ui.button(this, "Use this code", false, v -> redeem()), Ui.wide(this, 8));
        }
        noteView = small("", Ui.MUTED);
        body.addView(noteView, Ui.wide(this, 12));
    }

    private void showTiers() {
        tiersView.setText("Friends 1 to " + first + ": " + firstDays + " days of Pro each\nFriends " + (first + 1) + " to " + max + ": " + laterDays
                + " days each\nUp to " + (first * firstDays + (max - first) * laterDays) + " days in all");
    }

    private void say(final String text) {
        runOnUiThread(() -> {
            if (!gone && noteView != null) noteView.setText(text);
        });
    }

    /** Asks the server for the code and what the invites have earned, and collects any reward that is waiting. */
    private void refresh() {
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                InviteClient api = new InviteClient(new Http.Url(6000, 8000), InviteClient.BASE);
                String device = Pro.get(c).inviteDevice();
                final InviteClient.Result r = api.code(device);
                if (!r.ok) {
                    say(r.network ? InviteClient.DOWN : r.error);
                    return;
                }
                code = r.code;
                runOnUiThread(() -> {
                    if (!gone) codeView.setText(code);
                });
                InviteClient.Result s = api.status(device);
                if (!s.ok) {
                    say(s.network ? InviteClient.DOWN : s.error);
                    return;
                }
                if (s.first > 0) {
                    first = s.first;
                    firstDays = s.firstDays;
                    laterDays = s.laterDays;
                    max = s.max;
                    runOnUiThread(() -> {
                        if (!gone) showTiers();
                    });
                }
                InviteSync.run(c);
                InviteClient.Result t = api.status(device);
                final InviteClient.Result shown = t.ok ? t : s;
                runOnUiThread(() -> {
                    if (gone) return;
                    int left = Pro.get(c).inviteDaysLeft();
                    statusView.setText(shown.invited + " of " + shown.max + " invites used"
                            + (shown.pending > 0 ? "\n" + shown.pending + " waiting for the friend to use the app" : "")
                            + (left > 0 ? "\nFree Pro days left: " + left : ""));
                });
            } catch (RuntimeException e) {
                CrashLog.note(c, "invite screen", e);
                say(InviteClient.DOWN);
            }
        }, "invite-screen").start();
    }

    private void redeem() {
        final String typed = friendCode == null ? "" : friendCode.getText().toString().trim();
        if (typed.isEmpty()) {
            say("Type your friend's code first.");
            return;
        }
        say("One moment…");
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                InviteClient api = new InviteClient(new Http.Url(6000, 8000), InviteClient.BASE);
                InviteClient.Result r = api.redeem(Pro.get(c).inviteDevice(), typed);
                if (!r.ok) {
                    say(r.network ? InviteClient.DOWN : r.error);
                    return;
                }
                Pro pro = Pro.get(c);
                pro.addInviteDays(r.days > 0 ? r.days : 7);
                pro.setInviteUsed(InviteActive.redeemed(java.time.LocalDate.now(Store.zone()).toEpochDay()));
                runOnUiThread(() -> {
                    if (gone) return;
                    Lang.toast(c, "You got " + (r.days > 0 ? r.days : 7) + " days of free Pro", Toast.LENGTH_LONG).show();
                    body.removeAllViews();
                    build("");
                    refresh();
                });
            } catch (RuntimeException e) {
                CrashLog.note(c, "invite redeem", e);
                say(InviteClient.DOWN);
            }
        }, "invite-redeem").start();
    }

    /** The message that goes with the code. */
    private String message() {
        return "I plan my day with Dinacharya Launcher. Get it and use my code " + code + " for 7 days of free Pro: " + LINK + code;
    }

    /** Opens the phone's share sheet with the invite message. If the phone has no share sheet, the message is copied instead. */
    private void share() {
        if (code.isEmpty()) {
            say("Your code is not ready yet. Check your connection and try again.");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message());
        try {
            startActivity(Intent.createChooser(send, "Invite a friend"));
        } catch (ActivityNotFoundException | SecurityException e) {
            CrashLog.note(this, "invite share", e);
            copyCode();
        }
    }

    private void copyCode() {
        if (code.isEmpty()) {
            say("Your code is not ready yet. Check your connection and try again.");
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Dinacharya invite", message()));
            Lang.toast(this, "Copied", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            CrashLog.note(this, "invite copy", e);
        }
    }
}
