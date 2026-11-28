package app.dinacharya.wallpaper;

/**
 * What to do with a friend's code from an install link after the server was asked whether it can be used ({@link InviteClient#check}).
 * Only a code the server accepts is offered. A code it refuses (not a real code, the phone's own code, a phone that already used one) is
 * dropped for good, so a made-up link shows nothing. When the server cannot be reached, or says "too many tries", nothing is decided:
 * the code is kept and asked about again later. Uses no Android classes.
 */
public final class InviteCheck {
    private InviteCheck() {
    }

    /** Show the offer sheet. */
    public static final int SHOW = 1;
    /** Nothing is known yet: keep the code, show nothing, ask again later. */
    public static final int LATER = 2;
    /** The server refused the code: forget it and show nothing. */
    public static final int DROP = 3;

    /** How long to wait before asking the server again after it could not be reached. */
    public static final long RETRY_MS = 10L * 60000L;

    public static int verdict(InviteClient.Result r) {
        if (r == null || r.network || r.limited) return LATER;
        return r.ok ? SHOW : DROP;
    }

    /** True when it is time to ask again: nothing was tried yet, the last try was long enough ago, or the clock went back. */
    public static boolean canTry(long lastTry, long now) {
        return lastTry <= 0 || now < lastTry || now - lastTry >= RETRY_MS;
    }
}
