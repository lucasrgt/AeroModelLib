import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Serializes the converter's normalized document into Aero animation JSON. */
final class Aero_ConvertWriter {
    private Aero_ConvertWriter() {}

    static void writePivots(StringBuilder output, Map pivots) {
        Iterator entries = pivots.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            double[] value = (double[]) entry.getValue();
            output.append("    \"").append(entry.getKey()).append("\": [")
                .append(number(value[0])).append(", ").append(number(value[1])).append(", ")
                .append(number(value[2])).append("]");
            if (entries.hasNext()) output.append(",");
            output.append("\n");
        }
    }

    static void writeChildMap(StringBuilder output, Map children) {
        Iterator entries = children.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            output.append("    \"").append(entry.getKey()).append("\": \"")
                .append(entry.getValue()).append("\"");
            if (entries.hasNext()) output.append(",");
            output.append("\n");
        }
    }

    static void writeAnimations(StringBuilder output, Map animations) {
        Iterator entries = animations.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            Map clip = (Map) entry.getValue();
            output.append("    \"").append(entry.getKey()).append("\": {\n")
                .append("      \"loop\": \"").append(clip.get("loop")).append("\",\n")
                .append("      \"length\": ").append(number(((Number) clip.get("length")).doubleValue()))
                .append(",\n      \"bones\": {\n");
            writeBones(output, (Map) clip.get("bones"));
            output.append("      }\n    }");
            if (entries.hasNext()) output.append(",");
            output.append("\n");
        }
    }

    private static void writeBones(StringBuilder output, Map bones) {
        Iterator entries = bones.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            output.append("        \"").append(entry.getKey()).append("\": {\n");
            writeChannels(output, (Map) entry.getValue());
            output.append("        }");
            if (entries.hasNext()) output.append(",");
            output.append("\n");
        }
    }

    private static void writeChannels(StringBuilder output, Map channels) {
        Iterator entries = channels.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            output.append("          \"").append(entry.getKey()).append("\": {\n");
            writeKeyframes(output, (Map) entry.getValue());
            output.append("          }");
            if (entries.hasNext()) output.append(",");
            output.append("\n");
        }
    }

    private static void writeKeyframes(StringBuilder output, Map keyframes) {
        List entries = new ArrayList(keyframes.entrySet());
        Collections.sort(entries, new Comparator() {
            public int compare(Object left, Object right) {
                double a = Double.parseDouble((String) ((Map.Entry) left).getKey());
                double b = Double.parseDouble((String) ((Map.Entry) right).getKey());
                return Double.compare(a, b);
            }
        });
        Iterator iterator = entries.iterator();
        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            Map frame = (Map) entry.getValue();
            double[] value = (double[]) frame.get("value");
            output.append("            \"").append(entry.getKey()).append("\": { \"value\": [")
                .append(number(value[0])).append(", ").append(number(value[1])).append(", ")
                .append(number(value[2])).append("], \"interp\": \"")
                .append(frame.get("interp")).append("\" }");
            if (iterator.hasNext()) output.append(",");
            output.append("\n");
        }
    }

    private static String number(double value) {
        if (value == Math.floor(value) && Math.abs(value) < 1e10) return String.valueOf((int) value);
        String text = String.valueOf(value);
        if (!text.contains(".") || text.contains("E") || text.contains("e")) return text;
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
