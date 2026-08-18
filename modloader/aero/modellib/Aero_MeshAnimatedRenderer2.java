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
final class Aero_MeshAnimatedRenderer2 extends Aero_MeshRendererState {
    private Aero_MeshAnimatedRenderer2() {}

static void renderAnimatedInternal(Aero_MeshModel model,
                                                Aero_AnimationBundle bundle,
                                                Aero_AnimationDefinition def,
                                                Aero_AnimationPlayback state,
                                                double x, double y, double z,
                                                float brightness, float partialTick,
                                                Aero_RenderOptions options,
                                                Aero_ProceduralPose proceduralPose,
                                                Aero_IkChain[] ikChains,
                                                Aero_MorphState morphState,
                                                boolean preculled) {
        if (!preculled
            && Aero_MeshPoseRenderer.prepareAnimatedRender(model, x, y, z, brightness, options) != ANIMATED_RENDER_ACTIVE) return;

        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            Aero_AnimationClip clip = null;
            float time = 0f;
            Aero_MeshModel.BoneRef[] refs = null;
            Aero_BoneRenderPose[] pool = null;
            if (entries.length != 0) {
                clip = state.getCurrentClip();
                time = state.getInterpolatedTime(partialTick);
                refs = model.boneRefsFor(clip, bundle);

                if (clip != null) {
                    pool = Aero_MeshPoseRenderer.ensurePoolSize(clip.boneNames.length);
                    float[][] clipPivots = bundle.resolvePivotsFor(clip);
                    // Pass 1: resolve every animated bone's pose into the
                    // pool so the hierarchical render walk + any IK pre-pass
                    // can read parent poses while drawing children.
                    for (int b = 0; b < clip.boneNames.length; b++) {
                        String boneName = clip.boneNames[b];
                        Aero_AnimationPoseResolver.resolveClip(b, boneName, clipPivots[b],
                            clip, state, time, partialTick,
                            SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, pool[b]);
                        if (proceduralPose != null) proceduralPose.apply(boneName, pool[b]);
                    }

                    // Pass 1.5: run IK chains. Only present when the
                    // caller provided ikChains; v0.1 callers pay zero cost
                    // since the array is null.
                    if (ikChains != null && ikChains.length > 0) {
                        Aero_Profiler.start("aero.mesh.ikSolve");
                        try {
                            Aero_MeshPoseRenderer.runIkChains(ikChains, clip, bundle, pool);
                        } finally {
                            Aero_Profiler.end("aero.mesh.ikSolve");
                        }
                    }
                }
            }

            int poseDepthLimit = Aero_MeshPoseRenderer.skeletalPoseDepthLimit(x, y, z,
                proceduralPose, ikChains, morphState);
            if (Aero_MeshBonePageRenderer.renderAnimatedViaBonePages(model, entries, refs, pool, x, y, z,
                    brightness, options, morphState, poseDepthLimit)) {
                return;
            }

            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshGlStateRenderer.beginMeshState(options);
                try {
                    // Static geometry: morph-blended path when the caller
                    // provided a non-empty MorphState and the model carries
                    // morph targets; else the raw fast path.
                    if (morphState != null && model.hasMorphTargets() && !morphState.isEmpty()) {
                        Aero_MeshGeometryRenderer.drawGroupsMorph(tess, model, brightness, options, morphState);
                    } else {
                        Aero_MeshGeometryRenderer.drawGroups(tess, model.groups, model.invScale, brightness, options);
                    }

                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        Aero_MeshModel.BoneRef    rf = refs[e];

                        GL11.glPushMatrix();
                        try {
                            // Pass 2: walk root → leaf, applying each
                            // animated ancestor's pose. Parent rotations
                            // propagate to children automatically.
                            Aero_BoneRenderPose deepest = pool != null
                                ? Aero_MeshPoseRenderer.applyPoseChain(rf, pool, poseDepthLimit)
                                : null;
                            float uOff   = deepest != null ? deepest.uOffset : 0f;
                            float vOff   = deepest != null ? deepest.vOffset : 0f;
                            float uScale = deepest != null ? deepest.uScale  : 1f;
                            float vScale = deepest != null ? deepest.vScale  : 1f;
                            Aero_MeshGeometryRenderer.drawGroups(tess, ng.tris, model.invScale, brightness, options,
                                uOff, vOff, uScale, vScale);
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
            Aero_Profiler.end("aero.mesh.renderAnimated");
        }
    }

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        Aero_MeshRenderer.renderAnimated(model, state, x, y, z, brightness, partialTick, Aero_RenderOptions.DEFAULT);
    }

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        Aero_MeshRenderer.renderAnimated(model, state, x, y, z, brightness, partialTick, options, null);
    }

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options,
                                       Aero_ProceduralPose proceduralPose) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        Aero_MeshRenderer.renderAnimated(model, state.getBundle(), state.getDef(), state,
            x, y, z, brightness, partialTick, options, proceduralPose);
    }

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationStack stack,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        Aero_MeshRenderer.renderAnimated(model, stack, x, y, z, brightness, partialTick, Aero_RenderOptions.DEFAULT);
    }

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationStack stack,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        Aero_MeshRenderer.renderAnimated(model, stack, x, y, z, brightness, partialTick, options, null);
    }

static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationGraph graph,
                                       Aero_AnimationBundle bundle,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        Aero_MeshRenderer.renderAnimated(model, graph, bundle, x, y, z, brightness, partialTick,
            Aero_RenderOptions.DEFAULT);
    }
static void renderAnimatedInternal(Aero_MeshModel model,
                                                Aero_AnimationBundle bundle,
                                                Aero_AnimationDefinition def,
                                                Aero_AnimationPlayback state,
                                                double x, double y, double z,
                                                float brightness, float partialTick,
                                                Aero_RenderOptions options,
                                                Aero_ProceduralPose proceduralPose,
                                                Aero_IkChain[] ikChains,
                                                Aero_MorphState morphState) {
        Aero_MeshAnimatedRenderer2.renderAnimatedInternal(model, bundle, def, state, x, y, z, brightness,
            partialTick, options, proceduralPose, ikChains, morphState, false);
    }
}
