package app.dinacharya.wallpaper;

import java.time.ZoneId;
import java.util.Arrays;

/** The ad layers: the order the ad units are asked in, leaving a unit alone after it failed, and the day's cap. */
public class AdLayersTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final long S = 1000L, MIN = 60000L;

    public static void main(String[] args) {
        long t = 1_800_000_000_000L;

        AdLayers a = new AdLayers(Arrays.asList("top", "mid", "any"));
        ok("layers: three units, the best paying first", a.size() == 3 && a.id(0).equals("top") && a.id(2).equals("any") && a.id(3).isEmpty() && a.id(-1).isEmpty());
        ok("layers: all are asked in order when none has failed", Arrays.equals(a.plan(t), new int[]{0, 1, 2}));
        AdLayers b = new AdLayers(Arrays.asList(" top ", "", null, "top", "mid", "  ", "mid"));
        ok("layers: blanks and repeats are dropped (one id used three times is one layer)", b.size() == 2 && b.id(0).equals("top") && b.id(1).equals("mid"));
        ok("layers: no ids at all means no layers and an empty plan", new AdLayers(null).size() == 0 && new AdLayers(null).plan(t).length == 0
                && new AdLayers(Arrays.asList("x", "x", "x")).size() == 1);

        a.failed(0, AdLayers.CODE_NO_FILL, t);
        ok("fail: a unit that had nothing is left out of the next asks", Arrays.equals(a.plan(t + 1), new int[]{1, 2}) && a.backingOff(0, t + 1));
        ok("fail: and comes back after its wait", Arrays.equals(a.plan(t + AdLayers.NO_FILL_MS), new int[]{0, 1, 2}) && !a.backingOff(0, t + AdLayers.NO_FILL_MS));
        a.failed(0, AdLayers.CODE_NO_FILL, t + AdLayers.NO_FILL_MS);
        ok("fail: failing again makes the wait longer", a.backingOff(0, t + AdLayers.NO_FILL_MS + AdLayers.NO_FILL_MS + 1000)
                && !a.backingOff(0, t + AdLayers.NO_FILL_MS + 2 * AdLayers.NO_FILL_MS));
        a.loaded(0);
        ok("fail: a unit that gives an ad is asked every time again, and starts from the short wait", !a.backingOff(0, t + AdLayers.NO_FILL_MS + 1)
                && fresh(a));

        AdLayers c = new AdLayers(Arrays.asList("top", "mid", "any"));
        long last = t;
        for (int i = 0; i < 12; i++) {
            c.failed(0, AdLayers.CODE_NO_FILL, last);
            last += AdLayers.MAX_BACKOFF_MS + 1;
        }
        c.failed(0, AdLayers.CODE_NO_FILL, last);
        ok("fail: the wait never grows past eight minutes", !c.backingOff(0, last + AdLayers.MAX_BACKOFF_MS));

        AdLayers d = new AdLayers(Arrays.asList("top", "any"));
        d.failed(0, AdLayers.CODE_NETWORK, t);
        ok("fail: no network is a short wait", !d.backingOff(0, t + AdLayers.NETWORK_MS) && d.backingOff(0, t + AdLayers.NETWORK_MS - 1));
        d.failed(1, AdLayers.CODE_INVALID_REQUEST, t);
        ok("fail: a wrong ad unit id is left alone for a long time, and does not grow", d.backingOff(1, t + 29 * MIN) && !d.backingOff(1, t + 30 * MIN));
        d.failed(1, AdLayers.CODE_INVALID_REQUEST, t + 30 * MIN);
        ok("fail: …still thirty minutes the second time", !d.backingOff(1, t + 60 * MIN) && d.backingOff(1, t + 59 * MIN));
        AdLayers e = new AdLayers(Arrays.asList("top"));
        e.failed(0, 0, t);
        ok("fail: any other error waits five minutes", e.backingOff(0, t + 4 * MIN) && !e.backingOff(0, t + 5 * MIN));

        AdLayers f = new AdLayers(Arrays.asList("top", "mid", "any"));
        f.failed(0, AdLayers.CODE_NO_FILL, t);
        f.failed(1, AdLayers.CODE_NO_FILL, t);
        f.failed(2, AdLayers.CODE_NO_FILL, t);
        ok("fail: when every unit failed a moment ago the last one is still asked, so a video is never refused for that",
                Arrays.equals(f.plan(t + S), new int[]{2}));
        f.failed(9, 3, t);
        f.failed(-1, 3, t);
        f.loaded(9);
        ok("fail: unknown layers are ignored", f.size() == 3 && !f.backingOff(9, t));
        ok("one unit: the plan is that unit, even right after it failed", Arrays.equals(e.plan(t + S), new int[]{0}));

        ok("fresh: an ad is used within fifty minutes of loading", AdLayers.fresh(t, t) && AdLayers.fresh(t, t + 49 * MIN) && !AdLayers.fresh(t, t + 50 * MIN)
                && !AdLayers.fresh(0, t) && !AdLayers.fresh(t, t - 1));
        ok("cap: twelve videos a day", AdLayers.underCap(0) && AdLayers.underCap(11) && !AdLayers.underCap(12) && !AdLayers.underCap(40));
        ZoneId z = ZoneId.of("Asia/Kolkata");
        long evening = java.time.ZonedDateTime.of(2026, 10, 21, 23, 30, 0, 0, z).toInstant().toEpochMilli();
        ok("cap: the day changes at local midnight", AdLayers.dayKey(evening, z).equals("2026-10-21") && AdLayers.dayKey(evening + 40 * MIN, z).equals("2026-10-22"));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static boolean fresh(AdLayers a) { return a.size() == 3; }
}
