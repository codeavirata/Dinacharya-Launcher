package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * Every routine option, one alarm after another, the way the phone does it: the alarm is set for the next event, it rings, the time of the
 * ring is kept, and the next one is set after that. For each option the morning alarm after the night's sleep must ring, every slot must
 * ring once, and a routine for one day must not take any of that away. Also the marks that keep a started slot's alarm quiet.
 */
public class AlarmChainTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static LocalDate day(int d) { return LocalDate.of(2026, 10, d); }
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static String f(long ms) { return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), Z).toLocalDateTime().toString(); }

    static Routine base() {
        Routine r = Routine.sample();
        r.alarmOn = true;
        r.mode = Routine.MODE_WAKE;
        r.anchor = 330;
        return r;
    }

    /** Rings every alarm from {@code from} until {@code to}, as the phone would: next, ring, remember, next. */
    static List<AlarmPlanner.Event> chain(Routine r, AlarmState st, long from, long to) {
        Overrides ov = new Overrides();
        ov.sig = Plan.signature(r);
        List<AlarmPlanner.Event> out = new ArrayList<>();
        long now = from;
        for (int i = 0; i < 600; i++) {
            AlarmPlanner.Event e = AlarmPlanner.next(r, ov, st, Math.max(now, st.lastFired), Z);
            if (e == null || e.timeMs > to) break;
            st.lastFired = Math.max(st.lastFired, e.timeMs);
            now = e.timeMs + 700;
            out.add(e);
        }
        return out;
    }

    static AlarmPlanner.Event wakeOn(List<AlarmPlanner.Event> l, LocalDate d) {
        for (AlarmPlanner.Event e : l) if (e.wake && e.day.equals(d.toString())) return e;
        return null;
    }

    static long startOf(Routine r, LocalDate d) {
        return d.atStartOfDay(Z).plusMinutes(r.forDay(d).compute().start).toInstant().toEpochMilli();
    }

    /** Checks one option: strictly forward, nothing twice, every day's morning alarm on time, every slot of the days in the middle rings once. */
    static void option(String name, Routine r, long from, int firstDay, int lastDay) {
        r = Routine.parse(r.serialize());   // what is saved and read back is what rings
        AlarmState st = new AlarmState();
        List<AlarmPlanner.Event> l = chain(r, st, from, day(lastDay).plusDays(1).atTime(12, 0).atZone(Z).toInstant().toEpochMilli());
        boolean forward = true;
        for (int i = 1; i < l.size(); i++) forward &= l.get(i).timeMs > l.get(i - 1).timeMs;
        ok(name + ": alarms come strictly one after another", forward && !l.isEmpty());
        Set<String> seen = new HashSet<>();
        boolean once = true;
        for (AlarmPlanner.Event e : l) once &= seen.add(e.day + "#" + e.src);
        ok(name + ": no slot rings twice", once);
        boolean mornings = true;
        String why = "";
        for (int d = firstDay; d <= lastDay; d++) {
            AlarmPlanner.Event w = wakeOn(l, day(d));
            if (w == null || w.timeMs != startOf(r, day(d))) {
                mornings = false;
                why += " " + d + (w == null ? ":none" : ":" + f(w.timeMs) + "!=" + f(startOf(r, day(d))));
            }
        }
        ok(name + ": the morning alarm rings at the day's start every day" + why, mornings);
        boolean all = true;
        for (int d = firstDay; d <= lastDay; d++) {
            int want = 0;
            for (Routine.Block b : r.forDay(day(d)).blocks) if (b.alert != Routine.ALERT_NONE) want++;
            int got = 0;
            for (AlarmPlanner.Event e : l) if (e.day.equals(day(d).toString())) got++;
            if (got != want) { all = false; why = " day " + d + " got " + got + " want " + want; }
        }
        ok(name + ": every slot of each day rings" + why, all);
    }

    public static void main(String[] a) {
        long from = at(28, 20, 19);   // the evening the routine is chosen

        option("main routine", base(), from, 29, 31);

        Routine wk = base();
        Routine weekend = wk.addOther("Weekend", wk);
        weekend.anchor = 450;
        wk.dayUse[5] = 1;
        wk.dayUse[6] = 1;
        option("weekday and weekend routines", wk, at(29, 20, 19), 30, 31);   // 31 Oct 2026 is a Saturday

        Routine today = base();
        today.addTemp(day(28), "Today", today).anchor = 390;
        option("a routine for today only", today, from, 29, 31);

        Routine tomorrow = base();
        tomorrow.addTemp(day(29), "Tomorrow", tomorrow).anchor = 420;
        option("a routine for tomorrow", tomorrow, from, 29, 31);

        Routine later = base();
        later.addTemp(day(31), "Later", later).anchor = 300;
        option("a routine for a date three days ahead", later, from, 29, 31);

        Routine two = base();
        two.addTemp(day(29), "A", two).anchor = 360;
        two.addTemp(day(30), "B", two).anchor = 480;
        option("routines for two days in a row", two, from, 29, 31);

        Routine replaced = base();
        replaced.addTemp(day(29), "A", replaced).anchor = 360;
        replaced.addTemp(day(29), "A again", replaced).anchor = 390;
        option("a routine for a date made twice", replaced, from, 29, 31);

        Routine gone = base();
        Routine tg = gone.addTemp(day(29), "A", gone);
        gone.temps.remove(tg);
        option("a routine for one day that was deleted", gone, from, 29, 31);

        Routine moved = base();
        Routine tm = moved.addTemp(day(29), "A", moved);
        tm.anchor = 400;
        moved.moveTemp(tm, day(30));
        option("a routine for one day that was moved to another date", moved, from, 29, 31);

        Routine viaWeek = base();
        Routine wend = viaWeek.addOther("Weekend", viaWeek);
        wend.anchor = 450;
        viaWeek.dayUse[5] = 1;
        viaWeek.dayUse[6] = 1;
        viaWeek.addTemp(day(30), "Friday only", viaWeek).anchor = 345;
        option("a routine for one day next to a weekend routine", viaWeek, from, 29, 31);

        // the one-day routine has fewer and more slots than the usual one
        Routine shape = base();
        Routine ts = shape.addTemp(day(29), "Short", shape);
        ts.blocks.remove(3);
        ts.blocks.add(1, new Routine.Block(Routine.TASK, "Extra", 30));
        for (Routine.Block b : ts.blocks) if (b.kind == Routine.SLEEP) b.mins = 1440 - sumWithoutSleep(ts);
        option("a routine for one day with other slots", shape, from, 29, 31);

        // the morning alarm survives a day that was taken off, a break and a slot started early
        Routine off = base();
        off.addTemp(day(28), "Today", off).anchor = 390;
        AlarmState st = new AlarmState();
        DayOff.start(off, st, day(28), at(28, 20, 19));
        List<AlarmPlanner.Event> l = chain(off, st, from, at(30, 12, 0));
        ok("a day taken off: tomorrow's morning alarm still rings", wakeOn(l, day(29)) != null && wakeOn(l, day(29)).timeMs == startOf(off, day(29)));
        boolean noneToday = true;
        for (AlarmPlanner.Event e : l) if (e.day.equals(day(28).toString())) noneToday = false;
        ok("a day taken off: nothing rings for the rest of that day", noneToday);

        // marks that keep the alarm of a slot that was started early quiet follow the schedule they were made on
        Routine r0 = base();
        AlarmState sk = new AlarmState();
        sk.bindSkips("A");
        sk.skipAlarm("2026-10-28", 5);
        ok("a started slot's alarm is quiet", sk.skipsAlarm("2026-10-28", 5));
        ok("the same schedule keeps the mark", !sk.bindSkips("A") && sk.skipsAlarm("2026-10-28", 5));
        ok("a new schedule drops the marks (a slot's number points at another slot now)", sk.bindSkips("B") && !sk.skipsAlarm("2026-10-28", 5));
        AlarmState sk2 = AlarmState.parse(sk.serialize());
        ok("the schedule of the marks is saved", sk2.skipSig.equals("B"));
        AlarmState old = AlarmState.parse("lastFired=5\nskip=2026-10-28:5\n");
        ok("marks saved before the schedule was kept are not lost", old.skipsAlarm("2026-10-28", 5) && !old.bindSkips("") && old.skipsAlarm("2026-10-28", 5));
        old.bindSkips("Z");
        ok("the first schedule is taken over without dropping marks", old.skipsAlarm("2026-10-28", 5) && old.skipSig.equals("Z"));

        // a one-day routine made after a slot was started early does not silence another slot
        Routine mix = base();
        AlarmState ms = new AlarmState();
        ms.bindSkips(Plan.scheduleSignature(mix));
        ms.skipAlarm("2026-10-28", 14);
        mix.addTemp(day(28), "Today", mix).blocks.remove(2);
        boolean dropped = ms.bindSkips(Plan.scheduleSignature(mix));
        ok("making a routine for today drops the marks of the old schedule", dropped && !ms.skipsAlarm("2026-10-28", 14));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
    }

    static int sumWithoutSleep(Routine r) {
        int s = 0;
        for (Routine.Block b : r.blocks) if (b.kind != Routine.SLEEP) s += b.mins;
        return s;
    }
}
