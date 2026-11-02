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
        ok("longer takes from next", r.blocks.get(1).mins == 240 && r.blocks.get(2).mins == 300 && sum(r) == 1440 && !x.capped);
        x = SlotOps.resize(r, 0, 240);
        ok("shorter gives to next", r.blocks.get(1).mins == 360 && sum(r) == 1440);
        r = mk(300, 300, 300, 540);
        SlotOps.resize(r, 0, 300 + 300 - 5 + 100);
        ok("spills to following slots", r.blocks.get(1).mins == 5 && r.blocks.get(2).mins == 200 && sum(r) == 1440);
        r = mk(300, 300, 300, 540);
        SlotOps.resize(r, 3, 600);
        ok("last slot uses slots above", r.blocks.get(2).mins == 240 && r.blocks.get(3).mins == 600 && sum(r) == 1440);
        r = mk(300, 300, 300, 540);
        x = SlotOps.resize(r, 0, 5000);
        ok("capped when no room", x.capped && sum(r) == 1440 && x.applied == SlotOps.maxMinutes(mk(300, 300, 300, 540), 0) && r.blocks.get(3).mins == 5);
        ok("sleep warning when squeezed", x.sleepWarn);
        r = mk(300, 300, 300, 540);
        x = SlotOps.resize(r, 2, 300 + 540 - 200);
        ok("sleep under 4h warns", x.sleepWarn && r.blocks.get(3).mins == 200);
        r = mk(300, 300, 300, 540);
        x = SlotOps.resize(r, 2, 100);
        ok("sleep over 12h warns", x.sleepWarn && r.blocks.get(3).mins == 740);
        r = mk(300, 300, 300, 540);
        x = SlotOps.resize(r, 1, 320);
        ok("no warning inside 4-12h", !x.sleepWarn);
        if (fails > 0) System.exit(1);
        System.out.println("ALL PASS");
    }
}
