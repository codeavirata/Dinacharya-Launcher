package app.dinacharya.wallpaper;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The parts of the setup screen for alarms, today's changes, habits and backups. */
final class ExtraSections {
    static final int REQ_NOTIFICATIONS = 71;

    private final MainActivity a;
    private boolean asked;

    private LinearLayout changesBox;
    private TextView nextAlarm;
    private LinearLayout healthBox;
    private TextView diagText;
    private LinearLayout habitsBox;
    private Expander.Part readyPart;
    private boolean binding;
    private Switch alarmBox;
    private Switch askSilentBox;
    private TextView delayText;
    private Spinner challengeSpin;
    private EditText quoteEdit;
    private TextView quotePreview;
    private Spinner offSpin;
    private Spinner lineSpin;
    private Switch quoteOnBox;
    private Switch dayOffBox;

    ExtraSections(MainActivity host) {
        this.a = host;
    }

    private Routine routine() {
        return a.routine();
    }

    // ------------------------------------------------------------------ top: performance + today's changes

    /** The "changed for today only" card (Home). */
    void buildChanges(LinearLayout root) {
        changesBox = new LinearLayout(a);
        changesBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(changesBox, Ui.wide(a, 0));
    }

    private void refreshChanges() {
        if (changesBox == null) return;
        changesBox.removeAllViews();
        Routine base = routine();
        Overrides ov = Store.overrides(a, base);
        if (ov.isEmpty()) return;
        ZoneId zone = Store.zone();
        long now = System.currentTimeMillis();
        final Plan.DayPlan p = Plan.resolve(base, ov, now, zone);
        final String dayKey = p.day.toString();
        final List<String> lines = new ArrayList<>();
        final List<String> shiftKeys = new ArrayList<>();
        for (Map.Entry<String, Overrides.Shift> e : ov.shifts.entrySet()) {
            if (e.getKey().compareTo(dayKey) < 0) continue;
            LocalDate d = LocalDate.parse(e.getKey());
            int baseStart = base.forDay(d).compute().start;
            Plan.DayPlan dp = Plan.plan(base, ov, d, zone);
            String which = d.equals(p.day) ? "Today" : (d.equals(p.day.plusDays(1)) ? "Tomorrow" : d.toString());
            lines.add(which + " starts at " + Routine.formatTime(dp.sc.start, base.fmt24) + " instead of "
                    + Routine.formatTime(baseStart, base.fmt24)
                    + (e.getValue().type == Overrides.SKIP ? " (slots covered by the extra sleep are dropped)"
                    : " (the morning moves later, bedtime stays)"));
            shiftKeys.add(e.getKey());
        }
        final List<String> wakeKeys = new ArrayList<>();
        for (Map.Entry<String, Integer> e : ov.wakes.entrySet()) {
            if (e.getKey().compareTo(dayKey) < 0) continue;
            LocalDate d = LocalDate.parse(e.getKey());
            int baseStart = base.forDay(d).compute().start;
            Plan.DayPlan dp = Plan.plan(base, ov, d, zone);
            String which = d.equals(p.day) ? "Today" : (d.equals(p.day.plusDays(1)) ? "Tomorrow" : d.toString());
            lines.add(which + " starts at " + Routine.formatTime(dp.sc.start, base.fmt24) + " instead of "
                    + Routine.formatTime(baseStart, base.fmt24) + " (you woke early)");
            wakeKeys.add(e.getKey());
        }
        for (Overrides.Edit e : ov.edits) {
            if (!e.day.equals(dayKey)) continue;
            lines.add(e.skip ? e.orig + " is taken off" : e.orig + " is called “" + e.name + "”");
        }
        if (lines.isEmpty()) return;

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        card.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.AMBER, 18));
        card.addView(Ui.eyebrow(a, "Changed for today only"));
        for (String l : lines) {
            TextView t = Ui.label(a, "• " + l, 14, Ui.INK, false);
            card.addView(t, Ui.wide(a, 4));
        }
        card.addView(Ui.label(a, "Your saved routine is untouched. These end by themselves.", 12, Ui.MUTED, false), Ui.wide(a, 6));
        card.addView(Ui.button(a, "Undo today’s changes", false, v -> {
            Routine b = routine();
            Overrides o = Store.overrides(a, b);
            o.clearEdits(p.day);
            for (String k : shiftKeys) o.setShift(LocalDate.parse(k), 0, 0);
            for (String k : wakeKeys) o.setEarly(LocalDate.parse(k), 0);
            Store.saveOverrides(a, b, o);
            AlarmScheduler.reschedule(a);
            DayCloser.schedule(a);
            refresh();
            a.routineChanged();
        }), Ui.wide(a, 8));
        changesBox.addView(card, Ui.wide(a, 14));
    }

    // ------------------------------------------------------------------ alarms

    void buildAlarms(LinearLayout page) {
        LinearLayout root = Ui.section(a, page, "Alarms", "A nudge at every slot");
        final Accordion alarmParts = new Accordion();   // only one of the three dropdowns below is open at a time

        alarmBox = Ui.toggle(a, "Remind me with an alarm", routine().alarmOn, (b, on) -> {
            if (binding) return;
            setAlarms(on);
        });
        root.addView(alarmBox, Ui.wide(a, 6));
        root.addView(Ui.label(a, "An alarm rings when a slot starts. To stop it you say if you continue as planned or want a change "
                + "(a new name for today, or take the slot off). At bedtime it asks if you are going to sleep and when you want to wake. "
                + "You can switch single slots off with the bell on the Routine tab.", 13, Ui.MUTED, false), Ui.wide(a, 2));

        // Slots that are set to a silent notification.
        Expander.Part silent = alarmParts.add(Expander.more(a, root, "Slots without a sound", "alarm_silent", false));
        askSilentBox = Ui.toggle(a, "Ask me about slots set to a silent notification", routine().askSilent, (b, on) -> {
            if (binding) return;
            routine().askSilent = on;
            a.persistRoutine();
            AlarmScheduler.reschedule(a);
            refreshAlarms();
        });
        silent.body.addView(askSilentBox, Ui.wide(a, 4));
        silent.body.addView(Ui.label(a, "A silent notification puts the question on screen with Start now and In 10 min buttons, with no sound and no "
                + "vibration. Switch this off to see nothing at all for those slots. Each slot has its own choice: ring, vibration, silent or none, "
                + "under its bell on the Routine tab.", 13, Ui.MUTED, false), Ui.wide(a, 2));

        // The start-of-day alarm.
        Expander.Part wake = alarmParts.add(Expander.more(a, root, "Start-of-day alarm", "alarm_wake", false));
        wake.body.addView(Ui.label(a, "Choose one slot as your start-of-day alarm from its bell on the Routine tab. It has to be the first alarm of "
                + "the day. It keeps ringing until you start the day, put it off or take the day off. Before you can turn it off you can be asked to "
                + "solve a sum, take a pause, shake the phone or type a sentence, and you see a motivating quote.",
                13, Ui.MUTED, false), Ui.wide(a, 4));
        LinearLayout delayRow = new LinearLayout(a);
        delayRow.setOrientation(LinearLayout.HORIZONTAL);
        delayRow.setGravity(Gravity.CENTER_VERTICAL);
        delayRow.addView(Ui.label(a, "Put the start off by", 15, Ui.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        delayRow.addView(Ui.iconButton(a, "−", Ui.TEAL_DARK, v -> stepWakeDelay(-SlotPlay.STEP)));
        delayText = Ui.label(a, "", 16, Ui.TEAL_DARK, true);
        delayText.setGravity(Gravity.CENTER);
        delayRow.addView(delayText, new LinearLayout.LayoutParams(a.dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        delayRow.addView(Ui.iconButton(a, "+", Ui.TEAL_DARK, v -> stepWakeDelay(SlotPlay.STEP)));
        wake.body.addView(delayRow, Ui.wide(a, 10));

        wake.body.addView(Ui.label(a, "To turn the alarm off and start the day", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        offSpin = new Spinner(a);
        ArrayAdapter<String> offAd = Lang.adapter(a, android.R.layout.simple_spinner_item,
                new String[]{"Just tap Start my day", "Solve three quick sums", "Take a short pause (four slow breaths)", "Shake the phone",
                        "Type a sentence"});
        offAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        offSpin.setAdapter(offAd);
        offSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding || position == routine().wakeOff) return;
                routine().wakeOff = position;
                a.persistRoutine();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        wake.body.addView(offSpin, Ui.wide(a, 0));
        wake.body.addView(Ui.label(a, "The alarm keeps ringing until it is done. A phone that cannot sense a shake asks for the sums instead.",
                12, Ui.MUTED, false), Ui.wide(a, 2));

        quoteOnBox = Ui.toggle(a, "Show a motivating quote", routine().wakeQuoteOn, (b, on) -> {
            if (binding) return;
            routine().wakeQuoteOn = on;
            a.persistRoutine();
        });
        wake.body.addView(quoteOnBox, Ui.wide(a, 10));
        dayOffBox = Ui.toggle(a, "Offer \u201cTake the day off\u201d", routine().wakeDayOff, (b, on) -> {
            if (binding) return;
            routine().wakeDayOff = on;
            a.persistRoutine();
        });
        wake.body.addView(dayOffBox, Ui.wide(a, 0));
        wake.body.addView(Ui.label(a, "Taking the day off silences every alarm for the rest of today. You are asked if you want to plan the day "
                + "now, later or leave it.", 12, Ui.MUTED, false), Ui.wide(a, 2));

        wake.body.addView(Ui.label(a, "Challenge before putting it off", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        challengeSpin = new Spinner(a);
        ArrayAdapter<String> ad = Lang.adapter(a, android.R.layout.simple_spinner_item,
                new String[]{"No challenge", "Solve three quick sums", "Type a sentence"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        challengeSpin.setAdapter(ad);
        challengeSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding || position == routine().wakeChallenge) return;
                routine().wakeChallenge = position;
                a.persistRoutine();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        wake.body.addView(challengeSpin, Ui.wide(a, 0));

        wake.body.addView(Ui.label(a, "How your own line is used", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        lineSpin = new Spinner(a);
        ArrayAdapter<String> lineAd = Lang.adapter(a, android.R.layout.simple_spinner_item,
                new String[]{"Shown as the quote", "Typed to put the day off", "Typed to start the day", "Typed for both"});
        lineAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lineSpin.setAdapter(lineAd);
        lineSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding || position == routine().wakeLineUse) return;
                routine().wakeLineUse = position;
                a.persistRoutine();
                limitQuoteBox();
                showQuotePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        wake.body.addView(lineSpin, Ui.wide(a, 0));
        wake.body.addView(Ui.label(a, "A line you type is used only where that challenge is set to typing, and is cut at 80 letters.",
                12, Ui.MUTED, false), Ui.wide(a, 2));
        wake.body.addView(Ui.label(a, "Your own line (optional)", 12, Ui.MUTED, true), Ui.wrap(a, 12));
        quoteEdit = new EditText(a);
        quoteEdit.setHint("Leave empty to use the lines that come with the app, in your language");
        quoteEdit.setTextColor(Ui.INK);
        quoteEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        quoteEdit.setMinLines(2);
        quoteEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Routine.MAX_QUOTE_LEN)});
        quoteEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (binding) return;
                routine().wakeQuote = Routine.cleanQuote(s.toString());
                a.persistSoon();
                showQuotePreview();
            }
        });
        wake.body.addView(quoteEdit, Ui.wide(a, 0));
        quotePreview = Ui.label(a, "", 13, Ui.INK, false);
        quotePreview.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10));
        quotePreview.setBackground(Ui.box(a, Ui.AMBER_SOFT, Ui.LINE, 14));
        wake.body.addView(quotePreview, Ui.wide(a, 8));

        nextAlarm = Ui.label(a, "", 14, Ui.INK, true);
        root.addView(nextAlarm, Ui.wide(a, 18));

        root.addView(Ui.button(a, "Ring a test alarm in 10 seconds", true, v -> testAlarm()), Ui.wide(a, 12));
        root.addView(Ui.label(a, "The test goes the same way as a real alarm. Lock the phone first to see it as it will be.",
                12, Ui.MUTED, false), Ui.wide(a, 6));

        readyPart = alarmParts.add(Expander.more(a, root, "Is the phone ready?", "alarm_ready", false));
        healthBox = new LinearLayout(a);
        healthBox.setOrientation(LinearLayout.VERTICAL);
        readyPart.body.addView(healthBox, Ui.wide(a, 4));

        diagText = Ui.label(a, "", 12, Ui.MUTED, false);
        diagText.setLineSpacing(a.dp(2), 1f);
        readyPart.body.addView(diagText, Ui.wide(a, 10));
    }

    /** Switches alarms on (from Home or the Routine tab) and starts the permission questions. */
    void enableAlarms() {
        if (!routine().alarmOn) setAlarms(true);
    }

    private void setAlarms(boolean on) {
        Routine r = routine();
        r.alarmOn = on;
        a.persistRoutine();
        final long now = System.currentTimeMillis();
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            try {
                Store.history(app).append(Ev.alarms(now, on));
            } catch (RuntimeException e) {
                CrashLog.note(app, "alarm log", e);
            }
        }, "ev-alarms").start();
        if (on) {
            DayCloser.schedule(a);
            if (!nextPermissionStep()) a.toastText("Alarms are on");
        }
        refresh();
        a.refreshAlarmViews();
    }

    /**
     * Asks for whatever alarms still need, one thing at a time: notifications, then exact alarms.
     * Returns true when it opened a question, false when both are already allowed.
     */
    boolean nextPermissionStep() {
        if (!AlarmHealth.notificationsOn(a)) {
            askNotifications();
            return true;
        }
        if (!AlarmScheduler.exactAllowed(a)) {
            a.toastText("Switch on \u201cAllow setting alarms and reminders\u201d so alarms ring on the minute.");
            openExactAlarmSettings();
            return true;
        }
        return false;
    }

    /** Rings a test alarm in ten seconds and says so. */
    void testAlarm() {
        boolean exact = AlarmScheduler.scheduleTest(a, 10);
        String extra = "";
        if (!AlarmHealth.notificationsOn(a)) extra += "\n\nNotifications are off for this app, so you will only hear it.";
        if (!exact) extra += "\n\nAndroid is not allowing exact alarms, so it may come a little late.";
        new android.app.AlertDialog.Builder(a)
                .setTitle("Test alarm set")
                .setMessage("It rings in 10 seconds. To try it as it will really be, press the power button now to lock the phone." + extra)
                .setPositiveButton("OK", null)
                .show();
    }

    /** Does what the button next to a checklist line promises. */
    void fix(int item) {
        switch (item) {
            case AlarmHealth.ON: enableAlarms(); break;
            case AlarmHealth.NOTIF: askNotifications(); break;
            case AlarmHealth.EXACT: openExactAlarmSettings(); break;
            case AlarmHealth.FULL: openFullScreenSettings(); break;
            case AlarmHealth.BATTERY: openBatterySettings(); break;
            case AlarmHealth.POPUP: openAlarmChannelSettings(); break;
            case AlarmHealth.PHONE: AlarmHealth.openPhoneSettings(a); break;
            case AlarmHealth.VOLUME: AlarmHealth.openSoundSettings(a); break;
            default: break;
        }
    }

    private void open(Class<?> screen) {
        try {
            a.startActivity(new Intent(a, screen));
        } catch (RuntimeException e) {
            CrashLog.note(a, "open screen", e);
            a.toastText("Could not open that screen");
        }
    }

    private void openExactAlarmSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                i.setData(Uri.parse("package:" + a.getPackageName()));
                a.startActivity(i);
            }
        } catch (RuntimeException e) {
            a.toastText("Open Settings, Apps, Dinacharya Launcher, Alarms and reminders");
        }
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !asked
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            asked = true;
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
        try {
            a.startActivity(i);
        } catch (RuntimeException e) {
            a.toastText("Open Settings, Apps, Dinacharya Launcher, Notifications");
        }
    }

    private void openFullScreenSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                i.setData(Uri.parse("package:" + a.getPackageName()));
                a.startActivity(i);
            }
        } catch (RuntimeException e) {
            a.toastText("Open Settings, Apps, Dinacharya Launcher, and allow full screen notifications");
        }
    }

    /** The alarm notification's own settings, where it can be set back to "Pop on screen". */
    private void openAlarmChannelSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
            i.putExtra(Settings.EXTRA_CHANNEL_ID, "ring");
            a.startActivity(i);
            a.toastText("Choose Alerting, and allow pop-up on screen.");
        } catch (RuntimeException e) {
            askNotifications();
        }
    }

    private void openBatterySettings() {
        BatterySetup.ask(a);
    }

    /** "6:30 AM tomorrow". */
    String when(long ms) {
        ZoneId z = Store.zone();
        ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(z);
        LocalDate today = LocalDate.now(z);
        String day;
        if (t.toLocalDate().equals(today)) day = "today";
        else if (t.toLocalDate().equals(today.plusDays(1))) day = "tomorrow";
        else day = t.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Lang.locale());
        return Routine.formatTime(t.getHour() * 60 + t.getMinute(), routine().fmt24) + " " + day;
    }

    /** The next alarm that would ring, or null when alarms are off or every slot has its bell off. */
    AlarmPlanner.Event nextEvent() {
        Routine r = routine();
        if (!r.alarmOn) return null;
        return AlarmPlanner.nextAlarm(r, Plan.valid(r, Store.overrides(a, r)), Store.alarmState(a), System.currentTimeMillis(), Store.zone());
    }

    /** Where the alarms stand, in plain words: set in Android, last handed to the app, whether ringing started. */
    String diagnosis() {
        AlarmScheduler.Diag d = AlarmScheduler.diag(a);
        Routine r = routine();
        StringBuilder sb = new StringBuilder();
        if (!r.alarmOn) sb.append("Alarms are off, so nothing is set in Android.");
        else if (d.armedFor > 0) sb.append("Set in Android for ").append(when(d.armedFor))
                .append(d.armedExact ? " (exact)." : ". Android may deliver it a few minutes late.");
        else sb.append("Nothing is set in Android right now.");
        if (d.missedFor > 0) {
            if (d.missedExit != 0) {
                sb.append("\nAndroid closed the app and cleared the alarm set for ").append(when(d.missedFor))
                        .append(". It is set again now.");
            } else {
                sb.append("\nAn alarm set for ").append(when(d.missedFor))
                        .append(" never reached the app. The phone was off, or it stopped the app. See the checklist above.");
            }
        }
        // How the app last ended, when that matters to the alarms and the wallpaper (Android 11 and later tells).
        if (d.exitAt > 0 && System.currentTimeMillis() - d.exitAt < 3L * 24 * 3600000L) {
            sb.append("\nLast time Android ended the app: ").append(when(d.exitAt));
            sb.append("\n").append(AlarmWatch.what(d.exitReason));
            sb.append("\n").append(AlarmWatch.consequence(d.exitReason));
        }
        if (d.firedAt > 0) {
            sb.append("\nLast alarm Android gave the app: ").append(when(d.firedAt)).append(d.firedTest ? " (test)" : "");
            if (d.ringAt > 0) sb.append(". Ringing started.");
            else if (d.fallbackAt > 0) sb.append(". Android would not start the ringing service, so a notification was used.");
            else sb.append(". Ringing did not start. Copy the problem report from Settings > Help and send it.");
        } else {
            sb.append("\nNo alarm has reached the app yet. Try the test alarm.");
        }
        return sb.toString();
    }

    /** Alarm facts for the problem report: what was last set, what reached the app, and the checklist. */
    String alarmReport() {
        try {
            StringBuilder sb = new StringBuilder(diagnosis());
            for (AlarmHealth.Item it : AlarmHealth.check(a, routine())) {
                sb.append("\n").append(it.title).append(": ")
                        .append(it.state == AlarmHealth.OK ? "ok" : it.state == AlarmHealth.NEEDED ? "MISSING" : "not set");
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return "Alarm details unavailable.";
        }
    }

    private void stepWakeDelay(int delta) {
        Routine r = routine();
        int v = Math.max(SlotPlay.STEP, Math.min(60, Math.round((r.wakeDelayMin + delta) / (float) SlotPlay.STEP) * SlotPlay.STEP));
        if (v == r.wakeDelayMin) return;
        r.wakeDelayMin = v;
        a.persistRoutine();
        delayText.setText(v + " min");
    }

    /** Shows the line the wake-up screen would show today, so the choice can be judged. */
    private void showQuotePreview() {
        if (quotePreview == null) return;
        Routine r = routine();
        if (r.wakeLineUse != Routine.LINE_QUOTE && !Routine.cleanQuote(r.wakeQuote).isEmpty()) {
            quotePreview.setText("You will type:  “" + Routine.cutForTyping(Routine.cleanQuote(r.wakeQuote)) + "”");
            return;
        }
        WakeQuotes.Quote q = WakeQuotes.quote("", LocalDate.now(Store.zone()), 0);
        quotePreview.setText("Today\u2019s quote:  “" + Tr.t(q.text) + "”" + (q.author.isEmpty() ? "" : "  \u2014 " + q.author));
    }

    /** The box takes up to 200 letters for a quote, but only 80 when the line is to be typed. */
    private void limitQuoteBox() {
        int max = routine().wakeLineUse == Routine.LINE_QUOTE ? Routine.MAX_QUOTE_LEN : Routine.MAX_TYPE_LEN;
        quoteEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
    }

    private void refreshAlarms() {
        Routine r = routine();
        binding = true;
        alarmBox.setChecked(r.alarmOn);
        askSilentBox.setChecked(r.askSilent);
        delayText.setText(r.wakeDelayMin + " min");
        challengeSpin.setSelection(Math.max(0, Math.min(2, r.wakeChallenge)));
        offSpin.setSelection(Math.max(0, Math.min(Routine.OFF_TYPE, r.wakeOff)));
        quoteOnBox.setChecked(r.wakeQuoteOn);
        dayOffBox.setChecked(r.wakeDayOff);
        lineSpin.setSelection(Math.max(0, Math.min(Routine.LINE_BOTH, r.wakeLineUse)));
        // never rewrite the box while it is being typed in: the saved line has its spaces trimmed, so it would differ and jump the cursor to the start
        if (!quoteEdit.hasFocus() && !quoteEdit.getText().toString().equals(r.wakeQuote)) quoteEdit.setText(r.wakeQuote);
        limitQuoteBox();
        binding = false;
        showQuotePreview();
        String next = "";
        if (r.alarmOn) {
            AlarmPlanner.Event e = nextEvent();
            next = e == null ? "No alarm is planned (all slots have their bell switched off)."
                    : "Next alarm: " + when(e.timeMs) + "  \u00b7  " + (e.title.isEmpty() ? "Untitled" : e.title);
        }
        nextAlarm.setText(next);
        nextAlarm.setVisibility(r.alarmOn ? View.VISIBLE : View.GONE);

        healthBox.removeAllViews();
        java.util.List<AlarmHealth.Item> checked = AlarmHealth.check(a, r);
        String missing = AlarmHealth.missingNames(checked);
        if (readyPart != null) readyPart.setSummary(missing.isEmpty() ? "All set" : "Still to allow: " + missing);
        for (final AlarmHealth.Item it : checked) {
            if (it.id == AlarmHealth.ON) continue;   // the switch above is that
            String action = it.id == AlarmHealth.PHONE || it.id == AlarmHealth.VOLUME ? "Open" : "Allow";
            boolean ok = it.state == AlarmHealth.OK;
            healthBox.addView(Ui.statusRow(a, it.title, it.why, ok, action, () -> fix(it.id)));
        }
        diagText.setText(diagnosis());
    }

    // ------------------------------------------------------------------ habits

    private Expander.Part habitsPart;
    /** Which habit was open, so that it stays open when the notes are worked out again. */
    private int habitsOpen = -1;

    void buildHabits(LinearLayout page) {
        habitsPart = Expander.card(a, page, "Habits", null, Limits.analysisAllowed(Pro.has(a, DayPass.ANALYSIS)) ? "What your days are telling you" : "Part of Pro", "ins_habits", false);
        LinearLayout root = habitsPart.body;
        habitsBox = new LinearLayout(a);
        habitsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(habitsBox, Ui.wide(a, 10));
        root.addView(Ui.label(a, "Worked out on this phone from your own days with simple rules. No AI service, nothing leaves the phone.",
                12, Ui.MUTED, false), Ui.wide(a, 10));
    }

    /** The habit notes are part of Pro: the free version says what they are, with the way to Pro. */
    private void showHabitsLocked() {
        if (habitsBox == null) return;
        habitsBox.removeAllViews();
        TextView t = Ui.label(a, "Notes worked out from your last days: where the day slips, what is going well, and one thing to try. "
                + "Part of Pro.", 14, Ui.MUTED, false);
        t.setLineSpacing(Ui.dp(a, 2), 1f);
        habitsBox.addView(t);
        habitsBox.addView(Ui.button(a, "See Pro", true, v -> ProPrompt.open(a)), Ui.wide(a, 12));
        ProPrompt.addOffer(a, habitsBox, 8, this::refreshHabits);
        if (habitsPart != null) habitsPart.setSummary("Part of Pro");
    }

    void refreshHabits() {
        if (!Limits.analysisAllowed(Pro.has(a, DayPass.ANALYSIS))) {
            showHabitsLocked();
            return;
        }
        final Context app = a.getApplicationContext();
        final Routine base = routine();
        new Thread(() -> {
            List<Suggestions.Habit> found;
            boolean empty;
            try {
                List<DayReport> recent = Store.history(app).recent(14);
                empty = recent.isEmpty();
                found = empty ? new ArrayList<Suggestions.Habit>() : Suggestions.habits(null, recent, base);
            } catch (RuntimeException e) {
                CrashLog.note(app, "habits", e);
                empty = true;
                found = new ArrayList<>();
            }
            final List<Suggestions.Habit> notes = found;
            final boolean none = empty;
            a.runOnUiThread(() -> {
                if (!a.isFinishing()) showHabits(notes, none);
            });
        }, "habits").start();
    }

    private static int habitStripe(int tone) {
        return tone == Suggestions.Habit.GOOD ? Ui.TEAL : tone == Suggestions.Habit.WATCH ? Ui.AMBER : 0xFF7A8090;
    }

    private static int habitInk(int tone) {
        return tone == Suggestions.Habit.GOOD ? Ui.TEAL_DARK : tone == Suggestions.Habit.WATCH ? Ui.AMBER : (Ui.night ? 0xFFC4C8D2 : 0xFF3F4552);
    }

    private static int habitFill(int tone) {
        return tone == Suggestions.Habit.GOOD ? Ui.TONAL : tone == Suggestions.Habit.WATCH ? Ui.AMBER_SOFT : (Ui.night ? 0x33808899 : 0xFFE9EAEE);
    }

    private static String habitWord(int tone) {
        return tone == Suggestions.Habit.GOOD ? "Good" : tone == Suggestions.Habit.WATCH ? "Watch" : "Set up";
    }

    /** The habits as small cards that open one at a time: a heading and a badge closed, what was seen and what to try open. */
    private void showHabits(List<Suggestions.Habit> habits, boolean none) {
        if (habitsBox == null) return;
        habitsBox.removeAllViews();
        String summary = "What your days are telling you";
        if (none) {
            habitsBox.addView(Ui.label(a, "Notes appear after your first full day. Turn on alarms and usage access so the app can see how the day went.",
                    14, Ui.MUTED, false));
        } else if (habits.isEmpty()) {
            habitsBox.addView(Ui.label(a, "Nothing to flag. Keep going.", 14, Ui.MUTED, false));
        } else {
            int watch = 0, good = 0, setup = 0;
            final Accordion acc = new Accordion();
            for (int i = 0; i < habits.size(); i++) {
                Suggestions.Habit h = habits.get(i);
                if (h.tone == Suggestions.Habit.GOOD) good++;
                else if (h.tone == Suggestions.Habit.WATCH) watch++;
                else setup++;
                Drop d = acc.add(new Drop(a, habitStripe(h.tone), "", h.head, "", habitWord(h.tone), "", habitInk(h.tone), habitFill(h.tone),
                        habitWord(h.tone)));
                if (!h.saw.isEmpty()) {
                    d.body.addView(Ui.label(a, "What I noticed", 13, Ui.TEAL_DARK, true));
                    TextView t = Ui.label(a, h.saw, 15, Ui.INK, false);
                    t.setLineSpacing(Ui.dp(a, 2), 1f);
                    d.body.addView(t, Ui.wide(a, 4));
                }
                if (!h.advice.isEmpty()) {
                    d.body.addView(Ui.label(a, "Try this", 13, Ui.TEAL_DARK, true), Ui.wide(a, h.saw.isEmpty() ? 0 : 12));
                    TextView t = Ui.label(a, h.advice, 15, Ui.INK, false);
                    t.setLineSpacing(Ui.dp(a, 2), 1f);
                    d.body.addView(t, Ui.wide(a, 4));
                }
                habitsBox.addView(d.root, Ui.wide(a, i == 0 ? 0 : 8));
            }
            acc.onChanged(() -> habitsOpen = acc.open());
            acc.show(habitsOpen);
            StringBuilder sb = new StringBuilder();
            if (watch > 0) sb.append(watch).append(" need attention");
            if (good > 0) sb.append(sb.length() > 0 ? ", " : "").append(good).append(" going well");
            if (setup > 0) sb.append(sb.length() > 0 ? ", " : "").append(setup).append(" not set up yet");
            summary = sb.toString();
        }
        if (habitsPart != null) habitsPart.setSummary(summary);
        habitsBox.addView(Ui.button(a, "Open Performance", false,
                v -> open(PerformanceActivity.class)), Ui.wide(a, 12));
    }

    // ------------------------------------------------------------------ refresh

    /** Everything that can change while the screen is open. */
    void refresh() {
        // Each part on its own, so one problem cannot take the others (or the screen) down.
        try {
            if (routine().alarmOn) AlarmScheduler.reschedule(a);   // picks up a newly granted permission
            if (nextAlarm != null) refreshAlarms();
        } catch (RuntimeException e) {
            CrashLog.note(a, "alarms section", e);
        }
        try {
            refreshChanges();
        } catch (RuntimeException e) {
            CrashLog.note(a, "today's changes", e);
        }
    }

    /** Cheap update for when the routine changes while the user types. */
    void routineChanged() {
        if (nextAlarm == null) return;
        try {
            refreshAlarms();
        } catch (RuntimeException e) {
            CrashLog.note(a, "alarms section", e);
        }
    }
}
