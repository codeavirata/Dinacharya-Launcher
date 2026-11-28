package app.dinacharya.wallpaper;

/** The invite code that comes back from Google Play after an install through a shared link. */
public class ReferrerCodeTest {
    static int fails = 0;
    static void ok(String n, boolean v) { System.out.println((v ? "PASS " : "FAIL ") + n); if (!v) fails++; }

    public static void main(String[] args) {
        ok("the plain referrer", ReferrerCode.parse("invite_ABCD2345").equals("ABCD2345"));
        ok("small letters are made capitals", ReferrerCode.parse("invite_abcd2345").equals("ABCD2345"));
        ok("with a campaign tag before it", ReferrerCode.parse("utm_source=whatsapp&utm_medium=share&invite_ABCD2345").equals("ABCD2345"));
        ok("with a tag after it", ReferrerCode.parse("invite_ABCD2345&utm_campaign=x").equals("ABCD2345"));
        ok("nothing", ReferrerCode.parse(null).isEmpty() && ReferrerCode.parse("").isEmpty());
        ok("another referrer", ReferrerCode.parse("utm_source=google-play&utm_medium=organic").isEmpty());
        ok("a code that is too short", ReferrerCode.parse("invite_ABC").isEmpty());
        ok("a code that is too long", ReferrerCode.parse("invite_ABCD23456").isEmpty());
        ok("a code inside a longer word is not taken", ReferrerCode.parse("xinvite_ABCD2345").isEmpty());
        ok("a code with a bad letter", ReferrerCode.parse("invite_ABCD-345").isEmpty());
        ok("a huge text is refused", ReferrerCode.parse("a".repeat(600) + "&invite_ABCD2345").isEmpty());
        ok("the capitals do not depend on the phone's language", ReferrerCode.parse("invite_ijklmnop").equals("IJKLMNOP"));
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILS");
        if (fails > 0) System.exit(1);
    }
}
