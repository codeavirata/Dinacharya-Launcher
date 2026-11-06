package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Starts before any screen, service or receiver of the app, so the crash guard and the language cover all of them. */
public class App extends Application {
    /** How many of the app's screens are started (on screen, or about to be) right now. */
    private static volatile int started;

    /** True while one of the app's own screens is in front of the person. */
    static boolean inFront() {
        return started > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        try {
            Lang.init(this);
        } catch (RuntimeException e) {
            CrashLog.note(this, "language start", e);
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity a) {
                started++;
            }

            @Override
            public void onActivityStopped(Activity a) {
                if (started > 0) started--;
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityResumed(Activity a) {
            }

            @Override
            public void onActivityPaused(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            @Override
            public void onActivityDestroyed(Activity a) {
            }
        });
    }
}
