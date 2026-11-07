package app.dinacharya.wallpaper;

import java.util.Arrays;

/**
 * The quick answers on an alarm notification. They are chosen in one place and used by the ringing notification and by the backup
 * notification (the one Android shows when it will not run the ringing service, which is the case on a phone that has not allowed
 * exact alarms). Before, the backup notification had no buttons at all.
 */
public class NoticeButtonsTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static AlarmPlanner.Event ev(int kind, int src, boolean wake) {
        return new AlarmPlanner.Event(1000000L, kind, "2026-11-07", src, "Revision", Routine.TASK, 1000000L, 6000000L, -1L, false,
                Routine.ALERT_RING, wake);
    }

    static boolean is(int[] got, int... want) { return Arrays.equals(got, want); }

    public static void main(String[] a) {
        AlarmPlanner.Event slot = ev(AlarmPlanner.SLOT, 12, false);
        AlarmPlanner.Event again = ev(AlarmPlanner.START_AGAIN, 12, false);
        AlarmPlanner.Event brk = ev(AlarmPlanner.BREAK_END, 12, false);
        AlarmPlanner.Event wake = ev(AlarmPlanner.SLOT, 0, true);
        AlarmPlanner.Event wakeAgain = ev(AlarmPlanner.START_AGAIN, 0, true);
        AlarmPlanner.Event test = ev(AlarmPlanner.SLOT, AlarmPlanner.TEST_SRC, false);
        AlarmPlanner.Event bed = ev(AlarmPlanner.BEDTIME, 30, false);
        AlarmPlanner.Event reask = ev(AlarmPlanner.REASK, 30, false);
        AlarmPlanner.Event plan = ev(AlarmPlanner.PLAN_ASK, -1, false);

        ok("a slot: Start now, In 10 min", is(NoticeButtons.forEvent(slot, false), NoticeButtons.START_NOW, NoticeButtons.START_LATER));
        ok("a slot asked again: Start now, In 10 min", is(NoticeButtons.forEvent(again, false), NoticeButtons.START_NOW, NoticeButtons.START_LATER));
        ok("a slot's buttons do not depend on the break flag", is(NoticeButtons.forEvent(slot, true), NoticeButtons.START_NOW, NoticeButtons.START_LATER));
        ok("a break that is over, with time left: Back to work, +5 min, Stop",
                is(NoticeButtons.forEvent(brk, true), NoticeButtons.BREAK_BACK, NoticeButtons.BREAK_MORE, NoticeButtons.STOP));
        ok("a break that is over, slot nearly done: no +5 min", is(NoticeButtons.forEvent(brk, false), NoticeButtons.BREAK_BACK, NoticeButtons.STOP));
        ok("the start-of-day alarm has no buttons (it rings until the day is started or put off)",
                NoticeButtons.forEvent(wake, false).length == 0 && NoticeButtons.forEvent(wakeAgain, false).length == 0);
        ok("the test alarm can only be stopped", is(NoticeButtons.forEvent(test, false), NoticeButtons.STOP));
        ok("bedtime can be silenced", is(NoticeButtons.forEvent(bed, false), NoticeButtons.STOP));
        ok("the bedtime question asked again can be silenced", is(NoticeButtons.forEvent(reask, false), NoticeButtons.STOP));
        ok("the plan-your-day question can be silenced", is(NoticeButtons.forEvent(plan, false), NoticeButtons.STOP));
        ok("no alarm, no buttons", NoticeButtons.forEvent(null, true).length == 0);

        // every alarm that is not the start-of-day alarm has at least one way to answer from the shade
        int[] kinds = {AlarmPlanner.SLOT, AlarmPlanner.BEDTIME, AlarmPlanner.REASK, AlarmPlanner.BREAK_END, AlarmPlanner.START_AGAIN, AlarmPlanner.PLAN_ASK};
        boolean all = true;
        for (int k : kinds) {
            all &= NoticeButtons.forEvent(ev(k, 5, false), true).length > 0;
            all &= NoticeButtons.forEvent(ev(k, 5, false), false).length > 0;
        }
        ok("every ordinary alarm has a button", all);
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails != 0) System.exit(1);
    }
}
