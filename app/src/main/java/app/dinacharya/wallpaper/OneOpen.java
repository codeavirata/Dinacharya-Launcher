package app.dinacharya.wallpaper;

/**
 * Which one of a row of dropdowns is open: tapping a closed one opens it and closes the one that was open; tapping the open one closes it.
 * At most one is ever open. Uses no Android classes.
 */
public final class OneOpen {
    private int open = -1;

    /** The number of the open dropdown, or -1 when all are closed. */
    public int open() {
        return open;
    }

    public boolean isOpen(int i) {
        return i >= 0 && i == open;
    }

    /** The person tapped dropdown {@code i}. Returns the one that is open now (-1 for none). A number below 0 changes nothing. */
    public int tap(int i) {
        if (i < 0) return open;
        open = open == i ? -1 : i;
        return open;
    }

    /** Opens {@code i} for certain (a number below 0 closes all). */
    public void set(int i) {
        open = i < 0 ? -1 : i;
    }

    public void closeAll() {
        open = -1;
    }
}
