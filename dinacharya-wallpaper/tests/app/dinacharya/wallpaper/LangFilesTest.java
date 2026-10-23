package app.dinacharya.wallpaper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The language files that ship in the app (assets/lang): every one loads, its pairs keep the same marks as the English, digits are 0-9,
 * the list matches the languages the picker offers, and the sentences that are put together from pieces in the program come out in the
 * language with no English left in them. Run from the tests folder.
 */
public class LangFilesTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static final String DIR = "../app/src/main/assets/lang";
    static final String[] CODES = {"hi", "mr", "gu", "bn", "ta", "te"};
    /** English words that stay as they are in every language: names, and the apps used in the samples. */
    static final Set<String> KEEP = new HashSet<>(Arrays.asList("Dinacharya", "Launcher", "Pro", "Google", "Play", "Drive", "Android", "Chrome", "Maps",
            "YouTube", "Pixel", "Wi", "Fi", "PDF", "Amit", "Singh", "Yoga"));
    static final Pattern WORD = Pattern.compile("[A-Za-z]{3,}");
    static final Pattern MARK = Pattern.compile("\\{\\d+\\}");

    /** The sentences the program puts together from pieces (and some it shows whole), each as the person sees it in English. */
    static final String[] SAMPLES = {
            // the videos, in the buttons and in the dialog
            "1 video: strict pauses for a day",
            "2 videos: strict pauses for 3 days",
            "3 videos: everything in Pauses (strict pauses for 3 days, the rest for a day)",
            "Watch videos: open strict pauses free",
            "Keep strict pauses for 3 days: watch 1 more video",
            "Strict pauses is open now.\n\nWatch one more video to keep strict pauses open for 3 days.\n\nOr stop here and keep what you have.",
            "Strict pauses is open now.\n\nWatch 2 more videos to open Pauses for a day.\n\nOr stop here and keep what you have.",
            "Strict pauses is open now.\n\nWatch one more video to open Pauses for a day.\n\nOr stop here and keep what you have.",
            "Thank you for watching.\nStrict pauses: open until Tue 6:00 PM.",
            "Strict pauses: open until tomorrow 9:00 AM.",
            "A video is already on its way.",
            "Getting the next video…",
            // groups and locks
            "3 apps: Chrome, Maps, YouTube, and 2 more",
            "1 app: Chrome",
            "3 apps  ·  Locked until 6:00 AM  ·  strict  ·  needs Pro",
            "2 apps  ·  Next: Mon 9:00 PM  ·  only 1 held, needs Pro",
            "1 app  ·  Not scheduled  ·  strict part off",
            // pauses
            "Strict pause, 10 s",
            "Short pause, 5 s  ·  breathe",
            "You can open it in 5 s",
            "30 min a day",
            "1 h a day",
            "2 h 15 min a day",
            "No daily limit",
            "Task time",
            "Sleep time",
            // reports and goals
            "2h 05m of 3h goal ✓",
            "45 min of 1h 30m goal",
            "1h of 2h planned",
            "You asked for more time 3 times (2h 15m in all).",
            "You asked for more time 1 time (5 min in all).",
            "🔥 5-day streak.  Planned today: 2h.",
            "🔥 5-day streak.  Your plan has 1h of it today.",
            "Goal set: Yoga, 2h a day.",
            "3 older days are saved on this phone. Opening days older than the last 7 days is part of Pro.",
            "1 older day is saved on this phone. Opening days older than the last 7 days is part of Pro.",
            "Used on 3 days. Tap a day to change.",
            "Used on 1 day. Tap a day to change.",
            "Sunset colours, selected",
            "Teal colours",
            "2h 15m left",
            "5 min late",
            // the Pro page
            "Pro is one payment through Google Play, with no subscription. ",
            "Free: the home screen, alarms, today's report and the last three days, two paused apps and two locked apps. Pro is one payment of ₹99 through Google Play, with no subscription. It adds every saved day and the weekly view, strict and playful pauses, daily limits, groups of locked apps and strict locks, weekday and weekend routines, goals, every colour look, and no ads.",
            " Or watch short videos to open some of these for a while: 1 video opens just ONE of them for a day, 2 videos for 3 days, 3 videos the whole group it is in. Tap “Watch videos” wherever you see “Part of Pro”, or on the Pro screen. What you set up with it goes back to the free version when the time is up.",
            "Looks: Sunset",
            "Ocean look",
            // lists and settings
            "Pause during",
            "Open the keyboard in the list of all apps",
            "You have kept “Yoga” for 5 days in a row. It is a good place for your hardest work.",
    };
    /** Text that is the person's own, or a name, and must stay as it is. */
    static final String[] STAYS = {"Social apps", "Meeting time", "Study room", "Time left", "Chrome", "Watch a video", "Morning run"};

    static Translator load(String code) throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream(new File(DIR, code + ".tsv")), StandardCharsets.UTF_8)) {
            return new Translator(Translator.read(r));
        }
    }

    static boolean hasIndicDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x0966 && c <= 0x096F) || (c >= 0x09E6 && c <= 0x09EF) || (c >= 0x0AE6 && c <= 0x0AEF) || (c >= 0x0BE6 && c <= 0x0BEF)
                    || (c >= 0x0C66 && c <= 0x0C6F)) return true;
        }
        return false;
    }

    static List<String> leftover(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(s);
        while (m.find()) if (!KEEP.contains(m.group())) out.add(m.group());
        return out;
    }

    public static void main(String[] args) throws Exception {
        // the picker offers exactly the languages that have a file
        String lang = new String(Files.readAllBytes(new File("../app/src/main/java/app/dinacharya/wallpaper/Lang.java").toPath()), StandardCharsets.UTF_8);
        Matcher lm = Pattern.compile("\\{\"([a-z]{2})\", \"[^\"]+\"\\}").matcher(lang);
        Set<String> offered = new HashSet<>();
        while (lm.find()) if (!lm.group(1).equals("en")) offered.add(lm.group(1));
        ok("files: the picker offers " + offered, offered.equals(new HashSet<>(Arrays.asList(CODES))));

        for (String code : CODES) {
            File f = new File(DIR, code + ".tsv");
            ok(code + ": the file is there", f.isFile());
            List<String[]> pairs;
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                pairs = Translator.read(r);
            }
            ok(code + ": has the whole list (" + pairs.size() + " pairs)", pairs.size() > 1200);
            int badMarks = 0, badDigits = 0, same = 0, empty = 0;
            Map<String, String> seen = new HashMap<>();
            int clash = 0;
            for (String[] p : pairs) {
                if (!Translator.marksOf(p[0]).equals(Translator.marksOf(p[1]))) badMarks++;
                if (hasIndicDigit(p[1])) badDigits++;
                if (p[1].trim().isEmpty()) empty++;
                String prev = seen.put(p[0].trim(), p[1].trim());
                if (prev != null && !prev.equals(p[1].trim())) clash++;
            }
            ok(code + ": every pair keeps its marks", badMarks == 0);
            ok(code + ": digits are 0-9", badDigits == 0);
            ok(code + ": no empty translation", empty == 0);
            ok(code + ": one translation for each English text", clash == 0);

            Translator t = load(code);
            ok(code + ": 'Save' is translated", !t.translate("Save").equals("Save") && !t.translate("Save").isEmpty());
            int shown = 0;
            for (String s : SAMPLES) {
                String out = t.translate(s);
                List<String> left = leftover(out);
                if (!left.isEmpty() || out.equals(s)) {
                    ok(code + ": no English left in: " + s.replace("\n", "\\n").substring(0, Math.min(60, s.replace("\n", "\\n").length())) + "  -> " + left, false);
                    if (++shown > 5) break;
                }
            }
            if (shown == 0) ok(code + ": " + SAMPLES.length + " sentences come out with no English left", true);
            boolean stays = true;
            for (String s : STAYS) if (!t.translate(s).equals(s)) { stays = false; System.out.println("  changed: " + s + " -> " + t.translate(s)); }
            ok(code + ": names and the person's own words stay as they are", stays);
            String v = t.translate("3 videos: strict pauses for 3 days");
            ok(code + ": numbers stay 0-9 in a translated sentence", v.contains("3") && !hasIndicDigit(v));
        }
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
