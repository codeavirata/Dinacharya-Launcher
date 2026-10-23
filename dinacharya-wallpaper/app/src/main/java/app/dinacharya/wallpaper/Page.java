package app.dinacharya.wallpaper;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** One tab of the main screen. Built the first time it is opened, then kept (so it keeps its scroll). */
abstract class Page {
    protected final MainActivity a;
    private View view;
    protected ScrollView scroll;
    protected LinearLayout body;

    Page(MainActivity host) {
        this.a = host;
    }

    final View view() {
        if (view == null) {
            scroll = new ScrollView(a);
            scroll.setFillViewport(true);
            body = new LinearLayout(a);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(a.dp(16), a.dp(18), a.dp(16), a.dp(24));
            scroll.addView(body, new android.view.ViewGroup.LayoutParams(-1, -2));
            build(body);
            view = scroll;
        }
        return view;
    }

    final boolean built() {
        return view != null;
    }

    /** Adds the page's content. */
    abstract void build(LinearLayout body);

    /** The page became visible, or the data behind it changed. */
    void refresh() {
    }

    /** Pro was bought or lost, or ads became possible. The default is to refresh everything. */
    void monetizationChanged() {
        refresh();
    }

    /** The screen is closing: free anything held, such as an ad. */
    void destroy() {
    }

    /** Alarms were switched on or off, or the phone's alarm permissions changed. */
    void alarmsChanged() {
    }

    /** Back was pressed on this page. Return true when the page handled it (closed a sub-page). */
    boolean onBack() {
        return false;
    }

    /** The page is no longer visible. */
    void hidden() {
    }

    /** A big page title with a smaller line under it. */
    protected void title(String big, String small) {
        body.addView(Ui.label(a, big, 28, Ui.INK, true));
        if (small != null && !small.isEmpty()) body.addView(Ui.label(a, small, 14, Ui.MUTED, false), Ui.wide(a, 2));
    }
}
