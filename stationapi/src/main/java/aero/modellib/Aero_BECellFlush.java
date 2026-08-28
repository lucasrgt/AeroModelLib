package aero.modellib;

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
final class Aero_BECellFlush extends Aero_BECellRenderState {
    private Aero_BECellFlush() {}

static void flush(double cameraX, double cameraY, double cameraZ) {
        pageCallsThisFrame = 0;
        pageRebuildsThisFrame = 0;
        directFallbacksThisFrame = 0;
        if (ACTIVE_PAGES.isEmpty()) {
            queuedLastFrame = queuedThisFrame;
            queuedThisFrame = 0;
            return;
        }
        frameIndex++;
        Aero_Profiler.start("aero.becell.flush");
        try {
            Collections.sort(ACTIVE_PAGES, BY_RENDER_STATE);
            for (int i = 0; i < ACTIVE_PAGES.size(); i++) {
                Aero_BECellFlush.flushPage(ACTIVE_PAGES.get(i), cameraX, cameraY, cameraZ);
            }
        } finally {
            Aero_Profiler.end("aero.becell.flush");
        }
        ACTIVE.clear();
        for (int i = 0; i < ACTIVE_PAGES.size(); i++) {
            ACTIVE_PAGES.get(i).clear();
        }
        ACTIVE_PAGES.clear();
        queuedLastFrame = queuedThisFrame;
        queuedThisFrame = 0;
        if ((frameIndex & 127) == 0) {
            Aero_BECellCache.sweepOldPages();
        }
    }

static void flushCachedCamera() {
        if (!Aero_RenderDistance.hasCachedCamera()) return;
        Aero_BECellRenderer.flush(Aero_RenderDistance.cachedCameraX(),
              Aero_RenderDistance.cachedCameraY(),
              Aero_RenderDistance.cachedCameraZ());
    }

static void flushPage(Aero_BECellQueuedPage page, double cameraX, double cameraY, double cameraZ) {
        if (page.count < MIN_INSTANCES) {
            Aero_BECellFlush.drawDirect(page, cameraX, cameraY, cameraZ);
            return;
        }
        int[] modelIds = null;
        if (!FLATTENED_PAGES) {
            modelIds = Aero_MeshRenderer.ensureAtRestListIds(page.key.model);
            if (modelIds == null) {
                Aero_BECellFlush.drawDirect(page, cameraX, cameraY, cameraZ);
                return;
            }
        }

        Aero_BECellCachedPage cached = CACHE.get(page.key);
        int membershipHash = page.membershipHash();
        if (cached == null
            || cached.count != page.count
            || cached.membershipHash != membershipHash) {
            if (!Aero_BECellCompile.canRebuildAnotherPageThisFrame()) {
                Aero_BECellFlush.drawDirect(page, cameraX, cameraY, cameraZ);
                return;
            }
            Aero_BECellCachedPage rebuilt = Aero_BECellCompile.compilePage(page, modelIds, membershipHash);
            if (rebuilt == null) {
                Aero_BECellFlush.drawDirect(page, cameraX, cameraY, cameraZ);
                return;
            }
            if (cached != null) Aero_BECellCache.deleteIds(cached.ids);
            CACHE.put(page.key, rebuilt);
            Aero_BECellCache.enforceMaxCachedPages(page.key);
            cached = rebuilt;
            pageRebuildsThisFrame++;
            compiledCachedPages++;
        }

        Aero_BECellFlush.drawCached(page.key, cached, cameraX, cameraY, cameraZ);
    }

static void drawCached(Aero_BECellPageKey key, Aero_BECellCachedPage cached,
                                   double cameraX, double cameraY, double cameraZ) {
        Aero_Profiler.start("aero.becell.call");
        Aero_AnimatedBatcher.bindTexturePath(key.texturePath);
        Aero_MeshRenderer.beginMeshState(key.options);
        try {
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(key.originX() - cameraX,
                                  key.originY() - cameraY,
                                  key.originZ() - cameraZ);
                for (int g = 0; g < 4; g++) {
                    int id = cached.ids[g];
                    if (id == 0) continue;
                    if (!PER_INSTANCE_LIGHT) {
                        float bright = key.brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                        GL11.glColor4f(bright * key.options.tintR,
                                       bright * key.options.tintG,
                                       bright * key.options.tintB,
                                       key.options.alpha);
                    }
                    GL11.glCallList(id);
                    pageCallsThisFrame++;
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_MeshRenderer.endMeshState();
            Aero_Profiler.end("aero.becell.call");
        }
        cached.lastUsedFrame = frameIndex;
    }

static void drawDirect(Aero_BECellQueuedPage page, double cameraX, double cameraY, double cameraZ) {
        Aero_Profiler.start("aero.becell.direct");
        Aero_AnimatedBatcher.bindTexturePath(page.key.texturePath);
        try {
            for (int i = 0; i < page.count; i++) {
                Aero_MeshRenderer.renderModelAtRestPreculled(page.key.model,
                    page.worldXs[i] - cameraX,
                    page.worldYs[i] - cameraY,
                    page.worldZs[i] - cameraZ,
                    page.key.rotation,
                    page.brightnesses[i],
                    page.key.options);
                directFallbacksThisFrame++;
            }
        } finally {
            Aero_Profiler.end("aero.becell.direct");
        }
    }

static void drawDirect(Aero_MeshModel model, String texturePath,
                                   double x, double y, double z,
                                   float rotation, float brightness,
                                   Aero_RenderOptions options) {
        if (model == null) return;
        Aero_Profiler.start("aero.becell.direct");
        Aero_AnimatedBatcher.bindTexturePath(texturePath);
        try {
            Aero_MeshRenderer.renderModelAtRestPreculled(model, x, y, z, rotation, brightness, options);
            directFallbacksThisFrame++;
        } finally {
            Aero_Profiler.end("aero.becell.direct");
        }
    }
}
