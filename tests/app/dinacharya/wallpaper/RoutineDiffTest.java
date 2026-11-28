package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/** The pop-up for a change that must start tomorrow lists what the change is, in a few short lines. */
public class RoutineDiffTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static Routine base() { Routine r = Routine.sample(); r.mode = Routine.MODE_WAKE; r.anchor = 330; return r; }
    static Routine copy(Routine r) { return Routine.parse(r.serialize()); }
    static List<String> d(Routine b, Routine a) { return RoutineDiff.between(b, a, false); }

    public static void main(String[] args) {
        Routine r = base();
        ok("the same routine has no changes", d(r, copy(r)).isEmpty());

        Routine e = copy(r);
        String old = e.blocks.get(3).title;
        e.blocks.get(3).title = "Deep revision";
        List<String> c = d(r, e);
        ok("a new name is one line, old then new", c.size() == 1 && c.get(0).equals(old + " → Deep revision"));

        e = copy(r);
        Routine.Block lunch = null;
        for (Routine.Block b : e.blocks) if (b.kind == Routine.MEAL) { lunch = b; break; }
        int was = lunch.mins;
        lunch.mins = was - 15;
        c = d(r, e);
        ok("a shorter slot says from how long to how long", c.size() == 1 && c.get(0).equals(lunch.title + ": " + was + " min → " + (was - 15) + " min"));

        e = copy(r);
        String gone = e.blocks.get(5).title;
        e.blocks.remove(5);
        c = d(r, e);
        ok("a deleted slot is named", c.contains("Deleted: " + gone) && c.size() == 1);

        e = copy(r);
        e.blocks.add(5, new Routine.Block(Routine.TASK, "Mock test", 90));
        c = d(r, e);
        ok("an added slot is named", c.size() == 1 && c.get(0).equals("Added: Mock test"));

        e = copy(r);
        e.anchor = 360;
        c = d(r, e);
        ok("another start of the day: 12-hour clock", c.size() == 1 && c.get(0).equals("Start of day: 5:30 AM → 6:00 AM"));
        c = RoutineDiff.between(r, e, true);
        ok("another start of the day: 24-hour clock", c.get(0).equals("Start of day: 05:30 → 06:00"));
        e.anchor = 330 + 12 * 60;
        ok("an evening start reads PM", d(r, e).get(0).equals("Start of day: 5:30 AM → 5:30 PM"));
        e.anchor = 0;
        ok("midnight reads 12:00 AM", d(r, e).get(0).equals("Start of day: 5:30 AM → 12:00 AM"));
        e.anchor = 12 * 60;
        ok("noon reads 12:00 PM", d(r, e).get(0).equals("Start of day: 5:30 AM → 12:00 PM"));

        e = copy(r);
        e.mode = Routine.MODE_TASK;
        c = d(r, e);
        ok("only the way the day starts changed: said so", c.size() == 1 && c.get(0).equals("Start of day setting changed"));

        e = copy(r);
        Routine.Block t = e.blocks.get(3);
        t.kind = t.kind == Routine.REST ? Routine.TASK : Routine.REST;
        c = d(r, e);
        ok("a slot of another type says its settings changed", c.size() == 1 && c.get(0).equals(t.title + ": settings changed"));

        e = copy(r);
        e.blocks.get(3).setAlert(e.blocks.get(3).alert == Routine.ALERT_NONE ? Routine.ALERT_RING : Routine.ALERT_NONE);
        c = d(r, e);
        ok("a changed alert says the settings changed", c.size() == 1 && c.get(0).endsWith(": settings changed"));

        e = copy(r);
        Routine.Block x = e.blocks.remove(4);
        e.blocks.add(8, x);
        c = d(r, e);
        ok("a slot moved to another place: order changed, nothing deleted or added", c.contains("Order of slots changed") && c.size() == 1);

        e = copy(r);
        e.blocks.get(3).title = "Deep revision";
        e.blocks.get(3).mins += 10;
        c = d(r, e);
        ok("a renamed and resized slot gives both lines", c.size() == 2 && c.get(0).contains("→ Deep revision") && c.get(1).startsWith("Deep revision: "));

        e = copy(r);
        String d1 = e.blocks.get(6).title;
        e.blocks.remove(6);
        e.blocks.get(3).mins += 5;
        e.anchor = 345;
        e.blocks.add(new Routine.Block(Routine.TASK, "Mock test", 30));
        c = d(r, e);
        ok("several changes: start first, then slots in order, added last", c.size() == 4 && c.get(0).startsWith("Start of day: ")
                && c.get(1).contains(" min → ") && c.get(2).equals("Deleted: " + d1) && c.get(3).equals("Added: Mock test"));

        e = copy(r);
        e.blocks.get(3).title = e.blocks.get(4).title;
        c = d(r, e);
        ok("two slots with the same name: no crash and something is said", !c.isEmpty());

        e = copy(r);
        e.blocks.clear();
        c = d(r, e);
        ok("every slot deleted: one line each", c.size() == r.blocks.size() && c.get(0).startsWith("Deleted: "));
        c = d(e, r);
        ok("every slot added: one line each", c.size() == r.blocks.size() && c.get(0).startsWith("Added: "));

        e = copy(r);
        Routine.Block was5 = e.blocks.get(5);
        int otherKind = was5.kind == Routine.MEAL ? Routine.TASK : Routine.MEAL;
        e.blocks.set(5, new Routine.Block(otherKind, "Brand new", was5.mins + 7));
        c = d(r, e);
        ok("a different kind of slot at the same place is a delete and an add, not a rename", c.contains("Deleted: " + was5.title) && c.contains("Added: Brand new") && c.size() == 2);

        ok("more(): nothing when it fits", RoutineDiff.more(3).isEmpty() && RoutineDiff.more(0).isEmpty());
        ok("more(): the rest are counted", RoutineDiff.more(4).equals("+ 1 more") && RoutineDiff.more(9).equals("+ 6 more"));

        // for a day: the routine that day uses is compared
        LocalDate day = LocalDate.of(2026, 10, 20);
        e = copy(r);
        e.blocks.get(3).title = "Deep revision";
        c = RoutineDiff.changes(r, e, day, false);
        ok("changes() looks at the routine the day follows", c.size() == 1 && c.get(0).endsWith("→ Deep revision"));
        Routine r2 = copy(r);
        Routine w = new Routine();
        w.name = "Weekend";
        w.blocks.add(new Routine.Block(Routine.TASK, "Only", 60));
        r2.others.add(w);
        r2.dayUse[day.getDayOfWeek().getValue() - 1] = 1;
        e = copy(r2);
        e.blocks.get(3).title = "Deep revision";
        ok("a change to the main routine does not show on a day that follows another routine", RoutineDiff.changes(r2, e, day, false).isEmpty());
        e = copy(r2);
        e.others.get(0).blocks.get(0).mins = 45;
        c = RoutineDiff.changes(r2, e, day, false);
        ok("a change to the routine the day follows does show", c.size() == 1 && c.get(0).equals("Only: 60 min → 45 min"));

        // never empty when the lock would have refused
        e = copy(r);
        e.blocks.get(3).sub = e.blocks.get(3).sub.equals("Other") ? "Maths" : "Other";
        c = d(r, e);
        ok("a change the lines don't name is still said", !RoutineLock.sameDay(r, e) && !c.isEmpty());

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
