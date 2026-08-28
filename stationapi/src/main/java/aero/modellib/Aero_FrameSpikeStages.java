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
final class Aero_FrameSpikeStages extends Aero_FrameSpikeState {
    private Aero_FrameSpikeStages() {}

static void endGameRendererUpdate() {
        if (!TIMING_ENABLED || gameRendererUpdateStartNs == 0L) return;
        lastGameRendererUpdateNs = System.nanoTime() - gameRendererUpdateStartNs;
        lastGameRendererUpdateAllocBytes =
            Aero_FrameSpikeMetrics.allocDeltaSince(gameRendererUpdateStartAllocBytes);
        gameRendererUpdateStartNs = 0L;
        gameRendererUpdateStartAllocBytes = -1L;
    }

static void beginRenderWorld() {
        if (!TIMING_ENABLED) return;
        renderWorldStartNs = System.nanoTime();
        renderWorldStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
    }

static void endRenderWorld() {
        if (!TIMING_ENABLED || renderWorldStartNs == 0L) return;
        lastRenderWorldNs = System.nanoTime() - renderWorldStartNs;
        lastRenderWorldAllocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(renderWorldStartAllocBytes);
        renderWorldStartNs = 0L;
        renderWorldStartAllocBytes = -1L;
    }

static long beginAeroRenderPrep() {
        return TIMING_ENABLED ? System.nanoTime() : 0L;
    }

static void endAeroRenderPrep(long startNs) {
        if (!TIMING_ENABLED || startNs == 0L) return;
        lastAeroRenderPrepNs = System.nanoTime() - startNs;
    }

static void beginRenderEntities() {
        if (!TIMING_ENABLED) return;
        renderEntitiesStartNs = System.nanoTime();
        renderEntitiesStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
    }

static void endRenderEntitiesBeforeAeroFlush() {
        if (!TIMING_ENABLED || renderEntitiesStartNs == 0L) return;
        lastRenderEntitiesNs = System.nanoTime() - renderEntitiesStartNs;
        lastRenderEntitiesAllocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(renderEntitiesStartAllocBytes);
        renderEntitiesStartNs = 0L;
        renderEntitiesStartAllocBytes = -1L;
    }

static void beginClientTick() {
        if (!TIMING_ENABLED) return;
        clientTickStartNs = System.nanoTime();
        clientTickStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
    }

static void endClientTick() {
        if (!TIMING_ENABLED || clientTickStartNs == 0L) return;
        lastClientTickNs = System.nanoTime() - clientTickStartNs;
        lastClientTickAllocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(clientTickStartAllocBytes);
        clientTickStartNs = 0L;
        clientTickStartAllocBytes = -1L;
    }

static void beginDisplayUpdate() {
        if (!TIMING_ENABLED) return;
        displayUpdateStartNs = System.nanoTime();
        displayUpdateStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
    }

static void endDisplayUpdate() {
        if (!TIMING_ENABLED || displayUpdateStartNs == 0L) return;
        lastDisplayUpdateNs = System.nanoTime() - displayUpdateStartNs;
        lastDisplayUpdateAllocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(displayUpdateStartAllocBytes);
        displayUpdateStartNs = 0L;
        displayUpdateStartAllocBytes = -1L;
    }

static void beginProfilerChart() {
        if (!TIMING_ENABLED) return;
        profilerChartStartNs = System.nanoTime();
    }

static void endProfilerChart() {
        if (!TIMING_ENABLED || profilerChartStartNs == 0L) return;
        lastProfilerChartNs = System.nanoTime() - profilerChartStartNs;
        profilerChartStartNs = 0L;
    }

static void beginWorldSave() {
        if (!TIMING_ENABLED) return;
        worldSaveStartNs = System.nanoTime();
        worldSaveStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
    }

static void endWorldSave() {
        if (!TIMING_ENABLED || worldSaveStartNs == 0L) return;
        lastWorldSaveNs = System.nanoTime() - worldSaveStartNs;
        lastWorldSaveAllocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(worldSaveStartAllocBytes);
        worldSaveStartNs = 0L;
        worldSaveStartAllocBytes = -1L;
    }

static void skipWorldSave() {
        if (!TIMING_ENABLED) return;
        worldSaveSkipped++;
    }

static void beginChunkCompile() {
        if (!TIMING_ENABLED) return;
        chunkCompileStartNs = System.nanoTime();
        chunkCompileStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
    }

static void skipChunkCompile() {
        if (!TIMING_ENABLED) return;
        chunkCompileSkipped++;
    }

static void endChunkCompile() {
        if (!TIMING_ENABLED || chunkCompileStartNs == 0L) return;
        long elapsedNs = System.nanoTime() - chunkCompileStartNs;
        lastChunkCompileNs += elapsedNs;
        if (elapsedNs > lastChunkCompileMaxNs) {
            lastChunkCompileMaxNs = elapsedNs;
        }
        chunkCompileCalls++;
        long allocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(chunkCompileStartAllocBytes);
        if (allocBytes > 0L) lastChunkCompileAllocBytes += allocBytes;
        chunkCompileStartNs = 0L;
        chunkCompileStartAllocBytes = -1L;
        if (elapsedNs >= (long) (FLUSH_THRESHOLD_MS * 1000000.0d)) {
            slowChunkCompiles++;
        }
    }

static void beginRenderChunks() {
        if (!TIMING_ENABLED) return;
        renderChunksStartNs = System.nanoTime();
        renderChunksStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
    }

static void endRenderChunks() {
        if (!TIMING_ENABLED || renderChunksStartNs == 0L) return;
        long elapsedNs = System.nanoTime() - renderChunksStartNs;
        lastRenderChunksNs += elapsedNs;
        if (elapsedNs > lastRenderChunksMaxNs) {
            lastRenderChunksMaxNs = elapsedNs;
        }
        renderChunksCalls++;
        long allocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(renderChunksStartAllocBytes);
        if (allocBytes > 0L) lastRenderChunksAllocBytes += allocBytes;
        renderChunksStartNs = 0L;
        renderChunksStartAllocBytes = -1L;
    }

static long beginWorldFlush() {
        if (TIMING_ENABLED) {
            worldFlushStartAllocBytes = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
        }
        return TIMING_ENABLED ? System.nanoTime() : 0L;
    }

static void endWorldFlush(long startNs) {
        if (!TIMING_ENABLED || startNs == 0L) return;
        long elapsedNs = System.nanoTime() - startNs;
        lastWorldFlushNs = elapsedNs;
        lastWorldFlushAllocBytes = Aero_FrameSpikeMetrics.allocDeltaSince(worldFlushStartAllocBytes);
        worldFlushStartAllocBytes = -1L;
        double elapsedMs = elapsedNs / 1000000.0d;
        if (ENABLED && elapsedMs >= FLUSH_THRESHOLD_MS) {
            slowWorldFlushes++;
            Aero_FrameSpikeWriter.logEvent("WorldFlush", elapsedMs, 0L, 0L);
        }
    }

static long beginCellRebuild() {
        return TIMING_ENABLED ? System.nanoTime() : 0L;
    }

static void endCellRebuild(long startNs) {
        if (!TIMING_ENABLED || startNs == 0L) return;
        lastCellRebuildNs += System.nanoTime() - startNs;
    }
}
