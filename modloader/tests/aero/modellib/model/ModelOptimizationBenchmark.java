package aero.modellib.model;

import aero.modellib.OptimizationBenchmarkSupport;
import aero.modellib.OptimizationBenchmarkSupport.Work;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Retroactive oracles for immutable-model metadata and render-loop work. */
public final class ModelOptimizationBenchmark {
    private static final int TRIANGLES = 32768;
    private static final Aero_MeshModel MESH = mesh(TRIANGLES, 128);

    private ModelOptimizationBenchmark() {}

    public static void main(String[] args) {
        MESH.getBounds();
        MESH.getStaticSmoothLightData();
        MESH.getNamedGroupArray();
        OptimizationBenchmarkSupport.header();
        OptimizationBenchmarkSupport.compare("aero.model.render-metadata-caches.bounds",
            100, 20000, 1, cachedBounds(), recomputedBounds());
        OptimizationBenchmarkSupport.compare("aero.model.render-metadata-caches.named-groups",
            100, 10000, 128, cachedNamedGroups(), rebuiltNamedGroups());
        OptimizationBenchmarkSupport.compare("aero.render.smooth-light-cache",
            20, 500, TRIANGLES, cachedSmoothLight(), recomputedSmoothLight());
        OptimizationBenchmarkSupport.compare("aero.render.loop-invariant-hoisting",
            20, 300, TRIANGLES * 3, multipliedIdentityUv(), dividedTransformedUv());
        System.out.println("sink=" + OptimizationBenchmarkSupport.sink());
    }

    private static Work cachedBounds() {
        return new Work() { public long run() {
            float[] bounds = MESH.getBounds();
            return checksum(bounds);
        }};
    }

    private static Work recomputedBounds() {
        return new Work() { public long run() {
            float[] bounds = Aero_MeshGeometryMetadata.bounds(
                MESH.groups, MESH.getNamedGroupArray(), MESH.invScale);
            return checksum(bounds);
        }};
    }

    private static Work cachedNamedGroups() {
        return new Work() { public long run() {
            Aero_MeshModel.NamedGroup[] groups = MESH.getNamedGroupArray();
            long sum = 0L;
            for (int i = 0; i < groups.length; i++) sum += groups[i].name.hashCode();
            return sum;
        }};
    }

    private static Work rebuiltNamedGroups() {
        return new Work() { public long run() {
            Aero_MeshModel.NamedGroup[] groups = new Aero_MeshModel.NamedGroup[MESH.namedGroups.size()];
            Iterator iterator = MESH.namedGroups.entrySet().iterator();
            int index = 0;
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                groups[index++] = new Aero_MeshModel.NamedGroup(
                    (String) entry.getKey(), (float[][][]) entry.getValue());
            }
            long sum = 0L;
            for (int i = 0; i < groups.length; i++) sum += groups[i].name.hashCode();
            return sum;
        }};
    }

    private static Work cachedSmoothLight() {
        return new Work() { public long run() {
            return smoothChecksum(MESH.getStaticSmoothLightData());
        }};
    }

    private static Work recomputedSmoothLight() {
        return new Work() { public long run() {
            return smoothChecksum(Aero_MeshGeometryMetadata.smoothLight(MESH.groups, MESH.invScale));
        }};
    }

    private static Work multipliedIdentityUv() {
        return new Work() { public long run() {
            long sum = 0L;
            float inv = MESH.invScale;
            for (int group = 0; group < 4; group++) {
                float[][] tris = MESH.groups[group];
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    for (int vertex = 0; vertex < 3; vertex++) {
                        int offset = vertex * 5;
                        sum += Float.floatToIntBits(t[offset] * inv);
                        sum += Float.floatToIntBits(t[offset + 1] * inv);
                        sum += Float.floatToIntBits(t[offset + 2] * inv);
                        sum += Float.floatToIntBits(t[offset + 3]);
                        sum += Float.floatToIntBits(t[offset + 4]);
                    }
                }
            }
            return sum;
        }};
    }

    private static Work dividedTransformedUv() {
        return new Work() { public long run() {
            long sum = 0L;
            float scale = MESH.scale;
            for (int group = 0; group < 4; group++) {
                float[][] tris = MESH.groups[group];
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    for (int vertex = 0; vertex < 3; vertex++) {
                        int offset = vertex * 5;
                        sum += Float.floatToIntBits(t[offset] / scale);
                        sum += Float.floatToIntBits(t[offset + 1] / scale);
                        sum += Float.floatToIntBits(t[offset + 2] / scale);
                        sum += Float.floatToIntBits(t[offset + 3] * 1f + 0f);
                        sum += Float.floatToIntBits(t[offset + 4] * 1f + 0f);
                    }
                }
            }
            return sum;
        }};
    }

    private static long checksum(float[] values) {
        long sum = 0L;
        for (int i = 0; i < values.length; i++) sum += Float.floatToIntBits(values[i]);
        return sum;
    }

    private static long smoothChecksum(Aero_MeshModel.SmoothLightData data) {
        long sum = Float.floatToIntBits(data.minX) + Float.floatToIntBits(data.maxX)
            + Float.floatToIntBits(data.minZ) + Float.floatToIntBits(data.maxZ);
        for (int group = 0; group < 4; group++) {
            for (int i = 0; i < data.centroidX[group].length; i++) {
                sum += Float.floatToIntBits(data.centroidX[group][i]);
                sum += Float.floatToIntBits(data.centroidZ[group][i]);
            }
        }
        return sum;
    }

    private static Aero_MeshModel mesh(int triangles, int namedCount) {
        float[][][] groups = new float[4][][];
        for (int group = 0; group < 4; group++) {
            groups[group] = new float[triangles / 4][];
            for (int i = 0; i < groups[group].length; i++) {
                float x = i & 255, z = i >>> 8;
                groups[group][i] = triangle(x, group, z);
            }
        }
        Map named = new LinkedHashMap();
        for (int i = 0; i < namedCount; i++) {
            float[][][] part = new float[4][][];
            for (int group = 0; group < 4; group++) part[group] = new float[0][];
            part[i & 3] = new float[][]{triangle(i, i & 3, i)};
            named.put("part_" + i, part);
        }
        return new Aero_MeshModel("bench", groups, 16f, named);
    }

    private static float[] triangle(float x, float y, float z) {
        return new float[]{x, y, z, 0f, 0f, x + 1f, y, z, 1f, 0f,
            x, y + 1f, z + 1f, 0f, 1f};
    }
}
