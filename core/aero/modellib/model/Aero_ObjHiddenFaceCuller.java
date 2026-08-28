package aero.modellib.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

/** Removes exact coincident opposite-facing triangle pairs inside one OBJ group. */
@aero.modellib.optimization.OptimizationRef({"aero.model.hidden-face-cull"})
final class Aero_ObjHiddenFaceCuller {
    private static final int GRID =
        Math.max(1, Integer.getInteger("aero.obj.cullhidden.grid", 4096).intValue());

    private Aero_ObjHiddenFaceCuller() {}

    static void cull(List[] groups) {
        HashMap byFace = new HashMap();
        for (int group = 0; group < groups.length; group++) {
            List triangles = groups[group];
            for (int index = 0; index < triangles.size(); index++) {
                float[] triangle = (float[]) triangles.get(index);
                FaceKey key = new FaceKey(triangle);
                ArrayList references = (ArrayList) byFace.get(key);
                if (references == null) {
                    references = new ArrayList(2);
                    byFace.put(key, references);
                }
                references.add(new TriangleRef(triangle));
            }
        }
        IdentityHashMap removed = oppositePairs(byFace);
        if (removed.isEmpty()) return;
        for (int group = 0; group < groups.length; group++) {
            Iterator triangles = groups[group].iterator();
            while (triangles.hasNext()) if (removed.containsKey(triangles.next())) triangles.remove();
        }
    }

    private static IdentityHashMap oppositePairs(HashMap byFace) {
        IdentityHashMap removed = new IdentityHashMap();
        Iterator buckets = byFace.values().iterator();
        while (buckets.hasNext()) {
            ArrayList references = (ArrayList) buckets.next();
            boolean[] consumed = new boolean[references.size()];
            for (int left = 0; left < references.size(); left++) {
                if (consumed[left]) continue;
                TriangleRef a = (TriangleRef) references.get(left);
                for (int right = left + 1; right < references.size(); right++) {
                    if (consumed[right]) continue;
                    TriangleRef b = (TriangleRef) references.get(right);
                    if (!a.opposes(b)) continue;
                    consumed[left] = consumed[right] = true;
                    removed.put(a.triangle, Boolean.TRUE);
                    removed.put(b.triangle, Boolean.TRUE);
                    break;
                }
            }
        }
        return removed;
    }

    private static final class TriangleRef {
        final float[] triangle;
        final float x;
        final float y;
        final float z;

        TriangleRef(float[] triangle) {
            this.triangle = triangle;
            float ax = triangle[5] - triangle[0], ay = triangle[6] - triangle[1];
            float az = triangle[7] - triangle[2], bx = triangle[10] - triangle[0];
            float by = triangle[11] - triangle[1], bz = triangle[12] - triangle[2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1e-7f) { nx /= length; ny /= length; nz /= length; }
            x = nx; y = ny; z = nz;
        }

        boolean opposes(TriangleRef other) { return x * other.x + y * other.y + z * other.z < -0.98f; }
    }

    private static final class FaceKey {
        final long[] coordinates = new long[9];
        final int hash;

        FaceKey(float[] triangle) {
            for (int vertex = 0; vertex < 3; vertex++) {
                int source = vertex * 5, target = vertex * 3;
                coordinates[target] = quantize(triangle[source]);
                coordinates[target + 1] = quantize(triangle[source + 1]);
                coordinates[target + 2] = quantize(triangle[source + 2]);
            }
            sortVertices();
            int value = 1;
            for (int index = 0; index < coordinates.length; index++)
                value = 31 * value + (int) (coordinates[index] ^ (coordinates[index] >>> 32));
            hash = value;
        }

        public int hashCode() { return hash; }

        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof FaceKey)) return false;
            FaceKey other = (FaceKey) object;
            for (int index = 0; index < coordinates.length; index++)
                if (coordinates[index] != other.coordinates[index]) return false;
            return true;
        }

        private long quantize(float value) { return Math.round(value * GRID); }

        private void sortVertices() {
            for (int left = 0; left < 2; left++)
                for (int right = left + 1; right < 3; right++)
                    if (compare(right, left) < 0) swap(left, right);
        }

        private int compare(int left, int right) {
            int a = left * 3, b = right * 3;
            for (int axis = 0; axis < 3; axis++) {
                if (coordinates[a + axis] < coordinates[b + axis]) return -1;
                if (coordinates[a + axis] > coordinates[b + axis]) return 1;
            }
            return 0;
        }

        private void swap(int left, int right) {
            int a = left * 3, b = right * 3;
            for (int axis = 0; axis < 3; axis++) {
                long value = coordinates[a + axis];
                coordinates[a + axis] = coordinates[b + axis];
                coordinates[b + axis] = value;
            }
        }
    }
}
