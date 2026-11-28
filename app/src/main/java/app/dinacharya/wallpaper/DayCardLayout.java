package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * Which slots go on the "my day" picture: all of them when they fit, otherwise the short rests are left out first, and then the end of
 * the list is replaced by one "+ N more" line. Pure rules; {@link DayCard} draws them.
 */
public final class DayCardLayout {
    /** The most lines on the picture, the "+ N more" line included. */
    public static final int MAX_ROWS = 11;
    /** A rest this short or shorter is the first thing left out. */
    public static final int SHORT_REST = 20;

    /** One line: when, what, how long, which kind (a {@link Routine} kind), or a "more" line. */
    public static final class Row {
        public final String time, title;
        public final int kind, mins;
        public final boolean more;

        public Row(String time, String title, int kind, int mins, boolean more) {
            this.time = time;
            this.title = title;
            this.kind = kind;
            this.mins = mins;
            this.more = more;
        }
    }

    private DayCardLayout() {
    }

    /** The lines for the picture, at most {@link #MAX_ROWS}. */
    public static List<Row> pick(List<Row> all) {
        List<Row> rows = new ArrayList<>(all);
        for (int i = rows.size() - 1; i >= 0 && rows.size() > MAX_ROWS; i--) {
            Row r = rows.get(i);
            if (r.kind == Routine.REST && r.mins <= SHORT_REST) rows.remove(i);
        }
        if (rows.size() <= MAX_ROWS) return rows;
        int left = rows.size() - (MAX_ROWS - 1);
        List<Row> out = new ArrayList<>(rows.subList(0, MAX_ROWS - 1));
        out.add(new Row("", "+ " + left + " more", Routine.TASK, 0, true));
        return out;
    }

    /** The lines of a day: one per slot, with the times written the way the person chose (12 or 24 hours). */
    public static List<Row> rows(Routine.Schedule sc, boolean h24) {
        List<Row> l = new ArrayList<>();
        for (Routine.Slot s : sc.slots) {
            String t = s.block.title == null || s.block.title.trim().isEmpty() ? Routine.KIND_NAMES[s.block.kind] : s.block.title.trim();
            l.add(new Row(Routine.formatTime(s.start, h24), t, s.block.kind, s.block.mins, false));
        }
        return l;
    }
}
