package app.dinacharya.wallpaper;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The buttons of a silent slot notification: "Start now" and "In 10 min". */
public class SlotActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        AlarmPlanner.Event e = AlarmScheduler.read(intent);
        if (e == null || intent.getAction() == null) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(AlarmService.silentId(e));
            if (SlotActions.ACTION_START_NOW.equals(intent.getAction())) {
                SlotActions.startNow(c, e);
            } else if (SlotActions.ACTION_START_LATER.equals(intent.getAction())) {
                SlotActions.startAfter(c, e, SlotActions.NOTIFICATION_LATER_MIN);
            }
        } catch (RuntimeException ex) {
            CrashLog.note(c, "slot action", ex);
        }
    }
}
