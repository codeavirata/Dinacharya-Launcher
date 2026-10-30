package app.dinacharya.wallpaper;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Looks at the alarms and the wallpaper every few hours ({@link Keeper}), also when the app is never opened. An alarm that the phone
 * dropped quietly is set again within hours instead of when the app is next opened. It cannot run after the app is closed from App info
 * (Android then drops every job too); the next time Home is pressed the launcher puts everything back.
 */
public class HealthJob extends JobService {
    private static final int JOB_ID = 4101;
    private static final long EVERY = 3 * 3600 * 1000L;

    /** Sets the job up. Safe to call often: an existing one is left alone, so its clock is not restarted. */
    static void schedule(Context context) {
        try {
            Context c = context.getApplicationContext();
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null || js.getPendingJob(JOB_ID) != null) return;
            js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, HealthJob.class))
                    .setPersisted(true)
                    .setPeriodic(EVERY)
                    .build());
        } catch (RuntimeException e) {
            CrashLog.note(context, "health job schedule", e);
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context c = getApplicationContext();
        new Thread(() -> {
            try {
                Keeper.ensure(c, "job");
            } catch (RuntimeException e) {
                CrashLog.note(c, "health job", e);
            }
            jobFinished(params, false);
        }, "health-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
