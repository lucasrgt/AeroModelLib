package aero.modellib.render;

import java.util.LinkedHashMap;
import java.util.Map;

import aero.modellib.optimization.OptimizationRef;

/**
 * Opt-in cache of resolved smooth-light brightness for static model instances.
 *
 * <p>The smooth-light render path samples a world brightness grid over the
 * model footprint and bilinearly blends one brightness per triangle — every
 * draw, even though a resting block entity's light almost never changes
 * between frames. With {@code -Daero.smoothlight.cache=true} the resolved
 * per-triangle brightness array is kept per (world, geometry, block position)
 * and reused until its TTL expires, so steady-state draws skip both the world
 * grid sampling and the per-triangle bilinear blend.
 *
 * <p>Behavioral contract (documented opt-in): a local light change may render
 * up to {@code aero.smoothlight.cacheMs} milliseconds late (default 50 ms).
 * With the flag unset the renderers never consult this class and behavior is
 * byte-identical to the uncached path.
 *
 * <p>Entries are bounded by an LRU cap ({@code aero.smoothlight.cacheMax},
 * default 1024). Keys hold strong world references until eviction or
 * {@link #clear()}; callers that unload worlds aggressively should clear on
 * world switch. Render thread only — no synchronization.
 */
@OptimizationRef({"aero.render.smooth-light-resolved-cache"})
public final class Aero_SmoothLightCache {

    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.smoothlight.cache"));

    private static long ttlNanos =
        1000000L * longProperty("aero.smoothlight.cacheMs", 50L, 0L, 10000L);
    private static int maxEntries =
        (int) longProperty("aero.smoothlight.cacheMax", 1024L, 1L, 1048576L);

    private static final Key LOOKUP = new Key();
    private static final LinkedHashMap<Key, Resolved> ENTRIES =
        new LinkedHashMap<Key, Resolved>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Resolved> eldest) {
                return size() > maxEntries;
            }
        };

    private Aero_SmoothLightCache() {}

    /**
     * Returns the fresh resolved brightness array for the instance, or null
     * when absent, stale, or sized for different geometry. {@code size} must
     * be the instance's total triangle count; {@code nowNanos} is the caller's
     * monotonic clock (tests inject it, renderers pass System.nanoTime()).
     */
    public static float[] cached(Object world, Object geometry,
                                 int x, int y, int z, int size, long nowNanos) {
        Resolved entry = ENTRIES.get(LOOKUP.set(world, geometry, x, y, z));
        if (entry == null || entry.values.length != size) return null;
        return nowNanos - entry.stamp <= ttlNanos ? entry.values : null;
    }

    /**
     * Returns the instance's brightness array to fill, creating or refreshing
     * its entry. The entry is stamped fresh at {@code nowNanos}; the caller
     * must fill the array before drawing from it.
     */
    public static float[] claim(Object world, Object geometry,
                                int x, int y, int z, int size, long nowNanos) {
        Resolved entry = ENTRIES.get(LOOKUP.set(world, geometry, x, y, z));
        if (entry == null) {
            entry = new Resolved(size);
            ENTRIES.put(LOOKUP.copy(), entry);
        } else if (entry.values.length != size) {
            entry.values = new float[size];
        }
        entry.stamp = nowNanos;
        return entry.values;
    }

    /** Current entry count — diagnostics and tests. */
    public static int entryCount() {
        return ENTRIES.size();
    }

    /** Drops every entry. Call on world unload or from tests. */
    public static void clear() {
        ENTRIES.clear();
    }

    /**
     * Overrides TTL and capacity, then clears. Intended for tests and
     * embedders that manage configuration programmatically.
     */
    public static void configure(long ttlMilliseconds, int maxCachedEntries) {
        ttlNanos = 1000000L * Math.max(0L, ttlMilliseconds);
        maxEntries = Math.max(1, maxCachedEntries);
        clear();
    }

    private static long longProperty(String name, long fallback, long min, long max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            return Math.max(min, Math.min(max, Long.parseLong(raw.trim())));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    /** Identity key: (world, geometry) by reference plus exact block coords. */
    private static final class Key {
        Object world;
        Object geometry;
        int x, y, z;
        int hash;

        Key set(Object world, Object geometry, int x, int y, int z) {
            this.world = world;
            this.geometry = geometry;
            this.x = x;
            this.y = y;
            this.z = z;
            int mixed = System.identityHashCode(world) * 31 + System.identityHashCode(geometry);
            mixed = mixed * 31 + x;
            mixed = mixed * 31 + y;
            mixed = mixed * 31 + z;
            this.hash = mixed ^ (mixed >>> 16);
            return this;
        }

        Key copy() {
            return new Key().set(world, geometry, x, y, z);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return world == key.world && geometry == key.geometry
                && x == key.x && y == key.y && z == key.z;
        }
    }

    private static final class Resolved {
        float[] values;
        long stamp;

        Resolved(int size) {
            this.values = new float[size];
        }
    }
}
