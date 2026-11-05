package app.dinacharya.wallpaper;

import java.util.*;

/** The slots that rang and were not answered, for "Start it now" on the next alarm. */
public class MissedSlotsTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final long MIN = 60000L;
    static final long T0 = 1_800_000_000_000L;

    public static void main(String[] args) {
        String day = "2026-11-05";
        List<Ev> ev = new ArrayList<>();
        ev.add(Ev.rang(T0, day, 3, "Study"));
        long now = T0 + 70 * MIN;
        ok("a slot that rang and was not answered is open", MissedSlots.pending(ev, day, 4, now, MissedSlots.WITHIN_MS).size() == 1);
        ok("it is the right slot", MissedSlots.pending(ev, day, 4, now, MissedSlots.WITHIN_MS).get(0).src == 3);
        ok("the slot that is asking now is left out", MissedSlots.pending(ev, day, 3, now, MissedSlots.WITHIN_MS).isEmpty());

        List<Ev> answered = new ArrayList<>(ev);
        answered.add(Ev.ack(T0 + 20 * MIN, day, 3, "Study", "C", ""));
        ok("an answer closes it", MissedSlots.pending(answered, day, 4, now, MissedSlots.WITHIN_MS).isEmpty());

        List<Ev> missedNote = new ArrayList<>(ev);
        missedNote.add(Ev.missed(T0 + 5 * MIN, day, 3, "Study"));
        ok("the missed note alone is not an answer", MissedSlots.pending(missedNote, day, 4, now, MissedSlots.WITHIN_MS).size() == 1);

        ok("too long ago is not offered", MissedSlots.pending(ev, day, 4, T0 + 4 * 3600000L, MissedSlots.WITHIN_MS).isEmpty());
        ok("another day is not offered", MissedSlots.pending(ev, "2026-11-04", 4, now, MissedSlots.WITHIN_MS).isEmpty());
        ok("a ring in the future is ignored", MissedSlots.pending(ev, day, 4, T0 - MIN, MissedSlots.WITHIN_MS).isEmpty());

        List<Ev> two = new ArrayList<>(ev);
        two.add(Ev.rang(T0 + 30 * MIN, day, 4, "Lunch"));
        List<MissedSlots.Key> k = MissedSlots.pending(two, day, 5, now, MissedSlots.WITHIN_MS);
        ok("two open slots, the later ring first", k.size() == 2 && k.get(0).src == 4 && k.get(1).src == 3);

        List<Ev> twice = new ArrayList<>(ev);
        twice.add(Ev.rang(T0 + 10 * MIN, day, 3, "Study"));
        ok("a slot that rang twice is listed once", MissedSlots.pending(twice, day, 4, now, MissedSlots.WITHIN_MS).size() == 1);

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
    }
}
