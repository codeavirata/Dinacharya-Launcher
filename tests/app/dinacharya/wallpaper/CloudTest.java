package app.dinacharya.wallpaper;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class CloudTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static class Kv implements DeviceAuth.Kv {
        Map<String, String> m = new HashMap<>();
        public String get(String k) { return m.get(k); }
        public void put(String k, String v) { m.put(k, v); }
    }

    /** A pretend Google with an appDataFolder. */
    static class Fake implements Http {
        List<String> log = new ArrayList<>();
        int pending = 2;
        boolean expireOnce = false, deadRefresh = false, offline = false, flagged = false;
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> modified = new LinkedHashMap<>();
        Map<String, String> propsJson = new LinkedHashMap<>();
        int ids = 0, tick = 0;
        String lastMeta = "";
        Map<String, String> lastHeaders = new HashMap<>();

        public Resp send(String method, String url, Map<String, String> h, byte[] body) throws IOException {
            if (offline) throw new IOException("no network");
            log.add(method + " " + url.replaceAll("\\?.*", ""));
            lastHeaders = h;
            String b = body == null ? "" : new String(body, StandardCharsets.ISO_8859_1);
            if (url.startsWith(DeviceAuth.DEVICE_URL)) {
                String d = URLDecoder.decode(b, "UTF-8");
                if (!d.contains("client_id=CID") || !d.contains("scope=openid email profile https://www.googleapis.com/auth/drive.appdata")) return r(400, "{\"error\":\"invalid_scope\"}");
                return r(200, "{\"device_code\":\"DEV\",\"user_code\":\"ABCD-EFGH\",\"verification_url\":\"https://www.google.com/device\",\"expires_in\":1800,\"interval\":5}");
            }
            if (url.startsWith(DeviceAuth.TOKEN_URL)) {
                if (b.contains("device_code")) {
                    if (!b.contains("client_secret=SEC")) return r(400, "{\"error\":\"invalid_client\"}");
                    if (pending-- > 0) return r(428, "{\"error\":\"authorization_pending\"}");
                    return r(200, "{\"access_token\":\"AT1\",\"expires_in\":3599,\"refresh_token\":\"RT\"}");
                }
                if (b.contains("grant_type=refresh_token")) {
                    if (deadRefresh) return r(400, "{\"error\":\"invalid_grant\"}");
                    return r(200, "{\"access_token\":\"AT2\",\"expires_in\":3599}");
                }
            }
            if (url.startsWith(CloudDrive.REVOKE_URL)) return r(200, "{}");
            String auth = h.get("Authorization");
            if (auth == null) return r(401, "{}");
            if (expireOnce && auth.equals("Bearer AT1")) { expireOnce = false; return r(401, "{\"error\":{\"code\":401}}"); }
            if (url.startsWith(CloudDrive.USERINFO_URL)) return r(200, "{\"email\":\"amit@example.com\",\"name\":\"Amit Singh\"}");
            String path = url.replaceAll("\\?.*", "");
            String q = URLDecoder.decode(url, "UTF-8");
            if (path.equals(CloudDrive.FILES_URL) && method.equals("GET")) {
                if (!q.contains("spaces=appDataFolder") || !q.contains("name = 'dinacharya-backup.zip'")) return r(400, "{}");
                StringBuilder sb = new StringBuilder("{\"files\":[");
                List<String> order = new ArrayList<>(names.keySet());
                order.sort((x, y) -> modified.get(y).compareTo(modified.get(x)));
                boolean first = true;
                for (String id : order) {
                    if (!first) sb.append(',');
                    sb.append("{\"id\":\"").append(id).append("\",\"name\":\"").append(names.get(id)).append("\",\"size\":\"")
                      .append(files.get(id).length).append("\",\"modifiedTime\":\"").append(modified.get(id)).append("\",\"appProperties\":")
                      .append(propsJson.getOrDefault(id, "{}")).append('}');
                    first = false;
                }
                return r(200, sb.append("]}").toString());
            }
            if (path.startsWith(CloudDrive.UPLOAD_URL) && method.equals("POST")) {
                String ct = h.get("Content-Type");
                String boundary = ct.substring(ct.indexOf("boundary=") + 9);
                String[] parts = b.split("--" + boundary);
                String meta = parts[1].substring(parts[1].indexOf("\r\n\r\n") + 4).trim();
                String dataPart = parts[2];
                byte[] data = dataPart.substring(dataPart.indexOf("\r\n\r\n") + 4, dataPart.length() - 2).getBytes(StandardCharsets.ISO_8859_1);
                lastMeta = meta;
                Map<String, Object> mj = MiniJson.object(meta);
                String id;
                if (path.equals(CloudDrive.UPLOAD_URL)) {
                    if (!meta.contains("\"parents\":[\"appDataFolder\"]")) return r(400, "{}");
                    id = "F" + (++ids);
                } else {
                    if (!"PATCH".equals(h.get("X-HTTP-Method-Override"))) return r(400, "{}");
                    id = path.substring(path.lastIndexOf('/') + 1);
                    if (!names.containsKey(id)) return r(404, "{}");
                }
                names.put(id, MiniJson.str(mj, "name"));
                files.put(id, data);
                modified.put(id, "2026-10-19T10:" + String.format("%02d", ++tick) + ":00.000Z");
                int a = meta.indexOf("\"appProperties\":");
                if (a >= 0) propsJson.put(id, meta.substring(a + 16, meta.length() - 1));
                return r(200, "{\"id\":\"" + id + "\",\"name\":\"" + names.get(id) + "\",\"size\":\"" + data.length + "\",\"modifiedTime\":\"" + modified.get(id) + "\"}");
            }
            if (path.startsWith(CloudDrive.FILES_URL + "/") && method.equals("GET") && url.contains("alt=media")) {
                String id = path.substring(path.lastIndexOf('/') + 1);
                if (flagged && !url.contains("acknowledgeAbuse=true")) return r(403, "{\"error\":{\"code\":403,\"message\":\"This file has been identified as malware or spam and cannot be downloaded.\",\"errors\":[{\"reason\":\"cannotDownloadAbusiveFile\"}]}}");
                return files.containsKey(id) ? new Resp(200, files.get(id)) : r(404, "{}");
            }
            if (path.startsWith(CloudDrive.FILES_URL + "/") && method.equals("DELETE")) {
                String id = path.substring(path.lastIndexOf('/') + 1);
                names.remove(id); files.remove(id); modified.remove(id);
                return r(204, "");
            }
            return r(404, "{}");
        }
        static Resp r(int c, String s) { return new Resp(c, s.getBytes(StandardCharsets.UTF_8)); }
    }

    static Map<String, byte[]> sampleFiles() {
        Map<String, byte[]> f = new LinkedHashMap<>();
        Routine r = Routine.sample();
        r.addOther("Weekend", r);
        r.dayUse[6] = 1;
        r.blocks.get(3).note = "polity ch 4\nmaps";
        f.put("prefs/routine.txt", r.serialize().getBytes(StandardCharsets.UTF_8));
        f.put("prefs/overrides.txt", "x".getBytes(StandardCharsets.UTF_8));
        f.put("history/events.log", "1\tACK\n".getBytes(StandardCharsets.UTF_8));
        f.put("history/reports/2026-10-18.txt", "DINAREPORT1\nday=2026-10-18\n".getBytes(StandardCharsets.UTF_8));
        byte[] big = new byte[300000];
        new Random(1).nextBytes(big);
        f.put("history/reports/blob.bin", big);
        return f;
    }

    public static void main(String[] a) throws Exception {
        // ---- MiniJson basics still hold
        Map<String, Object> j = MiniJson.object("{\"a\":\"x\\ny\\u00e9\",\"n\":12.5,\"l\":[1,{\"k\":\"v\"}]}");
        ok("json", "x\nyé".equals(MiniJson.str(j, "a")) && MiniJson.num(j, "n", 0) == 12 && ((List<?>) j.get("l")).size() == 2);

        // ---- code sign-in
        Fake g = new Fake(); Kv kv = new Kv();
        long[] now = {1_000_000L};
        DeviceAuth da = new DeviceAuth(g, kv, () -> now[0], "CID", "SEC");
        ok("not configured without an id", !new DeviceAuth(g, new Kv(), () -> 0, "", "").configured());
        DeviceAuth.DeviceCode dc = da.start();
        ok("code shown", dc.userCode.equals("ABCD-EFGH") && dc.intervalSec == 5);
        ok("pending, pending, done", da.poll(dc) == DeviceAuth.Poll.PENDING && da.poll(dc) == DeviceAuth.Poll.PENDING && da.poll(dc) == DeviceAuth.Poll.DONE);
        ok("signed in, token cached", da.signedIn() && da.token(false).equals("AT1"));
        now[0] += 3_600_000L;
        ok("expired token renewed", da.token(false).equals("AT2"));

        // ---- Drive hidden space
        Fake g2 = new Fake(); Kv kv2 = new Kv();
        kv2.put("refresh", "RT"); kv2.put("access", "AT1"); kv2.put("expiry", String.valueOf(Long.MAX_VALUE));
        DeviceAuth auth = new DeviceAuth(g2, kv2, () -> 0L, "CID", "SEC");
        CloudDrive d = new CloudDrive(g2, auth);
        ok("who am I", d.me().email.equals("amit@example.com") && d.me().name.equals("Amit Singh"));
        ok("no backup yet", CloudBackup.find(d) == null);

        Map<String, String> info = new LinkedHashMap<>();
        info.put("created", "1760860000000"); info.put("device", "Pixel 8"); info.put("days", "12"); info.put("app", "2.0");
        CloudBackup.Result res = CloudBackup.backUp(d, sampleFiles(), info);
        ok("first backup made in appDataFolder", res.file.id.equals("F1") && g2.names.size() == 1 && g2.lastMeta.contains("appDataFolder"));
        ok("backup size reported", res.bytes > 1000 && res.file.size == res.bytes);
        ok("facts stored with the file", g2.lastMeta.contains("\"device\":\"Pixel 8\"") && g2.lastMeta.contains("\"days\":\"12\""));

        g2.expireOnce = true;
        CloudBackup.Result res2 = CloudBackup.backUp(d, sampleFiles(), info);
        ok("second backup replaces the first (same file, PATCH)", res2.file.id.equals("F1") && g2.names.size() == 1);
        ok("expired key renewed once during backup", g2.log.contains("POST " + DeviceAuth.TOKEN_URL));

        // a stray duplicate gets cleaned up
        g2.names.put("F9", BackupArchive.NAME); g2.files.put("F9", new byte[]{1}); g2.modified.put("F9", "2026-10-01T00:00:00.000Z");
        CloudBackup.backUp(d, sampleFiles(), info);
        ok("extra copies removed, one backup left", g2.names.size() == 1 && g2.names.containsKey("F1"));

        // ---- restore
        CloudDrive.FileInfo found = CloudBackup.find(d);
        ok("found with its facts", found != null && found.prop("device").equals("Pixel 8") && found.size > 0 && found.modified.startsWith("2026-10-19"));
        BackupArchive.Contents c = CloudBackup.fetch(d, found);
        Map<String, byte[]> orig = sampleFiles();
        boolean same = c.files.size() == orig.size();
        for (Map.Entry<String, byte[]> e : orig.entrySet()) same &= Arrays.equals(e.getValue(), c.files.get(e.getKey()));
        ok("restore gives back every file byte for byte", same);
        ok("manifest read", c.format() == 1 && c.created() == 1760860000000L && "Pixel 8".equals(c.info.get("device")));
        Routine back = Routine.parse(new String(c.files.get("prefs/routine.txt"), StandardCharsets.UTF_8));
        ok("restored routine keeps weekend and notes", back.others.size() == 1 && back.dayUse[6] == 1 && back.blocks.get(3).note.equals("polity ch 4\nmaps"));

        // Drive marks the backup as "malware or spam" by mistake: restore still works (asks again with acknowledgeAbuse), and other 403s still fail
        g2.flagged = true; g2.log.clear();
        BackupArchive.Contents cf = CloudBackup.fetch(d, found);
        ok("flagged file: restore still gives back every file", cf.files.size() == orig.size() && Arrays.equals(orig.get("prefs/routine.txt"), cf.files.get("prefs/routine.txt")));
        ok("flagged file: asked twice, no more", g2.log.stream().filter(x -> x.contains("/files/F1")).count() == 2);
        g2.flagged = false;

        // ---- sign-in problems
        Fake g3 = new Fake(); Kv kv3 = new Kv(); kv3.put("refresh", "RT"); g3.deadRefresh = true;
        try { new CloudDrive(g3, new DeviceAuth(g3, kv3, () -> 0L, "CID", "SEC")).me(); ok("revoked sign-in is reported", false); }
        catch (Tokens.CloudException e) { ok("revoked sign-in is reported as 'sign in again'", e.signInExpired); }
        Fake g4 = new Fake(); g4.offline = true;
        try { new CloudDrive(g4, auth).find(BackupArchive.NAME); ok("offline fails", false); }
        catch (IOException e) { ok("offline is an IOException, not a crash", !(e instanceof Tokens.CloudException)); }

        // ---- archive safety
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            z.putNextEntry(new ZipEntry("manifest.txt")); z.write("format=1\n".getBytes()); z.closeEntry();
            z.putNextEntry(new ZipEntry("prefs/routine.txt")); z.write(Routine.sample().serialize().getBytes()); z.closeEntry();
            z.putNextEntry(new ZipEntry("history/../../evil.txt")); z.write("x".getBytes()); z.closeEntry();
        }
        try { BackupArchive.unpack(bo.toByteArray()); ok("path escape rejected", false); } catch (IOException e) { ok("path escape rejected", true); }
        bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            z.putNextEntry(new ZipEntry("manifest.txt")); z.write("format=9\n".getBytes()); z.closeEntry();
            z.putNextEntry(new ZipEntry("prefs/routine.txt")); z.write(Routine.sample().serialize().getBytes()); z.closeEntry();
        }
        try { BackupArchive.unpack(bo.toByteArray()); ok("newer format refused", false); } catch (IOException e) { ok("newer format refused with a clear message", e.getMessage().contains("newer")); }
        try { BackupArchive.unpack("hello".getBytes()); ok("junk refused", false); } catch (IOException e) { ok("junk refused", true); }
        try { BackupArchive.pack(Collections.singletonMap("/etc/x", new byte[1]), info); ok("pack refuses odd paths", false); } catch (IOException e) { ok("pack refuses odd paths", true); }

        // ---- when backups are due
        long H = 3600000L, D = 24 * H, t0 = 1_760_000_000_000L;
        ok("never / manual: not automatic", !BackupPolicy.due(BackupPolicy.NEVER, 0, t0) && !BackupPolicy.due(BackupPolicy.MANUAL, 0, t0));
        ok("daily: first time due", BackupPolicy.due(BackupPolicy.DAILY, 0, t0));
        ok("daily: not after 10 h, due after 21 h", !BackupPolicy.due(BackupPolicy.DAILY, t0, t0 + 10 * H) && BackupPolicy.due(BackupPolicy.DAILY, t0, t0 + 21 * H));
        ok("weekly: due after 7 days, not after 5", BackupPolicy.due(BackupPolicy.WEEKLY, t0, t0 + 7 * D) && !BackupPolicy.due(BackupPolicy.WEEKLY, t0, t0 + 5 * D));
        ok("monthly: due after 30 days", BackupPolicy.due(BackupPolicy.MONTHLY, t0, t0 + 30 * D) && !BackupPolicy.due(BackupPolicy.MONTHLY, t0, t0 + 20 * D));
        ok("clock set back: back up", BackupPolicy.due(BackupPolicy.DAILY, t0, t0 - H));
        ok("sizes", BackupPolicy.size(900).equals("900 B") && BackupPolicy.size(48 * 1024).equals("48 KB") && BackupPolicy.size(1300000).equals("1.2 MB"));

        System.out.println(fails == 0 ? "ALL PASS" : "FAILS " + fails);
    }
}
