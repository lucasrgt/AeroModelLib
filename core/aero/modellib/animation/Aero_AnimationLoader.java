package aero.modellib.animation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads strict animation JSON resources into bounded, cached bundles. */
@aero.modellib.optimization.OptimizationRef({"aero.loader.bounded-caches"})
public class Aero_AnimationLoader {
    public static final String SUPPORTED_FORMAT_VERSION = "1.1";
    public static final String[] BACKWARD_COMPAT_VERSIONS = {"1.0"};
    private static final int MAX_CACHE_ENTRIES =
        Integer.getInteger("aero.modellib.cache.maxEntries", 512).intValue();
    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
    };

    public static synchronized Aero_AnimationBundle load(String resourcePath) {
        if (cache.containsKey(resourcePath)) return (Aero_AnimationBundle) cache.get(resourcePath);
        try {
            InputStream stream = Aero_AnimationLoader.class.getResourceAsStream(resourcePath);
            if (stream == null) throw new RuntimeException("resource not found: " + resourcePath);
            StringBuilder json = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) json.append(line).append('\n');
            } finally {
                stream.close();
            }
            Aero_AnimationBundle bundle = decode(json.toString());
            cache.put(resourcePath, bundle);
            return bundle;
        } catch (Exception error) {
            throw new RuntimeException("Aero_AnimationLoader: failed to load " + resourcePath
                + ": " + error.getMessage(), error);
        }
    }

    public static synchronized void clearCache() { cache.clear(); }
    public static synchronized int cacheSize() { return cache.size(); }

    public static Aero_AnimationBundle loadFromString(String json) {
        try { return decode(json); }
        catch (Exception error) {
            throw new RuntimeException("Aero_AnimationLoader: failed to parse animation JSON: "
                + error.getMessage(), error);
        }
    }

    private static Aero_AnimationBundle decode(String json) {
        return Aero_AnimationBundleDecoder.decode((Map) new Aero_AnimationJsonParser(json).parseValue());
    }
}
