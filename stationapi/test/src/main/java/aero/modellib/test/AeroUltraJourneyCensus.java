package aero.modellib.test;

/** Per-camera-phase frame and activation census for the ULTRA journey. */
final class AeroUltraJourneyCensus {
    private static final int COUNT = AeroUltraJourney.phaseCount();
    private static final long BUCKET_NS = 200_000L;
    private static final long[][] HISTOGRAMS = new long[COUNT][5001];
    private static final long[] FRAMES = new long[COUNT];
    private static final long[] TOTAL_NS = new long[COUNT];
    private static final long[] WORST_NS = new long[COUNT];
    private static final long[] OVER_33 = new long[COUNT];
    private static final long[] ALLOCATED = new long[COUNT];
    private static final long[] MAX_QUEUED = new long[COUNT];
    private static final long[] MAX_IMMEDIATE = new long[COUNT];
    private static final long[] MAX_VIEW_CULLED = new long[COUNT];
    private static final long[] MAX_VISIBLE_CHUNKS = new long[COUNT];

    private AeroUltraJourneyCensus() {}

    static void record(int phase, long frameNs, long allocated, int queued,
                       int immediate, int viewCulled, int visibleChunks) {
        if (phase < 0 || phase >= COUNT) return;
        FRAMES[phase]++;
        TOTAL_NS[phase] += frameNs;
        WORST_NS[phase] = Math.max(WORST_NS[phase], frameNs);
        if (frameNs >= 33_333_334L) OVER_33[phase]++;
        ALLOCATED[phase] += allocated;
        MAX_QUEUED[phase] = Math.max(MAX_QUEUED[phase], queued);
        MAX_IMMEDIATE[phase] = Math.max(MAX_IMMEDIATE[phase], immediate);
        MAX_VIEW_CULLED[phase] = Math.max(MAX_VIEW_CULLED[phase], viewCulled);
        MAX_VISIBLE_CHUNKS[phase] = Math.max(MAX_VISIBLE_CHUNKS[phase], visibleChunks);
        int bucket = (int) Math.min(HISTOGRAMS[phase].length - 1, frameNs / BUCKET_NS);
        HISTOGRAMS[phase][bucket]++;
    }

    static void appendJson(StringBuilder out) {
        pair(out, "journeyEnabled", AeroUltraStressConfig.JOURNEY ? 1 : 0).append(',').append('\n');
        pair(out, "journeyCheckpoint", AeroUltraStressConfig.JOURNEY_CHECKPOINT).append(',').append('\n');
        pair(out, "journeyCoverageComplete", coverageComplete() ? 1 : 0).append(',').append('\n');
        names(out).append(',').append('\n');
        array(out, "journeyPhaseFrames", FRAMES).append(',').append('\n');
        array(out, "journeyPhaseAverageFrameNanos", averages()).append(',').append('\n');
        array(out, "journeyPhaseP95FrameNanos", percentiles(95, 100)).append(',').append('\n');
        array(out, "journeyPhaseP99FrameNanos", percentiles(99, 100)).append(',').append('\n');
        array(out, "journeyPhaseWorstFrameNanos", WORST_NS).append(',').append('\n');
        array(out, "journeyPhaseFramesOver33ms", OVER_33).append(',').append('\n');
        array(out, "journeyPhaseAllocatedBytes", ALLOCATED).append(',').append('\n');
        array(out, "journeyPhaseMaxAnimatedQueued", MAX_QUEUED).append(',').append('\n');
        array(out, "journeyPhaseMaxImmediateRenders", MAX_IMMEDIATE).append(',').append('\n');
        array(out, "journeyPhaseMaxViewCulled", MAX_VIEW_CULLED).append(',').append('\n');
        array(out, "journeyPhaseMaxVisibleChunks", MAX_VISIBLE_CHUNKS).append(',').append('\n');
    }

    private static boolean coverageComplete() {
        if (!AeroUltraStressConfig.JOURNEY) return true;
        if (AeroUltraStressConfig.JOURNEY_CHECKPOINT >= 0)
            return FRAMES[AeroUltraStressConfig.JOURNEY_CHECKPOINT] > 0L;
        for (long frames : FRAMES) if (frames == 0L) return false;
        return true;
    }

    private static long[] averages() {
        long[] result = new long[COUNT];
        for (int i = 0; i < COUNT; i++) result[i] = FRAMES[i] == 0L ? 0L : TOTAL_NS[i] / FRAMES[i];
        return result;
    }

    private static long[] percentiles(long numerator, long denominator) {
        long[] result = new long[COUNT];
        for (int phase = 0; phase < COUNT; phase++) {
            long target = Math.max(1L, (FRAMES[phase] * numerator + denominator - 1L) / denominator);
            long seen = 0L;
            for (int bucket = 0; bucket < HISTOGRAMS[phase].length; bucket++) {
                seen += HISTOGRAMS[phase][bucket];
                if (seen >= target) { result[phase] = bucket * BUCKET_NS; break; }
            }
        }
        return result;
    }

    private static StringBuilder names(StringBuilder out) {
        out.append("  \"journeyPhaseOrder\": [");
        for (int i = 0; i < COUNT; i++) {
            if (i > 0) out.append(',');
            out.append('\"').append(AeroUltraJourney.name(i)).append('\"');
        }
        return out.append(']');
    }

    private static StringBuilder pair(StringBuilder out, String name, long value) {
        return out.append("  \"").append(name).append("\": ").append(value);
    }

    private static StringBuilder array(StringBuilder out, String name, long[] values) {
        out.append("  \"").append(name).append("\": [");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        return out.append(']');
    }
}
