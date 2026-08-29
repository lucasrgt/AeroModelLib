package aero.modellib.test;

import aero.modellib.Aero_AnimatedBatcher;
import aero.modellib.Aero_BECellRenderer;
import aero.modellib.Aero_DisplayListBudget;
import aero.modellib.Aero_FrameSpikeLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Allocation-free online frame distribution for the ultra-stress client. */
public final class AeroUltraStressCensus {
    private static final long WARMUP_NS =
        Long.getLong("aero.ultra.warmupSec", 30L).longValue() * 1_000_000_000L;
    private static final long DURATION_NS =
        Long.getLong("aero.ultra.durationSec", 180L).longValue() * 1_000_000_000L;
    private static final long BUCKET_NS = 100_000L;
    private static final long[] HISTOGRAM = new long[100_001];
    private static final String OUTPUT =
        System.getProperty("aero.ultra.summary", "run/aero-ultra-summary.json");
    private static long firstFrameNs, measurementStartNs, lastFrameNs;
    private static long measurementStartEpochMillis, measurementEndEpochMillis;
    private static long frames, sumFrameNs, worstFrameNs, allocatedBytes;
    private static long over16, over33, over50, over100, over250, over1000;
    private static long gcStartCount, gcStartMillis;
    private static int maxQueued, maxBatches, maxClientArrayDraws, maxClientArrayVertices;
    private static int maxPosesReused, maxPosesResolved;
    private static int maxVerticesTransformed, maxVertexTransformsReused;
    private static int maxTessellatorBulkVertices;
    private static int maxPages, maxCachedPages, maxLiveLists;
    private static final long[] STAGE_SUMS = new long[9];
    private static final long[] WORST_STAGES = new long[9];
    private static final long[] CURRENT_STAGES = new long[9];
    private static boolean installed, written;

    private AeroUltraStressCensus() {}

    public static void install() {
        if (!AeroUltraStressConfig.ENABLED || installed) return;
        installed = true;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() { writeSummary(); }
        }, "aero-ultra-census"));
        System.out.println("[AeroUltraStress] census warmupSec=" + WARMUP_NS / 1_000_000_000L
            + " summary=" + OUTPUT);
    }

    public static void beforeFrame() {
        if (!AeroUltraStressConfig.ENABLED || !AeroUltraStressState.ready()) return;
        long start = Aero_FrameSpikeLogger.frameStartNanos();
        if (start == 0L) return;
        long now = System.nanoTime();
        if (firstFrameNs == 0L) firstFrameNs = now;
        if (now - firstFrameNs < WARMUP_NS) return;
        if (measurementStartNs == 0L) {
            measurementStartNs = now;
            measurementStartEpochMillis = System.currentTimeMillis();
            gcStartCount = Aero_FrameSpikeLogger.gcCollectionCount();
            gcStartMillis = Aero_FrameSpikeLogger.gcCollectionTimeMillis();
        }
        long frameNs = Math.max(0L, now - start);
        lastFrameNs = now;
        frames++;
        sumFrameNs += frameNs;
        HISTOGRAM[(int) Math.min(HISTOGRAM.length - 1, frameNs / BUCKET_NS)]++;
        if (frameNs >= 16_666_667L) over16++;
        if (frameNs >= 33_333_334L) over33++;
        if (frameNs >= 50_000_000L) over50++;
        if (frameNs >= 100_000_000L) over100++;
        if (frameNs >= 250_000_000L) over250++;
        if (frameNs >= 1_000_000_000L) over1000++;
        long[] stages = stages();
        for (int i = 0; i < stages.length; i++) STAGE_SUMS[i] += stages[i];
        allocatedBytes += Math.max(0L, Aero_FrameSpikeLogger.frameAllocatedBytes());
        maxQueued = Math.max(maxQueued, Aero_AnimatedBatcher.queuedThisFrame());
        maxBatches = Math.max(maxBatches, Aero_AnimatedBatcher.flushedBatchesThisFrame());
        maxClientArrayDraws = Math.max(maxClientArrayDraws,
            Aero_AnimatedBatcher.clientArrayDrawsThisFrame());
        maxClientArrayVertices = Math.max(maxClientArrayVertices,
            Aero_AnimatedBatcher.clientArrayVerticesThisFrame());
        maxPosesReused = Math.max(maxPosesReused,
            Aero_AnimatedBatcher.batchPosesReusedThisFrame());
        maxPosesResolved = Math.max(maxPosesResolved,
            Aero_AnimatedBatcher.batchPosesResolvedThisFrame());
        maxVerticesTransformed = Math.max(maxVerticesTransformed,
            Aero_AnimatedBatcher.batchVerticesTransformedThisFrame());
        maxVertexTransformsReused = Math.max(maxVertexTransformsReused,
            Aero_AnimatedBatcher.batchVertexTransformsReusedThisFrame());
        maxTessellatorBulkVertices = Math.max(maxTessellatorBulkVertices,
            Aero_AnimatedBatcher.tessellatorBulkVerticesThisFrame());
        maxPages = Math.max(maxPages, Aero_BECellRenderer.pageRebuildsThisFrame());
        maxCachedPages = Math.max(maxCachedPages, Aero_BECellRenderer.cachedPageCount());
        maxLiveLists = Math.max(maxLiveLists, Aero_DisplayListBudget.liveLists());
        if (frameNs > worstFrameNs) {
            worstFrameNs = frameNs;
            System.arraycopy(stages, 0, WORST_STAGES, 0, stages.length);
        }
        if (now - measurementStartNs >= DURATION_NS) {
            measurementEndEpochMillis = System.currentTimeMillis();
            writeSummary();
            System.out.println("[AeroUltraStress] measured window elapsed; exiting");
            System.exit(0);
        }
    }

    private static long[] stages() {
        CURRENT_STAGES[0] = positive(Aero_FrameSpikeLogger.clientTickNanos());
        CURRENT_STAGES[1] = positive(Aero_FrameSpikeLogger.worldSaveNanos());
        CURRENT_STAGES[2] = positive(Aero_FrameSpikeLogger.chunkCompileMaxNanos());
        CURRENT_STAGES[3] = positive(Aero_FrameSpikeLogger.terrainRenderNanos());
        CURRENT_STAGES[4] = positive(Aero_FrameSpikeLogger.aeroPrepareNanos());
        CURRENT_STAGES[5] = positive(Aero_FrameSpikeLogger.cellRebuildNanos());
        CURRENT_STAGES[6] = positive(Aero_FrameSpikeLogger.entityRenderNanos());
        CURRENT_STAGES[7] = positive(Aero_FrameSpikeLogger.worldFlushNanos());
        CURRENT_STAGES[8] = positive(Aero_FrameSpikeLogger.displayUpdateNanos());
        return CURRENT_STAGES;
    }

    private static synchronized void writeSummary() {
        if (!AeroUltraStressConfig.ENABLED || written) return;
        written = true;
        try {
            Path path = Path.of(OUTPUT).toAbsolutePath();
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, json().getBytes(StandardCharsets.UTF_8));
            System.out.println("[AeroUltraStress] summary=" + path + " frames=" + frames
                + " p99Ms=" + ms(percentile(990, 1000)) + " worstMs=" + ms(worstFrameNs));
        } catch (Exception error) {
            System.err.println("[AeroUltraStress] summary write failed: " + error);
        }
    }

    private static String json() {
        long elapsed = measurementStartNs == 0L ? 0L : Math.max(0L, lastFrameNs - measurementStartNs);
        long gcCount = frames == 0L ? 0L
            : delta(Aero_FrameSpikeLogger.gcCollectionCount(), gcStartCount);
        long gcMillis = frames == 0L ? 0L
            : delta(Aero_FrameSpikeLogger.gcCollectionTimeMillis(), gcStartMillis);
        StringBuilder out = new StringBuilder(1400).append("{\n");
        pair(out, "schema", 1).append(',').append('\n');
        pair(out, "machinesPerChunk", AeroUltraStressConfig.machinesPerChunk()).append(',').append('\n');
        pair(out, "spacingChunks", AeroUltraStressConfig.SPACING_CHUNKS).append(',').append('\n');
        pair(out, "frames", frames).append(',').append('\n');
        pair(out, "measurementStartEpochMillis", measurementStartEpochMillis).append(',').append('\n');
        pair(out, "measurementEndEpochMillis", measurementEndEpochMillis).append(',').append('\n');
        pair(out, "elapsedNanos", elapsed).append(',').append('\n');
        pair(out, "averageFrameNanos", frames == 0L ? 0L : sumFrameNs / frames).append(',').append('\n');
        pair(out, "p50FrameNanos", percentile(50, 100)).append(',').append('\n');
        pair(out, "p95FrameNanos", percentile(95, 100)).append(',').append('\n');
        pair(out, "p99FrameNanos", percentile(99, 100)).append(',').append('\n');
        pair(out, "p999FrameNanos", percentile(999, 1000)).append(',').append('\n');
        pair(out, "worstFrameNanos", worstFrameNs).append(',').append('\n');
        pair(out, "framesOver16ms", over16).append(',').append('\n');
        pair(out, "framesOver33ms", over33).append(',').append('\n');
        pair(out, "framesOver50ms", over50).append(',').append('\n');
        pair(out, "framesOver100ms", over100).append(',').append('\n');
        pair(out, "framesOver250ms", over250).append(',').append('\n');
        pair(out, "framesOver1000ms", over1000).append(',').append('\n');
        pair(out, "allocatedBytes", allocatedBytes).append(',').append('\n');
        pair(out, "gcCount", gcCount).append(',').append('\n');
        pair(out, "gcNanos", gcMillis * 1_000_000L).append(',').append('\n');
        pair(out, "maxAnimatedQueued", maxQueued).append(',').append('\n');
        pair(out, "maxAnimatedBatches", maxBatches).append(',').append('\n');
        pair(out, "maxClientArrayDraws", maxClientArrayDraws).append(',').append('\n');
        pair(out, "maxClientArrayVertices", maxClientArrayVertices).append(',').append('\n');
        pair(out, "maxBatchPosesReused", maxPosesReused).append(',').append('\n');
        pair(out, "maxBatchPosesResolved", maxPosesResolved).append(',').append('\n');
        pair(out, "maxBatchVerticesTransformed", maxVerticesTransformed).append(',').append('\n');
        pair(out, "maxBatchVertexTransformsReused", maxVertexTransformsReused).append(',').append('\n');
        pair(out, "maxTessellatorBulkVertices", maxTessellatorBulkVertices).append(',').append('\n');
        pair(out, "maxPageRebuilds", maxPages).append(',').append('\n');
        pair(out, "maxCachedPages", maxCachedPages).append(',').append('\n');
        pair(out, "maxLiveDisplayLists", maxLiveLists).append(',').append('\n');
        out.append("  \"stageOrder\": [\"clientTick\",\"worldSave\",\"chunkCompileMax\","
            + "\"terrainRender\",\"aeroPrepare\",\"cellRebuild\",\"entityRender\","
            + "\"aeroFlush\",\"displayUpdate\"],\n");
        array(out, "stageTotalNanos", STAGE_SUMS).append(',').append('\n');
        array(out, "worstFrameStageNanos", WORST_STAGES).append('\n');
        return out.append("}\n").toString();
    }

    private static long percentile(long numerator, long denominator) {
        if (frames == 0L) return 0L;
        long target = Math.max(1L, (frames * numerator + denominator - 1L) / denominator);
        long seen = 0L;
        for (int i = 0; i < HISTOGRAM.length; i++) {
            seen += HISTOGRAM[i];
            if (seen >= target) return i * BUCKET_NS;
        }
        return (HISTOGRAM.length - 1L) * BUCKET_NS;
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

    private static long positive(long value) { return Math.max(0L, value); }
    private static long delta(long value, long start) { return Math.max(0L, value - start); }
    private static double ms(long nanos) { return Math.round(nanos / 100_000.0d) / 10.0d; }
}
