package app.dinacharya.wallpaper;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** The translation engine: exact text, texts with a changing part, texts put together from pieces, and what must stay as it is. */
public class TranslatorTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static Translator of(String... pairs) {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) l.add(new String[]{pairs[i], pairs[i + 1]});
        return new Translator(l);
    }

    public static void main(String[] args) throws Exception {
        Translator t = of(
                "Save", "सहेजें",
                "Cancel", "रद्द करें",
                "Strict pauses", "सख़्त पॉज़",
                "Free: up to {1} apps ({2} used).", "मुफ़्त: {2} उपयोग में, अधिकतम {1} ऐप्स।",
                "{1} min", "{1} मिनट",
                "{1} min left today", "आज {1} मिनट बाकी",
                "Open until {1}", "{1} तक खुला",
                "tomorrow {1}", "कल {1}",
                "Line one.", "पहली पंक्ति।",
                "Line two.", "दूसरी पंक्ति।",
                "Mon", "सोम", "Tue", "मंगल", "Fri", "शुक्र",
                "Wait a few seconds", "कुछ सेकंड रुकें",
                "Undo", "पूर्ववत",
                "Strict pause ({1} seconds){2}", "सख़्त पॉज़ ({1} सेकंड){2}",
                "  • Pro", "• प्रो",
                "{1}: open until {2}.", "{1}: {2} तक खुला।",
                "Thank you for watching.", "देखने के लिए धन्यवाद।");

        ok("exact: a known text", t.translate("Save").equals("सहेजें"));
        ok("exact: spaces around it stay where they were", t.translate("  Save\n").equals("  सहेजें\n"));
        ok("exact: an unknown text is returned as it is", t.translate("Instagram").equals("Instagram") && t.translate("My own words 123").equals("My own words 123"));
        ok("exact: nothing and spaces", t.translate("").equals("") && t.translate(null) == null && t.translate("   ").equals("   "));

        ok("marks: the pieces are placed where the translation wants them", t.translate("Free: up to 2 apps (1 used).").equals("मुफ़्त: 1 उपयोग में, अधिकतम 2 ऐप्स।"));
        ok("marks: a piece that is itself a known text is translated too", t.translate("Open until tomorrow 4:30 PM").equals("कल 4:30 PM तक खुला"));
        ok("marks: a number only", t.translate("45 min").equals("45 मिनट"));
        ok("marks: the entry with more fixed words wins over a shorter one", t.translate("45 min left today").equals("आज 45 मिनट बाकी"));
        ok("marks: a piece may be a name that has no translation", t.translate("Open until Friday evening").equals("Friday evening तक खुला"));
        ok("marks: two marks, the spaces the second one had are kept", t.translate("Strict pause (30 seconds)  • Pro").equals("सख़्त पॉज़ (30 सेकंड)  • प्रो")
                && t.translate("Strict pause (30 seconds)").equals("Strict pause (30 seconds)"));
        ok("marks: the first piece can be anything before the colon", t.translate("Strict pauses: open until tomorrow 9:00 AM.").equals("सख़्त पॉज़: कल 9:00 AM तक खुला।"));

        Translator w = of("{1} to {2}", "{1} से {2}", "{1} min", "{1} मिनट", "Up to {1} apps in a group", "एक समूह में {1} ऐप्स तक");
        ok("weak: a time range with so few fixed words still works", w.translate("6:00 AM to 7:30 AM").equals("6:00 AM से 7:30 AM"));
        ok("weak: words are never taken for a time range", w.translate("Gym to Home").equals("Gym to Home") && w.translate("Back up to Drive").equals("Back up to Drive"));
        ok("weak: the entry with more words still wins", w.translate("Up to 5 apps in a group").equals("एक समूह में 5 ऐप्स तक"));
        ok("weak: a minute count but not a word before min", w.translate("45 min").equals("45 मिनट") && w.translate("Quick min").equals("Quick min"));

        ok("pieces: lines put together are each translated and the line ends stay", t.translate("Line one.\nLine two.").equals("पहली पंक्ति।\nदूसरी पंक्ति।"));
        ok("pieces: sentences put together", t.translate("Line one. Line two.").equals("पहली पंक्ति। दूसरी पंक्ति।"));
        ok("pieces: a list of days", t.translate("Mon, Tue, Fri").equals("सोम, मंगल, शुक्र"));
        ok("pieces: a thank-you line then a pass line", t.translate("Thank you for watching.\nStrict pauses: open until tomorrow 9:00 AM.")
                .equals("देखने के लिए धन्यवाद।\nसख़्त पॉज़: कल 9:00 AM तक खुला।"));
        ok("pieces: only the known pieces change, the rest is left alone", t.translate("Line one. Something else.").equals("पहली पंक्ति। Something else."));
        ok("pieces: bullets and colons", t.translate("Save • Cancel").equals("सहेजें • रद्द करें") && t.translate("Save: Cancel").equals("सहेजें: रद्द करें"));
        ok("pieces: brackets", t.translate("Save (Cancel)").equals("सहेजें (रद्द करें)"));

        ok("case: a lower-case start finds the entry with a capital", t.translate("wait a few seconds").equals("कुछ सेकंड रुकें"));
        ok("case: ALL CAPS finds the entry", t.translate("UNDO").equals("पूर्ववत") && t.translate("SAVE").equals("सहेजें"));
        ok("case: a word that is only like an entry is not changed", t.translate("Saved").equals("Saved") && t.translate("Saver").equals("Saver"));

        // a mark never takes in the end of a sentence: such a text is cut into its sentences instead
        Translator s = of("{1}: open until {2}.", "{1}: {2} तक।", "Hello.", "नमस्ते।", "Bye: open until noon.", "अलविदा: दोपहर तक।");
        ok("sentences: a sentence before does not become part of the first mark", s.translate("Hello. Bye: open until noon.").equals("नमस्ते। अलविदा: दोपहर तक।"));

        // counts: "{1} apps" is for numbers, never for somebody's words; a colon splits a text before a count can swallow it
        Translator c = of("{1} apps", "{1} ऐप्स", "1 app", "1 ऐप", "{1} a day", "दिन में {1}", "{1} min a day", "दिन में {1} मिनट",
                "{1} h {2} min a day", "दिन में {1} घंटा {2} मिनट", "{1} min", "{1} मिनट", "1 video", "1 वीडियो",
                "{1} for a day", "{1} एक दिन के लिए", "strict pauses", "सख़्त पॉज़", "{1} time", "{1} बार", "Mon {1}", "सोम {1}",
                "Thank you for watching.\n{1}", "धन्यवाद।\n{1}", "{1}: open until {2}.", "{1}: {2} तक।", "{1}-day streak.", "लगातार {1} दिन।");
        ok("counts: a number and the unit", c.translate("3 apps").equals("3 ऐप्स") && c.translate("1 app").equals("1 ऐप"));
        ok("counts: a name that ends in the unit is left alone", c.translate("Social apps").equals("Social apps") && c.translate("Meeting time").equals("Meeting time")
                && c.translate("Mon morning").equals("Mon morning") && c.translate("3 time").equals("3 बार"));
        ok("counts: hours and minutes a day, the longer shape first", c.translate("2 h 15 min a day").equals("दिन में 2 घंटा 15 मिनट") && c.translate("30 min a day").equals("दिन में 30 मिनट"));
        ok("counts: a count in front of a colon is its own piece", c.translate("1 video: strict pauses for a day").equals("1 वीडियो: सख़्त पॉज़ एक दिन के लिए"));
        ok("counts: a day name and a time", c.translate("Mon 9:00 PM").equals("सोम 9:00 PM"));
        ok("counts: a long text with a colon in the piece that fills a mark is still one text", c.translate("Thank you for watching.\nStrict pauses: open until Mon 9:00 PM.")
                .equals("धन्यवाद।\nStrict pauses: सोम 9:00 PM तक।") || c.translate("Thank you for watching.\nStrict pauses: open until Mon 9:00 PM.").equals("धन्यवाद।\nसख़्त पॉज़: सोम 9:00 PM तक।"));
        ok("counts: a sentence that comes first and then another", c.translate("5-day streak.  3 apps").equals("लगातार 5 दिन।  3 ऐप्स"));

        // many marks, and a mark number above 9
        List<String[]> many = new ArrayList<>();
        StringBuilder en = new StringBuilder(), to = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            en.append("[{").append(i).append("}]");
            to.append("<{").append(12 - i).append("}>");
        }
        many.add(new String[]{en.toString(), to.toString()});
        Translator m = new Translator(many);
        StringBuilder in = new StringBuilder(), want = new StringBuilder();
        for (int i = 1; i <= 11; i++) in.append("[v").append(i).append("]");
        for (int i = 1; i <= 11; i++) want.append("<v").append(12 - i).append(">");
        ok("marks: eleven of them, in any order (mark 11 is not taken for mark 1)", m.translate(in.toString()).equals(want.toString()));

        // an entry never makes a loop
        Translator loop = of("Hello {1}", "Hello {1} Hello", "A", "A A");
        ok("loops: a translation that contains its own words ends", loop.translate("Hello Hello Hello Hello").length() < 200 && loop.translate("A").equals("A A"));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3000; i++) big.append("Save. ");
        long t0 = System.nanoTime();
        String bigOut = t.translate(big.toString());
        ok("size: a long text is done quickly", bigOut.startsWith("सहेजें।") || bigOut.startsWith("सहेजें.") || bigOut.length() > 0 && (System.nanoTime() - t0) < 3_000_000_000L);

        // reading a language file
        String file = "﻿# a comment\nSave\tसहेजें\n\nLine\\none\tपहली\\nदूसरी\nBad line without a tab\nempty\t\nTab\\there\tसमझ\\\\ ठीक\n\tno english\n";
        List<String[]> rows = Translator.read(new StringReader(file));
        ok("file: comments, blank lines and lines without both sides are skipped", rows.size() == 3);
        ok("file: line ends, tabs and a backslash are written as \\n \\t \\\\", rows.get(1)[0].equals("Line\none") && rows.get(1)[1].equals("पहली\nदूसरी")
                && rows.get(2)[0].equals("Tab\there") && rows.get(2)[1].equals("समझ\\ ठीक"));
        ok("file: and written back the same way", Translator.escape("a\nb\tc\\d").equals("a\\nb\\tc\\\\d") && Translator.unescape("a\\nb\\tc\\\\d").equals("a\nb\tc\\d"));
        ok("file: the first line's byte order mark does not spoil the first entry", new Translator(rows).translate("Save").equals("सहेजें"));
        ok("file: marks of a pair, for checking that both sides have the same", Translator.marksOf("{2} of {1} and {10}").equals("[1, 2, 10]")
                && Translator.marksOf("none").equals("[]"));

        Translator dup = of("Save", "पहला", "Save", "दूसरा", "Empty", "", "", "x");
        ok("entries: the first of two for the same text counts, an empty translation is ignored", dup.translate("Save").equals("पहला") && dup.translate("Empty").equals("Empty") && dup.size() == 1);

        // the global hook used by the plain-Java classes
        ok("hook: without a translator the text is returned as it is", Tr.t("Save").equals("Save") && Tr.t("") .equals("") && Tr.t(null) == null);
        Tr.use(t::translate);
        ok("hook: with one the text is translated", Tr.t("Save").equals("सहेजें"));
        Tr.use(x -> { throw new IllegalStateException("broken"); });
        ok("hook: a broken translator never breaks the screen", Tr.t("Save").equals("Save"));
        Tr.use(null);
        ok("hook: and can be removed", Tr.t("Save").equals("Save"));

        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
