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
final class Aero_BECellQueue extends Aero_BECellRenderState {
    private Aero_BECellQueue() {}

static void queueAtRest(Aero_MeshModel model, String texturePath,
                                   BlockEntity be,
                                   double x, double y, double z,
                                   float rotation, float brightness,
                                   Aero_RenderOptions options) {
        if (options == null) options = Aero_RenderOptions.DEFAULT;
        if (!Aero_BECellQueue.canQueue(model, be, options)) {
            Aero_BECellFlush.drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
            return;
        }
        if (!Aero_RenderDistance.hasCachedCamera()) {
            Aero_BECellFlush.drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
            return;
        }

        Aero_CellRenderableBE renderable = (Aero_CellRenderableBE) be;
        if (!renderable.aeroCanCellPage()) {
            Aero_BECellFlush.drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
            return;
        }

        // Renderer-provided x/y/z are camera-relative and may use vanilla's
        // interpolated camera, while Aero_RenderDistance caches the current
        // tick camera for culling. Reconstructing world coords from the cache
        // makes static overflow pages shimmer while the player walks. The BE
        // block coordinates are stable and match the normal renderer origin.
        double worldX = be.x;
        double worldY = be.y;
        double worldZ = be.z;
        if (!Aero_BECellQueue.queueWorldAtRest(model, texturePath, be, worldX, worldY, worldZ,
                rotation, brightness, options)) {
            Aero_BECellFlush.drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
        }
    }

static boolean tryQueueManagedAtRest(BlockEntity be, Aero_CellPageRenderableBE renderable) {
        if (!SKIP_INDIVIDUAL_RENDERERS || renderable == null) return false;
        Aero_RenderOptions options = renderable.aeroCellRenderOptions();
        if (options == null) options = Aero_RenderOptions.DEFAULT;
        Aero_MeshModel model = renderable.aeroCellModel();
        if (!Aero_BECellQueue.canQueue(model, be, options) || !renderable.aeroCanCellPage()) return false;
        return Aero_BECellQueue.queueWorldAtRest(model, renderable.aeroCellTexturePath(), be,
            be.x, be.y, be.z,
            renderable.aeroCellRotation(),
            renderable.aeroCellBrightness(),
            options);
    }

static boolean queueWorldAtRest(Aero_MeshModel model, String texturePath,
                                            BlockEntity be,
                                            double worldX, double worldY, double worldZ,
                                            float rotation, float brightness,
                                            Aero_RenderOptions options) {
        if (options == null) options = Aero_RenderOptions.DEFAULT;
        if (!Aero_BECellQueue.canQueue(model, be, options)) return false;

        Aero_CellRenderableBE renderable = (Aero_CellRenderableBE) be;
        if (!renderable.aeroCanCellPage()) return false;

        int cellX = Math.floorDiv(Aero_BECellGeometry.floorToInt(worldX), Aero_BECellIndex.CELL_SIZE);
        int cellY = Math.floorDiv(Aero_BECellGeometry.floorToInt(worldY), Aero_BECellIndex.CELL_SIZE);
        int cellZ = Math.floorDiv(Aero_BECellGeometry.floorToInt(worldZ), Aero_BECellIndex.CELL_SIZE);

        float keyBrightness = Aero_BECellGeometry.pageKeyBrightness(brightness);
        int stateHash = renderable.aeroRenderStateHash();
        int orientationHash = renderable.aeroOrientationHash();
        Aero_BECellQueuedPage page = ACTIVE.get(LOOKUP_KEY.set(be.world, model, texturePath, options,
            cellX, cellY, cellZ, rotation, keyBrightness, stateHash, orientationHash));
        if (page == null) {
            Aero_BECellPageKey key = new Aero_BECellPageKey(be.world, model, texturePath, options,
                cellX, cellY, cellZ, rotation, keyBrightness,
                stateHash, orientationHash);
            page = new Aero_BECellQueuedPage(key);
            ACTIVE.put(key, page);
            ACTIVE_PAGES.add(page);
        }
        page.add(be, worldX, worldY, worldZ, brightness);
        Aero_BECellReplay.add(page, worldX, worldY, worldZ, brightness);
        queuedThisFrame++;
        return true;
    }

static boolean canQueue(Aero_MeshModel model, BlockEntity be, Aero_RenderOptions options) {
        if (!ENABLED || model == null || be == null || be.world == null) return false;
        if (!(be instanceof Aero_CellRenderableBE)) return false;
        return options.blend == Aero_MeshBlendMode.OFF;
    }

static int queuedThisFrame() {
        return queuedThisFrame;
    }

static int queuedLastFrame() {
        return queuedLastFrame;
    }
}
