import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.AssertTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

/** Zero-dependency production cyclomatic-complexity gate backed by the JDK parser. */
public final class Check {
    private Check() {}

    public static void main(String[] arguments) {
        try {
            require(arguments.length >= 2, "usage: Check <maximum> <source-root>...");
            int maximum = Integer.parseInt(arguments[0]);
            require(maximum >= 1, "maximum must be positive");
            List<Path> sources = sources(Arrays.copyOfRange(arguments, 1, arguments.length));
            require(!sources.isEmpty(), "no production Java sources found");
            verify(sources, maximum);
        } catch (Exception error) {
            System.err.println("complexity check failed: " + error.getMessage());
            System.exit(1);
        }
    }

    private static void verify(List<Path> sources, int maximum) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "JDK compiler is unavailable");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        int[] methods = {0};
        List<String> findings = new ArrayList<String>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            List<File> files = sources.stream().map(Path::toFile).collect(Collectors.toList());
            Iterable<? extends JavaFileObject> inputs = manager.getJavaFileObjectsFromFiles(files);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    Arrays.asList("-proc:none"), null, inputs);
            Trees trees = Trees.instance(task);
            for (com.sun.source.tree.CompilationUnitTree unit : task.parse())
                new MethodCheck(unit, trees, maximum, methods, findings).scan(unit, null);
        }
        List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(item -> item.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString).collect(Collectors.toList());
        require(errors.isEmpty(), "Java parse errors:\n" + String.join("\n", errors));
        require(findings.isEmpty(), "methods exceed CC " + maximum + ":\n"
                + String.join("\n", findings));
        System.out.println("  cyclomatic complexity: " + methods[0]
                + " production methods, maximum " + maximum);
    }

    private static List<Path> sources(String[] roots) throws Exception {
        List<Path> result = new ArrayList<Path>();
        for (String root : roots) {
            Path directory = Paths.get(root).toAbsolutePath().normalize();
            require(Files.isDirectory(directory), "missing source root: " + root);
            try (Stream<Path> paths = Files.walk(directory)) {
                result.addAll(paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .collect(Collectors.toList()));
            }
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private static final class MethodCheck extends TreePathScanner<Void, Void> {
        private final com.sun.source.tree.CompilationUnitTree unit;
        private final Trees trees;
        private final int maximum;
        private final int[] methods;
        private final List<String> findings;

        MethodCheck(com.sun.source.tree.CompilationUnitTree unit, Trees trees,
                int maximum, int[] methods, List<String> findings) {
            this.unit = unit;
            this.trees = trees;
            this.maximum = maximum;
            this.methods = methods;
            this.findings = findings;
        }

        @Override
        public Void visitMethod(MethodTree method, Void unused) {
            if (method.getBody() != null) {
                methods[0]++;
                BranchCount count = new BranchCount();
                count.scan(method.getBody(), null);
                int complexity = count.value + 1;
                if (complexity > maximum) findings.add(location(method) + " "
                        + method.getName() + " CC=" + complexity);
            }
            return super.visitMethod(method, unused);
        }

        private String location(MethodTree method) {
            long offset = trees.getSourcePositions().getStartPosition(unit, method);
            long line = offset < 0 ? -1 : unit.getLineMap().getLineNumber(offset);
            return Paths.get(unit.getSourceFile().toUri()).toString() + ":" + line;
        }
    }

    private static final class BranchCount extends TreeScanner<Void, Void> {
        int value;

        @Override public Void visitClass(ClassTree tree, Void unused) { return null; }
        @Override public Void visitIf(IfTree tree, Void unused) { value++; return super.visitIf(tree, unused); }
        @Override public Void visitForLoop(ForLoopTree tree, Void unused) { value++; return super.visitForLoop(tree, unused); }
        @Override public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) { value++; return super.visitEnhancedForLoop(tree, unused); }
        @Override public Void visitWhileLoop(WhileLoopTree tree, Void unused) { value++; return super.visitWhileLoop(tree, unused); }
        @Override public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) { value++; return super.visitDoWhileLoop(tree, unused); }
        @Override public Void visitCatch(CatchTree tree, Void unused) { value++; return super.visitCatch(tree, unused); }
        @Override public Void visitConditionalExpression(ConditionalExpressionTree tree, Void unused) { value++; return super.visitConditionalExpression(tree, unused); }
        @Override public Void visitAssert(AssertTree tree, Void unused) { value++; return super.visitAssert(tree, unused); }

        @Override
        public Void visitCase(CaseTree tree, Void unused) {
            if (!tree.getExpressions().isEmpty()) value++;
            return super.visitCase(tree, unused);
        }

        @Override
        public Void visitBinary(BinaryTree tree, Void unused) {
            if (tree.getKind() == Tree.Kind.CONDITIONAL_AND
                    || tree.getKind() == Tree.Kind.CONDITIONAL_OR) value++;
            return super.visitBinary(tree, unused);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
