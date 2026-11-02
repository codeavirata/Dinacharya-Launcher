package app.dinacharya.wallpaper;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.SystemClock;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;

/**
 * The question that has to be answered to stop an alarm. Over the lock screen it asks how the slot that starts goes: start now, start
 * after a few minutes, carry on the slot before for a while, make another plan, or take it off for today. The start-of-day alarm asks to
 * start the day or put it off (after a line to read and, if you turned it on, a challenge). At the end of a break it asks you to get back
 * to work. At bedtime it asks whether you are going to sleep.
 */
public class AlarmActivity extends LangActivity {
    private AlarmPlanner.Event ev;
    private Routine base;
    private Overrides ov;
    private boolean h24;

    private TextView bigClock;
    private TextView titleView;
    private TextView subView;
    private LinearLayout body;

    /** Set on the intent that opens the bedtime question from the Home screen's "Early sleep", not from an alarm. */
    static final String X_EARLY_SLEEP = "early_sleep";

    /** The bedtime question for sleep that begins before its planned time (opened by "Early sleep", or by skipping the slot before sleep). */
    static AlarmPlanner.Event earlySleepEvent(String day, int sleepSrc, String title, long plannedBedMs, long sleepEndMs) {
        return new AlarmPlanner.Event(System.currentTimeMillis(), AlarmPlanner.BEDTIME, day, sleepSrc, title, Routine.SLEEP,
                plannedBedMs, sleepEndMs, plannedBedMs, false);
    }

    static Intent earlySleepIntent(Context c, String day, int sleepSrc, String title, long plannedBedMs, long sleepEndMs) {
        Intent i = new Intent(c, AlarmActivity.class);
        AlarmScheduler.put(i, earlySleepEvent(day, sleepSrc, title, plannedBedMs, sleepEndMs));
        i.putExtra(X_EARLY_SLEEP, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return i;
    }

    private boolean earlySleep;
    private List<Plan.WakeOption> options;
    private int chosen;
    private long sleepAt;
    private int snooze = 15;
    private final Random rnd = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildFrame();
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildFrame() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Insets.pad(sv);
        Insets.lightBars(this);
        setContentView(sv);

        bigClock = Ui.label(this, "", 40, Ui.INK, true);
        bigClock.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(bigClock, Ui.wide(this, 0));
        titleView = Ui.label(this, "", 22, Ui.TEAL, true);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(titleView, Ui.wide(this, 10));
        subView = Ui.label(this, "", 15, Ui.MUTED, false);
        subView.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(subView, Ui.wide(this, 4));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, Ui.wide(this, 20));
    }

    // ------------------------------------------------------------------ which question

    private void handle(Intent intent) {
        ev = AlarmScheduler.read(intent);
        if (ev == null) {
            finish();
            return;
        }
        base = Store.load(this);
        ov = Store.overrides(this, base);
        h24 = base.fmt24;
        bigClock.setText(clock(System.currentTimeMillis()));
        stopShake();
        quoteN = 0;
        earlySleep = intent.getBooleanExtra(X_EARLY_SLEEP, false) && ev.kind == AlarmPlanner.BEDTIME;
        if (AlarmPlanner.isTest(ev)) {
            showTest();
        } else if (ev.kind == AlarmPlanner.PLAN_ASK) {
            showPlanAsk();
        } else if (ev.kind == AlarmPlanner.BREAK_END) {
            showBreakEnd();
        } else if (ev.kind == AlarmPlanner.SLOT || ev.kind == AlarmPlanner.START_AGAIN) {
            if (ev.wake) showWakeUp();
            else showSlot();
        } else {
            showBedtime(ev.kind == AlarmPlanner.REASK);
        }
    }

    private String clock(long ms) {
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
    }

    /** The start-of-day alarm keeps ringing until the day is started or put off, so Back does not hide its screen while it rings. */
    @Override
    public void onBackPressed() {
        if (ev != null && ev.wake && AlarmService.isRinging() && !AlarmPlanner.isTest(ev)) return;
        super.onBackPressed();
    }

    private void silence() {
        AlarmService.answered();   // at once, so the home screen does not bring this question back
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && ev != null) nm.cancel(AlarmService.silentId(ev));   // the quiet notification of a silent slot, if that was it
        } catch (RuntimeException ignored) {
            // nothing to clear
        }
        Intent i = new Intent(this, AlarmService.class);
        i.setAction(AlarmService.ACTION_SILENCE);
        try {
            startService(i);
        } catch (RuntimeException ignored) {
            // the service stops itself after five minutes anyway
        }
    }

    private void log(Ev e) {
        Store.history(this).append(e);
    }

    private void done(String message) {
        AlarmScheduler.reschedule(this);
        DayCloser.schedule(this);
        if (message != null) Lang.toast(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private TextView question(String text) {
        TextView t = Ui.label(this, text, 17, Ui.INK, true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private void addButton(String text, boolean primary, View.OnClickListener l) {
        Button b = Ui.button(this, text, primary, l);
        body.addView(b, Ui.wide(this, 10));
    }

    // ------------------------------------------------------------------ the test alarm

    private void showTest() {
        titleView.setText("Test alarm");
        subView.setText("If you can see and hear this, your alarms will work.");
        body.removeAllViews();
        body.addView(question("Alarms are working on this phone."), Ui.wide(this, 0));
        body.addView(Ui.label(this, "A real alarm looks like this and asks if you are continuing with the schedule. "
                + "Nothing in your routine was changed.", 14, Ui.MUTED, false), Ui.wide(this, 10));
        addButton("Stop the test", true, v -> {
            silence();
            finish();
        });
    }

    // ------------------------------------------------------------------ a slot starts

    /** The note you wrote for this slot, if any (from the routine of that day). */
    private String slotNote() {
        try {
            Routine day = base.forDay(java.time.LocalDate.parse(ev.day));
            if (ev.src >= 0 && ev.src < day.blocks.size()) {
                String n = day.blocks.get(ev.src).note;
                return n == null ? "" : n.trim();
            }
        } catch (RuntimeException ignored) {
            // no note
        }
        return "";
    }

    private String slotName() {
        return ev.title.isEmpty() ? "Next slot" : ev.title;
    }

    /** How many minutes can still be added inside this slot (one minute of it always stays). */
    private int fitMinutes() {
        return Math.max(0, SlotPlay.roomMinutes(System.currentTimeMillis(), ev.slotEndMs) - 1);
    }

    private void addNote() {
        addNote(0);
    }

    private void addNote(float top) {
        String note = slotNote();
        if (!note.isEmpty()) {
            TextView n = Ui.label(this, "📝  " + note, 15, Ui.INK, false);
            n.setPadding(dp(14), dp(12), dp(14), dp(12));
            n.setBackground(Ui.box(this, Ui.SOFT, Ui.LINE, 14));
            body.addView(n, Ui.wide(this, top));
        }
    }

    private void showSlot() {
        titleView.setText(slotName());
        String range = clock(ev.slotStartMs) + " to " + clock(ev.slotEndMs);
        subView.setText(ev.kind == AlarmPlanner.START_AGAIN ? "You asked to start later. " + range
                : ev.firstOfDay ? "Your day starts. " + range : range);
        body.removeAllViews();
        panels.clear();
        boolean hasNote = !slotNote().isEmpty();
        addNote();
        body.addView(question("What would you like to do?"), Ui.wide(this, hasNote ? 14 : 0));
        // One question first: start now is a single tap. The details of the other answers open under their button, one at a time.
        addButton(ev.title.isEmpty() ? "Start now" : "Start “" + ev.title + "” now", true, v -> {
            silence();
            SlotActions.startNow(this, ev);
            done(null);
        });
        final int fit = fitMinutes();
        if (fit >= SlotPlay.STEP) {
            Expander.Part later = choice("Start later");
            LinearLayout box = panel(later);
            box.addView(Ui.label(this, ev.title.isEmpty() ? "Start after" : "Start “" + ev.title + "” after", 14, Ui.MUTED, true));
            addStepper(box, SlotPlay.DEFAULT_START_DELAY, fit, false, "Start later", m -> {
                silence();
                long until = SlotActions.startAfter(this, ev, m);
                done("I will ask again at " + clock(until));
            });
            final SlotActions.Prev prev = ev.kind == AlarmPlanner.SLOT ? SlotActions.previous(this, ev) : null;
            if (prev != null) {
                View rule = Ui.rule(this);
                box.addView(rule, Ui.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, dp(1), 14));
                box.addView(Ui.label(this, prev.title.isEmpty() ? "Continue the slot before until" : "Continue “" + prev.title + "” until",
                        14, Ui.MUTED, true), Ui.wide(this, 12));
                addStepper(box, SlotPlay.DEFAULT_CONTINUE, fit, true, "Keep going", m -> {
                    silence();
                    long until = SlotActions.continuePrevious(this, ev, prev, m);
                    done("I will ask again at " + clock(until));
                });
            }
        }
        addSkipButton();
        Expander.Part other = choice("Other options");
        LinearLayout ob = panel(other);
        ob.addView(Ui.button(this, "Other plan", false, v -> {
            silence();
            showChange();
        }), Ui.wide(this, 0));
        ob.addView(Ui.button(this, "Take it off for today", false, v -> takeOff()), Ui.wide(this, 8));
    }

    /** The panels that open under the choices of this screen; opening one closes the others. */
    private final java.util.List<Expander.Part> panels = new java.util.ArrayList<>();

    private Expander.Part choice(String label) {
        final Expander.Part p = Expander.choice(this, body, label);
        panels.add(p);
        p.onToggled(open -> {
            if (!open) return;
            for (Expander.Part other : panels) if (other != p) other.setOpen(false, false);
        });
        return p;
    }

    /** The soft box inside an opened choice. */
    private LinearLayout panel(Expander.Part p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(14));
        box.setBackground(Ui.box(this, Ui.SOFT, Ui.LINE, 14));
        p.body.addView(box, Ui.wide(this, 8));
        return box;
    }

    private void takeOff() {
        silence();
        applyEdit(true, "");
        log(Ev.ack(System.currentTimeMillis(), ev.day, ev.src, ev.title, "S", ""));
        done("Taken off for today");
    }

    /**
     * "Skip this slot": the slot that is starting is left out for today and the next one begins now. The first slot of the day asks for what
     * turns the wake-up alarm off first (a skip is not a way round it).
     */
    private void addSkipButton() {
        final SlotActions.Skipped skip = ev.kind == AlarmPlanner.SLOT || ev.kind == AlarmPlanner.START_AGAIN ? SlotActions.whatSkipping(this, ev) : null;
        if (skip == null) return;
        String label = skip.nextIsSleep ? "Skip this slot and go to sleep"
                : skip.nextTitle.isEmpty() ? "Skip this slot, start the next one now" : "Skip this slot, start “" + skip.nextTitle + "” now";
        addButton(label, false, v -> {
            if (ev.wake && base.wakeOff != Routine.OFF_NONE) {
                stopShake();
                showOffChallenge(() -> skipHere(skip));
            } else {
                skipHere(skip);
            }
        });
    }

    private void skipHere(SlotActions.Skipped skip) {
        stopShake();
        silence();
        SlotActions.Skipped result = SlotActions.skipSlot(this, ev);
        if (result == null) {
            done(null);
            return;
        }
        if (result.nextIsSleep) {
            // sleep begins when the person answers the bedtime question, right here
            ev = earlySleepEvent(ev.day, result.nextSrc, result.nextTitle, result.nextPlannedMs, result.nextEndMs);
            earlySleep = true;
            base = Store.load(this);
            ov = Store.overrides(this, base);   // with the slot that was just taken off
            showBedtime(false);
            return;
        }
        done(result.nextTitle.isEmpty() ? "The next slot has started" : "“" + result.nextTitle + "” has started");
    }

    private interface Minutes {
        void go(int minutes);
    }

    /**
     * Adds to {@code box} a − and + that move the minutes by five, a line saying what that means, and a button. With {@code clockMode} the
     * number shows the time that many minutes from now, otherwise the minutes themselves.
     */
    private void addStepper(LinearLayout box, int initial, int maxFit, final boolean clockMode, String go, final Minutes action) {
        final TextView caption = Ui.label(this, "", 13, Ui.MUTED, false);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        final MinuteStepper[] st = new MinuteStepper[1];
        final Runnable draw = () -> {
            long at = System.currentTimeMillis() + st[0].minutes() * 60000L;
            caption.setText(clockMode ? st[0].minutes() + " min from now." : "Starts at " + clock(at) + ".");
        };
        st[0] = new MinuteStepper(this, initial, maxFit, m -> draw.run());
        if (clockMode) st[0].words(m -> clock(System.currentTimeMillis() + m * 60000L));
        box.addView(st[0].view, Ui.wide(this, 6));
        box.addView(caption, Ui.wide(this, 2));
        draw.run();
        box.addView(Ui.button(this, go, false, v -> action.go(st[0].minutes())), Ui.wide(this, 8));
    }

    // ------------------------------------------------------------------ the start of the day

    private void startDay() {
        stopShake();
        silence();
        SlotActions.startNow(this, ev);
        done("Your day has started.");
    }

    /** The quote on the screen: 0 is the quote of the day, 1 the next, and so on. */
    private int quoteN;

    /** Starting the day turns the alarm off, after what the person chose for it: nothing, sums, a pause, shaking or a sentence. */
    private void tryStartDay() {
        if (base.wakeOff == Routine.OFF_NONE) startDay();
        else showOffChallenge(this::startDay);
    }

    /** What the person has to do to turn the alarm off, in a line. */
    private String offHint() {
        switch (base.wakeOff) {
            case Routine.OFF_SUM:
                return "To turn this alarm off, solve three quick sums.";
            case Routine.OFF_PAUSE:
                return "To turn this alarm off, take a short pause and breathe.";
            case Routine.OFF_SHAKE:
                return "To turn this alarm off, shake your phone.";
            case Routine.OFF_TYPE:
                return "To turn this alarm off, type a short sentence.";
            default:
                return "";
        }
    }

    /** A motivating quote, with who said it and a way to see another. */
    private void addQuoteCard() {
        final LocalDate day = LocalDate.parse(ev.day);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(10));
        card.setBackground(Ui.box(this, Ui.AMBER_SOFT, Ui.LINE, 16));
        final TextView q = Ui.label(this, "", 18, Ui.INK, true);
        q.setGravity(Gravity.CENTER);
        q.setLineSpacing(0, 1.15f);
        final TextView who = Ui.label(this, "", 13, Ui.MUTED, false);
        who.setGravity(Gravity.CENTER);
        final TextView more = Ui.label(this, "Another quote", 13, Ui.TEAL_DARK, true);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(12), dp(10), dp(12), dp(6));
        final Runnable draw = () -> {
            WakeQuotes.Quote qt = WakeQuotes.quote(base.quoteLine(), day, quoteN);
            q.setText("“" + Tr.t(qt.text) + "”");
            who.setText(qt.author.isEmpty() ? "" : "— " + qt.author);
            who.setVisibility(qt.author.isEmpty() ? View.GONE : View.VISIBLE);
        };
        more.setOnClickListener(v -> {
            quoteN++;
            draw.run();
        });
        draw.run();
        card.addView(q);
        card.addView(who, Ui.wide(this, 6));
        // the person's own line is the only one there is: nothing else to show
        if (base.quoteLine().isEmpty()) card.addView(more, Ui.wide(this, 2));
        body.addView(card, Ui.wide(this, 0));
    }

    private void showWakeUp() {
        stopShake();
        titleView.setText("Time to start your day");
        subView.setText(slotName() + "  ·  " + clock(ev.slotStartMs) + " to " + clock(ev.slotEndMs));
        body.removeAllViews();
        panels.clear();
        if (base.wakeQuoteOn) addQuoteCard();
        boolean hasNote = !slotNote().isEmpty();
        addNote(base.wakeQuoteOn ? 12 : 0);
        body.addView(question("Ready to start your day?"), Ui.wide(this, hasNote || base.wakeQuoteOn ? 14 : 0));
        String hint = offHint();
        if (!hint.isEmpty()) {
            TextView h = Ui.label(this, hint, 13, Ui.MUTED, false);
            h.setGravity(Gravity.CENTER_HORIZONTAL);
            body.addView(h, Ui.wide(this, 4));
        }
        addButton("Start my day", true, v -> tryStartDay());
        int fit = Math.min(120, fitMinutes());
        if (fit >= SlotPlay.STEP) {
            Expander.Part delay = choice("Delay the start");
            LinearLayout box = panel(delay);
            box.addView(Ui.label(this, "Start with a delay of", 14, Ui.MUTED, true));
            addStepper(box, base.wakeDelayMin, fit, false, "Delay my start", this::showQuote);
        }
        addSkipButton();
        if (base.wakeDayOff) {
            addButton("Take the day off", false, v -> DayOffDialog.confirm(this, this::silence, () -> done("Enjoy your day off.")));
        }
    }

    /** The line to read before the start is put off. The alarm keeps ringing. */
    private void showQuote(final int minutes) {
        body.removeAllViews();
        body.addView(question("Before you put it off, read this."), Ui.wide(this, 0));
        LocalDate day = LocalDate.parse(ev.day);
        // another line than the one on the first screen
        WakeQuotes.Quote qt = WakeQuotes.quote(base.quoteLine(), day, base.wakeQuoteOn ? quoteN + 1 : 0);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(18));
        card.setBackground(Ui.box(this, Ui.AMBER_SOFT, Ui.LINE, 14));
        TextView q = Ui.label(this, "“" + Tr.t(qt.text) + "”", 18, Ui.INK, true);
        q.setGravity(Gravity.CENTER);
        q.setLineSpacing(0, 1.15f);
        card.addView(q);
        if (!qt.author.isEmpty()) {
            TextView who = Ui.label(this, "— " + qt.author, 13, Ui.MUTED, false);
            who.setGravity(Gravity.CENTER);
            card.addView(who, Ui.wide(this, 6));
        }
        body.addView(card, Ui.wide(this, 14));
        addButton("I am up. Start my day", true, v -> tryStartDay());
        addButton("Still delay by " + minutes + " min", false, v -> {
            if (base.wakeChallenge == Routine.CHALLENGE_NONE) confirmDelay(minutes);
            else showChallenge(minutes);
        });
        addButton("Back", false, v -> showWakeUp());
    }

    private void confirmDelay(int minutes) {
        silence();
        long until = SlotActions.startAfter(this, ev, minutes);
        done("I will ask again at " + clock(until));
    }

    /** The challenge for putting the day off: a few sums, or a sentence to type. */
    private void showChallenge(final int minutes) {
        body.removeAllViews();
        final Runnable passed = () -> confirmDelay(minutes);
        if (base.wakeChallenge == Routine.CHALLENGE_SUM) {
            addSums(3, passed);
        } else {
            String mine = base.typeLine(true);
            addTyping("Type this to put your day off", mine.isEmpty() ? WakeQuotes.phrase(rnd) : mine, passed);
        }
        addButton("I am up. Start my day", false, v -> tryStartDay());
    }

    // ------------------------------------------------------------------ turning the start-of-day alarm off

    /** The alarm keeps ringing until the challenge is done. */
    private void showOffChallenge(final Runnable passed) {
        body.removeAllViews();
        panels.clear();
        switch (base.wakeOff) {
            case Routine.OFF_PAUSE:
                addPause(passed);
                break;
            case Routine.OFF_SHAKE:
                addShake(passed);
                break;
            case Routine.OFF_TYPE:
                String mine = base.typeLine(false);
                addTyping("Type this to start your day", mine.isEmpty() ? WakeQuotes.startPhrase(rnd) : mine, passed);
                break;
            default:
                addSums(3, passed);
                break;
        }
        addButton("Back", false, v -> showWakeUp());
    }

    /** {@code rounds} sums one after the other; {@code passed} runs when the last one is right. */
    private void addSums(final int rounds, final Runnable passed) {
        final EditText answer = new EditText(this);
        answer.setSingleLine(true);
        answer.setTextColor(Ui.INK);
        answer.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView note = Ui.label(this, " ", 14, Ui.TEAL_DARK, false);
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        final int[] done = {0};
        final PauseChallenge.Sum[] cur = {PauseChallenge.sum(rnd, 0)};
        final TextView step = Ui.label(this, "Sum 1 of " + rounds, 14, Ui.MUTED, false);
        step.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(step, Ui.wide(this, 0));
        final TextView q = Ui.label(this, cur[0].question + " = ?", 32, Ui.INK, true);
        q.setGravity(Gravity.CENTER);
        body.addView(q, Ui.wide(this, 10));
        answer.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        answer.setHint("Your answer");
        body.addView(answer, Ui.wide(this, 14));
        body.addView(note, Ui.wide(this, 8));
        final Runnable check = () -> {
            if (PauseChallenge.answerMatches(answer.getText().toString(), cur[0].answer)) {
                done[0]++;
                if (done[0] >= rounds) {
                    passed.run();
                    return;
                }
                note.setText("Right! One more.");
                cur[0] = PauseChallenge.sum(rnd, done[0]);
                q.setText(cur[0].question + " = ?");
                step.setText("Sum " + (done[0] + 1) + " of " + rounds);
            } else {
                note.setText("Not quite. Try again.");
            }
            answer.setText("");
        };
        answer.setOnEditorActionListener((v, actionId, e) -> {
            check.run();
            return true;
        });
        addButton("Check", true, v -> check.run());
    }

    /** A sentence to type exactly; {@code passed} runs when it matches. */
    private void addTyping(String heading, final String phrase, final Runnable passed) {
        final EditText answer = new EditText(this);
        answer.setSingleLine(true);
        answer.setTextColor(Ui.INK);
        answer.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView note = Ui.label(this, " ", 14, Ui.TEAL_DARK, false);
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(question(heading), Ui.wide(this, 0));
        TextView p = Ui.label(this, "“" + phrase + "”", 18, Ui.INK, true);
        p.setGravity(Gravity.CENTER);
        p.setLineSpacing(0, 1.1f);
        body.addView(p, Ui.wide(this, 10));
        answer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        answer.setHint("Type it here");
        answer.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        body.addView(answer, Ui.wide(this, 14));
        body.addView(note, Ui.wide(this, 8));
        final Runnable check = () -> {
            if (PauseChallenge.phraseMatches(answer.getText().toString(), phrase)) {
                passed.run();
            } else {
                note.setText("Not the same yet. Check each word.");
            }
        };
        answer.setOnEditorActionListener((v, actionId, e) -> {
            check.run();
            return true;
        });
        addButton("Check", true, v -> check.run());
    }

    /** Four slow breaths: the circle grows while you breathe in and shrinks while you breathe out. */
    private void addPause(final Runnable passed) {
        final int breaths = 4;
        body.addView(question("Take a short pause"), Ui.wide(this, 0));
        final TextView phase = Ui.label(this, "Breathe in", 24, Ui.INK, true);
        phase.setGravity(Gravity.CENTER);
        body.addView(phase, Ui.wide(this, 10));
        BreatheView bv = new BreatheView(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(190), dp(190));
        bp.topMargin = dp(10);
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(bv, bp);
        final TextView count = Ui.label(this, "Breath 1 of " + breaths, 14, Ui.MUTED, false);
        count.setGravity(Gravity.CENTER);
        body.addView(count, Ui.wide(this, 8));
        body.addView(Ui.label(this, "The alarm stops when the last breath is done. Leaving this screen starts the breaths again.", 13,
                Ui.MUTED, false), Ui.wide(this, 8));
        bv.start(breaths, new BreatheView.Listener() {
            @Override
            public void phase(boolean in, int breath, int total) {
                // in the language of the app, so that what is on screen is compared with what would be put there
                String p = Tr.t(in ? "Breathe in" : "Breathe out");
                if (!p.contentEquals(phase.getText())) phase.setText(p);
                String c = Tr.t("Breath " + breath + " of " + total);
                if (!c.contentEquals(count.getText())) count.setText(c);
            }

            @Override
            public void done() {
                passed.run();
            }
        });
    }

    private SensorManager sensors;
    private SensorEventListener shakeListener;

    private void stopShake() {
        if (shakeListener != null && sensors != null) {
            try {
                sensors.unregisterListener(shakeListener);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        shakeListener = null;
    }

    @Override
    protected void onDestroy() {
        stopShake();
        super.onDestroy();
    }

    /**
     * Shake the phone twenty times. A phone without a motion sensor gets three sums instead, so the alarm can always be turned off.
     */
    private void addShake(final Runnable passed) {
        stopShake();
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor acc = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc == null) {
            body.addView(Ui.label(this, "This phone cannot tell when it is shaken, so here are three sums instead.", 14, Ui.MUTED, false),
                    Ui.wide(this, 0));
            addSums(3, passed);
            return;
        }
        final ShakeCounter counter = new ShakeCounter();
        body.addView(question("Shake your phone"), Ui.wide(this, 0));
        final TextView count = Ui.label(this, "0 of " + counter.target() + " shakes", 32, Ui.INK, true);
        count.setGravity(Gravity.CENTER);
        body.addView(count, Ui.wide(this, 14));
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(counter.target());
        body.addView(bar, Ui.wide(this, 14));
        body.addView(Ui.label(this, "Hold it firmly and shake it up and down. The alarm stops after the last shake.", 13, Ui.MUTED, false),
                Ui.wide(this, 14));
        shakeListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent e) {
                if (e.values == null || e.values.length < 3) return;
                boolean finished = counter.add(e.values[0], e.values[1], e.values[2], SystemClock.uptimeMillis());
                count.setText(counter.count() + " of " + counter.target() + " shakes");
                bar.setProgress(counter.count());
                if (finished) {
                    stopShake();
                    passed.run();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        if (!sensors.registerListener(shakeListener, acc, SensorManager.SENSOR_DELAY_GAME)) {
            stopShake();
            body.removeAllViews();
            body.addView(Ui.label(this, "This phone cannot tell when it is shaken, so here are three sums instead.", 14, Ui.MUTED, false),
                    Ui.wide(this, 0));
            addSums(3, passed);
        }
    }

    // ------------------------------------------------------------------ plan your day (after a day off)

    private void showPlanAsk() {
        titleView.setText("Plan your day?");
        subView.setText("Your day is off");
        body.removeAllViews();
        panels.clear();
        body.addView(question("Plan your day now, later, or leave it?"), Ui.wide(this, 0));
        body.addView(Ui.label(this, "No alarm rings until you plan your day again.", 14, Ui.MUTED, false), Ui.wide(this, 10));
        addButton("Plan now", true, v -> {
            silence();
            DayOffDialog.openPlanner(this);
            done(null);
        });
        addButton("Later", false, v -> {
            silence();
            DayOffDialog.askLater(this, () -> done(null));
        });
        addButton("Leave it", false, v -> {
            silence();
            SlotActions.planLater(this, 0L);
            done("Enjoy your day off. Your alarms are back tomorrow.");
        });
    }

    // ------------------------------------------------------------------ a break is over

    private void showBreakEnd() {
        titleView.setText("Your break is over");
        subView.setText("Your break started at " + clock(ev.slotStartMs));
        body.removeAllViews();
        panels.clear();
        body.addView(question(ev.title.isEmpty() ? "Ready to get back to work?" : "Ready to get back to " + ev.title + "?"), Ui.wide(this, 0));
        addButton("Back to work", true, v -> {
            silence();
            AlarmScheduler.reschedule(this);
            done(null);
        });
        final long now = System.currentTimeMillis();
        if (ev.slotEndMs - now >= 2 * 60000L) {
            Expander.Part more = choice("More time");
            LinearLayout box = panel(more);
            box.addView(Ui.label(this, "Rest for", 14, Ui.MUTED, true));
            final int room = SlotPlay.roomMinutes(now, ev.slotEndMs);
            final TextView caption = Ui.label(this, "", 13, Ui.MUTED, false);
            caption.setGravity(Gravity.CENTER_HORIZONTAL);
            final MinuteStepper[] st = new MinuteStepper[1];
            final Runnable draw = () -> caption.setText("Back at " + clock(SlotPlay.breakEnd(System.currentTimeMillis(), st[0].minutes(), ev.slotEndMs)) + ".");
            st[0] = new MinuteStepper(this, 5, Math.max(room, SlotPlay.STEP), m -> draw.run());
            box.addView(st[0].view, Ui.wide(this, 6));
            box.addView(caption, Ui.wide(this, 2));
            draw.run();
            box.addView(Ui.button(this, "Add this time", false, v -> {
                silence();
                long until = SlotActions.extendBreak(this, st[0].minutes());
                done(until > 0 ? "Break extended until " + clock(until) : null);
            }), Ui.wide(this, 8));
        }
    }

    private void showChange() {
        body.removeAllViews();
        body.addView(question("Change the plan for this slot"), Ui.wide(this, 0));
        body.addView(Ui.label(this, "Pick one. This changes today only. Your saved routine stays as it is.", 13, Ui.MUTED, false),
                Ui.wide(this, 4));
        final int slotMins = Math.max(SlotPlay.STEP, (int) ((ev.slotEndMs - ev.slotStartMs) / 60000L));
        final int part = Math.max(SlotPlay.STEP, slotMins - SlotPlay.STEP);
        final LocalDate day = LocalDate.parse(ev.day);
        final SlotActions.Prev prev = ev.kind == AlarmPlanner.SLOT ? SlotActions.previous(this, ev) : null;

        // the other slots of this day, to swap with
        final java.util.List<Routine.Block> others = new java.util.ArrayList<>();
        final java.util.List<String> otherNames = new java.util.ArrayList<>();
        try {
            Plan.DayPlan dp = Plan.plan(base, ov, day, Store.zone());
            for (Routine.Slot sl : dp.sc.slots) {
                if (sl.block.src < 0 || sl.block.src == ev.src || sl.block.skipped || sl.block.kind == Routine.SLEEP) continue;
                if (others.stream().anyMatch(x -> x.src == sl.block.src)) continue;
                others.add(sl.block);
                otherNames.add(sl.block.title + " (" + sl.block.mins + " min)");
            }
        } catch (RuntimeException ignored) {
        }

        int curKind = Routine.TASK;
        String curSub = Routine.OTHER;
        try {
            Routine.Block cb = base.forDay(day).blocks.get(ev.src);
            curKind = cb.kind;
            curSub = cb.sub;
        } catch (RuntimeException ignored) {
        }
        final int baseKind = curKind;
        final String baseSub = curSub;
        final ChangePlanUi.TypePicker typePick = new ChangePlanUi.TypePicker(this, base, curKind, curSub);
        final LinearLayout[] panelBox = new LinearLayout[3];
        final RadioButton[] rb = new RadioButton[3];
        final String[] labels = {"Replace slot", "Continue previous slot", "Swap slot with"};
        final MinuteStepper[] steps = new MinuteStepper[3];
        final Switch[] whole = new Switch[3];
        final EditText name = new EditText(this);
        final java.util.List<String> otherNotes = new java.util.ArrayList<>();
        for (Routine.Block ob : others) otherNotes.add(ob.mins + " min");
        final ChangePlanUi.Cards target = new ChangePlanUi.Cards(this, titlesOf(others), otherNotes);
        final RadioButton[] swapMode = new RadioButton[2];

        for (int i = 0; i < 3; i++) {
            final int k = i;
            if (i == 1 && prev == null) continue;
            if (i == 2 && others.isEmpty()) continue;
            rb[i] = new RadioButton(this);
            rb[i].setId(View.generateViewId());
            rb[i].setText(labels[i]);
            rb[i].setTextColor(Ui.INK);
            rb[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(10), dp(14), dp(12));
            box.setBackground(Ui.box(this, Ui.SOFT, Ui.LINE, 14));
            panelBox[i] = box;
            if (i == 0) {
                name.setSingleLine(true);
                name.setHint("What will you do instead?");
                name.setText(ev.title);
                name.setSelectAllOnFocus(true);
                name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
                name.setTextColor(Ui.INK);
                name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                box.addView(name, Ui.wide(this, 0));
                box.addView(typePick.view, Ui.wide(this, 0));
            } else if (i == 1) {
                box.addView(Ui.label(this, "“" + prev.title + "” goes on into this slot.", 13, Ui.MUTED, false), Ui.wide(this, 0));
            } else {
                box.addView(Ui.label(this, "Swap with which slot?", 13, Ui.MUTED, true), Ui.wide(this, 0));
                box.addView(target.view, Ui.wide(this, 6));
                swapMode[0] = new RadioButton(this);
                swapMode[0].setText("Swap names (times stay)");
                swapMode[0].setTextColor(Ui.INK);
                swapMode[1] = new RadioButton(this);
                swapMode[1].setText("Swap places and times");
                swapMode[1].setTextColor(Ui.INK);
                RadioGroup mg = new RadioGroup(this);
                swapMode[0].setId(View.generateViewId());
                swapMode[1].setId(View.generateViewId());
                mg.addView(swapMode[0]);
                mg.addView(swapMode[1]);
                swapMode[0].setChecked(true);
                box.addView(mg, Ui.wide(this, 6));
            }
            whole[i] = new Switch(this);
            whole[i].setText("Whole slot (" + slotMins + " min)");
            whole[i].setTextColor(Ui.INK);
            whole[i].setChecked(true);
            box.addView(whole[i], Ui.wide(this, 10));
            steps[i] = new MinuteStepper(this, Math.min(30, part), part, m -> { });
            steps[i].view.setVisibility(View.GONE);
            box.addView(steps[i].view, Ui.wide(this, 6));
            whole[i].setOnCheckedChangeListener((c, on) -> steps[k].view.setVisibility(on ? View.GONE : View.VISIBLE));
            box.setVisibility(View.GONE);
            body.addView(rb[i], Ui.wide(this, 10));
            body.addView(box, Ui.wide(this, 4));
        }
        if (swapMode[1] != null) {
            // swapping places moves the whole slot
            swapMode[1].setOnCheckedChangeListener((c, on) -> {
                if (on) whole[2].setChecked(true);
                whole[2].setEnabled(!on);
            });
        }
        // one choice at a time: showing its details, hiding the others
        for (int i = 0; i < 3; i++) {
            final int k = i;
            if (rb[i] == null) continue;
            rb[i].setOnCheckedChangeListener((c, on) -> {
                if (!on) return;
                for (int j = 0; j < 3; j++) {
                    if (rb[j] == null) continue;
                    if (j != k) rb[j].setChecked(false);
                    panelBox[j].setVisibility(j == k ? View.VISIBLE : View.GONE);
                }
            });
        }
        rb[0].setChecked(true);

        body.addView(Ui.label(this, "The rest of the slot goes back to your saved plan.", 12, Ui.MUTED, false), Ui.wide(this, 8));
        addButton("Save for today", true, v -> {
            int pick = rb[1] != null && rb[1].isChecked() ? 1 : rb[2] != null && rb[2].isChecked() ? 2 : 0;
            boolean full = whole[pick].isChecked();
            int mins = steps[pick].minutes();
            long now = System.currentTimeMillis();
            if (pick == 0) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) {
                    Lang.toast(this, "Type a name, or choose Take it off", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (n.equals(ev.title) && typePick.kind() == baseKind && typePick.sub().equalsIgnoreCase(baseSub)) {
                    log(Ev.ack(now, ev.day, ev.src, ev.title, "C", ""));
                    done(null);
                    return;
                }
                Plan.editPart(base, ov, day, ev.src, n, full ? 0 : mins, typePick.kind(), typePick.sub());
                Store.saveOverrides(this, base, ov);
                log(Ev.ack(now, ev.day, ev.src, ev.title, "R", n));
                done(full ? "Changed for today: " + n : n + " for " + mins + " min, then back to the plan");
            } else if (pick == 1) {
                silence();
                long until = SlotActions.continuePrevious(this, ev, prev, full ? slotMins : mins);
                done("“" + prev.title + "” goes on. I will ask again at " + clock(until));
            } else {
                int t = target.selected();
                if (t < 0 || t >= others.size()) return;
                Routine.Block o = others.get(t);
                if (swapMode[1].isChecked()) {
                    Plan.swapPlaces(base, ov, day, ev.src, o.src);
                } else if (full) {
                    Plan.edit(base, ov, day, ev.src, false, o.title);
                    Plan.edit(base, ov, day, o.src, false, ev.title);
                } else {
                    Plan.editPart(base, ov, day, ev.src, o.title, mins);
                    Plan.editPart(base, ov, day, o.src, ev.title, Math.min(mins, Math.max(SlotPlay.STEP, o.mins - SlotPlay.STEP)));
                }
                Store.saveOverrides(this, base, ov);
                log(Ev.ack(now, ev.day, ev.src, ev.title, "R", o.title));
                done("Swapped with " + o.title);
            }
        });
        addButton("Take it off for today", false, v -> takeOff());
        addButton("Back", false, v -> showSlot());
    }

    private static java.util.List<String> titlesOf(java.util.List<Routine.Block> bl) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Routine.Block b : bl) out.add(b.title.isEmpty() ? Routine.KIND_NAMES[b.kind] : b.title);
        return out;
    }

    private void applyEdit(boolean skip, String newName) {
        Plan.edit(base, ov, LocalDate.parse(ev.day), ev.src, skip, newName);
        Store.saveOverrides(this, base, ov);
    }

    // ------------------------------------------------------------------ bedtime

    private void showBedtime(boolean again) {
        titleView.setText(earlySleep ? "Early sleep" : again ? "Asking again" : "Time to sleep");
        subView.setText("Planned bedtime " + clock(ev.plannedBedMs > 0 ? ev.plannedBedMs : ev.slotStartMs));
        body.removeAllViews();
        body.addView(question(earlySleep ? "Going to sleep early?" : "Are you going to sleep, or will it take more time?"), Ui.wide(this, 0));
        addButton("I am going to sleep", true, v -> {
            silence();
            sleepAt = System.currentTimeMillis();
            long planned = ev.plannedBedMs > 0 ? ev.plannedBedMs : ev.slotStartMs;
            options = Plan.wakeOptions(base, ov, LocalDate.parse(ev.day), sleepAt, Store.zone(), h24);
            chosen = 0;
            showWake(planned);
        });
        if (earlySleep) {
            addButton("Not yet", false, v -> finish());
            return;
        }
        addButton("It will take more time", false, v -> {
            silence();
            snooze = Suggestions.favouriteSnooze(Store.history(this).events());
            showSnooze();
        });
    }

    private void showSnooze() {
        body.removeAllViews();
        body.addView(question("Remind me again after how long?"), Ui.wide(this, 0));
        final int[] presets = {10, 15, 30, 60};
        final LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(chips, Ui.wide(this, 14));
        final EditText custom = new EditText(this);
        custom.setInputType(InputType.TYPE_CLASS_NUMBER);
        custom.setSingleLine(true);
        custom.setHint("or type minutes");
        custom.setTextColor(Ui.INK);
        custom.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(custom, Ui.wide(this, 10));

        // What it costs: sleep lost before the alarm, which stays where it is.
        int sleepNow = 0;
        int planned = 0;
        int before = 0;
        try {
            Plan.WakeOption first = Plan.wakeOptions(base, ov, LocalDate.parse(ev.day), System.currentTimeMillis(), Store.zone(), h24).get(0);
            sleepNow = first.sleepMin;
            planned = first.plannedMin;
            before = SnoozeCost.snoozedBefore(Store.history(this).events(), ev.day);
        } catch (RuntimeException ignored) {
            // the cost is only a help: the choice works without it
        }
        final int fSleepNow = sleepNow;
        final int fPlanned = planned;
        final int fBefore = before;
        final TextView cost = Ui.label(this, "", 14, Ui.INK, false);
        cost.setPadding(dp(14), dp(12), dp(14), dp(12));
        cost.setBackground(Ui.box(this, Ui.AMBER_SOFT, Ui.LINE, 14));
        cost.setLineSpacing(0, 1.12f);
        body.addView(cost, Ui.wide(this, 14));
        final Runnable showCost = () -> {
            int minutes = snooze;
            String typed = custom.getText().toString().trim();
            if (!typed.isEmpty()) {
                try {
                    minutes = Math.min(360, Integer.parseInt(typed));
                } catch (NumberFormatException e) {
                    minutes = 0;
                }
            }
            List<String> lines = fSleepNow > 0 ? SnoozeCost.lines(minutes, fSleepNow, fPlanned, fBefore) : new java.util.ArrayList<String>();
            if (lines.isEmpty()) {
                cost.setVisibility(View.GONE);
                return;
            }
            cost.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(l);
            }
            cost.setText(sb.toString());
        };
        custom.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                showCost.run();
            }
        });
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            showCost.run();
            chips.removeAllViews();
            for (final int m : presets) {
                Button b = Ui.button(this, m + " min", m == snooze, v -> {
                    snooze = m;
                    custom.setText("");
                    draw[0].run();
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                p.rightMargin = dp(6);
                chips.addView(b, p);
            }
        };
        boolean isPreset = false;
        for (int m : presets) if (m == snooze) isPreset = true;
        if (!isPreset) custom.setText(String.valueOf(snooze));
        draw[0].run();
        addButton("Remind me", true, v -> {
            int minutes = snooze;
            String typed = custom.getText().toString().trim();
            if (!typed.isEmpty()) {
                try {
                    minutes = Integer.parseInt(typed);
                } catch (NumberFormatException e) {
                    minutes = -1;
                }
            }
            if (minutes < 1 || minutes > 360) {
                Lang.toast(this, "Pick between 1 and 360 minutes", Toast.LENGTH_SHORT).show();
                return;
            }
            long now = System.currentTimeMillis();
            AlarmState st = Store.alarmState(this);
            st.reaskAt = now + minutes * 60000L;
            st.reaskBedMs = ev.plannedBedMs > 0 ? ev.plannedBedMs : ev.slotStartMs;
            st.reaskDay = ev.day;
            st.reaskSrc = ev.src;
            st.reaskTitle = ev.title;
            Store.saveAlarmState(this, st);
            log(Ev.snooze(now, ev.day, minutes));
            done("I will ask again at " + clock(st.reaskAt));
        });
        addButton("Back", false, v -> showBedtime(ev.kind == AlarmPlanner.REASK));
    }

    private void showWake(final long plannedBed) {
        body.removeAllViews();
        long late = Math.max(0, (sleepAt - plannedBed) / 60000L);
        long soon = Math.max(0, (plannedBed - sleepAt) / 60000L);
        subView.setText("Going to sleep at " + clock(sleepAt)
                + (late >= 5 ? ", " + Routine.formatDuration((int) late) + " after your plan" : "")
                + (soon >= 5 ? ", " + Routine.formatDuration((int) soon) + " before your plan" : ""));
        if (options.size() == 1) {
            Plan.WakeOption o = options.get(0);
            body.addView(question("Good night. I will wake you at " + clock(o.wakeMs) + "."), Ui.wide(this, 0));
            addButton("Set the alarm", true, v -> confirmWake(plannedBed));
            addButton("Back", false, v -> showBedtime(ev.kind == AlarmPlanner.REASK));
            return;
        }
        body.addView(question("When do you want to wake up?"), Ui.wide(this, 0));
        String[] heads = {"Normal schedule", "Shift my day later", "Skip the slots I sleep through"};
        for (int i = 0; i < options.size(); i++) {
            final int idx = i;
            final Plan.WakeOption o = options.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            boolean sel = i == chosen && o.fits;
            card.setBackground(Ui.box(this, sel ? 0xFFDDF0EE : Ui.SURFACE, sel ? Ui.TEAL : Ui.LINE, 14));
            card.setAlpha(o.fits ? 1f : 0.55f);
            TextView head = Ui.label(this, heads[Math.min(i, 2)] + "  ·  " + clock(o.wakeMs), 17, Ui.INK, true);
            card.addView(head);
            for (String note : o.notes) {
                card.addView(Ui.label(this, note, 13, Ui.MUTED, false), Ui.wide(this, 3));
            }
            if (o.fits) {
                card.setOnClickListener(v -> {
                    chosen = idx;
                    showWake(plannedBed);
                });
            }
            body.addView(card, Ui.wide(this, 12));
        }
        body.addView(Ui.label(this, "These changes are for tomorrow only. To change your routine for good, edit it in the app.",
                13, Ui.MUTED, false), Ui.wide(this, 14));
        addButton("Set the alarm", true, v -> confirmWake(plannedBed));
        addButton("Back", false, v -> showBedtime(ev.kind == AlarmPlanner.REASK));
    }

    private void confirmWake(long plannedBed) {
        Plan.WakeOption o = options.get(Math.max(0, Math.min(chosen, options.size() - 1)));
        if (!o.fits) o = options.get(0);
        Plan.commit(base, ov, LocalDate.parse(ev.day), o);
        Store.saveOverrides(this, base, ov);
        AlarmState st = Store.alarmState(this);
        st.reaskAt = 0;
        Store.saveAlarmState(this, st);
        log(Ev.sleep(sleepAt, ev.day, plannedBed, o.type, o.shiftMin));
        if (earlySleep) SlotActions.sleepEarly(this, ev.day, ev.src, sleepAt);   // sleep begins now, and the bedtime question stays quiet
        done("Good night. Alarm set for " + clock(o.wakeMs));
    }
}
