package aero.modellib;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import net.minecraft.client.render.Tessellator;

/** Reuses exact pose-transformed local vertices inside the current bucket. */
@OptimizationRef({"aero.render.batch-transformed-vertex-reuse"})
final class Aero_BatchVertexReuse {
    static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.batchvertexreuse"));
    static final int REST = 0, TRANSLATE = 1, SCALE = 2, ROTATE = 3;
    private static float[] vertices = new float[0];
    private static int sharedSource, mode, vertexCount, emissions;
    private static float baseX, baseY, baseZ;
    private static boolean prepared;
    private static int transformedThisFrame, reusedThisFrame;

    private Aero_BatchVertexReuse() {}

    static void beginBucket(int source) {
        sharedSource = source;
        prepared = false;
        emissions = 0;
    }

    static boolean emitPose(Tessellator tess, float[][] tris, float invScale,
            Aero_BoneRenderPose pose, int poseSource, double x, double y, double z) {
        if (!ENABLED || poseSource != sharedSource || sharedSource < 0) return false;
        if (!prepared) preparePose(tris, invScale, pose);
        emit(tess, x, y, z);
        return true;
    }

    static boolean emitRest(Tessellator tess, float[][] tris, float invScale,
            int poseSource, double x, double y, double z) {
        if (!ENABLED || poseSource != sharedSource || sharedSource < 0) return false;
        if (!prepared) prepareRest(tris, invScale);
        emit(tess, x, y, z);
        return true;
    }

    static void beginFrameCounters() { transformedThisFrame = reusedThisFrame = 0; }
    static int transformedThisFrame() { return transformedThisFrame; }
    static int reusedThisFrame() { return reusedThisFrame; }

    private static void preparePose(float[][] tris, float invScale,
            Aero_BoneRenderPose pose) {
        boolean rotated = pose.rotX != 0f || pose.rotY != 0f || pose.rotZ != 0f;
        boolean scaled = pose.scaleX != 1f || pose.scaleY != 1f || pose.scaleZ != 1f;
        if (rotated) prepareRotated(tris, invScale, pose);
        else if (scaled) prepareScaled(tris, invScale, pose);
        else prepareTranslated(tris, invScale, pose);
    }

    private static void prepareRest(float[][] tris, float invScale) {
        startPrepare(tris, REST, 0f, 0f, 0f);
        for (int tri = 0; tri < tris.length; tri++) {
            float[] source = tris[tri];
            for (int vertex = 0; vertex < 3; vertex++) {
                int src = vertex * 5, dst = (tri * 3 + vertex) * 5;
                vertices[dst] = source[src] * invScale;
                vertices[dst + 1] = source[src + 1] * invScale;
                vertices[dst + 2] = source[src + 2] * invScale;
                vertices[dst + 3] = source[src + 3];
                vertices[dst + 4] = source[src + 4];
            }
        }
    }

    private static void prepareTranslated(float[][] tris, float invScale,
            Aero_BoneRenderPose pose) {
        startPrepare(tris, TRANSLATE, pose.offsetX, pose.offsetY, pose.offsetZ);
        fillSimple(tris, invScale, pose);
    }

    private static void prepareScaled(float[][] tris, float invScale,
            Aero_BoneRenderPose pose) {
        startPrepare(tris, SCALE, 0f, 0f, 0f);
        float postX = pose.pivotX + pose.offsetX;
        float postY = pose.pivotY + pose.offsetY;
        float postZ = pose.pivotZ + pose.offsetZ;
        for (int tri = 0; tri < tris.length; tri++) {
            float[] source = tris[tri];
            for (int vertex = 0; vertex < 3; vertex++) {
                int src = vertex * 5, dst = (tri * 3 + vertex) * 5;
                vertices[dst] = (source[src] * invScale - pose.pivotX) * pose.scaleX + postX;
                vertices[dst + 1] = (source[src + 1] * invScale - pose.pivotY) * pose.scaleY + postY;
                vertices[dst + 2] = (source[src + 2] * invScale - pose.pivotZ) * pose.scaleZ + postZ;
                copyUv(source, src, vertices, dst, pose);
            }
        }
    }

    private static void prepareRotated(float[][] tris, float invScale,
            Aero_BoneRenderPose pose) {
        startPrepare(tris, ROTATE, pose.pivotX + pose.offsetX,
            pose.pivotY + pose.offsetY, pose.pivotZ + pose.offsetZ);
        float radians = (float) (Math.PI / 180.0);
        float cx = (float) Math.cos(pose.rotX * radians), sx = (float) Math.sin(pose.rotX * radians);
        float cy = (float) Math.cos(pose.rotY * radians), sy = (float) Math.sin(pose.rotY * radians);
        float cz = (float) Math.cos(pose.rotZ * radians), sz = (float) Math.sin(pose.rotZ * radians);
        for (int tri = 0; tri < tris.length; tri++) {
            float[] source = tris[tri];
            for (int vertex = 0; vertex < 3; vertex++) {
                int src = vertex * 5, dst = (tri * 3 + vertex) * 5;
                float x = (source[src] * invScale - pose.pivotX) * pose.scaleX;
                float y = (source[src + 1] * invScale - pose.pivotY) * pose.scaleY;
                float z = (source[src + 2] * invScale - pose.pivotZ) * pose.scaleZ;
                float ny = y * cx - z * sx, nz = y * sx + z * cx;
                float nx = x * cy + nz * sy;
                z = -x * sy + nz * cy; x = nx;
                vertices[dst] = x * cz - ny * sz;
                vertices[dst + 1] = x * sz + ny * cz;
                vertices[dst + 2] = z;
                copyUv(source, src, vertices, dst, pose);
            }
        }
    }

    private static void fillSimple(float[][] tris, float invScale,
            Aero_BoneRenderPose pose) {
        for (int tri = 0; tri < tris.length; tri++) {
            float[] source = tris[tri];
            for (int vertex = 0; vertex < 3; vertex++) {
                int src = vertex * 5, dst = (tri * 3 + vertex) * 5;
                vertices[dst] = source[src] * invScale;
                vertices[dst + 1] = source[src + 1] * invScale;
                vertices[dst + 2] = source[src + 2] * invScale;
                copyUv(source, src, vertices, dst, pose);
            }
        }
    }

    private static void copyUv(float[] source, int src, float[] target, int dst,
            Aero_BoneRenderPose pose) {
        if (pose.uvIsIdentity()) {
            target[dst + 3] = source[src + 3]; target[dst + 4] = source[src + 4];
        } else {
            target[dst + 3] = source[src + 3] * pose.uScale + pose.uOffset;
            target[dst + 4] = source[src + 4] * pose.vScale + pose.vOffset;
        }
    }

    private static void startPrepare(float[][] tris, int nextMode,
            float x, float y, float z) {
        vertexCount = tris.length * 3;
        int floats = vertexCount * 5;
        if (vertices.length < floats) vertices = new float[Math.max(floats, vertices.length * 2 + 15)];
        mode = nextMode; baseX = x; baseY = y; baseZ = z;
        prepared = true;
        transformedThisFrame += vertexCount;
    }

    private static void emit(Tessellator tess, double x, double y, double z) {
        if (emissions++ > 0) reusedThisFrame += vertexCount;
        if (Aero_TessellatorBulkWriter.write(tess, vertices, vertexCount,
                mode, baseX, baseY, baseZ, x, y, z)) return;
        double bx = mode == TRANSLATE ? x + baseX : x;
        double by = mode == TRANSLATE ? y + baseY : y;
        double bz = mode == TRANSLATE ? z + baseZ : z;
        for (int vertex = 0, off = 0; vertex < vertexCount; vertex++, off += 5) {
            if (mode == ROTATE) {
                tess.vertex(x + vertices[off] + baseX, y + vertices[off + 1] + baseY,
                    z + vertices[off + 2] + baseZ, vertices[off + 3], vertices[off + 4]);
            } else {
                tess.vertex(bx + vertices[off], by + vertices[off + 1], bz + vertices[off + 2],
                    vertices[off + 3], vertices[off + 4]);
            }
        }
    }
}
