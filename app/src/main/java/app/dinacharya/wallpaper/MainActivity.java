package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The main screen: five tabs on a bar at the bottom (Home, Routine, Insights, Backup, Settings).
 * Holds the routine being edited and saves it; the tabs themselves are in the *Page classes.
 */
public class MainActivity extends LangActivity {
    static final int TAB_HOME = 0, TAB_ROUTINE = 1, TAB_INSIGHTS = 2, TAB_BACKUP = 3, TAB_SETTINGS = 4;
    private static final String[] TAB_NAMES = {"Home", "Routine", "Insights", "Backup", "Settings"};
    private static final int[] TAB_ICONS = {Glyph.HOME, Glyph.ROUTINE, Glyph.CHART, Glyph.CLOUD, Glyph.GEAR};
    static final int REQ_GOOGLE = 81;
    /** An extra other screens can send to open a page: "pro" opens the Dinacharya Pro page, "ready" shows the end-of-setup message. */
    static final String EXTRA_OPEN = "open";
    /** Typing is saved this long after the last key, so the phone is not busy on every letter. */
    private static final long SAVE_DELAY_MS = 500;
    private static final long UNDO_MS = 6000;

    private Routine routine;
    ExtraSections extras;
    private final Page[] pages = new Page[5];
    private final Glyph[] tabIcons = new Glyph[5];
    private final TextView[] tabLabels = new TextView[5];
    private final View[] tabPills = new View[5];
    private FrameLayout content;
    private int tab = -1;
    private final List<PosterView> previews = new ArrayList<>();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean savePending;
    private final Runnable saveTask = new Runnable() {
        @Override
        public void run() {
            persistNow();
        }
    };

    private LinearLayout undoBar;
    private TextView undoText;
    private String undoSnapshot;
    private final Runnable hideUndo = new Runnable() {
        @Override
        public void run() {
            hideUndoBar();
        }
    };

    private PickerAuth.Callback googleCallback;
    private final Pro.Listener proListener = this::monetizationChanged;
    /** The count of pass resets this screen has taken into account (see {@link PassSettle}). */
    private int passEpoch;
    /** True while the "make Dinacharya your home app again" offer has been made during this visit. */
    private boolean homeOffered;
    private final Runnable passTimer = () -> {
        settlePass();
        monetizationChanged();   // the locked and open states of the pages follow the end of the pass
    };

    // ------------------------------------------------------------------ life cycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(this);
        PassSettle.run(this);   // a video pass that ended while the app was closed gives its changes back first
        passEpoch = PassSettle.epoch();
        routine = Store.load(this);
        Store.since(this);
        extras = new ExtraSections(this);
        pages[TAB_HOME] = new HomePage(this);
        pages[TAB_ROUTINE] = new RoutinePage(this);
        pages[TAB_INSIGHTS] = new InsightsPage(this);
        pages[TAB_BACKUP] = new BackupPage(this);
        pages[TAB_SETTINGS] = new SettingsPage(this);
        buildShell();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        int start = savedInstanceState != null ? savedInstanceState.getInt("tab", TAB_HOME) : TAB_HOME;
        go(start);
        if (savedInstanceState == null) handleOpenExtra(getIntent());

        AlarmScheduler.reschedule(this);
        DayCloser.schedule(this);
        DriveJob.schedule(this);
        Pro.get(this).addListener(proListener);
        Ads.get().setOnReady(this::monetizationChanged);
        Pro.get(this).start();
        Ads.get().start(this);
        PassSettle.schedule(this);
        showPassMessage();
        armPassTimer();
        // Close any finished days that were missed while the phone was off, then refresh what shows them.
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                DayCloser.closeFinished(app);
            } catch (RuntimeException e) {
                CrashLog.note(app, "closing days", e);
            }
            runOnUiThread(() -> {
                if (!isFinishing()) refreshVisible();
            });
        }, "close-days").start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenExtra(intent);
    }

    /** Opens the page another screen asked for (the home screen's "See Pro", for one). */
    private void handleOpenExtra(Intent intent) {
        if (intent == null) return;
        String open = intent.getStringExtra(EXTRA_OPEN);
        if (open == null) return;
        intent.removeExtra(EXTRA_OPEN);
        if ("pro".equals(open)) openPro();
        else if ("alarms".equals(open)) {
            go(TAB_SETTINGS);
            ((SettingsPage) pages[TAB_SETTINGS]).open(SettingsPage.ALARMS);
        } else if ("look".equals(open)) {
            go(TAB_SETTINGS);
            ((SettingsPage) pages[TAB_SETTINGS]).open(SettingsPage.LOOK);
        } else if ("ready".equals(open)) {
            showReady();
        }
    }

    /** The end of the first-run welcome: everything is set up, and what to do next. */
    private void showReady() {
        try {
            final boolean home = HomeRole.isDefault(this);
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                    .setTitle("You\u2019re all set")
                    .setMessage(home
                            ? "Press the Home button to see your day. Everything else is here in the dashboard: your routine, insights, backup and settings."
                            : "One more step: make Dinacharya Launcher your home app, so your day is what you see when you press Home. "
                            + "Everything else is here in the dashboard: your routine, insights, backup and settings.")
                    .setNegativeButton("Stay here", null);
            if (home) {
                b.setPositiveButton("Go to my home screen", (d, w) -> startActivity(new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            } else {
                b.setPositiveButton("Make it my home app", (d, w) -> HomeRole.request(this));
            }
            b.show();
        } catch (RuntimeException e) {
            CrashLog.note(this, "ready message", e);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AlarmService.showIfRinging(this)) return;
        Pro.get(this).start();
        settlePass();
        HomeRole.look(this);
        StrongMode.ensure(this);   // the strong lock runs whenever it is switched on and allowed
        if (HomeRole.lost(this) && !homeOffered) {
            homeOffered = true;   // once each time the dashboard is opened
            ui.postDelayed(() -> HomeRole.offerBack(this, 0), 600);
        }
        for (PosterView p : previews) {
            p.refreshScreen();
            p.invalidate();
        }
        refreshVisible();
    }

    @Override
    protected void onPause() {
        if (savePending) persistNow();
        if (tab >= 0) pages[tab].hidden();
        super.onPause();
    }

    @Override
    protected void onStop() {
        homeOffered = false;   // the offer is made again the next time the dashboard comes back after being left
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(saveTask);
        ui.removeCallbacks(hideUndo);
        ui.removeCallbacks(passTimer);
        Pro.get(this).removeListener(proListener);
        Ads.get().setOnReady(null);
        for (Page p : pages) {
            if (p != null) p.destroy();
        }
        super.onDestroy();
    }

    /** Back: close a sub-page, else go to Home, else leave the app. */
    private void handleBack() {
        if (tab >= 0 && pages[tab].onBack()) return;
        if (tab != TAB_HOME) {
            go(TAB_HOME);
            return;
        }
        finish();
    }

    // Android 13 and later send Back to a callback (and Android 16 stops calling onBackPressed);
    // earlier versions still call onBackPressed.
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ExtraSections.REQ_NOTIFICATIONS) {
            if (routine.alarmOn) extras.nextPermissionStep();   // then the exact-alarm switch, if it is still off
            extras.refresh();
            refreshAlarmViews();
            refreshVisible();
        }
    }

    /** Pro was bought or lost, or ads became possible: every built page updates what it shows. */
    void monetizationChanged() {
        armPassTimer();
        for (Page p : pages) {
            if (p == null || !p.built()) continue;
            try {
                p.monetizationChanged();
            } catch (RuntimeException e) {
                CrashLog.note(this, "monetization views", e);
            }
        }
    }

    /**
     * A one-day video pass that has ended gives back what was made with it (see {@link PassSettle}): the saved settings
     * are changed, this screen reads them again, and the person is told. Safe to call at any time.
     */
    private void settlePass() {
        try {
            flushSave();
            PassSettle.run(this);
            if (PassSettle.epoch() != passEpoch) {
                passEpoch = PassSettle.epoch();
                reloadAll();
            }
            showPassMessage();
        } catch (RuntimeException e) {
            CrashLog.note(this, "pass reset", e);
        }
        armPassTimer();
    }

    /** Wakes this screen up just after the running pass ends, if it is still open then. */
    private void armPassTimer() {
        ui.removeCallbacks(passTimer);
        long until = Pro.get(this).passUntil();
        if (until <= 0) return;
        long wait = until - System.currentTimeMillis() + 1500L;
        if (wait < 1000L) wait = 1000L;
        ui.postDelayed(passTimer, Math.min(wait, DayPass.MAX_MS));
    }

    private void showPassMessage() {
        String text = PassSettle.takeMessage(this);
        if (text == null || isFinishing() || isDestroyed()) return;
        try {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Your video pass has ended")
                    .setMessage(text)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("See Pro", (d, w) -> openPro())
                    .show();
        } catch (RuntimeException e) {
            CrashLog.note(this, "pass message", e);
        }
    }

    /** Opens the Dinacharya Pro page in Settings. */
    void openPro() {
        ProPrompt.open(this);
    }

    /**
     * Something Pro unlocks was tapped. Does nothing and returns true when {@code feature} (a {@link DayPass} feature)
     * is open, because of Pro or a video pass; otherwise shows what Pro is, with the offer of a video that opens just
     * that feature for a day, and returns false so the caller stops. If a video opens it, {@code proceed} (may be null)
     * runs, so what the person was doing carries on.
     */
    boolean requirePro(int feature, String what, Runnable proceed) {
        if (Pro.has(this, feature)) return true;
        ProPrompt.show(this, feature, what, 0, proceed);
        return false;
    }

    /** Tells every built page that alarms were switched or their permissions changed. */
    void refreshAlarmViews() {
        for (Page p : pages) {
            if (p == null || !p.built()) continue;
            try {
                p.alarmsChanged();
            } catch (RuntimeException e) {
                CrashLog.note(this, "alarm views", e);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_GOOGLE) return;
        PickerAuth.Callback cb = googleCallback;
        googleCallback = null;
        if (cb == null) return;
        // Even when Google's screen reports "cancelled", its answer often carries the real error code; read it.
        if (data != null) {
            try {
                String token = PickerAuth.finish(this, data);
                if (token != null && !token.isEmpty()) {
                    cb.token(token);
                    return;
                }
            } catch (Exception e) {
                CrashLog.note(this, "Google sign-in (result " + resultCode + ")", e);
                cb.failed(PickerAuth.explain(e), true);
                return;
            }
        }
        CrashLog.note(this, "Google sign-in", new IllegalStateException("closed with result " + resultCode
                + (data == null ? ", no data" : ", no token")));
        cb.failed(PickerAuth.CLOSED + "\n\n(Result " + resultCode + (data == null ? ", no details from Google)" : ")"), true);
    }

    /** Opens Google's account list. The answer comes back to {@code cb}. */
    void signInWithGoogle(PickerAuth.Callback cb) {
        googleCallback = cb;
        PickerAuth.begin(this, REQ_GOOGLE, new PickerAuth.Callback() {
            @Override
            public void token(String accessToken) {
                googleCallback = null;
                cb.token(accessToken);
            }

            @Override
            public void failed(String message, boolean useCodeInstead) {
                googleCallback = null;
                cb.failed(message, useCodeInstead);
            }
        });
    }

    // ------------------------------------------------------------------ the frame: pages, bottom bar, undo bar

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFEDF2F1);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Ui.SURFACE);
        nav.setElevation(dp(8));
        nav.setPadding(dp(4), dp(6), dp(4), dp(8));
        for (int i = 0; i < 5; i++) {
            final int t = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setBackground(Ui.pressable(this, 0x00FFFFFF, 0, 16, 0x200E7C7B));
            item.setPadding(0, dp(4), 0, dp(2));
            item.setContentDescription(TAB_NAMES[i]);
            FrameLayout pill = new FrameLayout(this);
            pill.setBackground(Ui.box(this, Ui.TONAL, 0, 16));
            Glyph g = new Glyph(this, TAB_ICONS[i], Ui.MUTED);
            pill.addView(g, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            item.addView(pill, new LinearLayout.LayoutParams(dp(56), dp(30)));
            TextView label = Ui.label(this, TAB_NAMES[i], 11.5f, Ui.MUTED, false);
            label.setGravity(Gravity.CENTER);
            item.addView(label, Ui.wrap(this, 3));
            item.setOnClickListener(v -> go(t));
            tabIcons[i] = g;
            tabLabels[i] = label;
            tabPills[i] = pill;
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        column.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The undo bar floats just above the bottom bar.
        undoBar = new LinearLayout(this);
        undoBar.setOrientation(LinearLayout.HORIZONTAL);
        undoBar.setGravity(Gravity.CENTER_VERTICAL);
        undoBar.setBackground(Ui.box(this, 0xF0182826, 0, 14));
        undoBar.setElevation(dp(10));
        undoBar.setPadding(dp(16), dp(6), dp(6), dp(6));
        undoText = Ui.label(this, "", 14, 0xFFFFFFFF, false);
        undoText.setMaxLines(3);
        undoBar.addView(undoText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView undoBtn = Ui.label(this, "UNDO", 14, 0xFF7FE0DC, true);
        undoBtn.setLetterSpacing(0.08f);
        undoBtn.setGravity(Gravity.CENTER);
        undoBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
        undoBtn.setBackground(Ui.pressable(this, 0x00FFFFFF, 0, 10, 0x40FFFFFF));
        undoBtn.setOnClickListener(v -> undo());
        undoBar.addView(undoBtn);
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        ulp.leftMargin = dp(12);
        ulp.rightMargin = dp(12);
        ulp.bottomMargin = dp(84);
        undoBar.setVisibility(View.GONE);
        root.addView(undoBar, ulp);

        final LinearLayout navBar = nav;
        final FrameLayout.LayoutParams undoLp = ulp;
        Insets.watch(root, (l, t, r, b) -> {
            content.setPadding(l, t, r, 0);
            navBar.setPadding(dp(4) + l, dp(6), dp(4) + r, dp(8) + b);
            undoLp.bottomMargin = dp(84) + b;
            undoBar.setLayoutParams(undoLp);
        });
        Insets.lightBars(this);
        setContentView(root);
    }

    /** Shows a tab. */
    void go(int t) {
        if (t < 0 || t >= pages.length) t = TAB_HOME;
        if (t == tab) {
            pages[t].refresh();
            return;
        }
        if (tab >= 0) {
            pages[tab].hidden();
            pages[tab].view().setVisibility(View.GONE);
        }
        tab = t;
        View v = pages[t].view();
        if (v.getParent() == null) {
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(160).start();
        for (int i = 0; i < 5; i++) {
            boolean on = i == t;
            tabIcons[i].setColor(on ? Ui.TEAL_DARK : Ui.MUTED);
            tabLabels[i].setTextColor(on ? Ui.TEAL_DARK : Ui.MUTED);
            tabLabels[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabPills[i].setBackground(on ? Ui.box(this, Ui.TONAL, 0, 16) : null);
        }
        pages[t].refresh();
    }

    int currentTab() {
        return tab;
    }

    Page page(int t) {
        return pages[t];
    }

    private void refreshVisible() {
        if (tab >= 0) {
            try {
                pages[tab].refresh();
            } catch (RuntimeException e) {
                CrashLog.note(this, "refresh " + TAB_NAMES[tab], e);
            }
        }
    }

    // ------------------------------------------------------------------ shared with the pages

    int dp(float v) {
        return Ui.dp(this, v);
    }

    Routine routine() {
        return routine;
    }

    void toastText(String msg) {
        Lang.toast(this, msg, Toast.LENGTH_SHORT).show();
    }

    void persistRoutine() {
        persist();
    }

    /** Saves now and sets the next alarm. */
    void persist() {
        ui.removeCallbacks(saveTask);
        persistNow();
    }

    /** Saves a moment after the last change; used while typing or tapping quickly. */
    void persistSoon() {
        savePending = true;
        ui.removeCallbacks(saveTask);
        ui.postDelayed(saveTask, SAVE_DELAY_MS);
    }

    void flushSave() {
        if (savePending) persistNow();
    }

    private void persistNow() {
        savePending = false;
        if (PassSettle.epoch() != passEpoch) {
            // A finished video pass changed the saved routine behind this screen's back: keep that, read it again.
            passEpoch = PassSettle.epoch();
            ui.post(this::reloadAll);
            return;
        }
        try {
            Store.save(this, routine);
        } catch (RuntimeException e) {
            CrashLog.note(this, "save", e);
            toastText("Could not save. Please try again.");
        }
        AlarmScheduler.reschedule(this);
        extras.routineChanged();
    }

    void registerPreview(PosterView p) {
        p.setSource(routine);
        previews.add(p);
    }

    void previewChanged() {
        for (PosterView p : previews) p.invalidate();
    }

    /** The routine as text, taken before a change so it can be undone. */
    String snapshot() {
        return routine.serialize();
    }

    /** Shows {@code msg} with an Undo button that puts the routine back to {@code snapshot}. */
    void undoable(String msg, String snapshot) {
        undoSnapshot = snapshot;
        undoText.setText(msg);
        ui.removeCallbacks(hideUndo);
        if (undoBar.getVisibility() != View.VISIBLE) {
            undoBar.setVisibility(View.VISIBLE);
            undoBar.setAlpha(0f);
            undoBar.setTranslationY(dp(16));
            undoBar.animate().alpha(1f).translationY(0).setDuration(180).start();
        }
        ui.postDelayed(hideUndo, UNDO_MS);
    }

    private void hideUndoBar() {
        undoSnapshot = null;
        if (undoBar.getVisibility() != View.VISIBLE) return;
        undoBar.animate().alpha(0f).translationY(dp(16)).setDuration(160)
                .withEndAction(() -> undoBar.setVisibility(View.GONE)).start();
    }

    private void undo() {
        String snap = undoSnapshot;
        ui.removeCallbacks(hideUndo);
        hideUndoBar();
        if (snap == null) return;
        Routine back = Routine.parse(snap);
        if (back == null) return;
        routine.replaceWith(back);
        persist();
        routineChanged();
        toastText("Undone");
    }

    /** The routine changed in a way other tabs show (slots, times, types, goals). */
    void routineChanged() {
        previewChanged();
        Page current = tab >= 0 ? pages[tab] : null;
        for (Page p : pages) {
            if (p == null || !p.built() || p == current) continue;
            try {
                p.refresh();
            } catch (RuntimeException e) {
                CrashLog.note(this, "refresh", e);
            }
        }
        if (current != null) current.refresh();
    }

    /** After a restore: read everything again and start from Home. */
    void reloadAll() {
        routine.replaceWith(Store.load(this));
        content.removeAllViews();
        previews.clear();
        extras = new ExtraSections(this);
        pages[TAB_HOME] = new HomePage(this);
        pages[TAB_ROUTINE] = new RoutinePage(this);
        pages[TAB_INSIGHTS] = new InsightsPage(this);
        pages[TAB_SETTINGS] = new SettingsPage(this);
        int keep = tab;
        tab = -1;
        go(keep == TAB_BACKUP ? TAB_BACKUP : TAB_HOME);
    }

    /** A thin line and the "Developed by" mark, for the bottom of a page. */
    void addFooter(LinearLayout body) {
        body.addView(Ui.rule(this), Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, dp(1), 28));
        body.addView(new BrandMark(this), Ui.wide(this, 8));
    }

    /** A small screen-wide note line, used by pages for status. */
    TextView note(String text) {
        TextView t = Ui.label(this, text, 13, Ui.MUTED, false);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    /** Text sizes in sp for consistency. */
    static void size(TextView t, float sp) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }
}
