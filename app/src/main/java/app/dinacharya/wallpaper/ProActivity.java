package app.dinacharya.wallpaper;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Dinacharya Pro: what is free, what Pro adds, the two plans (monthly, or lifetime) with the prices Google Play shows, and the
 * subscribe, buy, manage and restore buttons. Every
 * "See Pro" button in the app opens this one screen, on top of whatever the person was doing, so Back returns to it.
 */
public class ProActivity extends LangActivity {
    private final Pro.Listener listener = this::render;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.SOFT);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Insets.pad(sv);
        setContentView(sv);
        Insets.lightBars(this);
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Pro.get(this).addListener(listener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pro.get(this).start();   // refreshes the price and what this account owns
        render();
    }

    @Override
    protected void onStop() {
        Pro.get(this).removeListener(listener);
        super.onStop();
    }

    private void render() {
        if (body == null) return;
        try {
            body.removeAllViews();
            final Pro p = Pro.get(this);
            boolean owned = p.owned();
            boolean life = p.lifetimeOwned();
            boolean month = p.monthlyOwned();
            body.addView(Ui.eyebrow(this, "Dinacharya"));
            body.addView(Ui.label(this, "Dinacharya Pro", 28, Ui.INK, true), Ui.wide(this, 4));
            body.addView(Ui.label(this, owned ? "You have Pro. Thank you!"
                    : "Choose a monthly plan or pay once for life. Paid through Google Play.",
                    15, owned ? Ui.TEAL : Ui.MUTED, owned), Ui.wide(this, 6));

            if (month && !life) {
                body.addView(small("Your monthly plan is active. It renews every month until you cancel it in Google Play.", Ui.MUTED),
                        Ui.wide(this, 6));
                body.addView(Ui.button(this, "Manage subscription", false, v -> p.manageSubscription(this)), Ui.wide(this, 12));
            }

            if (!owned || (month && !life)) {
                if (!owned) addMonthly(p);
                addLifetime(p, month);
                if (!owned && p.monthlyPrice().isEmpty() && p.price().isEmpty()) {
                    TextView note = Ui.label(this, "The price comes from Google Play. It shows here once Play answers, and Pro can only be "
                            + "bought in a copy of the app that was installed from Google Play.", 12, Ui.MUTED, false);
                    note.setLineSpacing(Ui.dp(this, 2), 1f);
                    body.addView(note, Ui.wide(this, 6));
                }
            } else if (p.debugPro()) {
                body.addView(Ui.label(this, "Switched on for testing on this phone.", 14, Ui.TEAL, true), Ui.wide(this, 10));
            }

            if (!owned && VideoPass.offered(this)) {
                LinearLayout trial = Ui.section(this, body, "Try Pro free for a day", "Watch " + VideoProgress.NEED + " short videos");
                trial.addView(small("Watch " + VideoProgress.NEED + " short videos and everything in Pro is open for 24 hours: every saved day and the week "
                        + "in Performance, the deeper analysis, more pauses and locks, routines, goals and the colour looks. Nothing is charged. "
                        + "When the day is up, what you set up with it goes back to the free version (Pro keeps it for good).", Ui.MUTED), Ui.wide(this, 4));
                ProPrompt.addOffer(this, trial, 10, this::render);
            }

            LinearLayout card = Ui.section(this, body, "What is free, and what Pro adds", null);
            card.addView(small("Everything the app does, Free and Pro side by side, in groups you open one at a time.", Ui.MUTED), Ui.wide(this, 2));
            card.addView(Ui.button(this, "Compare Free and Pro", false, v -> startActivity(new android.content.Intent(this, CompareActivity.class))),
                    Ui.wide(this, 12));

            Button restore = Ui.button(this, "Restore my purchase", false, v -> p.restore());
            body.addView(restore, Ui.wide(this, 18));
            body.addView(small("Tied to your Google Play account, so it comes back on a new phone. Refunds follow Google Play's rules. "
                    + "If Pro ever ends (a refund, say), nothing you made is taken away: a routine, a lock or a pause that goes past the "
                    + "free limit stays saved and starts working again with Pro. A video pass is different: when its time is up, "
                    + "what you set up with it goes back to the free version.", Ui.MUTED), Ui.wide(this, 10));

            if (p.debuggable()) {
                LinearLayout dev = Ui.section(this, body, "For testing", "Test build only");
                dev.addView(Ui.toggle(this, "Pretend I have Pro", p.debugPro(), (b, on) -> p.setDebugPro(on)), Ui.wide(this, 6));
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "pro screen", e);
        }
    }

    /** The monthly plan: its price, what it is, and the button. */
    private void addMonthly(final Pro p) {
        String price = p.monthlyPrice();
        LinearLayout card = Ui.section(this, body, "Monthly plan", price.isEmpty() ? null : price + " a month");
        card.addView(small("Pro for as long as you subscribe. Renews every month; cancel any time in Google Play.", Ui.MUTED), Ui.wide(this, 6));
        card.addView(Ui.button(this, "Subscribe monthly", false, v -> p.buyMonthly(this)), Ui.wide(this, 12));
    }

    /** The lifetime purchase: its price, what it is, and the button. {@code subscribed} adds the note about the monthly plan. */
    private void addLifetime(final Pro p, boolean subscribed) {
        String price = p.price();
        LinearLayout card = Ui.section(this, body, "Lifetime plan", price.isEmpty() ? null : price + " once");
        card.addView(small("Pay once and keep Pro for good.", Ui.MUTED), Ui.wide(this, 6));
        if (subscribed) {
            card.addView(small("If you buy lifetime, cancel the monthly plan in Google Play so it does not renew.", Ui.MUTED), Ui.wide(this, 4));
        }
        card.addView(Ui.button(this, "Get lifetime Pro", true, v -> p.buyLifetime(this)), Ui.wide(this, 12));
    }

    private TextView small(String text, int color) {
        TextView t = Ui.label(this, text, 13, color, false);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        return t;
    }
}
