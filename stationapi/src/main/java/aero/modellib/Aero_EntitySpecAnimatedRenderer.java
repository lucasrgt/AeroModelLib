package aero.modellib;

import net.minecraft.entity.Entity;
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
 * Texture binding stays in the caller's EntityRenderer. This class only owns
 * entity-origin translation, yaw conversion, optional scale and delegating to
 * the existing JSON/Mesh renderers.
 */
final class Aero_EntitySpecAnimatedRenderer {
    private Aero_EntitySpecAnimatedRenderer() {}



static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick) {
        Aero_EntityModelRenderer.renderAnimated(spec, state, entity, x, y, z, yaw, partialTick, null);
    }



static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick,
                                      Aero_ProceduralPose proceduralPose) {
        Aero_EntityRenderSupport.requireSpec(spec);
        Aero_EntityModelRenderer.renderAnimated(spec, state, x, y, z, yaw,
            entity.getBrightnessAtEyes(partialTick), partialTick, proceduralPose);
    }



static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick) {
        Aero_EntityModelRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }



static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_ProceduralPose proceduralPose) {
        Aero_EntityModelRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT,
            proceduralPose);
    }



static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_RenderOptions options) {
        Aero_EntityModelRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick, options, null);
    }



static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_RenderOptions options,
                                      Aero_ProceduralPose proceduralPose) {
        Aero_EntityRenderSupport.requireSpec(spec);
        Aero_EntityRenderSupport.requireOptions(options);
        if (!spec.isMesh()) {
            throw new IllegalStateException("animated rendering requires a mesh spec");
        }
        Aero_EntityModelRenderer.renderAnimated(spec.getMeshModel(), state, x, y, z, yaw, brightness, partialTick,
            spec.getEntityTransform(), options, proceduralPose);
    }



static void renderAnimatedPreculled(Aero_ModelSpec spec,
                                                Aero_AnimationPlayback state,
                                                double x, double y, double z,
                                                float yaw, float brightness, float partialTick,
                                                Aero_RenderOptions options,
                                                Aero_ProceduralPose proceduralPose) {
        if (!spec.isMesh()) {
            throw new IllegalStateException("animated rendering requires a mesh spec");
        }
        if (state == null) throw new IllegalArgumentException("state must not be null");
        Aero_EntityMeshAnimatedRenderer.renderMeshAnimatedPreculled(spec.getMeshModel(), state.getBundle(), state.getDef(), state,
            x, y, z, yaw, brightness, partialTick, spec.getEntityTransform(), options,
            proceduralPose);
    }
}
