package app.dinacharya.wallpaper;

/** When Home offers the way to rate the app on Google Play (RatePrompt). Run from the tests folder. */
public class RateTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    public static void main(String[] args) {
        long d0 = 20400;   // an epoch day

        RatePrompt r = new RatePrompt();
        ok("a new install is not asked", !r.due(d0));
        r.used(d0);
        ok("first day: not asked", !r.due(d0));
        r.used(d0); r.used(d0);
        ok("opening the app three times in one day counts one day", r.daysUsed == 1);
        for (int i = 1; i <= 3; i++) r.used(d0 + i);
        ok("four days of use in the first week: not asked", !r.due(d0 + 3));
        r.used(d0 + 4);
        ok("five days of use, but only four days since the first: not asked", !r.due(d0 + 4));
        ok("five days of use, a week after the first: asked", r.due(d0 + 7));

        RatePrompt few = new RatePrompt();
        few.used(d0); few.used(d0 + 30);
        ok("a month old but used on only two days: not asked", !few.due(d0 + 30));

        RatePrompt s = RatePrompt.parse(r.serialize());
        ok("saved and read back: still asked", s.due(d0 + 7));
        s.later(d0 + 7);
        ok("Not now: gone the same day", !s.due(d0 + 7));
        ok("Not now: still gone thirteen days later", !s.due(d0 + 20));
        ok("Not now: back fourteen days later", s.due(d0 + 21));
        s.later(d0 + 21);
        ok("Not now a second time: never again", !s.due(d0 + 400) && s.done());

        RatePrompt g = RatePrompt.parse(r.serialize());
        g.rated();
        ok("rated: never again", !g.due(d0 + 8) && !g.due(d0 + 1000));
        ok("rated, saved and read back: never again", !RatePrompt.parse(g.serialize()).due(d0 + 1000));

        ok("nothing saved: starts from nothing", RatePrompt.parse(null).daysUsed == 0 && !RatePrompt.parse(null).done());
        ok("damaged text: starts from nothing", RatePrompt.parse("garbage\nfirst=x").daysUsed == 0);
        RatePrompt back = RatePrompt.parse(r.serialize());
        back.used(d0 - 3);   // the clock was set back
        ok("a day before the last one does not count again", back.daysUsed == r.daysUsed);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
