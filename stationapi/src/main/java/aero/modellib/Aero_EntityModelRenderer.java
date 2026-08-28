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
public final class Aero_EntityModelRenderer {
    private Aero_EntityModelRenderer() {}

    public static void render(Aero_ModelSpec spec, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntitySpecRenderer.render(spec, entity, x, y, z, yaw, partialTick); }
    public static void render(Aero_ModelSpec spec, double x, double y, double z, float yaw, float brightness, float partialTick) { Aero_EntitySpecRenderer.render(spec, x, y, z, yaw, brightness, partialTick); }
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntitySpecRenderer.render(spec, state, entity, x, y, z, yaw, partialTick); }
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick, Aero_ProceduralPose proceduralPose) { Aero_EntitySpecRenderer.render(spec, state, entity, x, y, z, yaw, partialTick, proceduralPose); }
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick) { Aero_EntitySpecRenderer.render(spec, state, x, y, z, yaw, brightness, partialTick); }
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_RenderOptions options) { Aero_EntitySpecRenderer.render(spec, state, x, y, z, yaw, brightness, partialTick, options); }
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state, Aero_RenderLod lod, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntitySpecRenderer.render(spec, state, lod, entity, x, y, z, yaw, partialTick); }
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state, Aero_RenderLod lod, double x, double y, double z, float yaw, float brightness, float partialTick) { Aero_EntitySpecRenderer.render(spec, state, lod, x, y, z, yaw, brightness, partialTick); }
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state, Aero_RenderLod lod, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_RenderOptions options) { Aero_EntitySpecRenderer.render(spec, state, lod, x, y, z, yaw, brightness, partialTick, options); }
    public static void renderAtRest(Aero_ModelSpec spec, double x, double y, double z, float yaw, float brightness) { Aero_EntitySpecRenderer.renderAtRest(spec, x, y, z, yaw, brightness); }
    public static void renderAtRest(Aero_ModelSpec spec, double x, double y, double z, float yaw, float brightness, Aero_RenderOptions options) { Aero_EntitySpecRenderer.renderAtRest(spec, x, y, z, yaw, brightness, options); }
    public static void renderAnimated(Aero_ModelSpec spec, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntitySpecAnimatedRenderer.renderAnimated(spec, state, entity, x, y, z, yaw, partialTick); }
    public static void renderAnimated(Aero_ModelSpec spec, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick, Aero_ProceduralPose proceduralPose) { Aero_EntitySpecAnimatedRenderer.renderAnimated(spec, state, entity, x, y, z, yaw, partialTick, proceduralPose); }
    public static void renderAnimated(Aero_ModelSpec spec, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick) { Aero_EntitySpecAnimatedRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick); }
    public static void renderAnimated(Aero_ModelSpec spec, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_ProceduralPose proceduralPose) { Aero_EntitySpecAnimatedRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick, proceduralPose); }
    public static void renderAnimated(Aero_ModelSpec spec, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_RenderOptions options) { Aero_EntitySpecAnimatedRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick, options); }
    public static void renderAnimated(Aero_ModelSpec spec, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose) { Aero_EntitySpecAnimatedRenderer.renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick, options, proceduralPose); }
    public static void render(Aero_JsonModel model, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntityJsonRenderer.render(model, entity, x, y, z, yaw, partialTick); }
    public static void render(Aero_JsonModel model, Entity entity, double x, double y, double z, float yaw, float partialTick, Aero_EntityModelTransform transform) { Aero_EntityJsonRenderer.render(model, entity, x, y, z, yaw, partialTick, transform); }
    public static void render(Aero_JsonModel model, double x, double y, double z, float yaw, float brightness) { Aero_EntityJsonRenderer.render(model, x, y, z, yaw, brightness); }
    public static void render(Aero_JsonModel model, double x, double y, double z, float yaw, float brightness, Aero_EntityModelTransform transform) { Aero_EntityJsonRenderer.render(model, x, y, z, yaw, brightness, transform); }
    public static void render(Aero_MeshModel model, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntityMeshRenderer.render(model, entity, x, y, z, yaw, partialTick); }
    public static void render(Aero_MeshModel model, Entity entity, double x, double y, double z, float yaw, float partialTick, Aero_EntityModelTransform transform) { Aero_EntityMeshRenderer.render(model, entity, x, y, z, yaw, partialTick, transform); }
    public static void render(Aero_MeshModel model, double x, double y, double z, float yaw, float brightness) { Aero_EntityMeshRenderer.render(model, x, y, z, yaw, brightness); }
    public static void render(Aero_MeshModel model, double x, double y, double z, float yaw, float brightness, Aero_EntityModelTransform transform) { Aero_EntityMeshRenderer.render(model, x, y, z, yaw, brightness, transform); }
    public static void render(Aero_MeshModel model, double x, double y, double z, float yaw, float brightness, Aero_EntityModelTransform transform, Aero_RenderOptions options) { Aero_EntityMeshRenderer.render(model, x, y, z, yaw, brightness, transform, options); }
    public static void renderAtRest(Aero_MeshModel model, Entity entity, double x, double y, double z, float yaw, float partialTick, Aero_EntityModelTransform transform) { Aero_EntityMeshRenderer.renderAtRest(model, entity, x, y, z, yaw, partialTick, transform); }
    public static void renderAtRest(Aero_MeshModel model, double x, double y, double z, float yaw, float brightness, Aero_EntityModelTransform transform) { Aero_EntityMeshRenderer.renderAtRest(model, x, y, z, yaw, brightness, transform); }
    public static void renderAtRest(Aero_MeshModel model, double x, double y, double z, float yaw, float brightness, Aero_EntityModelTransform transform, Aero_RenderOptions options) { Aero_EntityMeshRenderer.renderAtRest(model, x, y, z, yaw, brightness, transform, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, state, entity, x, y, z, yaw, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick, Aero_EntityModelTransform transform) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, state, entity, x, y, z, yaw, partialTick, transform); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, state, x, y, z, yaw, brightness, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_EntityModelTransform transform) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, state, x, y, z, yaw, brightness, partialTick, transform); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_EntityModelTransform transform, Aero_RenderOptions options) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, state, x, y, z, yaw, brightness, partialTick, transform, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_EntityModelTransform transform, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, state, x, y, z, yaw, brightness, partialTick, transform, options, proceduralPose); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, bundle, def, state, entity, x, y, z, yaw, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, Entity entity, double x, double y, double z, float yaw, float partialTick, Aero_EntityModelTransform transform) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, bundle, def, state, entity, x, y, z, yaw, partialTick, transform); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_EntityModelTransform transform) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick, transform); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_EntityModelTransform transform, Aero_RenderOptions options) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick, transform, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float yaw, float brightness, float partialTick, Aero_EntityModelTransform transform, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose) { Aero_EntityMeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick, transform, options, proceduralPose); }
}
