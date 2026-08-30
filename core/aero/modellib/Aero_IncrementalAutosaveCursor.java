package aero.modellib;

import aero.modellib.optimization.OptimizationRef;

/** Maintains fair progress when a bounded save returns before scanning every chunk. */
@OptimizationRef({"aero.world.incremental-autosave"})
public final class Aero_IncrementalAutosaveCursor {
    private int cursor;
    private int nextCursor;

    public void begin() {
        nextCursor = cursor;
    }

    public int visit(int vanillaIndex, int size, boolean force) {
        if (!Aero_IncrementalAutosave.isBounded(force) || size <= 0) {
            return vanillaIndex;
        }
        int mapped = (cursor + vanillaIndex) % size;
        nextCursor = (mapped + 1) % size;
        return mapped;
    }

    public void end(boolean force) {
        if (Aero_IncrementalAutosave.isBounded(force)) {
            cursor = nextCursor;
        }
    }
}
