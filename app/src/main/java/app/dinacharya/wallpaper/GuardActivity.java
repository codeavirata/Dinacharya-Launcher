package app.dinacharya.wallpaper;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * The lock screen or pause screen that comes up in front of a locked or paused app when the strong lock is on
 * ({@link GuardService}). It shows the very same screens as the home screen does, decides again for itself whether the app
 * still has to be held (a lock may have ended in the meantime), and either sends the person home or lets them into the app.
 * It lives in a task of its own, is not kept in the recent apps, and closes as soon as it is left.
 */
public class GuardActivity extends LangActivity {

    @Override
    protected int look() {
        return LOOK_OWN;
    }

    static final String EXTRA_PKG = "pkg";

    private FrameLayout host;
    private LockScreen lock;
    private PauseScreen pause;
    private String pkg = "";
    private String label = "";
    private SlotNow slot = SlotNow.empty();
    private boolean closing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(this);
        host = new FrameLayout(this);
        setContentView(host, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        closing = false;
        handle(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The lock screen is in front now: the plain cover over the app has done its job.
        GuardService.dropCover();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Left by home, recents or another app: this screen is not kept around.
        if (!isChangingConfigurations() && !closing) {
            release();
            closing = true;
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        release();
        GuardService.noteDone();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    // ------------------------------------------------------------------ deciding and showing

    private void handle(Intent intent) {
        release();
        host.removeAllViews();
        pkg = intent == null ? "" : intent.getStringExtra(EXTRA_PKG);
        if (pkg == null) pkg = "";
        LauncherConfig cfg;
        try {
            cfg = LauncherStore.load(this);
        } catch (RuntimeException e) {
            CrashLog.note(this, "guard config", e);
            cfg = new LauncherConfig();
        }
        long now = System.currentTimeMillis();
        slot = SlotNow.read(this);
        int open = Pro.open(this);
        long used = GuardRules.needsUsage(cfg, pkg, open) ? AppUsage.todayMs(this, pkg) : 0;
        GuardRules.Verdict v = GuardRules.decide(cfg, pkg, true, open, slot.kind, used, now, Store.zone(), GuardService.tracker(), slot.toLock());
        if (v.type == GuardRules.NONE) {
            // Nothing holds the app any more (a lock ran out, a rule was removed): let it be.
            leave();
            return;
        }
        LauncherTheme th = LauncherTheme.of(this, cfg);
        Ui.mode(th.dark);
        getWindow().setBackgroundDrawable(new ColorDrawable(th.bg | 0xFF000000));
        host.setBackground(Ui.ground(this, th.dark));
        Insets.bars(this, !th.dark);
        label = labelOf(pkg);
        final AppEntry app = new AppEntry(v.key, label, AppKey.user(v.key) != 0);
        View view;
        if (v.type == GuardRules.LOCK) {
            boolean h24 = false;
            try {
                h24 = Store.load(this).fmt24;
            } catch (RuntimeException ignored) {
                // the 12-hour clock is the fallback
            }
            lock = new LockScreen(this, th, app, v.lock, h24, new LockScreen.Exit() {
                @Override
                public void goBack() {
                    GuardActivity.this.goBack();
                }

                @Override
                public void open() {
                    GuardActivity.this.openApp();
                }
            });
            view = lock.view();
        } else {
            pause = new PauseScreen(this, th, app, GuardRules.pauseMessage(v.pause, slot.title), v.pause, new PauseScreen.Exit() {
                @Override
                public void goBack() {
                    GuardActivity.this.goBack();
                }

                @Override
                public void open() {
                    GuardActivity.this.openApp();
                }
            });
            view = pause.view();
        }
        host.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (lock != null) lock.start();
        if (pause != null) pause.start();
    }

    private void release() {
        if (lock != null) {
            lock.release();
            lock = null;
        }
        if (pause != null) {
            pause.release();
            pause = null;
        }
    }

    private String labelOf(String p) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(p, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            return l == null || l.length() == 0 ? p : l.toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return p;
        }
    }

    // ------------------------------------------------------------------ leaving

    private void log(boolean wentBack) {
        try {
            String day = slot.day.isEmpty() ? java.time.LocalDate.now(Store.zone()).toString() : slot.day;
            Store.history(this).append(Ev.launch(System.currentTimeMillis(), day, slot.src, pkg, label, wentBack));
        } catch (RuntimeException e) {
            CrashLog.note(this, "log guard", e);
        }
    }

    /** The person chose to go back: to the home screen, and the app stays closed. */
    private void goBack() {
        if (closing) return;
        log(true);
        closing = true;
        release();
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            CrashLog.note(this, "guard home", e);
        }
        finish();
    }

    /** The wait is over, the phrase was typed or the lock ran out: remember that, and bring the app back. */
    private void openApp() {
        if (closing) return;
        log(false);
        GuardService.passed(pkg);
        closing = true;
        release();
        leave();
    }

    /** Close this screen and bring the app that was in front back to the front. */
    private void leave() {
        closing = true;
        try {
            Intent i = pkg.isEmpty() ? null : getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            CrashLog.note(this, "guard reopen", e);
        }
        finish();
    }
}
