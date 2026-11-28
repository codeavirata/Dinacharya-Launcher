package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Intent;

/**
 * Once, after the app was installed through a friend's invite link: a sheet that says a friend invited the person and what they get, with
 * one button that uses the code. The reward is stated in the sheet before anything is used. The server is asked first whether the code can
 * be used ({@link InviteCheck}). Not now leaves the code filled in on the Invite friends screen.
 */
final class InviteOffer {
    private InviteOffer() {
    }

    private static boolean showing;
    private static long lastTry;

    /**
     * Asks the server whether the code can be used, and only then shows the sheet. A code the server refuses is forgotten, so a made-up
     * link shows nothing. When the server cannot be reached nothing is shown and nothing is used up: it asks again later.
     */
    static void maybeShow(final Activity a) {
        if (a.isFinishing() || a.isDestroyed() || showing) return;
        final String code = InstallReferrerReader.pendingCode(a);
        if (code.isEmpty() || InstallReferrerReader.offered(a)) return;
        long now = System.currentTimeMillis();
        if (!InviteCheck.canTry(lastTry, now)) return;
        lastTry = now;
        showing = true;
        final String device;
        try {
            device = Pro.get(a).inviteDevice();
        } catch (RuntimeException e) {
            showing = false;
            CrashLog.note(a, "invite offer", e);
            return;
        }
        new Thread(() -> {
            int verdict = InviteCheck.LATER;
            try {
                verdict = InviteCheck.verdict(new InviteClient(new Http.Url(6000, 8000), InviteClient.BASE).check(device, code));
            } catch (RuntimeException e) {
                CrashLog.note(a, "invite check", e);
            }
            final int v = verdict;
            try {
                a.runOnUiThread(() -> decide(a, code, v));
            } catch (RuntimeException e) {
                showing = false;
            }
        }, "invite-check").start();
    }

    private static void decide(final Activity a, final String code, int verdict) {
        if (a.isFinishing() || a.isDestroyed()) {
            showing = false;
            lastTry = 0;   // the screen went away while the server was asked: the next screen may ask again at once
            return;
        }
        if (verdict == InviteCheck.LATER) {
            showing = false;
            return;
        }
        InstallReferrerReader.setOffered(a);
        if (verdict == InviteCheck.DROP) {
            InstallReferrerReader.clear(a);
            showing = false;
            return;
        }
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
