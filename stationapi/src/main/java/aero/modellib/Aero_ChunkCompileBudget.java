package aero.modellib;

import java.util.List;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.render.Aero_ChunkWorkScheduler;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.LivingEntity;

/** Opt-in bounded scheduler for vanilla chunk display-list rebuilds. */
@OptimizationRef({"aero.chunk.compile-budget"})
public final class Aero_ChunkCompileBudget {
    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.chunkCompileBudget"));

    private static final int BUDGET =
        intProperty("aero.chunkCompileBudget.chunksPerFrame", 2, 1, 64);
    private static final int MAXIMUM_AGE =
        intProperty("aero.chunkCompileBudget.maximumAge", 120, 1, 3600);
    private static final int DEBT_LIMIT =
        intProperty("aero.chunkCompileBudget.debtLimit", 30, 1, 3600);
    private static final Aero_ChunkWorkScheduler<ChunkBuilder> SCHEDULER =
        new Aero_ChunkWorkScheduler<ChunkBuilder>();
    private static final ChunkAdapter ADAPTER = new ChunkAdapter();

    private static int builtThisFrame, visibleBuiltThisFrame;
    private static int urgentBuiltThisFrame, oldestAge, maximumDebt;
    private static int builtLastFrame, visibleBuiltLastFrame;
    private static int urgentBuiltLastFrame;

    private Aero_ChunkCompileBudget() {}

    public static void beginFrame() {
        builtLastFrame = builtThisFrame;
        visibleBuiltLastFrame = visibleBuiltThisFrame;
        urgentBuiltLastFrame = urgentBuiltThisFrame;
        builtThisFrame = visibleBuiltThisFrame = urgentBuiltThisFrame = 0;
    }

    public static boolean handles(boolean forced) {
        return ENABLED && !forced;
    }

    public static boolean schedule(List<ChunkBuilder> dirtyChunks,
                                   LivingEntity camera) {
        ADAPTER.camera = camera;
        try {
            SCHEDULER.schedule(dirtyChunks, ADAPTER, BUDGET,
                MAXIMUM_AGE, DEBT_LIMIT);
        } finally {
            ADAPTER.camera = null;
        }
        builtThisFrame += SCHEDULER.built();
        visibleBuiltThisFrame += SCHEDULER.visibleBuilt();
        urgentBuiltThisFrame += SCHEDULER.urgentBuilt();
        oldestAge = SCHEDULER.oldestAge();
        maximumDebt = SCHEDULER.maximumDebt();
        return dirtyChunks.isEmpty();
    }

    public static int builtThisFrame() { return builtThisFrame; }
    public static int builtLastFrame() { return builtLastFrame; }
    public static int visibleBuiltLastFrame() { return visibleBuiltLastFrame; }
    public static int urgentBuiltLastFrame() { return urgentBuiltLastFrame; }
    public static int oldestAge() { return oldestAge; }
    public static int maximumDebt() { return maximumDebt; }

    private static int intProperty(String name, int fallback, int min, int max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class ChunkAdapter
            implements Aero_ChunkWorkScheduler.Adapter<ChunkBuilder> {
        LivingEntity camera;

        public boolean isDirty(ChunkBuilder chunk) { return chunk.dirty; }
        public boolean isVisible(ChunkBuilder chunk) { return chunk.inFrustum; }
        public double squaredDistance(ChunkBuilder chunk) {
            return camera == null ? Double.POSITIVE_INFINITY
                : chunk.squaredDistanceTo(camera);
        }
        public void rebuild(ChunkBuilder chunk) { chunk.rebuild(); }
        public void markClean(ChunkBuilder chunk) { chunk.dirty = false; }
    }
}
