package aero.modellib;

import org.junit.Test;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ObjLoader;

import static org.junit.Assert.*;

public class ObjLoaderCacheDiscoveryTest {
    @Test
    public void emptySnapshotTracksCacheInvalidation() {
        Aero_ObjLoader.clearCache();
        int before = Aero_ObjLoader.cacheRevision();
        assertArrayEquals(new Aero_MeshModel[0], Aero_ObjLoader.cachedModels());
        Aero_ObjLoader.clearCache();
        assertEquals(before + 1, Aero_ObjLoader.cacheRevision());
        assertEquals(0, Aero_ObjLoader.cacheSize());
    }
}
