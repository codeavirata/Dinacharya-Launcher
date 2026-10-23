package app.dinacharya.wallpaper;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * The automatic backup. Android wakes it every few hours when the phone is online (on Wi-Fi only,
 * if you chose that) and the battery is not low; it backs up when your schedule says it is time.
 */
public class DriveJob extends JobService {
    private static final int JOB_ID = 4100;
    private static final long EVERY = 6 * 3600 * 1000L;

    /** Sets the job up to match the settings, or removes it. Safe to call often. */
    static void schedule(Context context) {
        try {
            Context c = context.getApplicationContext();
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            AccountStore a = new AccountStore(c);
            if (!a.signedIn() || !BackupPolicy.automatic(a.frequency())) {
                js.cancel(JOB_ID);
                a.put("jobKey", "");
                return;
            }
            String key = a.frequency() + ":" + a.wifiOnly();
            // Setting the same job again would restart its clock, so only do it when something changed.
            if (key.equals(a.get("jobKey")) && js.getPendingJob(JOB_ID) != null) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, DriveJob.class))
                    .setRequiredNetworkType(a.wifiOnly() ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .setPeriodic(EVERY)
                    .build());
            a.put("jobKey", key);
        } catch (RuntimeException e) {
            CrashLog.note(context, "backup schedule", e);
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                Cloud.runIfDue(c);
            } catch (RuntimeException e) {
                CrashLog.note(c, "automatic backup", e);
            }
            jobFinished(params, false);
        }, "drive-backup").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
