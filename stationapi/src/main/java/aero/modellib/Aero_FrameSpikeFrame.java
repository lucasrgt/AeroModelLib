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
            initializeMetrics(gcCount, gcTimeMs, threadCpuNs, threadAllocBytes);
        }
        if (lastFrameStartNs != 0L) {
            completeFrame(now, gcCount, gcTimeMs, threadCpuNs, threadAllocBytes);
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

private static void initializeMetrics(long gcCount, long gcTimeMs,
                                      long threadCpuNs, long threadAllocBytes) {
        lastGcCount = gcCount;
        lastGcTimeMs = gcTimeMs;
        lastThreadCpuNs = threadCpuNs;
        lastThreadAllocBytes = threadAllocBytes;
    }

private static void completeFrame(long now, long gcCount, long gcTimeMs,
                                  long threadCpuNs, long threadAllocBytes) {
        double frameMs = (now - lastFrameStartNs) / 1000000.0d;
        Aero_Prewarm.recordFrameTime(frameMs);
        long gcCountDelta = Aero_FrameSpikeMetrics.positiveDelta(gcCount, lastGcCount);
        long gcTimeDelta = Aero_FrameSpikeMetrics.positiveDelta(gcTimeMs, lastGcTimeMs);
        lastFrameCpuNs = measuredDelta(threadCpuNs, lastThreadCpuNs);
        lastFrameAllocBytes = measuredDelta(threadAllocBytes, lastThreadAllocBytes);
        completedFrameAllocBytes = lastFrameAllocBytes;
        recordPressure(frameMs, gcTimeDelta);
        logFrame(now, frameMs, gcCountDelta, gcTimeDelta);
    }

private static long measuredDelta(long current, long previous) {
        return current >= 0L && previous >= 0L
            ? Aero_FrameSpikeMetrics.positiveDelta(current, previous) : -1L;
    }

private static void recordPressure(double frameMs, long gcTimeDelta) {
        double displayMs = lastDisplayUpdateNs / 1000000.0d;
        double chunksMs = lastRenderChunksNs / 1000000.0d;
        Aero_AnimationRenderBudget.recordFramePressure(frameMs, displayMs, chunksMs, gcTimeDelta);
        Aero_RenderLoadGovernor.recordFramePressure(frameMs, displayMs, chunksMs,
            lastRenderEntitiesNs / 1000000.0d, gcTimeDelta,
            Aero_ChunkVisibility.visibleChunkCount());
        Aero_FramePacer.recordFramePressure(frameMs, displayMs);
    }

private static void logFrame(long now, double frameMs,
                             long gcCountDelta, long gcTimeDelta) {
        if (shouldLogSpike(now, frameMs)) {
            lastLogNs = now;
            logSpike(frameMs, gcCountDelta, gcTimeDelta);
            return;
        }
        if (ENABLED && LOG_GC && gcCountDelta > 0L) {
            Aero_FrameSpikeWriter.logEvent("GC", frameMs, gcCountDelta, gcTimeDelta);
            return;
        }
        if (ENABLED && HEARTBEAT_NS > 0L && now - lastHeartbeatNs >= HEARTBEAT_NS) {
            lastHeartbeatNs = now;
            Aero_FrameSpikeWriter.logEvent("Pulse", frameMs, gcCountDelta, gcTimeDelta);
        }
    }

private static boolean shouldLogSpike(long now, double frameMs) {
        if (!ENABLED || frameMs < THRESHOLD_MS) return false;
        return MIN_INTERVAL_NS == 0L || now - lastLogNs >= MIN_INTERVAL_NS;
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
        clientTickTotalNs = 0L;
        clientTickMaxNs = 0L;
        clientTickCalls = 0L;
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
