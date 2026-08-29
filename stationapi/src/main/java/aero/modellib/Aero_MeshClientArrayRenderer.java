package aero.modellib;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.optimization.OptimizationRef;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BoneRenderPose;

/** Opt-in OpenGL 1.1 client-array path for compatible animated batches. */
@OptimizationRef({"aero.render.client-vertex-arrays"})
final class Aero_MeshClientArrayRenderer {
    static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.clientarrays"));

    private Aero_MeshClientArrayRenderer() {}

    static void render(Aero_AnimatedBatcher.Batch batch, Aero_MeshModel model,
            Aero_RenderOptions options, Aero_MeshRendererState.BatchPlan plan,
            Aero_MeshModel.NamedGroup[] entries,
            Aero_BoneRenderPose[][] poses, boolean[][] active, int[] poseSources, int count) {
        Aero_AnimatedBatcher.bindBatchTexture(batch);
        Aero_MeshRenderer.beginMeshState(options);
        Aero_ClientArrayBuffer.beginClientState();
        try {
            if (plan.hasStaticGeometry) renderStatic(batch, model, options, count);
            for (int d = 0; d < plan.drawableEntries.length; d++) {
                int entry = plan.drawableEntries[d];
                renderBone(batch, model, options, entries[entry], poses, active,
                    poseSources, entry, count);
            }
        } finally {
            Aero_ClientArrayBuffer.endClientState();
            Aero_MeshRenderer.endMeshState();
        }
    }

    private static void renderStatic(Aero_AnimatedBatcher.Batch batch,
            Aero_MeshModel model, Aero_RenderOptions options, int count) {
        Aero_ClientArrayBuffer.begin();
        float lastBrightness = Float.NaN;
        for (int group = 0; group < 4; group++) {
            float[][] tris = model.groups[group];
            if (tris.length == 0) continue;
            float factor = Aero_MeshModel.BRIGHTNESS_FACTORS[group];
            for (int i = 0; i < count; i++) {
                float brightness = batch.brightnesses[i] * factor;
                if (brightness != lastBrightness) {
                    color(brightness, options);
                    lastBrightness = brightness;
                }
                Aero_MeshClientArrayEmitter.emitStatic(tris, model.invScale,
                    batch.xs[i], batch.ys[i], batch.zs[i]);
            }
        }
        Aero_ClientArrayBuffer.draw();
    }

    private static void renderBone(Aero_AnimatedBatcher.Batch batch,
            Aero_MeshModel model, Aero_RenderOptions options, Aero_MeshModel.NamedGroup named,
            Aero_BoneRenderPose[][] poses, boolean[][] active, int[] poseSources,
            int entry, int count) {
        Aero_ClientArrayBuffer.begin();
        float lastBrightness = Float.NaN;
        for (int group = 0; group < 4; group++) {
            float[][] tris = named.tris[group];
            if (tris.length == 0) continue;
            float factor = Aero_MeshModel.BRIGHTNESS_FACTORS[group];
            for (int i = 0; i < count; i++) {
                int poseIndex = Aero_BatchPoseReuse.ENABLED ? poseSources[i] : i;
                float brightness = batch.brightnesses[i] * factor;
                if (brightness != lastBrightness) {
                    color(brightness, options);
                    lastBrightness = brightness;
                }
                if (active[poseIndex][entry]) {
                    Aero_MeshClientArrayEmitter.emitBone(tris, model.invScale, poses[poseIndex][entry],
                        batch.xs[i], batch.ys[i], batch.zs[i]);
                } else {
                    Aero_MeshClientArrayEmitter.emitStatic(tris, model.invScale,
                        batch.xs[i], batch.ys[i], batch.zs[i]);
                }
            }
        }
        Aero_ClientArrayBuffer.draw();
    }

    private static void color(float brightness, Aero_RenderOptions options) {
        Aero_ClientArrayBuffer.color(brightness * options.tintR,
            brightness * options.tintG, brightness * options.tintB, options.alpha);
    }
}
