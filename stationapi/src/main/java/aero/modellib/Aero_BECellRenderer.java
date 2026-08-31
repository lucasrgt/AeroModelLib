package aero.modellib;


import aero.modellib.optimization.OptimizationRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.entity.BlockEntity;
import org.lwjgl.opengl.GL11;

import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_CellRenderableBE;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.util.Aero_PerfConfig;
import aero.modellib.util.Aero_Profiler;

/**
 * At-rest BlockEntity cell pages. Renderers can queue static/LOD-overflow
 * meshes here instead of drawing each BE immediately; the flush compiles one
 * small display-list page per visible cell/render key and replays that page
 * while preserving the existing direct-render fallback.
 */
@OptimizationRef({"aero.render.be-cell-fragmentation", "aero.render.be-cell-pages"})
public final class Aero_BECellRenderer extends Aero_BECellRenderState {
    public static final boolean ENABLED = Aero_BECellRenderState.ENABLED;
    public static final boolean SKIP_INDIVIDUAL_RENDERERS =
        Aero_BECellRenderState.SKIP_INDIVIDUAL_RENDERERS;
    private Aero_BECellRenderer() {}

    public static void queueAtRest(Aero_MeshModel model, String texturePath, BlockEntity be, double x, double y, double z, float rotation, float brightness, Aero_RenderOptions options) { Aero_BECellQueue.queueAtRest(model, texturePath, be, x, y, z, rotation, brightness, options); }
    public static boolean tryQueueManagedAtRest(BlockEntity be, Aero_CellPageRenderableBE renderable) { return Aero_BECellQueue.tryQueueManagedAtRest(be, renderable); }
    public static void flush(double cameraX, double cameraY, double cameraZ) { Aero_BECellFlush.flush(cameraX, cameraY, cameraZ); }
    static void flushCachedCamera() { Aero_BECellFlush.flushCachedCamera(); }
    static void disposeModel(Aero_MeshModel model) { Aero_BECellCache.disposeModel(model); }
    public static int queuedThisFrame() { return Aero_BECellQueue.queuedThisFrame(); }
    public static int queuedLastFrame() { return Aero_BECellQueue.queuedLastFrame(); }
    public static int pageCallsThisFrame() { return Aero_BECellMetrics.pageCallsThisFrame(); }
    public static int pageRebuildsThisFrame() { return Aero_BECellMetrics.pageRebuildsThisFrame(); }
    public static int directFallbacksThisFrame() { return Aero_BECellMetrics.directFallbacksThisFrame(); }
    public static int cachedPageCount() { return Aero_BECellMetrics.cachedPageCount(); }
    public static int deletedPages() { return Aero_BECellMetrics.deletedPages(); }
    public static int compiledCachedPages() { return Aero_BECellMetrics.compiledCachedPages(); }
    public static int expiredCachedPages() { return Aero_BECellMetrics.expiredCachedPages(); }
    public static int evictedCachedPages() { return Aero_BECellMetrics.evictedCachedPages(); }
    public static boolean flattenedPagesEnabled() { return FLATTENED_PAGES; }
    public static int pageTtlFrames() { return PAGE_TTL_FRAMES; }
    public static int rebuildsPerFrame() { return REBUILDS_PER_FRAME; }
    public static int maxCachedPages() { return MAX_CACHED_PAGES; }
}
