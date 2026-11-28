package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * The first-day help on the Routine tab ("Exam Specific": ready-made days to start from) and the shape of a day as a bar.
 *
 * The section is for a person who has not made a schedule of their own yet. A person who changed the sample day (renamed, resized, added or
 * deleted a slot, started another routine or one for a date) or took a template has made one: from then on the section is not shown, and
 * stays away even if the routine is later set back to the sample. Hiding it before that only hides it for the rest of this session: it
 * comes back, because the person still has no schedule of their own. Changing only the start time does not count. Uses no Android classes.
 */
public final class FirstDay {
    private FirstDay() {
    }

    /** What tapping Hide does. */
    public static final int HIDE_FOR_NOW = 0, HIDE_FOR_GOOD = 1;

    /** True when {@code r} is no longer the sample day: its slots were changed, or it has other routines or one-day routines. The start time does not count. */
    public static boolean customized(Routine r) {
        if (!r.others.isEmpty() || !r.temps.isEmpty()) return true;
        Routine sample = Routine.sample();
        sample.anchor = r.anchor;
        sample.mode = r.mode;
        return !RoutineLock.sameDay(sample, r);
    }

    /** Hide for good when the person has a schedule of their own, otherwise only for now (it comes back next time the app is opened). */
    public static int hide(Routine r) {
        return customized(r) ? HIDE_FOR_GOOD : HIDE_FOR_NOW;
    }

    /** Whether the section is shown: not once a schedule of the person's own was made ({@code done} remembers that), and not after Hide this session. */
    public static boolean shown(Routine r, boolean done, boolean hiddenThisSession) {
        return !done && !hiddenThisSession && !customized(r);
    }

    /** Whether to remember for good that the person has made a schedule of their own. */
    public static boolean done(Routine r, boolean done) {
        return done || customized(r);
    }

    /** The slots of a day as a bar: {kind, minutes} runs, with slots of the same kind next to each other made one run. */
    public static List<int[]> shape(Routine r) {
        List<int[]> out = new ArrayList<>();
        for (Routine.Block b : r.blocks) {
            if (b.mins <= 0) continue;
            int[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last[0] == b.kind) last[1] += b.mins;
            else out.add(new int[]{b.kind, b.mins});
        }
        return out;
    }
}
