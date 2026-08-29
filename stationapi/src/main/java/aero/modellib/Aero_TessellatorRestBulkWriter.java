package aero.modellib;

import aero.modellib.optimization.OptimizationRef;
import net.minecraft.client.render.Tessellator;
import net.modificationstation.stationapi.api.client.render.StationTessellator;
import net.modificationstation.stationapi.mixin.render.client.TessellatorAccessor;

/** Direct Tessellator staging for unique inactive/rest-pose geometry. */
@OptimizationRef({"aero.render.tessellator-bulk-staging"})
final class Aero_TessellatorRestBulkWriter {
    private static final boolean ENABLED = Aero_TessellatorBulkWriter.ENABLED
        && !"false".equalsIgnoreCase(
            System.getProperty("aero.tessellatorbulk.rest"));
    private static boolean supported = true;
    private static TessellatorAccessor access;
    private static StationTessellator station;
    private static double offsetX, offsetY, offsetZ;

    private Aero_TessellatorRestBulkWriter() {}

    static void beginBatch() {
        if (!ENABLED || !supported) return;
        try {
            if (access == null) {
                access = (TessellatorAccessor) (Object) Tessellator.INSTANCE;
                station = (StationTessellator) (Object) Tessellator.INSTANCE;
            }
            offsetX = access.getXOffset();
            offsetY = access.getYOffset();
            offsetZ = access.getZOffset();
        } catch (ClassCastException error) {
            supported = false;
        }
    }

    static boolean write(float[][] triangles, float invScale,
            double instanceX, double instanceY, double instanceZ) {
        if (!ENABLED || !supported || triangles.length == 0) return false;
        int vertexCount = triangles.length * 3;
        if (access.getColorDisabled()) return false;
        ensureCapacity(vertexCount * 8);
        int[] target = access.stationapi$getBuffer();
        int position = access.stationapi$getBufferPosition();
        int color = access.getColor();
        if (position == 0) access.setHasTexture(true);
        for (int triangle = 0; triangle < triangles.length; triangle++) {
            float[] source = triangles[triangle];
            for (int vertex = 0, src = 0; vertex < 3; vertex++, src += 5) {
                target[position] = Float.floatToRawIntBits(
                    (float) (instanceX + source[src] * invScale + offsetX));
                target[position + 1] = Float.floatToRawIntBits(
                    (float) (instanceY + source[src + 1] * invScale + offsetY));
                target[position + 2] = Float.floatToRawIntBits(
                    (float) (instanceZ + source[src + 2] * invScale + offsetZ));
                target[position + 3] = Float.floatToRawIntBits(source[src + 3]);
                target[position + 4] = Float.floatToRawIntBits(source[src + 4]);
                target[position + 5] = color;
                position += 8;
            }
        }
        access.stationapi$setAddedVertexCount(access.stationapi$getAddedVertexCount() + vertexCount);
        access.stationapi$setBufferPosition(position);
        access.stationapi$setVertexCount(access.stationapi$getVertexCount() + vertexCount);
        Aero_TessellatorBulkWriter.recordVertices(vertexCount);
        return true;
    }

    private static void ensureCapacity(int required) {
        while (access.stationapi$getBufferSize()
                - access.stationapi$getBufferPosition() < required) {
            station.ensureBufferCapacity(required);
        }
    }
}
