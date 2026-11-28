package app.dinacharya.wallpaper;

import java.io.IOException;
import java.util.Map;

public class InviteTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    static class Fake implements Http {
        int code = 200; String body = "{}"; boolean down; String lastUrl, lastBody;
        public Resp send(String m, String url, Map<String, String> h, byte[] b) throws IOException {
            lastUrl = url; lastBody = b == null ? "" : new String(b);
            if (down) throw new IOException("no route");
            return new Resp(code, body.getBytes());
        }
    }

    public static void main(String[] a) {
        Fake f = new Fake();
        InviteClient c = new InviteClient(f, "https://x.test");
        f.body = "{\"ok\":true,\"code\":\"ABCD2345\"}";
        InviteClient.Result r = c.code("device-000000000001");
        ok("code is read", r.ok && "ABCD2345".equals(r.code) && f.lastUrl.equals("https://x.test/v1/code"));
        ok("the device id is sent", f.lastBody.contains("\"device\":\"device-000000000001\""));
        f.body = "{\"ok\":true,\"days\":7}";
        r = c.redeem("device-000000000001", "abcd-2345");
        ok("redeem sends the code and reads the days", r.ok && r.days == 7 && f.lastBody.contains("\"code\":\"abcd-2345\""));
        f.code = 400; f.body = "{\"ok\":false,\"error\":\"That code is not valid.\"}";
        r = c.redeem("device-000000000001", "x");
        ok("a refusal shows the server's sentence and is not a network failure", !r.ok && !r.network && r.error.equals("That code is not valid."));
        f.code = 200; f.body = "{\"ok\":true,\"waiting\":2,\"days\":14,\"invited\":5,\"max\":10,\"tiers\":{\"first\":4,\"firstDays\":7,\"laterDays\":3}}";
        r = c.status("device-000000000001");
        ok("status reads waiting, days and the tier schedule", r.ok && r.waiting == 2 && r.days == 14 && r.invited == 5 && r.max == 10 && r.first == 4 && r.firstDays == 7 && r.laterDays == 3);
        f.body = "{\"ok\":true,\"claimed\":2,\"days\":14}";
        r = c.claim("device-000000000001");
        ok("claim reads the paid days", r.ok && r.claimed == 2 && r.days == 14);
        f.down = true;
        r = c.status("device-000000000001");
        ok("a server that cannot be reached gives a short message, no crash", !r.ok && r.network && r.error.equals(InviteClient.DOWN));
        f.down = false; f.code = 503; f.body = "<html>oops</html>";
        r = c.code("device-000000000001");
        ok("a server error page is treated as down", !r.ok && r.network);
        f.code = 200; f.body = "";
        ok("an empty answer is treated as down", c.code("device-000000000001").network);

        // check: the app asks before it offers a code
        f.code = 200; f.body = "{\"ok\":true}";
        r = c.check("device-000000000001", "ABCD2345");
        ok("check posts to /v1/check with the code and reads yes", r.ok && f.lastUrl.equals("https://x.test/v1/check") && f.lastBody.contains("\"code\":\"ABCD2345\"") && InviteCheck.verdict(r) == InviteCheck.SHOW);
        f.code = 400; f.body = "{\"ok\":false,\"error\":\"That code is not valid.\"}";
        r = c.check("device-000000000001", "ZZZZZZZZ");
        ok("a refused code is dropped for good", !r.ok && !r.network && !r.limited && InviteCheck.verdict(r) == InviteCheck.DROP);
        f.body = "{\"ok\":false,\"error\":\"You cannot use your own code.\"}";
        ok("your own code is dropped too", InviteCheck.verdict(c.check("device-000000000001", "ABCD2345")) == InviteCheck.DROP);
        f.body = "{\"ok\":false,\"error\":\"This phone has already used an invite code.\"}";
        ok("a phone that already used a code gets no offer", InviteCheck.verdict(c.check("device-000000000001", "ABCD2345")) == InviteCheck.DROP);
        f.code = 429; f.body = "{\"ok\":false,\"error\":\"Too many tries. Please wait a minute and try again.\"}";
        r = c.check("device-000000000001", "ABCD2345");
        ok("too many tries says nothing about the code: it is asked again later", r.limited && !r.network && r.error.startsWith("Too many tries") && InviteCheck.verdict(r) == InviteCheck.LATER);
        f.down = true;
        ok("a server that cannot be reached keeps the code for later", InviteCheck.verdict(c.check("device-000000000001", "ABCD2345")) == InviteCheck.LATER);
        f.down = false; f.code = 503; f.body = "<html>oops</html>";
        ok("a server error page keeps the code for later", InviteCheck.verdict(c.check("device-000000000001", "ABCD2345")) == InviteCheck.LATER);
        ok("no answer at all keeps the code for later", InviteCheck.verdict(null) == InviteCheck.LATER);
        f.code = 200;
        ok("a 429 is only 'limited' for 429, not for other refusals", !c.redeem("device-000000000001", "x").limited);
        long t0 = 1_700_000_000_000L;
        ok("the first ask is allowed", InviteCheck.canTry(0, t0));
        ok("not asked again within ten minutes", !InviteCheck.canTry(t0, t0 + 9 * 60000L));
        ok("asked again after ten minutes", InviteCheck.canTry(t0, t0 + 10 * 60000L));
        ok("a clock that went back does not block asking", InviteCheck.canTry(t0, t0 - 5000L));

        // active: "too early" is not the same as "used"
        f.code = 200; f.body = "{\"ok\":true,\"activated\":0,\"early\":true}";
        r = c.active("device-000000000001");
        ok("early is read and is not counted as told", r.ok && r.early && !InviteClient.released(r));
        f.body = "{\"ok\":true,\"activated\":1}";
        r = c.active("device-000000000001");
        ok("a released reward counts as told", r.ok && !r.early && InviteClient.released(r));
        f.body = "{\"ok\":true,\"activated\":0}";
        ok("a repeat after the reward was released counts as told (nothing more to wait for)", InviteClient.released(c.active("device-000000000001")));
        f.down = true;
        ok("an unreachable server never counts as told", !InviteClient.released(c.active("device-000000000001")) && !InviteClient.released(null));
        f.down = false; f.code = 400; f.body = "{\"ok\":false,\"error\":\"That did not work.\"}";
        ok("a refusal never counts as told", !InviteClient.released(c.active("device-000000000001")));
        f.code = 200;

        long now = 1_000_000_000_000L, D = InviteDays.DAY_MS;
        ok("first days start from now", InviteDays.add(0, 7, now) == now + 7 * D);
        ok("days add up after the end date kept", InviteDays.add(now + 3 * D, 7, now) == now + 10 * D);
        ok("an old end date starts again from now", InviteDays.add(now - 5 * D, 7, now) == now + 7 * D);
        ok("never more than the cap ahead", InviteDays.add(now + 58 * D, 7, now) == now + InviteDays.MAX_DAYS * D);
        ok("a bad number of days adds nothing", InviteDays.add(0, -3, now) == 0 && InviteDays.add(now + 2 * D, 0, now) == now + 2 * D && InviteDays.add(0, 9999, now) == 0);
        ok("active only inside the window", InviteDays.active(now + D, now) && !InviteDays.active(now, now) && !InviteDays.active(now - D, now));
        ok("a date set by hand far ahead is not trusted", !InviteDays.active(now + 400 * D, now));
        ok("days left rounds up", InviteDays.left(now + 3 * D - 1, now) == 3 && InviteDays.left(now + 1, now) == 1 && InviteDays.left(0, now) == 0);
        String st = InviteActive.redeemed(20000);
        ok("the day a code is used counts as one day, not enough yet", !InviteActive.due(st));
        ok("the same day again counts nothing", !InviteActive.due(InviteActive.used(st, 20000)));
        st = InviteActive.used(st, 20001);
        ok("a second day of use makes it due", InviteActive.due(st));
        st = InviteActive.told(st);
        ok("once told it is never due again", !InviteActive.due(st) && !InviteActive.due(InviteActive.used(st, 20005)));
        ok("a clock that went back adds nothing", !InviteActive.due(InviteActive.used(InviteActive.redeemed(20000), 19990)));
        ok("an empty or broken record is never due and never crashes", !InviteActive.due("") && !InviteActive.due(null) && !InviteActive.due("a,b") && InviteActive.used("", 5).isEmpty() && InviteActive.used("x,y,z,w", 5).equals("x,y,z,w"));
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
    }
}
