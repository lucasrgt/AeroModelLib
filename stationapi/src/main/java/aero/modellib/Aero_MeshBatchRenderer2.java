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
final class Aero_MeshBatchRenderer2 extends Aero_MeshRendererState {
    private Aero_MeshBatchRenderer2() {}

static BatchPlan buildBatchPlan(Aero_MeshModel model,
                                            Aero_AnimationClip clip,
                                            Aero_AnimationBundle bundle,
                                            Aero_MeshModel.NamedGroup[] entries) {
        int[] entryBoneIdx = new int[entries.length];
        int[] drawableTmp = new int[entries.length];
        int drawableCount = 0;
        for (int e = 0; e < entries.length; e++) {
            entryBoneIdx[e] = -1;
            if (Aero_MeshBatchRenderer2.hasGeometry(entries[e])) drawableTmp[drawableCount++] = e;
        }

        boolean batchableFlat = true;
        if (bundle != null) {
            Aero_MeshModel.BoneRef[] refs = model.boneRefsFor(clip, bundle);
            for (int e = 0; e < entries.length; e++) {
                int len = refs[e].ancestorBoneIdx.length;
                if (len > 1) {
                    batchableFlat = false;
                } else if (len == 1) {
                    entryBoneIdx[e] = refs[e].ancestorBoneIdx[0];
                }
            }
        } else if (clip != null) {
            batchableFlat = false;
        }

        int[] drawableEntries = new int[drawableCount];
        System.arraycopy(drawableTmp, 0, drawableEntries, 0, drawableCount);
        return new BatchPlan(batchableFlat, entryBoneIdx, drawableEntries,
            Aero_MeshBatchRenderer.hasStaticGeometry(model));
    }

static boolean hasGeometry(Aero_MeshModel.NamedGroup group) {
        for (int g = 0; g < 4; g++) {
            if (group.tris[g].length > 0) return true;
        }
        return false;
    }

static float emitStaticInstancesBatched(Tessellator tess, float[][] tris, float invSc,
                                                    float bucketFactor,
                                                    Aero_AnimatedBatcher.Batch batch, int count,
                                                    Aero_RenderOptions options,
                                                    float lastBright) {
        for (int i = 0; i < count; i++) {
            float bright = batch.brightnesses[i] * bucketFactor;
            if (bright != lastBright) {
                tess.color(bright * options.tintR, bright * options.tintG,
                           bright * options.tintB, options.alpha);
                lastBright = bright;
            }
            double instX = batch.xs[i], instY = batch.ys[i], instZ = batch.zs[i];
            for (int t = 0; t < tris.length; t++) {
                float[] tri = tris[t];
                tess.vertex(instX + tri[0]*invSc,  instY + tri[1]*invSc,  instZ + tri[2]*invSc,  tri[3],  tri[4]);
                tess.vertex(instX + tri[5]*invSc,  instY + tri[6]*invSc,  instZ + tri[7]*invSc,  tri[8],  tri[9]);
                tess.vertex(instX + tri[10]*invSc, instY + tri[11]*invSc, instZ + tri[12]*invSc, tri[13], tri[14]);
            }
        }
        return lastBright;
    }

static void emitBoneInstanceBatched(Tessellator tess, float[][] tris, float invSc,
                                                 Aero_BoneRenderPose pose,
                                                 double instX, double instY, double instZ) {
        boolean rotIdentity = pose.rotX == 0f && pose.rotY == 0f && pose.rotZ == 0f;
        boolean scaleIdentity = pose.scaleX == 1f && pose.scaleY == 1f && pose.scaleZ == 1f;
        if (rotIdentity) {
            if (scaleIdentity) {
                Aero_MeshBatchRenderer2.emitBoneInstanceBatchedTranslateUv(tess, tris, invSc, pose, instX, instY, instZ);
            } else {
                Aero_MeshBatchRenderer3.emitBoneInstanceBatchedScaleTranslateUv(tess, tris, invSc, pose, instX, instY, instZ);
            }
            return;
        }

        // Pre-compute trig once per (instance, bone). The same matrix
        // applies to every vertex of this bone for this instance.
        final float DEG_TO_RAD = (float) (Math.PI / 180.0);
        float cosX = (float) Math.cos(pose.rotX * DEG_TO_RAD);
        float sinX = (float) Math.sin(pose.rotX * DEG_TO_RAD);
        float cosY = (float) Math.cos(pose.rotY * DEG_TO_RAD);
        float sinY = (float) Math.sin(pose.rotY * DEG_TO_RAD);
        float cosZ = (float) Math.cos(pose.rotZ * DEG_TO_RAD);
        float sinZ = (float) Math.sin(pose.rotZ * DEG_TO_RAD);
        float scaleX = pose.scaleX, scaleY = pose.scaleY, scaleZ = pose.scaleZ;
        float pivotX = pose.pivotX, pivotY = pose.pivotY, pivotZ = pose.pivotZ;
        float postX  = pose.pivotX + pose.offsetX;
        float postY  = pose.pivotY + pose.offsetY;
        float postZ  = pose.pivotZ + pose.offsetZ;
        boolean uvIdentity = pose.uvIsIdentity();
        float uOffset = pose.uOffset, vOffset = pose.vOffset;
        float uScale = pose.uScale, vScale = pose.vScale;

        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            // Apply pose × (vertex × invScale) for each of the 3 vertices.
            // Same transform sequence as Aero_MeshRenderer.applyPose() but
            // composed in CPU: T(-pivot) → S → Rx → Ry → Rz → T(pivot+offset).
            for (int vertex = 0; vertex < 3; vertex++) {
                int off = vertex * 5;
                float lx = tri[off]     * invSc - pivotX;
                float ly = tri[off + 1] * invSc - pivotY;
                float lz = tri[off + 2] * invSc - pivotZ;
                lx *= scaleX; ly *= scaleY; lz *= scaleZ;
                // Rx: y, z change
                float ny = ly * cosX - lz * sinX;
                float nz = ly * sinX + lz * cosX;
                ly = ny; lz = nz;
                // Ry: x, z change
                float nx = lx * cosY + lz * sinY;
                nz       = -lx * sinY + lz * cosY;
                lx = nx; lz = nz;
                // Rz: x, y change
                nx = lx * cosZ - ly * sinZ;
                ny = lx * sinZ + ly * cosZ;
                lx = nx; ly = ny;
                float u = tri[off + 3];
                float v = tri[off + 4];
                if (!uvIdentity) {
                    u = u * uScale + uOffset;
                    v = v * vScale + vOffset;
                }
                tess.vertex(instX + lx + postX, instY + ly + postY, instZ + lz + postZ,
                            u, v);
            }
        }
    }

static void emitBoneInstanceBatchedTranslateUv(Tessellator tess, float[][] tris,
                                                           float invSc,
                                                           Aero_BoneRenderPose pose,
                                                           double instX, double instY, double instZ) {
        double baseX = instX + pose.offsetX;
        double baseY = instY + pose.offsetY;
        double baseZ = instZ + pose.offsetZ;
        boolean uvIdentity = pose.uvIsIdentity();
        float uOffset = pose.uOffset, vOffset = pose.vOffset;
        float uScale = pose.uScale, vScale = pose.vScale;

        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            if (uvIdentity) {
                tess.vertex(baseX + tri[0]*invSc,  baseY + tri[1]*invSc,  baseZ + tri[2]*invSc,  tri[3],  tri[4]);
                tess.vertex(baseX + tri[5]*invSc,  baseY + tri[6]*invSc,  baseZ + tri[7]*invSc,  tri[8],  tri[9]);
                tess.vertex(baseX + tri[10]*invSc, baseY + tri[11]*invSc, baseZ + tri[12]*invSc, tri[13], tri[14]);
            } else {
                tess.vertex(baseX + tri[0]*invSc,  baseY + tri[1]*invSc,  baseZ + tri[2]*invSc,
                    tri[3] * uScale + uOffset,  tri[4] * vScale + vOffset);
                tess.vertex(baseX + tri[5]*invSc,  baseY + tri[6]*invSc,  baseZ + tri[7]*invSc,
                    tri[8] * uScale + uOffset,  tri[9] * vScale + vOffset);
                tess.vertex(baseX + tri[10]*invSc, baseY + tri[11]*invSc, baseZ + tri[12]*invSc,
                    tri[13] * uScale + uOffset, tri[14] * vScale + vOffset);
            }
        }
    }
}
