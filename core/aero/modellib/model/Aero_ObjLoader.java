package aero.modellib.model;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

import aero.modellib.util.Aero_PerfConfig;

/**
 * AeroMesh OBJ Loader by lucasrgt - aerocoding.dev
 * Loads OBJ models from the classpath at runtime — no conversion pipeline needed.
 *
 * Usage:
 *   Aero_MeshModel model = Aero_ObjLoader.load("/models/my_machine.obj");
 *
 * Export from Blockbench: File > Export > Export OBJ Model
 * Place only the .obj in src/retronism/assets/models/ (the .mtl is not used).
 *
 * Supported directives:
 *   - v  (vertices), vt (UVs), vn (ignored — normal computed from geometry)
 *   - f  (faces: triangles and quads, fan triangulation)
 *   - o / g (named objects/groups — used to separate animated parts)
 *   - Negative OBJ indices (reference from end of list)
 *   - usemtl, mtllib, s → ignored
 *
 * UV: applies V-flip (1-V) — OBJ uses V=0 at bottom, Minecraft uses V=0 at top.
 *
 * Named groups (o / g directives):
 *   Triangles under a named object/group are stored separately in the model's
 *   namedGroups map and excluded from the main groups array. Unnamed triangles
 *   (before any o/g directive) go into the main groups array as static geometry.
 *
 *   This enables animated parts:
 *     Aero_MeshRenderer.renderModel(MODEL, ...);              // static geometry
 *     Aero_MeshRenderer.renderGroupRotated(MODEL, "fan", ...); // animated fan
 *
 * Triangles are classified into 4 brightness groups at parse time
 * (see Aero_MeshModel.GROUP_*), avoiding per-frame normal computation.
 */
public class Aero_ObjLoader {

    private static final int MAX_CACHE_ENTRIES =
        Aero_PerfConfig.intProperty("aero.modellib.cache.maxEntries",
            512, -1, -1, Integer.MAX_VALUE);
    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
    };
    private static int cacheRevision;

    /** Loads and caches an OBJ model from the classpath. */
    public static synchronized Aero_MeshModel load(String resourcePath) {
        return load(resourcePath, resourcePath);
    }

    /** Loads and caches an OBJ model from the classpath with an explicit name. */
    public static synchronized Aero_MeshModel load(String resourcePath, String name) {
        if (cache.containsKey(resourcePath)) {
            return (Aero_MeshModel) cache.get(resourcePath);
        }
        try {
            InputStream is = Aero_ObjLoader.class.getResourceAsStream(resourcePath);
            if (is == null) {
                throw new RuntimeException("AeroObjLoader: resource not found: " + resourcePath);
            }
            Aero_MeshModel model;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                model = parseObj(reader, name, hiddenFaceCullEnabled());
            } finally {
                is.close();
            }
            cache.put(resourcePath, model);
            cacheRevision++;
            return model;
        } catch (Exception e) {
            throw new RuntimeException("AeroObjLoader: failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /** Drops all cached OBJ models. Useful for tests and hot-reload tooling. */
    public static synchronized void clearCache() {
        cache.clear();
        cacheRevision++;
    }

    public static synchronized int cacheSize() {
        return cache.size();
    }

    /** Monotonic signal for render-side discovery without per-frame snapshots. */
    public static synchronized int cacheRevision() {
        return cacheRevision;
    }

    /** Stable identity snapshot used by render-thread cache warmup. */
    public static synchronized Aero_MeshModel[] cachedModels() {
        return (Aero_MeshModel[]) cache.values().toArray(
            new Aero_MeshModel[cache.size()]);
    }

    // -----------------------------------------------------------------------
    // OBJ Parser
    // -----------------------------------------------------------------------

    public static Aero_MeshModel parseObjForTest(BufferedReader reader, String name,
                                          boolean cullHiddenFaces) throws Exception {
        return parseObj(reader, name, cullHiddenFaces);
    }

    private static Aero_MeshModel parseObj(BufferedReader reader, String name,
                                           boolean cullHiddenFaces) throws Exception {
        return Aero_ObjParser.parse(reader, name, cullHiddenFaces);
    }

    static int brightnessGroup(float nx, float ny, float nz) {
        return Aero_ObjParser.brightnessGroup(nx, ny, nz);
    }

    private static boolean hiddenFaceCullEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("aero.obj.cullhidden"));
    }

}
