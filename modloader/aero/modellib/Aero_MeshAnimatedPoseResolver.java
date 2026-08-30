package aero.modellib;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationPoseResolver;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_ProceduralPose;

/** Resolves one animation pose and its optional IK pass. */
final class Aero_MeshAnimatedPoseResolver {
    private Aero_MeshAnimatedPoseResolver() {}

    static Aero_BoneRenderPose[] resolve(Aero_AnimationClip clip, Aero_AnimationBundle bundle,
            Aero_AnimationPlayback state, float time, float partialTick,
            Aero_ProceduralPose proceduralPose) {
        if (clip == null) return null;
        Aero_BoneRenderPose[] pool = Aero_MeshPoseRenderer.ensurePoolSize(clip.boneNames.length);
        float[][] pivots = bundle.resolvePivotsFor(clip);
        for (int bone = 0; bone < clip.boneNames.length; bone++) {
            String name = clip.boneNames[bone];
            Aero_AnimationPoseResolver.resolveClip(bone, name, pivots[bone], clip, state,
                time, partialTick, Aero_MeshRendererState.SCRATCH_ROT,
                Aero_MeshRendererState.SCRATCH_POS, Aero_MeshRendererState.SCRATCH_SCL,
                pool[bone]);
            if (proceduralPose != null) proceduralPose.apply(name, pool[bone]);
        }
        return pool;
    }
}
