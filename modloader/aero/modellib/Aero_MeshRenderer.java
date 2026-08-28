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
public class Aero_MeshRenderer extends Aero_MeshRendererState {
    public Aero_MeshRenderer() {}

    public static void renderModel(Aero_MeshModel model, double x, double y, double z, float rotation, float brightness) { Aero_MeshModelRenderer.renderModel(model, x, y, z, rotation, brightness); }
    public static void renderModel(Aero_MeshModel model, double x, double y, double z, float rotation, float brightness, Aero_RenderOptions options) { Aero_MeshModelRenderer.renderModel(model, x, y, z, rotation, brightness, options); }
    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z, float rotation, float brightness) { Aero_MeshAtRestRenderer.renderModelAtRest(model, x, y, z, rotation, brightness); }
    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z, float rotation, float brightness, Aero_RenderOptions options) { Aero_MeshAtRestRenderer.renderModelAtRest(model, x, y, z, rotation, brightness, options); }
    static void renderModelAtRestPreculled(Aero_MeshModel model, double x, double y, double z, float rotation, float brightness, Aero_RenderOptions options) { Aero_MeshAtRestRenderer.renderModelAtRestPreculled(model, x, y, z, rotation, brightness, options); }
    public static void disposeModel(Aero_MeshModel model) { Aero_MeshAtRestRenderer.disposeModel(model); }
    public static void renderModel(Aero_MeshModel model, double x, double y, double z, float rotation, World world, int ox, int topY, int oz) { Aero_MeshModelRenderer.renderModel(model, x, y, z, rotation, world, ox, topY, oz); }
    public static void renderModel(Aero_MeshModel model, double x, double y, double z, float rotation, World world, int ox, int topY, int oz, Aero_RenderOptions options) { Aero_MeshModelRenderer.renderModel(model, x, y, z, rotation, world, ox, topY, oz, options); }
    public static void renderGroup(Aero_MeshModel model, String groupName, float brightness) { Aero_MeshModelRenderer.renderGroup(model, groupName, brightness); }
    public static void renderGroup(Aero_MeshModel model, String groupName, float brightness, Aero_RenderOptions options) { Aero_MeshModelRenderer.renderGroup(model, groupName, brightness, options); }
    public static void renderGroupRotated(Aero_MeshModel model, String groupName, double x, double y, double z, float brightness, float pivotX, float pivotY, float pivotZ, float angle, float axisX, float axisY, float axisZ) { Aero_MeshModelRenderer.renderGroupRotated(model, groupName, x, y, z, brightness, pivotX, pivotY, pivotZ, angle, axisX, axisY, axisZ); }
    public static void renderGroupRotated(Aero_MeshModel model, String groupName, double x, double y, double z, float brightness, float pivotX, float pivotY, float pivotZ, float angle, float axisX, float axisY, float axisZ, Aero_RenderOptions options) { Aero_MeshModelRenderer.renderGroupRotated(model, groupName, x, y, z, brightness, pivotX, pivotY, pivotZ, angle, axisX, axisY, axisZ, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationState state, double x, double y, double z, float brightness, float partialTick) { Aero_MeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationState state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options) { Aero_MeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick) { Aero_MeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options) { Aero_MeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose, Aero_IkChain[] ikChains) { Aero_MeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick, options, proceduralPose, ikChains); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose, Aero_IkChain[] ikChains, Aero_MorphState morphState) { Aero_MeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick, options, proceduralPose, ikChains, morphState); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose) { Aero_MeshAnimatedRenderer.renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick, options, proceduralPose); }
    static void renderAnimatedPreculled(Aero_MeshModel model, Aero_AnimationBundle bundle, Aero_AnimationDefinition def, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose) { Aero_MeshAnimatedRenderer.renderAnimatedPreculled(model, bundle, def, state, x, y, z, brightness, partialTick, options, proceduralPose); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick) { Aero_MeshAnimatedRenderer2.renderAnimated(model, state, x, y, z, brightness, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options) { Aero_MeshAnimatedRenderer2.renderAnimated(model, state, x, y, z, brightness, partialTick, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationPlayback state, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose) { Aero_MeshAnimatedRenderer2.renderAnimated(model, state, x, y, z, brightness, partialTick, options, proceduralPose); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationStack stack, double x, double y, double z, float brightness, float partialTick) { Aero_MeshAnimatedRenderer2.renderAnimated(model, stack, x, y, z, brightness, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationStack stack, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options) { Aero_MeshAnimatedRenderer2.renderAnimated(model, stack, x, y, z, brightness, partialTick, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationGraph graph, Aero_AnimationBundle bundle, double x, double y, double z, float brightness, float partialTick) { Aero_MeshAnimatedRenderer2.renderAnimated(model, graph, bundle, x, y, z, brightness, partialTick); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationGraph graph, Aero_AnimationBundle bundle, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options) { Aero_MeshAnimatedRenderer3.renderAnimated(model, graph, bundle, x, y, z, brightness, partialTick, options); }
    public static void renderAnimated(Aero_MeshModel model, Aero_AnimationStack stack, double x, double y, double z, float brightness, float partialTick, Aero_RenderOptions options, Aero_ProceduralPose proceduralPose) { Aero_MeshAnimatedRenderer3.renderAnimated(model, stack, x, y, z, brightness, partialTick, options, proceduralPose); }
    static void drawGroupsForInventory(Tessellator tess, float[][][] groups, float invSc) { Aero_MeshGeometryRenderer.drawGroupsForInventory(tess, groups, invSc); }
}
