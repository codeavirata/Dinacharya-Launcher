package app.dinacharya.wallpaper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The minimal home screen. It lists no apps: the whole screen is the Dinacharya day, shown by the Dinacharya live
 * wallpaper (which Android asks you to confirm once). Until it is in place the launcher paints the same poster itself.
 * Swipe up for every app (search and an A to Z strip, over a blurred home screen; Dinacharya is in it too and opens the dashboard), swipe down for
 * notifications, left and right for two shortcuts (the camera and the phone by default), tap the day to open the
 * dashboard, press and hold for settings. Opening an app you gave a pause rule shows a wait, or (in Pro) a sum, a
 * sentence, slow breaths or a question, first. It shows no ads.
 */
public class LauncherActivity extends LangActivity {

    @Override
    protected int look() {
        return LOOK_OWN;
    }

    private static final String PREF_HINT = "launcher_hint_done";
    private static final String PREF_PRO_CHECK = "launcher_pro_check";
    static final String PREF_WALL_ASKED = "wallpaper_asked_at";
    static final String PREF_TOUR_DONE = "tour_done";
    private static final int NONE = 0, DRAWER = 1, PAUSE = 2, LOCK = 3;
    private static final int REQ_ROLE = 7311;
    private static final int REQ_WALL = 7312;

    /** Kept while the app process lives, so the list is there at once when Home is pressed again. */
    private static List<AppEntry> cachedApps = new ArrayList<>();
    private final android.os.Handler appsLater = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable reloadApps = this::loadApps;
    private boolean watchingPackages;
    /** An app was installed, removed, updated, turned off or turned on: read the list again (after the burst of broadcasts is over). */
    private final android.content.BroadcastReceiver packageChanged = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsLater.removeCallbacks(reloadApps);
            appsLater.postDelayed(reloadApps, 600);
        }
    };

    /** The slot running now, as far as the home screen needs it. */
    private static final class Now {
        int kind = -1;
        int src = -1;
        String day = "";
        String title = "";
        /** The slot as the locks by activity type see it. */
        LockRules.Slot lock = LockRules.Slot.NONE;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LauncherConfig cfg = new LauncherConfig();
    /** The count of pass resets this screen has taken into account (see {@link PassSettle}). */
    private int passEpoch;
    private LauncherTheme th;
    private AppSource source;
    private List<AppEntry> apps = new ArrayList<>();
    private final Map<String, AppEntry> byKey = new HashMap<>();
    private int loadGen;

    private FrameLayout root;
    private LinearLayout home;
    private TextView defaultPill;
    private Now now = new Now();
    /**
     * The day poster the launcher draws itself. It shows only while the Dinacharya Launcher live wallpaper is not the phone's
     * wallpaper yet; once it is, the wallpaper (which shows the same poster) is what is behind the screen.
     */
    private PosterView posterView;
    private boolean ours;
    /** The small button that brings the wallpaper back while it is not in place. */
    private TextView wallPill;
    /** The screen was built in onCreate a moment ago, so the first onResume does not build it again. */
    private boolean builtInCreate;
    /** Shown while something is locked or paused and Android has not been told to let the lock hold everywhere. */
    private TextView lockPill;
    private final Runnable lookAgain = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || root == null) return;
            try {
                updatePill();
            } catch (RuntimeException e) {
                CrashLog.note(LauncherActivity.this, "wallpaper look again", e);
            }
        }
    };

    private GestureDetector gestures;
    private GestureDetector drawerGestures;
    private boolean swallow, cancelSent, drawerAtTop;
    private int overlayKind = NONE;
    private View overlay;
    private AlertDialog menu;

    // the list of all apps
    private EditText search;
    private ListView drawerList;
    private LetterRail rail;
    private DrawerAdapter adapter;
    private final List<AppEntry> shown = new ArrayList<>();
    private int lastLen;
    private View drawerCol;
    private ValueAnimator drawerAnim;
    private float drawerP;
    private boolean drawerBlur, drawerClosing;
    private int introGen;
    private long introUntil;
    private static final int BLUR_DP = 26;

    // the pause screen and the lock screen
    private PauseScreen pauseScreen;
    private LockScreen lockScreen;
    /** The apps whose group is locked right now, worked out each time the list is filled. */
    private final java.util.Set<String> lockedKeys = new java.util.HashSet<>();

    private boolean receiving;
    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            try {
                settlePass();
                refreshNow();
            } catch (RuntimeException e) {
                CrashLog.note(LauncherActivity.this, "home screen tick", e);
            }
        }
    };

    // ------------------------------------------------------------------ life cycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        source = new AppSource(this);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
            }
            cfg = LauncherStore.load(this);
            apps = new ArrayList<>(cachedApps);
            index();
            buildScreen();
            builtInCreate = true;
            loadApps();
            // The home screen is the app that is always there, so it keeps the alarms and the end-of-day report set.
            AlarmScheduler.reschedule(this);
            DayCloser.schedule(this);
            int first = b != null ? FirstRun.DASHBOARD : FirstRun.next(Store.prefs(this).getBoolean(SetupActivity.PREF_SETUP_DONE, false),
                    Store.prefs(this).getBoolean(PREF_TOUR_DONE, false), Store.prefs(this).getBoolean(SetupActivity.PREF_SETUP_BEGUN, false));
            if (first == FirstRun.SETUP) {
                // The setup was cut short (Android's home app page opens the home screen when it closes, for one): carry on from
                // the features already chosen, in the app's own task, not from the welcome.
                startActivity(new Intent(this, SetupActivity.class).putExtra(SetupActivity.EXTRA_RESUME, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (first != FirstRun.DASHBOARD) {
                // The first run was not finished (Home was pressed before the welcome, setup and tour were done): pick it up.
                startActivity(new Intent(this, WelcomeActivity.class));
            }
        } catch (RuntimeException | LinkageError e) {
            // The home screen must always come up: if anything in the normal start fails, a plain list of apps is shown instead.
            CrashLog.note(this, "home screen start", e);
            showFallback();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            source.watch(this::loadApps);
            // Android only tells the watcher about changes while it is watching, and it stops when this screen does. An app that was
            // turned off or on in Settings (or installed or removed) in the meantime would be missing or stale, so read the list again.
            loadApps();
            // Turning an app off or on is told by the package broadcast on every Android version (the watcher above can miss it), and
            // the phone can still be finishing the change when this screen comes back, so the list is read once more a moment later.
            if (!watchingPackages) {
                IntentFilter pf = new IntentFilter(Intent.ACTION_PACKAGE_CHANGED);
                pf.addAction(Intent.ACTION_PACKAGE_ADDED);
                pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
                pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
                pf.addDataScheme("package");
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageChanged, pf, Context.RECEIVER_NOT_EXPORTED);
                else registerReceiver(packageChanged, pf);
                watchingPackages = true;
            }
            appsLater.removeCallbacks(reloadApps);
            appsLater.postDelayed(reloadApps, 1500);
        } catch (RuntimeException e) {
            CrashLog.note(this, "home screen watch", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root == null) return;
        try {
            // An alarm is ringing and nobody answered (the screen did not come up, or you came home first): show it now.
            if (AlarmService.showIfRinging(this)) return;
            HomeRole.look(this);   // remembers that Dinacharya is the home app, so a later loss can be noticed
            StrongMode.afterReturn(this);   // the strong lock runs whenever something is locked or paused and it is allowed; asks for the next permission
            PassSettle.run(this);   // a video pass that has ended gives back what was made with it
            passEpoch = PassSettle.epoch();
            final boolean fresh = builtInCreate;   // built a moment ago in onCreate: nothing can have changed since
            builtInCreate = false;
            if (!fresh) cfg = LauncherStore.load(this);
            if (overlayKind == NONE) {
                if (!fresh) buildScreen();
            } else if (overlayKind == DRAWER) {
                refreshDrawer();   // an app shown again (or hidden) in the settings is shown (or gone) at once
            }
            tellAboutPass();
            refreshNow();
            updatePill();
            if (!receiving) {
                IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
                f.addAction(Intent.ACTION_TIME_CHANGED);
                f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
                else registerReceiver(tick, f);
                receiving = true;
            }
            checkProDaily();
            // Every time Home is pressed the alarms and the wallpaper are looked at again: Android drops both, without a word, when the
            // app is closed from App info or stopped by the phone.
            Keeper.ensure(this, "home");
        } catch (RuntimeException | LinkageError e) {
            CrashLog.note(this, "home screen resume", e);
        }
    }

    @Override
    protected void onPause() {
        if (receiving) {
            try {
                unregisterReceiver(tick);
            } catch (RuntimeException ignored) {
                // not registered any more
            }
            receiving = false;
        }
        if (overlayKind == PAUSE || overlayKind == LOCK) closeOverlay();
        super.onPause();
    }

    @Override
    protected void onStop() {
        source.unwatch();
        appsLater.removeCallbacks(reloadApps);
        if (watchingPackages) {
            try {
                unregisterReceiver(packageChanged);
            } catch (RuntimeException ignored) {
                // not registered any more
            }
            watchingPackages = false;
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home again: back to the plain home screen.
        try {
            closeOverlay();
            if (menu != null) {
                menu.dismiss();
                menu = null;
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "home screen new intent", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            updatePill();
            // Next in the same setup: the wallpaper.
            if (HomeRole.isDefault(this) && !WallpaperSetup.settled(this)) {
                Store.prefs(this).edit().putLong(PREF_WALL_ASKED, System.currentTimeMillis()).apply();
                WallpaperSetup.request(this, REQ_WALL);
            }
        } else if (requestCode == REQ_WALL) {
            WallpaperSetup.noteResult(this, resultCode == RESULT_OK);
            updatePill();
            // The wallpaper starts a moment after Android's preview closes: look again once it has.
            ui.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                updatePill();
                if (WallpaperSetup.lockOnly(this)) {
                    Lang.toast(this, "That set the lock screen only. Open the wallpaper step again and choose the home screen too.",
                            Toast.LENGTH_LONG).show();
                }
            }, 1500);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (overlayKind == DRAWER) {
            dismissDrawer();
        } else if (overlayKind != NONE) {
            closeOverlay();
        } else if (!HomeRole.isDefault(this)) {
            finish();
        }
        // As the home app there is nothing behind this screen to go back to.
    }

    // ------------------------------------------------------------------ building the screen

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @SuppressWarnings("deprecation")
    private void buildScreen() {
        th = LauncherTheme.of(this, cfg);
        Ui.mode(th.dark);   // the dialogs and cards built here wear the same glass
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        w.setStatusBarColor(0);
        w.setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (root == null) {
            root = new FrameLayout(this);
            setContentView(root);
            gestures = new GestureDetector(this, new HomeGestures());
            drawerGestures = new GestureDetector(this, new DrawerGestures());
            // The day poster is the home screen's background. It is part of the launcher: there is nothing to switch on.
            posterView = new PosterView(this);
            posterView.setFullScreen(true);
            root.addView(posterView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        updateBars();
        buildHome();
        syncWallpaper();
    }

    /**
     * Shows the launcher's own poster whenever the live wallpaper (which draws the same thing) is not really in place, so the home screen
     * is never plain. The wallpaper is looked at on the phone every time, not taken from an old note.
     */
    private void syncWallpaper() {
        int st = WallpaperSetup.status(this);
        ours = WallpaperWatch.seeThrough(st);   // anything less sure than "it is there": the launcher paints the day itself
        if (posterView != null) posterView.setVisibility(ours ? View.GONE : View.VISIBLE);
        // With the wallpaper in place the window is see-through, so the wallpaper itself is what you see.
        root.setBackgroundColor(ours ? 0x00000000 : th.bg);
        if (wallPill != null) {
            boolean setupDone = Store.prefs(this).getBoolean(SetupActivity.PREF_SETUP_DONE, false);
            wallPill.setVisibility(setupDone && WallpaperWatch.needsAttention(st) ? View.VISIBLE : View.GONE);
            wallPill.setText(st == WallpaperWatch.LOST ? "Bring back your day wallpaper" : "Set your day wallpaper");
        }
        // Just started, or just confirmed: Android may still be putting it in place, so look once more a moment later.
        long again = WallpaperSetup.checkAgainMs(this);
        ui.removeCallbacks(lookAgain);
        if (again > 0) ui.postDelayed(lookAgain, again);
    }

    /** Status and navigation bar icons that read against what is behind them: the poster, or the list's own colour. */
    private void updateBars() {
        boolean darkIcons = overlayKind == NONE ? (posterView == null || !posterView.isNight()) : !th.dark;
        Insets.bars(this, darkIcons);
    }

    private TextView text(String s, float sp, int color, boolean light) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(light ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A small label that stays readable on the poster, light or dark: white words on a soft dark pill. */
    private TextView chip(String s, float sp, int stroke) {
        TextView t = text(s, sp, 0xFFFFFFFF, false);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(0xB3141418);
        if (stroke != 0) bg.setStroke(dp(1), stroke);
        t.setBackground(bg);
        return t;
    }

    /** What sits on the poster: only the "make this your home app" button, until it is, and a first-time hint. */
    private void buildHome() {
        if (home != null) root.removeView(home);
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        int side = dp(28);
        // Small and low, so the poster stays visible: the day fills the screen down to its last slot.
        home.setPadding(side, dp(24), side, dp(6));
        home.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        wallPill = chip("Bring back your day wallpaper", 13, 0xFFFFC66B);
        wallPill.setPadding(dp(16), dp(9), dp(16), dp(9));
        wallPill.setVisibility(View.GONE);
        wallPill.setOnClickListener(v -> {
            Store.prefs(this).edit().putLong(PREF_WALL_ASKED, System.currentTimeMillis()).apply();
            WallpaperSetup.request(this, REQ_WALL);
        });
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.gravity = Gravity.CENTER_HORIZONTAL;
        wp.bottomMargin = dp(6);
        home.addView(wallPill, wp);

        lockPill = chip("Make your locks hold everywhere", 13, 0xFFFFC66B);
        lockPill.setPadding(dp(16), dp(9), dp(16), dp(9));
        lockPill.setVisibility(View.GONE);
        lockPill.setOnClickListener(v -> StrongMode.ask(this));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.gravity = Gravity.CENTER_HORIZONTAL;
        lp2.bottomMargin = dp(6);
        home.addView(lockPill, lp2);

        defaultPill = chip("Make this your home app", 13, 0xFFFFC66B);
        defaultPill.setPadding(dp(16), dp(9), dp(16), dp(9));
        defaultPill.setOnClickListener(v -> {
            if (!HomeRole.isDefault(this)) HomeRole.request(this);
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.bottomMargin = dp(6);
        home.addView(defaultPill, pp);

        if (!Store.prefs(this).getBoolean(PREF_HINT, false)) {
            TextView hint = chip("Swipe up: all apps  \u00b7  Tap: dashboard  \u00b7  Hold: settings", 11, 0);
            hint.setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.gravity = Gravity.CENTER_HORIZONTAL;
            home.addView(hint, hp);
        }

        root.addView(home, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Insets.pad(home);
    }

    /** The one button that is left to press: the home app first, then the wallpaper. Gone when both are done. */
    private void updatePill() {
        if (defaultPill == null) return;
        syncWallpaper();
        if (lockPill != null) {
            boolean setupDone = Store.prefs(this).getBoolean(SetupActivity.PREF_SETUP_DONE, false);
            lockPill.setVisibility(setupDone && StrongMode.needsAttention(this) ? View.VISIBLE : View.GONE);
        }
        // Only "make this your home app" is ever shown here; the wallpaper is asked for in the first-start setup.
        defaultPill.setVisibility(HomeRole.isDefault(this) ? View.GONE : View.VISIBLE);
    }

    /** Works out which slot is running, for the focus rules and the log of paused apps. */
    private void refreshNow() {
        Now n = new Now();
        try {
            // The same reading the strong lock uses: while a break is on, no slot runs and the rules of the slot are lifted.
            SlotNow s = SlotNow.read(this);
            n.kind = s.kind;
            n.src = s.src;
            n.day = s.day;
            n.title = s.title;
            n.lock = s.toLock();
        } catch (RuntimeException e) {
            CrashLog.note(this, "home screen slot", e);
        }
        now = n;
    }

    private android.graphics.drawable.Drawable ripple() {
        int c = th.dark ? 0x33FFFFFF : 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(c), null, new ColorDrawable(0xFFFFFFFF));
    }

    // ------------------------------------------------------------------ apps

    private void index() {
        byKey.clear();
        for (AppEntry e : apps) byKey.put(e.key, e);
    }

    private void loadApps() {
        final int gen = ++loadGen;
        new Thread(() -> {
            final List<AppEntry> list;
            try {
                list = source.load();
            } catch (RuntimeException e) {
                CrashLog.note(getApplicationContext(), "home screen apps", e);
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed() || gen != loadGen || list.isEmpty()) return;
                apps = list;
                cachedApps = list;
                index();
                prune();
                try {
                    if (overlayKind == DRAWER) refreshDrawer();
                } catch (RuntimeException e) {
                    CrashLog.note(this, "home screen apps shown", e);
                }
            });
        }, "launcher-apps").start();
    }

    /** Forgets apps that were uninstalled (only main-profile apps, where the phone can say for sure). */
    private void prune() {
        Set<String> refs = new LinkedHashSet<>(cfg.hidden);
        for (LauncherConfig.Rule r : cfg.rules) refs.add(r.key);
        refs.add(cfg.swipeLeft);
        refs.add(cfg.swipeRight);
        boolean changed = false;
        for (String k : refs) {
            if (k == null || k.isEmpty() || AppKey.isAction(k) || byKey.containsKey(k)) continue;
            if (AppKey.user(k) != 0) continue;
            try {
                getPackageManager().getApplicationInfo(AppKey.pkg(k), 0);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                cfg.forget(k);
                changed = true;
            } catch (RuntimeException ignored) {
                // cannot tell: keep it
            }
        }
        if (changed) LauncherStore.save(this, cfg);
    }

    /**
     * A finished video pass gives back what was made with it (see {@link PassSettle}). If that changed the saved settings
     * since this screen read them, it reads them again.
     */
    private void settlePass() {
        PassSettle.run(this);
        if (PassSettle.epoch() != passEpoch) {
            passEpoch = PassSettle.epoch();
            cfg = LauncherStore.load(this);
            if (overlayKind == NONE) buildScreen();
        }
        tellAboutPass();
    }

    private void tellAboutPass() {
        String text = PassSettle.takeMessage(this);
        if (text != null) Lang.toast(this, text, Toast.LENGTH_LONG).show();
    }

    private void checkProDaily() {
        long last = Store.prefs(this).getLong(PREF_PRO_CHECK, 0);
        long t = System.currentTimeMillis();
        if (t - last < 24L * 3600 * 1000) return;
        Store.prefs(this).edit().putLong(PREF_PRO_CHECK, t).apply();
        try {
            Pro.get(this).start();
        } catch (RuntimeException e) {
            CrashLog.note(this, "home screen pro check", e);
        }
    }

    // ------------------------------------------------------------------ opening things

    private void openDashboard() {
        try {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            CrashLog.note(this, "open dashboard", e);
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(this, LauncherSettingsActivity.class));
        } catch (RuntimeException e) {
            CrashLog.note(this, "open home settings", e);
        }
    }

    private void openIntent(Intent i, String missing) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Lang.toast(this, missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void runAction(String key) {
        if (key == null || key.isEmpty()) return;
        if (AppKey.DASHBOARD.equals(key)) {
            openDashboard();
            return;
        }
        if (AppKey.CAMERA.equals(key)) {
            openIntent(new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "No camera app found");
            return;
        }
        if (AppKey.PHONE.equals(key)) {
            openIntent(new Intent(Intent.ACTION_DIAL), "No phone app found");
            return;
        }
        AppEntry e = byKey.get(key);
        if (e != null) openApp(e);
    }

    /** Opens an app, after a pause if its rule asks for one. */
    private void openApp(AppEntry e) {
        if (getPackageName().equals(e.pkg)) {
            closeOverlay();
            openDashboard();
            return;
        }
        FocusRules.Decision d = null;
        try {
            // A locked group comes first: it holds the app until its time is over.
            refreshNow();
            LockRules.Hit lock = LockRules.check(cfg, e.key, Pro.open(this), System.currentTimeMillis(), Store.zone(), now.lock);
            if (lock != null) {
                closeOverlay();
                showLock(e, lock);
                return;
            }
        } catch (RuntimeException ex) {
            CrashLog.note(this, "lock check", ex);
        }
        try {
            int open = Pro.open(this);   // each part of a pause follows its own item
            LauncherConfig.Rule r = cfg.ruleFor(e.key);
            long used = (r != null && r.limitMin > 0 && DayPass.has(open, DayPass.PAUSE_LIMIT)) ? AppUsage.todayMs(this, e.pkg) : 0;
            d = FocusRules.decide(cfg, e.key, open, now.kind, used);
        } catch (RuntimeException ex) {
            CrashLog.note(this, "focus decision", ex);
        }
        closeOverlay();
        if (d == null || d.type == FocusRules.ALLOW) launchNow(e);
        else showPause(e, d);
    }

    private void launchNow(AppEntry e) {
        if (!source.launch(this, e.key)) {
            Lang.toast(this, "That app could not be opened.", Toast.LENGTH_SHORT).show();
            loadApps();
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private void expandShade() {
        try {
            Object sb = getSystemService("statusbar");
            Class<?> k = Class.forName("android.app.StatusBarManager");
            k.getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception e) {
            // Not every phone allows it: swiping down from the very top still works.
        }
    }

    // ------------------------------------------------------------------ gestures

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                swallow = false;
                cancelSent = false;
                if (overlayKind == DRAWER) drawerAtTop = listAtTop();
            }
            GestureDetector g = overlayKind == NONE ? gestures : (overlayKind == DRAWER ? drawerGestures : null);
            if (g != null) g.onTouchEvent(ev);
            if (swallow) {
                if (!cancelSent) {
                    cancelSent = true;
                    MotionEvent c = MotionEvent.obtain(ev);
                    c.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(c);
                    c.recycle();
                }
                return true;
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "home screen touch", e);
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean listAtTop() {
        if (drawerList == null || drawerList.getChildCount() == 0) return true;
        return drawerList.getFirstVisiblePosition() == 0 && drawerList.getChildAt(0).getTop() >= 0;
    }

    private final class HomeGestures extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (e1 == null || e2 == null) return false;
            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();
            float min = ViewConfiguration.get(LauncherActivity.this).getScaledMinimumFlingVelocity() * 3f;
            if (Math.abs(dy) >= Math.abs(dx)) {
                if (Math.abs(dy) < dp(70) || Math.abs(vy) < min) return false;
                swallow = true;
                if (dy < 0) openDrawer();
                else expandShade();
            } else {
                if (Math.abs(dx) < dp(70) || Math.abs(vx) < min) return false;
                swallow = true;
                runAction(dx < 0 ? cfg.swipeLeft : cfg.swipeRight);
            }
            return true;
        }

        /** A tap on the day opens the dashboard (a tap on the "make this your home app" button does not). */
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (defaultPill != null && defaultPill.getVisibility() == View.VISIBLE) {
                int[] at = new int[2];
                defaultPill.getLocationOnScreen(at);
                if (e.getRawX() >= at[0] && e.getRawX() <= at[0] + defaultPill.getWidth()
                        && e.getRawY() >= at[1] && e.getRawY() <= at[1] + defaultPill.getHeight()) {
                    return false;
                }
            }
            openDashboard();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            swallow = true;
            openSettings();
        }
    }

    private final class DrawerGestures extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (e1 == null || e2 == null || !drawerAtTop) return false;
            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();
            float min = ViewConfiguration.get(LauncherActivity.this).getScaledMinimumFlingVelocity() * 3f;
            if (dy > dp(80) && dy > Math.abs(dx) * 1.5f && vy > min) {
                swallow = true;
                dismissDrawer();
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ all apps

    private void openDrawer() {
        if (overlayKind != NONE) return;
        refreshNow();   // what runs now decides which apps a lock by activity type hides
        Store.prefs(this).edit().putBoolean(PREF_HINT, true).apply();
        drawerBlur = canBlur();
        drawerClosing = false;
        FrameLayout d = new FrameLayout(this);
        // See-through: the poster or wallpaper shows faintly behind the list, blurred where the phone can do it.
        d.setBackgroundColor(th.scrim(drawerBlur ? 0xB8 : 0xDC));
        d.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(12), dp(8), dp(8));
        drawerCol = col;

        search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(th.muted);
        search.setTextColor(th.text);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        search.setSingleLine(true);
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dp(28));
        field.setColor(th.dark ? 0x26FFFFFF : 0x14000000);
        field.setStroke(dp(1), th.dark ? 0x2EFFFFFF : 0x1F000000);
        search.setBackground(field);
        search.setPadding(dp(20), dp(13), dp(20), dp(13));
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.rightMargin = dp(16);
        sp.bottomMargin = dp(6);
        col.addView(search, sp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        drawerList = new ListView(this);
        Lang.keep(drawerList);   // the names of the person's apps stay as the phone gives them
        drawerList.setDivider(null);
        drawerList.setSelector(new ColorDrawable(0));
        drawerList.setVerticalScrollBarEnabled(false);
        drawerList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        drawerList.setClipToPadding(false);
        adapter = new DrawerAdapter();
        drawerList.setAdapter(adapter);
        drawerList.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < shown.size()) openApp(shown.get(pos));
        });
        drawerList.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < shown.size()) showAppMenu(shown.get(pos));
            return true;
        });
        body.addView(drawerList, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        rail = new LetterRail(this);
        rail.setColors(th.accent, th.muted);
        rail.setListener(this::jumpTo);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.MATCH_PARENT);
        rp.topMargin = dp(8);
        rp.bottomMargin = dp(8);
        body.addView(rail, rp);
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        d.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(d, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Insets.pad(col);
        overlay = d;
        overlayKind = DRAWER;
        updateBars();
        lastLen = 0;
        introGen++;
        introUntil = SystemClock.uptimeMillis() + 700;
        applyDrawer(0f);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean grew = s.length() > lastLen;
                lastLen = s.length();
                refreshDrawer();
                // Two letters at least, so a single letter never opens something by surprise.
                AppEntry one = s.toString().trim().length() >= 2 ? AppSearch.autoOpen(shown, s.toString(), cfg.autoOpen) : null;
                if (one != null && grew) openApp(one);
            }
        });
        search.setOnEditorActionListener((v, action, ev) -> {
            if (!shown.isEmpty()) openApp(shown.get(0));
            return true;
        });
        refreshDrawer();
        animateDrawer(true, null);
        if (cfg.keyboard) {
            search.requestFocus();
            showKeyboardSoon(0);
        }
    }

    /** Waits (in ms) before each try at showing the keyboard for the search field of the list of all apps. */
    private static final long[] KEYBOARD_TRIES = {60L, 200L, 450L, 900L};

    /**
     * Brings the keyboard up for the search field once the list is on screen. Some phones ignore the first request while the list is
     * still sliding in, so it is tried again a few times until the system accepts it (or the list is closed).
     */
    private void showKeyboardSoon(final int attempt) {
        if (attempt >= KEYBOARD_TRIES.length) return;
        ui.postDelayed(() -> {
            if (overlayKind != DRAWER || search == null || drawerClosing) return;
            boolean accepted = false;
            try {
                if (!search.hasFocus()) search.requestFocus();
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController wic = search.getWindowInsetsController();
                    if (wic != null) wic.show(WindowInsets.Type.ime());
                    WindowInsets ins = search.getRootWindowInsets();
                    accepted = ins != null && ins.isVisible(WindowInsets.Type.ime());
                }
                InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (m != null) accepted |= m.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            } catch (RuntimeException e) {
                CrashLog.note(this, "keyboard", e);
            }
            if (!accepted) showKeyboardSoon(attempt + 1);
        }, KEYBOARD_TRIES[attempt]);
    }

    private void refreshDrawer() {
        if (search == null || adapter == null) return;
        List<AppEntry> visible = new ArrayList<>();
        lockedKeys.clear();
        Set<String> keepOut = new LinkedHashSet<>();
        try {
            long t = System.currentTimeMillis();
            int open = Pro.open(this);
            for (LauncherConfig.Group g : cfg.groups) {
                if (LockRules.groupLocked(g, t, Store.zone(), now.lock)) lockedKeys.addAll(LockRules.heldApps(cfg, g, open));
            }
            // A group set to hide its apps takes them out of the list and out of search while its lock holds.
            keepOut.addAll(LockRules.hiddenNow(cfg, open, t, Store.zone(), now.lock));
        } catch (RuntimeException ex) {
            CrashLog.note(this, "locked apps", ex);
        }
        for (AppEntry e : apps) if (!cfg.hidden.contains(e.key) && !keepOut.contains(e.key)) visible.add(e);
        String q = search.getText().toString();
        shown.clear();
        shown.addAll(AppSearch.filter(visible, q));
        adapter.notifyDataSetChanged();
        boolean letters = q.trim().isEmpty();
        rail.setVisibility(letters ? View.VISIBLE : View.INVISIBLE);
        boolean[] present = new boolean[27];
        for (AppEntry e : shown) present[LetterRail.indexOf(AppSearch.letter(e.label))] = true;
        rail.setPresent(present);
    }

    private void jumpTo(char letter) {
        int want = LetterRail.indexOf(letter);
        for (int i = 0; i < shown.size(); i++) {
            if (LetterRail.indexOf(AppSearch.letter(shown.get(i).label)) >= want) {
                drawerList.setSelectionFromTop(i, 0);
                return;
            }
        }
        if (!shown.isEmpty()) drawerList.setSelectionFromTop(shown.size() - 1, 0);
    }

    /** One line of the list of all apps: just the name. */
    private final class Row extends LinearLayout {
        final TextView label;
        int introGen;

        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(52));
            setPadding(dp(4), dp(6), dp(12), dp(6));
            setBackground(ripple());
            label = text("", Math.max(18, LauncherConfig.textSp(cfg.size) - 4), th.text, false);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private final class DrawerAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Row r = convert instanceof Row ? (Row) convert : new Row(LauncherActivity.this);
            AppEntry e = shown.get(i);
            boolean locked = lockedKeys.contains(e.key);
            r.label.setText(locked ? e.shown() + "  ·  " + Lang.tr("locked") : e.shown());
            r.label.setAlpha(locked ? 0.5f : 1f);
            if (r.introGen != introGen) {
                r.introGen = introGen;
                r.animate().cancel();
                if (SystemClock.uptimeMillis() < introUntil && i < 14) {
                    // The first rows rise into place one after another as the list opens.
                    r.setAlpha(0f);
                    r.setTranslationY(dp(26));
                    r.animate().alpha(1f).translationY(0f).setStartDelay(50L + i * 26L).setDuration(280)
                            .setInterpolator(new DecelerateInterpolator(1.6f)).start();
                } else {
                    r.setAlpha(1f);
                    r.setTranslationY(0f);
                }
            }
            return r;
        }
    }

    // ------------------------------------------------------------------ the list's motion and glass

    private boolean canBlur() {
        if (Build.VERSION.SDK_INT < 31) return false;
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            return wm != null && wm.isCrossWindowBlurEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** p = 0: the home screen as it is. p = 1: the list fully open, with the day pushed back, dimmed and blurred behind it. */
    private void applyDrawer(float p) {
        drawerP = p;
        if (overlay != null && overlayKind == DRAWER) {
            overlay.setAlpha(p);
            if (drawerCol != null) drawerCol.setTranslationY((1f - p) * dp(44));
        }
        float scale = 1f - 0.06f * p;
        if (posterView != null) {
            posterView.setScaleX(scale);
            posterView.setScaleY(scale);
            posterView.setAlpha(1f - 0.45f * p);
        }
        if (home != null) {
            home.setScaleX(scale);
            home.setScaleY(scale);
            home.setAlpha(1f - p);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                getWindow().setBackgroundBlurRadius(drawerBlur ? Math.round(dp(BLUR_DP) * p) : 0);
            } catch (RuntimeException ignored) {
                // this phone does not blur: the dimming alone does the job
            }
        }
    }

    private void animateDrawer(boolean open, final Runnable end) {
        if (drawerAnim != null) drawerAnim.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(drawerP, open ? 1f : 0f);
        va.setDuration(open ? 320 : 210);
        va.setInterpolator(open ? new DecelerateInterpolator(1.8f) : new AccelerateInterpolator(1.3f));
        va.addUpdateListener(an -> applyDrawer((Float) an.getAnimatedValue()));
        final boolean[] cancelled = {false};
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator an) {
                cancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(Animator an) {
                if (!cancelled[0] && end != null) end.run();
            }
        });
        drawerAnim = va;
        va.start();
    }

    /** Closes the list of all apps the way it opened, in reverse. */
    private void dismissDrawer() {
        if (overlayKind != DRAWER) {
            closeOverlay();
            return;
        }
        if (drawerClosing) return;
        drawerClosing = true;
        hideKeyboard();
        animateDrawer(false, this::closeOverlay);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (m != null && search != null) m.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } catch (RuntimeException ignored) {
            // the keyboard closes with the screen anyway
        }
    }

    // ------------------------------------------------------------------ overlays

    private void closeOverlay() {
        if (drawerAnim != null) {
            drawerAnim.cancel();
            drawerAnim = null;
        }
        if (pauseScreen != null) {
            pauseScreen.release();
            pauseScreen = null;
        }
        if (lockScreen != null) {
            lockScreen.release();
            lockScreen = null;
        }
        hideKeyboard();
        if (overlay != null) root.removeView(overlay);
        overlay = null;
        overlayKind = NONE;
        drawerClosing = false;
        drawerCol = null;
        applyDrawer(0f);
        updateBars();
        search = null;
        drawerList = null;
        rail = null;
        adapter = null;
    }

    private void showPause(final AppEntry app, final FocusRules.Decision dec) {
        final Now at = now;
        String msg;
        if (dec.type == FocusRules.LIMIT) {
            msg = "You have used it for " + Routine.formatDuration(Math.max(1, dec.usedMin)) + " today.\nYour limit is "
                    + Routine.formatDuration(dec.limitMin) + ".";
        } else if (dec.type == FocusRules.STRICT) {
            msg = at.title.isEmpty() ? "This is a focus time." : "Right now: " + at.title + ".\nThis is a focus time.";
        } else if (!at.title.isEmpty()) {
            msg = "Right now: " + at.title + ".\nIs this the best use of the next few minutes?";
        } else {
            msg = "Take a breath.\nIs this what you meant to open?";
        }
        PauseScreen ps = new PauseScreen(this, th, app, msg, dec, new PauseScreen.Exit() {
            @Override
            public void goBack() {
                logLaunch(app, at, true);
                closeOverlay();
            }

            @Override
            public void open() {
                logLaunch(app, at, false);
                closeOverlay();
                GuardService.passed(app.pkg);   // the strong lock (if on) must not stop an app the person was just let into
                launchNow(app);
            }
        });
        View v = ps.view();
        root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = v;
        pauseScreen = ps;
        overlayKind = PAUSE;
        updateBars();
        ps.start();
    }

    private void showLock(final AppEntry app, final LockRules.Hit hit) {
        final Now at = now;
        boolean h24 = false;
        try {
            h24 = Store.load(this).fmt24;
        } catch (RuntimeException ignored) {
            // the 12-hour clock is the fallback
        }
        LockScreen ls = new LockScreen(this, th, app, hit, h24, new LockScreen.Exit() {
            @Override
            public void goBack() {
                logLaunch(app, at, true);
                closeOverlay();
            }

            @Override
            public void open() {
                logLaunch(app, at, false);
                closeOverlay();
                GuardService.passed(app.pkg);   // the strong lock (if on) must not stop an app the person was just let into
                launchNow(app);
            }
        });
        View v = ls.view();
        root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = v;
        lockScreen = ls;
        overlayKind = LOCK;
        updateBars();
        ls.start();
    }

    private void logLaunch(AppEntry app, Now at, boolean wentBack) {
        try {
            String day = at.day.isEmpty() ? java.time.LocalDate.now(Store.zone()).toString() : at.day;
            Store.history(this).append(Ev.launch(System.currentTimeMillis(), day, at.src, app.pkg, app.label, wentBack));
        } catch (RuntimeException e) {
            CrashLog.note(this, "log launch", e);
        }
    }

    // ------------------------------------------------------------------ the press-and-hold menu

    /**
     * True for an app in the main profile that Android can remove: one the person installed, or a phone app that has had updates
     * (Android then takes the updates off). A phone app with no updates can only be turned off, in App info; work apps are not offered.
     */
    private boolean canUninstall(AppEntry e) {
        if (getPackageName().equals(e.pkg) || AppKey.user(e.key) != 0) return false;
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(e.pkg, 0);
            boolean system = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (ai.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return !system || updated;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ex) {
            return false;
        }
    }

    /** True for a phone app with updates: "Uninstall" then removes the updates and leaves the app that came with the phone. */
    private boolean removesUpdatesOnly(AppEntry e) {
        try {
            return (getPackageManager().getApplicationInfo(e.pkg, 0).flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ex) {
            return false;
        }
    }

    private void showAppMenu(final AppEntry e) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (getPackageName().equals(e.pkg)) {
            // This app opens the dashboard, so a pause or a lock would make no sense for it.
            labels.add("Open the dashboard");
            acts.add(this::openDashboard);
        } else {
            LauncherConfig.Rule r = cfg.ruleFor(e.key);
            labels.add(r == null ? "Add a pause" : "Change the pause");
            acts.add(() -> FocusDialog.show(this, th.dialog(), cfg, e, Pro.open(this), null));
        }
        labels.add("Hide from the list");
        acts.add(() -> {
            cfg.hidden.add(e.key);
            LauncherStore.save(this, cfg);
            if (overlayKind == DRAWER) refreshDrawer();
            Lang.toast(this, "Hidden. You can show it again in the home screen settings.", Toast.LENGTH_LONG).show();
        });
        labels.add("App info");
        acts.add(() -> {
            if (!source.details(this, e.key)) {
                Lang.toast(this, "Could not open the app info.", Toast.LENGTH_SHORT).show();
            }
        });
        if (canUninstall(e)) {
            labels.add(removesUpdatesOnly(e) ? "Uninstall updates" : "Uninstall");
            acts.add(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + e.pkg)));
                } catch (RuntimeException ex) {
                    Lang.toast(this, "Could not open the uninstall screen.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        menu = new DBuilder(this, th.dialog())
                .setTitle(e.shown())
                .setItems(labels.toArray(new String[0]), (dlg, which) -> {
                    try {
                        acts.get(which).run();
                    } catch (RuntimeException ex) {
                        CrashLog.note(this, "home screen menu", ex);
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ if something goes wrong

    /** A plain screen with a way out, so a problem here can never leave the phone without a working home screen. */
    private void showFallback() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xFF141418);
        col.setPadding(dp(32), dp(32), dp(32), dp(32));
        TextView t = new TextView(this);
        t.setText("The home screen could not start.\nYour apps are fine. Try again, or pick another home app.");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        col.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addFallbackButton(col, "Try again", v -> recreate());
        addFallbackButton(col, "Change home app", v -> HomeRole.openChooser(this));
        addFallbackButton(col, "Open Dinacharya Launcher", v -> openDashboard());
        setContentView(col);
    }

    private void addFallbackButton(LinearLayout col, String label, View.OnClickListener l) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        col.addView(b, p);
    }
}
