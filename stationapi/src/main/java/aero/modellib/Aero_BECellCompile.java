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
final class Aero_BECellCompile extends Aero_BECellRenderState {
    private Aero_BECellCompile() {}

static boolean canRebuildAnotherPageThisFrame() {
        return REBUILDS_PER_FRAME < 0 || pageRebuildsThisFrame < REBUILDS_PER_FRAME;
    }

static Aero_BECellCachedPage compilePage(Aero_BECellQueuedPage page, int[] modelIds, int membershipHash) {
        long censusStartNs = Aero_FrameSpikeLogger.beginCellRebuild();
        Aero_Profiler.start("aero.becell.compile");
        try {
            int[] ids = new int[4];
            for (int g = 0; g < 4; g++) {
                int modelList = modelIds != null ? modelIds[g] : 0;
                if (FLATTENED_PAGES) {
                    if (!Aero_BECellCompile.hasBucketGeometry(page.key.model, g)) continue;
                } else if (modelList == 0) {
                    continue;
                }
                int id = Aero_DisplayListBudget.glGenList();
                if (id == 0) {
                    Aero_BECellCache.deleteIds(ids);
                    return null;
                }
                GL11.glNewList(id, GL11.GL_COMPILE);
                if (FLATTENED_PAGES) {
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    for (int i = 0; i < page.count; i++) {
                        if (PER_INSTANCE_LIGHT) {
                            float bright = page.brightnesses[i] * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                            GL11.glColor4f(bright * page.key.options.tintR,
                                           bright * page.key.options.tintG,
                                           bright * page.key.options.tintB,
                                           page.key.options.alpha);
                        }
                        Aero_BECellGeometry.emitModelBucketFlattened(page.key.model, g,
                            page.worldXs[i] - page.key.originX(),
                            page.worldYs[i] - page.key.originY(),
                            page.worldZs[i] - page.key.originZ(),
                            page.key.rotation);
                    }
                    GL11.glEnd();
                } else {
                    for (int i = 0; i < page.count; i++) {
                        if (PER_INSTANCE_LIGHT) {
                            float bright = page.brightnesses[i] * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                            GL11.glColor4f(bright * page.key.options.tintR,
                                           bright * page.key.options.tintG,
                                           bright * page.key.options.tintB,
                                           page.key.options.alpha);
                        }
                        GL11.glPushMatrix();
                        GL11.glTranslated(
                            page.worldXs[i] - page.key.originX(),
                            page.worldYs[i] - page.key.originY(),
                            page.worldZs[i] - page.key.originZ());
                        Aero_MeshRenderer.applyRotation(page.key.rotation);
                        GL11.glCallList(modelList);
                        GL11.glPopMatrix();
                    }
                }
                GL11.glEndList();
                ids[g] = id;
            }
            return new Aero_BECellCachedPage(ids, page.count, membershipHash, frameIndex);
        } finally {
            Aero_FrameSpikeLogger.endCellRebuild(censusStartNs);
            Aero_Profiler.end("aero.becell.compile");
        }
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
