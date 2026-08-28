package aero.modellib.animation;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.skeletal.Aero_Quaternion;

/** Immutable validated channel data with optional pre-baked sampling LUT. */
@OptimizationRef({"aero.animation.curve-lut", "aero.animation.hot-path-sampling", "aero.animation.sample-cursors"})
final class Aero_AnimationChannelTrack {
    final float[] times; final float[][] values; final Aero_Easing[] easings;
    final float[][] quatValues; final boolean[] useSlerpSegment; final float[] slerpScratch;
    private float[][] lut; private float lutTimeMin, lutTimeRange;
    Aero_AnimationChannelTrack(String clip, String bone, String kind, float[] sourceTimes,
            float[][] sourceValues, Aero_Easing[] sourceEasings) {
        String context = "clip '" + clip + "' bone '" + bone + "' " + kind;
        Aero_AnimationValidation.arrays(context, sourceTimes, sourceValues, sourceEasings);
        times = new float[sourceTimes.length]; values = new float[sourceValues.length][];
        easings = new Aero_Easing[sourceEasings.length];
        for (int index = 0; index < times.length; index++) {
            Aero_AnimationValidation.key(context, index, sourceTimes, sourceValues, sourceEasings);
            times[index] = sourceTimes[index]; values[index] = new float[]{sourceValues[index][0], sourceValues[index][1], sourceValues[index][2]}; easings[index] = sourceEasings[index];
        }
        if ("rotation".equals(kind) && times.length > 0) {
            quatValues = quaternions(values); slerpScratch = new float[4];
            useSlerpSegment = times.length > 1 ? slerpSegments(values) : null;
        } else { quatValues = null; slerpScratch = null; useSlerpSegment = null; }
        if (Aero_AnimationLUTConfig.ENABLED && times.length >= 2) bakeLut(Aero_AnimationLUTConfig.SAMPLES);
    }
    boolean sampleInto(float time, float[] out, int[] cursor, int cursorIndex) {
        return lut != null ? sampleLut(time, out) : Aero_AnimationChannelSampler.sample(this, time, out, cursor, cursorIndex);
    }
    private void bakeLut(int samples) {
        float range = times[times.length - 1] - times[0]; if (range <= 0f) return;
        float[][] table = new float[samples][3]; int last = samples - 1;
        for (int index = 0; index < samples; index++) Aero_AnimationChannelSampler.sample(this, times[0] + range * index / last, table[index], null, -1);
        lut = table; lutTimeMin = times[0]; lutTimeRange = range;
    }
    private boolean sampleLut(float time, float[] out) {
        int last = lut.length - 1;
        if (time <= lutTimeMin) { copy(lut[0], out); return true; }
        if (time >= lutTimeMin + lutTimeRange) { copy(lut[last], out); return true; }
        float exact = (time - lutTimeMin) / lutTimeRange * last; int low = (int) exact;
        if (low >= last) low = last - 1;
        float blend = exact - low; float[] left = lut[low], right = lut[low + 1];
        for (int axis = 0; axis < 3; axis++) out[axis] = left[axis] + (right[axis] - left[axis]) * blend;
        return true;
    }
    static void copy(float[] source, float[] target) { target[0] = source[0]; target[1] = source[1]; target[2] = source[2]; }
    private static float[][] quaternions(float[][] values) {
        float[][] result = new float[values.length][4];
        for (int index = 0; index < values.length; index++) Aero_Quaternion.fromEulerDegrees(values[index][0], values[index][1], values[index][2], result[index]);
        return result;
    }
    private static boolean[] slerpSegments(float[][] values) {
        boolean[] result = new boolean[values.length - 1];
        for (int index = 0; index < result.length; index++) {
            float[] left = values[index], right = values[index + 1];
            result[index] = Math.abs(right[0] - left[0]) < 180f && Math.abs(right[1] - left[1]) < 180f && Math.abs(right[2] - left[2]) < 180f;
        }
        return result;
    }
}
