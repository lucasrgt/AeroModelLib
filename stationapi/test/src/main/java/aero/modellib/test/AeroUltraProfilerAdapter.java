package aero.modellib.test;

import aero.modellib.Aero_FrameSpikeLogger;

/** Acquires ULTRA runtime samples for the neutral Worldline Profiler contract. */
final class AeroUltraProfilerAdapter {
    private static long totalFrameCpuNs, worstFrameCpuNs;
    private static long totalTickNs, totalTickCalls, maxTickNs, maxTicksPerFrame;
    private static long totalGameUpdateNs, totalRenderWorldNs;
    private static long totalUnattributedWallNs, worstUnattributedWallNs;
    private static long totalUnattributedCpuNs, worstUnattributedCpuNs;
    private static long decorationNs, decorationChunks, decorationMachines;
    private static long maxDecorationNs, maxDecorationChunks;
    private static long worstTickNs, worstTickCalls, worstTickMaxNs;
    private static long worstGameUpdateNs, worstRenderWorldNs;
    private static long worstDecorationNs, worstDecorationChunks;
    private static long worstFrameNs;
    private static long boundaryCpuNs = -1L, frameCpuNs;

    private AeroUltraProfilerAdapter() {}

    static void captureFrameBoundary() {
        AeroUltraStressScene.captureFrameDecoration();
        long nowCpuNs = Aero_FrameSpikeLogger.currentThreadCpuNanos();
        frameCpuNs = nowCpuNs >= 0L && boundaryCpuNs >= 0L
            ? positive(nowCpuNs - boundaryCpuNs) : 0L;
        boundaryCpuNs = nowCpuNs;
    }

    static void record(long frameNs) {
        long cpuNs = frameCpuNs;
        long tickNs = positive(Aero_FrameSpikeLogger.clientTickNanos());
        long tickCalls = positive(Aero_FrameSpikeLogger.clientTickCalls());
        long tickMaxNs = positive(Aero_FrameSpikeLogger.clientTickMaxNanos());
        long gameNs = positive(Aero_FrameSpikeLogger.gameRendererUpdateNanos());
        long renderWorldNs = positive(Aero_FrameSpikeLogger.renderWorldNanos());
        long displayNs = positive(Aero_FrameSpikeLogger.displayUpdateNanos());
        long topLevelNs = tickNs + gameNs + displayNs;
        long unattributedWallNs = positive(frameNs - topLevelNs);
        long unattributedCpuNs = positive(cpuNs - topLevelNs);
        long decorateNs = AeroUltraStressScene.frameDecorationNanos();
        long decorateChunks = AeroUltraStressScene.frameDecorationChunks();

        totalFrameCpuNs += cpuNs;
        worstFrameCpuNs = Math.max(worstFrameCpuNs, cpuNs);
        totalTickNs += tickNs;
        totalTickCalls += tickCalls;
        maxTickNs = Math.max(maxTickNs, tickMaxNs);
        maxTicksPerFrame = Math.max(maxTicksPerFrame, tickCalls);
        totalGameUpdateNs += gameNs;
        totalRenderWorldNs += renderWorldNs;
        totalUnattributedWallNs += unattributedWallNs;
        worstUnattributedWallNs = Math.max(worstUnattributedWallNs, unattributedWallNs);
        totalUnattributedCpuNs += unattributedCpuNs;
        worstUnattributedCpuNs = Math.max(worstUnattributedCpuNs, unattributedCpuNs);
        decorationNs += decorateNs;
        decorationChunks += decorateChunks;
        decorationMachines += AeroUltraStressScene.frameDecorationMachines();
        maxDecorationNs = Math.max(maxDecorationNs, decorateNs);
        maxDecorationChunks = Math.max(maxDecorationChunks, decorateChunks);

        if (frameNs > worstFrameNs) {
            worstFrameNs = frameNs;
            worstTickNs = tickNs;
            worstTickCalls = tickCalls;
            worstTickMaxNs = tickMaxNs;
            worstGameUpdateNs = gameNs;
            worstRenderWorldNs = renderWorldNs;
            worstDecorationNs = decorateNs;
            worstDecorationChunks = decorateChunks;
        }
    }

    static void appendJson(StringBuilder out) {
        text(out, "profiler", "worldline");
        text(out, "worldMode", AeroUltraStressConfig.WORLD_MODE);
        pair(out, "steadyWorldQualified",
            AeroUltraStressConfig.steadyWorld() && decorationChunks == 0L ? 1L : 0L);
        pair(out, "frameCpuTotalNanos", totalFrameCpuNs);
        pair(out, "worstFrameCpuNanos", worstFrameCpuNs);
        pair(out, "clientTickTotalNanos", totalTickNs);
        pair(out, "clientTickCalls", totalTickCalls);
        pair(out, "maxClientTickNanos", maxTickNs);
        pair(out, "maxClientTicksPerFrame", maxTicksPerFrame);
        pair(out, "gameUpdateTotalNanos", totalGameUpdateNs);
        pair(out, "renderWorldTotalNanos", totalRenderWorldNs);
        pair(out, "unattributedWallTotalNanos", totalUnattributedWallNs);
        pair(out, "worstUnattributedWallNanos", worstUnattributedWallNs);
        pair(out, "unattributedCpuTotalNanos", totalUnattributedCpuNs);
        pair(out, "worstUnattributedCpuNanos", worstUnattributedCpuNs);
        pair(out, "measurementDecorationNanos", decorationNs);
        pair(out, "measurementDecoratedChunks", decorationChunks);
        pair(out, "measurementDecoratedMachines", decorationMachines);
        pair(out, "maxFrameDecorationNanos", maxDecorationNs);
        pair(out, "maxFrameDecoratedChunks", maxDecorationChunks);
        pair(out, "worstFrameClientTickNanos", worstTickNs);
        pair(out, "worstFrameClientTickCalls", worstTickCalls);
        pair(out, "worstFrameClientTickMaxNanos", worstTickMaxNs);
        pair(out, "worstFrameGameUpdateNanos", worstGameUpdateNs);
        pair(out, "worstFrameRenderWorldNanos", worstRenderWorldNs);
        pair(out, "worstFrameDecorationNanos", worstDecorationNs);
        pair(out, "worstFrameDecoratedChunks", worstDecorationChunks);
    }

    private static void pair(StringBuilder out, String name, long value) {
        out.append("  \"").append(name).append("\": ").append(value).append(',').append('\n');
    }

    private static void text(StringBuilder out, String name, String value) {
        out.append("  \"").append(name).append("\": \"").append(value)
            .append("\",").append('\n');
    }

    private static long positive(long value) { return Math.max(0L, value); }
}
