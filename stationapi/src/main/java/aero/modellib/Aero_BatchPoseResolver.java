package aero.modellib;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationPoseResolver;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.optimization.OptimizationRef;
import aero.modellib.skeletal.Aero_BoneRenderPose;

/** Resolves unique flat-skeleton poses and maps equivalent batch instances. */
@OptimizationRef({"aero.render.animated-batcher", "aero.animation.batch-pose-reuse"})
final class Aero_BatchPoseResolver extends Aero_MeshRendererState {
    private Aero_BatchPoseResolver() {}

    static boolean resolve(Aero_MeshModel model, Aero_AnimatedBatcher.Batch batch, int count,
            Aero_MeshModel.NamedGroup[] entries, Aero_BoneRenderPose[][] poses,
            boolean[][] active, int[] poseSources) {
        for (int i = 0; i < count; i++) {
            Aero_AnimationPlayback state = batch.states[i];
            Aero_AnimationBundle bundle = batch.bundles[i];
            Aero_AnimationClip clip = state.getCurrentClip();
            BatchPlan plan = Aero_MeshModelRenderer.batchPlanFor(model, clip, bundle, entries);
            if (!plan.batchableFlat) return false;
            if (clip == null) {
                Aero_MeshBatchRenderer.prepareBatchPoseRow(i, entries.length);
                continue;
            }
            float time = state.getInterpolatedTime(batch.partialTicks[i]);
            int source = i;
            if (Aero_BatchPoseReuse.ENABLED) {
                source = Aero_BatchPoseReuse.sourceFor(bundle, clip, time, state, i);
                poseSources[i] = source;
            } else {
                Aero_BatchPoseReuse.recordResolved();
            }
            if (source != i) continue;
            Aero_MeshBatchRenderer.prepareBatchPoseRow(i, entries.length);
            if (!resolveUnique(bundle, clip, state, time, batch.partialTicks[i], plan,
                    entries, poses[i], active[i])) return false;
        }
        return true;
    }

    private static boolean resolveUnique(Aero_AnimationBundle bundle, Aero_AnimationClip clip,
            Aero_AnimationPlayback state, float time, float partialTick, BatchPlan plan,
            Aero_MeshModel.NamedGroup[] entries, Aero_BoneRenderPose[] poses, boolean[] active) {
        Aero_BoneRenderPose[] pool = Aero_MeshPoseRenderer.ensurePoolSize(clip.boneNames.length);
        float[][] pivots = bundle.resolvePivotsFor(clip);
        for (int bone = 0; bone < clip.boneNames.length; bone++) {
            Aero_AnimationPoseResolver.resolveClip(bone, clip.boneNames[bone], pivots[bone],
                clip, state, time, partialTick, SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, pool[bone]);
        }
        for (int entry = 0; entry < entries.length; entry++) {
            int bone = plan.entryBoneIdx[entry];
            if (bone < 0) continue;
            Aero_MeshBatchRenderer.copyPose(pool[bone], poses[entry]);
            if (!Aero_AnimatedBatcher.UV_BATCH_ENABLED && !poses[entry].uvIsIdentity()) {
                return false;
            }
            active[entry] = true;
        }
        return true;
    }
}
