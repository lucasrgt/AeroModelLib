package aero.modellib;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import aero.modellib.render.Aero_AnimationRenderBudget;
import aero.modellib.render.Aero_AnimationTickBudget;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderLoadGovernor;

/**
 * Opt-in frame spike logger for dense BE scenes. It samples once per render
 * frame and prints a compact diagnostic line when the previous frame exceeded
 * the configured threshold.
 */
final class Aero_FrameSpikeFrame extends Aero_FrameSpikeState {
    private Aero_FrameSpikeFrame() {}

static void beginFrame() {
        if (!TIMING_ENABLED) return;
        long now = System.nanoTime();
        long gcCount = Aero_FrameSpikeMetrics.gcCollectionCount();
        long gcTimeMs = Aero_FrameSpikeMetrics.gcCollectionTimeMs();
        long threadCpuNs = Aero_FrameSpikeMetrics.currentThreadCpuTimeNs();
        long threadAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
        if (lastGcCount < 0L) {
            lastGcCount = gcCount;
            lastGcTimeMs = gcTimeMs;
            lastThreadCpuNs = threadCpuNs;
            lastThreadAllocBytes = threadAllocBytes;
        }
        if (lastFrameStartNs != 0L) {
            double frameMs = (now - lastFrameStartNs) / 1000000.0d;
            long gcCountDelta = Aero_FrameSpikeMetrics.positiveDelta(gcCount, lastGcCount);
            long gcTimeDelta = Aero_FrameSpikeMetrics.positiveDelta(gcTimeMs, lastGcTimeMs);
            lastFrameCpuNs = threadCpuNs >= 0L && lastThreadCpuNs >= 0L
                ? Aero_FrameSpikeMetrics.positiveDelta(threadCpuNs, lastThreadCpuNs)
                : -1L;
            lastFrameAllocBytes = threadAllocBytes >= 0L && lastThreadAllocBytes >= 0L
                ? Aero_FrameSpikeMetrics.positiveDelta(threadAllocBytes, lastThreadAllocBytes)
                : -1L;
            Aero_AnimationRenderBudget.recordFramePressure(frameMs,
                lastDisplayUpdateNs / 1000000.0d,
                lastRenderChunksNs / 1000000.0d,
                gcTimeDelta);
            Aero_RenderLoadGovernor.recordFramePressure(frameMs,
                lastDisplayUpdateNs / 1000000.0d,
                lastRenderChunksNs / 1000000.0d,
                lastRenderEntitiesNs / 1000000.0d,
                gcTimeDelta,
                Aero_ChunkVisibility.visibleChunkCount());
            Aero_FramePacer.recordFramePressure(frameMs,
                lastDisplayUpdateNs / 1000000.0d);
            if (ENABLED && frameMs >= THRESHOLD_MS
                && (MIN_INTERVAL_NS == 0L || now - lastLogNs >= MIN_INTERVAL_NS)) {
                lastLogNs = now;
                Aero_FrameSpikeFrame.logSpike(frameMs, gcCountDelta, gcTimeDelta);
            } else if (ENABLED && LOG_GC && gcCountDelta > 0L) {
                Aero_FrameSpikeWriter.logEvent("GC", frameMs, gcCountDelta, gcTimeDelta);
            } else if (ENABLED && HEARTBEAT_NS > 0L && now - lastHeartbeatNs >= HEARTBEAT_NS) {
                lastHeartbeatNs = now;
                Aero_FrameSpikeWriter.logEvent("Pulse", frameMs, gcCountDelta, gcTimeDelta);
            }
        }
        Aero_FrameSpikeFrame.resetFrameStageCounters();
        lastFrameStartNs = now;
        lastGcCount = gcCount;
        lastGcTimeMs = gcTimeMs;
        lastThreadCpuNs = threadCpuNs;
        lastThreadAllocBytes = threadAllocBytes;
        gameRendererUpdateStartNs = now;
        gameRendererUpdateStartAllocBytes = threadAllocBytes;
        Aero_AnimatedBatcher.beginFrameCounters();
        Aero_MeshRenderer.beginFrameCounters();
    }

static void logSpike(double frameMs, long gcCountDelta, long gcTimeDelta) {
        Aero_FrameSpikeWriter.logEvent("FrameSpike", frameMs, gcCountDelta, gcTimeDelta);
    }

static void resetFrameStageCounters() {
        lastFrameCpuNs = 0L;
        lastFrameAllocBytes = 0L;
        lastGameRendererUpdateNs = 0L;
        lastGameRendererUpdateAllocBytes = 0L;
        lastRenderWorldNs = 0L;
        lastRenderWorldAllocBytes = 0L;
        lastAeroRenderPrepNs = 0L;
        lastRenderEntitiesNs = 0L;
        lastRenderEntitiesAllocBytes = 0L;
        lastClientTickNs = 0L;
        lastClientTickAllocBytes = 0L;
        lastDisplayUpdateNs = 0L;
        lastDisplayUpdateAllocBytes = 0L;
        lastProfilerChartNs = 0L;
        lastWorldSaveNs = 0L;
        lastWorldSaveAllocBytes = 0L;
        worldSaveSkipped = 0L;
        lastChunkCompileNs = 0L;
        lastChunkCompileAllocBytes = 0L;
        lastChunkCompileMaxNs = 0L;
        chunkCompileCalls = 0L;
        chunkCompileSkipped = 0L;
        slowChunkCompiles = 0L;
        lastRenderChunksNs = 0L;
        lastRenderChunksAllocBytes = 0L;
        lastRenderChunksMaxNs = 0L;
        renderChunksCalls = 0L;
        lastWorldFlushNs = 0L;
        lastWorldFlushAllocBytes = 0L;
        slowWorldFlushes = 0L;
        lastCellRebuildNs = 0L;
    }
}
