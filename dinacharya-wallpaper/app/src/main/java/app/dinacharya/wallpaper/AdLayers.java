package app.dinacharya.wallpaper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The ad layers for the rewarded video: the same kind of ad asked for from several ad units in turn, the best paying one first.
 *
 * In AdMob each unit gets its own minimum price (eCPM floor): the top unit a high one, the next a lower one, the last none.
 * An ad is asked for from the top unit; if nobody pays that much, the next one is asked, and so on, so every video earns
 * as much as it can and a video is still found when the best price is not on offer. A unit that had nothing is left alone for
 * a while ({@link #failed}) so the next video does not wait for it again. The rules only: Android does the asking (see
 * {@link VideoPass}). Uses no Android classes.
 */
public final class AdLayers {
    /** The codes Google's ads library reports (the numbers are theirs). */
    public static final int CODE_INTERNAL = 0, CODE_INVALID_REQUEST = 1, CODE_NETWORK = 2, CODE_NO_FILL = 3;
    /** How long a unit is left alone after it failed: nothing to show, no network, a mistake in the id, anything else. */
    public static final long NO_FILL_MS = 90000L, NETWORK_MS = 20000L, INVALID_MS = 30L * 60000L, OTHER_MS = 5L * 60000L;
    /** A unit that keeps having nothing is left alone for longer each time, up to this. */
    public static final long MAX_BACKOFF_MS = 8L * 60000L;
    /** A loaded ad is used within this time (Google drops rewarded ads that are left for about an hour). */
    public static final long FRESH_MS = 50L * 60000L;
    /** The most videos one person is asked to watch in a day: more than this is a sign something is wrong, and Pro is the better deal. */
    public static final int DAILY_CAP = 12;

    private final List<String> ids = new ArrayList<>();
    private final long[] skipUntil;
    private final int[] streak;

    /** @param units the ad unit ids, the best paying first; blanks and repeats are dropped */
    public AdLayers(List<String> units) {
        if (units != null) {
            for (String u : units) {
                String t = u == null ? "" : u.trim();
                if (!t.isEmpty() && !ids.contains(t)) ids.add(t);
            }
        }
        skipUntil = new long[ids.size()];
        streak = new int[ids.size()];
    }

    public int size() {
        return ids.size();
    }

    public String id(int layer) {
        return layer >= 0 && layer < ids.size() ? ids.get(layer) : "";
    }

    /** True while the layer is left alone because of an earlier failure. */
    public boolean backingOff(int layer, long now) {
        return layer >= 0 && layer < ids.size() && now < skipUntil[layer];
    }

    /**
     * The layers to ask now, in order: the best paying first, the ones that are being left alone left out. When every layer
     * is being left alone, the last (the one with no price floor, the likeliest to have an ad) is asked anyway, so a video is
     * never refused only because of earlier failures.
     */
    public int[] plan(long now) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) if (!backingOff(i, now)) out.add(i);
        if (out.isEmpty() && !ids.isEmpty()) out.add(ids.size() - 1);
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** A layer had no ad ({@code code} is Google's error code). */
    public void failed(int layer, int code, long now) {
        if (layer < 0 || layer >= ids.size()) return;
        long base;
        switch (code) {
            case CODE_NO_FILL: base = NO_FILL_MS; break;
            case CODE_NETWORK: base = NETWORK_MS; break;
            case CODE_INVALID_REQUEST: base = INVALID_MS; break;
            default: base = OTHER_MS; break;
        }
        int n = Math.min(streak[layer], 4);
        long wait = code == CODE_INVALID_REQUEST ? base : Math.min(MAX_BACKOFF_MS, base << n);
        skipUntil[layer] = now + wait;
        streak[layer]++;
    }

    /** A layer gave an ad: it is asked again every time from now on. */
    public void loaded(int layer) {
        if (layer < 0 || layer >= ids.size()) return;
        skipUntil[layer] = 0;
        streak[layer] = 0;
    }

    /** True while an ad loaded at {@code loadedAt} can still be shown. */
    public static boolean fresh(long loadedAt, long now) {
        return loadedAt > 0 && now >= loadedAt && now - loadedAt < FRESH_MS;
    }

    /** The key for counting a day's videos. */
    public static String dayKey(long now, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString();
    }

    /** True while the day's videos are under the cap. */
    public static boolean underCap(int shownToday) {
        return shownToday < DAILY_CAP;
    }
}
