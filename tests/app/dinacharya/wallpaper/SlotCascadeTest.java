package app.dinacharya.wallpaper;

/** Changing one slot's length moves minutes to or from the slots right below it; the day stays 24 hours. */
public class SlotCascadeTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static Routine mk(int... m) {
        Routine r = new Routine();
        r.blocks.clear();
        for (int k = 0; k < m.length; k++)
            r.blocks.add(new Routine.Block(k == m.length - 1 ? Routine.SLEEP : Routine.TASK, "S" + k, m[k]));
        return r;
    }
    static int sum(Routine r) { int s = 0; for (Routine.Block b : r.blocks) s += b.mins; return s; }

    public static void main(String[] a) {
        Routine r = mk(300, 300, 300, 540);
        SlotOps.Resized x = SlotOps.resize(r, 0, 360);
        ok("longer takes from sleep", r.blocks.get(3).mins == 480 && r.blocks.get(1).mins == 300 && sum(r) == 1440 && !x.capped);
        SlotOps.resize(r, 0, 240);
        ok("shorter gives to sleep", r.blocks.get(3).mins == 600 && sum(r) == 1440);
        r = mk(300, 300, 300, 540);
        x = SlotOps.resize(r, 0, 900);
        ok("capped so sleep stays 4 hours", x.capped && r.blocks.get(3).mins == 240 && r.blocks.get(0).mins == 600 && sum(r) == 1440 && !x.sleepWarn);
        r = mk(300, 300, 300, 540);
        x = SlotOps.resize(r, 0, 5);
        ok("capped so sleep stays 12 hours", x.capped && r.blocks.get(3).mins == 720 && sum(r) == 1440);
        r = mk(300, 300, 300, 540);
        SlotOps.resize(r, 3, 600);
        ok("editing sleep trades with the slot above", r.blocks.get(2).mins == 240 && r.blocks.get(3).mins == 600 && sum(r) == 1440);
        SlotOps.resize(r, 3, 100);
        ok("sleep cannot go below 4 hours", r.blocks.get(3).mins == 240 && sum(r) == 1440);
        SlotOps.resize(r, 3, 1000);
        ok("sleep cannot go above 12 hours", r.blocks.get(3).mins == 720 && sum(r) == 1440);
        r = mk(300, 300, 300, 540);
        ok("permanent slots", SlotOps.isPermanent(r, 0) && SlotOps.isPermanent(r, 3) && !SlotOps.isPermanent(r, 1));
        ok("add comes from sleep", SlotOps.maxAddMinutes(r, 1) == 300 && SlotOps.maxAddMinutes(r, 3) == 0);
        Routine.Block nb = new Routine.Block(Routine.TASK, "New", 100);
        ok("insert takes from sleep", SlotOps.insertBelow(r, 1, nb) == nb && r.blocks.get(4).mins == 440 && r.blocks.get(2) == nb && sum(r) == 1440);
        ok("too long add refused", SlotOps.insertBelow(r, 1, new Routine.Block(Routine.TASK, "Big", 300)) == null);
        ok("cannot move permanent", !SlotOps.move(r, 0, 2) && !SlotOps.move(r, 1, 0) && !SlotOps.move(r, 1, r.blocks.size() - 1));
        r = mk(300, 700, 300, 140);
        ok("delete refused when sleep would pass 12 h", !SlotOps.canDeleteNow(r, 1) && SlotOps.canDeleteNow(r, 2));
        if (fails > 0) System.exit(1);
        System.out.println("ALL PASS");
    }
}
