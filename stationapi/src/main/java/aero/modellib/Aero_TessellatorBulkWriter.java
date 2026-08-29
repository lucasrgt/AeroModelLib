package aero.modellib;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.model.Aero_MeshModel;
import net.minecraft.client.render.Tessellator;
import net.modificationstation.stationapi.api.client.render.StationTessellator;
import net.modificationstation.stationapi.mixin.render.client.TessellatorAccessor;

/** Packs known triangle vertices directly into StationAPI's Tessellator buffer. */
@OptimizationRef({"aero.render.tessellator-bulk-staging"})
final class Aero_TessellatorBulkWriter {
    private static final int MIN_VERTICES_PER_BATCH = 262_144;
    static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.tessellatorbulk"));
    private static boolean supported = true;
    private static boolean enabledThisBatch;
    private static int verticesThisFrame;

    private Aero_TessellatorBulkWriter() {}

    static boolean write(Tessellator tess, float[] source, int vertexCount,
            int mode, float baseX, float baseY, float baseZ,
            double instanceX, double instanceY, double instanceZ) {
        if (!enabledThisBatch || !supported || vertexCount <= 0) return false;
        try {
            TessellatorAccessor access = (TessellatorAccessor) (Object) tess;
            if (access.getColorDisabled()) return false;
            ensureCapacity(tess, access, vertexCount * 8);
            int[] target = access.stationapi$getBuffer();
            int position = access.stationapi$getBufferPosition();
            int color = access.getColor();
            double offsetX = access.getXOffset(), offsetY = access.getYOffset();
            double offsetZ = access.getZOffset();
            double translatedX = mode == Aero_BatchVertexReuse.TRANSLATE
                ? instanceX + baseX : instanceX;
            double translatedY = mode == Aero_BatchVertexReuse.TRANSLATE
                ? instanceY + baseY : instanceY;
            double translatedZ = mode == Aero_BatchVertexReuse.TRANSLATE
                ? instanceZ + baseZ : instanceZ;
            for (int vertex = 0, src = 0; vertex < vertexCount; vertex++, src += 5) {
                double x = translatedX + source[src];
                double y = translatedY + source[src + 1];
                double z = translatedZ + source[src + 2];
                if (mode == Aero_BatchVertexReuse.ROTATE) {
                    x = instanceX + source[src] + baseX;
                    y = instanceY + source[src + 1] + baseY;
                    z = instanceZ + source[src + 2] + baseZ;
                }
                target[position] = Float.floatToRawIntBits((float) (x + offsetX));
                target[position + 1] = Float.floatToRawIntBits((float) (y + offsetY));
                target[position + 2] = Float.floatToRawIntBits((float) (z + offsetZ));
                target[position + 3] = Float.floatToRawIntBits(source[src + 3]);
                target[position + 4] = Float.floatToRawIntBits(source[src + 4]);
                target[position + 5] = color;
                position += 8;
            }
            access.setHasTexture(true);
            access.setHasColor(true);
            access.stationapi$setAddedVertexCount(
                access.stationapi$getAddedVertexCount() + vertexCount);
            access.stationapi$setBufferPosition(position);
            access.stationapi$setVertexCount(access.stationapi$getVertexCount() + vertexCount);
            verticesThisFrame += vertexCount;
            return true;
        } catch (ClassCastException error) {
            supported = false;
            return false;
        }
    }

    static void beginBatch(Aero_MeshModel.NamedGroup[] entries,
            int[] drawableEntries, int sharedCount) {
        if (!ENABLED || sharedCount <= 0) {
            enabledThisBatch = false;
            return;
        }
        long vertices = 0L;
        for (int d = 0; d < drawableEntries.length; d++) {
            float[][][] groups = entries[drawableEntries[d]].tris;
            for (int group = 0; group < groups.length; group++) {
                vertices += groups[group].length * 3L;
            }
        }
        enabledThisBatch = vertices * sharedCount >= MIN_VERTICES_PER_BATCH;
    }
    static void beginFrameCounters() { verticesThisFrame = 0; }
    static int verticesThisFrame() { return verticesThisFrame; }

    private static void ensureCapacity(Tessellator tess, TessellatorAccessor access,
            int required) {
        StationTessellator station = (StationTessellator) (Object) tess;
        while (access.stationapi$getBufferSize()
                - access.stationapi$getBufferPosition() < required) {
            station.ensureBufferCapacity(required);
        }
    }
}
