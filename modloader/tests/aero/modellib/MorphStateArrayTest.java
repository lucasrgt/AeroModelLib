package aero.modellib;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import aero.modellib.skeletal.Aero_MorphState;

import static org.junit.Assert.*;

/**
 * Unit tests for the parallel-array morph weight storage:
 *  - indexed render-path accessors (activeCount/nameAt/weightAt)
 *  - zero-weight eviction and swap-with-last compaction
 *  - in-place overwrite and growth past the initial capacity
 *  - getWeightsView snapshot semantics
 *  - NBT round-trip with removals in between
 */
public class MorphStateArrayTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void indexedAccessorsExposeEveryStoredEntry() {
        Aero_MorphState state = new Aero_MorphState();
        state.set("smile", 0.5f);
        state.set("frown", -0.25f);

        assertEquals(2, state.activeCount());
        Map seen = new HashMap();
        for (int i = 0; i < state.activeCount(); i++) {
            seen.put(state.nameAt(i), Float.valueOf(state.weightAt(i)));
        }
        assertEquals(0.5f, ((Float) seen.get("smile")).floatValue(), DELTA);
        assertEquals(-0.25f, ((Float) seen.get("frown")).floatValue(), DELTA);
    }

    @Test
    public void overwriteUpdatesInPlaceWithoutGrowingCount() {
        Aero_MorphState state = new Aero_MorphState();
        state.set("smile", 0.5f);
        state.set("smile", 0.9f);

        assertEquals(1, state.activeCount());
        assertEquals(0.9f, state.get("smile"), DELTA);
        assertEquals(0.9f, state.weightAt(0), DELTA);
    }

    @Test
    public void zeroWeightRemovalCompactsStorage() {
        Aero_MorphState state = new Aero_MorphState();
        state.set("a", 1f);
        state.set("b", 2f);
        state.set("c", 3f);

        state.set("a", 0f);

        assertEquals(2, state.activeCount());
        assertEquals(0f, state.get("a"), DELTA);
        assertEquals(2f, state.get("b"), DELTA);
        assertEquals(3f, state.get("c"), DELTA);
        // Every remaining index stays readable and non-zero.
        for (int i = 0; i < state.activeCount(); i++) {
            assertNotNull(state.nameAt(i));
            assertTrue(state.weightAt(i) != 0f);
        }
    }

    @Test
    public void growsPastInitialCapacity() {
        Aero_MorphState state = new Aero_MorphState();
        for (int i = 0; i < 12; i++) {
            state.set("morph" + i, i + 1f);
        }
        assertEquals(12, state.activeCount());
        for (int i = 0; i < 12; i++) {
            assertEquals(i + 1f, state.get("morph" + i), DELTA);
        }
    }

    @Test
    public void indexOutOfRangeThrows() {
        Aero_MorphState state = new Aero_MorphState();
        state.set("smile", 0.5f);
        try {
            state.nameAt(1);
            fail("expected out-of-range rejection");
        } catch (IndexOutOfBoundsException expected) {
            assertTrue(expected.getMessage().contains("1"));
        }
        try {
            state.weightAt(-1);
            fail("expected out-of-range rejection");
        } catch (IndexOutOfBoundsException expected) {
            // message covered above
        }
    }

    @Test
    public void weightsViewIsASnapshot() {
        Aero_MorphState state = new Aero_MorphState();
        state.set("smile", 0.5f);

        Map view = state.getWeightsView();
        state.set("smile", 0.9f);
        state.set("frown", 0.1f);

        assertEquals("snapshot must not track later mutations", 1, view.size());
        assertEquals(0.5f, ((Float) view.get("smile")).floatValue(), DELTA);
        assertEquals(2, state.getWeightsView().size());
    }

    @Test
    public void clearReleasesEveryEntry() {
        Aero_MorphState state = new Aero_MorphState();
        state.set("smile", 0.5f);
        state.set("frown", 0.2f);
        state.clear();

        assertTrue(state.isEmpty());
        assertEquals(0, state.activeCount());
        assertEquals(0f, state.get("smile"), DELTA);
    }

    @Test
    public void nbtRoundTripAfterRemovals() {
        Aero_MorphState src = new Aero_MorphState();
        src.set("a", 0.7f);
        src.set("b", -0.2f);
        src.set("c", 0.4f);
        src.set("b", 0f);

        final Map bag = new HashMap();
        src.writeStringFloatMapNbt(new Aero_MorphState.StringFloatBagWriter() {
            public void put(String name, float value) { bag.put(name, Float.valueOf(value)); }
        });
        assertEquals(2, bag.size());

        Aero_MorphState dst = new Aero_MorphState();
        dst.set("stale", 1f);
        dst.readStringFloatMapNbt(new Aero_MorphState.StringFloatBagReader() {
            public void forEach(Aero_MorphState.StringFloatBagWriter sink) {
                java.util.Iterator it = bag.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry e = (Map.Entry) it.next();
                    sink.put((String) e.getKey(), ((Float) e.getValue()).floatValue());
                }
            }
        });

        assertEquals(2, dst.activeCount());
        assertEquals(0.7f, dst.get("a"), DELTA);
        assertEquals(0.4f, dst.get("c"), DELTA);
        assertEquals("replaced state must drop pre-read entries", 0f, dst.get("stale"), DELTA);
    }

    @Test
    public void readSkipsZeroAndNullEntries() {
        Aero_MorphState dst = new Aero_MorphState();
        dst.readStringFloatMapNbt(new Aero_MorphState.StringFloatBagReader() {
            public void forEach(Aero_MorphState.StringFloatBagWriter sink) {
                sink.put("kept", 0.3f);
                sink.put("zero", 0f);
                sink.put(null, 0.9f);
            }
        });
        assertEquals(1, dst.activeCount());
        assertEquals(0.3f, dst.get("kept"), DELTA);
    }
}
