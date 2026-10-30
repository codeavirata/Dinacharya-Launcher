package app.dinacharya.wallpaper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** The Settings tab: a menu, and one page per group of settings. */
final class SettingsPage extends Page {
    static final int MENU = -1, LOOK = 0, ALARMS = 1, PERMISSIONS = 2, HELP = 3, ABOUT = 4;
    private int open = MENU;
    private PosterView preview;
    private LinearLayout swatchBox;
    private Button accessBtn;
    private TextView accessNote;

    SettingsPage(MainActivity host) {
        super(host);
    }

    @Override
    void build(LinearLayout body) {
        show(open);
    }

    @Override
    void refresh() {
        if (!built()) return;
        if (open == ALARMS || open == PERMISSIONS) a.extras.refresh();
        if (open == PERMISSIONS) show(PERMISSIONS);
        if (open == LOOK) refreshAccess();
    }

    @Override
    boolean onBack() {
        if (open == MENU) return false;
        show(MENU);
        return true;
    }

    /** Opens one settings page (or the menu). */
    void open(int which) {
        view();
        show(which);
    }

    private void show(int which) {
        open = which;
        body.removeAllViews();
        if (scroll != null) scroll.scrollTo(0, 0);
        switch (which) {
            case LOOK: look(); break;
            case ALARMS: alarms(); break;
            case PERMISSIONS: permissions(); break;
            case HELP: help(); break;
            case ABOUT: about(); break;
            default: menu(); break;
        }
    }

    // ------------------------------------------------------------------ menu

    private void menu() {
        title("Settings", null);
        proCard();
        LinearLayout general = Ui.section(a, body, "General", null);
        itemAction(general, Glyph.GLOBE, "Language", LanguageDialog.summary(a),
                () -> LanguageDialog.show(a, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert));
        itemAction(general, Glyph.HELP, "Take the tour", "A short walk through what the app does",
                () -> a.startActivity(new Intent(a, TourActivity.class)));
        itemAction(general, Glyph.SHIELD, "Check phone settings", "Permissions and settings the app needs, with a tick for each",
                () -> a.startActivity(new Intent(a, SetupActivity.class).putExtra(SetupActivity.EXTRA_REVIEW, true)));
        LinearLayout home = Ui.section(a, body, "Home screen", null);
        homeItem(home, Glyph.HOME, "Home app", "Make it your home app, and the wallpaper", LauncherSettingsActivity.SEC_HOME, "home");
        homeItem(home, Glyph.GEAR, "Shortcut", "What swiping left and right opens", LauncherSettingsActivity.SEC_HOME, "swipes");
        homeItem(home, Glyph.CHART, "App list", "Colours, text size and the keyboard in the list of all apps", LauncherSettingsActivity.SEC_HOME, "list");
        homeItem(home, Glyph.PERSON, "Hidden apps", "Apps kept out of the list of all apps", LauncherSettingsActivity.SEC_HOME, "hidden");
        item(home, Glyph.PALETTE, "Home screen look", "Colours, clock, phone time, spacing", LOOK);
        LinearLayout focus = Ui.section(a, body, "Focus and locks", null);
        homeItem(focus, Glyph.TARGET, "Focus", "A short wait before apps open", LauncherSettingsActivity.SEC_FOCUS, "pauses");
        homeItem(focus, Glyph.SHIELD, "Locked apps", "Lock apps for a time", LauncherSettingsActivity.SEC_FOCUS, "locks");
        homeItem(focus, Glyph.ROUTINE, "Phone use in slots", "See when you pick up the phone during a slot", LauncherSettingsActivity.SEC_FOCUS, "use");
        LinearLayout card2 = Ui.section(a, body, "Alarms and phone", null);
        item(card2, Glyph.BELL, "Alarms", "On or off, test alarm, phone checklist", ALARMS);
        item(card2, Glyph.SHIELD, "Permissions", "What the app may do, and why", PERMISSIONS);
        LinearLayout card3 = Ui.section(a, body, "Support", null);
        item(card3, Glyph.HELP, "Help and problem report", "How things work, copy a report", HELP);
        if (Ads.wanted(a) && Ads.get().privacyOptionsRequired()) {
            itemAction(card3, Glyph.SHIELD, "Ad privacy choices", "Change what you agreed to for ads",
                    () -> Ads.get().showPrivacyOptions(a));
        }
        item(card3, Glyph.INFO, "About", "Version and developer", ABOUT);
        a.addFooter(body);
    }

    /** A line that opens one of the home and focus settings pages, with the card for this line open. */
    private void homeItem(LinearLayout parent, int icon, String title, String sub, final int section, final String card) {
        itemAction(parent, icon, title, sub, () -> a.startActivity(new Intent(a, LauncherSettingsActivity.class)
                .putExtra(LauncherSettingsActivity.EXTRA_SECTION, section)
                .putExtra(LauncherSettingsActivity.EXTRA_CARD, card)));
    }

    private void item(LinearLayout parent, int icon, String title, String sub, final int target) {
        itemAction(parent, icon, title, sub, () -> show(target));
    }

    private void itemAction(LinearLayout parent, int icon, String title, String sub, final Runnable action) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(a.dp(4), a.dp(10), a.dp(4), a.dp(10));
        row.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 14, 0x200E7C7B));
        Glyph g = new Glyph(a, icon, Ui.TEAL_DARK);
        g.setBackground(Ui.box(a, Ui.TONAL, 0, 14));
        g.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
        row.addView(g, new LinearLayout.LayoutParams(a.dp(42), a.dp(42)));
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(a.dp(14), 0, a.dp(6), 0);
        words.addView(Ui.label(a, title, 16, Ui.INK, true));
        words.addView(Ui.label(a, sub, 13, Ui.MUTED, false), Ui.wide(a, 1));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Glyph(a, Glyph.CHEVRON, Ui.MUTED), new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        row.setOnClickListener(v -> action.run());
        parent.addView(row, Ui.wide(a, 4));
    }

    /** "‹ Settings" and the page title. */
    private void header(String title) {
        TextView back = Ui.label(a, "‹  Settings", 15, Ui.TEAL, true);
        back.setPadding(a.dp(4), a.dp(8), a.dp(12), a.dp(8));
        back.setBackground(Ui.pressable(a, 0x00FFFFFF, 0, 12, 0x200E7C7B));
        back.setOnClickListener(v -> show(MENU));
        body.addView(back, Ui.wrap(a, 0));
        body.addView(Ui.label(a, title, 26, Ui.INK, true), Ui.wide(a, 6));
    }

    // ------------------------------------------------------------------ look

    private boolean nightNow() {
        Routine r = a.routine();
        if (r.theme == Routine.THEME_NIGHT) return true;
        if (r.theme == Routine.THEME_LIGHT) return false;
        return (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void look() {
        header("Home screen look");
        final Routine r = a.routine();

        if (preview == null) {
            preview = new PosterView(a);
            preview.setElevation(a.dp(3));
            a.registerPreview(preview);
        }
        if (preview.getParent() != null) ((ViewGroup) preview.getParent()).removeView(preview);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int pw = Math.min((int) (dm.widthPixels * 0.42f), a.dp(190));
        int ph = (int) (pw * (dm.heightPixels / (float) dm.widthPixels));
        LinearLayout.LayoutParams plp = Ui.lp(a, pw, ph, 14);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(preview, plp);

        LinearLayout card = Ui.section(a, body, "Theme", null);
        Segmented themeSeg = new Segmented(a, new String[]{"Light", "Night", "Auto"});
        themeSeg.pick(r.theme, false);
        themeSeg.setListener(i -> {
            r.theme = i;
            a.persist();
            rebuildSwatches();
            a.previewChanged();
        });
        card.addView(themeSeg, Ui.wide(a, 10));
        card.addView(Ui.label(a, "Colours", 13, Ui.MUTED, true), Ui.wrap(a, 16));
        swatchBox = new LinearLayout(a);
        swatchBox.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(swatchBox, Ui.wide(a, 6));
        rebuildSwatches();

        final Accordion lookParts = new Accordion();   // only one of the two cards below is open at a time
        LinearLayout show = lookParts.add(Expander.card(a, body, "On the home screen", null, "Clock, phone screen time, the “Developed by” mark",
                "look_show", false)).body;
        show.addView(Ui.toggle(a, "24-hour clock", r.fmt24, (b, on) -> {
            r.fmt24 = on;
            a.persist();
            a.routineChanged();
        }), Ui.wide(a, 4));
        show.addView(Ui.toggle(a, "Show the time", r.showClock, (b, on) -> {
            r.showClock = on;
            a.persist();
            a.previewChanged();
        }), Ui.wide(a, 0));
        show.addView(Ui.toggle(a, "Show phone screen time today", r.showScreen, (b, on) -> {
            r.showScreen = on;
            a.persist();
            if (on) preview.refreshScreen();
            refreshAccess();
            a.previewChanged();
        }), Ui.wide(a, 0));
        accessNote = Ui.label(a, "", 12, Ui.MUTED, false);
        show.addView(accessNote, Ui.wide(a, 2));
        accessBtn = Ui.button(a, "Allow usage access", false, v -> openUsageAccess());
        show.addView(accessBtn, Ui.wide(a, 8));
        show.addView(Ui.toggle(a, "Show “Developed by” mark", r.showMark, (b, on) -> {
            r.showMark = on;
            a.persist();
            a.previewChanged();
        }), Ui.wide(a, 0));
        refreshAccess();

        LinearLayout space = lookParts.add(Expander.card(a, body, "Spacing", null, "Space at the top and at the bottom", "look_space", false)).body;
        addSlider(space, "Space at top", r.topPct, true);
        addSlider(space, "Space at bottom", r.bottomPct, false);
        space.addView(Ui.label(a, "Add top space if your lock screen shows a big clock, and bottom space for the app dock.",
                12, Ui.MUTED, false), Ui.wide(a, 4));
        a.addFooter(body);
    }

    private GradientDrawable ring(int fill, int stroke, float radiusDp, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(a.dp(radiusDp));
        g.setStroke(a.dp(strokeDp), stroke);
        return g;
    }

    /** Applies a colour look: saves it and redraws the swatches and everything that shows the day. */
    private void choosePalette(Routine r, int id) {
        r.palette = id;
        a.persist();
        rebuildSwatches();
        a.routineChanged();
    }

    private void rebuildSwatches() {
        if (swatchBox == null) return;
        swatchBox.removeAllViews();
        final Routine r = a.routine();
        boolean night = nightNow();
        for (int i = 0; i < Routine.PALETTE_NAMES.length; i++) {
            final int id = i;
            int[] c = PosterRenderer.swatch(i, night);
            boolean sel = r.palette == i;
            LinearLayout tile = new LinearLayout(a);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(a.dp(4), a.dp(12), a.dp(4), a.dp(10));
            tile.setBackground(sel ? ring(c[0], c[2], 16, 2.5f) : ring(c[0], Ui.LINE, 16, 1));
            LinearLayout dots = new LinearLayout(a);
            dots.setOrientation(LinearLayout.HORIZONTAL);
            dots.setGravity(Gravity.CENTER);
            for (int k : new int[]{2, 4, 5, 6}) {
                View dot = new View(a);
                dot.setBackground(Ui.box(a, c[k], 0, 6));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(a.dp(11), a.dp(11));
                p.leftMargin = a.dp(2);
                p.rightMargin = a.dp(2);
                dots.addView(dot, p);
            }
            tile.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            boolean locked = !Pro.hasLook(a, id) && !sel;
            tile.addView(Ui.label(a, Routine.PALETTE_NAMES[i] + (sel ? " ✓" : locked ? " 🔒" : ""), 12, c[1], sel), Ui.wrap(a, 7));
            tile.setContentDescription(sel ? Routine.PALETTE_NAMES[i] + " colours, selected" : Routine.PALETTE_NAMES[i] + " colours");
            tile.setOnClickListener(v -> {
                if (r.palette == id) return;
                if (id > 0 && !a.requirePro(DayPass.lookItem(id), "The " + Routine.PALETTE_NAMES[id] + " colours", () -> choosePalette(r, id))) return;
                choosePalette(r, id);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) p.leftMargin = a.dp(8);
            swatchBox.addView(tile, p);
        }
    }

    private void addSlider(LinearLayout card, final String name, int value, final boolean top) {
        final TextView t = Ui.label(a, name + ": " + value + "%", 14, Ui.INK, false);
        card.addView(t, Ui.wrap(a, 10));
        SeekBar sb = new SeekBar(a);
        sb.setMax(35);
        sb.setProgress(value);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                t.setText(name + ": " + progress + "%");
                if (!fromUser) return;
                if (top) a.routine().topPct = progress;
                else a.routine().bottomPct = progress;
                a.persistSoon();
                a.previewChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        card.addView(sb, Ui.wide(a, 2));
    }

    private void refreshAccess() {
        if (accessBtn == null) return;
        boolean ok;
        try {
            ok = ScreenTime.hasAccess(a);
        } catch (RuntimeException e) {
            ok = false;
        }
        boolean on = a.routine().showScreen;
        accessBtn.setVisibility(ok || !on ? View.GONE : View.VISIBLE);
        accessNote.setVisibility(on ? View.VISIBLE : View.GONE);
        accessNote.setText(ok ? "Screen time is read on this phone only. Nothing is sent anywhere."
                : "To show screen time, Android needs your OK. Tap the button, find Dinacharya Launcher in the list and switch it on.");
    }

    private void openUsageAccess() {
        UsageAccess.ask(a);
    }

    // ------------------------------------------------------------------ pro

    /** The card at the top of the Settings menu. */
    private void proCard() {
        boolean owned = Pro.isPro(a);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(a.dp(18), a.dp(16), a.dp(14), a.dp(16));
        card.setBackground(Ui.box(a, owned ? Ui.SURFACE : Ui.TEAL, 0, 22));
        card.setElevation(a.dp(1.5f));
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        int ink = owned ? Ui.INK : 0xFFFFFFFF;
        int soft = owned ? Ui.MUTED : 0xE6FFFFFF;
        words.addView(Ui.label(a, owned ? "Dinacharya Pro \u2713" : "Dinacharya Pro", 18, ink, true));
        String pass = owned ? "" : VideoPass.line(a);
        words.addView(Ui.label(a, owned ? "Thank you for supporting the app."
                : !pass.isEmpty() ? pass
                : VideoPass.offered(a)
                ? "No ads, every saved day and deeper analysis, goals, more pauses and locks, routines, colours. Or watch " + VideoProgress.NEED + " short videos for a free day of Pro"
                : "No ads, every saved day and deeper analysis, goals, more pauses and locks, routines, colours", 13, soft, false), Ui.wide(a, 3));
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(new Glyph(a, Glyph.CHEVRON, owned ? Ui.MUTED : 0xFFFFFFFF), new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        card.setOnClickListener(v -> a.openPro());
        body.addView(card, Ui.wide(a, 16));
    }

    // ------------------------------------------------------------------ alarms

    private void alarms() {
        header("Alarms");
        a.extras.buildAlarms(body);
        a.extras.refresh();
        body.addView(a.note("Each slot has a bell on the Routine tab: switch it off for slots that should not ring."), Ui.wide(a, 12));
        a.addFooter(body);
    }

    // ------------------------------------------------------------------ permissions

    private void permissions() {
        header("Permissions");
        boolean usage;
        try {
            usage = ScreenTime.hasAccess(a);
        } catch (RuntimeException e) {
            usage = false;
        }
        boolean notif = true;
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
            notif = nm == null || nm.areNotificationsEnabled();
        } catch (RuntimeException ignored) {
            // assume yes
        }
        boolean exact = AlarmScheduler.exactAllowed(a);
        boolean battery = false;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) a.getSystemService(Context.POWER_SERVICE);
            battery = pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
        } catch (RuntimeException ignored) {
            // assume no
        }
        LinearLayout card = Ui.section(a, body, "This phone", null);
        perm(card, "Usage access", "Phone time on the home screen and in reports.", usage, () -> openUsageAccess());
        perm(card, "Notifications", "The alarm question, also on the lock screen.", notif, () -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
            start(i);
        });
        perm(card, "Exact alarms", "Rings at the exact minute a slot starts.", exact, () -> {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                i.setData(Uri.parse("package:" + a.getPackageName()));
                start(i);
            }
        });
        perm(card, "No battery limits", "Keeps alarms and backups on time on phones that stop apps.", battery,
                () -> BatterySetup.ask(a));
        body.addView(a.note("Your routine and reports stay on this phone. Google Drive is used only for your own backup, "
                + "after you sign in. The free version shows small ads from Google AdMob, which may use your device's "
                + "advertising ID; Pro removes them. Nothing else is sent anywhere."), Ui.wide(a, 14));
        a.addFooter(body);
    }

    private void perm(LinearLayout parent, String name, String why, boolean ok, final Runnable fix) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, a.dp(8), 0, a.dp(8));
        TextView mark = Ui.label(a, ok ? "✓" : "!", 16, ok ? 0xFFFFFFFF : Ui.INK, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.box(a, ok ? Ui.TEAL : Ui.AMBER_SOFT, ok ? 0 : Ui.AMBER, 16));
        row.addView(mark, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(a.dp(12), 0, a.dp(8), 0);
        words.addView(Ui.label(a, name, 15, Ui.INK, true));
        words.addView(Ui.label(a, ok ? why : why + " Not allowed yet.", 12, Ui.MUTED, false));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!ok) {
            TextView btn = Ui.label(a, "Allow", 14, Ui.TEAL, true);
            btn.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8));
            btn.setBackground(Ui.pressable(a, Ui.TONAL, 0, 14, 0x300E7C7B));
            btn.setOnClickListener(v -> fix.run());
            row.addView(btn);
        }
        parent.addView(row, Ui.wide(a, 4));
    }

    private void start(Intent i) {
        try {
            a.startActivity(i);
        } catch (RuntimeException e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (RuntimeException e2) {
                a.toastText("Open the phone's Settings, then Apps, Dinacharya Launcher");
            }
        }
    }

    // ------------------------------------------------------------------ help

    private void help() {
        header("Help");
        LinearLayout card = Ui.section(a, body, "Quick guide", null);
        int tipNo = 0;
        final Accordion helpParts = new Accordion();   // only one tip, or the problem report, is open at a time
        String[][] tips = {
            {"Home screen", "Dinacharya Launcher is your home screen: no apps on it, just your day, with the slot you are in highlighted. "
                    + "Tap your day to open the dashboard, swipe up for every app, down for notifications, press and hold for its settings. Settings > Home screen has the rest, and Take the tour shows it all."},
            {"Routine", "Pick a start time; every slot follows. Drag ≡ to move a slot, swipe left to delete, swipe right to add one below. "
                    + "Tap ⋮ on a slot for notes, duplicating and more."},
            {"Weekdays and weekends", "On the Routine tab, add another routine and tap the days it is used on. "
                    + "Alarms, the home screen and reports follow the right one each day."},
            {"Alarms", "Turn alarms on from the Alarms card on Home. When one rings, say if you continue as planned or change the slot for today. "
                    + "If one does not ring, open Settings > Alarms: the checklist shows what is missing, and Ring a test alarm tries it in 10 seconds."},
            {"Goals", "On Insights, set a daily goal for a type such as Study. Kept slots of that type fill it up; days in a row make a streak."},
            {"Undo", "After deleting, moving or adding, a bar with Undo appears for a few seconds."},
            {"Backup", "On Backup, sign in with Google and choose Daily, Weekly or Monthly. Restore on a new phone from the same tab."},
        };
        for (String[] t : tips) {
            Expander.Part tip = helpParts.add(Expander.more(a, card, t[0], "help_tip_" + (tipNo++), false));
            tip.body.addView(a.note(t[1]), Ui.wide(a, 2));
        }
        String crash = CrashLog.lastCrash(a);
        String notes = CrashLog.notes(a);
        LinearLayout rep = helpParts.add(Expander.card(a, body, "Problem report", null,
                crash == null && notes.isEmpty() ? "Nothing written down" : "Some problems were written down", "help_report", false)).body;
        rep.addView(a.note(crash == null && notes.isEmpty()
                ? "No problems have been written down. If something goes wrong, the details are kept here so you can send them."
                : "Some problems were written down. Copy them and send them to the developer."), Ui.wide(a, 4));
        rep.addView(Ui.button(a, "Copy problem report", false, v -> {
            String text = "Dinacharya Launcher " + buildName(a) + " on Android " + Build.VERSION.RELEASE + " (" + Build.MANUFACTURER + " " + Build.MODEL + ")\n\n"
                    + "Alarms:\n" + a.extras.alarmReport() + "\n\n" + "Locks:\n" + StrongMode.report(a) + "\n\n" + Ads.get().status(a) + "\n\n"
                    + (crash == null ? "No crash recorded." : crash) + "\n\n" + (notes.isEmpty() ? "No handled problems." : notes);
            try {
                ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Dinacharya problem report", text));
                a.toastText("Report copied");
            } catch (RuntimeException e) {
                a.toastText("Could not copy");
            }
        }), Ui.wide(a, 10));
        a.addFooter(body);
    }

    // ------------------------------------------------------------------ about

    /** "1.0 (build 18)": the version and the build number of the app that is installed on this phone. */
    @SuppressWarnings("deprecation")
    private static String buildName(Context c) {
        try {
            android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
            return BackupData.APP_VERSION + " (build " + code + ")";
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) {
            return BackupData.APP_VERSION;
        }
    }

    private void about() {
        header("About");
        LinearLayout card = Ui.section(a, body, "Dinacharya Launcher", null);
        card.addView(Ui.label(a, "Version " + buildName(a), 15, Ui.INK, true), Ui.wide(a, 6));
        card.addView(a.note("A minimal home screen that is your daily routine, with alarms for every slot, weekday and weekend routines, "
                + "daily goals and reports, and a Google Drive backup. Dinacharya means “daily routine”."), Ui.wide(a, 6));
        BrandMark mark = new BrandMark(a);
        card.addView(mark, Ui.wide(a, 18));
        card.addView(a.note("Designed and developed by ASG."), Ui.wide(a, 4));
        card.addView(a.note("Free with small ads. Dinacharya Pro, monthly or for life, removes the ads and unlocks the extras."), Ui.wide(a, 8));
        a.addFooter(body);
    }
}
