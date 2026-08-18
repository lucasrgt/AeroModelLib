package aero.modellib.animation;

import aero.modellib.skeletal.Aero_Quaternion;

/** Allocation-free interpolation engine for animation channel tracks. */
final class Aero_AnimationChannelSampler {
    private Aero_AnimationChannelSampler() {}
    static boolean sample(Aero_AnimationChannelTrack track, float time, float[] out, int[] cursor, int cursorIndex) {
        int count = track.times.length;
        if (count == 0) return false;
        if (count == 1 || time <= track.times[0]) { Aero_AnimationChannelTrack.copy(track.values[0], out); return true; }
        if (time >= track.times[count - 1]) { Aero_AnimationChannelTrack.copy(track.values[count - 1], out); return true; }
        int low = findSegment(track.times, time, cursor, cursorIndex), high = low + 1;
        Aero_Easing easing = track.easings[high];
        if (easing == Aero_Easing.STEP) { Aero_AnimationChannelTrack.copy(track.values[low], out); return true; }
        float alpha = fraction(track.times[low], track.times[high], time);
        float[] left = track.values[low], right = track.values[high];
        if (track.quatValues != null && track.useSlerpSegment != null && track.useSlerpSegment[low]) {
            float eased = easing == Aero_Easing.LINEAR || easing == Aero_Easing.CATMULLROM ? alpha : easing.apply(alpha);
            Aero_Quaternion.slerp(track.quatValues[low], track.quatValues[high], eased, track.slerpScratch);
            Aero_Quaternion.toEulerDegrees(track.slerpScratch, out); return true;
        }
        if (easing == Aero_Easing.CATMULLROM) { catmull(track.values, low, high, alpha, out); return true; }
        float eased = easing == Aero_Easing.LINEAR ? alpha : easing.apply(alpha);
        for (int axis = 0; axis < 3; axis++) out[axis] = left[axis] + (right[axis] - left[axis]) * eased;
        return true;
    }
    private static int findSegment(float[] times, float time, int[] cursor, int cursorIndex) {
        int count = times.length;
        if (cursor != null && cursorIndex >= 0 && cursorIndex < cursor.length) {
            int low = cursor[cursorIndex];
            if (low >= 0 && low < count - 1) {
                if (time >= times[low] && time < times[low + 1]) return low;
                while (low < count - 2 && time >= times[low + 1]) low++;
                if (time >= times[low] && time < times[low + 1]) { cursor[cursorIndex] = low; return low; }
            }
        }
        int low = 0, high = count - 1;
        while (high - low > 1) { int middle = (low + high) >>> 1; if (times[middle] <= time) low = middle; else high = middle; }
        if (cursor != null && cursorIndex >= 0 && cursorIndex < cursor.length) cursor[cursorIndex] = low;
        return low;
    }
    private static void catmull(float[][] values, int low, int high, float time, float[] out) {
        float[] left = values[low], right = values[high];
        float[] before = low > 0 ? values[low - 1] : left, after = high < values.length - 1 ? values[high + 1] : right;
        float squared = time * time, cubed = squared * time;
        for (int axis = 0; axis < 3; axis++) out[axis] = curve(before[axis], left[axis], right[axis], after[axis], time, squared, cubed);
    }
    private static float fraction(float start, float end, float time) { return end > start ? (time - start) / (end - start) : 0f; }
    private static float curve(float p0, float p1, float p2, float p3, float t, float t2, float t3) {
        return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }
}
