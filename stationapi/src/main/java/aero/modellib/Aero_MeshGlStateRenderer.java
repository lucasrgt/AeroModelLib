package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationPoseResolver;
import aero.modellib.animation.Aero_AnimationStack;
import aero.modellib.animation.graph.Aero_AnimationGraph;
import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_AnimationTickLOD;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BonePageLists;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_CCDSolver;
import aero.modellib.skeletal.Aero_IkChain;
import aero.modellib.skeletal.Aero_MorphState;
import aero.modellib.skeletal.Aero_MorphTarget;
import aero.modellib.skeletal.Aero_ProceduralPose;
import aero.modellib.util.Aero_Profiler;

/**
 * AeroMesh Renderer (StationAPI/Yarn port). Same algorithm as the ModLoader
 * version, with Yarn-mapped Tessellator + World API.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time.
 *   - Tessellator color called 4× per draw (vs N× naive).
 *   - Coordinate division by `sc` replaced with single multiplication.
 *   - Smooth-light path samples each (x,z) world column once per draw and
 *     bilinearly interpolates from the cache (vs 4 lookups per triangle).
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc).
 *   - Bone/pivot resolution memoized per (clip identity) on the model.
 */
final class Aero_MeshGlStateRenderer extends Aero_MeshRendererState {
    private Aero_MeshGlStateRenderer() {}

static void updateCameraForwardFromPlayer() {
        Aero_RenderDistance.updateCameraForwardFromPlayer();
    }

static void beginMeshState() {
        Aero_MeshRenderer.beginMeshState(Aero_RenderOptions.DEFAULT);
    }

static double doubleProperty(String name, double fallback, double min, double max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            double parsed = Double.parseDouble(raw.trim());
            return parsed >= min && parsed <= max ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

static void beginMeshState(Aero_RenderOptions options) {
        GL11.glPushAttrib(MESH_ATTRIB_BITS);
        if (options.cullFaces) {
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        Aero_MeshGlStateRenderer.applyBlendMode(options.blend);
        if (options.alphaClip > 0f) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, options.alphaClip);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
        if (options.depthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

static void applyBlendMode(Aero_MeshBlendMode mode) {
        switch (mode) {
            case ALPHA:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                break;
            case ADDITIVE:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                break;
            default:
                GL11.glDisable(GL11.GL_BLEND);
                break;
        }
    }

static void endMeshState() {
        GL11.glPopAttrib();
    }

static void applyRotation(float rotation) {
        if (rotation != 0) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        }
    }
}
