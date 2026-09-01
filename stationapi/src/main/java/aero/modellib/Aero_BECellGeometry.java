package aero.modellib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.entity.BlockEntity;
import org.lwjgl.opengl.GL11;

import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.optimization.OptimizationRef;
import aero.modellib.render.Aero_CellRenderableBE;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.util.Aero_PerfConfig;
import aero.modellib.util.Aero_Profiler;

/**
 * At-rest BlockEntity cell pages. Renderers can queue static/LOD-overflow
 * meshes here instead of drawing each BE immediately; the flush compiles one
 * small display-list page per visible cell/render key and replays that page
 * while preserving the existing direct-render fallback.
 */
@OptimizationRef({"aero.render.be-cell-pages", "aero.render.be-cell-page-flattening"})
final class Aero_BECellGeometry extends Aero_BECellRenderState {
    private Aero_BECellGeometry() {}

static void emitModelBucketFlattened(Aero_MeshModel model, int bucket,
                                                 double ox, double oy, double oz,
                                                 float rotation) {
        float invSc = model.invScale;
        Aero_BECellGeometry.emitTrisFlattened(model.groups[bucket], invSc, ox, oy, oz, rotation);
        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int i = 0; i < entries.length; i++) {
            Aero_BECellGeometry.emitTrisFlattened(entries[i].tris[bucket], invSc, ox, oy, oz, rotation);
        }
    }

static void emitTrisFlattened(float[][] tris, float invSc,
                                          double ox, double oy, double oz,
                                          float rotation) {
        if (tris.length == 0) return;
        double radians = Math.toRadians(rotation);
        double sin = rotation != 0.0f ? Math.sin(radians) : 0.0d;
        double cos = rotation != 0.0f ? Math.cos(radians) : 1.0d;
        for (int i = 0; i < tris.length; i++) {
            float[] t = tris[i];
            Aero_BECellGeometry.emitVertexFlattened(t[0] * invSc, t[1] * invSc, t[2] * invSc,
                t[3], t[4], ox, oy, oz, rotation, sin, cos);
            Aero_BECellGeometry.emitVertexFlattened(t[5] * invSc, t[6] * invSc, t[7] * invSc,
                t[8], t[9], ox, oy, oz, rotation, sin, cos);
            Aero_BECellGeometry.emitVertexFlattened(t[10] * invSc, t[11] * invSc, t[12] * invSc,
                t[13], t[14], ox, oy, oz, rotation, sin, cos);
        }
    }

static void emitVertexFlattened(double x, double y, double z,
                                            float u, float v,
                                            double ox, double oy, double oz,
                                            float rotation, double sin, double cos) {
        double rx = x;
        double rz = z;
        if (rotation != 0.0f) {
            double dx = x - 0.5d;
            double dz = z - 0.5d;
            rx = dx * cos + dz * sin + 0.5d;
            rz = -dx * sin + dz * cos + 0.5d;
        }
        GL11.glTexCoord2f(u, v);
        // Match the float vertices used by the normal nested display-list path.
        // Keeping doubles here changes coplanar depth outcomes after flattening.
        GL11.glVertex3f((float) (ox + rx), (float) (oy + y), (float) (oz + rz));
    }

static float pageKeyBrightness(float brightness) {
        if (PER_INSTANCE_LIGHT) return 1.0f;
        if (LIGHT_BUCKETS <= 1) return brightness;
        int max = LIGHT_BUCKETS - 1;
        int bucket = Math.round(brightness * max);
        if (bucket < 0) bucket = 0;
        if (bucket > max) bucket = max;
        return bucket / (float) max;
    }

static int floorToInt(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

static long stableSortKey(double worldX, double worldY, double worldZ,
                                      int identityHash) {
        long x = Aero_BECellGeometry.floorToInt(worldX * 16.0d) & 0x1FFFFFL;
        long y = Aero_BECellGeometry.floorToInt(worldY * 16.0d) & 0x1FFFFFL;
        long z = Aero_BECellGeometry.floorToInt(worldZ * 16.0d) & 0x1FFFFFL;
        return (x << 43) ^ (z << 22) ^ (y << 1) ^ (identityHash & 1);
    }

static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
