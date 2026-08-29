import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Zero-dependency repository gate. Run with: java tools/harness/Verify.java */
public final class Verify {
    private final Path root = Paths.get("").toAbsolutePath().normalize();
    private final Path build = root.resolve(".aero/build");
    private final Properties config = new Properties();
    private final boolean platforms;

    private Verify(boolean platforms) { this.platforms = platforms; }

    public static void main(String[] arguments) {
        boolean platforms = Arrays.equals(arguments, new String[] {"--platforms"});
        if (arguments.length > 0 && !platforms) {
            System.err.println("usage: java tools/harness/Verify.java [--platforms]");
            System.exit(2);
        }
        try {
            new Verify(platforms).execute();
        } catch (Exception error) {
            System.err.println("verify failed: " + error.getMessage());
            System.exit(1);
        }
    }

    private void execute() throws Exception {
        System.out.println("AeroModelLib repository verification");
        load(config, root.resolve("harness.properties"));
        List<String> modules = values("modules");
        validateModules(modules);
        Set<String> production = new HashSet<String>();
        for (String module : modules) production.addAll(values("module." + module + ".roots"));
        enforce("product", production, integer("product.max.file"));
        enforce("test", new HashSet<String>(values("test.roots")), integer("test.max.file"));
        enforce("harness", Collections.singleton("tools/harness"), integer("harness.max.file"));
        enforce("tool", new HashSet<String>(values("tool.roots")), integer("tool.max.file"),
                Collections.singleton("tools/harness"));
        run(Arrays.asList("java", "tools/optimization-catalog/Audit.java"), root);
        recreateBuild();
        compileAndTest();
        if (platforms) buildPlatforms();
        System.out.println("verify passed");
    }

    private void validateModules(List<String> modules) {
        require(!modules.isEmpty(), "at least one module is required");
        Set<String> seen = new HashSet<String>();
        Set<String> roots = new HashSet<String>();
        for (String module : modules) {
            List<String> moduleRoots = values("module." + module + ".roots");
            require(!moduleRoots.isEmpty(), "module lacks source roots: " + module);
            for (String name : moduleRoots) {
                require(roots.add(name), "source root belongs to multiple modules: " + name);
                require(Files.isDirectory(root.resolve(name)), "missing module source root: " + name);
            }
            for (String dependency : values("module." + module + ".dependencies"))
                require(seen.contains(dependency), "unknown or later dependency " + module + " -> " + dependency);
            seen.add(module);
        }
        System.out.println("  module order: " + String.join(" -> ", modules));
    }

    private void enforce(String kind, Set<String> roots, int limit) throws IOException {
        enforce(kind, roots, limit, Collections.<String>emptySet());
    }

    private void enforce(String kind, Set<String> roots, int limit,
            Set<String> excludedRoots) throws IOException {
        int files = 0;
        long lines = 0;
        for (String sourceRoot : roots) {
            for (Path path : javaFiles(root.resolve(sourceRoot))) {
                String relative = relative(path);
                if (underAny(relative, excludedRoots)) continue;
                int count = codeLines(path);
                files++;
                lines += count;
                require(count <= limit,
                        kind + " file ceiling exceeded: " + relative + " has " + count + "/" + limit);
            }
        }
        System.out.println("  " + kind + " sources: " + files + " files, " + lines
                + " code lines, max file " + limit);
    }

    private int codeLines(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        boolean block = false;
        int count = 0;
        for (String line : lines) {
            boolean code = false;
            boolean string = false;
            boolean character = false;
            boolean escaped = false;
            for (int index = 0; index < line.length(); index++) {
                char current = line.charAt(index);
                char next = index + 1 < line.length() ? line.charAt(index + 1) : '\0';
                if (block) {
                    if (current == '*' && next == '/') { block = false; index++; }
                    continue;
                }
                if (!string && !character && current == '/' && next == '/') break;
                if (!string && !character && current == '/' && next == '*') { block = true; index++; continue; }
                if (!Character.isWhitespace(current)) code = true;
                if (escaped) { escaped = false; continue; }
                if ((string || character) && current == '\\') { escaped = true; continue; }
                if (!character && current == '"') string = !string;
                else if (!string && current == '\'') character = !character;
            }
            if (code) count++;
        }
        return count;
    }

    private void compileAndTest() throws Exception {
        List<Path> sources = javaFiles(root.resolve("core"));
        List<Path> tests = new ArrayList<Path>();
        for (String name : values("test.roots")) tests.addAll(javaFiles(root.resolve(name)));
        require(!tests.isEmpty(), "no pure-Java tests found");
        List<String> command = new ArrayList<String>(Arrays.asList(
                "javac", "--release", required("java.release"), "-encoding", "UTF-8",
                "-cp", classpath(values("test.libs")), "-d", build.toString()));
        for (Path path : sources) command.add(path.toString());
        for (Path path : tests) command.add(path.toString());
        run(command, root);
        List<String> suites = tests.stream()
                .filter(path -> path.getFileName().toString().endsWith("Test.java"))
                .map(this::testClass).sorted().collect(Collectors.toList());
        require(!suites.isEmpty(), "no JUnit suites found");
        command = new ArrayList<String>(Arrays.asList(
                "java", "-cp", build + java.io.File.pathSeparator + classpath(values("test.libs")),
                "org.junit.runner.JUnitCore"));
        command.addAll(suites);
        run(command, root);
        System.out.println("  pure-Java tests: " + suites.size() + " suites");
    }

    private void buildPlatforms() throws Exception {
        buildPlatform(root.resolve("stationapi"), false);
        // Loom keys its remapped composite dependency by the stable mod GAV.
        // Refresh after building the parent so the integration mod cannot
        // compile against an older checkout's remapped Aero jar.
        buildPlatform(root.resolve("stationapi/test"), true);
        System.out.println("  platform builds: stationapi library + integration mod");
    }

    private void buildPlatform(Path directory, boolean refreshDependencies) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String wrapper = windows ? directory.resolve("gradlew.bat").toString() : "./gradlew";
        List<String> command = new ArrayList<String>(Arrays.asList(wrapper, "--no-daemon"));
        if (refreshDependencies) command.add("--refresh-dependencies");
        command.add("build");
        run(command, directory);
    }

    private String testClass(Path path) {
        String text;
        try { text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
        catch (IOException error) { throw new IllegalStateException(error); }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^package\\s+([A-Za-z0-9_.]+)\\s*;").matcher(text);
        require(matcher.find(), "test source lacks package: " + relative(path));
        String file = path.getFileName().toString();
        return matcher.group(1) + "." + file.substring(0, file.length() - 5);
    }

    private void recreateBuild() throws IOException {
        if (Files.exists(build)) {
            require(build.startsWith(root) && !build.equals(root), "unsafe build path: " + build);
            try (Stream<Path> stream = Files.walk(build)) {
                for (Path path : stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path);
            }
        }
        Files.createDirectories(build);
    }

    private void run(List<String> command, Path directory) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
        int exit = process.waitFor();
        require(exit == 0, "command failed (" + exit + "): " + String.join(" ", command));
    }

    private List<Path> javaFiles(Path directory) throws IOException {
        require(Files.isDirectory(directory), "missing source directory: " + relative(directory));
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted().collect(Collectors.toList());
        }
    }

    private String classpath(List<String> paths) {
        for (String path : paths) require(Files.isRegularFile(root.resolve(path)), "missing test library: " + path);
        return paths.stream().map(path -> root.resolve(path).toString())
                .collect(Collectors.joining(java.io.File.pathSeparator));
    }

    private boolean underAny(String path, Set<String> roots) {
        for (String candidate : roots) if (path.equals(candidate) || path.startsWith(candidate + "/")) return true;
        return false;
    }

    private void load(Properties target, Path path) throws IOException {
        require(Files.isRegularFile(path), "missing " + relative(path));
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { target.load(reader); }
    }

    private List<String> values(String key) {
        String raw = required(key);
        if (raw.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toList());
    }

    private int integer(String key) { return Integer.parseInt(required(key).trim()); }
    private String required(String key) { String value = config.getProperty(key); require(value != null, "missing harness property: " + key); return value; }
    private String relative(Path path) { return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
