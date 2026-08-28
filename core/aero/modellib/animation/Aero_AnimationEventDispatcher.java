package aero.modellib.animation;

/** Dispatches sorted keyframe events over a half-open playback window. */
@aero.modellib.optimization.OptimizationRef({"aero.animation.event-lower-bound"})
final class Aero_AnimationEventDispatcher {
    private Aero_AnimationEventDispatcher() {}

    static void fire(Aero_AnimationEventListener listener, Aero_AnimationClip clip,
            float from, float inclusiveEnd, boolean includeFrom) {
        if (inclusiveEnd < from || (inclusiveEnd == from && !includeFrom)) return;
        Aero_AnimationClip.KeyframeEvent[] events = clip.events;
        for (int index = lowerBound(events, from, includeFrom); index < events.length; index++) {
            Aero_AnimationClip.KeyframeEvent event = events[index];
            if (event.time > inclusiveEnd) return;
            listener.onEvent(event.channel, event.data, event.locator, event.time);
        }
    }

    private static int lowerBound(Aero_AnimationClip.KeyframeEvent[] events,
            float bound, boolean inclusive) {
        int low = 0, high = events.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            boolean before = inclusive ? events[middle].time < bound : events[middle].time <= bound;
            if (before) low = middle + 1;
            else high = middle;
        }
        return low;
    }
}
