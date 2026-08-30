package aero.modellib;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_MorphState;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/** Owns bone-page selection and the immediate animated draw body. */
final class Aero_MeshAnimatedDraw {
    private Aero_MeshAnimatedDraw() {}

    static boolean tryBonePages(Aero_MeshModel model,
            Aero_MeshModel.NamedGroup[] entries, Aero_MeshModel.BoneRef[] refs,
            Aero_BoneRenderPose[] pool, double x, double y, double z, float brightness,
            Aero_RenderOptions options, Aero_MorphState morphState, int poseDepthLimit) {
        return Aero_MeshBonePageRenderer.renderAnimatedViaBonePages(model, entries, refs, pool,
            x, y, z, brightness, options, morphState, poseDepthLimit);
    }

    static void render(Aero_MeshModel model, Aero_MeshModel.NamedGroup[] entries,
            Aero_MeshModel.BoneRef[] refs, Aero_BoneRenderPose[] pool,
            double x, double y, double z, float brightness, Aero_RenderOptions options,
            Aero_MorphState morphState, int poseDepthLimit) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            Aero_MeshGlStateRenderer.beginMeshState(options);
            try {
                drawStatic(tessellator, model, brightness, options, morphState);
                drawNamed(tessellator, model, entries, refs, pool,
                    brightness, options, poseDepthLimit);
            } finally {
                Aero_MeshGlStateRenderer.endMeshState();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void drawStatic(Tessellator tessellator, Aero_MeshModel model,
            float brightness, Aero_RenderOptions options, Aero_MorphState morphState) {
        if (morphState != null && model.hasMorphTargets() && !morphState.isEmpty()) {
            Aero_MeshGeometryRenderer.drawGroupsMorph(
                tessellator, model, brightness, options, morphState);
            return;
        }
        Aero_MeshGeometryRenderer.drawGroups(
            tessellator, model.groups, model.invScale, brightness, options);
    }

    private static void drawNamed(Tessellator tessellator, Aero_MeshModel model,
            Aero_MeshModel.NamedGroup[] entries, Aero_MeshModel.BoneRef[] refs,
            Aero_BoneRenderPose[] pool, float brightness, Aero_RenderOptions options,
            int poseDepthLimit) {
        for (int index = 0; index < entries.length; index++) {
            GL11.glPushMatrix();
            try {
                drawGroup(tessellator, model, entries[index], refs[index], pool,
                    brightness, options, poseDepthLimit);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    private static void drawGroup(Tessellator tessellator, Aero_MeshModel model,
            Aero_MeshModel.NamedGroup group, Aero_MeshModel.BoneRef ref,
            Aero_BoneRenderPose[] pool, float brightness, Aero_RenderOptions options,
            int poseDepthLimit) {
        Aero_BoneRenderPose pose = pool == null ? null
            : Aero_MeshPoseRenderer.applyPoseChain(ref, pool, poseDepthLimit);
        float uOffset = pose == null ? 0f : pose.uOffset;
        float vOffset = pose == null ? 0f : pose.vOffset;
        float uScale = pose == null ? 1f : pose.uScale;
        float vScale = pose == null ? 1f : pose.vScale;
        Aero_MeshGeometryRenderer.drawGroups(tessellator, group.tris, model.invScale,
            brightness, options, uOffset, vOffset, uScale, vScale);
    }
}
