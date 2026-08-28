package aero.modellib.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable animation clip assembled through {@link #builder(String)}. */
public final class Aero_AnimationClip {
    public final String name;
    public final Aero_AnimationLoop loop;
    public final float length;
    public final String[] boneNames;
    final BoneTrack[] bones;
    final KeyframeEvent[] events;
    private final Map boneIndexByName;
    private final boolean hasUvAnimation;
    private String cachedBoneNameRef;
    private int cachedBoneIdx;

    public static Builder builder(String name) { return new Builder(name); }
    private Aero_AnimationClip(Builder builder) {
        if (builder.name == null || builder.name.length() == 0) {
            throw new IllegalArgumentException("clip name must not be empty");
        }
        if (builder.length < 0f || Float.isNaN(builder.length) || Float.isInfinite(builder.length)) {
            throw new IllegalArgumentException("clip '" + builder.name
                + "': length must be finite and >= 0, got " + builder.length);
        }
        name = builder.name; loop = builder.loop; length = builder.length;
        bones = new BoneTrack[builder.bones.size()]; boneNames = new String[bones.length];
        boolean uv = false;
        for (int index = 0; index < bones.length; index++) {
            BoneBuilder bone = (BoneBuilder) builder.bones.get(index);
            bones[index] = bone.build(); boneNames[index] = bones[index].name;
            uv |= bones[index].uvOffset != null || bones[index].uvScale != null;
        }
        hasUvAnimation = uv; boneIndexByName = buildBoneIndex(boneNames);
        Collections.sort(builder.events, new Comparator() {
            public int compare(Object left, Object right) {
                return Float.compare(((KeyframeEvent) left).time, ((KeyframeEvent) right).time);
            }
        });
        events = (KeyframeEvent[]) builder.events.toArray(new KeyframeEvent[builder.events.size()]);
    }
    public boolean hasEvents() { return events.length > 0; }
    public boolean hasUvAnimation() { return hasUvAnimation; }
    public int indexOfBone(String boneName) {
        if (boneName == cachedBoneNameRef) return cachedBoneIdx;
        Integer index = (Integer) boneIndexByName.get(boneName);
        cachedBoneNameRef = boneName; cachedBoneIdx = index != null ? index.intValue() : -1;
        return cachedBoneIdx;
    }
    public boolean sampleRotInto(int bone, float time, float[] out) { return sample(bone, time, out, Channel.ROTATION, null); }
    public boolean sampleRotInto(int bone, float time, float[] out, int[] cursor) { return sample(bone, time, out, Channel.ROTATION, cursor); }
    public boolean samplePosInto(int bone, float time, float[] out) { return sample(bone, time, out, Channel.POSITION, null); }
    public boolean samplePosInto(int bone, float time, float[] out, int[] cursor) { return sample(bone, time, out, Channel.POSITION, cursor); }
    public boolean sampleSclInto(int bone, float time, float[] out) { return sample(bone, time, out, Channel.SCALE, null); }
    public boolean sampleSclInto(int bone, float time, float[] out, int[] cursor) { return sample(bone, time, out, Channel.SCALE, cursor); }
    public boolean sampleUvOffsetInto(int bone, float time, float[] out) { return sample(bone, time, out, Channel.UV_OFFSET, null); }
    public boolean sampleUvOffsetInto(int bone, float time, float[] out, int[] cursor) { return sample(bone, time, out, Channel.UV_OFFSET, cursor); }
    public boolean sampleUvScaleInto(int bone, float time, float[] out) { return sample(bone, time, out, Channel.UV_SCALE, null); }
    public boolean sampleUvScaleInto(int bone, float time, float[] out, int[] cursor) { return sample(bone, time, out, Channel.UV_SCALE, cursor); }
    private boolean sample(int bone, float time, float[] out, Channel channel, int[] cursor) {
        if (bone < 0 || bone >= bones.length) return false;
        Aero_AnimationChannelTrack track = bones[bone].track(channel);
        return track != null && track.sampleInto(time, out, cursor, bone);
    }
    private static Map buildBoneIndex(String[] names) {
        Map result = new HashMap((names.length * 4 / 3) + 1);
        for (int index = 0; index < names.length; index++) result.put(names[index], Integer.valueOf(index));
        return result;
    }
    private enum Channel { ROTATION, POSITION, SCALE, UV_OFFSET, UV_SCALE }
    static final class BoneTrack {
        final String name;
        final Aero_AnimationChannelTrack rotation, position, scale, uvOffset, uvScale;
        BoneTrack(String name, Aero_AnimationChannelTrack rotation, Aero_AnimationChannelTrack position,
                Aero_AnimationChannelTrack scale, Aero_AnimationChannelTrack uvOffset, Aero_AnimationChannelTrack uvScale) {
            this.name = name; this.rotation = rotation; this.position = position; this.scale = scale;
            this.uvOffset = uvOffset; this.uvScale = uvScale;
        }
        Aero_AnimationChannelTrack track(Channel channel) {
            switch (channel) {
                case ROTATION: return rotation;
                case POSITION: return position;
                case SCALE: return scale;
                case UV_OFFSET: return uvOffset;
                default: return uvScale;
            }
        }
    }
    static final class KeyframeEvent {
        final float time; final String channel, data, locator;
        KeyframeEvent(float time, String channel, String data, String locator) {
            this.time = time; this.channel = channel; this.data = data; this.locator = locator;
        }
    }
    public static final class Builder {
        private final String name;
        private Aero_AnimationLoop loop = Aero_AnimationLoop.PLAY_ONCE;
        private float length = 1f;
        private final List bones = new ArrayList();
        private final Map bonesByName = new HashMap();
        private final List events = new ArrayList();
        private Builder(String name) { this.name = name; }
        public Builder loop(Aero_AnimationLoop value) {
            if (value == null) throw new IllegalArgumentException("loop must not be null");
            loop = value; return this;
        }
        public Builder length(float value) { length = value; return this; }
        public BoneBuilder bone(String boneName) {
            if (boneName == null || boneName.length() == 0) throw new IllegalArgumentException("clip '" + name + "': bone name must not be empty");
            BoneBuilder bone = (BoneBuilder) bonesByName.get(boneName);
            if (bone == null) { bone = new BoneBuilder(this, boneName); bonesByName.put(boneName, bone); bones.add(bone); }
            return bone;
        }
        public Builder event(float time, String channel, String data, String locator) {
            Aero_AnimationValidation.event(name, time, channel, data);
            events.add(new KeyframeEvent(time, channel, data, locator)); return this;
        }
        public Aero_AnimationClip build() { return new Aero_AnimationClip(this); }
    }
    public static final class BoneBuilder {
        private final Builder owner; private final String name;
        private Aero_AnimationChannelTrack rotation, position, scale, uvOffset, uvScale;
        private BoneBuilder(Builder owner, String name) { this.owner = owner; this.name = name; }
        public BoneBuilder rotation(float[] t, float[][] v, Aero_Easing[] e) { rotation = track("rotation", t, v, e); return this; }
        public BoneBuilder position(float[] t, float[][] v, Aero_Easing[] e) { position = track("position", t, v, e); return this; }
        public BoneBuilder scale(float[] t, float[][] v, Aero_Easing[] e) { scale = track("scale", t, v, e); return this; }
        public BoneBuilder uvOffset(float[] t, float[][] v, Aero_Easing[] e) { uvOffset = track("uv_offset", t, v, e); return this; }
        public BoneBuilder uvScale(float[] t, float[][] v, Aero_Easing[] e) { uvScale = track("uv_scale", t, v, e); return this; }
        public Builder endBone() { return owner; }
        private Aero_AnimationChannelTrack track(String kind, float[] t, float[][] v, Aero_Easing[] e) {
            return new Aero_AnimationChannelTrack(owner.name, name, kind, t, v, e);
        }
        private BoneTrack build() { return new BoneTrack(name, rotation, position, scale, uvOffset, uvScale); }
    }
}
