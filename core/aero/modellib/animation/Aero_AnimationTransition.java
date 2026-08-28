package aero.modellib.animation;

import java.util.HashMap;
import java.util.Map;

/** Captures and blends a previous clip pose during state transitions. */
final class Aero_AnimationTransition {
    static final int ROTATION = 0;
    static final int POSITION = 1;
    static final int SCALE = 2;
    static final int UV_OFFSET = 3;
    static final int UV_SCALE = 4;

    private final Map[] snapshots = new Map[5];
    private final float[] scratch = new float[3];
    private int ticks;
    private int remaining;

    void start(Aero_AnimationClip clip, float time, int duration) {
        capture(clip, time);
        ticks = duration;
        remaining = duration;
    }

    void cancel() { ticks = remaining = 0; }
    void tick() { if (remaining > 0) remaining--; }
    boolean active() { return remaining > 0 && ticks > 0; }

    float alpha(float partialTick) {
        if (ticks <= 0) return 1f;
        float completed = (ticks - remaining) + partialTick;
        if (completed >= ticks) return 1f;
        if (completed <= 0f) return 0f;
        return completed / ticks;
    }

    boolean blend(int channel, String bone, float partialTick, boolean sampled, float[] output) {
        Map snapshot = snapshots[channel];
        if (!active() || snapshot == null || bone == null) return sampled;
        float[] previous = (float[]) snapshot.get(bone);
        if (previous == null) return sampled;
        float blend = alpha(partialTick);
        float rest = channel == SCALE || channel == UV_SCALE ? 1f : 0f;
        for (int axis = 0; axis < 3; axis++) {
            float target = sampled ? output[axis] : rest;
            output[axis] = previous[axis] + (target - previous[axis]) * blend;
        }
        return true;
    }

    private void capture(Aero_AnimationClip clip, float time) {
        if (clip == null) return;
        for (int channel = 0; channel < snapshots.length; channel++) {
            if (snapshots[channel] == null) snapshots[channel] = new HashMap();
            else snapshots[channel].clear();
        }
        for (int bone = 0; bone < clip.boneNames.length; bone++) {
            String name = clip.boneNames[bone];
            capture(clip, bone, time, name, ROTATION);
            capture(clip, bone, time, name, POSITION);
            capture(clip, bone, time, name, SCALE);
            capture(clip, bone, time, name, UV_OFFSET);
            capture(clip, bone, time, name, UV_SCALE);
        }
    }

    private void capture(Aero_AnimationClip clip, int bone, float time, String name, int channel) {
        boolean sampled;
        if (channel == ROTATION) sampled = clip.sampleRotInto(bone, time, scratch);
        else if (channel == POSITION) sampled = clip.samplePosInto(bone, time, scratch);
        else if (channel == SCALE) sampled = clip.sampleSclInto(bone, time, scratch);
        else if (channel == UV_OFFSET) sampled = clip.sampleUvOffsetInto(bone, time, scratch);
        else sampled = clip.sampleUvScaleInto(bone, time, scratch);
        if (sampled) snapshots[channel].put(name, new float[] {scratch[0], scratch[1], scratch[2]});
    }
}
