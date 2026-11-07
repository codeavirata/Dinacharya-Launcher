package app.dinacharya.wallpaper;

/**
 * Which quick answers an alarm notification carries. The ringing notification and the backup notification (the one used when Android
 * will not let the ringing service run, which is the normal case on a phone that has not yet allowed exact alarms) must offer the same
 * buttons, so the choice is made here, once. Uses no Android classes, so it is tested on its own.
 * <ul>
 * <li>a slot (or a slot asked again), not the start-of-day alarm and not the test: Start now, In 10 min;</li>
 * <li>a break that is over: Back to work, +5 min (only while the slot has time left for it), Stop;</li>
 * <li>every other alarm except the start-of-day alarm: Stop.</li>
 * </ul>
 */
public final class NoticeButtons {
    public static final int START_NOW = 1, START_LATER = 2, BREAK_BACK = 3, BREAK_MORE = 4, STOP = 5;

    private NoticeButtons() {
    }

    /** The buttons for this alarm, left to right. {@code canGrow}: a break that is over can still be made longer. */
    public static int[] forEvent(AlarmPlanner.Event e, boolean canGrow) {
        if (e == null) return new int[0];
        if ((e.kind == AlarmPlanner.SLOT || e.kind == AlarmPlanner.START_AGAIN) && !e.wake && !AlarmPlanner.isTest(e)) {
            return new int[]{START_NOW, START_LATER};
        }
        if (e.kind == AlarmPlanner.BREAK_END) {
            return canGrow ? new int[]{BREAK_BACK, BREAK_MORE, STOP} : new int[]{BREAK_BACK, STOP};
        }
        if (!e.wake) return new int[]{STOP};
        return new int[0];
    }
}
