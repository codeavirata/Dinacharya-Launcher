package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;

/** An activity whose system words (OK, Cancel, the time picker) follow the app's language. */
public class LangActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }
}
