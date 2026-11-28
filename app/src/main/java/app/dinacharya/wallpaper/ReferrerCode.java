package app.dinacharya.wallpaper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the invite code in the install referrer that Google Play keeps from a shared link. The link sends the phone to Play with
 * {@code referrer=invite_ABCD2345}; after the install Play hands that text back to the app. Anything else (a campaign tag, nothing at
 * all, a code of another length) gives "". Uses no Android classes.
 */
public final class ReferrerCode {
    private ReferrerCode() {
    }

    private static final Pattern P = Pattern.compile("(?<![A-Za-z0-9])invite_([A-Za-z0-9]{8})(?![A-Za-z0-9])");

    /** The 8-letter code in capitals, or "". */
    public static String parse(String referrer) {
        if (referrer == null || referrer.length() > 500) return "";
        Matcher m = P.matcher(referrer);
        return m.find() ? m.group(1).toUpperCase(java.util.Locale.ROOT) : "";
    }
}
