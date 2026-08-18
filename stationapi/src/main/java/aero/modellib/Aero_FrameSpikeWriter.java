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
final class Aero_FrameSpikeWriter extends Aero_FrameSpikeState {
    private Aero_FrameSpikeWriter() {}

static void logEvent(String kind, double frameMs, long gcCountDelta, long gcTimeDelta) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long totalMb = rt.totalMemory() >> 20;
        long maxMb = rt.maxMemory() >> 20;
        String line = "[Aero_" + kind + "] frameMs=" + Aero_FrameSpikeFormat.round1(frameMs)
            + " frameCpuMs=" + Aero_FrameSpikeFormat.round1(lastFrameCpuNs / 1000000.0d)
            + " frameAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastFrameAllocBytes))
            + " gameUpdateMs=" + Aero_FrameSpikeFormat.round1(lastGameRendererUpdateNs / 1000000.0d)
            + " gameAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastGameRendererUpdateAllocBytes))
            + " renderWorldMs=" + Aero_FrameSpikeFormat.round1(lastRenderWorldNs / 1000000.0d)
            + " renderWorldAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastRenderWorldAllocBytes))
            + " aeroPrepMs=" + Aero_FrameSpikeFormat.round1(lastAeroRenderPrepNs / 1000000.0d)
            + " renderEntitiesMs=" + Aero_FrameSpikeFormat.round1(lastRenderEntitiesNs / 1000000.0d)
            + " renderEntitiesAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastRenderEntitiesAllocBytes))
            + " clientTickMs=" + Aero_FrameSpikeFormat.round1(lastClientTickNs / 1000000.0d)
            + " clientTickAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastClientTickAllocBytes))
            + " displayUpdateMs=" + Aero_FrameSpikeFormat.round1(lastDisplayUpdateNs / 1000000.0d)
            + " displayAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastDisplayUpdateAllocBytes))
            + " profilerChartMs=" + Aero_FrameSpikeFormat.round1(lastProfilerChartNs / 1000000.0d)
            + " worldSaveMs=" + Aero_FrameSpikeFormat.round1(lastWorldSaveNs / 1000000.0d)
            + " worldSaveAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastWorldSaveAllocBytes))
            + " worldSaveSkipped=" + worldSaveSkipped
            + " compileChunksMs=" + Aero_FrameSpikeFormat.round1(lastChunkCompileNs / 1000000.0d)
            + " compileChunksAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastChunkCompileAllocBytes))
            + " compileChunksMaxMs=" + Aero_FrameSpikeFormat.round1(lastChunkCompileMaxNs / 1000000.0d)
            + " compileChunksCalls=" + chunkCompileCalls
            + " compileChunksSkipped=" + chunkCompileSkipped
            + " compileBudgetSkipped=" + Aero_ChunkCompileBudget.skippedLastFrame()
            + " slowChunkCompiles=" + slowChunkCompiles
            + " renderChunksMs=" + Aero_FrameSpikeFormat.round1(lastRenderChunksNs / 1000000.0d)
            + " renderChunksAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastRenderChunksAllocBytes))
            + " renderChunksMaxMs=" + Aero_FrameSpikeFormat.round1(lastRenderChunksMaxNs / 1000000.0d)
            + " renderChunksCalls=" + renderChunksCalls
            + " heap=" + usedMb + "/" + totalMb + "/" + maxMb + "MB"
            + " gcCountDelta=" + gcCountDelta
            + " gcTimeDeltaMs=" + gcTimeDelta
            + " worldFlushMs=" + Aero_FrameSpikeFormat.round1(lastWorldFlushNs / 1000000.0d)
            + " worldFlushAllocMB=" + Aero_FrameSpikeFormat.round2(Aero_FrameSpikeFormat.bytesToMb(lastWorldFlushAllocBytes))
            + " slowWorldFlushes=" + slowWorldFlushes
            + " animLimit=" + Aero_AnimationRenderBudget.effectiveMaxAnimatedThisFrame()
            + " animPressureLimit=" + Aero_AnimationRenderBudget.framePressureLimitThisFrame()
            + " animPressureDrops=" + Aero_AnimationRenderBudget.framePressureDrops()
            + " animThroughputBad=" + Aero_AnimationRenderBudget.throughputBadFrames()
            + " animTickSeen=" + Aero_AnimationTickBudget.seenLastTick()
            + " animTicked=" + Aero_AnimationTickBudget.tickedLastTick()
            + " animTickStride=" + Aero_AnimationTickBudget.denseStrideThisTick()
            + " renderScale=" + Aero_FrameSpikeFormat.round1(Aero_RenderLoadGovernor.distanceScale() * 100.0d)
            + " renderDrops=" + Aero_RenderLoadGovernor.drops()
            + " renderThroughputBad=" + Aero_RenderLoadGovernor.throughputBadFrames()
            + " paceFps=" + Aero_FramePacer.targetFps()
            + " paceSleepMs=" + Aero_FrameSpikeFormat.round1(Aero_FramePacer.sleptLastFrameMs())
            + " animAccepted=" + Aero_AnimationRenderBudget.acceptedThisFrame()
            + " animRejected=" + Aero_AnimationRenderBudget.rejectedThisFrame()
            + " animPriorityRejected=" + Aero_AnimationRenderBudget.priorityRejectedThisFrame()
            + " animHysteresis=" + Aero_AnimationRenderBudget.hysteresisAcceptedThisFrame()
            + " batchQueued=" + Aero_AnimatedBatcher.queuedThisFrame()
            + " batchFlushed=" + Aero_AnimatedBatcher.flushedInstancesThisFrame()
            + " batchBatches=" + Aero_AnimatedBatcher.flushedBatchesThisFrame()
            + " batchBonePageDrain=" + Aero_AnimatedBatcher.bonePageDrainedInstancesThisFrame()
            + " batchImmediate=" + Aero_AnimatedBatcher.immediateRendersThisFrame()
            + " beViewCulled=" + Aero_FrustumCull.beViewCulledThisFrame()
            + " beViewFastHold=" + Aero_FrustumCull.beViewFastTurnHoldFrames()
            + " beViewHistory=" + Aero_FrustumCull.beViewHistoryAcceptedThisFrame()
            + " atRestRenders=" + Aero_MeshRenderer.atRestRendersThisFrame()
            + " atRestListCalls=" + Aero_MeshRenderer.atRestListCallsThisFrame()
            + " atRestFallbacks=" + Aero_MeshRenderer.atRestTessFallbacksThisFrame()
            + " cellQueued=" + Aero_BECellRenderer.queuedLastFrame()
            + " cellCalls=" + Aero_BECellRenderer.pageCallsThisFrame()
            + " cellRebuilds=" + Aero_BECellRenderer.pageRebuildsThisFrame()
            + " cellDirect=" + Aero_BECellRenderer.directFallbacksThisFrame()
            + " cellCached=" + Aero_BECellRenderer.cachedPageCount()
            + " dlLive=" + Aero_DisplayListBudget.liveLists()
            + " dlPeak=" + Aero_DisplayListBudget.peakLiveLists()
            + " dlDenied=" + Aero_DisplayListBudget.deniedAllocations()
            + " dlFailed=" + Aero_DisplayListBudget.failedAllocations()
            + " prewarmDrained=" + Aero_Prewarm.drainedThisFrame()
            + " prewarmQueued=" + Aero_Prewarm.queuedModelCount()
            + " visibleChunks=" + Aero_ChunkVisibility.visibleChunkCount()
            + " recentChunks=" + Aero_ChunkVisibility.recentChunkCount();
        System.out.println(line);
        Aero_FrameSpikeWriter.writeFile(line);
    }

static void writeFile(String line) {
        if (LOG_FILE == null || LOG_FILE.length() == 0 || fileLogFailed) return;
        PrintWriter out = fileLog;
        if (out == null) {
            try {
                out = new PrintWriter(new FileWriter(LOG_FILE, true));
                fileLog = out;
                out.println("# Aero frame spike log");
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    public void run() {
                        PrintWriter log = fileLog;
                        if (log != null) {
                            log.flush();
                            log.close();
                        }
                    }
                }, "AeroFrameSpikeLogFlush"));
            } catch (IOException e) {
                fileLogFailed = true;
                return;
            }
        }
        out.println(line);
        if (SYNC_WRITES) out.flush();
    }
}
