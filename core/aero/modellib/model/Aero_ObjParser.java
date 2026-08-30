package aero.modellib.model;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stateful OBJ text parser kept separate from resource loading and caching. */
final class Aero_ObjParser {
    private final List verts = new ArrayList();
    private final List uvs = new ArrayList();
    private final List[] staticGroups = newGroups();
    private final Map namedGroups = new LinkedHashMap();
    private List[] currentGroup;

    private Aero_ObjParser() {}

    static Aero_MeshModel parse(BufferedReader reader, String name,
                                boolean cullHiddenFaces) throws Exception {
        Aero_ObjParser parser = new Aero_ObjParser();
        parser.read(reader);
        if (cullHiddenFaces) parser.cullHiddenFaces();
        return parser.build(name);
    }

    private void read(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) parseLine(line.trim());
    }

    private void parseLine(String line) {
        if (line.isEmpty() || line.charAt(0) == '#') return;
        if (line.startsWith("v ")) {
            addVertex(line.substring(2));
            return;
        }
        if (line.startsWith("vt ")) {
            addUv(line.substring(3));
            return;
        }
        if (line.startsWith("f ")) {
            parseFace(line.substring(2).trim(), activeGroups());
            return;
        }
        if (line.startsWith("o ") || line.startsWith("g ")) selectGroup(line.substring(2));
    }

    private void addVertex(String value) {
        String[] parts = split(value);
        verts.add(new float[] {number(parts[0]), number(parts[1]), number(parts[2])});
    }

    private void addUv(String value) {
        String[] parts = split(value);
        uvs.add(new float[] {number(parts[0]), 1.0f - number(parts[1])});
    }

    private List[] activeGroups() {
        return currentGroup != null ? currentGroup : staticGroups;
    }

    private void selectGroup(String value) {
        String name = value.trim();
        if (!namedGroups.containsKey(name)) namedGroups.put(name, newGroups());
        currentGroup = (List[]) namedGroups.get(name);
    }

    private void cullHiddenFaces() {
        Aero_ObjHiddenFaceCuller.cull(staticGroups);
        Iterator values = namedGroups.values().iterator();
        while (values.hasNext()) Aero_ObjHiddenFaceCuller.cull((List[]) values.next());
    }

    private Aero_MeshModel build(String name) {
        if (allEmpty(staticGroups) && namedGroups.isEmpty()) {
            throw new RuntimeException("AeroObjLoader: no faces found in " + name);
        }
        Map arrays = new LinkedHashMap();
        Iterator entries = namedGroups.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            arrays.put(entry.getKey(), toArrays((List[]) entry.getValue()));
        }
        return new Aero_MeshModel(name, toArrays(staticGroups), 1.0f, arrays);
    }

    private void parseFace(String value, List[] groups) {
        String[] tokens = split(value);
        float[][] polygon = new float[tokens.length][];
        for (int i = 0; i < tokens.length; i++) polygon[i] = parseFaceVertex(tokens[i]);
        for (int i = 1; i < polygon.length - 1; i++) addTriangle(groups,
            polygon[0], polygon[i], polygon[i + 1]);
    }

    private static void addTriangle(List[] groups, float[] v0, float[] v1, float[] v2) {
        float ax = v1[0] - v0[0], ay = v1[1] - v0[1], az = v1[2] - v0[2];
        float bx = v2[0] - v0[0], by = v2[1] - v0[1], bz = v2[2] - v0[2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1e-7f) { nx /= length; ny /= length; nz /= length; }
        groups[brightnessGroup(nx, ny, nz)].add(new float[] {
            v0[0], v0[1], v0[2], v0[3], v0[4],
            v1[0], v1[1], v1[2], v1[3], v1[4],
            v2[0], v2[1], v2[2], v2[3], v2[4]
        });
    }

    private float[] parseFaceVertex(String token) {
        String[] parts = token.split("/", -1);
        int vertexIndex = Integer.parseInt(parts[0].trim());
        int uvIndex = parts.length > 1 && !parts[1].isEmpty()
            ? Integer.parseInt(parts[1].trim()) : 0;
        float[] vertex = (float[]) verts.get(resolveIndex(vertexIndex, verts.size()));
        float[] uv = uvIndex == 0 ? new float[] {0.0f, 0.0f}
            : (float[]) uvs.get(resolveIndex(uvIndex, uvs.size()));
        return new float[] {vertex[0], vertex[1], vertex[2], uv[0], uv[1]};
    }

    private static int resolveIndex(int index, int size) {
        return index < 0 ? size + index : index - 1;
    }

    static int brightnessGroup(float nx, float ny, float nz) {
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        if (ay >= ax && ay >= az)
            return ny > 0 ? Aero_MeshModel.GROUP_TOP : Aero_MeshModel.GROUP_BOTTOM;
        if (az >= ax) return Aero_MeshModel.GROUP_NS;
        return Aero_MeshModel.GROUP_EW;
    }

    private static List[] newGroups() {
        List[] groups = new List[4];
        for (int i = 0; i < groups.length; i++) groups[i] = new ArrayList();
        return groups;
    }

    private static float[][][] toArrays(List[] groups) {
        float[][][] arrays = new float[groups.length][][];
        for (int i = 0; i < groups.length; i++)
            arrays[i] = (float[][]) groups[i].toArray(new float[groups[i].size()][]);
        return arrays;
    }

    private static boolean allEmpty(List[] groups) {
        for (int i = 0; i < groups.length; i++) if (!groups[i].isEmpty()) return false;
        return true;
    }

    private static String[] split(String value) { return value.trim().split("\\s+"); }
    private static float number(String value) { return Float.parseFloat(value.trim()); }
}
