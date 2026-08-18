package aero.modellib.render;

import java.util.Iterator;
import java.util.Map;

/** Importance scoring, identity hysteresis, and decision memoization. */
final class Aero_AnimationBudgetAdmission {
    private Aero_AnimationBudgetAdmission() {}
    static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z,
            double radius, Object key) {
        if (lod != Aero_RenderLod.ANIMATED) return lod;
        Aero_RenderLod cached = key == null ? null : Aero_AnimationBudgetEngine.DECISIONS.get(key);
        if (cached != null) return cached;
        if (!Aero_AnimationBudgetConfig.ENABLED || Aero_AnimationBudgetConfig.MAX_ANIMATED < 0) return remember(key, lod);
        double distance = x * x + y * y + z * z, pixels = projected(distance, radius);
        if (held(key) && Aero_AnimationBudgetEngine.accepted < hysteresisLimit()) { accept(key, true, false); return remember(key, lod); }
        if (critical(distance, pixels) && Aero_AnimationBudgetEngine.accepted < criticalLimit()) { accept(key, false, true); return remember(key, lod); }
        if (priorityReject(pixels)) { Aero_AnimationBudgetEngine.rejected++; Aero_AnimationBudgetEngine.priorityRejected++; return remember(key, Aero_RenderLod.STATIC); }
        if (Aero_AnimationBudgetEngine.accepted < Aero_AnimationBudgetEngine.effectiveLimit()) { accept(key, false, false); return remember(key, lod); }
        Aero_AnimationBudgetEngine.rejected++; return remember(key, Aero_RenderLod.STATIC);
    }
    static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z,
            double radius, long key) {
        if (lod != Aero_RenderLod.ANIMATED) return lod;
        int ordinal = key == Long.MIN_VALUE ? -1 : Aero_AnimationBudgetEngine.LONG_DECISIONS.get(key, -1);
        if (ordinal >= 0) return Aero_RenderLod.values()[ordinal];
        if (!Aero_AnimationBudgetConfig.ENABLED || Aero_AnimationBudgetConfig.MAX_ANIMATED < 0) return remember(key, lod);
        double distance = x * x + y * y + z * z, pixels = projected(distance, radius);
        if (held(key) && Aero_AnimationBudgetEngine.accepted < hysteresisLimit()) { accept(key, true, false); return remember(key, lod); }
        if (critical(distance, pixels) && Aero_AnimationBudgetEngine.accepted < criticalLimit()) { accept(key, false, true); return remember(key, lod); }
        if (priorityReject(pixels)) { Aero_AnimationBudgetEngine.rejected++; Aero_AnimationBudgetEngine.priorityRejected++; return remember(key, Aero_RenderLod.STATIC); }
        if (Aero_AnimationBudgetEngine.accepted < Aero_AnimationBudgetEngine.effectiveLimit()) { accept(key, false, false); return remember(key, lod); }
        Aero_AnimationBudgetEngine.rejected++; return remember(key, Aero_RenderLod.STATIC);
    }
    private static boolean critical(double distance, double pixels) {
        return pixels >= Aero_AnimationBudgetConfig.CRITICAL_PX
            || Aero_AnimationBudgetConfig.NEAR_BLOCKS > 0d
            && distance <= Aero_AnimationBudgetConfig.NEAR_BLOCKS * Aero_AnimationBudgetConfig.NEAR_BLOCKS;
    }
    private static boolean priorityReject(double pixels) {
        int maximum = Aero_AnimationBudgetEngine.effectiveLimit(); if (maximum <= 0) return true;
        int low = Math.max(1, maximum / 2), middle = Math.max(low, maximum * 3 / 4);
        return pixels > 0d && pixels < Aero_AnimationBudgetConfig.LOW_PX && Aero_AnimationBudgetEngine.accepted >= low
            || pixels > 0d && pixels < Aero_AnimationBudgetConfig.MID_PX && Aero_AnimationBudgetEngine.accepted >= middle;
    }
    private static double projected(double distance, double radius) {
        return Aero_AnimationBudgetEngine.focalPx <= 0d || radius <= 0d || distance <= 0.000001d ? 0d
            : 2d * radius * Aero_AnimationBudgetEngine.focalPx / Math.sqrt(distance);
    }
    private static int hysteresisLimit() {
        int base = Aero_AnimationBudgetEngine.effectiveLimit();
        return Aero_AnimationBudgetConfig.HARD_CAP ? base : base + Math.max(0, Aero_AnimationBudgetConfig.HYSTERESIS_EXTRA);
    }
    private static int criticalLimit() {
        int base = Aero_AnimationBudgetEngine.effectiveLimit();
        return Aero_AnimationBudgetConfig.HARD_CAP ? base : base + Math.max(0, Aero_AnimationBudgetConfig.HYSTERESIS_EXTRA)
            + Math.max(0, Aero_AnimationBudgetConfig.CRITICAL_EXTRA);
    }
    private static boolean held(Object key) {
        if (key == null || Aero_AnimationBudgetConfig.HYSTERESIS_FRAMES <= 0) return false;
        Integer until = Aero_AnimationBudgetEngine.HOLDS.get(key); return until != null && until.intValue() >= Aero_AnimationBudgetEngine.frame;
    }
    private static boolean held(long key) {
        return key != Long.MIN_VALUE && Aero_AnimationBudgetConfig.HYSTERESIS_FRAMES > 0
            && Aero_AnimationBudgetEngine.LONG_HOLDS.get(key, Integer.MIN_VALUE) >= Aero_AnimationBudgetEngine.frame;
    }
    private static void accept(Object key, boolean hysteresis, boolean critical) {
        count(hysteresis, critical);
        if (key != null && Aero_AnimationBudgetConfig.HYSTERESIS_FRAMES > 0) Aero_AnimationBudgetEngine.HOLDS.put(key, Integer.valueOf(Aero_AnimationBudgetEngine.frame + Aero_AnimationBudgetConfig.HYSTERESIS_FRAMES));
    }
    private static void accept(long key, boolean hysteresis, boolean critical) {
        count(hysteresis, critical);
        if (key != Long.MIN_VALUE && Aero_AnimationBudgetConfig.HYSTERESIS_FRAMES > 0) Aero_AnimationBudgetEngine.LONG_HOLDS.put(key, Aero_AnimationBudgetEngine.frame + Aero_AnimationBudgetConfig.HYSTERESIS_FRAMES);
    }
    private static void count(boolean hysteresis, boolean critical) {
        Aero_AnimationBudgetEngine.accepted++;
        if (hysteresis) Aero_AnimationBudgetEngine.hysteresisAccepted++;
        if (critical) Aero_AnimationBudgetEngine.criticalAccepted++;
    }
    private static Aero_RenderLod remember(Object key, Aero_RenderLod lod) {
        if (key != null) Aero_AnimationBudgetEngine.DECISIONS.put(key, lod); return lod;
    }
    private static Aero_RenderLod remember(long key, Aero_RenderLod lod) {
        if (key != Long.MIN_VALUE) Aero_AnimationBudgetEngine.LONG_DECISIONS.put(key, lod.ordinal()); return lod;
    }
    static void expireHolds() {
        Iterator<Map.Entry<Object, Integer>> iterator = Aero_AnimationBudgetEngine.HOLDS.entrySet().iterator();
        while (iterator.hasNext()) { Integer until = iterator.next().getValue(); if (until == null || until.intValue() < Aero_AnimationBudgetEngine.frame) iterator.remove(); }
        Aero_AnimationBudgetEngine.LONG_HOLDS.removeValuesLessThan(Aero_AnimationBudgetEngine.frame);
    }
}
