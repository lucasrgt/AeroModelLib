package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationPoseResolver;
import aero.modellib.animation.Aero_AnimationStack;
import aero.modellib.animation.graph.Aero_AnimationGraph;
import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_AnimationTickLOD;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BonePageLists;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_CCDSolver;
import aero.modellib.skeletal.Aero_IkChain;
import aero.modellib.skeletal.Aero_MorphState;
import aero.modellib.skeletal.Aero_MorphTarget;
import aero.modellib.skeletal.Aero_ProceduralPose;
import aero.modellib.util.Aero_Profiler;

/**
 * AeroMesh Renderer by lucasrgt - aerocoding.dev
 * Renders OBJ models (Aero_MeshModel) using GL_TRIANGLES.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time
 *   - setColorOpaque_F called 4× per draw (vs N× in the naive approach)
 *   - Coordinate division by `sc` replaced with single multiplication
 *   - Smooth-light path samples each (x,z) world column once per draw and
 *     bilinearly interpolates from the cache (vs 4 lookups per triangle)
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc)
 *   - Bone/pivot resolution is memoized per (clip identity) on the model,
 *     so the per-group HashMap and linear-scan lookups happen only when
 *     the active clip changes
 *
 * Static geometry usage (TileEntitySpecialRenderer):
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, rotation, brightness);
 *
 * Animated part usage:
 *   // Render static geometry (everything except the named animated group)
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, 0, brightness);
 *   // Render animated group with per-tick angle + partial tick smoothing
 *   float angle = tile.fanAngle + (tile.isActive ? SPEED * partialTick : 0f);
 *   Aero_MeshRenderer.renderGroupRotated(MODEL, "fan",
 *       d + ox, d1 + oy, d2 + oz, brightness,
 *       pivotX, pivotY, pivotZ,   // pivot in model space (block units)
 *       angle, 0, 1, 0);          // angle + axis (Y-axis spin)
 *
 * Inventory usage:
 *   Aero_MeshRenderer.renderInventory(rb, MODEL);
 *
 * NOTE: uses Tessellator with GL_TRIANGLES — only call outside an active
 * startDrawingQuads() block. The TileEntitySpecialRenderer context is safe.
 */
final class Aero_MeshBonePageRenderer extends Aero_MeshRendererState {
    private Aero_MeshBonePageRenderer() {}

static boolean renderAnimatedViaBonePages(Aero_MeshModel model,
                                                       Aero_MeshModel.NamedGroup[] entries,
                                                       Aero_MeshModel.BoneRef[] refs,
                                                       Aero_BoneRenderPose[] pool,
                                                       double x, double y, double z,
                                                       float brightness,
                                                       Aero_RenderOptions options,
                                                       Aero_MorphState morphState,
                                                       int poseDepthLimit) {
        if (!canRenderAnimatedPages(model, morphState)) return false;

        Aero_BonePageLists pages = Aero_MeshBonePageRenderer.getOrCompileBonePageLists(model, entries);
        if (pages == null || !pages.hasAnyPages) return false;

        Aero_Profiler.start("aero.bonepages.call");
        try {
            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshGlStateRenderer.beginMeshState(options);
                try {
                    Aero_MeshBonePageRenderer2.renderStaticPageOrFallback(tess, model, pages, brightness, options);
                    renderAnimatedGroups(tess, model, pages, entries, refs, pool,
                        brightness, options, poseDepthLimit);
                } finally {
                    Aero_MeshGlStateRenderer.endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.bonepages.call");
        }
        return true;
    }

private static boolean canRenderAnimatedPages(Aero_MeshModel model, Aero_MorphState morph) {
        if (!BONE_PAGES_ENABLED) return false;
        return morph == null || !model.hasMorphTargets() || morph.isEmpty();
    }

private static void renderAnimatedGroups(Tessellator tess, Aero_MeshModel model,
        Aero_BonePageLists pages, Aero_MeshModel.NamedGroup[] entries,
        Aero_MeshModel.BoneRef[] refs, Aero_BoneRenderPose[] pool,
        float brightness, Aero_RenderOptions options, int poseDepthLimit) {
        for (int index = 0; index < entries.length; index++) {
            GL11.glPushMatrix();
            try {
                Aero_BoneRenderPose pose = pool != null && refs != null
                    ? Aero_MeshPoseRenderer.applyPoseChain(refs[index], pool, poseDepthLimit) : null;
                Aero_MeshBonePageRenderer2.renderBonePageOrFallback(tess,
                    Aero_MeshBonePageRenderer2.pageFor(pages, index), entries[index].tris,
                    model.invScale, brightness, options, pose);
            } finally { GL11.glPopMatrix(); }
        }
    }

static boolean renderGraphViaBonePages(Aero_MeshModel model,
                                                    Aero_AnimationGraph graph,
                                                    Aero_AnimationBundle bundle,
                                                    Aero_MeshModel.NamedGroup[] entries,
                                                    double x, double y, double z,
                                                    float brightness, float partialTick,
                                                    Aero_RenderOptions options) {
        if (!BONE_PAGES_ENABLED) return false;
        Aero_BonePageLists pages = Aero_MeshBonePageRenderer.getOrCompileBonePageLists(model, entries);
        if (pages == null || !pages.hasAnyPages) return false;

        Aero_Profiler.start("aero.bonepages.call");
        try {
            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshGlStateRenderer.beginMeshState(options);
                try {
                    Aero_MeshBonePageRenderer2.renderStaticPageOrFallback(tess, model, pages, brightness, options);
                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        SCRATCH_POSE.reset();
                        bundle.getPivotInto(ng.name, SCRATCH_PIVOT);
                        SCRATCH_POSE.setPivot(SCRATCH_PIVOT);
                        graph.samplePose(ng.name, partialTick,
                            SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL);
                        SCRATCH_POSE.rotX = SCRATCH_ROT[0];
                        SCRATCH_POSE.rotY = SCRATCH_ROT[1];
                        SCRATCH_POSE.rotZ = SCRATCH_ROT[2];
                        SCRATCH_POSE.offsetX = SCRATCH_POS[0] / 16f;
                        SCRATCH_POSE.offsetY = SCRATCH_POS[1] / 16f;
                        SCRATCH_POSE.offsetZ = SCRATCH_POS[2] / 16f;
                        SCRATCH_POSE.scaleX = SCRATCH_SCL[0];
                        SCRATCH_POSE.scaleY = SCRATCH_SCL[1];
                        SCRATCH_POSE.scaleZ = SCRATCH_SCL[2];

                        GL11.glPushMatrix();
                        try {
                            Aero_MeshPoseRenderer.applyPose(SCRATCH_POSE);
                            Aero_MeshBonePageRenderer2.renderBonePageOrFallback(tess, Aero_MeshBonePageRenderer2.pageFor(pages, e), ng.tris,
                                model.invScale, brightness, options, SCRATCH_POSE);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    Aero_MeshGlStateRenderer.endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.bonepages.call");
        }
        return true;
    }

static boolean renderStackViaBonePages(Aero_MeshModel model,
                                                    Aero_AnimationStack stack,
                                                    Aero_MeshModel.NamedGroup[] entries,
                                                    double x, double y, double z,
                                                    float brightness, float partialTick,
                                                    Aero_RenderOptions options,
                                                    Aero_ProceduralPose proceduralPose) {
        if (!BONE_PAGES_ENABLED) return false;
        Aero_BonePageLists pages = Aero_MeshBonePageRenderer.getOrCompileBonePageLists(model, entries);
        if (pages == null || !pages.hasAnyPages) return false;

        Aero_Profiler.start("aero.bonepages.call");
        try {
            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshGlStateRenderer.beginMeshState(options);
                try {
                    Aero_MeshBonePageRenderer2.renderStaticPageOrFallback(tess, model, pages, brightness, options);
                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        Aero_AnimationPoseResolver.resolveStack(stack, ng.name, partialTick,
                            SCRATCH_PIVOT, SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, SCRATCH_POSE);
                        if (proceduralPose != null) proceduralPose.apply(ng.name, SCRATCH_POSE);

                        GL11.glPushMatrix();
                        try {
                            Aero_MeshPoseRenderer.applyPose(SCRATCH_POSE);
                            Aero_MeshBonePageRenderer2.renderBonePageOrFallback(tess, Aero_MeshBonePageRenderer2.pageFor(pages, e), ng.tris,
                                model.invScale, brightness, options, SCRATCH_POSE);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    Aero_MeshGlStateRenderer.endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.bonepages.call");
        }
        return true;
    }

static Aero_BonePageLists getOrCompileBonePageLists(Aero_MeshModel model,
                                                                Aero_MeshModel.NamedGroup[] entries) {
        Aero_BonePageLists pages = model.getBonePageLists();
        if (pages != null) return pages;
        if (model.bonePageListsCompileFailed()) return null;

        Aero_Profiler.start("aero.bonepages.compile");
        try {
            pages = Aero_MeshBonePageRenderer2.compileBonePageLists(model, entries);
        } finally {
            Aero_Profiler.end("aero.bonepages.compile");
        }
        if (pages == null) {
            model.markBonePageListsCompileFailed();
            return null;
        }
        model.setBonePageLists(pages);
        return pages;
    }
}
