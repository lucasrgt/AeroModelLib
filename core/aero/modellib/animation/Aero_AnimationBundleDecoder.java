package aero.modellib.animation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Converts a parsed animation document into immutable runtime objects. */
final class Aero_AnimationBundleDecoder {
    private Aero_AnimationBundleDecoder() {}

    static Aero_AnimationBundle decode(Map root) {
        validateVersion(root);
        return new Aero_AnimationBundle(decodeClips(root), decodePivots(root),
            copyStringMap(root, "childMap"), copyStringMap(root, "morph_targets"));
    }

    private static void validateVersion(Map root) {
        if (!root.containsKey("format_version"))
            throw new RuntimeException("missing required \"format_version\" — expected \""
                + Aero_AnimationLoader.SUPPORTED_FORMAT_VERSION + "\"");
        Object value = root.get("format_version");
        if (!(value instanceof String)) throw new RuntimeException("format_version must be a string");
        boolean accepted = Aero_AnimationLoader.SUPPORTED_FORMAT_VERSION.equals(value);
        StringBuilder supported = new StringBuilder("\""
            + Aero_AnimationLoader.SUPPORTED_FORMAT_VERSION + "\"");
        for (int i = 0; i < Aero_AnimationLoader.BACKWARD_COMPAT_VERSIONS.length; i++) {
            String candidate = Aero_AnimationLoader.BACKWARD_COMPAT_VERSIONS[i];
            accepted |= candidate.equals(value);
            supported.append(", \"").append(candidate).append("\"");
        }
        if (!accepted) throw new RuntimeException("unsupported format_version \"" + value
            + "\" — this loader supports " + supported);
    }

    private static Map decodePivots(Map root) {
        Map result = new HashMap();
        if (!root.containsKey("pivots")) return result;
        Iterator entries = ((Map) root.get("pivots")).entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            List values = (List) entry.getValue();
            result.put(entry.getKey(), new float[] {
                number(values.get(0)) / 16f, number(values.get(1)) / 16f, number(values.get(2)) / 16f
            });
        }
        return result;
    }

    private static Map decodeClips(Map root) {
        Map result = new HashMap();
        if (!root.containsKey("animations")) return result;
        Iterator entries = ((Map) root.get("animations")).entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            String name = (String) entry.getKey();
            result.put(name, decodeClip(name, (Map) entry.getValue()));
        }
        return result;
    }

    private static Aero_AnimationClip decodeClip(String name, Map data) {
        Aero_AnimationLoop loop = decodeLoop(name, data);
        float length = data.containsKey("length") ? number(data.get("length")) : 1f;
        Aero_AnimationClip.Builder builder = Aero_AnimationClip.builder(name).loop(loop).length(length);
        Map bones = data.containsKey("bones") ? (Map) data.get("bones") : new HashMap();
        Iterator entries = bones.entrySet().iterator();
        while (entries.hasNext()) decodeBone(name, builder, (Map.Entry) entries.next());
        if (data.containsKey("keyframes"))
            Aero_AnimationChannelDecoder.decodeEvents(name, (Map) data.get("keyframes"), builder);
        return builder.build();
    }

    private static Aero_AnimationLoop decodeLoop(String name, Map data) {
        if (!data.containsKey("loop")) return Aero_AnimationLoop.PLAY_ONCE;
        Object value = data.get("loop");
        if (!(value instanceof String)) throw new RuntimeException("clip '" + name
            + "': loop must be a string, got " + type(value));
        try { return Aero_AnimationLoop.fromName((String) value); }
        catch (IllegalArgumentException error) {
            throw new RuntimeException("clip '" + name + "': " + error.getMessage(), error);
        }
    }

    private static void decodeBone(String clip, Aero_AnimationClip.Builder builder, Map.Entry entry) {
        String boneName = (String) entry.getKey();
        Map channels = (Map) entry.getValue();
        Aero_AnimationClip.BoneBuilder bone = builder.bone(boneName);
        decodeChannel(clip, boneName, "rotation", channels, bone);
        decodeChannel(clip, boneName, "position", channels, bone);
        decodeChannel(clip, boneName, "scale", channels, bone);
        decodeChannel(clip, boneName, "uv_offset", channels, bone);
        decodeChannel(clip, boneName, "uv_scale", channels, bone);
    }

    private static void decodeChannel(String clip, String boneName, String kind,
            Map channels, Aero_AnimationClip.BoneBuilder bone) {
        if (!channels.containsKey(kind)) return;
        Aero_AnimationChannelDecoder.Channel channel = Aero_AnimationChannelDecoder.decode(
            clip, boneName, kind, (Map) channels.get(kind));
        if ("rotation".equals(kind)) bone.rotation(channel.times, channel.values, channel.easings);
        else if ("position".equals(kind)) bone.position(channel.times, channel.values, channel.easings);
        else if ("scale".equals(kind)) bone.scale(channel.times, channel.values, channel.easings);
        else if ("uv_offset".equals(kind)) bone.uvOffset(channel.times, channel.values, channel.easings);
        else bone.uvScale(channel.times, channel.values, channel.easings);
    }

    private static Map copyStringMap(Map root, String key) {
        Map result = new HashMap();
        if (!root.containsKey(key)) return result;
        Iterator entries = ((Map) root.get(key)).entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            Object value = entry.getValue();
            if (!(value instanceof String)) throw new RuntimeException(key + "[\"" + entry.getKey()
                + "\"]: must be a string, got " + type(value));
            result.put(entry.getKey(), value);
        }
        return result;
    }

    static float number(Object value) {
        if (value instanceof Number) return ((Number) value).floatValue();
        return Float.parseFloat(value.toString());
    }

    private static String type(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
