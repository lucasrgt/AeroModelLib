package aero.modellib;

import org.junit.Test;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ModelSpec;
import aero.modellib.model.Aero_ThreeJsonLoader;

import static org.junit.Assert.*;

public class ThreeJsonLoaderTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void importsIndexedObjectJsonWithHierarchyTransformAndNamedGroup() {
        String json = "{"
            + "\"metadata\":{\"version\":4.6,\"type\":\"Object\"},"
            + "\"geometries\":[{"
            + "\"uuid\":\"geo\",\"type\":\"BufferGeometry\",\"data\":{"
            + "\"attributes\":{"
            + "\"position\":{\"itemSize\":3,\"array\":[0,0,0,1,0,0,0,1,0]},"
            + "\"uv\":{\"itemSize\":2,\"array\":[0,0,1,0,0,1]}},"
            + "\"index\":{\"array\":[0,1,2]}}}],"
            + "\"object\":{\"type\":\"Scene\","
            + "\"matrix\":[1,0,0,0,0,1,0,0,0,0,1,0,10,0,0,1],"
            + "\"children\":[{\"type\":\"Group\",\"name\":\"arm\","
            + "\"matrix\":[1,0,0,0,0,1,0,0,0,0,1,0,0,2,0,1],"
            + "\"children\":[{\"type\":\"Mesh\",\"geometry\":\"geo\","
            + "\"matrix\":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,3,1]}]}]}}";

        Aero_MeshModel model = Aero_ThreeJsonLoader.parse(json, "robot");

        assertEquals(0, model.triangleCount());
        assertEquals(1, model.triangleCountForGroup("arm"));
        float[] triangle = model.getNamedGroup("arm")[Aero_MeshModel.GROUP_NS][0];
        assertArrayEquals(new float[]{10f, 2f, 3f}, xyz(triangle, 0), DELTA);
        assertArrayEquals(new float[]{11f, 2f, 3f}, xyz(triangle, 5), DELTA);
        assertEquals(0f, triangle[3], DELTA);
        assertEquals(1f, triangle[4], DELTA);
        assertEquals(1f, triangle[8], DELTA);
        assertEquals(1f, triangle[9], DELTA);
        assertEquals(0f, triangle[13], DELTA);
        assertEquals(0f, triangle[14], DELTA);
    }

    @Test
    public void importsStandaloneNonIndexedBufferGeometryAsStaticMesh() {
        String json = "{\"metadata\":{\"type\":\"BufferGeometry\"},"
            + "\"type\":\"BufferGeometry\",\"data\":{\"attributes\":{"
            + "\"position\":{\"itemSize\":3,\"array\":[0,0,0,0,0,1,1,0,0]}"
            + "}}}";

        Aero_MeshModel model = Aero_ThreeJsonLoader.parse(json, "triangle");

        assertEquals(1, model.triangleCount());
        assertEquals(0, model.namedGroups.size());
        assertEquals(1, model.groups[Aero_MeshModel.GROUP_TOP].length);
    }

    @Test
    public void mergesMeshesThatInheritTheSameNamedParent() {
        String geometry = "{\"uuid\":\"geo\",\"type\":\"BufferGeometry\","
            + "\"data\":{\"attributes\":{\"position\":{\"itemSize\":3,"
            + "\"array\":[0,0,0,1,0,0,0,1,0]}}}}";
        String child = "{\"type\":\"Mesh\",\"geometry\":\"geo\"}";
        String json = "{\"geometries\":[" + geometry + "],\"object\":{"
            + "\"type\":\"Group\",\"name\":\"body\",\"children\":["
            + child + "," + child + "]}}";

        Aero_MeshModel model = Aero_ThreeJsonLoader.parse(json, "robot");

        assertEquals(2, model.triangleCountForGroup("body"));
        assertEquals(1, model.namedGroups.size());
    }

    @Test
    public void rejectsProceduralGeometryThatWasNotBaked() {
        String json = "{\"geometries\":[{\"uuid\":\"box\","
            + "\"type\":\"BoxGeometry\",\"width\":1}],\"object\":{"
            + "\"type\":\"Mesh\",\"geometry\":\"box\"}}";

        assertRejected(json, "is not baked BufferGeometry");
    }

    @Test
    public void rejectsTriangleDataWithOutOfRangeIndex() {
        String json = "{\"type\":\"BufferGeometry\",\"data\":{"
            + "\"attributes\":{\"position\":{\"itemSize\":3,"
            + "\"array\":[0,0,0,1,0,0,0,1,0]}},"
            + "\"index\":{\"array\":[0,1,4]}}}";

        assertRejected(json, "vertex index out of bounds: 4");
    }

    @Test
    public void rejectsLineGeometryInsteadOfTreatingVerticesAsTriangles() {
        String geometry = "{\"uuid\":\"line\",\"type\":\"BufferGeometry\","
            + "\"data\":{\"attributes\":{\"position\":{\"itemSize\":3,"
            + "\"array\":[0,0,0,1,0,0,0,1,0]}}}}";
        String json = "{\"geometries\":[" + geometry + "],\"object\":{"
            + "\"type\":\"Line\",\"geometry\":\"line\"}}";

        assertRejected(json, "object type Line is not supported");
    }

    @Test
    public void preservesFrontFaceAndLightingAcrossMirroredObjectMatrix() {
        String geometry = "{\"uuid\":\"geo\",\"type\":\"BufferGeometry\","
            + "\"data\":{\"attributes\":{\"position\":{\"itemSize\":3,"
            + "\"array\":[0,0,0,1,0,0,0,1,0]}}}}";
        String json = "{\"geometries\":[" + geometry + "],\"object\":{"
            + "\"type\":\"Mesh\",\"geometry\":\"geo\","
            + "\"matrix\":[-1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]}}";

        Aero_MeshModel model = Aero_ThreeJsonLoader.parse(json, "mirrored");

        assertEquals(1, model.groups[Aero_MeshModel.GROUP_NS].length);
        float[] triangle = model.groups[Aero_MeshModel.GROUP_NS][0];
        assertArrayEquals(new float[]{0f, 1f, 0f}, xyz(triangle, 5), DELTA);
        assertArrayEquals(new float[]{-1f, 0f, 0f}, xyz(triangle, 10), DELTA);
    }

    @Test
    public void modelSpecDispatchesThreeJsonSuffixToThreeLoader() {
        try {
            Aero_ModelSpec.mesh("/missing.three.json").build();
            fail("expected missing resource failure");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(),
                expected.getMessage().contains("AeroThreeJsonLoader"));
        }
    }

    private static float[] xyz(float[] triangle, int offset) {
        return new float[]{triangle[offset], triangle[offset + 1], triangle[offset + 2]};
    }

    private static void assertRejected(String json, String message) {
        try {
            Aero_ThreeJsonLoader.parse(json, "bad");
            fail("expected parse failure");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }
}
