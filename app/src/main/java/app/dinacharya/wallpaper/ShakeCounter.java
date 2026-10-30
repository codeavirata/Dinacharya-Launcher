package app.dinacharya.wallpaper;

/**
 * Counts shakes of the phone from accelerometer readings, for the "shake to start the day" way of turning the start-of-day alarm off.
 * A shake is a reading well away from steady gravity; two shakes must be a little apart, so one hard jolt counts once. Uses no Android classes.
 */
public final class ShakeCounter {
    /** Standard gravity, m/s². */
    public static final float G = 9.80665f;
    /** The shakes asked for, how far from one g a reading must be to count, and the pause between two shakes. */
    public static final int DEFAULT_TARGET = 20;
    public static final float DEFAULT_THRESHOLD_G = 0.9f;
    public static final long DEFAULT_GAP_MS = 280L;

    private final int target;
    private final float threshold;
    private final long gapMs;
    private int count;
    private long lastMs = Long.MIN_VALUE;

    public ShakeCounter() {
        this(DEFAULT_TARGET, DEFAULT_THRESHOLD_G, DEFAULT_GAP_MS);
    }

    public ShakeCounter(int target, float thresholdG, long gapMs) {
        this.target = Math.max(1, target);
        this.threshold = Math.max(0.1f, thresholdG);
        this.gapMs = Math.max(0L, gapMs);
    }

    /** Feeds one reading (m/s² on each axis, at {@code timeMs}); returns true once the target is reached. */
    public boolean add(float x, float y, float z, long timeMs) {
        if (done()) return true;
        float g = (float) Math.sqrt(x * x + y * y + z * z) / G;
        if (Float.isNaN(g)) return false;
        if (Math.abs(g - 1f) >= threshold && (lastMs == Long.MIN_VALUE || timeMs - lastMs >= gapMs)) {
            count++;
            lastMs = timeMs;
        }
        return done();
    }

    public int count() {
        return Math.min(count, target);
    }

    public int target() {
        return target;
    }

    public boolean done() {
        return count >= target;
    }

    /** From 0 to 1. */
    public float progress() {
        return Math.min(1f, count / (float) target);
    }

    public void reset() {
        count = 0;
        lastMs = Long.MIN_VALUE;
    }
}
