package app.dinacharya.wallpaper;

public class ShareTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static boolean same(Routine a, Routine b) {
        if (a.blocks.size() != b.blocks.size() || a.anchor != b.anchor || a.mode != b.mode) return false;
        for (int i = 0; i < a.blocks.size(); i++) {
            Routine.Block x = a.blocks.get(i), y = b.blocks.get(i);
            if (x.kind != y.kind || x.mins != y.mins || !x.title.equals(y.title) || !x.sub.equals(y.sub) || x.wake != y.wake || x.alert != y.alert || !x.note.equals(y.note)) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        ok("there are six templates", RoutineTemplates.count() == 6);
        for (int i = 0; i < RoutineTemplates.count(); i++) {
            Routine r = RoutineTemplates.build(i);
            String n = RoutineTemplates.name(i);
            ok(n + ": fills exactly 24 hours", r.totalMinutes() == 1440);
            Routine.Block sl = r.blocks.get(r.blocks.size() - 1);
            ok(n + ": sleep is 6 to 9 hours and last", sl.kind == Routine.SLEEP && sl.mins >= 360 && sl.mins <= 540);
            ok(n + ": first slot is the start-of-day alarm and only one is", r.blocks.get(0).wake && r.blocks.stream().filter(b -> b.wake).count() == 1);
            boolean subsOk = true, lens = true;
            for (Routine.Block b : r.blocks) {
                if (r.findSub(b.kind, b.sub) == null) subsOk = false;
                if (b.sub.length() > Routine.MAX_SUB_LEN || b.title.length() > 40 || b.mins < 5) lens = false;
            }
            ok(n + ": every type exists and names are short", subsOk && lens);
            ok(n + ": has a line about it and a start time", !RoutineTemplates.about(i).isEmpty() && r.anchor >= 240 && r.anchor <= 450);
            Routine back = Routine.parse(r.serialize());
            ok(n + ": survives being saved and read", back != null && same(r, back));
            String msg = RoutineShare.message(r);
            Routine shared = RoutineShare.read("Hi!\n" + msg + "\nthanks");
            ok(n + ": shares and imports with the same slots", shared != null && same(r, shared) && shared.name.equals(r.name));
            ok(n + ": the share message is one code line plus words, under 2500 characters", msg.length() < 2500 && msg.contains("DNC1:") && !msg.contains("\t"));
        }
        Routine s = Routine.sample();
        s.blocks.get(3).note = "Chapter 4\nPYQs";
        s.alarmOn = true;
        s.goals.add(Routine.Goal.once(Routine.TASK, "Study", 30, java.time.LocalDate.now()));
        Routine back = RoutineShare.read(RoutineShare.code(s));
        ok("the sample day with a note shares and imports", back != null && same(s, back));
        ok("settings and goals are not shared", back != null && back.goals.isEmpty() && !back.alarmOn);
        ok("the web app's plain text still imports", RoutineShare.read("note\n" + Routine.sample().serialize()) != null);
        ok("plain words give nothing", RoutineShare.read("hello") == null && RoutineShare.read("") == null && RoutineShare.read(null) == null);
        ok("a broken code gives nothing and no crash", RoutineShare.read("DNC1:AAAAAAAAAAAA") == null && RoutineShare.read("DNC1:!!!!") == null && RoutineShare.read("DNC1:ab") == null);
        StringBuilder bomb = new StringBuilder();
        java.util.zip.Deflater d = new java.util.zip.Deflater();
        d.setInput(new byte[5_000_000]); d.finish();
        byte[] buf = new byte[100000]; int n = d.deflate(buf);
        ok("a code that unpacks to something huge is refused", RoutineShare.read("DNC1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(buf, n))) == null);
        for (int i = 0; i < RoutineTemplates.count(); i++) {
            Routine t = RoutineTemplates.build(i);
            java.util.List<DayCardLayout.Row> all = DayCardLayout.rows(t.compute(), false);
            java.util.List<DayCardLayout.Row> pick = DayCardLayout.pick(all);
            ok(RoutineTemplates.name(i) + ": the day picture has at most " + DayCardLayout.MAX_ROWS + " lines", pick.size() <= DayCardLayout.MAX_ROWS && !pick.isEmpty());
            ok(RoutineTemplates.name(i) + ": the picture starts with the first slot and shows its time", pick.get(0).title.equals(t.blocks.get(0).title) && pick.get(0).time.endsWith("M"));
        }
        java.util.List<DayCardLayout.Row> few = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) few.add(new DayCardLayout.Row("6:00 AM", "S" + i, Routine.TASK, 60, false));
        ok("a short day is shown whole", DayCardLayout.pick(few).size() == 5);
        java.util.List<DayCardLayout.Row> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) many.add(new DayCardLayout.Row("6:00 AM", "S" + i, Routine.TASK, 60, false));
        java.util.List<DayCardLayout.Row> pm = DayCardLayout.pick(many);
        ok("a long day ends with a '+ N more' line that counts what is left out", pm.size() == DayCardLayout.MAX_ROWS && pm.get(pm.size() - 1).more && pm.get(pm.size() - 1).title.equals("+ 10 more"));
        java.util.List<DayCardLayout.Row> rests = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) { rests.add(new DayCardLayout.Row("t", "T" + i, Routine.TASK, 60, false)); rests.add(new DayCardLayout.Row("t", "R" + i, Routine.REST, 10, false)); }
        java.util.List<DayCardLayout.Row> pr = DayCardLayout.pick(rests);
        int tasksKept = 0; for (DayCardLayout.Row x : pr) if (x.kind == Routine.TASK) tasksKept++;
        ok("short rests are left out before anything else (all 8 slots kept, no 'more' line)", pr.size() == DayCardLayout.MAX_ROWS && tasksKept == 8 && !pr.get(pr.size() - 1).more);
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
    }
}
