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
final class Aero_BECellCache extends Aero_BECellRenderState {
    private Aero_BECellCache() {}

static void disposeModel(Aero_MeshModel model) {
        if (model == null || CACHE.isEmpty()) return;
        Iterator<Map.Entry<Aero_BECellPageKey, Aero_BECellCachedPage>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Aero_BECellPageKey, Aero_BECellCachedPage> entry = it.next();
            if (entry.getKey().model == model) {
                Aero_BECellCache.deleteIds(entry.getValue().ids);
                it.remove();
            }
        }
    }

static void sweepOldPages() {
        if (CACHE.isEmpty()) return;
        Iterator<Map.Entry<Aero_BECellPageKey, Aero_BECellCachedPage>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Aero_BECellPageKey, Aero_BECellCachedPage> entry = it.next();
            if (frameIndex - entry.getValue().lastUsedFrame > PAGE_TTL_FRAMES) {
                Aero_BECellCache.deleteIds(entry.getValue().ids);
                it.remove();
                expiredCachedPages++;
            }
        }
    }

static void enforceMaxCachedPages(Aero_BECellPageKey protectedKey) {
        if (MAX_CACHED_PAGES < 0) return;
        while (CACHE.size() > MAX_CACHED_PAGES) {
            Aero_BECellPageKey oldestKey = null;
            Aero_BECellCachedPage oldestPage = null;
            Iterator<Map.Entry<Aero_BECellPageKey, Aero_BECellCachedPage>> it = CACHE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Aero_BECellPageKey, Aero_BECellCachedPage> entry = it.next();
                if (entry.getKey() == protectedKey) continue;
                Aero_BECellCachedPage page = entry.getValue();
                if (oldestPage == null || page.lastUsedFrame < oldestPage.lastUsedFrame) {
                    oldestKey = entry.getKey();
                    oldestPage = page;
                }
            }
            if (oldestKey == null) return;
            Aero_BECellCache.deleteIds(oldestPage.ids);
            CACHE.remove(oldestKey);
            evictedCachedPages++;
        }
    }

static void deleteIds(int[] ids) {
        if (ids == null) return;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] != 0) {
                Aero_DisplayListBudget.glDeleteList(ids[i]);
                ids[i] = 0;
                deletedPages++;
            }
        }
    }
}
