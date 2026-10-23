package app.dinacharya.wallpaper;

import java.util.*;

public class SlotOpsTest {
    static int fails = 0;
    static void ok(String n, boolean v) { if (!v) { System.out.println("FAIL " + n); fails++; } else System.out.println("PASS " + n); }
    static int idx(Routine r, String t) { for (int i = 0; i < r.blocks.size(); i++) if (r.blocks.get(i).title.equals(t)) return i; return -1; }
    static int mins(Routine r, String t) { int i = idx(r, t); return i < 0 ? -1 : r.blocks.get(i).mins; }
    static int total(Routine r) { return r.totalMinutes(); }

    public static void main(String[] a) {
        // ---------------- delete
        Routine r = Routine.sample();
        ok("sample is 24h", total(r) == 1440);
        int i = idx(r, "Tea break");
        int above = r.blocks.get(i - 1).mins, tea = r.blocks.get(i).mins;
        ok("delete -> above works", SlotOps.delete(r, i, SlotOps.TO_ABOVE));
        ok("still 24h after delete above", total(r) == 1440);
        ok("tea break gone", idx(r, "Tea break") < 0 && r.blocks.size() == 18);
        ok("above got the minutes", mins(r, "Reading & notes") == above + tea);

        r = Routine.sample();
        i = idx(r, "Tea break");
        int below = r.blocks.get(i + 1).mins;
        SlotOps.delete(r, i, SlotOps.TO_BELOW);
        ok("below got the minutes", mins(r, "Practice problems") == below + 10 && total(r) == 1440);

        r = Routine.sample();
        int nxt = r.blocks.get(1).mins;
        SlotOps.delete(r, 0, SlotOps.TO_ABOVE);   // nothing above the first slot: goes to the one below
        ok("first slot, 'above' falls back to below", r.blocks.get(0).title.equals("Yoga & stretching") && r.blocks.get(0).mins == nxt + 30 && total(r) == 1440);

        r = Routine.sample();
        int lastBefore = r.blocks.get(r.blocks.size() - 2).mins;
        SlotOps.delete(r, r.blocks.size() - 1, SlotOps.TO_BELOW);   // sleep deleted, nothing below: goes above
        ok("last slot, 'below' falls back to above", r.blocks.get(r.blocks.size() - 1).title.equals("Wind down") && r.blocks.get(r.blocks.size() - 1).mins == lastBefore + 480 && total(r) == 1440);

        // spread over all
        r = Routine.sample();
        Map<String, Integer> before = new LinkedHashMap<>();
        for (Routine.Block b : r.blocks) before.put(b.title, b.mins);
        i = idx(r, "Project work"); // 120
        SlotOps.delete(r, i, SlotOps.TO_ALL);
        boolean nonDecreasing = true, sleepSame = mins(r, "Sleep") == 480;
        for (Routine.Block b : r.blocks) if (b.mins < before.get(b.title)) nonDecreasing = false;
        ok("spread: 24h kept", total(r) == 1440);
        ok("spread: nobody lost time, sleep untouched", nonDecreasing && sleepSame);
        ok("spread: longer slots got at least as much as shorter ones", (mins(r, "Morning focus block") - 120) >= (mins(r, "Tea break") - 10));

        // can't delete the last remaining slot
        Routine one = new Routine(); one.blocks.add(new Routine.Block(Routine.TASK, "Only", 1440));
        ok("cannot delete the only slot", !SlotOps.delete(one, 0, SlotOps.TO_ALL) && one.blocks.size() == 1);

        // wake-up stays when the only Task is deleted in first-task mode
        r = Routine.sample();
        r.mode = Routine.MODE_TASK; r.anchor = r.compute().firstTaskStart;
        int startBefore = r.compute().start;
        for (int k = r.blocks.size() - 1; k >= 0; k--) if (r.blocks.get(k).kind == Routine.TASK && SlotOps.canDelete(r, k)) SlotOps.delete(r, k, SlotOps.TO_ABOVE);
        boolean anyTask = false; for (Routine.Block b : r.blocks) if (b.kind == Routine.TASK) anyTask = true;
        ok("no tasks left", !anyTask);
        ok("day start kept after the last Task is deleted", r.compute().start == startBefore && r.mode == Routine.MODE_WAKE);
        ok("24h kept after deleting all tasks", total(r) == 1440);

        // ---------------- add
        r = Routine.sample();
        i = idx(r, "Lunch"); int lunch = mins(r, "Lunch");
        Routine.Block nb = new Routine.Block(Routine.TASK, "Mock test", 30);
        ok("max new from this slot", SlotOps.maxNewMinutes(r, i, SlotOps.FROM_THIS) == lunch - 5);
        ok("insert from this slot", SlotOps.insertAfter(r, i, nb, SlotOps.FROM_THIS) == nb);
        ok("placed right after", r.blocks.get(i + 1) == nb && r.blocks.get(i).title.equals("Lunch"));
        ok("lender gave the minutes", mins(r, "Lunch") == lunch - 30 && total(r) == 1440);

        r = Routine.sample();
        i = idx(r, "Lunch"); int pn = mins(r, "Power nap");
        nb = new Routine.Block(Routine.PERSONAL, "Call home", 12);
        SlotOps.insertAfter(r, i, nb, SlotOps.FROM_NEXT);
        ok("insert from next slot", mins(r, "Power nap") == pn - 12 && total(r) == 1440);

        r = Routine.sample();
        i = r.blocks.size() - 1; int sl = mins(r, "Sleep");
        nb = new Routine.Block(Routine.REST, "Extra", 20);
        ok("from next with no next -> from this", SlotOps.lender(r, i, SlotOps.FROM_NEXT) == i && SlotOps.insertAfter(r, i, nb, SlotOps.FROM_NEXT) == nb && mins(r, "Sleep") == sl - 20 && total(r) == 1440);

        // too long is refused and changes nothing
        r = Routine.sample();
        i = idx(r, "Tea break");
        String before2 = r.serialize();
        nb = new Routine.Block(Routine.TASK, "Too long", 10);   // tea break is 10, may lend at most 5
        ok("refused when the slot cannot lend that much", SlotOps.insertAfter(r, i, nb, SlotOps.FROM_THIS) == null && r.serialize().equals(before2));
        ok("max from tea break is 5", SlotOps.maxNewMinutes(r, i, SlotOps.FROM_THIS) == 5);

        // spread: nobody below 5, sleep untouched, 24h kept
        r = Routine.sample();
        i = idx(r, "Lunch");
        int cap = SlotOps.maxNewMinutes(r, i, SlotOps.FROM_ALL);
        ok("capacity is the sum of spare minutes (no sleep)", cap > 600);
        nb = new Routine.Block(Routine.TASK, "Big", 300);
        ok("spread insert", SlotOps.insertAfter(r, i, nb, SlotOps.FROM_ALL) == nb);
        boolean min5 = true; for (Routine.Block b : r.blocks) if (b.mins < 5) min5 = false;
        ok("spread insert: all slots >= 5, sleep same, 24h", min5 && mins(r, "Sleep") == 480 && total(r) == 1440);
        r = Routine.sample();
        nb = new Routine.Block(Routine.TASK, "Max", SlotOps.maxNewMinutes(r, 3, SlotOps.FROM_ALL));
        ok("spread insert of the exact maximum works and every other slot ends at 5 min", SlotOps.insertAfter(r, 3, nb, SlotOps.FROM_ALL) == nb && total(r) == 1440);
        boolean allFive = true; for (Routine.Block b : r.blocks) if (b != nb && b.kind != Routine.SLEEP && b.mins != 5) allFive = false;
        ok("  ...all other non-sleep slots are 5", allFive);
        ok("  ...and the room left is just what the new slot can spare", SlotOps.maxNewMinutes(r, 3, SlotOps.FROM_ALL) == nb.mins - 5);

        // first-task mode: day start stays when a new first Task is not involved
        r = Routine.sample(); r.mode = Routine.MODE_TASK; r.anchor = r.compute().firstTaskStart;
        int st = r.compute().start;
        SlotOps.insertAfter(r, idx(r, "Lunch"), new Routine.Block(Routine.REST, "x", 10), SlotOps.FROM_THIS);
        ok("first-task mode: start unchanged by a later insert", r.compute().start == st);

        // ---------------- move
        r = Routine.sample();
        String t0 = r.blocks.get(2).title;
        ok("move down", SlotOps.move(r, 2, 6) && r.blocks.get(6).title.equals(t0) && total(r) == 1440 && r.blocks.size() == 19);
        ok("move up", SlotOps.move(r, 6, 1) && r.blocks.get(1).title.equals(t0));
        ok("move to same place is a no-op", !SlotOps.move(r, 1, 1));
        ok("move out of range refused", !SlotOps.move(r, 1, 40) && !SlotOps.move(r, -1, 2));

        // ---------------- fuzz: the day never changes length
        Random rnd = new Random(7);
        boolean fuzzOk = true; String why = "";
        for (int round = 0; round < 400 && fuzzOk; round++) {
            r = Routine.sample();
            for (int step = 0; step < 40; step++) {
                int n = r.blocks.size();
                int op = rnd.nextInt(3);
                if (op == 0 && n > 1) SlotOps.delete(r, rnd.nextInt(n), rnd.nextInt(3));
                else if (op == 1) {
                    int after = rnd.nextInt(n), from = rnd.nextInt(3);
                    int mx = SlotOps.maxNewMinutes(r, after, from);
                    if (mx >= 1) SlotOps.insertAfter(r, after, new Routine.Block(rnd.nextInt(4), "n" + step, 1 + rnd.nextInt(mx)), from);
                } else if (n > 1) SlotOps.move(r, rnd.nextInt(n), rnd.nextInt(n));
                if (total(r) != 1440) { fuzzOk = false; why = "total " + total(r) + " round " + round + " step " + step; break; }
                for (Routine.Block b : r.blocks) if (b.mins < 1 || b.mins > 1440) { fuzzOk = false; why = "bad mins " + b.mins; }
                if (r.blocks.isEmpty()) { fuzzOk = false; why = "empty"; }
            }
        }
        ok("fuzz: 400 x 40 random delete/add/move keep exactly 1440 min (" + why + ")", fuzzOk);

        // ---------------- activity types
        r = Routine.sample();
        ok("defaults present", r.findSub(Routine.TASK, "Study") != null && r.findSub(Routine.PERSONAL, "exercise") != null);
        Routine.Sub gym = r.addSub(Routine.PERSONAL, "  Gym  ");
        ok("add a custom type (trimmed)", gym != null && gym.name.equals("Gym") && gym.kind == Routine.PERSONAL);
        ok("adding the same name again returns the same one", r.addSub(Routine.PERSONAL, "gym") == gym);
        ok("same name under another kind is a different type", r.addSub(Routine.TASK, "Gym") != gym);
        ok("empty name refused", r.addSub(Routine.TASK, "   ") == null);
        ok("name limited to 24", r.addSub(Routine.TASK, "abcdefghijklmnopqrstuvwxyz0123").name.length() <= 24);
        r.blocks.get(idx(r, "Evening walk")).sub = "Gym";
        String text = r.serialize();
        Routine back = Routine.parse(text);
        ok("round trip keeps types, block type and palette", back != null && back.findSub(Routine.PERSONAL, "Gym") != null
            && back.blocks.get(idx(back, "Evening walk")).sub.equals("Gym") && back.blocks.get(idx(back, "Morning focus block")).sub.equals("Study") && back.palette == r.palette);
        r.palette = Routine.PAL_OCEAN;
        ok("palette round trip", Routine.parse(r.serialize()).palette == Routine.PAL_OCEAN);
        r.removeSub(gym);
        ok("removing a type makes it Other on blocks but keeps the slot", r.blocks.get(idx(r, "Evening walk")).sub.equals(Routine.OTHER) && idx(r, "Evening walk") >= 0 && total(r) == 1440);
        // an explicit list with every removable type deleted stays that way (only the five "Other" types remain)
        Routine empty = Routine.sample();
        for (Routine.Sub u : new ArrayList<>(empty.subs)) empty.removeSub(u);
        Routine e2 = Routine.parse(empty.serialize());
        boolean onlyOther = e2 != null && e2.subs.size() == Routine.KIND_NAMES.length;
        if (e2 != null) for (Routine.Sub u : e2.subs) onlyOther &= Routine.isOther(u.name);
        ok("deleted types stay deleted after save and load; Other stays", onlyOther);
        // an old save (no subs / palette / sub column) loads with defaults
        String old = "DINACHARYA1\nmode=0\nanchor=330\ntheme=2\nB\t0\t60\tStudy hard\t1\nB\t4\t480\tSleep\t1\n";
        Routine o = Routine.parse(old);
        ok("old save loads: defaults, blocks with no type are Other, teal palette", o != null && !o.subs.isEmpty() && o.blocks.get(0).sub.equals(Routine.OTHER) && o.palette == Routine.PAL_TEAL);
        // web hand-off text with an unknown type is registered
        String web = "DINACHARYA1\nmode=0\nanchor=330\nB\t0\t60\tOrganic chem\t1\tChemistry\nB\t4\t480\tSleep\t1\n";
        Routine w = Routine.parse(web);
        ok("a block's type is registered when it is missing from the list", w.findSub(Routine.TASK, "Chemistry") != null);
        // import keeps your own types
        Routine mine = Routine.sample(); mine.addSub(Routine.TASK, "Maths");
        mine.copyScheduleFrom(w);
        ok("import merges types and keeps yours", mine.findSub(Routine.TASK, "Maths") != null && mine.findSub(Routine.TASK, "Chemistry") != null && mine.blocks.get(0).sub.equals("Chemistry"));
        // Block copy keeps the type
        ok("block copy keeps type", r.blocks.get(idx(r, "Revision")).copy().sub.equals("Revision"));
        // copyLook keeps types
        ok("copyLook keeps the list and palette", r.copyLook().subs.size() == r.subs.size() && r.copyLook().palette == Routine.PAL_OCEAN);

        // ---------------- rests between back-to-back tasks keep the day at 24 hours
        {
            Routine rr = new Routine();
            rr.blocks.clear();
            rr.blocks.add(new Routine.Block(Routine.TASK, "A", 60));
            rr.blocks.add(new Routine.Block(Routine.TASK, "B", 60));
            rr.blocks.add(new Routine.Block(Routine.TASK, "C", 10));
            rr.blocks.add(new Routine.Block(Routine.TASK, "D", 5));
            rr.blocks.add(new Routine.Block(Routine.REST, "Long rest", 1440 - 135));
            int[] res = SlotOps.addRests(rr);
            ok("rests: all three gaps got one", res[0] == 3 && res[1] == 0);
            ok("rests: day still the same length", total(rr) == 1440);
            ok("rests: each rest sits between two tasks", rr.blocks.size() == 8 && rr.blocks.get(1).kind == Routine.REST && rr.blocks.get(3).kind == Routine.REST && rr.blocks.get(5).kind == Routine.REST);
            ok("rests: 10 minutes taken from the task before", mins(rr, "A") == 50 && rr.blocks.get(1).mins == 10);
            ok("rests: short task before -> smaller rest, never below 5 min", mins(rr, "C") >= 5 && mins(rr, "D") >= 5);
            Routine tight = new Routine();
            tight.blocks.clear();
            tight.blocks.add(new Routine.Block(Routine.TASK, "A", 5));
            tight.blocks.add(new Routine.Block(Routine.TASK, "B", 5));
            tight.blocks.add(new Routine.Block(Routine.REST, "Big", 1430));
            int[] r2 = SlotOps.addRests(tight);
            ok("rests: no room -> nothing added and reported", r2[0] == 0 && r2[1] == 1 && tight.blocks.size() == 3 && total(tight) == 1440);
            Routine sm = Routine.sample();
            int tasksBefore = 0;
            for (Routine.Block b : sm.blocks) if (b.kind == Routine.TASK) tasksBefore++;
            int[] r3 = SlotOps.addRests(sm);
            ok("rests on the sample routine keep 24h", total(sm) == 1440 && r3[0] >= 0);
        }

        // ---------------- today-only changes survive look/type changes, but not slot changes
        {
            Routine rr = Routine.sample();
            String sigBefore = Plan.signature(rr);
            rr.palette = Routine.PAL_SUNSET;
            rr.blocks.get(0).sub = "Study";
            rr.syncSubs();
            ok("signature ignores palette and type changes", Plan.signature(rr).equals(sigBefore));
            SlotOps.delete(rr, 3, SlotOps.TO_BELOW);
            ok("signature changes when a slot is deleted", !Plan.signature(rr).equals(sigBefore));
        }

        // ---------------- − / + steps
        ok("step: 52 + 5 -> 55", SlotOps.stepped(52, 5) == 55);
        ok("step: 52 - 5 -> 50", SlotOps.stepped(52, -5) == 50);
        ok("step: 50 - 5 -> 45", SlotOps.stepped(50, -5) == 45);
        ok("step: 5 - 5 stays 5", SlotOps.stepped(5, -5) == 5);
        ok("step: 3 - 5 stays 3", SlotOps.stepped(3, -5) == 3);
        ok("step: 3 + 5 -> 5", SlotOps.stepped(3, 5) == 5);
        ok("step: 40 + 30 -> 60", SlotOps.stepped(40, 30) == 60);
        ok("step: 40 - 30 -> 30", SlotOps.stepped(40, -30) == 30);
        ok("step: 20 - 30 -> 5", SlotOps.stepped(20, -30) == 5);
        ok("step: 1440 + 5 stays 1440", SlotOps.stepped(1440, 5) == 1440);

        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
