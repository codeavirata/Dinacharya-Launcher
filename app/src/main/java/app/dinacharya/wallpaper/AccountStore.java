package app.dinacharya.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The Google account and the backup settings. Kept in its own file ("account.xml") so Android's own
 * device backup never copies the sign-in keys.
 */
final class AccountStore implements DeviceAuth.Kv {
    static final String PICKER = "picker";
    static final String CODE = "code";

    private final Context c;
    private final SharedPreferences p;

    AccountStore(Context context) {
        c = context.getApplicationContext();
        p = c.getSharedPreferences("account", Context.MODE_PRIVATE);
    }

    @Override
    public String get(String key) {
        return p.getString(key, null);
    }

    @Override
    public void put(String key, String value) {
        p.edit().putString(key, value).apply();
    }

    private String s(String key) {
        String v = p.getString(key, null);
        return v == null ? "" : v;
    }

    private long l(String key) {
        try {
            return Long.parseLong(s(key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ account

    /** "picker", "code" or "" when not signed in. */
    String method() {
        return s("method");
    }

    boolean signedIn() {
        String m = method();
        if (CODE.equals(m)) return deviceAuth().signedIn();
        return PICKER.equals(m);
    }

    String email() {
        return s("email");
    }

    String name() {
        return s("name");
    }

    /** The first name, for a greeting. */
    String firstName() {
        String n = name().trim();
        int sp = n.indexOf(' ');
        return sp > 0 ? n.substring(0, sp) : n;
    }

    void signedInAs(String method, String email, String name) {
        p.edit().putString("method", method).putString("email", email == null ? "" : email)
                .putString("name", name == null ? "" : name).putString("error", "").apply();
    }

    void forget() {
        p.edit().putString("method", "").putString("email", "").putString("name", "")
                .putString("refresh", "").putString("access", "").putString("expiry", "0")
                .putString("lastOk", "0").putString("lastSize", "0").putString("error", "").apply();
    }

    // ------------------------------------------------------------------ code sign-in

    /** The client for code sign-in: built into the app (res/values/google.xml), or pasted in the app. */
    String codeClientId() {
        String pasted = s("codeClientId");
        return !pasted.isEmpty() ? pasted : c.getString(R.string.google_code_client_id).trim();
    }

    String codeClientSecret() {
        String pasted = s("codeClientSecret");
        return !pasted.isEmpty() ? pasted : c.getString(R.string.google_code_client_secret).trim();
    }

    void setCodeClient(String id, String secret) {
        p.edit().putString("codeClientId", id.trim()).putString("codeClientSecret", secret.trim()).apply();
    }

    DeviceAuth deviceAuth() {
        return new DeviceAuth(new Http.Url(), this, System::currentTimeMillis, codeClientId(), codeClientSecret());
    }

    // ------------------------------------------------------------------ backup settings

    int frequency() {
        String v = s("freq");
        if (v.isEmpty()) return BackupPolicy.DAILY;
        try {
            int f = Integer.parseInt(v);
            return f >= 0 && f < BackupPolicy.NAMES.length ? f : BackupPolicy.DAILY;
        } catch (NumberFormatException e) {
            return BackupPolicy.DAILY;
        }
    }

    void setFrequency(int f) {
        put("freq", String.valueOf(f));
    }

    boolean wifiOnly() {
        return !"0".equals(s("wifiOnly"));
    }

    void setWifiOnly(boolean on) {
        put("wifiOnly", on ? "1" : "0");
    }

    long lastOk() {
        return l("lastOk");
    }

    long lastSize() {
        return l("lastSize");
    }

    String lastError() {
        return s("error");
    }

    void backedUp(long when, long size) {
        p.edit().putString("lastOk", String.valueOf(when)).putString("lastSize", String.valueOf(size)).putString("error", "").apply();
    }

    void failed(String why) {
        put("error", why == null ? "Backup failed" : why);
    }

    /** Remembers that the "restore your backup?" question was answered for this account. */
    boolean restoreAsked() {
        return s("restoreAsked").equals(email());
    }

    void setRestoreAsked() {
        put("restoreAsked", email());
    }
}
