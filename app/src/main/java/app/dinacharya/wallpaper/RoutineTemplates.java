package app.dinacharya.wallpaper;

/**
 * Ready-made days to start from: UPSC, SSC and banking, board exams, JEE and NEET, college, and a working professional. Each one is a
 * start-of-day time and a list of slots that fill the whole day (the last slot, Sleep, takes what is left, between 6 and 9 hours), so a
 * person edits a day that already adds up instead of building one from nothing. Uses no Android classes except through {@link Routine}.
 */
public final class RoutineTemplates {
    private RoutineTemplates() {
    }

    private static final int T = Routine.TASK, R = Routine.REST, M = Routine.MEAL, P = Routine.PERSONAL;

    /** One template: its name, a line about it, the start-of-day time (minutes after midnight) and its slots as {kind, title, minutes, type}. */
    private static final class Def {
        final String name, about;
        final int wake;
        final Object[][] slots;

        Def(String name, String about, int wake, Object[][] slots) {
            this.name = name;
            this.about = about;
            this.wake = wake;
            this.slots = slots;
        }
    }

    private static final Def[] ALL = {
        new Def("UPSC Civil Services", "Wake at 5:00. GS, optional, answer writing, current affairs, mock tests.", 300, new Object[][]{
            {P, "Wake up & freshen up", 30, "Personal"}, {P, "Yoga & pranayama", 40, "Exercise"}, {T, "Newspaper & current affairs", 60, "Current affairs"},
            {M, "Breakfast", 30, "Meal"}, {T, "GS study", 150, "Study"}, {R, "Short break", 15, "Other"}, {T, "Optional subject", 120, "Study"},
            {M, "Lunch", 60, "Meal"}, {R, "Power nap", 20, "Nap"}, {T, "Answer writing", 90, "Answer writing"}, {R, "Tea break", 15, "Other"},
            {T, "Revision & notes", 90, "Revision"}, {P, "Evening walk", 40, "Exercise"}, {M, "Dinner", 45, "Meal"},
            {T, "Mock test or PYQs", 90, "Mock test"}, {T, "Plan tomorrow", 20, "Revision"}, {R, "Wind down", 30, "Other"}}),
        new Def("SSC / Banking", "Wake at 5:30. Quant, reasoning, English, GA and a daily mock.", 330, new Object[][]{
            {P, "Wake up & freshen up", 30, "Personal"}, {P, "Exercise or run", 45, "Exercise"}, {M, "Breakfast", 30, "Meal"},
            {T, "Quant practice", 90, "Quant"}, {T, "Reasoning", 75, "Reasoning"}, {R, "Short break", 15, "Other"}, {T, "English", 75, "English"},
            {T, "General awareness", 60, "General awareness"}, {M, "Lunch", 60, "Meal"}, {R, "Power nap", 20, "Nap"},
            {T, "Full mock test", 120, "Mock test"}, {R, "Tea & walk", 20, "Other"}, {T, "Mock analysis", 75, "Revision"},
            {P, "Evening walk", 40, "Exercise"}, {M, "Dinner", 45, "Meal"}, {T, "Vocabulary & revision", 60, "Revision"},
            {P, "Family & free time", 40, "Family time"}, {R, "Wind down", 25, "Other"}}),
        new Def("Board exams (school)", "Wake at 6:00. School, homework, daily study and revision.", 360, new Object[][]{
            {P, "Wake up & freshen up", 30, "Personal"}, {P, "Exercise", 30, "Exercise"}, {M, "Breakfast", 30, "Meal"},
            {T, "School", 360, "School"}, {M, "Lunch", 45, "Meal"}, {R, "Rest", 30, "Other"}, {T, "Homework", 90, "Homework"},
            {R, "Tea & play", 45, "Other"}, {T, "Maths & Science", 120, "Study"}, {M, "Dinner", 45, "Meal"},
            {T, "Revision & writing practice", 90, "Revision"}, {P, "Family time", 40, "Family time"}, {R, "Wind down", 30, "Other"}}),
        new Def("JEE / NEET", "Wake at 5:30. Physics, chemistry, maths or biology, practice and tests.", 330, new Object[][]{
            {P, "Wake up & freshen up", 30, "Personal"}, {P, "Exercise", 30, "Exercise"}, {M, "Breakfast", 30, "Meal"},
            {T, "Physics", 120, "Physics"}, {R, "Short break", 15, "Other"}, {T, "Chemistry", 120, "Chemistry"}, {M, "Lunch", 60, "Meal"},
            {R, "Power nap", 20, "Nap"}, {T, "Maths or Biology", 120, "Maths or Biology"}, {R, "Tea & walk", 20, "Other"},
            {T, "Problem practice", 120, "Practice"}, {T, "Mock test or PYQs", 120, "Mock test"}, {M, "Dinner", 45, "Meal"},
            {T, "Revision & error log", 75, "Revision"}, {P, "Family & free time", 40, "Family time"}, {R, "Wind down", 30, "Other"}}),
        new Def("College", "Wake at 6:30. Classes, self-study, workout, assignments and friends.", 390, new Object[][]{
            {P, "Wake up & freshen up", 30, "Personal"}, {P, "Exercise", 30, "Exercise"}, {M, "Breakfast", 30, "Meal"},
            {P, "Get ready & travel", 30, "Personal"}, {T, "Classes", 300, "Classes"}, {M, "Lunch", 45, "Meal"},
            {T, "Classes or lab", 120, "Classes"}, {R, "Tea break", 20, "Other"}, {T, "Self-study", 90, "Study"},
            {P, "Sports or workout", 60, "Exercise"}, {M, "Dinner", 45, "Meal"}, {T, "Assignments & projects", 90, "Assignments"},
            {P, "Friends & hobbies", 60, "Personal"}, {R, "Wind down", 30, "Other"}}),
        new Def("Working professional", "Wake at 6:00. Deep work, meetings, learning and family time.", 360, new Object[][]{
            {P, "Wake up & freshen up", 30, "Personal"}, {P, "Exercise", 45, "Exercise"}, {M, "Breakfast", 30, "Meal"},
            {P, "Get ready & commute", 60, "Commute"}, {T, "Deep work", 180, "Work"}, {R, "Short break", 15, "Other"},
            {T, "Meetings & email", 120, "Work"}, {M, "Lunch", 45, "Meal"}, {R, "Walk", 15, "Other"}, {T, "Work", 180, "Work"},
            {R, "Tea break", 15, "Other"}, {P, "Commute home", 45, "Commute"}, {T, "Learning or side project", 60, "Study"},
            {M, "Dinner", 45, "Meal"}, {P, "Family time", 60, "Family time"}, {R, "Wind down", 30, "Other"}}),
    };

    public static int count() {
        return ALL.length;
    }

    /** The first this many templates are for an exam (UPSC, SSC / Banking, board exams, JEE / NEET); the rest are for every day. */
    public static final int EXAMS = 4;

    public static boolean isExam(int i) {
        return i >= 0 && i < EXAMS;
    }

    /** The start-of-day time of a template, in minutes after midnight. */
    public static int wake(int i) {
        return ALL[i].wake;
    }

    public static String name(int i) {
        return ALL[i].name;
    }

    public static String about(int i) {
        return ALL[i].about;
    }

    /** The template as a routine: the first slot is the start-of-day alarm, and Sleep takes the rest of the 24 hours. */
    public static Routine build(int i) {
        Def d = ALL[i];
        Routine r = new Routine();
        r.name = d.name;
        r.mode = Routine.MODE_WAKE;
        r.anchor = d.wake;
        int used = 0;
        for (Object[] s : d.slots) {
            Routine.Block b = new Routine.Block((Integer) s[0], (String) s[1], (Integer) s[2]);
            b.sub = (String) s[3];
            r.blocks.add(b);
            used += b.mins;
        }
        Routine.Block sleep = new Routine.Block(Routine.SLEEP, "Sleep", Math.max(1, 1440 - used));
        sleep.sub = "Sleep";
        r.blocks.add(sleep);
        r.blocks.get(0).wake = true;
        r.syncSubs();
        return r;
    }
}
