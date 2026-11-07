package app.dinacharya.wallpaper;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The buttons of an alarm notification, silent or ringing: "Start now" and "In 10 min" for a slot; "Back to work", "+5 min" and "Stop" for a break that is over; "Stop" for the rest. */
public class SlotActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        AlarmPlanner.Event e = AlarmScheduler.read(intent);
        if (e == null || intent.getAction() == null) return;
        try {
            String action = intent.getAction();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(AlarmService.silentId(e));
            if (SlotActions.ACTION_START_NOW.equals(action)) {
                SlotActions.startNow(c, e);
            } else if (SlotActions.ACTION_START_LATER.equals(action)) {
                SlotActions.startAfter(c, e, SlotActions.NOTIFICATION_LATER_MIN);
            } else if (SlotActions.ACTION_BREAK_BACK.equals(action)) {
                AlarmScheduler.reschedule(c);   // as "Back to work" in the question: the break is over, the plan goes on
            } else if (SlotActions.ACTION_BREAK_MORE.equals(action)) {
                long until = AlarmService.breakCanGrow(e) ? SlotActions.extendBreak(c, SlotActions.BREAK_MORE_MIN) : -1L;
                if (until > 0) Lang.toast(c, "Break extended until " + SlotActions.clockText(c, until), android.widget.Toast.LENGTH_SHORT).show();
                else AlarmScheduler.reschedule(c);
            } else if (SlotActions.ACTION_STOP.equals(action)) {
                AlarmService.silenceFromNotification(c);
                AlarmService.clearRingNotification(c);   // the backup notification (no ringing service) goes too, and with it its repeating sound
                AlarmService.postSilent(c, e);   // the sound stops, the question stays one tap away
                return;
            }
            AlarmService.silenceFromNotification(c);   // when the buttons were on the ringing notification, it stops now
            AlarmService.clearRingNotification(c);     // ... and when they were on the backup notification, that goes now
        } catch (RuntimeException ex) {
            CrashLog.note(c, "slot action", ex);
        }
    }
}
