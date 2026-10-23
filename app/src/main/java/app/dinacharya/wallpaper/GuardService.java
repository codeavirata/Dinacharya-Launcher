package app.dinacharya.wallpaper;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

/**
 * The strong lock. While it is on, this small foreground service looks at which app is in front of the screen (through the
 * Usage access the daily limits already use) a few times a second, and when that app is locked or paused by a rule
 * ({@link GuardRules}) it puts a cover over it at once and brings up the lock or pause screen ({@link GuardActivity}).
 * Nothing is recorded or sent: the name of the app in front is compared with the person's own rules and forgotten.
 *
 * It stops itself when it is switched off or a permission is taken away, looks at nothing while the screen is off or the
 * phone is locked, and never covers the phone, settings, system screens, the keyboard, a home app or Dinacharya itself.
 */
public class GuardService extends Service {
    private static final String CHANNEL = "strong_lock";
    private static final int NOTIFICATION_ID = 7001;
    /** How long a cover is kept for an app while its lock screen is being brought up. */
    private static final long PENDING_MS = 4000L;
    private static final long FALLBACK_MS = 1500L;

    private static volatile GuardService live;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler work;
    private WindowManager wm;
    private PackageManager pm;
    private final GuardRules.Tracker tracker = new GuardRules.Tracker();
    private final FrontTracker front = new FrontTracker();
    private Set<String> safe = new HashSet<>();
    private long safeBuilt;
    private LauncherConfig cfg = new LauncherConfig();
    private String cfgText;
    private SlotNow slot;
    private long slotAt;
    private volatile boolean screenOn = true;
    private final GuardLoop loop = new GuardLoop();

    // main thread only (the package of the cover is also read from the watching thread)
    private Cover cover;
    private volatile String coverPackage = "";

    // ------------------------------------------------------------------ for the lock screen

    /** The app was let through (a pause waited out, a lock broken): do not stop it again while it is used. */
    static void passed(String pkg) {
        GuardService s = live;
        if (s != null) s.tracker.passed(pkg, System.currentTimeMillis());
    }

    /** The tracker of who was let through, or null when the service is not running. */
    static GuardRules.Tracker tracker() {
        GuardService s = live;
        return s == null ? null : s.tracker;
    }

    /** Takes the cover down: the lock or pause screen is in front now (or is no longer needed). */
    static void dropCover() {
        final GuardService s = live;
        if (s != null) s.main.post(s::removeCover);
    }

    /** The lock screen finished or was left: forget that one was being brought up. */
    static void noteDone() {
        GuardService s = live;
        if (s != null) s.loop.done();
    }

    // ------------------------------------------------------------------ life

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        pm = getPackageManager();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        try {
            startAsForeground();
        } catch (RuntimeException refused) {
            // Android would not let this run in the foreground (the app is in the background and not allowed to start it).
            CrashLog.note(this, "strong lock foreground", refused);
            stopSelf();
            return;
        }
        live = this;
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        screenOn = power == null || power.isInteractive();
        thread = new HandlerThread("strong-lock");
        thread.start();
        work = new Handler(thread.getLooper());
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, f);
        work.post(poll);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        live = null;
        try {
            unregisterReceiver(screen);
        } catch (RuntimeException ignored) {
            // it was never registered
        }
        if (work != null) work.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        main.removeCallbacksAndMessages(null);
        removeCover();
        super.onDestroy();
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, Lang.tr("Strong lock"), NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Shown while the strong lock is on");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 7002, new Intent(this, LauncherSettingsActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(Lang.tr("Strong lock is on"))
                .setContentText(Lang.tr("Holding locked and paused apps. Tap to change."))
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pi)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final boolean on = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            screenOn = on;
            tracker.forgetFront();
            loop.clear();
            main.post(GuardService.this::removeCover);
            if (work != null) {
                work.removeCallbacks(poll);
                if (on) {
                    front.reset();
                    work.post(poll);
                } else {
                    work.postDelayed(poll, GuardRules.pollMs(true, false, false));
                }
            }
        }
    };

    // ------------------------------------------------------------------ looking at the screen

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            long next = 3000L;
            try {
                next = step();
            } catch (RuntimeException | LinkageError e) {
                CrashLog.note(GuardService.this, "strong lock step", e);
            }
            if (live == GuardService.this && work != null) work.postDelayed(this, next);
        }
    };

    /** One look at the screen. Returns how long to wait before the next one. */
    private long step() {
        if (!StrongMode.enabled(this) || !StrongMode.hasUsage(this) || !StrongMode.hasOverlay(this)) {
            main.post(this::stopSelf);   // switched off, or a permission was taken away
            return 5000L;
        }
        if (!screenOn) return GuardRules.pollMs(true, false, false);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            tracker.forgetFront();
            front.reset();
            loop.clear();
            return 1000L;
        }
        final long now = System.currentTimeMillis();
        refreshConfig(now);
        readEvents(now);
        GuardLoop.Out out = loop.look(cfg, front.front(), coverPackage, safeSet(now), getPackageName(), Pro.open(this), now,
                Store.zone(), tracker, env);
        if (out.dropCover) main.post(this::removeCover);
        if (out.present) {
            final String shownPkg = out.pkg;
            final GuardRules.Verdict verdict = out.verdict;
            final String slotTitle = slot().title;
            main.post(() -> present(shownPkg, verdict, slotTitle));
        }
        return out.nextMs;
    }

    /** What part of the day it is, read at most every 20 seconds. */
    private SlotNow slot() {
        long now = System.currentTimeMillis();
        if (slot == null || now - slotAt > 20000L || SlotNow.changedSince(slotAt)) {
            slot = SlotNow.read(this);
            slotAt = now;
        }
        return slot;
    }

    private final GuardLoop.Env env = new GuardLoop.Env() {
        @Override
        public int slotKind() {
            return slot().kind;
        }

        @Override
        public long usedToday(String pkg) {
            return AppUsage.todayMs(GuardService.this, pkg);
        }

        @Override
        public LockRules.Slot lockSlot() {
            return slot().toLock();
        }
    };

    private void refreshConfig(long now) {
        try {
            String text = Store.prefs(this).getString(LauncherStore.KEY, null);
            if (cfgText == null || !cfgText.equals(text)) {
                cfg = LauncherConfig.parse(text);
                cfgText = text;
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "strong lock config", e);
        }
    }

    private void readEvents(long now) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;
        long from = front.lastTs() == 0 ? now - 10 * 60000L : front.lastTs() - 1000L;
        UsageEvents ev = usm.queryEvents(Math.max(0L, from), now + 1000L);
        if (ev == null) return;
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            front.feed(e.getEventType(), e.getPackageName(), e.getClassName(), e.getTimeStamp());
        }
    }

    /** The packages that are never covered: the fixed list, the keyboard and the home apps. Rebuilt now and then. */
    private Set<String> safeSet(long now) {
        if (safeBuilt != 0 && now - safeBuilt < 60000L) return safe;
        Set<String> s = GuardRules.alwaysSafe();
        try {
            String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains("/")) s.add(ime.substring(0, ime.indexOf('/')));
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) s.add(ri.activityInfo.packageName);
            }
            ResolveInfo dial = pm.resolveActivity(new Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY);
            if (dial != null && dial.activityInfo != null && dial.activityInfo.packageName != null
                    && !"android".equals(dial.activityInfo.packageName)) {
                s.add(dial.activityInfo.packageName);
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "strong lock safe list", e);
        }
        s.add(getPackageName());
        safe = s;
        safeBuilt = now;
        return s;
    }

    // ------------------------------------------------------------------ the cover and the lock screen

    private String labelOf(String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            return l == null || l.length() == 0 ? pkg : l.toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return pkg;
        }
    }

    /** Main thread: cover the app, then bring up the lock or pause screen. */
    private void present(String pkg, GuardRules.Verdict v, String slotTitle) {
        try {
            if (!screenOn) return;
            removeCover();
            Cover c = new Cover(pkg, labelOf(pkg), v, slotTitle);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.OPAQUE);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(c.view, lp);
            cover = c;
            coverPackage = pkg;
            Intent i = new Intent(this, GuardActivity.class)
                    .putExtra(GuardActivity.EXTRA_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try {
                startActivity(i);
            } catch (RuntimeException e) {
                CrashLog.note(this, "strong lock screen", e);
            }
            // If the lock screen has not come up shortly, the cover works on its own: Go back, and a pause can be waited out.
            main.postDelayed(c::standAlone, FALLBACK_MS);
        } catch (RuntimeException | LinkageError e) {
            // Without the cover the app is not held: say so in the problem report rather than crash.
            CrashLog.note(this, "strong lock cover", e);
            loop.done();
        }
    }

    private void removeCover() {
        Cover c = cover;
        cover = null;
        coverPackage = "";
        if (c == null) return;
        c.stop();
        try {
            wm.removeViewImmediate(c.view);
        } catch (RuntimeException ignored) {
            // it was already gone
        }
    }

    private void goHome() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            CrashLog.note(this, "strong lock home", e);
        }
    }

    /** The plain cover that hides the app while the lock screen comes up (and holds it if that screen cannot). */
    private final class Cover {
        final String pkg;
        final View view;
        private final TextView line;
        private final TextView back;
        private final TextView open;
        private final GuardRules.Verdict verdict;
        private boolean stopped;
        private int left;
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (stopped) return;
                left--;
                refreshLeft();
                if (left > 0) main.postDelayed(this, 1000);
            }
        };

        Cover(String pkg, String label, GuardRules.Verdict v, String slotTitle) {
            this.pkg = pkg;
            this.verdict = v;
            LauncherTheme th = LauncherTheme.of(GuardService.this, cfg);
            FrameLayout root = new FrameLayout(GuardService.this);
            root.setBackgroundColor(th.bg | 0xFF000000);
            root.setClickable(true);
            LinearLayout col = new LinearLayout(GuardService.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            int pad = dp(32);
            col.setPadding(pad, pad, pad, pad);
            root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            col.addView(text(label, 30, th.text, true), wide(0));
            String what;
            if (v.type == GuardRules.LOCK) {
                String group = v.lock.group.name.isEmpty() ? "a locked group" : v.lock.group.name;
                what = (v.lock.strict ? "Strictly locked  ·  " : "Locked  ·  ") + group;
            } else {
                what = GuardRules.pauseMessage(v.pause, slotTitle);
            }
            col.addView(text(what, 17, th.muted, false), wide(14));
            line = text("", 15, th.accent, false);
            col.addView(line, wide(16));
            back = pill("Go back", true, th);
            back.setVisibility(View.GONE);
            back.setOnClickListener(x -> {
                noteDone();
                removeCover();
                goHome();
            });
            col.addView(back, wide(26));
            open = pill("Open anyway", false, th);
            open.setVisibility(View.GONE);
            open.setOnClickListener(x -> {
                if (left > 0) return;
                passed(pkg);
                noteDone();
                removeCover();
            });
            col.addView(open, wide(12));
            line.setText("Opening the lock screen…");
            this.view = root;
        }

        /** The lock screen did not come up: this cover holds the app on its own. */
        void standAlone() {
            if (stopped || cover != this) return;
            back.setVisibility(View.VISIBLE);
            if (verdict.type == GuardRules.PAUSE) {
                left = Math.max(1, verdict.pause.waitSec);
                open.setVisibility(View.VISIBLE);
                refreshLeft();
                main.postDelayed(tick, 1000);
            } else if (verdict.lock.strict) {
                line.setText("This lock is strict. There is no way past it until it ends.");
            } else {
                line.setText("To break this lock, open the app from the Dinacharya Launcher home screen.");
            }
            if (verdict.type == GuardRules.LOCK) {
                // when the lock ends, the cover goes with it
                long ms = verdict.lock.untilMs - System.currentTimeMillis();
                main.postDelayed(() -> {
                    if (!stopped && cover == this) {
                        noteDone();
                        removeCover();
                    }
                }, Math.max(0L, ms) + 300L);
            }
        }

        private void refreshLeft() {
            line.setText(left > 0 ? "You can open it in " + left + " s" : "You can open it now.");
            open.setAlpha(left > 0 ? 0.4f : 1f);
        }

        void stop() {
            stopped = true;
            main.removeCallbacks(tick);
        }

        private int dp(float v) {
            return Ui.dp(GuardService.this, v);
        }

        private LinearLayout.LayoutParams wide(float topDp) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(topDp);
            return p;
        }

        private TextView text(String s, float sp, int color, boolean light) {
            TextView t = new TextView(GuardService.this);
            t.setText(s);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            t.setTextColor(color);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.create(light ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
            t.setIncludeFontPadding(false);
            return t;
        }

        private TextView pill(String label, boolean filled, LauncherTheme th) {
            TextView b = text(label, 17, filled ? (th.dark ? 0xFF001B1A : 0xFFFFFFFF) : th.text, false);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setMinHeight(dp(54));
            b.setPadding(dp(24), dp(14), dp(24), dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(30));
            if (filled) bg.setColor(th.accent);
            else bg.setStroke(dp(1), th.muted);
            b.setBackground(bg);
            return b;
        }
    }
}
