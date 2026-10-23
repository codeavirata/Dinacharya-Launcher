package app.dinacharya.wallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The system calls this when an alarm is due. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmPlanner.Event e = AlarmScheduler.read(intent);
        if (e == null) return;
        Context c = context.getApplicationContext();
        boolean test = AlarmPlanner.isTest(e);
        AlarmScheduler.noteFired(c, e.timeMs, test);
        if (!test) {
            try {
                AlarmState st = Store.alarmState(c);
                st.lastFired = Math.max(st.lastFired, e.timeMs);
                if (e.kind == AlarmPlanner.REASK) st.reaskAt = 0;
                Store.saveAlarmState(c, st);
            } catch (RuntimeException ex) {
                CrashLog.note(c, "alarm state", ex);
            }
            // Line up the next alarm first, so a problem while ringing can never break the chain.
            AlarmScheduler.reschedule(c);
        }
        if (!test && e.mode == Routine.ALERT_SILENT) {
            // a silent slot: a quiet notification with the question, no sound, no vibration and no screen taking over
            AlarmService.postSilent(c, e);
            return;
        }
        Intent svc = new Intent(c, AlarmService.class);
        svc.setAction(AlarmService.ACTION_RING);
        AlarmScheduler.put(svc, e);
        try {
            c.startForegroundService(svc);
        } catch (RuntimeException ex) {
            // Android refused to start the ringing service (a background limit on some phones).
            // A loud notification with the alarm sound still gets the question to you.
            CrashLog.note(c, "start ringing service", ex);
            AlarmService.postFallback(c, e);
        }
    }
}
