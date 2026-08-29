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
    private static int reusedThisBatch, reusedThisFrame, resolvedThisFrame;
    private static boolean representativeLocked;

    private Aero_BatchPoseReuse() {}

    static int[] beginBatch(int count) {
        ensureSources(count);
        for (int i = 0; i < count; i++) poseSources[i] = i;
        representativeBundle = null;
        representativeClip = null;
        representativeLocked = false;
        reusedThisBatch = 0;
        return poseSources;
    }

    static int sourceFor(Aero_AnimationBundle bundle, Aero_AnimationClip clip,
            float time, Aero_AnimationPlayback playback, int instance) {
        Class<?> playbackType = playback.getClass();
        boolean standard = playbackType == Aero_AnimationPlayback.class
            || playbackType == Aero_AnimationState.class;
        if (!ENABLED || !standard || playback.inTransition()) {
            resolvedThisFrame++;
            return instance;
        }
        int timeBits = Float.floatToRawIntBits(time);
        if (representativeClip == null) {
            setRepresentative(bundle, clip, timeBits, instance);
        } else if (representativeBundle == bundle && representativeClip == clip
                && representativeTime == timeBits) {
            representativeLocked = true;
            reusedThisBatch++;
            reusedThisFrame++;
            return representativeSource;
        } else if (!representativeLocked) {
            setRepresentative(bundle, clip, timeBits, instance);
        }
        resolvedThisFrame++;
        return instance;
    }

    static void beginFrameCounters() { reusedThisFrame = resolvedThisFrame = 0; }
    static void recordResolved() { resolvedThisFrame++; }
    static int reusedThisFrame() { return reusedThisFrame; }
    static int resolvedThisFrame() { return resolvedThisFrame; }
    static int sharedSource() {
        return ENABLED && reusedThisBatch > 0 ? representativeSource : -1;
    }
    static int sharedCount() { return reusedThisBatch > 0 ? reusedThisBatch + 1 : 0; }

    private static void setRepresentative(Aero_AnimationBundle bundle,
            Aero_AnimationClip clip, int time, int source) {
        representativeBundle = bundle;
        representativeClip = clip;
        representativeTime = time;
        representativeSource = source;
    }

    private static void ensureSources(int count) {
        if (poseSources.length >= count) return;
        poseSources = new int[Math.max(count, poseSources.length * 2)];
    }
}
