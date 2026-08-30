package aero.modellib.render;

/** Visible-chunk and measured-frame feedback policy. */
final class Aero_AnimationBudgetPressure {
    private Aero_AnimationBudgetPressure() {}
    static boolean enabled() {
        return Aero_AnimationBudgetConfig.ENABLED && Aero_AnimationBudgetConfig.FRAME_PRESSURE_THROTTLE
            && Aero_AnimationBudgetConfig.MAX_ANIMATED > 0;
    }
    static void updateVisibleChunks(int chunks) {
        int maximum = Aero_AnimationBudgetConfig.MAX_ANIMATED;
        if (!Aero_AnimationBudgetConfig.VISIBLE_CHUNK_THROTTLE || maximum < 0 || chunks < 0) {
            Aero_AnimationBudgetEngine.visibleLimit = maximum; return;
        }
        int target = targetForChunks(chunks, maximum);
        if (!Aero_AnimationBudgetConfig.VISIBLE_CHUNK_SMOOTH) {
            Aero_AnimationBudgetEngine.visibleLimit = target; return;
        }
        Aero_AnimationBudgetEngine.visibleLimit = smoothVisibleLimit(target, maximum);
    }
    private static int targetForChunks(int chunks, int maximum) {
        if (chunks >= Aero_AnimationBudgetConfig.VISIBLE_CHUNK_HIGH)
            return Math.min(maximum, Aero_AnimationBudgetConfig.VISIBLE_CHUNK_HIGH_MAX);
        if (chunks >= Aero_AnimationBudgetConfig.VISIBLE_CHUNK_MID)
            return Math.min(maximum, Aero_AnimationBudgetConfig.VISIBLE_CHUNK_MID_MAX);
        return maximum;
    }
    private static int smoothVisibleLimit(int target, int maximum) {
        int current = Aero_AnimationBudgetEngine.visibleLimit;
        if (current <= 0 || current > maximum) current = maximum;
        if (current > target) current = Math.max(target, current - Aero_AnimationBudgetConfig.VISIBLE_CHUNK_STEP);
        else if (current < target) current = Math.min(target, current + Aero_AnimationBudgetConfig.VISIBLE_CHUNK_RECOVERY_STEP);
        return current;
    }
    static void record(double frameMs, double displayMs, double chunkMs, long gcMs) {
        if (!enabled() || !hasRenderWork()) return;
        int base = Aero_AnimationBudgetEngine.baseLimit();
        normalizePressureLimit(base);
        boolean driver = driverPressure(displayMs, chunkMs);
        if (!driver && !generalPressure(frameMs, gcMs)) return;
        int current = Math.min(base, Aero_AnimationBudgetEngine.pressureLimit);
        int next = driver ? Aero_AnimationBudgetConfig.FRAME_PRESSURE_MIN
            : Math.max(Aero_AnimationBudgetConfig.FRAME_PRESSURE_MIN,
                current - Aero_AnimationBudgetConfig.FRAME_PRESSURE_STEP);
        if (next < Aero_AnimationBudgetEngine.pressureLimit) {
            Aero_AnimationBudgetEngine.pressureLimit = next; Aero_AnimationBudgetEngine.pressureDrops++;
        }
        Aero_AnimationBudgetEngine.pressureUntil = Aero_AnimationBudgetEngine.frame + Aero_AnimationBudgetConfig.FRAME_PRESSURE_FRAMES;
        Aero_AnimationBudgetEngine.nextRecovery = Aero_AnimationBudgetEngine.pressureUntil
            + Aero_AnimationBudgetConfig.FRAME_PRESSURE_RECOVERY_FRAMES;
    }
    private static boolean hasRenderWork() {
        return Aero_AnimationBudgetEngine.accepted > 0 || Aero_AnimationBudgetEngine.rejected > 0;
    }
    private static void normalizePressureLimit(int base) {
        if (Aero_AnimationBudgetEngine.pressureLimit <= 0
                || Aero_AnimationBudgetEngine.pressureLimit > base)
            Aero_AnimationBudgetEngine.pressureLimit = base;
    }
    private static boolean driverPressure(double displayMs, double chunkMs) {
        return displayMs >= Aero_AnimationBudgetConfig.DISPLAY_STALL_MS
            || chunkMs >= Aero_AnimationBudgetConfig.RENDER_CHUNK_STALL_MS;
    }
    private static boolean generalPressure(double frameMs, long gcMs) {
        return gcMs >= Aero_AnimationBudgetConfig.GC_STALL_MS
            || frameMs >= Aero_AnimationBudgetConfig.FRAME_PRESSURE_MS || throughput(frameMs);
    }
    private static boolean throughput(double frameMs) {
        if (!Aero_AnimationBudgetConfig.THROUGHPUT_THROTTLE || frameMs <= 0d) return false;
        if (Aero_AnimationBudgetEngine.fastFrameMs <= 0d || frameMs < Aero_AnimationBudgetEngine.fastFrameMs) {
            Aero_AnimationBudgetEngine.fastFrameMs = frameMs; Aero_AnimationBudgetEngine.throughputBadFrames = 0; return false;
        }
        boolean bad = frameMs >= Aero_AnimationBudgetConfig.THROUGHPUT_MIN_MS
            && frameMs >= Aero_AnimationBudgetEngine.fastFrameMs * Aero_AnimationBudgetConfig.THROUGHPUT_RATIO;
        if (bad) return ++Aero_AnimationBudgetEngine.throughputBadFrames >= Aero_AnimationBudgetConfig.THROUGHPUT_FRAMES;
        if (Aero_AnimationBudgetEngine.throughputBadFrames > 0) Aero_AnimationBudgetEngine.throughputBadFrames--;
        Aero_AnimationBudgetEngine.fastFrameMs += (frameMs - Aero_AnimationBudgetEngine.fastFrameMs) * 0.0025d;
        return false;
    }
    static void recover() {
        if (!enabled()) return;
        int base = Aero_AnimationBudgetEngine.baseLimit();
        if (Aero_AnimationBudgetEngine.pressureLimit <= 0 || Aero_AnimationBudgetEngine.pressureLimit > base) {
            Aero_AnimationBudgetEngine.pressureLimit = base; return;
        }
        if (Aero_AnimationBudgetEngine.pressureLimit >= base
                || Aero_AnimationBudgetEngine.frame <= Aero_AnimationBudgetEngine.pressureUntil
                || Aero_AnimationBudgetEngine.frame < Aero_AnimationBudgetEngine.nextRecovery) return;
        int step = Math.max(1, Aero_AnimationBudgetConfig.FRAME_PRESSURE_STEP / 2);
        Aero_AnimationBudgetEngine.pressureLimit = Math.min(base, Aero_AnimationBudgetEngine.pressureLimit + step);
        Aero_AnimationBudgetEngine.nextRecovery = Aero_AnimationBudgetEngine.frame
            + Aero_AnimationBudgetConfig.FRAME_PRESSURE_RECOVERY_FRAMES;
    }
}
