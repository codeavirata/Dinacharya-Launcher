package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.Arrays;
import java.util.List;

/**
 * "Watch short videos to open Pro features for a while." The person chooses it and chooses how many (nothing plays by itself):
 * one video opens one feature for a day, two open the same feature for three days, three open the whole group the feature is in
 * (all the pauses, say) for a day. The rewards are the {@link DayPass} tiers; the record is in {@link Pro}.
 *
 * Ad layers: the video is asked for from up to three AdMob ad units in turn, the best paying first ({@link AdLayers}), so each
 * video earns as much as it can and one is still found when the best price is not on offer. One ad is loaded ahead of time while
 * an offer is on screen (and the next one while a video plays), so the person rarely waits.
 *
 * Fair to the person: a reward is given only when the ad has really been shown and the ad network says it was watched to the end
 * (never for a video that did not play), and it is given at that moment, one step per video, so stopping after one or two videos
 * keeps what those earned and a video that cannot be found never costs anything. Every item has its own pass: a video opens only the
 * item it was started for, and an item that needs a second thing (a strict pause on a third app) needs a second video. Someone who
 * owns Pro never sees the offer.
 */
final class VideoPass {
    /** How long one layer may take before the next is asked. */
    private static final long LAYER_TIMEOUT_MS = 10000L;
    /** After every layer failed, ads are not preloaded again for this long. */
    private static final long WARM_PAUSE_MS = 60000L;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    /** A run that has not moved for this long is taken as lost (the screen went away without the ad calling back). */
    private static final long STALE_MS = 6 * 60000L;
    private static Run active;
    private static long activeAt;

    private static AdLayers layers;
    private static RewardedAd cached;
    private static long cachedAt;
    private static boolean warming;
    private static long warmBlockedUntil;

    private VideoPass() {
    }

    /** True when the "watch videos" offer should be shown: ads are on in this build and Pro has not been bought. */
    static boolean offered(Context c) {
        return Ads.wanted(c);
    }

    private static synchronized AdLayers layers(Context c) {
        if (layers == null) {
            layers = new AdLayers(Arrays.asList(c.getString(R.string.admob_rewarded_id), c.getString(R.string.admob_rewarded_id_2),
                    c.getString(R.string.admob_rewarded_id_3)));
        }
        return layers;
    }

    /** What happens when a pass for the items in {@code mask} ends: what was set up with it goes back to the free version. */
    static String endNote(int mask) {
        return PassReset.needsResetMask(mask) ? "What you set up with it goes back to the free version then, unless you get Pro." : "";
    }

    /** What the running passes open and until when, one line each ("Strict pauses: open until tomorrow 4:30 PM."), or an empty string. */
    static String line(Context c) {
        try {
            List<PassLedger.Entry> run = Pro.get(c).runningPasses();
            if (run.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            int all = 0;
            for (PassLedger.Entry e : run) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(openLine(c, e));
                all |= e.mask;
            }
            String end = endNote(all);
            return end.isEmpty() ? sb.toString() : sb + "\n" + end;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** "Strict pauses: open until tomorrow 4:30 PM." for one pass. */
    private static String openLine(Context c, PassLedger.Entry e) {
        String what = DayPass.maskName(e.mask);
        if (!what.isEmpty()) what = Character.toUpperCase(what.charAt(0)) + what.substring(1);
        return what + ": open until "
                + LockRules.untilText(e.until, System.currentTimeMillis(), Store.zone(), DateFormat.is24HourFormat(c)) + ".";
    }

    /** The same for the running passes that open any of the items in {@code mask}. */
    private static String lineFor(Context c, int mask) {
        StringBuilder sb = new StringBuilder();
        for (PassLedger.Entry e : Pro.get(c).runningPasses()) {
            if ((e.mask & mask) == 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(openLine(c, e));
        }
        return sb.toString();
    }

    private static void say(Context c, String text) {
        Lang.toast(c, text, Toast.LENGTH_LONG).show();
    }

    private static void tell(Activity a, String title, String message) {
        try {
            if (gone(a)) return;
            new DBuilder(a).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "video message", e);
        }
    }

    private static boolean gone(Activity a) {
        return a.isFinishing() || a.isDestroyed();
    }

    // ------------------------------------------------------------------ the day's count

    private static int shownToday(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences("videos", Context.MODE_PRIVATE);
            return AdLayers.dayKey(System.currentTimeMillis(), Store.zone()).equals(p.getString("day", "")) ? p.getInt("count", 0) : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void countShown(Context c) {
        try {
            String day = AdLayers.dayKey(System.currentTimeMillis(), Store.zone());
            SharedPreferences p = c.getSharedPreferences("videos", Context.MODE_PRIVATE);
            int n = day.equals(p.getString("day", "")) ? p.getInt("count", 0) : 0;
            p.edit().putString("day", day).putInt("count", n + 1).apply();
        } catch (RuntimeException e) {
            CrashLog.note(c, "video count", e);
        }
    }

    // ------------------------------------------------------------------ asking for a video, layer by layer

    /** Where an ask for a video ended up. */
    private interface Done {
        void loaded(RewardedAd ad, int layer);

        void failed(String why);
    }

    /** One ask that may be given up on (the person pressed Cancel). */
    private static final class Chain {
        boolean cancelled;
    }

    /** Asks the layers in {@code plan} one after the other, starting at {@code pos}, until one gives a video. */
    private static void load(final Context app, final AdLayers ly, final int[] plan, final int pos, final Chain ch, final String why,
                             final Done done) {
        if (ch.cancelled) return;
        if (pos >= plan.length) {
            done.failed(why.isEmpty() ? "no ad units" : why);
            return;
        }
        final int layer = plan[pos];
        final boolean[] settled = {false};
        final Runnable timeout = () -> {
            if (settled[0]) return;
            settled[0] = true;
            ly.failed(layer, AdLayers.CODE_NETWORK, System.currentTimeMillis());
            Ads.get().noteVideo("layer " + (layer + 1) + " took too long");
            load(app, ly, plan, pos + 1, ch, "layer " + (layer + 1) + " took too long", done);
        };
        UI.postDelayed(timeout, LAYER_TIMEOUT_MS);
        Ads.get().noteVideo("asked layer " + (layer + 1) + " of " + ly.size());
        try {
            RewardedAd.load(app, ly.id(layer), new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    UI.removeCallbacks(timeout);
                    if (settled[0]) return;   // it took too long and the next layer is already being asked
                    settled[0] = true;
                    ly.failed(layer, error.getCode(), System.currentTimeMillis());
                    String what = "layer " + (layer + 1) + " failed, code " + error.getCode() + ": " + error.getMessage();
                    Ads.get().noteVideo(what);
                    load(app, ly, plan, pos + 1, ch, what, done);
                }

                @Override
                public void onAdLoaded(RewardedAd ad) {
                    UI.removeCallbacks(timeout);
                    ly.loaded(layer);
                    if (settled[0] || ch.cancelled) {   // too late for the ask it was for: keep it for the next one
                        stash(ad);
                        return;
                    }
                    settled[0] = true;
                    Ads.get().noteVideo("layer " + (layer + 1) + " loaded");
                    done.loaded(ad, layer);
                }
            });
        } catch (RuntimeException e) {
            UI.removeCallbacks(timeout);
            if (settled[0]) return;
            settled[0] = true;
            ly.failed(layer, AdLayers.CODE_INTERNAL, System.currentTimeMillis());
            CrashLog.note(app, "rewarded video load", e);
            load(app, ly, plan, pos + 1, ch, "layer " + (layer + 1) + " could not be asked", done);
        }
    }

    private static void stash(RewardedAd ad) {
        long now = System.currentTimeMillis();
        if (cached == null || !AdLayers.fresh(cachedAt, now)) {
            cached = ad;
            cachedAt = now;
        }
    }

    private static RewardedAd takeCached() {
        RewardedAd ad = cached;
        long at = cachedAt;
        cached = null;
        return ad != null && AdLayers.fresh(at, System.currentTimeMillis()) ? ad : null;
    }

    /**
     * Gets one video ready ahead of time, so the next "watch" starts at once. Called while an offer is on screen. Does nothing
     * when videos are not offered, one is ready already, one is being fetched, or a fetch just failed on every layer.
     */
    static void warm(Context context) {
        if (!busy()) preload(context);
    }

    private static void preload(Context context) {
        try {
            final Context app = context.getApplicationContext();
            long now = System.currentTimeMillis();
            if (!offered(app) || !Ads.get().ready() || warming || now < warmBlockedUntil) return;
            if (cached != null && AdLayers.fresh(cachedAt, now)) return;
            if (!AdLayers.underCap(shownToday(app))) return;
            warming = true;
            final AdLayers ly = layers(app);
            load(app, ly, ly.plan(now), 0, new Chain(), "", new Done() {
                @Override
                public void loaded(RewardedAd ad, int layer) {
                    warming = false;
                    stash(ad);
                }

                @Override
                public void failed(String why) {
                    warming = false;
                    warmBlockedUntil = System.currentTimeMillis() + WARM_PAUSE_MS;
                }
            });
        } catch (RuntimeException e) {
            warming = false;
            CrashLog.note(context, "video warm", e);
        }
    }

    // ------------------------------------------------------------------ one run of videos

    /** The videos one choice asks for, and how far it has got. */
    private static final class Run {
        final Activity a;
        final int item;
        final int need;
        final boolean extend;
        final Runnable done;
        int earned;
        /** The ad on screen was really shown (the ad network said so); no reward counts before that. */
        boolean shown;
        /** The ad network said this video was watched to the end. */
        boolean rewardThis;
        boolean over;
        Chain chain = new Chain();
        AlertDialog wait;

        Run(Activity a, int item, int need, boolean extend, Runnable done) {
            this.a = a;
            this.item = item;
            this.need = need;
            this.extend = extend;
            this.done = done;
        }
    }

    /** True while a run is going on. A run that stopped moving (its screen went away) is let go, so it never blocks the next one. */
    private static boolean busy() {
        Run r = active;
        if (r == null) return false;
        if (r.over || gone(r.a) || System.currentTimeMillis() - activeAt > STALE_MS) {
            active = null;
            return false;
        }
        return true;
    }

    private static void touch() {
        activeAt = System.currentTimeMillis();
    }

    /** One video for {@code item}, open for a day (the quickest way). */
    static void watch(final Activity a, final int item, final Runnable opened) {
        watch(a, item, DayPass.TIER_DAY, opened);
    }

    /**
     * Plays the videos a {@link DayPass} tier asks for (1, 2 or 3) for {@code item}: each video watched to the end adds its step
     * at once (the item for a day; then three days; then the rest of its group for a day). {@code opened} (may be null) runs
     * afterwards, on the main thread, when something was opened.
     */
    static void watch(final Activity a, final int item, final int tier, final Runnable opened) {
        if (!DayPass.tierOffered(tier, item)) return;
        begin(new Run(a, item, DayPass.videosFor(tier), false, opened));
    }

    /** One more video stretches the day-long pass on {@code item} to three days. */
    static void extend(final Activity a, final int item, final Runnable opened) {
        begin(new Run(a, item, 1, true, opened));
    }

    private static void begin(Run run) {
        final Activity a = run.a;
        try {
            final Pro pro = Pro.get(a);
            if (pro.owned()) return;
            if (busy()) {
                say(a, "A video is already on its way.");
                return;
            }
            if (run.extend) {
                if (!pro.canExtendPass(run.item)) {
                    say(a, "That cannot be kept longer.");
                    return;
                }
            } else if (!pro.canStartPass(run.item)) {
                // Nothing is asked of the person and nothing is given: it is open already.
                String open = lineFor(a, DayPass.bit(run.item));
                say(a, open.isEmpty() ? "That is open already." : open);
                return;
            }
            if (!AdLayers.underCap(shownToday(a))) {
                tell(a, "That is plenty for today", "You have watched a lot of videos today. Try again tomorrow, or get Pro: it is one payment, "
                        + "opens everything for good and removes the ads.");
                return;
            }
            if (!Ads.get().ready()) {
                Ads.get().start(a);
                Ads.get().noteVideo("not asked for: ads are not ready yet");
                say(a, "Videos are not ready yet. Check your connection and try again in a moment.");
                return;
            }
            active = run;
            touch();
            next(run);
        } catch (RuntimeException e) {
            active = null;
            CrashLog.note(a, "rewarded video", e);
            say(a, "A video could not be started. Try again in a moment.");
        }
    }

    /** Plays the next video of the run: the one ready ahead of time, or one asked for now. */
    private static void next(final Run run) {
        final Activity a = run.a;
        try {
            touch();
            if (gone(a)) {
                finish(run);
                return;
            }
            RewardedAd ready = takeCached();
            if (ready != null) {
                Ads.get().noteVideo("using the video that was ready");
                present(run, ready);
                return;
            }
            run.shown = false;
            run.rewardThis = false;
            run.chain = new Chain();
            run.wait = new DBuilder(a)
                    .setMessage(run.earned == 0 ? "Getting a short video…" : "Getting the next video…")
                    .setNegativeButton("Cancel", (d, w) -> cancelWait(run))
                    .setOnCancelListener(d -> cancelWait(run))
                    .create();
            run.wait.show();
            final AdLayers ly = layers(a);
            load(a.getApplicationContext(), ly, ly.plan(System.currentTimeMillis()), 0, run.chain, "", new Done() {
                @Override
                public void loaded(RewardedAd ad, int layer) {
                    dismiss(run.wait);
                    if (run.over) {
                        stash(ad);
                        return;
                    }
                    try {
                        present(run, ad);
                    } catch (RuntimeException e) {
                        CrashLog.note(a, "rewarded video show", e);
                        finish(run);
                        say(a, "The video could not be shown. Try again in a little while.");
                    }
                }

                @Override
                public void failed(String why) {
                    dismiss(run.wait);
                    if (run.over) return;
                    Ads.get().noteVideo("failed: " + why);
                    CrashLog.note(a, "rewarded video", new IllegalStateException(why));
                    boolean any = run.earned > 0;
                    finish(run);   // a later video that is missing never costs the earlier ones: they were given when they ended
                    if (!any) tell(a, "No video right now", "There is no video to show at the moment. Try again in a little while. "
                            + "Nothing was used up and nothing was opened.");
                    else say(a, "The next video could not be found. You keep what you already earned.");
                }
            });
        } catch (RuntimeException e) {
            CrashLog.note(a, "rewarded video", e);
            finish(run);
            say(a, "A video could not be started. Try again in a moment.");
        }
    }

    private static void cancelWait(Run run) {
        if (run.over) return;
        run.chain.cancelled = true;
        finish(run);
    }

    private static void present(final Run run, final RewardedAd ad) {
        final Activity a = run.a;
        if (gone(a)) {
            stash(ad);
            finish(run);
            return;
        }
        run.shown = false;
        run.rewardThis = false;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                run.shown = true;
                touch();
                countShown(a);
                // while this one plays, the next is fetched, so "next video" starts at once
                if (run.earned + 1 < run.need) preload(a);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                touch();
                if (run.over) return;
                if (run.rewardThis) {
                    if (run.earned >= run.need) finish(run);
                    else askNext(run);
                } else if (run.earned > 0) {
                    finish(run);   // closed early, but the earlier videos still count
                } else {
                    finish(run);
                    say(a, "Watch the whole video to open it. Nothing was opened.");
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError error) {
                String what = "could not be shown, code " + error.getCode() + ": " + error.getMessage();
                Ads.get().noteVideo(what);
                CrashLog.note(a, "rewarded video", new IllegalStateException(what));
                if (run.over) return;
                boolean any = run.earned > 0;
                finish(run);
                if (!any) tell(a, "No video right now", "The video could not be shown. Try again in a little while. Nothing was opened.");
            }
        });
        ad.show(a, reward -> {
            // Only an ad that was really shown, and only once per video, can earn anything.
            if (!run.shown || run.rewardThis || run.over) {
                Ads.get().noteVideo("reward ignored: shown=" + run.shown + " already=" + run.rewardThis);
                return;
            }
            run.rewardThis = true;
            Ads.get().noteVideo("watched, reward given");
            reward(run);
        });
    }

    /** The video was watched to the end: gives its step now, so nothing earned is ever lost. */
    private static void reward(Run run) {
        final Activity a = run.a;
        try {
            Pro pro = Pro.get(a);
            run.earned++;
            if (run.extend) {
                pro.extendPass(run.item);
            } else if (DayPass.stepExtends(run.earned)) {
                pro.extendPass(run.item);
            } else {
                pro.grant(DayPass.stepMask(run.earned, run.item), DayPass.LENGTH_MS);
            }
        } catch (RuntimeException e) {
            CrashLog.note(a, "video grant", e);
        }
    }

    /** Between two videos: what the next one adds, and a way to stop with what has been earned. */
    private static void askNext(final Run run) {
        final Activity a = run.a;
        try {
            touch();
            if (gone(a)) {
                finish(run);
                return;
            }
            final int got = run.earned;
            final int nextStep = got + 1;
            final String item = DayPass.name(run.item);
            final int left = run.need - got;
            String ask;
            if (DayPass.stepExtends(nextStep)) {
                ask = left == 1 ? "Watch one more video to keep " + item + " open for 3 days."
                        : "Watch " + left + " more videos to keep " + item + " open for 3 days.";
            } else {
                String group = DayPass.maskName(DayPass.tierMask(DayPass.TIER_GROUP, run.item));
                ask = left == 1 ? "Watch one more video to open " + group + " for a day."
                        : "Watch " + left + " more videos to open " + group + " for a day.";
            }
            String title = "Video " + got + " of " + run.need + " watched";
            String message = Character.toUpperCase(item.charAt(0)) + item.substring(1) + " is open now.\n\n"
                    + ask + "\n\nOr stop here and keep what you have.";
            new DBuilder(a)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Next video", (d, w) -> next(run))
                    .setNegativeButton("Stop here", (d, w) -> finish(run))
                    .setOnCancelListener(d -> finish(run))
                    .show();
        } catch (RuntimeException e) {
            CrashLog.note(a, "video next", e);
            finish(run);
        }
    }

    /** The run is over, whichever way: tells the person what is open now (if anything) and lets the screen carry on. */
    private static void finish(Run run) {
        if (run.over) return;
        run.over = true;
        run.chain.cancelled = true;
        if (active == run) active = null;
        dismiss(run.wait);
        final Activity a = run.a;
        if (run.earned <= 0) return;
        try {
            Pro pro = Pro.get(a);
            int mask = DayPass.bit(run.item) | DayPass.stepMask(DayPass.TIER_GROUP, run.item);
            String open = lineFor(a, mask);
            if (open.isEmpty()) {
                if (!pro.owned()) say(a, "It could not be opened. Please try again.");
                return;
            }
            say(a, "Thank you for watching.\n" + open);
            if (run.done != null) run.done.run();
        } catch (RuntimeException e) {
            CrashLog.note(a, "video finish", e);
        }
    }

    private static void dismiss(AlertDialog d) {
        try {
            if (d != null && d.isShowing()) d.dismiss();
        } catch (RuntimeException ignored) {
            // the screen is already gone
        }
    }
}
