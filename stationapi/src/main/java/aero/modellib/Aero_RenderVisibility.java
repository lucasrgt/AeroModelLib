package aero.modellib;

import net.minecraft.block.entity.BlockEntity;

import aero.modellib.render.Aero_AnimationRenderBudget;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderDistanceCulling;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.render.Aero_SmallObjectCull;

/** Composes distance, size, chunk, cone, and real-frustum visibility decisions. */
final class Aero_RenderVisibility {
    private Aero_RenderVisibility() {}

    static boolean shouldRender(double x, double y, double z, double radius, double maximum) {
        return lod(x, y, z, radius, maximum, maximum, false).shouldRender();
    }

    static Aero_RenderLod lod(double x, double y, double z, double radius,
            double animated, double maximum, boolean budget) {
        Aero_RenderLod result = Aero_RenderDistanceCulling.lodRelative(
            x, y, z, Aero_RenderFrame.viewDistance(), radius, animated, maximum);
        if (!result.shouldRender() || culledRelative(x, y, z, radius)) return Aero_RenderLod.CULLED;
        return budget ? Aero_AnimationRenderBudget.apply(
            result, x, y, z, radius, hysteresisKey(x, y, z)) : result;
    }

    static double blockEntityDistance(BlockEntity blockEntity, double cameraX, double cameraY,
            double cameraZ, double radius, double maximum) {
        int view = Aero_RenderFrame.viewDistance();
        double dx = blockEntity.x + 0.5d - cameraX;
        double dy = blockEntity.y + 0.5d - cameraY;
        double dz = blockEntity.z + 0.5d - cameraZ;
        if (!Aero_ChunkVisibility.isBlockChunkVisible(blockEntity.x, blockEntity.z, radius)
                || culledBlockEntity(blockEntity, dx, dy, dz, radius)) {
            return Double.POSITIVE_INFINITY;
        }
        return Aero_RenderDistanceCulling.normalizedDistanceForVanillaDispatcher(
            Aero_RenderDistanceCulling.squaredDistance(dx, dy, dz), view, radius, maximum);
    }

    private static boolean culledRelative(double x, double y, double z, double radius) {
        double distance = x * x + y * y + z * z;
        return Aero_SmallObjectCull.isTooSmall(distance, radius)
            || !Aero_FrustumCull.isBlockEntityViewVisible(x, y, z, radius)
            || !Aero_FrustumCull.isLikelyVisibleWithRadius(x, y, z, radius)
            || !passesRealFrustum(x, y, z, radius);
    }

    private static boolean culledBlockEntity(BlockEntity blockEntity,
            double x, double y, double z, double radius) {
        double distance = x * x + y * y + z * z;
        double centerX = blockEntity.x + 0.5d;
        double centerY = blockEntity.y + 0.5d;
        double centerZ = blockEntity.z + 0.5d;
        return Aero_SmallObjectCull.isTooSmall(distance, radius)
            || !Aero_FrustumCull.isBlockEntityViewVisible(x, y, z, radius)
            || !Aero_FrustumCull.isLikelyVisibleWithRadius(x, y, z, radius)
            || !passesRealFrustumAbsolute(centerX, centerY, centerZ, radius);
    }

    private static boolean passesRealFrustum(double x, double y, double z, double radius) {
        if (!Aero_RenderFrame.cameraValid()) return true;
        double cx = Aero_RenderFrame.cameraX() + x;
        double cy = Aero_RenderFrame.cameraY() + y;
        double cz = Aero_RenderFrame.cameraZ() + z;
        return passesRealFrustumAbsolute(cx, cy, cz, radius);
    }

    private static boolean passesRealFrustumAbsolute(
            double x, double y, double z, double radius) {
        if (!Aero_RenderFrame.cameraValid()) return true;
        double resolved = radius > 0.0d ? radius : 0.5d;
        return Aero_Frustum6Plane.isVisibleAABB(x - resolved, y - resolved, z - resolved,
            x + resolved, y + resolved, z + resolved);
    }

    private static long hysteresisKey(double x, double y, double z) {
        if (!Aero_RenderFrame.cameraValid()) return Aero_AnimationRenderBudget.NO_HYSTERESIS_KEY;
        int blockX = (int) Math.floor(Aero_RenderFrame.cameraX() + x);
        int blockY = (int) Math.floor(Aero_RenderFrame.cameraY() + y);
        int blockZ = (int) Math.floor(Aero_RenderFrame.cameraZ() + z);
        long key = ((long) blockX & 0x3FFFFFL) << 42
            | ((long) blockY & 0xFFFFFL) << 22 | ((long) blockZ & 0x3FFFFFL);
        return key == Aero_AnimationRenderBudget.NO_HYSTERESIS_KEY
            ? key ^ 0x9E3779B97F4A7C15L : key;
    }
}
