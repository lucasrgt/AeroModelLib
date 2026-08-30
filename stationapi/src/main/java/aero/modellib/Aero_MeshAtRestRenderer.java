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
final class Aero_MeshAtRestRenderer extends Aero_MeshRendererState {
    private Aero_MeshAtRestRenderer() {}

static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness) {
        Aero_MeshRenderer.renderModelAtRest(model, x, y, z, rotation, brightness, Aero_RenderOptions.DEFAULT);
    }

static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness,
        Aero_RenderOptions options) {
        Aero_MeshGlStateRenderer.updateCameraForwardFromPlayer();
        boolean visible = Aero_FrustumCull.isLikelyVisible(x, y, z);
        Aero_Prewarm.observeModel(model, visible);
        if (!visible) return;
        Aero_MeshAtRestRenderer.renderModelAtRestBody(model, x, y, z, rotation, brightness, options);
    }

static void renderModelAtRestPreculled(Aero_MeshModel model, double x, double y, double z,
                                           float rotation, float brightness,
                                           Aero_RenderOptions options) {
        Aero_Prewarm.observeModel(model, true);
        Aero_MeshAtRestRenderer.renderModelAtRestBody(model, x, y, z, rotation, brightness, options);
    }

static void renderModelAtRestBody(Aero_MeshModel model, double x, double y, double z,
                                              float rotation, float brightness,
                                              Aero_RenderOptions options) {
        atRestRendersThisFrame++;
        Aero_Profiler.start("aero.mesh.render");
        try {
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                Aero_MeshRenderer.applyRotation(rotation);
                Aero_MeshRenderer.beginMeshState(options);
                try {
                    if (!Aero_MeshAtRestRenderer.renderAtRestViaLists(model, brightness, options)) {
                        atRestTessFallbacksThisFrame++;
                        Tessellator tess = Tessellator.INSTANCE;
                        Aero_MeshGeometryRenderer.drawGroups(tess, model.groups, model.invScale, brightness, options);
                        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
                        for (int e = 0; e < entries.length; e++) {
                            Aero_MeshGeometryRenderer.drawGroups(tess, entries[e].tris, model.invScale, brightness, options);
                        }
                    }
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

static boolean renderAtRestViaLists(Aero_MeshModel model, float brightness,
                                                Aero_RenderOptions options) {
        if (!AT_REST_LISTS_ENABLED) return false;
        if (Aero_Prewarm.deferFirstUse(model)) return false;
        int[] ids = Aero_MeshRenderer.ensureAtRestListIds(model);
        if (ids == null) return false;
        for (int g = 0; g < 4; g++) {
            int id = ids[g];
            if (id == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            GL11.glColor4f(bright * options.tintR, bright * options.tintG,
                           bright * options.tintB, options.alpha);
            GL11.glCallList(id);
            atRestListCallsThisFrame++;
        }
        return true;
    }

static void disposeModel(Aero_MeshModel model) {
        if (model == null) return;
        Aero_BECellRenderer.disposeModel(model);
        int[] ids = model.extractAndClearAtRestListIds();
        Aero_MeshBonePageRenderer2.deletePageIds(ids);
        Aero_MeshBonePageRenderer2.deleteBonePageLists(model.extractAndClearBonePageLists());
    }

static int[] compileAtRestLists(Aero_MeshModel model) {
        int[] ids = new int[4];
        final float invSc = model.invScale;
        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int g = 0; g < 4; g++) {
            boolean hasContent = model.groups[g].length > 0;
            for (int e = 0; e < entries.length && !hasContent; e++) {
                if (entries[e].tris[g].length > 0) hasContent = true;
            }
            if (!hasContent) { ids[g] = 0; continue; }

            int id = Aero_DisplayListBudget.glGenList();
            if (id == 0) {
                for (int j = 0; j < g; j++) {
                    Aero_DisplayListBudget.glDeleteList(ids[j]);
                }
                return null;
            }
            GL11.glNewList(id, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_TRIANGLES);
            Aero_MeshAtRestRenderer.emitTrisIntoList(model.groups[g], invSc);
            for (int e = 0; e < entries.length; e++) {
                Aero_MeshAtRestRenderer.emitTrisIntoList(entries[e].tris[g], invSc);
            }
            GL11.glEnd();
            GL11.glEndList();
            ids[g] = id;
        }
        return ids;
    }

static void emitTrisIntoList(float[][] tris, float invSc) {
        for (int i = 0; i < tris.length; i++) {
            float[] t = tris[i];
            GL11.glTexCoord2f(t[3],  t[4]);  GL11.glVertex3f(t[0]*invSc,  t[1]*invSc,  t[2]*invSc);
            GL11.glTexCoord2f(t[8],  t[9]);  GL11.glVertex3f(t[5]*invSc,  t[6]*invSc,  t[7]*invSc);
            GL11.glTexCoord2f(t[13], t[14]); GL11.glVertex3f(t[10]*invSc, t[11]*invSc, t[12]*invSc);
        }
    }

static int[] ensureAtRestListIds(Aero_MeshModel model) {
        int[] ids = model.getAtRestListIds();
        if (ids != null) return ids;
        if (model.atRestListsCompileFailed()) return null;
        ids = Aero_MeshAtRestRenderer.compileAtRestLists(model);
        if (ids == null) {
            model.markAtRestListsCompileFailed();
            return null;
        }
        model.setAtRestListIds(ids);
        return ids;
    }

static void prewarmModel(Aero_MeshModel model) {
        if (model == null) return;
        Aero_MeshRenderer.ensureAtRestListIds(model);
        if (BONE_PAGES_ENABLED && !model.bonePageListsCompileFailed()) {
            Aero_MeshBonePageRenderer2.getOrCompileBonePageLists(model, model.getNamedGroupArray());
        }
    }
}
