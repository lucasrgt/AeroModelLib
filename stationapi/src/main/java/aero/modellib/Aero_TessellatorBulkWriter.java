package aero.modellib;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import net.minecraft.client.render.Tessellator;
import net.modificationstation.stationapi.api.client.render.StationTessellator;
import net.modificationstation.stationapi.mixin.render.client.TessellatorAccessor;

/** Packs known triangle vertices directly into StationAPI's Tessellator buffer. */
@OptimizationRef({"aero.render.tessellator-bulk-staging"})
final class Aero_TessellatorBulkWriter {
    private static final int MIN_VERTICES_PER_BATCH = 262_144;
    static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.tessellatorbulk"));
    private static final boolean UNIQUE_POSE_ENABLED =
        ENABLED && !"false".equalsIgnoreCase(
            System.getProperty("aero.tessellatorbulk.uniquePose"));
    private static boolean supported = true;
    private static boolean enabledThisBatch;
    private static TessellatorAccessor access;
    private static StationTessellator station;
    private static double offsetX, offsetY, offsetZ;
    private static int verticesThisFrame;

    private Aero_TessellatorBulkWriter() {}

    static boolean write(float[] source, int vertexCount, int mode,
            float baseX, float baseY, float baseZ,
            double instanceX, double instanceY, double instanceZ) {
        if (!enabledThisBatch || !supported || vertexCount <= 0) return false;
        if (access.getColorDisabled()) return false;
        ensureCapacity(vertexCount * 8);
        try {
            int[] target = access.stationapi$getBuffer();
            int position = access.stationapi$getBufferPosition();
            int color = access.getColor();
            if (position == 0) access.setHasTexture(true);
            double translatedX = mode == Aero_BatchVertexReuse.TRANSLATE
                ? instanceX + baseX : instanceX;
            double translatedY = mode == Aero_BatchVertexReuse.TRANSLATE
                ? instanceY + baseY : instanceY;
            double translatedZ = mode == Aero_BatchVertexReuse.TRANSLATE
                ? instanceZ + baseZ : instanceZ;
            for (int vertex = 0, src = 0; vertex < vertexCount; vertex++, src += 5) {
                double x, y, z;
                if (mode == Aero_BatchVertexReuse.ROTATE) {
                    x = instanceX + source[src] + baseX;
                    y = instanceY + source[src + 1] + baseY;
                    z = instanceZ + source[src + 2] + baseZ;
                } else {
                    x = translatedX + source[src];
                    y = translatedY + source[src + 1];
                    z = translatedZ + source[src + 2];
                }
                target[position] = Float.floatToRawIntBits((float) (x + offsetX));
                target[position + 1] = Float.floatToRawIntBits((float) (y + offsetY));
                target[position + 2] = Float.floatToRawIntBits((float) (z + offsetZ));
                target[position + 3] = Float.floatToRawIntBits(source[src + 3]);
                target[position + 4] = Float.floatToRawIntBits(source[src + 4]);
                target[position + 5] = color;
                position += 8;
            }
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

    static boolean writeUniquePose(float[][] triangles, float invScale,
            Aero_BoneRenderPose pose, double instanceX, double instanceY, double instanceZ) {
        int vertexCount = triangles.length * 3;
        if (!UNIQUE_POSE_ENABLED || !supported || vertexCount == 0) return false;
        if (access == null) bindTessellator();
        if (!supported || access.getColorDisabled()) return false;
        ensureCapacity(vertexCount * 8);
        try {
            int[] target = access.stationapi$getBuffer();
            int position = access.stationapi$getBufferPosition();
            int color = access.getColor();
            if (position == 0) access.setHasTexture(true);
            float radians = (float) (Math.PI / 180.0);
            float cx = (float) Math.cos(pose.rotX * radians);
            float sx = (float) Math.sin(pose.rotX * radians);
            float cy = (float) Math.cos(pose.rotY * radians);
            float sy = (float) Math.sin(pose.rotY * radians);
            float cz = (float) Math.cos(pose.rotZ * radians);
            float sz = (float) Math.sin(pose.rotZ * radians);
            float postX = pose.pivotX + pose.offsetX;
            float postY = pose.pivotY + pose.offsetY;
            float postZ = pose.pivotZ + pose.offsetZ;
            boolean uvIdentity = pose.uvIsIdentity();
            for (int triangle = 0; triangle < triangles.length; triangle++) {
                float[] source = triangles[triangle];
                for (int vertex = 0, src = 0; vertex < 3; vertex++, src += 5) {
                    float x = (source[src] * invScale - pose.pivotX) * pose.scaleX;
                    float y = (source[src + 1] * invScale - pose.pivotY) * pose.scaleY;
                    float z = (source[src + 2] * invScale - pose.pivotZ) * pose.scaleZ;
                    float ny = y * cx - z * sx, nz = y * sx + z * cx;
                    float nx = x * cy + nz * sy;
                    z = -x * sy + nz * cy; x = nx;
                    y = x * sz + ny * cz;
                    x = x * cz - ny * sz;
                    float u = source[src + 3], v = source[src + 4];
                    if (!uvIdentity) {
                        u = u * pose.uScale + pose.uOffset;
                        v = v * pose.vScale + pose.vOffset;
                    }
                    target[position] = Float.floatToRawIntBits(
                        (float) (instanceX + x + postX + offsetX));
                    target[position + 1] = Float.floatToRawIntBits(
                        (float) (instanceY + y + postY + offsetY));
                    target[position + 2] = Float.floatToRawIntBits(
                        (float) (instanceZ + z + postZ + offsetZ));
                    target[position + 3] = Float.floatToRawIntBits(u);
                    target[position + 4] = Float.floatToRawIntBits(v);
                    target[position + 5] = color;
                    position += 8;
                }
            }
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
        if (!ENABLED) {
            enabledThisBatch = false;
            return;
        }
        if (UNIQUE_POSE_ENABLED) bindTessellator();
        if (sharedCount <= 0) {
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
        if (enabledThisBatch) bindTessellator();
    }
    static void beginFrameCounters() { verticesThisFrame = 0; }
    static int verticesThisFrame() { return verticesThisFrame; }

    private static void bindTessellator() {
        try {
            if (access == null) {
                access = (TessellatorAccessor) (Object) Tessellator.INSTANCE;
                station = (StationTessellator) (Object) Tessellator.INSTANCE;
            }
            offsetX = access.getXOffset();
            offsetY = access.getYOffset();
            offsetZ = access.getZOffset();
        } catch (ClassCastException error) {
            supported = enabledThisBatch = false;
        }
    }

    private static void ensureCapacity(int required) {
        while (access.stationapi$getBufferSize()
                - access.stationapi$getBufferPosition() < required) {
            station.ensureBufferCapacity(required);
        }
    }
}
