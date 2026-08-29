package aero.modellib.animation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Fidelity gates for the opt-in contiguous animation curve LUT. */
public class AnimationLUTAccuracyTest {
    @Test
    public void nonlinearPositionStaysWithinDeclaredError() {
        Aero_AnimationChannelTrack track = track("position",
            new float[]{0f, 1f},
            new float[][]{{0f, 0f, 0f}, {6f, -4f, 2f}},
            new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.EASE_IN_OUT_CUBIC});
        track.prepareLut(256, "position");
        assertTrue(track.usesLut());
        assertTrackError(track, false, 0.02f);
    }

    @Test
    public void slerpedRotationStaysWithinDeclaredError() {
        Aero_AnimationChannelTrack track = track("rotation",
            new float[]{0f, 0.4f, 1f},
            new float[][]{{0f, 0f, 0f}, {75f, 40f, -15f}, {130f, -20f, 35f}},
            new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.EASE_IN_OUT_SINE,
                Aero_Easing.EASE_OUT_QUAD});
        track.prepareLut(256, "rotation");
        assertTrue(track.usesLut());
        assertTrackError(track, true, 0.25f);
    }

    @Test
    public void stepTrackRetainsExactEvaluator() {
        Aero_AnimationChannelTrack track = track("position",
            new float[]{0f, 0.5f, 1f},
            new float[][]{{0f, 0f, 0f}, {8f, 0f, 0f}, {16f, 0f, 0f}},
            new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.STEP, Aero_Easing.LINEAR});
        track.prepareLut(256, "position");
        assertFalse(track.usesLut());
    }

    @Test
    public void wrappedEulerTrackFallsBackWhenInterpolationIsUnsafe() {
        Aero_AnimationChannelTrack track = track("rotation",
            new float[]{0f, 1f},
            new float[][]{{170f, 0f, 0f}, {190f, 0f, 0f}},
            new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR});
        track.prepareLut(16, "rotation");
        assertFalse(track.usesLut());
    }

    private static Aero_AnimationChannelTrack track(String kind, float[] times,
            float[][] values, Aero_Easing[] easings) {
        return new Aero_AnimationChannelTrack("test", "bone", kind, times, values, easings);
    }

    private static void assertTrackError(Aero_AnimationChannelTrack track,
            boolean rotation, float limit) {
        float[] exact = new float[3], approximate = new float[3];
        for (int sample = 0; sample <= 10000; sample++) {
            float time = sample / 10000f;
            Aero_AnimationChannelSampler.sample(track, time, exact, null, -1);
            track.sampleInto(time, approximate, null, -1);
            for (int axis = 0; axis < 3; axis++) {
                float error = rotation ? angleError(exact[axis], approximate[axis])
                    : Math.abs(exact[axis] - approximate[axis]);
                assertTrue("sample=" + sample + " axis=" + axis + " error=" + error,
                    error <= limit + 0.00001f);
            }
        }
    }

    private static float angleError(float left, float right) {
        float difference = Math.abs(left - right) % 360f;
        return difference > 180f ? 360f - difference : difference;
    }
}
