package aero.modellib.test;

/** Bounded knobs for the deliberately pathological ultra-stress scene. */
final class AeroUltraStressConfig {
    static final boolean ENABLED = Boolean.getBoolean("aero.ultra")
        && !Boolean.getBoolean("aero.testmod.disabled");
    static final int SPACING_CHUNKS = integer("aero.ultra.spacingChunks", 4, 1, 32);
    static final int LAYERS = integer("aero.ultra.layers", 8, 1, 48);
    static final boolean SOLID_FLOORS =
        !"false".equalsIgnoreCase(System.getProperty("aero.ultra.solidFloors", "true"));
    static final boolean PHASE_SPREAD =
        Boolean.parseBoolean(System.getProperty("aero.ultra.phaseSpread", "false"));
    static final boolean JOURNEY =
        Boolean.parseBoolean(System.getProperty("aero.ultra.journey", "true"));
    static final int JOURNEY_CHECKPOINT =
        integer("aero.ultra.journeyCheckpoint", -1, -1, 9);
    static final long DURATION_SECONDS =
        longInteger("aero.ultra.durationSec", 180L, 10L, 3600L);
    static final long WARMUP_SECONDS =
        longInteger("aero.ultra.warmupSec", 30L, 0L, 600L);

    private AeroUltraStressConfig() {}

    static int machinesPerChunk() {
        return 16 * 16 * LAYERS;
    }

    static int verticalStride() {
        return SOLID_FLOORS ? 2 : 1;
    }

    private static int integer(String name, int fallback, int minimum, int maximum) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException ignored) {
            System.out.println("[AeroUltraStress] invalid " + name + "=" + raw
                + "; using " + fallback);
            return fallback;
        }
    }

    private static long longInteger(String name, long fallback, long minimum, long maximum) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            return Math.max(minimum, Math.min(maximum, Long.parseLong(raw.trim())));
        } catch (NumberFormatException ignored) {
            System.out.println("[AeroUltraStress] invalid " + name + "=" + raw
                + "; using " + fallback);
            return fallback;
        }
    }
}
