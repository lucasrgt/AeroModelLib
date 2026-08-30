package aero.modellib;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.player.PlayerEntity;

import aero.modellib.render.Aero_AnimationRenderBudget;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderDistanceCulling;
import aero.modellib.render.Aero_SmallObjectCull;

/** Owns StationAPI render-frame boundaries and the per-frame camera snapshot. */
final class Aero_RenderFrame {
    private static boolean valid, cameraValid, prewarmDone;
    private static int viewDistance = Aero_RenderDistanceCulling.VIEW_DISTANCE_SHORT;
    private static int displayWidth = -1, displayHeight = -1;
    private static double cameraX, cameraY, cameraZ, coneHalfDegrees;
    private static PlayerEntity player;

    private Aero_RenderFrame() {}

    static void begin() {
        viewDistance = readViewDistance();
        refreshCamera();
        Aero_FrustumCull.beginFrameCounters();
        Aero_SmallObjectCull.beginFrameCounters();
        Aero_AnimationRenderBudget.beginFrame();
        Aero_BECellIndex.beginFrame();
        Aero_Frustum6Plane.invalidateFrame();
        Aero_AnimatedBatcher.flush();
        Aero_BECellRenderer.flushCachedCamera();
        Aero_Prewarm.discoverLoadedModels();
        Aero_Prewarm.drainFrame();
        if (!prewarmDone) prewarmDone = Aero_MeshChunkBaker.prewarmAll();
        snapshotChunks();
        Aero_AnimationRenderBudget.updateVisibleChunkPressure(Aero_ChunkVisibility.visibleChunkCount());
        valid = true;
    }

    static void ensure() { if (!valid) begin(); }
    static int viewDistance() { ensure(); return viewDistance; }
    static boolean cameraValid() { return cameraValid; }
    static double cameraX() { return cameraX; }
    static double cameraY() { return cameraY; }
    static double cameraZ() { return cameraZ; }
    static PlayerEntity player() { return player; }

    private static int readViewDistance() {
        EntityRenderDispatcher dispatcher = EntityRenderDispatcher.INSTANCE;
        return dispatcher != null && dispatcher.options != null
            ? dispatcher.options.viewDistance : Aero_RenderDistanceCulling.VIEW_DISTANCE_SHORT;
    }

    private static void snapshotChunks() {
        if (!Aero_ChunkVisibility.ENABLED) return;
        Object game = FabricLoader.getInstance().getGameInstance();
        if (!(game instanceof Minecraft)) return;
        Object renderer = ((Minecraft) game).worldRenderer;
        if (renderer == null) return;
        try {
            net.minecraft.client.render.chunk.ChunkBuilder[] chunks =
                ((aero.modellib.mixin.WorldRendererChunksAccessor) renderer).aero_modellib_getChunks();
            Aero_ChunkVisibility.snapshot(chunks);
        } catch (Throwable ignored) {}
    }

    private static void refreshCamera() {
        cameraValid = false;
        player = null;
        Object game = FabricLoader.getInstance().getGameInstance();
        if (!(game instanceof Minecraft) || ((Minecraft) game).player == null) {
            Aero_FrustumCull.clearCamera();
            return;
        }
        Minecraft minecraft = (Minecraft) game;
        player = minecraft.player;
        cameraX = player.x; cameraY = player.y; cameraZ = player.z;
        cameraValid = true;
        if (!Aero_FrustumCull.ENABLED) return;
        Aero_FrustumCull.updateCameraForward(player.yaw, player.pitch);
        updateViewport(minecraft);
    }

    private static void updateViewport(Minecraft minecraft) {
        if (minecraft.displayHeight <= 0) return;
        if (minecraft.displayWidth != displayWidth || minecraft.displayHeight != displayHeight) {
            double aspect = (double) minecraft.displayWidth / minecraft.displayHeight;
            double horizontal = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(35.0d)) * aspect));
            coneHalfDegrees = Math.max(75.0d, horizontal + 20.0d);
            Aero_FrustumCull.setViewportHalfAnglesDegrees(horizontal, 35.0d);
            displayWidth = minecraft.displayWidth;
            displayHeight = minecraft.displayHeight;
        }
        Aero_FrustumCull.setConeHalfAngleDegrees(coneHalfDegrees);
        Aero_SmallObjectCull.updateFromDisplayHeight(minecraft.displayHeight);
        Aero_AnimationRenderBudget.updateFromDisplayHeight(minecraft.displayHeight);
    }
}
