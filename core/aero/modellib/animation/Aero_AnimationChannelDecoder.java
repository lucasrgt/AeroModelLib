package aero.modellib.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Strict channel and event decoder shared by all animation document loads. */
final class Aero_AnimationChannelDecoder {
    private Aero_AnimationChannelDecoder() {}

    static Channel decode(String clip, String bone, String kind, Map keyframes) {
        String context = "clip '" + clip + "' bone '" + bone + "' " + kind;
        List rows = new ArrayList();
        Iterator entries = keyframes.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            float time = Float.parseFloat((String) entry.getKey());
            Object value = entry.getValue();
            if (!(value instanceof Map)) fail(context, time, "pose keyframe must be an object", value);
            Map object = (Map) value;
            Object vector = object.get("value");
            Object interpolation = object.get("interp");
            if (!(vector instanceof List)) fail(context, time, "pose keyframe value must be an array", vector);
            if (!(interpolation instanceof String))
                fail(context, time, "pose keyframe interp must be a string", interpolation);
            List values = (List) vector;
            float[] xyz = new float[] {number(values.get(0)), number(values.get(1)), number(values.get(2))};
            Aero_Easing easing;
            try { easing = Aero_Easing.fromName((String) interpolation); }
            catch (IllegalArgumentException error) {
                throw new RuntimeException(context + " @t=" + time + ": " + error.getMessage(), error);
            }
            rows.add(new Object[] {Float.valueOf(time), xyz, easing});
        }
        sort(rows);
        Channel result = new Channel(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = (Object[]) rows.get(i);
            result.times[i] = ((Float) row[0]).floatValue();
            result.values[i] = (float[]) row[1];
            result.easings[i] = (Aero_Easing) row[2];
        }
        return result;
    }

    static void decodeEvents(String clip, Map channels, Aero_AnimationClip.Builder builder) {
        List rows = new ArrayList();
        Iterator channelEntries = channels.entrySet().iterator();
        while (channelEntries.hasNext()) {
            Map.Entry channel = (Map.Entry) channelEntries.next();
            if (!(channel.getValue() instanceof Map)) continue;
            Iterator events = ((Map) channel.getValue()).entrySet().iterator();
            while (events.hasNext()) {
                Map.Entry event = (Map.Entry) events.next();
                float time = Float.parseFloat((String) event.getKey());
                String context = "clip '" + clip + "' channel '" + channel.getKey() + "'";
                Object value = event.getValue();
                if (!(value instanceof Map)) fail(context, time, "event keyframe must be an object", value);
                Map object = (Map) value;
                Object name = object.get("name");
                if (!(name instanceof String)) fail(context, time, "event name must be a string", name);
                Object locator = object.get("locator");
                rows.add(new Object[] {Float.valueOf(time), channel.getKey(), name,
                    locator == null ? null : String.valueOf(locator)});
            }
        }
        sort(rows);
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = (Object[]) rows.get(i);
            builder.event(((Float) row[0]).floatValue(), (String) row[1],
                (String) row[2], (String) row[3]);
        }
    }

    private static void sort(List rows) {
        Collections.sort(rows, new Comparator() {
            public int compare(Object left, Object right) {
                return ((Float) ((Object[]) left)[0]).compareTo((Float) ((Object[]) right)[0]);
            }
        });
    }

    private static float number(Object value) { return Aero_AnimationBundleDecoder.number(value); }

    private static void fail(String context, float time, String message, Object value) {
        throw new RuntimeException(context + " @t=" + time + ": " + message + ", got "
            + (value == null ? "null" : value.getClass().getSimpleName()));
    }

    static final class Channel {
        final float[] times;
        final float[][] values;
        final Aero_Easing[] easings;

        Channel(int size) {
            times = new float[size];
            values = new float[size][];
            easings = new Aero_Easing[size];
        }
    }
}
