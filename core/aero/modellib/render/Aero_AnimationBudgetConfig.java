package aero.modellib.render;

/** Immutable system-property configuration for animation admission. */
final class Aero_AnimationBudgetConfig {
    static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("aero.animBudget"));
    static final int MAX_ANIMATED = Integer.getInteger("aero.maxAnimatedBE", -1).intValue();
    static final double TAN_HALF_VFOV = Math.tan(Math.toRadians(35d));
    static final double CRITICAL_PX = decimal("aero.animBudget.criticalPx", 64d, 1d, 10000d);
    static final double MID_PX = decimal("aero.animBudget.midPx", 32d, 1d, 10000d);
    static final double LOW_PX = decimal("aero.animBudget.lowPx", 16d, 1d, 10000d);
    static final double NEAR_BLOCKS = decimal("aero.animBudget.nearBlocks", 12d, 0d, 1024d);
    static final int CRITICAL_EXTRA = Integer.getInteger("aero.animBudget.criticalExtra", criticalExtra()).intValue();
    static final int HYSTERESIS_FRAMES = integer("aero.animBudget.hysteresisFrames", 6, 0, 600);
    static final int HYSTERESIS_EXTRA = integer("aero.animBudget.hysteresisExtra", hysteresisExtra(), 0, 100000);
    static final boolean HARD_CAP = !"false".equalsIgnoreCase(System.getProperty("aero.animBudget.hardCap"));
    static final boolean VISIBLE_CHUNK_THROTTLE = "true".equalsIgnoreCase(System.getProperty("aero.animBudget.visibleChunkThrottle"));
    static final boolean VISIBLE_CHUNK_SMOOTH = !"false".equalsIgnoreCase(System.getProperty("aero.animBudget.visibleChunkSmooth"));
    static final int VISIBLE_CHUNK_MID = integer("aero.animBudget.visibleChunkMid", 350, 1, 100000);
    static final int VISIBLE_CHUNK_HIGH = integer("aero.animBudget.visibleChunkHigh", 450, 1, 100000);
    static final int VISIBLE_CHUNK_MID_MAX = integer("aero.animBudget.visibleChunkMidMax", fraction(2, 3), 0, 100000);
    static final int VISIBLE_CHUNK_HIGH_MAX = integer("aero.animBudget.visibleChunkHighMax", fraction(1, 3), 0, 100000);
    static final int VISIBLE_CHUNK_STEP = integer("aero.animBudget.visibleChunkStep", step(12, 4), 1, 100000);
    static final int VISIBLE_CHUNK_RECOVERY_STEP = integer("aero.animBudget.visibleChunkRecoveryStep", step(24, 2), 1, 100000);
    static final boolean FRAME_PRESSURE_THROTTLE = "true".equalsIgnoreCase(System.getProperty("aero.animBudget.framePressure"));
    static final double FRAME_PRESSURE_MS = decimal("aero.animBudget.framePressureMs", 45d, 1d, 10000d);
    static final double DISPLAY_STALL_MS = decimal("aero.animBudget.displayStallMs", 35d, 0d, 10000d);
    static final double RENDER_CHUNK_STALL_MS = decimal("aero.animBudget.renderChunkStallMs", 30d, 0d, 10000d);
    static final double GC_STALL_MS = decimal("aero.animBudget.gcStallMs", 18d, 0d, 10000d);
    static final int FRAME_PRESSURE_FRAMES = integer("aero.animBudget.framePressureFrames", 90, 0, 100000);
    static final int FRAME_PRESSURE_RECOVERY_FRAMES = integer("aero.animBudget.framePressureRecoveryFrames", 90, 1, 100000);
    static final int FRAME_PRESSURE_STEP = integer("aero.animBudget.framePressureStep", step(2, 8), 1, 100000);
    static final int FRAME_PRESSURE_MIN = integer("aero.animBudget.framePressureMin", pressureMin(), 0, 100000);
    static final boolean THROUGHPUT_THROTTLE = "true".equalsIgnoreCase(System.getProperty("aero.animBudget.throughputThrottle"));
    static final double THROUGHPUT_RATIO = decimal("aero.animBudget.throughputRatio", 1.65d, 1.01d, 100d);
    static final double THROUGHPUT_MIN_MS = decimal("aero.animBudget.throughputMinMs", 6d, 0d, 10000d);
    static final int THROUGHPUT_FRAMES = integer("aero.animBudget.throughputFrames", 4, 1, 100000);
    private Aero_AnimationBudgetConfig() {}

    private static int criticalExtra() { return MAX_ANIMATED > 0 ? Math.max(8, MAX_ANIMATED / 4) : 0; }
    private static int hysteresisExtra() { return MAX_ANIMATED > 0 ? Math.max(4, MAX_ANIMATED / 8) : 0; }
    private static int fraction(int numerator, int denominator) {
        return MAX_ANIMATED > 0 ? Math.max(8, MAX_ANIMATED * numerator / denominator) : 0;
    }
    private static int step(int divisor, int minimum) {
        return MAX_ANIMATED > 0 ? Math.max(minimum, MAX_ANIMATED / divisor) : 1;
    }
    private static int pressureMin() {
        return MAX_ANIMATED <= 0 ? 0 : Math.max(8, Math.min(16, MAX_ANIMATED / 4));
    }
    static int integer(String name, int fallback, int min, int max) {
        String raw = System.getProperty(name); if (raw == null) return fallback;
        try { int value = Integer.parseInt(raw.trim()); return value >= min && value <= max ? value : fallback; }
        catch (NumberFormatException ignored) { return fallback; }
    }
    static double decimal(String name, double fallback, double min, double max) {
        String raw = System.getProperty(name); if (raw == null) return fallback;
        try { double value = Double.parseDouble(raw.trim()); return value >= min && value <= max ? value : fallback; }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
