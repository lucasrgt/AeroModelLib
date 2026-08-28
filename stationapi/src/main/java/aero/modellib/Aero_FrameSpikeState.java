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
class Aero_FrameSpikeState {

    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.spikelog"));
    static final boolean TIMING_ENABLED =
        ENABLED
        || Aero_AnimationRenderBudget.framePressureThrottleEnabled()
        || Aero_RenderLoadGovernor.enabled();

    static final double THRESHOLD_MS =
        Aero_FrameSpikeFormat.doubleProperty("aero.spikelog.ms", 25.0d, 1.0d, 10000.0d);
    static final long MIN_INTERVAL_NS = (long)
        (Aero_FrameSpikeFormat.doubleProperty("aero.spikelog.intervalMs", 0.0d, 0.0d, 60000.0d) * 1000000.0d);
    static final long HEARTBEAT_NS = (long)
        (Aero_FrameSpikeFormat.doubleProperty("aero.spikelog.heartbeatMs", 5000.0d, 0.0d, 600000.0d) * 1000000.0d);
    static final double FLUSH_THRESHOLD_MS =
        Aero_FrameSpikeFormat.doubleProperty("aero.spikelog.flushMs", 4.0d, 0.0d, 10000.0d);
    static final boolean LOG_GC =
        !"false".equalsIgnoreCase(System.getProperty("aero.spikelog.gc"));
    static final boolean SYNC_WRITES =
        "true".equalsIgnoreCase(System.getProperty("aero.spikelog.sync"));
    static final String LOG_FILE =
        System.getProperty("aero.spikelog.file", "aero-frame-spikes.log");

    static long lastFrameStartNs;
    static long lastLogNs;
    static long lastHeartbeatNs;
    static long lastGcCount = -1L;
    static long lastGcTimeMs = -1L;
    static long lastThreadCpuNs = -1L;
    static long lastThreadAllocBytes = -1L;
    static long lastFrameCpuNs;
    static long lastFrameAllocBytes;
    static long gameRendererUpdateStartNs;
    static long gameRendererUpdateStartAllocBytes;
    static long lastGameRendererUpdateNs;
    static long lastGameRendererUpdateAllocBytes;
    static long renderWorldStartNs;
    static long renderWorldStartAllocBytes;
    static long lastRenderWorldNs;
    static long lastRenderWorldAllocBytes;
    static long aeroRenderPrepStartNs;
    static long lastAeroRenderPrepNs;
    static long renderEntitiesStartNs;
    static long renderEntitiesStartAllocBytes;
    static long lastRenderEntitiesNs;
    static long lastRenderEntitiesAllocBytes;
    static long clientTickStartNs;
    static long clientTickStartAllocBytes;
    static long lastClientTickNs;
    static long lastClientTickAllocBytes;
    static long displayUpdateStartNs;
    static long displayUpdateStartAllocBytes;
    static long lastDisplayUpdateNs;
    static long lastDisplayUpdateAllocBytes;
    static long profilerChartStartNs;
    static long lastProfilerChartNs;
    static long worldSaveStartNs;
    static long worldSaveStartAllocBytes;
    static long lastWorldSaveNs;
    static long lastWorldSaveAllocBytes;
    static long worldSaveSkipped;
    static long chunkCompileStartNs;
    static long chunkCompileStartAllocBytes;
    static long lastChunkCompileNs;
    static long lastChunkCompileAllocBytes;
    static long lastChunkCompileMaxNs;
    static long chunkCompileCalls;
    static long chunkCompileSkipped;
    static long slowChunkCompiles;
    static long renderChunksStartNs;
    static long renderChunksStartAllocBytes;
    static long lastRenderChunksNs;
    static long lastRenderChunksAllocBytes;
    static long lastRenderChunksMaxNs;
    static long renderChunksCalls;
    static long worldFlushStartAllocBytes;
    static long lastWorldFlushNs;
    static long lastWorldFlushAllocBytes;
    static long slowWorldFlushes;
    static final List GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    static final ThreadMXBean THREAD_BEAN =
        ManagementFactory.getThreadMXBean();
    static final boolean THREAD_CPU_SUPPORTED =
        THREAD_BEAN.isCurrentThreadCpuTimeSupported();
    static final com.sun.management.ThreadMXBean ALLOC_BEAN =
        ManagementFactory.getPlatformMXBean(com.sun.management.ThreadMXBean.class);
    static final boolean THREAD_ALLOC_SUPPORTED =
        ALLOC_BEAN != null && ALLOC_BEAN.isThreadAllocatedMemorySupported();
    static PrintWriter fileLog;
    static boolean fileLogFailed;

    static {
        if (THREAD_ALLOC_SUPPORTED && !ALLOC_BEAN.isThreadAllocatedMemoryEnabled()) {
            try {
                ALLOC_BEAN.setThreadAllocatedMemoryEnabled(true);
            } catch (UnsupportedOperationException ignored) {
                // Allocation counters are diagnostic-only; timing still works.
            }
        }
    }

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

    

}
