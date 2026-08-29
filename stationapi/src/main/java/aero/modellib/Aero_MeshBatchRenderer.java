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
@aero.modellib.optimization.OptimizationRef({
    "aero.render.animated-batcher", "aero.render.client-vertex-arrays",
    "aero.animation.batch-pose-reuse", "aero.render.batch-transformed-vertex-reuse",
    "aero.render.tessellator-bulk-staging"
})
final class Aero_MeshBatchRenderer extends Aero_MeshRendererState {
    private Aero_MeshBatchRenderer() {}

static void renderAnimatedBatch(Aero_AnimatedBatcher.Batch batch) {
        Aero_MeshModel model = batch.model;
        int count = batch.count;
        if (count == 0) return;

        // Use first instance's options; we assume all in a same-model
        // batch share render options (showcase BERs typically use DEFAULT).
        Aero_RenderOptions options = batch.options[0];
        if (options == null) options = Aero_RenderOptions.DEFAULT;

        Aero_Profiler.start("aero.mesh.renderAnimatedBatch");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            BatchPlan renderPlan = Aero_MeshModelRenderer.batchPlanFor(model, null, batch.bundles[0], entries);
            // Per-instance, per-bone resolved poses. Lazy-resolve on first
            // need; null if instance has no named bones / nested skeleton.
            Aero_BoneRenderPose[][] perInstancePoses = Aero_MeshBatchRenderer.ensureBatchPoseScratch(count);
            boolean[][] perInstancePoseActive = Aero_MeshBatchRenderer.ensureBatchPoseActiveScratch(count);
            int[] poseSources = Aero_BatchPoseReuse.ENABLED
                ? Aero_BatchPoseReuse.beginBatch(count) : null;

            // Pre-resolve poses for all instances. Falls back to the
            // unbatched path (returns false) if any instance has nested
            // ancestor chain.
            boolean canBatch = Aero_BatchPoseResolver.resolve(model, batch, count, entries,
                perInstancePoses, perInstancePoseActive, poseSources);
            if (!canBatch) {
                Aero_MeshBatchRenderer3.drainAsUnbatched(batch, count);
                return;
            }
            Aero_TessellatorBulkWriter.beginBatch(entries, renderPlan.drawableEntries,
                Aero_BatchPoseReuse.sharedCount());

            if (Aero_MeshClientArrayRenderer.ENABLED) {
                Aero_MeshClientArrayRenderer.render(batch, model, options, renderPlan,
                    entries, perInstancePoses, perInstancePoseActive, poseSources, count);
                return;
            }

            Tessellator tess = Tessellator.INSTANCE;
            // Bind the batch's texture once before any tess.draw — at this
            // point in the entity render pass, the texture state is whatever
            // the last unbatched BER bound. Our batched draws need the
            // model's own texture for the duration of all subsequent cycles.
            Aero_AnimatedBatcher.bindBatchTexture(batch);
            Aero_MeshRenderer.beginMeshState(options);
            try {
                // Static (unnamed) groups — no per-instance bone transform,
                // just translate. Single tess cycle for ALL instances.
                if (renderPlan.hasStaticGeometry) {
                    tess.start(GL11.GL_TRIANGLES);
                    // Dedup tess.color across (instance, bucket) — instances
                    // in the same chunk share lighting so consecutive bright
                    // values usually match.
                    float lastBrightStatic = Float.NaN;
                    for (int g = 0; g < 4; g++) {
                        float[][] tris = model.groups[g];
                        if (tris.length == 0) continue;
                        float bucketFactor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                        lastBrightStatic = Aero_MeshBatchRenderer2.emitStaticInstancesBatched(tess, tris,
                            model.invScale, bucketFactor, batch, count, options,
                            lastBrightStatic);
                    }
                    tess.draw();
                }

                // Named bones — per-vertex CPU matrix transform composes
                // pose + instance translate. Single tess cycle per bone
                // for ALL instances.
                for (int d = 0; d < renderPlan.drawableEntries.length; d++) {
                    int e = renderPlan.drawableEntries[d];
                    Aero_MeshModel.NamedGroup ng = entries[e];

                    tess.start(GL11.GL_TRIANGLES);
                    // tess.color dedup — instances inside a batch share a
                    // chunk so most consecutive brightness values match.
                    // NaN sentinel forces the first iteration to set the
                    // color; downstream comparisons skip the JNI call when
                    // the bright value is identical.
                    float lastBright = Float.NaN;
                    for (int g = 0; g < 4; g++) {
                        float[][] tris = ng.tris[g];
                        if (tris.length == 0) continue;
                        Aero_BatchVertexReuse.beginBucket(Aero_BatchPoseReuse.sharedSource());
                        float bucketFactor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                        for (int i = 0; i < count; i++) {
                            int poseIndex = Aero_BatchPoseReuse.ENABLED ? poseSources[i] : i;
                            float bright = batch.brightnesses[i] * bucketFactor;
                            if (bright != lastBright) {
                                tess.color(bright * options.tintR, bright * options.tintG,
                                           bright * options.tintB, options.alpha);
                                lastBright = bright;
                            }
                            if (!perInstancePoseActive[poseIndex][e]) {
                                // Named group with no animated bone in the
                                // clip (e.g. static body parts of a model
                                // whose only animated parts are sub-bones).
                                // Match the unbatched path: render at rest
                                // pose, no transform — just instance
                                // translate. Otherwise the static body
                                // disappears, leaving "fans floating in air".
                                if (!Aero_BatchVertexReuse.emitRest(tess, tris, model.invScale,
                                        poseIndex, batch.xs[i], batch.ys[i], batch.zs[i])) {
                                    Aero_MeshBatchRenderer3.emitBoneInstanceBatchedRest(tess, tris,
                                        model.invScale,
                                        batch.xs[i], batch.ys[i], batch.zs[i]);
                                }
                            } else {
                                Aero_BoneRenderPose pose = perInstancePoses[poseIndex][e];
                                if (!Aero_BatchVertexReuse.emitPose(tess, tris, model.invScale,
                                        pose, poseIndex, batch.xs[i], batch.ys[i], batch.zs[i])) {
                                    Aero_MeshBatchRenderer2.emitBoneInstanceBatched(tess, tris,
                                        model.invScale, pose,
                                        batch.xs[i], batch.ys[i], batch.zs[i]);
                                }
                            }
                        }
                    }
                    tess.draw();
                }
            } finally {
                Aero_MeshRenderer.endMeshState();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimatedBatch");
        }
    }

static void renderAnimatedBatchUnbatched(Aero_AnimatedBatcher.Batch batch) {
        if (batch == null || batch.count == 0) return;
        Aero_MeshBatchRenderer3.drainAsUnbatched(batch, batch.count);
    }

static Aero_BoneRenderPose[][] ensureBatchPoseScratch(int instanceCount) {
        if (BATCH_POSES.length < instanceCount) {
            BATCH_POSES = new Aero_BoneRenderPose[Math.max(instanceCount, BATCH_POSES.length * 2)][];
        }
        return BATCH_POSES;
    }

static boolean[][] ensureBatchPoseActiveScratch(int instanceCount) {
        if (BATCH_POSE_ACTIVE.length < instanceCount) {
            boolean[][] grown = new boolean[Math.max(instanceCount, BATCH_POSE_ACTIVE.length * 2)][];
            System.arraycopy(BATCH_POSE_ACTIVE, 0, grown, 0, BATCH_POSE_ACTIVE.length);
            BATCH_POSE_ACTIVE = grown;
        }
        return BATCH_POSE_ACTIVE;
    }

static void prepareBatchPoseRow(int instance, int boneCount) {
        if (BATCH_POSES[instance] == null || BATCH_POSES[instance].length < boneCount) {
            BATCH_POSES[instance] = new Aero_BoneRenderPose[Math.max(boneCount, 4)];
        }
        if (BATCH_POSE_ACTIVE[instance] == null
                || BATCH_POSE_ACTIVE[instance].length < boneCount) {
            BATCH_POSE_ACTIVE[instance] = new boolean[Math.max(boneCount, 4)];
        }
        for (int bone = 0; bone < boneCount; bone++) {
            if (BATCH_POSES[instance][bone] == null) {
                BATCH_POSES[instance][bone] = new Aero_BoneRenderPose();
            }
            BATCH_POSE_ACTIVE[instance][bone] = false;
        }
    }

static void copyPose(Aero_BoneRenderPose src, Aero_BoneRenderPose dst) {
        dst.pivotX = src.pivotX; dst.pivotY = src.pivotY; dst.pivotZ = src.pivotZ;
        dst.offsetX = src.offsetX; dst.offsetY = src.offsetY; dst.offsetZ = src.offsetZ;
        dst.rotX = src.rotX; dst.rotY = src.rotY; dst.rotZ = src.rotZ;
        dst.scaleX = src.scaleX; dst.scaleY = src.scaleY; dst.scaleZ = src.scaleZ;
        dst.uOffset = src.uOffset; dst.vOffset = src.vOffset;
        dst.uScale = src.uScale; dst.vScale = src.vScale;
    }

static boolean hasStaticGeometry(Aero_MeshModel model) {
        for (int g = 0; g < 4; g++) {
            if (model.groups[g].length > 0) return true;
        }
        return false;
    }
}
