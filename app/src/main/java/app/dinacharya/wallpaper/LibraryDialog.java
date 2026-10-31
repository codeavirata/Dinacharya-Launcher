package app.dinacharya.wallpaper;

import android.app.Activity;
import android.widget.Toast;

/** Switching library mode on and off by hand (the switch on Home and in settings). */
final class LibraryDialog {
    private LibraryDialog() {
    }

    /**
     * On: library mode stays on until it is switched off. Off: the session started by hand ends; if library mode is also on by the clock
     * (fixed hours), it stays on until those hours end, and a short note says so.
     */
    static void set(Activity a, boolean on, Runnable changed) {
        LibraryMode lib = Store.library(a);
        long now = System.currentTimeMillis();
        if (on) lib.turnOn(now, 0);
        else lib.turnOff();
        save(a, lib, changed);
        if (!on && lib.on(now, Store.zone())) {
            Lang.toast(a, "Still on by the clock until " + LockRules.untilText(lib.endMs(now, Store.zone()), now, Store.zone(), clock24(a))
                    + ". Turn off the fixed hours to stop it.", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean clock24(Activity a) {
        try {
            return Store.load(a).fmt24;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void save(Activity a, LibraryMode lib, Runnable changed) {
        Store.saveLibrary(a, lib);
        SlotNow.changed();
        Quiet.refresh(a);
        if (changed != null) changed.run();
    }
}
