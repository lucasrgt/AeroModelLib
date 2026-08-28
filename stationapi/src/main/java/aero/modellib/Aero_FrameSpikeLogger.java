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
public final class Aero_FrameSpikeLogger extends Aero_FrameSpikeState {
    public static final boolean ENABLED = Aero_FrameSpikeState.ENABLED;
    private Aero_FrameSpikeLogger() {}

    public static void beginFrame() { Aero_FrameSpikeFrame.beginFrame(); }
    public static void endGameRendererUpdate() { Aero_FrameSpikeStages.endGameRendererUpdate(); }
    public static void beginRenderWorld() { Aero_FrameSpikeStages.beginRenderWorld(); }
    public static void endRenderWorld() { Aero_FrameSpikeStages.endRenderWorld(); }
    public static long beginAeroRenderPrep() { return Aero_FrameSpikeStages.beginAeroRenderPrep(); }
    public static void endAeroRenderPrep(long startNs) { Aero_FrameSpikeStages.endAeroRenderPrep(startNs); }
    public static void beginRenderEntities() { Aero_FrameSpikeStages.beginRenderEntities(); }
    public static void endRenderEntitiesBeforeAeroFlush() { Aero_FrameSpikeStages.endRenderEntitiesBeforeAeroFlush(); }
    public static void beginClientTick() { Aero_FrameSpikeStages.beginClientTick(); }
    public static void endClientTick() { Aero_FrameSpikeStages.endClientTick(); }
    public static void beginDisplayUpdate() { Aero_FrameSpikeStages.beginDisplayUpdate(); }
    public static void endDisplayUpdate() { Aero_FrameSpikeStages.endDisplayUpdate(); }
    public static void beginProfilerChart() { Aero_FrameSpikeStages.beginProfilerChart(); }
    public static void endProfilerChart() { Aero_FrameSpikeStages.endProfilerChart(); }
    public static void beginWorldSave() { Aero_FrameSpikeStages.beginWorldSave(); }
    public static void endWorldSave() { Aero_FrameSpikeStages.endWorldSave(); }
    public static void skipWorldSave() { Aero_FrameSpikeStages.skipWorldSave(); }
    public static void beginChunkCompile() { Aero_FrameSpikeStages.beginChunkCompile(); }
    public static void skipChunkCompile() { Aero_FrameSpikeStages.skipChunkCompile(); }
    public static void endChunkCompile() { Aero_FrameSpikeStages.endChunkCompile(); }
    public static void beginRenderChunks() { Aero_FrameSpikeStages.beginRenderChunks(); }
    public static void endRenderChunks() { Aero_FrameSpikeStages.endRenderChunks(); }
    public static long beginWorldFlush() { return Aero_FrameSpikeStages.beginWorldFlush(); }
    public static void endWorldFlush(long startNs) { Aero_FrameSpikeStages.endWorldFlush(startNs); }
}
