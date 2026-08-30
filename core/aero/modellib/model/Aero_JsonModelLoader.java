package aero.modellib.model;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import aero.modellib.util.Aero_PerfConfig;

/**
 * AeroModel JSON Loader by lucasrgt - aerocoding.dev
 * Loads Blockbench models directly at runtime — no conversion pipeline.
 *
 * Usage:
 *   Aero_JsonModel model = Aero_JsonModelLoader.load("/models/my_machine.json");
 *
 * Place JSONs in src/retronism/assets/models/ — the transpiler injects them
 * into the jar automatically. Export from Blockbench: File > Export > Export as JSON.
 */
public class Aero_JsonModelLoader {
    private static final String[] FACE_ORDER = {"down", "up", "north", "south", "west", "east"};

    private static final int MAX_CACHE_ENTRIES =
        Aero_PerfConfig.intProperty("aero.modellib.cache.maxEntries",
            512, -1, -1, Integer.MAX_VALUE);

    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
    };

    /** Loads and caches a Blockbench model from the classpath. */
    public static synchronized Aero_JsonModel load(String resourcePath) {
        return load(resourcePath, resourcePath);
    }

    /** Loads and caches a Blockbench model from the classpath with an explicit name. */
    public static synchronized Aero_JsonModel load(String resourcePath, String name) {
        if (cache.containsKey(resourcePath)) {
            return (Aero_JsonModel) cache.get(resourcePath);
        }
        try {
            InputStream is = Aero_JsonModelLoader.class.getResourceAsStream(resourcePath);
            if (is == null) {
                throw new RuntimeException("AeroModelLoader: resource not found: " + resourcePath);
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            try {
                while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
            } finally {
                is.close();
            }
            Aero_JsonModel model = fromJson(parseJson(buf.toString("UTF-8"), new int[]{0}), name);
            cache.put(resourcePath, model);
            return model;
        } catch (Exception e) {
            throw new RuntimeException("AeroModelLoader: failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /** Drops all cached JSON models. Useful for tests and hot-reload tooling. */
    public static synchronized void clearCache() {
        cache.clear();
    }

    public static synchronized int cacheSize() {
        return cache.size();
    }

    // -----------------------------------------------------------------------
    // JSON → Aero_JsonModel conversion
    // -----------------------------------------------------------------------

    private static Aero_JsonModel fromJson(Object root, String name) {
        Map obj = (Map) root;
        float textureSize = textureSize(obj);
        List elements = (List) obj.get("elements");
        if (elements == null || elements.isEmpty()) {
            throw new RuntimeException("AeroModelLoader: no elements in " + name);
        }
        List cubes = cubes(elements);
        float[][] parts = new float[cubes.size()][30];
        for (int i = 0; i < cubes.size(); i++) {
            fillPart((Map) cubes.get(i), parts[i]);
        }
        return new Aero_JsonModel(name, parts, textureSize, 16.0f);
    }

    private static float textureSize(Map object) {
        Map resolution = object.containsKey("resolution")
            ? (Map) object.get("resolution") : null;
        return resolution != null && resolution.containsKey("width")
            ? toFloat(resolution.get("width")) : 128.0f;
    }

    private static List cubes(List elements) {
        List cubes = new ArrayList();
        for (int i = 0; i < elements.size(); i++) {
            Object value = elements.get(i);
            if (!(value instanceof Map)) continue;
            Map element = (Map) value;
            if (element.containsKey("from") && element.containsKey("to")) cubes.add(element);
        }
        return cubes;
    }

    private static void fillPart(Map element, float[] part) {
        List from = (List) element.get("from");
        List to = (List) element.get("to");
        float inflate = element.containsKey("inflate") ? toFloat(element.get("inflate")) : 0.0f;
        part[0] = toFloat(from.get(0)) - inflate;
        part[1] = toFloat(from.get(1)) - inflate;
        part[2] = toFloat(from.get(2)) - inflate;
        part[3] = toFloat(to.get(0)) + inflate;
        part[4] = toFloat(to.get(1)) + inflate;
        part[5] = toFloat(to.get(2)) + inflate;
        Map faces = element.containsKey("faces") ? (Map) element.get("faces") : new HashMap();
        for (int face = 0; face < FACE_ORDER.length; face++)
            fillFaceUv(faces.get(FACE_ORDER[face]), part, 6 + face * 4);
    }

    private static void fillFaceUv(Object value, float[] part, int base) {
        List uv = value instanceof Map ? (List) ((Map) value).get("uv") : null;
        if (uv == null || uv.size() < 4) {
            part[base] = part[base + 1] = part[base + 2] = part[base + 3] = -1.0f;
            return;
        }
        part[base] = toFloat(uv.get(0));
        part[base + 1] = toFloat(uv.get(1));
        part[base + 2] = toFloat(uv.get(2));
        part[base + 3] = toFloat(uv.get(3));
    }

    private static float toFloat(Object o) {
        if (o instanceof Float)   return (Float) o;
        if (o instanceof Double)  return ((Double) o).floatValue();
        if (o instanceof Integer) return (float)(int)(Integer) o;
        if (o instanceof Long)    return (float)(long)(Long) o;
        return 0.0f;
    }

    // -----------------------------------------------------------------------
    // Minimal recursive-descent JSON parser
    // pos[0] = current index in the string
    // -----------------------------------------------------------------------

    private static Object parseJson(String s, int[] pos) {
        skipWs(s, pos);
        if (pos[0] >= s.length()) throw new RuntimeException("Unexpected end of JSON");
        char c = s.charAt(pos[0]);
        if (c == '{') return parseObject(s, pos);
        if (c == '[') return parseArray(s, pos);
        if (c == '"') return parseString(s, pos);
        if (c == 't') { pos[0] += 4; return Boolean.TRUE; }
        if (c == 'f') { pos[0] += 5; return Boolean.FALSE; }
        if (c == 'n') { pos[0] += 4; return null; }
        return parseNumber(s, pos);
    }

    private static Map parseObject(String s, int[] pos) {
        pos[0]++; // '{'
        Map map = new HashMap();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == '}') { pos[0]++; return map; }
        while (pos[0] < s.length()) {
            skipWs(s, pos);
            String key = parseString(s, pos);
            skipWs(s, pos);
            pos[0]++; // ':'
            skipWs(s, pos);
            map.put(key, parseJson(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) break;
            char next = s.charAt(pos[0]);
            if (next == '}') { pos[0]++; break; }
            if (next == ',') pos[0]++;
        }
        return map;
    }

    private static List parseArray(String s, int[] pos) {
        pos[0]++; // '['
        List list = new ArrayList();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == ']') { pos[0]++; return list; }
        while (pos[0] < s.length()) {
            skipWs(s, pos);
            list.add(parseJson(s, pos));
            skipWs(s, pos);
            if (pos[0] >= s.length()) break;
            char next = s.charAt(pos[0]);
            if (next == ']') { pos[0]++; break; }
            if (next == ',') pos[0]++;
        }
        return list;
    }

    private static String parseString(String s, int[] pos) {
        pos[0]++; // '"'
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]++);
            if (c == '"') break;
            if (c == '\\' && pos[0] < s.length()) {
                appendEscape(s.charAt(pos[0]++), sb);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void appendEscape(char escape, StringBuilder output) {
        switch (escape) {
            case '"': output.append('"'); break;
            case '\\': output.append('\\'); break;
            case '/': output.append('/'); break;
            case 'n': output.append('\n'); break;
            case 'r': output.append('\r'); break;
            case 't': output.append('\t'); break;
            default: output.append(escape);
        }
    }

    private static Float parseNumber(String s, int[] pos) {
        int start = pos[0];
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == ',' || c == ']' || c == '}' || c <= ' ') break;
            pos[0]++;
        }
        return Float.parseFloat(s.substring(start, pos[0]));
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length() && s.charAt(pos[0]) <= ' ') pos[0]++;
    }
}
