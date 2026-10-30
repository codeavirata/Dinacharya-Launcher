package app.dinacharya.wallpaper;

import java.util.List;

/**
 * The list of video passes: three videos earn one pass that opens everything for a day, an item is never opened twice, a pass is never
 * longer than a day, passes from an earlier version still run beside it, and the record can be kept and read back without ever opening
 * more than was given.
 */
public class PassLedgerTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final long H = 3600000L, DAY = 24 * H;

    static int bit(int item) { return DayPass.bit(item); }

    public static void main(String[] args) {
        long t = 1_800_000_000_000L;

        PassLedger l = new PassLedger();
        ok("fresh: nothing open, nothing to end", l.open(t) == DayPass.FREE && l.nextEnd(t) == 0 && l.size() == 0 && l.ended(t).isEmpty());

        // ---------- the case that went wrong: strict pause on a third app needs two things
        int got = l.add(bit(DayPass.PAUSE_STRICT), DAY, t, "n1");
        ok("strict: one video opens strict pauses and nothing else", got == bit(DayPass.PAUSE_STRICT) && l.open(t + 1) == bit(DayPass.PAUSE_STRICT));
        ok("strict: pauses for more apps are still shut", !DayPass.has(l.open(t + 1), DayPass.PAUSE_APPS)
                && !DayPass.has(l.open(t + 1), DayPass.PAUSE_LIMIT) && !DayPass.has(l.open(t + 1), DayPass.PAUSE_PLAYFUL));
        ok("strict: and the person is allowed to watch for the second thing while the first runs", l.free(bit(DayPass.PAUSE_APPS), t + 1) != 0);
        got = l.add(bit(DayPass.PAUSE_APPS), DAY, t + H, "n2");
        ok("apps: its own video opens it, on top of strict", got == bit(DayPass.PAUSE_APPS)
                && l.open(t + H + 1) == (bit(DayPass.PAUSE_STRICT) | bit(DayPass.PAUSE_APPS)) && l.size() == 2);
        ok("apps: each has its own end (strict first)", l.nextEnd(t + H + 1) == t + DAY && l.covering(DayPass.PAUSE_APPS, t + H + 1).until == t + H + DAY);
        ok("apps: when strict ends, apps carries on alone", l.open(t + DAY + 1) == bit(DayPass.PAUSE_APPS));
        ok("apps: and after both nothing is open", l.open(t + H + DAY + 1) == DayPass.FREE);
        ok("ended: both are over, the oldest first, with their notes", l.ended(t + H + DAY + 1).size() == 2
                && l.ended(t + H + DAY + 1).get(0).note.equals("n1") && l.ended(t + H + DAY + 1).get(1).note.equals("n2"));
        ok("ended: only the first when the second still runs", l.ended(t + DAY + 1).size() == 1 && l.ended(t + DAY + 1).get(0).note.equals("n1"));

        // ---------- an item is never opened twice
        PassLedger d = new PassLedger();
        d.add(bit(DayPass.GOALS), DAY, t, "g");
        ok("twice: the same item is refused while it is open", d.add(bit(DayPass.GOALS), DAY, t + 1, "g2") == 0 && d.size() == 1);
        ok("twice: and may be asked again after it ended", d.add(bit(DayPass.GOALS), DAY, t + DAY + 1, "g3") == bit(DayPass.GOALS) && d.size() == 2);

        // ---------- the three-video pass opens everything, for one day
        PassLedger g = new PassLedger();
        int allOpened = g.add(DayPass.ALL, DayPass.LENGTH_MS, t, "all");
        ok("all: the pass opens every item", allOpened == DayPass.ALL && g.open(t + 1) == DayPass.ALL && DayPass.count(g.open(t + 1)) == 15);
        ok("all: including the deeper analysis", DayPass.has(g.open(t + 1), DayPass.ANALYSIS) && DayPass.has(g.open(t + 1), DayPass.GOALS));
        ok("all: for a day, then everything shuts at once", g.open(t + DAY - 1) == DayPass.ALL && g.open(t + DAY) == DayPass.FREE
                && g.nextEnd(t + 1) == t + DAY);
        ok("all: while it runs, nothing is left to open", g.free(DayPass.ALL, t + 1) == 0 && g.add(DayPass.ALL, DAY, t + 5, "") == 0 && g.size() == 1);
        ok("all: after it ended another may be earned", g.add(DayPass.ALL, DAY, t + DAY, "again") == DayPass.ALL && g.size() == 2);
        ok("all: the ended one is still to be put back", g.ended(t + DAY).size() == 1 && g.ended(t + DAY).get(0).note.equals("all"));
        PassLedger part = new PassLedger();
        part.add(bit(DayPass.PAUSE_STRICT), DAY, t, "s");
        ok("all: a pass from an earlier version still running leaves only the rest to open", part.free(DayPass.ALL, t + 1) == (DayPass.ALL & ~bit(DayPass.PAUSE_STRICT))
                && part.add(DayPass.ALL, DAY, t + 5, "rest") == (DayPass.ALL & ~bit(DayPass.PAUSE_STRICT)) && part.open(t + 6) == DayPass.ALL);

        // ---------- never longer than a day
        ok("day: the longest a pass can last is one day", DayPass.MAX_MS == DayPass.LENGTH_MS && DayPass.LENGTH_MS == DAY);
        ok("day: three days are refused", new PassLedger().add(DayPass.ALL, 3 * DAY, t, "") == 0 && new PassLedger().add(DayPass.ALL, DAY + 1, t, "") == 0);
        ok("day: a record of an earlier version that ran three days opens nothing", PassLedger.parse("DINAPASSES1\n" + bit(DayPass.GOALS) + "\t" + (t - 1000) + "\t" + (t + 3 * DAY) + "\t")
                .open(t) == 0);

        // ---------- bad input opens nothing
        PassLedger b = new PassLedger();
        ok("refused: no items, items that do not exist, no time, more than three days",
                b.add(0, DAY, t, "") == 0 && b.add(1, DAY, t, "") == 0 && b.add(1 << 20, DAY, t, "") == 0
                        && b.add(bit(DayPass.GOALS), 0, t, "") == 0 && b.add(bit(DayPass.GOALS), -5, t, "") == 0
                        && b.add(bit(DayPass.GOALS), DayPass.MAX_MS + 1, t, "") == 0 && b.size() == 0 && b.open(t) == 0);
        ok("refused: a pass cannot be given before it starts or after it ended (by hand)", PassLedger.parse("DINAPASSES1\n" + bit(DayPass.GOALS) + "\t" + (t + DAY) + "\t" + (t + 2 * DAY) + "\t")
                .open(t) == 0 && PassLedger.parse("DINAPASSES1\n" + bit(DayPass.GOALS) + "\t" + (t - 2 * DAY) + "\t" + (t - DAY) + "\t").open(t) == 0);
        ok("refused: a record changed by hand to run a month opens nothing", PassLedger.parse("DINAPASSES1\n" + bit(DayPass.GOALS) + "\t" + (t - 1000) + "\t" + (t + 30 * DAY) + "\t")
                .open(t) == 0);

        PassLedger full = new PassLedger();
        for (int i = 0; i < 100; i++) full.add(bit(DayPass.GOALS), DAY, t + i * 2 * DAY, "");
        ok("limit: the list never holds more than " + PassLedger.MAX_ENTRIES, full.size() == PassLedger.MAX_ENTRIES);

        // ---------- notes and ending
        PassLedger n = new PassLedger();
        n.add(bit(DayPass.GOALS), DAY, t, "");
        n.add(bit(DayPass.ROUTINES), DAY, t + H, "note");
        ok("notes: the next pass with something to put back is the one with a note", n.nextNoteEnd() == t + H + DAY);
        n.clearNotes();
        ok("notes: clearing leaves nothing to put back but keeps the passes", n.nextNoteEnd() == 0 && n.open(t + 2 * H) == (bit(DayPass.GOALS) | bit(DayPass.ROUTINES)));
        List<PassLedger.Entry> over = n.ended(t + 2 * DAY);
        n.remove(over);
        ok("remove: ended passes leave the list", n.size() == 0);

        // ---------- keeping and reading back
        PassLedger k = new PassLedger();
        String tricky = "DINAPASS3\nM\t12\nR\tcom.a.b\t1\t0\t0\t0\nG\tName with \\ backslash\t1\t2\tx\ty\n\n\ttabs\tand\r\nlines";
        k.add(bit(DayPass.PAUSE_STRICT), DAY, t, tricky);
        k.add(DayPass.areaMask(DayPass.AREA_LOOKS), DAY, t + 1, "");
        PassLedger back = PassLedger.parse(k.write());
        ok("keep: read back exactly, notes with tabs, lines and backslashes included", back.size() == 2 && back.entries().get(0).note.equals(tricky)
                && back.entries().get(0).mask == bit(DayPass.PAUSE_STRICT) && back.entries().get(1).until == t + 1 + DAY
                && back.write().equals(k.write()) && back.open(t + 2) == k.open(t + 2));
        ok("keep: nothing, rubbish and damaged text all read as an empty list", PassLedger.parse(null).size() == 0 && PassLedger.parse("").size() == 0
                && PassLedger.parse("hello").size() == 0 && PassLedger.parse("DINAPASSES1").size() == 0 && PassLedger.parse("DINAPASSES1\nx\ty\tz\t").size() == 0
                && PassLedger.parse("DINAPASSES1\n3\t1").size() == 0 && PassLedger.parse("DINAPASSES1\n" + (-1) + "\t1\t2\t").size() == 0);
        ok("keep: one damaged line does not spoil the others", PassLedger.parse("DINAPASSES1\njunk\n" + bit(DayPass.GOALS) + "\t" + t + "\t" + (t + DAY) + "\tn\n").open(t + 1) == bit(DayPass.GOALS));

        // ---------- an earlier version's single pass
        PassLedger old = PassLedger.fromOnePass(bit(DayPass.LOCK_STRICT), t, t + DAY, "old");
        ok("old record: one pass carries over with its note", old.size() == 1 && old.open(t + 1) == bit(DayPass.LOCK_STRICT) && old.entries().get(0).note.equals("old"));
        ok("old record: nothing there means an empty list", PassLedger.fromOnePass(0, 0, 0, "").size() == 0 && PassLedger.fromOnePass(1, t, t + DAY, "").size() == 0);

        // ---------- the words, and how far the three videos have got
        ok("list: items in words", DayPass.shortList(bit(DayPass.PAUSE_APPS) | bit(DayPass.PAUSE_STRICT)).equals("More paused apps and strict pauses")
                && DayPass.shortList(0).isEmpty());
        ok("words: everything is everything in Pro", DayPass.maskName(DayPass.ALL).equals("everything in Pro"));
        ok("words: one app is not 'apps'", DayPass.name(DayPass.PAUSE_APPS).equals("pauses for more than 1 app")
                && DayPass.name(DayPass.LOCK_APPS).equals("more than 1 app in a lock"));
        ok("videos: three earn the pass", VideoProgress.NEED == 3 && DayPass.VIDEOS == 3);
        ok("videos: none counted at first, then one more each time", VideoProgress.counted("2026-10-26", 0, "2026-10-26") == 0
                && VideoProgress.after(0) == 1 && VideoProgress.after(1) == 2 && !VideoProgress.earned(2) && VideoProgress.earned(VideoProgress.after(2)));
        ok("videos: they count for the day they were watched", VideoProgress.counted("2026-10-26", 2, "2026-10-26") == 2
                && VideoProgress.counted("2026-10-25", 2, "2026-10-26") == 0 && VideoProgress.counted(null, 2, "2026-10-26") == 0
                && VideoProgress.counted("", 2, "2026-10-26") == 0);
        ok("videos: a record by hand cannot hold three or more or less than none", VideoProgress.counted("d", 9, "d") == 2 && VideoProgress.counted("d", -4, "d") == 0);
        ok("videos: what is left", VideoProgress.remaining(0) == 3 && VideoProgress.remaining(1) == 2 && VideoProgress.remaining(2) == 1
                && VideoProgress.remaining(7) == 1 && VideoProgress.remaining(-1) == 3);
        ok("videos: the button says how many are left", VideoProgress.buttonText(0).startsWith("Watch 3 videos")
                && VideoProgress.buttonText(1).startsWith("Watch 2 more videos") && VideoProgress.buttonText(2).startsWith("Watch 1 more video:"));
        ok("videos: the progress line", VideoProgress.progressText(0).isEmpty() && VideoProgress.progressText(1).equals("1 of 3 videos watched today")
                && VideoProgress.progressText(2).equals("2 of 3 videos watched today"));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
