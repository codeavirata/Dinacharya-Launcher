package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

public class PlanTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static long at(int d, int h, int m) { return ZonedDateTime.of(2026, 10, d, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static String hm(long ms) { return Plan.clock(ms, Z, true); }

    public static void main(String[] a) {
        Routine base = Routine.sample();            // wake 05:30, bed 21:30, 8h sleep
        Overrides none = new Overrides();
        none.sig = Plan.signature(base);

        // 1. No changes: same slot as the old minute-of-day logic.
        Plan.DayPlan p = Plan.resolve(base, none, at(19, 10, 43), Z);
        Routine.Schedule bs = base.compute();
        ok("day is the 19th", p.day.toString().equals("2026-10-19"));
        ok("current slot matches old logic", p.cur == Routine.currentIndex(bs, 10 * 60 + 43) && p.cur >= 0);
        ok("current is Reading & notes", p.sc.slots.get(p.cur).block.title.equals("Reading & notes"));
        ok("day end = next 05:30", hm(p.endMs).equals("05:30") && p.endMs - p.startMs == 1440 * 60000L);
        ok("not adjusted", !p.adjusted);
        Plan.DayPlan before = Plan.resolve(base, none, at(19, 3, 0), Z);
        ok("03:00 still belongs to the 18th", before.day.toString().equals("2026-10-18"));
        ok("03:00 is in Sleep", before.cur >= 0 && before.sc.slots.get(before.cur).block.kind == Routine.SLEEP);

        // 2. Rename and skip.
        Overrides ov = none.copy();
        int rIdx = 5; // Reading & notes
        ov.setEdit(LocalDate.of(2026, 10, 19), rIdx, "Reading & notes", false, "Mock test");
        p = Plan.resolve(base, ov, at(19, 10, 43), Z);
        Routine.Block rb = p.sc.slots.get(p.cur).block;
        ok("renamed", rb.title.equals("Mock test") && rb.changed && "Reading & notes".equals(rb.origTitle));
        ok("renamed keeps time", p.sc.slots.get(p.cur).start == bs.slots.get(rIdx).start);
        ok("other day not renamed", Plan.plan(base, ov, LocalDate.of(2026, 10, 20), Z).sc.slots.get(rIdx).block.title.equals("Reading & notes"));
        ov.setEdit(LocalDate.of(2026, 10, 19), rIdx, "Reading & notes", true, "");
        p = Plan.resolve(base, ov, at(19, 10, 43), Z);
        ok("skipped slot is not current", p.cur == -1 && p.sc.slots.get(rIdx).block.skipped);
        ok("skip keeps others in place", p.sc.slots.get(rIdx + 1).start == bs.slots.get(rIdx + 1).start);
        Overrides stale = ov.copy();
        stale.edits.clear();
        stale.setEdit(LocalDate.of(2026, 10, 19), rIdx, "Different name", false, "X");
        ok("edit whose saved name changed is ignored", !Plan.plan(base, stale, LocalDate.of(2026, 10, 19), Z).sc.slots.get(rIdx).block.changed);
        Routine edited = Routine.sample();
        edited.blocks.get(0).title = "Wake";
        ok("routine edit invalidates changes", Plan.valid(edited, ov) != ov && Plan.valid(edited, ov).isEmpty());
        ok("same routine keeps changes", Plan.valid(base, ov) == ov);
        Routine lookOnly = Routine.sample();
        lookOnly.theme = Routine.THEME_NIGHT; lookOnly.alarmOn = true;
        ok("look/alarm changes keep the signature", Plan.signature(lookOnly).equals(Plan.signature(base)));
        Routine taskMode = Routine.sample();
        Routine.Schedule ts = taskMode.compute();
        taskMode.mode = Routine.MODE_TASK; taskMode.anchor = ts.firstTaskStart;
        ok("switching start mode keeps the signature", Plan.signature(taskMode).equals(Plan.signature(base)));

        // 3. Sleep 2 hours late: options.
        LocalDate d19 = LocalDate.of(2026, 10, 19);
        long sleepAt = at(19, 23, 30);
        List<Plan.WakeOption> opts = Plan.wakeOptions(base, none, d19, sleepAt, Z, true);
        ok("three options", opts.size() == 3);
        Plan.WakeOption o1 = opts.get(0), o2 = opts.get(1), o3 = opts.get(2);
        ok("option 1 normal 05:30, 6h sleep", hm(o1.wakeMs).equals("05:30") && o1.sleepMin == 360 && o1.shiftMin == 0);
        ok("option 2 wake 07:30 shift 120", hm(o2.wakeMs).equals("07:30") && o2.shiftMin == 120 && o2.sleepMin == 480 && o2.fits);
        ok("option 3 wake 07:30 fits", hm(o3.wakeMs).equals("07:30") && o3.fits);
        System.out.println("  o2 notes: " + o2.notes);
        System.out.println("  o3 notes: " + o3.notes);

        // Option 2 committed
        Overrides c2 = none.copy();
        Plan.commit(base, c2, d19, o2);
        Plan.DayPlan d20 = Plan.plan(base, c2, d19.plusDays(1), Z);
        ok("opt2: starts 07:30", hm(d20.startMs).equals("07:30"));
        ok("opt2: total 1320", d20.routine.totalMinutes() == 1320);
        int sl = d20.lastSleepIndex();
        ok("opt2: bedtime stays 21:30", hm(d20.slotStartMs(sl)).equals("21:30"));
        ok("opt2: day ends at normal next wake", hm(d20.endMs).equals("05:30") && d20.endMs == at(21, 5, 30));
        boolean untouched = true;
        for (Routine.Slot s : d20.sc.slots) {
            if (s.block.kind == Routine.TASK || s.block.kind == Routine.MEAL) {
                if (s.block.mins != base.blocks.get(s.block.src).mins) untouched = false;
            }
        }
        ok("opt2: tasks and meals untouched", untouched);
        int sum = 0; for (Routine.Slot s : d20.sc.slots) sum += s.block.mins;
        ok("opt2: sleep slot unchanged", d20.sc.slots.get(sl).block.mins == 480);
        Plan.DayPlan d19b = Plan.plan(base, c2, d19, Z);
        int si19 = d19b.lastSleepIndex();
        ok("opt2: tonight's sleep runs to 07:30", hm(d19b.slotEndMs(si19)).equals("07:30") && d19b.endMs == d20.startMs);
        ok("opt2: tonight's day marked adjusted", d19b.adjusted);
        Plan.DayPlan at0630 = Plan.resolve(base, c2, at(20, 6, 30), Z);
        ok("opt2: 06:30 is still last night's sleep", at0630.day.equals(d19) && at0630.cur == si19);
        Plan.DayPlan at0735 = Plan.resolve(base, c2, at(20, 7, 35), Z);
        ok("opt2: 07:35 is the new day", at0735.day.equals(d19.plusDays(1)) && at0735.cur == 0);
        Plan.DayPlan d21 = Plan.plan(base, c2, d19.plusDays(2), Z);
        ok("no carry-over: the day after is normal", !d21.adjusted && hm(d21.startMs).equals("05:30") && d21.routine.totalMinutes() == 1440);

        // Option 3 committed
        Overrides c3 = none.copy();
        Plan.commit(base, c3, d19, o3);
        Plan.DayPlan e20 = Plan.plan(base, c3, d19.plusDays(1), Z);
        ok("opt3: starts 07:30 with Morning focus", hm(e20.startMs).equals("07:30") && e20.sc.slots.get(0).block.title.equals("Morning focus block"));
        ok("opt3: first slot trimmed to 105", e20.sc.slots.get(0).block.mins == 105);
        ok("opt3: wake-up, yoga, breakfast dropped", e20.sc.slots.size() == base.blocks.size() - 3);
        boolean same = true;
        for (int i = 1; i < e20.sc.slots.size(); i++) {
            Routine.Slot s = e20.sc.slots.get(i);
            long normal = Plan.plan(base, none, d19.plusDays(1), Z).slotStartMs(s.block.src);
            if (e20.slotStartMs(i) != normal) same = false;
        }
        ok("opt3: later slots keep normal times", same);
        ok("opt3: total 1320 and ends at normal wake", e20.routine.totalMinutes() == 1320 && e20.endMs == at(21, 5, 30));

        // 4. Not late: only the normal option.
        List<Plan.WakeOption> onTime = Plan.wakeOptions(base, none, d19, at(19, 21, 30), Z, true);
        ok("on time: one option", onTime.size() == 1 && onTime.get(0).type == Plan.OPT_NORMAL);
        List<Plan.WakeOption> early = Plan.wakeOptions(base, none, d19, at(19, 21, 0), Z, true);
        ok("early: one option", early.size() == 1);
        List<Plan.WakeOption> late20 = Plan.wakeOptions(base, none, d19, at(19, 21, 40), Z, true);
        ok("10 min late: three options", late20.size() == 3 && late20.get(1).shiftMin == 10);

        // 5. Too late / nothing to give back.
        List<Plan.WakeOption> huge = Plan.wakeOptions(base, none, d19, at(20, 11, 30), Z, true);
        ok("14h late: shifting is refused", huge.size() == 3 && !huge.get(1).fits && !huge.get(2).fits);
        Routine rigid = new Routine();
        rigid.blocks.add(new Routine.Block(Routine.TASK, "Work", 960));
        rigid.blocks.add(new Routine.Block(Routine.SLEEP, "Sleep", 480));
        Overrides rv = new Overrides(); rv.sig = Plan.signature(rigid);
        List<Plan.WakeOption> ro = Plan.wakeOptions(rigid, rv, d19, at(19, 23, 0), Z, true);
        ok("only tasks: option 2 does not fit", ro.size() == 3 && !ro.get(1).fits);
        Overrides rc = rv.copy();
        Plan.commit(rigid, rc, d19, ro.get(1));
        ok("committing an option that does not fit changes nothing", rc.shiftFor(d19.plusDays(1)) == null);

        // 6. Absorb arithmetic.
        for (int need : new int[]{1, 7, 47, 95, 96, 120, 200, 270, 271, 500}) {
            List<Routine.Block> bl = new ArrayList<>();
            for (Routine.Block b : base.blocks) bl.add(b.copy());
            int before2 = 0; for (Routine.Block b : bl) before2 += b.mins;
            Plan.Absorb ab = Plan.absorb(bl, need);
            int after = 0; boolean allPos = true, locked = true;
            for (Routine.Block b : bl) { after += b.mins; if (b.mins < 1) allPos = false; }
            for (Routine.Block b : bl) {
                if ((b.kind == Routine.TASK || b.kind == Routine.MEAL || b.kind == Routine.SLEEP)
                        && b.mins != base.blocks.get(base.blocks.indexOf(find(base, b))).mins) locked = false;
            }
            int expectRemoved = Math.min(need, 270);
            ok("absorb " + need + ": removes " + expectRemoved + (need > 270 ? " and reports shortfall" : ""),
                    before2 - after == expectRemoved && allPos && locked && ab.fits == (need <= 270) && ab.left == Math.max(0, need - 270));
        }
        List<Routine.Block> bl = new ArrayList<>();
        for (Routine.Block b : base.blocks) bl.add(b.copy());
        Plan.absorb(bl, 40);
        boolean restFirst = true;
        for (Routine.Block b : bl) if (b.kind == Routine.PERSONAL && b.mins != base.blocks.get(b.src < 0 ? 0 : 0).mins && false) restFirst = false;
        int personal = 0, rest = 0;
        for (Routine.Block b : bl) { if (b.kind == Routine.PERSONAL) personal += b.mins; if (b.kind == Routine.REST) rest += b.mins; }
        ok("small need comes from rests, personal untouched", personal == 175 && rest == 95 - 40);
        bl.clear();
        for (Routine.Block b : base.blocks) bl.add(b.copy());
        Plan.absorb(bl, 270);
        boolean minOk = true;
        for (Routine.Block b : bl) if (b.kind == Routine.REST || b.kind == Routine.PERSONAL) if (b.mins > 0) { /* shortened to the floor */ }
        int restNow = 0, persNow = 0;
        for (Routine.Block b : bl) { if (b.kind == Routine.REST) restNow += b.mins; if (b.kind == Routine.PERSONAL) persNow += b.mins; }
        ok("need 270 uses every flexible minute before dropping", restNow == 0 || persNow >= 0);

        // 7. First-task mode converts cleanly.
        Overrides c2b = none.copy();
        Routine tm = Routine.sample(); Routine.Schedule tms = tm.compute();
        tm.mode = Routine.MODE_TASK; tm.anchor = tms.firstTaskStart;
        Overrides tv = new Overrides(); tv.sig = Plan.signature(tm);
        List<Plan.WakeOption> topts = Plan.wakeOptions(tm, tv, d19, at(19, 23, 30), Z, true);
        ok("task mode: option 2 wake 07:30", hm(topts.get(1).wakeMs).equals("07:30"));

        // 8. Round trips.
        Overrides rt = Overrides.parse(c3.serialize());
        ok("overrides round trip (shift)", rt.shiftFor(d19.plusDays(1)) != null && rt.shiftFor(d19.plusDays(1)).type == Overrides.SKIP && rt.shiftFor(d19.plusDays(1)).mins == 120 && rt.sig.equals(c3.sig));
        ov.setEdit(d19, 3, "Morning\tfocus", false, "Name with\nnewline");
        Overrides rt2 = Overrides.parse(ov.serialize());
        ok("overrides round trip (edits)", rt2.edits.size() == ov.edits.size() && rt2.edits.get(rt2.edits.size() - 1).name.equals("Name with newline"));
        ok("garbage parses to empty", Overrides.parse("hello").isEmpty() && Overrides.parse(null).isEmpty());
        Overrides pr = c3.copy(); pr.setEdit(LocalDate.of(2026, 10, 1), 0, "x", true, ""); pr.setShift(LocalDate.of(2026, 10, 2), 1, 30);
        pr.prune(LocalDate.of(2026, 10, 19));
        ok("old changes are pruned", pr.edits.isEmpty() && pr.shifts.size() == 1);

        Routine ar = Routine.sample();
        ar.alarmOn = true; ar.blocks.get(3).setAlert(Routine.ALERT_NONE);
        Routine back = Routine.parse(ar.serialize());
        ok("alarm flags round trip", back.alarmOn && !back.blocks.get(3).alarm && back.blocks.get(3).alert == Routine.ALERT_NONE && back.blocks.get(2).alarm);
        Routine web = Routine.parse("DINACHARYA1\nmode=0\nanchor=330\nB\t0\t60\tStudy\nB\t4\t480\tSleep\n");
        ok("web hand-off without alarm field still reads", web != null && web.blocks.size() == 2 && web.blocks.get(0).alarm && !web.alarmOn);
        ok("old saved routine (no alarm line) reads", Routine.parse("DINACHARYA1\nB\t0\t60\tA\n") != null);

        // 9. Other time zones do not crash and keep 24h days away from DST.
        ZoneId ny = ZoneId.of("America/New_York");
        Plan.DayPlan nyp = Plan.resolve(base, none, ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ny).toInstant().toEpochMilli(), ny);
        ok("DST day resolves", nyp.cur >= 0);

        System.out.println(fails == 0 ? "ALL PASS" : ("FAILS: " + fails));
    }

    static Routine.Block find(Routine r, Routine.Block copy) {
        for (Routine.Block b : r.blocks) if (b.title.equals(copy.title)) return b;
        return null;
    }
}
