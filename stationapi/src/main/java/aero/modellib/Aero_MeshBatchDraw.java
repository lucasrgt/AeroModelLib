package aero.modellib;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import net.minecraft.client.render.Tessellator;
import org.lwjgl.opengl.GL11;

/** Emits one prepared animated batch through the tessellator. */
final class Aero_MeshBatchDraw {
    private Aero_MeshBatchDraw() {}

    static void render(Aero_AnimatedBatcher.Batch batch, Aero_MeshModel model,
            Aero_RenderOptions options, Aero_MeshRendererState.BatchPlan plan,
            Aero_MeshModel.NamedGroup[] entries, Aero_BoneRenderPose[][] poses,
            boolean[][] active, int[] poseSources, int count) {
        Tessellator tessellator = Tessellator.INSTANCE;
        Aero_AnimatedBatcher.bindBatchTexture(batch);
        Aero_MeshRenderer.beginMeshState(options);
        try {
            drawStatic(tessellator, batch, model, options, plan, count);
            drawNamed(tessellator, batch, model, options, plan, entries,
                poses, active, poseSources, count);
        } finally {
            Aero_MeshRenderer.endMeshState();
        }
    }

    private static void drawStatic(Tessellator tessellator, Aero_AnimatedBatcher.Batch batch,
            Aero_MeshModel model, Aero_RenderOptions options,
            Aero_MeshRendererState.BatchPlan plan, int count) {
        if (!plan.hasStaticGeometry) return;
        tessellator.start(GL11.GL_TRIANGLES);
        float lastBrightness = Float.NaN;
        for (int group = 0; group < 4; group++) {
            float[][] triangles = model.groups[group];
            if (triangles.length == 0) continue;
            lastBrightness = Aero_MeshBatchRenderer2.emitStaticInstancesBatched(
                tessellator, triangles, model.invScale,
                Aero_MeshModel.BRIGHTNESS_FACTORS[group], batch, count, options,
                lastBrightness);
        }
        tessellator.draw();
    }

    private static void drawNamed(Tessellator tessellator, Aero_AnimatedBatcher.Batch batch,
            Aero_MeshModel model, Aero_RenderOptions options,
            Aero_MeshRendererState.BatchPlan plan, Aero_MeshModel.NamedGroup[] entries,
            Aero_BoneRenderPose[][] poses, boolean[][] active, int[] poseSources, int count) {
        for (int drawable = 0; drawable < plan.drawableEntries.length; drawable++) {
            int entry = plan.drawableEntries[drawable];
            tessellator.start(GL11.GL_TRIANGLES);
            drawEntry(tessellator, batch, model, options, entries[entry],
                poses, active, poseSources, count, entry);
            tessellator.draw();
        }
    }

    private static void drawEntry(Tessellator tessellator, Aero_AnimatedBatcher.Batch batch,
            Aero_MeshModel model, Aero_RenderOptions options, Aero_MeshModel.NamedGroup entry,
            Aero_BoneRenderPose[][] poses, boolean[][] active, int[] poseSources,
            int count, int entryIndex) {
        float lastBrightness = Float.NaN;
        for (int group = 0; group < 4; group++) {
            float[][] triangles = entry.tris[group];
            if (triangles.length == 0) continue;
            Aero_BatchVertexReuse.beginBucket(Aero_BatchPoseReuse.sharedSource());
            lastBrightness = drawBucket(tessellator, batch, model, options, triangles,
                poses, active, poseSources, count, entryIndex,
                Aero_MeshModel.BRIGHTNESS_FACTORS[group], lastBrightness);
        }
    }

    private static float drawBucket(Tessellator tessellator, Aero_AnimatedBatcher.Batch batch,
            Aero_MeshModel model, Aero_RenderOptions options, float[][] triangles,
            Aero_BoneRenderPose[][] poses, boolean[][] active, int[] poseSources,
            int count, int entryIndex, float factor, float lastBrightness) {
        for (int instance = 0; instance < count; instance++) {
            int poseIndex = Aero_BatchPoseReuse.ENABLED ? poseSources[instance] : instance;
            float brightness = batch.brightnesses[instance] * factor;
            if (brightness != lastBrightness) {
                tessellator.color(brightness * options.tintR, brightness * options.tintG,
                    brightness * options.tintB, options.alpha);
                lastBrightness = brightness;
            }
            emitInstance(tessellator, batch, model, triangles, poses, active,
                instance, poseIndex, entryIndex);
        }
        return lastBrightness;
    }

    private static void emitInstance(Tessellator tessellator, Aero_AnimatedBatcher.Batch batch,
            Aero_MeshModel model, float[][] triangles, Aero_BoneRenderPose[][] poses,
            boolean[][] active, int instance, int poseIndex, int entryIndex) {
        if (!active[poseIndex][entryIndex]) {
            if (!Aero_BatchVertexReuse.emitRest(tessellator, triangles, model.invScale,
                    poseIndex, batch.xs[instance], batch.ys[instance], batch.zs[instance]))
                Aero_MeshBatchRenderer3.emitBoneInstanceBatchedRest(tessellator, triangles,
                    model.invScale, batch.xs[instance], batch.ys[instance], batch.zs[instance]);
            return;
        }
        Aero_BoneRenderPose pose = poses[poseIndex][entryIndex];
        if (!Aero_BatchVertexReuse.emitPose(tessellator, triangles, model.invScale,
                pose, poseIndex, batch.xs[instance], batch.ys[instance], batch.zs[instance]))
            Aero_MeshBatchRenderer2.emitBoneInstanceBatched(tessellator, triangles,
                model.invScale, pose, batch.xs[instance], batch.ys[instance], batch.zs[instance]);
    }
}
