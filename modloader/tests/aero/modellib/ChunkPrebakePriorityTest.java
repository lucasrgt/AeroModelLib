package aero.modellib;

import org.junit.Test;

import aero.modellib.render.Aero_ChunkPrebakePriority;

import static org.junit.Assert.*;

public class ChunkPrebakePriorityTest {
    @Test
    public void currentChunkWinsAtWorldEntry() {
        assertEquals(Aero_ChunkPrebakePriority.CURRENT,
            tier(false, 0, 0, 8.0d, 8.0d, 0.0f));
    }

    @Test
    public void hiddenAdjacentChunkSharesVisiblePriority() {
        assertEquals(Aero_ChunkPrebakePriority.VISIBLE_OR_ADJACENT,
            tier(false, 16, 0, 8.0d, 8.0d, 0.0f));
        assertEquals(Aero_ChunkPrebakePriority.VISIBLE_OR_ADJACENT,
            tier(true, 64, 0, 8.0d, 8.0d, 0.0f));
    }

    @Test
    public void cameraDirectionSelectsTheLookAheadRing() {
        assertEquals(Aero_ChunkPrebakePriority.LOOK_AHEAD,
            tier(false, 0, 32, 8.0d, 8.0d, 0.0f));
        assertEquals(Aero_ChunkPrebakePriority.BACKGROUND,
            tier(false, 0, -32, 8.0d, 8.0d, 0.0f));
    }

    @Test
    public void teleportReclassifiesWithoutStoredJobs() {
        assertEquals(Aero_ChunkPrebakePriority.BACKGROUND,
            tier(false, 160, 160, 8.0d, 8.0d, 0.0f));
        assertEquals(Aero_ChunkPrebakePriority.CURRENT,
            tier(false, 160, 160, 168.0d, 168.0d, 0.0f));
    }

    @Test
    public void negativeCameraCoordinatesUseFlooring() {
        assertEquals(Aero_ChunkPrebakePriority.CURRENT,
            tier(false, -16, -16, -0.1d, -0.1d, 0.0f));
    }

    private static int tier(boolean visible, int x, int z,
                            double cameraX, double cameraZ, float yaw) {
        return Aero_ChunkPrebakePriority.tier(
            visible, x, z, cameraX, cameraZ, yaw, 3);
    }
}
