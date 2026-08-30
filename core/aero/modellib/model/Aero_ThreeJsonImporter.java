package aero.modellib.model;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts parsed Three.js scene values into Aero mesh groups. */
final class Aero_ThreeJsonImporter {
    private final String name;
    private final Map geometries = new LinkedHashMap();
    private final List[] staticGroups = Aero_ThreeJsonGeometry.emptyLists();
    private final Map namedGroups = new LinkedHashMap();
    private int triangleCount;

    private Aero_ThreeJsonImporter(String name) { this.name = name; }

    static Aero_MeshModel parse(Map root, String name) {
        return new Aero_ThreeJsonImporter(name).parse(root);
    }

    private Aero_MeshModel parse(Map root) {
        Map object = Aero_ThreeJsonGeometry.map(root.get("object"));
        if (object == null && root.get("data") instanceof Map) {
            geometries.put("$root", root);
            object = new LinkedHashMap();
            object.put("geometry", "$root");
        } else {
            indexGeometries(Aero_ThreeJsonGeometry.list(root.get("geometries")));
        }
        if (object == null) Aero_ThreeJsonGeometry.fail(
            "expected Object/Scene JSON or BufferGeometry JSON");
        appendObject(object, Aero_ThreeJsonGeometry.identity(), null);
        if (triangleCount == 0) Aero_ThreeJsonGeometry.fail("no triangles found in " + name);
        return new Aero_MeshModel(name, Aero_ThreeJsonGeometry.arrays(staticGroups),
            1.0f, namedArrays());
    }

    private void indexGeometries(List values) {
        if (values == null) return;
        for (int index = 0; index < values.size(); index++) {
            Map geometry = Aero_ThreeJsonGeometry.map(values.get(index));
            if (geometry == null || !(geometry.get("uuid") instanceof String))
                Aero_ThreeJsonGeometry.fail("geometry " + index + " has no uuid");
            geometries.put(geometry.get("uuid"), geometry);
        }
    }

    private void appendObject(Map object, double[] parent, String inheritedName) {
        if (Boolean.FALSE.equals(object.get("visible"))) return;
        double[] world = Aero_ThreeJsonGeometry.multiply(parent,
            Aero_ThreeJsonGeometry.localMatrix(object));
        String objectName = Aero_ThreeJsonGeometry.string(object.get("name"));
        String groupName = objectName.length() == 0 ? inheritedName : objectName;
        Object geometryId = object.get("geometry");
        if (geometryId instanceof String) {
            String type = Aero_ThreeJsonGeometry.string(object.get("type"));
            if (type.length() > 0 && !"Mesh".equals(type))
                Aero_ThreeJsonGeometry.fail("object type " + type + " is not supported");
            appendGeometry((String) geometryId, world, groupName);
        }
        List children = Aero_ThreeJsonGeometry.list(object.get("children"));
        if (children == null) return;
        for (int index = 0; index < children.size(); index++) {
            Map child = Aero_ThreeJsonGeometry.map(children.get(index));
            if (child == null) Aero_ThreeJsonGeometry.fail(
                "child " + index + " must be an object");
            appendObject(child, world, groupName);
        }
    }

    private void appendGeometry(String id, double[] matrix, String groupName) {
        Map geometry = Aero_ThreeJsonGeometry.map(geometries.get(id));
        if (geometry == null) Aero_ThreeJsonGeometry.fail("unknown geometry " + id);
        Map data = Aero_ThreeJsonGeometry.map(geometry.get("data"));
        Map attributes = data == null ? null : Aero_ThreeJsonGeometry.map(data.get("attributes"));
        List xyz = positionValues(id, geometry, attributes);
        List uv = uvValues(id, attributes, xyz.size() / 3);
        List indices = indexValues(data);
        int count = indices == null ? xyz.size() / 3 : indices.size();
        if (count % 3 != 0) Aero_ThreeJsonGeometry.fail(
            "geometry " + id + " is not triangle-aligned");
        appendTriangles(target(groupName), xyz, uv, indices, count, matrix);
    }

    private List positionValues(String id, Map geometry, Map attributes) {
        Map positions = attributes == null ? null
            : Aero_ThreeJsonGeometry.map(attributes.get("position"));
        if (positions == null) Aero_ThreeJsonGeometry.fail("geometry " + id + " ("
            + Aero_ThreeJsonGeometry.string(geometry.get("type")) + ") is not baked BufferGeometry");
        if (Boolean.TRUE.equals(positions.get("normalized")))
            Aero_ThreeJsonGeometry.fail("geometry " + id + " has normalized positions");
        List xyz = Aero_ThreeJsonGeometry.list(positions.get("array"));
        if (Aero_ThreeJsonGeometry.number(positions.get("itemSize"), 3) != 3
                || xyz == null || xyz.size() % 3 != 0)
            Aero_ThreeJsonGeometry.fail("geometry " + id + " has invalid position attribute");
        return xyz;
    }

    private List uvValues(String id, Map attributes, int vertexCount) {
        Map uvAttribute = Aero_ThreeJsonGeometry.map(attributes.get("uv"));
        List uv = uvAttribute == null ? null : Aero_ThreeJsonGeometry.list(uvAttribute.get("array"));
        if (uvAttribute != null && Boolean.TRUE.equals(uvAttribute.get("normalized")))
            Aero_ThreeJsonGeometry.fail("geometry " + id + " has normalized uvs");
        if (uv != null && (Aero_ThreeJsonGeometry.number(uvAttribute.get("itemSize"), 2) != 2
                || uv.size() / 2 < vertexCount))
            Aero_ThreeJsonGeometry.fail("geometry " + id + " has invalid uv attribute");
        return uv;
    }

    private List indexValues(Map data) {
        Map indexAttribute = Aero_ThreeJsonGeometry.map(data.get("index"));
        return indexAttribute == null ? null
            : Aero_ThreeJsonGeometry.list(indexAttribute.get("array"));
    }

    private void appendTriangles(List[] target, List xyz, List uv, List indices,
            int count, double[] matrix) {
        boolean mirrored = Aero_ThreeJsonGeometry.determinant(matrix) < 0.0d;
        for (int offset = 0; offset < count; offset += 3) {
            int a = Aero_ThreeJsonGeometry.vertexIndex(indices, offset, xyz.size() / 3);
            int b = Aero_ThreeJsonGeometry.vertexIndex(indices, offset + 1, xyz.size() / 3);
            int c = Aero_ThreeJsonGeometry.vertexIndex(indices, offset + 2, xyz.size() / 3);
            if (mirrored) { int swap = b; b = c; c = swap; }
            addTriangle(target, Aero_ThreeJsonGeometry.vertex(xyz, uv, a, matrix),
                Aero_ThreeJsonGeometry.vertex(xyz, uv, b, matrix),
                Aero_ThreeJsonGeometry.vertex(xyz, uv, c, matrix));
        }
    }

    private List[] target(String groupName) {
        if (groupName == null || groupName.length() == 0) return staticGroups;
        List[] target = (List[]) namedGroups.get(groupName);
        if (target == null) {
            target = Aero_ThreeJsonGeometry.emptyLists();
            namedGroups.put(groupName, target);
        }
        return target;
    }

    private void addTriangle(List[] target, float[] a, float[] b, float[] c) {
        float abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        float acx = c[0] - a[0], acy = c[1] - a[1], acz = c[2] - a[2];
        int group = Aero_ObjLoader.brightnessGroup(aby * acz - abz * acy,
            abz * acx - abx * acz, abx * acy - aby * acx);
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
            result.put(entry.getKey(), Aero_ThreeJsonGeometry.arrays((List[]) entry.getValue()));
        }
        return result;
    }
}
