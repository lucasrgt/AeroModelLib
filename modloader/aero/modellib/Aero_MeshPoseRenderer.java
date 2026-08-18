package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

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
 * AeroMesh Renderer by lucasrgt - aerocoding.dev
 * Renders OBJ models (Aero_MeshModel) using GL_TRIANGLES.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time
 *   - setColorOpaque_F called 4× per draw (vs N× in the naive approach)
 *   - Coordinate division by `sc` replaced with single multiplication
 *   - Smooth-light path samples each (x,z) world column once per draw and
 *     bilinearly interpolates from the cache (vs 4 lookups per triangle)
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc)
 *   - Bone/pivot resolution is memoized per (clip identity) on the model,
 *     so the per-group HashMap and linear-scan lookups happen only when
 *     the active clip changes
 *
 * Static geometry usage (TileEntitySpecialRenderer):
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, rotation, brightness);
 *
 * Animated part usage:
 *   // Render static geometry (everything except the named animated group)
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, 0, brightness);
 *   // Render animated group with per-tick angle + partial tick smoothing
 *   float angle = tile.fanAngle + (tile.isActive ? SPEED * partialTick : 0f);
 *   Aero_MeshRenderer.renderGroupRotated(MODEL, "fan",
 *       d + ox, d1 + oy, d2 + oz, brightness,
 *       pivotX, pivotY, pivotZ,   // pivot in model space (block units)
 *       angle, 0, 1, 0);          // angle + axis (Y-axis spin)
 *
 * Inventory usage:
 *   Aero_MeshRenderer.renderInventory(rb, MODEL);
 *
 * NOTE: uses Tessellator with GL_TRIANGLES — only call outside an active
 * startDrawingQuads() block. The TileEntitySpecialRenderer context is safe.
 */
final class Aero_MeshPoseRenderer extends Aero_MeshRendererState {
    private Aero_MeshPoseRenderer() {}

static int prepareAnimatedRender(Aero_MeshModel model,
                                             double x, double y, double z,
                                             float brightness,
                                             Aero_RenderOptions options) {
        Aero_MeshGlStateRenderer.updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return ANIMATED_RENDER_CULLED;

        double distSq = x * x + y * y + z * z;
        double animatedRadius = Aero_AnimationTickLOD.recommendedAnimatedDistance(
            Aero_RenderDistance.currentViewDistance());
        if (distSq > animatedRadius * animatedRadius) {
            Aero_MeshRenderer.renderModelAtRestPreculled(model, x, y, z, 0f, brightness, options);
            return ANIMATED_RENDER_STATIC_DONE;
        }
        return ANIMATED_RENDER_ACTIVE;
    }

static void applyPose(Aero_BoneRenderPose pose) {
        GL11.glTranslatef(pose.pivotX + pose.offsetX,
            pose.pivotY + pose.offsetY,
            pose.pivotZ + pose.offsetZ);
        GL11.glRotatef(pose.rotZ, 0f, 0f, 1f);
        GL11.glRotatef(pose.rotY, 0f, 1f, 0f);
        GL11.glRotatef(pose.rotX, 1f, 0f, 0f);
        if (pose.scaleX != 1f || pose.scaleY != 1f || pose.scaleZ != 1f) {
            GL11.glScalef(pose.scaleX, pose.scaleY, pose.scaleZ);
        }
        GL11.glTranslatef(-pose.pivotX, -pose.pivotY, -pose.pivotZ);
    }

static Aero_BoneRenderPose applyPoseChain(Aero_MeshModel.BoneRef rf,
                                                       Aero_BoneRenderPose[] pool) {
        return Aero_MeshPoseRenderer.applyPoseChain(rf, pool, -1);
    }

static Aero_BoneRenderPose applyPoseChain(Aero_MeshModel.BoneRef rf,
                                                       Aero_BoneRenderPose[] pool,
                                                       int maxDepth) {
        int len = rf.ancestorBoneIdx.length;
        if (len == 0) return null;
        if (maxDepth >= 0 && len > maxDepth) len = maxDepth;
        if (len == 0) return null;
        Aero_BoneRenderPose deepest = null;
        for (int c = 0; c < len; c++) {
            Aero_BoneRenderPose pose = pool[rf.ancestorBoneIdx[c]];
            Aero_MeshPoseRenderer.applyPose(pose);
            deepest = pose;
        }
        return deepest;
    }

static int skeletalPoseDepthLimit(double x, double y, double z,
                                              Aero_ProceduralPose proceduralPose,
                                              Aero_IkChain[] ikChains,
                                              Aero_MorphState morphState) {
        if (!SKELETAL_LOD_ENABLED) return -1;
        if (proceduralPose != null) return -1;
        if (ikChains != null && ikChains.length > 0) return -1;
        if (morphState != null && !morphState.isEmpty()) return -1;
        double distSq = x * x + y * y + z * z;
        return distSq >= SKELETAL_LOD_DISTANCE * SKELETAL_LOD_DISTANCE
            ? SKELETAL_LOD_DEPTH
            : -1;
    }

static Aero_BoneRenderPose[] newPosePool(int size) {
        Aero_BoneRenderPose[] pool = new Aero_BoneRenderPose[size];
        for (int i = 0; i < size; i++) pool[i] = new Aero_BoneRenderPose();
        return pool;
    }

static Aero_BoneRenderPose[] ensurePoolSize(int size) {
        if (POSE_POOL.length >= size) return POSE_POOL;
        Aero_BoneRenderPose[] grown = new Aero_BoneRenderPose[size];
        System.arraycopy(POSE_POOL, 0, grown, 0, POSE_POOL.length);
        for (int i = POSE_POOL.length; i < size; i++) grown[i] = new Aero_BoneRenderPose();
        POSE_POOL = grown;
        return grown;
    }

static void runIkChains(Aero_IkChain[] chains,
                                     Aero_AnimationClip clip,
                                     Aero_AnimationBundle bundle,
                                     Aero_BoneRenderPose[] pool) {
        float[] target = new float[3];
        for (int c = 0; c < chains.length; c++) {
            Aero_IkChain chain = chains[c];
            if (chain == null) continue;
            String[] names = chain.getBoneChain();
            if (names == null || names.length < 2) continue;

            int[] boneIdx = new int[names.length];
            float[][] pivots = new float[names.length][];
            boolean valid = true;
            for (int i = 0; i < names.length; i++) {
                int idx = clip.indexOfBone(names[i]);
                if (idx < 0) { valid = false; break; }
                boneIdx[i] = idx;
                pivots[i] = bundle.pivotOrZero(names[i]);
            }
            if (!valid) continue;

            if (!chain.resolveTargetInto(target)) continue;
            Aero_CCDSolver.solve(boneIdx, pivots, pool, target,
                Aero_CCDSolver.DEFAULT_TOLERANCE);
        }
    }
}
