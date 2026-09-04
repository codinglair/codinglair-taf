package com.codinglair.taf.conformance;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** JDK-parser-backed deterministic rules for consumer Java sources. */
final class JavaSourceRules {
  private static final Set<String> SELECTOR_FACTORIES =
      Set.of("locator", "getByRole", "getByText", "getByLabel", "getByTestId", "findElement");
  private static final Set<String> SESSION_TYPES =
      Set.of(
          "Page",
          "Locator",
          "ElementHandle",
          "WebElement",
          "MobileElement",
          "PlaywrightController",
          "AppiumController",
          "TestController");
  private static final Set<String> DIRECT_CONSTRUCTION =
      Set.of(
          "PlaywrightController",
          "AppiumController",
          "AllureReporter",
          "AllureTestReporter",
          "AllureReportingAdapter");
  private static final Set<String> LIFECYCLE_CALLS =
      Set.of(
          "preflight",
          "beginTest",
          "endTest",
          "beginStep",
          "flushSteps",
          "openSession",
          "closeSession",
          "bindSession",
          "unbindSession");

  private JavaSourceRules() {}

  static List<Finding> inspect(Path file, String source, boolean testSource) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null)
      return List.of(new Finding("source.parser", 1, "Java compiler is unavailable"));
    var unit = new StringSource(file, source);
    JavacTask task =
        (JavacTask)
            compiler.getTask(
                null, null, diagnostic -> {}, List.of("-proc:none"), null, List.of(unit));
    try {
      Iterable<? extends CompilationUnitTree> parsed = task.parse();
      Trees trees = Trees.instance(task);
      List<Finding> findings = new ArrayList<>();
      for (CompilationUnitTree tree : parsed)
        new Scanner(tree, trees.getSourcePositions(), findings, testSource, ownerLayer(file))
            .scan(tree, null);
      return findings;
    } catch (IOException | RuntimeException failure) {
      return List.of(
          new Finding(
              "source.parser",
              1,
              "Java source could not be structurally parsed ("
                  + failure.getClass().getSimpleName()
                  + ")"));
    }
  }

  record Finding(String rule, long line, String detail) {}

  private static boolean ownerLayer(Path file) {
    String path = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    return path.contains("/page/") || path.contains("/component/") || path.contains("/screen/");
  }

  private static final class Scanner extends TreePathScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final List<Finding> findings;
    private final boolean testSource;
    private final boolean objectOwnerLayer;
    private final Deque<String> owners = new ArrayDeque<>();
    private int methodDepth;

    private Scanner(
        CompilationUnitTree unit,
        SourcePositions positions,
        List<Finding> findings,
        boolean testSource,
        boolean objectOwnerLayer) {
      this.unit = unit;
      this.positions = positions;
      this.findings = findings;
      this.testSource = testSource;
      this.objectOwnerLayer = objectOwnerLayer;
    }

    @Override
    public Void visitImport(ImportTree tree, Void unused) {
      String imported = tree.getQualifiedIdentifier().toString();
      if (imported.startsWith("io.qameta.allure"))
        add("reporting.vendor-boundary", tree, "direct Allure import " + imported);
      if (prohibitedDirection(imported))
        add("dependency.package-direction", tree, "prohibited package import " + imported);
      return super.visitImport(tree, unused);
    }

    @Override
    public Void visitClass(ClassTree tree, Void unused) {
      String name = tree.getSimpleName().toString();
      owners.push(name.isEmpty() ? "<anonymous>" : name);
      if (Set.of("TafBaseTest", "TafCucumberHooks", "TestSessionLifecycle").contains(name))
        add("lifecycle.framework-owned", tree, "consumer reimplements " + name);
      if (tree.getImplementsClause().stream()
          .anyMatch(type -> simpleName(type.toString()).equals("TestController")))
        add("lifecycle.framework-owned", tree, "consumer reimplements TestController");
      Void result = super.visitClass(tree, unused);
      owners.pop();
      return result;
    }

    @Override
    public Void visitMethod(MethodTree tree, Void unused) {
      methodDepth++;
      String name = tree.getName().toString();
      if (name.equals("executeReported") || name.equals("executeWithReporting"))
        add("lifecycle.wrapper", tree, "consumer lifecycle/reporting wrapper " + name);
      Void result = super.visitMethod(tree, unused);
      methodDepth--;
      return result;
    }

    @Override
    public Void visitVariable(VariableTree tree, Void unused) {
      String type = tree.getType() == null ? "<inferred>" : simpleName(tree.getType().toString());
      boolean staticField =
          methodDepth == 0 && tree.getModifiers().getFlags().contains(Modifier.STATIC);
      if (staticField && SESSION_TYPES.contains(type))
        add("state.static-session-bound", tree, "static field retains session-bound " + type);
      if (staticField && type.equals("LocatorSpec") && !isLocatorOwner())
        add(
            "locator.owner",
            tree,
            "LocatorSpec is outside a page, component, or screen owner (" + owner() + ")");
      return super.visitVariable(tree, unused);
    }

    @Override
    public Void visitNewClass(NewClassTree tree, Void unused) {
      String type = simpleName(tree.getIdentifier().toString());
      if (DIRECT_CONSTRUCTION.contains(type))
        add("dependency.direct-construction", tree, "consumer constructs " + type);
      if (type.equals("TestSession"))
        add("lifecycle.consumer-owned", tree, "consumer constructs TestSession");
      return super.visitNewClass(tree, unused);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
      String name = invocationName(tree.getMethodSelect());
      if (methodDepth > 0 && isLocatorOwner() && SELECTOR_FACTORIES.contains(name))
        add("locator.inline-construction", tree, "action method constructs selector via " + name);
      if (testSource && (name.equals("service") || name.equals("getBean")))
        add(
            "dependency.service-lookup",
            tree,
            "test uses generic application-context lookup " + name);
      if (testSource && LIFECYCLE_CALLS.contains(name))
        add("lifecycle.manual", tree, "test manually invokes lifecycle operation " + name);
      return super.visitMethodInvocation(tree, unused);
    }

    private boolean prohibitedDirection(String imported) {
      if (imported.startsWith("com.codinglair.taf.mcp")
          || imported.startsWith("com.codinglair.taf.quality")) return true;
      if (objectOwnerLayer)
        return imported.contains(".workflow.") || imported.contains(".service.");
      return false;
    }

    private boolean isLocatorOwner() {
      String lower = owner().toLowerCase(Locale.ROOT);
      return lower.endsWith("page")
          || lower.endsWith("component")
          || lower.endsWith("screen")
          || lower.endsWith("pcom");
    }

    private String owner() {
      return owners.isEmpty() ? "<top-level>" : owners.peek();
    }

    private void add(String rule, Tree tree, String detail) {
      long start = positions.getStartPosition(unit, tree);
      long line = start < 0 ? 1 : unit.getLineMap().getLineNumber(start);
      findings.add(new Finding(rule, line, detail));
    }
  }

  private static String invocationName(Tree select) {
    if (select instanceof IdentifierTree identifier) return identifier.getName().toString();
    if (select instanceof MemberSelectTree member) return member.getIdentifier().toString();
    return select.toString();
  }

  private static String simpleName(String type) {
    int generic = type.indexOf('<');
    String raw = generic < 0 ? type : type.substring(0, generic);
    int dot = raw.lastIndexOf('.');
    return dot < 0 ? raw : raw.substring(dot + 1);
  }

  private static final class StringSource extends SimpleJavaFileObject {
    private final String source;

    private StringSource(Path path, String source) {
      super(path.toUri(), Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }
}
