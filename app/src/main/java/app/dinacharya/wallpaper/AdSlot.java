package app.dinacharya.wallpaper;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;

/**
 * A place on a page for one small banner. It is on the page (and keeps room for the banner) whenever ads are wanted and
 * possible: a banner inside a hidden view is never asked for or drawn, so the slot cannot wait out of sight until an
 * ad has arrived. If an ad cannot load it says so in one small line and tries again a little later. When someone
 * buys Pro it removes itself and frees the ad.
 */
final class AdSlot extends LinearLayout {
    private static final long RETRY_MS = 45000L;
    private static final int MAX_TRIES = 6;

    private final Activity a;
    private final Runnable openPro;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private AdView adView;
    private TextView status;
    private int tries;

    AdSlot(Activity host, Runnable openPro) {
        super(host);
        this.a = host;
        this.openPro = openPro;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setVisibility(GONE);
        setPadding(Ui.dp(host, 8), Ui.dp(host, 8), Ui.dp(host, 8), Ui.dp(host, 6));
        setBackground(Ui.box(host, Ui.SURFACE, 0, 18));
    }

    /** Shows, loads or removes the banner to match whether ads are wanted and possible right now. */
    void refresh() {
        try {
            if (!Ads.wanted(a) || !Ads.get().ready()) {
                release();
                return;
            }
            if (adView != null) return;   // already showing or loading
            setVisibility(VISIBLE);
            adView = new AdView(a);
            adView.setAdUnitId(a.getString(R.string.admob_banner_id));
            adView.setAdSize(AdSize.BANNER);
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    tries = 0;
                    Ads.get().noteBanner("loaded");
                    if (status != null) status.setVisibility(GONE);
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    String what = "failed, code " + error.getCode() + ": " + error.getMessage();
                    Ads.get().noteBanner(what);
                    CrashLog.note(a, "banner", new IllegalStateException(what));
                    if (status != null) {
                        status.setText("No ad right now (code " + error.getCode() + "). Trying again soon.");
                        status.setVisibility(VISIBLE);
                    }
                    retrySoon();
                }
            });
            // A fixed box the size of a banner, so the page does not jump when the ad arrives.
            FrameLayout box = new FrameLayout(a);
            box.addView(adView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(a, 50)));
            status = Ui.label(a, "Loading an ad…", 11, Ui.MUTED, false);
            status.setGravity(Gravity.CENTER);
            addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView remove = Ui.label(a, "Remove ads with Pro", 12, Ui.TEAL_DARK, true);
            remove.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 2));
            remove.setOnClickListener((View v) -> openPro.run());
            addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Ads.get().noteBanner("asked for");
            adView.loadAd(new AdRequest.Builder().build());
        } catch (RuntimeException e) {
            CrashLog.note(a, "banner", e);
            release();
        }
    }

    private void retrySoon() {
        if (tries >= MAX_TRIES) return;
        tries++;
        ui.postDelayed(() -> {
            try {
                if (adView != null && Ads.wanted(a)) adView.loadAd(new AdRequest.Builder().build());
            } catch (RuntimeException e) {
                CrashLog.note(a, "banner retry", e);
            }
        }, RETRY_MS);
    }

    /** Takes the banner away and frees it. */
    void release() {
        try {
            ui.removeCallbacksAndMessages(null);
            if (adView != null) {
                adView.destroy();
                adView = null;
            }
            status = null;
            tries = 0;
            removeAllViews();
            setVisibility(GONE);
        } catch (RuntimeException e) {
            CrashLog.note(a, "banner release", e);
        }
    }
}
