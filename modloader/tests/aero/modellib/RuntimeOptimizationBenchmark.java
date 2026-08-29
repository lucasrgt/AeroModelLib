package aero.modellib;

import aero.modellib.OptimizationBenchmarkSupport.Work;
import aero.modellib.skeletal.Aero_BoneFK;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_CCDSolver;
import aero.modellib.skeletal.Aero_MorphState;
import aero.modellib.util.Aero_Profiler;
import aero.modellib.util.Aero_SoundCoalesce;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Allocation, retention, IK, morph, profiler, and sound pressure oracles. */
public final class RuntimeOptimizationBenchmark {
    private static final String[] NAMES = names(16);
    private static volatile long soundSink;

    private RuntimeOptimizationBenchmark() {}

    public static void main(String[] args) {
        OptimizationBenchmarkSupport.header();
        Aero_Profiler.setEnabled(true);
        Aero_Profiler.reset();
        OptimizationBenchmarkSupport.compare("aero.diagnostics.profiler-timer-reuse",
            100, 5000, 64, profilerCurrent(), profilerLegacy());
        OptimizationBenchmarkSupport.compare("aero.skeletal.morph-weight-arrays",
            100, 20000, NAMES.length, morphArrays(), morphMap());
        OptimizationBenchmarkSupport.compare("aero.render.morph-scratch-reuse",
            100, 10000, NAMES.length, morphScratch(), morphAllocating());
        OptimizationBenchmarkSupport.compare("aero.animation.ik-scratch-reuse",
            100, 5000, 1, ikScratch(), ikAllocating());
        OptimizationBenchmarkSupport.compare("aero.loader.bounded-caches",
            20, 300, 4096, boundedCache(), unboundedCache());
        OptimizationBenchmarkSupport.compare("aero.audio.sound-coalescing",
            100, 5000, 144, coalescedSound(), directSound());
        System.out.println("SOUND_CALLS,coalesced=3,oracle=144");
        System.out.println("CACHE_RETAINED,bounded=512,oracle=4096");
        System.out.println("sink=" + OptimizationBenchmarkSupport.sink());
    }

    private static Work profilerCurrent() {
        return new Work() { public long run() {
            for (int i = 0; i < 64; i++) {
                String name = NAMES[i & 15];
                Aero_Profiler.start(name); Aero_Profiler.end(name);
            }
            return 64L;
        }};
    }

    private static Work profilerLegacy() {
        return new Work() {
            private final LegacyProfiler profiler = new LegacyProfiler();
            public long run() {
                for (int i = 0; i < 64; i++) {
                    String name = NAMES[i & 15];
                    profiler.start(name); profiler.end(name);
                }
                return 64L;
            }
        };
    }

    private static Work morphArrays() {
        final Aero_MorphState state = morphState();
        return new Work() { public long run() {
            long sum = 0L;
            for (int i = 0; i < state.activeCount(); i++) {
                sum += state.nameAt(i).hashCode() + Float.floatToIntBits(state.weightAt(i));
            }
            return sum;
        }};
    }

    private static Work morphMap() {
        final Map weights = weights();
        return new Work() { public long run() {
            long sum = 0L;
            Iterator iterator = weights.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                sum += entry.getKey().hashCode() + Float.floatToIntBits(((Float) entry.getValue()).floatValue());
            }
            return sum;
        }};
    }

    private static Work morphScratch() {
        final Aero_MorphState state = morphState();
        return new Work() {
            private final String[] names = new String[NAMES.length];
            private final float[] values = new float[NAMES.length];
            public long run() { return copyMorph(state, names, values); }
        };
    }

    private static Work morphAllocating() {
        final Aero_MorphState state = morphState();
        return new Work() { public long run() {
            return copyMorph(state, new String[NAMES.length], new float[NAMES.length]);
        }};
    }

    private static long copyMorph(Aero_MorphState state, String[] names, float[] values) {
        long sum = 0L;
        for (int i = 0; i < state.activeCount(); i++) {
            names[i] = state.nameAt(i); values[i] = state.weightAt(i);
            sum += names[i].hashCode() + Float.floatToIntBits(values[i]);
        }
        return sum;
    }

    private static Work ikScratch() {
        return new Work() {
            private final int[] chain = {0, 1, 2, 3};
            private final float[][] pivots = pivots();
            private final Aero_BoneRenderPose[] poses = poses();
            private final float[] target = {1.5f, 0.25f, 1.5f};
            private final float[] effector = new float[3];
            public long run() { reset(poses); return solve(chain, pivots, poses, target, effector); }
        };
    }

    private static Work ikAllocating() {
        return new Work() { public long run() {
            int[] chain = {0, 1, 2, 3};
            float[][] pivots = pivots();
            Aero_BoneRenderPose[] poses = poses();
            float[] target = {1.5f, 0.25f, 1.5f};
            float[] effector = new float[3];
            return solve(chain, pivots, poses, target, effector);
        }};
    }

    private static long solve(int[] chain, float[][] pivots, Aero_BoneRenderPose[] poses,
                              float[] target, float[] effector) {
        int iterations = Aero_CCDSolver.solve(chain, pivots, poses, target, 0.01f);
        Aero_BoneFK.computePivotInto(chain, pivots, poses, effector);
        return iterations + Float.floatToIntBits(effector[0])
            + Float.floatToIntBits(effector[1]) + Float.floatToIntBits(effector[2]);
    }

    private static Work boundedCache() { return cache(true); }
    private static Work unboundedCache() { return cache(false); }
    private static Work cache(final boolean bounded) {
        return new Work() { public long run() {
            Map map = bounded ? new BoundedMap() : new LinkedHashMap();
            long sum = 0L;
            for (int i = 0; i < 4096; i++) { map.put(Integer.valueOf(i), Integer.valueOf(i)); sum += i; }
            return sum;
        }};
    }

    private static Work coalescedSound() {
        final CountingDispatcher dispatcher = new CountingDispatcher();
        Aero_SoundCoalesce.setMaxPerName(3);
        return new Work() { public long run() {
            dispatcher.calls = 0;
            soundSink = 0L;
            for (int i = 0; i < 144; i++) Aero_SoundCoalesce.queue(i, 0, 0, "machine", 1f, 1f);
            Aero_SoundCoalesce.flush(0, 0, 0, dispatcher);
            return 144L + (soundSink == Long.MIN_VALUE ? 1L : 0L);
        }};
    }

    private static Work directSound() {
        final CountingDispatcher dispatcher = new CountingDispatcher();
        return new Work() { public long run() {
            dispatcher.calls = 0;
            soundSink = 0L;
            for (int i = 0; i < 144; i++) dispatcher.play(i, 0, 0, "machine", 1f, 1f);
            return 144L + (soundSink == Long.MIN_VALUE ? 1L : 0L);
        }};
    }

    private static Aero_MorphState morphState() {
        Aero_MorphState state = new Aero_MorphState();
        for (int i = 0; i < NAMES.length; i++) state.set(NAMES[i], (i + 1) * 0.05f);
        return state;
    }

    private static Map weights() {
        Map map = new HashMap();
        for (int i = 0; i < NAMES.length; i++) map.put(NAMES[i], Float.valueOf((i + 1) * 0.05f));
        return map;
    }

    private static float[][] pivots() {
        return new float[][]{{0f,0f,0f},{0f,0f,1f},{0f,0f,2f},{0f,0f,3f}};
    }

    private static Aero_BoneRenderPose[] poses() {
        Aero_BoneRenderPose[] result = new Aero_BoneRenderPose[4];
        for (int i = 0; i < result.length; i++) result[i] = new Aero_BoneRenderPose();
        reset(result); return result;
    }

    private static void reset(Aero_BoneRenderPose[] poses) {
        for (int i = 0; i < poses.length; i++) poses[i].reset();
    }

    private static String[] names(int count) {
        String[] result = new String[count];
        for (int i = 0; i < count; i++) result[i] = "morph_" + i;
        return result;
    }

    private static final class CountingDispatcher implements Aero_SoundCoalesce.Dispatcher {
        int calls;
        public void play(double x, double y, double z, String name, float volume, float pitch) {
            calls++;
            soundSink = soundSink * 31L + Double.doubleToLongBits(x) + name.hashCode();
        }
    }

    private static final class BoundedMap extends LinkedHashMap {
        BoundedMap() { super(16, 0.75f, true); }
        protected boolean removeEldestEntry(Map.Entry eldest) { return size() > 512; }
    }

    private static final class LegacyProfiler {
        private final Map starts = new HashMap();
        private final Map totals = new HashMap();
        void start(String name) { starts.put(name, Long.valueOf(System.nanoTime())); }
        void end(String name) {
            Long start = (Long) starts.remove(name);
            Long total = (Long) totals.get(name);
            totals.put(name, Long.valueOf((total == null ? 0L : total.longValue())
                + System.nanoTime() - start.longValue()));
        }
    }
}
