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
 * The Dinacharya live wallpaper, which comes with the home screen. It redraws the poster at the start of every minute
 * while the screen is visible, so the date rolls over at midnight, the old day scrolls away towards the next wake-up
 * time, and the highlighted slot moves on by itself.
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
                drawFrame();
                refreshScreen();
                scheduleNext();
            }
        };

        private final BroadcastReceiver clockChanged = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                drawFrame();
                refreshScreen();
                scheduleNext();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
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
            if (!isPreview()) {
                screens = currentScreens();
                WallpaperSetup.engineScreens(PosterWallpaperService.this, 0, screens);
            }
            // Keeps the alarms and the end-of-day report set, even if the app is never opened.
            PassSettle.run(PosterWallpaperService.this);
            AlarmScheduler.reschedule(PosterWallpaperService.this);
            DayCloser.schedule(PosterWallpaperService.this);
            PassSettle.schedule(PosterWallpaperService.this);
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
            if (isPreview()) return;
            int now = currentScreens();
            WallpaperSetup.engineScreens(PosterWallpaperService.this, screens, now);
            screens = now;
        }

        @Override
        public void onDestroy() {
            if (screens != 0) {
                WallpaperSetup.engineScreens(PosterWallpaperService.this, screens, 0);
                screens = 0;
            }
            handler.removeCallbacks(tick);
            Store.prefs(PosterWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);
            if (receiverOn) {
                unregisterReceiver(clockChanged);
                receiverOn = false;
            }
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (isVisible) {
                drawFrame();
                refreshScreen();
                scheduleNext();
            } else {
                handler.removeCallbacks(tick);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(tick);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            drawFrame();
            refreshScreen();
            scheduleNext();   // a changed to-do list may now need its pages to move
        }

        /** Reads today's screen time in the background, then redraws. Does nothing if the user turned it off. */
        private void refreshScreen() {
            if (!visible) return;
            if (Store.load(PosterWallpaperService.this).showScreen) {
                ScreenTime.refresh(PosterWallpaperService.this, () -> drawFrame());
            }
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
                            Store.posterExtras(PosterWallpaperService.this));
                }
            } catch (RuntimeException ignored) {
                // The surface can disappear between the check and the draw; the next frame recovers.
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
