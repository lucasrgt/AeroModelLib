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
final class Aero_EntityRenderSupport {
    private Aero_EntityRenderSupport() {}

static void beginEntityTransform(double x, double y, double z,
                                             float yaw, Aero_EntityModelTransform transform) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(transform.modelYaw(yaw), 0f, 1f, 0f);
        if (transform.scale != 1f) {
            GL11.glScalef(transform.scale, transform.scale, transform.scale);
        }
    }

static void requireTransform(Aero_EntityModelTransform transform) {
        if (transform == null) throw new IllegalArgumentException("transform must not be null");
    }

static void requireSpec(Aero_ModelSpec spec) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
    }

static void requireOptions(Aero_RenderOptions options) {
        if (options == null) throw new IllegalArgumentException("options must not be null");
    }

    static boolean shouldRender(double x, double y, double z,
            Aero_EntityModelTransform transform) {
        return Aero_RenderDistance.shouldRenderRelative(x, y, z,
            transform.cullingRadius, transform.maxRenderDistance);
    }
}
