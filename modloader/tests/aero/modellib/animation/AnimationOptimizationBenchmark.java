package aero.modellib.animation;

import aero.modellib.OptimizationBenchmarkSupport;
import aero.modellib.OptimizationBenchmarkSupport.Work;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Retroactive oracles for active animation hot-path optimizations. */
public final class AnimationOptimizationBenchmark {
    private static final int BONES = 128;
    private static final int KEYS = 32;
    private static final Aero_AnimationClip CLIP = clip();
    private static final Aero_AnimationBundle BUNDLE = bundle();
    private static int eventCount;

    private AnimationOptimizationBenchmark() {}

    public static void main(String[] args) {
        OptimizationBenchmarkSupport.header();
        OptimizationBenchmarkSupport.compare("aero.animation.event-lower-bound",
            40, 2000, 64, eventOptimized(), eventOracle());
        OptimizationBenchmarkSupport.compare("aero.animation.hot-path-sampling",
            80, 4000, BONES, sampleReused(), sampleAllocating());
        OptimizationBenchmarkSupport.compare("aero.animation.sample-cursors",
            40, 600, BONES * 64, sampleCursor(), sampleCursorless());
        OptimizationBenchmarkSupport.compare("aero.animation.lookup-caches.bone-index",
            100, 4000, BONES, mapLookup(), linearLookup());
        OptimizationBenchmarkSupport.compare("aero.animation.lookup-caches.pivots",
            100, 10000, BONES, pivotArray(), pivotMap());
        System.out.println("sink=" + OptimizationBenchmarkSupport.sink());
    }

    private static Work eventOptimized() {
        return new Work() { public long run() {
            eventCount = 0;
            for (int window = 0; window < 64; window++) {
                float from = 3.8f + window * 0.001f;
                Aero_AnimationEventDispatcher.fire(LISTENER, CLIP, from, from + 0.0009f, false);
            }
            return eventCount;
        }};
    }

    private static Work eventOracle() {
        return new Work() { public long run() {
            eventCount = 0;
            for (int window = 0; window < 64; window++) {
                float from = 3.8f + window * 0.001f;
                linearFire(CLIP.events, from, from + 0.0009f, false);
            }
            return eventCount;
        }};
    }

    private static final Aero_AnimationEventListener LISTENER =
        new Aero_AnimationEventListener() {
            public void onEvent(String channel, String data, String locator, float time) {
                eventCount++;
            }
        };

    private static void linearFire(Aero_AnimationClip.KeyframeEvent[] events,
                                   float from, float end, boolean includeFrom) {
        for (int i = 0; i < events.length; i++) {
            Aero_AnimationClip.KeyframeEvent event = events[i];
            if (event.time < from || (event.time == from && !includeFrom)) continue;
            if (event.time > end) return;
            LISTENER.onEvent(event.channel, event.data, event.locator, event.time);
        }
    }

    private static Work sampleReused() {
        return new Work() {
            private final float[] out = new float[3];
            public long run() {
                long sum = 0L;
                for (int bone = 0; bone < BONES; bone++) {
                    CLIP.sampleRotInto(bone, ((bone * 17) & 255) / 255f, out);
                    sum += Float.floatToIntBits(out[0]);
                }
                return sum;
            }
        };
    }

    private static Work sampleAllocating() {
        return new Work() { public long run() {
            long sum = 0L;
            for (int bone = 0; bone < BONES; bone++) {
                float[] out = new float[3];
                CLIP.sampleRotInto(bone, ((bone * 17) & 255) / 255f, out);
                sum += Float.floatToIntBits(out[0]);
            }
            return sum;
        }};
    }

    private static Work sampleCursor() { return samples(true); }
    private static Work sampleCursorless() { return samples(false); }
    private static Work samples(final boolean cursorEnabled) {
        return new Work() {
            private final float[] out = new float[3];
            private final int[] cursor = new int[BONES];
            public long run() {
                Arrays.fill(cursor, -1);
                long sum = 0L;
                for (int step = 0; step < 64; step++) {
                    float time = step / 63f;
                    for (int bone = 0; bone < BONES; bone++) {
                        if (cursorEnabled) CLIP.sampleRotInto(bone, time, out, cursor);
                        else CLIP.sampleRotInto(bone, time, out);
                        sum += Float.floatToIntBits(out[0]);
                    }
                }
                return sum;
            }
        };
    }

    private static Work mapLookup() {
        return new Work() { public long run() {
            long sum = 0L;
            for (int i = 0; i < BONES; i++) sum += CLIP.indexOfBone(CLIP.boneNames[i]);
            return sum;
        }};
    }

    private static Work linearLookup() {
        return new Work() { public long run() {
            long sum = 0L;
            for (int i = 0; i < BONES; i++) sum += linearIndex(CLIP.boneNames, CLIP.boneNames[i]);
            return sum;
        }};
    }

    private static Work pivotArray() {
        return new Work() { public long run() {
            float[][] pivots = BUNDLE.resolvePivotsFor(CLIP);
            long sum = 0L;
            for (int i = 0; i < BONES; i++) sum += Float.floatToIntBits(pivots[i][0]);
            return sum;
        }};
    }

    private static Work pivotMap() {
        return new Work() { public long run() {
            long sum = 0L;
            for (int i = 0; i < BONES; i++) {
                float[] pivot = BUNDLE.pivotOrZero(CLIP.boneNames[i]);
                sum += Float.floatToIntBits(pivot[0]);
            }
            return sum;
        }};
    }

    private static int linearIndex(String[] names, String target) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(target)) return i;
        return -1;
    }

    private static Aero_AnimationClip clip() {
        Aero_AnimationClip.Builder builder = Aero_AnimationClip.builder("bench")
            .loop(Aero_AnimationLoop.LOOP).length(4.096f);
        for (int event = 0; event < 4096; event++) {
            builder.event(event * 0.001f, "sound", "tick", null);
        }
        for (int bone = 0; bone < BONES; bone++) {
            float[] times = new float[KEYS];
            float[][] values = new float[KEYS][];
            Aero_Easing[] easing = new Aero_Easing[KEYS];
            for (int key = 0; key < KEYS; key++) {
                times[key] = key / (float) (KEYS - 1);
                values[key] = new float[]{bone * 0.25f + key, key * 0.5f, -key};
                easing[key] = Aero_Easing.LINEAR;
            }
            builder.bone("bone_" + bone).rotation(times, values, easing);
        }
        return builder.build();
    }

    private static Aero_AnimationBundle bundle() {
        Map pivots = new HashMap();
        for (int i = 0; i < BONES; i++) pivots.put(CLIP.boneNames[i], new float[]{i, i, i});
        return new Aero_AnimationBundle(new HashMap(), pivots, new HashMap());
    }
}
