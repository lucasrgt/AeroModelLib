package aero.modellib;


import aero.modellib.optimization.OptimizationRef;
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
@OptimizationRef({"aero.animation.ik-scratch-reuse"})
final class Aero_MeshPoseRenderer extends Aero_MeshRendererState {
    private Aero_MeshPoseRenderer() {}

static int prepareAnimatedRender(Aero_MeshModel model,
                                             double x, double y, double z,
                                             float brightness,
                                             Aero_RenderOptions options) {
        Aero_MeshGlStateRenderer.updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return ANIMATED_RENDER_CULLED;

        // Distance LOD: beyond the recommended animated radius (scales with
        // player render-distance setting), fall through to the display-list
        // at-rest path. Eliminates per-frame Tessellator cost for far entities
        // without requiring every renderer to plumb its own lodRelative call.
        // The threshold uses Aero_AnimationTickLOD's policy so tick + render
        // LOD line up at the same distance.
        double distSq = x * x + y * y + z * z;
        double animatedRadius = Aero_AnimationTickLOD.recommendedAnimatedDistance(
            Aero_RenderDistance.currentViewDistance());
        if (distSq > animatedRadius * animatedRadius) {
            Aero_MeshRenderer.renderModelAtRest(model, x, y, z, 0f, brightness, options);
            return ANIMATED_RENDER_STATIC_DONE;
        }
        return ANIMATED_RENDER_ACTIVE;
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
        float[] target = SCRATCH_IK_TARGET;
        for (int c = 0; c < chains.length; c++) {
            Aero_IkChain chain = chains[c];
            if (chain == null) continue;
            String[] names = chain.getBoneChain();
            if (names == null || names.length < 2) continue;

            int len = names.length;
            int[] boneIdx = (SCRATCH_IK_BONE_IDX != null
                    && SCRATCH_IK_BONE_IDX.length == len)
                ? SCRATCH_IK_BONE_IDX
                : (SCRATCH_IK_BONE_IDX = new int[len]);
            float[][] pivots = (SCRATCH_IK_PIVOTS != null
                    && SCRATCH_IK_PIVOTS.length == len)
                ? SCRATCH_IK_PIVOTS
                : (SCRATCH_IK_PIVOTS = new float[len][]);
            boolean valid = true;
            for (int i = 0; i < len; i++) {
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
}
