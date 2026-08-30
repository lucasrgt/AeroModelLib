package aero.modellib.worldline;

import static worldline.test.Expect.expect;
import static worldline.test.Worldline.describe;
import static worldline.test.Worldline.test;

import aero.modellib.render.Aero_ChunkWorkScheduler;
import aero.modellib.render.Aero_ChunkPrebakePriority;
import aero.modellib.skeletal.Aero_MorphState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import worldline.test.WorldlineSpec;

/** External differential coverage for maintained platform-neutral Aero paths. */
public final class AeroCpuPathsWorldlineTest extends WorldlineSpec {
    @Override protected void define() {
        describe("Aero optimized CPU paths", () -> {
            test("parallel morph storage matches a boxed reference", context ->
                    morphDifferential());
            test("chunk scheduling is bounded and visibility-first", context ->
                    schedulerDifferential());
            test("chunk debt prevents hidden-work starvation", context ->
                    schedulerFairness());
            test("camera pre-bake reprioritizes after teleport", context ->
                    cameraPrebakeReprioritization());
        });
    }

    @SuppressWarnings("unchecked")
    private static void morphDifferential() {
        Aero_MorphState candidate = new Aero_MorphState();
        Map<String, Float> reference = new HashMap<String, Float>();
        for (int step = 0; step < 256; step++) {
            String name = "morph-" + (step * 17 % 13);
            float weight = step % 7 == 0 ? 0.0F : (step % 19 - 9) / 8.0F;
            candidate.set(name, weight);
            if (weight == 0.0F) reference.remove(name);
            else reference.put(name, Float.valueOf(weight));
            expect(candidate.activeCount()).toEqual(reference.size());
            for (Map.Entry<String, Float> entry : reference.entrySet()) {
                expect(Float.floatToIntBits(candidate.get(entry.getKey())))
                        .toEqual(Float.floatToIntBits(entry.getValue().floatValue()));
            }
        }
        Map<String, Float> snapshot = (Map<String, Float>) candidate.getWeightsView();
        expect(snapshot).toEqual(reference);
    }

    private static void schedulerDifferential() {
        List<Work> queue = new ArrayList<Work>();
        queue.add(new Work("hidden-near", false, 1.0D));
        queue.add(new Work("visible-far", true, 100.0D));
        queue.add(new Work("visible-near", true, 4.0D));
        Adapter adapter = new Adapter();
        Aero_ChunkWorkScheduler<Work> scheduler = new Aero_ChunkWorkScheduler<Work>();

        expect(scheduler.schedule(queue, adapter, 2, 20, 20)).toEqual(2);
        expect(adapter.rebuilt.toString()).toEqual("[visible-near, visible-far]");
        expect(queue.size()).toEqual(1);
        expect(queue.get(0).name).toEqual("hidden-near");
    }

    private static void schedulerFairness() {
        List<Work> queue = new ArrayList<Work>();
        Work hidden = new Work("hidden", false, 1.0D);
        queue.add(hidden);
        Adapter adapter = new Adapter();
        Aero_ChunkWorkScheduler<Work> scheduler = new Aero_ChunkWorkScheduler<Work>();
        for (int frame = 0; frame < 4; frame++) {
            queue.add(new Work("visible-" + frame, true, 1.0D));
            scheduler.schedule(queue, adapter, 1, 100, 3);
        }
        expect(hidden.dirty).toBeFalse();
        expect(adapter.rebuilt.contains("hidden")).toBeTrue();
        expect(scheduler.urgentBuilt()).toEqual(1);
    }

    private static void cameraPrebakeReprioritization() {
        int before = Aero_ChunkPrebakePriority.tier(
                false, 160, 160, 8.0D, 8.0D, 0.0F, 3);
        int after = Aero_ChunkPrebakePriority.tier(
                false, 160, 160, 168.0D, 168.0D, 0.0F, 3);
        expect(before).toEqual(Aero_ChunkPrebakePriority.BACKGROUND);
        expect(after).toEqual(Aero_ChunkPrebakePriority.CURRENT);
    }

    private static final class Work {
        final String name;
        final boolean visible;
        final double distance;
        boolean dirty = true;

        Work(String name, boolean visible, double distance) {
            this.name = name;
            this.visible = visible;
            this.distance = distance;
        }
    }

    private static final class Adapter implements Aero_ChunkWorkScheduler.Adapter<Work> {
        final List<String> rebuilt = new ArrayList<String>();

        @Override public boolean isDirty(Work work) { return work.dirty; }
        @Override public boolean isVisible(Work work) { return work.visible; }
        @Override public double squaredDistance(Work work) { return work.distance; }
        @Override public void rebuild(Work work) { rebuilt.add(work.name); }
        @Override public void markClean(Work work) { work.dirty = false; }
    }
}
