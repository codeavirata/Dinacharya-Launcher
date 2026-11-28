package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The picture tour (test build only). It runs a list of steps one after the other on the main thread: open a screen, press something, wait a
 * moment, take a picture. The steps are written in {@link ShotScript}; this class is what they run on. It lives in the application, not in a
 * screen, so it carries on when a screen is closed or made again (a change of colours or language).
 *
 * <p>Everything it does is written in {@code log.txt} beside the pictures, with the buttons it could not find and a list of what could be
 * pressed on that screen, so a script that has gone out of date shows itself.
 */
final class ShotTour {
    interface Action {
        void run(ShotTour t) throws Exception;
    }

    private static final class Step {
        final String label;
        final long afterMs;
        final Action action;

        Step(String label, long afterMs, Action action) {
            this.label = label;
            this.afterMs = afterMs;
            this.action = action;
        }
    }

    private static ShotTour instance;

    static synchronized ShotTour get(Application app) {
        if (instance == null) instance = new ShotTour(app);
        return instance;
    }

    /** The tour that is running or was run, or null before the first. */
    static synchronized ShotTour peek() {
        return instance;
    }

    final Application app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Step> queue = new ArrayDeque<>();
    private final List<Step> pending = new ArrayList<>();
    private final List<Uri> saved = new ArrayList<>();
    private final StringBuilder log = new StringBuilder();
    private Activity top;
    private File root;
    private String pass = "";
    private int number;
    private int total;
    private int shots;
    private boolean running;
    private boolean done;
    private Runnable onDone;
    private Runnable onProgress;

    private ShotTour(Application app) {
        this.app = app;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity a) {
                top = a;
                // the phone must not go to sleep half way through
                if (running) a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onActivityDestroyed(Activity a) {
                if (top == a) top = null;
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityStarted(Activity a) {
            }

            @Override
            public void onActivityPaused(Activity a) {
            }

            @Override
            public void onActivityStopped(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }
        });
    }

    // ------------------------------------------------------------------ running

    boolean running() {
        return running;
    }

    boolean done() {
        return done;
    }

    int shots() {
        return shots;
    }

    int stepsLeft() {
        return queue.size();
    }

    int stepsTotal() {
        return total;
    }

    String pass() {
        return pass;
    }

    File folder() {
        return root;
    }

    List<Uri> saved() {
        return saved;
    }

    void listen(Runnable progress, Runnable finished) {
        onProgress = progress;
        onDone = finished;
    }

    /** Starts the tour from the beginning (does nothing when it is already running). */
    void start(File folder) {
        if (running) return;
        running = true;
        done = false;
        root = folder;
        number = 0;
        shots = 0;
        saved.clear();
        log.setLength(0);
        queue.clear();
        deleteTree(root);
        root.mkdirs();
        note("tour starts, " + android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.SDK_INT);
        try {
            ShotScript.build(this);
        } catch (Throwable e) {
            note("SCRIPT FAILED to build: " + e);
        }
        total = queue.size();
        ui.postDelayed(this::next, 800);
    }

    private void next() {
        if (onProgress != null) onProgress.run();
        Step s = queue.pollFirst();
        if (s == null) {
            finish();
            return;
        }
        pending.clear();
        try {
            s.action.run(this);
        } catch (Throwable e) {
            note("ERROR in '" + s.label + "': " + e);
        }
        for (int i = pending.size() - 1; i >= 0; i--) queue.addFirst(pending.get(i));
        pending.clear();
        ui.postDelayed(this::next, s.afterMs);
    }

    private void finish() {
        note("tour ends, " + shots + " pictures");
        writeLog();
        try {
            new File(root, "DONE").createNewFile();
        } catch (java.io.IOException ignored) {
            // the log says it too
        }
        running = false;
        done = true;
        if (onProgress != null) onProgress.run();
        if (onDone != null) onDone.run();
    }

    /** Adds a step at the end of the list (used while the script is being built). */
    void add(String label, long afterMs, Action action) {
        queue.addLast(new Step(label, afterMs, action));
    }

    /** Adds a step to run straight after the one that is running (used by steps that find out what to do as they go). */
    void later(String label, long afterMs, Action action) {
        pending.add(new Step(label, afterMs, action));
    }

    // ------------------------------------------------------------------ the log

    void note(String s) {
        android.util.Log.i("DinaShots", s);
        log.append(String.format(Locale.US, "[%03d] ", number)).append(pass).append(" | ").append(s).append('\n');
        if (log.length() > 400000) log.delete(0, 100000);
    }

    private void writeLog() {
        if (root == null) return;
        try (FileWriter w = new FileWriter(new File(root, "log.txt"))) {
            w.write(log.toString());
        } catch (java.io.IOException ignored) {
            // nothing more can be done
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    // ------------------------------------------------------------------ pictures

    /** From here on, the pictures go in the folder of this pass (a step of its own, so it happens when the tour gets there). */
    void pass(final String name) {
        add("pass " + name, 0, x -> {
            x.pass = name;
            x.number = 0;
            x.note("--- pass " + name);
        });
    }

    Activity top() {
        return top;
    }

    /** Takes a picture of the screen as it is now and saves it as the next number of the pass, with this name. */
    void shot(String name) {
        Activity a = top;
        if (a == null) {
            note("NO SCREEN for picture " + name);
            return;
        }
        number++;
        String file = String.format(Locale.US, "%03d-%s.png", number, name.replaceAll("[^A-Za-z0-9_-]+", "-"));
        try {
            Bitmap bmp = ShotCapture.take(a);
            Uri u = ShotCapture.save(app, bmp, new File(root, pass), pass + "-" + file, pass);
            bmp.recycle();
            if (u != null) saved.add(u);
            shots++;
            note("picture " + file + " (" + a.getClass().getSimpleName() + ")");
        } catch (Throwable e) {
            note("PICTURE FAILED " + file + ": " + e);
        }
    }

    // ------------------------------------------------------------------ looking at the screen

    /** The windows on screen, the one in front first. */
    private List<View> windowsFront() {
        List<View> w = ShotCapture.windows(top);
        List<View> out = new ArrayList<>();
        for (int i = w.size() - 1; i >= 0; i--) out.add(w.get(i));
        return out;
    }

    private static String wordsOf(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof TextView && ((TextView) v).getText() != null) sb.append(((TextView) v).getText());
        if (v.getContentDescription() != null) sb.append(" | ").append(v.getContentDescription());
        return sb.toString();
    }

    private static boolean shown(View v) {
        return v.isShown() && v.getWidth() > 0 && v.getHeight() > 0;
    }

    private static void collect(View v, List<View> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    /** Every view of the windows on screen, the front window first. */
    List<View> views() {
        List<View> out = new ArrayList<>();
        for (View root : windowsFront()) collect(root, out);
        return out;
    }

    private static View pressable(View v) {
        View p = v;
        for (int i = 0; i < 7 && p != null; i++) {
            if (p.isClickable() && p.isEnabled()) return p;
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return null;
    }

    /** The view to press for a button with these words in it (its text or its description), or null. The front window is looked at first. */
    View find(String words) {
        View v = findOne(words);
        if (v == null) {
            String tr = Lang.tr(words);
            if (!tr.equals(words)) v = findOne(tr);   // the screen may be in another language
        }
        return v;
    }

    private View findOne(String words) {
        String w = words.toLowerCase(Locale.ROOT);
        View partial = null;
        for (View v : views()) {
            if (!shown(v)) continue;
            String s = wordsOf(v).toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            boolean exact = s.equals(w) || s.startsWith(w + " |") || s.endsWith("| " + w);
            if (exact || (partial == null && s.contains(w))) {
                View p = pressable(v);
                if (p == null) continue;
                if (exact) return p;
                partial = p;
            }
        }
        return partial;
    }

    /** Presses the button with these words. False (and a note of what can be pressed here) when there is none. */
    boolean click(String words) {
        View v = find(words);
        if (v == null) {
            note("NOT FOUND: '" + words + "'. On screen: " + pressables());
            return false;
        }
        v.performClick();
        note("pressed '" + words + "'");
        return true;
    }

    /** Presses the button whose description is exactly this (the tabs of the bottom bar, for one), or starts with it when {@code prefix}. */
    boolean clickDesc(String desc, boolean prefix) {
        String[] tries = {desc, Lang.tr(desc)};
        for (String want : tries) {
            for (View v : views()) {
                CharSequence d = v.getContentDescription();
                if (d == null || !shown(v) || !v.isClickable() || !v.isEnabled()) continue;
                String s = d.toString();
                if (prefix ? s.startsWith(want) : s.equals(want)) {
                    v.performClick();
                    note("pressed [" + s + "]");
                    return true;
                }
            }
        }
        note("NOT FOUND description '" + desc + "'. On screen: " + pressables());
        return false;
    }

    /** Presses the first clickable thing whose description has these words in it. */
    boolean clickDescContains(String part) {
        for (View v : views()) {
            CharSequence d = v.getContentDescription();
            if (d == null || !shown(v) || !v.isClickable() || !v.isEnabled()) continue;
            if (d.toString().contains(part)) {
                v.performClick();
                note("pressed [" + d + "]");
                return true;
            }
        }
        note("NOT FOUND description containing '" + part + "'. On screen: " + pressables());
        return false;
    }

    /** Presses the first thing whose text matches this pattern (a time such as 06:30, for one). */
    boolean clickMatching(String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        for (View v : views()) {
            if (!(v instanceof TextView) || !shown(v)) continue;
            CharSequence s = ((TextView) v).getText();
            if (s == null || !p.matcher(s).find()) continue;
            View press = pressable(v);
            if (press == null) continue;
            press.performClick();
            note("pressed text /" + regex + "/ = '" + s + "'");
            return true;
        }
        note("NOT FOUND text /" + regex + "/. On screen: " + pressables());
        return false;
    }

    /** Presses the button with these words if it is there, and says nothing when it is not (a tip that may or may not be showing). */
    boolean clickQuiet(String words) {
        View v = find(words);
        if (v == null) return false;
        v.performClick();
        note("pressed (if there) '" + words + "'");
        return true;
    }

    /** Hides every piece of text on screen that reads exactly one of these (a nudge that would only be in the way of a picture). */
    void hideTexts(String... texts) {
        int n = 0;
        for (View v : views()) {
            if (!(v instanceof TextView) || !shown(v)) continue;
            CharSequence s = ((TextView) v).getText();
            if (s == null) continue;
            for (String t : texts) {
                if (s.toString().equals(t)) {
                    v.setVisibility(View.GONE);
                    n++;
                }
            }
        }
        note("hid " + n + " nudges");
    }

    /** Calls a private method that takes nothing on the screen in front (opening the app list of the home screen, for one). */
    void callOnTop(String method) {
        Activity a = top;
        if (a == null) {
            note("NOT FOUND screen to call " + method + " on");
            return;
        }
        try {
            java.lang.reflect.Method m = a.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(a);
            note("called " + a.getClass().getSimpleName() + "." + method);
        } catch (Exception e) {
            note("COULD NOT call " + method + " on " + a.getClass().getSimpleName() + ": " + e);
        }
    }

    /** Closes the sheet or dialog in front, and only that (a note says so when there is none). */
    void closeSheet() {
        GlassDialog g = GlassDialog.top();
        if (g != null) {
            g.cancel();
            note("closed a sheet");
        } else {
            note("no sheet to close");
        }
    }

    /** Back: closes the sheet in front, or else asks the screen to go back. */
    void back() {
        GlassDialog g = GlassDialog.top();
        if (g != null) {
            g.cancel();
            note("back: closed a sheet");
            return;
        }
        if (top != null) {
            note("back from " + top.getClass().getSimpleName());
            top.onBackPressed();
        }
    }

    /** Presses the first of these that is there. */
    boolean clickAny(String... words) {
        for (String w : words) {
            View v = find(w);
            if (v != null) {
                v.performClick();
                note("pressed '" + w + "'");
                return true;
            }
        }
        note("NOT FOUND any of " + java.util.Arrays.toString(words) + ". On screen: " + pressables());
        return false;
    }

    /** A short list of the words on what can be pressed on the screen now. */
    String pressables() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (View v : views()) {
            if (!shown(v) || !v.isClickable()) continue;
            String s = wordsOf(v).replace('\n', ' ').trim();
            if (s.isEmpty()) {
                // a row that holds the words
                if (v instanceof ViewGroup) {
                    List<View> in = new ArrayList<>();
                    collect(v, in);
                    for (View c : in) {
                        if (c instanceof TextView && ((TextView) c).getText().length() > 0) {
                            s = ((TextView) c).getText().toString().replace('\n', ' ');
                            break;
                        }
                    }
                }
            }
            if (s.isEmpty()) s = "(" + v.getClass().getSimpleName() + ")";
            if (s.length() > 44) s = s.substring(0, 44) + "…";
            if (n++ > 0) sb.append(" ; ");
            sb.append(s);
            if (n >= 90) break;
        }
        return sb.toString();
    }

    /** Opens every collapsed card on the screen (twice over, for cards inside cards). */
    void expandAll() {
        for (int round = 0; round < 3; round++) {
            int n = 0;
            for (View v : views()) {
                CharSequence d = v.getContentDescription();
                if (d != null && shown(v) && v.isClickable() && d.toString().endsWith(", collapsed")) {
                    v.performClick();
                    n++;
                }
            }
            if (n == 0) break;
            note("opened " + n + " cards");
        }
    }

    /** Closes whatever is open in front: a sheet, a dialog (by its Cancel or Close button), or a page inside a page. */
    void closeFront() {
        GlassDialog g = GlassDialog.top();
        if (g != null) {
            g.cancel();
            note("closed a sheet");
            return;
        }
        if (clickAny("Cancel", "Close", "Not now", "Done")) return;
    }

    /** The biggest scrolling page on screen, or null. */
    ScrollView mainScroll() {
        ScrollView best = null;
        for (View v : views()) {
            if (v instanceof ScrollView && shown(v) && (best == null || v.getHeight() > best.getHeight())) best = (ScrollView) v;
        }
        return best;
    }

    /**
     * Takes the picture at the top of the page, then one for each screenful down to the end (at most {@code max} in all). Each one is a
     * step of its own, so the page has time to draw after each move.
     */
    void scrollShots(final String name, final int max) {
        ScrollView sv = mainScroll();
        if (sv == null || sv.getChildCount() == 0) {
            later(name, 200, t -> t.shot(name));
            return;
        }
        int viewport = sv.getHeight();
        int content = sv.getChildAt(0).getHeight();
        int stride = Math.max(1, (int) (viewport * 0.85f));
        int n = Math.min(max, 1 + (content <= viewport ? 0 : (int) Math.ceil((content - viewport) / (double) stride)));
        note("page '" + name + "' is " + content + "px tall in " + viewport + "px: " + n + " pictures");
        for (int i = 0; i < n; i++) {
            final int k = i;
            later(name + "-scroll" + k, 350, t -> {
                ScrollView s = t.mainScroll();
                if (s != null) s.scrollTo(0, Math.min(k * stride, Math.max(0, content - viewport)));
            });
            later(name + "-shot" + k, 150, t -> t.shot(n == 1 ? name : name + "-" + (k + 1)));
        }
        later(name + "-top", 100, t -> {
            ScrollView s = t.mainScroll();
            if (s != null) s.scrollTo(0, 0);
        });
    }

    /** Waits until a screen of this kind is in front (up to {@code timeoutMs}), then goes on. */
    void waitFor(final Class<? extends Activity> kind, final long timeoutMs) {
        final long until = android.os.SystemClock.uptimeMillis() + timeoutMs;
        add("wait for " + kind.getSimpleName(), 300, new Action() {
            @Override
            public void run(ShotTour t) {
                if (kind.isInstance(t.top)) return;
                if (android.os.SystemClock.uptimeMillis() > until) {
                    t.note("TIMEOUT waiting for " + kind.getSimpleName() + "; in front is " + (t.top == null ? "nothing" : t.top.getClass().getSimpleName()));
                    return;
                }
                t.later("wait for " + kind.getSimpleName(), 300, this);
            }
        });
    }

    /** Starts a screen of the app. */
    void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(i);
    }
}
