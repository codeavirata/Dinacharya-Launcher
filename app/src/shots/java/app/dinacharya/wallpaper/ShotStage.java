package app.dinacharya.wallpaper;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Calendar;

/**
 * The pause screen and the lock screen as they come up in front of an app (test build only), for an app with a made-up name, so the
 * pictures never show a real company's app. The decision is the real one ({@link GuardRules}) taken on a made-up rule; only the name of
 * the app is put in by hand. It is built the way {@link GuardActivity} builds them.
 */
public class ShotStage extends LangActivity {
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_LABEL = "label";
    static final String PAUSE = "pause", PAUSE_STRICT = "pause-strict", LOCK_TIMED = "lock-timed", LOCK_TYPE = "lock-type", LOCK_STRICT = "lock-strict";

    private FrameLayout host;
    private PauseScreen pause;
    private LockScreen lock;

    @Override
    protected int look() {
        return LOOK_OWN;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        host = new FrameLayout(this);
        setContentView(host, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        setIntent(in);
        show(in);
    }

    @Override
    protected void onDestroy() {
        release();
        super.onDestroy();
    }

    private void release() {
        if (pause != null) {
            pause.release();
            pause = null;
        }
        if (lock != null) {
            lock.release();
            lock = null;
        }
    }

    private void show(Intent in) {
        release();
        host.removeAllViews();
        String kind = in.getStringExtra(EXTRA_KIND);
        String name = in.getStringExtra(EXTRA_LABEL);
        if (kind == null) kind = PAUSE;
        if (name == null) name = "Reels";
        final String pkg = "shots.made.up";
        final String key = AppKey.make(pkg, "shots.made.up.Main", 0);
        LauncherConfig cfg = new LauncherConfig();
        long now = System.currentTimeMillis();
        if (PAUSE.equals(kind) || PAUSE_STRICT.equals(kind)) {
            cfg.setRule(key, PAUSE_STRICT.equals(kind) ? LauncherConfig.RULE_STRICT : LauncherConfig.RULE_NUDGE, 0, true);
        } else {
            LauncherConfig.Group g = cfg.addGroup("Time wasters");
            g.apps.add(key);
            if (LOCK_TIMED.equals(kind)) {
                // a daily window that holds now: from two hours ago to two hours ahead, every day
                g.days = 0x7F;
                Calendar c = Calendar.getInstance();
                int mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                g.startMin = (mins - 120 + 1440) % 1440;
                g.endMin = (mins + 120) % 1440;
            } else if (LOCK_TYPE.equals(kind)) {
                g.during.add(LauncherConfig.anyKey(Routine.TASK));
            } else {
                g.days = 0x7F;
                g.strict = true;
                g.untilMs = now + 3 * 3600000L;
            }
        }
        SlotNow slot = SlotNow.read(this);
        int open = Pro.open(this);
        GuardRules.Verdict v = GuardRules.decide(cfg, pkg, true, open, slot.kind, 0, now, Store.zone(), null, slot.toLock());
        if (v.type == GuardRules.NONE) {
            ShotTour t = ShotTour.peek();
            if (t != null) t.note("STAGE " + kind + ": nothing holds the made-up app (type " + v.type + ")");
            return;
        }
        LauncherTheme th = LauncherTheme.of(this, LauncherStore.load(this));
        Ui.mode(th.dark);
        getWindow().setBackgroundDrawable(new ColorDrawable(th.bg | 0xFF000000));
        host.setBackground(Ui.ground(this, th.dark));
        Insets.bars(this, !th.dark);
        AppEntry app = new AppEntry(v.key, name, false);
        android.view.View view;
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
                    finish();
                }

                @Override
                public void open() {
                    finish();
                }
            });
            view = lock.view();
        } else {
            pause = new PauseScreen(this, th, app, GuardRules.pauseMessage(v.pause, slot.title), v.pause, new PauseScreen.Exit() {
                @Override
                public void goBack() {
                    finish();
                }

                @Override
                public void open() {
                    finish();
                }
            });
            view = pause.view();
        }
        host.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (lock != null) lock.start();
        if (pause != null) pause.start();
    }
}
