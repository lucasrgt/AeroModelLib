package aero.modellib.animation;

/** Fail-closed validation shared by clip builders and immutable tracks. */
final class Aero_AnimationValidation {
    private Aero_AnimationValidation() {}
    static void event(String clip, float time, String channel, String data) {
        if (Float.isNaN(time) || Float.isInfinite(time)) throw new IllegalArgumentException("clip '" + clip + "': event time must be finite, got " + time);
        if (channel == null || channel.length() == 0) throw new IllegalArgumentException("clip '" + clip + "': event channel must not be empty");
        if (data == null || data.length() == 0) throw new IllegalArgumentException("clip '" + clip + "' channel '" + channel + "' @t=" + time + ": event name must not be empty");
    }
    static void arrays(String context, float[] times, float[][] values, Aero_Easing[] easings) {
        if (times == null || values == null || easings == null) {
            throw new IllegalArgumentException(context + ": channel arrays must not be null"
                + " (times=" + (times == null ? "null" : "ok") + ", values=" + (values == null ? "null" : "ok")
                + ", easings=" + (easings == null ? "null" : "ok") + ")");
        }
        if (times.length != values.length || times.length != easings.length) {
            throw new IllegalArgumentException(context + ": channel array lengths must match"
                + " (times=" + times.length + ", values=" + values.length + ", easings=" + easings.length + ")");
        }
    }
    static void key(String context, int index, float[] times, float[][] values, Aero_Easing[] easings) {
        float time = times[index];
        if (Float.isNaN(time) || Float.isInfinite(time)) throw new IllegalArgumentException(context + ": keyframe[" + index + "] time must be finite, got " + time);
        if (index > 0 && time < times[index - 1]) throw new IllegalArgumentException(context + ": keyframe times must be sorted ascending (t[" + (index - 1) + "]=" + times[index - 1] + " > t[" + index + "]=" + time + ")");
        if (values[index] == null || values[index].length < 3) throw new IllegalArgumentException(context + ": keyframe[" + index + "] value must have 3 components, got " + (values[index] == null ? "null" : "length=" + values[index].length));
        if (easings[index] == null) throw new IllegalArgumentException(context + ": keyframe[" + index + "] easing must not be null");
    }
}
