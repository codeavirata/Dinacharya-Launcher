package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The home screen and focus pages of Settings. {@link #SEC_HOME} holds the home app, the swipes, the app list and the hidden apps;
 * {@link #SEC_FOCUS} holds the pauses, the locks (with the strong lock that makes them hold everywhere) and the phone use in slots.
 * Each page is a row of cards of which only one is open at a time; Settings opens the page with the card the person chose.
 */
public class LauncherSettingsActivity extends LangActivity {
    static final String EXTRA_SECTION = "section";
    static final String EXTRA_CARD = "card";
    static final int SEC_HOME = 0, SEC_FOCUS = 1;

    private interface IntChange {
        void set(int value);
    }

    private LauncherConfig cfg = new LauncherConfig();
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable passTimer = this::render;
    private LinearLayout body;
    private ScrollView sv;
    private List<AppEntry> apps = new ArrayList<>();
    private final Map<String, AppEntry> byKey = new HashMap<>();
    private int section = SEC_HOME;
    private Accordion cards = new Accordion();
    private final List<String> cardIds = new ArrayList<>();
    private String openId;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        section = getIntent().getIntExtra(EXTRA_SECTION, SEC_HOME) == SEC_FOCUS ? SEC_FOCUS : SEC_HOME;
        openId = getIntent().getStringExtra(EXTRA_CARD);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView back = Ui.label(this, "‹  Back", 15, Ui.TEAL_DARK, true);
        back.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 4));
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView title = Ui.label(this, section == SEC_FOCUS ? "Focus and locks" : "Home screen", 22, Ui.INK, true);
        title.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        root.addView(title);

        sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Insets.pad(root);
        setContentView(root);

        final AppSource source = new AppSource(this);
        new Thread(() -> {
            final List<AppEntry> list = source.load();
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                apps = list;
                byKey.clear();
                for (AppEntry e : apps) byKey.put(e.key, e);
                render();
            });
        }, "launcher-settings-apps").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        StrongMode.afterReturn(this);   // coming back from Android's permission pages starts (or stops) the strong lock, and asks for the next one
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(passTimer);
        super.onDestroy();
    }

    /** Draws the page again just after the running pass ends, so the pause and lock cards go back to the free version. */
    private void armPassTimer() {
        ui.removeCallbacks(passTimer);
        long until = Pro.get(this).passUntil();
        if (until <= 0) return;
        long wait = until - System.currentTimeMillis() + 1500L;
        ui.postDelayed(passTimer, Math.max(1000L, Math.min(wait, DayPass.MAX_MS)));
    }

    // ------------------------------------------------------------------ helpers

    private void save() {
        LauncherStore.save(this, cfg);
    }

    private String nameOf(String key) {
        if (key == null || key.isEmpty()) return "Nothing";
        if (AppKey.isAction(key)) return AppKey.actionName(key);
        AppEntry e = byKey.get(key);
        return e == null ? AppKey.pkg(key) : e.shown();
    }

    private List<AppEntry> visibleApps() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) if (!cfg.hidden.contains(e.key)) out.add(e);
        return out;
    }

    /** The apps a pause or a lock can be given to: every visible app except Dinacharya itself. */
    private List<AppEntry> ruleApps() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : visibleApps()) if (!getPackageName().equals(e.pkg)) out.add(e);
        return out;
    }

    private TextView note(String text) {
        TextView t = Ui.label(this, text, 13, Ui.MUTED, false);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        return t;
    }

    private TextView smallButton(String text, View.OnClickListener l) {
        TextView t = Ui.label(this, text, 14, Ui.TEAL_DARK, true);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        t.setBackground(Ui.pressable(this, Ui.TONAL, 0, 14, Ui.RIPPLE));
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        return t;
    }

    /** A line with a title and a short description on the left and a small button on the right. */
    private LinearLayout row(String title, String sub, String action, final Runnable run) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(0, 0, Ui.dp(this, 8), 0);
        words.addView(Ui.label(this, title, 15, Ui.INK, true));
        if (sub != null && !sub.isEmpty()) words.addView(Ui.label(this, sub, 12, Ui.MUTED, false));
        r.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(smallButton(action, v -> run.run()));
        return r;
    }

    private LinearLayout stepper(String label, int value, final String unit, final int min, final int max, final int step,
                                 final IntChange change) {
        final int[] v = {value};
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        r.addView(Ui.label(this, label, 15, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView num = Ui.label(this, value + unit, 15, Ui.INK, true);
        num.setGravity(Gravity.CENTER);
        num.setMinWidth(Ui.dp(this, 64));
        Button minus = Ui.iconButton(this, "−", Ui.TEAL_DARK, x -> {
            v[0] = Math.max(min, v[0] - step);
            num.setText(v[0] + unit);
            change.set(v[0]);
        });
        Button plus = Ui.iconButton(this, "+", Ui.TEAL_DARK, x -> {
            v[0] = Math.min(max, v[0] + step);
            num.setText(v[0] + unit);
            change.set(v[0]);
        });
        r.addView(minus);
        r.addView(num);
        r.addView(plus);
        return r;
    }

    /** A card of the page: it joins the row, so opening it closes the one that was open. */
    private Expander.Part card(String id, String title, String summary) {
        Expander.Part p = cards.add(Expander.card(this, body, title, null, summary, null, false));
        cardIds.add(id);
        return p;
    }

    private void addRow(LinearLayout card, View v, float topDp) {
        card.addView(v, Ui.wide(this, topDp));
    }

    // ------------------------------------------------------------------ the page

    private void render() {
        if (body == null) return;
        PassSettle.run(this);   // a video pass that has ended gives back what was made with it
        cfg = LauncherStore.load(this);
        String ended = PassSettle.takeMessage(this);
        if (ended != null) Lang.toast(this, ended, Toast.LENGTH_LONG).show();
        armPassTimer();
        int y = sv.getScrollY();
        body.removeAllViews();
        cards = new Accordion();
        cardIds.clear();
        if (section == SEC_FOCUS) {
            final int open = Pro.open(this);   // what is open now: everything with Pro, or the one item a video opened
            focusCard(open);
            lockCard(open);
            quietCard();
            useCard();
            todayCard();
        } else {
            homeAppCard();
            gesturesCard();
            lookCard();
            hiddenCard();
            resetCard();
        }
        int idx = cardIds.indexOf(openId);
        if (idx < 0 && cardIds.size() == 1) idx = 0;
        cards.onChanged(() -> openId = cards.open() >= 0 && cards.open() < cardIds.size() ? cardIds.get(cards.open()) : null);
        cards.show(idx);
        body.addView(Ui.rule(this), Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1), 28));
        body.addView(new BrandMark(this), Ui.wide(this, 8));
        final int keep = y;
        sv.post(() -> sv.scrollTo(0, keep));
    }

    private void homeAppCard() {
        boolean def = HomeRole.isDefault(this);
        LinearLayout c = card("home", "Home app",
                def ? "Dinacharya Launcher is your home app" : "Not your home app yet").body;
        addRow(c, note("No apps on the home screen: your day is the background. Swipe up for every app."), 6);
        addRow(c, Ui.statusRow(this, def ? "Dinacharya Launcher is your home app" : "Not your home app yet",
                def ? "Pressing Home shows this screen." : "Press Try it to look first.", def, "Set", () -> HomeRole.request(this)), 8);
        boolean wall = WallpaperSetup.settled(this);
        boolean lockOnly = WallpaperSetup.lockOnly(this);
        boolean no = WallpaperSetup.declined(this);
        LinearLayout wallRow = Ui.statusRow(this, wall ? "The Dinacharya Launcher wallpaper is on" : lockOnly ? "Only the lock screen has it"
                        : no ? "Day wallpaper: off" : "Wallpaper not in place yet",
                wall ? "Your day shows on the home screen, lock screen and recent apps."
                        : lockOnly ? "On Android’s preview, choose Home screen (or both)."
                        : no ? "Optional. The home screen still shows your day. Set it to show it on the lock screen and in recent apps too."
                        : "Optional. Android asks you to confirm once.", wall, "Set", () -> WallpaperSetup.open(this));
        if (no && !wall && !lockOnly && wallRow.getChildAt(0) instanceof TextView) {
            // said no on purpose: a quiet dash, not an amber warning
            TextView mark = (TextView) wallRow.getChildAt(0);
            mark.setText("–");
            mark.setTextColor(Ui.MUTED);
            mark.setBackground(Ui.box(this, Ui.TONAL, 0, 16));
        }
        addRow(c, wallRow, 2);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button tryIt = Ui.button(this, "Try it now", true, v -> startActivity(new Intent(this, LauncherActivity.class)));
        Button change = Ui.button(this, "Change home app", false, v -> HomeRole.openChooser(this));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a.rightMargin = Ui.dp(this, 6);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b2.leftMargin = Ui.dp(this, 6);
        btns.addView(tryIt, a);
        btns.addView(change, b2);
        addRow(c, btns, 10);
        addRow(c, note("You can go back to your old home screen at any time: tap Change home app and pick it. "
                + "Nothing is removed from your phone."), 10);
    }

    private void gesturesCard() {
        LinearLayout c = card("swipes", "Shortcut", nameOf(cfg.swipeLeft) + "  ·  " + nameOf(cfg.swipeRight)).body;
        addRow(c, row("Swipe left", nameOf(cfg.swipeLeft), "Change", () -> pickSwipe(true)), 6);
        addRow(c, row("Swipe right", nameOf(cfg.swipeRight), "Change", () -> pickSwipe(false)), 2);
        addRow(c, note("Swipe up: all apps. Swipe down: notifications. Press and hold anywhere: these settings. "
                + "In the list of all apps, press and hold an app to add a pause, hide it, or open its info."), 8);
    }

    private void pickSwipe(final boolean left) {
        AppPicker.show(this, Ui.dialogTheme(), left ? "Swipe left opens" : "Swipe right opens", visibleApps(),
                new String[]{"Nothing", "Camera", "Phone", "Dinacharya routine"},
                new String[]{"", AppKey.CAMERA, AppKey.PHONE, AppKey.DASHBOARD}, key -> {
                    if (left) cfg.swipeLeft = key;
                    else cfg.swipeRight = key;
                    save();
                    render();
                });
    }

    private void lookCard() {
        LinearLayout c = card("list", "App list", "Colours, text size and the keyboard in the list of all apps").body;
        addRow(c, Ui.label(this, "Colours of the list of all apps and the pause screen", 14, Ui.MUTED, true), 4);
        Segmented theme = new Segmented(this, LauncherConfig.THEME_NAMES);
        theme.pick(cfg.theme, false);
        theme.setListener(i -> {
            cfg.theme = i;
            save();
        });
        addRow(c, theme, 4);
        addRow(c, Ui.label(this, "Text size in the list of all apps", 14, Ui.MUTED, true), 12);
        Segmented size = new Segmented(this, LauncherConfig.SIZE_NAMES);
        size.pick(cfg.size, false);
        size.setListener(i -> {
            cfg.size = i;
            save();
        });
        addRow(c, size, 4);
        addRow(c, Ui.toggle(this, "Open an app when only one matches", cfg.autoOpen, (b, on) -> {
            cfg.autoOpen = on;
            save();
        }), 10);
        addRow(c, Ui.toggle(this, "Open the keyboard in the list of all apps", cfg.keyboard, (b, on) -> {
            cfg.keyboard = on;
            save();
        }), 0);
    }

    /** The switch for pausing apps during one kind of slot, as a whole phrase (so it can be translated as one). */
    private static String kindTime(int kind) {
        switch (kind) {
            case 0: return "Task time";
            case 1: return "Rest time";
            case 2: return "Meal time";
            case 3: return "Personal time";
            case 4: return "Sleep time";
            default: return Routine.KIND_NAMES[Math.max(0, Math.min(Routine.KIND_NAMES.length - 1, kind))];
        }
    }

    private static String times(int n) {
        return n == 1 ? "1 time" : n + " times";
    }

    private String ruleSummary(LauncherConfig.Rule r, int open) {
        StringBuilder sb = new StringBuilder();
        if (r.mode == LauncherConfig.RULE_STRICT) sb.append("Strict pause, ").append(cfg.strictSec).append(" s");
        else if (r.mode == LauncherConfig.RULE_NUDGE) sb.append("Short pause, ").append(cfg.pauseSec).append(" s");
        else sb.append("No pause");
        if (r.challenge > 0 && DayPass.has(open, DayPass.PAUSE_PLAYFUL)) sb.append("  ·  ").append(PauseChallenge.name(r.challenge).toLowerCase(java.util.Locale.ROOT));
        if (r.limitMin > 0) sb.append("  ·  limit ").append(Routine.formatDuration(r.limitMin)).append(" a day");
        if (r.always) sb.append("  ·  also outside focus times");
        if (!FocusRules.active(cfg, r.key, open)) sb.append("  ·  needs Pro");
        else if ((r.mode == LauncherConfig.RULE_STRICT && !DayPass.has(open, DayPass.PAUSE_STRICT))
                || (r.limitMin > 0 && !DayPass.has(open, DayPass.PAUSE_LIMIT))
                || (r.challenge > 0 && !DayPass.has(open, DayPass.PAUSE_PLAYFUL))) sb.append("  ·  Pro part off");
        return sb.toString();
    }

    /** The kinds of slot that count as focus time, as a short line for the closed "Pause during" part. */
    private String focusKindsText() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) {
            if (!cfg.isFocusKind(k)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(kindTime(k));
        }
        return sb.length() == 0 ? "None" : sb.toString();
    }

    private void focusCard(final int open) {
        LinearLayout c = card("pauses", "Focus",
                cfg.rules.isEmpty() ? "A pause before apps open: none yet"
                        : cfg.rules.size() == 1 ? "1 app has a pause" : cfg.rules.size() + " apps have a pause").body;
        final Accordion focusParts = new Accordion();   // "How it works" and "Pause during": one open at a time
        Expander.Part focusHow = focusParts.add(Expander.more(this, c, "How it works", "set_focus_how", false));
        // whole sentences, so that each can be translated as one piece
        addRow(focusHow.body, note("Pick apps that pull you away. "
                + (strongOn() ? "When you open one, it waits a few seconds first, so you can go back or carry on. "
                : "When you open one from this home screen, it waits a few seconds first, so you can go back or carry on. ")
                + "It never locks you out: Open anyway is always there."), 4);
        final Expander.Part during = focusParts.add(Expander.more(this, c, "Pause during", "set_focus_during", false));
        during.setSummary(focusKindsText());
        for (int k = 0; k < Routine.KIND_NAMES.length; k++) {
            final int kind = k;
            addRow(during.body, Ui.toggle(this, kindTime(k), cfg.isFocusKind(k), (b, on) -> {
                cfg.setFocusKind(kind, on);
                save();
                during.setSummary(focusKindsText());
            }), 0);
        }
        addRow(c, stepper("Short pause", cfg.pauseSec, " s", 3, 60, 1, v -> {
            cfg.pauseSec = v;
            save();
        }), 8);
        addRow(c, stepper(DayPass.has(open, DayPass.PAUSE_STRICT) ? "Strict pause" : "Strict pause (Pro)", cfg.strictSec, " s", 5, 120, 5, v -> {
            cfg.strictSec = v;
            save();
        }), 0);

        addRow(c, Ui.label(this, "Apps with a pause", 14, Ui.MUTED, true), 14);
        if (cfg.rules.isEmpty()) {
            addRow(c, note("None yet. Add one below, or press and hold an app in the list of all apps."), 4);
        }
        for (int i = 0; i < cfg.rules.size(); i++) {
            final LauncherConfig.Rule r = cfg.rules.get(i);
            addRow(c, row(nameOf(r.key), ruleSummary(r, open), "Edit", () -> {
                AppEntry e = byKey.get(r.key);
                if (e == null) e = new AppEntry(r.key, AppKey.pkg(r.key), false);
                FocusDialog.show(this, Ui.dialogTheme(), cfg, e, Pro.open(this), this::render);
            }), 2);
        }
        Button add = Ui.button(this, "Add a pause for an app", false, v -> AppPicker.show(this, Ui.dialogTheme(), "Add a pause for", ruleApps(),
                null, null, key -> {
                    AppEntry e = byKey.get(key);
                    if (e != null) FocusDialog.show(this, Ui.dialogTheme(), cfg, e, Pro.open(this), this::render);
                }));
        addRow(c, add, 10);
        if (!Pro.get(this).owned()) {
            addRow(c, note("Free: a pause rule for " + FocusRules.FREE_APPS + " app. Pro adds strict pauses, daily limits, "
                    + "playful pauses (a sum, a sentence, breathing), as many apps as you like, and removes the ads."), 10);
            addRow(c, Ui.button(this, "See Pro", true, v -> ProPrompt.open(this)), 8);
            LinearLayout offer = new LinearLayout(this);
            offer.setOrientation(LinearLayout.VERTICAL);
            ProPrompt.addOffer(this, offer, 0, this::render);
            if (offer.getChildCount() > 0) addRow(c, offer, 8);
        }
        if (DayPass.has(open, DayPass.PAUSE_LIMIT)) {
            addRow(c, Ui.statusRow(this, "Usage access", "Needed to count the minutes for daily limits",
                    ScreenTime.hasAccess(this), "Allow", () -> UsageAccess.ask(this)), 10);
        }
    }

    // ------------------------------------------------------------------ phone use in slots

    /** Which kind's dropdown was open in the card for phone use in slots (kept when the page is drawn again). */
    private int useOpen = -1;

    private static String useKindName(int kind) {
        return Routine.KIND_NAMES[Math.max(0, Math.min(Routine.KIND_NAMES.length - 1, kind))];
    }

    /** The apps chosen for an activity as a short line: "Notes, Docs +2", or that none are chosen. */
    private String useAppsText(int kind, String sub) {
        List<String> keys = cfg.appsOfType(kind, sub);
        if (keys.isEmpty()) return "No apps chosen";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String k : keys) {
            if (shown == 2) break;
            if (shown > 0) sb.append(", ");
            sb.append(nameOf(k));
            shown++;
        }
        if (keys.size() > shown) sb.append(" +").append(keys.size() - shown);
        return sb.toString();
    }

    private void useCard() {
        int n = cfg.typeApps.size();
        String summary = !cfg.trackUse ? "Off. Mark the phone use that is not part of a slot's activity"
                : n == 0 ? "On. Every app counts as phone use"
                : n == 1 ? "On. Apps chosen for 1 activity" : "On. Apps chosen for " + n + " activities";
        if (!Limits.analysisAllowed(Pro.has(this, DayPass.ANALYSIS))) {
            LinearLayout locked = card("use", "Phone use in slots · Pro", "Part of Pro").body;
            addRow(locked, note("See when you pick up the phone during a slot, with the exact times, and mark the apps that belong to an "
                    + "activity so they are never counted against you. The reports then show it slot by slot." + (cfg.trackUse
                    ? " Your choices are kept and work again with Pro." : "")), 4);
            addRow(locked, Ui.button(this, "See Pro", true, v -> ProPrompt.open(this)), 8);
            LinearLayout offer = new LinearLayout(this);
            offer.setOrientation(LinearLayout.VERTICAL);
            ProPrompt.addOffer(this, offer, 0, this::render);
            if (offer.getChildCount() > 0) addRow(locked, offer, 8);
            return;
        }
        LinearLayout c = card("use", "Phone use in slots", summary).body;
        addRow(c, note("See when you pick up the phone during a slot. The home screen marks it with the exact times and the reports "
                + "count it. Apps you choose for an activity are part of it and are never marked."), 4);
        addRow(c, Ui.toggle(this, "Show phone use during slots", cfg.trackUse, (b, on) -> {
            if (on == cfg.trackUse) return;
            cfg.trackUse = on;
            save();
            render();
        }), 6);
        if (!cfg.trackUse) return;
        addRow(c, Ui.statusRow(this, "Usage access", "Needed to see which apps are used, and when", ScreenTime.hasAccess(this),
                "Allow", () -> UsageAccess.ask(this)), 6);

        addRow(c, Ui.label(this, "Apps that are part of an activity", 14, Ui.MUTED, true), 12);
        addRow(c, note("Choose for each activity the apps it needs (a notes app for Study, say). Any other app used during that "
                + "activity's slot is marked. This app is always part of the slot."), 4);
        Routine routine;
        try {
            routine = Store.load(this);
        } catch (RuntimeException e) {
            CrashLog.note(this, "phone use settings", e);
            return;
        }
        final Accordion kinds = new Accordion();
        for (int k = 0; k < Routine.SLEEP; k++) {
            final int kind = k;
            List<Routine.Sub> subs = routine.subsOf(kind);
            int with = 0;
            for (Routine.Sub s : subs) if (!cfg.appsOfType(kind, s.name).isEmpty()) with++;
            Expander.Part p = kinds.add(Expander.more(this, c, useKindName(kind), null, false));
            p.setSummary(with == 0 ? "No apps chosen yet" : with == 1 ? "1 activity has apps" : with + " activities have apps");
            for (final Routine.Sub s : subs) {
                addRow(p.body, row(s.name, useAppsText(kind, s.name), "Choose", () -> chooseUseApps(kind, s.name)), 0);
            }
        }
        kinds.onChanged(() -> useOpen = kinds.open());
        kinds.show(useOpen);
    }

    /** The list of apps for one activity: tick the ones that are part of it. Nothing is kept until Done. */
    private void chooseUseApps(final int kind, final String sub) {
        final List<AppEntry> list = ruleApps();
        final Set<String> picked = new LinkedHashSet<>(cfg.appsOfType(kind, sub));   // an app that is gone from the list stays as it is
        final String[] names = new String[list.size()];
        final boolean[] on = new boolean[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).shown();
            on[i] = picked.contains(list.get(i).key);
        }
        new DBuilder(this, Ui.dialogTheme()).setAppChoices(names, on, (dlg, which, checked) -> {
                    String key = list.get(which).key;
                    if (!checked) {
                        picked.remove(key);
                        return;
                    }
                    if (picked.size() >= LauncherConfig.MAX_USE_APPS) {
                        ((AlertDialog) dlg).getListView().setItemChecked(which, false);
                        Lang.toast(this, "Up to " + LauncherConfig.MAX_USE_APPS + " apps for one activity", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    picked.add(key);
                })
                .setTitle("Apps for " + sub)
                .setPositiveButton("Done", (dlg, w) -> {
                    if (!cfg.setTypeApps(kind, sub, new ArrayList<>(picked))) {
                        Lang.toast(this, "Up to " + LauncherConfig.MAX_USE_TYPES + " activities can have apps", Toast.LENGTH_LONG).show();
                        return;
                    }
                    save();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void lockCard(final int open) {
        final boolean more = DayPass.has(open, DayPass.LOCK_MORE);
        LinearLayout c = card("locks", more ? "Locked groups" : "Locked apps",
                (cfg.groups.isEmpty() ? (more ? "Lock a group of apps for a time" : "Lock apps for a time")
                        : cfg.groups.size() == 1 ? "1 lock" : cfg.groups.size() + " locks")
                        + (GuardRules.needsPermission(StrongMode.state(this))
                        ? "  ·  Needs your permission: locks hold only from this home screen" : "")).body;
        Expander.Part lockHow = Expander.more(this, c, "How locks work", "set_lock_how", false);
        addRow(lockHow.body, note((DayPass.has(open, DayPass.LOCK_APPS)
                ? "Put the apps that eat your time in a group, then lock the group for hours you choose, on the days you choose, or just for the next hour. "
                : "Pick the apps that eat your time, then lock them for hours you choose, on the days you choose, or just for the next hour. ")
                + (strongOn() ? "A locked app does not open until the lock ends. "
                : "A locked app does not open from this home screen until the lock ends. ")
                + "You can break a lock by waiting 30 seconds and typing a sentence; a strict lock (Pro) cannot be broken."), 4);
        boolean h24 = false;
        try {
            h24 = Store.load(this).fmt24;
        } catch (RuntimeException ignored) {
            // the 12-hour clock is the fallback
        }
        final boolean clock24 = h24;
        long t = System.currentTimeMillis();
        final LockRules.Slot slotNow = SlotNow.read(this).toLock();
        if (cfg.groups.isEmpty()) {
            addRow(c, note("No groups yet."), 8);
        }
        for (int i = 0; i < cfg.groups.size(); i++) {
            final LauncherConfig.Group g = cfg.groups.get(i);
            StringBuilder sb = new StringBuilder();
            int held = LockRules.heldApps(cfg, g, open).size();
            sb.append(g.apps.size()).append(g.apps.size() == 1 ? " app" : " apps").append("  ·  ");
            sb.append(LockRules.status(g, t, Store.zone(), h24, slotNow));
            if (g.hide) sb.append("  ·  hides its apps");
            if (g.strict) sb.append("  ·  strict");
            if (!LockRules.active(cfg, g, open)) sb.append("  ·  needs Pro");
            else if (held < g.apps.size()) sb.append("  ·  only ").append(held).append(" held, needs Pro");
            else if (g.strict && !DayPass.has(open, DayPass.LOCK_STRICT)) sb.append("  ·  strict part off");
            addRow(c, row(g.name, sb.toString(), "Edit", () ->
                    GroupDialog.show(this, Ui.dialogTheme(), cfg, g, visibleApps(), Pro.open(this), clock24, this::render)), 2);
        }
        Button add = Ui.button(this, more ? "Add a locked group" : "Lock apps", false, v -> {
            final int now = Pro.open(this);
            if (!DayPass.has(now, DayPass.LOCK_MORE) && cfg.groups.size() >= LockRules.FREE_GROUPS) {
                ProPrompt.show(this, DayPass.LOCK_MORE, "More than one lock", Ui.dialogTheme(), this::render);
                return;
            }
            GroupDialog.show(this, Ui.dialogTheme(), cfg, null, visibleApps(), now, clock24, this::render);
        });
        addRow(c, add, 10);
        holdsEverywhere(c, lockHow);
        if (!Pro.get(this).owned()) {
            addRow(c, note("Free: one lock that holds " + LockRules.FREE_GROUP_APPS + " app. Pro adds groups of apps, as many locks as you like, "
                    + "and strict locks."), 6);
            addRow(c, Ui.button(this, "See Pro", true, v -> ProPrompt.open(this)), 8);
            LinearLayout offer = new LinearLayout(this);
            offer.setOrientation(LinearLayout.VERTICAL);
            ProPrompt.addOffer(this, offer, 0, this::render);
            if (offer.getChildCount() > 0) addRow(c, offer, 8);
        }
    }

    /** Library mode (what it does, when it is on) and Quiet mode (rules that set the ringer by time or activity), in one card. */
    private void quietCard() {
        final long t = System.currentTimeMillis();
        final QuietRules q = Store.quiet(this);
        final LibraryMode lib = Store.library(this);
        boolean h24 = false;
        try {
            h24 = Store.load(this).fmt24;
        } catch (RuntimeException ignored) {
            // the 12-hour clock is the fallback
        }
        final boolean clock24 = h24;
        LinearLayout c = card("quiet", "Library and Quiet mode",
                (lib.on(t, Store.zone()) ? "Library mode is on" : "Library mode is off")
                        + "  ·  " + (q.rules.isEmpty() ? "no quiet rules" : q.rules.size() == 1 ? "1 quiet rule" : q.rules.size() + " quiet rules")).body;

        Expander.Part libPart = Expander.more(this, c, "Library mode", "set_library", false);
        new LibrarySettings(this).build(libPart);

        Expander.Part quietPart = Expander.more(this, c, "Quiet mode", "set_quiet", false);
        LinearLayout qb = quietPart.body;
        quietPart.setSummary(q.rules.isEmpty() ? "Off" : q.rules.size() == 1 ? "1 rule" : q.rules.size() + " rules");
        addRow(qb, note("Make the phone ring, vibrate, go silent or switch on Do Not Disturb by itself: at hours on days you choose, during an "
                + "activity type (like Study), or only when both hold. When a rule ends, the phone goes back to how it was, unless you changed "
                + "it yourself meanwhile. If rules overlap, the quietest wins."), 4);
        if (q.rules.isEmpty()) addRow(qb, note("No quiet rules yet."), 8);
        for (final QuietRules.Rule r : q.rules) {
            addRow(qb, row(r.name, r.text(clock24), "Edit", () -> QuietDialog.show(this, Ui.dialogTheme(), q, r, clock24, this::render)), 2);
        }
        addRow(qb, Ui.button(this, "Add a quiet rule", false, v -> {
            if (q.rules.size() >= QuietRules.MAX_RULES) {
                Lang.toast(this, "That is the most rules you can have", Toast.LENGTH_SHORT).show();
                return;
            }
            QuietDialog.show(this, Ui.dialogTheme(), q, null, clock24, this::render);
        }), 10);
        boolean needs = false;
        for (QuietRules.Rule r : q.rules) needs |= r.sound == QuietRules.SILENT || r.sound == QuietRules.DND;
        if (needs && !Quiet.hasAccess(this)) {
            addRow(qb, Ui.statusRow(this, "Do Not Disturb access", "Needed for Silent and Do Not Disturb. Until then the phone only vibrates",
                    false, "Allow", () -> QuietDialog.openAccess(this)), 8);
        }
    }

    /** True when the strong lock is on and has what it needs (whether or not anything is locked yet). */
    private boolean strongOn() {
        return GuardRules.wantsStrong(cfg) && StrongMode.hasUsage(this) && StrongMode.hasOverlay(this);
    }

    /**
     * What makes a lock hold whichever way an app is opened. It has no switch: it starts by itself when something is locked or paused,
     * and Android asks once for the two things it needs. Here is where those two things are allowed, under the locks they serve.
     * A group's own "strict" choice is separate: it only decides whether that lock can be broken.
     */
    private void holdsEverywhere(LinearLayout c, Expander.Part how) {
        if (GuardRules.wantsStrong(cfg)) {   // only while a strict lock or pause is set: the strict choice is what switches it on
            addRow(c, note(StrongMode.line(StrongMode.state(this))), 12);
            addRow(c, Ui.statusRow(this, "Usage access", "Lets Dinacharya Launcher see which app is in front",
                    StrongMode.hasUsage(this), "Allow", () -> UsageAccess.ask(this, true)), 6);
            addRow(c, Ui.statusRow(this, "Display over other apps", "Lets the lock screen appear over an app",
                    StrongMode.hasOverlay(this), "Allow", () -> StrongMode.askOverlayExplained(this)), 2);
        }
        addRow(how.body, note(strongOn()
                ? "A strict lock also holds an app opened from Recents, App info, a notification, a widget or another home app, "
                + "and one that is open when the lock starts."
                : "A lock holds when you open an app from this home screen. A strict lock also holds an app opened from Recents, App info, "
                + "a notification, a widget or another home app, and one that is open when the lock starts. For that, allow the two things "
                + "that show once a strict lock is set."), 8);
        addRow(how.body, note("It shows a small notification while it runs. It looks only at the name of the app in front, on this phone, "
                + "and keeps and sends nothing. The phone, Settings, the keyboard and system screens are never blocked. If your phone stops "
                + "background apps to save battery and the lock stops holding, allow Dinacharya Launcher to run in the background in the "
                + "phone's battery settings."), 8);
    }

    private void hiddenCard() {
        LinearLayout c = card("hidden", "Hidden apps",
                cfg.hidden.isEmpty() ? "None" : cfg.hidden.size() == 1 ? "1 app is hidden" : cfg.hidden.size() + " apps are hidden").body;
        if (cfg.hidden.isEmpty()) {
            addRow(c, note("None. In the list of all apps, press and hold an app to hide it."), 6);
            return;
        }
        addRow(c, note(cfg.hidden.size() == 1 ? "1 app is hidden from the list of all apps and from search."
                : cfg.hidden.size() + " apps are hidden from the list of all apps and from search."), 6);
        addRow(c, Ui.button(this, "Show some again", false, v -> unhideDialog()), 8);
    }

    private void unhideDialog() {
        final List<String> keys = new ArrayList<>(cfg.hidden);
        String[] names = new String[keys.size()];
        final boolean[] hidden = new boolean[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            names[i] = nameOf(keys.get(i));
            hidden[i] = true;
        }
        new DBuilder(this, Ui.dialogTheme())
                .setTitle("Hidden apps")
                .setMultiChoiceItems(names, hidden, (d, which, checked) -> hidden[which] = checked)
                .setPositiveButton("Done", (d, w) -> {
                    for (int i = 0; i < keys.size(); i++) if (!hidden[i]) cfg.hidden.remove(keys.get(i));
                    save();
                    render();
                })
                .setNegativeButton("Cancel", null)
                .setMessage(null)
                .show();
    }

    private void todayCard() {
        int pauses = 0, back = 0, opened = 0;
        try {
            long start = LocalDate.now(Store.zone()).atStartOfDay(Store.zone()).toInstant().toEpochMilli();
            for (Ev e : Store.history(this).events()) {
                if (!Ev.LAUNCH.equals(e.type) || e.ts < start) continue;
                pauses++;
                if ("B".equals(e.a)) back++;
                else opened++;
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "home screen today", e);
        }
        LinearLayout c = card("today", "Today",
                pauses == 0 ? "No pauses so far today." : pauses == 1 ? "1 pause today" : pauses + " pauses today").body;
        if (pauses == 0) {
            addRow(c, note("No pauses so far today."), 6);
        } else {
            addRow(c, Ui.label(this, pauses == 1 ? "1 pause today" : pauses + " pauses today", 16, Ui.INK, true), 6);
            addRow(c, note("You went back " + times(back) + " and opened the app anyway " + times(opened)
                    + ". The day report keeps this count."), 4);
        }
    }

    private void resetCard() {
        Button reset = Ui.button(this, "Reset these settings", false, v -> {
            long until = strictLockUntil();
            if (until != 0) {
                new DBuilder(this, Ui.dialogTheme()).setTitle("A strict lock is holding")
                        .setMessage("The settings cannot be reset while a strict lock holds. It ends at "
                                + LockRules.untilText(until, System.currentTimeMillis(), Store.zone(), clock24()) + ".")
                        .setPositiveButton("OK", null).show();
                return;
            }
            new DBuilder(this, Ui.dialogTheme())
                    .setTitle("Reset the home screen?")
                    .setMessage("This clears the swipes, the look, the hidden apps, the pause rules, the locked groups and the phone use choices. "
                            + "Your routine and reports are not touched.")
                    .setPositiveButton("Reset", (d, w) -> {
                        LauncherStore.save(this, new LauncherConfig());
                        render();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        body.addView(reset, Ui.wide(this, 20));
    }

    /** When the latest strict lock that holds right now ends, or 0 when none holds. */
    private long strictLockUntil() {
        return LockRules.strictUntil(cfg, Pro.open(this), System.currentTimeMillis(), Store.zone(), SlotNow.read(this).toLock());
    }

    private boolean clock24() {
        try {
            return Store.load(this).fmt24;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
