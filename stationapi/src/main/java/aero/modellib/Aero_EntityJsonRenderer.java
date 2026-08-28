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
final class Aero_EntityJsonRenderer {
    private Aero_EntityJsonRenderer() {}

static void render(Aero_JsonModel model, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        Aero_EntityModelRenderer.render(model, entity, x, y, z, yaw, partialTick, Aero_EntityModelTransform.DEFAULT);
    }

static void render(Aero_JsonModel model, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick,
                              Aero_EntityModelTransform transform) {
        Aero_EntityModelRenderer.render(model, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), transform);
    }

static void render(Aero_JsonModel model,
                              double x, double y, double z,
                              float yaw, float brightness) {
        Aero_EntityModelRenderer.render(model, x, y, z, yaw, brightness, Aero_EntityModelTransform.DEFAULT);
    }

static void render(Aero_JsonModel model,
                              double x, double y, double z,
                              float yaw, float brightness,
                              Aero_EntityModelTransform transform) {
        Aero_EntityRenderSupport.requireTransform(transform);
        if (!Aero_EntityRenderSupport.shouldRender(x, y, z, transform)) return;
        Aero_EntityRenderSupport.beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_JsonModelRenderer.renderModel(model, transform.offsetX, transform.offsetY, transform.offsetZ, 0f, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }

static void renderJsonPreculled(Aero_JsonModel model,
                                            double x, double y, double z,
                                            float yaw, float brightness,
                                            Aero_EntityModelTransform transform) {
        Aero_EntityRenderSupport.beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_JsonModelRenderer.renderModel(model,
                transform.offsetX, transform.offsetY, transform.offsetZ, 0f, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
