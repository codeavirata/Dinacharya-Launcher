package app.dinacharya.wallpaper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The app's hidden space in your Google Drive (the "appDataFolder"), like WhatsApp's backup: you do
 * not see the file among your Drive files, only this app can read it, and it is there again when you
 * sign in on a new phone. Uses no Android classes.
 */
public final class CloudDrive {
    public static final String SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata";
    static final String FILES_URL = "https://www.googleapis.com/drive/v3/files";
    static final String UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files";
    static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    static final String FIELDS = "id,name,size,modifiedTime,appProperties";

    /** One file in the hidden space. */
    public static final class FileInfo {
        public final String id;
        public final String name;
        public final long size;
        /** As Google writes it, for example 2026-10-19T08:15:30.000Z. */
        public final String modified;
        public final Map<String, String> props;

        FileInfo(String id, String name, long size, String modified, Map<String, String> props) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.modified = modified == null ? "" : modified;
            this.props = props;
        }

        public String prop(String k) {
            String v = props.get(k);
            return v == null ? "" : v;
        }
    }

    /** Who is signed in. */
    public static final class Profile {
        public final String email;
        public final String name;

        Profile(String email, String name) {
            this.email = email == null ? "" : email;
            this.name = name == null ? "" : name;
        }
    }

    private final Http http;
    private final Tokens tokens;

    public CloudDrive(Http http, Tokens tokens) {
        this.http = http;
        this.tokens = tokens;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A request with the access token; asks for a fresh token once if Google says the old one expired. */
    private Http.Resp authed(String method, String url, Map<String, String> extra, byte[] body) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            Map<String, String> h = new LinkedHashMap<>();
            h.put("Authorization", "Bearer " + tokens.token(attempt > 0));
            if (extra != null) h.putAll(extra);
            Http.Resp r = http.send(method, url, h, body);
            if (r.code != 401) return r;
        }
        throw new Tokens.CloudException("Google did not accept the sign-in. Please sign in again.", 401, true);
    }

    private static Tokens.CloudException fail(String what, Http.Resp r) {
        Map<String, Object> j = MiniJson.object(r.text());
        String msg = null;
        Object e = j.get("error");
        if (e instanceof Map) {
            Object m = ((Map<?, ?>) e).get("message");
            if (m instanceof String) msg = (String) m;
        }
        boolean scope = r.code == 403 && r.text().contains("insufficient");
        return new Tokens.CloudException(what + " (" + (msg != null ? msg : "HTTP " + r.code) + ")", r.code, scope);
    }

    @SuppressWarnings("unchecked")
    private static FileInfo info(Object o) {
        if (!(o instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) o;
        String id = MiniJson.str(m, "id");
        if (id == null) return null;
        long size = 0;
        Object sz = m.get("size");
        try {
            if (sz instanceof String) size = Long.parseLong((String) sz);
            else if (sz instanceof Double) size = (long) (double) (Double) sz;
        } catch (NumberFormatException ignored) {
            size = 0;
        }
        Map<String, String> props = new LinkedHashMap<>();
        Object ap = m.get("appProperties");
        if (ap instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) ap).entrySet()) {
                if (e.getValue() instanceof String) props.put(String.valueOf(e.getKey()), (String) e.getValue());
            }
        }
        return new FileInfo(id, MiniJson.str(m, "name"), size, MiniJson.str(m, "modifiedTime"), props);
    }

    /** Files in the hidden space with this name, newest first. */
    public List<FileInfo> find(String name) throws IOException {
        String q = "name = '" + name.replace("'", "\\'") + "' and trashed = false";
        String url = FILES_URL + "?spaces=appDataFolder&orderBy=" + enc("modifiedTime desc") + "&pageSize=20&q=" + enc(q)
                + "&fields=" + enc("files(" + FIELDS + ")");
        Http.Resp r = authed("GET", url, null, null);
        if (r.code != 200) throw fail("Could not look for the backup", r);
        List<FileInfo> out = new ArrayList<>();
        Object files = MiniJson.object(r.text()).get("files");
        if (files instanceof List) {
            for (Object o : (List<?>) files) {
                FileInfo fi = info(o);
                if (fi != null) out.add(fi);
            }
        }
        return out;
    }

    /** The newest file with this name, or null. */
    public FileInfo latest(String name) throws IOException {
        List<FileInfo> l = find(name);
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Uploads {@code data}: replaces the file {@code existingId} when given (so Drive keeps one backup,
     * like WhatsApp), or makes a new one in the hidden space.
     */
    public FileInfo upload(String name, String mime, byte[] data, String existingId, Map<String, String> props) throws IOException {
        StringBuilder meta = new StringBuilder("{\"name\":").append(MiniJson.quote(name));
        if (existingId == null) meta.append(",\"parents\":[\"appDataFolder\"]");
        if (props != null && !props.isEmpty()) {
            meta.append(",\"appProperties\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (!first) meta.append(',');
                meta.append(MiniJson.quote(e.getKey())).append(':').append(MiniJson.quote(e.getValue()));
                first = false;
            }
            meta.append('}');
        }
        meta.append('}');
        String boundary = "dinacharya" + Long.toHexString(Double.doubleToLongBits(Math.random()));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta + "\r\n--" + boundary
                + "\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bo.write(data);
        bo.write(("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "multipart/related; boundary=" + boundary);
        String url;
        if (existingId != null) {
            // Android's HTTP client cannot send PATCH; Google accepts POST with this header instead.
            h.put("X-HTTP-Method-Override", "PATCH");
            url = UPLOAD_URL + "/" + enc(existingId) + "?uploadType=multipart&fields=" + enc(FIELDS);
        } else {
            url = UPLOAD_URL + "?uploadType=multipart&fields=" + enc(FIELDS);
        }
        Http.Resp r = authed("POST", url, h, bo.toByteArray());
        if (r.code == 404 && existingId != null) return upload(name, mime, data, null, props);   // deleted meanwhile: make a new one
        if (r.code / 100 != 2) throw fail("Upload failed", r);
        FileInfo fi = info(MiniJson.object(r.text()));
        if (fi == null) throw new Tokens.CloudException("Upload failed (no file id)", r.code, false);
        return fi;
    }

    public byte[] download(String id) throws IOException {
        String url = FILES_URL + "/" + enc(id) + "?alt=media";
        Http.Resp r = authed("GET", url, null, null);
        // Drive sometimes marks a file as "malware or spam" by mistake (a zip of text and settings can trip it). The backup is this app's own
        // file in its hidden space, and Drive allows its owner to download it anyway when the call says so, so ask again once, only for that answer.
        if (r.code == 403 && r.text().contains("cannotDownloadAbusiveFile")) r = authed("GET", url + "&acknowledgeAbuse=true", null, null);
        if (r.code != 200) throw fail("Download failed", r);
        return r.body;
    }

    public void delete(String id) throws IOException {
        Http.Resp r = authed("DELETE", FILES_URL + "/" + enc(id), null, null);
        if (r.code / 100 != 2 && r.code != 404) throw fail("Could not delete the old backup", r);
    }

    /** The name and email of the signed-in account. */
    public Profile me() throws IOException {
        Http.Resp r = authed("GET", USERINFO_URL, null, null);
        if (r.code != 200) throw fail("Could not read your Google account", r);
        Map<String, Object> j = MiniJson.object(r.text());
        return new Profile(MiniJson.str(j, "email"), MiniJson.str(j, "name"));
    }

    /** Withdraws a key at Google (used when signing out). Never throws. */
    public static void revoke(Http http, String token) {
        if (token == null || token.isEmpty()) return;
        try {
            Map<String, String> h = new LinkedHashMap<>();
            h.put("Content-Type", "application/x-www-form-urlencoded");
            http.send("POST", REVOKE_URL, h, ("token=" + enc(token)).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            // signing out locally is what matters
        }
    }
}
