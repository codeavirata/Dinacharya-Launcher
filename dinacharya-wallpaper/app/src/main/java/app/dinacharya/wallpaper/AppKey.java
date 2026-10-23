package app.dinacharya.wallpaper;

/**
 * Names one launchable app: its package, its main screen and the profile (personal or work) it lives in,
 * as one piece of text such as {@code com.whatsapp/com.whatsapp.Main@0}. Uses no Android classes.
 *
 * A key can also name a built-in action instead of an app, such as {@link #DASHBOARD}.
 */
public final class AppKey {
    /** Opens the Dinacharya app itself (the routine, insights and settings). */
    public static final String DASHBOARD = "act:dashboard";
    /** Opens the phone's camera. */
    public static final String CAMERA = "act:camera";
    /** Opens the phone's dialer. */
    public static final String PHONE = "act:phone";

    private AppKey() {
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replace('@', '_').trim();
    }

    public static String make(String pkg, String cls, long user) {
        return clean(pkg) + "/" + clean(cls) + "@" + user;
    }

    public static boolean isAction(String key) {
        return key != null && key.startsWith("act:");
    }

    public static String pkg(String key) {
        if (key == null || isAction(key)) return "";
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : "";
    }

    public static String cls(String key) {
        if (key == null || isAction(key)) return "";
        int slash = key.indexOf('/');
        int at = key.lastIndexOf('@');
        if (slash < 0) return "";
        return at > slash ? key.substring(slash + 1, at) : key.substring(slash + 1);
    }

    public static long user(String key) {
        if (key == null) return 0;
        int at = key.lastIndexOf('@');
        if (at < 0 || at == key.length() - 1) return 0;
        try {
            return Long.parseLong(key.substring(at + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** What a built-in action is called on screen, or "" for an app key. */
    public static String actionName(String key) {
        if (DASHBOARD.equals(key)) return "Dinacharya routine";
        if (CAMERA.equals(key)) return "Camera";
        if (PHONE.equals(key)) return "Phone";
        return "";
    }

    /** True for a well-formed app key or one of the built-in actions. */
    public static boolean valid(String key) {
        if (key == null) return false;
        if (isAction(key)) return !actionName(key).isEmpty();
        return !pkg(key).isEmpty() && !cls(key).isEmpty();
    }
}
