package aero.modellib.animation;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.skeletal.Aero_Quaternion;

/** Immutable validated channel data with optional pre-baked sampling LUT. */
@OptimizationRef({"aero.animation.curve-lut", "aero.animation.hot-path-sampling", "aero.animation.sample-cursors"})
final class Aero_AnimationChannelTrack {
    final float[] times; final float[][] values; final Aero_Easing[] easings;
    final float[][] quatValues; final boolean[] useSlerpSegment; final float[] slerpScratch;
    private final boolean rotationTrack;
    private float[] lut; private float lutTimeMin, lutTimeRange;
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
        rotationTrack = "rotation".equals(kind);
        if (rotationTrack && times.length > 0) {
            quatValues = quaternions(values); slerpScratch = new float[4];
            useSlerpSegment = times.length > 1 ? slerpSegments(values) : null;
        } else { quatValues = null; slerpScratch = null; useSlerpSegment = null; }
        if (Aero_AnimationLUTConfig.ENABLED) prepareLut(Aero_AnimationLUTConfig.SAMPLES, kind);
    }
    boolean sampleInto(float time, float[] out, int[] cursor, int cursorIndex) {
        return lut != null ? sampleLut(time, out) : Aero_AnimationChannelSampler.sample(this, time, out, cursor, cursorIndex);
    }
    void prepareLut(int samples, String kind) {
        if (times.length >= 2 && !hasStep()) bakeLut(samples, tolerance(kind));
    }
    private void bakeLut(int samples, float tolerance) {
        float range = times[times.length - 1] - times[0]; if (range <= 0f) return;
        float[] table = new float[samples * 3], sample = new float[3]; int last = samples - 1;
        for (int index = 0; index < samples; index++) {
            Aero_AnimationChannelSampler.sample(this,
                times[0] + range * index / last, sample, null, -1);
            int offset = index * 3;
            table[offset] = sample[0]; table[offset + 1] = sample[1];
            table[offset + 2] = sample[2];
        }
        lut = table; lutTimeMin = times[0]; lutTimeRange = range;
        if (!withinTolerance(samples, tolerance, sample)) lut = null;
    }
    private boolean sampleLut(float time, float[] out) {
        int last = lut.length / 3 - 1;
        if (time <= lutTimeMin) { copyLut(0, out); return true; }
        if (time >= lutTimeMin + lutTimeRange) { copyLut(last * 3, out); return true; }
        float exact = (time - lutTimeMin) / lutTimeRange * last; int low = (int) exact;
        if (low >= last) low = last - 1;
        float blend = exact - low; int left = low * 3, right = left + 3;
        for (int axis = 0; axis < 3; axis++)
            out[axis] = lut[left + axis] + (lut[right + axis] - lut[left + axis]) * blend;
        return true;
    }
    private boolean withinTolerance(int samples, float tolerance, float[] exact) {
        float step = lutTimeRange / (samples - 1);
        for (int interval = 0; interval < samples - 1; interval++) {
            for (int eighth = 1; eighth <= 7; eighth++) {
                float fraction = eighth * 0.125f;
                float time = lutTimeMin + step * (interval + fraction);
                Aero_AnimationChannelSampler.sample(this, time, exact, null, -1);
                int left = interval * 3, right = left + 3;
                for (int axis = 0; axis < 3; axis++) {
                    float approximate = lut[left + axis]
                        + (lut[right + axis] - lut[left + axis]) * fraction;
                    float error = rotationTrack ? angleError(exact[axis], approximate)
                        : Math.abs(exact[axis] - approximate);
                    if (error > tolerance) return false;
                }
            }
        }
        return true;
    }
    private boolean hasStep() {
        for (int index = 1; index < easings.length; index++)
            if (easings[index] == Aero_Easing.STEP) return true;
        return false;
    }
    private void copyLut(int offset, float[] out) {
        out[0] = lut[offset]; out[1] = lut[offset + 1]; out[2] = lut[offset + 2];
    }
    private static float tolerance(String kind) {
        if ("rotation".equals(kind)) return 0.25f;
        if ("position".equals(kind)) return 0.02f;
        return 0.001f;
    }
    private static float angleError(float exact, float approximate) {
        float difference = Math.abs(exact - approximate) % 360f;
        return difference > 180f ? 360f - difference : difference;
    }
    boolean usesLut() { return lut != null; }
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
