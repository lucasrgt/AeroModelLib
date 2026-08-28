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
final class Aero_MeshGeometryRenderer extends Aero_MeshRendererState {
    private Aero_MeshGeometryRenderer() {}

static void drawGroupsForInventory(Tessellator tess, float[][][] groups, float invSc) {
        Aero_MeshGeometryRenderer.drawGroups(tess, groups, invSc, 1.0f, Aero_RenderOptions.DEFAULT);
    }

static void drawGroupsMorph(Tessellator tess, Aero_MeshModel model,
                                         float brightness, Aero_RenderOptions options,
                                         Aero_MorphState morphState) {
        java.util.Map weights = morphState.getWeightsView();
        int upperBound = weights.size();
        if (SCRATCH_MORPH_TARGETS.length < upperBound) {
            SCRATCH_MORPH_TARGETS = new Aero_MorphTarget[upperBound];
            SCRATCH_MORPH_WEIGHTS = new float[upperBound];
        }
        Aero_MorphTarget[] activeTargets = SCRATCH_MORPH_TARGETS;
        float[] activeWeights = SCRATCH_MORPH_WEIGHTS;
        int activeCount = 0;
        java.util.Iterator it = weights.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry e = (java.util.Map.Entry) it.next();
            float w = ((Float) e.getValue()).floatValue();
            if (w == 0f) continue;
            Aero_MorphTarget target = model.getMorphTarget((String) e.getKey());
            if (target == null) continue;
            activeTargets[activeCount] = target;
            activeWeights[activeCount] = w;
            activeCount++;
        }
        if (activeCount == 0) {
            Aero_MeshGeometryRenderer.drawGroups(tess, model.groups, model.invScale, brightness, options);
            return;
        }

        float invSc = model.invScale;
        tess.start(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = model.groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.color(bright * options.tintR, bright * options.tintG,
                bright * options.tintB, options.alpha);
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                float v0dx = 0f, v0dy = 0f, v0dz = 0f;
                float v1dx = 0f, v1dy = 0f, v1dz = 0f;
                float v2dx = 0f, v2dy = 0f, v2dz = 0f;
                for (int a = 0; a < activeCount; a++) {
                    float[] td = activeTargets[a].deltas[g][i];
                    float w = activeWeights[a];
                    v0dx += td[0] * w; v0dy += td[1] * w; v0dz += td[2] * w;
                    v1dx += td[3] * w; v1dy += td[4] * w; v1dz += td[5] * w;
                    v2dx += td[6] * w; v2dy += td[7] * w; v2dz += td[8] * w;
                }
                tess.vertex((t[0]  + v0dx) * invSc, (t[1]  + v0dy) * invSc, (t[2]  + v0dz) * invSc, t[3],  t[4]);
                tess.vertex((t[5]  + v1dx) * invSc, (t[6]  + v1dy) * invSc, (t[7]  + v1dz) * invSc, t[8],  t[9]);
                tess.vertex((t[10] + v2dx) * invSc, (t[11] + v2dy) * invSc, (t[12] + v2dz) * invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

static void drawGroups(Tessellator tess, float[][][] groups, float invSc,
                                   float brightness, Aero_RenderOptions options) {
        // Identity UV transform — fast path that emits raw u/v with no per-vertex math.
        Aero_MeshGeometryRenderer.drawGroups(tess, groups, invSc, brightness, options, 0f, 0f, 1f, 1f);
    }

static void drawGroups(Tessellator tess, float[][][] groups, float invSc,
                                   float brightness, Aero_RenderOptions options,
                                   float uOff, float vOff, float uScale, float vScale) {
        boolean uvIdentity = uOff == 0f && vOff == 0f && uScale == 1f && vScale == 1f;
        tess.start(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.color(bright * options.tintR, bright * options.tintG, bright * options.tintB, options.alpha);
            if (uvIdentity) {
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                    tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                    tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
                }
            } else {
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,
                        t[3] *uScale + uOff,  t[4] *vScale + vOff);
                    tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,
                        t[8] *uScale + uOff,  t[9] *vScale + vOff);
                    tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc,
                        t[13]*uScale + uOff, t[14]*vScale + vOff);
                }
            }
        }
        tess.draw();
    }

static void drawGroupsSmooth(Tessellator tess, float[][][] groups, float invSc,
                                          Aero_MeshModel.SmoothLightData light,
                                          World world, int ox, int topY, int oz,
                                          Aero_RenderOptions options) {
        if (!light.hasTriangles) return;

        int xLo = Aero_MeshGeometryRenderer.fastFloor(ox + light.minX);
        int xHi = Aero_MeshGeometryRenderer.fastFloor(ox + light.maxX) + 1;
        int zLo = Aero_MeshGeometryRenderer.fastFloor(oz + light.minZ);
        int zHi = Aero_MeshGeometryRenderer.fastFloor(oz + light.maxZ) + 1;
        int w = xHi - xLo + 1;
        int h = zHi - zLo + 1;

        int needed = w * h;
        if (LIGHT_CACHE.length < needed) LIGHT_CACHE = new float[needed];
        float[] cache = LIGHT_CACHE;
        for (int zi = 0; zi < h; zi++) {
            int row = zi * w;
            int wz = zLo + zi;
            for (int xi = 0; xi < w; xi++) {
                // method_1782 is the float-brightness equivalent of vanilla
                // getLightBrightness(int,int,int). Yarn for Beta 1.7.3 hasn't
                // assigned a human name yet (still raw intermediary). Update
                // when biny mappings give it a real name.
                cache[row + xi] = world.method_1782(xLo + xi, topY, wz);
            }
        }

        tess.start(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float factor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            float[] centroidX = light.centroidX[g];
            float[] centroidZ = light.centroidZ[g];
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                float wx = ox + centroidX[i];
                float wz = oz + centroidZ[i];
                int x0i = Aero_MeshGeometryRenderer.fastFloor(wx);
                int z0i = Aero_MeshGeometryRenderer.fastFloor(wz);
                float tx = wx - x0i, tz = wz - z0i;
                int cx = x0i - xLo;
                int cz = z0i - zLo;
                int row0 = cz * w;
                int row1 = row0 + w;
                float b00 = cache[row0 + cx];
                float b10 = cache[row0 + cx + 1];
                float b01 = cache[row1 + cx];
                float b11 = cache[row1 + cx + 1];
                float bright = Aero_MeshGeometryRenderer.lerp(Aero_MeshGeometryRenderer.lerp(b00, b10, tx), Aero_MeshGeometryRenderer.lerp(b01, b11, tx), tz) * factor;
                tess.color(bright * options.tintR, bright * options.tintG, bright * options.tintB, options.alpha);
                tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

static float lerp(float a, float b, float t) { return a + (b - a) * t; }

static int fastFloor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
