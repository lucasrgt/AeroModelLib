package aero.modellib.model;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import aero.modellib.util.Aero_PerfConfig;

/** Loads Three.js Object/Scene JSON with baked BufferGeometry data. */
public final class Aero_ThreeJsonLoader {
    private static final int MAX_CACHE_ENTRIES =
        Aero_PerfConfig.intProperty("aero.modellib.cache.maxEntries",
            512, -1, -1, Integer.MAX_VALUE);
    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
    };
    private static final double[] IDENTITY = {
        1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1
    };

    private Aero_ThreeJsonLoader() {}

    public static synchronized Aero_MeshModel load(String resourcePath) {
        return load(resourcePath, resourcePath);
    }

    public static synchronized Aero_MeshModel load(String resourcePath, String name) {
        Aero_MeshModel cached = (Aero_MeshModel) cache.get(resourcePath);
        if (cached != null) return cached;
        try {
            InputStream input = Aero_ThreeJsonLoader.class.getResourceAsStream(resourcePath);
            if (input == null) fail("resource not found: " + resourcePath);
            String json;
            try {
                json = readUtf8(input);
            } finally {
                input.close();
            }
            Aero_MeshModel model = parse(json, name);
            cache.put(resourcePath, model);
            return model;
        } catch (Exception e) {
            throw new RuntimeException("AeroThreeJsonLoader: failed to load "
                + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /** Parses Three.js JSON already held in memory. Useful for tools and tests. */
    public static Aero_MeshModel parse(String json, String name) {
        try {
            Object value = Aero_JsonValueParser.parse(json);
            if (!(value instanceof Map)) fail("root must be an object");
            return new Importer(name).parse((Map) value);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("AeroThreeJsonLoader:")) {
                throw e;
            }
            throw new RuntimeException("AeroThreeJsonLoader: failed to parse "
                + name + ": " + e.getMessage(), e);
        }
    }

    public static synchronized void clearCache() {
        cache.clear();
    }

    public static synchronized int cacheSize() {
        return cache.size();
    }

    private static final class Importer {
        private final String name;
        private final Map geometries = new LinkedHashMap();
        private final List[] staticGroups = emptyLists();
        private final Map namedGroups = new LinkedHashMap();
        private int triangleCount;

        Importer(String name) {
            this.name = name;
        }

        Aero_MeshModel parse(Map root) {
            Map object = map(root.get("object"));
            if (object == null && root.get("data") instanceof Map) {
                geometries.put("$root", root);
                object = new LinkedHashMap();
                object.put("geometry", "$root");
            } else {
                indexGeometries(list(root.get("geometries")));
            }
            if (object == null) fail("expected Object/Scene JSON or BufferGeometry JSON");
            appendObject(object, IDENTITY, null);
            if (triangleCount == 0) fail("no triangles found in " + name);
            return new Aero_MeshModel(name, arrays(staticGroups), 1.0f, namedArrays());
        }

        private void indexGeometries(List values) {
            if (values == null) return;
            for (int i = 0; i < values.size(); i++) {
                Map geometry = map(values.get(i));
                if (geometry == null || !(geometry.get("uuid") instanceof String)) {
                    fail("geometry " + i + " has no uuid");
                }
                geometries.put(geometry.get("uuid"), geometry);
            }
        }

        private void appendObject(Map object, double[] parent, String inheritedName) {
            if (Boolean.FALSE.equals(object.get("visible"))) return;
            double[] world = multiply(parent, localMatrix(object));
            String objectName = string(object.get("name"));
            String groupName = objectName.length() == 0 ? inheritedName : objectName;
            Object geometryId = object.get("geometry");
            if (geometryId instanceof String) {
                String type = string(object.get("type"));
                if (type.length() > 0 && !"Mesh".equals(type)) {
                    fail("object type " + type + " is not supported");
                }
                appendGeometry((String) geometryId, world, groupName);
            }
            List children = list(object.get("children"));
            if (children == null) return;
            for (int i = 0; i < children.size(); i++) {
                Map child = map(children.get(i));
                if (child == null) fail("child " + i + " must be an object");
                appendObject(child, world, groupName);
            }
        }

        private void appendGeometry(String id, double[] matrix, String groupName) {
            Map geometry = map(geometries.get(id));
            if (geometry == null) fail("unknown geometry " + id);
            Map data = map(geometry.get("data"));
            Map attributes = data == null ? null : map(data.get("attributes"));
            Map positions = attributes == null ? null : map(attributes.get("position"));
            if (positions == null) {
                fail("geometry " + id + " (" + string(geometry.get("type"))
                    + ") is not baked BufferGeometry");
            }
            if (Boolean.TRUE.equals(positions.get("normalized"))) {
                fail("geometry " + id + " has normalized positions");
            }
            List xyz = list(positions.get("array"));
            if (number(positions.get("itemSize"), 3) != 3 || xyz == null || xyz.size() % 3 != 0) {
                fail("geometry " + id + " has invalid position attribute");
            }
            Map uvAttribute = map(attributes.get("uv"));
            List uv = uvAttribute == null ? null : list(uvAttribute.get("array"));
            if (uvAttribute != null && Boolean.TRUE.equals(uvAttribute.get("normalized"))) {
                fail("geometry " + id + " has normalized uvs");
            }
            if (uv != null && (number(uvAttribute.get("itemSize"), 2) != 2
                || uv.size() / 2 < xyz.size() / 3)) fail("geometry " + id + " has invalid uv attribute");
            Map indexAttribute = map(data.get("index"));
            List indices = indexAttribute == null ? null : list(indexAttribute.get("array"));
            int count = indices == null ? xyz.size() / 3 : indices.size();
            if (count % 3 != 0) fail("geometry " + id + " is not triangle-aligned");
            List[] target = target(groupName);
            boolean mirrored = determinant(matrix) < 0.0d;
            for (int i = 0; i < count; i += 3) {
                int a = vertexIndex(indices, i, xyz.size() / 3);
                int b = vertexIndex(indices, i + 1, xyz.size() / 3);
                int c = vertexIndex(indices, i + 2, xyz.size() / 3);
                if (mirrored) { int swap = b; b = c; c = swap; }
                addTriangle(target, vertex(xyz, uv, a, matrix),
                    vertex(xyz, uv, b, matrix), vertex(xyz, uv, c, matrix));
            }
        }

        private List[] target(String groupName) {
            if (groupName == null || groupName.length() == 0) return staticGroups;
            List[] target = (List[]) namedGroups.get(groupName);
            if (target == null) {
                target = emptyLists();
                namedGroups.put(groupName, target);
            }
            return target;
        }

        private void addTriangle(List[] target, float[] a, float[] b, float[] c) {
            float abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
            float acx = c[0] - a[0], acy = c[1] - a[1], acz = c[2] - a[2];
            float nx = aby * acz - abz * acy;
            float ny = abz * acx - abx * acz;
            float nz = abx * acy - aby * acx;
            int group = Aero_ObjLoader.brightnessGroup(nx, ny, nz);
            target[group].add(new float[]{
                a[0], a[1], a[2], a[3], a[4], b[0], b[1], b[2], b[3], b[4],
                c[0], c[1], c[2], c[3], c[4]
            });
            triangleCount++;
        }

        private Map namedArrays() {
            Map result = new LinkedHashMap();
            Iterator entries = namedGroups.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry entry = (Map.Entry) entries.next();
                result.put(entry.getKey(), arrays((List[]) entry.getValue()));
            }
            return result;
        }
    }

    private static float[] vertex(List xyz, List uv, int index, double[] matrix) {
        double x = value(xyz, index * 3), y = value(xyz, index * 3 + 1), z = value(xyz, index * 3 + 2);
        float tx = (float) (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]);
        float ty = (float) (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]);
        float tz = (float) (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]);
        float u = uv == null ? 0f : (float) value(uv, index * 2);
        float v = uv == null ? 0f : 1f - (float) value(uv, index * 2 + 1);
        return new float[]{tx, ty, tz, u, v};
    }

    private static int vertexIndex(List indices, int offset, int vertexCount) {
        double encoded = indices == null ? offset : value(indices, offset);
        int index = (int) encoded;
        if (encoded != index) fail("vertex index must be an integer: " + encoded);
        if (index < 0 || index >= vertexCount) fail("vertex index out of bounds: " + index);
        return index;
    }

    private static double determinant(double[] m) {
        return m[0] * (m[5] * m[10] - m[9] * m[6])
            - m[4] * (m[1] * m[10] - m[9] * m[2])
            + m[8] * (m[1] * m[6] - m[5] * m[2]);
    }

    private static double[] localMatrix(Map object) {
        List matrix = list(object.get("matrix"));
        if (matrix == null) return IDENTITY;
        if (matrix.size() != 16) fail("object matrix must contain 16 numbers");
        double[] result = new double[16];
        for (int i = 0; i < 16; i++) result[i] = value(matrix, i);
        return result;
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] result = new double[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[column * 4 + k];
                result[column * 4 + row] = sum;
            }
        }
        return result;
    }

    private static List[] emptyLists() {
        return new List[]{new ArrayList(), new ArrayList(), new ArrayList(), new ArrayList()};
    }

    private static float[][][] arrays(List[] groups) {
        float[][][] result = new float[4][][];
        for (int i = 0; i < 4; i++) result[i] = (float[][]) groups[i].toArray(new float[groups[i].size()][]);
        return result;
    }

    private static Map map(Object value) { return value instanceof Map ? (Map) value : null; }
    private static List list(Object value) { return value instanceof List ? (List) value : null; }
    private static String string(Object value) { return value instanceof String ? (String) value : ""; }
    private static int number(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
    private static double value(List values, int index) {
        Object value = values.get(index);
        if (!(value instanceof Number)) fail("array value " + index + " must be numeric");
        return ((Number) value).doubleValue();
    }

    private static String readUtf8(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
        return new String(bytes.toByteArray(), "UTF-8");
    }

    private static void fail(String message) {
        throw new RuntimeException("AeroThreeJsonLoader: " + message);
    }
}
