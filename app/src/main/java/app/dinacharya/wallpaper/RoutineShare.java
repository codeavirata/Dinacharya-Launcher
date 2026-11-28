package app.dinacharya.wallpaper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Sharing a routine as text that survives chat apps: the slots of one routine (its name, start time and slots, not the look, goals or
 * settings) are packed into one line of letters and digits that starts with "DNC1:". The message around it says how to use it. Reading
 * finds that line anywhere in a pasted message, and also still reads the plain routine text of the web app. Uses no Android classes.
 */
public final class RoutineShare {
    static final String PREFIX = "DNC1:";
    private static final Pattern CODE = Pattern.compile("DNC1:([A-Za-z0-9_-]{8,40000})");
    /** A shared routine is never this big once unpacked (a packed file that unpacks to more is refused). */
    private static final int MAX_TEXT = 200_000;
    public static final String PLAY = "https://play.google.com/store/apps/details?id=app.dinacharya.launcher";

    private RoutineShare() {
    }

    /** One line for the slots of {@code r}. */
    public static String code(Routine r) {
        Routine copy = new Routine();
        copy.name = r.name;
        copy.copyScheduleFrom(r);
        byte[] raw = copy.serialize().getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** The message to send around the code. */
    public static String message(Routine r) {
        return "My daily routine “" + r.name + "” on Dinacharya Launcher.\n"
                + "To use it: copy this whole message, open Dinacharya > Routine > More tools > Paste a routine.\n\n"
                + code(r) + "\n\nGet the app: " + PLAY;
    }

    /** The routine in a pasted message (a shared code, or the web app's plain routine text), or null when there is none. */
    public static Routine read(String text) {
        if (text == null) return null;
        try {
            Matcher m = CODE.matcher(text);
            if (m.find()) {
                byte[] packed = Base64.getUrlDecoder().decode(m.group(1));
                Inflater inf = new Inflater();
                inf.setInput(packed);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                try {
                    while (!inf.finished()) {
                        int n = inf.inflate(buf);
                        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) return null;
                        out.write(buf, 0, n);
                        if (out.size() > MAX_TEXT) return null;
                    }
                } catch (DataFormatException e) {
                    return null;
                } finally {
                    inf.end();
                }
                return Routine.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
            int at = text.indexOf("DINACHARYA1");
            return at < 0 ? null : Routine.parse(text.substring(at));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return null;
        }
    }
}
