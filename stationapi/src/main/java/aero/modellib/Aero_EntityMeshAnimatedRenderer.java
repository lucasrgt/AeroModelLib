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
final class Aero_EntityMeshAnimatedRenderer {
    private Aero_EntityMeshAnimatedRenderer() {}



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick) {
        Aero_EntityModelRenderer.renderAnimated(model, state, entity, x, y, z, yaw, partialTick, Aero_EntityModelTransform.DEFAULT);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick,
                                      Aero_EntityModelTransform transform) {
        Aero_EntityModelRenderer.renderAnimated(model, state, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), partialTick, transform);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick) {
        Aero_EntityModelRenderer.renderAnimated(model, state, x, y, z, yaw, brightness, partialTick, Aero_EntityModelTransform.DEFAULT);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform) {
        Aero_EntityModelRenderer.renderAnimated(model, state, x, y, z, yaw, brightness, partialTick,
            transform, Aero_RenderOptions.DEFAULT);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options) {
        Aero_EntityModelRenderer.renderAnimated(model, state, x, y, z, yaw, brightness, partialTick,
            transform, options, null);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options,
                                      Aero_ProceduralPose proceduralPose) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        Aero_EntityModelRenderer.renderAnimated(model, state.getBundle(), state.getDef(), state,
            x, y, z, yaw, brightness, partialTick, transform, options, proceduralPose);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick) {
        Aero_EntityModelRenderer.renderAnimated(model, bundle, def, state, entity, x, y, z, yaw, partialTick, Aero_EntityModelTransform.DEFAULT);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick,
                                      Aero_EntityModelTransform transform) {
        Aero_EntityModelRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), partialTick, transform);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick) {
        Aero_EntityModelRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick, Aero_EntityModelTransform.DEFAULT);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform) {
        Aero_EntityModelRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick,
            transform, Aero_RenderOptions.DEFAULT);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options) {
        Aero_EntityModelRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick,
            transform, options, null);
    }



static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options,
                                      Aero_ProceduralPose proceduralPose) {
        Aero_EntityRenderSupport.requireTransform(transform);
        if (!Aero_EntityRenderSupport.shouldRender(x, y, z, transform)) return;
        Aero_EntityRenderSupport.beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_MeshRenderer.renderAnimated(model, bundle, def, state,
                transform.offsetX, transform.offsetY, transform.offsetZ,
                brightness, partialTick, options, proceduralPose);
        } finally {
            GL11.glPopMatrix();
        }
    }



static void renderMeshAnimatedPreculled(Aero_MeshModel model,
                                                    Aero_AnimationBundle bundle,
                                                    Aero_AnimationDefinition def,
                                                    Aero_AnimationPlayback state,
                                                    double x, double y, double z,
                                                    float yaw, float brightness, float partialTick,
                                                    Aero_EntityModelTransform transform,
                                                    Aero_RenderOptions options,
                                                    Aero_ProceduralPose proceduralPose) {
        Aero_EntityRenderSupport.beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_MeshRenderer.renderAnimatedPreculled(model, bundle, def, state,
                transform.offsetX, transform.offsetY, transform.offsetZ,
                brightness, partialTick, options, proceduralPose);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
