package app.dinacharya.wallpaper;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of dropdowns of which only one is open at a time: opening one closes the one that was open, so a screen full of details stays
 * short and calm. The rule itself is {@link OneOpen}.
 */
final class Accordion {
    /** Anything that opens and closes. */
    interface Foldable {
        boolean isOpen();

        void setOpen(boolean on, boolean remember);

        /** Told when the person taps it (not when the program opens or closes it). */
        void onToggled(Expander.Toggled l);
    }

    private final List<Foldable> parts = new ArrayList<>();
    private final OneOpen state = new OneOpen();
    private Runnable changed;

    /** Told after the person opens or closes a dropdown (not when the program does), so a screen can remember which one was open. */
    void onChanged(Runnable r) {
        changed = r;
    }

    /** The number of the dropdown that is open (in the order they were added), or -1 when all are closed. */
    int open() {
        return state.open();
    }

    /** Adds a dropdown to the row. It starts closed. */
    <T extends Foldable> T add(final T part) {
        final int idx = parts.size();
        parts.add(part);
        part.setOpen(false, false);
        part.onToggled(open -> {
            // the dropdown has already changed itself; the row decides what everything else does
            if (open) state.set(idx);
            else if (state.isOpen(idx)) state.closeAll();
            apply();
            if (changed != null) changed.run();
        });
        return part;
    }

    /** Adds a dropdown made with {@link Expander}. */
    Expander.Part add(final Expander.Part p) {
        add(new Foldable() {
            @Override
            public boolean isOpen() {
                return p.isOpen();
            }

            @Override
            public void setOpen(boolean on, boolean remember) {
                p.setOpen(on, remember);
            }

            @Override
            public void onToggled(Expander.Toggled l) {
                p.onToggled(l);
            }
        });
        return p;
    }

    /** Opens one dropdown (and closes the rest); a number that is not there closes them all. */
    void show(int index) {
        state.set(index >= 0 && index < parts.size() ? index : -1);
        apply();
    }

    private void apply() {
        for (int i = 0; i < parts.size(); i++) {
            Foldable p = parts.get(i);
            boolean should = state.isOpen(i);
            if (p.isOpen() != should) p.setOpen(should, false);
        }
    }
}
