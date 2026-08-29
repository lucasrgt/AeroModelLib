import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Counterbalanced end-to-end ablation runner for default-on Aero optimizations. */
public final class AblationMatrix {
    private static final Path TEST = Paths.get("stationapi", "test");
    private static final Path SUMMARY = TEST.resolve("run/aero-ultra-summary.json");
    private static final Pattern NUMBER = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?[0-9]+)");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String[] PHASES = {
        "front-static", "yaw-sweep", "pitch-sweep", "lateral-strafe", "tower-dolly",
        "tower-orbit", "vertical-scan", "floor-occlusion", "chunk-teleports",
        "post-teleport-recovery"
    };

    private static final class Experiment {
        final String id, scenario, property, activationKey;
        Experiment(String id, String scenario, String property, String activationKey) {
            this.id = id; this.scenario = scenario; this.property = property;
            this.activationKey = activationKey;
        }
    }

    private static final class Result {
        final String id, variant;
        final int sequence;
        final Path source;
        final Map<String, Long> values;
        Result(String id, String variant, int sequence, Path source, Map<String, Long> values) {
            this.id = id; this.variant = variant; this.sequence = sequence;
            this.source = source; this.values = values;
        }
        double fps() { return value("frames") * 1_000_000_000.0 / value("elapsedNanos"); }
        double ms(String key) { return value(key) / 1_000_000.0; }
        double allocationPerFrame() { return value("allocatedBytes") / (double) value("frames"); }
        double flushMs() {
            long[] stages = array(source, "stageTotalNanos");
            return stages.length > 7 ? stages[7] / (double) value("frames") / 1_000_000.0 : 0.0;
        }
        long value(String key) {
            Long value = values.get(key);
            if (value == null || value.longValue() <= 0) {
                throw new IllegalStateException("Missing positive " + key + " in " + source);
            }
            return value.longValue();
        }
    }

    private static final List<Experiment> EXPERIMENTS = Arrays.asList(
        new Experiment("animated-batcher", "animated", "aero.animatedbatch", "maxAnimatedQueued"),
        new Experiment("batcher-state-sort", "animated", "aero.batcher.sort", "maxAnimatedBatches"),
        new Experiment("batch-pose-reuse", "animated", "aero.batchposereuse", "maxBatchPosesReused"),
        new Experiment("batch-vertex-reuse", "animated", "aero.batchvertexreuse", "maxBatchVertexTransformsReused"),
        new Experiment("tessellator-bulk-staging", "animated", "aero.tessellatorbulk", "maxTessellatorBulkVertices"),
        new Experiment("bone-pages", "animated", "aero.bonepages", "maxBonePageListCalls"),
        new Experiment("at-rest-display-lists", "at-rest-direct", "aero.atRestLists", "maxAtRestListCalls"),
        new Experiment("be-cell-pages", "at-rest", "aero.becell.pages", "maxCellPageCalls"),
        new Experiment("be-skip-individual", "at-rest", "aero.becell.skipIndividual", "maxCellPageCalls"),
        new Experiment("be-cell-index", "at-rest", "aero.becell", "maxCellIndexEntries"),
        new Experiment("texture-id-cache", "animated", "aero.textureBinder.cache", "maxTextureIds"),
        new Experiment("chunk-visibility", "visibility", "aero.chunkvisibility", "maxVisibleChunks"),
        new Experiment("cone-frustum-cull", "visibility", "aero.frustumcull", "maxViewCulled"),
        new Experiment("smart-lod", "visibility", "aero.smartlod", "maxAnimatedQueued"),
        new Experiment("small-object-cull", "visibility", "aero.smallobj", "maxSmallObjectCulled")
    );

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        if (options.containsKey("list")) { list(); return; }
        int bench = integer(options, "bench", 15);
        int warmup = integer(options, "warmup", 10);
        int rounds = integer(options, "rounds", 1);
        List<Experiment> selected = select(options.get("only"));
        Path output = TEST.resolve("run/ablation/" + STAMP.format(LocalDateTime.now()));
        Files.createDirectories(output);
        List<Result> results = new ArrayList<Result>();
        for (Experiment experiment : selected) {
            for (int round = 0; round < rounds; round++) {
                String[] order = round % 2 == 0
                    ? new String[] {"enabled", "disabled", "disabled", "enabled"}
                    : new String[] {"disabled", "enabled", "enabled", "disabled"};
                for (String variant : order) {
                    results.add(run(experiment, variant, results.size() + 1, bench, warmup, output));
                    writeReports(results, output);
                }
            }
        }
        System.out.println("[Ablation] report=" + output.resolve("report.md").toAbsolutePath());
    }

    private static Result run(Experiment experiment, String variant, int sequence,
                              int bench, int warmup, Path output) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(TEST.resolve(isWindows() ? "gradlew.bat" : "gradlew").toAbsolutePath().toString());
        command.add("runClientUltraStress");
        command.add("-PultraLayers=" + ("animated".equals(experiment.scenario)
            && !"bone-pages".equals(experiment.id) ? "4" : "1"));
        command.add("-PultraSpacing=" + ("visibility".equals(experiment.scenario) ? "4" : "32"));
        command.add("-PultraPhaseSpread=" + ("animated".equals(experiment.scenario) ? "true" : "false"));
        command.add("-Pbench=" + bench);
        command.add("-Pwarmup=" + warmup);
        command.add("-PaeroJvmArgs=" + jvmArgs(experiment, variant));
        long started = System.currentTimeMillis();
        System.out.println("[Ablation] " + sequence + " " + experiment.id + " " + variant);
        ProcessBuilder builder = new ProcessBuilder(command).directory(TEST.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(output.resolve("runner.log").toFile()));
        Process process = builder.start();
        int exit = process.waitFor();
        if (!Files.isRegularFile(SUMMARY) || Files.getLastModifiedTime(SUMMARY).toMillis() < started) {
            throw new IllegalStateException("No fresh summary; Gradle exit=" + exit);
        }
        Path directory = output.resolve(experiment.id);
        Files.createDirectories(directory);
        Path copy = directory.resolve(String.format(Locale.ROOT, "%03d-%s.json", sequence, variant));
        Files.copy(SUMMARY, copy, StandardCopyOption.REPLACE_EXISTING);
        Result result = new Result(experiment.id, variant, sequence, copy, numbers(copy));
        Long coverage = result.values.get("journeyCoverageComplete");
        if (coverage == null || coverage.longValue() != 1L) {
            throw new IllegalStateException("Incomplete ULTRA journey in " + copy);
        }
        Long activation = experiment.activationKey == null ? null
            : result.values.get(experiment.activationKey);
        if ("enabled".equals(variant) && experiment.activationKey != null
                && (activation == null || activation.longValue() <= 0L)) {
            System.out.println("[Ablation] inactive: " + experiment.activationKey + "=0");
        }
        System.out.printf(Locale.ROOT, "[Ablation] %.2f FPS, p99 %.2f ms, flush %.2f ms%n",
            result.fps(), result.ms("p99FrameNanos"), result.flushMs());
        return result;
    }

    private static String jvmArgs(Experiment experiment, String variant) {
        StringBuilder args = new StringBuilder();
        args.append("-Daero.benchmark.skipNonForcedSaves=true");
        args.append(" -D").append(experiment.property).append('=').append("enabled".equals(variant));
        if (experiment.scenario.startsWith("at-rest")) args.append(" -Daero.animatedLOD=0");
        if ("at-rest-direct".equals(experiment.scenario)) args.append(" -Daero.becell.pages=false");
        if ("bone-pages".equals(experiment.id))
            args.append(" -Daero.animatedbatch=false -Daero.bonepages.minTris=1");
        if ("small-object-cull".equals(experiment.id)) args.append(" -Daero.smallobj.px=8");
        if ("visibility".equals(experiment.scenario)) {
            args.append(" -Daero.chunkvisibility=true -Daero.frustumcull=true");
            args.append(" -Daero.animatedLOD=96");
            args.append(" -D").append(experiment.property).append('=').append("enabled".equals(variant));
        }
        return args.toString();
    }

    private static void writeReports(List<Result> results, Path output) throws IOException {
        StringBuilder csv = new StringBuilder("optimization,variant,sequence,fps,p95_ms,p99_ms,flush_ms,allocation_bytes_per_frame,summary\n");
        for (Result result : results) {
            csv.append(result.id).append(',').append(result.variant).append(',').append(result.sequence).append(',');
            csv.append(format(result.fps())).append(',').append(format(result.ms("p95FrameNanos"))).append(',');
            csv.append(format(result.ms("p99FrameNanos"))).append(',').append(format(result.flushMs())).append(',');
            csv.append(format(result.allocationPerFrame())).append(',').append(result.source).append('\n');
        }
        Files.write(output.resolve("results.csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder md = new StringBuilder("# Active optimization ablation\n\n");
        md.append("Positive deltas mean the shipped, enabled optimization won over its rollback oracle.\n\n");
        md.append("| Optimization | Active | Runs | FPS delta | p99 delta | Flush delta | Allocation delta |\n");
        md.append("| --- | --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (Experiment experiment : EXPERIMENTS) {
            double[] on = averages(results, experiment.id, "enabled");
            double[] off = averages(results, experiment.id, "disabled");
            if (on == null || off == null) continue;
            md.append("| ").append(experiment.id).append(" | ").append(activated(results, experiment) ? "yes" : "NO");
            md.append(" | ").append((int) on[4] + (int) off[4]);
            md.append(" | ").append(percent(on[0], off[0], false));
            md.append(" | ").append(percent(on[1], off[1], true));
            md.append(" | ").append(percent(on[2], off[2], true));
            md.append(" | ").append(percent(on[3], off[3], true)).append(" |\n");
        }
        Files.write(output.resolve("report.md"), md.toString().getBytes(StandardCharsets.UTF_8));
        writePhaseCsv(results, output);
    }

    private static void writePhaseCsv(List<Result> results, Path output) throws IOException {
        StringBuilder csv = new StringBuilder(
            "optimization,variant,sequence,phase,frames,fps,p95_ms,p99_ms,worst_ms\n");
        for (Result result : results) {
            long[] frames = array(result.source, "journeyPhaseFrames");
            long[] averages = array(result.source, "journeyPhaseAverageFrameNanos");
            long[] p95 = array(result.source, "journeyPhaseP95FrameNanos");
            long[] p99 = array(result.source, "journeyPhaseP99FrameNanos");
            long[] worst = array(result.source, "journeyPhaseWorstFrameNanos");
            for (int i = 0; i < PHASES.length; i++) {
                csv.append(result.id).append(',').append(result.variant).append(',')
                    .append(result.sequence).append(',').append(PHASES[i]).append(',')
                    .append(frames[i]).append(',')
                    .append(format(averages[i] == 0L ? 0.0d : 1_000_000_000.0d / averages[i])).append(',')
                    .append(format(p95[i] / 1_000_000.0d)).append(',')
                    .append(format(p99[i] / 1_000_000.0d)).append(',')
                    .append(format(worst[i] / 1_000_000.0d)).append('\n');
            }
        }
        Files.write(output.resolve("phases.csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static double[] averages(List<Result> results, String id, String variant) {
        double[] sum = new double[5];
        for (Result result : results) if (id.equals(result.id) && variant.equals(result.variant)) {
            sum[0] += result.fps(); sum[1] += result.ms("p99FrameNanos");
            sum[2] += result.flushMs(); sum[3] += result.allocationPerFrame(); sum[4]++;
        }
        if (sum[4] == 0) return null;
        for (int i = 0; i < 4; i++) sum[i] /= sum[4];
        return sum;
    }

    private static boolean activated(List<Result> results, Experiment experiment) {
        if (experiment.activationKey == null) return false;
        for (Result result : results) if (experiment.id.equals(result.id)
                && "enabled".equals(result.variant)) {
            Long value = result.values.get(experiment.activationKey);
            if (value != null && value.longValue() > 0L) return true;
        }
        return false;
    }

    private static Map<String, Long> numbers(Path path) {
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Matcher matcher = NUMBER.matcher(json);
            Map<String, Long> values = new LinkedHashMap<String, Long>();
            while (matcher.find()) values.put(matcher.group(1), Long.valueOf(matcher.group(2)));
            return values;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    private static long[] array(Path path, String key) {
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^]]*)]").matcher(json);
            if (!matcher.find()) return new long[0];
            String[] raw = matcher.group(1).split(",");
            long[] values = new long[raw.length];
            for (int i = 0; i < raw.length; i++) values[i] = Long.parseLong(raw[i].trim());
            return values;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    private static List<Experiment> select(String only) {
        if (only == null) return EXPERIMENTS;
        List<String> ids = Arrays.asList(only.split(","));
        List<Experiment> selected = new ArrayList<Experiment>();
        for (Experiment experiment : EXPERIMENTS) if (ids.contains(experiment.id)) selected.add(experiment);
        if (selected.size() != ids.size()) throw new IllegalArgumentException("Unknown --only id; use --list");
        return selected;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<String, String>();
        for (String arg : args) {
            if (!arg.startsWith("--")) throw new IllegalArgumentException("Expected --option: " + arg);
            int equals = arg.indexOf('=');
            options.put(arg.substring(2, equals < 0 ? arg.length() : equals), equals < 0 ? "true" : arg.substring(equals + 1));
        }
        return options;
    }

    private static int integer(Map<String, String> options, String key, int fallback) {
        int value = Integer.parseInt(options.containsKey(key) ? options.get(key) : String.valueOf(fallback));
        if (value < 1) throw new IllegalArgumentException("--" + key + " must be positive");
        return value;
    }

    private static void list() {
        for (Experiment experiment : EXPERIMENTS) {
            System.out.println(experiment.id + "\t" + experiment.scenario + "\t-D"
                + experiment.property + "\t" + experiment.activationKey);
        }
    }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static String percent(double enabled, double disabled, boolean lowerWins) {
        double delta = (enabled / disabled - 1.0) * 100.0;
        if (lowerWins) delta = -delta;
        return String.format(Locale.ROOT, "%+.1f%%", delta);
    }
}
