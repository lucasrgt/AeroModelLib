package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.render.Aero_SmoothLightCache;

/**
 * Smooth-light draw path (StationAPI/Yarn port).
 *
 * <p>Brightness resolution is split from vertex emission: {@code resolve}
 * samples one world column per footprint cell and bilinearly blends one
 * brightness per triangle into a flat array; {@code emit} replays that array
 * through the Tessellator. The split keeps the GL stream identical to the
 * historical single-pass loop while letting {@link Aero_SmoothLightCache}
 * (opt-in) skip the resolve step entirely for instances whose resolved
 * brightness is still fresh.
 */
@aero.modellib.optimization.OptimizationRef({
    "aero.render.smooth-light-cache", "aero.render.smooth-light-resolved-cache"})
final class Aero_MeshSmoothLightRenderer {

    // Reusable scratch buffers — render thread is single-threaded in Beta 1.7.3.
    private static float[] LIGHT_CACHE = new float[64];
    private static float[] RESOLVED_SCRATCH = new float[256];

    private Aero_MeshSmoothLightRenderer() {}

    static void drawGroupsSmooth(Tessellator tess, float[][][] groups, float invSc,
                                 Aero_MeshModel.SmoothLightData light,
                                 World world, int ox, int topY, int oz,
                                 Aero_RenderOptions options) {
        if (!light.hasTriangles) return;
        int size = groups[0].length + groups[1].length + groups[2].length + groups[3].length;
        float[] resolved;
        if (Aero_SmoothLightCache.ENABLED) {
            long now = System.nanoTime();
            resolved = Aero_SmoothLightCache.cached(world, groups, ox, topY, oz, size, now);
            if (resolved == null) {
                resolved = Aero_SmoothLightCache.claim(world, groups, ox, topY, oz, size, now);
                resolve(groups, light, world, ox, topY, oz, resolved);
            }
        } else {
            if (RESOLVED_SCRATCH.length < size) RESOLVED_SCRATCH = new float[size];
            resolved = RESOLVED_SCRATCH;
            resolve(groups, light, world, ox, topY, oz, resolved);
        }
        emit(tess, groups, invSc, options, resolved);
    }

    /** Samples the footprint light grid and resolves one brightness per triangle. */
    private static void resolve(float[][][] groups, Aero_MeshModel.SmoothLightData light,
                                World world, int ox, int topY, int oz, float[] resolved) {
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

        int cursor = 0;
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float factor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            float[] centroidX = light.centroidX[g];
            float[] centroidZ = light.centroidZ[g];
            for (int i = 0; i < tris.length; i++) {
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
                resolved[cursor++] = Aero_MeshGeometryRenderer.lerp(
                    Aero_MeshGeometryRenderer.lerp(b00, b10, tx),
                    Aero_MeshGeometryRenderer.lerp(b01, b11, tx), tz) * factor;
            }
        }
    }

    /** Emits every triangle with its resolved brightness (same GL stream as before). */
    private static void emit(Tessellator tess, float[][][] groups, float invSc,
                             Aero_RenderOptions options, float[] resolved) {
        tess.start(GL11.GL_TRIANGLES);
        int cursor = 0;
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            for (int i = 0; i < tris.length; i++) {
                float bright = resolved[cursor++];
                tess.color(bright * options.tintR, bright * options.tintG,
                    bright * options.tintB, options.alpha);
                float[] t = tris[i];
                tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }
}
