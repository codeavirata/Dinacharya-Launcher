package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** Alarm planning: off by default, real alarms only, the test alarm is its own thing. */
public class AlarmPlanTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }

    public static void main(String[] a) {
        Routine r = Routine.sample();
        ok("alarms are off on a new routine", !r.alarmOn);
        ok("nothing to ring while off", AlarmPlanner.upcoming(r, new Overrides(), new AlarmState(), at(21, 5, 0), Z).isEmpty());

        r.alarmOn = true;
        List<AlarmPlanner.Event> l = AlarmPlanner.upcoming(r, new Overrides(), new AlarmState(), at(21, 5, 0), Z);
        ok("alarms on: there are alarms", !l.isEmpty());
        boolean real = true, sorted = true;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).src < 0 || AlarmPlanner.isTest(l.get(i))) real = false;
            if (i > 0 && l.get(i).timeMs < l.get(i - 1).timeMs) sorted = false;
        }
        ok("every planned alarm belongs to a real slot, never the test marker", real);
        ok("sorted by time", sorted);
        ok("first alarm is after the moment asked", l.get(0).timeMs > at(21, 5, 0));

        // The bell of a single slot.
        int firstIdx = l.get(0).src;
        r.blocks.get(firstIdx).setAlert(Routine.ALERT_NONE);
        AlarmPlanner.Event n = AlarmPlanner.next(r, new Overrides(), new AlarmState(), at(21, 5, 0), Z);
        ok("a slot with no alert is skipped", n == null || n.src != firstIdx || n.timeMs != l.get(0).timeMs);

        // The test marker.
        AlarmPlanner.Event t = new AlarmPlanner.Event(at(21, 9, 0), AlarmPlanner.SLOT, "2026-10-21", AlarmPlanner.TEST_SRC, "Test alarm",
                Routine.TASK, at(21, 9, 0), at(21, 9, 1), -1L, false);
        ok("test event is recognised", AlarmPlanner.isTest(t));
        ok("a real event is not a test", !AlarmPlanner.isTest(l.get(0)));
        ok("null is not a test", !AlarmPlanner.isTest(null));
        ok("test marker cannot be a slot number", AlarmPlanner.TEST_SRC < 0);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
    }
}
