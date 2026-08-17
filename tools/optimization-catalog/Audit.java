import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Aero-owned source-to-catalog drift audit. Run from the repository root. */
public final class Audit {
    private static final List<String> ROOTS = Arrays.asList(
            "core/aero", "modloader/aero", "stationapi/src/main/java");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern REVISION = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern PROPERTY = Pattern.compile("\"aero\\.[A-Za-z0-9_.-]+\"");
    private static final Pattern INTENT = Pattern.compile(
            "(?i)(fast[- ]path|zero[- ]allocation|alloc-free|avoid(?:s|ing)?[^\\n]{0,50}alloc"
            + "|reus(?:e|es|ing)[^\\n]{0,50}scratch|cache[^\\n]{0,60}(?:hot path|every frame|per-frame)"
            + "|precomput(?:e|ed)[^\\n]{0,50}(?:render|frame)|display-list cache)");
    private final Path root;
    private final List<Source> sources;

    private Audit(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.sources = loadSources();
    }

    public static void main(String[] args) {
        if (args.length > 1) fail("usage: java tools/optimization-catalog/Audit.java [repository-root]");
        try {
            Path root = args.length == 0 ? Paths.get("") : Paths.get(args[0]);
            new Audit(root).run();
        } catch (Exception error) {
            System.err.println("optimization source audit failed: " + error.getMessage());
            System.exit(1);
        }
    }

    private void run() throws Exception {
        List<Record> records = loadRecords();
        Map<String, String> exclusions = loadExclusions();
        Set<String> coveredTypes = new HashSet<String>();
        int symbols = 0;
        int historical = 0;
        for (Record record : records) {
            for (String symbol : record.symbols) {
                symbols++;
                List<Source> candidates = candidates(symbol, record.paths);
                if (candidates.isEmpty()) {
                    require(record.status.equals("retired"),
                            "unresolved current source symbol " + record.id + " -> " + symbol);
                    historical++;
                    continue;
                }
                String member = member(symbol);
                if (member != null) require(candidates.stream().anyMatch(source -> hasMember(source, member)),
                        "unresolved source member " + record.id + " -> " + symbol);
                for (Source candidate : candidates) coveredTypes.add(candidate.type);
            }
        }

        Set<String> propertyTypes = signalTypes(PROPERTY);
        Set<String> intentTypes = signalTypes(INTENT);
        Set<String> signaled = new HashSet<String>(propertyTypes);
        signaled.addAll(intentTypes);
        List<String> uncovered = signaled.stream()
                .filter(type -> !coveredTypes.contains(type) && !exclusions.containsKey(type))
                .sorted().collect(Collectors.toList());
        require(uncovered.isEmpty(), "unclassified performance source: " + String.join(",", uncovered));
        for (String type : exclusions.keySet()) {
            require(signaled.contains(type), "stale audit exclusion " + type);
            require(!coveredTypes.contains(type), "covered type must leave audit exclusions " + type);
        }

        long active = records.stream().filter(record -> record.status.equals("active")).count();
        long candidate = records.stream().filter(record -> record.status.equals("candidate")).count();
        long rejected = records.stream().filter(record -> record.status.equals("rejected")).count();
        long retired = records.stream().filter(record -> record.status.equals("retired")).count();
        System.out.println("  Aero optimization audit: " + records.size() + " records ("
                + active + " active, " + candidate + " candidate, " + rejected
                + " rejected, " + retired + " retired)");
        System.out.println("  source coverage: " + symbols + " symbols, " + historical
                + " historical, " + propertyTypes.size() + " property types, "
                + intentTypes.size() + " explicit-intent types, " + exclusions.size() + " exclusions");
    }

    private List<Record> loadRecords() throws IOException {
        Path directory = root.resolve("optimizations/catalog");
        require(Files.isDirectory(directory), "missing optimizations/catalog");
        List<Record> records = new ArrayList<Record>();
        Set<String> ids = new HashSet<String>();
        for (Path path : files(directory, ".properties")) {
            Properties fields = properties(path);
            String id = required(fields, "id", path);
            String status = required(fields, "status", path);
            String revision = required(fields, "source.revision", path);
            require(REVISION.matcher(revision).matches(), "invalid source.revision in " + id);
            require(ids.add(id), "duplicate optimization id " + id);
            require(path.getFileName().toString().equals(id + ".properties"), "filename/id drift " + id);
            List<String> symbols = csv(required(fields, "source.symbols", path));
            List<String> paths = csv(fields.getProperty("source.paths", ""));
            for (String sourcePath : paths)
                require(Files.isRegularFile(root.resolve(sourcePath)), "missing source.paths entry " + sourcePath);
            records.add(new Record(id, status, symbols, paths));
        }
        return records;
    }

    private Map<String, String> loadExclusions() throws IOException {
        Path path = root.resolve("optimizations/audit-exclusions.properties");
        Properties fields = properties(path);
        Map<String, String> result = new HashMap<String, String>();
        for (String key : fields.stringPropertyNames()) {
            String reason = fields.getProperty(key).trim();
            require(reason.length() >= 20, "audit exclusion needs a specific reason " + key);
            result.put(key, reason);
        }
        return result;
    }

    private List<Source> loadSources() throws IOException {
        List<Source> result = new ArrayList<Source>();
        for (String name : ROOTS) {
            Path directory = root.resolve(name);
            for (Path path : files(directory, ".java")) {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                Matcher matcher = PACKAGE.matcher(text);
                require(matcher.find(), "source lacks package " + relative(path));
                String file = path.getFileName().toString();
                String type = matcher.group(1) + "." + file.substring(0, file.length() - 5);
                result.add(new Source(type, relative(path), text));
            }
        }
        return result;
    }

    private List<Source> candidates(String symbol, List<String> constrainedPaths) {
        String type = type(symbol);
        return sources.stream().filter(source -> constrainedPaths.isEmpty()
                        ? type.equals(source.type) || type.startsWith(source.type + ".")
                        : constrainedPaths.contains(source.path)
                                && (type.equals(source.type) || type.startsWith(source.type + ".")))
                .filter(source -> nestedTypeExists(source, type)).collect(Collectors.toList());
    }

    private static boolean nestedTypeExists(Source source, String type) {
        if (type.equals(source.type)) return true;
        String nested = type.substring(source.type.length() + 1);
        return Pattern.compile("\\b(?:class|interface|enum)\\s+" + Pattern.quote(nested) + "\\b")
                .matcher(source.text).find();
    }

    private static boolean hasMember(Source source, String member) {
        return Pattern.compile("\\b" + Pattern.quote(member) + "\\b\\s*(?:\\(|[=;,])")
                .matcher(source.text).find();
    }

    private Set<String> signalTypes(Pattern pattern) {
        return sources.stream().filter(source -> pattern.matcher(source.text).find())
                .map(source -> source.type).collect(Collectors.toSet());
    }

    private List<Path> files(Path directory, String suffix) throws IOException {
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted().collect(Collectors.toList());
        }
    }

    private static Properties properties(Path path) throws IOException {
        require(Files.isRegularFile(path), "missing " + path);
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }

    private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }
    private static String type(String symbol) { int at = symbol.indexOf('#'); return at < 0 ? symbol : symbol.substring(0, at); }
    private static String member(String symbol) { int at = symbol.indexOf('#'); return at < 0 ? null : symbol.substring(at + 1); }
    private static List<String> csv(String value) { return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()); }
    private static String required(Properties fields, String key, Path path) { String value = fields.getProperty(key); require(value != null && !value.trim().isEmpty(), "missing " + key + " in " + path); return value.trim(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void fail(String message) { System.err.println(message); System.exit(2); }

    private static final class Record {
        final String id, status; final List<String> symbols, paths;
        Record(String id, String status, List<String> symbols, List<String> paths) { this.id = id; this.status = status; this.symbols = symbols; this.paths = paths; }
    }
    private static final class Source {
        final String type, path, text;
        Source(String type, String path, String text) { this.type = type; this.path = path; this.text = text; }
    }
}
