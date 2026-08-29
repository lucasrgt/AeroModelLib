package aero.modellib.animation;


import aero.modellib.util.Aero_PerfConfig;

/**
 * Animation curve LUT (look-up table) configuration. Controls whether each
 * {@link Aero_AnimationClip.ChannelTrack} pre-bakes its sample output into
 * a fixed-size table at construction, so runtime evaluation collapses from
 * <em>find segment + apply easing + slerp + euler-decode</em> to a single
 * O(1) array lookup with a linear blend between adjacent entries.
 *
 * <h2>Trade-off</h2>
 * <ul>
 *   <li><strong>Win:</strong> per-frame channel evaluation drops from
 *       hundreds of ns (binary search + easing function + quaternion
 *       slerp + atan2/asin/atan2 for euler decode) to ~20 ns
 *       (mul + index + 3 lerps). Hottest at high BE counts with
 *       complex easings (ELASTIC, BACK, BOUNCE) and rotations.</li>
 *   <li><strong>Cost:</strong> RAM. Each channel allocates
 *       {@code samples × 3 floats}. With the default 256 samples that's
 *       3 KiB per channel. A 30-channel mega-model adds ~90 KiB.</li>
 *   <li><strong>Accuracy:</strong> the LUT samples the FINAL output of
 *       the clip's evaluation pipeline (easing + slerp + decode), so
 *       inter-sample linear blending is the only approximation source.
 *       At 256 samples for a 1-second clip the time resolution is ~4 ms.
 *       Tracks with steps or excessive interpolation error automatically
 *       retain the exact evaluator.</li>
 * </ul>
 *
 * <h2>Toggle</h2>
 * <ul>
 *   <li>{@code -Daero.anim.lut=true} enables the research path. Default OFF,
 *       including under {@code -Daero.perf.memory=high}: representative ULTRA
 *       stress evidence found the exact evaluator faster.</li>
 *   <li>{@code -Daero.anim.lut.samples=N} (default 256, clamped to
 *       [{@value #MIN_SAMPLES}, {@value #MAX_SAMPLES}]) sets the bake
 *       resolution.</li>
 * </ul>
 *
 * <p>The LUT is built once at clip construction (mod-init time, not
 * per-frame), so enabling it pays a one-time bake cost and saves on
 * every render thereafter.
 */
public final class Aero_AnimationLUTConfig {

    /**
     * Default <strong>OFF</strong>, including the high-memory preset.
     * The exact path remains canonical after the LUT regressed in the
     * representative diverse-phase stress scene.
     */
    public static final boolean ENABLED =
        Aero_PerfConfig.booleanProperty("aero.anim.lut", false, false);

    public static final int MIN_SAMPLES = 4;
    public static final int MAX_SAMPLES = 1024;
    public static final int DEFAULT_SAMPLES = 256;

    public static final int SAMPLES;
    static {
        int parsed = DEFAULT_SAMPLES;
        String s = System.getProperty("aero.anim.lut.samples");
        if (s != null) {
            try { parsed = Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) { /* keep default */ }
        }
        if (parsed < MIN_SAMPLES) parsed = MIN_SAMPLES;
        if (parsed > MAX_SAMPLES) parsed = MAX_SAMPLES;
        SAMPLES = parsed;
    }

    private Aero_AnimationLUTConfig() {}
}
