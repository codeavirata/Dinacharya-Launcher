package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;

import java.time.LocalDate;
import java.util.Locale;

/**
 * What the picture tour does, in order (test build only). Each pass walks the whole app in one set of colours and one language; the
 * first pass also visits every other screen. If a button is renamed the step logs "NOT FOUND" with what could be pressed, and the tour
 * goes on, so one out-of-date step costs a few pictures, not the run.
 */
final class ShotScript {
    private ShotScript() {
    }

    static void build(ShotTour t) {
        final Application app = t.app;
        t.add("Pro on", 100, x -> Pro.get(app).setDebugPro(true));

        firstRun(t);

        t.add("seed a week of days", 600, x -> ShotSeed.run(app));

        mainPass(t, "a-graphite-en", Ui.COLOURS_GRAPHITE, "en", true);
        mainPass(t, "b-paper-en", Ui.COLOURS_PAPER, "en", false);
        mainPass(t, "c-graphite-hi", Ui.COLOURS_GRAPHITE, "hi", false);

        t.add("back to English and Graphite", 200, x -> {
            Lang.set(app, "en");
            Ui.chooseColours(app, Ui.COLOURS_GRAPHITE);
        });
    }

    // ------------------------------------------------------------------ the first start: welcome, setup, tour, compare, ready

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
        t.add("skip setup", 300, x -> x.click("Skip for now"));
        t.waitFor(TourActivity.class, 6000);
        for (int i = 0; i <= TourArt.PAGES; i++) {
            final int page = i;
            t.add("tour page " + page, 900, x -> x.shot("tour-" + (page + 1)));
            t.add("tour next " + page, 350, x -> x.click("Next"));
        }
        t.waitFor(CompareActivity.class, 6000);
        t.add("compare (first run)", 900, x -> x.shot("compare-first"));
        t.add("compare scroll", 100, x -> x.scrollShots("compare-first-full", 4));
        t.add("compare done", 300, x -> x.clickAny("Done", "Close"));
        t.waitFor(MainActivity.class, 6000);
        t.add("ready message", 1200, x -> x.shot("ready"));
        t.add("ready close", 300, x -> x.closeFront());
        t.add("settle", 600, x -> {
        });
    }

    // ------------------------------------------------------------------ the dashboard, in one set of colours and one language

    private static void mainPass(ShotTour t, final String id, final int colours, final String lang, final boolean everything) {
        final Application app = t.app;
        t.pass(id);
        t.add("restart in " + id, 1500, x -> {
            Store.save(app, Routine.sample());   // the sheets add and delete slots: start each pass from the same routine
            Ui.chooseColours(app, colours);
            Lang.set(app, lang);
            x.open(new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
        t.waitFor(MainActivity.class, 8000);

        // Home
        tab(t, "Home", "01-home", 6);

        // Routine
        tab(t, "Routine", "02-routine", 8);
        routineSheets(t, everything);

        // Insights
        tab(t, "Insights", "03-insights", 10);
        if (everything) {
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
        settingsPages(t, everything);

        if (everything) otherScreens(t);
    }

    /** Switches to a tab, takes the page as it first looks, opens every card on it, then takes it screen by screen. */
    private static void tab(ShotTour t, final String name, final String shot, final int max) {
        t.add("tab " + name, 500, x -> x.clickDesc(name, false));
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
        t.add("open " + label, 700, open);
        t.add(shot, 600, x -> x.shot(shot));
        t.add("close " + label, 500, x -> x.closeSheet());
    }

    // ------------------------------------------------------------------ the menus and dialogs of the routine

    private static void routineSheets(ShotTour t, final boolean everything) {
        t.add("tab Routine again", 400, x -> x.clickDesc("Routine", false));
        sheet(t, "slot menu", "routine-menu-more", x -> x.clickDesc("More: add below", true));
        t.add("menu: add a slot below", 500, x -> x.clickDesc("More: add below", true));
        t.add("pick add a slot below", 400, x -> x.click("Add a slot below"));
        t.add("add-a-slot sheet", 700, x -> x.shot("routine-add-below"));
        t.add("close", 500, x -> x.closeSheet());
        t.add("menu: note", 500, x -> x.clickDesc("More: add below", true));
        t.add("pick note", 400, x -> x.click("Add a note"));
        t.add("note sheet", 700, x -> x.shot("routine-note"));
        t.add("close", 500, x -> x.closeSheet());
        t.add("menu: delete", 500, x -> x.clickDesc("More: add below", true));
        t.add("pick delete", 400, x -> x.clickAny("Delete", "Remove"));
        t.add("delete sheet", 700, x -> x.shot("routine-delete"));
        t.add("close", 500, x -> x.closeSheet());
        sheet(t, "length", "routine-length", x -> x.clickDesc("Length. Tap to type", true));
        sheet(t, "add a slot", "routine-add", x -> x.clickAny("Add a slot"));
        sheet(t, "round clock", "routine-clock", x -> x.clickMatching("^\\d{1,2}:\\d{2}"));
        if (everything) {
            sheet(t, "add rests", "routine-rests", x -> x.clickAny("Add rests"));
            sheet(t, "more tools", "routine-tools", x -> x.clickAny("More tools"));
        }
    }

    // ------------------------------------------------------------------ settings

    private static void settingsPages(ShotTour t, final boolean everything) {
        sheet(t, "language", "settings-language", x -> x.click("Language"));
        page(t, "App colours", "settings-colours", 3);
        page(t, "Home screen look", "settings-look", 6);
        page(t, "Alarms", "settings-alarms", 6);
        page(t, "Permissions", "settings-permissions", 5);
        page(t, "Feature guide", "settings-guide", 10);
        page(t, "Help and problem report", "settings-help", 5);
        page(t, "About", "settings-about", 3);
        if (everything) {
            t.add("check phone settings", 500, x -> x.click("Check phone settings"));
            t.waitFor(SetupActivity.class, 5000);
            t.add("setup review", 900, x -> x.shot("setup-review"));
            t.add("back", 400, x -> x.back());
            t.add("settle", 700, x -> {
            });
        }
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
        t.add(name, 1000, x -> x.shot(name));
        t.add(name + " expand", 300, x -> x.expandAll());
        t.add(name + " scroll", 600, x -> x.scrollShots(name + "-open", max));
        t.add("close " + name, 300, x -> {
            Activity a = x.top();
            if (a != null && cls.isInstance(a)) a.finish();
        });
        t.add("settle", 700, x -> {
        });
    }

    private static void otherScreens(ShotTour t) {
        final Application app = t.app;
        t.pass("d-other-screens");
        visit(t, "pro", ProActivity.class, new Intent(app, ProActivity.class), 6);
        visit(t, "compare", CompareActivity.class, new Intent(app, CompareActivity.class), 4);
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

        // The home screen itself, and the lock or pause screen that comes up in front of an app.
        visit(t, "home-screen", LauncherActivity.class, new Intent(app, LauncherActivity.class), 2);
        visit(t, "guard-screen", GuardActivity.class, new Intent(app, GuardActivity.class).putExtra(GuardActivity.EXTRA_PKG, "com.android.settings"), 2);

        // The alarm question, in its four forms.
        final long now = System.currentTimeMillis();
        final String day = LocalDate.now().toString();
        int[] kinds = {AlarmPlanner.SLOT, AlarmPlanner.BEDTIME, AlarmPlanner.REASK, AlarmPlanner.PLAN_ASK};
        String[] names = {"slot", "bedtime", "reask", "plan-ask"};
        for (int i = 0; i < kinds.length; i++) {
            final int kind = kinds[i];
            final String name = names[i];
            t.add("alarm " + name + " prepare", 100, x -> {
            });
            t.add("alarm " + name, 800, x -> {
                AlarmPlanner.Event e = new AlarmPlanner.Event(now, kind, day, 0, "Study", Routine.TASK, now - 600000L, now + 3000000L,
                        now + 8 * 3600000L, false, Routine.ALERT_SILENT, false);
                Intent in = new Intent(app, AlarmActivity.class);
                AlarmScheduler.put(in, e);
                x.open(in);
            });
            t.waitFor(AlarmActivity.class, 5000);
            t.add("alarm " + name + " shot", 1200, x -> x.shot("alarm-" + name));
            t.add("alarm " + name + " close", 300, x -> {
                Activity a = x.top();
                if (a instanceof AlarmActivity) a.finish();
            });
            t.add("settle", 700, x -> {
            });
        }
        t.add("end note", 100, x -> x.note("other screens done " + String.format(Locale.US, "%d", x.shots()) + " pictures so far"));
    }
}
