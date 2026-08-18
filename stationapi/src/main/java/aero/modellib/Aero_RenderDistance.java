package aero.modellib;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import aero.modellib.model.Aero_ModelSpec;
import aero.modellib.render.Aero_EntityModelTransform;
import aero.modellib.render.Aero_RenderDistanceCulling;
import aero.modellib.render.Aero_RenderLod;

/** StationAPI facade for frame-cached Aero render-distance decisions. */
public final class Aero_RenderDistance {
    private Aero_RenderDistance() {}

    public static void beginRenderFrame() { Aero_RenderFrame.begin(); }
    public static int currentViewDistance() { return Aero_RenderFrame.viewDistance(); }
    public static double currentBlockRadius() {
        return Aero_RenderDistanceCulling.blockRadiusForViewDistance(currentViewDistance());
    }
    public static boolean shouldRenderRelative(double x, double y, double z, double radius) {
        return Aero_RenderVisibility.shouldRender(x, y, z, radius,
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }
    public static boolean shouldRenderRelative(double x, double y, double z,
            double radius, double maximum) {
        return Aero_RenderVisibility.shouldRender(x, y, z, radius, maximum);
    }
    public static Aero_RenderLod lodRelative(double x, double y, double z,
            double radius, double animated) {
        return Aero_RenderVisibility.lod(x, y, z, radius, animated,
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS, true);
    }
    public static Aero_RenderLod lodRelative(double x, double y, double z,
            double radius, double animated, double maximum) {
        return Aero_RenderVisibility.lod(x, y, z, radius, animated, maximum, true);
    }
    static Aero_RenderLod lodRelativeNoAnimationBudget(double x, double y, double z,
            double radius, double animated, double maximum) {
        return Aero_RenderVisibility.lod(x, y, z, radius, animated, maximum, false);
    }
    public static Aero_RenderLod lodRelative(Aero_ModelSpec spec, double x, double y, double z) {
        requireSpec(spec);
        Aero_EntityModelTransform transform = spec.getEntityTransform();
        return lodRelative(x, y, z, transform.cullingRadius,
            spec.getAnimatedDistanceBlocks(), transform.maxRenderDistance);
    }
    public static void updateCameraForwardFromPlayer() { Aero_RenderFrame.ensure(); }
    public static PlayerEntity getCachedLocalPlayer() { return Aero_RenderFrame.player(); }
    static boolean hasCachedCamera() { return Aero_RenderFrame.cameraValid(); }
    static double cachedCameraX() { return Aero_RenderFrame.cameraX(); }
    static double cachedCameraY() { return Aero_RenderFrame.cameraY(); }
    static double cachedCameraZ() { return Aero_RenderFrame.cameraZ(); }
    public static boolean shouldRenderFrustumRelative(double x, double y, double z, double radius) {
        updateCameraForwardFromPlayer();
        return aero.modellib.render.Aero_FrustumCull.isLikelyVisibleWithRadius(x, y, z, radius);
    }
    public static double blockEntityDistanceFrom(BlockEntity blockEntity,
            double cameraX, double cameraY, double cameraZ, double radius) {
        return blockEntityDistanceFrom(blockEntity, cameraX, cameraY, cameraZ, radius,
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }
    public static double blockEntityDistanceFrom(BlockEntity blockEntity,
            double cameraX, double cameraY, double cameraZ, double radius, double maximum) {
        return Aero_RenderVisibility.blockEntityDistance(
            blockEntity, cameraX, cameraY, cameraZ, radius, maximum);
    }
    public static void applyEntityRenderDistance(Entity entity, double radius) {
        applyEntityRenderDistance(entity, radius, Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }
    public static void applyEntityRenderDistance(Entity entity, double radius, double maximum) {
        if (entity == null) return;
        double limit = Aero_RenderDistanceCulling.maximumBlockRadiusWithMargin(radius, maximum);
        double side = entity.boundingBox != null ? entity.boundingBox.getAverageSideLength()
            : Math.max(0.25d, Math.max(entity.width, entity.height));
        double multiplier = Aero_RenderDistanceCulling.entityRenderDistanceMultiplier(limit, side);
        if (multiplier > entity.renderDistanceMultiplier) entity.renderDistanceMultiplier = multiplier;
    }
    public static void applyEntityRenderDistance(Entity entity, Aero_ModelSpec spec) {
        requireSpec(spec);
        Aero_EntityModelTransform transform = spec.getEntityTransform();
        applyEntityRenderDistance(entity, transform.cullingRadius, transform.maxRenderDistance);
    }
    private static void requireSpec(Aero_ModelSpec spec) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
    }
}
