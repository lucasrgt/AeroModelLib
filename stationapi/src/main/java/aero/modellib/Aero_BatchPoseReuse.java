package aero.modellib;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.optimization.OptimizationRef;

/** Exact, allocation-free pose sharing inside one compatible render batch. */
@OptimizationRef({"aero.animation.batch-pose-reuse"})
final class Aero_BatchPoseReuse {
    static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.batchposereuse"));
    private static int[] poseSources = new int[256];
    private static Aero_AnimationBundle representativeBundle;
    private static Aero_AnimationClip representativeClip;
    private static int representativeTime, representativeSource;
    private static int reusedThisFrame, resolvedThisFrame;

    private Aero_BatchPoseReuse() {}

    static int[] beginBatch(int count) {
        ensureSources(count);
        for (int i = 0; i < count; i++) poseSources[i] = i;
        representativeBundle = null;
        representativeClip = null;
        return poseSources;
    }

    static int sourceFor(Aero_AnimationBundle bundle, Aero_AnimationClip clip,
            float time, Aero_AnimationPlayback playback, int instance) {
        if (!ENABLED || playback.getClass() != Aero_AnimationPlayback.class
                || playback.inTransition()) {
            resolvedThisFrame++;
            return instance;
        }
        int timeBits = Float.floatToRawIntBits(time);
        if (representativeClip == null) {
            representativeBundle = bundle;
            representativeClip = clip;
            representativeTime = timeBits;
            representativeSource = instance;
        } else if (representativeBundle == bundle && representativeClip == clip
                && representativeTime == timeBits) {
            reusedThisFrame++;
            return representativeSource;
        }
        resolvedThisFrame++;
        return instance;
    }

    static void beginFrameCounters() { reusedThisFrame = resolvedThisFrame = 0; }
    static void recordResolved() { resolvedThisFrame++; }
    static int reusedThisFrame() { return reusedThisFrame; }
    static int resolvedThisFrame() { return resolvedThisFrame; }

    private static void ensureSources(int count) {
        if (poseSources.length >= count) return;
        poseSources = new int[Math.max(count, poseSources.length * 2)];
    }
}
