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
final class Aero_BECellMetrics extends Aero_BECellRenderState {
    private Aero_BECellMetrics() {}

static int pageCallsThisFrame() {
        return pageCallsThisFrame;
    }

static int pageRebuildsThisFrame() {
        return pageRebuildsThisFrame;
    }

static int directFallbacksThisFrame() {
        return directFallbacksThisFrame;
    }

static int cachedPageCount() {
        return CACHE.size();
    }

static int deletedPages() {
        return deletedPages;
    }

static int compiledCachedPages() {
        return compiledCachedPages;
    }

static int expiredCachedPages() {
        return expiredCachedPages;
    }

static int evictedCachedPages() {
        return evictedCachedPages;
    }
}
