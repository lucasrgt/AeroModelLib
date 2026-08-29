package aero.modellib;

import aero.modellib.optimization.OptimizationRef;
import aero.modellib.skeletal.Aero_BoneRenderPose;

/** CPU transforms used by the animated client-array submission candidate. */
@OptimizationRef({"aero.render.client-vertex-arrays"})
final class Aero_MeshClientArrayEmitter {
    private Aero_MeshClientArrayEmitter() {}

    static void emitStatic(float[][] tris, float invScale,
            double x, double y, double z) {
        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            Aero_ClientArrayBuffer.vertex(x + tri[0] * invScale,
                y + tri[1] * invScale, z + tri[2] * invScale, tri[3], tri[4]);
            Aero_ClientArrayBuffer.vertex(x + tri[5] * invScale,
                y + tri[6] * invScale, z + tri[7] * invScale, tri[8], tri[9]);
            Aero_ClientArrayBuffer.vertex(x + tri[10] * invScale,
                y + tri[11] * invScale, z + tri[12] * invScale, tri[13], tri[14]);
        }
    }

    static void emitBone(float[][] tris, float invScale, Aero_BoneRenderPose pose,
            double x, double y, double z) {
        boolean noRotation = pose.rotX == 0f && pose.rotY == 0f && pose.rotZ == 0f;
        boolean noScale = pose.scaleX == 1f && pose.scaleY == 1f && pose.scaleZ == 1f;
        if (noRotation) {
            if (noScale) emitTranslate(tris, invScale, pose, x, y, z);
            else emitScaleTranslate(tris, invScale, pose, x, y, z);
            return;
        }
        emitRotated(tris, invScale, pose, x, y, z);
    }

    private static void emitTranslate(float[][] tris, float invScale,
            Aero_BoneRenderPose pose, double x, double y, double z) {
        double baseX = x + pose.offsetX;
        double baseY = y + pose.offsetY;
        double baseZ = z + pose.offsetZ;
        boolean uvIdentity = pose.uvIsIdentity();
        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            for (int vertex = 0; vertex < 3; vertex++) {
                int off = vertex * 5;
                uvVertex(tri, off, baseX + tri[off] * invScale,
                    baseY + tri[off + 1] * invScale,
                    baseZ + tri[off + 2] * invScale, pose, uvIdentity);
            }
        }
    }

    private static void emitScaleTranslate(float[][] tris, float invScale,
            Aero_BoneRenderPose pose, double x, double y, double z) {
        float postX = pose.pivotX + pose.offsetX;
        float postY = pose.pivotY + pose.offsetY;
        float postZ = pose.pivotZ + pose.offsetZ;
        boolean uvIdentity = pose.uvIsIdentity();
        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            for (int vertex = 0; vertex < 3; vertex++) {
                int off = vertex * 5;
                float lx = (tri[off] * invScale - pose.pivotX) * pose.scaleX + postX;
                float ly = (tri[off + 1] * invScale - pose.pivotY) * pose.scaleY + postY;
                float lz = (tri[off + 2] * invScale - pose.pivotZ) * pose.scaleZ + postZ;
                uvVertex(tri, off, x + lx, y + ly, z + lz, pose, uvIdentity);
            }
        }
    }

    private static void emitRotated(float[][] tris, float invScale,
            Aero_BoneRenderPose pose, double x, double y, double z) {
        float radians = (float) (Math.PI / 180.0);
        float cx = (float) Math.cos(pose.rotX * radians), sx = (float) Math.sin(pose.rotX * radians);
        float cy = (float) Math.cos(pose.rotY * radians), sy = (float) Math.sin(pose.rotY * radians);
        float cz = (float) Math.cos(pose.rotZ * radians), sz = (float) Math.sin(pose.rotZ * radians);
        float postX = pose.pivotX + pose.offsetX;
        float postY = pose.pivotY + pose.offsetY;
        float postZ = pose.pivotZ + pose.offsetZ;
        boolean uvIdentity = pose.uvIsIdentity();
        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            for (int vertex = 0; vertex < 3; vertex++) {
                int off = vertex * 5;
                float lx = (tri[off] * invScale - pose.pivotX) * pose.scaleX;
                float ly = (tri[off + 1] * invScale - pose.pivotY) * pose.scaleY;
                float lz = (tri[off + 2] * invScale - pose.pivotZ) * pose.scaleZ;
                float ny = ly * cx - lz * sx, nz = ly * sx + lz * cx;
                ly = ny; lz = nz;
                float nx = lx * cy + lz * sy;
                nz = -lx * sy + lz * cy;
                lx = nx; lz = nz;
                nx = lx * cz - ly * sz;
                ny = lx * sz + ly * cz;
                uvVertex(tri, off, x + nx + postX, y + ny + postY,
                    z + lz + postZ, pose, uvIdentity);
            }
        }
    }

    private static void uvVertex(float[] tri, int off, double x, double y, double z,
            Aero_BoneRenderPose pose, boolean uvIdentity) {
        float u = tri[off + 3], v = tri[off + 4];
        if (!uvIdentity) {
            u = u * pose.uScale + pose.uOffset;
            v = v * pose.vScale + pose.vOffset;
        }
        Aero_ClientArrayBuffer.vertex(x, y, z, u, v);
    }
}
