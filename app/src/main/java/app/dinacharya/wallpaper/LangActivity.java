package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * An activity whose system words (OK, Cancel, the time picker) follow the app's language, and whose look is the app's frosted glass:
 * the colours, the gradient ground and the see-through system bars, in the night or the day version the routine's look asks for.
 */
public class LangActivity extends Activity {
    /** The screen follows the routine's look: night glass or day glass. */
    protected static final int LOOK_ROUTINE = 0;
    /** The screen is always night glass (the alarm). */
    protected static final int LOOK_NIGHT = 1;
    /** The screen dresses itself (the tour, the home screen, the lock screens). */
    protected static final int LOOK_OWN = 2;

    private boolean night = true;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    /** Which look the screen has; see the LOOK_ values. */
    protected int look() {
        return LOOK_ROUTINE;
    }

    private boolean wantsNight() {
        return look() == LOOK_NIGHT || Ui.nightNow(this);
    }

    @Override
    protected void onCreate(Bundle b) {
        if (look() != LOOK_OWN) {
            night = wantsNight();
            setTheme(night ? R.style.AppNight : R.style.AppDay);
            Ui.mode(night);
        }
        super.onCreate(b);
        if (look() != LOOK_OWN) Ui.screen(this, night);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (look() == LOOK_OWN) return;
        // another screen may have set the other colours, and the look may have been changed in the settings
        boolean now = wantsNight();
        Ui.mode(now);
        if (now != night) recreate();
    }

    /** The routine's look was changed: dress the screen again. */
    public void restyle() {
        if (look() == LOOK_OWN) return;
        if (wantsNight() != night) recreate();
    }
}
