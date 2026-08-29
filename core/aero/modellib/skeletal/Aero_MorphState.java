package aero.modellib.skeletal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import aero.modellib.optimization.OptimizationRef;

/**
 * Per-instance morph weights — usually attached to a tile/entity
 * alongside an {@link Aero_AnimationState} so persistent state survives
 * world saves.
 *
 * <p>Weights are unbounded by design (clamping at runtime would silently
 * eat overshoot, which is occasionally desired for stylized animation).
 * Renderers that need clamped behavior should clamp on read.
 *
 * <p>Storage is a pair of parallel (name, weight) arrays so the render
 * path reads active morphs by index — {@link #activeCount()},
 * {@link #nameAt(int)}, {@link #weightAt(int)} — with no map iterator,
 * {@code Map.Entry}, or {@code Float} boxing per draw. Morph sets are
 * small (a handful of names), so name lookups are linear scans.
 * Zero-weighted entries are removed in {@link #set} so every stored
 * entry is active by construction.
 *
 * <p>NBT serialization is delegated to a platform adapter so this class
 * stays in pure-Java {@code core/} — see
 * {@link Aero_MorphState#writeStringFloatMapNbt} for the contract.
 */
@OptimizationRef({"aero.skeletal.morph-weight-arrays"})
public final class Aero_MorphState {

    private String[] names = new String[4];
    private float[] weights = new float[4];
    private int count;

    public boolean isEmpty() {
        return count == 0;
    }

    /** Number of stored (all non-zero) morph entries. Render-path index bound. */
    public int activeCount() {
        return count;
    }

    /** Morph name at {@code index} in {@code [0, activeCount())}. */
    public String nameAt(int index) {
        checkIndex(index);
        return names[index];
    }

    /** Morph weight at {@code index} in {@code [0, activeCount())}. */
    public float weightAt(int index) {
        checkIndex(index);
        return weights[index];
    }

    public float get(String name) {
        if (name == null) return 0f;
        int index = indexOf(name);
        return index < 0 ? 0f : weights[index];
    }

    public void set(String name, float weight) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("morph name must not be empty");
        }
        if (Float.isNaN(weight) || Float.isInfinite(weight)) {
            throw new IllegalArgumentException("morph '" + name
                + "': weight must be finite, got " + weight);
        }
        if (weight == 0f) {
            int index = indexOf(name);
            if (index >= 0) removeAt(index);
            return;
        }
        put(name, weight);
    }

    public void clear() {
        Arrays.fill(names, 0, count, null);
        count = 0;
    }

    /**
     * Snapshot of the weights as a {@code Map<String, Float>} for
     * serialization adapters and legacy callers. Built per call — render
     * code should use the indexed accessors instead.
     */
    public Map getWeightsView() {
        HashMap view = new HashMap();
        for (int index = 0; index < count; index++) {
            view.put(names[index], Float.valueOf(weights[index]));
        }
        return view;
    }

    /**
     * Writes morph weights into an NBT-compatible bag via the supplied
     * adapter. Entries arrive in deterministic storage order (insertion
     * order, disturbed only by removals — fine for save round-trips since
     * readers look up by key, not order). Caller's adapter knows how to
     * write {@code (String, float)} pairs into its NBT compound.
     */
    public void writeStringFloatMapNbt(StringFloatBagWriter writer) {
        for (int index = 0; index < count; index++) {
            writer.put(names[index], weights[index]);
        }
    }

    /**
     * Reads weights from a bag, replacing any existing state. Zero-weight
     * and null-name entries are skipped, matching write-side invariants.
     */
    public void readStringFloatMapNbt(StringFloatBagReader reader) {
        clear();
        reader.forEach(new StringFloatBagWriter() {
            public void put(String name, float value) {
                if (name != null && value != 0f) Aero_MorphState.this.put(name, value);
            }
        });
    }

    private void put(String name, float weight) {
        int index = indexOf(name);
        if (index >= 0) {
            weights[index] = weight;
            return;
        }
        if (count == names.length) {
            names = Arrays.copyOf(names, count * 2);
            weights = Arrays.copyOf(weights, count * 2);
        }
        names[count] = name;
        weights[count] = weight;
        count++;
    }

    private int indexOf(String name) {
        for (int index = 0; index < count; index++) {
            if (names[index].equals(name)) return index;
        }
        return -1;
    }

    private void removeAt(int index) {
        int last = count - 1;
        names[index] = names[last];
        weights[index] = weights[last];
        names[last] = null;
        count = last;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("morph index " + index + " of " + count);
        }
    }

    /** Adapter interface for writing into NBT (or any string→float bag). */
    public interface StringFloatBagWriter {
        void put(String name, float value);
    }

    /** Adapter interface for reading out of NBT. */
    public interface StringFloatBagReader {
        void forEach(StringFloatBagWriter sink);
    }
}
