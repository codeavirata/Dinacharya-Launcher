package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Intent;

/**
 * Once, after the app was installed through a friend's invite link: a sheet that says a friend invited the person and what they get, with
 * one button that uses the code. The reward is stated in the sheet before anything is used. Not now leaves the code filled in on the Invite
 * friends screen.
 */
final class InviteOffer {
    private InviteOffer() {
    }

    private static boolean showing;

    static void maybeShow(final Activity a) {
        if (a.isFinishing() || a.isDestroyed() || showing) return;
        final String code = InstallReferrerReader.pendingCode(a);
        if (code.isEmpty() || InstallReferrerReader.offered(a)) return;
        InstallReferrerReader.setOffered(a);
        showing = true;
        try {
            new DBuilder(a)
                    .setTitle("A friend invited you")
                    .setMessage("Use their code to get 7 days of free Pro. Your friend also earns Pro days once you have used the app on two different days.")
                    .setPositiveButton("Get 7 days of Pro", (d, w) -> {
                        Intent i = new Intent(a, InviteActivity.class).putExtra(InviteActivity.EXTRA_CODE, code).putExtra(InviteActivity.EXTRA_AUTO, true);
                        a.startActivity(i);
                    })
                    .setNegativeButton("Not now", null)
                    .setOnDismissListener(d -> showing = false)
                    .show();
        } catch (RuntimeException e) {
            showing = false;
            CrashLog.note(a, "invite offer", e);
        }
    }
}
