package aero.modellib;

import org.junit.Before;
import org.junit.Test;

import aero.modellib.render.Aero_SmoothLightCache;

import static org.junit.Assert.*;

/**
 * Unit tests for the opt-in resolved smooth-light cache:
 *  - TTL freshness window (hit inside, miss after expiry)
 *  - claim create/refresh/resize semantics
 *  - key identity across world, geometry, and block position
 *  - LRU capacity bound with access-order retention
 *  - clear/configure lifecycle
 *
 * Time is injected through the nowNanos parameter, so no sleeping.
 */
public class SmoothLightCacheTest {

    private static final long MS = 1000000L;

    private final Object world = new Object();
    private final Object geometry = new Object();

    @Before
    public void reset() {
        // 50 ms TTL, capacity 8 — deterministic regardless of system properties.
        Aero_SmoothLightCache.configure(50L, 8);
    }

    @Test
    public void missOnEmptyCache() {
        assertNull(Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 0L));
        assertEquals(0, Aero_SmoothLightCache.entryCount());
    }

    @Test
    public void claimThenFreshHitReturnsSameFilledArray() {
        float[] claimed = Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 0L);
        assertEquals(4, claimed.length);
        claimed[0] = 0.25f;
        claimed[3] = 1.0f;

        float[] hit = Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 49L * MS);
        assertSame("fresh lookup must reuse the resolved array", claimed, hit);
        assertEquals(0.25f, hit[0], 0f);
        assertEquals(1.0f, hit[3], 0f);
    }

    @Test
    public void staleEntryMissesAfterTtl() {
        Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 0L);
        assertNotNull(Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 50L * MS));
        assertNull("entry older than the TTL must miss",
            Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 51L * MS));
    }

    @Test
    public void claimRefreshesStaleEntryInPlace() {
        float[] first = Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 0L);
        float[] second = Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 200L * MS);
        assertSame("same-size refresh must reuse the array", first, second);
        assertNotNull(Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 210L * MS));
        assertEquals(1, Aero_SmoothLightCache.entryCount());
    }

    @Test
    public void sizeChangeResizesAndSizeMismatchMisses() {
        float[] first = Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 0L);
        assertNull("size mismatch must miss even when fresh",
            Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 6, 1L * MS));
        float[] resized = Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 6, 1L * MS);
        assertNotSame(first, resized);
        assertEquals(6, resized.length);
        assertEquals(1, Aero_SmoothLightCache.entryCount());
    }

    @Test
    public void keysDiscriminateWorldGeometryAndPosition() {
        float[] base = Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 0L);
        base[0] = 7f;

        assertNull(Aero_SmoothLightCache.cached(new Object(), geometry, 1, 2, 3, 4, 0L));
        assertNull(Aero_SmoothLightCache.cached(world, new Object(), 1, 2, 3, 4, 0L));
        assertNull(Aero_SmoothLightCache.cached(world, geometry, 9, 2, 3, 4, 0L));
        assertNull(Aero_SmoothLightCache.cached(world, geometry, 1, 9, 3, 4, 0L));
        assertNull(Aero_SmoothLightCache.cached(world, geometry, 1, 2, 9, 4, 0L));

        float[] hit = Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 0L);
        assertSame(base, hit);
    }

    @Test
    public void lruBoundEvictsColdestEntry() {
        Aero_SmoothLightCache.configure(1000L, 2);
        Object geometryB = new Object();
        Object geometryC = new Object();

        Aero_SmoothLightCache.claim(world, geometry, 0, 0, 0, 4, 0L);
        Aero_SmoothLightCache.claim(world, geometryB, 0, 0, 0, 4, 1L);
        // Touch the first entry so geometryB becomes the eldest.
        assertNotNull(Aero_SmoothLightCache.cached(world, geometry, 0, 0, 0, 4, 2L));
        Aero_SmoothLightCache.claim(world, geometryC, 0, 0, 0, 4, 3L);

        assertEquals(2, Aero_SmoothLightCache.entryCount());
        assertNotNull("recently used entry must survive",
            Aero_SmoothLightCache.cached(world, geometry, 0, 0, 0, 4, 4L));
        assertNull("least recently used entry must be evicted",
            Aero_SmoothLightCache.cached(world, geometryB, 0, 0, 0, 4, 4L));
        assertNotNull(Aero_SmoothLightCache.cached(world, geometryC, 0, 0, 0, 4, 4L));
    }

    @Test
    public void clearDropsEverything() {
        Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 0L);
        Aero_SmoothLightCache.clear();
        assertEquals(0, Aero_SmoothLightCache.entryCount());
        assertNull(Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 0L));
    }

    @Test
    public void zeroTtlAlwaysMissesOnLaterLookups() {
        Aero_SmoothLightCache.configure(0L, 8);
        Aero_SmoothLightCache.claim(world, geometry, 1, 2, 3, 4, 0L);
        assertNotNull("same-instant lookup is still fresh at TTL 0",
            Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 0L));
        assertNull(Aero_SmoothLightCache.cached(world, geometry, 1, 2, 3, 4, 1L));
    }
}
