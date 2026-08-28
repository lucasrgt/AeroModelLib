package aero.modellib.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Numeric and collection helpers for the Three.js importer. */
final class Aero_ThreeJsonGeometry {
    private static final double[] IDENTITY = {
        1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1
    };

    private Aero_ThreeJsonGeometry() {}

    static double[] identity() { return IDENTITY; }

    static float[] vertex(List xyz, List uv, int index, double[] matrix) {
        double x = value(xyz, index * 3);
        double y = value(xyz, index * 3 + 1);
        double z = value(xyz, index * 3 + 2);
        float tx = (float) (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]);
        float ty = (float) (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]);
        float tz = (float) (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]);
        float u = uv == null ? 0f : (float) value(uv, index * 2);
        float v = uv == null ? 0f : 1f - (float) value(uv, index * 2 + 1);
        return new float[]{tx, ty, tz, u, v};
    }

    static int vertexIndex(List indices, int offset, int vertexCount) {
        double encoded = indices == null ? offset : value(indices, offset);
        int index = (int) encoded;
        if (encoded != index) fail("vertex index must be an integer: " + encoded);
        if (index < 0 || index >= vertexCount) fail("vertex index out of bounds: " + index);
        return index;
    }

    static double determinant(double[] matrix) {
        return matrix[0] * (matrix[5] * matrix[10] - matrix[9] * matrix[6])
            - matrix[4] * (matrix[1] * matrix[10] - matrix[9] * matrix[2])
            + matrix[8] * (matrix[1] * matrix[6] - matrix[5] * matrix[2]);
    }

    static double[] localMatrix(Map object) {
        List matrix = list(object.get("matrix"));
        if (matrix == null) return IDENTITY;
        if (matrix.size() != 16) fail("object matrix must contain 16 numbers");
        double[] result = new double[16];
        for (int index = 0; index < 16; index++) result[index] = value(matrix, index);
        return result;
    }

    static double[] multiply(double[] left, double[] right) {
        double[] result = new double[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                double sum = 0;
                for (int index = 0; index < 4; index++)
                    sum += left[index * 4 + row] * right[column * 4 + index];
                result[column * 4 + row] = sum;
            }
        }
        return result;
    }

    static List[] emptyLists() {
        return new List[]{new ArrayList(), new ArrayList(), new ArrayList(), new ArrayList()};
    }

    static float[][][] arrays(List[] groups) {
        float[][][] result = new float[4][][];
        for (int index = 0; index < 4; index++)
            result[index] = (float[][]) groups[index].toArray(new float[groups[index].size()][]);
        return result;
    }

    static Map map(Object value) { return value instanceof Map ? (Map) value : null; }
    static List list(Object value) { return value instanceof List ? (List) value : null; }
    static String string(Object value) { return value instanceof String ? (String) value : ""; }
    static int number(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static double value(List values, int index) {
        Object value = values.get(index);
        if (!(value instanceof Number)) fail("array value " + index + " must be numeric");
        return ((Number) value).doubleValue();
    }

    static void fail(String message) {
        throw new RuntimeException("AeroThreeJsonLoader: " + message);
    }
}
