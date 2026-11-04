package app.dinacharya.wallpaper;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A short list of things to do, in the app's glass sheet (in place of the system's pop-up menu). Lines are added with a number of the
 * caller's choosing; a tap closes the sheet and gives that number. One line can carry a tick for the current choice.
 */
final class GlassMenu {
    private final Context c;
    private final List<Integer> ids = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private CharSequence title;
    private int current = -1;

    GlassMenu(Context c) {
        this.c = c;
    }

    GlassMenu title(CharSequence title) {
        this.title = title;
        return this;
    }

    /** Adds a line (the words are in English; the sheet puts them into the language in use). */
    GlassMenu add(int id, String text) {
        ids.add(id);
        words.add(text);
        return this;
    }

    /** Adds a line that carries the tick when {@code ticked}. */
    GlassMenu add(int id, String text, boolean ticked) {
        if (ticked) current = ids.size();
        return add(id, text);
    }

    void show(final IntConsumer picked) {
        if (ids.isEmpty()) return;
        DBuilder b = new DBuilder(c);
        if (title != null) b.setTitle(title);
        b.setMenu(words.toArray(new CharSequence[0]), current, (d, which) -> {
            if (which >= 0 && which < ids.size()) picked.accept(ids.get(which));
        });
        b.show();
    }
}
