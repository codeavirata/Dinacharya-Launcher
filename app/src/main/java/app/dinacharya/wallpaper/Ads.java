package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * The small banner ads shown to people who have not bought Pro.
 *
 * Order matters: first the consent form (shown only where the law asks for it, such as the EU, the
 * UK and some US states), then, only if the answer allows ads, Google's ads library is started. Until
 * then nothing is requested and no banner takes up room. Ads never appear on the alarm, pause or lock screens, the
 * wallpaper or in a PDF.
 */
final class Ads {
    private static Ads instance;

    static synchronized Ads get() {
        if (instance == null) instance = new Ads();
        return instance;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ConsentInformation consent;
    private boolean started;
    private boolean initializing;
    private volatile boolean ready;
    private Runnable onReady;
    private int retries;
    private String lastBanner = "no banner asked for yet";
    private String lastVideo = "no video asked for yet";
    private String lastConsent = "not asked yet";

    private Ads() {
    }

    /** True when ads are switched on in this build and this person does not have Pro. */
    static boolean wanted(Context c) {
        try {
            return c.getResources().getBoolean(R.bool.ads_enabled) && !Pro.isPro(c);
        } catch (RuntimeException e) {
            return false;
        }
    }

    boolean ready() {
        return ready;
    }

    /** Called when ads become possible, so banners can appear. */
    void setOnReady(Runnable r) {
        this.onReady = r;
    }

    /** Asks for consent where it is required, then starts the ads library. Call from the main screen. */
    void start(final Activity a) {
        if (started || !wanted(a)) return;
        started = true;
        try {
            consent = UserMessagingPlatform.getConsentInformation(a);
            final java.lang.ref.WeakReference<Activity> host = new java.lang.ref.WeakReference<>(a);
            consent.requestConsentInfoUpdate(a, new ConsentRequestParameters.Builder().build(),
                    () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(a, formError -> {
                        if (formError != null) {
                            lastConsent = "form error " + formError.getErrorCode() + ": " + formError.getMessage();
                            CrashLog.note(a, "ads consent form", new IllegalStateException(formError.getMessage()));
                        } else {
                            lastConsent = "answered";
                        }
                        begin(a);
                    }),
                    error -> {
                        lastConsent = "update error " + error.getErrorCode() + ": " + error.getMessage();
                        CrashLog.note(a, "ads consent", new IllegalStateException(error.getMessage()));
                        begin(a);   // an earlier answer may still allow ads
                        retryLater(host);
                    });
            // An answer from an earlier visit may already allow ads: do not wait for the network to start.
            if (consent.canRequestAds()) begin(a);
        } catch (RuntimeException e) {
            started = false;
            CrashLog.note(a, "ads start", e);
        }
    }

    /** The consent check could not reach Google: try the whole start again a little later (up to three times). */
    private void retryLater(final java.lang.ref.WeakReference<Activity> host) {
        if (ready || retries >= 3) return;
        retries++;
        ui.postDelayed(() -> {
            Activity a = host.get();
            if (a == null || a.isFinishing() || ready) return;
            started = false;
            start(a);
        }, 30000L * retries);
    }

    /** What the last banner did, for the problem report and the line under the banner. */
    void noteBanner(String what) {
        lastBanner = what;
    }

    /** What the last rewarded video did, for the problem report. */
    void noteVideo(String what) {
        lastVideo = what;
    }

    /** One line about the state of the ads, for the problem report. */
    String status(Context c) {
        StringBuilder sb = new StringBuilder("Ads: ");
        try {
            sb.append(c.getResources().getBoolean(R.bool.ads_enabled) ? "on in this build" : "off in this build");
            sb.append(", Pro ").append(Pro.isPro(c) ? "owned" : "not owned");
            sb.append(", consent ").append(lastConsent);
            sb.append(", canRequestAds ").append(consent != null && consent.canRequestAds());
            sb.append(", library ready ").append(ready);
            sb.append(", banner: ").append(lastBanner);
            sb.append(", video: ").append(lastVideo);
        } catch (RuntimeException e) {
            sb.append("unknown");
        }
        return sb.toString();
    }

    private void begin(Activity a) {
        try {
            if (ready || initializing || consent == null || !consent.canRequestAds()) return;
            initializing = true;
            final Context app = a.getApplicationContext();
            new Thread(() -> {
                try {
                    MobileAds.initialize(app, status -> ui.post(() -> {
                        ready = true;
                        initializing = false;
                        if (onReady != null) onReady.run();
                    }));
                } catch (RuntimeException e) {
                    initializing = false;
                    CrashLog.note(app, "ads initialize", e);
                }
            }, "ads-init").start();
        } catch (RuntimeException e) {
            CrashLog.note(a, "ads begin", e);
        }
    }

    /** True where the law wants a "privacy choices" entry in the app. */
    boolean privacyOptionsRequired() {
        try {
            return consent != null && consent.getPrivacyOptionsRequirementStatus()
                    == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    void showPrivacyOptions(final Activity a) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(a, formError -> {
                if (formError != null) CrashLog.note(a, "ads privacy options", new IllegalStateException(formError.getMessage()));
            });
        } catch (RuntimeException e) {
            CrashLog.note(a, "ads privacy options", e);
        }
    }
}
