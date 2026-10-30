package app.dinacharya.wallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/**
 * The Dinacharya Launcher live wallpaper, which comes with the home screen. It redraws the poster at the start of every minute
 * while the screen is visible, so the date rolls over at midnight, the old day scrolls away towards the next wake-up
 * time, and the highlighted slot moves on by itself.
 *
 * Nothing in here may throw: when a wallpaper service keeps failing, Android gives up on it and puts the phone's own wallpaper back.
 * Every callback is therefore wrapped, a failure is written to the problem log and the frame is skipped, and the upkeep that keeps the
 * alarms set runs a moment after the wallpaper is up, not while it starts.
 */
public class PosterWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new PosterEngine();
    }

    private final class PosterEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final PosterRenderer renderer = new PosterRenderer();
        private boolean visible;
        private boolean receiverOn;
        private int width;
        private int height;
        /** The screens this engine draws for when it is the real wallpaper (0 in a preview). */
        private int screens;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                safe("tick", () -> {
                    drawFrame();
                    refreshScreen();
                    scheduleNext();
                });
            }
        };

        private final BroadcastReceiver clockChanged = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                safe("clock", () -> {
                    drawFrame();
                    refreshScreen();
                    scheduleNext();
                });
            }
        };

        /** Runs one step so that a failure in it is written down and skipped instead of closing the wallpaper. */
        private void safe(String what, Runnable step) {
            try {
                step.run();
            } catch (RuntimeException | LinkageError | OutOfMemoryError e) {
                CrashLog.note(PosterWallpaperService.this, "wallpaper " + what, e);
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            safe("start", () -> {
                setTouchEventsEnabled(false);
                Store.prefs(PosterWallpaperService.this).registerOnSharedPreferenceChangeListener(this);
                IntentFilter f = new IntentFilter();
                f.addAction(Intent.ACTION_TIME_CHANGED);
                f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                f.addAction(Intent.ACTION_DATE_CHANGED);
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(clockChanged, f, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(clockChanged, f);
                }
                receiverOn = true;
            });
            safe("screens", () -> {
                if (!isPreview()) {
                    screens = currentScreens();
                    WallpaperSetup.engineScreens(PosterWallpaperService.this, 0, screens);
                }
            });
            // Keeps the alarms and the end-of-day report set, even if the app is never opened. A moment after the wallpaper is up,
            // so a slow or failing step can never stop the wallpaper itself from starting.
            if (!isPreview()) {
                handler.postDelayed(() -> safe("upkeep", () -> Keeper.ensure(PosterWallpaperService.this, "wallpaper")), 1500L);
            }
        }

        /** The home screen, the lock screen or both (older phones cannot say, so the home screen is assumed). */
        private int currentScreens() {
            int f = 0;
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    f = getWallpaperFlags();
                } catch (RuntimeException ignored) {
                    f = 0;
                }
            }
            f &= WallpaperSetup.HOME | WallpaperSetup.LOCK;
            return f == 0 ? WallpaperSetup.HOME : f;
        }

        @Override
        public void onWallpaperFlagsChanged(int which) {
            super.onWallpaperFlagsChanged(which);
            safe("screens changed", () -> {
                if (isPreview()) return;
                int now = currentScreens();
                WallpaperSetup.engineScreens(PosterWallpaperService.this, screens, now);
                screens = now;
            });
        }

        @Override
        public void onDestroy() {
            safe("stop", () -> {
                if (screens != 0) {
                    WallpaperSetup.engineScreens(PosterWallpaperService.this, screens, 0);
                    screens = 0;
                }
                handler.removeCallbacksAndMessages(null);
                Store.prefs(PosterWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);
                if (receiverOn) {
                    receiverOn = false;
                    unregisterReceiver(clockChanged);
                }
            });
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            safe("visibility", () -> {
                if (isVisible) {
                    drawFrame();
                    refreshScreen();
                    scheduleNext();
                } else {
                    handler.removeCallbacks(tick);
                }
            });
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            safe("surface", this::drawFrame);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(tick);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            safe("settings changed", () -> {
                drawFrame();
                refreshScreen();
                scheduleNext();   // a changed to-do list may now need its pages to move
            });
        }

        /** Reads today's screen time in the background, then redraws. Does nothing if the user turned it off. */
        private void refreshScreen() {
            if (!visible) return;
            if (Store.load(PosterWallpaperService.this).showScreen) {
                ScreenTime.refresh(PosterWallpaperService.this, () -> safe("screen time", this::drawFrame));
            }
            // phone use in the slots (only when the person turned it on; it does nothing otherwise)
            SlotUseCache.refresh(PosterWallpaperService.this, () -> safe("slot use", this::drawFrame));
        }

        private void scheduleNext() {
            handler.removeCallbacks(tick);
            if (!visible) return;
            long now = System.currentTimeMillis();
            long delay = 60000L - (now % 60000L) + 150L;
            // A to-do list longer than its room moves on to its next page every few seconds, but only while the screen is showing.
            if (renderer.scrolling()) delay = Math.min(delay, Todos.PAGE_MS - (now % Todos.PAGE_MS) + 40L);
            handler.postDelayed(tick, delay);
        }

        private boolean systemIsNight() {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }

        private void drawFrame() {
            if (width <= 0 || height <= 0) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    Routine r = Store.load(PosterWallpaperService.this);
                    long now = System.currentTimeMillis();
                    renderer.drawLive(canvas, width, height, r, Store.overrides(PosterWallpaperService.this, r), now,
                            Store.zone(), systemIsNight(), r.showScreen ? ScreenTime.last : null,
                            Store.posterExtras(PosterWallpaperService.this, r));
                }
            } catch (RuntimeException | OutOfMemoryError e) {
                // The surface can disappear between the check and the draw; the next frame recovers. Anything else is written down.
                if (!(e instanceof IllegalArgumentException) && !(e instanceof IllegalStateException)) {
                    CrashLog.note(PosterWallpaperService.this, "wallpaper draw", e);
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                        // Surface already gone.
                    }
                }
            }
        }
    }
}
