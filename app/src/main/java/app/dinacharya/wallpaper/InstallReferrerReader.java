package app.dinacharya.wallpaper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

/**
 * Reads, once after an install from Google Play, the text a shared invite link attached ("invite_ABCD2345", see {@link ReferrerCode}) and
 * keeps the code, so the friend does not have to type it. It asks the Play Store app through the same service Google's install-referrer
 * library uses, without adding that library. Nothing happens when the app did not come from Play, when Play is not on the phone, or
 * when anything goes wrong: the code can still be typed in. The code is only kept, never used here: {@link InviteOffer} asks first.
 */
final class InstallReferrerReader {
    private InstallReferrerReader() {
    }

    private static final String KEY_DONE = "install_referrer_done";
    private static final String KEY_TRIES = "install_referrer_tries";
    private static final String KEY_CODE = "install_invite_code";
    private static final String KEY_OFFERED = "install_invite_offered";
    private static final int MAX_TRIES = 5;
    private static final String PLAY = "com.android.vending";
    private static final String DESCRIPTOR = "com.google.android.finsky.externalreferrer.IGetInstallReferrerService";
    private static boolean running;

    /** Asks Play for the install referrer, once (a few tries when Play does not answer). {@code found} runs on the main thread when a code was kept. */
    static void check(Context c, final Runnable found) {
        final Context app = c.getApplicationContext();
        try {
            final SharedPreferences p = Store.prefs(app);
            if (p.getBoolean(KEY_DONE, false)) return;
            int tries = p.getInt(KEY_TRIES, 0);
            if (tries >= MAX_TRIES) {
                p.edit().putBoolean(KEY_DONE, true).apply();
                return;
            }
            synchronized (InstallReferrerReader.class) {
                if (running) return;
                running = true;
            }
            p.edit().putInt(KEY_TRIES, tries + 1).apply();
            Intent i = new Intent("com.google.android.finsky.BIND_GET_INSTALL_REFERRER_SERVICE");
            i.setComponent(new ComponentName(PLAY, "com.google.android.finsky.externalreferrer.GetInstallReferrerService"));
            final ServiceConnection[] conn = new ServiceConnection[1];
            conn[0] = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, final IBinder binder) {
                    new Thread(() -> {
                        try {
                            read(app, binder, found);
                        } finally {
                            try {
                                app.unbindService(conn[0]);
                            } catch (RuntimeException ignored) {
                                // already unbound
                            }
                            synchronized (InstallReferrerReader.class) {
                                running = false;
                            }
                        }
                    }, "install-referrer").start();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            boolean bound = false;
            try {
                bound = app.bindService(i, conn[0], Context.BIND_AUTO_CREATE);
            } catch (RuntimeException e) {
                bound = false;
            }
            if (!bound) {
                // Play is not there (or will not talk to this app): there is no referrer to read, now or later.
                p.edit().putBoolean(KEY_DONE, true).apply();
                synchronized (InstallReferrerReader.class) {
                    running = false;
                }
            }
        } catch (RuntimeException e) {
            synchronized (InstallReferrerReader.class) {
                running = false;
            }
            CrashLog.note(app, "install referrer", e);
        }
    }

    /** One call to the Play service: the same Binder call the AIDL of Google's library makes (getInstallReferrer(Bundle), call number 1). */
    private static void read(Context app, IBinder binder, Runnable found) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            Bundle request = new Bundle();
            request.putString("package_name", app.getPackageName());
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            request.writeToParcel(data, 0);
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) return;
            reply.readException();
            Bundle result = reply.readInt() != 0 ? Bundle.CREATOR.createFromParcel(reply) : null;
            if (result == null) return;
            String code = ReferrerCode.parse(result.getString("install_referrer"));
            SharedPreferences.Editor e = Store.prefs(app).edit().putBoolean(KEY_DONE, true);
            if (!code.isEmpty()) e.putString(KEY_CODE, code);
            e.apply();
            if (!code.isEmpty() && found != null) new Handler(Looper.getMainLooper()).post(found);
        } catch (Exception e) {
            CrashLog.note(app, "install referrer read", e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** The friend's code from the install link when it has not been offered or used yet, otherwise "". */
    static String pendingCode(Context c) {
        try {
            String code = Store.prefs(c).getString(KEY_CODE, "");
            return code == null || !Pro.get(c).inviteUsed().isEmpty() ? "" : code;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** True when the friend was already asked about this code. */
    static boolean offered(Context c) {
        try {
            return Store.prefs(c).getBoolean(KEY_OFFERED, false);
        } catch (RuntimeException e) {
            return true;
        }
    }

    static void setOffered(Context c) {
        try {
            Store.prefs(c).edit().putBoolean(KEY_OFFERED, true).apply();
        } catch (RuntimeException e) {
            CrashLog.note(c, "invite offered", e);
        }
    }
}
