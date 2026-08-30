package aero.modellib.render;

import aero.modellib.optimization.OptimizationRef;

/** Camera-relative priority tiers for incremental chunk pre-baking. */
@OptimizationRef({"aero.chunk.compile-budget"})
public final class Aero_ChunkPrebakePriority {
    public static final int CURRENT = 0;
    public static final int VISIBLE_OR_ADJACENT = 1;
    public static final int LOOK_AHEAD = 2;
    public static final int BACKGROUND = 3;

    private Aero_ChunkPrebakePriority() {}

    public static int tier(boolean visible, int chunkOriginX, int chunkOriginZ,
                           double cameraX, double cameraZ, float cameraYaw,
                           int lookAheadRadius) {
        double radians = Math.toRadians(cameraYaw);
        return tierWithForward(visible, chunkOriginX, chunkOriginZ,
            cameraX, cameraZ, -Math.sin(radians), Math.cos(radians),
            lookAheadRadius);
    }

    public static int tierWithForward(boolean visible,
                                      int chunkOriginX, int chunkOriginZ,
                                      double cameraX, double cameraZ,
                                      double forwardX, double forwardZ,
                                      int lookAheadRadius) {
        int cameraChunkX = floorToInt(cameraX) >> 4;
        int cameraChunkZ = floorToInt(cameraZ) >> 4;
        int chunkX = chunkOriginX >> 4;
        int chunkZ = chunkOriginZ >> 4;
        int deltaX = chunkX - cameraChunkX;
        int deltaZ = chunkZ - cameraChunkZ;
        int ring = Math.max(Math.abs(deltaX), Math.abs(deltaZ));

        if (ring == 0) return CURRENT;
        if (visible || ring == 1) return VISIBLE_OR_ADJACENT;
        if (ring <= lookAheadRadius
            && deltaX * forwardX + deltaZ * forwardZ > 0.0d) {
            return LOOK_AHEAD;
        }
        return BACKGROUND;
    }

    public static boolean isPrebakeTier(int tier, boolean visible) {
        return !visible && tier <= LOOK_AHEAD;
    }

    private static int floorToInt(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }
}
