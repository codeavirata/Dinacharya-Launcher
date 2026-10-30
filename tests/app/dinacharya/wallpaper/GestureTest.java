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
        // ---- swiping between the tabs (slop 8, far 72, flick 40; 5 tabs)
        TabSwipe ts = new TabSwipe(8, 72, 40);
        ts.down(300, 400, 0, true);
        ok("tab swipe: a few pixels is not taken", !ts.takes(305, 402));
        ok("tab swipe: just past the slop is still not taken", !ts.takes(312, 400));
        ok("tab swipe: clearly sideways is taken", ts.takes(330, 405));
        ok("tab swipe: left swipe goes to the next tab", ts.release(150, 410, 220, 1, 5) == 2);
        ts.down(100, 400, 1000, true);
        ok("tab swipe: right swipe goes to the previous tab", ts.release(260, 395, 1200, 3, 5) == 2);
        ts.down(100, 400, 2000, true);
        ok("tab swipe: right swipe on the first tab stays", ts.release(300, 400, 2200, 0, 5) == TabSwipe.STAY);
        ts.down(300, 400, 3000, true);
        ok("tab swipe: left swipe on the last tab stays", ts.release(100, 400, 3200, 4, 5) == TabSwipe.STAY);
        ts.down(300, 400, 4000, true);
        ok("tab swipe: too short stays", ts.release(272, 400, 4150, 1, 5) == TabSwipe.STAY);
        ts.down(300, 400, 5000, true);
        ok("tab swipe: a short quick flick counts", ts.release(255, 402, 5120, 1, 5) == 2);
        ts.down(300, 400, 6000, true);
        ok("tab swipe: a short slow drag does not", ts.release(255, 402, 6600, 1, 5) == TabSwipe.STAY);
        ts.down(300, 400, 7000, true);
        ok("tab swipe: a long slow drag counts", ts.release(150, 400, 7800, 1, 5) == 2);
        ts.down(300, 400, 8000, true);
        ok("tab swipe: too slow even when long stays", ts.release(100, 400, 9200, 1, 5) == TabSwipe.STAY);
        ts.down(300, 300, 10000, true);
        ok("tab swipe: a diagonal is not taken", !ts.takes(360, 360));
        ok("tab swipe: a diagonal does not change the tab", ts.release(150, 460, 10200, 1, 5) == TabSwipe.STAY);
        ts.down(300, 300, 11000, true);
        ok("tab swipe: a vertical scroll is not taken", !ts.takes(305, 380));
        ok("tab swipe: a vertical scroll that drifts stays", ts.release(340, 700, 11300, 1, 5) == TabSwipe.STAY);
        ts.down(300, 300, 12000, true);
        ok("tab swipe: a swipe with some vertical drift is taken", ts.takes(340, 315));
        ok("tab swipe: ... and counts", ts.release(120, 330, 12250, 2, 5) == 3);
        ts.down(300, 400, 13000, false);
        ok("tab swipe: a gesture on a slider or swipe row is never taken", !ts.takes(100, 400) && !ts.live());
        ok("tab swipe: ... and never counts", ts.release(100, 400, 13200, 2, 5) == TabSwipe.STAY);
        ts.down(300, 400, 14000, true);
        ts.cancel();
        ok("tab swipe: a second finger cancels it", !ts.takes(100, 400) && ts.release(100, 400, 14200, 2, 5) == TabSwipe.STAY);
        ts.down(300, 400, 15000, true);
        ts.release(100, 400, 15200, 2, 5);
        ok("tab swipe: a release without a new touch does nothing", ts.release(100, 400, 15300, 2, 5) == TabSwipe.STAY);
        ts.down(300, 400, 16000, true);
        ok("tab swipe: a tab that does not exist stays", ts.release(100, 400, 16200, -1, 5) == TabSwipe.STAY);
        ts.down(300, 400, 17000, true);
        ok("tab swipe: swipes through every tab in order", ts.release(100, 400, 17200, 0, 5) == 1);
        ts.down(300, 400, 18000, true);
        ok("tab swipe: ... to the last one", ts.release(100, 400, 18200, 3, 5) == 4);
        // a page that follows the finger: a third of the width counts at any speed
        ts.down(300, 400, 19000, true);
        ok("tab swipe: a third of the page, slowly, counts", ts.release(180, 400, 21000, 1, 5, 360) == 2);
        ts.down(300, 400, 22000, true);
        ok("tab swipe: just under a third, slowly, stays", ts.release(190, 400, 24000, 1, 5, 360) == TabSwipe.STAY);
        ts.down(100, 400, 25000, true);
        ok("tab swipe: a third of the page to the right goes back", ts.release(220, 400, 27000, 2, 5, 360) == 1);
        ts.down(300, 400, 28000, true);
        ok("tab swipe: a third of the page on the last tab stays", ts.release(180, 400, 30000, 4, 5, 360) == TabSwipe.STAY);
        ts.down(300, 400, 31000, true);
        ok("tab swipe: a mostly vertical drag never counts, however far", ts.release(180, 700, 33000, 1, 5, 360) == TabSwipe.STAY);
        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
