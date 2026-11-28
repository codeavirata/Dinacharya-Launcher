package app.dinacharya.wallpaper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Talks to the invite server (a small Cloudflare Worker, see server/). Only the invite screen uses it: nothing else in the app
 * depends on it, and a server that is down or slow only means a short message on that screen. Every call returns a {@link Result};
 * none throws. Uses no Android classes.
 */
public final class InviteClient {
    /** Where the invite server runs. */
    public static final String BASE = "https://dinacharya-invites.gandhar-amitsingh.workers.dev";
    public static final String DOWN = "Could not reach the invite server. Please try again later.";

    /** The answer to one call. When {@link #ok} is false, {@link #error} is a sentence to show; {@link #network} says the server was not reached. */
    public static final class Result {
        public boolean ok;
        public boolean network;
        /** The server said "too many tries" (HTTP 429): nothing is known about the code or the phone, only that this is not the moment. */
        public boolean limited;
        /** /active: the server was told too soon after the code was used, so nothing was released yet. Ask again later. */
        public boolean early;
        public String error = "";
        public String code = "";
        public int days;       // /redeem: the days the friend gets. /status: the days waiting. /claim: the days just paid out.
        public int waiting;
        public int invited;
        public int max;
        public int first, firstDays, laterDays;
        public int claimed;
        public int pending;
    }

    /** /active only counts as told when the server accepted it and released it (not when it said it was too early). */
    public static boolean released(Result r) {
        return r != null && r.ok && !r.early;
    }

    private final Http http;
    private final String base;

    public InviteClient(Http http, String base) {
        this.http = http;
        this.base = base;
    }

    public Result code(String device) {
        return call("/v1/code", device, null);
    }

    public Result redeem(String device, String code) {
        return call("/v1/redeem", device, code);
    }

    /** Asks whether {@code code} could be used by this phone. Uses nothing: the answer only tells the app whether to offer it. */
    public Result check(String device, String code) {
        return call("/v1/check", device, code);
    }

    public Result status(String device) {
        return call("/v1/status", device, null);
    }

    /** The friend's phone says the app has really been used. */
    public Result active(String device) {
        return call("/v1/active", device, null);
    }

    public Result claim(String device) {
        return call("/v1/claim", device, null);
    }

    private Result call(String path, String device, String code) {
        Result r = new Result();
        try {
            String body = "{\"device\":" + MiniJson.quote(device) + (code == null ? "" : ",\"code\":" + MiniJson.quote(code)) + "}";
            Map<String, String> h = new HashMap<>();
            h.put("Content-Type", "application/json");
            Http.Resp resp = http.send("POST", base + path, h, body.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> m = MiniJson.object(resp.text());
            if (resp.code >= 500 || m.isEmpty()) {
                r.network = true;
                r.error = DOWN;
                return r;
            }
            Object ok = m.get("ok");
            r.ok = Boolean.TRUE.equals(ok);
            String err = MiniJson.str(m, "error");
            if (!r.ok) {
                r.limited = resp.code == 429;
                r.error = err == null || err.isEmpty() ? "That did not work. Please try again." : err;
                return r;
            }
            String c = MiniJson.str(m, "code");
            r.code = c == null ? "" : c;
            r.days = (int) MiniJson.num(m, "days", 0);
            r.waiting = (int) MiniJson.num(m, "waiting", 0);
            r.invited = (int) MiniJson.num(m, "invited", 0);
            r.max = (int) MiniJson.num(m, "max", 0);
            r.pending = (int) MiniJson.num(m, "pending", 0);
            r.claimed = (int) MiniJson.num(m, "claimed", 0);
            r.early = Boolean.TRUE.equals(m.get("early"));
            Object t = m.get("tiers");
            if (t instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> tm = (Map<String, Object>) t;
                r.first = (int) MiniJson.num(tm, "first", 0);
                r.firstDays = (int) MiniJson.num(tm, "firstDays", 0);
                r.laterDays = (int) MiniJson.num(tm, "laterDays", 0);
            }
            return r;
        } catch (IOException | RuntimeException e) {
            r.ok = false;
            r.network = true;
            r.error = DOWN;
            return r;
        }
    }
}
