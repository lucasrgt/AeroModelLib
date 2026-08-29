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
final class Aero_MeshBatchRenderer3 extends Aero_MeshRendererState {
    private Aero_MeshBatchRenderer3() {}

static void emitBoneInstanceBatchedScaleTranslateUv(Tessellator tess, float[][] tris,
                                                                float invSc,
                                                                Aero_BoneRenderPose pose,
                                                                double instX, double instY, double instZ) {
        float scaleX = pose.scaleX, scaleY = pose.scaleY, scaleZ = pose.scaleZ;
        float pivotX = pose.pivotX, pivotY = pose.pivotY, pivotZ = pose.pivotZ;
        float postX = pose.pivotX + pose.offsetX;
        float postY = pose.pivotY + pose.offsetY;
        float postZ = pose.pivotZ + pose.offsetZ;
        boolean uvIdentity = pose.uvIsIdentity();
        float uOffset = pose.uOffset, vOffset = pose.vOffset;
        float uScale = pose.uScale, vScale = pose.vScale;

        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            for (int vertex = 0; vertex < 3; vertex++) {
                int off = vertex * 5;
                float lx = (tri[off]     * invSc - pivotX) * scaleX + postX;
                float ly = (tri[off + 1] * invSc - pivotY) * scaleY + postY;
                float lz = (tri[off + 2] * invSc - pivotZ) * scaleZ + postZ;
                float u = tri[off + 3];
                float v = tri[off + 4];
                if (!uvIdentity) {
                    u = u * uScale + uOffset;
                    v = v * vScale + vOffset;
                }
                tess.vertex(instX + lx, instY + ly, instZ + lz, u, v);
            }
        }
    }

static void emitBoneInstanceBatchedRest(Tessellator tess, float[][] tris, float invSc,
                                                     double instX, double instY, double instZ) {
        if (Aero_TessellatorRestBulkWriter.write(
                tris, invSc, instX, instY, instZ)) return;
        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            tess.vertex(instX + tri[0]*invSc,  instY + tri[1]*invSc,  instZ + tri[2]*invSc,  tri[3],  tri[4]);
            tess.vertex(instX + tri[5]*invSc,  instY + tri[6]*invSc,  instZ + tri[7]*invSc,  tri[8],  tri[9]);
            tess.vertex(instX + tri[10]*invSc, instY + tri[11]*invSc, instZ + tri[12]*invSc, tri[13], tri[14]);
        }
    }

static void drainAsUnbatched(Aero_AnimatedBatcher.Batch batch, int count) {
        Aero_AnimatedBatcher.bindBatchTexture(batch);
        for (int i = 0; i < count; i++) {
            Aero_AnimationClip clip = batch.states[i] != null
                ? batch.states[i].getCurrentClip()
                : null;
            if (clip != null && clip.hasUvAnimation()) {
                Aero_MeshAnimatedRenderer2.renderAnimatedInternal(batch.model, batch.bundles[i], batch.defs[i], batch.states[i],
                    batch.xs[i], batch.ys[i], batch.zs[i],
                    batch.brightnesses[i], batch.partialTicks[i], batch.options[i],
                    null, null, null, false, true);
            } else {
                Aero_MeshRenderer.renderAnimated(batch.model, batch.bundles[i], batch.defs[i], batch.states[i],
                    batch.xs[i], batch.ys[i], batch.zs[i],
                    batch.brightnesses[i], batch.partialTicks[i], batch.options[i]);
            }
        }
    }
}
