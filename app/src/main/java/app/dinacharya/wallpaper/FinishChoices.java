package app.dinacharya.wallpaper;

/**
 * What "Finish" asks when the running slot is done before its time. Which of the three answers are offered depends on the time that is
 * left and on the slot that comes next:
 * <ul>
 *   <li>more than 30 minutes left and the next slot is not a rest: Rest, Start next, New activity;</li>
 *   <li>more than 30 minutes left and the next slot is a rest: Rest, Start next;</li>
 *   <li>(a slot that began early gets no "New activity" either);</li>
 *   <li>30 minutes or less left (or the slot has nothing to hand over, or the next is sleep): only a confirm, so an accidental tap can be
 *       taken back.</li>
 * </ul>
 * Uses no Android classes.
 */
public final class FinishChoices {
    /** More than this many minutes left is needed for the three answers. */
    public static final int MIN_LEFT = 30;

    public final boolean rest;
    public final boolean startNext;
    public final boolean newActivity;

    private FinishChoices(boolean rest, boolean startNext, boolean newActivity) {
        this.rest = rest;
        this.startNext = startNext;
        this.newActivity = newActivity;
    }

    /** Only the confirm button is shown. */
    public boolean confirmOnly() {
        return !rest && !newActivity;
    }

    public static FinishChoices of(EarlyFinish.Offer o) {
        if (o == null) return new FinishChoices(false, true, false);
        // going to sleep early and waking early have their own single question
        if (o.kind != EarlyFinish.FINISH || o.curIsSleep || o.carried) return new FinishChoices(false, true, false);
        if (o.roomMin <= MIN_LEFT) return new FinishChoices(false, true, false);
        boolean nextIsRest = o.kindNext == Routine.REST;
        // a slot that began early cannot be given a new activity: the saved plan has it starting later, so the new activity could not be placed
        return new FinishChoices(o.canWait(), true, !nextIsRest && !o.beganEarly && o.adjacent && maxFromNext(o, 0) >= 0 && fitsAny(o));
    }

    /** Whether some length of a new activity can be had at all (the longest, with everything the next slot can give, reaches the smallest limit). */
    private static boolean fitsAny(EarlyFinish.Offer o) {
        return maxLength(o) >= SlotAdjust.OTHER_MIN;
    }

    /** The most minutes of the leftover time a new activity can use (whole steps). */
    public static int maxFromLeft(EarlyFinish.Offer o) {
        return Math.max(0, (o.roomMin / SlotAdjust.STEP) * SlotAdjust.STEP);
    }

    /**
     * The most minutes a new activity can also take from the next slot when it uses {@code fromLeft} of the leftover time: the next slot
     * keeps its own limit, and what is left over of the leftover time goes to it. Whole steps; never below 0.
     */
    public static int maxFromNext(EarlyFinish.Offer o, int fromLeft) {
        int floor = SlotAdjust.floorOf(o.kindNext, o.nextMins);
        int v = o.nextMins + (o.roomMin - fromLeft) - floor;
        return Math.max(0, (v / SlotAdjust.STEP) * SlotAdjust.STEP);
    }

    /** The longest a new activity can last. */
    public static int maxLength(EarlyFinish.Offer o) {
        return maxFromLeft(o) + maxFromNext(o, maxFromLeft(o));
    }

    /** The fewest minutes a new activity of this type may last. */
    public static int minLength(int kind) {
        return Math.max(SlotAdjust.STEP, SlotAdjust.minFor(kind == Routine.SLEEP ? Routine.REST : kind));
    }
}
