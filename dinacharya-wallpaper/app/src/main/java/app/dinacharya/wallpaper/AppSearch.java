package app.dinacharya.wallpaper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Sorting, searching and the A to Z index of the list of all apps. Uses no Android classes. */
public final class AppSearch {
    private AppSearch() {
    }

    /** Lower case with accents removed, so "Café" is found by "cafe". */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) continue;
            sb.append(ch);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Alphabetical by name, then by package so two apps with the same name keep a steady order. */
    public static void sort(List<AppEntry> apps) {
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                int c = fold(a.label).compareTo(fold(b.label));
                if (c != 0) return c;
                c = a.key.compareTo(b.key);
                return c;
            }
        });
    }

    /** The letter an app is filed under in the index on the right: A to Z, or # for anything else. */
    public static char letter(String label) {
        String f = fold(label == null ? "" : label.trim());
        if (f.isEmpty()) return '#';
        char ch = f.charAt(0);
        return (ch >= 'a' && ch <= 'z') ? Character.toUpperCase(ch) : '#';
    }

    private static boolean wordStart(String name, String q) {
        int from = 0;
        while (true) {
            int i = name.indexOf(q, from);
            if (i < 0) return false;
            if (i == 0 || !Character.isLetterOrDigit(name.charAt(i - 1))) return true;
            from = i + 1;
        }
    }

    /**
     * The apps that match what was typed, best first: names that start with it, then names with a word
     * that starts with it, then names that contain it. An empty search returns everything.
     * {@code all} should already be sorted; that order is kept inside each group.
     */
    public static List<AppEntry> filter(List<AppEntry> all, String query) {
        String q = fold(query).trim();
        if (q.isEmpty()) return new ArrayList<>(all);
        List<AppEntry> g0 = new ArrayList<>();
        List<AppEntry> g1 = new ArrayList<>();
        List<AppEntry> g2 = new ArrayList<>();
        for (AppEntry e : all) {
            String name = fold(e.label);
            if (name.startsWith(q)) g0.add(e);
            else if (wordStart(name, q)) g1.add(e);
            else if (name.contains(q)) g2.add(e);
        }
        List<AppEntry> out = new ArrayList<>(g0.size() + g1.size() + g2.size());
        out.addAll(g0);
        out.addAll(g1);
        out.addAll(g2);
        return out;
    }

    /** The one app to open at once when the search has narrowed down to exactly one, or null. */
    public static AppEntry autoOpen(List<AppEntry> matches, String query, boolean enabled) {
        if (!enabled || fold(query).trim().isEmpty()) return null;
        return matches.size() == 1 ? matches.get(0) : null;
    }
}
