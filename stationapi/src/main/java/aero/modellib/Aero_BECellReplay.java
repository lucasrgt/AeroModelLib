package aero.modellib;

import org.lwjgl.opengl.GL11;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.optimization.OptimizationRef;
import aero.modellib.util.Aero_Profiler;

/** Allocation-free original-submission-order replay for non-flattened pages. */
@OptimizationRef({"aero.render.be-cell-pages"})
final class Aero_BECellReplay extends Aero_BECellRenderState {
    private static Aero_BECellQueuedPage[] pages = new Aero_BECellQueuedPage[256];
    private static double[] xs = new double[256];
    private static double[] ys = new double[256];
    private static double[] zs = new double[256];
    private static float[] brightnesses = new float[256];
    private static int count;

    private Aero_BECellReplay() {}

    static void add(Aero_BECellQueuedPage page,
            double x, double y, double z, float brightness) {
        ensureCapacity();
        pages[count] = page;
        xs[count] = x;
        ys[count] = y;
        zs[count] = z;
        brightnesses[count] = brightness;
        count++;
    }

    static void draw(double cameraX, double cameraY, double cameraZ) {
        Aero_BECellPageKey active = null;
        Aero_Profiler.start("aero.becell.call");
        try {
            for (int i = 0; i < count; i++) {
                Aero_BECellQueuedPage page = pages[i];
                if (page.replayDirect) {
                    active = close(active);
                    Aero_BECellFlush.drawDirectInstance(page, xs[i], ys[i], zs[i],
                        brightnesses[i], cameraX, cameraY, cameraZ);
                    continue;
                }
                if (active == null || !active.sameReplayState(page.key)) {
                    active = close(active);
                    Aero_AnimatedBatcher.bindTexturePath(page.key.texturePath);
                    Aero_MeshRenderer.beginMeshState(page.key.options);
                    active = page.key;
                }
                drawCached(page, xs[i], ys[i], zs[i], brightnesses[i],
                    cameraX, cameraY, cameraZ);
            }
        } finally {
            close(active);
            Aero_Profiler.end("aero.becell.call");
        }
    }

    static void clear() {
        for (int i = 0; i < count; i++) pages[i] = null;
        count = 0;
    }

    private static void drawCached(Aero_BECellQueuedPage page,
            double worldX, double worldY, double worldZ, float brightness,
            double cameraX, double cameraY, double cameraZ) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(worldX - cameraX, worldY - cameraY, worldZ - cameraZ);
            Aero_MeshRenderer.applyRotation(page.key.rotation);
            if (PER_INSTANCE_LIGHT) drawBuckets(page, brightness);
            else drawTemplate(page.replayCached);
        } finally {
            GL11.glPopMatrix();
        }
        page.replayCached.lastUsedFrame = frameIndex;
    }

    private static void drawTemplate(Aero_BECellCachedPage cached) {
        int id = cached.ids[0];
        if (id == 0) return;
        GL11.glCallList(id);
        pageCallsThisFrame++;
    }

    private static void drawBuckets(Aero_BECellQueuedPage page, float base) {
        int[] modelIds = page.replayModelIds;
        for (int group = 0; group < modelIds.length; group++) {
            if (modelIds[group] == 0) continue;
            float bright = base * Aero_MeshModel.BRIGHTNESS_FACTORS[group];
            GL11.glColor4f(bright * page.key.options.tintR, bright * page.key.options.tintG,
                bright * page.key.options.tintB, page.key.options.alpha);
            GL11.glCallList(modelIds[group]);
            pageCallsThisFrame++;
        }
    }

    private static Aero_BECellPageKey close(Aero_BECellPageKey active) {
        if (active != null) Aero_MeshRenderer.endMeshState();
        return null;
    }

    private static void ensureCapacity() {
        if (count < pages.length) return;
        int size = pages.length * 2;
        Aero_BECellQueuedPage[] newPages = new Aero_BECellQueuedPage[size];
        double[] newXs = new double[size];
        double[] newYs = new double[size];
        double[] newZs = new double[size];
        float[] newBrightnesses = new float[size];
        System.arraycopy(pages, 0, newPages, 0, count);
        System.arraycopy(xs, 0, newXs, 0, count);
        System.arraycopy(ys, 0, newYs, 0, count);
        System.arraycopy(zs, 0, newZs, 0, count);
        System.arraycopy(brightnesses, 0, newBrightnesses, 0, count);
        pages = newPages;
        xs = newXs;
        ys = newYs;
        zs = newZs;
        brightnesses = newBrightnesses;
    }
}
