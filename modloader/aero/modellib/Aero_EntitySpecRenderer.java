package aero.modellib;

import net.minecraft.src.Entity;
import org.lwjgl.opengl.GL11;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.model.Aero_JsonModel;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ModelSpec;
import aero.modellib.render.Aero_EntityModelTransform;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_ProceduralPose;

/**
 * Entity-oriented render helper for Aero models.
 *
 * The lower-level model renderers are still useful directly, but they use
 * block-style rotations. This helper wraps them in an entity-origin transform
 * and keeps texture binding in the caller, matching vanilla Render classes.
 */
final class Aero_EntitySpecRenderer {
    private Aero_EntitySpecRenderer() {}



static void render(Aero_ModelSpec spec, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        Aero_EntityRenderSupport.requireSpec(spec);
        Aero_EntityModelRenderer.render(spec, x, y, z, yaw, entity.getEntityBrightness(partialTick), partialTick);
    }



static void render(Aero_ModelSpec spec,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick) {
        Aero_EntityRenderSupport.requireSpec(spec);
        if (spec.isJson()) {
            Aero_EntityModelRenderer.render(spec.getJsonModel(), x, y, z, yaw, brightness, spec.getEntityTransform());
        } else {
            Aero_EntityModelRenderer.render(spec.getMeshModelForRelative(x, y, z), x, y, z, yaw, brightness,
                spec.getEntityTransform(), spec.getRenderOptions());
        }
    }



static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        Aero_EntityModelRenderer.render(spec, state, entity, x, y, z, yaw, partialTick, null);
    }



static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick,
                              Aero_ProceduralPose proceduralPose) {
        Aero_EntityRenderSupport.requireSpec(spec);
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(spec, x, y, z);
        if (!lod.shouldRender()) return;
        float brightness = entity.getEntityBrightness(partialTick);
        Aero_EntitySpecRenderer.renderResolvedLod(spec, state, lod, x, y, z, yaw, brightness, partialTick,
            spec.getRenderOptions(), proceduralPose);
    }



static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick) {
        Aero_EntityModelRenderer.render(spec, state, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }



static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick,
                              Aero_RenderOptions options) {
        Aero_EntityRenderSupport.requireSpec(spec);
        Aero_EntityRenderSupport.requireOptions(options);
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(spec, x, y, z);
        Aero_EntitySpecRenderer.renderResolvedLod(spec, state, lod, x, y, z, yaw, brightness, partialTick,
            options, null);
    }



static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Aero_RenderLod lod, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        Aero_EntityRenderSupport.requireSpec(spec);
        if (lod == null) throw new IllegalArgumentException("lod must not be null");
        if (!lod.shouldRender()) return;
        if (!Aero_EntityRenderSupport.shouldRender(x, y, z, spec.getEntityTransform())) return;
        float brightness = entity.getEntityBrightness(partialTick);
        Aero_EntityModelRenderer.render(spec, state, lod, x, y, z, yaw, brightness, partialTick);
    }



static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Aero_RenderLod lod,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick) {
        Aero_EntityModelRenderer.render(spec, state, lod, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }



static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Aero_RenderLod lod,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick,
                              Aero_RenderOptions options) {
        Aero_EntityRenderSupport.requireSpec(spec);
        Aero_EntityRenderSupport.requireOptions(options);
        if (lod == null) throw new IllegalArgumentException("lod must not be null");
        if (!lod.shouldRender()) return;
        if (!Aero_EntityRenderSupport.shouldRender(x, y, z, spec.getEntityTransform())) return;
        if (lod.shouldAnimate()) {
            Aero_EntityModelRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick, options);
        } else {
            Aero_EntityModelRenderer.renderAtRest(spec, x, y, z, yaw, brightness, options);
        }
    }



static void renderAtRest(Aero_ModelSpec spec,
                                    double x, double y, double z,
                                    float yaw, float brightness) {
        Aero_EntityModelRenderer.renderAtRest(spec, x, y, z, yaw, brightness,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }



static void renderAtRest(Aero_ModelSpec spec,
                                    double x, double y, double z,
                                    float yaw, float brightness,
                                    Aero_RenderOptions options) {
        Aero_EntityRenderSupport.requireSpec(spec);
        Aero_EntityRenderSupport.requireOptions(options);
        if (spec.isJson()) {
            Aero_EntityModelRenderer.render(spec.getJsonModel(), x, y, z, yaw, brightness, spec.getEntityTransform());
        } else {
            Aero_EntityModelRenderer.renderAtRest(spec.getMeshModelForRelative(x, y, z), x, y, z, yaw, brightness,
                spec.getEntityTransform(), options);
        }
    }



static void renderResolvedLod(Aero_ModelSpec spec,
                                          Aero_AnimationPlayback state,
                                          Aero_RenderLod lod,
                                          double x, double y, double z,
                                          float yaw, float brightness, float partialTick,
                                          Aero_RenderOptions options,
                                          Aero_ProceduralPose proceduralPose) {
        if (!lod.shouldRender()) return;
        if (lod.shouldAnimate()) {
            Aero_EntitySpecAnimatedRenderer.renderAnimatedPreculled(spec, state, x, y, z, yaw, brightness, partialTick,
                options, proceduralPose);
        } else {
            Aero_EntitySpecRenderer.renderAtRestPreculled(spec, x, y, z, yaw, brightness, options);
        }
    }



static void renderAtRestPreculled(Aero_ModelSpec spec,
                                              double x, double y, double z,
                                              float yaw, float brightness,
                                              Aero_RenderOptions options) {
        if (spec.isJson()) {
            Aero_EntityJsonRenderer.renderJsonPreculled(spec.getJsonModel(), x, y, z, yaw, brightness,
                spec.getEntityTransform());
        } else {
            Aero_EntityMeshRenderer.renderMeshAtRestPreculled(spec.getMeshModelForRelative(x, y, z), x, y, z, yaw, brightness,
                spec.getEntityTransform(), options);
        }
    }
}
