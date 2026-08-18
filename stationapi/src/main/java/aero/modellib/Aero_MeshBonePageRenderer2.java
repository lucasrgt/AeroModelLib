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
final class Aero_MeshBonePageRenderer2 extends Aero_MeshRendererState {
    private Aero_MeshBonePageRenderer2() {}

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

static Aero_BonePageLists compileBonePageLists(Aero_MeshModel model,
                                                           Aero_MeshModel.NamedGroup[] entries) {
        int[] staticPages = null;
        int[][] bonePages = new int[entries.length][];
        boolean any = false;

        if (Aero_MeshBonePageRenderer2.eligibleForBonePage(model.groups)) {
            staticPages = Aero_MeshBonePageRenderer2.compileBucketPages(model.groups, model.invScale);
            if (staticPages == null) return null;
            any |= Aero_MeshBonePageRenderer2.hasPages(staticPages);
        }

        for (int e = 0; e < entries.length; e++) {
            if (!Aero_MeshBonePageRenderer2.eligibleForBonePage(entries[e].tris)) continue;
            bonePages[e] = Aero_MeshBonePageRenderer2.compileBucketPages(entries[e].tris, model.invScale);
            if (bonePages[e] == null) {
                Aero_MeshBonePageRenderer2.deletePageIds(staticPages);
                Aero_MeshBonePageRenderer2.deleteBonePageArrays(bonePages);
                return null;
            }
            any |= Aero_MeshBonePageRenderer2.hasPages(bonePages[e]);
        }

        return new Aero_BonePageLists(staticPages, bonePages, any);
    }

static int[] compileBucketPages(float[][][] groups, float invSc) {
        int[] ids = new int[4];
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;

            int id = Aero_DisplayListBudget.glGenList();
            if (id == 0) {
                Aero_MeshBonePageRenderer2.deletePageIds(ids);
                return null;
            }
            GL11.glNewList(id, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_TRIANGLES);
            Aero_MeshAtRestRenderer.emitTrisIntoList(tris, invSc);
            GL11.glEnd();
            GL11.glEndList();
            ids[g] = id;
        }
        return ids;
    }

static void renderStaticPageOrFallback(Tessellator tess, Aero_MeshModel model,
                                                    Aero_BonePageLists pages,
                                                    float brightness, Aero_RenderOptions options) {
        if (Aero_MeshBonePageRenderer2.hasPages(pages.staticPages)) {
            Aero_MeshBonePageRenderer2.callPageBuckets(pages.staticPages, brightness, options);
        } else if (Aero_MeshBonePageRenderer2.hasTriangles(model.groups)) {
            Aero_MeshGeometryRenderer.drawGroups(tess, model.groups, model.invScale, brightness, options);
        }
    }

static void renderBonePageOrFallback(Tessellator tess, int[] ids,
                                                  float[][][] groups, float invSc,
                                                  float brightness, Aero_RenderOptions options,
                                                  Aero_BoneRenderPose pose) {
        // UV animation is exact in the Tessellator path. Avoid routing it
        // through display-list texture matrices; old GL 1.1 state around MC's
        // texture units can make UV-only effects look frozen or bleed atlas
        // state on some StationAPI stacks.
        if (Aero_MeshBonePageRenderer2.hasPages(ids) && (pose == null || pose.uvIsIdentity())) {
            boolean uv = Aero_MeshBonePageRenderer2.pushUvMatrix(pose);
            try {
                Aero_MeshBonePageRenderer2.callPageBuckets(ids, brightness, options);
            } finally {
                if (uv) Aero_MeshBonePageRenderer2.popUvMatrix();
            }
            return;
        }

        float uOff   = pose != null ? pose.uOffset : 0f;
        float vOff   = pose != null ? pose.vOffset : 0f;
        float uScale = pose != null ? pose.uScale  : 1f;
        float vScale = pose != null ? pose.vScale  : 1f;
        Aero_MeshGeometryRenderer.drawGroups(tess, groups, invSc, brightness, options, uOff, vOff, uScale, vScale);
    }

static void callPageBuckets(int[] ids, float brightness, Aero_RenderOptions options) {
        for (int g = 0; g < 4; g++) {
            int id = ids[g];
            if (id == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            GL11.glColor4f(bright * options.tintR, bright * options.tintG,
                           bright * options.tintB, options.alpha);
            GL11.glCallList(id);
        }
    }

static boolean pushUvMatrix(Aero_BoneRenderPose pose) {
        if (pose == null || pose.uvIsIdentity()) return false;
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glTranslatef(pose.uOffset, pose.vOffset, 0f);
        GL11.glScalef(pose.uScale, pose.vScale, 1f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        return true;
    }

static void popUvMatrix() {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

static int[] pageFor(Aero_BonePageLists pages, int index) {
        return pages.bonePages != null && index < pages.bonePages.length
            ? pages.bonePages[index]
            : null;
    }

static boolean eligibleForBonePage(float[][][] groups) {
        int n = Aero_MeshBonePageRenderer2.triangleCount(groups);
        return n > 0 && n >= BONE_PAGES_MIN_TRIS;
    }

static int triangleCount(float[][][] groups) {
        int n = 0;
        for (int g = 0; g < 4; g++) n += groups[g].length;
        return n;
    }

static boolean hasTriangles(float[][][] groups) {
        for (int g = 0; g < 4; g++) {
            if (groups[g].length > 0) return true;
        }
        return false;
    }

static boolean hasPages(int[] ids) {
        if (ids == null) return false;
        for (int g = 0; g < ids.length; g++) {
            if (ids[g] != 0) return true;
        }
        return false;
    }

static void deleteBonePageLists(Aero_BonePageLists pages) {
        if (pages == null) return;
        Aero_MeshBonePageRenderer2.deletePageIds(pages.staticPages);
        Aero_MeshBonePageRenderer2.deleteBonePageArrays(pages.bonePages);
    }

static void deleteBonePageArrays(int[][] pages) {
        if (pages == null) return;
        for (int i = 0; i < pages.length; i++) {
            Aero_MeshBonePageRenderer2.deletePageIds(pages[i]);
            pages[i] = null;
        }
    }

static void deletePageIds(int[] ids) {
        if (ids == null) return;
        for (int g = 0; g < ids.length; g++) {
            if (ids[g] != 0) {
                Aero_DisplayListBudget.glDeleteList(ids[g]);
                ids[g] = 0;
            }
        }
    }
}
