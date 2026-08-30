package aero.modellib.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable ordered collection of {@link Aero_AnimationLayer layers} sampled
 * together into one pose per bone.
 */
public final class Aero_AnimationStack {

    private static final Aero_AnimationLayer[] EMPTY_LAYERS = new Aero_AnimationLayer[0];

    private final Aero_AnimationLayer[] layers;

    // Reused per-frame so sampleRot/Pos/Scl don't allocate.
    private final float[] tmp = new float[3];

    public static Builder builder() {
        return new Builder();
    }

    public static Aero_AnimationStack empty() {
        return new Aero_AnimationStack(EMPTY_LAYERS);
    }

    private Aero_AnimationStack(Aero_AnimationLayer[] layers) {
        this.layers = layers;
    }

    public Aero_AnimationLayer get(int index) {
        return layers[index];
    }

    public int size() {
        return layers.length;
    }

    /** Advances every layer's playback by one game tick. */
    public void tick() {
        for (int i = 0; i < layers.length; i++) {
            layers[i].getPlayback().tick();
        }
    }

    public boolean sampleRot(String boneName, float partialTick, float[] out) {
        return sampleChannel(boneName, partialTick, out, CHANNEL_ROT);
    }

    public boolean samplePos(String boneName, float partialTick, float[] out) {
        return sampleChannel(boneName, partialTick, out, CHANNEL_POS);
    }

    public boolean sampleScl(String boneName, float partialTick, float[] out) {
        out[0] = 1f; out[1] = 1f; out[2] = 1f;
        return sampleChannel(boneName, partialTick, out, CHANNEL_SCL);
    }

    /**
     * Samples rotation, position and scale in one layer walk. Renderers use
     * this to avoid doing the same clip lookup, bone-name map lookup and
     * interpolated-time calculation once per channel.
     */
    public boolean samplePose(String boneName, float partialTick,
                              float[] outRot, float[] outPos, float[] outScl) {
        return samplePose(boneName, partialTick, outRot, outPos, outScl, null, null);
    }

    /**
     * Samples rotation, position, scale plus UV offset/scale in one layer
     * walk. Pass {@code null} for outUvOffset/outUvScale to skip UV
     * sampling — same fast-path as the 5-arg overload.
     */
    public boolean samplePose(String boneName, float partialTick,
                              float[] outRot, float[] outPos, float[] outScl,
                              float[] outUvOffset, float[] outUvScale) {
        if (outRot == null || outPos == null || outScl == null) {
            throw new IllegalArgumentException("pose outputs must not be null");
        }
        resetPose(outRot, outPos, outScl, outUvOffset, outUvScale);
        boolean any = false;
        for (int i = 0; i < layers.length; i++) {
            if (sampleLayerPose(layers[i], boneName, partialTick, outRot, outPos,
                    outScl, outUvOffset, outUvScale)) any = true;
        }
        return any;
    }

    private void resetPose(float[] rotation, float[] position, float[] scale,
                           float[] uvOffset, float[] uvScale) {
        reset(rotation, 0f);
        reset(position, 0f);
        reset(scale, 1f);
        if (uvOffset != null) reset(uvOffset, 0f);
        if (uvScale != null) reset(uvScale, 1f);
    }

    private boolean sampleLayerPose(Aero_AnimationLayer layer, String boneName,
            float partialTick, float[] rotation, float[] position, float[] scale,
            float[] uvOffset, float[] uvScale) {
        Aero_AnimationPlayback playback = layer.getPlayback();
        Aero_AnimationClip clip = playback.getCurrentClip();
        if (clip == null) return false;
        int bone = clip.indexOfBone(boneName);
        if (bone < 0) return false;
        float time = playback.getInterpolatedTime(partialTick);
        boolean any = sampleLayerChannel(playback, clip, bone, boneName, time, partialTick,
            rotation, layer, CHANNEL_ROT);
        any |= sampleLayerChannel(playback, clip, bone, boneName, time, partialTick,
            position, layer, CHANNEL_POS);
        any |= sampleLayerChannel(playback, clip, bone, boneName, time, partialTick,
            scale, layer, CHANNEL_SCL);
        if (uvOffset != null && playback.sampleUvOffsetBlended(
                clip, bone, boneName, time, partialTick, tmp)) {
            compose(uvOffset, tmp, layer.getWeight(), layer.isAdditive(), CHANNEL_POS);
            any = true;
        }
        if (uvScale != null && playback.sampleUvScaleBlended(
                clip, bone, boneName, time, partialTick, tmp)) {
            compose(uvScale, tmp, layer.getWeight(), layer.isAdditive(), CHANNEL_SCL);
            any = true;
        }
        return any;
    }

    private boolean sampleLayerChannel(Aero_AnimationPlayback playback,
            Aero_AnimationClip clip, int bone, String boneName, float time,
            float partialTick, float[] output, Aero_AnimationLayer layer, int channel) {
        boolean sampled;
        if (channel == CHANNEL_ROT)
            sampled = playback.sampleRotBlended(clip, bone, boneName, time, partialTick, tmp);
        else if (channel == CHANNEL_POS)
            sampled = playback.samplePosBlended(clip, bone, boneName, time, partialTick, tmp);
        else sampled = playback.sampleSclBlended(clip, bone, boneName, time, partialTick, tmp);
        if (!sampled) return false;
        compose(output, tmp, layer.getWeight(), layer.isAdditive(), channel);
        return true;
    }

    private static void reset(float[] output, float value) {
        output[0] = value; output[1] = value; output[2] = value;
    }

    private static final int CHANNEL_ROT = 0;
    private static final int CHANNEL_POS = 1;
    private static final int CHANNEL_SCL = 2;

    private boolean sampleChannel(String boneName, float partialTick, float[] out, int channel) {
        if (channel != CHANNEL_SCL) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f;
        }

        boolean any = false;
        for (int i = 0; i < layers.length; i++) {
            Aero_AnimationLayer layer = layers[i];
            Aero_AnimationPlayback pb = layer.getPlayback();
            Aero_AnimationClip clip = pb.getCurrentClip();
            if (clip == null) continue;
            int bi = clip.indexOfBone(boneName);
            if (bi < 0) continue;

            float time = pb.getInterpolatedTime(partialTick);
            boolean got;
            switch (channel) {
                case CHANNEL_ROT: got = pb.sampleRotBlended(clip, bi, boneName, time, partialTick, tmp); break;
                case CHANNEL_POS: got = pb.samplePosBlended(clip, bi, boneName, time, partialTick, tmp); break;
                default:          got = pb.sampleSclBlended(clip, bi, boneName, time, partialTick, tmp); break;
            }
            if (!got) continue;

            compose(out, tmp, layer.getWeight(), layer.isAdditive(), channel);
            any = true;
        }
        return any;
    }

    private static void compose(float[] out, float[] value, float weight,
                                boolean additive, int channel) {
        if (additive) {
            if (channel == CHANNEL_SCL) {
                out[0] *= 1f + (value[0] - 1f) * weight;
                out[1] *= 1f + (value[1] - 1f) * weight;
                out[2] *= 1f + (value[2] - 1f) * weight;
            } else {
                out[0] += value[0] * weight;
                out[1] += value[1] * weight;
                out[2] += value[2] * weight;
            }
        } else {
            out[0] = out[0] + (value[0] - out[0]) * weight;
            out[1] = out[1] + (value[1] - out[1]) * weight;
            out[2] = out[2] + (value[2] - out[2]) * weight;
        }
    }

    public static final class Builder {
        private final List layers = new ArrayList();

        private Builder() {}

        public Builder add(Aero_AnimationLayer layer) {
            if (layer == null) throw new IllegalArgumentException("layer must not be null");
            layers.add(layer);
            return this;
        }

        public Builder replace(Aero_AnimationPlayback playback) {
            return add(Aero_AnimationLayer.replace(playback));
        }

        public Builder additive(Aero_AnimationPlayback playback) {
            return add(Aero_AnimationLayer.additive(playback));
        }

        public Builder additive(Aero_AnimationPlayback playback, float weight) {
            return add(Aero_AnimationLayer.builder(playback).additive(true).weight(weight).build());
        }

        public Aero_AnimationStack build() {
            if (layers.isEmpty()) return Aero_AnimationStack.empty();
            return new Aero_AnimationStack((Aero_AnimationLayer[])
                layers.toArray(new Aero_AnimationLayer[layers.size()]));
        }
    }
}
