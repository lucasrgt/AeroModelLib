import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Compiles and runs AeroModelLib's external Worldline TestKit specs. */
public final class Run {
    private Run() {}
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) throw new IllegalArgumentException("usage: java tools/testkit/Run.java");
        Path root = Paths.get("").toAbsolutePath().normalize(), home = requiredHome();
        Path api = home.resolve("worldline-test-api-0.1.0.jar");
        Path runner = home.resolve("worldline-test-runner-0.1.0.jar");
        require(Files.isRegularFile(api) && Files.isRegularFile(runner), "TestKit 0.1.0 distribution is incomplete");
        Path core = root.resolve("modloader/tests/worldline-core");
        Path specs = root.resolve("modloader/tests/worldline-specs");
        Files.createDirectories(core); Files.createDirectories(specs);
        List<Path> coreSources = sources(root.resolve("core"));
        coreSources.add(root.resolve("modloader/tests/aero/modellib/Aero_AnimationState.java"));
        coreSources.sort(Comparator.naturalOrder());
        List<String> compileCore = compile("-source", "1.8", "-target", "1.8", "-Xlint:none", "-d",
                core.toString()); add(compileCore, coreSources); run(root, compileCore);
        List<Path> specSources = sources(root.resolve("worldline-tests/src/test/java"));
        List<String> compileSpecs = compile("--release", "8", "-Xlint:all,-options", "-Werror",
                "-classpath", api + File.pathSeparator + core, "-d", specs.toString());
        add(compileSpecs, specSources); run(root, compileSpecs);
        run(root, java(), "-jar", runner.toString(), "test", "run", specs.toString(),
                "--classpath=" + core, "--no-runtime", "--reporter=default,agent",
                "--artifacts=" + root.resolve("modloader/tests/worldline-results"));
    }
    private static List<String> compile(String... arguments) {
        List<String> command = new ArrayList<>(); command.add(javac());
        java.util.Collections.addAll(command, arguments); return command;
    }
    private static void add(List<String> command, List<Path> paths) {
        for (Path path : paths) command.add(path.toString());
    }
    private static List<Path> sources(Path root) throws Exception {
        List<Path> values = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder()).limit(5001).forEach(values::add);
        }
        require(!values.isEmpty() && values.size() <= 5000, "invalid Java source count"); return values;
    }
    private static Path requiredHome() {
        String value = System.getenv("WORLDLINE_TESTKIT_HOME");
        require(value != null && !value.trim().isEmpty(), "WORLDLINE_TESTKIT_HOME is required");
        return Paths.get(value).toAbsolutePath().normalize();
    }
    private static String java() { return executable("java"); }
    private static String javac() { return executable("javac"); }
    private static String executable(String name) {
        Path path = Paths.get(System.getProperty("java.home"), "bin", name + (File.separatorChar == '\\' ? ".exe" : ""));
        require(Files.isRegularFile(path), "missing JDK executable " + path); return path.toString();
    }
    private static void run(Path root, String... command) throws Exception {
        run(root, java.util.Arrays.asList(command));
    }
    private static void run(Path root, List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
        int status = process.waitFor(); if (status != 0) throw new IllegalStateException("command failed: " + command.get(0));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
