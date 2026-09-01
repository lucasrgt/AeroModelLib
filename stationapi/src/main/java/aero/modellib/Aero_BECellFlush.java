package aero.modellib;

import java.util.Collections;
import org.lwjgl.opengl.GL11;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.util.Aero_Profiler;

/** Prepares Cell Page caches and replays them without changing draw order. */
final class Aero_BECellFlush extends Aero_BECellRenderState {
    private Aero_BECellFlush() {}

    static void flush(double cameraX, double cameraY, double cameraZ) {
        resetFrameCounters();
        if (ACTIVE_PAGES.isEmpty()) {
            finishEmptyFrame();
            return;
        }
        frameIndex++;
        Aero_Profiler.start("aero.becell.flush");
        try {
            Collections.sort(ACTIVE_PAGES, BY_RENDER_STATE);
            for (int i = 0; i < ACTIVE_PAGES.size(); i++) {
                prepareOrDraw(ACTIVE_PAGES.get(i), cameraX, cameraY, cameraZ);
            }
            if (!FLATTENED_PAGES) Aero_BECellReplay.draw(cameraX, cameraY, cameraZ);
        } finally {
            Aero_Profiler.end("aero.becell.flush");
        }
        finishFrame();
    }

    static void flushCachedCamera() {
        if (!Aero_RenderDistance.hasCachedCamera()) return;
        Aero_BECellRenderer.flush(Aero_RenderDistance.cachedCameraX(),
            Aero_RenderDistance.cachedCameraY(), Aero_RenderDistance.cachedCameraZ());
    }

    private static void prepareOrDraw(Aero_BECellQueuedPage page,
            double cameraX, double cameraY, double cameraZ) {
        if (page.count < MIN_INSTANCES) {
            fallback(page, cameraX, cameraY, cameraZ);
            return;
        }
        int[] modelIds = FLATTENED_PAGES ? null
            : Aero_MeshRenderer.ensureAtRestListIds(page.key.model);
        if (!FLATTENED_PAGES && modelIds == null) {
            fallback(page, cameraX, cameraY, cameraZ);
            return;
        }
        Aero_BECellCachedPage cached = resolveCached(page, modelIds);
        if (cached == null) {
            fallback(page, cameraX, cameraY, cameraZ);
            return;
        }
        if (FLATTENED_PAGES) drawFlattened(page.key, cached, cameraX, cameraY, cameraZ);
        else page.prepareReplay(cached, modelIds);
    }

    private static Aero_BECellCachedPage resolveCached(Aero_BECellQueuedPage page,
            int[] modelIds) {
        Aero_BECellCachedPage cached = CACHE.get(page.key);
        int membershipHash = page.membershipHash();
        if (cached != null && cached.count == page.count
                && cached.membershipHash == membershipHash) return cached;
        if (!Aero_BECellCompile.canRebuildAnotherPageThisFrame()) return null;
        Aero_BECellCachedPage rebuilt = Aero_BECellCompile.compilePage(page, modelIds, membershipHash);
        if (rebuilt == null) return null;
        if (cached != null) Aero_BECellCache.deleteIds(cached.ids);
        CACHE.put(page.key, rebuilt);
        Aero_BECellCache.enforceMaxCachedPages(page.key);
        pageRebuildsThisFrame++;
        compiledCachedPages++;
        return rebuilt;
    }

    private static void fallback(Aero_BECellQueuedPage page,
            double cameraX, double cameraY, double cameraZ) {
        if (FLATTENED_PAGES) drawDirect(page, cameraX, cameraY, cameraZ);
        else page.prepareDirectReplay();
    }

    private static void drawFlattened(Aero_BECellPageKey key, Aero_BECellCachedPage cached,
            double cameraX, double cameraY, double cameraZ) {
        Aero_Profiler.start("aero.becell.call");
        Aero_AnimatedBatcher.bindTexturePath(key.texturePath);
        Aero_MeshRenderer.beginMeshState(key.options);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(key.originX() - cameraX,
                key.originY() - cameraY, key.originZ() - cameraZ);
            for (int group = 0; group < cached.ids.length; group++) {
                drawFlattenedGroup(key, cached.ids[group], group);
            }
        } finally {
            GL11.glPopMatrix();
            Aero_MeshRenderer.endMeshState();
            Aero_Profiler.end("aero.becell.call");
        }
        cached.lastUsedFrame = frameIndex;
    }

    private static void drawFlattenedGroup(Aero_BECellPageKey key, int id, int group) {
        if (id == 0) return;
        if (!PER_INSTANCE_LIGHT) {
            float bright = key.brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[group];
            GL11.glColor4f(bright * key.options.tintR, bright * key.options.tintG,
                bright * key.options.tintB, key.options.alpha);
        }
        GL11.glCallList(id);
        pageCallsThisFrame++;
    }

    static void drawDirectInstance(Aero_BECellQueuedPage page,
            double worldX, double worldY, double worldZ, float brightness,
            double cameraX, double cameraY, double cameraZ) {
        Aero_AnimatedBatcher.bindTexturePath(page.key.texturePath);
        Aero_MeshRenderer.renderModelAtRestPreculled(page.key.model,
            worldX - cameraX, worldY - cameraY, worldZ - cameraZ,
            page.key.rotation, brightness, page.key.options);
        directFallbacksThisFrame++;
    }

    static void drawDirect(Aero_BECellQueuedPage page,
            double cameraX, double cameraY, double cameraZ) {
        Aero_Profiler.start("aero.becell.direct");
        try {
            for (int i = 0; i < page.count; i++) {
                drawDirectInstance(page, page.worldXs[i], page.worldYs[i], page.worldZs[i],
                    page.brightnesses[i], cameraX, cameraY, cameraZ);
            }
        } finally {
            Aero_Profiler.end("aero.becell.direct");
        }
    }

    static void drawDirect(Aero_MeshModel model, String texturePath,
            double x, double y, double z, float rotation, float brightness,
            Aero_RenderOptions options) {
        if (model == null) return;
        Aero_Profiler.start("aero.becell.direct");
        try {
            Aero_AnimatedBatcher.bindTexturePath(texturePath);
            Aero_MeshRenderer.renderModelAtRestPreculled(
                model, x, y, z, rotation, brightness, options);
            directFallbacksThisFrame++;
        } finally {
            Aero_Profiler.end("aero.becell.direct");
        }
    }

    private static void resetFrameCounters() {
        pageCallsThisFrame = pageRebuildsThisFrame = directFallbacksThisFrame = 0;
    }

    private static void finishEmptyFrame() {
        Aero_BECellReplay.clear();
        queuedLastFrame = queuedThisFrame;
        queuedThisFrame = 0;
    }

    private static void finishFrame() {
        ACTIVE.clear();
        for (int i = 0; i < ACTIVE_PAGES.size(); i++) ACTIVE_PAGES.get(i).clear();
        ACTIVE_PAGES.clear();
        Aero_BECellReplay.clear();
        queuedLastFrame = queuedThisFrame;
        queuedThisFrame = 0;
        if ((frameIndex & 127) == 0) Aero_BECellCache.sweepOldPages();
    }
}
