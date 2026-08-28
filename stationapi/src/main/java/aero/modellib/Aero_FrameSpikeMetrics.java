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
final class Aero_FrameSpikeMetrics extends Aero_FrameSpikeState {
    private Aero_FrameSpikeMetrics() {}

static long gcCollectionCount() {
        long total = 0L;
        for (int i = 0; i < GC_BEANS.size(); i++) {
            long value = ((GarbageCollectorMXBean) GC_BEANS.get(i)).getCollectionCount();
            if (value > 0L) total += value;
        }
        return total;
    }

static long currentThreadCpuTimeNs() {
        if (!THREAD_CPU_SUPPORTED) return -1L;
        try {
            return THREAD_BEAN.getCurrentThreadCpuTime();
        } catch (UnsupportedOperationException e) {
            return -1L;
        }
    }

static long currentThreadAllocatedBytes() {
        if (!THREAD_ALLOC_SUPPORTED || !ALLOC_BEAN.isThreadAllocatedMemoryEnabled()) return -1L;
        try {
            return ALLOC_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (UnsupportedOperationException e) {
            return -1L;
        }
    }

static long allocDeltaSince(long startBytes) {
        long now = Aero_FrameSpikeMetrics.currentThreadAllocatedBytes();
        if (now < 0L || startBytes < 0L) return -1L;
        return Aero_FrameSpikeMetrics.positiveDelta(now, startBytes);
    }

static long gcCollectionTimeMs() {
        long total = 0L;
        for (int i = 0; i < GC_BEANS.size(); i++) {
            long value = ((GarbageCollectorMXBean) GC_BEANS.get(i)).getCollectionTime();
            if (value > 0L) total += value;
        }
        return total;
    }

static long positiveDelta(long current, long previous) {
        return current >= previous ? current - previous : 0L;
    }
}
