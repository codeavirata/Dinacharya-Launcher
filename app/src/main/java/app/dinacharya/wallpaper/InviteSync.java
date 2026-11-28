package app.dinacharya.wallpaper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.time.LocalDate;

/**
 * The quiet part of invites, done in the background when the dashboard opens (never on the main thread, never more than once every
 * six hours): a friend's phone tells the server the app has been used on enough days, and an inviter's phone collects rewards that
 * are waiting. A server that cannot be reached is ignored. Nothing else in the app depends on this.
 */
final class InviteSync {
    private static final long EVERY_MS = 6L * 3600000L;
    private static volatile boolean running;

    private InviteSync() {
    }

    /** The app was opened: count the day for a friend's record, and sync in the background when it is time. */
    static void opened(Context context) {
        final Context c = context.getApplicationContext();
        try {
            Pro pro = Pro.get(c);
            long today = LocalDate.now(Store.zone()).toEpochDay();
            String used = pro.inviteUsed();
            String next = InviteActive.used(used, today);
            if (!next.equals(used)) pro.setInviteUsed(next);
            boolean friend = InviteActive.due(pro.inviteUsed());
            boolean inviter = pro.inviteAsked();
            if (!friend && !inviter) return;
            long now = System.currentTimeMillis();
            if (!friend && now - pro.inviteSyncedAt() < EVERY_MS && pro.inviteSyncedAt() <= now) return;
            if (running) return;
            running = true;
            new Thread(() -> {
                try {
                    run(c);
                } catch (RuntimeException e) {
                    CrashLog.note(c, "invite sync", e);
                } finally {
                    running = false;
                }
            }, "invite-sync").start();
        } catch (RuntimeException e) {
            CrashLog.note(c, "invite opened", e);
        }
    }

    /** Does the calls (call from a background thread). Returns the days collected for an inviter, or 0. */
    static int run(Context c) {
        Pro pro = Pro.get(c);
        InviteClient api = new InviteClient(new Http.Url(6000, 8000), InviteClient.BASE);
        String device = pro.inviteDevice();
        if (InviteActive.due(pro.inviteUsed())) {
            InviteClient.Result r = api.active(device);
            if (r.ok) pro.setInviteUsed(InviteActive.told(pro.inviteUsed()));
        }
        int got = 0;
        if (pro.inviteAsked()) {
            InviteClient.Result s = api.status(device);
            if (s.ok) {
                pro.setInviteSyncedAt(System.currentTimeMillis());
                if (s.waiting > 0) got = collect(c, api, device);
            }
        }
        return got;
    }

    /** Collects waiting rewards and adds the days; tells the person. Returns the days added. */
    static int collect(Context c, InviteClient api, String device) {
        InviteClient.Result r = api.claim(device);
        if (!r.ok || r.days <= 0) return 0;
        Pro.get(c).addInviteDays(r.days);
        final int days = r.days;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(c, Lang.tr("A friend joined: you earned " + days + " free Pro days"), Toast.LENGTH_LONG).show();
            } catch (RuntimeException ignored) {
                // nothing to do
            }
        });
        return days;
    }
}
