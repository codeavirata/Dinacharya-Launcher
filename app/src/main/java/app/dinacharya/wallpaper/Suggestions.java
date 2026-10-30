package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain rules that turn your recent days into suggestions. Nothing here is a trained model: each
 * tip says what it saw, so you can judge it. Uses no Android classes.
 */
public final class Suggestions {
    static final int MAX_TIPS = 5;

    private Suggestions() {
    }

    /**
     * One note about your days, in pieces so a screen can lay it out: a short heading, what was seen, and what to try. {@link #text()} puts
     * the pieces together as one sentence or two, which is the plain text of the tip.
     */
    public static final class Habit {
        /** Going well, to watch, or something to set up so the app can see more. */
        public static final int GOOD = 0, WATCH = 1, SETUP = 2;

        public final int tone;
        public final String head;
        /** What was seen (empty when the note is only something to do). */
        public final String saw;
        /** What to try (empty when the note is only an observation). */
        public final String advice;

        Habit(int tone, String head, String saw, String advice) {
            this.tone = tone;
            this.head = head;
            this.saw = saw;
            this.advice = advice;
        }

        /** The whole note as plain text. */
        public String text() {
            if (saw.isEmpty()) return advice;
            if (advice.isEmpty()) return saw;
            return saw + " " + advice;
        }
    }

    private static String q(String s) {
        return "“" + s + "”";
    }

    private static String dur(long ms) {
        return Routine.formatDuration((int) Math.round(ms / 60000.0));
    }

    /**
     * @param latest today's report (possibly partial), or null
     * @param past   closed reports, newest first
     * @param base   the saved routine
     */
    public static List<String> generate(DayReport latest, List<DayReport> past, Routine base) {
        List<String> out = new ArrayList<>();
        for (Habit h : habits(latest, past, base)) out.add(h.text());
        return out;
    }

    /** The same notes as {@link #generate}, in pieces (at most {@link #MAX_TIPS}). */
    public static List<Habit> habits(DayReport latest, List<DayReport> past, Routine base) {
        List<Habit> tips = new ArrayList<>();
        List<DayReport> window = new ArrayList<>();
        if (latest != null) window.add(latest);
        for (DayReport d : past) {
            if (window.size() >= 8) break;
            if (latest != null && d.day.equals(latest.day)) continue;
            window.add(d);
        }
        DayReport ref = latest != null ? latest : (past.isEmpty() ? null : past.get(0));

        // Setup first: without these the app cannot measure much.
        if (ref != null && !ref.alarmsOn) tips.add(new Habit(Habit.SETUP, "Turn on alarms", "", "Turn on alarms so the app can see which slots you keep."));
        if (ref != null && !ref.phoneKnown) tips.add(new Habit(Habit.SETUP, "Allow usage access", "", "Allow usage access to see your phone time and the apps behind it."));

        // Slots you often take off or rename.
        Map<String, int[]> skipped = new HashMap<>();
        Map<String, int[]> seen = new HashMap<>();
        Map<String, Integer> renamed = new HashMap<>();
        Map<String, String> renamedTo = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (DayReport d : window) {
            for (DayReport.Row r : d.rows) {
                if (r.kind == Routine.SLEEP || r.status > DayReport.UNANSWERED) continue;
                int[] s = seen.get(r.title);
                if (s == null) {
                    s = new int[1];
                    seen.put(r.title, s);
                    skipped.put(r.title, new int[1]);
                    order.add(r.title);
                }
                s[0]++;
                if (r.status == DayReport.SKIPPED) skipped.get(r.title)[0]++;
                if (r.status == DayReport.CHANGED && !r.newTitle.isEmpty()) {
                    String key = r.title + "\u0000" + r.newTitle;
                    renamed.put(key, renamed.containsKey(key) ? renamed.get(key) + 1 : 1);
                    renamedTo.put(key, r.title + "\u0000" + r.newTitle);
                }
            }
        }
        String worstSkip = null;
        for (String t : order) {
            int n = skipped.get(t)[0];
            if (n >= 3 && (worstSkip == null || n > skipped.get(worstSkip)[0])) worstSkip = t;
        }
        if (worstSkip != null) {
            tips.add(new Habit(Habit.WATCH, "A slot you often take off",
                    "You took off " + q(worstSkip) + " on " + skipped.get(worstSkip)[0] + " of the last " + seen.get(worstSkip)[0] + " days.",
                    "Make it shorter, or move it to a quieter time."));
        }
        String worstRename = null;
        for (Map.Entry<String, Integer> e : renamed.entrySet()) {
            if (e.getValue() >= 3 && (worstRename == null || e.getValue() > renamed.get(worstRename))) worstRename = e.getKey();
        }
        if (worstRename != null) {
            String[] parts = renamedTo.get(worstRename).split("\u0000");
            tips.add(new Habit(Habit.WATCH, "A slot you often rename",
                    "You renamed " + q(parts[0]) + " to " + q(parts[1]) + " on " + renamed.get(worstRename) + " days.",
                    "Change it in the routine so the plan matches your day."));
        }

        // Late nights.
        long lateSum = 0;
        int lateN = 0;
        for (DayReport d : window) {
            if (d.actualBedMs > 0 && d.plannedBedMs > 0) {
                lateSum += Math.max(0, (d.actualBedMs - d.plannedBedMs) / 60000L);
                lateN++;
            }
        }
        if (lateN >= 3 && lateSum / lateN >= 30) {
            tips.add(new Habit(Habit.WATCH, "Late to bed",
                    "You went to sleep about " + Routine.formatDuration((int) (lateSum / lateN)) + " late on average over the last " + lateN + " nights.",
                    "Move bedtime and wake-up later in the app, or start winding down earlier."));
        }

        // Phone during a task block.
        if (ref != null && ref.phoneKnown) {
            DayReport.Row worst = null;
            for (DayReport.Row r : ref.rows) {
                if (r.kind != Routine.TASK || r.status == DayReport.SKIPPED || r.activeMs < 20 * 60000L) continue;
                if (r.offMs() < 10 * 60000L || r.offMs() < 0.25 * r.activeMs) continue;
                if (worst == null || r.offMs() > worst.offMs()) worst = r;
            }
            if (worst != null) {
                tips.add(new Habit(Habit.WATCH, "Phone during a task",
                        "You spent " + dur(worst.offMs()) + " on your phone during " + q(worst.title) + " ("
                                + Math.round(100.0 * worst.offMs() / worst.activeMs) + "% of the block).",
                        "Keep the phone in another room for that block."));
            }
        }
        // Phone before bed.
        if (ref != null && ref.beforeBedMs >= 20 * 60000L) {
            tips.add(new Habit(Habit.WATCH, "Phone before bed",
                    "You used the phone for " + dur(ref.beforeBedMs) + " in the hour before bed.",
                    "A screen-free hour helps you fall asleep."));
        }
        // One app dominating.
        if (ref != null && ref.phoneKnown && !ref.apps.isEmpty() && ref.phoneMs > 0) {
            DayReport.App top = ref.apps.get(0);
            if (top.ms >= 60 * 60000L && top.ms >= 0.4 * ref.phoneMs) {
                tips.add(new Habit(Habit.WATCH, "One app takes the most",
                        top.name + " took " + dur(top.ms) + ", " + Math.round(100.0 * top.ms / ref.phoneMs) + "% of your phone time.",
                        "Set a daily limit for it in Settings > Home screen, or in Android's Digital Wellbeing."));
            }
        }
        // The home screen's focus pauses.
        if (ref != null && ref.pauses >= 3) {
            String who = ref.pausedMost.isEmpty() ? "an app" : ref.pausedMost;
            if (ref.openedAfter * 2 > ref.pauses) {
                tips.add(new Habit(Habit.WATCH, "Pauses that are skipped",
                        "The home screen paused you " + ref.pauses + " times and you opened the app anyway " + ref.openedAfter
                                + " times; " + who + " pulled you most.",
                        "Make it Strict, or give it a daily limit, in Settings > Home screen."));
            } else if (ref.wentBack >= 3) {
                tips.add(new Habit(Habit.GOOD, "You stopped yourself",
                        "You stopped yourself " + ref.wentBack + " times at the home screen.", "That is focus working."));
            }
        }

        // The shape of the routine.
        int total = base.totalMinutes();
        if (total < 1440 - 60) {
            tips.add(new Habit(Habit.WATCH, "Time that is not planned",
                    Routine.formatDuration(1440 - total) + " of your 24 hours are not planned.", "Add a block for that time."));
        } else if (total > 1440 + 30) {
            tips.add(new Habit(Habit.WATCH, "A plan that is too long",
                    "Your blocks add up to " + Routine.formatDuration(total - 1440) + " more than 24 hours.", "Shorten something."));
        }
        int rest = 0, tasks = 0, awake = 0;
        for (Routine.Block b : base.blocks) {
            if (b.kind == Routine.SLEEP) continue;
            awake += b.mins;
            if (b.kind == Routine.REST) rest += b.mins;
            if (b.kind == Routine.TASK) tasks += b.mins;
        }
        if (tasks >= 240 && awake > 0 && rest * 100 < awake * 5) {
            tips.add(new Habit(Habit.WATCH, "Little time to rest",
                    "Only " + Math.round(100.0 * rest / awake) + "% of your awake time is rest.",
                    "Short breaks between tasks help you stay focused."));
        }

        // A habit that works.
        Map<String, Integer> streak = new HashMap<>();
        String bestStreak = null;
        for (String t : order) {
            int run = 0;
            for (DayReport d : window) {
                boolean kept = false;
                boolean present = false;
                for (DayReport.Row r : d.rows) {
                    if (r.kind == Routine.TASK && r.title.equals(t) && r.status <= DayReport.UNANSWERED) {
                        present = true;
                        kept = r.status == DayReport.KEPT;
                    }
                }
                if (!present) continue;
                if (kept) run++; else break;
            }
            streak.put(t, run);
            if (run >= 5 && (bestStreak == null || run > streak.get(bestStreak))) bestStreak = t;
        }
        if (bestStreak != null) {
            tips.add(new Habit(Habit.GOOD, "A habit that works",
                    "You have kept " + q(bestStreak) + " for " + streak.get(bestStreak) + " days in a row.",
                    "It is a good place for your hardest work."));
        }

        if (tips.isEmpty() && ref != null && ref.score >= 70) tips.add(new Habit(Habit.GOOD, "A solid day", "A solid day.", "Keep the same routine tomorrow."));
        if (tips.isEmpty()) {
            tips.add(new Habit(Habit.SETUP, "Not enough days yet", "Not enough days yet for suggestions.",
                    "Keep the alarms on and check back in a few days."));
        }
        return tips.size() > MAX_TIPS ? new ArrayList<>(tips.subList(0, MAX_TIPS)) : tips;
    }

    /** The "remind me again after" length you pick most often, or 15 minutes. */
    public static int favouriteSnooze(List<Ev> events) {
        Map<Integer, Integer> count = new HashMap<>();
        int best = 15;
        int bestN = 0;
        for (Ev e : events) {
            if (!Ev.SNOOZE.equals(e.type)) continue;
            int m = (int) e.aLong(0);
            if (m <= 0) continue;
            int n = count.containsKey(m) ? count.get(m) + 1 : 1;
            count.put(m, n);
            if (n > bestN || (n == bestN && m < best)) {
                best = m;
                bestN = n;
            }
        }
        return best;
    }
}
