package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;

import java.time.LocalDate;
import java.util.Locale;

/**
 * What the picture tour does, in order (test build only). It takes the pictures for the website: the first start, then the whole app
 * on one routine (the UPSC day) in graphite and in white, and in Hindi. If a button is renamed the step logs "NOT FOUND" with what could
 * be pressed, and the tour goes on, so one out-of-date step costs a few pictures, not the run.
 */
final class ShotScript {
    private ShotScript() {
    }

    static void build(ShotTour t) {
        final Application app = t.app;
        t.add("Pro on", 100, x -> Pro.get(app).setDebugPro(true));

        firstRun(t);

        t.add("seed the UPSC routine and a week of days", 600, x -> ShotSeed.site(app));

        sitePass(t, "site-a-graphite-en", Ui.COLOURS_GRAPHITE, "en", true);
        sitePass(t, "site-b-paper-en", Ui.COLOURS_PAPER, "en", false);
        sitePass(t, "site-c-graphite-hi", Ui.COLOURS_GRAPHITE, "hi", false);

        t.add("back to English and Graphite", 200, x -> {
            Lang.set(app, "en");
            Ui.chooseColours(app, Ui.COLOURS_GRAPHITE);
        });
    }

    // ------------------------------------------------------------------ the first start: welcome, setup, tour, compare

    private static void firstRun(ShotTour t) {
        final Application app = t.app;
        t.pass("00-first-run");
        t.add("forget that setup and tour were done", 200, x -> {
            Store.prefs(app).edit().putBoolean(SetupActivity.PREF_SETUP_DONE, false).putBoolean(LauncherActivity.PREF_TOUR_DONE, false).apply();
            Lang.set(app, "en");
            Ui.chooseColours(app, Ui.COLOURS_GRAPHITE);
            x.open(new Intent(app, WelcomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
        t.waitFor(WelcomeActivity.class, 8000);
        t.add("welcome", 900, x -> x.shot("welcome"));
        t.add("welcome scroll", 100, x -> x.scrollShots("welcome-full", 3));
        t.add("get started", 300, x -> x.click("Get started"));
        t.waitFor(SetupActivity.class, 6000);
        t.add("setup", 900, x -> x.shot("setup"));
        t.add("setup scroll", 100, x -> x.scrollShots("setup-full", 4));
        // the feature chooser: pick two features, walk through what they ask (nothing is allowed, every page is skipped), then the all-set page
        t.add("pick alarms", 400, x -> x.click("Day alarms"));
        t.add("pick usage", 400, x -> x.click("Phone time and app limits"));
        t.add("setup picked", 900, x -> x.shot("setup-picked"));
        t.add("set up the features", 400, x -> x.clickAny("Set up 2 features", "Set up 1 feature"));
        for (int i = 1; i <= 5; i++) {
            final int n = i;
            t.add("step page " + n, 900, x -> x.shot("setup-step-" + n));
            t.add("skip step " + n, 400, x -> {
                if (!x.clickQuiet("Not now")) x.clickQuiet("Skip this one");
            });
        }
        t.add("setup done", 900, x -> x.shot("setup-done"));
        // the tour, the way a first start shows it
        t.add("open the tour", 500, x -> x.open(new Intent(app, TourActivity.class).putExtra(TourActivity.EXTRA_FIRST_RUN, true)));
        t.waitFor(TourActivity.class, 6000);
        for (int i = 1; i <= 6; i++) {
            final int page = i;
            t.add("tour page " + page, 1100, x -> x.shot("tour-" + page));
            t.add("tour next " + page, 350, x -> x.click("Next"));
        }
        t.waitFor(CompareActivity.class, 6000);
        t.add("compare (first run)", 900, x -> x.shot("compare-first"));
        t.add("compare scroll", 100, x -> x.scrollShots("compare-first-full", 4));
        t.add("compare done", 300, x -> x.clickAny("Done", "Close", "Continue"));
        t.add("settle", 900, x -> {
        });
    }

    // ------------------------------------------------------------------ the dashboard, in one set of colours and one language

    private static void sitePass(ShotTour t, final String id, final int colours, final String lang, final boolean everything) {
        final Application app = t.app;
        t.pass(id);
        t.add("restart in " + id, 1500, x -> {
            ShotSeed.site(app);
            Ui.chooseColours(app, colours);
            Lang.set(app, lang);
            x.open(new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
        t.waitFor(MainActivity.class, 8000);
        t.add("settle", 1500, x -> {
        });

        // Home
        tab(t, "Home", "01-home", 6);
        if (everything) homeSheets(t);

        // Routine
        tab(t, "Routine", "02-routine", 8);
        if (everything) routineSheets(t);

        // Insights
        tab(t, "Insights", "03-insights", 10);
        if (everything) {
            sheet(t, "new goal", "insights-new-goal", x -> x.clickAny("New goal"));
            t.add("performance and reports", 300, x -> x.click("Performance and reports"));
            t.waitFor(PerformanceActivity.class, 5000);
            t.add("performance", 1000, x -> x.shot("performance"));
            t.add("performance expand", 300, x -> x.expandAll());
            t.add("performance scroll", 500, x -> x.scrollShots("performance-open", 6));
            t.add("performance back", 300, x -> x.back());
            t.add("settle", 700, x -> {
            });
        }

        // Backup
        tab(t, "Backup", "04-backup", 4);

        // Settings
        tab(t, "Settings", "05-settings", 6);
        if (everything) settingsPages(t);

        if (everything) otherScreens(t);
        else lookScreens(t, id);
    }

    /** Switches to a tab, puts away its tip, takes the page as it first looks, opens every card on it, then takes it screen by screen. */
    private static void tab(ShotTour t, final String name, final String shot, final int max) {
        t.add("tab " + name, 600, x -> x.clickDesc(name, false));
        t.add("tip away " + name, 500, x -> x.clickQuiet("Got it"));
        t.add(shot, 1100, x -> {
            x.note("clickables on " + name + ": " + x.pressables());
            x.shot(shot);
        });
        t.add(shot + " expand", 300, x -> x.expandAll());
        t.add(shot + " scroll", 700, x -> x.scrollShots(shot + "-open", max));
        t.add(shot + " settle", 300, x -> {
        });
    }

    private static void sheet(ShotTour t, final String label, final String shot, final ShotTour.Action open) {
        t.add("open " + label, 800, open);
        t.add(shot, 700, x -> x.shot(shot));
        t.add("close " + label, 500, x -> x.closeSheet());
    }

    // ------------------------------------------------------------------ the buttons of the Home card

    private static void homeSheets(ShotTour t) {
        t.add("tab Home again", 500, x -> x.clickDesc("Home", false));
        t.add("scroll up", 300, x -> {
            android.widget.ScrollView sv = x.mainScroll();
            if (sv != null) sv.scrollTo(0, 0);
        });
        sheet(t, "adjust time", "home-adjust-time", x -> x.clickAny("Adjust time"));
        sheet(t, "change plan", "home-change-plan", x -> x.clickAny("Change plan"));
        sheet(t, "to-do", "home-todo", x -> x.clickAny("To-do"));
        sheet(t, "break", "home-break", x -> x.clickAny("Break"));
        sheet(t, "day off", "home-day-off", x -> x.clickAny("Day off"));
        sheet(t, "finish", "home-finish", x -> x.clickAny("Finish"));
    }

    // ------------------------------------------------------------------ the menus and dialogs of the routine

    private static void routineSheets(ShotTour t) {
        final Application app = t.app;
        t.add("tab Routine again", 400, x -> x.clickDesc("Routine", false));
        sheet(t, "templates", "routine-templates", x -> x.clickDesc("Templates", false));
        sheet(t, "share", "routine-share", x -> x.clickDesc("Share", false));
        sheet(t, "add a slot", "routine-add", x -> x.clickAny("Add slot"));
        sheet(t, "add rests", "routine-rests", x -> x.clickAny("Add rests"));
        t.add("open the first slot", 700, x -> x.clickDescContains("details hidden"));
        t.add("first slot open", 700, x -> {
            x.note("on the open slot: " + x.pressables());
            x.shot("routine-slot-open");
        });
        sheet(t, "slot menu", "routine-menu-more", x -> x.clickDesc("More: add below, note, duplicate, delete", false));
        sheet(t, "length", "routine-length", x -> x.clickDesc("Length. Tap to type the minutes.", false));
        sheet(t, "start of day", "routine-start-of-day", x -> x.clickMatching("^\\d{1,2}:\\d{2}"));
        // the days for an exam, as a person who has not made a schedule yet sees them
        t.add("an empty routine", 300, x -> {
            Store.save(app, Routine.sample());
            Store.prefs(app).edit().putBoolean("first_day_done", false).apply();
            x.open(new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
        t.waitFor(MainActivity.class, 6000);
        t.add("tab Routine for exams", 1500, x -> x.clickDesc("Routine", false));
        t.add("tip away", 400, x -> x.clickQuiet("Got it"));
        t.add("exam tiles", 1100, x -> x.shot("routine-exam"));
        t.add("back to the UPSC routine", 300, x -> {
            ShotSeed.site(app);
            x.open(new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
        t.waitFor(MainActivity.class, 6000);
        t.add("settle", 1500, x -> {
        });
    }

    // ------------------------------------------------------------------ settings

    private static void settingsPages(ShotTour t) {
        t.add("tab Settings again", 500, x -> x.clickDesc("Settings", false));
        sheet(t, "language", "settings-language", x -> x.click("Language"));
        page(t, "App colours", "settings-colours", 3);
        page(t, "Home screen look", "settings-look", 6);
        page(t, "Alarms", "settings-alarms", 6);
        page(t, "Permissions", "settings-permissions", 5);
        page(t, "Feature guide", "settings-guide", 10);
        page(t, "Help and problem report", "settings-help", 5);
        page(t, "About", "settings-about", 3);
        t.add("features", 500, x -> x.click("Features"));
        t.waitFor(SetupActivity.class, 5000);
        t.add("setup review", 900, x -> x.shot("setup-review"));
        t.add("back", 400, x -> x.back());
        t.add("settle", 700, x -> {
        });
    }

    /** Opens a settings page from the menu, takes it open, and goes back to the menu. */
    private static void page(ShotTour t, final String row, final String shot, final int max) {
        t.add("settings: " + row, 500, x -> x.click(row));
        t.add(shot, 900, x -> x.shot(shot));
        t.add(shot + " expand", 300, x -> x.expandAll());
        t.add(shot + " scroll", 700, x -> x.scrollShots(shot + "-open", max));
        t.add(shot + " back", 300, x -> x.back());
        t.add(shot + " settled", 500, x -> {
        });
    }

    // ------------------------------------------------------------------ screens that are not tabs

    private static void visit(ShotTour t, final String name, final Class<? extends Activity> cls, final Intent intent, final int max) {
        t.add("open " + name, 600, x -> x.open(intent));
        t.waitFor(cls, 6000);
        t.add(name, 1200, x -> x.shot(name));
        t.add(name + " expand", 300, x -> x.expandAll());
        t.add(name + " scroll", 600, x -> x.scrollShots(name + "-open", max));
        t.add("close " + name, 300, x -> {
            Activity a = x.top();
            if (a != null && cls.isInstance(a)) a.finish();
        });
        t.add("settle", 700, x -> {
        });
    }

    /** The pause or lock screen in front of a made-up app. */
    private static void stage(ShotTour t, final String kind, final String label, final String shot) {
        final Application app = t.app;
        t.add("stage " + kind, 600, x -> x.open(new Intent(app, ShotStage.class).putExtra(ShotStage.EXTRA_KIND, kind)
                .putExtra(ShotStage.EXTRA_LABEL, label)));
        t.waitFor(ShotStage.class, 5000);
        t.add(shot, 1500, x -> x.shot(shot));
        t.add("stage " + kind + " later", 3500, x -> x.shot(shot + "-later"));
        t.add("close stage", 300, x -> {
            Activity a = x.top();
            if (a instanceof ShotStage) a.finish();
        });
        t.add("settle", 600, x -> {
        });
    }

    private static void otherScreens(ShotTour t) {
        final Application app = t.app;
        t.pass("d-other-screens");
        visit(t, "pro", ProActivity.class, new Intent(app, ProActivity.class), 6);
        visit(t, "compare", CompareActivity.class, new Intent(app, CompareActivity.class), 4);
        visit(t, "invite", InviteActivity.class, new Intent(app, InviteActivity.class), 3);
        visit(t, "report-yesterday", ReportActivity.class,
                new Intent(app, ReportActivity.class).putExtra(ReportActivity.EXTRA_DAY, LocalDate.now().minusDays(1).toString()), 8);
        visit(t, "report-2-days-ago", ReportActivity.class,
                new Intent(app, ReportActivity.class).putExtra(ReportActivity.EXTRA_DAY, LocalDate.now().minusDays(2).toString()), 3);

        String[] homeCards = {"home", "swipes", "list", "hidden"};
        for (String card : homeCards) {
            visit(t, "home-settings-" + card, LauncherSettingsActivity.class, new Intent(app, LauncherSettingsActivity.class)
                    .putExtra(LauncherSettingsActivity.EXTRA_SECTION, LauncherSettingsActivity.SEC_HOME)
                    .putExtra(LauncherSettingsActivity.EXTRA_CARD, card), 4);
        }
        String[] focusCards = {"pauses", "locks", "quiet", "use"};
        for (String card : focusCards) {
            visit(t, "focus-settings-" + card, LauncherSettingsActivity.class, new Intent(app, LauncherSettingsActivity.class)
                    .putExtra(LauncherSettingsActivity.EXTRA_SECTION, LauncherSettingsActivity.SEC_FOCUS)
                    .putExtra(LauncherSettingsActivity.EXTRA_CARD, card), 4);
        }

        // The home screen itself, its list of apps, and the pause and lock screens that come up in front of an app.
        homeScreen(t, "home-screen");
        stage(t, ShotStage.PAUSE, "Reels", "pause");
        stage(t, ShotStage.PAUSE_STRICT, "Reels", "pause-strict");
        stage(t, ShotStage.LOCK_TIMED, "Reels", "lock-timed");
        stage(t, ShotStage.LOCK_TYPE, "Reels", "lock-type");
        stage(t, ShotStage.LOCK_STRICT, "Reels", "lock-strict");

        alarms(t, "alarm");
        t.add("end note", 100, x -> x.note("other screens done " + String.format(Locale.US, "%d", x.shots()) + " pictures so far"));
    }

    /** The home screen and its list of all apps. */
    private static void homeScreen(ShotTour t, final String name) {
        final Application app = t.app;
        t.add("open the home screen", 700, x -> x.open(new Intent(app, LauncherActivity.class)));
        t.waitFor(LauncherActivity.class, 6000);
        t.add("nudges away", 1500, x -> x.hideTexts("Set your day wallpaper", "Bring back your day wallpaper", "Make this your home app",
                "Make your locks hold everywhere"));
        t.add(name, 700, x -> x.shot(name));
        t.add("open the list of apps", 600, x -> x.callOnTop("openDrawer"));
        t.add(name + "-apps", 1500, x -> x.shot(name + "-apps"));
        t.add("close the list", 300, x -> x.back());
        t.add("close the home screen", 300, x -> {
            Activity a = x.top();
            if (a instanceof LauncherActivity) a.finish();
        });
        t.add("settle", 700, x -> {
        });
    }

    /** The alarm question in its four forms, for an earlier slot of today that rang and was not answered. */
    private static void alarms(ShotTour t, final String prefix) {
        final Application app = t.app;
        final long now = System.currentTimeMillis();
        final String day = LocalDate.now().toString();
        final long dayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        t.add("an unanswered earlier slot", 100, x -> Store.history(app).append(Ev.rang(now - 30 * 60000L, day, 1, "Yoga & pranayama")));
        int[] kinds = {AlarmPlanner.SLOT, AlarmPlanner.BEDTIME, AlarmPlanner.REASK, AlarmPlanner.PLAN_ASK};
        String[] names = {"slot", "bedtime", "reask", "plan-ask"};
        for (int i = 0; i < kinds.length; i++) {
            final int kind = kinds[i];
            final String name = prefix + "-" + names[i];
            t.add("alarm " + name, 800, x -> {
                AlarmPlanner.Event e = new AlarmPlanner.Event(now, kind, day, 4, "GS study", Routine.TASK, dayStart + 7 * 3600000L + 40 * 60000L,
                        now + 3000000L, now + 8 * 3600000L, false, Routine.ALERT_SILENT, false);
                Intent in = new Intent(app, AlarmActivity.class);
                AlarmScheduler.put(in, e);
                x.open(in);
            });
            t.waitFor(AlarmActivity.class, 5000);
            t.add(name + " shot", 1500, x -> x.shot(name));
            t.add(name + " close", 300, x -> {
                Activity a = x.top();
                if (a instanceof AlarmActivity) a.finish();
            });
            t.add("settle", 700, x -> {
            });
        }
    }

    /** The pictures of the other two sets of colours and the other language: the home screen and the alarm. */
    private static void lookScreens(ShotTour t, final String id) {
        homeScreen(t, "home-screen");
        if (id.contains("paper")) stage(t, ShotStage.PAUSE, "Reels", "pause");
        alarms(t, "alarm");
    }
}
