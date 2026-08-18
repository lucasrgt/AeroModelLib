package aero.modellib.model;

/** Computes immutable geometry metadata once for mesh-model caches. */
final class Aero_MeshGeometryMetadata {
    private Aero_MeshGeometryMetadata() {}

    static int triangleCount(float[][][] groups) {
        if (groups == null) return 0;
        int result = 0;
        for (int group = 0; group < groups.length; group++) result += groups[group].length;
        return result;
    }

    static float[] bounds(float[][][] groups, Aero_MeshModel.NamedGroup[] named, float scale) {
        float[] result = new float[] {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        include(groups, scale, result);
        for (int index = 0; index < named.length; index++) include(named[index].tris, scale, result);
        if (result[0] == Float.POSITIVE_INFINITY)
            return new float[] {0f, 0f, 0f, 1f, 1f, 1f};
        return result;
    }

    private static void include(float[][][] groups, float scale, float[] bounds) {
        for (int group = 0; group < groups.length; group++) {
            float[][] triangles = groups[group];
            for (int index = 0; index < triangles.length; index++) {
                float[] triangle = triangles[index];
                for (int vertex = 0; vertex < 3; vertex++) {
                    int base = vertex * 5;
                    include(triangle[base] * scale, triangle[base + 1] * scale,
                        triangle[base + 2] * scale, bounds);
                }
            }
        }
    }

    private static void include(float x, float y, float z, float[] bounds) {
        if (x < bounds[0]) bounds[0] = x;
        if (y < bounds[1]) bounds[1] = y;
        if (z < bounds[2]) bounds[2] = z;
        if (x > bounds[3]) bounds[3] = x;
        if (y > bounds[4]) bounds[4] = y;
        if (z > bounds[5]) bounds[5] = z;
    }

    static Aero_MeshModel.SmoothLightData smoothLight(float[][][] groups, float scale) {
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        float[][] centroidX = new float[4][];
        float[][] centroidZ = new float[4][];
        boolean populated = false;
        for (int group = 0; group < 4; group++) {
            float[][] triangles = groups[group];
            centroidX[group] = new float[triangles.length];
            centroidZ[group] = new float[triangles.length];
            for (int index = 0; index < triangles.length; index++) {
                float[] triangle = triangles[index];
                float x0 = triangle[0] * scale, x1 = triangle[5] * scale, x2 = triangle[10] * scale;
                float z0 = triangle[2] * scale, z1 = triangle[7] * scale, z2 = triangle[12] * scale;
                minX = Math.min(minX, Math.min(x0, Math.min(x1, x2)));
                maxX = Math.max(maxX, Math.max(x0, Math.max(x1, x2)));
                minZ = Math.min(minZ, Math.min(z0, Math.min(z1, z2)));
                maxZ = Math.max(maxZ, Math.max(z0, Math.max(z1, z2)));
                centroidX[group][index] = (x0 + x1 + x2) / 3f;
                centroidZ[group][index] = (z0 + z1 + z2) / 3f;
                populated = true;
            }
        }
        if (!populated) minX = maxX = minZ = maxZ = 0f;
        return new Aero_MeshModel.SmoothLightData(
            populated, minX, maxX, minZ, maxZ, centroidX, centroidZ);
    }
}
