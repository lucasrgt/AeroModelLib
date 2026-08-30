package aero.modellib;

import aero.modellib.optimization.OptimizationRef;

/**
 * Opt-in policy for bounded non-forced single-player chunk saves.
 *
 * <p>Beta 1.7.3 normally writes at most 24 dirty chunks every 40 ticks.
 * Reducing that batch spreads synchronous disk and NBT work across later
 * autosaves. Forced saves retain vanilla's complete drain and storage flush.
 */
@OptimizationRef({"aero.world.incremental-autosave"})
public final class Aero_IncrementalAutosave {
    private static final String ENABLE_PROPERTY =
        "aero.incrementalAutosave";
    private static final String BUDGET_PROPERTY =
        "aero.incrementalAutosave.chunkBudget";

    private Aero_IncrementalAutosave() {}

    public static int chunkLimit(int vanillaLimit, boolean force) {
        if (!isBounded(force)) {
            return vanillaLimit;
        }
        return intProperty(BUDGET_PROPERTY, 1, 1, vanillaLimit);
    }

    public static boolean isBounded(boolean force) {
        return !force && Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static int intProperty(String name, int fallback,
                                   int minimum, int maximum) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < minimum) return minimum;
            if (parsed > maximum) return maximum;
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
