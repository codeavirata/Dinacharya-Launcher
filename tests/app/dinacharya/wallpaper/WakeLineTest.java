package app.dinacharya.wallpaper;

/** The person's own line: read as the quote by default, or typed to put the day off, to start the day, or both. */
public class WakeLineTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    public static void main(String[] a) {
        Routine r = Routine.parse("DINACHARYA1\nB\t0\t60\tStudy\nB\t4\t480\tSleep\n");
        r.wakeQuote = "  Small steps, every day.  ";
        ok("default: read as the quote", r.wakeLineUse == Routine.LINE_QUOTE && r.quoteLine().equals("Small steps, every day."));
        ok("default: nothing is typed from it", r.typeLine(true).isEmpty() && r.typeLine(false).isEmpty());

        r.wakeLineUse = Routine.LINE_SNOOZE;
        ok("snooze: typed to put the day off", r.typeLine(true).equals("Small steps, every day."));
        ok("snooze: not typed to start", r.typeLine(false).isEmpty());
        ok("typed lines are not also the quote", r.quoteLine().isEmpty());

        r.wakeLineUse = Routine.LINE_START;
        ok("start: typed to start", r.typeLine(false).equals("Small steps, every day.") && r.typeLine(true).isEmpty());

        r.wakeLineUse = Routine.LINE_BOTH;
        ok("both: typed in both places", !r.typeLine(true).isEmpty() && !r.typeLine(false).isEmpty());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("word ");
        r.wakeQuote = sb.toString().trim();
        String t = r.typeLine(true);
        ok("a long line is cut for typing at a word", t.length() <= Routine.MAX_TYPE_LEN && t.endsWith("word") && t.length() > 40);

        r.wakeLineUse = Routine.LINE_START;
        Routine copy = r.copyLook();
        ok("a copy keeps how the line is used", copy.wakeLineUse == Routine.LINE_START);
        Routine same = new Routine();
        same.replaceWith(r);
        ok("replacing keeps how the line is used", same.wakeLineUse == Routine.LINE_START);
        Routine back = Routine.parse(r.serialize());
        ok("it survives saving and loading", back != null && back.wakeLineUse == Routine.LINE_START);
        r.wakeLineUse = Routine.LINE_QUOTE;
        ok("the default is not written", !r.serialize().contains("wakeLineUse"));
        ok("an unknown value falls back to the quote", Routine.parse(r.serialize() + "wakeLineUse=9\n").wakeLineUse == Routine.LINE_QUOTE);
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
