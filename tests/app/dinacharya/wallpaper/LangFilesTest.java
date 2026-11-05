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
            // build 26: locks that hold wherever an app is opened from (Recents, App info, a notification), and asking for what Android needs
            "Make your locks hold everywhere",
            "Your locks hold only on the home screen",
            "An app can still be opened from Recents or App info. Allow what Android asks for, and a lock holds wherever the app is opened from.",
            "Hold them everywhere",
            "Allow display over other apps?",
            "To hold a lock when an app is opened from Recents, App info or a notification, Dinacharya Launcher shows its lock screen in front of that app. Android asks you to allow this.\n\nNext, Android's settings open. Find Dinacharya Launcher in the list and switch it on. You can switch it off there at any time.",
            "To hold a lock when an app is opened from Recents, App info or a notification, it also needs to see which app is in front. Only the name of that app is compared with your own list of locked apps. Nothing is saved or sent.",
            "On. Nothing is locked or paused yet, so nothing is being watched.",
            "Not holding yet: allow Usage access below.",
            "Not holding yet: allow Display over other apps below.",
            "Not holding yet: allow the two things below.",
            "Needs your permission: locks hold only from this home screen",
            "On: nothing is locked or paused yet",
            "A lock that only works when you open an app from this home screen is easy to get around: open the app from Recents or App info instead. With the strong lock on, Dinacharya Launcher also holds an app opened from Recents, App info, a notification, a widget or another home app, and an app that is already open when a lock starts. It starts by itself as soon as something is locked or paused. The phone, Settings, the keyboard and system screens are never blocked.",
            "The strong lock is on, so a lock also holds an app opened from Recents, App info, a notification, a widget or another home app, and one that is open when the lock starts.",
            "For now a lock holds only when you open an app from this home screen. Allow the two things in the Strong lock card below and it also holds an app opened from Recents, App info, a notification, a widget or another home app, and one that is open when the lock starts.",
            "Locks work when you open an app from this home screen. They cannot stop an app that is already open, or one opened from Recents, a notification or another home app. The strong lock below does.",
            "Without strict, you can still break a lock: wait 30 seconds and type a sentence. A strict lock cannot be broken, and cannot be changed while it holds. For now locks hold only when you open an app from this home screen. Allow the two things in the Strong lock card in the home settings and they hold everywhere.",
            "Pick apps that pull you away. When you open one from this home screen, it waits a few seconds first, so you can go back or carry on. It never locks you out: Open anyway is always there. To hold them whichever way they open, allow the two things in the Strong lock card below.",
            "It shows a small notification while it runs. It looks only at the name of the app in front, on this phone, and keeps and sends nothing. If your phone stops background apps to save battery and the lock stops holding, allow Dinacharya Launcher to run in the background in the phone's battery settings.",
            // build 25: skipping a slot, Early sleep and Early wake, the wallpaper that Android removes, and alarms that Android clears
            "Skip this slot, start “Reading & notes” now",
            "Skip this slot and go to sleep",
            "Skip this slot, start the next one now",
            "The next slot has started",
            "Early sleep",
            "Early wake",
            "Going to sleep early?",
            "Not yet",
            "Go to sleep early?",
            "Wake up early?",
            "Start my day now",
            "Sleep was to begin at 9:30 PM. If you go to sleep now, “Wind down” ends here and your wake-up alarm stays as planned.",
            "Your day was to start at 5:30 AM. Start it now, and “Wake up & freshen up” begins early. It still ends at 6:00 AM, so it gets the time that was left.",
            "Tomorrow starts at 4:00 AM instead of 5:30 AM (you woke early)",
            "Going to sleep at 9:10 PM, 20 min before your plan",
            "Missed alarm",
            "This alarm did not ring because Android had closed the app. It is set again now.",
            "Android closed the app and cleared the alarm set for 5:30 AM. It is set again now.",
            "Last time Android ended the app: 11:42 PM",
            "It was closed from App info (Force stop).",
            "It crashed.",
            "Android clears every alarm of an app when it is closed this way, so the alarms and the live wallpaper were gone until you came back. They are in place again now.",
            "Android keeps the alarms of an app that stops this way. They have been checked and are in place.",
            "Your day wallpaper is gone",
            "Only the lock screen has your day",
            "Your day wallpaper is not set",
            "Android removes a live wallpaper when the app is closed from App info. The home screen still shows your day. Bring the wallpaper back for the lock screen and recent apps.",
            "It shows your day on the lock screen and in recent apps. Android asks you to confirm once.",
            "Bring it back",
            "Bring back your day wallpaper",
            "Set your day wallpaper",
            // build 24: slot goals with to-dos, the Free and Pro limits, the one video offer and the Free and Pro screen
            "Free and Pro",
            "Watch 3 short videos and everything in Pro is open for 24 hours. Nothing is charged. When the day is up, what you set up with it goes back to the free version.",
            "Watch 3 short videos",
            "Watch 2 more videos to open everything in Pro for a day.",
            "Watch one more video to open everything in Pro for a day.",
            "1 more video",
            "Watch 2 more videos: everything in Pro free for a day",
            "Watch 1 more video: everything in Pro free for a day",
            "Watch 3 videos: everything in Pro free for a day",
            "2 more videos",
            "2 of 3 videos watched today",
            "Everything in Pro: open until tomorrow 4:30 PM.",
            "Pauses: more than 1 app",
            "Locks: more than 1 app in a lock",
            "Free: a pause rule for 1 app (1 used). Pro has no limit.",
            "Apps to lock (1 free)",
            "Add a slot to your routine first (Routine tab)",
            "The list is full",
            "That is on the list already",
            "To-do list (optional)",
            "To-do list",
            "1 to-do",
            "3 to-dos",
            "Goals with to-dos",
            "Days · Pro",
            "Slot analysis · Pro",
            "Phone use in slots · Pro",
            "Saved days are part of Pro",
            "Compare Free and Pro",
            "Try Pro free for a day",
            "You have Pro",
            "See Pro plans",
            "Any day with this slot",
            "Goals of this slot",
            "Free: 1 app. Pro: any number, strict and playful",
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
            "Pro is a monthly plan or one payment for life, through Google Play. ",
            "Free: the home screen, alarms, today's report and the last three days, two paused apps and two locked apps. Pro is ₹49.00 a month, or ₹499.00 once for life, through Google Play. It adds every saved day and the weekly view, strict and playful pauses, daily limits, groups of locked apps and strict locks, weekday and weekend routines, goals, every colour look, and no ads.",
            "Choose a monthly plan or pay once for life. Paid through Google Play.",
            "Monthly plan",
            "Lifetime plan",
            "₹49.00 a month",
            "₹499.00 once",
            "Pro for as long as you subscribe. Renews every month; cancel any time in Google Play.",
            "Subscribe monthly",
            "Get lifetime Pro",
            "Manage subscription",
            "Monthly or once, or a short video for a day",
            "Your monthly plan is active. It renews every month until you cancel it in Google Play.",
            "Free with small ads. Dinacharya Pro, monthly or for life, removes the ads and unlocks the extras.",
            "Strict pauses is part of Pro, which also removes the ads.",
            "Strict pauses is open now. To finish, you also need: Pause rules for more than 2 apps. It has its own video, or it is part of Pro, which also removes the ads.",
            "You have watched a lot of videos today. Try again tomorrow, or get Pro: it opens everything and removes the ads.",
            "If you buy lifetime, cancel the monthly plan in Google Play so it does not renew.",
            " Or watch short videos to open some of these for a while: 1 video opens just ONE of them for a day, 2 videos for 3 days, 3 videos the whole group it is in. Tap “Watch videos” wherever you see “Part of Pro”, or on the Pro screen. What you set up with it goes back to the free version when the time is up.",
            "Looks: Sunset",
            "Ocean look",
            // lists and settings
            "Pause during",
            "Open the keyboard in the list of all apps",
            "You have kept “Yoga” for 5 days in a row. It is a good place for your hardest work.",
            // build 21: the day as it really goes, locks by activity, library mode, the start-of-day alarm
            "Break until 4:30 PM   ·   12 min left",
            "Then back to Study, which ends at 5:00 PM",
            "Starts at 9:00 AM   ·   in 20 min",
            "It was to start at 9:00 AM, and ends at 10:00 AM",
            "Started late, at 9:10 AM instead of 9:00 AM",
            "Started early, at 8:50 AM instead of 9:00 AM",
            "Carried on, so it ends at 9:30 AM instead of 9:00 AM",
            "Finished early, at 8:40 AM instead of 9:00 AM",
            "Locks during Study, every day, 9:00 AM to 5:00 PM",
            "Locked only while a Study slot is going on, every day from 9:00 AM to 5:00 PM.",
            "Locked every day, 9:00 AM to 5:00 PM, but only during the activity types below.",
            "Locked until library mode is switched off",
            "On until 6:00 PM",
            "Off. Next: tomorrow 7:00 AM",
            "From 9:00 PM",
            "Until 6:00 AM",
            "It runs from 10:00 PM into the next morning, until 6:00 AM.",
            "Plan your day now, later, or leave it?",
            "Study, Revision and 2 more",
            // the lock editor: the choice of how to lock, and the line that says what the choice comes to
            "Lock by",
            "Time",
            "Activity",
            "Time + activity",
            "Time or activity",
            "More options",
            "Locked at the hours you set, whatever you are doing.",
            "Locked whenever a slot of the types you pick is going on, at any hour.",
            "Locked only when a slot of the types you pick is going on inside the hours you set.",
            "Locked in the hours you set, and also whenever a slot of the types you pick is going on.",
            "No activity types chosen yet.",
            "A break, a slot you took off and the time between slots do not lock.",
            "Choose at least one activity type",
            "Locked while a Study slot is going on.",
            "Locked only while a Study slot is going on, weekdays from 9:00 PM to 6:00 AM.",
            "Locked weekdays, 9:00 PM to 6:00 AM (into the next morning).",
            "Locked every day, 9:00 PM to 6:00 AM (into the next morning).",
            "No daily schedule. Use Lock now, pick days, or lock during an activity type.",
            "Locked in the times above, and also whenever a Study slot is going on.",
            // build 23: phone use in slots, the slot analysis of the report, the habit cards, the marks on the wallpaper
            "Phone use in slots",
            "On. Apps chosen for 1 activity",
            "On. Apps chosen for 3 activities",
            "3 activities have apps",
            "Up to 40 apps for one activity",
            "Apps for Yoga",
            "Up to 40 activities can have apps",
            "Needed to see which apps are used, and when",
            "Slot analysis",
            "How each slot went",
            "85% focus",
            "Devoted 45 min of 1h",
            "Devoted 1h 5m  ·  No other phone use",
            "Devoted 1h 5m  ·  Other phone use 14 min",
            "Devoted 1h 5m  ·  Longest focus 14 min  ·  Other phone use 20 min",
            "Devoted 1h  ·  Longest focus 1h  ·  No other phone use",
            "Devoted <1 min  ·  Other phone use 2 min",
            "Chrome  +3 more",
            "Showing the latest 40 of 52.",
            "1 other slot has not started or was taken off.",
            "3 other slots have not started or were taken off.",
            "Timeline of the slot: no other phone use",
            "Timeline of the slot: 1 stretch of other phone use",
            "Timeline of the slot: 3 stretches of other phone use",
            "12 min of the phone time was in apps chosen for this activity.",
            "2 need attention, 1 going well, 3 not set up yet",
            "2 need attention, 3 not set up yet",
            "1 going well, 12 not set up yet",
            "11 need attention, 10 going well",
            "7 not set up yet",
            "2 need attention",
            "1 going well",
            "You took off Yoga on 3 of the last 7 days.",
            "You renamed Yoga to Chrome on 2 days.",
            "You went to sleep about 40 min late on average over the last 5 nights.",
            "You spent 1h 5m on your phone during Yoga (35% of the block).",
            "You used the phone for 12 min in the hour before bed.",
            "YouTube took 1h 5m, 40% of your phone time.",
            "The home screen paused you 5 times and you opened the app anyway 3 times; YouTube pulled you most.",
            "You stopped yourself 4 times at the home screen.",
            "3h of your 24 hours are not planned.",
            "Your blocks add up to 1h 30m more than 24 hours.",
            "Only 12% of your awake time is rest.",
            "Set a daily limit for it in Settings > Home screen, or in Android's Digital Wellbeing.",
            "Make it Strict, or give it a daily limit, in Settings > Home screen.",
            "Keep the phone in another room for that block.",
            "NO PHONE USE  ·  focused 1h 5m",
            "PHONE IN USE NOW  ·  12 min so far",
            "PHONE USE  ·  14 min",
            "Focused for 1h 5m since",
            "phone 14 min",
    };
    /** Text that is the person's own, or a name, and must stay as it is. */
    static final String[] STAYS = {"Social apps", "Meeting time", "Study room", "Time left", "Chrome", "Watch a video", "Morning run"};

    static Translator load(String code) throws Exception {
        try (Reader r = new InputStreamReader(new FileInputStream(new File(DIR, code + ".tsv")), StandardCharsets.UTF_8)) {
            return new Translator(Translator.read(r));
        }
    }

    static String[] concat(String[] x, String[] y) {
        String[] r = Arrays.copyOf(x, x.length + y.length);
        System.arraycopy(y, 0, r, x.length, y.length);
        return r;
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
            // the lines of the start-of-day alarm: every quote, and the sentences to type, come out in the language (the names of who said them stay)
            int quoteLeft = 0;
            for (WakeQuotes.Quote q : WakeQuotes.LIST) {
                String out = t.translate(q.text);
                if (out.equals(q.text) || !leftover(out).isEmpty()) {
                    quoteLeft++;
                    System.out.println("  quote not translated: " + q.text);
                }
            }
            ok(code + ": all " + WakeQuotes.LIST.length + " quotes are translated", quoteLeft == 0);
            int phraseBad = 0;
            for (String ph : concat(WakeQuotes.PHRASES, WakeQuotes.START_PHRASES)) {
                String out = t.translate(ph);
                // typed by the person: it has to be translated, and the sentences to start the day carry no comma or full stop inside (easy to mistype)
                boolean toStart = Arrays.asList(WakeQuotes.START_PHRASES).contains(ph);
                if (out.equals(ph) || !leftover(out).isEmpty() || (toStart && (out.contains(",") || out.contains(".")))) {
                    phraseBad++;
                    System.out.println("  sentence to type: " + ph + " -> " + out);
                }
            }
            ok(code + ": the sentences to type are translated and easy to type", phraseBad == 0);
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
