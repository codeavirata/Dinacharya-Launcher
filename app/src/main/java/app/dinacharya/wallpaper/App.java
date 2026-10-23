package app.dinacharya.wallpaper;

import android.app.Application;

/** Starts before any screen, service or receiver of the app, so the crash guard and the language cover all of them. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        try {
            Lang.init(this);
        } catch (RuntimeException e) {
            CrashLog.note(this, "language start", e);
        }
    }
}
