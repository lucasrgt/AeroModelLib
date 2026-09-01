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
import aero.modellib.optimization.OptimizationRef;
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
@OptimizationRef({"aero.render.be-cell-pages", "aero.render.be-cell-page-flattening"})
final class Aero_BECellCompile extends Aero_BECellRenderState {
    private Aero_BECellCompile() {}

static boolean canRebuildAnotherPageThisFrame() {
        return REBUILDS_PER_FRAME < 0 || pageRebuildsThisFrame < REBUILDS_PER_FRAME;
    }

static Aero_BECellCachedPage compilePage(Aero_BECellQueuedPage page, int[] modelIds, int membershipHash) {
    long censusStartNs = Aero_FrameSpikeLogger.beginCellRebuild();
    Aero_Profiler.start("aero.becell.compile");
    try {
        if (!FLATTENED_PAGES) {
            return compileOrderedModelPage(page, modelIds, membershipHash);
        }
        int[] ids = new int[4];
        for (int g = 0; g < 4; g++) {
            if (!hasBucketGeometry(page.key.model, g)) continue;
            int id = Aero_DisplayListBudget.glGenList();
            if (id == 0) {
                Aero_BECellCache.deleteIds(ids);
                return null;
            }
            GL11.glNewList(id, GL11.GL_COMPILE);
            emitFlattened(page, g);
            GL11.glEndList();
            ids[g] = id;
            }
            return new Aero_BECellCachedPage(ids, page.count, membershipHash, frameIndex);
        } finally {
            Aero_FrameSpikeLogger.endCellRebuild(censusStartNs);
            Aero_Profiler.end("aero.becell.compile");
    }
}

private static Aero_BECellCachedPage compileOrderedModelPage(Aero_BECellQueuedPage page,
        int[] modelIds, int membershipHash) {
    if (!hasModelList(modelIds)) return null;
    int id = Aero_DisplayListBudget.glGenList();
    if (id == 0) return null;
    GL11.glNewList(id, GL11.GL_COMPILE);
    emitOrderedModelTemplate(page, modelIds);
    GL11.glEndList();
    return new Aero_BECellCachedPage(new int[] {id, 0, 0, 0},
        page.count, membershipHash, frameIndex);
}

private static void emitFlattened(Aero_BECellQueuedPage page, int bucket) {
        for (int i = 0; i < page.count; i++) {
            applyLight(page, bucket, i);
            emitFlattenedInstance(page, bucket, i);
        }
    }

private static void emitFlattenedInstance(Aero_BECellQueuedPage page, int bucket, int index) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(page.worldXs[index] - page.key.originX(),
                page.worldYs[index] - page.key.originY(), page.worldZs[index] - page.key.originZ());
            Aero_MeshRenderer.applyRotation(page.key.rotation);
            GL11.glBegin(GL11.GL_TRIANGLES);
            try {
                Aero_BECellGeometry.emitModelBucketFlattened(
                    page.key.model, bucket, 0.0d, 0.0d, 0.0d, 0.0f);
            } finally {
                GL11.glEnd();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

private static void emitOrderedModelTemplate(Aero_BECellQueuedPage page, int[] modelIds) {
    for (int bucket = 0; bucket < modelIds.length; bucket++) {
        if (modelIds[bucket] == 0) continue;
        float bright = page.key.brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[bucket];
        GL11.glColor4f(bright * page.key.options.tintR, bright * page.key.options.tintG,
            bright * page.key.options.tintB, page.key.options.alpha);
        GL11.glCallList(modelIds[bucket]);
    }
}

private static boolean hasModelList(int[] modelIds) {
    if (modelIds == null) return false;
    for (int i = 0; i < modelIds.length; i++) {
        if (modelIds[i] != 0) return true;
    }
    return false;
}

private static void applyLight(Aero_BECellQueuedPage page, int bucket, int index) {
        if (!PER_INSTANCE_LIGHT) return;
        float bright = page.brightnesses[index] * Aero_MeshModel.BRIGHTNESS_FACTORS[bucket];
        GL11.glColor4f(bright * page.key.options.tintR, bright * page.key.options.tintG,
            bright * page.key.options.tintB, page.key.options.alpha);
    }

static boolean hasBucketGeometry(Aero_MeshModel model, int bucket) {
        if (model.groups[bucket].length > 0) return true;
        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].tris[bucket].length > 0) return true;
        }
        return false;
    }
}
