package app.dinacharya.wallpaper;

import android.app.Activity;

/** Turning library mode on (for how long) and off. */
final class LibraryDialog {
    private LibraryDialog() {
    }

    private static final String[] WORDS = {"1 hour", "2 hours", "4 hours", "Until I switch it off"};

    /** Asks how long, then switches it on. */
    static void turnOn(final Activity a, final Runnable changed) {
        new DBuilder(a)
                .setTitle("Library mode")
                .setMessage("Every alarm only vibrates while it is on, with no sound. Apps in locks that include library mode are locked too. How long?")
                .setItems(WORDS, (d, which) -> {
                    LibraryMode lib = Store.library(a);
                    lib.turnOn(System.currentTimeMillis(), LibraryMode.LENGTHS[which]);
                    save(a, lib, changed);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Switches off what was started by hand. */
    static void turnOff(Activity a, Runnable changed) {
        LibraryMode lib = Store.library(a);
        lib.turnOff();
        save(a, lib, changed);
    }

    static void save(Activity a, LibraryMode lib, Runnable changed) {
        Store.saveLibrary(a, lib);
        SlotNow.changed();
        Quiet.refresh(a);
        if (changed != null) changed.run();
    }
}
