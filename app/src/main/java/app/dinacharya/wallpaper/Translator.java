package app.dinacharya.wallpaper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the app's English text into another language, using a list of "English text, text in the language" pairs (one language file).
 *
 * The English side is the text as it is written in the program. Text with a changing part has a place marked {1}, {2}...:
 * "Free: up to {1} apps ({2} used)." The pieces that were filled in (a number, a time, the name of a thing) are translated too, each by
 * itself, and put into the translation where its mark is, so the words can come in a different order in another language.
 *
 * A screen's text is often several pieces put together (a sentence, a new line, another sentence). When the whole text has no entry it
 * is cut at line ends, then sentence ends, then colons, bullets and commas, and each piece is translated by itself. Anything that has no
 * entry (a person's own words, the name of an app) stays as it is. Uses no Android classes.
 */
public final class Translator {
    private static final int MAX_DEPTH = 6;

    private static final class Pattern1 {
        final String english;
        final String target;
        final Pattern regex;
        final String prefix;
        final String suffix;
        final List<String> middle = new ArrayList<>();
        final int literal;
        final int marks;
        /**
         * Fewer than four letters of fixed text ("{1} to {2}", "{1} min"), or only a unit after the one mark ("{1} apps", "{1} a day"):
         * only for numbers and times, never for words ("Social apps" is a name, not a count).
         */
        final boolean weak;

        Pattern1(String english, String target) {
            this.english = english;
            this.target = target;
            StringBuilder re = new StringBuilder("^");
            Matcher m = MARK.matcher(english);
            int last = 0;
            int lit = 0;
            int n = 0;
            String firstLit = null;
            String lastLit = "";
            while (m.find()) {
                String piece = english.substring(last, m.start());
                if (n == 0) firstLit = piece;
                else if (!piece.isEmpty()) middle.add(piece);
                re.append(Pattern.quote(piece)).append("([^\\n]+?)");
                lit += piece.length();
                last = m.end();
                lastLit = english.substring(last);
                n++;
            }
            re.append(Pattern.quote(english.substring(last))).append("$");
            lit += english.length() - last;
            this.prefix = firstLit == null ? "" : firstLit;
            this.suffix = lastLit;
            this.marks = n;
            this.literal = lit;
            int letters = 0;
            String fixed = MARK.matcher(english).replaceAll("");
            for (int i = 0; i < fixed.length(); i++) if (Character.isLetter(fixed.charAt(i))) letters++;
            this.weak = letters < 4 || (n == 1 && firstLit != null && firstLit.isEmpty() && UNITS.contains(lastLit.trim().toLowerCase(java.util.Locale.ROOT)));
            this.regex = Pattern.compile(re.toString(), Pattern.DOTALL);
        }

        private static boolean hasDigit(String s) {
            for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
            return false;
        }

        /** The captured pieces if {@code text} fits, or null. */
        String[] match(String text) {
            if (text.length() < literal + marks || !text.startsWith(prefix) || !text.endsWith(suffix)) return null;
            int from = prefix.length();
            for (String mid : middle) {
                int at = text.indexOf(mid, from);
                if (at < 0) return null;
                from = at + mid.length();
            }
            Matcher m = regex.matcher(text);
            if (!m.matches()) return null;
            String[] out = new String[marks];
            for (int i = 0; i < marks; i++) {
                out[i] = m.group(i + 1);
                // A piece that holds the end of a sentence belongs to a longer text: let that be cut into its sentences instead.
                if (out[i].contains(". ") || out[i].contains("! ") || out[i].contains("? ")) return null;
                // a count or a time is a number with a unit: a piece with a colon and a space is two parts of a longer text
                if (weak && (!hasDigit(out[i]) || out[i].contains(": "))) return null;
            }
            return out;
        }
    }

    private static final Pattern MARK = Pattern.compile("\\{(\\d+)\\}");

    /** What can follow a single mark so that the text is a count ("3 apps", "7 days left") and not somebody's words ("Social apps"). */
    private static final java.util.Set<String> UNITS = new java.util.HashSet<>(java.util.Arrays.asList(
            "time", "times", "app", "apps", "video", "videos", "change", "changes", "late", "left", "day", "days", "hour", "hours",
            "minute", "minutes", "week", "weeks", "slot", "slots", "goal", "goals", "a day", "min a day", "h a day", "a week"));

    private final Map<String, String> exact = new HashMap<>();
    private final Map<String, String> lower = new HashMap<>();
    private final List<Pattern1> patterns = new ArrayList<>();
    private final Map<String, String> cache = new HashMap<>();

    public Translator(List<String[]> pairs) {
        for (String[] p : pairs) {
            if (p == null || p.length < 2) continue;
            String en = p[0].trim();
            String to = p[1].trim();
            if (en.isEmpty() || to.isEmpty()) continue;
            if (MARK.matcher(en).find()) patterns.add(new Pattern1(en, to));
            else {
                exact.putIfAbsent(en, to);
                lower.putIfAbsent(en.toLowerCase(java.util.Locale.ROOT), to);
            }
        }
        // the most specific first: the one with the most fixed words wins
        Collections.sort(patterns, (a, b) -> b.literal != a.literal ? Integer.compare(b.literal, a.literal) : Integer.compare(a.marks, b.marks));
    }

    /** Number of entries (exact text and texts with marks). */
    public int size() {
        return exact.size() + patterns.size();
    }

    // ------------------------------------------------------------------ reading a language file

    /** Reads lines of "English text TAB translation". {@code \n}, {@code \t} and {@code \\} are written as such. Lines starting with # and bad lines are skipped. */
    public static List<String[]> read(Reader in) throws IOException {
        List<String[]> out = new ArrayList<>();
        BufferedReader r = in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in);
        String line;
        boolean first = true;
        while ((line = r.readLine()) != null) {
            if (first && line.startsWith("﻿")) line = line.substring(1);
            first = false;
            if (line.isEmpty() || line.startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab == line.length() - 1) continue;
            out.add(new String[]{unescape(line.substring(0, tab)), unescape(line.substring(tab + 1))});
        }
        return out;
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\t') sb.append("\\t");
            else if (c == '\r') sb.append("\\r");
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n == 'r' ? '\r' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The marks ({1}, {2}...) of a text, in order of appearance, as a sorted list of numbers written out; for checking a pair. */
    public static String marksOf(String s) {
        List<Integer> nums = new ArrayList<>();
        Matcher m = MARK.matcher(s);
        while (m.find()) nums.add(Integer.parseInt(m.group(1)));
        Collections.sort(nums);
        return nums.toString();
    }

    // ------------------------------------------------------------------ translating

    /** The text in this language, or the text itself where there is nothing to say it in. */
    public synchronized String translate(String text) {
        if (text == null || text.isEmpty()) return text;
        String hit = cache.get(text);
        if (hit != null) return hit;
        String out = tr(text, 0);
        if (cache.size() > 6000) cache.clear();
        cache.put(text, out);
        return out;
    }

    private String tr(String text, int depth) {
        if (depth > MAX_DEPTH || text.isEmpty()) return text;
        int a = 0, b = text.length();
        while (a < b && Character.isWhitespace(text.charAt(a))) a++;
        while (b > a && Character.isWhitespace(text.charAt(b - 1))) b--;
        if (a == b) return text;
        String core = text.substring(a, b);
        String done = whole(core, depth);
        if (done == null) done = cut(core, 0, depth);
        return text.substring(0, a) + done + text.substring(b);
    }

    /** The translation of the whole text if there is an entry for all of it, else null. */
    private String whole(String core, int depth) {
        String t = exact.get(core);
        if (t != null) return t;
        for (Pattern1 p : patterns) {
            String[] got = p.match(core);
            if (got == null) continue;
            String out = p.target;
            // fill the marks from the last to the first so {10} is not taken for {1}
            for (int i = got.length; i >= 1; i--) out = out.replace("{" + i + "}", tr(got[i - 1], depth + 1));
            return out;
        }
        // the start of a sentence in lower case where the entry has a capital (a name put in the middle of a sentence), and ALL CAPS
        char c0 = core.charAt(0);
        if (Character.isLowerCase(c0)) {
            String t2 = exact.get(Character.toUpperCase(c0) + core.substring(1));
            if (t2 != null) return t2;
        }
        String low = lower.get(core.toLowerCase(java.util.Locale.ROOT));
        if (low != null && core.equals(core.toUpperCase(java.util.Locale.ROOT)) && !core.equals(core.toLowerCase(java.util.Locale.ROOT))) return low;
        return null;
    }

    /** Where texts are cut, from the biggest to the smallest piece. */
    private static final Pattern[] CUTS = {
            Pattern.compile("(\\n+)"),
            Pattern.compile("(?<=[.!?])(\\s+)"),
            Pattern.compile("(\\s*[•·|;]\\s*|\\s+[–—]\\s+|:\\s+)"),
            Pattern.compile("(,\\s+)"),
            Pattern.compile("(\\s*[()]\\s*)"),
    };

    private String cut(String core, int level, int depth) {
        for (int l = level; l < CUTS.length; l++) {
            Matcher m = CUTS[l].matcher(core);
            List<String> pieces = new ArrayList<>();
            List<String> seps = new ArrayList<>();
            int last = 0;
            while (m.find()) {
                pieces.add(core.substring(last, m.start()));
                seps.add(m.group(1));
                last = m.end();
            }
            if (pieces.isEmpty()) continue;
            pieces.add(core.substring(last));
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            for (int i = 0; i < pieces.size(); i++) {
                String p = pieces.get(i);
                String t = p;
                int x = 0, y = p.length();
                while (x < y && Character.isWhitespace(p.charAt(x))) x++;
                while (y > x && Character.isWhitespace(p.charAt(y - 1))) y--;
                if (x < y) {
                    String inner = p.substring(x, y);
                    String w = whole(inner, depth);
                    if (w == null) w = cut(inner, l + 1, depth);
                    if (!w.equals(inner)) changed = true;
                    t = p.substring(0, x) + w + p.substring(y);
                }
                sb.append(t);
                if (i < seps.size()) sb.append(seps.get(i));
            }
            return changed ? sb.toString() : core;
        }
        return core;
    }
}
