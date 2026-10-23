package app.dinacharya.wallpaper;

public class GestureTest {
    static int fails = 0;
    static void ok(String n, boolean v) { if (!v) { System.out.println("FAIL " + n); fails++; } else System.out.println("PASS " + n); }

    public static void main(String[] a) {
        float slop = 8, frac = 0.33f, w = 300;
        SwipeTracker t = new SwipeTracker(slop, frac);
        // left swipe past 33%
        t.down(200, 100);
        ok("small move is undecided", !t.move(197, 101));
        ok("clear horizontal move is a swipe", t.move(150, 104));
        ok("offset follows the finger", Math.abs(t.offset(500) - (-50)) < 0.01);
        ok("offset is clamped", Math.abs(t.offset(30) - (-30)) < 0.01);
        t.move(80, 106);
        ok("armed negative (delete)", t.armed(w) < 0 && t.armed(w) >= -1);
        ok("left past 33% deletes", t.release(w) == SwipeTracker.DELETE);
        // right swipe
        t.down(20, 100); t.move(60, 102); t.move(140, 99);
        ok("right past 33% adds", t.release(w) == SwipeTracker.ADD);
        // short swipe does nothing
        t.down(100, 100); t.move(130, 101); t.move(160, 101);
        ok("short swipe is ignored", t.release(w) == SwipeTracker.NONE);
        // vertical gesture is never a swipe, even if it later drifts sideways
        t.down(100, 100);
        ok("vertical start is not a swipe", !t.move(103, 130));
        t.move(250, 140);
        ok("stays vertical", !t.swiping() && t.release(w) == SwipeTracker.NONE);
        // diagonal 45 degrees is not a swipe (needs 1.5x)
        t.down(100, 100);
        ok("diagonal is not a swipe", !t.move(130, 130));
        // swipe state resets after release
        t.down(100, 100);
        ok("fresh gesture after release starts undecided", !t.swiping());
        // wiggle then swipe
        t.down(100, 100); t.move(102, 101); t.move(104, 100);
        ok("jitter below slop is undecided", !t.swiping());
        t.move(-10, 104);
        ok("then a swipe is recognised", t.swiping() && t.release(w) == SwipeTracker.DELETE);

        // ---- drag target
        float[] h = {60, 60, 60, 60, 60};
        float gap = 8;
        ok("not moved -> stays", DragReorder.targetIndex(h, gap, 2, 2 * 68) == 2);
        ok("dragged one row down past the midpoint of the next", DragReorder.targetIndex(h, gap, 1, 1 * 68 + 40) == 2);
        ok("dragged a little is still the same slot", DragReorder.targetIndex(h, gap, 1, 1 * 68 + 15) == 1);
        ok("dragged to the very bottom", DragReorder.targetIndex(h, gap, 0, 1000) == 4);
        ok("dragged to the very top", DragReorder.targetIndex(h, gap, 4, -500) == 0);
        ok("dragged two rows up", DragReorder.targetIndex(h, gap, 4, 4 * 68 - 2 * 68) == 2);
        float[] mixed = {124, 70, 92, 104};
        ok("different heights: row 0 dragged past two rows", DragReorder.targetIndex(mixed, gap, 0, 200) == 2);
        // shifts
        ok("rows between move up when dragging down", DragReorder.shift(2, 1, 3, 68) == -68 && DragReorder.shift(3, 1, 3, 68) == -68 && DragReorder.shift(4, 1, 3, 68) == 0 && DragReorder.shift(0, 1, 3, 68) == 0);
        ok("rows between move down when dragging up", DragReorder.shift(1, 3, 1, 68) == 68 && DragReorder.shift(2, 3, 1, 68) == 68 && DragReorder.shift(0, 3, 1, 68) == 0);
        ok("dragged row itself gets no shift", DragReorder.shift(3, 3, 1, 68) == 0);
        // consistency: applying the shifts gives the order that SlotOps.move gives
        Routine r = Routine.sample();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Routine.Block b : r.blocks) names.add(b.title);
        SlotOps.move(r, 4, 9);
        java.util.List<String> expect = new java.util.ArrayList<>(names);
        String x = expect.remove(4); expect.add(9, x);
        boolean same = true;
        for (int k = 0; k < expect.size(); k++) if (!expect.get(k).equals(r.blocks.get(k).title)) same = false;
        ok("move gives the expected order", same);
        // autoscroll
        ok("no scroll in the middle", DragReorder.autoScroll(500, 100, 900, 80, 30) == 0);
        ok("scroll up near the top", DragReorder.autoScroll(110, 100, 900, 80, 30) < 0);
        ok("scroll down near the bottom", DragReorder.autoScroll(890, 100, 900, 80, 30) > 0);
        ok("faster the nearer the edge", DragReorder.autoScroll(895, 100, 900, 80, 30) > DragReorder.autoScroll(830, 100, 900, 80, 30));
        ok("capped at max step", DragReorder.autoScroll(5000, 100, 900, 80, 30) <= 30);
        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
