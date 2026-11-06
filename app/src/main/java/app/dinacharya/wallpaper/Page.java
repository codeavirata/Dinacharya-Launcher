package app.dinacharya.wallpaper;

import android.os.SystemClock;
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

    // ---- knowing when nothing has changed (so a tab that is switched to need not be built again)

    private long stampRevision = -1;
    private long stampAt;
    private int stampLang;
    private long stampKey;

    /** Notes that the page was brought up to date at {@code revision} (see {@link MainActivity#revision()}). */
    final void stamp(long revision) {
        stampRevision = revision;
        stampAt = SystemClock.elapsedRealtime();
        stampLang = Lang.version();
        stampKey = key();
    }

    /** True once the page has been given its content at least once (a page that was only built is empty). */
    final boolean stamped() {
        return stampRevision >= 0;
    }

    /** True when nothing the page shows can have changed since it was last brought up to date. */
    final boolean upToDate(long revision) {
        if (view == null || stampRevision != revision || stampLang != Lang.version() || stampKey != key()) return false;
        long age = SystemClock.elapsedRealtime() - stampAt;
        return age >= 0 && age < maxAgeMs();
    }

    /** How long the page stays right if nothing is changed by the person; 0 (the default) means it is always brought up to date. */
    long maxAgeMs() {
        return 0;
    }

    /** What moves on by itself and makes the page wrong (the minute, the day); a different number than at the last stamp means refresh. */
    long key() {
        return 0;
    }

    /** The page is shown again and needed no refresh: start again what {@link #hidden()} stopped. */
    void revive() {
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
