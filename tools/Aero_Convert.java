import java.io.*;
import java.util.*;

/**
 * AeroModelLib — Blockbench .bbmodel to .anim.json converter.
 *
 * Extracts pivots, childMap, and animation keyframes from a Blockbench
 * project file and writes an .anim.json compatible with Aero_AnimationLoader.
 *
 * Usage:
 *   javac Aero_Convert.java && java Aero_Convert MyMachine.bbmodel
 *   javac Aero_Convert.java && java Aero_Convert MyMachine.bbmodel output.anim.json
 *
 * Requires: JDK 8+
 *
 * by lucasrgt — aerocoding.dev
 */
public class Aero_Convert {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("AeroModelLib Converter");
            System.out.println();
            System.out.println("Usage: java Aero_Convert <input.bbmodel> [output.anim.json]");
            System.out.println();
            System.out.println("Converts Blockbench .bbmodel files to .anim.json format");
            System.out.println("for use with AeroModelLib's animation system.");
            System.out.println();
            System.out.println("The OBJ model must be exported manually from Blockbench:");
            System.out.println("  File > Export > Export OBJ Model");
            System.out.println();
            System.out.println("See README.md for the full workflow.");
            return;
        }

        String input = args[0];
        File inputFile = new File(input);
        if (!inputFile.exists()) {
            System.err.println("Error: file not found: " + input);
            System.exit(1);
        }

        String output;
        if (args.length >= 2) {
            output = args[1];
        } else {
            output = input.replaceAll("\\.bbmodel$", ".anim.json");
            if (output.equals(input)) output = input + ".anim.json";
        }

        try {
            String src = readFile(inputFile);
            Map root = (Map) new Aero_ConvertJsonParser(src).parseValue();

            // 1. Build UUID → group map
            Map groupByUuid = new HashMap(); // uuid → Map (group)
            List groups = root.containsKey("groups") ? (List) root.get("groups") : new ArrayList();
            for (int i = 0; i < groups.size(); i++) {
                Map g = (Map) groups.get(i);
                String uuid = (String) g.get("uuid");
                if (uuid != null) groupByUuid.put(uuid, g);
            }

            // 2. Build UUID → element map
            Map elementByUuid = new HashMap();
            List elements = root.containsKey("elements") ? (List) root.get("elements") : new ArrayList();
            for (int i = 0; i < elements.size(); i++) {
                Map el = (Map) elements.get(i);
                String uuid = (String) el.get("uuid");
                if (uuid != null) elementByUuid.put(uuid, el);
            }

            // 3. Extract pivots from outliner
            Map pivots = new LinkedHashMap(); // name → float[3]
            List outliner = root.containsKey("outliner") ? (List) root.get("outliner") : new ArrayList();
            collectPivots(outliner, groupByUuid, pivots);

            // 4. Extract childMap from outliner
            Map childMap = new LinkedHashMap(); // child → parent
            for (int i = 0; i < outliner.size(); i++) {
                Object item = outliner.get(i);
                if (item instanceof String) continue;
                if (!(item instanceof Map)) continue;
                Map node = (Map) item;
                String uuid = (String) node.get("uuid");
                Map g = uuid != null ? (Map) groupByUuid.get(uuid) : null;
                String name = g != null ? (String) g.get("name") : (String) node.get("name");
                List children = node.containsKey("children") ? (List) node.get("children") : null;
                if (children != null && name != null) {
                    collectChildren(children, name, groupByUuid, elementByUuid, childMap);
                }
            }

            // 5. Extract animations
            Map animations = new LinkedHashMap(); // clipName → clip data
            List bbAnims = root.containsKey("animations") ? (List) root.get("animations") : new ArrayList();
            for (int a = 0; a < bbAnims.size(); a++) {
                Map bbAnim = (Map) bbAnims.get(a);
                String animName = bbAnim.containsKey("name") ? (String) bbAnim.get("name") : "clip_" + a;
                if (animName.startsWith("animation.")) {
                    animName = animName.substring("animation.".length());
                }

                Map clip = new LinkedHashMap();
                Object loopVal = bbAnim.get("loop");
                clip.put("loop", ("loop".equals(loopVal) || Boolean.TRUE.equals(loopVal))
                    ? "loop" : "play_once");
                clip.put("length", bbAnim.containsKey("length") ? toNumber(bbAnim.get("length")) : 1.0);

                Map bones = new LinkedHashMap();
                Map animators = bbAnim.containsKey("animators") ? (Map) bbAnim.get("animators") : new HashMap();
                Iterator ait = animators.entrySet().iterator();
                while (ait.hasNext()) {
                    Map.Entry ae = (Map.Entry) ait.next();
                    Map animator = (Map) ae.getValue();
                    String type = (String) animator.get("type");
                    if (!"bone".equals(type)) continue;

                    String boneName = (String) animator.get("name");
                    if (boneName == null) {
                        Map bg = (Map) groupByUuid.get(ae.getKey());
                        if (bg != null) boneName = (String) bg.get("name");
                    }
                    if (boneName == null) continue;

                    List keyframes = animator.containsKey("keyframes") ? (List) animator.get("keyframes") : new ArrayList();
                    if (keyframes.isEmpty()) continue;

                    Map boneData = new LinkedHashMap();
                    for (int k = 0; k < keyframes.size(); k++) {
                        Map kf = (Map) keyframes.get(k);
                        String channel = (String) kf.get("channel");
                        if (!"rotation".equals(channel) && !"position".equals(channel) && !"scale".equals(channel)) continue;

                        if (!boneData.containsKey(channel)) boneData.put(channel, new LinkedHashMap());
                        Map channelMap = (Map) boneData.get(channel);

                        List dataPoints = (List) kf.get("data_points");
                        Map dp = (Map) dataPoints.get(0);
                        double x = parseCoord(dp.get("x"));
                        double y = parseCoord(dp.get("y"));
                        double z = parseCoord(dp.get("z"));

                        // Resolve interpolation mode
                        String interp = "linear";
                        Object interpObj = kf.get("interpolation");
                        if ("catmullrom".equals(interpObj)) interp = "catmullrom";
                        else if ("step".equals(interpObj)) interp = "step";

                        Object timeObj = kf.get("time");
                        String timeKey = formatTime(toNumber(timeObj));

                        Map kfData = new LinkedHashMap();
                        kfData.put("value", new double[]{x, y, z});
                        kfData.put("interp", interp);
                        channelMap.put(timeKey, kfData);
                    }

                    if (!boneData.isEmpty()) {
                        bones.put(boneName, boneData);
                    }
                }

                if (!bones.isEmpty()) {
                    clip.put("bones", bones);
                    animations.put(animName, clip);
                }
            }

            // 6. Write output
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"format_version\": \"1.0\",\n");

            // Pivots
            sb.append("  \"pivots\": {\n");
            Aero_ConvertWriter.writePivots(sb, pivots);
            sb.append("  },\n");

            // ChildMap
            sb.append("  \"childMap\": {\n");
            Aero_ConvertWriter.writeChildMap(sb, childMap);
            sb.append("  },\n");

            // Animations
            sb.append("  \"animations\": {\n");
            Aero_ConvertWriter.writeAnimations(sb, animations);
            sb.append("  }\n");

            sb.append("}\n");

            FileOutputStream fos = new FileOutputStream(output);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();

            // 7. Summary
            int boneCount = 0;
            Iterator cit = animations.values().iterator();
            while (cit.hasNext()) {
                Map cl = (Map) cit.next();
                Map bn = (Map) cl.get("bones");
                if (bn != null) boneCount += bn.size();
            }

            System.out.println("Converted: " + input + " -> " + output);
            System.out.println();
            System.out.println("  Pivots:     " + pivots.size() + " bones");
            System.out.println("  ChildMap:   " + childMap.size() + " entries");
            System.out.println("  Animations: " + animations.size() + " clips, " + boneCount + " animated bones");
            System.out.println();

            if (animations.isEmpty()) {
                System.out.println("  ! No animations found. If your model has animations,");
                System.out.println("    make sure they are created in Blockbench before exporting.");
                System.out.println();
            }

            System.out.println("Next steps:");
            System.out.println("  1. Export OBJ from Blockbench: File > Export > Export OBJ Model");
            System.out.println("  2. Place both files in your mod resources (e.g. /models/)");
            System.out.println("  3. See README.md for the Java integration code");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // Outliner traversal
    // -----------------------------------------------------------------------

    private static void collectPivots(List items, Map groupByUuid, Map pivots) {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String) continue;
            if (!(item instanceof Map)) continue;

            Map node = (Map) item;
            String uuid = (String) node.get("uuid");
            Map g = uuid != null ? (Map) groupByUuid.get(uuid) : null;
            String name = g != null ? (String) g.get("name") : (String) node.get("name");
            List origin = g != null ? (List) g.get("origin") : (List) node.get("origin");

            if (name != null && origin != null) {
                pivots.put(name, new double[]{
                    toNumber(origin.get(0)),
                    toNumber(origin.get(1)),
                    toNumber(origin.get(2))
                });
            }

            List children = node.containsKey("children") ? (List) node.get("children") : null;
            if (children != null) collectPivots(children, groupByUuid, pivots);
        }
    }

    private static void collectChildren(List items, String parentName, Map groupByUuid, Map elementByUuid, Map childMap) {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof String) {
                // Element UUID
                Map el = (Map) elementByUuid.get(item);
                if (el != null && parentName != null) {
                    String elName = (String) el.get("name");
                    if (elName != null) childMap.put(elName, parentName);
                }
                continue;
            }
            if (!(item instanceof Map)) continue;

            Map node = (Map) item;
            String uuid = (String) node.get("uuid");
            Map g = uuid != null ? (Map) groupByUuid.get(uuid) : null;
            String name = g != null ? (String) g.get("name") : (String) node.get("name");

            if (parentName != null && name != null) {
                childMap.put(name, parentName);
            }

            List children = node.containsKey("children") ? (List) node.get("children") : null;
            if (children != null) collectChildren(children, name, groupByUuid, elementByUuid, childMap);
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static String readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        fis.read(data);
        fis.close();
        return new String(data, "UTF-8");
    }

    private static double toNumber(Object o) {
        if (o instanceof Float) return ((Float) o).doubleValue();
        if (o instanceof Double) return (Double) o;
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        if (o instanceof Long) return ((Long) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    private static double parseCoord(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    private static String formatTime(double t) {
        if (t == Math.floor(t) && t < 1e10) return String.valueOf((int) t);
        String s = String.valueOf(t);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

}
