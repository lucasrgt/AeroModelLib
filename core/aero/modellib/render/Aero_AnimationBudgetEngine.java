package aero.modellib.render;

import java.util.HashMap;

/** Mutable frame state shared by admission and pressure policies. */
final class Aero_AnimationBudgetEngine {
    static final HashMap<Object, Integer> HOLDS = new HashMap<Object, Integer>();
    static final HashMap<Object, Aero_RenderLod> DECISIONS = new HashMap<Object, Aero_RenderLod>();
    static final Aero_LongIntMap LONG_HOLDS = new Aero_LongIntMap(1024);
    static final Aero_LongIntMap LONG_DECISIONS = new Aero_LongIntMap(1024);
    static double focalPx, fastFrameMs;
    static int displayHeight = -1, frame, accepted, rejected, criticalAccepted;
    static int hysteresisAccepted, priorityRejected;
    static int visibleLimit = Aero_AnimationBudgetConfig.MAX_ANIMATED;
    static int pressureLimit = Aero_AnimationBudgetConfig.MAX_ANIMATED;
    static int pressureUntil, nextRecovery, pressureDrops, throughputBadFrames;
    private Aero_AnimationBudgetEngine() {}

    static void beginFrame() {
        frame++; accepted = 0; rejected = 0; criticalAccepted = 0;
        hysteresisAccepted = 0; priorityRejected = 0;
        DECISIONS.clear(); LONG_DECISIONS.clear();
        if ((frame & 31) == 0) Aero_AnimationBudgetAdmission.expireHolds();
        Aero_AnimationBudgetPressure.recover();
    }
    static void updateDisplayHeight(int height) {
        if (height <= 0 || height == displayHeight) return;
        focalPx = height / (2d * Aero_AnimationBudgetConfig.TAN_HALF_VFOV); displayHeight = height;
    }
    static int effectiveLimit() {
        if (!Aero_AnimationBudgetConfig.ENABLED || Aero_AnimationBudgetConfig.MAX_ANIMATED < 0) {
            return Aero_AnimationBudgetConfig.MAX_ANIMATED;
        }
        int limit = baseLimit();
        if (Aero_AnimationBudgetPressure.enabled()) {
            if (pressureLimit <= 0 || pressureLimit > limit) pressureLimit = limit;
            limit = Math.min(limit, Math.max(0, pressureLimit));
        }
        return limit;
    }
    static int baseLimit() {
        return Math.min(Aero_AnimationBudgetConfig.MAX_ANIMATED, Math.max(0, visibleLimit));
    }
}
