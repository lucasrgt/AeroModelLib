package aero.modellib.animation;

import java.util.Arrays;

/** Owns monotonic per-channel sampling cursors for one playback instance. */
@aero.modellib.optimization.OptimizationRef({"aero.animation.sample-cursors"})
final class Aero_AnimationSampleCursors {
    private Aero_AnimationClip clip;
    private int[] rotation;
    private int[] position;
    private int[] scale;
    private int[] uvOffset;
    private int[] uvScale;

    int[] rotation(Aero_AnimationClip value) { prepare(value); rotation = sized(rotation, value); return rotation; }
    int[] position(Aero_AnimationClip value) { prepare(value); position = sized(position, value); return position; }
    int[] scale(Aero_AnimationClip value) { prepare(value); scale = sized(scale, value); return scale; }
    int[] uvOffset(Aero_AnimationClip value) { prepare(value); uvOffset = sized(uvOffset, value); return uvOffset; }
    int[] uvScale(Aero_AnimationClip value) { prepare(value); uvScale = sized(uvScale, value); return uvScale; }

    void reset() {
        clip = null;
        clear(rotation);
        clear(position);
        clear(scale);
        clear(uvOffset);
        clear(uvScale);
    }

    private void prepare(Aero_AnimationClip value) {
        if (clip == value) return;
        reset();
        clip = value;
    }

    private static int[] sized(int[] cursors, Aero_AnimationClip clip) {
        if (cursors != null && cursors.length >= clip.boneNames.length) return cursors;
        int[] result = new int[clip.boneNames.length];
        clear(result);
        return result;
    }

    private static void clear(int[] cursors) {
        if (cursors != null) Arrays.fill(cursors, -1);
    }
}
