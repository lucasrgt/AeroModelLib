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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Progressive multi-chunk ULTRA envelope with animated and at-rest controls. */
public final class UltraDensityMatrix {
    private static final Path TEST = Paths.get("stationapi", "test");
    private static final Path SUMMARY = TEST.resolve("run/aero-ultra-summary.json");
    private static final Pattern NUMBER = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?[0-9]+)");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final class Profile {
        final String id;
        final int layers, spacing;
        final boolean animated, culls;
        Profile(String id, int layers, int spacing, boolean animated, boolean culls) {
            this.id = id; this.layers = layers; this.spacing = spacing;
            this.animated = animated; this.culls = culls;
        }
    }

    private static final class Result {
        final Profile profile;
        final Path source;
        final Map<String, Long> values;
        Result(Profile profile, Path source, Map<String, Long> values) {
            this.profile = profile; this.source = source; this.values = values;
        }
        long value(String name) { return values.getOrDefault(name, 0L); }
        double fps() { return value("frames") * 1_000_000_000.0 / value("elapsedNanos"); }
        double ms(String name) { return value(name) / 1_000_000.0; }
    }

    private static final List<Profile> PROFILES = Arrays.asList(
        new Profile("isolated-animated", 4, 32, true, true),
        new Profile("field-animated", 2, 4, true, true),
        new Profile("dense-animated", 1, 2, true, true),
        new Profile("saturation-animated", 1, 1, true, true),
        new Profile("field-at-rest", 2, 4, false, true),
        new Profile("saturation-at-rest", 1, 1, false, true)
    );

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        if (options.containsKey("list")) { list(); return; }
        int bench = integer(options, "bench", 15);
        int warmup = integer(options, "warmup", 10);
        int timeout = integer(options, "timeout", 600);
        Path output = TEST.resolve("run/density/" + STAMP.format(LocalDateTime.now()));
        Files.createDirectories(output);
        List<Result> results = new ArrayList<Result>();
        for (Profile profile : select(options.get("only"))) {
            Result result = run(profile, bench, warmup, timeout, output);
            if (result != null) results.add(result);
            writeReports(results, output);
        }
        System.out.println("[Density] report=" + output.resolve("report.md").toAbsolutePath());
    }

    private static Result run(Profile profile, int bench, int warmup, int timeout,
                              Path output) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(TEST.resolve(isWindows() ? "gradlew.bat" : "gradlew").toAbsolutePath().toString());
        command.add("runClientUltraStress");
        command.add("-PultraLayers=" + profile.layers);
        command.add("-PultraSpacing=" + profile.spacing);
        command.add("-PultraPhaseSpread=" + profile.animated);
        command.add("-PultraCulls=" + profile.culls);
        command.add("-Pbench=" + bench);
        command.add("-Pwarmup=" + warmup);
        command.add("-PaeroJvmArgs=-Daero.benchmark.skipNonForcedSaves=true -Daero.animatedLOD="
            + (profile.animated ? "2048" : "0"));
        long started = System.currentTimeMillis();
        System.out.println("[Density] " + profile.id + " layers=" + profile.layers
            + " spacing=" + profile.spacing + " animated=" + profile.animated);
        ProcessBuilder builder = new ProcessBuilder(command).directory(TEST.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(output.resolve("runner.log").toFile()));
        Process process = builder.start();
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            stop(process);
            System.out.println("[Density] TIMEOUT " + profile.id + " after " + timeout + "s");
            return null;
        }
        if (!Files.isRegularFile(SUMMARY) || Files.getLastModifiedTime(SUMMARY).toMillis() < started) {
            System.out.println("[Density] NO SUMMARY " + profile.id + " exit=" + process.exitValue());
            return null;
        }
        Path copy = output.resolve(profile.id + ".json");
        Files.copy(SUMMARY, copy, StandardCopyOption.REPLACE_EXISTING);
        Result result = new Result(profile, copy, numbers(copy));
        if (result.value("journeyCoverageComplete") != 1L)
            throw new IllegalStateException("Incomplete journey in " + copy);
        System.out.printf(Locale.ROOT,
            "[Density] %.2f FPS p99=%.2f ms worst=%.2f ms towers=%d machines=%d%n",
            result.fps(), result.ms("p99FrameNanos"), result.ms("worstFrameNanos"),
            result.value("towerChunksPopulated"), result.value("machinesPlaced"));
        return result;
    }

    private static void writeReports(List<Result> results, Path output) throws IOException {
        StringBuilder csv = new StringBuilder(
            "profile,layers,spacing,animated,tower_chunks,machines,fps,p95_ms,p99_ms,worst_ms,over33,allocation_per_frame\n");
        StringBuilder md = new StringBuilder("# ULTRA multi-chunk density envelope\n\n")
            .append("| Profile | Towers | Machines | FPS | p99 | Worst | >33 ms |\n")
            .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (Result result : results) {
            Profile p = result.profile;
            double allocation = result.value("frames") == 0L ? 0.0
                : result.value("allocatedBytes") / (double) result.value("frames");
            csv.append(p.id).append(',').append(p.layers).append(',').append(p.spacing).append(',')
                .append(p.animated).append(',').append(result.value("towerChunksPopulated")).append(',')
                .append(result.value("machinesPlaced")).append(',').append(format(result.fps())).append(',')
                .append(format(result.ms("p95FrameNanos"))).append(',')
                .append(format(result.ms("p99FrameNanos"))).append(',')
                .append(format(result.ms("worstFrameNanos"))).append(',')
                .append(result.value("framesOver33ms")).append(',').append(format(allocation)).append('\n');
            md.append("| ").append(p.id).append(" | ").append(result.value("towerChunksPopulated"))
                .append(" | ").append(result.value("machinesPlaced")).append(" | ")
                .append(format(result.fps())).append(" | ").append(format(result.ms("p99FrameNanos")))
                .append(" ms | ").append(format(result.ms("worstFrameNanos"))).append(" ms | ")
                .append(result.value("framesOver33ms")).append(" |\n");
        }
        Files.write(output.resolve("results.csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(output.resolve("report.md"), md.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Long> numbers(Path file) throws IOException {
        Matcher matcher = NUMBER.matcher(Files.readString(file));
        Map<String, Long> values = new LinkedHashMap<String, Long>();
        while (matcher.find()) values.put(matcher.group(1), Long.valueOf(matcher.group(2)));
        return values;
    }

    private static List<Profile> select(String requested) {
        if (requested == null || requested.trim().isEmpty()) return PROFILES;
        List<String> ids = Arrays.asList(requested.split(","));
        List<Profile> selected = new ArrayList<Profile>();
        for (Profile profile : PROFILES) if (ids.contains(profile.id)) selected.add(profile);
        if (selected.isEmpty()) throw new IllegalArgumentException("No matching profile: " + requested);
        return selected;
    }

    private static void stop(Process process) {
        process.toHandle().descendants().forEach(handle -> handle.destroyForcibly());
        process.destroyForcibly();
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<String, String>();
        for (String arg : args) {
            String clean = arg.startsWith("--") ? arg.substring(2) : arg;
            int equals = clean.indexOf('=');
            options.put(equals < 0 ? clean : clean.substring(0, equals),
                equals < 0 ? "true" : clean.substring(equals + 1));
        }
        return options;
    }

    private static int integer(Map<String, String> options, String name, int fallback) {
        return options.containsKey(name) ? Integer.parseInt(options.get(name)) : fallback;
    }

    private static void list() {
        for (Profile profile : PROFILES) System.out.println(profile.id + " layers="
            + profile.layers + " spacing=" + profile.spacing + " animated=" + profile.animated);
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("win"); }
}
