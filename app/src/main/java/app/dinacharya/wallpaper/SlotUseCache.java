package app.dinacharya.wallpaper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the latest answer to "how is the phone being used in today's slots" for the home screen, which draws every minute and cannot wait
 * for the phone's usage records. Reads them in the background, at most every few seconds, and only when the person turned phone use in
 * slots on. Needs the Usage access permission; without it there is simply no answer.
 */
final class SlotUseCache {
    /** How long an answer is good for when nothing changed. */
    private static final long FRESH_MS = 20000L;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "slot-use");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private static volatile SlotUse.Snapshot last;
    private static volatile long lastAt;
    /** The settings the last answer was made with (a changed list of apps makes it out of date at once). */
    private static volatile String lastConfig = "";

    private SlotUseCache() {
    }

    /** The latest answer, or null when there is none. */
    static SlotUse.Snapshot snapshot() {
        return last;
    }

    /**
     * Which apps are part of which activity, from the settings. Apps are matched by package. When phone use in slots is on, Dinacharya
     * Launcher itself is part of every activity (checking the day is not a distraction); when it is off nothing is.
     */
    static SlotUse.Parts parts(Context context) {
        final LauncherConfig cfg = LauncherStore.load(context);
        final String own = context.getApplicationContext().getPackageName();
        final boolean on = on(context, cfg);
        return (kind, sub) -> {
            if (!on) return new HashSet<>();
            Set<String> s = new HashSet<>(cfg.partPackages(kind, sub));
            s.add(own);
            return s;
        };
    }

    /**
     * True when phone use in slots is in use: the person turned it on and it is open to them (it is part of Pro). In the free version
     * the choices are kept but nothing is marked and every app counts as phone use.
     */
    static boolean on(Context context, LauncherConfig cfg) {
        return cfg.trackUse && Limits.analysisAllowed(Pro.has(context, DayPass.ANALYSIS));
    }

    /**
     * Reads the day's phone use in the background and keeps the answer; runs {@code onDone} on the main thread when there is a new one.
     * Does nothing (and forgets the old answer) when phone use in slots is off.
     */
    static void refresh(Context context, final Runnable onDone) {
        final Context c = context.getApplicationContext();
        final LauncherConfig cfg;
        try {
            cfg = LauncherStore.load(c);
        } catch (RuntimeException e) {
            return;
        }
        if (!on(c, cfg)) {
            last = null;
            return;
        }
        final String stamp = cfg.serialize();
        final long now = System.currentTimeMillis();
        if (last != null && stamp.equals(lastConfig) && now - lastAt < FRESH_MS) return;
        if (!BUSY.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            boolean changed = false;
            try {
                SlotUse.Snapshot s = compute(c, System.currentTimeMillis());
                last = s;
                lastAt = System.currentTimeMillis();
                lastConfig = stamp;
                changed = true;
            } catch (RuntimeException e) {
                CrashLog.note(c, "slot use", e);
            } finally {
                BUSY.set(false);
            }
            if (changed && onDone != null) MAIN.post(onDone);
        });
    }

    /** Today's slots with the phone use read now (the same way the day report is made, so the two agree). */
    static SlotUse.Snapshot compute(Context c, long now) {
        Routine base = Store.load(c);
        Overrides ov = Store.overrides(c, base);
        ZoneId zone = Store.zone();
        Plan.DayPlan saved = Plan.resolve(base, ov, now, zone);
        Plan.DayPlan p = Plan.plan(base, ov, saved.day, zone);
        List<Ev> events = Store.history(c).events();
        UsageDetail u = ScreenTime.detail(c, p.startMs, Math.min(now, p.endMs));
        return SlotUse.snapshot(p, events, u, parts(c), now);
    }
}
