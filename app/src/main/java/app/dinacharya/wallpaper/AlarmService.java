package app.dinacharya.wallpaper;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Rings the alarm: plays the alarm sound and vibrates until the question is answered, and
 * shows the question over the lock screen. If nobody answers within five minutes it stops by
 * itself, keeps a "missed" note and leaves the schedule as it was.
 *
 * How it alerts follows the slot's setting: with ring it plays the sound and vibrates, with vibration it only vibrates, and a silent
 * one never reaches this service ({@link #postSilent} puts a quiet notification up instead). The start-of-day alarm is the one that
 * keeps going until the day is started or put off; it still stops by itself after half an hour so a phone left alone is not ringing
 * for ever.
 *
 * Only one thing shows for a ringing alarm (see {@link AlarmSurface}): the full-screen question when the phone is locked or asleep, one banner
 * when it is in use in another app, or the question itself when Dinacharya is on screen. Never the banner and the full screen together.
 */
public class AlarmService extends Service {
    static final String ACTION_RING = "app.dinacharya.wallpaper.RING";
    static final String ACTION_SILENCE = "app.dinacharya.wallpaper.SILENCE";

    private static final String CH_RING = "ring";
    /** The same alarm while its question is already on screen: it keeps a quiet notification and never pops up as a banner as well. */
    private static final String CH_RING_QUIET = "ring_quiet";
    private static final String CH_MISSED = "missed";
    private static final int ID_RING = 77;
    private static final int ID_MISSED = 78;
    private static final int ID_NEVER = 79;
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long WAKE_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final String CH_SILENT = "slot_silent";

    /** The alarm that is ringing right now and has not been answered (the service and the screens share one process). */
    private static volatile AlarmPlanner.Event ringing;
    private static long lastBrought;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** How long a new alarm waits for the question still ringing before it takes over (the wake-up alarm rings for half an hour). */
    static final long WAIT_CAP_MS = 5 * 60 * 1000L;
    /** The alarm that came while another was still ringing: it waits its turn, and is dropped when the first is answered. */
    private AlarmPlanner.Event queued;
    private long ringingSince;
    private final Runnable promote = new Runnable() {
        @Override
        public void run() {
            promoteQueued();
        }
    };
    private long lastReshow;
    private boolean watchingScreen;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            AlarmPlanner.Event e = current;
            if (e != null) reshow(e);
        }
    };
    private MediaPlayer player;
    /** A tone made by the app itself, for when no ringtone on the phone can be played. */
    private AudioTrack beep;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private AlarmPlanner.Event current;

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            giveUp();
        }
    };

    /** An answer given from a notification button: stops the ringing and clears its notification. */
    static void silenceFromNotification(Context c) {
        if (ringing == null) return;
        ringing = null;
        try {
            Intent i = new Intent(c, AlarmService.class);
            i.setAction(ACTION_SILENCE);
            c.startService(i);
        } catch (RuntimeException ignored) {
            // it stops by itself after five minutes
        }
    }

    /** The question was answered (or the alarm gave up): nothing is ringing any more. */
    static void answered() {
        ringing = null;
    }

    /** True while an alarm is ringing and has not been answered. */
    static boolean isRinging() {
        return ringing != null;
    }

    /** How long this alarm may ring before it stops by itself. */
    static long limitFor(AlarmPlanner.Event e) {
        return e != null && e.wake ? WAKE_TIMEOUT_MS : TIMEOUT_MS;
    }

    /** The number of the quiet notification of this alarm (the same one every time, so the buttons can clear it). */
    static int silentId(AlarmPlanner.Event e) {
        return 9000 + Math.floorMod((int) (e.timeMs / 60000L) + e.src * 31, 997);
    }

    /** What the notification of this alarm says, in the person's language: {title, text}. */
    static String[] words(Context c, AlarmPlanner.Event e) {
        boolean h24 = Store.load(c).fmt24;
        String title;
        String text;
        String name = e.title == null ? "" : e.title;
        if (AlarmPlanner.isTest(e)) {
            title = "Test alarm";
            text = "This is how an alarm looks. Tap to stop it.";
        } else if (e.kind == AlarmPlanner.PLAN_ASK) {
            title = "Plan your day?";
            text = "Your day is off. Plan it now, later, or leave it. Tap to answer.";
        } else if (e.kind == AlarmPlanner.BREAK_END) {
            title = "Your break is over";
            text = name.isEmpty() ? "Time to get back to work. Tap to answer." : "Time to get back to " + name + ". Tap to answer.";
        } else if ((e.kind == AlarmPlanner.SLOT || e.kind == AlarmPlanner.START_AGAIN) && e.wake) {
            title = "Time to start your day";
            text = "Good morning. Tap to start the day or put it off.";
        } else if (e.kind == AlarmPlanner.START_AGAIN) {
            title = name.isEmpty() ? "Next slot" : name;
            text = "Time to start now. Tap to answer.";
        } else if (e.kind == AlarmPlanner.SLOT) {
            title = name.isEmpty() ? "Next slot" : name;
            long mins = Math.max(1L, (e.slotEndMs - e.slotStartMs) / 60000L);
            return new String[]{Lang.tr(title), clock(e.slotStartMs, h24) + " – " + clock(e.slotEndMs, h24) + " · "
                    + Routine.formatDuration((int) mins) + ". " + Lang.tr("Start now, or tap to choose.")};
        } else {
            title = "Time to sleep";
            text = "Going to sleep, or will it take more time?";
        }
        return new String[]{Lang.tr(title), Lang.tr(text)};
    }

    /**
     * A slot with its alert set to silent: one quiet notification, no sound, no vibration and no screen taking over. For a slot it has
     * the two quick answers on it, "Start now" and "In 10 min"; tapping it opens the full question.
     */
    static void postSilent(Context c, AlarmPlanner.Event e) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CH_SILENT, Lang.tr("Slot questions (silent)"), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            PendingIntent open = PendingIntent.getActivity(c, silentId(e), openIntent(c, e), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String[] w = words(c, e);
            Notification.Builder b = new Notification.Builder(c, CH_SILENT)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(w[0])
                    .setContentText(w[1])
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(open)
                    .setAutoCancel(true);
            if (e.kind == AlarmPlanner.SLOT || e.kind == AlarmPlanner.START_AGAIN) {
                b.addAction(quickAction(c, e, SlotActions.ACTION_START_NOW, Lang.tr("Start now"), 1));
                b.addAction(quickAction(c, e, SlotActions.ACTION_START_LATER, Lang.tr("In 10 min"), 2));
            }
            nm.notify(silentId(e), b.build());
        } catch (RuntimeException ex) {
            CrashLog.note(c, "silent notification", ex);
        }
    }

    private static Notification.Action quickAction(Context c, AlarmPlanner.Event e, String action, String label, int slot) {
        Intent i = new Intent(c, SlotActionReceiver.class);
        i.setAction(action);
        AlarmScheduler.put(i, e);
        PendingIntent pi = PendingIntent.getBroadcast(c, silentId(e) + slot * 10000, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_stat), label, pi).build();
    }

    /**
     * Brings the question to the front if an alarm is ringing and nobody has answered. Called when the home screen or the
     * dashboard comes to the front: the app is then on screen, so Android lets it open the question at once.
     */
    static boolean showIfRinging(Context c) {
        AlarmPlanner.Event e = ringing;
        if (e == null) return false;
        long t = android.os.SystemClock.uptimeMillis();
        if (t - lastBrought < 2500L) return false;   // never in a loop, even if the screen closes itself
        lastBrought = t;
        try {
            c.startActivity(openIntent(c, e));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Intent openIntent(Context c, AlarmPlanner.Event e) {
        Intent open = new Intent(c, AlarmActivity.class);
        AlarmScheduler.put(open, e);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return open;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SILENCE.equals(intent.getAction())) {
            current = null;           // answered: nothing to mark as missed
            queued = null;            // the person is busy with the question; the next slot waits on the Home screen as "No response"
            handler.removeCallbacks(promote);
            ringing = null;
            stopRinging();
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(ID_RING);   // the backup notification, if that was used
            } catch (RuntimeException ignored) {
                // nothing to clear
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        AlarmPlanner.Event e = AlarmScheduler.read(intent);
        if (e == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (current != null && !AlarmPlanner.isTest(current)) {
            // A new alarm came while the last question was still ringing. It does not push the old one off the screen: it waits until the
            // old one is answered (then it is dropped, the person is busy) or stops by itself, and never for more than WAIT_CAP_MS.
            queued = e;
            handler.removeCallbacks(promote);
            long left = limitFor(current) - (android.os.SystemClock.elapsedRealtime() - ringingSince);
            handler.postDelayed(promote, Math.max(1000L, Math.min(WAIT_CAP_MS, left)));
            return START_NOT_STICKY;
        }
        if (current != null) logMissed(current);
        current = e;
        try {
            ring(e);
        } catch (RuntimeException ex) {
            CrashLog.note(this, "ring", ex);
            current = null;
            stopRinging();
            postFallback(this, e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(promote);
        ringing = null;
        stopRinging();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ ringing

    private void channels(NotificationManager nm) {
        NotificationChannel ring = new NotificationChannel(CH_RING, Lang.tr("Alarms"), NotificationManager.IMPORTANCE_HIGH);
        ring.setDescription("Rings when a slot of your day starts");
        ring.setSound(null, null);
        ring.enableVibration(false);
        ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ring);
        nm.createNotificationChannel(quietChannel());
        NotificationChannel missed = new NotificationChannel(CH_MISSED, Lang.tr("Missed alarms"), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(missed);
    }

    private static NotificationChannel quietChannel() {
        NotificationChannel quiet = new NotificationChannel(CH_RING_QUIET, Lang.tr("Alarm in progress"), NotificationManager.IMPORTANCE_LOW);
        quiet.setDescription("Shown while an alarm's question is on your screen");
        quiet.setSound(null, null);
        quiet.enableVibration(false);
        quiet.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        return quiet;
    }

    /**
     * Which one thing shows for an alarm that rings now: the full-screen question (phone locked or asleep), one banner (phone in use in
     * another app) or the question inside the app (Dinacharya is on screen). See {@link AlarmSurface}.
     */
    static int surfaceNow(Context c) {
        boolean screenOn = true;
        boolean locked = false;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm != null) screenOn = pm.isInteractive();
        } catch (RuntimeException ignored) {
            // cannot tell: treat the screen as on
        }
        try {
            KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) locked = km.isKeyguardLocked();
        } catch (RuntimeException ignored) {
            // cannot tell: treat the phone as unlocked
        }
        return AlarmSurface.choose(screenOn, locked, App.inFront());
    }

    /**
     * The question is on screen now, so the alarm's notification goes quiet: no banner on top of the question. (Called by the question
     * screen each time it comes to the front while an alarm rings.)
     */
    static void questionShown(Context c) {
        AlarmPlanner.Event e = ringing;
        if (e == null) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(quietChannel());
            nm.notify(ID_RING, ringNotification(c, e, AlarmSurface.IN_APP));
        } catch (RuntimeException ignored) {
            // the notification stays as it was
        }
    }

    private static String clock(long ms, boolean h24) {
        ZonedDateTime z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        return Routine.formatTime(z.getHour() * 60 + z.getMinute(), h24);
    }

    /**
     * The ringing notification: the slot's name, its time and length, and for a slot two quick answers, "Start now" and "In 10 min", so a
     * slot can be started from the shade without opening the screen. Tapping it opens the full question.
     */
    private static Notification ringNotification(Context c, AlarmPlanner.Event e, int surface) {
        PendingIntent pi = PendingIntent.getActivity(c, 5001, openIntent(c, e), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String[] w = words(c, e);
        Notification.Builder b = new Notification.Builder(c, AlarmSurface.popsBanner(surface) ? CH_RING : CH_RING_QUIET)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(w[0])
                .setContentText(w[1])
                .setStyle(new Notification.BigTextStyle().bigText(w[1]))
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pi);
        // Only a phone that is locked or asleep gets the full-screen intent. On a phone in use it would add a second window to the banner.
        if (AlarmSurface.carriesFullScreen(surface)) b.setFullScreenIntent(pi, true);
        if ((e.kind == AlarmPlanner.SLOT || e.kind == AlarmPlanner.START_AGAIN) && !e.wake && !AlarmPlanner.isTest(e)) {
            b.addAction(quickAction(c, e, SlotActions.ACTION_START_NOW, Lang.tr("Start now"), 1));
            b.addAction(quickAction(c, e, SlotActions.ACTION_START_LATER, Lang.tr("In 10 min"), 2));
        }
        return b.build();
    }

    private void ring(AlarmPlanner.Event e) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        channels(nm);
        int surface = surfaceNow(this);
        Notification n = ringNotification(this, e, surface);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14 and later: the type reserved for alarm apps that hold the exact-alarm permission.
                startForeground(ID_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
            } else {
                startForeground(ID_RING, n);
            }
        } catch (RuntimeException refused) {
            // Android would not let the ringing service run. A loud notification still carries the question.
            CrashLog.note(this, "alarm foreground", refused);
            current = null;
            postFallback(this, e);
            stopSelf();
            return;
        }

        ringing = e;
        ringingSince = android.os.SystemClock.elapsedRealtime();
        AlarmScheduler.noteRinging(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            if (wake != null && wake.isHeld()) wake.release();
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dinacharya:alarm");
            wake.acquire(limitFor(e) + 15000L);
        }
        startSound(e.mode);
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, limitFor(e));

        // One thing shows, chosen above (see AlarmSurface):
        // - phone locked or asleep: the full-screen notification opens the question by itself, and this tries it at once too;
        // - Dinacharya on screen: the question opens right there, and the notification stays quiet;
        // - phone in use in another app: one banner, and the question waits behind a tap, so nothing opens on top of what you are doing.
        // After that, waking or unlocking the phone while it rings shows the question again (see reshow), and so does coming back to the home screen.
        if (AlarmSurface.opensQuestion(surface)) {
            try {
                startActivity(openIntent(this, e));
            } catch (RuntimeException ignored) {
                // the notification still carries the question
            }
        }
        watchScreen();
    }

    /** While it rings, waking or unlocking the phone shows the question again (and pops the notification up once more). */
    private void watchScreen() {
        if (watchingScreen) return;
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen, f);
            watchingScreen = true;
        } catch (RuntimeException ignored) {
            // the other two ways still work
        }
    }

    private void unwatchScreen() {
        if (!watchingScreen) return;
        try {
            unregisterReceiver(screen);
        } catch (RuntimeException ignored) {
            // already gone
        }
        watchingScreen = false;
    }

    private void reshow(AlarmPlanner.Event e) {
        long t = System.currentTimeMillis();
        if (t - lastReshow < 1500L) return;
        lastReshow = t;
        if (AlarmActivity.onScreen()) return;   // the question is already in front: nothing to add
        int surface = surfaceNow(this);
        if (AlarmSurface.opensQuestion(surface)) {
            try {
                startActivity(openIntent(this, e));
            } catch (RuntimeException ignored) {
                // blocked in the background: the notification below still shows it
            }
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID_RING, ringNotification(this, e, surface));
        } catch (RuntimeException ignored) {
            // the first notification is still there
        }
    }

    private static AudioAttributes alarmAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    /**
     * The sounds to try, best first: the alarm tone the person chose on the phone, then the phone's default alarm, then the
     * notification and ringtone sounds. A phone can have none chosen, or one that cannot be read, so each is tried in turn.
     */
    private java.util.List<Uri> toneCandidates() {
        java.util.List<Uri> out = new java.util.ArrayList<>();
        int[] types = {RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE};
        for (int type : types) {
            Uri chosen = null;
            try {
                chosen = RingtoneManager.getActualDefaultRingtoneUri(this, type);
            } catch (RuntimeException ignored) {
                // fall back to the default below
            }
            if (chosen != null && !out.contains(chosen)) out.add(chosen);
            Uri def = RingtoneManager.getDefaultUri(type);
            if (def != null && !out.contains(def)) out.add(def);
        }
        return out;
    }

    /** Plays one sound over and over, or returns null when it cannot be played. */
    private MediaPlayer play(Uri uri) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(alarmAttributes());
            mp.setDataSource(this, uri);
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            return mp;
        } catch (java.io.IOException | RuntimeException e) {
            mp.release();
            return null;
        }
    }

    /** A plain beep, half a second on and half a second off, made by the app: the last resort when the phone has no sound to play. */
    private AudioTrack startBeep() {
        try {
            final int rate = 22050;
            final int on = rate / 2;
            short[] pcm = new short[rate];
            for (int i = 0; i < on; i++) {
                double fade = Math.min(1.0, Math.min(i, on - i) / 400.0);   // short fades, so it does not click
                pcm[i] = (short) (Math.sin(2 * Math.PI * 880 * i / rate) * 14000 * fade);
            }
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(alarmAttributes())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(pcm, 0, pcm.length);
            t.setLoopPoints(0, pcm.length, -1);
            t.play();
            return t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Starts the alert: the alarm sound only for ring, and the vibration for ring and for vibration. */
    @SuppressWarnings("deprecation")
    private void startSound(int mode) {
        stopRinging();
        if (mode == Routine.ALERT_RING) {
            for (Uri uri : toneCandidates()) {
                player = play(uri);
                if (player != null) break;
            }
            if (player == null) {
                beep = startBeep();
                CrashLog.note(this, "alarm sound", new RuntimeException(beep != null
                        ? "no ringtone on this phone could be played; a built-in beep was used"
                        : "no ringtone on this phone could be played and the built-in beep failed too"));
            }
        }
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // Tagged as an alarm, so the phone lets it vibrate from the background and while it is set to vibrate or silent.
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500}, 0), alarmAttributes());
            }
        } catch (RuntimeException e) {
            CrashLog.note(this, "alarm vibration", e);
        }
    }

    private void stopRinging() {
        handler.removeCallbacks(timeout);
        unwatchScreen();
        if (player != null) {
            try {
                player.stop();
            } catch (RuntimeException ignored) {
                // already stopped
            }
            player.release();
            player = null;
        }
        if (beep != null) {
            try {
                beep.stop();
            } catch (RuntimeException ignored) {
                // already stopped
            }
            beep.release();
            beep = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (wake != null && wake.isHeld()) wake.release();
    }

    /** Used when the ringing service could not start: one loud notification that carries the question. */
    static void postFallback(Context c, AlarmPlanner.Event e) {
        try {
            fallback(c, e);
        } catch (RuntimeException ex) {
            CrashLog.note(c, "alarm notification", ex);
        }
    }

    private static void fallback(Context c, AlarmPlanner.Event e) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        AlarmScheduler.noteFallback(c);
        boolean sound = e.mode == Routine.ALERT_RING;
        String id = sound ? "ring_fallback" : "ring_fallback_vibrate";
        NotificationChannel ch = new NotificationChannel(id, Lang.tr(sound ? "Alarms (backup)" : "Vibrating alarms (backup)"), NotificationManager.IMPORTANCE_HIGH);
        if (sound) {
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } else {
            ch.setSound(null, null);
        }
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 700, 500, 700, 500});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        PendingIntent pi = PendingIntent.getActivity(c, 5001, openIntent(c, e), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String[] w = words(c, e);
        // Locked or asleep: the full-screen intent opens the question. In use: only this one banner (it carries the sound), never both.
        boolean locked = AlarmSurface.carriesFullScreen(surfaceNow(c));
        Notification.Builder nb = new Notification.Builder(c, id)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(w[0])
                .setContentText(Lang.tr("Tap to answer."))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(limitFor(e));   // the same time the ringing service would have gone on for
        if (locked) nb.setFullScreenIntent(pi, true);
        Notification n = nb.build();
        // Without the ringing service nothing else repeats the sound, so the notification does: it goes on until it is opened
        // or its time is up. A sound that plays once and stops is easy to sleep through.
        n.flags |= Notification.FLAG_INSISTENT;
        nm.notify(ID_RING, n);
    }

    // ------------------------------------------------------------------ never came

    /**
     * An alarm that never reached the app (the phone or the person closed the app, and Android dropped its alarms): one notification that
     * says so, so the person knows it was not their mistake, and opens the app.
     */
    static void postNeverCame(Context c, String title) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(CH_MISSED, Lang.tr("Missed alarms"), NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(c, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(c, 5003, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String what = title == null || title.isEmpty() ? "" : title;
            Notification n = new Notification.Builder(c, CH_MISSED)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(what.isEmpty() ? Lang.tr("Missed alarm") : Lang.tr("Missed: " + what))
                    .setContentText(Lang.tr("This alarm did not ring because Android had closed the app. It is set again now."))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(ID_NEVER, n);
        } catch (RuntimeException ex) {
            CrashLog.note(c, "missed notice", ex);
        }
    }

    // ------------------------------------------------------------------ nobody answered

    private void logMissed(AlarmPlanner.Event e) {
        if (AlarmPlanner.isTest(e) || e.isTimer()) return;   // a test is not a missed slot, and a timer you set is not a question of the plan
        Store.history(this).append(Ev.missed(System.currentTimeMillis(), e.day, e.src, e.title));
    }

    /** The waiting alarm takes over: the old question is marked missed and the new one rings. */
    private void promoteQueued() {
        AlarmPlanner.Event next = queued;
        queued = null;
        handler.removeCallbacks(promote);
        if (next == null) return;
        handler.removeCallbacks(timeout);
        if (current != null) logMissed(current);
        current = next;
        try {
            ring(next);
        } catch (RuntimeException ex) {
            CrashLog.note(this, "ring queued", ex);
            current = null;
            stopRinging();
            postFallback(this, next);
            stopSelf();
        }
    }

    private void giveUp() {
        if (queued != null) {
            promoteQueued();   // the old one stops by itself: the waiting one rings now
            return;
        }
        AlarmPlanner.Event e = current;
        current = null;
        ringing = null;
        stopRinging();
        if (e != null && !AlarmPlanner.isTest(e) && !e.isTimer()) {
            logMissed(e);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 5002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String what = e.kind == AlarmPlanner.SLOT ? e.title : "Bedtime question";
            Notification n = new Notification.Builder(this, CH_MISSED)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(Lang.tr("Missed: " + what))
                    .setContentText(Lang.tr(e.wake ? "The wake-up alarm rang for half an hour. Your schedule was not changed."
                            : "The alarm rang for five minutes. Your schedule was not changed."))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(ID_MISSED, n);
        }
        stopForeground(true);
        stopSelf();
    }
}
