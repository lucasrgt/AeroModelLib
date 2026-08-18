package aero.modellib.animation;

/** Shared animation time normalization rules. */
final class Aero_AnimationTime {
    private Aero_AnimationTime() {}

    static float normalize(Aero_AnimationClip clip, float seconds) {
        if (clip == null || clip.length <= 0f || Float.isNaN(seconds) || Float.isInfinite(seconds))
            return 0f;
        if (clip.loop == Aero_AnimationLoop.LOOP) {
            float time = seconds % clip.length;
            return time < 0f ? time + clip.length : time;
        }
        if (seconds <= 0f) return 0f;
        return seconds >= clip.length ? clip.length : seconds;
    }
}
