package app.dinacharya.wallpaper;

import java.time.*;
import java.util.*;

/**
 * Build 84: a slot changed in the middle of the day is several parts with one number (the time before the change, the new plan, what is left),
 * and everything that answers a question about the slot has to land on the right part.
 *
 * <p>Covers: changes made one after the other keep each other, the order of changes is the order they were made in (Adjust time then Change
 * plan), taking off the tail of a slot, the part that an alarm answer belongs to (finishing early, carrying on, skipping, no response), and what
 * the home card offers on a slot that began early or whose break sits at the end of a carried slot. The sample routine on 21 October 2026:
 * "Reading &amp; notes" (number 5) 09:30 to 11:00, "Tea break" 11:00 to 11:10; "Walk &amp; tea" (11) 16:00 to 16:20, "Revision" (12) 16:20 to 17:50,
 * "Evening walk" (13) 17:50 to 18:35.
 */
public class PartsTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }
    static void is(String n, Object got, Object want) {
        boolean v = Objects.equals(got, want);
        System.out.println((v ? "PASS " : "FAIL ") + n + (v ? "" : "\n     got:  " + got + "\n     want: " + want));
        if (!v) fails++;
    }
    static final ZoneId Z = ZoneId.of("Asia/Kolkata");
    static final String DAY = "2026-10-21";
    static final LocalDate D = LocalDate.of(2026, 10, 21);
    static long at(int h, int m) { return ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, Z).toInstant().toEpochMilli(); }
    static long at(int h, int m, int s) { return ZonedDateTime.of(2026, 10, 21, h, m, s, 0, Z).toInstant().toEpochMilli(); }
    static String hm(long ms) { ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(Z); return String.format("%02d:%02d", z.getHour(), z.getMinute()); }

    /** The parts of the day that touch the hours {@code from} to {@code to}, as "09:30-10:00 Reading &amp; notes | ...". A taken-off part says [off]. */
    static String rows(Plan.DayPlan p, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.sc.slots.size(); i++) {
            long s = p.slotStartMs(i), e = p.slotEndMs(i);
            if (e <= at(from, 0) || s >= at(to, 0) || (from == 9 && e <= at(9, 15))) continue;
            Routine.Block b = p.sc.slots.get(i).block;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(hm(s)).append('-').append(hm(e)).append(' ').append(b.title).append(b.skipped ? "[off]" : "");
        }
        return sb.toString();
    }

    /** The same for the day as it is going at {@code now}: a mark after the title says what the part is (#6 cut short, #5 began early...). */
    static String liveRows(Plan.DayPlan p, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.sc.slots.size(); i++) {
            long s = p.slotStartMs(i), e = p.slotEndMs(i);
            if (e <= at(from, 0) || s >= at(to, 0) || (from == 9 && e <= at(9, 15))) continue;
            Routine.Block b = p.sc.slots.get(i).block;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(hm(s)).append('-').append(hm(e)).append(' ').append(b.title).append(b.skipped ? "[off]" : "").append(b.live != 0 ? "#" + b.live : "").append(i == p.cur ? "*" : "");
        }
        return sb.toString();
    }

    static Plan.DayPlan live(Routine r, Overrides ov, List<Ev> ev, long now) {
        Plan.DayPlan base = Plan.resolve(r, ov, now, Z);
        Plan.DayPlan l = LivePlan.apply(base, ev, new AlarmState(), false, now);
        return l == null ? base : l;
    }

    static Plan.DayPlan plan(Routine r, Overrides ov) { return Plan.plan(r, ov, D, Z); }

    /** What Change plan does from the home card: the new plan begins at the later of now and the part's planned start. */
    static void changeFromHome(Routine r, Overrides ov, int src, String name, int asked, long now, long partStartMs) {
        long[] span = ReplaceFromNow.span(plan(r, ov), src);
        ReplaceFromNow when = ReplaceFromNow.of(span[0], span[1], Math.max(now, partStartMs));
        Plan.editPart(r, ov, D, src, name, when.place(asked).stored, -1, "", when.lead);
    }

    public static void main(String[] args) {
        Routine r = Routine.sample();
        r.alarmOn = true;
        String plain = "09:15-09:30 Short break | 09:30-11:00 Reading & notes | 11:00-11:10 Tea break | 11:10-12:40 Practice problems";
        is("the saved plan, before any change", rows(plan(r, new Overrides()), 9, 12), plain);

        // ---- changes made one after the other keep each other
        Overrides a = new Overrides();
        Plan.editPart(r, a, D, 5, "Gym", 20, -1, "", 30);
        is("Gym for 20 minutes from 10:00, the rest goes back to the plan",
            rows(plan(r, a), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:20 Gym | 10:20-11:00 Reading & notes | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");
        Plan.editPart(r, a, D, 5, "Yoga", 0, -1, "", 60);
        is("a second change keeps the first (Gym stays, Yoga takes what is left from 10:30)",
            rows(plan(r, a), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:20 Gym | 10:20-10:30 Reading & notes | 10:30-11:00 Yoga | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");
        Plan.editPart(r, a, D, 5, "Run", 0, -1, "", 0);
        is("a change of the whole slot from its start replaces what was there before",
            rows(plan(r, a), 9, 12), "09:15-09:30 Short break | 09:30-11:00 Run | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        Overrides b = new Overrides();
        Plan.editPart(r, b, D, 5, "Gym", 30, -1, "", 30);
        Plan.editPart(r, b, D, 5, "Yoga", 0, -1, "", 45);
        is("a later change takes over only where it overlaps (Gym 10:00-10:30, Yoga from 10:15: Gym ends at 10:15)",
            rows(plan(r, b), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:15 Gym | 10:15-11:00 Yoga | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        Overrides c = new Overrides();
        Plan.editPart(r, c, D, 5, "Gym", 15, -1, "", 30);
        Plan.editPart(r, c, D, 5, "Gym", 0, -1, "", 45);
        is("two parts next to each other with the same name are one part",
            rows(plan(r, c), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-11:00 Gym | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        // ---- the order the changes were made in is the order they are applied in
        Overrides d = new Overrides();
        Plan.DayPlan p0 = Plan.resolve(r, d, at(10, 0), Z);
        ok("Adjust time: Reading & notes ends 10 minutes earlier, Tea break gets them", Plan.moveMinutes(r, d, p0, 5, 6, -10));
        is("the plan after Adjust time", rows(plan(r, d), 9, 12), "09:15-09:30 Short break | 09:30-10:50 Reading & notes | 10:50-11:10 Tea break | 11:10-12:40 Practice problems");
        changeFromHome(r, d, 5, "Gym", 0, at(10, 0), 0L);
        is("Change plan at 10:00 after Adjust time: Gym is 10:00 to the new end (10:50), not later",
            rows(plan(r, d), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:50 Gym | 10:50-11:10 Tea break | 11:10-12:40 Practice problems");
        Overrides d3 = new Overrides();
        changeFromHome(r, d3, 5, "Gym", 0, at(10, 0), 0L);
        Plan.DayPlan p2 = plan(r, d3);
        int gymIndex = -1;
        for (int i = 0; i < p2.sc.slots.size(); i++) if ("Gym".equals(p2.sc.slots.get(i).block.title)) gymIndex = i;
        ok("Change plan first, then Adjust time: Gym (10:00-11:00) can still be shortened by 10", gymIndex >= 0 && Plan.moveMinutes(r, d3, Plan.resolve(r, d3, at(10, 5), Z), gymIndex, gymIndex + 1, -10));
        is("... and it ends at 10:50, with the earlier part untouched",
            rows(plan(r, d3), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:50 Gym | 10:50-11:10 Tea break | 11:10-12:40 Practice problems");

        // ---- taking off the tail of a slot
        Overrides t = new Overrides();
        Plan.skipFrom(r, t, D, 5, 30);
        is("the part from 10:00 on is taken off, what was done before stays",
            rows(plan(r, t), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-11:00 Reading & notes[off] | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        // ---- saved and read back
        String text = d.serialize();
        Overrides back = Overrides.parse(text);
        is("saved changes read back give the same text", back.serialize(), text);
        is("... and the same plan", rows(plan(r, back), 9, 12), rows(plan(r, d), 9, 12));
        Overrides legacy = Overrides.parse(magic() + "\nsig=" + Plan.signature(r) + "\nE\t" + DAY + "\t5\t0\tReading & notes\tGym\t30\t-1\t-1\t\n");
        is("a change saved by an older version (no start offset, no order number) reads as before",
            rows(plan(r, legacy), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Gym | 10:00-11:00 Reading & notes | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        // ---- the part an answer belongs to
        Ev e1 = Ev.early(at(10, 10), DAY, 5, 5, "Gym", at(10, 10), at(10, 20));
        is("an answer names the number and the planned start of the part: number", Ev.srcOf(e1.b, -1), 5);
        is("... and the planned start", Ev.partOf(e1.b), at(10, 20));
        is("the old way of writing it (the number alone) still reads, with no part", Ev.srcOf("12", -1) + "/" + Ev.partOf("12"), "12/0");
        is("something unreadable gives the default", Ev.srcOf("x@y", 7) + "/" + Ev.partOf("x@y"), "7/0");

        Overrides g = new Overrides();
        Plan.editPart(r, g, D, 5, "Gym", 20, -1, "", 30);   // 09:30-10:00 Reading | 10:00-10:20 Gym | 10:20-11:00 Reading
        List<Ev> base = new ArrayList<>();
        base.add(Ev.rang(at(9, 30), DAY, 5, "Reading & notes"));
        base.add(Ev.ack(at(9, 30, 20), DAY, 5, "Reading & notes", "C", ""));
        base.add(Ev.rang(at(10, 0), DAY, 5, "Gym"));
        base.add(Ev.ack(at(10, 0, 20), DAY, 5, "Gym", "C", ""));
        is("the changed slot as it goes: Gym is running at 10:05",
            liveRows(live(r, g, base, at(10, 5)), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:20 Gym* | 10:20-11:00 Reading & notes | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        // finishing Gym early: Gym ends, the rest of the slot begins early, and nothing else moves
        List<Ev> fin = new ArrayList<>(base);
        fin.add(Ev.early(at(10, 10), DAY, 5, 5, "Gym", at(10, 10), at(10, 20)));
        is("Finish early on the middle part: Gym is cut at 10:10 and the rest of the slot begins then",
            liveRows(live(r, g, fin, at(10, 11)), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:10 Gym#6 | 10:10-11:00 Reading & notes#5* | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        // Change plan on the part that began early: the new plan takes the rest of the slot from the part's planned start, so Gym is not touched
        Overrides gy = Overrides.parse(g.serialize());
        changeFromHome(r, gy, 5, "Yoga", 0, at(10, 15), at(10, 20));
        is("Change plan on it: the plan has Yoga for the rest, Gym untouched",
            rows(plan(r, gy), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:20 Gym | 10:20-11:00 Yoga | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");
        is("... and the day shows Yoga now, Gym still cut at 10:10",
            liveRows(live(r, gy, fin, at(10, 16)), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:10 Gym#6 | 10:10-11:00 Yoga#5* | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");
        Overrides gn = Overrides.parse(g.serialize());
        changeFromHome(r, gn, 5, "Yoga", 0, at(10, 15), 0L);
        is("(counted from now alone, the new plan would land in Gym, and the early finish of Gym would be lost: Gym runs to 10:15, not 10:10)",
            liveRows(live(r, gn, fin, at(10, 16)), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:15 Gym | 10:15-11:00 Yoga* | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        // carrying Gym on when the alarm of the rest of the slot rings: Gym goes on, the rest begins late
        List<Ev> cont = new ArrayList<>(base);
        cont.add(Ev.rang(at(10, 20), DAY, 5, "Reading & notes"));
        cont.add(Ev.ack(at(10, 20, 20), DAY, 5, "Reading & notes", "C", ""));
        cont.add(Ev.extend(at(10, 20, 20), DAY, 5, 5, "Gym", at(10, 30)));
        is("Continue at the rest of the slot's alarm carries Gym on to 10:30 and the rest begins then",
            liveRows(live(r, g, cont, at(10, 25)), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:30 Gym#2* | 10:30-11:00 Reading & notes#1 | 11:00-11:10 Tea break | 11:10-12:40 Practice problems");

        // taking off the rest of the slot at its alarm: Gym is as it was, the rest is off, and Tea break begins
        Overrides sk = Overrides.parse(g.serialize());
        Plan.skipFrom(r, sk, D, 5, 50);
        List<Ev> skip = new ArrayList<>(base);
        skip.add(Ev.rang(at(10, 20), DAY, 5, "Reading & notes"));
        skip.add(Ev.skip(at(10, 20, 10), DAY, 5, "Reading & notes", 6, at(10, 20, 10), at(11, 0)));
        is("Skip at the rest of the slot's alarm takes off only that part (Gym stays) and the next slot begins",
            liveRows(live(r, sk, skip, at(10, 25)), 9, 12), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-10:20 Gym | 10:20-11:10 Tea break#5* | 11:10-12:40 Practice problems");

        // an alarm for the rest of the slot nobody answered shows as "No response" and Gym is not touched
        List<Ev> none = new ArrayList<>(base);
        none.add(Ev.rang(at(10, 20), DAY, 5, "Reading & notes"));
        Plan.DayPlan nr = live(r, g, none, at(10, 30));
        Routine.Block cur = nr.sc.slots.get(nr.cur).block;
        ok("the unanswered alarm of the rest of the slot shows 'No response' at 10:30", cur.src < 0 && LivePlan.NORESP_TITLE.equals(cur.title) && cur.owner == 5);
        ok("... and Gym before it is still Gym", liveRows(nr, 9, 12).contains("10:00-10:20 Gym |"));

        // swapping keeps what was done before the part
        Overrides sw = Overrides.parse(g.serialize());
        Plan.swap(r, sw, D, 5, "Reading & notes", 30, 7, "Practice problems", 0, false, 0);
        is("Swap from the part that is going on: the part before stays, from 10:00 the two trade names",
            rows(plan(r, sw), 9, 14), "09:15-09:30 Short break | 09:30-10:00 Reading & notes | 10:00-11:00 Practice problems | 11:00-11:10 Tea break | 11:10-12:40 Reading & notes | 12:40-13:40 Lunch | 13:40-14:00 Power nap");

        // ---- a slot that began early: the old way of writing the answer (no part) works as before
        List<Ev> old = new ArrayList<>();
        old.add(Ev.rang(at(16, 20), DAY, 12, "Revision"));
        old.add(Ev.ack(at(16, 20, 20), DAY, 12, "Revision", "C", ""));
        old.add(Ev.early(at(16, 41), DAY, 13, 12, "Revision", at(16, 41)));
        is("an early finish written without a part (an older version) cuts the slot and starts the next",
            liveRows(live(r, new Overrides(), old, at(16, 45)), 16, 19), "16:00-16:20 Walk & tea | 16:20-16:41 Revision#6 | 16:41-18:35 Evening walk#5* | 18:35-19:20 Dinner");

        // ---- what the home card offers
        List<Ev> early = new ArrayList<>(old.subList(0, 2));
        early.add(Ev.early(at(16, 41), DAY, 13, 12, "Revision", at(16, 41), at(17, 50)));
        Plan.DayPlan lv = live(r, new Overrides(), early, at(16, 45));
        EarlyFinish.Offer o = EarlyFinish.of(lv, at(16, 45));
        ok("Finish on a slot that began early is offered", o != null && o.kind == EarlyFinish.FINISH && o.srcCur == 13 && o.srcNext == 14);
        ok("... the offer knows it began early (the saved plan has it starting at 17:50)", o != null && o.beganEarly && !o.carried);
        FinishChoices fc = FinishChoices.of(o);
        ok("... Rest and Start next are offered, a New activity is not (the plan cannot place it)", fc.rest && fc.startNext && !fc.newActivity);
        Plan.DayPlan later = live(r, new Overrides(), early, at(17, 55));
        EarlyFinish.Offer o2 = EarlyFinish.of(later, at(17, 55));
        ok("once the planned start has passed it is a slot like any other: a New activity is offered again", o2 != null && !o2.beganEarly && FinishChoices.of(o2).newActivity);
        EarlyFinish.Offer plainOffer = EarlyFinish.of(live(r, new Overrides(), old.subList(0, 2), at(16, 41)), at(16, 41));
        ok("a slot that began on time is unchanged: all three answers", plainOffer != null && !plainOffer.beganEarly && FinishChoices.of(plainOffer).rest && FinishChoices.of(plainOffer).newActivity);

        // a break at the end of a carried slot: the row on the screen is the break, and Finish hands over to the slot after the carried one
        List<Ev> car = new ArrayList<>();
        car.add(Ev.rang(at(16, 0), DAY, 11, "Walk & tea"));
        car.add(Ev.ack(at(16, 0, 20), DAY, 11, "Walk & tea", "C", ""));
        car.add(Ev.rang(at(16, 20), DAY, 12, "Revision"));
        car.add(Ev.ack(at(16, 20, 20), DAY, 12, "Revision", "C", ""));
        car.add(Ev.extend(at(16, 20, 20), DAY, 12, 11, "Walk & tea", at(16, 31)));
        car.add(Ev.breakTaken(at(16, 26), at(16, 31), DAY, 11, "Walk & tea"));
        Plan.DayPlan lc = live(r, new Overrides(), car, at(16, 28));
        ok("on that break the card shows the break", lc.sc.slots.get(lc.cur).block.live == Routine.LIVE_BREAK);
        EarlyFinish.Offer oc = EarlyFinish.of(lc, at(16, 28));
        ok("Finish is offered as handing over from the carried slot (11) to the next (12)", oc != null && oc.carried && oc.srcCur == 11 && oc.srcNext == 12);
        ok("... with nothing else to choose", FinishChoices.of(oc).confirmOnly() || (!FinishChoices.of(oc).rest && !FinishChoices.of(oc).newActivity));
        // what Finish writes: the break ends now (so it cannot come back), and the carry ends now
        List<Ev> done = new ArrayList<>(car);
        done.add(Ev.breakTaken(at(16, 26), at(16, 28), DAY, 11, "Walk & tea"));
        done.add(Ev.extend(at(16, 28), DAY, 12, 11, "Walk & tea", at(16, 28)));
        Plan.DayPlan ld = live(r, new Overrides(), done, at(16, 29));
        is("after Finish the next slot is running and the break is over",
            liveRows(ld, 15, 18), "14:00-16:00 Project work | 16:00-16:26 Walk & tea | 16:26-16:28 Break#3 | 16:28-17:50 Revision#1* | 17:50-18:35 Evening walk");

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }

    static String magic() {
        return new Overrides().serialize().split("\n")[0];
    }
}
