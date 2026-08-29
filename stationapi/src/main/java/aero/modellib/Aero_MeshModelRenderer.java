package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;

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
 * AeroMesh Renderer (StationAPI/Yarn port). Same algorithm as the ModLoader
 * version, with Yarn-mapped Tessellator + World API.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time.
 *   - Tessellator color called 4× per draw (vs N× naive).
 *   - Coordinate division by `sc` replaced with single multiplication.
 *   - Smooth-light path samples each (x,z) world column once per draw and
 *     bilinearly interpolates from the cache (vs 4 lookups per triangle).
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc).
 *   - Bone/pivot resolution memoized per (clip identity) on the model.
 */
final class Aero_MeshModelRenderer extends Aero_MeshRendererState {
    private Aero_MeshModelRenderer() {}

static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, float brightness) {
        Aero_MeshRenderer.renderModel(model, x, y, z, rotation, brightness, Aero_RenderOptions.DEFAULT);
    }

static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, float brightness,
                                    Aero_RenderOptions options) {
        Aero_MeshGlStateRenderer.updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshRenderer.applyRotation(rotation);
                Aero_MeshRenderer.beginMeshState(options);
                try {
                    Aero_MeshGeometryRenderer.drawGroups(tess, model.groups, model.invScale, brightness, options);
                } finally {
                    Aero_MeshRenderer.endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
    }

static void beginFrameCounters() {
        atRestRendersThisFrame = 0;
        atRestListCallsThisFrame = 0;
        atRestTessFallbacksThisFrame = 0;
        bonePageListCallsThisFrame = 0;
    }

static int atRestRendersThisFrame() {
        return atRestRendersThisFrame;
    }

static int atRestListCallsThisFrame() {
        return atRestListCallsThisFrame;
    }

static int atRestTessFallbacksThisFrame() {
        return atRestTessFallbacksThisFrame;
    }

static int bonePageListCallsThisFrame() {
        return bonePageListCallsThisFrame;
    }

static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, World world, int ox, int topY, int oz) {
        Aero_MeshRenderer.renderModel(model, x, y, z, rotation, world, ox, topY, oz, Aero_RenderOptions.DEFAULT);
    }

static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, World world, int ox, int topY, int oz,
                                    Aero_RenderOptions options) {
        Aero_MeshGlStateRenderer.updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshRenderer.applyRotation(rotation);
                Aero_MeshRenderer.beginMeshState(options);
                try {
                    Aero_MeshSmoothLightRenderer.drawGroupsSmooth(tess, model.groups, model.invScale, model.getStaticSmoothLightData(),
                        world, ox, topY, oz, options);
                } finally {
                    Aero_MeshRenderer.endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
    }

static void renderGroup(Aero_MeshModel model, String groupName, float brightness) {
        Aero_MeshRenderer.renderGroup(model, groupName, brightness, Aero_RenderOptions.DEFAULT);
    }

static void renderGroup(Aero_MeshModel model, String groupName, float brightness,
                                   Aero_RenderOptions options) {
        float[][][] ng = model.getNamedGroup(groupName);
        if (ng == null) return;
        Tessellator tess = Tessellator.INSTANCE;
        Aero_MeshGeometryRenderer.drawGroups(tess, ng, model.invScale, brightness, options);
    }

static void renderGroupRotated(Aero_MeshModel model, String groupName,
                                           double x, double y, double z, float brightness,
                                           float pivotX, float pivotY, float pivotZ,
                                           float angle, float axisX, float axisY, float axisZ) {
        Aero_MeshRenderer.renderGroupRotated(model, groupName, x, y, z, brightness,
            pivotX, pivotY, pivotZ, angle, axisX, axisY, axisZ, Aero_RenderOptions.DEFAULT);
    }

static void renderGroupRotated(Aero_MeshModel model, String groupName,
                                           double x, double y, double z, float brightness,
                                           float pivotX, float pivotY, float pivotZ,
                                           float angle, float axisX, float axisY, float axisZ,
                                           Aero_RenderOptions options) {
        Aero_MeshGlStateRenderer.updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        float[][][] ng = model.getNamedGroup(groupName);
        if (ng == null) return;

        Tessellator tess = Tessellator.INSTANCE;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glTranslatef(pivotX, pivotY, pivotZ);
            GL11.glRotatef(angle, axisX, axisY, axisZ);
            GL11.glTranslatef(-pivotX, -pivotY, -pivotZ);
            Aero_MeshRenderer.beginMeshState(options);
            try {
                Aero_MeshGeometryRenderer.drawGroups(tess, ng, model.invScale, brightness, options);
            } finally {
                Aero_MeshRenderer.endMeshState();
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

static BatchPlan batchPlanFor(Aero_MeshModel model,
                                          Aero_AnimationClip clip,
                                          Aero_AnimationBundle bundle,
                                          Aero_MeshModel.NamedGroup[] entries) {
        BatchPlanCache cache = BATCH_PLAN_CACHE.get(model);
        if (cache == null) {
            cache = new BatchPlanCache();
            BATCH_PLAN_CACHE.put(model, cache);
        }
        return cache.get(model, clip, bundle, entries);
    }
}
