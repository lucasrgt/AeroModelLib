package aero.modellib;

import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;

/** Mutable allocation-free lookup probe compatible with immutable batch keys. */
final class Aero_AnimatedBatchLookupKey {
    private Aero_MeshModel model;
    private String texture;
    private int red, green, blue, alpha, clip;
    private Aero_MeshBlendMode blend;
    private boolean depth, cull;
    private int hash;

    Aero_AnimatedBatchLookupKey set(Aero_MeshModel model, String texture, Aero_RenderOptions options) {
        this.model = model; this.texture = texture;
        red = Float.floatToIntBits(options.tintR); green = Float.floatToIntBits(options.tintG);
        blue = Float.floatToIntBits(options.tintB); alpha = Float.floatToIntBits(options.alpha);
        clip = Float.floatToIntBits(options.alphaClip); blend = options.blend;
        depth = options.depthTest; cull = options.cullFaces;
        hash = Aero_AnimatedBatchKey.hash(model, texture != null ? texture.hashCode() : 0,
            red, green, blue, alpha, clip, blend, depth, cull);
        return this;
    }

    public int hashCode() { return hash; }
    public boolean equals(Object object) {
        if (!(object instanceof Aero_AnimatedBatchKey)) return false;
        Aero_AnimatedBatchKey other = (Aero_AnimatedBatchKey) object;
        if (model != other.model || !sameTint(other)) return false;
        if (!sameState(other)) return false;
        return Aero_AnimatedBatchKey.sameTexture(texture, other.texturePath);
    }

    private boolean sameTint(Aero_AnimatedBatchKey other) {
        return red == other.tintRBits && green == other.tintGBits
            && blue == other.tintBBits && alpha == other.alphaBits
            && clip == other.alphaClipBits;
    }

    private boolean sameState(Aero_AnimatedBatchKey other) {
        return blend == other.blend && depth == other.depthTest && cull == other.cullFaces;
    }
}
