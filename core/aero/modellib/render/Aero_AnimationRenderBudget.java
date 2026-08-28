package aero.modellib.render;

import aero.modellib.optimization.OptimizationRef;

/** Public facade for per-frame admission of expensive animated renders. */
@OptimizationRef({"aero.animation.render-budget"})
public final class Aero_AnimationRenderBudget {
    public static final boolean ENABLED = Aero_AnimationBudgetConfig.ENABLED;
    public static final int MAX_ANIMATED = Aero_AnimationBudgetConfig.MAX_ANIMATED;
    public static final long NO_HYSTERESIS_KEY = Long.MIN_VALUE;
    private Aero_AnimationRenderBudget() {}

    public static void beginFrame() { Aero_AnimationBudgetEngine.beginFrame(); }
    public static void updateFromDisplayHeight(int height) { Aero_AnimationBudgetEngine.updateDisplayHeight(height); }
    public static void updateVisibleChunkPressure(int chunks) { Aero_AnimationBudgetPressure.updateVisibleChunks(chunks); }
    public static void recordFramePressure(double frameMs, double displayMs,
            double renderChunksMs, long gcMs) {
        Aero_AnimationBudgetPressure.record(frameMs, displayMs, renderChunksMs, gcMs);
    }
    public static boolean framePressureThrottleEnabled() { return Aero_AnimationBudgetPressure.enabled(); }
    public static Aero_RenderLod apply(Aero_RenderLod lod) {
        return Aero_AnimationBudgetAdmission.apply(lod, 0d, 0d, 0d, 0d, (Object) null);
    }
    public static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z, double radius) {
        return Aero_AnimationBudgetAdmission.apply(lod, x, y, z, radius, (Object) null);
    }
    public static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z,
            double radius, Object key) {
        return Aero_AnimationBudgetAdmission.apply(lod, x, y, z, radius, key);
    }
    public static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z,
            double radius, long key) {
        return Aero_AnimationBudgetAdmission.apply(lod, x, y, z, radius, key);
    }
    public static int acceptedThisFrame() { return Aero_AnimationBudgetEngine.accepted; }
    public static int rejectedThisFrame() { return Aero_AnimationBudgetEngine.rejected; }
    public static int criticalAcceptedThisFrame() { return Aero_AnimationBudgetEngine.criticalAccepted; }
    public static int hysteresisAcceptedThisFrame() { return Aero_AnimationBudgetEngine.hysteresisAccepted; }
    public static int priorityRejectedThisFrame() { return Aero_AnimationBudgetEngine.priorityRejected; }
    public static int effectiveMaxAnimatedThisFrame() { return Aero_AnimationBudgetEngine.effectiveLimit(); }
    public static int framePressureLimitThisFrame() {
        return framePressureThrottleEnabled() ? Math.max(0, Aero_AnimationBudgetEngine.pressureLimit) : MAX_ANIMATED;
    }
    public static int framePressureDrops() { return Aero_AnimationBudgetEngine.pressureDrops; }
    public static int throughputBadFrames() { return Aero_AnimationBudgetEngine.throughputBadFrames; }
}
