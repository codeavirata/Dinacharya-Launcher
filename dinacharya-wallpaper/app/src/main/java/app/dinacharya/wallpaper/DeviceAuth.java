package app.dinacharya.wallpaper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sign-in with a code: the app shows a short code, you type it at google.com/device on any phone or
 * computer, and Google hands the app a key for your Drive's hidden app space and your name and email.
 * The fallback for phones where the account picker does not work. Uses no Android classes.
 */
public final class DeviceAuth implements Tokens {
    /** Only the app's own hidden space in Drive, plus who you are. Google allows these for code sign-in. */
    public static final String SCOPES = "openid email profile " + CloudDrive.SCOPE_APPDATA;
    static final String DEVICE_URL = "https://oauth2.googleapis.com/device/code";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    /** Where the sign-in details live. */
    public interface Kv {
        String get(String key);

        void put(String key, String value);
    }

    public interface Clock {
        long now();
    }

    /** The short code the user types at google.com/device. */
    public static final class DeviceCode {
        public final String deviceCode;
        public final String userCode;
        public final String url;
        public final int intervalSec;
        public final int expiresInSec;

        DeviceCode(String deviceCode, String userCode, String url, int intervalSec, int expiresInSec) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.url = url;
            this.intervalSec = intervalSec;
            this.expiresInSec = expiresInSec;
        }
    }

    public enum Poll { PENDING, SLOW_DOWN, DONE, DENIED, EXPIRED }

    private final Http http;
    private final Kv kv;
    private final Clock clock;
    private final String clientId;
    private final String clientSecret;

    public DeviceAuth(Http http, Kv kv, Clock clock, String clientId, String clientSecret) {
        this.http = http;
        this.kv = kv;
        this.clock = clock;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    public boolean configured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    public boolean signedIn() {
        String r = kv.get("refresh");
        return r != null && !r.isEmpty();
    }

    public void forget() {
        kv.put("refresh", "");
        kv.put("access", "");
        kv.put("expiry", "0");
    }

    static String form(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            try {
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
            } catch (java.io.UnsupportedEncodingException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return sb.toString();
    }

    private Http.Resp post(String url, Map<String, String> fields) throws IOException {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        return http.send("POST", url, h, form(fields).getBytes(StandardCharsets.UTF_8));
    }

    /** Step 1: asks Google for a code to show the user. */
    public DeviceCode start() throws IOException {
        if (!configured()) throw new CloudException("Code sign-in is not set up (no client ID).", 0, false);
        Map<String, String> f = new LinkedHashMap<>();
        f.put("client_id", clientId);
        f.put("scope", SCOPES);
        Http.Resp r = post(DEVICE_URL, f);
        Map<String, Object> j = MiniJson.object(r.text());
        if (r.code != 200 || MiniJson.str(j, "device_code") == null) {
            String why = MiniJson.str(j, "error_description");
            if (why == null) why = MiniJson.str(j, "error");
            throw new CloudException("Google refused the sign-in: " + (why == null ? "HTTP " + r.code : why), r.code, false);
        }
        String url = MiniJson.str(j, "verification_url");
        if (url == null) url = MiniJson.str(j, "verification_uri");
        return new DeviceCode(MiniJson.str(j, "device_code"), String.valueOf(MiniJson.str(j, "user_code")),
                url == null ? "https://www.google.com/device" : url,
                (int) Math.max(3, MiniJson.num(j, "interval", 5)), (int) MiniJson.num(j, "expires_in", 1800));
    }

    /** Step 2: asks once whether the user has entered the code. Call again after the interval while PENDING. */
    public Poll poll(DeviceCode dc) throws IOException {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("client_id", clientId);
        f.put("client_secret", clientSecret);
        f.put("device_code", dc.deviceCode);
        f.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        Http.Resp r = post(TOKEN_URL, f);
        Map<String, Object> j = MiniJson.object(r.text());
        if (r.code == 200 && MiniJson.str(j, "access_token") != null) {
            save(j);
            return Poll.DONE;
        }
        String err = MiniJson.str(j, "error");
        if ("authorization_pending".equals(err)) return Poll.PENDING;
        if ("slow_down".equals(err)) return Poll.SLOW_DOWN;
        if ("access_denied".equals(err)) return Poll.DENIED;
        if ("expired_token".equals(err)) return Poll.EXPIRED;
        String why = MiniJson.str(j, "error_description");
        throw new CloudException("Sign-in failed: " + (why != null ? why : (err != null ? err : "HTTP " + r.code)), r.code, false);
    }

    private void save(Map<String, Object> j) {
        String refresh = MiniJson.str(j, "refresh_token");
        if (refresh != null && !refresh.isEmpty()) kv.put("refresh", refresh);
        kv.put("access", MiniJson.str(j, "access_token"));
        kv.put("expiry", String.valueOf(clock.now() + MiniJson.num(j, "expires_in", 3600) * 1000L - 60000L));
    }

    @Override
    public String token(boolean fresh) throws IOException {
        String refresh = kv.get("refresh");
        if (refresh == null || refresh.isEmpty()) throw new CloudException("Not signed in to Google.", 0, true);
        String access = kv.get("access");
        long expiry = 0;
        try {
            expiry = Long.parseLong(String.valueOf(kv.get("expiry")));
        } catch (NumberFormatException ignored) {
            // treat as expired
        }
        if (!fresh && access != null && !access.isEmpty() && clock.now() < expiry) return access;
        Map<String, String> f = new LinkedHashMap<>();
        f.put("client_id", clientId);
        f.put("client_secret", clientSecret);
        f.put("refresh_token", refresh);
        f.put("grant_type", "refresh_token");
        Http.Resp r = post(TOKEN_URL, f);
        Map<String, Object> j = MiniJson.object(r.text());
        if (r.code == 200 && MiniJson.str(j, "access_token") != null) {
            save(j);
            return kv.get("access");
        }
        String err = MiniJson.str(j, "error");
        boolean dead = "invalid_grant".equals(err) || "invalid_client".equals(err) || "unauthorized_client".equals(err);
        throw new CloudException("Could not renew the Google sign-in (" + (err != null ? err : "HTTP " + r.code) + ")", r.code, dead);
    }

    /** The refresh key, so sign-out can withdraw it at Google. */
    public String refreshToken() {
        String r = kv.get("refresh");
        return r == null ? "" : r;
    }
}
