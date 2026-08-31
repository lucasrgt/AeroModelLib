package aero.modellib;

import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.optimization.OptimizationRef;
import aero.modellib.render.Aero_RenderOptions;

/** Immutable identity and render-state key for an animated batch. */
@OptimizationRef({"aero.render.batcher-state-sort"})
final class Aero_AnimatedBatchKey implements Comparable<Aero_AnimatedBatchKey> {
    final Aero_MeshModel model;
    final String texturePath;
    final int textureHash, tintRBits, tintGBits, tintBBits, alphaBits, alphaClipBits;
    final Aero_MeshBlendMode blend;
    final boolean depthTest, cullFaces;
    final int hash;

    Aero_AnimatedBatchKey(Aero_MeshModel model, String texture, Aero_RenderOptions options) {
        this.model = model; texturePath = texture;
        textureHash = texture != null ? texture.hashCode() : 0;
        tintRBits = Float.floatToIntBits(options.tintR);
        tintGBits = Float.floatToIntBits(options.tintG);
        tintBBits = Float.floatToIntBits(options.tintB);
        alphaBits = Float.floatToIntBits(options.alpha);
        alphaClipBits = Float.floatToIntBits(options.alphaClip);
        blend = options.blend; depthTest = options.depthTest; cullFaces = options.cullFaces;
        hash = hash(model, textureHash, tintRBits, tintGBits, tintBBits,
            alphaBits, alphaClipBits, blend, depthTest, cullFaces);
    }

    public int hashCode() { return hash; }
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Aero_AnimatedBatchKey)) return false;
        return matches((Aero_AnimatedBatchKey) object);
    }

    boolean matches(Aero_AnimatedBatchKey other) {
        return model == other.model && tintRBits == other.tintRBits && tintGBits == other.tintGBits
            && tintBBits == other.tintBBits && alphaBits == other.alphaBits
            && alphaClipBits == other.alphaClipBits && blend == other.blend
            && depthTest == other.depthTest && cullFaces == other.cullFaces
            && sameTexture(texturePath, other.texturePath);
    }

    public int compareTo(Aero_AnimatedBatchKey other) {
        int result = compareTexture(texturePath, other.texturePath);
        if (result == 0) result = blend.ordinal() - other.blend.ordinal();
        if (result == 0) result = bool(depthTest, other.depthTest);
        if (result == 0) result = bool(cullFaces, other.cullFaces);
        if (result == 0) result = integer(alphaClipBits, other.alphaClipBits);
        if (result == 0) result = integer(alphaBits, other.alphaBits);
        if (result == 0) result = integer(tintRBits, other.tintRBits);
        if (result == 0) result = integer(tintGBits, other.tintGBits);
        if (result == 0) result = integer(tintBBits, other.tintBBits);
        // Identity hashes vary between JVMs and made otherwise identical
        // render-state sorts swap model batches across launches. Model names
        // are stable resource identities; equal names deliberately preserve
        // the collector's insertion order.
        if (result == 0) result = compareTexture(model.name, other.model.name);
        return result;
    }

    static int hash(Aero_MeshModel model, int texture, int red, int green, int blue,
            int alpha, int clip, Aero_MeshBlendMode blend, boolean depth, boolean cull) {
        int result = System.identityHashCode(model);
        int[] values = {texture, red, green, blue, alpha, clip, blend.hashCode(), depth ? 1 : 0, cull ? 1 : 0};
        for (int index = 0; index < values.length; index++) result = 31 * result + values[index];
        return result;
    }

    static boolean sameTexture(String left, String right) {
        return left == right || left != null && left.equals(right);
    }
    private static int compareTexture(String left, String right) {
        if (left == right) return 0;
        if (left == null) return -1;
        return right == null ? 1 : left.compareTo(right);
    }
    private static int bool(boolean left, boolean right) { return left == right ? 0 : left ? 1 : -1; }
    private static int integer(int left, int right) { return left < right ? -1 : left == right ? 0 : 1; }
}
