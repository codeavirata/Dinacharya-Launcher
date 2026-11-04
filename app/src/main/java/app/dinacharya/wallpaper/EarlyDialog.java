package app.dinacharya.wallpaper;

/**
 * "Finish": the slot going on is done before its time. The questions are glass sheets ({@link SlotSheets#finish}); what each answer does
 * is here: start the next slot now, take a rest first (the next slot starts after it) or start it after a while. The next slot keeps its
 * planned end, so it gets the time that was left.
 */
final class EarlyDialog {
    private EarlyDialog() {
    }

    private static String clock(long ms, boolean h24) {
        return SlotSheets.clock(ms, h24);
    }

    static void show(final MainActivity a, final EarlyFinish.Offer o, final Runnable changed) {
        SlotSheets.finish(a, o, changed);
    }

    /** Does what was chosen, says so, and lets the screen behind redraw. */
    static void run(android.app.Activity a, EarlyFinish.Offer o, int how, int minutes, Runnable changed) {
        long begin = SlotActions.finishEarly(a, how, minutes);
        if (begin < 0) {
            Lang.toast(a, "That cannot be done now", android.widget.Toast.LENGTH_LONG).show();
        } else {
            boolean h24 = Store.load(a).fmt24;
            String next = o.titleNext.isEmpty() ? "The next slot" : o.titleNext;
            String msg;
            if (how == SlotActions.EARLY_NOW) msg = "“" + next + "” has started";
            else if (how == SlotActions.EARLY_BREAK) msg = "Enjoy your break. “" + next + "” goes on at " + clock(begin, h24);
            else msg = "“" + next + "” starts at " + clock(begin, h24);
            Lang.toast(a, msg, android.widget.Toast.LENGTH_LONG).show();
        }
        if (a instanceof MainActivity) ((MainActivity) a).previewChanged();
        if (changed != null) changed.run();
    }
}
