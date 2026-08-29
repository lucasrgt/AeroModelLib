package aero.modellib;

import aero.modellib.optimization.OptimizationRef;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Reusable interleaved position/UV/color buffer for OpenGL 1.1 draws. */
@OptimizationRef({"aero.render.client-vertex-arrays"})
final class Aero_ClientArrayBuffer {
    private static final int INTS_PER_VERTEX = 6;
    private static final boolean LITTLE_ENDIAN =
        ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static int[] data = new int[65_536];
    private static ByteBuffer directBytes = BufferUtils.createByteBuffer(data.length * 4);
    private static IntBuffer directInts = directBytes.asIntBuffer();
    private static int color, position, vertices, drawsThisFrame, verticesThisFrame;

    private Aero_ClientArrayBuffer() {}

    static void beginClientState() {
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
    }

    static void endClientState() {
        GL11.glPopClientAttrib();
    }

    static void begin() {
        position = 0;
        vertices = 0;
    }

    static void color(float r, float g, float b, float a) {
        int red = component(r), green = component(g);
        int blue = component(b), alpha = component(a);
        color = LITTLE_ENDIAN
            ? red | green << 8 | blue << 16 | alpha << 24
            : red << 24 | green << 16 | blue << 8 | alpha;
    }

    static void vertex(double x, double y, double z, float u, float v) {
        ensure(INTS_PER_VERTEX);
        data[position++] = Float.floatToRawIntBits(u);
        data[position++] = Float.floatToRawIntBits(v);
        data[position++] = color;
        data[position++] = Float.floatToRawIntBits((float) x);
        data[position++] = Float.floatToRawIntBits((float) y);
        data[position++] = Float.floatToRawIntBits((float) z);
        vertices++;
    }

    static void draw() {
        if (vertices == 0) return;
        directInts.clear();
        directInts.put(data, 0, position);
        directBytes.position(0);
        directBytes.limit(position * 4);
        GL11.glInterleavedArrays(GL11.GL_T2F_C4UB_V3F, 0, directBytes);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices);
        drawsThisFrame++;
        verticesThisFrame += vertices;
    }

    static void beginFrameCounters() {
        drawsThisFrame = verticesThisFrame = 0;
    }

    public static int drawsThisFrame() { return drawsThisFrame; }
    public static int verticesThisFrame() { return verticesThisFrame; }

    private static void ensure(int additional) {
        int needed = position + additional;
        if (needed <= data.length) return;
        int capacity = data.length;
        while (capacity < needed) capacity = capacity + (capacity >> 1);
        int[] grown = new int[capacity];
        System.arraycopy(data, 0, grown, 0, position);
        data = grown;
        directBytes = BufferUtils.createByteBuffer(capacity * 4);
        directInts = directBytes.asIntBuffer();
    }

    private static int component(float value) {
        return Math.max(0, Math.min(255, (int) (value * 255.0f)));
    }
}
