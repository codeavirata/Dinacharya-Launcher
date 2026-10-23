package app.dinacharya.wallpaper;

import java.util.List;

/**
 * The list of video passes: every unlock is a pass of its own with its own timer, two things that are needed together need two passes
 * (the case that used to end in "a video is asked for but never plays"), an item is never opened twice, and the record can be kept
 * and read back without ever opening more than was given.
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

        // ---------- a group opens only what is not open yet
        PassLedger g = new PassLedger();
        g.add(bit(DayPass.PAUSE_STRICT), 3 * DAY, t, "s");
        int rest = DayPass.stepMask(DayPass.TIER_GROUP, DayPass.PAUSE_STRICT);
        ok("steps: the third video opens the rest of the group, not the item itself", rest == (DayPass.areaMask(DayPass.AREA_PAUSES) & ~bit(DayPass.PAUSE_STRICT))
                && DayPass.count(rest) == 3 && DayPass.stepMask(DayPass.TIER_DAY, DayPass.PAUSE_STRICT) == bit(DayPass.PAUSE_STRICT));
        ok("steps: the second video stretches instead of opening", DayPass.stepExtends(DayPass.TIER_3DAYS) && !DayPass.stepExtends(DayPass.TIER_DAY)
                && !DayPass.stepExtends(DayPass.TIER_GROUP) && DayPass.stepMask(DayPass.TIER_3DAYS, DayPass.PAUSE_STRICT) == 0);
        ok("steps: items without a group have no third step", DayPass.stepMask(DayPass.TIER_GROUP, DayPass.GOALS) == 0 && DayPass.stepMask(DayPass.TIER_GROUP, DayPass.ROUTINES) == 0);
        int opened = g.add(rest, DAY, t + 5, "rest");
        ok("group: the rest of the pauses open next to strict", opened == rest && g.open(t + 6) == DayPass.areaMask(DayPass.AREA_PAUSES));
        ok("group: a pass that asks for the whole group only gets what is not open", new PassLedger().add(DayPass.areaMask(DayPass.AREA_LOOKS), DAY, t, "") == DayPass.areaMask(DayPass.AREA_LOOKS)
                && g.add(DayPass.areaMask(DayPass.AREA_PAUSES), DAY, t + 7, "") == 0);

        // ---------- three days
        PassLedger e = new PassLedger();
        e.add(bit(DayPass.LOOK_OCEAN), DAY, t, "o");
        ok("extend: a day-long pass on one item can be stretched", e.canExtend(DayPass.LOOK_OCEAN, t + H));
        ok("extend: it then runs three days from its start", e.extend(DayPass.LOOK_OCEAN, t + H) && e.covering(DayPass.LOOK_OCEAN, t + H).until == t + 3 * DAY
                && e.open(t + 2 * DAY) == bit(DayPass.LOOK_OCEAN) && e.open(t + 3 * DAY) == DayPass.FREE);
        ok("extend: and not again", !e.canExtend(DayPass.LOOK_OCEAN, t + H) && !e.extend(DayPass.LOOK_OCEAN, t + H));
        PassLedger f = new PassLedger();
        f.add(DayPass.areaMask(DayPass.AREA_LOOKS), DAY, t, "");
        ok("extend: a pass on a group is not stretched", !f.canExtend(DayPass.LOOK_OCEAN, t + 1) && !f.extend(DayPass.LOOK_OCEAN, t + 1));
        ok("extend: nothing running for the item, nothing to stretch", !e.canExtend(DayPass.GOALS, t) && !e.extend(DayPass.GOALS, t));
        PassLedger two = new PassLedger();
        two.add(bit(DayPass.GOALS), DAY, t, "");
        two.add(bit(DayPass.ROUTINES), DAY, t + H, "");
        two.extend(DayPass.ROUTINES, t + 2 * H);
        ok("extend: only the pass asked for is stretched, the other keeps its time", two.covering(DayPass.GOALS, t + 2 * H).until == t + DAY
                && two.covering(DayPass.ROUTINES, t + 2 * H).until == t + H + 3 * DAY);

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
        k.add(DayPass.areaMask(DayPass.AREA_LOOKS), 3 * DAY, t + 1, "");
        PassLedger back = PassLedger.parse(k.write());
        ok("keep: read back exactly, notes with tabs, lines and backslashes included", back.size() == 2 && back.entries().get(0).note.equals(tricky)
                && back.entries().get(0).mask == bit(DayPass.PAUSE_STRICT) && back.entries().get(1).until == t + 1 + 3 * DAY
                && back.write().equals(k.write()) && back.open(t + 2) == k.open(t + 2));
        ok("keep: nothing, rubbish and damaged text all read as an empty list", PassLedger.parse(null).size() == 0 && PassLedger.parse("").size() == 0
                && PassLedger.parse("hello").size() == 0 && PassLedger.parse("DINAPASSES1").size() == 0 && PassLedger.parse("DINAPASSES1\nx\ty\tz\t").size() == 0
                && PassLedger.parse("DINAPASSES1\n3\t1").size() == 0 && PassLedger.parse("DINAPASSES1\n" + (-1) + "\t1\t2\t").size() == 0);
        ok("keep: one damaged line does not spoil the others", PassLedger.parse("DINAPASSES1\njunk\n" + bit(DayPass.GOALS) + "\t" + t + "\t" + (t + DAY) + "\tn\n").open(t + 1) == bit(DayPass.GOALS));

        // ---------- an earlier version's single pass
        PassLedger old = PassLedger.fromOnePass(bit(DayPass.LOCK_STRICT), t, t + DAY, "old");
        ok("old record: one pass carries over with its note", old.size() == 1 && old.open(t + 1) == bit(DayPass.LOCK_STRICT) && old.entries().get(0).note.equals("old"));
        ok("old record: nothing there means an empty list", PassLedger.fromOnePass(0, 0, 0, "").size() == 0 && PassLedger.fromOnePass(1, t, t + DAY, "").size() == 0);

        // ---------- the words on the buttons
        ok("labels: one video, two videos, three videos", DayPass.tierLabel(DayPass.TIER_DAY, DayPass.PAUSE_STRICT).equals("1 video: strict pauses for a day")
                && DayPass.tierLabel(DayPass.TIER_3DAYS, DayPass.PAUSE_STRICT).equals("2 videos: strict pauses for 3 days")
                && DayPass.tierLabel(DayPass.TIER_GROUP, DayPass.PAUSE_STRICT).startsWith("3 videos: everything in Pauses")
                && DayPass.tierLabel(DayPass.TIER_GROUP, DayPass.GOALS).isEmpty());
        ok("list: items in words", DayPass.shortList(bit(DayPass.PAUSE_APPS) | bit(DayPass.PAUSE_STRICT)).equals("More paused apps and strict pauses")
                && DayPass.shortList(0).isEmpty());

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
