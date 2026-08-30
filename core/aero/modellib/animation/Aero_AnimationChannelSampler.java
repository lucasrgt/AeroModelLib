package aero.modellib.animation;

import aero.modellib.skeletal.Aero_Quaternion;

/** Allocation-free interpolation engine for animation channel tracks. */
final class Aero_AnimationChannelSampler {
    private Aero_AnimationChannelSampler() {}
    static boolean sample(Aero_AnimationChannelTrack track, float time, float[] out, int[] cursor, int cursorIndex) {
        int count = track.times.length;
        if (count == 0) return false;
        if (copyBoundary(track, time, out)) return true;
        int low = findSegment(track.times, time, cursor, cursorIndex), high = low + 1;
        Aero_Easing easing = track.easings[high];
        if (easing == Aero_Easing.STEP) { Aero_AnimationChannelTrack.copy(track.values[low], out); return true; }
        float alpha = fraction(track.times[low], track.times[high], time);
        if (sampleQuaternion(track, low, high, alpha, easing, out)) return true;
        if (easing == Aero_Easing.CATMULLROM) { catmull(track.values, low, high, alpha, out); return true; }
        float eased = easing == Aero_Easing.LINEAR ? alpha : easing.apply(alpha);
        float[] left = track.values[low], right = track.values[high];
        for (int axis = 0; axis < 3; axis++) out[axis] = left[axis] + (right[axis] - left[axis]) * eased;
        return true;
    }
    private static boolean copyBoundary(Aero_AnimationChannelTrack track, float time, float[] out) {
        int last = track.times.length - 1;
        if (last == 0 || time <= track.times[0]) {
            Aero_AnimationChannelTrack.copy(track.values[0], out);
            return true;
        }
        if (time < track.times[last]) return false;
        Aero_AnimationChannelTrack.copy(track.values[last], out);
        return true;
    }
    private static boolean sampleQuaternion(Aero_AnimationChannelTrack track, int low, int high,
            float alpha, Aero_Easing easing, float[] out) {
        if (track.quatValues == null || track.useSlerpSegment == null) return false;
        if (!track.useSlerpSegment[low]) return false;
        float eased = easing == Aero_Easing.LINEAR || easing == Aero_Easing.CATMULLROM
            ? alpha : easing.apply(alpha);
        Aero_Quaternion.slerp(
            track.quatValues[low], track.quatValues[high], eased, track.slerpScratch);
        Aero_Quaternion.toEulerDegrees(track.slerpScratch, out);
        return true;
    }
    private static int findSegment(float[] times, float time, int[] cursor, int cursorIndex) {
        int cached = cachedSegment(times, time, cursor, cursorIndex);
        if (cached >= 0) return cached;
        int low = 0, high = times.length - 1;
        while (high - low > 1) { int middle = (low + high) >>> 1; if (times[middle] <= time) low = middle; else high = middle; }
        storeCursor(cursor, cursorIndex, low);
        return low;
    }
    private static int cachedSegment(float[] times, float time, int[] cursor, int cursorIndex) {
        if (!validCursor(cursor, cursorIndex)) return -1;
        int low = cursor[cursorIndex];
        if (low < 0 || low >= times.length - 1) return -1;
        if (contains(times, low, time)) return low;
        while (low < times.length - 2 && time >= times[low + 1]) low++;
        if (!contains(times, low, time)) return -1;
        cursor[cursorIndex] = low;
        return low;
    }
    private static boolean contains(float[] times, int low, float time) {
        return time >= times[low] && time < times[low + 1];
    }
    private static void storeCursor(int[] cursor, int cursorIndex, int low) {
        if (validCursor(cursor, cursorIndex)) cursor[cursorIndex] = low;
    }
    private static boolean validCursor(int[] cursor, int cursorIndex) {
        return cursor != null && cursorIndex >= 0 && cursorIndex < cursor.length;
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
