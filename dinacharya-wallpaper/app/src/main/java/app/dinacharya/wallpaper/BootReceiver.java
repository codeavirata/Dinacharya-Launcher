package app.dinacharya.wallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart, an app update or a clock change, alarms, the end-of-day job and the end of a video pass are set again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        AlarmScheduler.reschedule(c);
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            StrongMode.ensure(c);   // only these two may start the strong lock's service from the background
        }
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                PassSettle.run(c);
                PassSettle.schedule(c);
                DayCloser.closeFinished(c);
                DayCloser.schedule(c);
                DriveJob.schedule(c);
            } catch (RuntimeException e) {
                CrashLog.note(c, "boot", e);
            } finally {
                pr.finish();
            }
        }, "boot-work").start();
    }
}
