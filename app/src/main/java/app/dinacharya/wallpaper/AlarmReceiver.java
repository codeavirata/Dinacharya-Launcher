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
        // Library mode is looked at when the alarm rings, not when it was set, so switching it on or off in between is always right:
        // an alarm that would ring only vibrates while it is on.
        try {
            e = e.withMode(LibraryMode.quietMode(Store.library(c), System.currentTimeMillis(), Store.zone(), e.mode));
        } catch (RuntimeException ex) {
            CrashLog.note(c, "library mode", ex);
        }
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
        // A slot that was started early (or whose slot before it was skipped) has begun already: its own alarm must not ring. This is the
        // last check at ring time, in case the alarm was set before the person began the slot.
        if (!test && (e.kind == AlarmPlanner.SLOT || e.kind == AlarmPlanner.BEDTIME)) {
            try {
                if (AlarmPlanner.begunEarly(Store.history(c).events(), e.day, e.src, e.timeMs)) return;
            } catch (RuntimeException ex) {
                CrashLog.note(c, "begun early check", ex);
            }
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
