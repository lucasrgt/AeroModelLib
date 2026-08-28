package aero.modellib.model;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import aero.modellib.util.Aero_PerfConfig;

/** Loads Three.js Object/Scene JSON with baked BufferGeometry data. */
@aero.modellib.optimization.OptimizationRef({"aero.loader.bounded-caches"})
public final class Aero_ThreeJsonLoader {
    private static final int MAX_CACHE_ENTRIES =
        Aero_PerfConfig.intProperty("aero.modellib.cache.maxEntries",
            512, -1, -1, Integer.MAX_VALUE);
    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
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
            if (input == null) Aero_ThreeJsonGeometry.fail("resource not found: " + resourcePath);
            String json;
            try {
                json = readUtf8(input);
            } finally {
                input.close();
            }
            Aero_MeshModel model = parse(json, name);
            cache.put(resourcePath, model);
            return model;
        } catch (Exception error) {
            throw new RuntimeException("AeroThreeJsonLoader: failed to load "
                + resourcePath + ": " + error.getMessage(), error);
        }
    }

    /** Parses Three.js JSON already held in memory. Useful for tools and tests. */
    public static Aero_MeshModel parse(String json, String name) {
        try {
            Object value = Aero_JsonValueParser.parse(json);
            if (!(value instanceof Map)) Aero_ThreeJsonGeometry.fail("root must be an object");
            return Aero_ThreeJsonImporter.parse((Map) value, name);
        } catch (RuntimeException error) {
            if (error.getMessage() != null
                    && error.getMessage().startsWith("AeroThreeJsonLoader:")) throw error;
            throw new RuntimeException("AeroThreeJsonLoader: failed to parse "
                + name + ": " + error.getMessage(), error);
        }
    }

    public static synchronized void clearCache() { cache.clear(); }
    public static synchronized int cacheSize() { return cache.size(); }

    private static String readUtf8(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
        return new String(bytes.toByteArray(), "UTF-8");
    }
}
