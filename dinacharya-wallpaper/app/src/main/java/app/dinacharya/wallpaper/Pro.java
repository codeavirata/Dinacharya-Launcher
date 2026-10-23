package app.dinacharya.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Dinacharya Pro": one purchase through Google Play (set to Rs 99 in Play Console; the app shows whatever
 * price Play reports) that removes the ads and unlocks the extras: the report for every saved day and the week
 * view, any number of paused apps with strict, limit and playful pauses, locked groups and strict locks,
 * weekday and weekend routines, goals and type totals, all colour themes. Everything else stays free.
 *
 * Someone who has not bought Pro can also watch short videos to open some of those for a while (a {@link DayPass}): one video
 * opens ONE item (strict pauses, say, or the Ocean colour look) for a day, two open the same item for three days, three open
 * the whole group the item is in (all the pauses, say) for a day. Every item has its own pass and its own timer
 * ({@link PassLedger}), so unlocking one thing never opens another and two things need two videos. Screens ask
 * {@link #has} about the item they are about to show, or take the {@link #open} mask, never {@link #isPro}, which is
 * only for what a pass must not open (the ads, the purchase itself). When a pass starts, a note of what exists is kept
 * here too; when it ends, {@link PassSettle} puts back what was made with it (see {@link PassReset}).
 *
 * Whether the user is Pro is kept on the phone and checked against Google Play each time the app
 * starts, so a refund or a new phone is picked up. If Google Play cannot be reached the last known
 * answer is kept, so a bad connection never takes Pro away. The record is not part of any backup.
 */
final class Pro {
    interface Listener {
        void onProChanged();
    }

    private static final String PREFS = "pro";
    private static final String K_PRO = "pro";
    private static final String K_DEBUG = "debugPro";
    // the passes videos gave (see PassLedger): one entry per pass, each with its items, start, end and the note of what existed.
    private static final String K_PASSES = "passes";
    // Versions before 2.8 kept ONE pass in separate values: the items (a mask), when it started and when it ends. Versions before
    // 2.7 kept one item (passItem) and no mask. They are still read once, so a pass running during an update carries on.
    private static final String K_PASS_MASK = "passMask", K_PASS_F = "passItem", K_PASS_FROM = "passItemFrom", K_PASS_UNTIL = "passItemUntil";
    private static final String K_PASS_NOTE = "passNote";
    // what to tell the person about passes that ended and put things back, until they have been told
    private static final String K_PASS_MSG = "passMessage";

    private static Pro instance;

    static synchronized Pro get(Context c) {
        if (instance == null) instance = new Pro(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private BillingClient client;
    private ProductDetails details;
    private boolean connecting;

    private Pro(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ the answer

    /** True when this person has Pro. */
    static boolean isPro(Context c) {
        return get(c).owned();
    }

    boolean owned() {
        try {
            return prefs.getBoolean(K_PRO, false) || (debuggable() && prefs.getBoolean(K_DEBUG, false));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when {@code item} (a {@link DayPass} item) is open to this person: they have Pro, or a video pass for just that item is running. */
    static boolean has(Context c, int item) {
        return get(c).hasFeature(item);
    }

    boolean hasFeature(int item) {
        return DayPass.has(open(), item);
    }

    /**
     * Everything that is open to this person right now, as a {@link DayPass} mask: all of it with Pro, otherwise only the
     * items a running video pass opened (or nothing). The rule classes take this instead of a plain "is Pro".
     */
    static int open(Context c) {
        return get(c).open();
    }

    int open() {
        try {
            if (owned()) return DayPass.ALL;
            return runningMask();
        } catch (RuntimeException e) {
            return DayPass.FREE;
        }
    }

    /** True when colour look number {@code palette} may be used: the free look always, any other with Pro or a pass for that look. */
    static boolean hasLook(Context c, int palette) {
        return palette <= 0 || has(c, DayPass.lookItem(palette));
    }

    // ------------------------------------------------------------------ the passes

    private PassLedger cache;
    private String cacheRaw;

    /** The passes, read from the phone (and, once, brought over from the single-pass record of an earlier version). */
    private synchronized PassLedger ledger() {
        String raw = prefs.getString(K_PASSES, null);
        if (raw == null) raw = migrate();
        if (cache == null || !raw.equals(cacheRaw)) {
            cache = PassLedger.parse(raw);
            cacheRaw = raw;
        }
        return cache;
    }

    /** What the earlier single-pass record held, as a list; the old values are removed once it is kept. */
    private String migrate() {
        int mask = prefs.getInt(K_PASS_MASK, 0);
        if (mask == 0) mask = DayPass.bit(prefs.getInt(K_PASS_F, 0));
        String note = prefs.getString(K_PASS_NOTE, "");
        if (note == null) note = "";
        if (mask == 0 && !note.isEmpty()) mask = PassReset.maskOf(note);   // only a note is left: it still has to be put back
        PassLedger l = PassLedger.fromOnePass(mask, prefs.getLong(K_PASS_FROM, 0), prefs.getLong(K_PASS_UNTIL, 0), note);
        String raw = l.write();
        prefs.edit().putString(K_PASSES, raw).remove(K_PASS_MASK).remove(K_PASS_F).remove(K_PASS_FROM).remove(K_PASS_UNTIL)
                .remove(K_PASS_NOTE).apply();
        return raw;
    }

    /** Keeps the list. */
    private synchronized void keep(PassLedger l) {
        String raw = l.write();
        prefs.edit().putString(K_PASSES, raw).apply();
        cache = PassLedger.parse(raw);
        cacheRaw = raw;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** The items the running passes open right now (a {@link DayPass} mask), or 0. */
    int passMask() {
        try {
            return ledger().open(now());
        } catch (RuntimeException e) {
            return DayPass.FREE;
        }
    }

    private int runningMask() {
        return ledger().open(now());
    }

    /** The first item a pass is running for right now, or {@link DayPass#NONE}. */
    int passItem() {
        return DayPass.firstItem(passMask());
    }

    /** When the first of the running passes ends (milliseconds), or 0 when none runs. */
    long passUntil() {
        try {
            return ledger().nextEnd(now());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** When the pass that opens {@code item} ends, or 0 when no pass opens it. */
    long passUntil(int item) {
        try {
            PassLedger.Entry e = ledger().covering(item, now());
            return e == null ? 0 : e.until;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** When the pass that opens {@code item} began, or 0 when no pass opens it. */
    long passFrom(int item) {
        try {
            PassLedger.Entry e = ledger().covering(item, now());
            return e == null ? 0 : e.from;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** The passes running now, the oldest first (copies; changing them changes nothing). */
    List<PassLedger.Entry> runningPasses() {
        try {
            List<PassLedger.Entry> out = new ArrayList<>();
            for (PassLedger.Entry e : ledger().running(now())) out.add(new PassLedger.Entry(e.mask, e.from, e.until, ""));
            return out;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    /** True when a video may be watched for {@code item} now: Pro is not owned and the item is not open already. */
    boolean canStartPass(int item) {
        try {
            return !owned() && DayPass.valid(item) && ledger().free(DayPass.bit(item), now()) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the pass on {@code item} is a day long, for that item alone, and one more video can stretch it to three days. */
    boolean canExtendPass(int item) {
        try {
            return !owned() && ledger().canExtend(item, now());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Stretches the pass on {@code item} to three days from when it began. Returns false when that is not possible. */
    boolean extendPass(int item) {
        try {
            if (owned()) return false;
            synchronized (this) {
                PassLedger l = ledger().copy();
                if (!l.extend(item, now())) return false;
                keep(l);
            }
            PassSettle.schedule(app);
            changed();
            return true;
        } catch (RuntimeException e) {
            CrashLog.note(app, "day pass extend", e);
            return false;
        }
    }

    /** Opens one {@code item} for a day, starting now, and nothing else. Returns false if it is open already. */
    boolean grantPass(int item) {
        return DayPass.valid(item) && grantPass(DayPass.bit(item), DayPass.LENGTH_MS);
    }

    /** {@link #grant} as a yes or no. */
    boolean grantPass(int mask, long lengthMs) {
        return grant(mask, lengthMs) != 0;
    }

    /**
     * Opens the items in {@code mask} that are not open yet for {@code lengthMs} (at most three days), starting now, as a pass of
     * its own, and nothing else. Passes that are running are not touched. Returns the items it opened: 0 when nothing happened
     * (nothing left to open, a bad length, or Pro is owned).
     */
    int grant(int mask, long lengthMs) {
        try {
            if (owned() || !DayPass.validMask(mask) || lengthMs <= 0 || lengthMs > DayPass.MAX_MS) return 0;
            // A pass that ended earlier gives back its changes first, so this one starts from what is really there.
            // (Done before the lock below is taken: PassSettle takes its own lock first and this one second.)
            PassSettle.run(app);
            int got;
            synchronized (this) {
                long now = now();
                PassLedger l = ledger().copy();
                int opens = l.free(mask, now);
                if (opens == 0) return 0;
                String note = PassReset.captureMask(opens, LauncherStore.load(app), Store.load(app));
                got = l.add(opens, lengthMs, now, note);
                if (got == 0) return 0;
                keep(l);
            }
            recentMask = got;
            recentAt = now();
            PassSettle.schedule(app);
            changed();
            return got;
        } catch (RuntimeException e) {
            CrashLog.note(app, "day pass", e);
            return 0;
        }
    }

    private static volatile int recentMask;
    private static volatile long recentAt;

    /**
     * What a video opened in the last few minutes and is still open (a {@link DayPass} mask), or 0. Lets a screen that needs one more
     * thing right after ("also more than 2 apps") say what is already open.
     */
    int recentlyOpened() {
        try {
            if (now() - recentAt > 3 * 60000L) return DayPass.FREE;
            return recentMask & ledger().open(now());
        } catch (RuntimeException e) {
            return DayPass.FREE;
        }
    }

    /** The passes that are over and whose changes may still have to be put back, the oldest first (copies). */
    synchronized List<PassLedger.Entry> endedPasses() {
        try {
            List<PassLedger.Entry> out = new ArrayList<>();
            for (PassLedger.Entry e : ledger().ended(now())) out.add(new PassLedger.Entry(e.mask, e.from, e.until, e.note));
            return out;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    /** Takes the passes that were settled (or no longer matter) out of the list. */
    synchronized void forgetPasses(List<PassLedger.Entry> done) {
        try {
            PassLedger l = ledger().copy();
            List<PassLedger.Entry> gone = new ArrayList<>();
            for (PassLedger.Entry d : done) {
                for (PassLedger.Entry e : l.entries()) {
                    if (e.mask == d.mask && e.from == d.from && e.until == d.until) {
                        gone.add(e);
                        break;
                    }
                }
            }
            l.remove(gone);
            keep(l);
        } catch (RuntimeException e) {
            CrashLog.note(app, "pass forget", e);
        }
    }

    /** Forgets what to put back, for every pass (a restored backup is what the person chose to keep). */
    synchronized void clearPassNotes() {
        try {
            PassLedger l = ledger().copy();
            l.clearNotes();
            keep(l);
        } catch (RuntimeException e) {
            CrashLog.note(app, "pass notes", e);
        }
    }

    /** When the first pass that still has something to put back is due (running or over), or 0 when none has. */
    long nextPassNoteEnd() {
        try {
            return ledger().nextNoteEnd();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Adds to what the person is told about passes that ended (until they have been told). */
    synchronized void addPassMessage(String text) {
        if (text == null || text.isEmpty()) return;
        String old = prefs.getString(K_PASS_MSG, "");
        prefs.edit().putString(K_PASS_MSG, old == null || old.isEmpty() ? text : old + "\n\n" + text).apply();
    }

    /** What to tell the person about finished passes, once (it is forgotten after this); null when there is nothing. */
    String takePassMessage() {
        try {
            String m = prefs.getString(K_PASS_MSG, "");
            if (m == null || m.isEmpty()) return null;
            prefs.edit().remove(K_PASS_MSG).apply();
            return m;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A test build installed straight onto a phone (not from Google Play) can switch Pro on to try the extras. */
    boolean debuggable() {
        return (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    void setDebugPro(boolean on) {
        if (!debuggable()) return;
        prefs.edit().putBoolean(K_DEBUG, on).apply();
        changed();
    }

    boolean debugPro() {
        return debuggable() && prefs.getBoolean(K_DEBUG, false);
    }

    /** "₹99" once Google Play has told us, otherwise an empty string. */
    String price() {
        try {
            ProductDetails d = details;
            if (d == null || d.getOneTimePurchaseOfferDetails() == null) return "";
            String p = d.getOneTimePurchaseOfferDetails().getFormattedPrice();
            return p == null ? "" : p;
        } catch (RuntimeException e) {
            return "";
        }
    }

    boolean canBuy() {
        return details != null;
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        ui.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) {
                try {
                    l.onProChanged();
                } catch (RuntimeException e) {
                    CrashLog.note(app, "pro listener", e);
                }
            }
        });
    }

    private void setOwned(boolean on) {
        boolean was = prefs.getBoolean(K_PRO, false);
        prefs.edit().putBoolean(K_PRO, on).apply();
        if (was != on) changed();
    }

    // ------------------------------------------------------------------ Google Play

    private String productId() {
        return app.getString(R.string.pro_product_id);
    }

    /** Connects to Google Play, finds the price and checks what this account owns. Safe to call often. */
    void start() {
        try {
            ensureClient();
            if (client.isReady()) {
                queryProduct();
                queryOwned();
                return;
            }
            if (connecting) return;
            connecting = true;
            client.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult r) {
                    connecting = false;
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        queryProduct();
                        queryOwned();
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    connecting = false;   // it reconnects by itself the next time it is used
                }
            });
        } catch (RuntimeException e) {
            connecting = false;
            CrashLog.note(app, "billing start", e);
        }
    }

    private void ensureClient() {
        if (client != null) return;
        client = BillingClient.newBuilder(app)
                .setListener((result, purchases) -> {
                    int code = result.getResponseCode();
                    if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (Purchase p : purchases) handle(p);
                    } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        queryOwned();
                    } else if (code != BillingClient.BillingResponseCode.USER_CANCELED) {
                        say("The purchase did not go through. You were not charged.");
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    private void queryProduct() {
        try {
            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId())
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()))
                    .build();
            client.queryProductDetailsAsync(params, (result, found) -> {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || found == null) return;
                List<ProductDetails> list = found.getProductDetailsList();
                if (list != null && !list.isEmpty()) {
                    details = list.get(0);
                    changed();
                }
            });
        } catch (RuntimeException e) {
            CrashLog.note(app, "billing product", e);
        }
    }

    /** Asks Google Play what this account owns. Only a clear answer changes anything. */
    private void queryOwned() {
        try {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                    (result, purchases) -> {
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) return;
                        boolean has = false;
                        for (Purchase p : purchases) {
                            if (isOurs(p) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                has = true;
                                acknowledge(p);
                            }
                        }
                        setOwned(has);
                    });
        } catch (RuntimeException e) {
            CrashLog.note(app, "billing owned", e);
        }
    }

    private boolean isOurs(Purchase p) {
        List<String> ids = p.getProducts();
        return ids != null && ids.contains(productId());
    }

    private void handle(Purchase p) {
        if (!isOurs(p)) return;
        if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            acknowledge(p);
            setOwned(true);
            say("Thank you! Dinacharya Pro is on.");
        } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            say("Your payment is pending. Pro turns on as soon as it goes through.");
        }
    }

    private void acknowledge(Purchase p) {
        try {
            if (p.isAcknowledged()) return;
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                    r -> { /* Google refunds an unacknowledged purchase after three days; it is retried at the next start */ });
        } catch (RuntimeException e) {
            CrashLog.note(app, "billing acknowledge", e);
        }
    }

    /** Opens Google Play's purchase sheet. */
    void buy(Activity a) {
        try {
            if (details == null) {
                start();
                say("Pro is not available right now. It is sold through Google Play, so install the app from there, "
                        + "check your connection and try again.");
                return;
            }
            BillingFlowParams.ProductDetailsParams.Builder one = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details);
            if (details.getOneTimePurchaseOfferDetails() != null) {
                String token = details.getOneTimePurchaseOfferDetails().getOfferToken();
                if (token != null && !token.isEmpty()) one.setOfferToken(token);
            }
            BillingResult r = client.launchBillingFlow(a, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(one.build()))
                    .build());
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                say("Could not open Google Play. Try again in a moment.");
            }
        } catch (RuntimeException e) {
            CrashLog.note(app, "billing buy", e);
            say("Could not open Google Play. Try again in a moment.");
        }
    }

    /** Checks Google Play again, for someone who bought Pro on another phone or before reinstalling. */
    void restore() {
        say("Checking Google Play…");
        start();
    }

    private void say(String text) {
        ui.post(() -> Lang.toast(app, text, android.widget.Toast.LENGTH_LONG).show());
    }
}
